#!/usr/bin/env python3
"""计算下一个 fork 发布版本号（规则见 docs/RELEASE_POLICY.md）。

    tag         = a44-v<上游版本>-<N>
    versionName = <上游版本>-<N>
    versionCode = 上游 versionCode * 100 + N

只读本地 git 与工作区文件，不访问网络。调用前先 git fetch <remote> --tags。
默认输出 JSON，便于定时任务或 AI 直接解析；--write 才会改动 build.gradle.kts。
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / 'kotlin-tv' / 'app' / 'build.gradle.kts'
UPSTREAM_MATCH = 'kotlin-v*'
FORK_PREFIX = 'a44-v'
NAME_RE = re.compile(r'versionName\s*=.*\?:\s*"([^"]+)"')
CODE_RE = re.compile(r'versionCode\s*=.*\?:\s*(\d+)')
NAME_SET_RE = re.compile(r'(versionName\s*=.*\?:\s*")[^"]+(")')
CODE_SET_RE = re.compile(r'(versionCode\s*=.*\?:\s*)\d+')


def git(*args):
    return subprocess.run(
        ['git', *args], cwd=ROOT, check=True, capture_output=True, text=True
    ).stdout.strip()


def latest_upstream_tag(remote):
    for ref in (f'{remote}/main', 'main', 'HEAD'):
        try:
            return git('describe', '--tags', '--abbrev=0', '--match', UPSTREAM_MATCH, ref)
        except subprocess.CalledProcessError:
            continue
    raise SystemExit(f'找不到 {UPSTREAM_MATCH} 标签，请先 git fetch {remote} --tags')


def upstream_version_code(tag):
    text = git('show', f'{tag}:kotlin-tv/app/build.gradle.kts')
    match = CODE_RE.search(text)
    if not match:
        raise SystemExit(f'无法从 {tag} 读取 versionCode')
    return int(match.group(1))


def read_local():
    text = GRADLE.read_text(encoding='utf-8')
    name, code = NAME_RE.search(text), CODE_RE.search(text)
    if not name or not code:
        raise SystemExit('无法从 build.gradle.kts 读取 versionName/versionCode')
    return name.group(1), int(code.group(1))


def split_name(name):
    match = re.fullmatch(r'(\d+\.\d+\.\d+)-(\d+)', name)
    if match:
        return match.group(1), int(match.group(2))
    if re.fullmatch(r'\d+\.\d+\.\d+', name):
        return name, 0
    raise SystemExit(f'versionName 不符合 <上游版本>-<N>：{name}')


def as_tuple(version):
    return tuple(int(part) for part in version.split('.'))


def main():
    parser = argparse.ArgumentParser(description='计算下一个 fork 发布版本')
    parser.add_argument('--remote', default='upstream', help='上游 remote 名，默认 upstream')
    parser.add_argument('--force', action='store_true', help='上游未变也再发一版（修复重发）')
    parser.add_argument('--write', action='store_true', help='把结果写回 build.gradle.kts')
    args = parser.parse_args()

    tag = latest_upstream_tag(args.remote)
    if not tag.startswith('kotlin-v'):
        raise SystemExit(f'上游标签格式不支持：{tag}')
    upstream_version = tag[len('kotlin-v'):]
    upstream_code = upstream_version_code(tag)
    local_name, local_code = read_local()
    local_upstream, local_revision = split_name(local_name)

    if as_tuple(local_upstream) > as_tuple(upstream_version):
        raise SystemExit(
            f'源码版本 {local_upstream} 超前于上游 {upstream_version}，先确认 {args.remote}/main 已最新'
        )

    published = [
        line for line in git('tag', '-l', f'{FORK_PREFIX}{upstream_version}-*').splitlines() if line.strip()
    ]
    revision = len(published) + 1
    if args.force and not published:
        revision = local_revision + 1

    if upstream_version != local_upstream:
        needs_release, reason = True, f'上游已前进到 {upstream_version}'
    elif not published:
        needs_release, reason = True, f'{upstream_version} 尚无 fork 发布'
    elif args.force:
        needs_release, reason = True, f'{upstream_version} 已有 {len(published)} 次发布，强制再发'
    else:
        needs_release = False
        reason = f'{upstream_version} 已发布 {len(published)} 次，无需发版'

    version_name = f'{upstream_version}-{revision}'
    if revision > 99:
        raise SystemExit(f'同一上游版本的发布次数达到 {revision}，超出 99 的编码上限，需调整 versionCode 规则')
    if upstream_code > 21474836:
        raise SystemExit(f'上游 versionCode {upstream_code} 过大，乘 100 会溢出 32 位')
    version_code = upstream_code * 100 + revision
    result = {
        'needs_release': needs_release,
        'reason': reason,
        'upstream_tag': tag,
        'upstream_version': upstream_version,
        'upstream_version_code': upstream_code,
        'revision': revision,
        'version_name': version_name,
        'version_code': version_code,
        'fork_tag': f'{FORK_PREFIX}{version_name}',
        'published_fork_tags': published,
    }

    if version_code < local_code and needs_release:
        result['warning'] = f'计算出的 versionCode {version_code} 小于当前源码的 {local_code}'

    if args.write:
        if not needs_release:
            result['written'] = False
        else:
            text = GRADLE.read_text(encoding='utf-8')
            text = CODE_SET_RE.sub(lambda m: m.group(1) + str(version_code), text, count=1)
            text = NAME_SET_RE.sub(lambda m: m.group(1) + version_name + m.group(2), text, count=1)
            GRADLE.write_text(text, encoding='utf-8')
            result['written'] = True

    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write('\n')


if __name__ == '__main__':
    main()
