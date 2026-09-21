#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - 前端 Nginx 启动脚本（tar 包内 prod-ai-frontend/bin/start.sh）
# 用法:  bash start.sh
# 部署布局（本次部署实际路径）：
#   APP_HOME     = /data/stq/crmpos/crm-pgcent-mng
#   前端包根     = ${APP_HOME}/prod-ai-frontend（本脚本所在包，含 dist/bin/conf）
#   NGINX_PREFIX = /data/stq/crmpos/nginx
# 站点配置：默认加载本包 conf/prod-ai.conf（完整 Nginx 主配置），
#   启动方式:  nginx -p <prefix> -c <PKG_DIR>/conf/prod-ai.conf
#   启动前仅按其实际 HTTP_ROOT 修正配置中的 root 根目录，其余保持模板默认。
# Nginx：可在下方 NGINX_HOME 处指定 Nginx 安装前缀（留空则用系统默认）。
# ============================================================
set -euo pipefail

# ---------- Nginx 配置（可配置） ----------
# Nginx 安装前缀（到安装根目录，非 sbin）。本次部署默认: /data/stq/crmpos/nginx
# 如系统默认安装（/etc/nginx）可改为留空。
NGINX_HOME="${NGINX_HOME:-/data/stq/crmpos/nginx}"

# ---------- 部署根目录（可配置） ----------
# APP_HOME = <根目录>/crm-pgcent-mng，其下含 prod-ai-backend / prod-ai-frontend
# 默认推导：APP_HOME = 本包目录(prod-ai-frontend) 的上一级
# 如需自定义，可在此填写完整 APP_HOME，或导出环境变量 APP_HOME
APP_HOME="${APP_HOME:-}"

# ---------- 路径常量 ----------
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_DIR="$(cd "${SELF_DIR}/.." && pwd)"          # tar 解压根（prod-ai-frontend，含 dist/、conf/、bin/）
# 未显式指定 APP_HOME 时，推导为包目录(prod-ai-frontend)的父目录（即 crm-pgcent-mng）
APP_HOME="${APP_HOME:-$(cd "${PKG_DIR}/.." && pwd)}"
HTTP_ROOT="${APP_HOME}/prod-ai-frontend/dist"

# 本包主配置（完整 nginx.conf），默认加载该文件
SITE_CONF="${PKG_DIR}/conf/prod-ai.conf"
# -p 前缀指向包 conf 目录：mime.types 随包放在此处，include mime.types 直接命中
NGINX_CONF_DIR="${PKG_DIR}/conf"
# 标注由本脚本自动修正，用于避免覆盖用户手工改动
SITE_CONF_MARKER="# managed by prod-ai start.sh (auto-tuned)"

# ---------- Nginx 可执行文件（可配置 NGINX_HOME 指向安装前缀） ----------
if [ -n "${NGINX_HOME}" ]; then
    NGINX_BIN="${NGINX_HOME}/sbin/nginx"
else
    NGINX_BIN="$(command -v nginx 2>/dev/null || true)"
fi
NGINX_BIN="${NGINX_BIN:-nginx}"

# 日志/pid 目录（prod-ai.conf 内为绝对路径，与此保持一致）
NGINX_LOG_DIR="/data/stq/crmpos/nginx/logs"

# 校验 PID 是否确为本实例 Nginx（cmdline 含本主配置路径 -c <SITE_CONF>）
is_nginx_instance() {
    local _pid="$1"
    if ! kill -0 "${_pid}" 2>/dev/null; then
        return 1
    fi
    [ -r "/proc/${_pid}/cmdline" ] || return 1
    grep -aq "${SITE_CONF}" "/proc/${_pid}/cmdline" 2>/dev/null
}

# ---------- 站点配置加载（默认加载包内 conf / 仅修正根目录） ----------
# 说明：直接修改并加载 conf/prod-ai.conf 本体，实现“加载 conf 下的文件”。
# 启动前仅用 sed 修正其中的 root 静态根目录（按实际 HTTP_ROOT）；
# 其余（upstream、监听端口等）保持模板默认，无需手改。
prepare_site_conf() {
    if ! [ -f "${SITE_CONF}" ]; then
        echo "[ERROR] 未找到主配置 ${SITE_CONF}" >&2
        exit 1
    fi
    if ! [ -f "${NGINX_CONF_DIR}/mime.types" ]; then
        echo "[ERROR] 未找到 ${NGINX_CONF_DIR}/mime.types，请确认部署包完整。" >&2
        exit 1
    fi
    # 首次：若非本脚本管理（无标注），默认接管并注入标注
    if ! grep -qF "${SITE_CONF_MARKER}" "${SITE_CONF}" 2>/dev/null; then
        printf '%s\n' "${SITE_CONF_MARKER}" | cat - "${SITE_CONF}" > "${SITE_CONF}.tmp"
        mv -f "${SITE_CONF}.tmp" "${SITE_CONF}"
    fi
    # 修正 root 静态目录（其余配置保持模板默认）
    sed -i -E "s|(^[[:space:]]*root[[:space:]]+)[^;]+;|\\1${HTTP_ROOT};|" "${SITE_CONF}"
    chmod 644 "${SITE_CONF}"
}

# ---------- 端口解析（用于展示） ----------
# 从主配置读取实际监听端口（未显式指定 NGINX_PORT 时）
NGINX_PORT="${NGINX_PORT:-}"
if [ -z "${NGINX_PORT}" ]; then
    NGINX_PORT="$(sed -nE 's/^[[:space:]]*listen[[:space:]]+([0-9]+).*/\1/p' "${SITE_CONF}" 2>/dev/null | head -n1 || true)"
fi
PORT="${NGINX_PORT:-80}"

# 权限自检：仅当确实需要 root 时才阻断（普通用户部署到自有写权限前缀、监听高端口时无需 root）
check_permission() {
    local _log_dir="${NGINX_LOG_DIR}"
    # 监听端口 <1024 需 root（Linux 特权端口）
    local _need_root=0
    if [ "${PORT}" -lt 1024 ] 2>/dev/null; then
        _need_root=1
    fi
    # 日志目录不可写也需更高权限
    if ! [ -w "${_log_dir}" ]; then
        _need_root=1
    fi
    # 已具备写权限且端口可绑定则无需 root（只要不是 root 且确有需要才报错）
    if [ "${_need_root}" -eq 1 ] && [ "$(id -u)" -ne 0 ]; then
        echo "[ERROR] 当前非 root，但需要写日志目录 ${_log_dir} 或监听端口 ${PORT}（<1024）。" >&2
        echo "        处理办法：以 root 运行；或将 Nginx prefix 设为当前用户可写目录，并让监听端口 ≥1024。" >&2
        exit 1
    fi
}

if ! [ -x "${NGINX_BIN}" ] && ! command -v "${NGINX_BIN}" >/dev/null 2>&1; then
    echo "[ERROR] 未找到 nginx 可执行文件: ${NGINX_BIN}" >&2
    echo "        请安装 Nginx，或在 start.sh 中配置 NGINX_HOME 指向 Nginx 安装前缀。" >&2
    exit 1
fi

# 确保日志目录存在（prod-ai.conf 中 error_log/access_log/pid 均为绝对路径）
mkdir -p "${NGINX_LOG_DIR}"

check_permission

prepare_site_conf

if [ ! -f "${HTTP_ROOT}/index.html" ]; then
    echo "[WARN] 未找到前端静态产物 ${HTTP_ROOT}/index.html，请先执行 bin/deployup.sh 或放置 dist/" >&2
fi

echo "[INFO] Nginx: ${NGINX_BIN}"
echo "[INFO] 加载主配置: ${SITE_CONF}（-p ${NGINX_CONF_DIR}）"
echo "[INFO] 静态根: ${HTTP_ROOT}"

# 已运行则视为已启动（依据 pid 文件，prod-ai.conf 中为绝对路径）
PID_FILE="$(sed -nE 's/^[[:space:]]*pid[[:space:]]+([^;]+);.*/\1/p' "${SITE_CONF}" 2>/dev/null | head -n1 || true)"
if [ -n "${PID_FILE}" ]; then
    PID_PATH="${PID_FILE}"
    if [ -f "${PID_PATH}" ] && is_nginx_instance "$(cat "${PID_PATH}" 2>/dev/null)"; then
        echo "[WARN] Nginx 已在运行（pid $(cat "${PID_PATH}")），跳过启动"
        exit 0
    fi
fi

echo "[START] $(date '+%F %T') 启动 Nginx ..."
if ! "${NGINX_BIN}" -t -p "${NGINX_CONF_DIR}" -c "${SITE_CONF}"; then
    echo "[ERROR] nginx -t 校验失败，请检查 ${SITE_CONF}" >&2
    exit 1
fi
"${NGINX_BIN}" -p "${NGINX_CONF_DIR}" -c "${SITE_CONF}"
sleep 1

if [ -n "${PID_FILE}" ] && [ -f "${PID_PATH}" ] && is_nginx_instance "$(cat "${PID_PATH}" 2>/dev/null)"; then
    echo "[OK] Nginx 已启动（pid $(cat "${PID_PATH}")），监听端口 ${PORT}，静态根 ${HTTP_ROOT}"
elif pgrep -f "${NGINX_BIN}.*${SITE_CONF}" >/dev/null 2>&1; then
    echo "[OK] Nginx 已启动，监听端口 ${PORT}，静态根 ${HTTP_ROOT}"
else
    echo "[ERROR] Nginx 启动失败，请查看 ${NGINX_LOG_DIR}/error.log" >&2
    exit 1
fi
