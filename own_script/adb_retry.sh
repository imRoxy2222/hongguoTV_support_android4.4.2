#!/usr/bin/env bash
# 用法: adb_retry.sh <命令...>
# 老设备（Android 4.x）的 adbd 在负载高时会把 TCP 连接断掉，单次 adb 调用常返回空。
# 这个包装在失败时自动重连设备并重试，只对「幂等」的 adb 命令使用（shell/logcat/pull）。
set -u
DEV="${ADB_DEV:-192.168.2.106:5555}"
TRIES="${ADB_TRIES:-6}"

export MSYS_NO_PATHCONV=1
command -v adb >/dev/null || { echo "adb 不可用" >&2; exit 1; }
[ $# -gt 0 ] || { echo "需要 adb 子命令" >&2; exit 1; }

for attempt in $(seq 1 "$TRIES"); do
  if out=$(adb -s "$DEV" "$@" 2>/dev/null); then
    if [ -n "$out" ]; then
      printf '%s\n' "$out"
      exit 0
    fi
  fi
  adb connect "$DEV" >/dev/null 2>&1
  sleep 2
done
echo "重试 $TRIES 次仍失败: adb $*" >&2
exit 1
