#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - 后端启动脚本（tar 包内 prod-ai-backend/bin/start.sh）
# 用法:  bash start.sh [start|stop|restart|status]
# 部署布局（按 crm-pgcent-mng 约定）：
#   <根目录>/crm-pgcent-mng/prod-ai-backend   后端（本包解压根，含 app.jar/config/logs/data）
#   <根目录>/crm-pgcent-mng/prod-ai-frontend  前端
#   其中 APP_HOME = <根目录>/crm-pgcent-mng
# 业务配置见 config/application.yml（随包自带，部署前修改库地址/账号密码/JWT）
# JDK：必须为 17。可在下方 JAVA_HOME 处指定 JDK 安装路径（留空则自动探测）
# ============================================================
set -euo pipefail

# ---------- JDK 配置 ----------
# 需 JDK 17。若系统自带 java 不是 17 或存在多版本，
# 在此填写 JDK17 安装路径（到 JDK 根目录，非 bin），如 /usr/lib/jvm/java-17-openjdk
JAVA_HOME="${JAVA_HOME:-}"
# 强制要求的 JDK 主版本
REQUIRED_JAVA_MAJOR=17

# ---------- 部署根目录（可配置） ----------
# APP_HOME = <根目录>/crm-pgcent-mng，其下含 prod-ai-backend / prod-ai-frontend
# 默认推导：APP_HOME = 本包目录(prod-ai-backend) 的上一级
# 如需自定义，可在此填写完整 APP_HOME，或导出环境变量 APP_HOME
APP_HOME="${APP_HOME:-}"

# ---------- 路径与常量 ----------
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 未显式指定 APP_HOME 时，推导为包目录(prod-ai-backend)的父目录（即 crm-pgcent-mng）
APP_HOME="${APP_HOME:-$(cd "${APP_DIR}/.." && pwd)}"
JAR="$(ls -1 "${APP_DIR}"/app.jar 2>/dev/null || ls -1 "${APP_DIR}"/prod-platform-ai-*.jar 2>/dev/null | head -n1 || true)"
CONFIG_DIR="${APP_DIR}/config"
LOG_DIR="${APP_DIR}/logs"
DATA_DIR="${APP_DIR}/data"
UPLOAD_DIR="${APP_DIR}/uploads"
PORT="${SERVER_PORT:-6174}"

# ---------- systemd 单元名（若使用 systemd，脚本转调 systemctl） ----------
SYSTEMD_UNIT="prod-ai-backend.service"
# 若 PGID=1 且存在 systemd 单元与 systemctl，则委托 systemd 管理
if [ "${USE_SYSTEMD:-0}" = "1" ] && [ -f "/etc/systemd/system/${SYSTEMD_UNIT}" ] && command -v systemctl >/dev/null 2>&1; then
    case "${1:-start}" in
        start|stop|restart|status)
            systemctl "$1" "${SYSTEMD_UNIT}"
            systemctl is-active --quiet "${SYSTEMD_UNIT}" && echo "systemd 管理: ${SYSTEMD_UNIT} 已 $1"
            exit 0
            ;;
    esac
fi

# ---------- JDK 解析与版本校验（必须为 JDK17） ----------
resolve_java() {
    # 1) 优先使用显式配置的 JAVA_HOME
    if [ -n "${JAVA_HOME}" ]; then
        if [ ! -x "${JAVA_HOME}/bin/java" ]; then
            echo "[ERROR] JAVA_HOME 无效: ${JAVA_HOME}（未找到 bin/java）" >&2
            exit 1
        fi
        echo "${JAVA_HOME}/bin/java"
        return 0
    fi
    # 2) 回退 PATH 中的 java
    command -v java 2>/dev/null || true
}

JAVA_BIN="$(resolve_java)"
if [ -z "${JAVA_BIN}" ] || [ ! -x "${JAVA_BIN}" ]; then
    echo "[ERROR] 未找到 java。请安装 JDK17，或在 start.sh 中配置 JAVA_HOME。" >&2
    exit 1
fi

# 解析主版本号（兼容 java 8 的 "1.8.0_x" 与 9+ 的 "17.x"）
JAVA_VER_RAW="$("${JAVA_BIN}" -version 2>&1 | head -n1)"
JAVA_MAJOR="$(printf '%s' "${JAVA_VER_RAW}" | sed -E 's/.*version "([0-9]+)(\.([0-9]+))?.*/\1/')"
if [ "${JAVA_MAJOR}" = "1" ]; then
    # 1.8.0 -> 8
    JAVA_MAJOR="$(printf '%s' "${JAVA_VER_RAW}" | sed -E 's/.*version "1\.([0-9]+).*/\1/')"
fi
if [ "${JAVA_MAJOR}" != "${REQUIRED_JAVA_MAJOR}" ]; then
    echo "[ERROR] 需要 JDK ${REQUIRED_JAVA_MAJOR}，当前为: ${JAVA_VER_RAW}" >&2
    echo "        请在 start.sh 中配置 JAVA_HOME 指向 JDK${REQUIRED_JAVA_MAJOR} 安装路径。" >&2
    exit 1
fi
echo "[INFO] 使用 JDK${JAVA_MAJOR}: ${JAVA_BIN}"

# ---------- 目录准备 ----------
mkdir -p "${LOG_DIR}" "${DATA_DIR}" "${UPLOAD_DIR}"
JAVA_OPTS="${JAVA_OPTS:--Xms1g -Xmx3g -Dfile.encoding=UTF-8}"

PID_FILE="${APP_DIR}/app.pid"
LOG_FILE="${LOG_DIR}/app.out"

start() {
    if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
        echo "[WARN] 后端已在运行: PID $(cat "${PID_FILE}")"
        exit 0
    fi
    if [ -z "${JAR}" ] || [ ! -f "${JAR}" ]; then
        echo "[ERROR] 未找到后端 jar: ${APP_DIR}/app.jar" >&2
        exit 1
    fi
    echo "[START] $(date '+%F %T') 启动后端 (PID 转写至 ${PID_FILE})"
    nohup "${JAVA_BIN}" ${JAVA_OPTS} \
        -jar "${JAR}" \
        --server.port="${PORT}" \
        --spring.config.additional-location="optional:file:${CONFIG_DIR}/" \
        >>"${LOG_FILE}" 2>&1 &
    echo $! > "${PID_FILE}"
    echo "[START] PID $(cat "${PID_FILE}")，日志: ${LOG_FILE}"
}

stop() {
    if [ ! -f "${PID_FILE}" ]; then
        echo "[INFO] 无 PID 文件，可能未运行"
        return 0
    fi
    PID="$(cat "${PID_FILE}")"
    # 校验该 PID 确为本应用进程（防止 pid 残留且 PID 被系统复用导致误杀其它进程）
    if ! kill -0 "${PID}" 2>/dev/null; then
        echo "[INFO] PID ${PID} 已不存在，清理 pid 文件"
        rm -f "${PID_FILE}"
        return 0
    fi
    if [ -r "/proc/${PID}/cmdline" ] && grep -aq "${JAR}" "/proc/${PID}/cmdline" 2>/dev/null; then
        echo "[STOP] 停止本应用 PID ${PID} ..."
    else
        echo "[WARN] PID ${PID} 不属于本应用（cmdline 不含 ${JAR}），拒绝停止并清理 pid 文件" >&2
        rm -f "${PID_FILE}"
        return 0
    fi
    kill "${PID}" || true
    for _ in $(seq 1 30); do
        kill -0 "${PID}" 2>/dev/null || break
        sleep 1
    done
    if kill -0 "${PID}" 2>/dev/null; then
        echo "[WARN] 强制终止 PID ${PID}"
        kill -9 "${PID}" || true
    fi
    rm -f "${PID_FILE}"
}

status() {
    if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
        echo "[OK] 后端运行中: PID $(cat "${PID_FILE}")"
        curl -fsS "http://127.0.0.1:${PORT}/health" >/dev/null 2>&1 \
            && echo "[OK] 健康检查通过 (/health)" \
            || echo "[WARN] 进程存活但 /health 未响应"
        return 0
    fi
    echo "[INFO] 后端未运行"
    return 1
}

case "${1:-start}" in
    start)   start ;;
    stop)    stop ;;
    restart) stop; sleep 2; start ;;
    status)  status ;;
    *)
        echo "用法: bash $0 {start|stop|restart|status}" >&2
        exit 1
        ;;
esac
