-- ============================================================
-- 05_change_subscription_tables.sql — 商品变更订阅与提醒表（方案 §6-C3，对应缺口 C5）
-- 目标库：平台自有 MySQL（GoldenGate）/H2 同构
-- 用途：
--   pd_ai_product_subscriptions  用户对单个商品的变更订阅登记（订阅人=服务端登录态，
--                                不接受前端透传；subscriber+offering_id 幂等唯一）
--   pd_ai_change_alerts         变更检测产生的提醒记录（ChangeDetectService 图谱重载后
--                                新旧快照字段级 diff，命中订阅商品落行）
-- 产生机制：product_change_alert 工具 subscribe 动作登记；ABoxSyncScheduler 同步链
--           COMMIT 成功后 ChangeDetectService 对比新旧货架快照（月费/状态），
--           变更命中订阅 → 落提醒（出口 v1=工具 list_alerts 查询透出，
--           生产可加站内信/短信监听者扩展，发布与检测零改动）
-- H2 同构 DDL：backend-app/src/main/resources/sql/h2/schema-h2.sql §25
-- ============================================================

USE prod_platform_ai;

CREATE TABLE `pd_ai_product_subscriptions` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `subscriber`    VARCHAR(64)   NOT NULL COMMENT '订阅人登录名（服务端登录态）',
    `offering_id`   VARCHAR(64)   NOT NULL COMMENT '订阅商品编码',
    `offering_name` VARCHAR(255)           DEFAULT NULL COMMENT '订阅时商品名称快照',
    `status`        VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT '状态：active/cancelled',
    `created_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_psub` (`subscriber`, `offering_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商品变更订阅登记（C3）';

CREATE TABLE `pd_ai_change_alerts` (
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
