#!/usr/bin/env bash
# =============================================================
# 一键离线依赖打包脚本（Linux/Mac）
# 在有网络的机器上执行，打包后端 Python 包 + 前端 Node 包
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ONLY=false
FRONTEND_ONLY=false
SKIP_GIT=false

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --backend-only) BACKEND_ONLY=true ;;
        --frontend-only) FRONTEND_ONLY=true ;;
        --skip-git) SKIP_GIT=true ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
    shift
done

echo ""
echo "██████████████████████████████████████████████"
echo "  内网离线依赖打包工具"
echo "  项目: prod_platform_ai"
echo "██████████████████████████████████████████████"
echo ""

START_TIME=$(date +%s)

# ── 后端 Python 依赖 ──────────────────────────────────────
if [ "$FRONTEND_ONLY" = false ]; then
    echo "▶ [1/2] 打包后端 Python 依赖..."
    chmod +x "$SCRIPT_DIR/scripts/pack-backend-deps.sh"
    bash "$SCRIPT_DIR/scripts/pack-backend-deps.sh"
    echo ""
fi

# ── 前端 Node 依赖 ────────────────────────────────────────
if [ "$BACKEND_ONLY" = false ]; then
    echo "▶ [2/2] 打包前端 Node 依赖..."
    chmod +x "$SCRIPT_DIR/scripts/pack-frontend-deps.sh"
    bash "$SCRIPT_DIR/scripts/pack-frontend-deps.sh"
    echo ""
fi

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

echo "██████████████████████████████████████████████"
echo "  ✅ 所有依赖打包完成！耗时: ${ELAPSED}s"
echo "██████████████████████████████████████████████"
echo ""

if [ "$SKIP_GIT" = false ]; then
    read -p "是否立即执行 git add + commit？(y/N) " ANSWER
    if [[ "$ANSWER" =~ ^[yY]$ ]]; then
        cd "$SCRIPT_DIR"
        git add backend/vendor/ frontend/vendor/ 2>/dev/null || true
        git status --short
        COMMIT_MSG="chore: update offline vendor dependencies $(date '+%Y-%m-%d')"
        git commit -m "$COMMIT_MSG"
        echo "[OK] 已提交到 git: $COMMIT_MSG"
    else
        echo ""
        echo "请手动执行以下命令提交到 git："
        echo "  git add backend/vendor/ frontend/vendor/"
        echo '  git commit -m "chore: update offline vendor dependencies"'
    fi
fi

echo ""
echo "内网构建命令："
echo "  docker-compose -f docker-compose.offline.yml up -d --build"
echo ""
