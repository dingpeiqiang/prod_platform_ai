# Prod Platform AI - 数据库脚本（按方言分目录）

> 表结构权威来源：`backend-app` MyBatis Plus 实体（`@TableName`）。
> 两个目录内脚本均**自洽完整**（建库 → 全量 DDL → 扩展 schema → 种子数据），
> 按目标数据库选择其中一个目录执行，**不要跨目录混用**。

## 目录结构

| 目录 | 方言 | 说明 |
|------|------|------|
| `mysql/` | MySQL 8.0+ | InnoDB / utf8mb4_unicode_ci；支持 `ADD COLUMN IF NOT EXISTS`（8.0 有限支持处以存储过程判存代替） |
| `goldendb/` | GoldenDB（5.7 基线） | 分布式表 `DISTRIBUTED BY DUPLICATE(g1,g2)`；禁用 `ON DUPLICATE KEY UPDATE`（ERR 12071）、`INSERT ... SELECT`（DBProxy 4000 UDAL）、`MODIFY COLUMN`（ORA-02441 规避）、`ADD COLUMN IF NOT EXISTS`（information_schema 判存） |

## 执行顺序（两目录一致）

```
00_create_database.sql            # 建库 + 账号（管理员执行；GoldenDB 常由 DBA 代建可跳过）
01_full_schema_ddl.sql            # 全量 DDL（含扩展表；DROP+CREATE，仅全新环境）
02_ontology_version_tables.sql    # （仅存量库升级）本体版本库增量
03_ext_schema.sql                 # 扩展 schema（metric 宽表 / ABox / 订阅 / 流程引擎）——仅 mysql/ 有
04_abox_shelf_view.sql            # ABox 在架商品同步源（幂等，可重复执行）
02_init_data.sql                  # 种子数据（幂等：MCP / DSL 规则 / 提示词 / LLM / admin / 在架商品）
```

## 目录内文件清单（mysql/）

| 文件 | 幂等性 | 说明 |
|------|--------|------|
| `00_create_database.sql` | 是 | 建库 + 应用账号授权 |
| `01_full_schema_ddl.sql` | **否**（DROP+CREATE 清数据） | 全量 24 表（含 pd_ai_users） |
| `02_ontology_version_tables.sql` | 是 | 存量库升级增量 |
| `03_ext_schema.sql` | 是 | 指标宽表 / ABox / 变更订阅 / 流程引擎扩展 |
| `04_abox_shelf_view.sql` | 是 | ABox 同步源 |
| `02_init_data.sql` | 是 | 种子数据（DELETE+INSERT 幂等，对齐 H2 data-h2.sql） |
| `deploy.ps1` | - | 一键执行脚本（Windows） |

> 幂等策略双方言统一：种子数据均为 **DELETE 种子键 + INSERT VALUES** 两步幂等
> （不使用 ON DUPLICATE KEY UPDATE / INSERT IGNORE / INSERT...SELECT，
> 便于两目录语义一致、交叉校验；重复执行结果与首次一致，种子行不承载运行时数据）；
> ABox 商品种子属业务库数据，Goldendb 侧不落本库（见该文件头部说明）。

## 快速开始

```bash
# MySQL 8.0
mysql -uroot -p < sql/mysql/00_create_database.sql
mysql -uprodplatformai -p < sql/mysql/01_full_schema_ddl.sql
mysql -uprodplatformai -p < sql/mysql/03_ext_schema.sql
mysql -uprodplatformai -p < sql/mysql/02_init_data.sql

# GoldenDB（库与账号通常已由 DBA 建好）
mysql -uprodplatformai -p < sql/goldendb/01_full_schema_ddl.sql
mysql -uprodplatformai -p < sql/goldendb/02_init_data.sql
```

## 存量库升级（MySQL）

存量库**严禁执行 01**（DROP+CREATE）。增量路径：

```sql
SOURCE sql/mysql/02_ontology_version_tables.sql;  -- 本体版本库 + 审计扩列（幂等）
SOURCE sql/mysql/03_ext_schema.sql;               -- 扩展表（幂等）
SOURCE sql/mysql/02_init_data.sql;                -- 种子数据（幂等）
```

字段级补齐（若存量库缺列）见 03_ext_schema.sql 尾部验证段与 `02_ontology_version_tables.sql`。

## 上线检查清单

- [ ] 方言目录选择正确（MySQL / GoldenDB 不混用）
- [ ] 字符集 utf8mb4（MySQL: utf8mb4_unicode_ci；GoldenDB: utf8mb4_general_ci）
- [ ] 应用账号密码已从默认 `prodplatformai@134` 修改（`00_create_database.sql` + `SPRING_DATASOURCE_PASSWORD`）
- [ ] 生产 `spring.sql.init.mode=never`（deploy 侧 application.yml 已默认）
- [ ] LLM api_key 未落库明文
- [ ] 存量库未误执行 01_full_schema_ddl.sql
- [ ] 种子数据执行后验证 SELECT 计数正常（02_init_data.sql 尾部自带）
- [ ] 退役表确认清理：`pd_ai_ontology_instance_data`、`pd_ai_ontology_instance_history`

## 回滚策略

- 结构回滚：以 `pd_ai_ontology_version`（payload 为版本唯一事实源）+ `pd_ai_ontology_version_log` 审计链为依据恢复资产
- 数据回滚：上线前 `mysqldump --single-transaction --routines=false <库名> > backup_$(date +%F).sql`
- DDL 回滚：无原生事务保护，回滚需基于上线前备份恢复

---

**最后更新**：2026-09-21（双方言目录重组；幂等策略统一为 DELETE+INSERT VALUES）
