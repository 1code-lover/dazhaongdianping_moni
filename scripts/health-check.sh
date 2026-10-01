#!/usr/bin/env bash
# ============================================================================
# hmdp 健康巡检脚本：检查核心组件状态，异常时推飞书群机器人告警
# 由 cron 每小时执行；进程级自动恢复由 systemd Restart=always 保证
# 飞书配置：在 .env 中设置 FEISHU_WEBHOOK（群设置→群机器人→自定义机器人 webhook 地址）
# ============================================================================
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG=/var/log/hmdp-health.log
TS=$(date '+%F %T')

# 加载飞书 webhook（若已配置）
if [[ -f "${PROJECT_DIR}/.env" ]]; then
  FEISHU_WEBHOOK=$(grep '^FEISHU_WEBHOOK=' "${PROJECT_DIR}/.env" | cut -d= -f2- | tr -d "'" | tr -d '"')
fi

backend=$(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/actuator/health 2>/dev/null || echo 000)
frontend=$(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1/ 2>/dev/null || echo 000)
redis=$(redis-cli -a 4d05a61de1d44594b2e0bf45facda0b3 ping 2>/dev/null || echo FAIL)
mysql=$(mysqladmin -u root status >/dev/null 2>&1 && echo OK || echo FAIL)
es=$(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:9200 2>/dev/null || echo 000)
kafka=$(docker ps --filter name=hmdp-kafka --format '{{.Status}}' 2>/dev/null | grep -q Up && echo UP || echo DOWN)
disk=$(df -h / | awk 'NR==2{print $5}')
load=$(cut -d' ' -f1 /proc/loadavg)

echo "$TS backend=$backend frontend=$frontend redis=$redis mysql=$mysql es=$es kafka=$kafka disk=$disk load=$load" >> "$LOG"

# 异常判定与飞书告警
alerts=()
[[ "$backend" != "200" ]] && alerts+=("后端健康检查异常(http=$backend)")
[[ "$frontend" != "200" ]] && alerts+=("前端80端口异常(http=$frontend)")
[[ "$redis" != "PONG" ]] && alerts+=("Redis异常($redis)")
[[ "$mysql" != "OK" ]] && alerts+=("MySQL异常")
[[ "$es" != "200" ]] && alerts+=("ES异常(http=$es)")
[[ "$kafka" != "UP" ]] && alerts+=("Kafka容器宕机")
disk_pct=${disk%\%}
[[ "$disk_pct" -ge 95 ]] && alerts+=("磁盘使用率过高($disk)")

if [[ ${#alerts[@]} -gt 0 ]]; then
  msg="🚨 hmdp 服务器告警 $(date '+%F %T')"
  for a in "${alerts[@]}"; do msg="$msg
• $a"; done
  echo "$TS ALERT: ${alerts[*]}" >> "$LOG"
  if [[ -n "$FEISHU_WEBHOOK" ]]; then
    curl -s -m 10 -X POST -H 'Content-Type: application/json' \
      -d "{\"msg_type\":\"text\",\"content\":{\"text\":\"$msg\"}}" \
      "$FEISHU_WEBHOOK" >/dev/null 2>&1 && echo "$TS 飞书告警已推送" >> "$LOG"
  else
    echo "$TS 飞书未配置(FEISHU_WEBHOOK为空)，仅记录日志" >> "$LOG"
  fi
fi
