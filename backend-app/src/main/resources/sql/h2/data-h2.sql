-- ============================================================
-- Prod Platform AI - H2 初始化数据（对齐 sql/02_init_data.sql）
-- 说明：
--   1) H2 MODE=MySQL：MERGE INTO ... KEY(...) 替代 MySQL ON DUPLICATE KEY UPDATE
--   2) NOW(6) → CURRENT_TIMESTAMP（H2 精度默认即可）
--   3) 幂等：重复执行不产生重复数据
--   4) LLM 模型配置（pd_ai_llm_user_configs）为「仅首次插入」语义：
--      已存在的配置重启后不会被种子覆盖（保护用户在管理页保存的
--      api_key / 激活状态等运行时数据），其余种子表仍为 MERGE 覆盖语义
-- ============================================================

-- ------------------------------------------------------------
-- 1. MCP 外部工具种子
-- ------------------------------------------------------------

MERGE INTO pd_ai_mcp_tool_definitions (
    tool_name, tool_code, description, category,
    is_enabled, is_public,
    input_schema, output_schema,
    tool_type, protocol, request_method, url,
    auth_type, need_summary, total_calls,
    created_by, created_at, updated_at
) KEY (tool_name) VALUES (
    'external_health_ping',
    'EXT_HEALTH_PING',
    '外部健康检查占位工具（演示种子）',
    'external',
    1, 1,
    '{"type":"object","properties":{"ping":{"type":"string","description":"可选探测标识"}}}',
    '{"type":"object","properties":{"ok":{"type":"boolean"}}}',
    'url', 'http', 'GET', 'https://httpbin.org/get',
    'none', 0, 0,
    'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- 2. 条件 DSL 规则（营销遗留路径；库中有数据时引擎优先读库）
-- ------------------------------------------------------------

MERGE INTO pd_ai_swrl_rules (
    rule_id, rule_name, module, description,
    condition_expr, action_expr, enabled,
    created_at, updated_at
) KEY (rule_id) VALUES (
    'COND_001',
    '高消费推导升级资费',
    'marketing_rules',
    '条件 DSL（非 OWL SWRL）：年消费 >= 50000 且会员等级为 Gold/Platinum',
    'annualSpend >= 50000 AND vipLevel IN (Gold, Platinum)',
    NULL,
    1,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

MERGE INTO pd_ai_swrl_rules (
    rule_id, rule_name, module, description,
    condition_expr, action_expr, enabled,
    created_at, updated_at
) KEY (rule_id) VALUES (
    'COND_002',
    '信用分推导额度调整',
    'marketing_rules',
    '条件 DSL（非 OWL SWRL）：信用分 >= 700',
    'creditScore >= 700',
    NULL,
    1,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- 3. 内置提示词模板
-- ------------------------------------------------------------

MERGE INTO pd_ai_prompt_templates (
    code, name, description, category, content,
    variables, tools, tags, is_builtin, is_active, created_at
) KEY (code) VALUES (
    'intent_recognition',
    '意图识别',
    '通用意图识别提示词模板',
    'intent',
    '你是产商品配置助手。根据用户输入识别意图，并输出结构化结果。\n用户输入：{{user_input}}',
    '[{"name":"user_input","description":"用户原始输入","default":""}]',
    '[]',
    '["builtin","intent"]',
    1, 1, CURRENT_TIMESTAMP
);

MERGE INTO pd_ai_prompt_templates (
    code, name, description, category, content,
    variables, tools, tags, is_builtin, is_active, created_at
) KEY (code) VALUES (
    'offering_ops_risk_audit',
    '产商品风险稽核',
    '运营助手风险稽核场景提示词模板',
    'ops',
    '你是产商品运营稽核助手。基于图谱与规则，对指定商品进行风险稽核并给出处置建议。\n商品：{{offering_name}}\n上下文：{{context}}',
    '[{"name":"offering_name","description":"商品名称","default":""},{"name":"context","description":"上下文","default":""}]',
    '[]',
    '["builtin","ops","audit"]',
    1, 1, CURRENT_TIMESTAMP
);

MERGE INTO pd_ai_prompt_templates (
    code, name, description, category, content,
    variables, tools, tags, is_builtin, is_active, created_at
) KEY (code) VALUES (
    'offering_ops_root_cause',
    '产商品异动归因',
    '运营助手根因分析场景提示词模板',
    'ops',
    '你是产商品运营归因助手。结合异动指标与规则链，输出 TopN 根因与证据。\n商品：{{offering_name}}\n异动描述：{{anomaly}}',
    '[{"name":"offering_name","description":"商品名称","default":""},{"name":"anomaly","description":"异动描述","default":""}]',
    '[]',
    '["builtin","ops","root_cause"]',
    1, 1, CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- 4. LLM 模型配置种子（唯一配置来源，替代原 yml prodai.llm.models）
--    仅在配置不存在时插入（INSERT IF ABSENT），避免每次重启
--    覆盖用户在「模型配置」页保存的 api_key / 激活状态等运行时数据。
--    生产务必修改 api_key（通过环境变量/密钥管理注入，禁止明文落盘）
-- ------------------------------------------------------------

-- 原 yml teamshub-qwen3-30b-a3b（企业网关，默认激活）
MERGE INTO pd_ai_llm_user_configs (
    user_identifier, provider, model, api_key, base_url,
    auth_type, api_format, is_full_url,
    temperature, max_tokens, thinking, stream_enabled, max_input_tokens,
    is_active, config_name, created_at, updated_at
) KEY (user_identifier, config_name)
SELECT 'default',
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
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM pd_ai_llm_user_configs
    WHERE user_identifier = 'default' AND config_name = 'teamshub-qwen3-30b-a3b'
);

-- 原 yml deepseek-chat（硅基流动，未激活备用）
MERGE INTO pd_ai_llm_user_configs (
    user_identifier, provider, model, api_key, base_url,
    auth_type, api_format, is_full_url,
    temperature, max_tokens, thinking, stream_enabled, max_input_tokens,
    is_active, config_name, created_at, updated_at
) KEY (user_identifier, config_name)
SELECT 'default',
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
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM pd_ai_llm_user_configs
    WHERE user_identifier = 'default' AND config_name = 'deepseek-chat'
);

-- ------------------------------------------------------------
-- 6. 用户认证种子（pd_ai_users）
--     初始账号：admin / admin123（首次登录后务必修改密码）
--     password_hash = salt:sha256(salt+password)
-- ------------------------------------------------------------

MERGE INTO pd_ai_users (
    username, password_hash, display_name, role,
    is_enabled, created_at, updated_at
) KEY (username) VALUES (
    'admin',
    'a1b2c3d4e5f60718293a4b5c6d7e8f90:0212b518b03b50cc62a0dadc9e897f48190e21e0af3ba93665b4b0085885e265',
    '管理员',
    'admin',
    1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
