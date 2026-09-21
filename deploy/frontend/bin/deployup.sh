#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - 前端部署更新脚本（tar 包内 prod-ai-frontend/bin/deployup.sh）
# 用法:  bash deployup.sh
# 职责：从 ${APP_HOME}/installer 取 prod-ai-frontend.tar.gz，解压后用包内 dist/
#       替换前端静态资源 ${APP_HOME}/prod-ai-frontend/dist。不重装 Nginx 站点配置。
# 部署布局（本次部署实际路径）：
#   APP_HOME     = /data/stq/crmpos/crm-pgcent-mng
#   前端包根     = ${APP_HOME}/prod-ai-frontend（本脚本所在包，静态根 dist/）
#   发布包目录   = ${APP_HOME}/installer
# ============================================================
set -euo pipefail

# ---------- 部署根目录（可配置） ----------
# APP_HOME = /data/stq/crmpos/crm-pgcent-mng，其下含 prod-ai-backend / prod-ai-frontend / installer
# 默认推导：APP_HOME = 本包目录(prod-ai-frontend) 的上一级
# 如需自定义，可在此填写完整 APP_HOME，或导出环境变量 APP_HOME
APP_HOME="${APP_HOME:-}"

# ---------- 发布包目录与包名（可配置） ----------
# installer 目录，默认 ${APP_HOME}/installer；可在此填写绝对路径或导出环境变量
INSTALLER_DIR="${INSTALLER_DIR:-}"
# 待安装的发布包文件名
PKG_NAME="${PKG_NAME:-prod-ai-frontend.tar.gz}"

# ---------- 路径解析 ----------
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_DIR="$(cd "${SELF_DIR}/.." && pwd)"          # 本包解压根（prod-ai-frontend）
# 未显式指定 APP_HOME 时，推导为包目录(prod-ai-frontend)的父目录（即 crm-pgcent-mng）
APP_HOME="${APP_HOME:-$(cd "${PKG_DIR}/.." && pwd)}"
INSTALLER_DIR="${INSTALLER_DIR:-${APP_HOME}/installer}"
PKG_PATH="${INSTALLER_DIR}/${PKG_NAME}"

HTTP_ROOT="${APP_HOME}/prod-ai-frontend/dist"

# 权限自检：对静态根父目录具备写权限即可（非 root 部署用户亦可）
check_permission() {
    local _parent
    _parent="$(dirname "${HTTP_ROOT}")"
    if [ ! -w "${_parent}" ]; then
        echo "[ERROR] 当前用户对 ${_parent} 无写权限，无法铺设/替换静态资源。" >&2
        echo "        处理办法：以属主用户运行，或以 root 执行。" >&2
        exit 1
    fi
}

# ---------- 前置校验 ----------
if [ ! -f "${PKG_PATH}" ]; then
    echo "[ERROR] 未找到发布包: ${PKG_PATH}" >&2
    echo "        请先将 ${PKG_NAME} 放到 ${INSTALLER_DIR}/ 目录。" >&2
    exit 1
fi

check_permission

# ---------- 解压到临时目录 ----------
TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "${TMP_DIR}"; }
trap cleanup EXIT

echo "[INFO] 发布包: ${PKG_PATH}"
echo "[INFO] 解压到临时目录: ${TMP_DIR}"
tar -xzf "${PKG_PATH}" -C "${TMP_DIR}"

# 兼容以下两种打包结构：
#   a) 包根含 dist/（当前 build-tar.ps1 产出）
#   b) 包根为 prod-ai-frontend/dist/
#   c) 旧包结构：包根含 html/（历史版本，向下兼容）
DIST_SRC="${TMP_DIR}/dist"
if [ ! -d "${DIST_SRC}" ] && [ -d "${TMP_DIR}/prod-ai-frontend/dist" ]; then
    DIST_SRC="${TMP_DIR}/prod-ai-frontend/dist"
fi
if [ ! -d "${DIST_SRC}" ]; then
    # 旧包兼容：html/ 视为 dist/
    if [ -d "${TMP_DIR}/html" ]; then
        DIST_SRC="${TMP_DIR}/html"
    elif [ -d "${TMP_DIR}/prod-ai-frontend/html" ]; then
        DIST_SRC="${TMP_DIR}/prod-ai-frontend/html"
    fi
fi
if [ ! -d "${DIST_SRC}" ] || [ ! -f "${DIST_SRC}/index.html" ]; then
    echo "[ERROR] 发布包内未找到前端静态产物 dist/index.html" >&2
    exit 1
fi

# ---------- 备份并替换静态资源 ----------
# 采用「先铺新目录 → 原子替换」方式，避免清空 dist/ 期间出现站点 404 窗口
PARENT_DIR="$(cd "$(dirname "${HTTP_ROOT}")" && pwd)"
FINAL_DIR="${HTTP_ROOT}"
NEW_DIR="${PARENT_DIR}/dist.new.$(date +%Y%m%d%H%M%S)"

echo "[DEPLOY] 铺设新静态资源 → ${NEW_DIR}"
mkdir -p "${NEW_DIR}"
cp -r "${DIST_SRC}/." "${NEW_DIR}/"
# 属主按运行用户设置（root 运行保持 root）；保证其它用户（nginx worker）可读
chown -R "$(id -u):$(id -g)" "${NEW_DIR}" 2>/dev/null || true
chmod -R u=rwX,go=rX "${NEW_DIR}"

if [ -e "${FINAL_DIR}" ]; then
    DIST_BAK="${FINAL_DIR}.bak.$(date +%Y%m%d%H%M%S)"
    echo "[BACKUP] 原静态资源备份 → ${DIST_BAK}"
    mv "${FINAL_DIR}" "${DIST_BAK}"
fi

echo "[DEPLOY] 原子切换到 → ${FINAL_DIR}"
mv "${NEW_DIR}" "${FINAL_DIR}"

echo "[OK] 前端静态资源更新完成。"
echo "     Nginx 站点配置由 bin/start.sh 自动加载包内 conf/prod-ai.conf 并修正 root 路径，"
echo "     无需再手动拷贝到 /etc/nginx/conf.d。"
echo "     如需生效: bash bin/stop.sh && bash bin/start.sh"
