# Docker 部署

## 目录结构

```
docker/
├── build-and-push.ps1           # 构建并推送基础镜像脚本
├── base-images/                 # 基础镜像
│   ├── backend-builder/         # 后端构建基础镜像
│   ├── backend-runtime/         # 后端运行时基础镜像
│   └── frontend-builder/        # 前端构建基础镜像
└── README.md                    # 本文档
```

## 配置 Docker（重要！）

由于私有仓库使用 HTTP 而非 HTTPS，需要先配置 Docker 允许不安全的注册表：

1. 打开 Docker Desktop Settings
2. 进入 Docker Engine
3. 添加以下配置：

```json
{
  "insecure-registries": [
    "10.86.12.11:20200"
  ]
}
```

4. 点击 Apply & Restart

## 快速开始

### 1. 构建并推送基础镜像

```powershell
cd docker
.\build-and-push.ps1
```

脚本会：
- 检查 Docker 配置
- 登录私有仓库 10.86.12.11:20200
- 交互式选择要构建的镜像
- 构建基础镜像
- 推送到 crm-pgcent 项目

镜像列表：
- `10.86.12.11:20200/crm-pgcent/ai-form-backend-builder:latest`
- `10.86.12.11:20200/crm-pgcent/ai-form-backend-runtime:latest`
- `10.86.12.11:20200/crm-pgcent/ai-form-frontend-builder:latest`

### 2. 使用基础镜像构建应用

基础镜像已经构建并推送后，应用的 Dockerfile 会自动从私有仓库拉取。

## 基础镜像说明

| 镜像 | 用途 | 包含内容 |
|------|------|----------|
| backend-builder | 后端构建 | python:3.10-slim + gcc + libpq-dev + 清华 pip 源 |
| backend-runtime | 后端运行 | python:3.10-slim + curl + 清华 pip 源 |
| frontend-builder | 前端构建 | node:20-alpine + npmmirror 源 |
