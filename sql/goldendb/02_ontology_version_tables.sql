-- ============================================================
-- P1-5 增量脚本：本体资产版本库 表 A/B（GoldenDB 兼容版）
-- 来源：sql/02_ontology_version_tables.sql（MySQL 8.0 基线）的 GoldenDB 适配
-- 适用：GoldenDB 存量库升级（新库直接执行 goldendb/01_full_schema_ddl.sql）
--
-- ORA-02441 根因与修复（baseline -> GoldenDB 差异）：
--   1) baseline 的 ALTER TABLE ... MODIFY COLUMN 在 version_id 列被代理层
--      识别为外键操作 -> ORA-02441: foreign key operation is not supported!
--      修复：新建表时直接定义为可空列，不再 MODIFY；本脚本改为按
--      information_schema 判断列是否存在，缺列才 ADD（幂等且无 MODIFY）
--   2) MySQL 8.0 的 ADD COLUMN IF NOT EXISTS 语法 GoldenDB（5.7 基线）
--      不支持 -> 改用 information_schema 检查 + 条件执行
--   3) DROP TABLE 前不依赖 FOREIGN_KEY_CHECKS=0（GoldenDB 分布式代理
--      不支持跳过外键检查），按依赖顺序删除
-- ============================================================

SET NAMES utf8mb4;
USE `prodplatformai`;

-- 清理遗留退役表（从无引用，直接删除）
DROP TABLE IF EXISTS `pd_ai_ontology_instance_history`;

-- 表 A：版本主表
CREATE TABLE IF NOT EXISTS `pd_ai_ontology_version` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本体资产版本主表' DISTRIBUTED BY DUPLICATE(g1,g2);

-- 表 B：动作日志（version_id 直接建为可空列，应用层维护逻辑引用，无物理外键）
CREATE TABLE IF NOT EXISTS `pd_ai_ontology_version_log` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `version_id`         BIGINT                DEFAULT NULL COMMENT '逻辑外键 pd_ai_ontology_version.id（应用层维护）',
    `domain`             VARCHAR(32)  NOT NULL DEFAULT 'version' COMMENT '审计域：version / risk / config / batch',
    `trace_id`           VARCHAR(128)          DEFAULT NULL COMMENT 'config 链路 trace_id（仅 domain=config 有值）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本体资产版本动作日志（审计一张表）' DISTRIBUTED BY DUPLICATE(g1,g2);

-- ------------------------------------------------------------
-- 存量库补列（替代 MySQL 8.0 的 ADD COLUMN IF NOT EXISTS；
-- GoldenDB（5.7 基线）不支持该语法，用 information_schema 判断）
-- ------------------------------------------------------------
-- 补 domain 列：
SELECT COUNT(*) INTO @col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pd_ai_ontology_version_log'
      AND COLUMN_NAME  = 'domain';
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `pd_ai_ontology_version_log` ADD COLUMN `domain` VARCHAR(32) NOT NULL DEFAULT ''version'' COMMENT ''审计域：version / risk / config / batch''',
    'SELECT ''column domain already exists''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 补 trace_id 列：
SELECT COUNT(*) INTO @col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pd_ai_ontology_version_log'
      AND COLUMN_NAME  = 'trace_id';
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `pd_ai_ontology_version_log` ADD COLUMN `trace_id` VARCHAR(128) DEFAULT NULL COMMENT ''config 链路 trace_id''',
    'SELECT ''column trace_id already exists''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- version_id 放宽为可空（替代 baseline 的 MODIFY COLUMN，规避 ORA-02441）
-- 说明：GoldenDB 分布式表禁止对被索引/约束引用的列做 MODIFY。
-- 该列在本表上只有普通索引 idx_ovl_version_id，无物理外键，
-- 因此不做任何 ALTER；若存量表上存在物理外键，需先手工删除：
--   SELECT constraint_name FROM information_schema.TABLE_CONSTRAINTS
--    WHERE TABLE_SCHEMA = DATABASE()
--      AND TABLE_NAME = 'pd_ai_ontology_version_log'
--      AND CONSTRAINT_TYPE = 'FOREIGN KEY';
--   ALTER TABLE `pd_ai_ontology_version_log` DROP CONSTRAINT <名称>;
--   ALTER TABLE `pd_ai_ontology_version_log` DROP INDEX <对应索引名>;
-- 新列语义（可空 BIGINT）与 baseline MODIFY 后的目标状态一致。
-- ------------------------------------------------------------
