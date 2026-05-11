# ============================================================
# 后端构建基础镜像 - 包含构建依赖
# ============================================================

FROM python:3.10-slim

LABEL maintainer="AI Platform"
LABEL description="Backend builder base image for AI Form"

WORKDIR /app

# 使用国内 APT 源
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources

# 安装构建依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# 配置国内 pip 源
RUN pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
RUN pip config set install.trusted-host pypi.tuna.tsinghua.edu.cn
