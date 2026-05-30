# Docker 构建工具

## 基础镜像（Base Images）

预装依赖的基础镜像，用于加速应用镜像构建。

| 镜像 | Dockerfile | 构建脚本 | 推送脚本 |
|------|-----------|---------|---------|
| 后端 (Python 3.12) | [Dockerfile.base](Dockerfile.base) | [build-base-image.ps1](build-base-image.ps1) | [push-base-image.ps1](push-base-image.ps1) |
| 前端 (Node.js 20) | [Dockerfile.base.frontend](Dockerfile.base.frontend) | [build-frontend-base-image.ps1](build-frontend-base-image.ps1) | [push-frontend-base-image.ps1](push-frontend-base-image.ps1) |

### 构建基础镜像

```powershell
# 后端
.\docker\build-base-image.ps1

# 前端
.\docker\build-frontend-base-image.ps1
```

### 推送到私有仓库

```powershell
# 后端
.\docker\push-base-image.ps1

# 前端
.\docker\push-frontend-base-image.ps1
```

## 应用镜像（App Images）

| 应用 | Dockerfile | 基础镜像 |
|------|-----------|---------|
| 后端 | [Sitech.BJ.Dockerfile.backend](Sitech.BJ.Dockerfile.backend) | prod-platform-backend-base:1.0 |
| 前端 | [Sitech.BJ.Dockerfile.frontend](Sitech.BJ.Dockerfile.frontend) | prod-platform-frontend-base:1.0 |