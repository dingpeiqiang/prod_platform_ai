# ============================================================
# AI驱动动态表单 - 部署到远程服务器
# ============================================================
# 用法：
#   1. 将整个项目打包上传到服务器
#   2. 在服务器上执行：chmod +x deploy-remote.sh && ./deploy-remote.sh
#   3. 或远程执行：ssh user@server "cd /path/to/prod_platform_ai && docker compose up -d"
# ============================================================

set -e

APP_NAME="prod_platform_ai"
IMAGE_BACKEND="ai-form-backend"
IMAGE_FRONTEND="ai-form-frontend"

echo "==> 构建后端镜像"
docker build -f backend/Dockerfile -t $IMAGE_BACKEND .

echo "==> 构建前端镜像"
docker build -f frontend/Dockerfile -t $IMAGE_FRONTEND .

echo "==> 停止旧容器（如有）"
docker compose down || true

echo "==> 启动服务"
docker compose up -d

echo "==> 等待服务就绪"
sleep 15

echo "==> 检查容器状态"
docker compose ps

echo ""
echo "==> 部署完成！"
echo "   前端：http://<服务器IP>"
echo "   API：  http://<服务器IP>:6173/docs"