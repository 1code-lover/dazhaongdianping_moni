#!/usr/bin/env bash
# 使用 redis-cli 验证 seckill_rollback.lua（需本机 Redis）
# 用法: chmod +x scripts/verify-seckill-rollback-lua.sh && ./scripts/verify-seckill-rollback-lua.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LUA="$ROOT/src/main/resources/seckill_rollback.lua"
REDIS_CLI="${REDIS_CLI:-redis-cli}"
HOST="${REDIS_HOST:-127.0.0.1}"
PORT="${REDIS_PORT:-6379}"
PASS="${REDIS_PASSWORD:-123456}"

rc() {
  if [[ -n "$PASS" ]]; then
    "$REDIS_CLI" -h "$HOST" -p "$PORT" -a "$PASS" "$@"
  else
    "$REDIS_CLI" -h "$HOST" -p "$PORT" "$@"
  fi
}

VID="999999"
UID="888888"
STOCK_KEY="seckill:stock:$VID"
ORDER_KEY="seckill:order:$VID"

echo "== 准备测试数据"
rc SET "$STOCK_KEY" 5 >/dev/null
rc SADD "$ORDER_KEY" "$UID" >/dev/null

echo "== 第一次回滚 (期望 0)"
R1=$(rc --eval "$LUA" , "$VID" "$UID" | tr -d '\r')
echo "script result: $R1"

S_AFTER=$(rc GET "$STOCK_KEY" | tr -d '\r')
IN_SET=$(rc SISMEMBER "$ORDER_KEY" "$UID" | tr -d '\r')
echo "回滚后 stock=$S_AFTER userInOrderSet=$IN_SET"

echo "== 第二次回滚 (期望 1 幂等)"
R2=$(rc --eval "$LUA" , "$VID" "$UID" | tr -d '\r')
echo "script result: $R2"

rc DEL "$STOCK_KEY" "$ORDER_KEY" >/dev/null

if [[ "$R1" == "0" && "$R2" == "1" && "$S_AFTER" == "6" && "$IN_SET" == "0" ]]; then
  echo "RESULT: OK"
  exit 0
fi
echo "RESULT: FAIL"
exit 1
