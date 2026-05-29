# Docker离线安装包生成工具

## 简介

本目录包含前端和后端的Docker离线安装包生成工具，用于在内网离线环境下构建Docker镜像。

## 工具列表

| 工具文件 | 说明 |
|---------|------|
| `generate-frontend-vendor.ps1` | 前端npm依赖包下载工具 |
| `generate-backend-vendor.ps1` | 后端Python依赖包下载工具 |

## 使用方法

### 生成前端依赖包

```powershell
cd docker
.\generate-frontend-vendor.ps1
```

此工具会：
- 读取 `frontend/package.json` 中的依赖配置
- 下载所有依赖包（dependencies和devDependencies）到 `frontend/vendor/` 目录
- 生成 `package-list.txt` 依赖包清单

### 生成后端依赖包

```powershell
cd docker
.\generate-backend-vendor.ps1
```

此工具会：
- 读取 `backend/requirements.txt` 中的依赖配置
- 下载所有依赖包到 `backend/vendor/` 目录
- 生成 `package-list.txt` 依赖包清单

## 前提条件

1. **前端工具**：需要Node.js环境（推荐Node.js 18+）
2. **后端工具**：需要Python环境（推荐Python 3.10+）
3. 首次运行需要联网下载依赖包

## Docker构建说明

### 前端构建

```dockerfile
# 复制离线依赖包
COPY frontend/vendor/ ./vendor/

# 配置npm离线模式
RUN npm config set cache /tmp/npm-cache && \
    find ./vendor -name "*.tgz" -exec npm cache add {} \; 2>/dev/null || true

# 使用prefer-offline安装
RUN npm ci --prefer-offline --cache /tmp/npm-cache
```

### 后端构建

```dockerfile
# 复制离线依赖包
COPY backend/vendor/ ./vendor/

# 从本地vendor目录安装（--no-index禁止访问PyPI）
RUN pip install \
    --no-cache-dir \
    --no-index \
    --find-links=./vendor \
    -r requirements.txt
```

## 更新依赖

当 `package.json` 或 `requirements.txt` 中的依赖发生变化时，重新运行对应的工具即可更新离线依赖包。
