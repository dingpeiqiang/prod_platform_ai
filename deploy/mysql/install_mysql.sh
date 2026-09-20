#!/usr/bin/env bash
# ============================================================
# Prod Platform AI - MySQL 一键初始化脚本（纯 MySQL 8.0）
# 作用：建库 → 全量 DDL → 扩展表 → 初始化数据，供 VM 上一次性建库
# 用法：
#   bash install_mysql.sh [选项]
# 选项：
#   -h <host>       MySQL 地址（默认 127.0.0.1）
#   -P <port>       端口（默认 3306）
#   -u <user>       管理员账号，用于建库/建账号（默认 root）
#   -p <pass>       管理员密码
#   -a <appuser>    应用账号（默认 prodplatformai）
#   -w <apppass>    应用账号密码（默认 prodplatformai@134，务必修改）
#   -d <db>         库名（默认 prodplatformai）
#   --skip-create  跳过建库建账号（表已存在时）
#   --skip-schema  跳过 01 全量 DDL
#   --skip-ext     跳过 03 扩展表
#   --skip-init    跳过 02 初始化数据
#   --dry-run      仅打印将执行的命令
# 示例：
#   bash install_mysql.sh -u root -p 'Admin#123'
#   bash install_mysql.sh -u root -p 'x' --skip-create
# ============================================================
set -euo pipefail

# ---------- 默认参数 ----------
DB_HOST="127.0.0.1"
DB_PORT="3306"
ROOT_USER="root"
ROOT_PASS=""
APP_USER="prodplatformai"
APP_PASS="prodplatformai@134"
DB_NAME="prodplatformai"
SKIP_CREATE=0
SKIP_SCHEMA=0
SKIP_EXT=0
SKIP_INIT=0
DRY_RUN=0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
}

# ---------- 参数解析 ----------
while [ $# -gt 0 ]; do
    case "$1" in
        -h) DB_HOST="$2"; shift 2 ;;
        -P) DB_PORT="$2"; shift 2 ;;
        -u) ROOT_USER="$2"; shift 2 ;;
        -p) ROOT_PASS="$2"; shift 2 ;;
        -a) APP_USER="$2"; shift 2 ;;
        -w) APP_PASS="$2"; shift 2 ;;
        -d) DB_NAME="$2"; shift 2 ;;
        --skip-create) SKIP_CREATE=1; shift ;;
        --skip-schema) SKIP_SCHEMA=1; shift ;;
        --skip-ext)    SKIP_EXT=1; shift ;;
        --skip-init)   SKIP_INIT=1; shift ;;
        --dry-run)     DRY_RUN=1; shift ;;
        --help|-help|help) usage ;;
        *) echo "[ERROR] 未知参数: $1" >&2; usage ;;
    esac
done

command -v mysql >/dev/null 2>&1 || { echo "[ERROR] 未找到 mysql 客户端，请安装 MySQL 8.0 客户端并加入 PATH" >&2; exit 1; }

MYSQL_ROOT=()
MYSQL_ROOT+=( -h"$DB_HOST" -P"$DB_PORT" -u"$ROOT_USER" )
[ -n "$ROOT_PASS" ] && MYSQL_ROOT+=( -p"$ROOT_PASS" )
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
echo " Prod Platform AI - MySQL 初始化"
echo " host=${DB_HOST}:${DB_PORT}   db=${DB_NAME}"
echo " app_user=${APP_USER}"
echo "==================================================="

# 1) 建库 + 应用账号（管理员身份）
if [ "$SKIP_CREATE" = "1" ]; then
    echo "[SKIP] 跳过建库建账号"
else
    run_mysql "建库+应用账号" "${SCRIPT_DIR}/00_create_database.sql" "${MYSQL_ROOT[@]}"
    echo "  [提示] 00 脚本默认密码为 prodplatformai@134，上线前请修改 ${APP_USER} 密码并同步后端 env。"
fi

# 2) 全量 DDL（应用账号）
if [ "$SKIP_SCHEMA" = "1" ]; then
    echo "[SKIP] 跳过全量 DDL"
else
    run_mysql "全量 DDL（23 表）" "${SCRIPT_DIR}/01_full_schema_ddl.sql" "${MYSQL_APP[@]}" "$DB_NAME"
fi

# 3) 扩展表（指标/ABox/变更订阅/流程引擎）
if [ "$SKIP_EXT" = "1" ]; then
    echo "[SKIP] 跳过扩展表"
else
    run_mysql "扩展表结构" "${SCRIPT_DIR}/03_ext_schema.sql" "${MYSQL_APP[@]}" "$DB_NAME"
fi

# 4) 初始化数据（幂等）
if [ "$SKIP_INIT" = "1" ]; then
    echo "[SKIP] 跳过初始化数据"
else
    run_mysql "初始化数据（MCP/规则/提示词/LLM 占位）" "${SCRIPT_DIR}/02_init_data.sql" "${MYSQL_APP[@]}" "$DB_NAME"
fi

# 5) 校验表数量
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
echo "[DONE] MySQL 初始化脚本执行完毕。"
echo "  - 后端连接串示例: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
echo "  - 请确认应用账号密码已修改、鉴权 JWT/LLM 已配置。"
