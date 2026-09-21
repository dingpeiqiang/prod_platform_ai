# MySQL 初始化包（纯 MySQL 8.0）

后端数据库的 DDL 与初始化数据脚本，供虚拟机上手动执行，**不依赖 git 仓库**。

## 目标环境（当前）

| 项 | 值 |
|----|----|
| 地址 | `172.30.0.232:8866` |
| 库名 | `poc-stq` |
| 账号 | `poc-stq` |
| 密码 | `Poc@StQ@2026` |

> 库与账号**已由 DBA 建好**，本包只负责建表与初始化数据，不建库建账号。

## 脚本清单与执行顺序

| 顺序 | 脚本 | 作用 | 可重复执行 |
|------|------|------|-----------|
| 1 | `01_full_schema_ddl.sql` | 全量 DDL（23 张业务表） | **否**(DROP+CREATE 清数据) |
| 2 | `03_ext_schema.sql` | 扩展表（指标宽表/ABox/变更订阅/流程引擎节点日志） | 是(IF NOT EXISTS+判存) |
| 3 | `02_init_data.sql` | 初始化数据（MCP 工具/DSL 规则/提示词/LLM 占位） | 是(幂等) |

> 首次建表按 01→03→02 顺序执行。

## 快速使用（VM 上，脚本手动执行）

```bash
# 1) 安装 MySQL 8.0 客户端（若未装）
#    CentOS/Rocky: dnf install -y mysql
#    Ubuntu:       apt install -y mysql-client

# 2) 手动执行建表
mysql -h172.30.0.232 -P8866 -upoc-stq -p'Poc@StQ@2026' poc-stq < 01_full_schema_ddl.sql
mysql -h172.30.0.232 -P8866 -upoc-stq -p'Poc@StQ@2026' poc-stq < 03_ext_schema.sql
mysql -h172.30.0.232 -P8866 -upoc-stq -p'Poc@StQ@2026' poc-stq < 02_init_data.sql

# 3) （可选）一键脚本，按默认环境执行 01→03→02
bash install_mysql.sh
```

常用参数见脚本头部（`bash install_mysql.sh --help` 查看）：`-h/-P/-a/-w/-d` 与 `--skip-*`。

## 与后端配置对应

后端外部配置 `config/application.yml` 数据源（部署包内模板已按此环境填好）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://172.30.0.232:8866/poc-stq?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: poc-stq
    password: Poc@StQ@2026
  sql:
    init:
      mode: never
```

`spring.sql.init.mode` 须设为 `never`（表结构由本包维护，禁用 jar 内 H2 初始化）。

## 与现有 sql/ 目录的关系（重要）

- 本包 `01_full_schema_ddl.sql`、`03_ext_schema.sql` 为**纯 MySQL 8.0 版**，已移除
  GoldenDB 专属 `DISTRIBUTED BY DUPLICATE(g1,g2)` 子句、修正保留字列名 `"value"→value`。
- 仓库 `sql/` 下：
  - `01_full_schema_ddl.sql`（根目录）与 `sql/goldendb/` 版本均含 GoldenDB 语法，**不用于纯 MySQL 8.0**。
  - `02_metric_schema.sql / 04_abox_shelf_view.sql / 05_change_subscription_tables.sql / 03_flow_engine_tables.sql`
    的增量对象已并入本包 `03_ext_schema.sql`。
  - ABox 演示种子（`04` 末尾 100 行）为演示数据，生产由业务 ETL 灌入，本包未含；如确需演示数据，可将其
    改库名后单独执行。

## 安全与回滚

- `01` 为 DROP+CREATE，**严禁对存量有数据的库执行**；存量升级请走仓库 `sql/README.md` 的增量路径。
- 建表前建议先备份：`mysqldump -h172.30.0.232 -P8866 -upoc-stq -p'Poc@StQ@2026' --single-transaction poc-stq > backup.sql`。
- 脚本内密码为明文（`poc-stq` 专用库），请勿外泄。
