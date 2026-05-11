# 🚀 部署指南 (Deployment Guide)

本文档详细说明了 AI 驱动动态表单平台的部署流程，涵盖本地开发、生产环境以及内网离线构建场景。

---

## 📋 目录
1. [环境要求](#环境要求)
2. [本地开发部署](#本地开发部署)
3. [生产环境部署 (Docker)](#生产环境部署-docker)
4. [内网离线构建方案](#内网离线构建方案)
5. [配置管理](#配置管理)
6. [常见问题排查](#常见问题排查)

---

## 🛠️ 环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| **Python** | >= 3.10 | 后端运行环境 |
| **Node.js** | >= 18.x | 前端构建与开发 |
| **Docker** | >= 20.10 | 容器化部署（可选） |
| **Git** | 最新版 | 代码管理 |

---

## 💻 本地开发部署

适用于开发人员快速启动项目进行调试。

### 1. 后端启动
```bash
cd backend
pip install -r requirements.txt
python -m app.main
```
*   **访问地址**: `http://localhost:6173`
*   **健康检查**: `http://localhost:6173/health`
*   **快捷方式**: 运行 `deploy/start-backend.bat`

### 2. 前端启动
```bash
cd frontend
npm install
npm run dev
```
*   **访问地址**: `http://localhost:5173` (默认 Vite 端口)
*   **代理配置**: 前端会自动将 `/api` 请求转发至后端 `6173` 端口。
*   **快捷方式**: 运行 `deploy/start-frontend.bat`

---

## 🐳 生产环境部署 (Docker)

使用 Docker Compose 一键拉起前后端服务，推荐用于测试和生产环境。

### 1. 准备环境变量
在项目根目录创建 `.env` 文件：
```env
LLM_API_KEY=your_api_key_here
LLM_BASE_URL=https://api.your-provider.com/v1
LLM_MODEL=gpt-4
```

### 2. 启动服务
```bash
docker-compose -f deploy/docker-compose.yml up -d --build
```

### 3. 访问系统
*   **前端页面**: `http://localhost:80`
*   **后端接口**: `http://localhost:6173`

---

## 🔒 内网离线构建方案

针对无法连接外网的内网环境，项目提供了完整的离线依赖打包方案。

### 1. 在外网机器上打包依赖
在有网络的机器上执行以下 PowerShell 脚本：
```powershell
.\deploy\pack-deps.ps1
```
该脚本会将 Python (`backend/vendor/`) 和 Node (`frontend/vendor/`) 的所有依赖包下载到本地。

### 2. 提交到代码仓库
将生成的 `vendor` 目录提交到 Git：
```bash
git add backend/vendor/ frontend/vendor/
git commit -m "chore: update offline dependencies"
```

### 3. 在内网机器上构建
将代码同步到内网后，使用相同的 Docker Compose 命令即可自动利用本地 vendor 包进行构建：
```bash
docker-compose -f deploy/docker-compose.yml up -d --build
```

---

## ⚙️ 配置管理

### 后端配置
*   **业务配置**: 位于 `backend/config/` 目录，包含本体定义 (`ontologies/`)、场景映射 (`scenes/`) 等。
*   **日志配置**: 默认输出到 `backend/logs/app.log`。

### 前端配置
*   **API 地址**: 通过 `VITE_API_BASE_URL` 在构建时注入。
*   **Nginx 代理**: 生产环境下由 `frontend/nginx.conf` 处理反向代理和 SPA 路由回退。

---

## ❓ 常见问题排查

1.  **后端启动失败**: 检查 `backend/logs/app.log` 确认是否缺少数据库文件或 LLM 配置错误。
2.  **前端跨域问题**: 确保 Nginx 配置中 `/api/` 路径正确指向了后端容器名 `backend`。
3.  **离线构建报错**: 确认 `vendor` 目录下的 `.tgz` 或 `.whl` 文件完整且未被 `.gitignore` 忽略。
