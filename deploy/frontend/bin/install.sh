#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - 前端安装/启停脚本（tar 包内 bin/）
# 部署目录结构:
#   /opt/prod-ai/frontend/dist    前端静态产物（html）
#   /etc/nginx/conf.d/prod-ai.conf Nginx 站点配置
# 用法: bash install.sh {install|start|stop|reload|status}
# ============================================================
set -euo pipefail

# ---------- 路径常量 ----------
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_DIR="$(cd "${SELF_DIR}/.." && pwd)"          # tar 解压根（含 html/、conf/）
HTML_SRC="${PKG_DIR}/html"
CONF_SRC="${PKG_DIR}/conf/prod-ai.conf"

HTTP_ROOT="/opt/prod-ai/frontend/dist"
NGINX_CONF="/etc/nginx/conf.d/prod-ai.conf"
PORT="${NGINX_PORT:-80}"

require_root() {
    if [ "$(id -u)" -ne 0 ]; then
        echo "[ERROR] 该操作需要 root 权限（写入 ${HTTP_ROOT} / ${NGINX_CONF}）" >&2
        exit 1
    fi
}

install_static() {
    require_root
    if [ ! -d "${HTML_SRC}" ] || [ ! -f "${HTML_SRC}/index.html" ]; then
        echo "[ERROR] 未找到前端静态产物: ${HTML_SRC}/index.html" >&2
        exit 1
    fi
    mkdir -p "${HTTP_ROOT}"
    echo "[INSTALL] 覆盖安装静态资源 → ${HTTP_ROOT}"
    cp -r "${HTML_SRC}/." "${HTTP_ROOT}/"
    chown -R root:root "${HTTP_ROOT}"

    if [ -f "${CONF_SRC}" ]; then
        echo "[INSTALL] 安装 Nginx 站点配置 → ${NGINX_CONF}"
        cp -f "${CONF_SRC}" "${NGINX_CONF}"
        chown root:root "${NGINX_CONF}"
    fi
    reload
}

start() {
    require_root
    if ! command -v nginx >/dev/null 2>&1; then
        echo "[ERROR] 未安装 nginx，请先 yum/apt 安装" >&2
        exit 1
    fi
    if systemctl list-unit-files 2>/dev/null | grep -q '^nginx.service'; then
        systemctl enable --now nginx
    else
        # 无 systemd 时用 nginx 自带管理
        nginx -t && nginx
    fi
    echo "[OK] Nginx 已启动，监听端口 ${PORT}"
}

stop() {
    require_root
    if systemctl list-unit-files 2>/dev/null | grep -q '^nginx.service'; then
        systemctl stop nginx
    elif [ -s /run/nginx.pid ] || pgrep -x nginx >/dev/null 2>&1; then
        nginx -s stop || true
    else
        echo "[INFO] Nginx 未运行"
    fi
    echo "[OK] Nginx 已停止"
}

reload() {
    require_root
    if nginx -t 2>/dev/null; then
        if systemctl list-unit-files 2>/dev/null | grep -q '^nginx.service'; then
            systemctl reload nginx || systemctl restart nginx
        else
            nginx -s reload || nginx -t && nginx
        fi
        echo "[OK] Nginx 已重载配置"
    else
        echo "[ERROR] nginx -t 失败，请检查 ${NGINX_CONF}" >&2
        exit 1
    fi
}

status() {
    if systemctl list-unit-files 2>/dev/null | grep -q '^nginx.service'; then
        systemctl status nginx --no-pager | head -n 5 || true
    fi
    if curl -fsS "http://127.0.0.1:${PORT}/" >/dev/null 2>&1; then
        echo "[OK] 前端首页可访问 (端口 ${PORT})"
    else
        echo "[WARN] 前端首页未响应 (端口 ${PORT})"
    fi
}

case "${1:-start}" in
    install)  install_static ;;
    start)    start ;;
    stop)     stop ;;
    reload)   reload ;;
    restart)  stop; start ;;
    status)   status ;;
    *)
        echo "用法: bash $0 {install|start|stop|reload|restart|status}" >&2
        exit 1
        ;;
esac
