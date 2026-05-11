# ============================================================
# AI驱动动态表单 - 前端容器 (基于基础镜像)
# ============================================================
# 构建：docker build -f frontend/Dockerfile -t prod-platform-ai-frontend .
# 运行：docker run -d -p 80:80 --name ai-frontend prod-platform-ai-frontend
# ============================================================

# 基础镜像名称（可以是本地镜像或私有仓库镜像）
ARG BUILDER_IMAGE=ai-form-frontend-builder:latest

# ============================================================
# 构建阶段 - 使用包含Node.js的基础镜像
# ============================================================
FROM ${BUILDER_IMAGE} AS builder

WORKDIR /app

# 复制 package.json 和 package-lock.json
COPY frontend/package.json frontend/package-lock.json* ./

# 安装依赖
RUN npm ci --prefer-offline

# 复制前端源码
COPY frontend/ ./

# 构建生产版本（VITE_API_BASE_URL 会注入到构建产物）
# 注意：开发环境 dev 模式通过 docker-compose dev profile 使用
ARG VITE_API_BASE_URL=http://localhost:6173
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# ============================================================
# Nginx 阶段
# ============================================================
FROM nginx:alpine

LABEL maintainer="AI Platform"
LABEL description="AI Form Frontend Nginx"

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 nginx 配置
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
