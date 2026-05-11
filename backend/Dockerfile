# ============================================================
# AI驱动动态表单 - 后端容器（基于私有仓库基础镜像）
# ============================================================

# 私有仓库配置
ARG REGISTRY=10.86.12.11:20200
ARG PROJECT=crm-pgcent

# 基础镜像名称
ARG BUILDER_IMAGE=${REGISTRY}/${PROJECT}/ai-form-backend-builder:latest
ARG RUNTIME_IMAGE=${REGISTRY}/${PROJECT}/ai-form-backend-runtime:latest

# ============================================================
# 构建阶段
# ============================================================
FROM ${BUILDER_IMAGE} AS builder

WORKDIR /app

# 复制依赖文件并安装
COPY backend/requirements.txt .
RUN pip install --no-cache-dir --user -r requirements.txt

# ============================================================
# 生产阶段
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

# 环境变量
ENV PYTHONUNBUFFERED=1
ENV PORT=6173
ENV DEBUG=False

# 暴露端口
EXPOSE 6173

# 启动命令
CMD ["uvicorn", "backend.app.main:app", "--host", "0.0.0.0", "--port", "6173", "--log-level", "info"]
