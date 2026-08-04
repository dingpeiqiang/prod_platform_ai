# Docker 构建工具

商用主路径：

| 组件 | 技术栈 | 端口 | 构建方式 |
|------|--------|------|----------|
| 后端 `backend-app` | Spring Boot 3.4 / JDK 17 | **6174** | **shell 打包 jar** → Docker 仅拷贝运行 |
| 前端 `frontend` | Vue 3 + Vite → Nginx | **80** | 基础镜像 + 应用镜像 |

> 旧 Python `backend/`（6173）已退出容器化主路径。

## 后端（shell 打包 + Docker 镜像）

```bash
# 仅打包 jar（产出 docker/dist/app.jar）
./docker/package-backend.sh

# 打包 + 构建镜像
./docker/build-backend.sh

# 指定版本 / 推送
IMAGE_TAG=2.1 ./docker/build-backend.sh
PUSH=1 REGISTRY_PASSWORD='***' ./docker/build-backend.sh
```

- 打包脚本：[`package-backend.sh`](package-backend.sh)（宿主机 `mvn package`）
- 镜像脚本：[`build-backend.sh`](build-backend.sh)（先打包再 `docker build`）
- Dockerfile：[`Sitech.BJ.Dockerfile.backend`](Sitech.BJ.Dockerfile.backend)（仅 JRE + 拷贝 `docker/dist/app.jar`）

### 运行

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

## 前端基础镜像

| 镜像 | Dockerfile | 版本 |
|------|-----------|------|
| 前端 base | [Dockerfile.base.frontend](Dockerfile.base.frontend) | 1.2 |

```powershell
.\docker\docker-manager.ps1
# 1/3 → 构建/推送前端基础镜像
```

## 前端应用镜像

```powershell
docker build -f docker/Sitech.BJ.Dockerfile.frontend -t prod-platform-frontend:1.0 .
```

依赖 `prod-platform-frontend-base:1.2`（私有仓库或本地已构建）。

## 前后端联调（同网络）

前端 Nginx 将 `/api/` 反代到 `http://backend:6174`（见 `frontend/nginx.conf`）。

```powershell
docker network create prod-ai
docker run -d --name backend --network prod-ai -p 6174:6174 prod-platform-backend:2.0
docker run -d --name frontend --network prod-ai -p 80:80 prod-platform-frontend:1.0
```

## 版本同步（仅前端）

修改前端基础镜像 tag 时，需同时更新：

1. `docker/docker-manager.ps1` 中 `ImageTag`
2. `Sitech.BJ.Dockerfile.frontend` 的 `FROM` 行
