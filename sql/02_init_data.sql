-- ============================================================
-- Prod Platform AI - 初始化数据脚本（可重复执行）
-- 依赖：已执行 01_full_schema_ddl.sql
-- 用法：
--   mysql -uprodplatformai -p prodplatformai < sql/02_init_data.sql
-- 说明：
--   1) MCP 种子对齐 classpath:ontology/mcp_tools_seed.json
--   2) SWRL/条件 DSL 内置规则对齐 SwrlRuleEngine.builtinRules()
--   3) 提示词模板为平台内置样例（可按环境删改）
--   4) LLM 配置仅占位，生产请填真实 api_key / base_url
--   5) 本体图谱 / 知识库 / ops_rules 等为文件型种子，不落本库
-- ============================================================

SET NAMES utf8mb4;
USE `prodplatformai`;

-- ------------------------------------------------------------
-- 1. MCP 外部工具种子
-- ------------------------------------------------------------

INSERT INTO `pd_ai_mcp_tool_definitions` (
    `tool_name`, `tool_code`, `description`, `category`,
    `is_enabled`, `is_public`,
    `input_schema`, `output_schema`,
    `tool_type`, `protocol`, `request_method`, `url`,
    `auth_type`, `need_summary`, `total_calls`,
    `created_by`, `created_at`, `updated_at`
) VALUES (
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
) ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`),
    `category` = VALUES(`category`),
    `is_enabled` = VALUES(`is_enabled`),
    `is_public` = VALUES(`is_public`),
    `input_schema` = VALUES(`input_schema`),
    `output_schema` = VALUES(`output_schema`),
    `tool_type` = VALUES(`tool_type`),
    `protocol` = VALUES(`protocol`),
    `request_method` = VALUES(`request_method`),
    `url` = VALUES(`url`),
    `updated_at` = NOW(6);

-- ------------------------------------------------------------
-- 2. 条件 DSL 规则（营销遗留路径；库中有数据时引擎优先读库）
-- ------------------------------------------------------------

INSERT INTO `pd_ai_swrl_rules` (
    `rule_id`, `rule_name`, `module`, `description`,
    `condition_expr`, `action_expr`, `enabled`,
    `created_at`, `updated_at`
) VALUES
(
    'COND_001',
    '高消费推导升级资格',
    'marketing_rules',
    '条件 DSL（非 OWL SWRL）：年消费 >= 50000 且会员等级为 Gold/Platinum',
    'annualSpend >= 50000 AND vipLevel IN (Gold, Platinum)',
    NULL,
    1,
    NOW(6), NOW(6)
),
(
    'COND_002',
    '信用分推导额度调整',
    'marketing_rules',
    '条件 DSL（非 OWL SWRL）：信用分 >= 700',
    'creditScore >= 700',
    NULL,
    1,
    NOW(6), NOW(6)
)
ON DUPLICATE KEY UPDATE
    `rule_name` = VALUES(`rule_name`),
    `module` = VALUES(`module`),
    `description` = VALUES(`description`),
    `condition_expr` = VALUES(`condition_expr`),
    `enabled` = VALUES(`enabled`),
    `updated_at` = NOW(6);

-- ------------------------------------------------------------
-- 3. 内置提示词模板
-- ------------------------------------------------------------

INSERT INTO `pd_ai_prompt_templates` (
    `code`, `name`, `description`, `category`, `content`,
    `variables`, `tools`, `tags`, `is_builtin`, `is_active`, `created_at`
) VALUES
(
    'intent_recognition',
    '意图识别',
    '通用意图识别提示词模板',
    'intent',
    '你是产商品配置助手。根据用户输入识别意图，并输出结构化结果。\n用户输入：{{user_input}}',
    '[{"name":"user_input","description":"用户原始输入","default":""}]',
    '[]',
    '["builtin","intent"]',
    1, 1, NOW(6)
),
(
    'offering_ops_risk_audit',
    '产商品风险稽核',
    '运营助手风险稽核场景提示词模板',
    'ops',
    '你是产商品运营稽核助手。基于图谱与规则，对指定商品进行风险稽核并给出处置建议。\n商品：{{offering_name}}\n上下文：{{context}}',
    '[{"name":"offering_name","description":"商品名称","default":""},{"name":"context","description":"上下文","default":""}]',
    '[]',
    '["builtin","ops","audit"]',
    1, 1, NOW(6)
),
(
    'offering_ops_root_cause',
    '产商品异动归因',
    '运营助手根因分析场景提示词模板',
    'ops',
    '你是产商品运营归因助手。结合异动指标与规则链，输出 TopN 根因与证据。\n商品：{{offering_name}}\n异动描述：{{anomaly}}',
    '[{"name":"offering_name","description":"商品名称","default":""},{"name":"anomaly","description":"异动描述","default":""}]',
    '[]',
    '["builtin","ops","root_cause"]',
    1, 1, NOW(6)
)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `description` = VALUES(`description`),
    `category` = VALUES(`category`),
    `content` = VALUES(`content`),
    `variables` = VALUES(`variables`),
    `tags` = VALUES(`tags`),
    `is_builtin` = 1,
    `is_active` = 1;

-- ------------------------------------------------------------
-- 4. LLM 默认配置占位（生产务必修改 api_key / base_url）
--    若已存在同名激活配置则跳过插入
-- ------------------------------------------------------------

INSERT INTO `pd_ai_llm_user_configs` (
    `user_identifier`, `provider`, `model`, `api_key`, `base_url`,
    `auth_type`, `api_format`, `is_full_url`,
    `temperature`, `max_tokens`, `thinking`, `max_input_tokens`,
    `is_active`, `config_name`, `created_at`, `updated_at`
)
SELECT
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
    180000,
    1,
    '系统默认配置',
    NOW(6),
    NOW(6)
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `pd_ai_llm_user_configs`
    WHERE `user_identifier` = 'default' AND `config_name` = '系统默认配置'
);

-- ------------------------------------------------------------
-- 5. 验证
-- ------------------------------------------------------------

SELECT 'pd_ai_mcp_tool_definitions' AS tbl, COUNT(*) AS cnt FROM `pd_ai_mcp_tool_definitions`
UNION ALL
SELECT 'pd_ai_swrl_rules', COUNT(*) FROM `pd_ai_swrl_rules`
UNION ALL
SELECT 'pd_ai_prompt_templates', COUNT(*) FROM `pd_ai_prompt_templates`
UNION ALL
SELECT 'pd_ai_llm_user_configs', COUNT(*) FROM `pd_ai_llm_user_configs`;
