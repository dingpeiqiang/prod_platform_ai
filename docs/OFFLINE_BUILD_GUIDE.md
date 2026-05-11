# 内网离线构建方案

## 方案概述

将所有外部依赖（Python pip 包 + Node npm 包）**预先下载打包**，存入项目 git 仓库的 `vendor/` 目录，内网构建时直接从本地加载，**完全不访问外部网络**。

```
外网机器：执行打包脚本 → vendor/ 目录 → git commit/push
内网服务器：git pull → docker-compose build（读本地 vendor）→ 运行
```

---

## 目录结构

```
prod_platform_ai/
├── backend/
│   ├── vendor/                  ← Python 离线包目录（*.whl / *.tar.gz）
│   │   ├── fastapi-0.115.0-*.whl
│   │   ├── uvicorn-0.32.0-*.whl
│   │   └── ...（共约 60+ 个包）
│   └── Dockerfile.offline       ← 离线构建专用（从 vendor/ 安装，无外网依赖）
├── frontend/
│   ├── vendor/                  ← npm 离线包目录（*.tgz）
│   │   ├── vue-3.4.x.tgz
│   │   ├── element-plus-2.5.x.tgz
│   │   └── ...（共约 200+ 个包）
│   └── Dockerfile.offline       ← 离线构建专用（从 vendor/ 安装，无外网依赖）
├── scripts/
│   ├── pack-backend-deps.sh     ← 后端打包脚本（Linux/Mac）
│   ├── pack-backend-deps.ps1    ← 后端打包脚本（Windows）
│   ├── pack-frontend-deps.sh    ← 前端打包脚本（Linux/Mac）
│   └── pack-frontend-deps.ps1   ← 前端打包脚本（Windows）
├── docker-compose.yml           ← 离线构建编排（引用 Dockerfile.offline）
├── pack-offline-deps.ps1        ← 一键打包（Windows）
└── pack-offline-deps.sh         ← 一键打包（Linux/Mac）
```

---

## 使用流程

### Step 1：在有网络的机器上打包依赖

> **只需要在依赖版本升级时重新执行**，日常迭代无需重复打包。

**Windows：**
```powershell
# 在项目根目录执行
.\pack-offline-deps.ps1

# 可选参数
.\pack-offline-deps.ps1 -BackendOnly   # 只打包后端
.\pack-offline-deps.ps1 -FrontendOnly  # 只打包前端
.\pack-offline-deps.ps1 -SkipGitAdd   # 跳过 git 提交询问
```

**Linux/Mac：**
```bash
chmod +x pack-offline-deps.sh
./pack-offline-deps.sh

# 可选参数
./pack-offline-deps.sh --backend-only
./pack-offline-deps.sh --frontend-only
./pack-offline-deps.sh --skip-git
```

### Step 2：提交到 git

打包脚本执行完成后会询问是否自动提交，选 `y` 即可。
也可以手动执行：

```bash
git add backend/vendor/ frontend/vendor/
git commit -m "chore: update offline vendor dependencies $(date +%Y-%m-%d)"
git push
```

### Step 3：内网服务器拉取并构建

```bash
# 拉取代码（含 vendor/）
git pull

# 离线构建并启动（完全不访问外网）
docker-compose up -d --build
```

---

## 离线 Dockerfile 核心原理

### 后端（Python）

```dockerfile
# 复制离线包
COPY backend/vendor/ ./vendor/

# 从本地安装（--no-index 禁止访问 PyPI）
RUN pip install \
    --no-cache-dir \
    --no-index \
    --find-links=./vendor \
    -r requirements.txt
```

### 前端（Node）

```dockerfile
# 复制离线包
COPY frontend/vendor/ ./vendor/

# 将 .tgz 加入 npm 本地 cache
RUN find ./vendor -name "*.tgz" -exec npm cache add {} \;

# 优先从 cache 安装（不访问 npm registry）
RUN npm ci --prefer-offline
```

---

## 依赖更新流程

当 `requirements.txt` 或 `package.json` 中依赖版本变更时：

1. 在有网络的机器上重新执行打包脚本
2. 提交更新后的 `vendor/` 目录到 git
3. 内网服务器 `git pull` 后重新 `docker-compose build`

---

## 注意事项

| 事项 | 说明 |
|------|------|
| vendor 目录大小 | Python ~80MB，Node ~300MB，合计约 380MB |
| git 仓库体积 | 首次提交较大，后续增量更新 |
| 平台兼容性 | Python 包已指定 `manylinux2014_x86_64` + Python 3.10，与容器平台一致 |
| 依赖未覆盖 | 若某个包无预编译 wheel，打包脚本会下载 sdist（源码包），容器构建时需要 gcc 编译 |
| git lfs | 如果公司 git 服务器对大文件有限制，建议启用 git-lfs 管理 vendor 目录 |

---

## Git LFS 配置（可选）

如果 git 仓库对大文件有限制，可以启用 git-lfs：

```bash
git lfs install
git lfs track "backend/vendor/*.whl"
git lfs track "backend/vendor/*.tar.gz"
git lfs track "frontend/vendor/*.tgz"
git add .gitattributes
git commit -m "chore: configure git-lfs for vendor packages"
```

---

## 验证离线构建

在完全断网环境下验证：

```bash
# 断开网络后执行构建
docker-compose build --no-cache

# 如果构建成功，说明完全离线可用
docker-compose up -d
```
