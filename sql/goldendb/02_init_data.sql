-- ============================================================
-- Prod Platform AI - 初始化数据脚本（GoldenDB 兼容版，可重复执行）
-- 来源：sql/02_init_data.sql（MySQL 8.0 基线）+ H2 data-h2.sql 的 GoldenDB 适配
-- 依赖：已执行 sql/goldendb/01_full_schema_ddl.sql
-- 用法：
--   mysql -uprodplatformai -p prodplatformai < sql/goldendb/02_init_data.sql
--
-- GoldenDB DBProxy 兼容改造（baseline -> GoldenDB 差异）：
--   1) baseline 使用 INSERT ... ON DUPLICATE KEY UPDATE，GoldenDB 对
--      DISTRIBUTED BY DUPLICATE 表抛错：
--        ERR 12071: insert values sql with 'on duplicate key update' must be 'SW'!
--   2) INSERT INTO ... SELECT ... FROM DUAL WHERE NOT EXISTS(...) 同样被
--      DBProxy 拦截：
--        4000 - UDAL - DBProxy internal error:
--          TODO: insert into .... select .... not supported!
--   最终方案：DELETE 精确键值 + INSERT VALUES 两步幂等：
--      先按种子唯一键 DELETE 旧种子行，再 INSERT VALUES。
--      种子行不承载运行时数据（用户在页面保存的配置 config_name 不同，
--      不会被误删）；重复执行结果与首次一致。
-- 对齐说明：LLM 配置种子与用户认证种子对齐 H2 data-h2.sql 最新形态
--   （3 条 LLM 配置 + admin 初始账号）；ABox 商品种子属业务库，不落本库。
-- ============================================================

SET NAMES utf8mb4;
USE `prodplatformai`;

-- ------------------------------------------------------------
-- 1. MCP 外部工具种子
-- ------------------------------------------------------------

-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_mcp_tool_definitions` WHERE `tool_name` = 'external_health_ping';

INSERT INTO `pd_ai_mcp_tool_definitions` (

    `tool_name`, `tool_code`, `description`, `category`,
    `is_enabled`, `is_public`,
    `input_schema`, `output_schema`,
    `tool_type`, `protocol`, `request_method`, `url`,
    `auth_type`, `need_summary`, `total_calls`,
    `created_by`, `created_at`, `updated_at`

)
VALUES (
    'external_health_ping',
    'EXT_HEALTH_PING',
    '外部健康检查占位工具（演示种子）',
    'external',
    1, 1,
    '{"type":"object","properties":{"ping":{"type":"string","description":"可选探测标记"}}}',
    '{"type":"object","properties":{"ok":{"type":"boolean"}}}',
    'url', 'http', 'GET', 'https://httpbin.org/get',
    'none', 0, 0,
    'system', NOW(6), NOW(6)
);

-- ------------------------------------------------------------
-- 2. 条件 DSL 规则（营销遗留路径；库中有数据时引擎优先读库）
-- ------------------------------------------------------------

-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_swrl_rules` WHERE `rule_id` = 'COND_001';

INSERT INTO `pd_ai_swrl_rules` (

    `rule_id`, `rule_name`, `module`, `description`,
    `condition_expr`, `action_expr`, `enabled`,
    `created_at`, `updated_at`

)
VALUES (
    'COND_001',
    '高消费推导升级资格',
    'marketing_rules',
    '条件 DSL（非 OWL SWRL）：年消费 >= 50000 且会员等级为 Gold/Platinum',
    'annualSpend >= 50000 AND vipLevel IN (Gold, Platinum)',
    NULL,
    1,
    NOW(6), NOW(6)
);

-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_swrl_rules` WHERE `rule_id` = 'COND_002';

INSERT INTO `pd_ai_swrl_rules` (

    `rule_id`, `rule_name`, `module`, `description`,
    `condition_expr`, `action_expr`, `enabled`,
    `created_at`, `updated_at`

)
VALUES (
    'COND_002',
    '信用分推导额度调整',
    'marketing_rules',
    '条件 DSL（非 OWL SWRL）：信用分 >= 700',
    'creditScore >= 700',
    NULL,
    1,
    NOW(6), NOW(6)
);

-- ------------------------------------------------------------
-- 3. 内置提示词模板
-- ------------------------------------------------------------

-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_prompt_templates` WHERE `code` = 'intent_recognition';

INSERT INTO `pd_ai_prompt_templates` (

    `code`, `name`, `description`, `category`, `content`,
    `variables`, `tools`, `tags`, `is_builtin`, `is_active`, `created_at`

)
VALUES (
    'intent_recognition',
    '意图识别',
    '通用意图识别提示词模板',
    'intent',
    '你是产商品配置助手。根据用户输入识别意图，并输出结构化结果。\n用户输入：{{user_input}}',
    '[{"name":"user_input","description":"用户原始输入","default":""}]',
    '[]',
    '["builtin","intent"]',
    1, 1, NOW(6)
);

-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_prompt_templates` WHERE `code` = 'offering_ops_risk_audit';

INSERT INTO `pd_ai_prompt_templates` (

    `code`, `name`, `description`, `category`, `content`,
    `variables`, `tools`, `tags`, `is_builtin`, `is_active`, `created_at`

)
VALUES (
    'offering_ops_risk_audit',
    '产商品风险稽核',
    '运营助手风险稽核场景提示词模板',
    'ops',
    '你是产商品运营稽核助手。基于图谱与规则，对指定商品进行风险稽核并给出处置建议。\n商品：{{offering_name}}\n上下文：{{context}}',
    '[{"name":"offering_name","description":"商品名称","default":""},{"name":"context","description":"上下文","default":""}]',
    '[]',
    '["builtin","ops","audit"]',
    1, 1, NOW(6)
);

-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_prompt_templates` WHERE `code` = 'offering_ops_root_cause';

INSERT INTO `pd_ai_prompt_templates` (

    `code`, `name`, `description`, `category`, `content`,
    `variables`, `tools`, `tags`, `is_builtin`, `is_active`, `created_at`

)
VALUES (
    'offering_ops_root_cause',
    '产商品异动归因',
    '运营助手根因分析场景提示词模板',
    'ops',
    '你是产商品运营归因助手。结合异动指标与规则链，输出 TopN 根因与证据。\n商品：{{offering_name}}\n异动描述：{{anomaly}}',
    '[{"name":"anomaly","description":"异动描述","default":""}]',
    '[]',
    '["builtin","ops","root_cause"]',
    1, 1, NOW(6)
);

-- ------------------------------------------------------------
-- 4. LLM 模型配置种子（对齐 H2 data-h2.sql）
--    仅首次插入语义：已存在的同名配置不会被覆盖，保护用户在
--    「模型配置」页保存的 api_key / 激活状态等运行时数据。
--    生产务必修改 api_key（通过环境变量/密钥管理注入，禁止明文落盘）
-- ------------------------------------------------------------

-- 企业网关 qwen3-30b-a3b（默认激活）
-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_llm_user_configs` WHERE `user_identifier` = 'default' AND `config_name` = 'teamshub-qwen3-30b-a3b';

INSERT INTO `pd_ai_llm_user_configs` (

    `user_identifier`, `provider`, `model`, `api_key`, `base_url`,
    `auth_type`, `api_format`, `is_full_url`,
    `temperature`, `max_tokens`, `thinking`, `stream_enabled`, `max_input_tokens`,
    `is_active`, `config_name`, `created_at`, `updated_at`

)
VALUES (
    'default',
    'custom',
    'qwen3-30b-a3b',
    'cb3a5cb469de1d0820d25a1e6349306dc4482f90',
    'https://aicp.teamshub.com/openai/api/v1/openai/v1',
    'custom',
    'openai',
    1,
    0.3,
    4096,
    0,
    1,
    180000,
    1,
    'teamshub-qwen3-30b-a3b',
    NOW(6),
    NOW(6)
);

-- 硅基流动 deepseek-chat（未激活备用）
-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_llm_user_configs` WHERE `user_identifier` = 'default' AND `config_name` = 'deepseek-chat';

INSERT INTO `pd_ai_llm_user_configs` (

    `user_identifier`, `provider`, `model`, `api_key`, `base_url`,
    `auth_type`, `api_format`, `is_full_url`,
    `temperature`, `max_tokens`, `thinking`, `stream_enabled`, `max_input_tokens`,
    `is_active`, `config_name`, `created_at`, `updated_at`

)
VALUES (
    'default',
    'custom',
    'deepseek-ai/DeepSeek-V4-Flash',
    NULL,
    'https://api.siliconflow.cn/v1',
    'bearer',
    'openai',
    0,
    0.3,
    4096,
    0,
    1,
    180000,
    0,
    'deepseek-chat',
    NOW(6),
    NOW(6)
);

-- 系统默认占位配置（生产务必修改 api_key / base_url）
-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_llm_user_configs` WHERE `user_identifier` = 'default' AND `config_name` = '系统默认配置';

INSERT INTO `pd_ai_llm_user_configs` (

    `user_identifier`, `provider`, `model`, `api_key`, `base_url`,
    `auth_type`, `api_format`, `is_full_url`,
    `temperature`, `max_tokens`, `thinking`, `stream_enabled`, `max_input_tokens`,
    `is_active`, `config_name`, `created_at`, `updated_at`

)
VALUES (
    'default',
    'custom',
    'gpt-4o-mini',
    NULL,
    NULL,
    'bearer',
    'openai',
    0,
    0.3,
    2048,
    0,
    1,
    180000,
    1,
    '系统默认配置',
    NOW(6),
    NOW(6)
);

-- ------------------------------------------------------------
-- 5. 用户认证种子（pd_ai_users，对齐 H2 data-h2.sql）
--    初始账号：admin / admin123（首次登录后务必修改密码）
--    password_hash = salt hex + ":" + SHA-256(salt + password) hex
-- ------------------------------------------------------------

-- 幂等：先删种子键（精确匹配，不涉及运行时数据），再插值
DELETE FROM `pd_ai_users` WHERE `username` = 'admin';

INSERT INTO `pd_ai_users` (

    `username`, `password_hash`, `display_name`, `role`,
    `is_enabled`, `created_at`, `updated_at`

)
VALUES (
    'admin',
    'a1b2c3d4e5f60718293a4b5c6d7e8f90:0212b518b03b50cc62a0dadc9e897f48190e21e0af3ba93665b4b0085885e265',
    '管理员',
    'admin',
    1,
    NOW(6),
    NOW(6)
);

-- ------------------------------------------------------------
-- 6. 验证
-- ------------------------------------------------------------

SELECT 'pd_ai_mcp_tool_definitions' AS tbl, COUNT(*) AS cnt FROM `pd_ai_mcp_tool_definitions`
UNION ALL
SELECT 'pd_ai_swrl_rules', COUNT(*) FROM `pd_ai_swrl_rules`
UNION ALL
SELECT 'pd_ai_prompt_templates', COUNT(*) FROM `pd_ai_prompt_templates`
UNION ALL
SELECT 'pd_ai_llm_user_configs', COUNT(*) FROM `pd_ai_llm_user_configs`
UNION ALL
SELECT 'pd_ai_users', COUNT(*) FROM `pd_ai_users`;
