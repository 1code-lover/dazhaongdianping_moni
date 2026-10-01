#!/usr/bin/env bash
# ============================================================================
# hmdp 后端一键启动脚本（服务器部署用）
# 自动加载项目根目录 .env 文件中的环境变量后启动后端
# 用法：bash scripts/start-server.sh   （或先 nohup bash scripts/start-server.sh &）
# ============================================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${PROJECT_DIR}"

# 使用新版 JDK 8（旧 8u40 证书链过老，无法连通 SMTP/HTTPS）
# 可用 HMDP_JAVA_HOME 环境变量覆盖
export JAVA_HOME="${HMDP_JAVA_HOME:-/usr/local/java/jdk8u504-b01}"
export PATH="${JAVA_HOME}/bin:${PATH}"

# 加载 .env（若存在），导出为环境变量供 Spring 占位符读取
if [[ -f "${PROJECT_DIR}/.env" ]]; then
  echo "Loading .env ..."
  set -a
  # shellcheck disable=SC1091
  source "${PROJECT_DIR}/.env"
  set +a
else
  echo "Warn: .env not found, starting with default config (channel=log)."
fi

echo "Starting hmdp backend (channel=${VERIFY_CODE_CHANNEL:-log}) ..."
exec java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
