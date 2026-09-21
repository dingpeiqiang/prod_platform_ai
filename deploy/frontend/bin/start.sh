#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - 前端 Nginx 启动脚本（tar 包内 prod-ai-frontend/bin/start.sh）
# 用法:  bash start.sh
# 部署布局（按 crm-pgcent-mng 约定）：
#   <根目录>/crm-pgcent-mng/prod-ai-backend   后端
#   <根目录>/crm-pgcent-mng/prod-ai-frontend  前端（本包解压根，含 dist/bin/conf/html）
#   其中 APP_HOME = <根目录>/crm-pgcent-mng
# 站点配置见 conf/prod-ai.conf（静态文件，部署时拷贝到 Nginx 配置目录）
# Nginx：可在下方 NGINX_HOME 处指定 Nginx 安装前缀（留空则用系统默认）
# ============================================================
set -euo pipefail

# ---------- Nginx 配置（可配置） ----------
# Nginx 安装前缀（到安装根目录，非 sbin），如 /usr/local/nginx 或 /opt/nginx。
# 留空则使用系统默认：从 PATH 查找 nginx 可执行文件，配置目录取 /etc/nginx。
NGINX_HOME="${NGINX_HOME:-}"

# ---------- 部署根目录（可配置） ----------
# APP_HOME = <根目录>/crm-pgcent-mng，其下含 prod-ai-backend / prod-ai-frontend
# 默认推导：APP_HOME = 本包目录(prod-ai-frontend) 的上一级
# 如需自定义，可在此填写完整 APP_HOME，或导出环境变量 APP_HOME
APP_HOME="${APP_HOME:-}"

# ---------- 路径常量 ----------
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_DIR="$(cd "${SELF_DIR}/.." && pwd)"          # tar 解压根（prod-ai-frontend，含 dist/、conf/、html/）
# 未显式指定 APP_HOME 时，推导为包目录(prod-ai-frontend)的父目录（即 crm-pgcent-mng）
APP_HOME="${APP_HOME:-$(cd "${PKG_DIR}/.." && pwd)}"
HTTP_ROOT="${APP_HOME}/prod-ai-frontend/dist"

# ---------- Nginx 可执行文件 / 配置目录 / PID 解析 ----------
# 指定 NGINX_HOME 时按其安装前缀推导；否则回退系统默认
if [ -n "${NGINX_HOME}" ]; then
    NGINX_BIN="${NGINX_HOME}/sbin/nginx"
    NGINX_CONF_DIR="${NGINX_HOME}/conf"
else
    NGINX_BIN="$(command -v nginx 2>/dev/null || true)"
    NGINX_CONF_DIR="/etc/nginx"
fi
NGINX_BIN="${NGINX_BIN:-nginx}"
NGINX_SITE_CONF="${NGINX_CONF_DIR}/conf.d/prod-ai.conf"
PORT="${NGINX_PORT:-80}"

# 解析站点实际监听端口（未显式指定 NGINX_PORT 时，从已安装的站点配置中读取）
if [ -z "${NGINX_PORT:-}" ] && [ -f "${NGINX_SITE_CONF}" ]; then
    CONF_PORT="$(sed -nE 's/^[[:space:]]*listen[[:space:]]+([0-9]+).*/\1/p' "${NGINX_SITE_CONF}" 2>/dev/null | head -n1 || true)"
    PORT="${CONF_PORT:-${PORT}}"
fi

require_root() {
    if [ "$(id -u)" -ne 0 ]; then
        echo "[ERROR] 该操作需要 root 权限（启动 Nginx 并读取 ${HTTP_ROOT}）" >&2
        exit 1
    fi
}

if ! [ -x "${NGINX_BIN}" ] && ! command -v "${NGINX_BIN}" >/dev/null 2>&1; then
    echo "[ERROR] 未找到 nginx 可执行文件: ${NGINX_BIN}" >&2
    echo "        请安装 Nginx，或在 start.sh 中配置 NGINX_HOME 指向 Nginx 安装前缀。" >&2
    exit 1
fi

require_root

if [ ! -f "${NGINX_SITE_CONF}" ]; then
    echo "[WARN] 未找到站点配置 ${NGINX_SITE_CONF}，请先拷贝 conf/prod-ai.conf 到该路径" >&2
fi
if [ ! -f "${HTTP_ROOT}/index.html" ]; then
    echo "[WARN] 未找到前端静态产物 ${HTTP_ROOT}/index.html，请先执行 bin/deployup.sh 或放置 dist/" >&2
fi

echo "[INFO] Nginx: ${NGINX_BIN}"
echo "[INFO] 配置目录: ${NGINX_CONF_DIR}  站点: ${NGINX_SITE_CONF}"

# 已有进程则视为已启动
if pgrep -f "${NGINX_BIN}" >/dev/null 2>&1; then
    echo "[WARN] Nginx 已在运行，跳过启动"
    exit 0
fi

echo "[START] $(date '+%F %T') 启动 Nginx ..."
if ! "${NGINX_BIN}" -t; then
    echo "[ERROR] nginx -t 校验失败，请检查 ${NGINX_SITE_CONF}" >&2
    exit 1
fi
"${NGINX_BIN}"
sleep 1
if pgrep -f "${NGINX_BIN}" >/dev/null 2>&1; then
    echo "[OK] Nginx 已启动，监听端口 ${PORT}，静态根 ${HTTP_ROOT}"
else
    echo "[ERROR] Nginx 启动失败，请查看 error.log" >&2
    exit 1
fi
