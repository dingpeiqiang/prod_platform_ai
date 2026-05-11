# ============================================================
# AI驱动动态表单 - 前端容器（基于私有仓库基础镜像）
# ============================================================

# 私有仓库配置
ARG REGISTRY=10.86.12.11:20200
ARG PROJECT=crm-pgcent

# 基础镜像名称
ARG BUILDER_IMAGE=${REGISTRY}/${PROJECT}/ai-form-frontend-builder:latest

# ============================================================
# 构建阶段
# ============================================================
FROM ${BUILDER_IMAGE} AS builder

WORKDIR /app

# 复制 package.json 和 package-lock.json
COPY frontend/package.json frontend/package-lock.json* ./

# 安装依赖
RUN npm ci --prefer-offline

# 复制前端源码
COPY frontend/ ./

# 构建生产版本
ARG VITE_API_BASE_URL=http://localhost:6173
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# ============================================================
# Nginx 阶段
# ============================================================
FROM nginx:alpine

LABEL maintainer="AI Platform"

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 nginx 配置
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
