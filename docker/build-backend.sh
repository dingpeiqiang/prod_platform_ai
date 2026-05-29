#!/bin/bash
# ============================================================
# AI驱动动态表单 - 后端构建脚本
# 使用方式: ./build-backend.sh [egg|wheel|all]
# ============================================================

set -e

# 获取脚本所在目录
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
PROJECT_ROOT=$(dirname "$SCRIPT_DIR")
BACKEND_DIR="$PROJECT_ROOT/backend"
DIST_DIR="$BACKEND_DIR/dist"

echo "========================================"
echo "AI驱动动态表单 - 后端构建脚本"
echo "项目根目录: $PROJECT_ROOT"
echo "后端目录: $BACKEND_DIR"
echo "========================================"

# 创建 dist 目录
mkdir -p "$DIST_DIR"

# 切换到后端目录
cd "$BACKEND_DIR"

# 根据参数选择打包方式
case "${1:-all}" in
    egg)
        echo "开始构建 egg 包..."
        python setup.py bdist_egg
        ;;
    wheel)
        echo "开始构建 wheel 包..."
        python setup.py bdist_wheel
        ;;
    all)
        echo "开始构建 egg 包..."
        python setup.py bdist_egg
        
        echo "开始构建 wheel 包..."
        python setup.py bdist_wheel
        ;;
    *)
        echo "用法: $0 [egg|wheel|all]"
        exit 1
        ;;
esac

echo "========================================"
echo "构建完成！"
echo "输出目录: $DIST_DIR"
echo "========================================"

# 列出生成的包
ls -la "$DIST_DIR"
