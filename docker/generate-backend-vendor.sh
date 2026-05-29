#!/bin/sh
# ============================================================
# AI驱动动态表单 - 后端依赖包生成脚本
# 使用方式: sh generate-backend-vendor.sh 或 ./generate-backend-vendor.sh
# 说明: 在 Linux 主机上运行，生成后端所需的所有依赖包
# 目标Python版本: 3.10
# ============================================================

set -e

# 获取脚本所在目录（兼容 sh 和 bash）
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_ROOT=$(dirname "$SCRIPT_DIR")
BACKEND_DIR="$PROJECT_ROOT/backend"
VENDOR_DIR="$BACKEND_DIR/vendor"
REQ_FILE="$BACKEND_DIR/requirements.txt"

echo "========================================"
echo "AI驱动动态表单 - 后端依赖包生成脚本"
echo "项目根目录: $PROJECT_ROOT"
echo "后端目录: $BACKEND_DIR"
echo "目标目录: $VENDOR_DIR"
echo "目标Python版本: 3.10"
echo "========================================"

# 检查 Python 环境
PYTHON_CMD=""
if command -v python3.10 >/dev/null 2>&1; then
    PYTHON_CMD="python3.10"
elif command -v python3 >/dev/null 2>&1; then
    PYTHON_CMD="python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON_CMD="python"
else
    echo "错误: 未找到 Python"
    exit 1
fi

echo "找到 Python: $PYTHON_CMD"
$PYTHON_CMD --version

# 检查 pip
PIP_CMD=""
if "$PYTHON_CMD" -m pip --version >/dev/null 2>&1; then
    PIP_CMD="$PYTHON_CMD -m pip"
elif command -v pip3 >/dev/null 2>&1; then
    PIP_CMD="pip3"
elif command -v pip >/dev/null 2>&1; then
    PIP_CMD="pip"
else
    echo "错误: 未找到 pip"
    exit 1
fi

echo "找到 pip: $PIP_CMD"

# 创建 vendor 目录
mkdir -p "$VENDOR_DIR"

# 清理旧的依赖包
echo "清理旧的依赖包..."
rm -f "$VENDOR_DIR"/*.whl
rm -f "$VENDOR_DIR"/*.tar.gz
rm -f "$VENDOR_DIR"/*.zip

# 使用 pip 下载依赖到 vendor 目录
# --platform linux_x86_64 指定下载 Linux 版本
echo "开始下载依赖包..."
$PIP_CMD download \
    --no-cache-dir \
    --no-deps \
    --platform linux_x86_64 \
    --python-version 310 \
    --implementation cp \
    --abi cp310 \
    -r "$REQ_FILE" \
    -d "$VENDOR_DIR"

# 对于没有预编译 wheel 的包，回退到源代码包
echo "下载源代码包作为补充..."
$PIP_CMD download \
    --no-cache-dir \
    --no-deps \
    --no-binary=:all: \
    -r "$REQ_FILE" \
    -d "$VENDOR_DIR"

echo "========================================"
echo "依赖包下载完成！"
echo "输出目录: $VENDOR_DIR"
echo "========================================"

# 列出下载的包
echo "下载的依赖包列表:"
ls -la "$VENDOR_DIR" | head -50

# 统计包数量
echo ""
echo "总下载包数: $(ls -la "$VENDOR_DIR"/*.whl "$VENDOR_DIR"/*.tar.gz 2>/dev/null | wc -l)"
