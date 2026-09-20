-- ============================================================
-- Prod Platform AI - 扩展表结构（纯 MySQL 8.0）
-- 覆盖 01_full_schema_ddl.sql 之外的增量对象：
--   1) 运营指标宽表   dwd_prod_metric_daily（指标域 P0）
--   2) ABox 在架事实表 pd_ops_shelf_offerings（abox-source=jdbc 数据源）
--   3) 商品变更订阅   pd_ai_product_subscriptions / pd_ai_change_alerts（C3）
--   4) 固定流程引擎   pd_ai_workflow_executions 扩展列 + pd_ai_workflow_node_logs
-- 执行顺序：先 01_full_schema_ddl.sql，再本脚本
-- 幂等性：表均用 CREATE TABLE IF NOT EXISTS；流程引擎列添加用 information_schema 判存
-- 用法：
--   mysql -uprodplatformai -p prodplatformai < 03_ext_schema.sql
-- ============================================================

SET NAMES utf8mb4;
USE `prodplatformai`;

-- ------------------------------------------------------------
-- 1. 运营指标宽表（指标域 P0）
-- 指标口径单源：backend-app/src/main/resources/ontology/metrics_registry.json
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dwd_prod_metric_daily` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `stat_date`        DATE          NOT NULL COMMENT '统计日期（日粒度）',
    `offering_id`      VARCHAR(64)   NOT NULL COMMENT '产商品编码',
    `region_id`        VARCHAR(32)   NOT NULL DEFAULT 'ALL' COMMENT '地区维度，ALL=全量',
    `channel_id`       VARCHAR(32)   NOT NULL DEFAULT 'ALL' COMMENT '渠道维度，ALL=全量',
    `customer_segment` VARCHAR(32)   NOT NULL DEFAULT 'ALL' COMMENT '客群维度，ALL=全量',
    `revenue`          DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '收入（元）',
    `order_cnt`        INT           NOT NULL DEFAULT 0 COMMENT '订购量（笔）',
    `order_cnt_addon`  INT                    DEFAULT NULL COMMENT '附加品订购量（attach_rate 派生用）',
    `order_cnt_main`   INT                    DEFAULT NULL COMMENT '主资费订购量（attach_rate 派生用）',
    `new_users`        INT           NOT NULL DEFAULT 0 COMMENT '新增用户（户）',
    `churn_users`      INT           NOT NULL DEFAULT 0 COMMENT '流失用户（户）',
    `active_users`     INT           NOT NULL DEFAULT 0 COMMENT '活跃用户（户，日快照）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pmd_natural` (`stat_date`, `offering_id`, `region_id`, `channel_id`, `customer_segment`),
    KEY `idx_pmd_offering_date` (`offering_id`, `stat_date`),
    KEY `idx_pmd_date` (`stat_date`),
    KEY `idx_pmd_region` (`region_id`, `stat_date`),
    KEY `idx_pmd_channel` (`channel_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='产商品运营指标日宽表';

-- ------------------------------------------------------------
-- 2. ABox 在架商品事实表（业务系统只读视图同构）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pd_ops_shelf_offerings` (
    `offering_id`      VARCHAR(64)   NOT NULL COMMENT '产商品编码（offeringId，硬校验非空）',
    `offering_name`    VARCHAR(255)  NOT NULL COMMENT '产商品名称（offeringName）',
    `category_code`    VARCHAR(64)            DEFAULT NULL COMMENT '品类编码（categoryCode，如 familyBasePrc）',
    `category_name`    VARCHAR(128)           DEFAULT NULL COMMENT '品类名称（categoryName，如 家庭基础套餐）',
    `product_line`     VARCHAR(64)            DEFAULT NULL COMMENT '产品线（productLine：家庭/宽带/个人）',
    `offering_type`    VARCHAR(32)   NOT NULL DEFAULT 'addon' COMMENT '商品类型（offeringType：main_pkg/addon/fusion）',
    `state`            VARCHAR(32)   NOT NULL COMMENT '状态（state：on_shelf/on_sale）',
    `monthly_fee`      DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '月费（monthlyFee，元）',
    `fixed_fee_amount` DECIMAL(18,2)          DEFAULT NULL COMMENT '一次性/固定费（fixedFeeAmount，元）',
    `sales_cnt_30d`    INT           NOT NULL DEFAULT 0 COMMENT '近30天订购量（salesCnt30d）',
    `revenue_30d`      DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '近30天收入（revenue30d，元）',
    `shelf_days`       INT           NOT NULL DEFAULT 0 COMMENT '在架天数（shelfDays）',
    `message_root_key` VARCHAR(64)            DEFAULT NULL COMMENT '报文根键（messageRootKey，模板品类码）',
    `category`         VARCHAR(32)   NOT NULL DEFAULT 'normal' COMMENT '风险标记（category：normal/zero_fee/low_eff/whitelist/abnormal_discount/threshold_demo）',
    PRIMARY KEY (`offering_id`),
    KEY `idx_osf_state` (`state`),
    KEY `idx_osf_category_code` (`category_code`),
    KEY `idx_osf_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='ABox 在架商品事实表（业务系统只读视图同构，abox-source=jdbc 数据源）';

-- ------------------------------------------------------------
-- 3. 商品变更订阅与提醒（C3）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pd_ai_product_subscriptions` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `subscriber`    VARCHAR(64)   NOT NULL COMMENT '订阅人登录名（服务端登录态）',
    `offering_id`   VARCHAR(64)   NOT NULL COMMENT '订阅商品编码',
    `offering_name` VARCHAR(255)           DEFAULT NULL COMMENT '订阅时商品名称快照',
    `status`        VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT '状态：active/cancelled',
    `created_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_psub` (`subscriber`, `offering_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商品变更订阅登记（C3）';

CREATE TABLE IF NOT EXISTS `pd_ai_change_alerts` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `offering_id`      VARCHAR(64)   NOT NULL COMMENT '商品编码',
    `offering_name`    VARCHAR(255)           DEFAULT NULL COMMENT '商品名称',
    `change_type`      VARCHAR(32)   NOT NULL COMMENT '变更类型：fee_change/state_change',
    `old_value`        VARCHAR(255)           DEFAULT NULL COMMENT '变更前值',
    `new_value`        VARCHAR(255)           DEFAULT NULL COMMENT '变更后值',
    `detected_version` VARCHAR(64)            DEFAULT NULL COMMENT '检测来源快照版本',
    `subscriber`       VARCHAR(64)            DEFAULT NULL COMMENT '提醒接收人（空=广播）',
    `read_flag`        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已读（0/1）',
    `created_at`       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '产生时间',
    PRIMARY KEY (`id`),
    KEY `idx_alert_sub` (`subscriber`, `read_flag`, `created_at`),
    KEY `idx_alert_offering` (`offering_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商品变更提醒记录（C3）';

-- ------------------------------------------------------------
-- 4. 固定流程引擎：执行实例扩展列 + 节点级执行记录
--    幂等：information_schema 判存列；建表用 IF NOT EXISTS
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS `p_add_engine_columns`;
DELIMITER $$
CREATE PROCEDURE `p_add_engine_columns`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pd_ai_workflow_executions' AND COLUMN_NAME = 'context_data'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions` ADD COLUMN `context_data` TEXT NULL COMMENT '运行上下文（恢复执行的数据源）' AFTER `execution_logs`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pd_ai_workflow_executions' AND COLUMN_NAME = 'current_node_id'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions` ADD COLUMN `current_node_id` VARCHAR(64) NULL COMMENT '当前推进到的节点' AFTER `context_data`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pd_ai_workflow_executions' AND COLUMN_NAME = 'resume_token'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions` ADD COLUMN `resume_token` VARCHAR(64) NULL COMMENT '人工节点恢复令牌（一次有效）' AFTER `current_node_id`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pd_ai_workflow_executions' AND COLUMN_NAME = 'status_version'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions` ADD COLUMN `status_version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER `resume_token`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pd_ai_workflow_executions' AND COLUMN_NAME = 'workflow_version'
    ) THEN
        ALTER TABLE `pd_ai_workflow_executions` ADD COLUMN `workflow_version` INT NULL COMMENT '执行时锁定的流程定义版本' AFTER `status_version`;
    END IF;
END$$
DELIMITER ;
CALL `p_add_engine_columns`();
DROP PROCEDURE IF EXISTS `p_add_engine_columns`;

CREATE TABLE IF NOT EXISTS `pd_ai_workflow_node_logs` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `execution_id`  VARCHAR(100) NOT NULL COMMENT '执行实例 ID',
    `node_id`       VARCHAR(64)  NOT NULL COMMENT '节点 ID',
    `node_name`     VARCHAR(128) NULL COMMENT '节点业务名（定义期 name 标签）',
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

DROP PROCEDURE IF EXISTS `p_add_node_name_column`;
DELIMITER $$
CREATE PROCEDURE `p_add_node_name_column`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pd_ai_workflow_node_logs' AND COLUMN_NAME = 'node_name'
    ) THEN
        ALTER TABLE `pd_ai_workflow_node_logs` ADD COLUMN `node_name` VARCHAR(128) NULL COMMENT '节点业务名（定义期 name 标签）' AFTER `node_id`;
    END IF;
END$$
DELIMITER ;
CALL `p_add_node_name_column`();
DROP PROCEDURE IF EXISTS `p_add_node_name_column`;

-- ============================================================
-- 校验：列出本脚本应产出的扩展对象
-- ============================================================
SELECT 'dwd_prod_metric_daily' AS obj FROM information_schema.TABLES WHERE table_schema=DATABASE() AND table_name='dwd_prod_metric_daily'
UNION ALL SELECT 'pd_ops_shelf_offerings' FROM information_schema.TABLES WHERE table_schema=DATABASE() AND table_name='pd_ops_shelf_offerings'
UNION ALL SELECT 'pd_ai_product_subscriptions' FROM information_schema.TABLES WHERE table_schema=DATABASE() AND table_name='pd_ai_product_subscriptions'
UNION ALL SELECT 'pd_ai_change_alerts' FROM information_schema.TABLES WHERE table_schema=DATABASE() AND table_name='pd_ai_change_alerts'
UNION ALL SELECT 'pd_ai_workflow_node_logs' FROM information_schema.TABLES WHERE table_schema=DATABASE() AND table_name='pd_ai_workflow_node_logs';
