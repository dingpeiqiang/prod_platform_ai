-- ============================================================
-- 02_metric_schema.sql — 运营指标宽表（指标域 P0）
-- 目标库：业务侧数仓 / 本平台 MySQL（GoldenDB 兼容）
-- 用途：商品×地区×渠道×客群 日粒度指标事实表，
--       由 T+1 ETL 从 BOSS/CRM/计费账务写入；H2 同构表见 schema-h2.sql §23
-- 指标口径单源：backend-app/src/main/resources/ontology/metrics_registry.json
--   （本表列名与字典 source.column 严格对应，改口径先改字典）
-- ============================================================

USE prod_platform_ai;

CREATE TABLE `dwd_prod_metric_daily` (
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
)
    ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='产商品运营指标日宽表';

-- ============================================================
-- ETL 约定（T+1）：
--   1. 幂等写入：按 uk_pmd_natural 自然键 ON DUPLICATE KEY UPDATE
--   2. 维度展开：明细行按 offering×region×channel×segment 写细粒度，
--      同时保证（offering,'ALL','ALL','ALL'）汇总行存在（MetricService 默认查 ALL）
--   3. 数据守卫：写入行数/指标空值率校验，异常告警并阻断当日发布
--   4. 平台侧读取：prodai.metric.source=jdbc + jdbc-url 指向本表（只读账号）
-- ============================================================
