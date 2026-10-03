#!/usr/bin/env bash
# ============================================================================
# hmdp MySQL 每日备份：mysqldump 压缩保留 7 天
# 由 cron 每日 03:30 执行
# ============================================================================
set -euo pipefail

# 从 .env 读取数据库凭据（.env 已被 gitignore，不入库）
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "${PROJECT_DIR}/.env" ]]; then
  set -a; source "${PROJECT_DIR}/.env"; set +a
fi
: "${DB_PASSWORD:?DB_PASSWORD 未配置}"

BACKUP_DIR=/home/dazhaongdianping_moni/backups
mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/hmdp_$(date +%F).sql.gz"

mysqldump -u "${DB_USERNAME:-hmdp}" -p"${DB_PASSWORD}" --single-transaction --no-tablespaces --quick hmdp | gzip > "$FILE"
# 清理 7 天前备份
find "$BACKUP_DIR" -name "hmdp_*.sql.gz" -mtime +7 -delete
echo "$(date '+%F %T') backup done: $FILE ($(du -h "$FILE" | cut -f1))"
