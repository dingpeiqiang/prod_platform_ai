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

-- ------------------------------------------------------------
-- 7. ABox 在架商品种子（pd_ops_shelf_offerings，§24）
--     92 行对齐 classpath:ontology/mock_graph.json shelfOfferings；
--     abox-source=jdbc 时 ABoxSyncScheduler 从本表同步进事实图，
--     与 mock 源（直接读 mock_graph.json）数据一致，联调可平滑切换。
--     幂等：MERGE INTO ... KEY(offering_id)
-- ------------------------------------------------------------

MERGE INTO pd_ops_shelf_offerings (
    offering_id, offering_name, category_code, category_name, product_line,
    offering_type, state, monthly_fee, fixed_fee_amount,
    sales_cnt_30d, revenue_30d, shelf_days, message_root_key, category
) KEY (offering_id) VALUES
    ('SCHEME_FAP_001', '家庭增值权益20', 'familyAddPrc', '家庭附加业务', '家庭', 'addon', 'on_shelf', 20, 20, 90, 1800, 40, 'familyAddPrc', 'normal'),
    ('SCHEME_FBP_001', '家庭亲情网基础套餐', 'familyBasePrc', '家庭基础套餐', '家庭', 'fusion', 'on_shelf', 99, 99, 150, 14850, 120, 'familyBasePrc', 'normal'),
    ('SCHEME_BOS_001', '宽带提速包30', 'broadBandOptSpeedPrc', '宽带加速包', '宽带', 'addon', 'on_shelf', 30, 30, 80, 2400, 50, 'broadBandOptSpeedPrc', 'normal'),
    ('SCHEME_PAP_001', '个人附加流量包19', 'personAddPrc', '个人附加资费', '个人', 'addon', 'on_shelf', 19, 19, 300, 5700, 60, 'personAddPrc', 'normal'),
    ('SCHEME_BBM_001', '宽带主资费80', 'broadBandMainPrc', '宽带主资费', '宽带', 'main_pkg', 'on_shelf', 80, 80, 120, 9600, 90, 'broadBandMainPrc', 'normal'),
    ('SCHEME_PMP_001', '测试资费-个人主', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 40, 40, 200, 8000, 100, 'personMainPrc', 'normal'),
    ('OF-HF-128', '家庭融合畅享128', 'familyBasePrc', '家庭基础套餐', '家庭', 'main_pkg', 'on_shelf', 128, 128, 860, 110080, 210, 'familyBasePrc', 'normal'),
    ('OF-RISK-001', '校园体验流量包0元', 'personAddPrc', '个人附加资费', '个人', 'addon', 'on_shelf', 0, 0, 120, 0, 45, 'personAddPrc', 'zero_fee'),
    ('OF-LOW-019', '旧版彩铃包-2019', NULL, NULL, NULL, 'addon', 'on_shelf', 3, NULL, 0, 0, 287, NULL, 'low_eff'),
    ('OF-GIFT-WL', '会员权益赠送流量包', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 200, 0, 60, NULL, 'whitelist'),
    ('OF-DISC-001', '全额赠送可重复体验包', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 55, 0, 40, NULL, 'abnormal_discount'),
    ('OF-DISC-002', '全额赠送可重复包-02', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 22, 50, 34, NULL, 'abnormal_discount'),
    ('OF-DISC-003', '全额赠送可重复包-03', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 23, 50, 36, NULL, 'abnormal_discount'),
    ('OF-DISC-004', '全额赠送可重复包-04', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 24, 50, 38, NULL, 'abnormal_discount'),
    ('OF-DISC-005', '全额赠送可重复包-05', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 25, 50, 40, NULL, 'abnormal_discount'),
    ('OF-LOW-001', '旧版加装包-长期零销-02', NULL, NULL, NULL, 'addon', 'on_shelf', 7, NULL, 0, 0, 218, NULL, 'low_eff'),
    ('OF-LOW-002', '旧版加装包-长期零销-03', NULL, NULL, NULL, 'addon', 'on_shelf', 8, NULL, 0, 0, 232, NULL, 'low_eff'),
    ('OF-LOW-003', '旧版加装包-长期零销-04', NULL, NULL, NULL, 'addon', 'on_shelf', 9, NULL, 0, 0, 246, NULL, 'low_eff'),
    ('OF-LOW-004', '旧版加装包-长期零销-05', NULL, NULL, NULL, 'addon', 'on_shelf', 10, NULL, 0, 0, 260, NULL, 'low_eff'),
    ('OF-LOW-005', '旧版加装包-长期零销-06', NULL, NULL, NULL, 'addon', 'on_shelf', 11, NULL, 0, 0, 274, NULL, 'low_eff'),
    ('OF-LOW-006', '旧版加装包-长期零销-07', NULL, NULL, NULL, 'addon', 'on_shelf', 12, NULL, 0, 0, 288, NULL, 'low_eff'),
    ('OF-LOW-T01', '旧版加装包-阈值演示-01', NULL, NULL, NULL, 'addon', 'on_shelf', 7, NULL, 0, 3, 112, NULL, 'threshold_demo'),
    ('OF-LOW-T02', '旧版加装包-阈值演示-02', NULL, NULL, NULL, 'addon', 'on_shelf', 8, NULL, 0, 4, 124, NULL, 'threshold_demo'),
    ('OF-LOW-T03', '旧版加装包-阈值演示-03', NULL, NULL, NULL, 'addon', 'on_shelf', 9, NULL, 0, 5, 136, NULL, 'threshold_demo'),
    ('OF-N-001', '标准套餐-001', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 44, NULL, 83, 5120, 61, NULL, 'normal'),
    ('OF-N-002', '标准套餐-002', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 49, NULL, 86, 5240, 62, NULL, 'normal'),
    ('OF-N-003', '标准套餐-003', NULL, NULL, NULL, 'addon', 'on_shelf', 54, NULL, 89, 5360, 63, NULL, 'normal'),
    ('OF-N-004', '标准套餐-004', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 59, NULL, 92, 5480, 64, NULL, 'normal'),
    ('OF-N-005', '标准套餐-005', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 64, NULL, 95, 5600, 65, NULL, 'normal'),
    ('OF-N-006', '标准套餐-006', NULL, NULL, NULL, 'addon', 'on_shelf', 69, NULL, 98, 5720, 66, NULL, 'normal'),
    ('OF-N-007', '标准套餐-007', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 74, NULL, 101, 5840, 67, NULL, 'normal'),
    ('OF-N-008', '标准套餐-008', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 79, NULL, 104, 5960, 68, NULL, 'normal'),
    ('OF-N-009', '标准套餐-009', NULL, NULL, NULL, 'addon', 'on_shelf', 84, NULL, 107, 6080, 69, NULL, 'normal'),
    ('OF-N-010', '标准套餐-010', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 89, NULL, 110, 6200, 70, NULL, 'normal'),
    ('OF-N-011', '标准套餐-011', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 94, NULL, 113, 6320, 71, NULL, 'normal'),
    ('OF-N-012', '标准套餐-012', NULL, NULL, NULL, 'addon', 'on_shelf', 99, NULL, 116, 6440, 72, NULL, 'normal'),
    ('OF-N-013', '标准套餐-013', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 104, NULL, 119, 6560, 73, NULL, 'normal'),
    ('OF-N-014', '标准套餐-014', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 109, NULL, 122, 6680, 74, NULL, 'normal'),
    ('OF-N-015', '标准套餐-015', NULL, NULL, NULL, 'addon', 'on_shelf', 114, NULL, 125, 6800, 75, NULL, 'normal'),
    ('OF-N-016', '标准套餐-016', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 119, NULL, 128, 6920, 76, NULL, 'normal'),
    ('OF-N-017', '标准套餐-017', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 124, NULL, 131, 7040, 77, NULL, 'normal'),
    ('OF-N-018', '标准套餐-018', NULL, NULL, NULL, 'addon', 'on_shelf', 129, NULL, 134, 7160, 78, NULL, 'normal'),
    ('OF-N-019', '标准套餐-019', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 134, NULL, 137, 7280, 79, NULL, 'normal'),
    ('OF-N-020', '标准套餐-020', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 39, NULL, 140, 7400, 80, NULL, 'normal'),
    ('OF-N-021', '标准套餐-021', NULL, NULL, NULL, 'addon', 'on_shelf', 44, NULL, 143, 7520, 81, NULL, 'normal'),
    ('OF-N-022', '标准套餐-022', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 49, NULL, 146, 7640, 82, NULL, 'normal'),
    ('OF-N-023', '标准套餐-023', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 54, NULL, 149, 7760, 83, NULL, 'normal'),
    ('OF-N-024', '标准套餐-024', NULL, NULL, NULL, 'addon', 'on_shelf', 59, NULL, 152, 7880, 84, NULL, 'normal'),
    ('OF-N-025', '标准套餐-025', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 64, NULL, 155, 8000, 85, NULL, 'normal'),
    ('OF-N-026', '标准套餐-026', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 69, NULL, 158, 8120, 86, NULL, 'normal'),
    ('OF-N-027', '标准套餐-027', NULL, NULL, NULL, 'addon', 'on_shelf', 74, NULL, 161, 8240, 87, NULL, 'normal'),
    ('OF-N-028', '标准套餐-028', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 79, NULL, 164, 8360, 88, NULL, 'normal'),
    ('OF-N-029', '标准套餐-029', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 84, NULL, 167, 8480, 89, NULL, 'normal'),
    ('OF-N-030', '标准套餐-030', NULL, NULL, NULL, 'addon', 'on_shelf', 89, NULL, 170, 8600, 90, NULL, 'normal'),
    ('OF-N-031', '标准套餐-031', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 94, NULL, 173, 8720, 91, NULL, 'normal'),
    ('OF-N-032', '标准套餐-032', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 99, NULL, 176, 8840, 92, NULL, 'normal'),
    ('OF-N-033', '标准套餐-033', NULL, NULL, NULL, 'addon', 'on_shelf', 104, NULL, 179, 8960, 93, NULL, 'normal'),
    ('OF-N-034', '标准套餐-034', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 109, NULL, 182, 9080, 94, NULL, 'normal'),
    ('OF-N-035', '标准套餐-035', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 114, NULL, 185, 9200, 95, NULL, 'normal'),
    ('OF-N-036', '标准套餐-036', NULL, NULL, NULL, 'addon', 'on_shelf', 119, NULL, 188, 9320, 96, NULL, 'normal'),
    ('OF-N-037', '标准套餐-037', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 124, NULL, 191, 9440, 97, NULL, 'normal'),
    ('OF-N-038', '标准套餐-038', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 129, NULL, 194, 9560, 98, NULL, 'normal'),
    ('OF-N-039', '标准套餐-039', NULL, NULL, NULL, 'addon', 'on_shelf', 134, NULL, 197, 9680, 99, NULL, 'normal'),
    ('OF-N-040', '标准套餐-040', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 39, NULL, 200, 9800, 100, NULL, 'normal'),
    ('OF-N-041', '标准套餐-041', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 44, NULL, 203, 9920, 101, NULL, 'normal'),
    ('OF-N-042', '标准套餐-042', NULL, NULL, NULL, 'addon', 'on_shelf', 49, NULL, 206, 10040, 102, NULL, 'normal'),
    ('OF-N-043', '标准套餐-043', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 54, NULL, 209, 10160, 103, NULL, 'normal'),
    ('OF-N-044', '标准套餐-044', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 59, NULL, 212, 10280, 104, NULL, 'normal'),
    ('OF-N-045', '标准套餐-045', NULL, NULL, NULL, 'addon', 'on_shelf', 64, NULL, 215, 10400, 105, NULL, 'normal'),
    ('OF-N-046', '标准套餐-046', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 69, NULL, 218, 10520, 106, NULL, 'normal'),
    ('OF-N-047', '标准套餐-047', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 74, NULL, 221, 10640, 107, NULL, 'normal'),
    ('OF-N-048', '标准套餐-048', NULL, NULL, NULL, 'addon', 'on_shelf', 79, NULL, 224, 10760, 108, NULL, 'normal'),
    ('OF-N-049', '标准套餐-049', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 84, NULL, 227, 10880, 109, NULL, 'normal'),
    ('OF-N-050', '标准套餐-050', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 89, NULL, 230, 11000, 110, NULL, 'normal'),
    ('OF-N-051', '标准套餐-051', NULL, NULL, NULL, 'addon', 'on_shelf', 94, NULL, 233, 11120, 111, NULL, 'normal'),
    ('OF-N-052', '标准套餐-052', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 99, NULL, 236, 11240, 112, NULL, 'normal'),
    ('OF-N-053', '标准套餐-053', NULL, NULL, NULL, 'addon', 'on_shelf', 104, NULL, 239, 11360, 113, NULL, 'normal'),
    ('OF-N-054', '标准套餐-054', NULL, NULL, NULL, 'addon', 'on_shelf', 109, NULL, 242, 11480, 114, NULL, 'normal'),
    ('OF-N-055', '标准套餐-055', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 114, NULL, 245, 11600, 115, NULL, 'normal'),
    ('OF-RISK-002', '体验测试流量包0元-02', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 12, 0, 41, NULL, 'zero_fee'),
    ('OF-RISK-003', '体验测试流量包0元-03', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 13, 0, 44, NULL, 'zero_fee'),
    ('OF-RISK-004', '体验测试流量包0元-04', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 14, 0, 47, NULL, 'zero_fee'),
    ('OF-RISK-005', '体验测试流量包0元-05', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 15, 0, 50, NULL, 'zero_fee'),
    ('OF-RISK-006', '体验测试流量包0元-06', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 16, 0, 53, NULL, 'zero_fee'),
    ('OF-RISK-007', '体验测试流量包0元-07', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 17, 0, 56, NULL, 'zero_fee'),
    ('OF-RISK-008', '体验测试流量包0元-08', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 18, 0, 59, NULL, 'zero_fee'),
    ('OF-CAMPUS-STU-29', '大学生专属套餐29', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 29, 29, 260, 7540, 12, 'personMainPrc', 'normal'),
    ('OF-CAMPUS-STU-39', '大学生畅学套餐39', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 39, 39, 410, 15990, 25, 'personMainPrc', 'normal'),
    ('OF-CAMPUS-STU-59', '校园青春大学生59', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 59, 59, 520, 30680, 30, 'personMainPrc', 'normal'),
    ('OF-CAMPUS-STU-0', '大学生体验套餐0元', 'personAddPrc', '个人附加资费', '个人', 'addon', 'on_shelf', 0, 0, 880, 0, 7, 'personAddPrc', 'zero_fee'),
    ('OF-CAMPUS-STU-19', '大学生流量加餐包19', 'personAddPrc', '个人附加资费', '个人', 'addon', 'on_shelf', 19, 19, 640, 12160, 18, 'personAddPrc', 'normal'),
    ('OF-CAMPUS-STU-OLD-49', '大学生经典套餐49', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 49, 49, 300, 14700, 95, 'personMainPrc', 'normal');
