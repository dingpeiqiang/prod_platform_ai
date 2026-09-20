#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - 后端启动脚本（tar 包内 bin/start.sh）
# 用法:  bash start.sh [start|stop|restart|status]
# 运行前请确保 /etc/prod-ai/backend.env 已配置（见 docs/虚拟机部署方案.md §6.1）
# ============================================================
set -euo pipefail

# ---------- 路径与常量 ----------
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(ls -1 "${APP_DIR}"/app.jar 2>/dev/null || ls -1 "${APP_DIR}"/prod-platform-ai-*.jar 2>/dev/null | head -n1)"
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

# ---------- 环境变量（优先读配置文件） ----------
ENV_FILE="/etc/prod-ai/backend.env"
if [ -f "${ENV_FILE}" ]; then
    # shellcheck disable=SC1090
    set -a; . "${ENV_FILE}"; set +a
fi
# 默认值兜底
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"
export AUTH_JWT_SECRET="${AUTH_JWT_SECRET:-}"
export LLM_ENABLED="${LLM_ENABLED:-true}"

# ---------- JDK 探测 ----------
JAVA_BIN="$(command -v java 2>/dev/null || true)"
if [ -z "${JAVA_BIN}" ] && [ -n "${JAVA_HOME:-}" ]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
fi
if [ -z "${JAVA_BIN}" ] || [ ! -x "${JAVA_BIN}" ]; then
    echo "[ERROR] 未找到 java。请安装 JDK17 或设置 JAVA_HOME。" >&2
    exit 1
fi

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
    if kill -0 "${PID}" 2>/dev/null; then
        echo "[STOP] 停止 PID ${PID} ..."
        kill "${PID}" || true
        for _ in $(seq 1 30); do
            kill -0 "${PID}" 2>/dev/null || break
            sleep 1
        done
        if kill -0 "${PID}" 2>/dev/null; then
            echo "[WARN] 强制终止 PID ${PID}"
            kill -9 "${PID}" || true
        fi
    else
        echo "[INFO] PID ${PID} 已不存在"
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
