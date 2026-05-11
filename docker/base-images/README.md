# 基础镜像目录

本目录包含项目的Docker基础镜像，用于加速应用构建过程。

## 基础镜像结构

```
docker/base-images/
├── backend-builder/       # 后端构建镜像
│   └── Dockerfile
├── backend-runtime/       # 后端运行时镜像
│   └── Dockerfile
└── frontend-builder/      # 前端构建镜像
    └── Dockerfile
```

## 基础镜像列表

| 镜像名称 | 标签 | 用途 | 基础镜像 |
|---------|------|------|---------|
| ai-form-backend-builder | latest | 包含gcc、libpq-dev等构建依赖 | python:3.10-slim |
| ai-form-backend-runtime | latest | 包含curl等运行时依赖 | python:3.10-slim |
| ai-form-frontend-builder | latest | 包含Node.js 20 | node:20-alpine |

## 构建基础镜像

### 方式1：使用脚本（推荐）

```powershell
cd docker
.\build-base-images.ps1
```

### 方式2：手动构建

```powershell
# 构建后端构建镜像
docker build -t ai-form-backend-builder:latest -f docker/base-images/backend-builder/Dockerfile docker/base-images/backend-builder

# 构建后端运行时镜像
docker build -t ai-form-backend-runtime:latest -f docker/base-images/backend-runtime/Dockerfile docker/base-images/backend-runtime

# 构建前端构建镜像
docker build -t ai-form-frontend-builder:latest -f docker/base-images/frontend-builder/Dockerfile docker/base-images/frontend-builder
```

## 使用基础镜像

### 构建应用镜像

```powershell
# 后端（自动使用ai-form-backend-builder和ai-form-backend-runtime）
docker build -t prod-platform-ai-backend:latest -f backend/Dockerfile .

# 前端（自动使用ai-form-frontend-builder）
docker build -t prod-platform-ai-frontend:latest -f frontend/Dockerfile .
```

### 使用自定义基础镜像

通过构建参数指定：

```powershell
# 后端
docker build \
  --build-arg BUILDER_IMAGE=my-custom-builder:v1 \
  --build-arg RUNTIME_IMAGE=my-custom-runtime:v1 \
  -t prod-platform-ai-backend:latest \
  -f backend/Dockerfile .

# 前端
docker build \
  --build-arg BUILDER_IMAGE=my-custom-frontend-builder:v1 \
  -t prod-platform-ai-frontend:latest \
  -f frontend/Dockerfile .
```

## 优势

1. **构建加速**：基础镜像只需要构建一次，后续构建直接使用
2. **网络优化**：APT源、pip源、npm源在基础镜像中已配置
3. **版本稳定**：基础镜像可长期保持不变
4. **减小体积**：依赖层可以复用
5. **可维护性**：基础镜像和应用镜像分开管理
