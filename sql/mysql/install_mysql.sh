#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - MySQL 建表与初始化脚本（纯 MySQL 8.0）
# 作用：全量 DDL → 扩展表 → 初始化数据（库/账号已存在，不建库）
# 用法：
#   bash install_mysql.sh [选项]
# 选项：
#   -h <host>      MySQL 地址（默认 172.30.0.232）
#   -P <port>      端口（默认 8866）
#   -a <appuser>   应用账号（默认 poc-stq）
#   -w <apppass>   应用账号密码（默认 Poc@StQ@2026）
#   -d <db>        库名（默认 poc-stq）
#   --skip-schema  跳过 01 全量 DDL
#   --skip-ext     跳过 03 扩展表
#   --skip-init    跳过 02 初始化数据
#   --dry-run      仅打印将执行的命令
# 示例：
#   bash install_mysql.sh
#   bash install_mysql.sh --dry-run
# ============================================================
set -euo pipefail

# ---------- 默认参数 ----------
DB_HOST="172.30.0.232"
DB_PORT="8866"
APP_USER="poc-stq"
APP_PASS="Poc@StQ@2026"
DB_NAME="poc-stq"
SKIP_SCHEMA=0
SKIP_EXT=0
SKIP_INIT=0
DRY_RUN=0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
}

# ---------- 参数解析 ----------
while [ $# -gt 0 ]; do
    case "$1" in
        -h) DB_HOST="$2"; shift 2 ;;
        -P) DB_PORT="$2"; shift 2 ;;
        -a) APP_USER="$2"; shift 2 ;;
        -w) APP_PASS="$2"; shift 2 ;;
        -d) DB_NAME="$2"; shift 2 ;;
        --skip-schema) SKIP_SCHEMA=1; shift ;;
        --skip-ext)    SKIP_EXT=1; shift ;;
        --skip-init)   SKIP_INIT=1; shift ;;
        --dry-run)     DRY_RUN=1; shift ;;
        --help|-help|help) usage ;;
        *) echo "[ERROR] 未知参数: $1" >&2; usage ;;
    esac
done

command -v mysql >/dev/null 2>&1 || { echo "[ERROR] 未找到 mysql 客户端，请安装 MySQL 8.0 客户端并加入 PATH" >&2; exit 1; }

MYSQL_APP=( -h"$DB_HOST" -P"$DB_PORT" -u"$APP_USER" -p"$APP_PASS" )
MYSQL_COMMON=( --default-character-set=utf8mb4 )

run_mysql() { # run_mysql <desc> <file> [<extra args...>]
    local desc="$1" file="$2"; shift 2
    echo ""
    echo "==================================================="
    echo "[STEP] ${desc} :: ${file}"
    echo "==================================================="
    if [ "$DRY_RUN" = "1" ]; then
        echo "[DRY-RUN] mysql ${MYSQL_COMMON[*]} $* < ${file}"
        return 0
    fi
    mysql "${MYSQL_COMMON[@]}" "$@" < "${file}"
    echo "[OK] ${desc} 完成"
}

echo "==================================================="
echo " Prod Platform AI - MySQL 建表与初始化"
echo " host=${DB_HOST}:${DB_PORT}   db=${DB_NAME}"
echo " app_user=${APP_USER}"
echo "==================================================="

# 1) 全量 DDL（23 表）
if [ "$SKIP_SCHEMA" = "1" ]; then
    echo "[SKIP] 跳过全量 DDL"
else
    run_mysql "全量 DDL（23 表）" "${SCRIPT_DIR}/01_full_schema_ddl.sql" "${MYSQL_APP[@]}" "$DB_NAME"
fi

# 2) 扩展表（指标/ABox/变更订阅/流程引擎）
if [ "$SKIP_EXT" = "1" ]; then
    echo "[SKIP] 跳过扩展表"
else
    run_mysql "扩展表结构" "${SCRIPT_DIR}/03_ext_schema.sql" "${MYSQL_APP[@]}" "$DB_NAME"
fi

# 3) 初始化数据（幂等）
if [ "$SKIP_INIT" = "1" ]; then
    echo "[SKIP] 跳过初始化数据"
else
    run_mysql "初始化数据（MCP/规则/提示词/LLM 占位）" "${SCRIPT_DIR}/02_init_data.sql" "${MYSQL_APP[@]}" "$DB_NAME"
fi

# 4) 校验表数量
echo ""
echo "==================================================="
echo " 结果校验"
echo "==================================================="
if [ "$DRY_RUN" = "1" ]; then
    echo "[DRY-RUN] 跳过实际校验"
else
    TBL_COUNT=$(mysql "${MYSQL_COMMON[@]}" -N -s "${MYSQL_APP[@]}" -e \
        "SELECT COUNT(*) FROM information_schema.TABLES WHERE table_schema='${DB_NAME}';" 2>/dev/null || echo "?")
    echo "[INFO] ${DB_NAME} 表数量: ${TBL_COUNT}"
    [ "$TBL_COUNT" != "?" ] && echo "[CHECK] 期望包含 23 张业务表 + 扩展表（指标/ABox/订阅/流程节点≥4 张）"
fi

echo ""
echo "[DONE] MySQL 建表与初始化完成。"
echo "  - 后端连接串示例: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
echo "  - 请确认 config/application.yml 数据源已同步，鉴权 JWT/LLM 已配置。"
