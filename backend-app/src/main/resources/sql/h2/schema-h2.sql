-- ============================================================
-- Prod Platform AI - H2 全量模型 DDL（MySQL 兼容模式）
-- 来源：sql/01_full_schema_ddl.sql（权威）+ sql/03_flow_engine_tables.sql 引擎扩展列
-- 说明：
--   1) H2 MODE=MySQL 下运行，去除 ENGINE/CHARSET/DISTRIBUTED 等 MySQL 专属子句
--   2) DATETIME(6) → TIMESTAMP(6)；TINYINT(1) → TINYINT（H2 兼容布尔整型）
--   3) 已合并 03_flow_engine_tables.sql 的 workflow 扩展列（context_data 等）与
--      pd_ai_workflow_node_logs 节点级执行记录表
--   4) 由 spring.sql.init 每次启动执行；CREATE TABLE IF NOT EXISTS 保证幂等
--   5) 表/字段描述统一通过 H2 方言的 COMMENT ON 语句补充（MySQL 的
--      行内 COMMENT 子句 H2 不支持），语义与权威 MySQL DDL 对齐
-- ============================================================

-- ------------------------------------------------------------
-- 1. 聊天系统
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_chat_sessions (
    id                 INT           NOT NULL AUTO_INCREMENT,
    session_id         VARCHAR(64)   NOT NULL,
    user_id            VARCHAR(100)  DEFAULT NULL,
    title              VARCHAR(200)  DEFAULT NULL,
    context_tags       TEXT          DEFAULT NULL,
    session_metadata   TEXT          DEFAULT NULL,
    status             VARCHAR(20)   DEFAULT 'active',
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_cs_session_id UNIQUE (session_id)
);
CREATE INDEX IF NOT EXISTS idx_cs_user_id ON pd_ai_chat_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_cs_updated_at ON pd_ai_chat_sessions (updated_at);

CREATE TABLE IF NOT EXISTS pd_ai_chat_messages (
    id                 INT           NOT NULL AUTO_INCREMENT,
    message_id         VARCHAR(64)   NOT NULL,
    session_id         VARCHAR(64)   NOT NULL,
    role               VARCHAR(20)   NOT NULL,
    content            TEXT          NOT NULL,
    content_type       VARCHAR(20)   DEFAULT 'text',
    parent_id          VARCHAR(64)   DEFAULT NULL,
    sort_order         INT           NOT NULL DEFAULT 0,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_cm_message_id UNIQUE (message_id),
    CONSTRAINT fk_cm_session FOREIGN KEY (session_id) REFERENCES pd_ai_chat_sessions (session_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_cm_session_id ON pd_ai_chat_messages (session_id);
CREATE INDEX IF NOT EXISTS idx_cm_created_at ON pd_ai_chat_messages (created_at);

CREATE TABLE IF NOT EXISTS pd_ai_chat_message_metadata (
    id                 INT           NOT NULL AUTO_INCREMENT,
    message_id         VARCHAR(64)   NOT NULL,
    meta_key           VARCHAR(100)  NOT NULL,
    "value"            TEXT          DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_message_key UNIQUE (message_id, meta_key),
    CONSTRAINT fk_cmm_message FOREIGN KEY (message_id) REFERENCES pd_ai_chat_messages (message_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_cmm_meta_key ON pd_ai_chat_message_metadata (meta_key);

-- ------------------------------------------------------------
-- 2. MCP 工具管理
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_mcp_tool_definitions (
    id                 INT           NOT NULL AUTO_INCREMENT,
    tool_name          VARCHAR(100)  NOT NULL,
    tool_code          VARCHAR(100)  DEFAULT NULL,
    description        TEXT          DEFAULT NULL,
    category           VARCHAR(50)   DEFAULT NULL,
    is_enabled         TINYINT       NOT NULL DEFAULT 1,
    is_public          TINYINT       NOT NULL DEFAULT 1,
    input_schema       TEXT          DEFAULT NULL,
    output_schema      TEXT          DEFAULT NULL,
    tool_type          VARCHAR(20)   DEFAULT 'url',
    protocol           VARCHAR(10)   DEFAULT 'http',
    request_method     VARCHAR(16)   DEFAULT 'POST',
    url                VARCHAR(500)  DEFAULT NULL,
    auth_type          VARCHAR(20)   DEFAULT 'none',
    auth_info          TEXT          DEFAULT NULL,
    need_summary       TINYINT       NOT NULL DEFAULT 0,
    prompt             TEXT          DEFAULT NULL,
    config             TEXT          DEFAULT NULL,
    extra_metadata     TEXT          DEFAULT NULL,
    total_calls        INT           NOT NULL DEFAULT 0,
    last_called_at     TIMESTAMP(6)  DEFAULT NULL,
    created_by         VARCHAR(100)  DEFAULT NULL,
    updated_by         VARCHAR(100)  DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_tool_name UNIQUE (tool_name),
    CONSTRAINT uk_tool_code UNIQUE (tool_code)
);
CREATE INDEX IF NOT EXISTS idx_tool_code ON pd_ai_mcp_tool_definitions (tool_code);
CREATE INDEX IF NOT EXISTS idx_tool_category ON pd_ai_mcp_tool_definitions (category);
CREATE INDEX IF NOT EXISTS idx_tool_enabled ON pd_ai_mcp_tool_definitions (is_enabled);

CREATE TABLE IF NOT EXISTS pd_ai_mcp_call_logs (
    id                 INT           NOT NULL AUTO_INCREMENT,
    tool_name          VARCHAR(100)  NOT NULL,
    tool_category      VARCHAR(50)   DEFAULT NULL,
    success            TINYINT       NOT NULL DEFAULT 0,
    execution_time_ms  DOUBLE        DEFAULT NULL,
    error_message      TEXT          DEFAULT NULL,
    timestamp          TIMESTAMP(6)  DEFAULT NULL,
    request_args       TEXT          DEFAULT NULL,
    response_data      TEXT          DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_cl_tool_name ON pd_ai_mcp_call_logs (tool_name);
CREATE INDEX IF NOT EXISTS idx_cl_tool_category ON pd_ai_mcp_call_logs (tool_category);
CREATE INDEX IF NOT EXISTS idx_tool_timestamp ON pd_ai_mcp_call_logs (tool_name, timestamp);
CREATE INDEX IF NOT EXISTS idx_timestamp_desc ON pd_ai_mcp_call_logs (timestamp);

CREATE TABLE IF NOT EXISTS pd_ai_mcp_tool_stats (
    id                      INT           NOT NULL AUTO_INCREMENT,
    tool_name               VARCHAR(100)  NOT NULL,
    stat_date               VARCHAR(20)   NOT NULL,
    stat_hour               INT           DEFAULT NULL,
    total_calls             INT           NOT NULL DEFAULT 0,
    success_calls           INT           NOT NULL DEFAULT 0,
    failed_calls            INT           NOT NULL DEFAULT 0,
    total_response_time_ms  DOUBLE        NOT NULL DEFAULT 0,
    avg_response_time_ms    DOUBLE        NOT NULL DEFAULT 0,
    created_at              TIMESTAMP(6)  DEFAULT NULL,
    updated_at              TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_tool_date_hour UNIQUE (tool_name, stat_date, stat_hour)
);
CREATE INDEX IF NOT EXISTS idx_ts_tool_name ON pd_ai_mcp_tool_stats (tool_name);
CREATE INDEX IF NOT EXISTS idx_ts_stat_date ON pd_ai_mcp_tool_stats (stat_date);

-- ------------------------------------------------------------
-- 3. LLM 用户配置
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_llm_user_configs (
    id                 INT           NOT NULL AUTO_INCREMENT,
    user_identifier    VARCHAR(100)  NOT NULL,
    provider           VARCHAR(50)   NOT NULL DEFAULT 'custom',
    model              VARCHAR(100)  NOT NULL,
    api_key            TEXT          DEFAULT NULL,
    base_url           TEXT          DEFAULT NULL,
    auth_type          VARCHAR(20)   NOT NULL DEFAULT 'bearer',
    auth_header        VARCHAR(50)   DEFAULT NULL,
    api_format         VARCHAR(50)   NOT NULL DEFAULT 'openai',
    is_full_url        TINYINT       NOT NULL DEFAULT 0,
    temperature        DOUBLE        NOT NULL DEFAULT 0.3,
    max_tokens         INT           NOT NULL DEFAULT 2048,
    thinking           TINYINT       NOT NULL DEFAULT 0,
    stream_enabled     TINYINT       NOT NULL DEFAULT 1,
    max_input_tokens   INT           DEFAULT 180000,
    is_active          TINYINT       NOT NULL DEFAULT 1,
    config_name        VARCHAR(100)  DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    last_used_at       TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_llm_user_identifier ON pd_ai_llm_user_configs (user_identifier);

-- ------------------------------------------------------------
-- 4. 提示词管理
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_prompts (
    id                 INT           NOT NULL AUTO_INCREMENT,
    code               VARCHAR(100)  NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    description        TEXT          DEFAULT NULL,
    category           VARCHAR(50)   DEFAULT 'general',
    content            TEXT          NOT NULL,
    variables          TEXT          DEFAULT NULL,
    tools              TEXT          DEFAULT NULL,
    is_template        TINYINT       DEFAULT 0,
    version            INT           DEFAULT 1,
    is_active          TINYINT       DEFAULT 1,
    created_by         VARCHAR(100)  DEFAULT NULL,
    updated_by         VARCHAR(100)  DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_prompt_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS pd_ai_prompt_versions (
    id                 INT           NOT NULL AUTO_INCREMENT,
    prompt_id          INT           NOT NULL,
    version            INT           NOT NULL,
    content            TEXT          NOT NULL,
    variables          TEXT          DEFAULT NULL,
    tools              TEXT          DEFAULT NULL,
    change_note        TEXT          DEFAULT NULL,
    created_by         VARCHAR(100)  DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pv_prompt FOREIGN KEY (prompt_id) REFERENCES pd_ai_prompts (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_pv_prompt_id ON pd_ai_prompt_versions (prompt_id);

CREATE TABLE IF NOT EXISTS pd_ai_prompt_templates (
    id                 INT           NOT NULL AUTO_INCREMENT,
    code               VARCHAR(100)  NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    description        TEXT          DEFAULT NULL,
    category           VARCHAR(50)   DEFAULT 'general',
    content            TEXT          NOT NULL,
    variables          TEXT          DEFAULT NULL,
    tools              TEXT          DEFAULT NULL,
    tags               TEXT          DEFAULT NULL,
    is_builtin         TINYINT       DEFAULT 0,
    is_active          TINYINT       DEFAULT 1,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_pt_code UNIQUE (code)
);

-- ------------------------------------------------------------
-- 5. 工作流（含 03_flow_engine_tables.sql 扩展列）
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_workflows (
    id                     INT           NOT NULL AUTO_INCREMENT,
    workflow_code          VARCHAR(100)  NOT NULL,
    workflow_name          VARCHAR(200)  NOT NULL,
    description            TEXT          DEFAULT NULL,
    category               VARCHAR(50)   DEFAULT 'general',
    tags                   TEXT          NOT NULL,
    priority               INT           DEFAULT 10,
    is_active              TINYINT       DEFAULT 1,
    is_in_library          TINYINT       DEFAULT 0,
    workflow_data          TEXT          NOT NULL,
    version                INT           DEFAULT 1,
    execution_count        INT           DEFAULT 0,
    last_execution_at      TIMESTAMP(6)  DEFAULT NULL,
    last_execution_status  VARCHAR(20)   DEFAULT NULL,
    created_by             VARCHAR(100)  DEFAULT NULL,
    updated_by             VARCHAR(100)  DEFAULT NULL,
    created_at             TIMESTAMP(6)  DEFAULT NULL,
    updated_at             TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_wf_code UNIQUE (workflow_code)
);

CREATE TABLE IF NOT EXISTS pd_ai_workflow_history (
    id                 INT           NOT NULL AUTO_INCREMENT,
    workflow_id        INT           NOT NULL,
    workflow_code      VARCHAR(100)  NOT NULL,
    version            INT           NOT NULL,
    workflow_name      VARCHAR(200)  NOT NULL,
    description        TEXT          DEFAULT NULL,
    workflow_data      TEXT          NOT NULL,
    category           VARCHAR(50)   DEFAULT NULL,
    tags               TEXT          NOT NULL,
    priority           INT           DEFAULT NULL,
    is_active          TINYINT       DEFAULT NULL,
    change_note        TEXT          DEFAULT NULL,
    created_by         VARCHAR(100)  DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wh_workflow FOREIGN KEY (workflow_id) REFERENCES pd_ai_workflows (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_wh_workflow_id ON pd_ai_workflow_history (workflow_id);
CREATE INDEX IF NOT EXISTS idx_wh_workflow_code ON pd_ai_workflow_history (workflow_code);

CREATE TABLE IF NOT EXISTS pd_ai_workflow_executions (
    id                 INT           NOT NULL AUTO_INCREMENT,
    workflow_id        INT           NOT NULL,
    workflow_code      VARCHAR(100)  NOT NULL,
    execution_id       VARCHAR(100)  NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'pending',
    start_time         TIMESTAMP(6)  DEFAULT NULL,
    end_time           TIMESTAMP(6)  DEFAULT NULL,
    duration_seconds   INT           DEFAULT NULL,
    input_data         TEXT          NOT NULL,
    output_data        TEXT          DEFAULT NULL,
    error_message      TEXT          DEFAULT NULL,
    execution_logs     TEXT          NOT NULL,
    triggered_by       VARCHAR(100)  DEFAULT NULL,
    trigger_type       VARCHAR(20)   DEFAULT 'manual',
    notes              TEXT          DEFAULT NULL,
    context_data       TEXT          DEFAULT NULL,
    current_node_id    VARCHAR(64)   DEFAULT NULL,
    resume_token       VARCHAR(64)   DEFAULT NULL,
    status_version     INT           NOT NULL DEFAULT 0,
    workflow_version   INT           DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_we_execution_id UNIQUE (execution_id),
    CONSTRAINT fk_we_workflow FOREIGN KEY (workflow_id) REFERENCES pd_ai_workflows (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_we_workflow_id ON pd_ai_workflow_executions (workflow_id);
CREATE INDEX IF NOT EXISTS idx_we_workflow_code ON pd_ai_workflow_executions (workflow_code);

CREATE TABLE IF NOT EXISTS pd_ai_workflow_node_logs (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    execution_id       VARCHAR(100)  NOT NULL,
    node_id            VARCHAR(64)   NOT NULL,
    node_name          VARCHAR(128)  DEFAULT NULL,
    node_type          VARCHAR(32)   NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    attempt            INT           NOT NULL DEFAULT 1,
    input_data         TEXT          DEFAULT NULL,
    output_data        TEXT          DEFAULT NULL,
    error_message      TEXT          DEFAULT NULL,
    branch_taken       VARCHAR(128)  DEFAULT NULL,
    started_at         TIMESTAMP(6)  NOT NULL,
    ended_at           TIMESTAMP(6)  DEFAULT NULL,
    duration_ms        BIGINT        DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_fnl_exec ON pd_ai_workflow_node_logs (execution_id);
CREATE INDEX IF NOT EXISTS idx_fnl_exec_node ON pd_ai_workflow_node_logs (execution_id, node_id, attempt);

-- 旧库文件升级：node_logs 表已存在时补齐 node_name 列（幂等，H2 2.x 支持 ADD COLUMN IF NOT EXISTS）
ALTER TABLE pd_ai_workflow_node_logs ADD COLUMN IF NOT EXISTS node_name VARCHAR(128) DEFAULT NULL;

-- ------------------------------------------------------------
-- 6. 链路追踪
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_traces (
    id                 VARCHAR(36)   NOT NULL,
    service_name       VARCHAR(100)  DEFAULT 'harness',
    start_time         TIMESTAMP(6)  NOT NULL,
    end_time           TIMESTAMP(6)  DEFAULT NULL,
    total_duration_ms  DOUBLE        DEFAULT NULL,
    span_count         INT           DEFAULT 0,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_trace_service_name ON pd_ai_traces (service_name);
CREATE INDEX IF NOT EXISTS idx_trace_created_at ON pd_ai_traces (created_at);

CREATE TABLE IF NOT EXISTS pd_ai_spans (
    id                 VARCHAR(36)   NOT NULL,
    trace_id           VARCHAR(36)   NOT NULL,
    parent_span_id     VARCHAR(36)   DEFAULT NULL,
    name               VARCHAR(200)  NOT NULL,
    component          VARCHAR(100)  DEFAULT 'harness',
    start_time         TIMESTAMP(6)  NOT NULL,
    end_time           TIMESTAMP(6)  DEFAULT NULL,
    duration_ms        DOUBLE        DEFAULT NULL,
    status             VARCHAR(20)   DEFAULT 'ok',
    tags               TEXT          DEFAULT NULL,
    logs               TEXT          DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_span_trace FOREIGN KEY (trace_id) REFERENCES pd_ai_traces (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_span_trace_id ON pd_ai_spans (trace_id);
CREATE INDEX IF NOT EXISTS idx_span_parent_id ON pd_ai_spans (parent_span_id);
CREATE INDEX IF NOT EXISTS idx_span_component ON pd_ai_spans (component);
CREATE INDEX IF NOT EXISTS idx_span_status ON pd_ai_spans (status);

-- ------------------------------------------------------------
-- 7. 本体实例（配置填报）+ 版本库
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_ontology_instance (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    ontology_code      VARCHAR(255)  DEFAULT NULL,
    user_id            VARCHAR(255)  DEFAULT NULL,
    session_id         VARCHAR(255)  DEFAULT NULL,
    status             VARCHAR(255)  DEFAULT NULL,
    submitted_at       TIMESTAMP(6)  DEFAULT NULL,
    data_json          TEXT          DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_oi_session_id ON pd_ai_ontology_instance (session_id);

CREATE TABLE IF NOT EXISTS pd_ai_ontology_version (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    asset_type         VARCHAR(32)   NOT NULL,
    asset_code         VARCHAR(128)  NOT NULL,
    version            VARCHAR(64)   NOT NULL,
    status             VARCHAR(32)   NOT NULL DEFAULT 'draft',
    author             VARCHAR(64)   DEFAULT NULL,
    summary            VARCHAR(512)  DEFAULT NULL,
    payload            TEXT          DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    published_at       TIMESTAMP(6)  DEFAULT NULL,
    deprecated_at      TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_oav_type_code_version UNIQUE (asset_type, asset_code, version)
);
CREATE INDEX IF NOT EXISTS idx_oav_asset ON pd_ai_ontology_version (asset_type, asset_code);
CREATE INDEX IF NOT EXISTS idx_oav_status ON pd_ai_ontology_version (status);

CREATE TABLE IF NOT EXISTS pd_ai_ontology_version_log (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    version_id         BIGINT        DEFAULT NULL,
    domain             VARCHAR(32)   NOT NULL DEFAULT 'version',
    trace_id           VARCHAR(128)  DEFAULT NULL,
    action             VARCHAR(32)   NOT NULL,
    operator           VARCHAR(64)   DEFAULT NULL,
    detail             TEXT          DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ovl_version_id ON pd_ai_ontology_version_log (version_id);
CREATE INDEX IF NOT EXISTS idx_ovl_domain ON pd_ai_ontology_version_log (domain);
CREATE INDEX IF NOT EXISTS idx_ovl_trace_id ON pd_ai_ontology_version_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_ovl_action ON pd_ai_ontology_version_log (action);
CREATE INDEX IF NOT EXISTS idx_ovl_created ON pd_ai_ontology_version_log (created_at);

-- ------------------------------------------------------------
-- 8. SWRL / 条件 DSL 规则
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_swrl_rules (
    id                 INT           NOT NULL AUTO_INCREMENT,
    rule_id            VARCHAR(64)   NOT NULL,
    rule_name          VARCHAR(200)  NOT NULL,
    module             VARCHAR(100)  DEFAULT NULL,
    description        TEXT          DEFAULT NULL,
    condition_expr     TEXT          DEFAULT NULL,
    action_expr        TEXT          DEFAULT NULL,
    enabled            TINYINT       NOT NULL DEFAULT 1,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT idx_sr_rule_id UNIQUE (rule_id)
);
CREATE INDEX IF NOT EXISTS idx_sr_module ON pd_ai_swrl_rules (module);

-- ------------------------------------------------------------
-- 8a. 节点结果存储（产销品加载 V1.6 · save_node_result / query_node_result）
--     双键形态：legacy(req_id+node_name) / V1.6(result_key)
--     同键覆盖：重跑环节仅保留最新一条
--     同构 MySQL DDL 见 sql/01_full_schema_ddl.sql
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pd_ai_node_results (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    record_id          VARCHAR(64)   NOT NULL,
    req_id             VARCHAR(128)  DEFAULT NULL,
    node_name          VARCHAR(64)   DEFAULT NULL,
    result_key         VARCHAR(191)  DEFAULT NULL,
    result_json        TEXT          DEFAULT NULL,
    status             VARCHAR(32)   NOT NULL DEFAULT 'ok',
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_nr_record_id UNIQUE (record_id)
);
CREATE INDEX IF NOT EXISTS idx_nr_req_node ON pd_ai_node_results (req_id, node_name, updated_at);
CREATE INDEX IF NOT EXISTS idx_nr_key ON pd_ai_node_results (result_key, updated_at);

-- ------------------------------------------------------------
-- 9. 产商品运营工单
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_ops_work_orders (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    work_order_id      VARCHAR(64)   NOT NULL,
    title              VARCHAR(255)  NOT NULL,
    offering_id        VARCHAR(64)   DEFAULT NULL,
    offering_name      VARCHAR(255)  DEFAULT NULL,
    summary            TEXT          DEFAULT NULL,
    actions            TEXT          DEFAULT NULL,
    status             VARCHAR(32)   NOT NULL DEFAULT 'open',
    source             VARCHAR(64)   DEFAULT NULL,
    session_id         VARCHAR(64)   DEFAULT NULL,
    hypo_mode          VARCHAR(32)   DEFAULT NULL,
    payload            TEXT          DEFAULT NULL,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_owo_work_order_id UNIQUE (work_order_id)
);
CREATE INDEX IF NOT EXISTS idx_owo_offering ON pd_ai_ops_work_orders (offering_id);
CREATE INDEX IF NOT EXISTS idx_owo_status ON pd_ai_ops_work_orders (status);
CREATE INDEX IF NOT EXISTS idx_owo_session ON pd_ai_ops_work_orders (session_id);
CREATE INDEX IF NOT EXISTS idx_owo_created ON pd_ai_ops_work_orders (created_at);

-- ------------------------------------------------------------
-- 22. 用户认证（pd_ai_users）
--     password_hash 格式：salt hex + ":" + SHA-256(salt + password) hex
--     username 为登录唯一标识；display_name 用于界面展示
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pd_ai_users (
    id                 INT           NOT NULL AUTO_INCREMENT,
    username           VARCHAR(64)   NOT NULL,
    password_hash      VARCHAR(128)  NOT NULL,
    display_name       VARCHAR(100)  DEFAULT NULL,
    role               VARCHAR(32)   NOT NULL DEFAULT 'user',
    is_enabled         TINYINT       NOT NULL DEFAULT 1,
    created_at         TIMESTAMP(6)  DEFAULT NULL,
    updated_at         TIMESTAMP(6)  DEFAULT NULL,
    last_login_at      TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
);

-- ------------------------------------------------------------
-- 23. 运营指标宽表（指标域 P0：dwd_prod_metric_daily）
--     商品×地区×渠道×客群 日粒度指标事实；本地 H2 由 mock 源灌演示数据，
--     生产由 T+1 ETL 从业务系统写入（同构 MySQL DDL 见 sql/02_metric_schema.sql）
--     order_cnt_addon/order_cnt_main 供派生指标 attach_rate 使用，缺省可空
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dwd_prod_metric_daily (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    stat_date          DATE          NOT NULL,
    offering_id        VARCHAR(64)   NOT NULL,
    region_id          VARCHAR(32)   NOT NULL DEFAULT 'ALL',
    channel_id         VARCHAR(32)   NOT NULL DEFAULT 'ALL',
    customer_segment   VARCHAR(32)   NOT NULL DEFAULT 'ALL',
    revenue            DECIMAL(18,2) NOT NULL DEFAULT 0,
    order_cnt          INT           NOT NULL DEFAULT 0,
    order_cnt_addon    INT           DEFAULT NULL,
    order_cnt_main     INT           DEFAULT NULL,
    new_users          INT           NOT NULL DEFAULT 0,
    churn_users        INT           NOT NULL DEFAULT 0,
    active_users       INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_pmd_natural UNIQUE (stat_date, offering_id, region_id, channel_id, customer_segment)
);
CREATE INDEX IF NOT EXISTS idx_pmd_offering_date ON dwd_prod_metric_daily (offering_id, stat_date);
CREATE INDEX IF NOT EXISTS idx_pmd_date ON dwd_prod_metric_daily (stat_date);
CREATE INDEX IF NOT EXISTS idx_pmd_region ON dwd_prod_metric_daily (region_id, stat_date);
CREATE INDEX IF NOT EXISTS idx_pmd_channel ON dwd_prod_metric_daily (channel_id, stat_date);

-- ------------------------------------------------------------
-- 24. ABox 在架商品事实表（ABoxSyncScheduler 同步源：pd_ops_shelf_offerings）
--     abox-source=jdbc 时 JdbcOpsProductDataSource.DEFAULT_SQL 只读查询本表，
--     snake_case 列名自动转 lowerCamelCase 映射货架行字段；
--     同构 MySQL DDL 见 sql/04_abox_shelf_view.sql（含 ETL 约定与演示种子）
--     state 取值英文枚举：on_shelf/on_sale（DEFAULT_SQL 仅取这两种）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pd_ops_shelf_offerings (
    offering_id      VARCHAR(64)   NOT NULL,
    offering_name    VARCHAR(255)  NOT NULL,
    category_code    VARCHAR(64)   DEFAULT NULL,
    category_name    VARCHAR(128)  DEFAULT NULL,
    product_line     VARCHAR(64)   DEFAULT NULL,
    offering_type    VARCHAR(32)   NOT NULL DEFAULT 'addon',
    state            VARCHAR(32)   NOT NULL,
    monthly_fee      DECIMAL(18,2) NOT NULL DEFAULT 0,
    fixed_fee_amount DECIMAL(18,2) DEFAULT NULL,
    sales_cnt_30d    INT           NOT NULL DEFAULT 0,
    revenue_30d      DECIMAL(18,2) NOT NULL DEFAULT 0,
    shelf_days       INT           NOT NULL DEFAULT 0,
    message_root_key VARCHAR(64)   DEFAULT NULL,
    category         VARCHAR(32)   NOT NULL DEFAULT 'normal',
    PRIMARY KEY (offering_id)
);
CREATE INDEX IF NOT EXISTS idx_osf_state ON pd_ops_shelf_offerings (state);
CREATE INDEX IF NOT EXISTS idx_osf_category_code ON pd_ops_shelf_offerings (category_code);
CREATE INDEX IF NOT EXISTS idx_osf_category ON pd_ops_shelf_offerings (category);

-- ------------------------------------------------------------
-- 25. 商品变更订阅与提醒（方案 §6-C3，对应缺口 C5）
--     订阅登记入口：product_change_alert 工具 subscribe 动作（订阅人取服务端登录态）；
--     提醒产生：ChangeDetectService 图谱重载后新旧快照 diff，命中订阅商品落提醒行；
--     出口 v1 = 本表（工具 list_alerts 查询透出），生产可加站内信/短信监听者扩展。
--     同构 MySQL DDL 见 sql/05_change_subscription_tables.sql
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pd_ai_product_subscriptions (
    id             BIGINT        AUTO_INCREMENT PRIMARY KEY,
    subscriber     VARCHAR(64)   NOT NULL,
    offering_id    VARCHAR(64)   NOT NULL,
    offering_name  VARCHAR(255)  DEFAULT NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'active',
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_psub UNIQUE (subscriber, offering_id)
);
CREATE INDEX IF NOT EXISTS idx_psub_offering ON pd_ai_product_subscriptions (offering_id, status);

CREATE TABLE IF NOT EXISTS pd_ai_change_alerts (
    id               BIGINT        AUTO_INCREMENT PRIMARY KEY,
    offering_id      VARCHAR(64)   NOT NULL,
    offering_name    VARCHAR(255)  DEFAULT NULL,
    change_type      VARCHAR(32)   NOT NULL,
    old_value        VARCHAR(255)  DEFAULT NULL,
    new_value        VARCHAR(255)  DEFAULT NULL,
    detected_version VARCHAR(64)   DEFAULT NULL,
    subscriber       VARCHAR(64)   DEFAULT NULL,
    read_flag        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_alert_sub ON pd_ai_change_alerts (subscriber, read_flag, created_at);
CREATE INDEX IF NOT EXISTS idx_alert_offering ON pd_ai_change_alerts (offering_id, created_at);

-- ============================================================
-- 26. 表与字段描述（COMMENT ON）
--     MySQL 行内 COMMENT 子句在 H2 MODE=MySQL 下不支持，统一改用
--     H2 方言 COMMENT ON 语句；内容与 sql/01_full_schema_ddl.sql 等
--     权威 MySQL DDL 的 COMMENT 语义对齐，重复执行幂等覆盖
-- ============================================================

-- ------------------------------------------------------------
-- 1. 聊天系统
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_chat_sessions IS '聊天会话表';
COMMENT ON COLUMN pd_ai_chat_sessions.id IS '自增主键';
COMMENT ON COLUMN pd_ai_chat_sessions.session_id IS '会话唯一标识（UUID）';
COMMENT ON COLUMN pd_ai_chat_sessions.user_id IS '用户ID（可空，支持匿名）';
COMMENT ON COLUMN pd_ai_chat_sessions.title IS '会话标题';
COMMENT ON COLUMN pd_ai_chat_sessions.context_tags IS '会话标签 JSON 数组';
COMMENT ON COLUMN pd_ai_chat_sessions.session_metadata IS '会话扩展信息 JSON 对象';
COMMENT ON COLUMN pd_ai_chat_sessions.status IS '状态：active / archived';
COMMENT ON COLUMN pd_ai_chat_sessions.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_chat_sessions.updated_at IS '最后更新时间';

COMMENT ON TABLE pd_ai_chat_messages IS '聊天消息表';
COMMENT ON COLUMN pd_ai_chat_messages.id IS '自增主键';
COMMENT ON COLUMN pd_ai_chat_messages.message_id IS '消息唯一标识（UUID）';
COMMENT ON COLUMN pd_ai_chat_messages.session_id IS '所属会话ID';
COMMENT ON COLUMN pd_ai_chat_messages.role IS '角色：user / assistant / system';
COMMENT ON COLUMN pd_ai_chat_messages.content IS '消息正文';
COMMENT ON COLUMN pd_ai_chat_messages.content_type IS '内容类型：text / markdown / json / form / thinking';
COMMENT ON COLUMN pd_ai_chat_messages.parent_id IS '父消息ID';
COMMENT ON COLUMN pd_ai_chat_messages.sort_order IS '同会话内排序';
COMMENT ON COLUMN pd_ai_chat_messages.created_at IS '创建时间';

COMMENT ON TABLE pd_ai_chat_message_metadata IS '消息 KV 扩展表';
COMMENT ON COLUMN pd_ai_chat_message_metadata.id IS '自增主键';
COMMENT ON COLUMN pd_ai_chat_message_metadata.message_id IS '所属消息ID';
COMMENT ON COLUMN pd_ai_chat_message_metadata.meta_key IS '扩展字段名';
COMMENT ON COLUMN pd_ai_chat_message_metadata."value" IS '扩展字段值（VALUE为保留字，需引号转义）';
COMMENT ON COLUMN pd_ai_chat_message_metadata.created_at IS '创建时间';

-- ------------------------------------------------------------
-- 2. MCP 工具管理
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_mcp_tool_definitions IS 'MCP工具定义表';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.id IS '自增主键';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.tool_name IS '工具名称（唯一）';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.tool_code IS '工具编码（唯一）';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.description IS '工具描述';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.category IS '工具分类';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.is_enabled IS '是否启用（0/1）';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.is_public IS '是否公开（0/1）';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.input_schema IS '输入 Schema JSON';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.output_schema IS '输出 Schema JSON';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.tool_type IS '工具类型：url 等';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.protocol IS '协议：http 等';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.request_method IS '请求方法：GET/POST 等';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.url IS '工具调用地址';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.auth_type IS '鉴权类型：none 等';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.auth_info IS '鉴权信息（脱敏存储）';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.need_summary IS '结果是否需要 LLM 摘要（0/1）';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.prompt IS '摘要用提示词';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.config IS '工具配置 JSON';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.extra_metadata IS '扩展元数据 JSON';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.total_calls IS '累计调用次数';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.last_called_at IS '最近调用时间';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.created_by IS '创建人';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.updated_by IS '更新人';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_mcp_tool_definitions.updated_at IS '最后更新时间';

COMMENT ON TABLE pd_ai_mcp_call_logs IS 'MCP工具调用日志表';
COMMENT ON COLUMN pd_ai_mcp_call_logs.id IS '自增主键';
COMMENT ON COLUMN pd_ai_mcp_call_logs.tool_name IS '工具名称';
COMMENT ON COLUMN pd_ai_mcp_call_logs.tool_category IS '工具分类';
COMMENT ON COLUMN pd_ai_mcp_call_logs.success IS '是否成功（0/1）';
COMMENT ON COLUMN pd_ai_mcp_call_logs.execution_time_ms IS '执行耗时（毫秒）';
COMMENT ON COLUMN pd_ai_mcp_call_logs.error_message IS '错误信息';
COMMENT ON COLUMN pd_ai_mcp_call_logs.timestamp IS '调用时间';
COMMENT ON COLUMN pd_ai_mcp_call_logs.request_args IS '请求参数 JSON';
COMMENT ON COLUMN pd_ai_mcp_call_logs.response_data IS '响应数据 JSON';

COMMENT ON TABLE pd_ai_mcp_tool_stats IS 'MCP工具聚合统计表';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.id IS '自增主键';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.tool_name IS '工具名称';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.stat_date IS '统计日期（YYYY-MM-DD）';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.stat_hour IS '统计小时（0-23，NULL=日统计）';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.total_calls IS '总调用次数';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.success_calls IS '成功次数';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.failed_calls IS '失败次数';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.total_response_time_ms IS '总响应耗时（毫秒）';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.avg_response_time_ms IS '平均响应耗时（毫秒）';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_mcp_tool_stats.updated_at IS '最后更新时间';

-- ------------------------------------------------------------
-- 3. LLM 用户配置
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_llm_user_configs IS '用户LLM配置表';
COMMENT ON COLUMN pd_ai_llm_user_configs.id IS '自增主键';
COMMENT ON COLUMN pd_ai_llm_user_configs.user_identifier IS '用户标识';
COMMENT ON COLUMN pd_ai_llm_user_configs.provider IS '提供商：openai/azure/custom/local';
COMMENT ON COLUMN pd_ai_llm_user_configs.model IS '模型名称';
COMMENT ON COLUMN pd_ai_llm_user_configs.api_key IS 'API 密钥（脱敏存储）';
COMMENT ON COLUMN pd_ai_llm_user_configs.base_url IS '服务基础地址';
COMMENT ON COLUMN pd_ai_llm_user_configs.auth_type IS '鉴权类型：bearer 等';
COMMENT ON COLUMN pd_ai_llm_user_configs.auth_header IS '自定义鉴权请求头名';
COMMENT ON COLUMN pd_ai_llm_user_configs.api_format IS 'API 格式：openai 等';
COMMENT ON COLUMN pd_ai_llm_user_configs.is_full_url IS 'base_url 是否为完整请求地址（0/1）';
COMMENT ON COLUMN pd_ai_llm_user_configs.temperature IS '采样温度';
COMMENT ON COLUMN pd_ai_llm_user_configs.max_tokens IS '最大输出 token 数';
COMMENT ON COLUMN pd_ai_llm_user_configs.thinking IS '是否开启思考模式（0/1）';
COMMENT ON COLUMN pd_ai_llm_user_configs.stream_enabled IS '是否流式输出，0=非流式';
COMMENT ON COLUMN pd_ai_llm_user_configs.max_input_tokens IS '最大输入 token 数';
COMMENT ON COLUMN pd_ai_llm_user_configs.is_active IS '是否当前激活配置（0/1）';
COMMENT ON COLUMN pd_ai_llm_user_configs.config_name IS '配置名称';
COMMENT ON COLUMN pd_ai_llm_user_configs.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_llm_user_configs.updated_at IS '最后更新时间';
COMMENT ON COLUMN pd_ai_llm_user_configs.last_used_at IS '最近使用时间';

-- ------------------------------------------------------------
-- 4. 提示词管理
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_prompts IS '提示词主表';
COMMENT ON COLUMN pd_ai_prompts.id IS '自增主键';
COMMENT ON COLUMN pd_ai_prompts.code IS '提示词编码（唯一）';
COMMENT ON COLUMN pd_ai_prompts.name IS '提示词名称';
COMMENT ON COLUMN pd_ai_prompts.description IS '描述';
COMMENT ON COLUMN pd_ai_prompts.category IS '分类';
COMMENT ON COLUMN pd_ai_prompts.content IS '提示词正文';
COMMENT ON COLUMN pd_ai_prompts.variables IS '变量定义 JSON 数组';
COMMENT ON COLUMN pd_ai_prompts.tools IS '可用工具 JSON 数组';
COMMENT ON COLUMN pd_ai_prompts.is_template IS '是否模板（0/1）';
COMMENT ON COLUMN pd_ai_prompts.version IS '当前版本号';
COMMENT ON COLUMN pd_ai_prompts.is_active IS '是否启用（0/1）';
COMMENT ON COLUMN pd_ai_prompts.created_by IS '创建人';
COMMENT ON COLUMN pd_ai_prompts.updated_by IS '更新人';
COMMENT ON COLUMN pd_ai_prompts.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_prompts.updated_at IS '最后更新时间';

COMMENT ON TABLE pd_ai_prompt_versions IS '提示词版本历史';
COMMENT ON COLUMN pd_ai_prompt_versions.id IS '自增主键';
COMMENT ON COLUMN pd_ai_prompt_versions.prompt_id IS '提示词ID（pd_ai_prompts.id）';
COMMENT ON COLUMN pd_ai_prompt_versions.version IS '版本号';
COMMENT ON COLUMN pd_ai_prompt_versions.content IS '该版本提示词正文';
COMMENT ON COLUMN pd_ai_prompt_versions.variables IS '变量定义 JSON 数组';
COMMENT ON COLUMN pd_ai_prompt_versions.tools IS '可用工具 JSON 数组';
COMMENT ON COLUMN pd_ai_prompt_versions.change_note IS '变更说明';
COMMENT ON COLUMN pd_ai_prompt_versions.created_by IS '创建人';
COMMENT ON COLUMN pd_ai_prompt_versions.created_at IS '创建时间';

COMMENT ON TABLE pd_ai_prompt_templates IS '提示词预设模板库';
COMMENT ON COLUMN pd_ai_prompt_templates.id IS '自增主键';
COMMENT ON COLUMN pd_ai_prompt_templates.code IS '模板编码（唯一）';
COMMENT ON COLUMN pd_ai_prompt_templates.name IS '模板名称';
COMMENT ON COLUMN pd_ai_prompt_templates.description IS '描述';
COMMENT ON COLUMN pd_ai_prompt_templates.category IS '分类';
COMMENT ON COLUMN pd_ai_prompt_templates.content IS '模板正文';
COMMENT ON COLUMN pd_ai_prompt_templates.variables IS '变量定义 JSON 数组';
COMMENT ON COLUMN pd_ai_prompt_templates.tools IS '可用工具 JSON 数组';
COMMENT ON COLUMN pd_ai_prompt_templates.tags IS '标签 JSON 数组';
COMMENT ON COLUMN pd_ai_prompt_templates.is_builtin IS '是否内置（0/1）';
COMMENT ON COLUMN pd_ai_prompt_templates.is_active IS '是否启用（0/1）';
COMMENT ON COLUMN pd_ai_prompt_templates.created_at IS '创建时间';

-- ------------------------------------------------------------
-- 5. 工作流
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_workflows IS '工作流主表';
COMMENT ON COLUMN pd_ai_workflows.id IS '自增主键';
COMMENT ON COLUMN pd_ai_workflows.workflow_code IS '工作流编码（唯一）';
COMMENT ON COLUMN pd_ai_workflows.workflow_name IS '工作流名称';
COMMENT ON COLUMN pd_ai_workflows.description IS '描述';
COMMENT ON COLUMN pd_ai_workflows.category IS '分类';
COMMENT ON COLUMN pd_ai_workflows.tags IS '标签 JSON 数组';
COMMENT ON COLUMN pd_ai_workflows.priority IS '优先级（小值优先）';
COMMENT ON COLUMN pd_ai_workflows.is_active IS '是否启用（0/1）';
COMMENT ON COLUMN pd_ai_workflows.is_in_library IS '是否入库到工作流库（0/1）';
COMMENT ON COLUMN pd_ai_workflows.workflow_data IS '完整工作流配置 JSON';
COMMENT ON COLUMN pd_ai_workflows.version IS '当前版本号';
COMMENT ON COLUMN pd_ai_workflows.execution_count IS '累计执行次数';
COMMENT ON COLUMN pd_ai_workflows.last_execution_at IS '最近执行时间';
COMMENT ON COLUMN pd_ai_workflows.last_execution_status IS '最近执行状态';
COMMENT ON COLUMN pd_ai_workflows.created_by IS '创建人';
COMMENT ON COLUMN pd_ai_workflows.updated_by IS '更新人';
COMMENT ON COLUMN pd_ai_workflows.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_workflows.updated_at IS '最后更新时间';

COMMENT ON TABLE pd_ai_workflow_history IS '工作流版本历史';
COMMENT ON COLUMN pd_ai_workflow_history.id IS '自增主键';
COMMENT ON COLUMN pd_ai_workflow_history.workflow_id IS '工作流ID（pd_ai_workflows.id）';
COMMENT ON COLUMN pd_ai_workflow_history.workflow_code IS '工作流编码';
COMMENT ON COLUMN pd_ai_workflow_history.version IS '版本号';
COMMENT ON COLUMN pd_ai_workflow_history.workflow_name IS '工作流名称快照';
COMMENT ON COLUMN pd_ai_workflow_history.description IS '描述快照';
COMMENT ON COLUMN pd_ai_workflow_history.workflow_data IS '该版本完整工作流配置 JSON';
COMMENT ON COLUMN pd_ai_workflow_history.category IS '分类快照';
COMMENT ON COLUMN pd_ai_workflow_history.tags IS '标签 JSON 数组快照';
COMMENT ON COLUMN pd_ai_workflow_history.priority IS '优先级快照';
COMMENT ON COLUMN pd_ai_workflow_history.is_active IS '是否启用快照（0/1）';
COMMENT ON COLUMN pd_ai_workflow_history.change_note IS '变更说明';
COMMENT ON COLUMN pd_ai_workflow_history.created_by IS '创建人';
COMMENT ON COLUMN pd_ai_workflow_history.created_at IS '创建时间';

COMMENT ON TABLE pd_ai_workflow_executions IS '工作流执行记录';
COMMENT ON COLUMN pd_ai_workflow_executions.id IS '自增主键';
COMMENT ON COLUMN pd_ai_workflow_executions.workflow_id IS '工作流ID（pd_ai_workflows.id）';
COMMENT ON COLUMN pd_ai_workflow_executions.workflow_code IS '工作流编码';
COMMENT ON COLUMN pd_ai_workflow_executions.execution_id IS '执行实例唯一标识';
COMMENT ON COLUMN pd_ai_workflow_executions.status IS '执行状态：pending/running/suspended/completed/failed';
COMMENT ON COLUMN pd_ai_workflow_executions.start_time IS '开始时间';
COMMENT ON COLUMN pd_ai_workflow_executions.end_time IS '结束时间';
COMMENT ON COLUMN pd_ai_workflow_executions.duration_seconds IS '执行时长（秒）';
COMMENT ON COLUMN pd_ai_workflow_executions.input_data IS '输入 JSON';
COMMENT ON COLUMN pd_ai_workflow_executions.output_data IS '输出 JSON';
COMMENT ON COLUMN pd_ai_workflow_executions.error_message IS '错误信息';
COMMENT ON COLUMN pd_ai_workflow_executions.execution_logs IS '执行日志 JSON 数组';
COMMENT ON COLUMN pd_ai_workflow_executions.triggered_by IS '触发人';
COMMENT ON COLUMN pd_ai_workflow_executions.trigger_type IS '触发类型：manual 等';
COMMENT ON COLUMN pd_ai_workflow_executions.notes IS '备注';
COMMENT ON COLUMN pd_ai_workflow_executions.context_data IS '运行上下文（各节点输出合并，恢复执行的数据源）';
COMMENT ON COLUMN pd_ai_workflow_executions.current_node_id IS '当前推进到的节点';
COMMENT ON COLUMN pd_ai_workflow_executions.resume_token IS '人工节点恢复令牌（一次有效）';
COMMENT ON COLUMN pd_ai_workflow_executions.status_version IS '乐观锁版本';
COMMENT ON COLUMN pd_ai_workflow_executions.workflow_version IS '执行时锁定的流程定义版本（回滚安全）';
COMMENT ON COLUMN pd_ai_workflow_executions.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_workflow_executions.updated_at IS '最后更新时间';

COMMENT ON TABLE pd_ai_workflow_node_logs IS '流程节点级执行记录（审计与断点恢复依据）';
COMMENT ON COLUMN pd_ai_workflow_node_logs.id IS '自增主键';
COMMENT ON COLUMN pd_ai_workflow_node_logs.execution_id IS '执行实例 ID';
COMMENT ON COLUMN pd_ai_workflow_node_logs.node_id IS '节点 ID';
COMMENT ON COLUMN pd_ai_workflow_node_logs.node_name IS '节点业务名（定义期 name 标签）';
COMMENT ON COLUMN pd_ai_workflow_node_logs.node_type IS '节点类型';
COMMENT ON COLUMN pd_ai_workflow_node_logs.status IS '状态：running/completed/skipped/failed';
COMMENT ON COLUMN pd_ai_workflow_node_logs.attempt IS '第几次重试';
COMMENT ON COLUMN pd_ai_workflow_node_logs.input_data IS '节点实际入参（变量解析后）';
COMMENT ON COLUMN pd_ai_workflow_node_logs.output_data IS '节点输出';
COMMENT ON COLUMN pd_ai_workflow_node_logs.error_message IS '错误信息';
COMMENT ON COLUMN pd_ai_workflow_node_logs.branch_taken IS 'condition 命中的分支 id 及表达式原文';
COMMENT ON COLUMN pd_ai_workflow_node_logs.started_at IS '开始时间';
COMMENT ON COLUMN pd_ai_workflow_node_logs.ended_at IS '结束时间';
COMMENT ON COLUMN pd_ai_workflow_node_logs.duration_ms IS '执行耗时（毫秒）';
COMMENT ON COLUMN pd_ai_workflow_node_logs.created_at IS '创建时间（实体 FieldFill.INSERT）';
COMMENT ON COLUMN pd_ai_workflow_node_logs.updated_at IS '更新时间（实体 FieldFill.INSERT_UPDATE）';

-- ------------------------------------------------------------
-- 6. 链路追踪
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_traces IS '追踪记录';
COMMENT ON COLUMN pd_ai_traces.id IS '追踪 ID（UUID）';
COMMENT ON COLUMN pd_ai_traces.service_name IS '服务名（默认 harness）';
COMMENT ON COLUMN pd_ai_traces.start_time IS '开始时间';
COMMENT ON COLUMN pd_ai_traces.end_time IS '结束时间';
COMMENT ON COLUMN pd_ai_traces.total_duration_ms IS '总耗时（毫秒）';
COMMENT ON COLUMN pd_ai_traces.span_count IS 'Span 数量';
COMMENT ON COLUMN pd_ai_traces.created_at IS '创建时间';

COMMENT ON TABLE pd_ai_spans IS '追踪 Span';
COMMENT ON COLUMN pd_ai_spans.id IS 'Span ID（UUID）';
COMMENT ON COLUMN pd_ai_spans.trace_id IS '所属追踪 ID（pd_ai_traces.id）';
COMMENT ON COLUMN pd_ai_spans.parent_span_id IS '父 Span ID';
COMMENT ON COLUMN pd_ai_spans.name IS 'Span 名称';
COMMENT ON COLUMN pd_ai_spans.component IS '组件名（默认 harness）';
COMMENT ON COLUMN pd_ai_spans.start_time IS '开始时间';
COMMENT ON COLUMN pd_ai_spans.end_time IS '结束时间';
COMMENT ON COLUMN pd_ai_spans.duration_ms IS '耗时（毫秒）';
COMMENT ON COLUMN pd_ai_spans.status IS '状态：ok / error / timeout';
COMMENT ON COLUMN pd_ai_spans.tags IS '标签 JSON';
COMMENT ON COLUMN pd_ai_spans.logs IS '日志 JSON 数组';
COMMENT ON COLUMN pd_ai_spans.created_at IS '创建时间';

-- ------------------------------------------------------------
-- 7. 本体实例（配置填报）+ 版本库
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_ontology_instance IS '本体实例主表';
COMMENT ON COLUMN pd_ai_ontology_instance.id IS '自增主键';
COMMENT ON COLUMN pd_ai_ontology_instance.ontology_code IS '本体编码';
COMMENT ON COLUMN pd_ai_ontology_instance.user_id IS '用户ID';
COMMENT ON COLUMN pd_ai_ontology_instance.session_id IS '来源会话 ID';
COMMENT ON COLUMN pd_ai_ontology_instance.status IS '实例状态';
COMMENT ON COLUMN pd_ai_ontology_instance.submitted_at IS '提交时间';
COMMENT ON COLUMN pd_ai_ontology_instance.data_json IS 'KV 数据 JSON 序列化（原 instance_data 子表）';

COMMENT ON TABLE pd_ai_ontology_version IS '本体资产版本主表';
COMMENT ON COLUMN pd_ai_ontology_version.id IS '自增主键';
COMMENT ON COLUMN pd_ai_ontology_version.asset_type IS '资产类型：template / message_projection / ops_rules / ttl / abox_snapshot';
COMMENT ON COLUMN pd_ai_ontology_version.asset_code IS '资产编码：template_id / 文件名等';
COMMENT ON COLUMN pd_ai_ontology_version.version IS '语义化版本（semver）';
COMMENT ON COLUMN pd_ai_ontology_version.status IS '状态：draft / review / published / deprecated';
COMMENT ON COLUMN pd_ai_ontology_version.author IS '作者';
COMMENT ON COLUMN pd_ai_ontology_version.summary IS '版本摘要';
COMMENT ON COLUMN pd_ai_ontology_version.payload IS '源码全文（回滚唯一事实源）';
COMMENT ON COLUMN pd_ai_ontology_version.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_ontology_version.published_at IS '发布时间';
COMMENT ON COLUMN pd_ai_ontology_version.deprecated_at IS '废弃时间';

COMMENT ON TABLE pd_ai_ontology_version_log IS '本体资产版本动作日志（审计一张表）';
COMMENT ON COLUMN pd_ai_ontology_version_log.id IS '自增主键';
COMMENT ON COLUMN pd_ai_ontology_version_log.version_id IS '外键 pd_ai_ontology_version.id（非版本键控审计可空）';
COMMENT ON COLUMN pd_ai_ontology_version_log.domain IS '审计域：version / risk / config / batch';
COMMENT ON COLUMN pd_ai_ontology_version_log.trace_id IS 'config 链路 trace_id';
COMMENT ON COLUMN pd_ai_ontology_version_log.action IS '动作：publish / rollback / deprecate / reload / override / config_step / batch_audit';
COMMENT ON COLUMN pd_ai_ontology_version_log.operator IS '操作人';
COMMENT ON COLUMN pd_ai_ontology_version_log.detail IS '动作明细 JSON';
COMMENT ON COLUMN pd_ai_ontology_version_log.created_at IS '创建时间';

-- ------------------------------------------------------------
-- 8. SWRL / 条件 DSL 规则
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_swrl_rules IS '条件DSL规则表（非OWL SWRL）';
COMMENT ON COLUMN pd_ai_swrl_rules.id IS '自增主键';
COMMENT ON COLUMN pd_ai_swrl_rules.rule_id IS '规则编码（唯一）';
COMMENT ON COLUMN pd_ai_swrl_rules.rule_name IS '规则名称';
COMMENT ON COLUMN pd_ai_swrl_rules.module IS '所属模块';
COMMENT ON COLUMN pd_ai_swrl_rules.description IS '规则描述';
COMMENT ON COLUMN pd_ai_swrl_rules.condition_expr IS '条件表达式（条件 DSL）';
COMMENT ON COLUMN pd_ai_swrl_rules.action_expr IS '动作表达式（条件 DSL）';
COMMENT ON COLUMN pd_ai_swrl_rules.enabled IS '是否启用（0/1）';
COMMENT ON COLUMN pd_ai_swrl_rules.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_swrl_rules.updated_at IS '最后更新时间';

-- ------------------------------------------------------------
-- 8a. 节点结果存储
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_node_results IS '节点结果存储（V1.6 子工作流环节结果）';
COMMENT ON COLUMN pd_ai_node_results.id IS '自增主键';
COMMENT ON COLUMN pd_ai_node_results.record_id IS '记录唯一标识（同键覆盖业务键，唯一）';
COMMENT ON COLUMN pd_ai_node_results.req_id IS '遗留键：请求 ID';
COMMENT ON COLUMN pd_ai_node_results.node_name IS '遗留键：环节名称';
COMMENT ON COLUMN pd_ai_node_results.result_key IS 'V1.6 键：plan_id 或 EXEC{execution_id}_STAGE{n}';
COMMENT ON COLUMN pd_ai_node_results.result_json IS '结果 JSON 透传存储（≤64KB）';
COMMENT ON COLUMN pd_ai_node_results.status IS '结果状态：ok 等';
COMMENT ON COLUMN pd_ai_node_results.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_node_results.updated_at IS '最后更新时间';

-- ------------------------------------------------------------
-- 9. 产商品运营工单
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_ops_work_orders IS '产商品运营处置工单';
COMMENT ON COLUMN pd_ai_ops_work_orders.id IS '自增主键';
COMMENT ON COLUMN pd_ai_ops_work_orders.work_order_id IS '工单唯一标识';
COMMENT ON COLUMN pd_ai_ops_work_orders.title IS '工单标题';
COMMENT ON COLUMN pd_ai_ops_work_orders.offering_id IS '关联产商品编码';
COMMENT ON COLUMN pd_ai_ops_work_orders.offering_name IS '关联产商品名称';
COMMENT ON COLUMN pd_ai_ops_work_orders.summary IS '处置摘要';
COMMENT ON COLUMN pd_ai_ops_work_orders.actions IS '处置动作 JSON 数组';
COMMENT ON COLUMN pd_ai_ops_work_orders.status IS '工单状态：open 等';
COMMENT ON COLUMN pd_ai_ops_work_orders.source IS '工单来源';
COMMENT ON COLUMN pd_ai_ops_work_orders.session_id IS '来源会话 ID（研发助手会话内工单聚合）';
COMMENT ON COLUMN pd_ai_ops_work_orders.hypo_mode IS '假设模式';
COMMENT ON COLUMN pd_ai_ops_work_orders.payload IS '扩展载荷 JSON';
COMMENT ON COLUMN pd_ai_ops_work_orders.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_ops_work_orders.updated_at IS '最后更新时间';

-- ------------------------------------------------------------
-- 22. 用户认证
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_users IS '用户表（认证与用户信息存储）';
COMMENT ON COLUMN pd_ai_users.id IS '自增主键';
COMMENT ON COLUMN pd_ai_users.username IS '登录用户名（唯一）';
COMMENT ON COLUMN pd_ai_users.password_hash IS '口令哈希：salt hex + ":" + SHA-256(salt + password) hex';
COMMENT ON COLUMN pd_ai_users.display_name IS '展示名（界面显示）';
COMMENT ON COLUMN pd_ai_users.role IS '角色：user / admin';
COMMENT ON COLUMN pd_ai_users.is_enabled IS '是否启用（0/1）';
COMMENT ON COLUMN pd_ai_users.created_at IS '创建时间';
COMMENT ON COLUMN pd_ai_users.updated_at IS '最后更新时间';
COMMENT ON COLUMN pd_ai_users.last_login_at IS '最近登录时间';

-- ------------------------------------------------------------
-- 23. 运营指标宽表
-- ------------------------------------------------------------
COMMENT ON TABLE dwd_prod_metric_daily IS '产商品运营指标日宽表';
COMMENT ON COLUMN dwd_prod_metric_daily.id IS '自增主键';
COMMENT ON COLUMN dwd_prod_metric_daily.stat_date IS '统计日期（日粒度）';
COMMENT ON COLUMN dwd_prod_metric_daily.offering_id IS '产商品编码';
COMMENT ON COLUMN dwd_prod_metric_daily.region_id IS '地区维度，ALL=全量';
COMMENT ON COLUMN dwd_prod_metric_daily.channel_id IS '渠道维度，ALL=全量';
COMMENT ON COLUMN dwd_prod_metric_daily.customer_segment IS '客群维度，ALL=全量';
COMMENT ON COLUMN dwd_prod_metric_daily.revenue IS '收入（元）';
COMMENT ON COLUMN dwd_prod_metric_daily.order_cnt IS '订购量（笔）';
COMMENT ON COLUMN dwd_prod_metric_daily.order_cnt_addon IS '附加品订购量（attach_rate 派生用）';
COMMENT ON COLUMN dwd_prod_metric_daily.order_cnt_main IS '主资费订购量（attach_rate 派生用）';
COMMENT ON COLUMN dwd_prod_metric_daily.new_users IS '新增用户（户）';
COMMENT ON COLUMN dwd_prod_metric_daily.churn_users IS '流失用户（户）';
COMMENT ON COLUMN dwd_prod_metric_daily.active_users IS '活跃用户（户，日快照）';

-- ------------------------------------------------------------
-- 24. ABox 在架商品事实表
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ops_shelf_offerings IS 'ABox 在架商品事实表（业务系统只读视图同构，abox-source=jdbc 数据源）';
COMMENT ON COLUMN pd_ops_shelf_offerings.offering_id IS '产商品编码（offeringId，硬校验非空）';
COMMENT ON COLUMN pd_ops_shelf_offerings.offering_name IS '产商品名称（offeringName）';
COMMENT ON COLUMN pd_ops_shelf_offerings.category_code IS '品类编码（categoryCode，如 familyBasePrc）';
COMMENT ON COLUMN pd_ops_shelf_offerings.category_name IS '品类名称（categoryName，如 家庭基础套餐）';
COMMENT ON COLUMN pd_ops_shelf_offerings.product_line IS '产品线（productLine：家庭/宽带/个人）';
COMMENT ON COLUMN pd_ops_shelf_offerings.offering_type IS '商品类型（offeringType：main_pkg/addon/fusion）';
COMMENT ON COLUMN pd_ops_shelf_offerings.state IS '状态（state：on_shelf/on_sale；DEFAULT_SQL 仅取这两种）';
COMMENT ON COLUMN pd_ops_shelf_offerings.monthly_fee IS '月费（monthlyFee，元）';
COMMENT ON COLUMN pd_ops_shelf_offerings.fixed_fee_amount IS '一次性/固定费（fixedFeeAmount，元）';
COMMENT ON COLUMN pd_ops_shelf_offerings.sales_cnt_30d IS '近30天订购量（salesCnt30d）';
COMMENT ON COLUMN pd_ops_shelf_offerings.revenue_30d IS '近30天收入（revenue30d，元）';
COMMENT ON COLUMN pd_ops_shelf_offerings.shelf_days IS '在架天数（shelfDays）';
COMMENT ON COLUMN pd_ops_shelf_offerings.message_root_key IS '报文根键（messageRootKey，模板品类码）';
COMMENT ON COLUMN pd_ops_shelf_offerings.category IS '风险标记（category：normal/zero_fee/low_eff/whitelist/abnormal_discount/threshold_demo）';

-- ------------------------------------------------------------
-- 25. 商品变更订阅与提醒
-- ------------------------------------------------------------
COMMENT ON TABLE pd_ai_product_subscriptions IS '商品变更订阅登记（C3）';
COMMENT ON COLUMN pd_ai_product_subscriptions.id IS '自增主键';
COMMENT ON COLUMN pd_ai_product_subscriptions.subscriber IS '订阅人登录名（服务端登录态）';
COMMENT ON COLUMN pd_ai_product_subscriptions.offering_id IS '订阅商品编码';
COMMENT ON COLUMN pd_ai_product_subscriptions.offering_name IS '订阅时商品名称快照';
COMMENT ON COLUMN pd_ai_product_subscriptions.status IS '状态：active/cancelled';
COMMENT ON COLUMN pd_ai_product_subscriptions.created_at IS '订阅时间';

COMMENT ON TABLE pd_ai_change_alerts IS '商品变更提醒记录（C3）';
COMMENT ON COLUMN pd_ai_change_alerts.id IS '自增主键';
COMMENT ON COLUMN pd_ai_change_alerts.offering_id IS '商品编码';
COMMENT ON COLUMN pd_ai_change_alerts.offering_name IS '商品名称';
COMMENT ON COLUMN pd_ai_change_alerts.change_type IS '变更类型：fee_change/state_change';
COMMENT ON COLUMN pd_ai_change_alerts.old_value IS '变更前值';
COMMENT ON COLUMN pd_ai_change_alerts.new_value IS '变更后值';
COMMENT ON COLUMN pd_ai_change_alerts.detected_version IS '检测来源快照版本';
COMMENT ON COLUMN pd_ai_change_alerts.subscriber IS '提醒接收人（空=广播）';
COMMENT ON COLUMN pd_ai_change_alerts.read_flag IS '是否已读（0/1）';
COMMENT ON COLUMN pd_ai_change_alerts.created_at IS '产生时间';


