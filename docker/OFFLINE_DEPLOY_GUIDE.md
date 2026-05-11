# 离线部署指南

## 概述

本指南介绍如何在无网络环境中部署 AI 驱动动态表单系统。

## 目录

- [前置条件](#前置条件)
- [离线打包（有网络环境）](#离线打包有网络环境)
- [传输离线包](#传输离线包)
- [离线部署（无网络环境）](#离线部署无网络环境)
- [常见问题](#常见问题)

## 前置条件

### 打包环境（有网络）
- Windows 10/11 或 Linux
- Docker Desktop 或 Docker Engine
- PowerShell 5.0+ (Windows) 或 Bash (Linux)
- 网络连接（用于拉取镜像）

### 部署环境（无网络）
- Windows 10/11 或 Linux
- Docker Desktop 或 Docker Engine
- Docker Compose
- PowerShell 5.0+ (Windows) 或 Bash (Linux)

## 离线打包（有网络环境）

### 步骤 1：准备基础镜像

确保基础镜像已经构建并推送到私有仓库，或者本地已经拉取：

```bash
# 检查镜像是否存在
docker images | grep ai-form
```

如果镜像不存在，请先运行构建脚本：

```powershell
cd docker
.\build-and-push.ps1
```

### 步骤 2：运行打包脚本

在项目根目录的 `docker` 文件夹下运行打包脚本：

```powershell
cd docker
.\package-offline.ps1
```

打包脚本会执行以下操作：
1. 导出所有必要的 Docker 镜像为 `.tar` 文件
2. 复制项目源代码和配置文件
3. 包含部署脚本
4. 创建压缩包

### 步骤 3：获取离线包

打包完成后，会在 `docker/offline-package/` 目录下生成类似 `prod-platform-ai-offline-20260511_143022.zip` 的压缩包。

## 传输离线包

将生成的压缩包传输到目标服务器：

- 使用 U 盘、移动硬盘等物理介质
- 使用内网文件传输工具
- 使用 scp (Linux): `scp prod-platform-ai-offline-xxx.zip user@server:/path/`

## 离线部署（无网络环境）

### Windows 环境部署

1. **解压离线包**

   将压缩包解压到目标目录，例如 `C:\prod-platform-ai\`

2. **运行部署脚本**

   进入解压后的目录，右键以管理员身份运行：

   ```powershell
   .\deploy-offline.ps1
   ```

3. **按照提示操作**

   脚本会自动：
   - 导入 Docker 镜像
   - 准备项目文件
   - 配置环境变量
   - 启动服务

### Linux 环境部署

1. **解压离线包**

   ```bash
   unzip prod-platform-ai-offline-xxx.zip -d /opt/prod-platform-ai
   cd /opt/prod-platform-ai
   ```

2. **给脚本添加执行权限并运行**

   ```bash
   chmod +x deploy-offline.sh
   ./deploy-offline.sh
   ```

3. **按照提示操作**

## 手动部署（可选）

如果自动脚本遇到问题，可以手动部署：

### 1. 导入 Docker 镜像

```bash
# Windows/Linux
docker load -i docker-images/ai-form-backend-builder.tar
docker load -i docker-images/ai-form-backend-runtime.tar
docker load -i docker-images/ai-form-frontend-builder.tar
docker load -i docker-images/mysql.tar
```

### 2. 准备项目文件

将 `source/` 目录下的所有文件复制到目标部署目录。

### 3. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 文件，配置数据库等信息
```

### 4. 启动服务

```bash
docker-compose up -d --build
```

## 验证部署

### 检查服务状态

```bash
docker-compose ps
```

### 查看服务日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
docker-compose logs -f frontend
```

### 访问应用

- 前端: http://localhost
- 后端 API: http://localhost:6173
- API 文档: http://localhost:6173/docs

## 常用管理命令

```bash
# 启动服务
docker-compose up -d

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 更新服务（需要重新构建镜像）
docker-compose up -d --build
```

## 常见问题

### Q: Docker 镜像导入失败？

A: 确保 Docker 服务正在运行，检查磁盘空间是否充足。

### Q: 服务启动后无法访问？

A: 检查：
1. 端口是否被占用
2. 防火墙是否阻止访问
3. 容器是否正常运行 (`docker-compose ps`)
4. 容器日志是否有错误 (`docker-compose logs`)

### Q: 如何更新离线部署的应用？

A: 在有网络环境重新打包，然后在部署环境：
1. 停止服务: `docker-compose down`
2. 备份数据（如果需要）
3. 使用新离线包重新部署

### Q: 数据库数据如何持久化？

A: 数据默认通过 Docker volume 持久化，除非删除 volume，否则数据不会丢失。

### Q: 如何修改配置？

A: 编辑 `.env` 文件，然后重启服务：
```bash
docker-compose down
docker-compose up -d
```

## 文件结构

```
docker/
├── package-offline.ps1          # 离线打包脚本 (Windows)
├── deploy-offline.ps1           # 离线部署脚本 (Windows)
├── deploy-offline.sh            # 离线部署脚本 (Linux)
├── OFFLINE_DEPLOY_GUIDE.md      # 本指南
└── offline-package/             # 打包输出目录（自动创建）
    └── prod-platform-ai-offline-xxx.zip
```

## 技术支持

如遇到问题，请检查：
1. Docker 和 Docker Compose 版本是否兼容
2. 系统资源是否充足（内存、磁盘空间）
3. 日志文件中的错误信息
4. .env 配置是否正确
