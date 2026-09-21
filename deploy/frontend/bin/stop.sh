#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - 前端 Nginx 停止脚本（tar 包内 prod-ai-frontend/bin/stop.sh）
# 用法:  bash stop.sh
# 部署布局（本次部署实际路径）：
#   APP_HOME     = /data/stq/crmpos/crm-pgcent-mng
#   前端包根     = ${APP_HOME}/prod-ai-frontend（本脚本所在包）
#   NGINX_PREFIX = /data/stq/crmpos/nginx
# 与 start.sh 配套：加载包内 conf/prod-ai.conf（完整 Nginx 主配置），
#   依据其 pid 指令路径停止进程。
# Nginx：可在下方 NGINX_HOME 处指定 Nginx 安装前缀（留空则用系统默认）。
# ============================================================
set -euo pipefail

# ---------- Nginx 配置（可配置） ----------
# Nginx 安装前缀（到安装根目录，非 sbin）。本次部署默认: /data/stq/crmpos/nginx
# 如系统默认安装（/etc/nginx）可改为留空。
NGINX_HOME="${NGINX_HOME:-/data/stq/crmpos/nginx}"

# ---------- 路径常量 ----------
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_DIR="$(cd "${SELF_DIR}/.." && pwd)"          # 本包解压根（prod-ai-frontend）

# 主配置（完整 nginx.conf）
SITE_CONF="${PKG_DIR}/conf/prod-ai.conf"

# ---------- Nginx 可执行文件 ----------
if [ -n "${NGINX_HOME}" ]; then
    NGINX_BIN="${NGINX_HOME}/sbin/nginx"
else
    NGINX_BIN="$(command -v nginx 2>/dev/null || true)"
fi
NGINX_BIN="${NGINX_BIN:-nginx}"

# 权限自检：非 root 时也可停止自身启动的实例；
# 仅当目标进程归属其它用户、无法 kill 时才提示需要 root。
check_permission() {
    if [ -z "${PID_TARGET}" ]; then
        return 0
    fi
    # 若已是 root 则无需检查
    [ "$(id -u)" -eq 0 ] && return 0
    # 判断 PID 属主是否为当前用户（/proc/<pid> 无 stat 信息则视为可操作）
    local _uid="$(id -u)"
    local _owner
    _owner="$(stat -c '%u' "/proc/${PID_TARGET}" 2>/dev/null || true)"
    if [ -n "${_owner}" ] && [ "${_owner}" != "${_uid}" ]; then
        echo "[ERROR] 目标进程 PID ${PID_TARGET} 归属其它用户，当前非 root 无法停止。" >&2
        echo "        请以 root 运行，或以启动该实例的用户执行。" >&2
        exit 1
    fi
}

if ! [ -x "${NGINX_BIN}" ] && ! command -v "${NGINX_BIN}" >/dev/null 2>&1; then
    echo "[ERROR] 未找到 nginx 可执行文件: ${NGINX_BIN}" >&2
    echo "        请安装 Nginx，或在 stop.sh 中配置 NGINX_HOME 指向 Nginx 安装前缀。" >&2
    exit 1
fi

# 由主配置解析 pid 文件（prod-ai.conf 中为绝对路径；兼容相对路径时基于包 conf 目录）
PID_FILE="$(sed -nE 's/^[[:space:]]*pid[[:space:]]+([^;]+);.*/\1/p' "${SITE_CONF}" 2>/dev/null | head -n1 || true)"
PID_PATH=""
if [ -n "${PID_FILE}" ]; then
    case "${PID_FILE}" in
        /*) PID_PATH="${PID_FILE}" ;;
        *) PID_PATH="${PKG_DIR}/conf/${PID_FILE}" ;;
    esac
fi

# 校验 PID 是否确为本实例 Nginx（cmdline 含本主配置路径 -c <SITE_CONF>）
# 防止 pid 文件残留且 PID 被系统复用导致误杀其它进程
is_nginx_instance() {
    local _pid="$1"
    if ! kill -0 "${_pid}" 2>/dev/null; then
        return 1
    fi
    if [ -r "/proc/${_pid}/cmdline" ] && grep -aq "${SITE_CONF}" "/proc/${_pid}/cmdline" 2>/dev/null; then
        return 0
    fi
    return 1
}

# 目标进程：优先读 pid 文件；否则回退按启动参数匹配
PID_TARGET=""
if [ -n "${PID_PATH}" ] && [ -f "${PID_PATH}" ]; then
    PID_CAND="$(cat "${PID_PATH}" 2>/dev/null || true)"
    if [ -n "${PID_CAND}" ] && is_nginx_instance "${PID_CAND}"; then
        PID_TARGET="${PID_CAND}"
    fi
fi
if [ -z "${PID_TARGET}" ]; then
    echo "[INFO] pid 文件未找到有效进程，尝试按启动参数匹配"
    PID_TARGET="$(pgrep -f "${NGINX_BIN}.*${SITE_CONF}" 2>/dev/null | head -n1 || true)"
fi

if [ -z "${PID_TARGET}" ]; then
    echo "[INFO] Nginx 未运行"
    exit 0
fi

if ! is_nginx_instance "${PID_TARGET}"; then
    echo "[WARN] PID ${PID_TARGET} 不属于本实例（cmdline 不含 ${SITE_CONF}），拒绝停止" >&2
    [ -z "${PID_PATH}" ] || rm -f "${PID_PATH}"
    exit 1
fi

check_permission

echo "[STOP] $(date '+%F %T') 停止 Nginx（pid ${PID_TARGET}）..."
if ! kill -QUIT "${PID_TARGET}"; then
    echo "[WARN] kill -QUIT 失败，尝试 kill -TERM"
    kill -TERM "${PID_TARGET}" || true
fi

for _ in $(seq 1 30); do
    kill -0 "${PID_TARGET}" >/dev/null 2>&1 || break
    sleep 1
done

if kill -0 "${PID_TARGET}" >/dev/null 2>&1; then
    echo "[WARN] Nginx 仍未退出，强制终止"
    kill -KILL "${PID_TARGET}" || true
    sleep 1
fi

if kill -0 "${PID_TARGET}" >/dev/null 2>&1; then
    echo "[ERROR] Nginx 停止失败" >&2
    exit 1
fi
[ -z "${PID_PATH}" ] || rm -f "${PID_PATH}" || true
echo "[OK] Nginx 已停止"
