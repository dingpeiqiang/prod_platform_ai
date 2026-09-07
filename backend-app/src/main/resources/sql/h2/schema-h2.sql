-- ============================================================
-- Prod Platform AI - H2 全量模型 DDL（MySQL 兼容模式）
-- 来源：sql/01_full_schema_ddl.sql（权威）+ sql/03_flow_engine_tables.sql 引擎扩展列
-- 说明：
--   1) H2 MODE=MySQL 下运行，去除 ENGINE/CHARSET/COMMENT/DISTRIBUTED 等 MySQL 专属子句
--   2) DATETIME(6) → TIMESTAMP(6)；TINYINT(1) → TINYINT（H2 兼容布尔整型）
--   3) 已合并 03_flow_engine_tables.sql 的 workflow 扩展列（context_data 等）与
--      pd_ai_workflow_node_logs 节点级执行记录表
--   4) 由 spring.sql.init 每次启动执行；CREATE TABLE IF NOT EXISTS 保证幂等
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
