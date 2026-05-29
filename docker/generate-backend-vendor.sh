#!/bin/bash
# ============================================================
# AI驱动动态表单 - 后端依赖包生成脚本
# 使用方式: bash generate-backend-vendor.sh 或 ./generate-backend-vendor.sh
# 说明: 在 Linux 主机上运行，生成后端所需的所有依赖包
# 目标Python版本: 3.10
# ============================================================

# 检查是否使用 bash 执行
if [ -z "$BASH_VERSION" ]; then
    echo "错误: 请使用 bash 执行此脚本，例如: bash generate-backend-vendor.sh"
    exit 1
fi

set -eo pipefail  # 严格模式：命令失败立即退出，管道失败也退出

# 日志函数
log_info() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] $1"
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $1" >&2
}

log_warn() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARN] $1"
}

# 获取脚本所在目录
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT=$(dirname "$SCRIPT_DIR")
BACKEND_DIR="$PROJECT_ROOT/backend"
VENDOR_DIR="$BACKEND_DIR/vendor"
REQ_FILE="$BACKEND_DIR/requirements.txt"
LOG_FILE="$SCRIPT_DIR/generate-vendor.log"

# 清空日志文件
> "$LOG_FILE"

log_info "========================================"
log_info "AI驱动动态表单 - 后端依赖包生成脚本"
log_info "项目根目录: $PROJECT_ROOT"
log_info "后端目录: $BACKEND_DIR"
log_info "目标目录: $VENDOR_DIR"
log_info "目标Python版本: 3.10"
log_info "日志文件: $LOG_FILE"
log_info "========================================"

# 检查 requirements.txt 是否存在
if [ ! -f "$REQ_FILE" ]; then
    log_error "错误: 未找到 requirements.txt 文件: $REQ_FILE"
    exit 1
fi

# 检查 Python 环境
PYTHON_CMD=""
if command -v python3.10 >/dev/null 2>&1; then
    PYTHON_CMD="python3.10"
elif command -v python3 >/dev/null 2>&1; then
    PYTHON_CMD="python3"
    log_warn "未找到 Python 3.10，使用 python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON_CMD="python"
    log_warn "未找到 Python 3.10，使用 python"
else
    log_error "错误: 未找到 Python"
    exit 1
fi

log_info "找到 Python: $PYTHON_CMD"
$PYTHON_CMD --version | tee -a "$LOG_FILE"

# 检查 Python 版本
PYTHON_VERSION=$($PYTHON_CMD -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
log_info "Python 版本: $PYTHON_VERSION"
if [ "$PYTHON_VERSION" != "3.10" ]; then
    log_warn "警告: 当前 Python 版本为 $PYTHON_VERSION，推荐使用 3.10"
fi

# 检查 pip
PIP_CMD=""
if "$PYTHON_CMD" -m pip --version >/dev/null 2>&1; then
    PIP_CMD="$PYTHON_CMD -m pip"
elif command -v pip3 >/dev/null 2>&1; then
    PIP_CMD="pip3"
elif command -v pip >/dev/null 2>&1; then
    PIP_CMD="pip"
else
    log_error "错误: 未找到 pip"
    exit 1
fi

log_info "找到 pip: $PIP_CMD"
$PIP_CMD --version | tee -a "$LOG_FILE"

# 升级 pip 到最新版本（解决 Python 3.10 兼容性问题）
log_info "升级 pip 到最新版本..."
$PIP_CMD install --upgrade pip 2>&1 | tee -a "$LOG_FILE" || log_warn "pip 升级失败，继续使用当前版本"

# 创建 vendor 目录
log_info "创建 vendor 目录..."
mkdir -p "$VENDOR_DIR"

# 清理旧的依赖包
log_info "清理旧的依赖包..."
rm -f "$VENDOR_DIR"/*.whl "$VENDOR_DIR"/*.tar.gz "$VENDOR_DIR"/*.zip 2>/dev/null || true

# 使用 pip 下载依赖到 vendor 目录（指定 Python 3.10 版本兼容，包含所有子依赖）
log_info "开始下载依赖包（目标 Python 3.10，包含所有子依赖）..."
if ! $PIP_CMD download \
    --no-cache-dir \
    --python-version 310 \
    --platform manylinux_x86_64 \
    --implementation cp \
    --abi cp310 \
    -r "$REQ_FILE" \
    -d "$VENDOR_DIR" 2>&1 | tee -a "$LOG_FILE"; then
    log_warn "部分包可能下载失败，尝试不带版本约束重新下载..."
    # 如果指定版本下载失败，尝试不带版本约束下载（可能会下载兼容的 wheel）
    $PIP_CMD download \
        --no-cache-dir \
        -r "$REQ_FILE" \
        -d "$VENDOR_DIR" 2>&1 | tee -a "$LOG_FILE" || true
fi

log_info "========================================"
log_info "依赖包下载完成！"
log_info "输出目录: $VENDOR_DIR"
log_info "========================================"

# 列出下载的包
log_info "下载的依赖包列表:"
ls -la "$VENDOR_DIR" | head -50 | tee -a "$LOG_FILE"

# 统计包数量
WHEEL_COUNT=$(ls -la "$VENDOR_DIR"/*.whl 2>/dev/null | wc -l)
TAR_COUNT=$(ls -la "$VENDOR_DIR"/*.tar.gz 2>/dev/null | wc -l)
TOTAL_COUNT=$(( WHEEL_COUNT + TAR_COUNT ))

log_info ""
log_info "下载统计:"
log_info "  Wheel 包数量: $WHEEL_COUNT"
log_info "  Source 包数量: $TAR_COUNT"
log_info "  总下载包数: $TOTAL_COUNT"

# 检查是否有下载失败的包
if [ "$TOTAL_COUNT" -eq 0 ]; then
    log_error "错误: 没有下载到任何依赖包，请检查网络连接和 requirements.txt"
    exit 1
fi

log_info "========================================"
log_info "脚本执行完成！"
log_info "========================================"
