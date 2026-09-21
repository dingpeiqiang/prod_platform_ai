#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - 后端部署更新脚本（tar 包内 prod-ai-backend/bin/deployup.sh）
# 用法:  bash deployup.sh
# 职责：从 ${APP_HOME}/installer 取 prod-ai-backend.tar.gz，解压后替换本包 app.jar。
#       保留现有 config/application.yml、logs/、data/、uploads/ 不动。
# 部署布局（按 crm-pgcent-mng 约定）：
#   <根目录>/crm-pgcent-mng/prod-ai-backend   后端（本包解压根）
#   <根目录>/crm-pgcent-mng/prod-ai-frontend  前端
#   <根目录>/crm-pgcent-mng/installer         发布包存放目录
#   其中 APP_HOME = <根目录>/crm-pgcent-mng
# ============================================================
set -euo pipefail

# ---------- 部署根目录（可配置） ----------
# APP_HOME = <根目录>/crm-pgcent-mng，其下含 prod-ai-backend / prod-ai-frontend / installer
# 默认推导：APP_HOME = 本包目录(prod-ai-backend) 的上一级
# 如需自定义，可在此填写完整 APP_HOME，或导出环境变量 APP_HOME
APP_HOME="${APP_HOME:-}"

# ---------- 发布包目录与包名（可配置） ----------
# installer 目录，默认 ${APP_HOME}/installer；可在此填写绝对路径或导出环境变量
INSTALLER_DIR="${INSTALLER_DIR:-}"
# 待安装的发布包文件名
PKG_NAME="${PKG_NAME:-prod-ai-backend.tar.gz}"

# ---------- 路径解析 ----------
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "${SELF_DIR}/.." && pwd)"          # 本包解压根（prod-ai-backend）
# 未显式指定 APP_HOME 时，推导为包目录(prod-ai-backend)的父目录（即 crm-pgcent-mng）
APP_HOME="${APP_HOME:-$(cd "${APP_DIR}/.." && pwd)}"
INSTALLER_DIR="${INSTALLER_DIR:-${APP_HOME}/installer}"
PKG_PATH="${INSTALLER_DIR}/${PKG_NAME}"

JAR_DST="${APP_DIR}/app.jar"

# ---------- 前置校验 ----------
if [ ! -f "${PKG_PATH}" ]; then
    echo "[ERROR] 未找到发布包: ${PKG_PATH}" >&2
    echo "        请先将 ${PKG_NAME} 放到 ${INSTALLER_DIR}/ 目录。" >&2
    exit 1
fi

# ---------- 解压到临时目录 ----------
TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "${TMP_DIR}"; }
trap cleanup EXIT

echo "[INFO] 发布包: ${PKG_PATH}"
echo "[INFO] 解压到临时目录: ${TMP_DIR}"
tar -xzf "${PKG_PATH}" -C "${TMP_DIR}"

# 兼容以下两种打包结构：
#   a) 包根直接是 app.jar（当前 build-tar.ps1 产出）
#   b) 包根为 prod-ai-backend/app.jar
JAR_SRC="${TMP_DIR}/app.jar"
if [ ! -f "${JAR_SRC}" ] && [ -f "${TMP_DIR}/prod-ai-backend/app.jar" ]; then
    JAR_SRC="${TMP_DIR}/prod-ai-backend/app.jar"
fi
if [ ! -f "${JAR_SRC}" ]; then
    echo "[ERROR] 发布包内未找到 app.jar（已尝试包根与 prod-ai-backend/）" >&2
    exit 1
fi

# ---------- 备份并替换 jar ----------
mkdir -p "${APP_DIR}"

# 若后端由本包 start.sh 启动且仍在运行，覆盖 jar 前先提示并停止，避免运行中进程读到损坏的 jar
PID_FILE="${APP_DIR}/app.pid"
if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
    echo "[WARN] 检测到后端正在运行: PID $(cat "${PID_FILE}")"
    echo "       为避免覆盖运行中的 jar，先停止后端 ..."
    bash "${SELF_DIR}/start.sh" stop || true
fi

if [ -f "${JAR_DST}" ]; then
    JAR_BAK="${JAR_DST}.bak.$(date +%Y%m%d%H%M%S)"
    cp -p "${JAR_DST}" "${JAR_BAK}"
    echo "[BACKUP] 原 jar 已备份 → ${JAR_BAK}"
    # 先删除再拷贝，生成新 inode，避免覆盖仍被占用的旧文件
    rm -f "${JAR_DST}"
fi

echo "[DEPLOY] 替换 app.jar → ${JAR_DST}"
cp -f "${JAR_SRC}" "${JAR_DST}"

echo "[OK] 后端 jar 更新完成。"
echo "     配置/数据目录保持不变: config/application.yml、logs/、data/、uploads/"
echo "     重启后端生效: bash bin/start.sh start  （或 systemctl restart prod-ai-backend）"
