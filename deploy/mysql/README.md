# MySQL 初始化包（纯 MySQL 8.0）

后端 `prodplatformai` 库的建库、DDL 与初始化数据脚本，供虚拟机上一次性初始化，**不依赖 git 仓库**。

## 脚本清单与执行顺序

| 顺序 | 脚本 | 作用 | 身份 | 可重复执行 |
|------|------|------|------|-----------|
| 1 | `00_create_database.sql` | 建库 + 应用账号授权 | 管理员(root) | 是(IF NOT EXISTS) |
| 2 | `01_full_schema_ddl.sql` | 全量 DDL（23 张业务表） | 应用账号 | **否**(DROP+CREATE 清数据) |
| 3 | `03_ext_schema.sql` | 扩展表（指标宽表/ABox/变更订阅/流程引擎节点日志） | 应用账号 | 是(IF NOT EXISTS+判存) |
| 4 | `02_init_data.sql` | 初始化数据（MCP 工具/DSL 规则/提示词/LLM 占位） | 应用账号 | 是(幂等) |

> 首次上线：一键执行 `install_mysql.sh`，顺序即 00→01→03→02。

## 快速使用（VM 上）

```bash
# 1) 安装 MySQL 8.0 客户端（若未装）
#    CentOS/Rocky: dnf install -y mysql
#    Ubuntu:       apt install -y mysql-client

# 2) 一键初始化（root 建库 + 全量 DDL + 扩展表 + 初始化数据）
bash install_mysql.sh -u root -p '你的root密码'

# 3) 已建好库/表，仅补初始化数据
bash install_mysql.sh -u root -p 'x' --skip-create --skip-schema --skip-ext
```

常用参数见脚本头部（`hash install_mysql.sh` 查看）：`-h/-P/-u/-p/-a/-w/-d` 与 `--skip-*`。

## 与后端配置对应

初始化完成后，后端 `backend.env` 需配置：

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/prodplatformai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_DRIVER=com.mysql.cj.jdbc.Driver
SPRING_DATASOURCE_USERNAME=prodplatformai
SPRING_DATASOURCE_PASSWORD=<应用账号密码>
```

同时后端外部配置应设 `spring.sql.init.mode=never`（表结构由本包维护，禁用 jar 内 H2 初始化）。

## 与现有 sql/ 目录的关系（重要）

- 本包 `01_full_schema_ddl.sql`、`03_ext_schema.sql` 为**纯 MySQL 8.0 版**，已移除
  GoldenDB 专属 `DISTRIBUTED BY DUPLICATE(g1,g2)` 子句、修正保留字列名 `"value"→value`、统一库名 `prodplatformai`。
- 仓库 `sql/` 下：
  - `01_full_schema_ddl.sql`（根目录）与 `sql/goldendb/` 版本均含 GoldenDB 语法，**不用于纯 MySQL 8.0**。
  - `02_metric_schema.sql / 04_abox_shelf_view.sql / 05_change_subscription_tables.sql / 03_flow_engine_tables.sql`
    的增量对象已并入本包 `03_ext_schema.sql`。
  - ABox 演示种子（`04` 末尾 100 行）为演示数据，生产由业务 ETL 灌入，本包未含；如确需演示数据，可将其
    改库名后单独执行。

## 安全与回滚

- `00_create_database.sql` 应用账号默认密码为 `prodplatformai@134`，**上线前必须修改**，并同步后端 `backend.env`。
- `01` 为 DROP+CREATE，**严禁对存量有数据的库执行**；存量升级请走仓库 `sql/README.md` 的增量路径。
- 建库前建议先 `mysqldump --single-transaction prodplatformai > backup.sql` 备份。
