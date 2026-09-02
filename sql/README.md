# Prod Platform AI - 数据库上线部署说明

> 数据库：MySQL 8.0+ / InnoDB / utf8mb4_unicode_ci
> 库名：`prodplatformai`
> 表结构权威来源：`backend-app` MyBatis Plus 实体（`@TableName`），本目录 01 DDL 与其一一对应。

---

## 一、脚本清单与执行顺序

| 顺序 | 脚本 | 作用 | 执行身份 | 幂等性 | 适用场景 |
|------|------|------|----------|--------|----------|
| 1 | `00_create_database.sql` | 建库 + 应用账号授权 | 管理员（root） | 是（IF NOT EXISTS） | 新环境部署 |
| 2 | `01_full_schema_ddl.sql` | 全量 DDL（20 张 pd_ai_* 应用表） | 应用账号即可 | **否**（DROP+CREATE，会清数据） | 全新环境 |
| 3 | `02_init_data.sql` | 种子数据（MCP 工具/DSL 规则/提示词模板/LLM 占位） | 应用账号 | 是（ON DUPLICATE KEY / NOT EXISTS） | 新环境 + 重复执行安全 |
| 4 | `02_ontology_version_tables.sql` | 本体版本库表 A/B（存量库增量升级） | 应用账号 | 是（CREATE IF NOT EXISTS + ADD COLUMN IF NOT EXISTS） | **仅存量库升级**，新库跳过 |

### 表覆盖清单（01 全量 DDL，共 20 张）

```
聊天系统   pd_ai_chat_sessions / pd_ai_chat_messages / pd_ai_chat_message_metadata
MCP 工具   pd_ai_mcp_tool_definitions / pd_ai_mcp_call_logs / pd_ai_mcp_tool_stats
LLM 配置   pd_ai_llm_user_configs
提示词     pd_ai_prompts / pd_ai_prompt_versions / pd_ai_prompt_templates
工作流     pd_ai_workflows / pd_ai_workflow_history / pd_ai_workflow_executions
链路追踪   pd_ai_traces / pd_ai_spans
本体实例   pd_ai_ontology_instance（data_json 单列，原 instance_data 子表已并入）
版本库     pd_ai_ontology_version / pd_ai_ontology_version_log
规则/工单  pd_ai_swrl_rules / pd_ai_ops_work_orders
```

---

## 二、部署方式

### 方式 A：一键脚本（Windows PowerShell，推荐）

```powershell
# 全新环境（建库 + 全量 DDL + 种子数据）
.\sql\deploy.ps1 -DbHost 127.0.0.1 -Port 3306 -RootUser root -RootPassword 'xxx'

# 仅执行种子数据（表已存在）
.\sql\deploy.ps1 -SkipCreateDb -SkipSchema

# 仅执行全量 DDL（库与表已建，跳过建库）
.\sql\deploy.ps1 -SkipCreateDb
```

依赖：`mysql` 客户端已加入 PATH。参数详见 `deploy.ps1` 头部注释。

### 方式 B：手动 mysql 命令

```bash
# 1. 建库与账号（管理员身份）
mysql -h<host> -uroot -p < sql/00_create_database.sql

# 2. 全量 DDL（应用账号）
mysql -h<host> -uprodplatformai -p prodplatformai < sql/01_full_schema_ddl.sql

# 3. 种子数据（应用账号）
mysql -h<host> -uprodplatformai -p prodplatformai < sql/02_init_data.sql

# 4.（仅存量库升级）本体版本库增量
mysql -h<host> -uprodplatformai -p prodplatformai < sql/02_ontology_version_tables.sql
```

---

## 三、新环境 vs 存量环境升级矩阵

### 场景 1：全新上线（首次部署）

```
执行：00 → 01 → 02（跳过 02_ontology_version_tables，01 中已含全部表）
```

### 场景 2：存量库升级（表已存在，需保留数据）

> ⚠️ `01_full_schema_ddl.sql` 会 DROP 重建全部表，**严禁**对存量库执行！

存量库按以下增量路径执行：

```sql
-- a) 本体版本库表 A/B + P3-5 审计扩列（幂等）
SOURCE sql/02_ontology_version_tables.sql;

-- b) 会话消息排序字段（若存量库无 sort_order）
ALTER TABLE `pd_ai_chat_messages`
    ADD COLUMN IF NOT EXISTS `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同会话内排序' AFTER `parent_id`;

-- c) 工单来源会话字段（若存量库无 session_id）
ALTER TABLE `pd_ai_ops_work_orders`
    ADD COLUMN IF NOT EXISTS `session_id` VARCHAR(64) DEFAULT NULL COMMENT '来源会话 ID' AFTER `source`,
    ADD KEY IF NOT EXISTS `idx_owo_session` (`session_id`);

-- d) 本体实例 KV→data_json 迁移（JPA→MyBatis Plus 后子表退役；01 脚本尾部含迁移参考 SQL）
ALTER TABLE `pd_ai_ontology_instance` ADD COLUMN IF NOT EXISTS `data_json` TEXT NULL AFTER `submitted_at`;
UPDATE `pd_ai_ontology_instance` i SET i.data_json = (
    SELECT JSON_OBJECTAGG(d.data_key, d.data) FROM `pd_ai_ontology_instance_data` d
    WHERE d.ontology_instance_id = i.id)
WHERE EXISTS (SELECT 1 FROM `pd_ai_ontology_instance_data` d2 WHERE d2.ontology_instance_id = i.id);

-- e) 种子数据（幂等，可重复执行）
SOURCE sql/02_init_data.sql;
```

### 场景 3：验证存量库结构是否与最新 DDL 一致

```sql
-- 核对表数量（应为 21 张，不含已退役的 instance_data / instance_history）
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema = 'prodplatformai';

-- 核对关键字段存在性
SELECT table_name, column_name FROM information_schema.columns
WHERE table_schema = 'prodplatformai'
  AND ((table_name = 'pd_ai_ontology_instance' AND column_name = 'data_json')
    OR (table_name = 'pd_ai_chat_messages' AND column_name = 'sort_order')
    OR (table_name = 'pd_ai_ops_work_orders' AND column_name = 'session_id')
    OR (table_name = 'pd_ai_ontology_version_log' AND column_name = 'domain'));
```

---

## 四、种子数据说明（02_init_data.sql）

| 种子 | 对齐来源 | 生产注意 |
|------|----------|----------|
| MCP 工具 `external_health_ping` | `classpath:ontology/mcp_tools_seed.json` | 演示占位，可禁用 |
| DSL 规则 COND_001/002 | `SwrlRuleEngine.builtinRules()` | 营销遗留路径 |
| 提示词模板 ×3 | 平台内置样例 | 可按环境删改 |
| LLM 配置 `default` | 占位（api_key/base_url 为 NULL） | **生产必须配置真实 LLM，或走 application.yml 环境变量** |

---

## 五、上线检查清单

- [ ] MySQL 8.0+（`ADD COLUMN IF NOT EXISTS` 语法依赖 8.0；02_ontology_version_tables.sql 必需）
- [ ] 字符集 utf8mb4 / utf8mb4_unicode_ci
- [ ] 应用账号密码已从默认 `prodplatformai@134` 修改（`00_create_database.sql` + `SPRING_DATASOURCE_PASSWORD`）
- [ ] 生产 `spring.jpa.hibernate.ddl-auto` 已移除（JPA 已退役，表结构由 sql/ 管理）
- [ ] LLM api_key 未落库明文（走环境变量 `LLM_API_KEY`）
- [ ] 存量库未误执行 01_full_schema_ddl.sql
- [ ] 种子数据执行后验证 SELECT 计数正常（脚本尾部自带验证查询）
- [ ] 已退役表确认清理：`pd_ai_ontology_instance_data`、`pd_ai_ontology_instance_history`

## 六、回滚策略

- 结构回滚：以 `pd_ai_ontology_version`（payload 为版本唯一事实源）+ `pd_ai_ontology_version_log` 审计链为依据恢复资产
- 数据回滚：上线前 `mysqldump --single-transaction --routines=false prodplatformai > backup_$(date +%F).sql`
- DDL 回滚：无原生事务保护，回滚需基于上线前备份恢复

---

**最后更新**：2026-09-02（对齐 develop @ 3bdc503 JPA→MyBatis Plus 迁移）
