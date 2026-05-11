# Docker 部署配置

本目录包含内网离线部署所需的所有脚本和配置文件，支持私有镜像仓库加速。

---

## 📁 目录结构

```
docker/
├── build-base-images.ps1     # 外网构建/导出基础镜像脚本 (PowerShell)
├── build-export.ps1          # 外网构建/导出应用镜像脚本 (PowerShell)
├── import-base-images.ps1    # 内网导入基础镜像脚本 (PowerShell)
├── deploy-offline.ps1        # 内网部署脚本 (PowerShell)
├── docker-compose.offline.yml # 内网部署配置
├── .gitignore                # Git 忽略规则
└── README.md                 # 本文档
```

---

## 🏗️ 工作流程

### 完整流程（首次使用）

```
外网机器                    内网服务器
  |                            |
  ├─ 1. build-base-images.ps1 ─┐
  │                            │
  ├─ [导出基础镜像] ────────┐
  │                         │
  └─ 2. build-export.ps1    │
                            │
                      ┌─────┴─────┐
                      │传输镜像包  │
                      └─────┬─────┘
                            │
                            ├─ 1. import-base-images.ps1
                            │
                            └─ 2. deploy-offline.ps1
```

### 快速流程（已有基础镜像）

```
外网机器                    内网服务器
  |                            |
  └─ 1. build-export.ps1 ────┐
                            │
                      ┌─────┴─────┐
                      │传输应用包  │
                      └─────┬─────┘
                            │
                            └─ 1. deploy-offline.ps1
```

---

## 🚀 快速开始

### 第一步：在外网机器上准备基础镜像（首次）

```powershell
# 进入 docker 目录
cd docker

# 右键 -> 使用 PowerShell 运行，或执行：
.\build-base-images.ps1
```

这个脚本会：
1. 连接私有镜像仓库（`10.86.12.11:20200`）
2. 优先从私有仓库拉取已有的基础镜像
3. 如果仓库没有，从Docker Hub拉取并推送到私有仓库
4. 导出基础镜像为tar文件

导出的文件：
- `python-3.10-slim.tar` - 后端基础镜像
- `node-20-alpine.tar` - 前端构建基础镜像  
- `nginx-alpine.tar` - 前端运行基础镜像

### 第二步：在外网机器上构建应用镜像

```powershell
# 进入 docker 目录
cd docker

# 右键 -> 使用 PowerShell 运行，或执行：
.\build-export.ps1
```

选择要构建的组件（后端/前端/全部）。

### 第三步：传输文件到内网

需要传输的文件（根据构建选择）：
- `prod-platform-ai-backend.tar` - 后端应用镜像
- `prod-platform-ai-frontend.tar` - 前端应用镜像
- `docker-compose.offline.yml` - 部署配置
- `deploy-offline.ps1` - 部署脚本

如果内网也需要基础镜像，一并传输：
- `python-3.10-slim.tar`
- `node-20-alpine.tar`
- `nginx-alpine.tar`

### 第四步：在内网服务器上导入基础镜像（首次）

```powershell
# 进入 docker 目录
cd docker

# 右键 -> 使用 PowerShell 运行，或执行：
.\import-base-images.ps1
```

有两种导入方式：
1. **推荐：从私有仓库拉取** - 速度快，无需传输大文件
2. **从本地tar文件导入** - 适用于无法访问仓库的情况

### 第五步：在内网服务器上部署

```powershell
# 进入 docker 目录
cd docker

# 1. 先加载应用镜像（如果使用方式2）
docker load -i prod-platform-ai-backend.tar
docker load -i prod-platform-ai-frontend.tar

# 2. 右键 -> 使用 PowerShell 运行部署脚本
.\deploy-offline.ps1
```

---

## 📋 文件说明

| 文件名 | 说明 | 使用环境 |
|--------|------|----------|
| `build-base-images.ps1` | 外网构建/导出基础镜像脚本 | 外网机器 |
| `build-export.ps1` | 外网构建/导出应用镜像脚本 | 外网机器 |
| `import-base-images.ps1` | 内网导入基础镜像脚本 | 内网服务器 |
| `deploy-offline.ps1` | 内网部署应用脚本 | 内网服务器 |
| `docker-compose.offline.yml` | 内网部署配置文件 | 内网服务器 |
| `*.tar` | 镜像包文件（构建后生成） | 两边都需要 |

---

## 🔐 私有仓库配置

脚本已配置私有仓库：
- **地址**: `10.86.12.11:20200`
- **用户**: `dingpq`
- **项目**: `crm-pgcent`

如需修改，编辑脚本开头的配置部分。

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

1. **镜像包较大**: `.tar` 文件较大，传输时请耐心等待，优先使用私有仓库
2. **PowerShell 执行策略**: 如果无法运行脚本，可能需要设置执行策略：`Set-ExecutionPolicy RemoteSigned -Scope CurrentUser`
3. **配置修改**: 修改配置后需要重启容器生效
4. **基础镜像只需一次**: 基础镜像只需构建一次，后续可以重复使用
