-- ============================================================
-- Prod Platform AI - 鍏ㄩ噺 DDL锛圙oldenDB / 鍒嗗竷寮� MySQL 鏂硅█锛塡n-- 鐢� sql/h2/schema-h2.sql 杞崲鑰屾潵锛歕n--   1) COMMENT ON TABLE/COLUMN 鏀逛负琛屽唴 COMMENT
--   2) CREATE INDEX IF NOT EXISTS 鏀逛负 ALTER TABLE ... ADD INDEX
--   3) 鍘绘帀 ENGINE/CHARSET锛堝垎甯冨紡搴撲笉鏀寔锛夛紝鍘绘帀澶栭敭锛圤RA-02441 涓嶆敮鎸侊級
--   4) 姣忚〃琛� distributed by duplicate(g1)锛堝垎甯冨紡搴撳己鍒惰姹傦級
-- 鎵ц椤哄簭锛氬厛 CREATE TABLE锛屽悗 ALTER TABLE ADD INDEX
-- ============================================================

-- pd_ai_chat_sessions
CREATE TABLE IF NOT EXISTS `pd_ai_chat_sessions` (
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
)
 distributed by duplicate(g1);


-- pd_ai_chat_messages
CREATE TABLE IF NOT EXISTS `pd_ai_chat_messages` (
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
	CONSTRAINT idx_cm_message_id UNIQUE (message_id)
)
 distributed by duplicate(g1);


-- pd_ai_chat_message_metadata
CREATE TABLE IF NOT EXISTS `pd_ai_chat_message_metadata` (
	id                 INT           NOT NULL AUTO_INCREMENT,
	message_id         VARCHAR(64)   NOT NULL,
	meta_key           VARCHAR(100)  NOT NULL,
	"value"            TEXT          DEFAULT NULL,
	created_at         TIMESTAMP(6)  DEFAULT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uq_message_key UNIQUE (message_id, meta_key)
)
 distributed by duplicate(g1);


-- pd_ai_mcp_tool_definitions
CREATE TABLE IF NOT EXISTS `pd_ai_mcp_tool_definitions` (
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
)
 distributed by duplicate(g1);


-- pd_ai_mcp_call_logs
CREATE TABLE IF NOT EXISTS `pd_ai_mcp_call_logs` (
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
)
 distributed by duplicate(g1);


-- pd_ai_mcp_tool_stats
CREATE TABLE IF NOT EXISTS `pd_ai_mcp_tool_stats` (
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
)
 distributed by duplicate(g1);


-- pd_ai_llm_user_configs
CREATE TABLE IF NOT EXISTS `pd_ai_llm_user_configs` (
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
)
 distributed by duplicate(g1);


-- pd_ai_prompts
CREATE TABLE IF NOT EXISTS `pd_ai_prompts` (
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
)
 distributed by duplicate(g1);


-- pd_ai_prompt_versions
CREATE TABLE IF NOT EXISTS `pd_ai_prompt_versions` (
	id                 INT           NOT NULL AUTO_INCREMENT,
	prompt_id          INT           NOT NULL,
	version            INT           NOT NULL,
	content            TEXT          NOT NULL,
	variables          TEXT          DEFAULT NULL,
	tools              TEXT          DEFAULT NULL,
	change_note        TEXT          DEFAULT NULL,
	created_by         VARCHAR(100)  DEFAULT NULL,
	created_at         TIMESTAMP(6)  DEFAULT NULL,
	PRIMARY KEY (id)
)
 distributed by duplicate(g1);


-- pd_ai_prompt_templates
CREATE TABLE IF NOT EXISTS `pd_ai_prompt_templates` (
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
)
 distributed by duplicate(g1);


-- pd_ai_workflows
CREATE TABLE IF NOT EXISTS `pd_ai_workflows` (
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
)
 distributed by duplicate(g1);


-- pd_ai_workflow_history
CREATE TABLE IF NOT EXISTS `pd_ai_workflow_history` (
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
	PRIMARY KEY (id)
)
 distributed by duplicate(g1);


-- pd_ai_workflow_executions
CREATE TABLE IF NOT EXISTS `pd_ai_workflow_executions` (
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
	CONSTRAINT idx_we_execution_id UNIQUE (execution_id)
)
 distributed by duplicate(g1);


-- pd_ai_workflow_node_logs
CREATE TABLE IF NOT EXISTS `pd_ai_workflow_node_logs` (
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
)
 distributed by duplicate(g1);


-- pd_ai_traces
CREATE TABLE IF NOT EXISTS `pd_ai_traces` (
	id                 VARCHAR(36)   NOT NULL,
	service_name       VARCHAR(100)  DEFAULT 'harness',
	start_time         TIMESTAMP(6)  NOT NULL,
	end_time           TIMESTAMP(6)  DEFAULT NULL,
	total_duration_ms  DOUBLE        DEFAULT NULL,
	span_count         INT           DEFAULT 0,
	created_at         TIMESTAMP(6)  DEFAULT NULL,
	PRIMARY KEY (id)
)
 distributed by duplicate(g1);


-- pd_ai_spans
CREATE TABLE IF NOT EXISTS `pd_ai_spans` (
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
	PRIMARY KEY (id)
)
 distributed by duplicate(g1);


-- pd_ai_ontology_instance
CREATE TABLE IF NOT EXISTS `pd_ai_ontology_instance` (
	id                 BIGINT        NOT NULL AUTO_INCREMENT,
	ontology_code      VARCHAR(255)  DEFAULT NULL,
	user_id            VARCHAR(255)  DEFAULT NULL,
	session_id         VARCHAR(255)  DEFAULT NULL,
	status             VARCHAR(255)  DEFAULT NULL,
	submitted_at       TIMESTAMP(6)  DEFAULT NULL,
	data_json          TEXT          DEFAULT NULL,
	PRIMARY KEY (id)
)
 distributed by duplicate(g1);


-- pd_ai_ontology_version
CREATE TABLE IF NOT EXISTS `pd_ai_ontology_version` (
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
)
 distributed by duplicate(g1);


-- pd_ai_ontology_version_log
CREATE TABLE IF NOT EXISTS `pd_ai_ontology_version_log` (
	id                 BIGINT        NOT NULL AUTO_INCREMENT,
	version_id         BIGINT        DEFAULT NULL,
	domain             VARCHAR(32)   NOT NULL DEFAULT 'version',
	trace_id           VARCHAR(128)  DEFAULT NULL,
	action             VARCHAR(32)   NOT NULL,
	operator           VARCHAR(64)   DEFAULT NULL,
	detail             TEXT          DEFAULT NULL,
	created_at         TIMESTAMP(6)  DEFAULT NULL,
	PRIMARY KEY (id)
)
 distributed by duplicate(g1);


-- pd_ai_swrl_rules
CREATE TABLE IF NOT EXISTS `pd_ai_swrl_rules` (
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
)
 distributed by duplicate(g1);


-- pd_ai_node_results
CREATE TABLE IF NOT EXISTS `pd_ai_node_results` (
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
)
 distributed by duplicate(g1);


-- pd_ai_ops_work_orders
CREATE TABLE IF NOT EXISTS `pd_ai_ops_work_orders` (
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
)
 distributed by duplicate(g1);


-- pd_ai_users
CREATE TABLE IF NOT EXISTS `pd_ai_users` (
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
)
 distributed by duplicate(g1);


-- dwd_prod_metric_daily
CREATE TABLE IF NOT EXISTS `dwd_prod_metric_daily` (
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
)
 distributed by duplicate(g1);


-- pd_ops_shelf_offerings
CREATE TABLE IF NOT EXISTS `pd_ops_shelf_offerings` (
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
)
 distributed by duplicate(g1);


-- pd_ai_product_subscriptions
CREATE TABLE IF NOT EXISTS `pd_ai_product_subscriptions` (
	id             BIGINT AUTO_INCREMENT,
	subscriber     VARCHAR(64)   NOT NULL,
	offering_id    VARCHAR(64)   NOT NULL,
	offering_name  VARCHAR(255)  DEFAULT NULL,
	status         VARCHAR(16)   NOT NULL DEFAULT 'active',
	created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (id),
	CONSTRAINT uk_psub UNIQUE (subscriber, offering_id)
)
 distributed by duplicate(g1);


-- pd_ai_change_alerts
CREATE TABLE IF NOT EXISTS `pd_ai_change_alerts` (
	id               BIGINT AUTO_INCREMENT,
	offering_id      VARCHAR(64)   NOT NULL,
	offering_name    VARCHAR(255)  DEFAULT NULL,
	change_type      VARCHAR(32)   NOT NULL,
	old_value        VARCHAR(255)  DEFAULT NULL,
	new_value        VARCHAR(255)  DEFAULT NULL,
	detected_version VARCHAR(64)   DEFAULT NULL,
	subscriber       VARCHAR(64)   DEFAULT NULL,
	read_flag        BOOLEAN       NOT NULL DEFAULT FALSE,
	created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (id)
)
 distributed by duplicate(g1);


-- pd_ai_api_saved_request
CREATE TABLE IF NOT EXISTS `pd_ai_api_saved_request` (
	id               BIGINT AUTO_INCREMENT,
	owner            VARCHAR(64)   DEFAULT NULL,
	collection_name  VARCHAR(100)  NOT NULL DEFAULT 'default',
	name             VARCHAR(200)  NOT NULL,
	method           VARCHAR(16)   NOT NULL DEFAULT 'GET',
	url              VARCHAR(2000) DEFAULT NULL,
	headers_json     TEXT          DEFAULT NULL,
	params_json      TEXT          DEFAULT NULL,
	body             TEXT          DEFAULT NULL,
	body_type        VARCHAR(16)   NOT NULL DEFAULT 'none',
	description      VARCHAR(500)  DEFAULT NULL,
	created_at       TIMESTAMP(6)  DEFAULT NULL,
	updated_at       TIMESTAMP(6)  DEFAULT NULL,
	PRIMARY KEY (id)
)
 distributed by duplicate(g1);


-- pd_ai_api_request_history
CREATE TABLE IF NOT EXISTS `pd_ai_api_request_history` (
	id              BIGINT AUTO_INCREMENT,
	owner           VARCHAR(64)   DEFAULT NULL,
	method          VARCHAR(16)   NOT NULL DEFAULT 'GET',
	url             VARCHAR(2000) DEFAULT NULL,
	headers_json    TEXT          DEFAULT NULL,
	params_json     TEXT          DEFAULT NULL,
	body            TEXT          DEFAULT NULL,
	body_type       VARCHAR(16)   NOT NULL DEFAULT 'none',
	status          INT           DEFAULT NULL,
	success         BOOLEAN       NOT NULL DEFAULT FALSE,
	duration_ms     BIGINT        DEFAULT NULL,
	response_body   TEXT          DEFAULT NULL,
	error_message   TEXT          DEFAULT NULL,
	created_at      TIMESTAMP(6)  DEFAULT NULL,
	PRIMARY KEY (id)
)
 distributed by duplicate(g1);


-- ------------------------------------------------------------
-- 绱㈠紩锛堥噸澶嶆墽琛屾姤 1061 Duplicate key name锛屽彲蹇界暐锛塡n-- ------------------------------------------------------------
ALTER TABLE `pd_ai_chat_sessions` ADD INDEX idx_cs_user_id (user_id);
ALTER TABLE `pd_ai_chat_sessions` ADD INDEX idx_cs_updated_at (updated_at);
ALTER TABLE `pd_ai_chat_messages` ADD INDEX idx_cm_session_id (session_id);
ALTER TABLE `pd_ai_chat_messages` ADD INDEX idx_cm_created_at (created_at);
ALTER TABLE `pd_ai_chat_message_metadata` ADD INDEX idx_cmm_meta_key (meta_key);
ALTER TABLE `pd_ai_mcp_tool_definitions` ADD INDEX idx_tool_code (tool_code);
ALTER TABLE `pd_ai_mcp_tool_definitions` ADD INDEX idx_tool_category (category);
ALTER TABLE `pd_ai_mcp_tool_definitions` ADD INDEX idx_tool_enabled (is_enabled);
ALTER TABLE `pd_ai_mcp_call_logs` ADD INDEX idx_cl_tool_name (tool_name);
ALTER TABLE `pd_ai_mcp_call_logs` ADD INDEX idx_cl_tool_category (tool_category);
ALTER TABLE `pd_ai_mcp_call_logs` ADD INDEX idx_tool_timestamp (tool_name, timestamp);
ALTER TABLE `pd_ai_mcp_call_logs` ADD INDEX idx_timestamp_desc (timestamp);
ALTER TABLE `pd_ai_mcp_tool_stats` ADD INDEX idx_ts_tool_name (tool_name);
ALTER TABLE `pd_ai_mcp_tool_stats` ADD INDEX idx_ts_stat_date (stat_date);
ALTER TABLE `pd_ai_llm_user_configs` ADD INDEX idx_llm_user_identifier (user_identifier);
ALTER TABLE `pd_ai_prompt_versions` ADD INDEX idx_pv_prompt_id (prompt_id);
ALTER TABLE `pd_ai_workflow_history` ADD INDEX idx_wh_workflow_id (workflow_id);
ALTER TABLE `pd_ai_workflow_history` ADD INDEX idx_wh_workflow_code (workflow_code);
ALTER TABLE `pd_ai_workflow_executions` ADD INDEX idx_we_workflow_id (workflow_id);
ALTER TABLE `pd_ai_workflow_executions` ADD INDEX idx_we_workflow_code (workflow_code);
ALTER TABLE `pd_ai_workflow_node_logs` ADD INDEX idx_fnl_exec (execution_id);
ALTER TABLE `pd_ai_workflow_node_logs` ADD INDEX idx_fnl_exec_node (execution_id, node_id, attempt);
ALTER TABLE `pd_ai_traces` ADD INDEX idx_trace_service_name (service_name);
ALTER TABLE `pd_ai_traces` ADD INDEX idx_trace_created_at (created_at);
ALTER TABLE `pd_ai_spans` ADD INDEX idx_span_trace_id (trace_id);
ALTER TABLE `pd_ai_spans` ADD INDEX idx_span_parent_id (parent_span_id);
ALTER TABLE `pd_ai_spans` ADD INDEX idx_span_component (component);
ALTER TABLE `pd_ai_spans` ADD INDEX idx_span_status (status);
ALTER TABLE `pd_ai_ontology_instance` ADD INDEX idx_oi_session_id (session_id);
ALTER TABLE `pd_ai_ontology_version` ADD INDEX idx_oav_asset (asset_type, asset_code);
ALTER TABLE `pd_ai_ontology_version` ADD INDEX idx_oav_status (status);
ALTER TABLE `pd_ai_ontology_version_log` ADD INDEX idx_ovl_version_id (version_id);
ALTER TABLE `pd_ai_ontology_version_log` ADD INDEX idx_ovl_domain (domain);
ALTER TABLE `pd_ai_ontology_version_log` ADD INDEX idx_ovl_trace_id (trace_id);
ALTER TABLE `pd_ai_ontology_version_log` ADD INDEX idx_ovl_action (action);
ALTER TABLE `pd_ai_ontology_version_log` ADD INDEX idx_ovl_created (created_at);
ALTER TABLE `pd_ai_swrl_rules` ADD INDEX idx_sr_module (module);
ALTER TABLE `pd_ai_node_results` ADD INDEX idx_nr_req_node (req_id, node_name, updated_at);
ALTER TABLE `pd_ai_node_results` ADD INDEX idx_nr_key (result_key, updated_at);
ALTER TABLE `pd_ai_ops_work_orders` ADD INDEX idx_owo_offering (offering_id);
ALTER TABLE `pd_ai_ops_work_orders` ADD INDEX idx_owo_status (status);
ALTER TABLE `pd_ai_ops_work_orders` ADD INDEX idx_owo_session (session_id);
ALTER TABLE `pd_ai_ops_work_orders` ADD INDEX idx_owo_created (created_at);
ALTER TABLE `dwd_prod_metric_daily` ADD INDEX idx_pmd_offering_date (offering_id, stat_date);
ALTER TABLE `dwd_prod_metric_daily` ADD INDEX idx_pmd_date (stat_date);
ALTER TABLE `dwd_prod_metric_daily` ADD INDEX idx_pmd_region (region_id, stat_date);
ALTER TABLE `dwd_prod_metric_daily` ADD INDEX idx_pmd_channel (channel_id, stat_date);
ALTER TABLE `pd_ops_shelf_offerings` ADD INDEX idx_osf_state (state);
ALTER TABLE `pd_ops_shelf_offerings` ADD INDEX idx_osf_category_code (category_code);
ALTER TABLE `pd_ops_shelf_offerings` ADD INDEX idx_osf_category (category);
ALTER TABLE `pd_ai_product_subscriptions` ADD INDEX idx_psub_offering (offering_id, status);
ALTER TABLE `pd_ai_change_alerts` ADD INDEX idx_alert_sub (subscriber, read_flag, created_at);
ALTER TABLE `pd_ai_change_alerts` ADD INDEX idx_alert_offering (offering_id, created_at);
ALTER TABLE `pd_ai_api_saved_request` ADD INDEX idx_asr_owner (owner);
ALTER TABLE `pd_ai_api_saved_request` ADD INDEX idx_asr_collection (collection_name);
ALTER TABLE `pd_ai_api_request_history` ADD INDEX idx_arh_owner (owner, created_at);
ALTER TABLE `pd_ai_api_request_history` ADD INDEX idx_arh_created (created_at);
