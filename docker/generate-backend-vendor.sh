#!/bin/bash
# ============================================================
# AI驱动动态表单 - 后端依赖包生成脚本
# 使用方式: bash generate-backend-vendor.sh
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

# 选择 Python
PYTHON_CMD=""
if command -v python3.10 >/dev/null 2>&1; then
    PYTHON_CMD="python3.10"
elif command -v python3 >/dev/null 2>&1; then
    PYTHON_CMD="python3"
else
    PYTHON_CMD="python"
fi

echo "使用 Python: $PYTHON_CMD"
$PYTHON_CMD --version
echo ""

# 强制重新安装 pip（解决系统 pip 版本兼容问题）
echo "强制重新安装 pip..."
$PYTHON_CMD -m pip install --force-reinstall --no-cache-dir pip==24.0 -q

if [ $? -eq 0 ]; then
    echo "pip 安装成功"
else
    echo "警告: pip 安装失败，尝试使用当前版本"
fi
echo ""

# 创建 vendor 目录
echo "创建 vendor 目录..."
mkdir -p "$VENDOR_DIR"

# 清理旧文件
echo "清理旧依赖包..."
rm -f "$VENDOR_DIR"/*.whl "$VENDOR_DIR"/*.tar.gz 2>/dev/null || true

# 下载依赖
echo "开始下载依赖包..."
echo ""
$PYTHON_CMD -m pip download \
    --no-cache-dir \
    -r "$REQ_FILE" \
    -d "$VENDOR_DIR"

if [ $? -eq 0 ]; then
    echo ""
    echo "=== 下载完成 ==="
    echo "依赖包列表:"
    ls -la "$VENDOR_DIR" | head -30
    echo ""
    echo "包数量: $(ls -la "$VENDOR_DIR"/*.whl 2>/dev/null | wc -l) 个 wheel + $(ls -la "$VENDOR_DIR"/*.tar.gz 2>/dev/null | wc -l) 个 source"
else
    echo ""
    echo "错误: 下载失败"
    exit 1
fi
