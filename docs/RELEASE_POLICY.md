# 发布版本规则与自动化

本文定义本 fork（`imRoxy2222/hongguoTV_support_android4.4.2`）的版本号规则、发版判定条件与
自动化流程。分支模型与上游同步见 [FORK_RELEASE.md](FORK_RELEASE.md)，上游流水线见
`kotlin-tv/AUTO_UPDATES.md`。

## 1. 一句话规则

```
tag         = a44-v<上游版本>-<N>
versionName = <上游版本>-<N>
versionCode = 上游 versionCode * 100 + N
```

`<上游版本>` 恒等于 `main` 上最新的 `kotlin-v*` 标签去掉 `kotlin-v` 前缀；`<N>` 是**该上游版本的第几次
fork 发布**，从 1 开始。

## 2. 为什么需要一个修订位 `<N>`

上游版本号只能标识「上游的功能基线」，标识不了「本 fork 发布了几次」。以下情况都要求同一个上游版本
再发一次，而 fork 的发布节奏与上游并不同步：

- fork 专属修复（安装器入口、4.4.2 解码、自更新地址等）；
- 首次流水线失败后重发；
- 上游长期不升版，而 fork 需要出新包；
- 构建环境或签名轮换。

若 tag 只写 `a44-v0.13.2`，上述任一情况都会导致没有可用 tag，只能删除已发布的 tag 与 Release，
破坏「已发布产物不覆盖」的原则。`<N>` 用很小的成本消除了这个死结。

`versionCode = 上游code * 100 + N` 是配套设计：

- **天然大于上游**：上游 17 → fork 1701，数量级错开，不会与上游将来的 18、19 撞车；
- **单调递增由规则保证**：上游升版则百位变大，同版重发则 N 变大，不依赖 git 历史；
- **与 tag 一一对应**：`%100` 就是 `<N>`，CI 会强校验。

> 不用 commit 数：commit 数会在 rebase / amend / squash 后**减少**，一旦回退，已装用户将永远收不到
> 「更小 versionCode」的新版（客户端判定是严格大于）。

## 3. 示例

| 上游 tag | 上游 code | fork 场景 | fork tag | versionName | versionCode |
| --- | --- | --- | --- | --- | --- |
| `kotlin-v0.13.2` | 17 | 首次发布 | `a44-v0.13.2-1` | `0.13.2-1` | 1701 |
| `kotlin-v0.13.2` | 17 | 修复后重发 | `a44-v0.13.2-2` | `0.13.2-2` | 1702 |
| `kotlin-v0.13.3` | 18 | 上游升版后首次 | `a44-v0.13.3-1` | `0.13.3-1` | 1801 |

上游升到 18 时 `1801 > 1702`，覆盖关系自动正确。**不需要手算任何数字。**

## 4. 用脚本算，不要手改

```sh
cd /home/frk2222/code/hongguoTV_support_android4.4.2
git fetch upstream --prune --tags          # 脚本只读本地，不联网

python3 scripts/next_version.py            # 只计算并打印 JSON
python3 scripts/next_version.py --write    # 同时写回 build.gradle.kts
python3 scripts/next_version.py --force    # 上游未变也再发一版（修复重发）
```

输出示例：

```json
{
  "needs_release": true,
  "reason": "0.13.2 尚无 fork 发布",
  "upstream_tag": "kotlin-v0.13.2",
  "upstream_version": "0.13.2",
  "upstream_version_code": 17,
  "revision": 1,
  "version_name": "0.13.2-1",
  "version_code": 1701,
  "fork_tag": "a44-v0.13.2-1",
  "published_fork_tags": []
}
```

脚本会拒绝两种危险状态：源码版本超前于上游（说明 `upstream/main` 没更新），以及
`versionCode` 编码溢出（同一上游版本发布超过 99 次，或上游 code 大于 21474836）。

## 5. 定时任务：如何判定「需要发版」

按顺序执行，任一步给出结论即可停止：

1. `git fetch upstream --prune --tags`；
2. `python3 scripts/next_version.py` 读 `needs_release`；
3. `needs_release == true` → 发版，进入第 6 节；
4. `needs_release == false` 但本分支在上次发布后仍有源码改动 → 用 `--force` 发版；
5. 否则不发版。

第 4 步的「仍有源码改动」判定：

```sh
PREV_TAG=$(git tag -l 'a44-v*' --sort=-creatordate | head -1)
git log --oneline "${PREV_TAG}..HEAD" -- kotlin-tv scripts .github
```

输出非空即表示上次发布之后动过代码，需要再发一版。只看文档（`*.md`、`docs/`）的改动不触发发版。

## 6. 自动发布流程

```sh
cd /home/frk2222/code/hongguoTV_support_android4.4.2

# 1) 同步上游
git checkout main
git fetch upstream --prune --tags
git merge --ff-only upstream/main
git checkout feat/android-4.4.2-api19
git merge main                       # 依赖冲突时先改 kotlin-tv/gradle/libs.versions.toml

# 2) 本地验证（至少跑核心测试；有 SDK 时再跑 lint 与 assemble）
./kotlin-tv/gradlew -p kotlin-tv :core:test --console=plain

# 3) 计算并写入版本号
python3 scripts/next_version.py --write

# 4) 单独提交版本号，便于回滚与审计
git add kotlin-tv/app/build.gradle.kts
git commit -m "chore(release): 0.13.2-1 (versionCode 1701)"

# 5) 打标签并推送分支，再单独推标签
git tag a44-v0.13.2-1
git push origin feat/android-4.4.2-api19
git push origin a44-v0.13.2-1
```

推送后 `fork-release.yml` 自动构建、校验签名、生成 `update.json` 并创建 Release。**切勿 `git push --tags`**，
那会把上游的 `kotlin-v*` 标签一并推到本仓库。

> 第 5 步的 `git push` 需要 push 权限凭据。定时任务若要全自动，需为其配置可写凭据，并确认它只推送
> `feat/android-4.4.2-api19` 与本规则匹配的标签。

## 7. CI 强校验（违反任一即失败）

`fork-release.yml` 在构建前会依次检查：

1. 标签匹配 `^a44-v[0-9]+\.[0-9]+\.[0-9]+-[0-9]+$`；
2. 源码 `versionName` 与标签去掉 `a44-v` 后**严格相等**；
3. 源码 `minSdk` 仍为 19；
4. `versionCode % 100 == <N>`；
5. `versionCode` 大于最近一次已发布 Release 的 `versionCode`（从该 Release 的 `update.json` 读取）；
6. 每个 APK 的包名、版本、minSdk 与 v1(JAR) 签名正确；
7. 同名 Release 不存在（已发布的产物永不覆盖）。

第 4、5 条是为了让「手误写错数字」和「忘记递增」在构建阶段就暴露，而不是等用户收不到更新才发现。

## 8. 禁止事项

- 不删除已发布的 tag 或 Release，不改已发布版本的 `versionCode`；
- 不在标签上直接改版本号，版本号只在源码里改，标签只做镜像；
- 不手动编辑 `build.gradle.kts` 里的 `1701` / `"0.13.2-1"`，一律用 `--write`；
- 不向正式流水线传 `HONGGUOTV_VERSION_CODE` / `HONGGUOTV_VERSION_NAME` 环境变量，那会绕过源码校验；
- 不使用 `kotlin-v*` 前缀打本 fork 的标签，会与上游标签冲突。

## 9. 典型场景处置

| 场景 | 处置 |
| --- | --- |
| 上游发新版 | 正常流程，`N` 自动从 1 开始，`versionCode` 进入新的百位段 |
| 上游未变但需修 fork bug | `--force`，`N` 加 1，例如 `a44-v0.13.2-2` / 1702 |
| 流水线在构建阶段失败，未产出 Release | 修复原因后删除本地 tag 重打同一 tag 即可（无产物发布，可安全重用） |
| Release 已创建但附件有误 | 不改不删，改用 `--force` 增加 `N` 重发 |
| 签名证书轮换 | 必须增加 `N`；新证书与旧包不同签名，用户须先卸载再装（会清本机记录），需在 Release 说明中写明 |
| 上游 `versionCode` 未递增 | 脚本会因 `1801 <= 1702` 之类的比较给出 warning，需人工确认上游是否漏改 |

## 10. 客户端侧约束（改动前必读）

- `AppUpdate.parse` 的 tag 正则为 `(?:kotlin|a44)-v[0-9][0-9A-Za-z.+-]{0,80}`，`a44-v0.13.2-1` 合法；
- `versionName` 正则为 `[0-9][0-9A-Za-z.+-]{0,63}`，`0.13.2-1` 合法；
- APK 文件名必须是 `hongguotv-kotlin-<version>-android4.apk`，`update.json` 的 `apk.name` 指向它；
- 若将来更换标签前缀或版本名格式，**必须同时**修改上述两条正则、`fork-release.yml` 的正则，
  以及本文档，否则客户端会静默拒绝元数据。
