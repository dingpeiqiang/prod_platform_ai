# Docker 构建工具

前后端均采用 **基础镜像 → 应用镜像** 两层结构（与前端一致）。

| 组件 | 技术栈 | 端口 |
|------|--------|------|
| 后端 `backend-app` | Spring Boot 3.4 / JDK 17 + Spring AI | **6174** |
| 前端 `frontend` | Vue 3 + Vite → Nginx | **80** |

> Spring Boot 保持 3.4.x（Spring AI 不兼容 2.7）。旧 Python `backend/` 已退出主路径。

## 基础镜像（Base Images）

| 镜像 | Dockerfile | 版本 | 说明 |
|------|-----------|------|------|
| 后端 | [Dockerfile.base.backend](Dockerfile.base.backend) | **2.0** | JDK 17 + Maven + 预拉依赖 |
| 前端 | [Dockerfile.base.frontend](Dockerfile.base.frontend) | 1.2 | Node 20 + Nginx + npm 依赖 |

```powershell
.\docker\docker-manager.ps1
# 1/3 → 后端基础镜像；4/6 → 前端基础镜像
```

## 应用镜像（App Images）

| 应用 | Dockerfile | 基础镜像 |
|------|-----------|---------|
| 后端 | [Sitech.BJ.Dockerfile.backend](Sitech.BJ.Dockerfile.backend) | `prod-platform-backend-base:2.0` |
| 前端 | [Sitech.BJ.Dockerfile.frontend](Sitech.BJ.Dockerfile.frontend) | `prod-platform-frontend-base:1.2` |

```powershell
# 在项目根目录执行（需先有对应基础镜像）
docker build -f docker/Sitech.BJ.Dockerfile.backend -t prod-platform-backend:2.0 .
docker build -f docker/Sitech.BJ.Dockerfile.frontend -t prod-platform-frontend:1.0 .
```

### 运行后端

```powershell
docker run -d --name backend -p 6174:6174 `
  -e SPRING_DATASOURCE_URL="jdbc:mysql://host.docker.internal:3306/prodplatformai?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" `
  -e SPRING_DATASOURCE_USERNAME=prodplatformai `
  -e SPRING_DATASOURCE_PASSWORD=prodplatformai@134 `
  -e LLM_ENABLED=true `
  -e LLM_API_KEY=sk-xxx `
  -e LLM_BASE_URL=https://your-openai-compatible-host `
  -e LLM_MODEL=gpt-4o-mini `
  prod-platform-backend:2.0
```

健康检查：`GET http://localhost:6174/health`

### 前后端联调（同网络）

前端 Nginx 将 `/api/` 反代到 `http://backend:6174`。

```powershell
docker network create prod-ai
docker run -d --name backend --network prod-ai -p 6174:6174 prod-platform-backend:2.0
docker run -d --name frontend --network prod-ai -p 80:80 prod-platform-frontend:1.0
```

## 版本同步

修改基础镜像 tag 时，需同时更新：

1. `docker/docker-manager.ps1` 中对应 `ImageTag`
2. `Sitech.BJ.Dockerfile.backend` / `Sitech.BJ.Dockerfile.frontend` 的 `FROM` 行
