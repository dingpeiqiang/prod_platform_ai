-- ============================================================
-- Prod Platform AI - 全量模型 DDL（MySQL 8.0+ / InnoDB / utf8mb4）
-- 来源：backend-app JPA Entity（权威），对齐 docs/数据库设计文档.md 并补全遗漏表
-- 用法：
--   mysql -uprodplatformai -p prodplatformai < sql/01_full_schema_ddl.sql
-- 建议：生产环境将 spring.jpa.hibernate.ddl-auto 设为 validate 或 none
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `prodplatformai`;

-- ------------------------------------------------------------
-- 1. 聊天系统
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `pd_ai_chat_message_metadata`;
DROP TABLE IF EXISTS `pd_ai_chat_messages`;
DROP TABLE IF EXISTS `pd_ai_chat_sessions`;

CREATE TABLE `pd_ai_chat_sessions` (
    `id`                 INT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `session_id`         VARCHAR(64)  NOT NULL COMMENT '会话唯一标识（UUID）',
    `user_id`            VARCHAR(100)          DEFAULT NULL COMMENT '用户ID（可空，支持匿名）',
    `title`              VARCHAR(200)          DEFAULT NULL COMMENT '会话标题',
    `context_tags`       TEXT                  DEFAULT NULL COMMENT '会话标签 JSON 数组',
    `session_metadata`   TEXT                  DEFAULT NULL COMMENT '会话扩展信息 JSON 对象',
    `status`             VARCHAR(20)           DEFAULT 'active' COMMENT 'active / archived',
    `created_at`         DATETIME(6)           DEFAULT NULL COMMENT '创建时间',
    `updated_at`         DATETIME(6)           DEFAULT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_cs_session_id` (`session_id`),
    KEY `idx_cs_user_id` (`user_id`),
    KEY `idx_cs_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

CREATE TABLE `pd_ai_chat_messages` (
    `id`                 INT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `message_id`         VARCHAR(64)  NOT NULL COMMENT '消息唯一标识（UUID）',
    `session_id`         VARCHAR(64)  NOT NULL COMMENT '所属会话ID',
    `role`               VARCHAR(20)  NOT NULL COMMENT 'user / assistant / system',
    `content`            TEXT         NOT NULL COMMENT '消息正文',
    `content_type`       VARCHAR(20)           DEFAULT 'text' COMMENT 'text / markdown / json / form / thinking',
    `parent_id`          VARCHAR(64)           DEFAULT NULL COMMENT '父消息ID',
    `sort_order`         INT          NOT NULL DEFAULT 0 COMMENT '同会话内排序',
    `created_at`         DATETIME(6)           DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_cm_message_id` (`message_id`),
    KEY `idx_cm_session_id` (`session_id`),
    KEY `idx_cm_created_at` (`created_at`),
    CONSTRAINT `fk_cm_session`
        FOREIGN KEY (`session_id`) REFERENCES `pd_ai_chat_sessions` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

CREATE TABLE `pd_ai_chat_message_metadata` (
    `id`                 INT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `message_id`         VARCHAR(64)  NOT NULL COMMENT '所属消息ID',
    `meta_key`           VARCHAR(100) NOT NULL COMMENT '扩展字段名',
    `value`              TEXT                  DEFAULT NULL COMMENT '扩展字段值',
    `created_at`         DATETIME(6)           DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_message_key` (`message_id`, `meta_key`),
    KEY `idx_cmm_message_id` (`message_id`),
    KEY `idx_cmm_meta_key` (`meta_key`),
    CONSTRAINT `fk_cmm_message`
        FOREIGN KEY (`message_id`) REFERENCES `pd_ai_chat_messages` (`message_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息 KV 扩展表';

-- ------------------------------------------------------------
-- 2. MCP 工具管理
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `pd_ai_mcp_tool_stats`;
DROP TABLE IF EXISTS `pd_ai_mcp_call_logs`;
DROP TABLE IF EXISTS `pd_ai_mcp_tool_definitions`;

CREATE TABLE `pd_ai_mcp_tool_definitions` (
    `id`                 INT          NOT NULL AUTO_INCREMENT,
    `tool_name`          VARCHAR(100) NOT NULL COMMENT '工具名称（唯一）',
    `tool_code`          VARCHAR(100)          DEFAULT NULL COMMENT '工具编码（唯一）',
    `description`        TEXT                  DEFAULT NULL,
    `category`           VARCHAR(50)           DEFAULT NULL,
    `is_enabled`         TINYINT(1)   NOT NULL DEFAULT 1,
    `is_public`          TINYINT(1)   NOT NULL DEFAULT 1,
    `input_schema`       TEXT                  DEFAULT NULL COMMENT '输入 Schema JSON',
    `output_schema`      TEXT                  DEFAULT NULL COMMENT '输出 Schema JSON',
    `tool_type`          VARCHAR(20)           DEFAULT 'url',
    `protocol`           VARCHAR(10)           DEFAULT 'http',
    `request_method`     VARCHAR(16)           DEFAULT 'POST',
    `url`                VARCHAR(500)          DEFAULT NULL,
    `auth_type`          VARCHAR(20)           DEFAULT 'none',
    `auth_info`          TEXT                  DEFAULT NULL,
    `need_summary`       TINYINT(1)   NOT NULL DEFAULT 0,
    `prompt`             TEXT                  DEFAULT NULL,
    `config`             TEXT                  DEFAULT NULL COMMENT '工具配置 JSON',
    `extra_metadata`     TEXT                  DEFAULT NULL COMMENT '扩展元数据 JSON',
    `total_calls`        INT          NOT NULL DEFAULT 0,
    `last_called_at`     DATETIME(6)           DEFAULT NULL,
    `created_by`         VARCHAR(100)          DEFAULT NULL,
    `updated_by`         VARCHAR(100)          DEFAULT NULL,
    `created_at`         DATETIME(6)           DEFAULT NULL,
    `updated_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_tool_name` (`tool_name`),
    UNIQUE KEY `uk_tool_code` (`tool_code`),
    KEY `idx_tool_code` (`tool_code`),
    KEY `idx_tool_category` (`category`),
    KEY `idx_tool_enabled` (`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具定义表';

CREATE TABLE `pd_ai_mcp_call_logs` (
    `id`                 INT          NOT NULL AUTO_INCREMENT,
    `tool_name`          VARCHAR(100) NOT NULL,
    `tool_category`      VARCHAR(50)           DEFAULT NULL,
    `success`            TINYINT(1)   NOT NULL DEFAULT 0,
    `execution_time_ms`  DOUBLE                DEFAULT NULL,
    `error_message`      TEXT                  DEFAULT NULL,
    `timestamp`          DATETIME(6)           DEFAULT NULL,
    `request_args`       TEXT                  DEFAULT NULL,
    `response_data`      TEXT                  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_cl_tool_name` (`tool_name`),
    KEY `idx_cl_tool_category` (`tool_category`),
    KEY `idx_tool_timestamp` (`tool_name`, `timestamp`),
    KEY `idx_timestamp_desc` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具调用日志表';

CREATE TABLE `pd_ai_mcp_tool_stats` (
    `id`                      INT          NOT NULL AUTO_INCREMENT,
    `tool_name`               VARCHAR(100) NOT NULL,
    `stat_date`               VARCHAR(20)  NOT NULL COMMENT 'YYYY-MM-DD',
    `stat_hour`               INT                   DEFAULT NULL COMMENT '0-23，NULL=日统计',
    `total_calls`             INT          NOT NULL DEFAULT 0,
    `success_calls`           INT          NOT NULL DEFAULT 0,
    `failed_calls`            INT          NOT NULL DEFAULT 0,
    `total_response_time_ms`  DOUBLE       NOT NULL DEFAULT 0,
    `avg_response_time_ms`    DOUBLE       NOT NULL DEFAULT 0,
    `created_at`              DATETIME(6)           DEFAULT NULL,
    `updated_at`              DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_tool_date_hour` (`tool_name`, `stat_date`, `stat_hour`),
    KEY `idx_ts_tool_name` (`tool_name`),
    KEY `idx_ts_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具聚合统计表';

-- ------------------------------------------------------------
-- 3. LLM 用户配置
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `pd_ai_llm_user_configs`;

CREATE TABLE `pd_ai_llm_user_configs` (
    `id`                 INT          NOT NULL AUTO_INCREMENT,
    `user_identifier`    VARCHAR(100) NOT NULL,
    `provider`           VARCHAR(50)  NOT NULL DEFAULT 'custom' COMMENT 'openai/azure/custom/local',
    `model`              VARCHAR(100) NOT NULL,
    `api_key`            TEXT                  DEFAULT NULL,
    `base_url`           TEXT                  DEFAULT NULL,
    `auth_type`          VARCHAR(20)  NOT NULL DEFAULT 'bearer',
    `auth_header`        VARCHAR(50)           DEFAULT NULL,
    `api_format`         VARCHAR(50)  NOT NULL DEFAULT 'openai',
    `is_full_url`        TINYINT(1)   NOT NULL DEFAULT 0,
    `temperature`        DOUBLE       NOT NULL DEFAULT 0.3,
    `max_tokens`         INT          NOT NULL DEFAULT 2048,
    `thinking`           TINYINT(1)   NOT NULL DEFAULT 0,
    `stream_enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否流式输出，0=非流式',
    `max_input_tokens`   INT                   DEFAULT 180000,
    `is_active`          TINYINT(1)   NOT NULL DEFAULT 1,
    `config_name`        VARCHAR(100)          DEFAULT NULL,
    `created_at`         DATETIME(6)           DEFAULT NULL,
    `updated_at`         DATETIME(6)           DEFAULT NULL,
    `last_used_at`       DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_llm_user_identifier` (`user_identifier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户LLM配置表';

-- ------------------------------------------------------------
-- 4. 提示词管理
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `pd_ai_prompt_versions`;
DROP TABLE IF EXISTS `pd_ai_prompts`;
DROP TABLE IF EXISTS `pd_ai_prompt_templates`;

CREATE TABLE `pd_ai_prompts` (
    `id`                 INT          NOT NULL AUTO_INCREMENT,
    `code`               VARCHAR(100) NOT NULL,
    `name`               VARCHAR(200) NOT NULL,
    `description`        TEXT                  DEFAULT NULL,
    `category`           VARCHAR(50)           DEFAULT 'general',
    `content`            TEXT         NOT NULL,
    `variables`          TEXT                  DEFAULT NULL COMMENT '变量定义 JSON 数组',
    `tools`              TEXT                  DEFAULT NULL COMMENT '可用工具 JSON 数组',
    `is_template`        TINYINT(1)            DEFAULT 0,
    `version`            INT                   DEFAULT 1,
    `is_active`          TINYINT(1)            DEFAULT 1,
    `created_by`         VARCHAR(100)          DEFAULT NULL,
    `updated_by`         VARCHAR(100)          DEFAULT NULL,
    `created_at`         DATETIME(6)           DEFAULT NULL,
    `updated_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_prompt_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词主表';

CREATE TABLE `pd_ai_prompt_versions` (
    `id`                 INT          NOT NULL AUTO_INCREMENT,
    `prompt_id`          INT          NOT NULL,
    `version`            INT          NOT NULL,
    `content`            TEXT         NOT NULL,
    `variables`          TEXT                  DEFAULT NULL,
    `tools`              TEXT                  DEFAULT NULL,
    `change_note`        TEXT                  DEFAULT NULL,
    `created_by`         VARCHAR(100)          DEFAULT NULL,
    `created_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_pv_prompt_id` (`prompt_id`),
    CONSTRAINT `fk_pv_prompt`
        FOREIGN KEY (`prompt_id`) REFERENCES `pd_ai_prompts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词版本历史';

CREATE TABLE `pd_ai_prompt_templates` (
    `id`                 INT          NOT NULL AUTO_INCREMENT,
    `code`               VARCHAR(100) NOT NULL,
    `name`               VARCHAR(200) NOT NULL,
    `description`        TEXT                  DEFAULT NULL,
    `category`           VARCHAR(50)           DEFAULT 'general',
    `content`            TEXT         NOT NULL,
    `variables`          TEXT                  DEFAULT NULL,
    `tools`              TEXT                  DEFAULT NULL,
    `tags`               TEXT                  DEFAULT NULL,
    `is_builtin`         TINYINT(1)            DEFAULT 0,
    `is_active`          TINYINT(1)            DEFAULT 1,
    `created_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_pt_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词预设模板库';

-- ------------------------------------------------------------
-- 5. 工作流
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `pd_ai_workflow_executions`;
DROP TABLE IF EXISTS `pd_ai_workflow_history`;
DROP TABLE IF EXISTS `pd_ai_workflows`;

CREATE TABLE `pd_ai_workflows` (
    `id`                     INT          NOT NULL AUTO_INCREMENT,
    `workflow_code`          VARCHAR(100) NOT NULL,
    `workflow_name`          VARCHAR(200) NOT NULL,
    `description`            TEXT                  DEFAULT NULL,
    `category`               VARCHAR(50)           DEFAULT 'general',
    `tags`                   TEXT         NOT NULL COMMENT '标签 JSON 数组',
    `priority`               INT                   DEFAULT 10,
    `is_active`              TINYINT(1)            DEFAULT 1,
    `is_in_library`          TINYINT(1)            DEFAULT 0,
    `workflow_data`          TEXT         NOT NULL COMMENT '完整工作流配置 JSON',
    `version`                INT                   DEFAULT 1,
    `execution_count`        INT                   DEFAULT 0,
    `last_execution_at`      DATETIME(6)           DEFAULT NULL,
    `last_execution_status`  VARCHAR(20)           DEFAULT NULL,
    `created_by`             VARCHAR(100)          DEFAULT NULL,
    `updated_by`             VARCHAR(100)          DEFAULT NULL,
    `created_at`             DATETIME(6)           DEFAULT NULL,
    `updated_at`             DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_wf_code` (`workflow_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流主表';

CREATE TABLE `pd_ai_workflow_history` (
    `id`                 INT          NOT NULL AUTO_INCREMENT,
    `workflow_id`        INT          NOT NULL,
    `workflow_code`      VARCHAR(100) NOT NULL,
    `version`            INT          NOT NULL,
    `workflow_name`      VARCHAR(200) NOT NULL,
    `description`        TEXT                  DEFAULT NULL,
    `workflow_data`      TEXT         NOT NULL,
    `category`           VARCHAR(50)           DEFAULT NULL,
    `tags`               TEXT         NOT NULL,
    `priority`           INT                   DEFAULT NULL,
    `is_active`          TINYINT(1)            DEFAULT NULL,
    `change_note`        TEXT                  DEFAULT NULL,
    `created_by`         VARCHAR(100)          DEFAULT NULL,
    `created_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_wh_workflow_id` (`workflow_id`),
    KEY `idx_wh_workflow_code` (`workflow_code`),
    CONSTRAINT `fk_wh_workflow`
        FOREIGN KEY (`workflow_id`) REFERENCES `pd_ai_workflows` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流版本历史';

CREATE TABLE `pd_ai_workflow_executions` (
    `id`                 INT          NOT NULL AUTO_INCREMENT,
    `workflow_id`        INT          NOT NULL,
    `workflow_code`      VARCHAR(100) NOT NULL,
    `execution_id`       VARCHAR(100) NOT NULL,
    `status`             VARCHAR(20)  NOT NULL DEFAULT 'pending',
    `start_time`         DATETIME(6)           DEFAULT NULL,
    `end_time`           DATETIME(6)           DEFAULT NULL,
    `duration_seconds`   INT                   DEFAULT NULL,
    `input_data`         TEXT         NOT NULL COMMENT '输入 JSON',
    `output_data`        TEXT                  DEFAULT NULL COMMENT '输出 JSON',
    `error_message`      TEXT                  DEFAULT NULL,
    `execution_logs`     TEXT         NOT NULL COMMENT '执行日志 JSON 数组',
    `triggered_by`       VARCHAR(100)          DEFAULT NULL,
    `trigger_type`       VARCHAR(20)           DEFAULT 'manual',
    `notes`              TEXT                  DEFAULT NULL,
    `created_at`         DATETIME(6)           DEFAULT NULL,
    `updated_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_we_execution_id` (`execution_id`),
    KEY `idx_we_workflow_id` (`workflow_id`),
    KEY `idx_we_workflow_code` (`workflow_code`),
    CONSTRAINT `fk_we_workflow`
        FOREIGN KEY (`workflow_id`) REFERENCES `pd_ai_workflows` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行记录';

-- ------------------------------------------------------------
-- 6. 链路追踪
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `pd_ai_spans`;
DROP TABLE IF EXISTS `pd_ai_traces`;

CREATE TABLE `pd_ai_traces` (
    `id`                 VARCHAR(36)  NOT NULL,
    `service_name`       VARCHAR(100)          DEFAULT 'harness',
    `start_time`         DATETIME(6)  NOT NULL,
    `end_time`           DATETIME(6)           DEFAULT NULL,
    `total_duration_ms`  DOUBLE                DEFAULT NULL,
    `span_count`         INT                   DEFAULT 0,
    `created_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_trace_service_name` (`service_name`),
    KEY `idx_trace_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='追踪记录';

CREATE TABLE `pd_ai_spans` (
    `id`                 VARCHAR(36)  NOT NULL,
    `trace_id`           VARCHAR(36)  NOT NULL,
    `parent_span_id`     VARCHAR(36)           DEFAULT NULL,
    `name`               VARCHAR(200) NOT NULL,
    `component`          VARCHAR(100)          DEFAULT 'harness',
    `start_time`         DATETIME(6)  NOT NULL,
    `end_time`           DATETIME(6)           DEFAULT NULL,
    `duration_ms`        DOUBLE                DEFAULT NULL,
    `status`             VARCHAR(20)           DEFAULT 'ok' COMMENT 'ok / error / timeout',
    `tags`               TEXT                  DEFAULT NULL COMMENT '标签 JSON',
    `logs`               TEXT                  DEFAULT NULL COMMENT '日志 JSON 数组',
    `created_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_span_trace_id` (`trace_id`),
    KEY `idx_span_parent_id` (`parent_span_id`),
    KEY `idx_span_component` (`component`),
    KEY `idx_span_status` (`status`),
    CONSTRAINT `fk_span_trace`
        FOREIGN KEY (`trace_id`) REFERENCES `pd_ai_traces` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='追踪 Span';

-- ------------------------------------------------------------
-- 7. 本体实例（配置填报）
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `pd_ai_ontology_instance_data`;
DROP TABLE IF EXISTS `pd_ai_ontology_version_log`;
DROP TABLE IF EXISTS `pd_ai_ontology_version`;
DROP TABLE IF EXISTS `pd_ai_ontology_instance`;

-- data_json：KV 数据单 JSON TEXT 列存储（对齐 OntologyInstance 实体，
-- 原 pd_ai_ontology_instance_data 子表已由实体层退役，仅存量环境迁移用）
CREATE TABLE `pd_ai_ontology_instance` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `ontology_code`      VARCHAR(255)          DEFAULT NULL,
    `user_id`            VARCHAR(255)          DEFAULT NULL,
    `session_id`         VARCHAR(255)          DEFAULT NULL,
    `status`             VARCHAR(255)          DEFAULT NULL,
    `submitted_at`       DATETIME(6)           DEFAULT NULL,
    `data_json`          TEXT                  DEFAULT NULL COMMENT 'KV 数据 JSON 序列化（原 instance_data 子表）',
    PRIMARY KEY (`id`),
    KEY `idx_oi_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体实例主表';

-- 存量环境迁移参考（实体已改单列 data_json，本表仅保留给旧库数据搬迁）：
--   ALTER TABLE pd_ai_ontology_instance ADD COLUMN data_json TEXT NULL AFTER submitted_at;
--   UPDATE pd_ai_ontology_instance i SET i.data_json = (
--       SELECT JSON_OBJECTAGG(d.data_key, d.data) FROM pd_ai_ontology_instance_data d
--       WHERE d.ontology_instance_id = i.id)
--   WHERE EXISTS (SELECT 1 FROM pd_ai_ontology_instance_data d2 WHERE d2.ontology_instance_id = i.id);

-- P1-5 版本库表 A：本体资产版本主表（payload 为回滚唯一事实源）
CREATE TABLE `pd_ai_ontology_version` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `asset_type`         VARCHAR(32)  NOT NULL COMMENT 'template / message_projection / ops_rules / ttl / abox_snapshot',
    `asset_code`         VARCHAR(128) NOT NULL COMMENT '资产编码：template_id / 文件名等',
    `version`            VARCHAR(64)  NOT NULL COMMENT '语义化版本（semver）',
    `status`             VARCHAR(32)  NOT NULL DEFAULT 'draft' COMMENT 'draft / review / published / deprecated',
    `author`             VARCHAR(64)           DEFAULT NULL,
    `summary`            VARCHAR(512)          DEFAULT NULL,
    `payload`            TEXT                  COMMENT '源码全文（回滚唯一事实源）',
    `created_at`         DATETIME(6)           DEFAULT NULL,
    `published_at`       DATETIME(6)           DEFAULT NULL,
    `deprecated_at`      DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oav_type_code_version` (`asset_type`, `asset_code`, `version`),
    KEY `idx_oav_asset` (`asset_type`, `asset_code`),
    KEY `idx_oav_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体资产版本主表';

-- P1-5 版本库表 B：动作日志（由 pd_ai_ontology_instance_history 接线改造；P3-5 ① 泛化审计一张表）
CREATE TABLE `pd_ai_ontology_version_log` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `version_id`         BIGINT                DEFAULT NULL COMMENT '外键 pd_ai_ontology_version.id（非版本键控审计可空）',
    `domain`             VARCHAR(32)  NOT NULL DEFAULT 'version' COMMENT '审计域：version / risk / config / batch',
    `trace_id`           VARCHAR(128)          DEFAULT NULL COMMENT 'config 链路 trace_id',
    `action`             VARCHAR(32)  NOT NULL COMMENT 'publish / rollback / deprecate / reload / override / config_step / batch_audit',
    `operator`           VARCHAR(64)           DEFAULT NULL,
    `detail`             TEXT                  COMMENT '动作明细 JSON',
    `created_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ovl_version_id` (`version_id`),
    KEY `idx_ovl_domain` (`domain`),
    KEY `idx_ovl_trace_id` (`trace_id`),
    KEY `idx_ovl_action` (`action`),
    KEY `idx_ovl_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体资产版本动作日志（审计一张表）';

-- ------------------------------------------------------------
-- 8. SWRL / 条件 DSL 规则（遗留营销路径）
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `pd_ai_swrl_rules`;

CREATE TABLE `pd_ai_swrl_rules` (
    `id`                 INT          NOT NULL AUTO_INCREMENT,
    `rule_id`            VARCHAR(64)  NOT NULL,
    `rule_name`          VARCHAR(200) NOT NULL,
    `module`             VARCHAR(100)          DEFAULT NULL,
    `description`        TEXT                  DEFAULT NULL,
    `condition_expr`     TEXT                  DEFAULT NULL,
    `action_expr`        TEXT                  DEFAULT NULL,
    `enabled`            TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`         DATETIME(6)           DEFAULT NULL,
    `updated_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_sr_rule_id` (`rule_id`),
    KEY `idx_sr_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='条件DSL规则表（非OWL SWRL）';

-- ------------------------------------------------------------
-- 9. 产商品运营工单
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `pd_ai_ops_work_orders`;

CREATE TABLE `pd_ai_ops_work_orders` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `work_order_id`      VARCHAR(64)  NOT NULL,
    `title`              VARCHAR(255) NOT NULL,
    `offering_id`        VARCHAR(64)           DEFAULT NULL,
    `offering_name`      VARCHAR(255)          DEFAULT NULL,
    `summary`            TEXT                  DEFAULT NULL,
    `actions`            TEXT                  DEFAULT NULL COMMENT '处置动作 JSON 数组',
    `status`             VARCHAR(32)  NOT NULL DEFAULT 'open',
    `source`             VARCHAR(64)           DEFAULT NULL,
    `session_id`         VARCHAR(64)           DEFAULT NULL COMMENT '来源会话 ID（研发助手会话内工单聚合）',
    `hypo_mode`          VARCHAR(32)           DEFAULT NULL,
    `payload`            TEXT                  DEFAULT NULL COMMENT '扩展载荷 JSON',
    `created_at`         DATETIME(6)           DEFAULT NULL,
    `updated_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owo_work_order_id` (`work_order_id`),
    KEY `idx_owo_offering` (`offering_id`),
    KEY `idx_owo_status` (`status`),
    KEY `idx_owo_session` (`session_id`),
    KEY `idx_owo_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产商品运营处置工单';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 表清单（共 22 张）
-- pd_ai_chat_sessions, pd_ai_chat_messages, pd_ai_chat_message_metadata
-- pd_ai_mcp_tool_definitions, pd_ai_mcp_call_logs, pd_ai_mcp_tool_stats
-- pd_ai_llm_user_configs
-- pd_ai_prompts, pd_ai_prompt_versions, pd_ai_prompt_templates
-- pd_ai_workflows, pd_ai_workflow_history, pd_ai_workflow_executions
-- pd_ai_traces, pd_ai_spans
-- pd_ai_ontology_instance（含 data_json 单列，原 instance_data 子表已并入）
-- pd_ai_ontology_version, pd_ai_ontology_version_log
-- pd_ai_swrl_rules
-- pd_ai_ops_work_orders
-- ============================================================
