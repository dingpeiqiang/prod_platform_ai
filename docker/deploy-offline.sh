#!/bin/bash
# ============================================================
# 离线部署脚本 - 在无网络环境中执行 (Linux)
# ============================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
DARK_CYAN='\033[0;36m'
NC='\033[0m' # No Color

write_status() {
    local message="$1"
    local type="${2:-Info}"
    case "$type" in
        Success) echo -e "${GREEN}$message${NC}" ;;
        Warning) echo -e "${YELLOW}$message${NC}" ;;
        Error) echo -e "${RED}$message${NC}" ;;
        Header) echo -e "${DARK_CYAN}$message${NC}" ;;
        *) echo -e "${CYAN}$message${NC}" ;;
    esac
}

get_script_dir() {
    cd "$(dirname "${BASH_SOURCE[0]}")" && pwd
}

# Main
echo ""
write_status "========================================" "Header"
write_status "离线部署工具 (Linux)" "Header"
write_status "========================================" "Header"
echo ""

# Get script directory
SCRIPT_DIR=$(get_script_dir)
write_status "工作目录: $SCRIPT_DIR"
echo ""

# Check if running from correct location (package root)
IMAGES_DIR="$SCRIPT_DIR/docker-images"
SOURCE_DIR="$SCRIPT_DIR/source"
if [ ! -d "$IMAGES_DIR" ] || [ ! -d "$SOURCE_DIR" ]; then
    write_status "[ERROR] 请在离线包根目录下运行此脚本" "Error"
    write_status "目录结构应为:"
    write_status "  package-dir/"
    write_status "  ├── docker-images/"
    write_status "  ├── source/"
    write_status "  └── deploy-offline.sh (本文件)"
    read -p "按回车键退出"
    exit 1
fi

# ============================================================
# Step 1: 导入 Docker 镜像
# ============================================================
write_status "步骤 1/4: 导入 Docker 镜像..."

IMAGE_FILES=("$IMAGES_DIR"/*.tar)
if [ ${#IMAGE_FILES[@]} -eq 0 ] || [ ! -f "${IMAGE_FILES[0]}" ]; then
    write_status "[WARNING] 未找到镜像文件" "Warning"
else
    for IMG_FILE in "$IMAGES_DIR"/*.tar; do
        if [ -f "$IMG_FILE" ]; then
            IMG_NAME=$(basename "$IMG_FILE")
            write_status "  导入 $IMG_NAME ..."
            if docker load -i "$IMG_FILE"; then
                write_status "  [OK] $IMG_NAME 导入成功" "Success"
            else
                write_status "  [ERROR] $IMG_NAME 导入失败" "Error"
            fi
        fi
    done
fi
echo ""

# ============================================================
# Step 2: 准备项目文件
# ============================================================
write_status "步骤 2/4: 准备项目文件..."

# 复制项目文件到当前目录上级（如果需要）
TARGET_DIR=$(dirname "$SCRIPT_DIR")
write_status "  目标目录: $TARGET_DIR"

# 检查是否需要复制
COPY_SOURCE=true
if [ -d "$TARGET_DIR/backend" ] && [ -d "$TARGET_DIR/frontend" ]; then
    read -p "  检测到目标目录已存在项目文件，是否覆盖？(y/N，默认 N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        COPY_SOURCE=false
        write_status "  跳过文件复制"
    fi
fi

if [ "$COPY_SOURCE" = true ]; then
    write_status "  复制项目文件..."
    cp -rf "$SOURCE_DIR"/* "$TARGET_DIR/"
    write_status "[OK] 项目文件准备完成" "Success"
fi
echo ""

# ============================================================
# Step 3: 配置环境变量
# ============================================================
write_status "步骤 3/4: 配置环境变量..."

ENV_FILE="$TARGET_DIR/.env"
ENV_EXAMPLE="$TARGET_DIR/.env.example"

if [ ! -f "$ENV_FILE" ] && [ -f "$ENV_EXAMPLE" ]; then
    write_status "  创建 .env 文件..."
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    write_status "[OK] .env 文件已创建，请根据需要编辑配置" "Success"
elif [ -f "$ENV_FILE" ]; then
    write_status "[OK] .env 文件已存在" "Success"
else
    write_status "[WARNING] 未找到 .env.example 文件" "Warning"
fi
echo ""

# ============================================================
# Step 4: 启动服务
# ============================================================
write_status "步骤 4/4: 启动服务..."

cd "$TARGET_DIR"

write_status "  使用 Docker Compose 启动服务..."
if docker-compose up -d --build; then
    write_status "[OK] 服务启动成功" "Success"
else
    write_status "[ERROR] 服务启动失败" "Error"
    write_status "请检查 Docker 是否正常运行" "Warning"
fi
echo ""

# Summary
write_status "========================================" "Header"
write_status "部署完成!" "Header"
write_status "========================================" "Header"
echo ""
write_status "服务状态:"
write_status "  运行: docker-compose ps"
write_status "  日志: docker-compose logs -f"
write_status "  停止: docker-compose down"
echo ""
write_status "访问地址:" "Success"
write_status "  前端: http://localhost (或配置的端口)"
write_status "  后端: http://localhost:6173"
echo ""
