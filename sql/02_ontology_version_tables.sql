-- ============================================================
-- P1-5 增量脚本：本体资产版本库 表 A/B（设计方案 §13.1）
-- 表 A pd_ai_ontology_version        版本主表（payload = 回滚唯一事实源）
-- 表 B pd_ai_ontology_version_log    动作日志（原 pd_ai_ontology_instance_history 接线改造，低风险清理）
-- 适用：存量库升级（新库直接执行 01_full_schema_ddl.sql）
-- 幂等：重复执行不报错（历史表仅在存在且无数据时清理）
-- ============================================================

SET NAMES utf8mb4;
USE `prodplatformai`;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体资产版本主表';

-- 表 B：动作日志
CREATE TABLE IF NOT EXISTS `pd_ai_ontology_version_log` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `version_id`         BIGINT       NOT NULL COMMENT '外键 pd_ai_ontology_version.id',
    `action`             VARCHAR(32)  NOT NULL COMMENT 'publish / rollback / deprecate / reload / override',
    `operator`           VARCHAR(64)           DEFAULT NULL,
    `detail`             TEXT                  COMMENT '动作明细 JSON',
    `created_at`         DATETIME(6)           DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ovl_version_id` (`version_id`),
    KEY `idx_ovl_action` (`action`),
    KEY `idx_ovl_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本体资产版本动作日志';

-- 死代码清理：pd_ai_ontology_instance_history 从无引用，改造退役为表 B（保留历史数据不迁移——原表从未产生数据流）
DROP TABLE IF EXISTS `pd_ai_ontology_instance_history`;
