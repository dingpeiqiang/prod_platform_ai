# ============================================================
# 前端构建基础镜像 - 包含Node.js和npm
# ============================================================

FROM node:20-alpine

LABEL maintainer="AI Platform"
LABEL description="Frontend builder base image for AI Form"

WORKDIR /app

# 使用国内 npm 源
RUN npm config set registry https://registry.npmmirror.com
