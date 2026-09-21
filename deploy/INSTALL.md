# Prod Platform AI — 安装部署步骤文档

本文档描述基于 `deploy/` 目录产出 tar 包在**虚拟机（纯 MySQL 8.0）**环境的一键安装与部署流程。

## 部署目录规划（按 crm-pgcent-mng 约定）

部署根目录（根目录）**可配置**，约定在根目录下创建 `crm-pgcent-mng/`，其内前后端分别落位：

```
<根目录>/                                  # 如 /opt（可配置）
└── crm-pgcent-mng/                        # APP_HOME = <根目录>/crm-pgcent-mng
    ├── installer/                         # 发布包存放目录（deployup.sh 从这里取 tar 包）
    │   ├── prod-ai-backend.tar.gz
    │   └── prod-ai-frontend.tar.gz
    ├── prod-ai-backend/                   # 后端：app.jar + bin/ + conf/ + config/ + logs/ + data/ + uploads/
    │   ├── app.jar
    │   ├── bin/start.sh                   # 启动/停止/重启/状态
    │   ├── bin/deployup.sh                # 更新 jar（从 installer 取包）
    │   ├── conf/prod-ai-backend.service
    │   └── config/application.yml
    └── prod-ai-frontend/                  # 前端
        ├── html/                          # 解压原始静态产物
        ├── dist/                          # Nginx 静态根
        ├── conf/prod-ai.conf              # Nginx 站点配置（静态文件）
        └── bin/                           # deployup.sh 更新静态 / start.sh 启动 / stop.sh 停止
```

> 默认部署路径为 `/opt/crm-pgcent-mng`；若根目录不同（如 `/home`、`/data`），
> 全文相应路径前缀同步替换即可。脚本与单元均以 `APP_HOME` 变量标识该目录。

部署架构（单机）:

```
                    ┌──────────────────────────────────────────────────┐
                    │  VM  (Linux / 用户浏览器)                        │
                    │                                                  │
 浏览器 ──80──► Nginx ──/api、/ws──► Backend:6174 ──► MySQL:172.30.0.232:8866
                    │  (${APP_HOME}/prod-ai-frontend/dist)             │
                    └──────────────────────────────────────────────────┘
```

## 目录说明（源码侧 `deploy/`）

| 路径 | 说明 |
|------|------|
| `deploy/build-tar.ps1` | 构建机（Windows）打包脚本，产出前后端/数据库三个 tar 包 |
| `deploy/build-bundle.ps1` | 构建机（Windows）整体交付包脚本，产出 `crm-pgcent-mng.tar.gz`（含 installer + 已解压前后端 + mysql） |
| `deploy/mysql/` | MySQL DDL/初始化数据包（含 `install_mysql.sh`） |
| `deploy/backend/` | 后端启动脚本 `start.sh`、部署更新脚本 `deployup.sh`、systemd 单元、部署专用外部配置 `config/application.yml` |
| `deploy/frontend/` | 前端 Nginx 站点配置 `prod-ai.conf`、部署更新脚本 `deployup.sh`、启停脚本 `start.sh` / `stop.sh` |
| `deploy/out/` | 打包产物（`prod-ai-*.tar.gz` 与整体包 `crm-pgcent-mng.tar.gz`） |

---

## 一、构建机打包（Windows）

打包前置：
- 后端已执行 Maven 打包
  ```powershell
  cd backend-app
  mvn -s .mvn/local-settings.xml -DskipTests clean package
  ```
- 前端已执行生产构建
  ```powershell
  cd frontend
  npm ci
  npm run build
  ```

### 1.1 整体交付包（推荐）

一条命令产出可直接交付的 `crm-pgcent-mng.tar.gz`：

```powershell
powershell -ExecutionPolicy Bypass -File deploy\build-tar.ps1     # 先产出前后端/数据库三个 tar 包
powershell -ExecutionPolicy Bypass -File deploy\build-bundle.ps1  # 再组装整体交付包
```

产物：`deploy/out/crm-pgcent-mng.tar.gz`，包内结构：

```
crm-pgcent-mng/
├── installer/                     # 原始发布包（供 deployup.sh 后续更新）
│   ├── prod-ai-backend.tar.gz
│   └── prod-ai-frontend.tar.gz
├── prod-ai-backend/               # 后端（已解压到位）
│   ├── app.jar
│   ├── bin/{start,deployup}.sh
│   ├── conf/prod-ai-backend.service
│   └── config/application.yml
├── prod-ai-frontend/              # 前端（已解压到位，dist/ 已预置）
│   ├── html/  dist/
│   ├── conf/prod-ai.conf
│   └── bin/{start,stop,deployup}.sh
└── mysql/                         # 数据库 DDL/初始化脚本
```

上传到虚拟机**任意目录**解压即可，随后按第二、三章调整 `config/application.yml`、执行建表并启动。见「二、虚拟机部署 → 整体包快速部署」。

### 1.2 分体打包（可选）

若只需单个 tar 包：

```powershell
powershell -ExecutionPolicy Bypass -File deploy\build-tar.ps1
```

产物生成于 `deploy/out/`：
- `prod-ai-backend.tar.gz`（jar + `bin/{start,deployup}.sh` + `conf/` + `config/`）→ 解压到 `<根目录>/crm-pgcent-mng/prod-ai-backend`
- `prod-ai-frontend.tar.gz`（前端 `html/` + `conf/prod-ai.conf` + `bin/{start,stop,deployup}.sh`）→ 解压到 `<根目录>/crm-pgcent-mng/prod-ai-frontend`
- `prod-ai-mysql.tar.gz`（MySQL DDL/初始化脚本）

将三个 tar.gz 上传到目标虚拟机，建议目录 `/root/deploy-pack/`。
首次部署按下方步骤解压到位；后续更新只需把新包放进 `${APP_HOME}/installer/` 并执行各自的 `bin/deployup.sh`。

---

## 二、虚拟机部署

### 整体包快速部署（`crm-pgcent-mng.tar.gz`）

拿到整体交付包后，上传到虚拟机**任意目录**（如下例 `/opt`），解压后只需改配置、建表、启动三步：

```bash
# 1) 上传并解压（解压后自动得到 crm-pgcent-mng/ 目录）
cd /opt
tar -xzf /path/to/crm-pgcent-mng.tar.gz
cd /opt/crm-pgcent-mng
ls    # installer/  prod-ai-backend/  prod-ai-frontend/  mysql/

# 2) 调整后端配置（库地址/账号密码/JWT）
vi prod-ai-backend/config/application.yml

# 3) 建表与初始化数据（库/账号已由 DBA 建好）
cd mysql && bash install_mysql.sh && cd ..

# 4) 启动后端
cd prod-ai-backend && bash bin/start.sh start && cd ..

# 5) 启动 Nginx（bin/start.sh 自动加载包内 conf/prod-ai.conf 并修正 root/后端地址）
cd prod-ai-frontend && bash bin/start.sh && cd ..

# 6) 验证
curl -s http://127.0.0.1:6174/health    # 后端
curl -s http://127.0.0.1/health         # 经 Nginx
```

> 解压目录即 `APP_HOME`（上例 `/opt/crm-pgcent-mng`），前后端脚本均按此自动推导，无需额外配置；
> `bin/start.sh` 以 `nginx -c <包内 conf/prod-ai.conf>` 加载完整 Nginx 主配置，并按实际 `APP_HOME`
> 自动修正其中的 `root` 静态目录，其余（upstream 等）保持模板默认，无需手改 conf、无需拷贝到 `/etc/nginx/conf.d/`。
> 后续升级把新 tar 包放进 `installer/`，执行前后端 `bin/deployup.sh` 即可，见「三、升级部署」。

以下 0)~5) 为分步详解，整体包与分体包部署均适用。

### 0) 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| OS | CentOS 7/8、Rocky Linux、Ubuntu 20.04+ | 64 位 |
| JDK | **必须 17** | 后端 Spring Boot 运行（启动脚本会严格校验主版本） |
| MySQL | 8.0 | 后端 `poc-stq` 库（`172.30.0.232:8866`，已由 DBA 建好） |
| Nginx | 1.18+ | 前端静态托管 + 反向代理 |

安装基础依赖（举例）：
```bash
# JDK 17（必须）
yum install -y java-17-openjdk-devel    # CentOS/Rocky
# 或 apt install -y openjdk-17-jdk       # Ubuntu

# MySQL 客户端（初始化数据库用）
yum install -y mysql                     # CentOS/Rocky
# 或 apt install -y mysql-client         # Ubuntu

# Nginx
yum install -y nginx                     # CentOS/Rocky
# 或 apt install -y nginx                # Ubuntu
```

查看依赖是否就绪：
```bash
java -version        # 必须显示 17.x
mysql --version
nginx -v
```

#### 配置 JAVA_HOME（JDK 17）

若系统存在多版本 JDK，或默认 `java` 不是 17，需显式指定 JDK17 安装路径。二选一：

- **脚本方式（`bin/start.sh`）**：编辑脚本顶部的 `JAVA_HOME` 变量：
  ```bash
  vi /opt/crm-pgcent-mng/prod-ai-backend/bin/start.sh
  # 找到: JAVA_HOME="${JAVA_HOME:-}"
  # 改为: JAVA_HOME=/usr/lib/jvm/java-17-openjdk
  ```
  > `start.sh` 会先用该路径的 `java -version` 校验主版本为 17，不符则报错退出。
  查找 JDK 安装路径：`ls /usr/lib/jvm/`（CentOS/Rocky）或 `update-alternatives --list java`。

- **systemd 方式**：编辑单元中的 `Environment="JAVA_HOME=..."`
  ```bash
  vi /etc/systemd/system/prod-ai-backend.service
  # 找到: Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk"
  systemctl daemon-reload
  ```
  > 单元启动前同样会校验 JDK 主版本为 17，不符则启动失败。

---

### 1) MySQL 初始化（建表 / 初始化数据）

**目标库（已由 DBA 建好，仅需建表）：**

| 项 | 值 |
|----|----|
| 地址 | `172.30.0.232:8866` |
| 库名 | `poc-stq` |
| 账号 / 密码 | `poc-stq` / `Poc@StQ@2026` |

> 库与账号已存在，本包只负责建表与初始化数据，**不建库建账号**。
> ⚠️ `01_full_schema_ddl.sql` 为 DROP+CREATE，**严禁对已有数据的库执行**。

解压脚本：
```bash
cd /root/deploy-pack
tar -xzf prod-ai-mysql.tar.gz -C /opt/crm-pgcent-mng/prod-ai-mysql
cd /opt/crm-pgcent-mng/prod-ai-mysql
```

按顺序手动执行建表与初始化数据：
```bash
mysql -h172.30.0.232 -P8866 -upoc-stq -p'Poc@StQ@2026' poc-stq < 01_full_schema_ddl.sql
mysql -h172.30.0.232 -P8866 -upoc-stq -p'Poc@StQ@2026' poc-stq < 03_ext_schema.sql
mysql -h172.30.0.232 -P8866 -upoc-stq -p'Poc@StQ@2026' poc-stq < 02_init_data.sql
```

或使用一键脚本（按默认环境依次执行 01→03→02）：
```bash
bash install_mysql.sh
```

校验表数量：
```bash
mysql -h172.30.0.232 -P8866 -upoc-stq -p'Poc@StQ@2026' -e \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE table_schema='poc-stq';"
```

> 说明：本包为纯 MySQL 8.0 版 DDL，与仓库 `sql/`（GoldenDB 语法）不同，部署以本包为准。
> 存量库升级请勿执行 `01_full_schema_ddl.sql`（DROP+CREATE 会清数据），走仓库 `sql/` 增量路径。

---

### 2) 后端部署

#### 2.1 解压与放置

解压到 `<根目录>/crm-pgcent-mng/prod-ai-backend`（示例根目录 `/opt`）：

```bash
mkdir -p /opt/crm-pgcent-mng/prod-ai-backend
cd /root/deploy-pack
tar -xzf prod-ai-backend.tar.gz -C /opt/crm-pgcent-mng/prod-ai-backend
ls /opt/crm-pgcent-mng/prod-ai-backend
# 期望包含: app.jar  bin/  conf/  config/  logs/  data/  uploads/
```

#### 2.2 配置后端（`config/application.yml`）

后端解压后**自动读取 `config/` 目录下的外部配置 `config/application.yml`**
（启动脚本与 systemd 单元均通过 `--spring.config.additional-location=optional:file:config/`
加载，`build-tar.ps1` 已随包自带该文件）。

本部署**不使用环境变量文件**，数据源、JWT 密钥等业务配置全部直接在
`config/application.yml` 中填写：

```bash
vi /opt/crm-pgcent-mng/prod-ai-backend/config/application.yml
```

需修改项（生产务必填写真实值）：

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.url` | MySQL 连接串（已预设 `172.30.0.232:8866/poc-stq`） |
| `spring.datasource.username` / `password` | 已预设 `poc-stq` / `Poc@StQ@2026`，与步骤 1 数据库一致 |
| `prodai.auth.jwt-secret` | JWT 签名密钥，改为 32 字节随机字符串（多实例需一致） |
| `spring.sql.init.mode` | 已预设 `never`（表结构由 `deploy/mysql` 维护，禁用 jar 内 H2 初始化） |
| `prodai.llm.enabled` | LLM 总开关 |

> 注意：`config/application.yml` 中的值为**已展开的字面值**，直接改值即可，无需再配环境变量。
> 若端口、JVM 内存需调整，改 `--server.port` 参数或通过 `JAVA_OPTS`/`SERVER_PORT` 环境变量覆盖。

#### 2.3 可选：创建运行账号（推荐）

```bash
useradd -r -s /sbin/nologin prod-ai
chown -R prod-ai:prod-ai /opt/crm-pgcent-mng/prod-ai-backend
```

#### 2.4 选择启动方式（二选一）

**方式 A：systemd（推荐）**
```bash
cp /opt/crm-pgcent-mng/prod-ai-backend/conf/prod-ai-backend.service /etc/systemd/system/
# 按实际修改单元中的 APP_HOME（默认 /opt/crm-pgcent-mng）与 JAVA_HOME
vi /etc/systemd/system/prod-ai-backend.service
systemctl daemon-reload
systemctl enable --now prod-ai-backend
```

> 若手动创建了 `prod-ai` 账号请保持 `User/Group=prod-ai` 一致；否则改为 `root`。

**方式 B：bash 脚本（免 systemd）**
```bash
cd /opt/crm-pgcent-mng/prod-ai-backend
bash bin/start.sh start
```

> `bin/start.sh` 默认推导 `APP_HOME` 为包目录的父目录（即 crm-pgcent-mng）；
> 若目录结构不符，可在脚本顶部显式填写 `APP_HOME`。

#### 2.5 验证后端

```bash
curl -s http://127.0.0.1:6174/health
# 期望返回 JSON 健康状态

# 日志
tail -f /opt/crm-pgcent-mng/prod-ai-backend/logs/app.out
```

其他命令：`bash bin/start.sh status|restart|stop`。

---

### 3) 前端部署（Nginx）

#### 3.1 首次解压与放置

首次部署先手工解压到 `<根目录>/crm-pgcent-mng/prod-ai-frontend`，并把静态产物放到 `dist/`：

```bash
mkdir -p /opt/crm-pgcent-mng/prod-ai-frontend
cd /root/deploy-pack
tar -xzf prod-ai-frontend.tar.gz -C /opt/crm-pgcent-mng/prod-ai-frontend

# 首次把静态产物放到 Nginx 静态根
cd /opt/crm-pgcent-mng/prod-ai-frontend
mkdir -p dist && cp -r html/. dist/
```

#### 3.2 Nginx 站点配置加载

前端 `bin/start.sh` 默认加载包内 `conf/prod-ai.conf`（完整 Nginx 主配置）：

```bash
cd /opt/crm-pgcent-mng/prod-ai-frontend && bash bin/start.sh
```

无需往 `/etc/nginx/conf.d/` 拷贝。`start.sh` 启动前会自动按实际 `APP_HOME` 修正配置中的 `root`（静态根），
其余（upstream、监听端口等）保持模板默认，并以 `nginx -p "${NGINX_PREFIX}" -c "${SITE_CONF}"` 加载并校验（`nginx -t`）。

> 站点配置内已包含 `/api`、`/ws`、`/health` 到 `127.0.0.1:6174` 的反向代理，后端不直接对外暴露。
> `conf/prod-ai.conf` 为完整主配置（含 `events`/`http`），若被本脚本接管会自动同步 `root`；
> 若需手工维护，移除其中的 `# managed by prod-ai start.sh (auto-tuned)` 标注行即可停止自动覆盖。

#### 3.3 配置 Nginx 安装路径（可选）

若 Nginx 非系统默认安装（如源码编译到 `/usr/local/nginx`），在 `start.sh` / `stop.sh`
顶部设置 `NGINX_HOME` 指向其安装前缀（到安装根目录，非 `sbin`）：

```bash
vi /opt/crm-pgcent-mng/prod-ai-frontend/bin/start.sh
# 找到: NGINX_HOME="${NGINX_HOME:-}"
# 改为: NGINX_HOME=/usr/local/nginx
# （stop.sh 同样修改）

# 或运行时通过环境变量指定
NGINX_HOME=/usr/local/nginx bash bin/start.sh
```

> `NGINX_HOME` 为空时使用系统默认：从 `PATH` 查找 `nginx` 可执行文件，配置目录取 `/etc/nginx`。
> 指定后脚本按其前缀推导 `sbin/nginx` 与 `conf/`。

#### 3.4 启动 / 停止 Nginx

```bash
cd /opt/crm-pgcent-mng/prod-ai-frontend
bash bin/start.sh     # 校验 nginx -t 后启动 Nginx
bash bin/stop.sh      # 优雅停止（-s quit，最多等 30s 后强制终止）
```

> 需 root 权限。`start.sh` 若检测到 Nginx 已在运行会跳过；启动前会先 `nginx -t` 校验配置。
> 若安装后 Nginx 已在运行，重载配置生效：`<Nginx_BIN> -s reload` 或 systemctl reload nginx。

#### 3.5 验证前端

```bash
# 站点配置是否正常
nginx -t

# 页面与接口是否可达
curl -s http://127.0.0.1/
curl -s http://127.0.0.1/health
```

---

### 4) 后续更新（`deployup.sh`）

首次部署完成后，升级无需重新解压，只要把新的 tar 包放进 `${APP_HOME}/installer/`
（默认 `/opt/crm-pgcent-mng/installer/`），再执行前后端各自的 `bin/deployup.sh`：

```bash
mkdir -p /opt/crm-pgcent-mng/installer
cp /root/deploy-pack/prod-ai-backend.tar.gz  /opt/crm-pgcent-mng/installer/
cp /root/deploy-pack/prod-ai-frontend.tar.gz /opt/crm-pgcent-mng/installer/

# 后端：只替换 app.jar（保留 config/application.yml、logs/、data/、uploads/）
cd /opt/crm-pgcent-mng/prod-ai-backend
bash bin/deployup.sh
bash bin/start.sh restart          # 或 systemctl restart prod-ai-backend

# 前端：只替换静态资源 dist/（不重装站点配置）
cd /opt/crm-pgcent-mng/prod-ai-frontend
bash bin/deployup.sh
bash bin/stop.sh && bash bin/start.sh   # 或 <Nginx_BIN> -s reload
```

`deployup.sh` 行为：
- 自动从 `${APP_HOME}/installer/` 取固定名包（后端 `prod-ai-backend.tar.gz`、前端 `prod-ai-frontend.tar.gz`），内部解压到临时目录后清理
- **后端**：备份原 `app.jar` 为 `app.jar.bak.<时间戳>` 后替换；**不动** `config/application.yml`、`logs/`、`data/`、`uploads/`
- **前端**：备份原 `dist/` 为 `dist.bak.<时间戳>` 后清空并替换静态资源；**不动** Nginx 站点配置
- 包路径/包名可通过脚本顶部 `INSTALLER_DIR` / `PKG_NAME`（或环境变量）调整

> 需 root 权限（前端写 `dist/`）。两个脚本均以「包根含 app.jar / html/」为约定，
> 同时兼容「包根为 `prod-ai-backend/`、`prod-ai-frontend/` 子目录」的打包结构。

---

### 5) 防火墙 / 安全组放行

根据网络环境放行所需端口：

| 端口 | 用途 | 是否需对外 |
|------|------|-----------|
| 80 | Nginx 前端页面 | 是 |
| 6174 | 后端（由 Nginx 反代，通常不直接对外） | 建议关闭或仅内网 |
| 8866 | MySQL（`172.30.0.232`） | 仅内网 |

CentOS/Rocky 示例：
```bash
firewall-cmd --permanent --add-service=http
firewall-cmd --reload
```

---

## 三、升级部署（`deployup.sh` 覆盖更新）

以下以部署根 `APP_HOME=/opt/crm-pgcent-mng` 为例。升级只需把新包放进 `installer/`，再执行各自 `deployup.sh`。

### 后端
```bash
# 1) 放入新发布包
mkdir -p /opt/crm-pgcent-mng/installer
cp /root/deploy-pack/prod-ai-backend.tar.gz /opt/crm-pgcent-mng/installer/

# 2) 替换 app.jar（自动备份为 app.jar.bak.<时间戳>；保留 config/、logs/、data/、uploads/）
cd /opt/crm-pgcent-mng/prod-ai-backend
bash bin/deployup.sh

# 3) 重启
systemctl restart prod-ai-backend   # 或 bash bin/start.sh restart
```

> ⚠️ `deployup.sh` **不覆盖** `config/application.yml`。若新版本引入新的配置项，
> 请对比包内 `config/application.yml` 与现网配置后手工合并。

### 前端
```bash
# 1) 放入新发布包
cp /root/deploy-pack/prod-ai-frontend.tar.gz /opt/crm-pgcent-mng/installer/

# 2) 替换静态资源（自动备份为 dist.bak.<时间戳>；不重装站点配置）
cd /opt/crm-pgcent-mng/prod-ai-frontend
bash bin/deployup.sh

# 3) 重启/重载 Nginx
bash bin/stop.sh && bash bin/start.sh   # 或 <Nginx_BIN> -s reload
```

### 数据库
- 结构未变：无需操作。
- 结构变更：按仓库 `sql/README.md` 增量脚本路径执行，**禁止对存量库执行 `01_full_schema_ddl.sql`**。

---

## 四、回滚

- **后端**：把备份的 `app.jar.bak.*` 还原为 `app.jar`，重启服务。
- **前端**：把备份的 `dist.bak.*` 还原为 `dist/`，重启/重载 Nginx。
- **数据库**：`01` 为破坏性 DROP+CREATE，回滚需依赖执行前备份
  `mysqldump -h172.30.0.232 -P8866 -upoc-stq -p'Poc@StQ@2026' --single-transaction poc-stq > backup.sql`。

---

## 五、常见问题

**Q: 后端启动报 `未找到 java` / `需要 JDK 17`**
A: 安装 JDK17；若已安装但报错，说明默认 `java` 非 17。在 `bin/start.sh` 顶部
`JAVA_HOME` 或 systemd 单元的 `Environment="JAVA_HOME=..."` 指向 JDK17 安装路径后重试。

**Q: 后端启动但找不到 config / 连的还是 H2**
A: `bin/start.sh` 默认按「包目录的父目录」推导 `APP_HOME`。确认部署结构为
`<根目录>/crm-pgcent-mng/{prod-ai-backend,prod-ai-frontend}`；若结构不同，在脚本顶部显式填写 `APP_HOME`。

**Q: 前端 403 / 页面报错**
A: 检查 `nginx -t`（`start.sh` 启动前会校验）；确认 `/opt/crm-pgcent-mng/prod-ai-frontend/dist` 下存在 index.html（首次部署需 `cp -r html/. dist/`，
或执行 `bin/deployup.sh`）；确认 `prod-ai.conf` 中 `root` 指向正确（`start.sh` 会自动按 `APP_HOME` 修正）。

**Q: 前端启停报 `未找到 nginx 可执行文件`**
A: Nginx 非系统默认安装。在 `bin/start.sh` / `bin/stop.sh` 顶部将
`NGINX_HOME` 设为 Nginx 安装前缀（如 `/usr/local/nginx`），或运行时 `NGINX_HOME=... bash bin/start.sh`。

**Q: `deployup.sh` 报 `未找到发布包`**
A: 需先把 tar 包放到 `${APP_HOME}/installer/`（默认 `/opt/crm-pgcent-mng/installer/`），
且文件名与脚本内 `PKG_NAME` 一致（`prod-ai-backend.tar.gz` / `prod-ai-frontend.tar.gz`）。

**Q: 登录/鉴权失败**
A: 确认 `config/application.yml` 中 `prodai.auth.jwt-secret` 已配置且重启后端。

**Q: 数据库连接失败 / 日志显示仍连 H2**
A: 核对 `config/application.yml` 中 `spring.datasource.url` 为 `jdbc:mysql://172.30.0.232:8866/poc-stq`、
`username`/`password` 为 `poc-stq`/`Poc@StQ@2026`，且 `poc-stq` 库已按步骤 1 建表；
改完须重启后端。若仍连 H2，多为改错文件或 YAML 缩进错误导致该文件被整体忽略。

**Q: SSE/WebSocket 无法长连接**
A: 前端 `prod-ai.conf` 已关闭 `proxy_buffering` 并配置长超时；若改动过 web 代理请保持一致。
