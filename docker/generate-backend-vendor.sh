#!/bin/bash
# ============================================================
# AI驱动动态表单 - 后端依赖包生成脚本
# 使用方式: bash generate-backend-vendor.sh
# 说明: 直接使用当前环境的 pip 下载所有依赖包（包括子依赖）
# ============================================================

# 获取脚本所在目录
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT=$(dirname "$SCRIPT_DIR")
BACKEND_DIR="$PROJECT_ROOT/backend"
VENDOR_DIR="$BACKEND_DIR/vendor"
REQ_FILE="$BACKEND_DIR/requirements.txt"

echo "=== 后端依赖包生成脚本 ==="
echo "项目根目录: $PROJECT_ROOT"
echo "目标目录: $VENDOR_DIR"
echo ""

# 检查 requirements.txt 是否存在
if [ ! -f "$REQ_FILE" ]; then
    echo "错误: 未找到 requirements.txt 文件: $REQ_FILE"
    exit 1
fi

# 检查 pip 是否可用
if ! command -v pip >/dev/null 2>&1; then
    echo "错误: 未找到 pip，请激活正确的 Python 虚拟环境"
    exit 1
fi

echo "使用 pip: $(which pip)"
pip --version
echo ""

# 创建 vendor 目录
echo "创建 vendor 目录..."
mkdir -p "$VENDOR_DIR"

# 清理旧文件
echo "清理旧依赖包..."
rm -f "$VENDOR_DIR"/*.whl "$VENDOR_DIR"/*.tar.gz "$VENDOR_DIR"/*.zip 2>/dev/null || true

# 下载依赖（包含所有子依赖）
echo "开始下载依赖包..."
echo ""
pip download \
    --no-cache-dir \
    -r "$REQ_FILE" \
    -d "$VENDOR_DIR"

if [ $? -eq 0 ]; then
    echo ""
    echo "=== 下载完成 ==="
    WHEEL_COUNT=$(ls -la "$VENDOR_DIR"/*.whl 2>/dev/null | wc -l)
    TAR_COUNT=$(ls -la "$VENDOR_DIR"/*.tar.gz 2>/dev/null | wc -l)
    echo "下载统计:"
    echo "  Wheel 包: $WHEEL_COUNT 个"
    echo "  Source 包: $TAR_COUNT 个"
    echo "  总计: $((WHEEL_COUNT + TAR_COUNT)) 个"
    echo ""
    echo "依赖包列表（前30个）:"
    ls -la "$VENDOR_DIR" | head -30
else
    echo ""
    echo "错误: 下载失败"
    exit 1
fi
