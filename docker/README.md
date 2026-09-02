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
| 后端 | [Dockerfile.base.backend](Dockerfile.base.backend) | **2.0** | JDK 17 + **完整 Maven 本地仓库** |
| 前端 | [Dockerfile.base.frontend](Dockerfile.base.frontend) | **2.0** | Node 20 + **Nginx 1.30.4（CVE 修复版）** + npm 依赖 |

> 前端基础镜像 2.0：Nginx 升级至官方 1.30.4（修复 CVE-2026-42945 / CVE-2026-9256 等 7 项 2026 年披露漏洞），
> 非 root（uid=101）运行，监听端口统一为 **6173**。

### 无外网说明（后端）

构建日志里的：

```text
Downloading from aliyun-central: https://maven.aliyun.com/.../jboss-logging-...
```

表示 **Maven 正在从远程仓库下载依赖**（`jboss-logging` 是 Hibernate/日志传递依赖）。  
公司容器若不能出网，应用镜像阶段会失败。

正确流程：

1. **有网环境**（或能访问公司 Nexus）构建并推送基础镜像 → 依赖全部打进 `~/.m2`
2. **无外网环境**只拉基础镜像，应用镜像用 `mvn -o` **离线打包**，不再访问阿里云

```powershell
# 有网：构建基础镜像（可指定公司 Maven）
docker build -f docker/Dockerfile.base.backend `
  --build-arg MAVEN_MIRROR_URL=http://你的Nexus/repository/maven-public `
  -t prod-platform-backend-base:2.0 .
# 或用 docker-manager.ps1 推到 10.86.12.11

# 无外网：只构建应用镜像（依赖来自 base）
docker build -f docker/Sitech.BJ.Dockerfile.backend -t prod-platform-backend:2.0 .
```

```powershell
.\docker\docker-manager.ps1
# 1/3 → 后端基础镜像；4/6 → 前端基础镜像
```

## 应用镜像（App Images）

| 应用 | Dockerfile | 基础镜像 |
|------|-----------|---------|
| 后端 | [Sitech.BJ.Dockerfile.backend](Sitech.BJ.Dockerfile.backend) | `prod-platform-backend-base:2.4` |
| 前端 | [Sitech.BJ.Dockerfile.frontend](Sitech.BJ.Dockerfile.frontend) | `prod-platform-frontend-base:2.0` |

```powershell
# 在项目根目录执行（需先有对应基础镜像）
docker build -f docker/Sitech.BJ.Dockerfile.backend -t prod-platform-backend:2.0 .
docker build -f docker/Sitech.BJ.Dockerfile.frontend -t prod-platform-frontend:1.0 .
```

### 运行后端（外部配置挂载，推荐）

业务配置（数据源、LLM、profile 等）**不要**写在 `docker run -e` 里，改为挂载目录到 `/config`。

镜像启动参数已包含：`--spring.config.additional-location=optional:file:/config/`  
挂载目录中的 `application.yml` / `application-{profile}.yml` 会覆盖 jar 内同名配置。

1. 按环境修改 [`docker/config/application.yml`](config/application.yml)（库地址、密码、API Key 等）
2. 启动时只挂载配置目录：

```powershell
# 项目根目录执行；先编辑 docker/config/application.yml
docker run -d --name backend -p 6174:6174 `
  -v ${PWD}/docker/config:/config:ro `
  prod-platform-backend:2.0
```

切换演示环境：把 `docker/config/application.yml` 里 `spring.profiles.active` 改为 `demo`（同目录已有 `application-demo.yml`），重启容器即可，无需重建镜像。

> 仅保留容器运行时变量（可选）：`-e SERVER_PORT=6174`、`-e JAVA_OPTS=...`。业务项一律放外部 yml。

健康检查：`GET http://localhost:6174/health`

### 前后端联调（同网络）

前端 Nginx 将 `/api/` 反代到 `http://backend:6174`。

```powershell
docker network create prod-ai
docker run -d --name backend --network prod-ai -p 6174:6174 `
  -v ${PWD}/docker/config:/config:ro `
  prod-platform-backend:2.0
docker run -d --name frontend --network prod-ai -p 80:80 prod-platform-frontend:1.0
```

## 版本同步

修改基础镜像 tag 时，需同时更新：

1. `docker/docker-manager.ps1` 中对应 `ImageTag`
2. `Sitech.BJ.Dockerfile.backend` / `Sitech.BJ.Dockerfile.frontend` 的 `FROM` 行

> 仓库凭据通过环境变量 `DOCKER_REGISTRY_USER` / `DOCKER_REGISTRY_PASSWORD` 注入，不再写入脚本。
