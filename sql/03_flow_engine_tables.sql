-- ============================================================
-- 03_flow_engine_tables.sql
-- 固定流程引擎（P2）表结构变更：
--   1) pd_ai_workflow_executions 扩展列（运行上下文/当前节点/恢复令牌/乐观锁/版本锁定）
--   2) pd_ai_workflow_node_logs 节点级执行记录（新增，审计与断点恢复依据）
-- 对应设计：《固定流程引擎设计文档》§6
-- 兼容性（实测修正）：
--   - MySQL 8.0 不支持 ALTER TABLE ... DROP COLUMN IF EXISTS（那是 MariaDB 语法），
--     重放前请先手工确认列是否存在，或使用下方存储过程幂等方案；
--   - 去除 Doris 专属 DISTRIBUTED BY 子句，纯 MySQL 8.0 可执行；
--   - WorkflowNodeLog 实体含 created_at/updated_at（FieldFill），建表必须带上，
--     否则 MyBatis-Plus insert 报 Unknown column（曾导致引擎接口全量 500）。
-- ============================================================

-- 1. 执行实例表扩展（已有表加列）
--    幂等：借助 information_schema 动态拼接，列已存在则跳过
DROP PROCEDURE IF EXISTS `p_add_engine_columns`;
DELIMITER $$
CREATE PROCEDURE `p_add_engine_columns`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pd_ai_workflow_executions'
          AND COLUMN_NAME = 'context_data'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions`
            ADD COLUMN `context_data` TEXT NULL COMMENT '运行上下文（各节点输出合并，恢复执行的数据源）' AFTER `execution_logs`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pd_ai_workflow_executions'
          AND COLUMN_NAME = 'current_node_id'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions`
            ADD COLUMN `current_node_id` VARCHAR(64) NULL COMMENT '当前推进到的节点' AFTER `context_data`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pd_ai_workflow_executions'
          AND COLUMN_NAME = 'resume_token'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions`
            ADD COLUMN `resume_token` VARCHAR(64) NULL COMMENT '人工节点恢复令牌（一次有效）' AFTER `current_node_id`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pd_ai_workflow_executions'
          AND COLUMN_NAME = 'status_version'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions`
            ADD COLUMN `status_version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER `resume_token`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'pd_ai_workflow_executions'
          AND COLUMN_NAME = 'workflow_version'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions`
            ADD COLUMN `workflow_version` INT NULL COMMENT '执行时锁定的流程定义版本（回滚安全）' AFTER `status_version`;
    END IF;
END$$
DELIMITER ;

CALL `p_add_engine_columns`();
DROP PROCEDURE IF EXISTS `p_add_engine_columns`;

-- 2. 节点级执行记录（新增；DROP+CREATE 保证可重放）
DROP TABLE IF EXISTS `pd_ai_workflow_node_logs`;
CREATE TABLE `pd_ai_workflow_node_logs` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `execution_id`  VARCHAR(100) NOT NULL COMMENT '执行实例 ID',
    `node_id`       VARCHAR(64)  NOT NULL COMMENT '节点 ID',
    `node_type`     VARCHAR(32)  NOT NULL COMMENT '节点类型',
    `status`        VARCHAR(16)  NOT NULL COMMENT 'running/completed/skipped/failed',
    `attempt`       INT          NOT NULL DEFAULT 1 COMMENT '第几次重试',
    `input_data`    TEXT         NULL COMMENT '节点实际入参（变量解析后）',
    `output_data`   TEXT         NULL COMMENT '节点输出',
    `error_message` TEXT         NULL,
    `branch_taken`  VARCHAR(128) NULL COMMENT 'condition 命中的分支 id 及表达式原文',
    `started_at`    DATETIME(6)  NOT NULL,
    `ended_at`      DATETIME(6)  NULL,
    `duration_ms`   BIGINT       NULL,
    `created_at`    DATETIME(6)  NULL DEFAULT NULL COMMENT '创建时间（实体 FieldFill.INSERT）',
    `updated_at`    DATETIME(6)  NULL DEFAULT NULL COMMENT '更新时间（实体 FieldFill.INSERT_UPDATE）',
    PRIMARY KEY (`id`),
    KEY `idx_fnl_exec` (`execution_id`),
    KEY `idx_fnl_exec_node` (`execution_id`, `node_id`, `attempt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程节点级执行记录（审计与断点恢复依据）';
