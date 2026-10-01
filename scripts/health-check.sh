#!/usr/bin/env bash
# ============================================================================
# hmdp 健康巡检脚本：检查核心组件状态并记录到 /var/log/hmdp-health.log
# 由 cron 每小时执行；进程级自动恢复由 systemd Restart=always 保证
# ============================================================================
LOG=/var/log/hmdp-health.log
TS=$(date '+%F %T')

backend=$(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/actuator/health 2>/dev/null || echo 000)
frontend=$(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1/ 2>/dev/null || echo 000)
redis=$(redis-cli -a 4d05a61de1d44594b2e0bf45facda0b3 ping 2>/dev/null || echo FAIL)
mysql=$(mysqladmin -u root status >/dev/null 2>&1 && echo OK || echo FAIL)
es=$(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:9200 2>/dev/null || echo 000)
kafka=$(docker ps --filter name=hmdp-kafka --format '{{.Status}}' 2>/dev/null | grep -q Up && echo UP || echo DOWN)
disk=$(df -h / | awk 'NR==2{print $5}')
load=$(cut -d' ' -f1 /proc/loadavg)

echo "$TS backend=$backend frontend=$frontend redis=$redis mysql=$mysql es=$es kafka=$kafka disk=$disk load=$load" >> "$LOG"
