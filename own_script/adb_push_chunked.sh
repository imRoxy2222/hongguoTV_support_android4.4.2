#!/usr/bin/env bash
# 用法: adb_push_chunked.sh <设备地址> <本地文件> [设备目标目录]
# 适用场景：老系统（Android 4.x）的 adbd 在长时间传输时连接易被重置，
# 导致 adb push / adb install 卡在中途。本脚本按 1MB 分块、每块独立连接、
# 逐块 MD5 校验并重试，最后在设备端拼接并整体校验。
set -u
DEV="${1:?需要设备地址，如 192.168.2.106:5555}"
SRC="${2:?需要本地文件路径}"
DST_DIR="${3:-/data/local/tmp}"
CHUNK=1000000
TRIES=4

export MSYS_NO_PATHCONV=1
command -v adb >/dev/null || { echo "adb 不可用"; exit 1; }
[ -f "$SRC" ] || { echo "文件不存在: $SRC"; exit 1; }

base=$(basename "$SRC")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

echo "[1/4] 分块 ($CHUNK 字节/块)"
split -b $CHUNK -a 3 "$SRC" "$work/part."

echo "[2/4] 逐块推送并校验"
parts=$(ls "$work"/part.* | wc -l | tr -d ' ')
adb -s "$DEV" shell "rm -rf $DST_DIR/_chunks_$base; mkdir -p $DST_DIR/_chunks_$base" >/dev/null 2>&1
ok=0; fail=0
for f in "$work"/part.*; do
  name=$(basename "$f")
  host=$(md5sum "$f" | awk '{print $1}')
  got=""
  for t in $(seq 1 $TRIES); do
    adb -s "$DEV" push "$f" "$DST_DIR/_chunks_$base/$name" >/dev/null 2>&1
    got=$(adb -s "$DEV" shell "md5sum $DST_DIR/_chunks_$base/$name 2>/dev/null | awk '{print \$1}'" 2>/dev/null | tr -d '\r\n')
    [ "$got" = "$host" ] && break
    adb disconnect "$DEV" >/dev/null 2>&1; sleep 2
    adb connect "$DEV" >/dev/null 2>&1; sleep 4
  done
  if [ "$got" = "$host" ]; then
    ok=$((ok+1)); printf "  %s OK\n" "$name"
  else
    fail=$((fail+1)); printf "  %s FAIL\n" "$name"
  fi
done
echo "  完成: 成功 $ok / 共 $parts"
[ "$fail" -gt 0 ] && { echo "存在失败分片，中止"; exit 1; }

echo "[3/4] 设备端拼接"
adb -s "$DEV" shell "cd $DST_DIR/_chunks_$base && busybox cat part.* > $DST_DIR/$base" >/dev/null 2>&1

echo "[4/4] 整体校验"
host_sum=$(md5sum "$SRC" | awk '{print $1}')
dev_sum=$(adb -s "$DEV" shell "md5sum $DST_DIR/$base 2>/dev/null | awk '{print \$1}'" | tr -d '\r\n')
if [ "$host_sum" = "$dev_sum" ]; then
  echo "  校验通过: $dev_sum"
  echo "已就绪: $DST_DIR/$base"
  echo "安装: adb -s $DEV shell pm install -r $DST_DIR/$base"
  adb -s "$DEV" shell "rm -rf $DST_DIR/_chunks_$base" >/dev/null 2>&1
else
  echo "  校验失败: 主机=$host_sum 设备=$dev_sum"; exit 1
fi
