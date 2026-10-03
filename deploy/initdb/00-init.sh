#!/bin/bash
# ============================================================================
# MySQL 容器首次初始化：按依赖顺序导入全部库脚本 + 创建业务账号
# 由 mysql 镜像的 /docker-entrypoint-initdb.d 机制自动执行（仅首次建库时）
# ============================================================================
set -e

DB_DIR=/docker-entrypoint-initdb.d/sql
MYSQL="mysql -uroot -p${MYSQL_ROOT_PASSWORD}"

echo "[init] 导入库结构脚本（按依赖顺序）..."
$MYSQL <<'EOSQL'
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS hmdp DEFAULT CHARACTER SET utf8mb4;
EOSQL

for f in \
  hmdp.sql \
  chat_tables.sql \
  combo_order_tables.sql \
  review_tables.sql \
  shop_apply_tables.sql \
  voucher_order_fail_task.sql \
  voucher_order_unique_index.sql \
  user_email_column.sql \
  chat_faq_expand.sql \
  seed_demo_data.sql ; do
  if [ -f "$DB_DIR/$f" ]; then
    echo "[init]   -> $f"
    $MYSQL hmdp < "$DB_DIR/$f"
  else
    echo "[init]   !! 缺少 $f（跳过）"
  fi
done

# 老库迁移脚本：新库建表已含 failure_type 列，重复执行报错属预期，容错跳过
if [ -f "$DB_DIR/voucher_order_fail_task_failure_type.sql" ]; then
  echo "[init]   -> voucher_order_fail_task_failure_type.sql（容错模式）"
  $MYSQL hmdp < "$DB_DIR/voucher_order_fail_task_failure_type.sql" || echo "[init]     列已存在，跳过"
fi

echo "[init] 创建业务账号 hmdp..."
$MYSQL <<EOSQL
CREATE USER IF NOT EXISTS 'hmdp'@'%' IDENTIFIED WITH mysql_native_password BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON hmdp.* TO 'hmdp'@'%';
FLUSH PRIVILEGES;
EOSQL
echo "[init] 完成。"
