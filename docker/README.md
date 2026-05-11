# Docker 部署配置

本目录包含内网离线部署所需的所有脚本和配置文件。

---

## 📁 目录结构

```
docker/
├── build-export.ps1          # 外网构建导出脚本 (PowerShell)
├── deploy-offline.ps1        # 内网部署脚本 (PowerShell)
├── docker-compose.offline.yml # 内网部署配置
├── .gitignore                # Git 忽略规则
└── README.md                 # 本文档
```

---

## 🚀 快速开始

### 第一步：在外网机器上构建

```powershell
# 进入 docker 目录
cd docker

# 右键 -&gt; 使用 PowerShell 运行，或执行：
.\build-export.ps1
```

或者手动执行：

```powershell
# 进入 docker 目录
cd docker

# 构建后端镜像
docker build -f ../backend/Dockerfile -t prod-platform-ai-backend:latest ../

# 构建前端镜像
docker build -f ../frontend/Dockerfile -t prod-platform-ai-frontend:latest ../

# 导出镜像到当前 docker 目录
docker save prod-platform-ai-backend:latest -o prod-platform-ai-backend.tar
docker save prod-platform-ai-frontend:latest -o prod-platform-ai-frontend.tar
docker save mysql:8.0 -o mysql-8.0.tar
```

### 第二步：传输文件到内网

将整个 `docker` 目录传输到内网服务器（注意：`.tar` 文件比较大）。

### 第三步：在内网服务器上部署

```powershell
# 进入 docker 目录
cd docker

# 1. 先加载镜像
docker load -i prod-platform-ai-backend.tar
docker load -i prod-platform-ai-frontend.tar
docker load -i mysql-8.0.tar

# 2. 右键 -&gt; 使用 PowerShell 运行部署脚本
.\deploy-offline.ps1
```

---

## 📋 文件说明

| 文件名 | 说明 | 使用环境 |
|--------|------|----------|
| `build-export.ps1` | 外网构建导出脚本 | 外网机器 |
| `deploy-offline.ps1` | 内网部署脚本 | 内网服务器 |
| `docker-compose.offline.yml` | 内网部署配置文件 | 内网服务器 |
| `*.tar` | 镜像包文件（构建后生成） | 两边都需要 |

---

## 🌐 访问地址

部署成功后访问：

- **前端应用**: http://内网服务器IP
- **后端API**: http://内网服务器IP:6173
- **API文档**: http://内网服务器IP:6173/docs

---

## 🔧 常用命令

```powershell
# 查看服务状态
docker-compose -f docker-compose.offline.yml ps

# 查看日志
docker-compose -f docker-compose.offline.yml logs -f

# 停止服务
docker-compose -f docker-compose.offline.yml down

# 重启服务
docker-compose -f docker-compose.offline.yml restart
```

---

## 📝 注意事项

1. **镜像包较大**: `.tar` 文件较大，传输时请耐心等待
2. **PowerShell 执行策略**: 如果无法运行脚本，可能需要设置执行策略：`Set-ExecutionPolicy RemoteSigned -Scope CurrentUser`
3. **配置修改**: 修改配置后需要重启容器生效
4. **数据持久化**: MySQL 数据通过 Docker volume 持久化，不会丢失
