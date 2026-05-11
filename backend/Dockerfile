# ============================================================
# AI驱动动态表单 - 后端容器（基于基础镜像）
# ============================================================
# 构建：docker build -f backend/Dockerfile -t prod-platform-ai-backend .
# 运行：docker run -d -p 6173:6173 --name ai-backend prod-platform-ai-backend
# ============================================================

# 基础镜像名称（可以是本地镜像或私有仓库镜像）
ARG BUILDER_IMAGE=ai-form-backend-builder:latest
ARG RUNTIME_IMAGE=ai-form-backend-runtime:latest

# ============================================================
# 构建阶段 - 使用包含构建依赖的基础镜像
# ============================================================
FROM ${BUILDER_IMAGE} AS builder

WORKDIR /app

# 复制依赖文件并安装
COPY backend/requirements.txt .
RUN pip install --no-cache-dir --user -r requirements.txt

# ============================================================
# 生产阶段 - 使用运行时基础镜像
# ============================================================
FROM ${RUNTIME_IMAGE}

WORKDIR /app

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
