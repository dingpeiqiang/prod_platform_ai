#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - 前端 Nginx 停止脚本（tar 包内 prod-ai-frontend/bin/stop.sh）
# 用法:  bash stop.sh
# 部署布局（按 crm-pgcent-mng 约定）：
#   <根目录>/crm-pgcent-mng/prod-ai-backend   后端
#   <根目录>/crm-pgcent-mng/prod-ai-frontend  前端（本包解压根）
#   其中 APP_HOME = <根目录>/crm-pgcent-mng
# Nginx：可在下方 NGINX_HOME 处指定 Nginx 安装前缀（留空则用系统默认）
# ============================================================
set -euo pipefail

# ---------- Nginx 配置（可配置） ----------
# Nginx 安装前缀（到安装根目录，非 sbin），如 /usr/local/nginx 或 /opt/nginx。
# 留空则使用系统默认：从 PATH 查找 nginx 可执行文件，配置目录取 /etc/nginx。
NGINX_HOME="${NGINX_HOME:-}"

# ---------- Nginx 可执行文件 / 配置目录解析 ----------
if [ -n "${NGINX_HOME}" ]; then
    NGINX_BIN="${NGINX_HOME}/sbin/nginx"
    NGINX_CONF_DIR="${NGINX_HOME}/conf"
else
    NGINX_BIN="$(command -v nginx 2>/dev/null || true)"
    NGINX_CONF_DIR="/etc/nginx"
fi
NGINX_BIN="${NGINX_BIN:-nginx}"
NGINX_SITE_CONF="${NGINX_CONF_DIR}/conf.d/prod-ai.conf"

require_root() {
    if [ "$(id -u)" -ne 0 ]; then
        echo "[ERROR] 该操作需要 root 权限（停止 Nginx）" >&2
        exit 1
    fi
}

if ! [ -x "${NGINX_BIN}" ] && ! command -v "${NGINX_BIN}" >/dev/null 2>&1; then
    echo "[ERROR] 未找到 nginx 可执行文件: ${NGINX_BIN}" >&2
    echo "        请安装 Nginx，或在 stop.sh 中配置 NGINX_HOME 指向 Nginx 安装前缀。" >&2
    exit 1
fi

require_root

if ! pgrep -f "${NGINX_BIN}" >/dev/null 2>&1; then
    echo "[INFO] Nginx 未运行"
    exit 0
fi

echo "[STOP] $(date '+%F %T') 停止 Nginx ..."
if ! "${NGINX_BIN}" -s quit; then
    echo "[WARN] nginx -s quit 失败，尝试 nginx -s stop"
    "${NGINX_BIN}" -s stop || true
fi

for _ in $(seq 1 30); do
    pgrep -f "${NGINX_BIN}" >/dev/null 2>&1 || break
    sleep 1
done

if pgrep -f "${NGINX_BIN}" >/dev/null 2>&1; then
    echo "[WARN] Nginx 仍未退出，强制终止"
    pkill -f "${NGINX_BIN}" || true
    sleep 1
fi

if pgrep -f "${NGINX_BIN}" >/dev/null 2>&1; then
    echo "[ERROR] Nginx 停止失败" >&2
    exit 1
fi
echo "[OK] Nginx 已停止"
