# ============================================================
# AI驱动动态表单 - 后端容器
# ============================================================
# 构建：docker build -f backend/Dockerfile -t prod-platform-ai-backend .
# 运行：docker run -d -p 6173:6173 --name ai-backend prod-platform-ai-backend
# ============================================================

FROM python:3.10-slim AS builder

WORKDIR /app

# 使用国内 APT 源
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources

# 安装构建依赖（用于某些需要编译的包）
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# 复制依赖文件并安装（使用国内 pip 源）
COPY backend/requirements.txt .
RUN pip install --no-cache-dir --user -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

# ============================================================
# 生产镜像
# ============================================================
FROM python:3.10-slim

WORKDIR /app

# 使用国内 APT 源
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources

# 安装运行时依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 从 builder 复制已安装的包
COPY --from=builder /root/.local /root/.local
ENV PATH=/root/.local/bin:$PATH

# 复制后端代码
COPY backend/ ./backend/
COPY config/ ./config/

# 创建必要目录
RUN mkdir -p backend/logs backend/app/logs

# 环境变量（可在 docker-compose 或 .env 中覆盖）
ENV PYTHONUNBUFFERED=1
ENV PORT=6173
ENV DEBUG=False

# 暴露端口
EXPOSE 6173

# 启动命令
CMD ["uvicorn", "backend.app.main:app", "--host", "0.0.0.0", "--port", "6173", "--log-level", "info"]