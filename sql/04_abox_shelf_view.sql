-- ============================================================
-- 04_abox_shelf_view.sql — ABox 生产同步源（在架商品只读视图）
-- 目标库：业务侧库 / 本平台 MySQL（GoldenDB 兼容）
-- 用途：JdbcOpsProductDataSource.DEFAULT_SQL 的数据契约源，
--       abox-source=jdbc 时 ABoxSyncScheduler 每 30 分钟只读同步进事实图
--       （LOAD→VALIDATE→SMOKE→COMMIT 守卫，失败回退 last-known-good）
-- 列契约：与 JdbcOpsProductDataSource.DEFAULT_SQL 的 14 列严格对应，
--         snake_case 列名由同步器自动转 lowerCamelCase 映射货架行字段；
--         缺列由 OpsGraphSchemaValidator 软补齐（新增列向后兼容）
-- 状态语义：state 取值对齐 ops_graph（on_shelf=在架 / on_sale=在售），
--           DEFAULT_SQL 仅取这两种状态；mock_graph 中文"上架"在同步器侧不做翻译，
--           业务侧 ETL 写入时须映射为英文枚举
-- 演示种子：100 行对齐 classpath:ontology/mock_graph.json shelfOfferings
--           （真实感=真实库结构 + 确定性数据；生产由业务系统 ETL 接管）
-- H2 同构表：backend-app/src/main/resources/sql/h2/schema-h2.sql §24
-- ============================================================

USE prod_platform_ai;

CREATE TABLE `pd_ops_shelf_offerings` (
    `offering_id`      VARCHAR(64)   NOT NULL COMMENT '产商品编码（offeringId，硬校验非空）',
    `offering_name`    VARCHAR(255)  NOT NULL COMMENT '产商品名称（offeringName）',
    `category_code`    VARCHAR(64)            DEFAULT NULL COMMENT '品类编码（categoryCode，如 familyBasePrc）',
    `category_name`    VARCHAR(128)           DEFAULT NULL COMMENT '品类名称（categoryName，如 家庭基础套餐）',
    `product_line`     VARCHAR(64)            DEFAULT NULL COMMENT '产品线（productLine：家庭/宽带/个人）',
    `offering_type`    VARCHAR(32)   NOT NULL DEFAULT 'addon' COMMENT '商品类型（offeringType：main_pkg/addon/fusion）',
    `state`            VARCHAR(32)   NOT NULL COMMENT '状态（state：on_shelf/on_sale；DEFAULT_SQL 仅取这两种）',
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
)
    ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='ABox 在架商品事实表（业务系统只读视图同构，abox-source=jdbc 数据源）';

-- ============================================================
-- ETL 约定（业务系统 → 本表）：
--   1. 幂等写入：按主键 offering_id ON DUPLICATE KEY UPDATE
--   2. 状态映射：业务侧"在架/在售"→ on_shelf/on_sale（英文枚举）；
--      退市/下架行不写入（或写入后由 DEFAULT_SQL 的 WHERE 自然过滤）
--   3. 风险标记：category 由业务侧离线计算写入（0元/零销/折损等标记驱动 ops 风险稽核演示面）
--   4. 数据守卫：写入行数核对（offering_id 非空唯一）；中文品类名必填建议
--   5. 平台侧读取：ABOX_SOURCE=jdbc + ABOX_JDBC_URL 指向本表所在库（只读账号），
--      刷新周期 ABOX_SYNC_INTERVAL_MINUTES（默认 30 分钟），行数护栏 ABOX_MAX_ROWS（默认 10000）
-- ============================================================

-- ------------------------------------------------------------
-- 演示种子：100 行，对齐 mock_graph.json shelfOfferings（可重复执行）
-- 生产环境可不执行本段（由业务 ETL 灌入真实数据）
-- ------------------------------------------------------------

INSERT INTO `pd_ops_shelf_offerings` (
    `offering_id`, `offering_name`, `category_code`, `category_name`, `product_line`,
    `offering_type`, `state`, `monthly_fee`, `fixed_fee_amount`,
    `sales_cnt_30d`, `revenue_30d`, `shelf_days`, `message_root_key`, `category`
) VALUES (
    ('SCHEME_FAP_001', '家庭增值权益20', 'familyAddPrc', '家庭附加业务', '家庭', 'addon', 'on_shelf', 20, 20, 90, 1800, 40, 'familyAddPrc', 'normal'),
    ('SCHEME_FBP_001', '家庭亲情网基础套餐', 'familyBasePrc', '家庭基础套餐', '家庭', 'fusion', 'on_shelf', 99, 99, 150, 14850, 120, 'familyBasePrc', 'normal'),
    ('SCHEME_BOS_001', '宽带提速包30', 'broadBandOptSpeedPrc', '宽带加速包', '宽带', 'addon', 'on_shelf', 30, 30, 80, 2400, 50, 'broadBandOptSpeedPrc', 'normal'),
    ('SCHEME_PAP_001', '个人附加流量包19', 'personAddPrc', '个人附加资费', '个人', 'addon', 'on_shelf', 19, 19, 300, 5700, 60, 'personAddPrc', 'normal'),
    ('SCHEME_BBM_001', '宽带主资费80', 'broadBandMainPrc', '宽带主资费', '宽带', 'main_pkg', 'on_shelf', 80, 80, 120, 9600, 90, 'broadBandMainPrc', 'normal'),
    ('SCHEME_PMP_001', '测试资费-个人主', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 40, 40, 200, 8000, 100, 'personMainPrc', 'normal'),
    ('OF-HF-128', '家庭融合畅享128', 'familyBasePrc', '家庭基础套餐', '家庭', 'main_pkg', 'on_shelf', 128, 128, 860, 110080, 210, 'familyBasePrc', 'normal'),
    ('OF-RISK-001', '校园体验流量包0元', 'personAddPrc', '个人附加资费', '个人', 'addon', 'on_shelf', 0, 0, 120, 0, 45, 'personAddPrc', 'zero_fee'),
    ('OF-LOW-019', '旧版彩铃包-2019', NULL, NULL, NULL, 'addon', 'on_shelf', 3, NULL, 0, 0, 287, NULL, 'low_eff'),
    ('OF-GIFT-WL', '会员权益赠送流量包', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 200, 0, 60, NULL, 'whitelist'),
    ('OF-DISC-001', '全额赠送可重复体验包', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 55, 0, 40, NULL, 'abnormal_discount'),
    ('OF-DISC-002', '全额赠送可重复包-02', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 22, 50, 34, NULL, 'abnormal_discount'),
    ('OF-DISC-003', '全额赠送可重复包-03', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 23, 50, 36, NULL, 'abnormal_discount'),
    ('OF-DISC-004', '全额赠送可重复包-04', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 24, 50, 38, NULL, 'abnormal_discount'),
    ('OF-DISC-005', '全额赠送可重复包-05', NULL, NULL, NULL, 'addon', 'on_shelf', 19, NULL, 25, 50, 40, NULL, 'abnormal_discount'),
    ('OF-LOW-001', '旧版加装包-长期零销-02', NULL, NULL, NULL, 'addon', 'on_shelf', 7, NULL, 0, 0, 218, NULL, 'low_eff'),
    ('OF-LOW-002', '旧版加装包-长期零销-03', NULL, NULL, NULL, 'addon', 'on_shelf', 8, NULL, 0, 0, 232, NULL, 'low_eff'),
    ('OF-LOW-003', '旧版加装包-长期零销-04', NULL, NULL, NULL, 'addon', 'on_shelf', 9, NULL, 0, 0, 246, NULL, 'low_eff'),
    ('OF-LOW-004', '旧版加装包-长期零销-05', NULL, NULL, NULL, 'addon', 'on_shelf', 10, NULL, 0, 0, 260, NULL, 'low_eff'),
    ('OF-LOW-005', '旧版加装包-长期零销-06', NULL, NULL, NULL, 'addon', 'on_shelf', 11, NULL, 0, 0, 274, NULL, 'low_eff'),
    ('OF-LOW-006', '旧版加装包-长期零销-07', NULL, NULL, NULL, 'addon', 'on_shelf', 12, NULL, 0, 0, 288, NULL, 'low_eff'),
    ('OF-LOW-T01', '旧版加装包-阈值演示-01', NULL, NULL, NULL, 'addon', 'on_shelf', 7, NULL, 0, 3, 112, NULL, 'threshold_demo'),
    ('OF-LOW-T02', '旧版加装包-阈值演示-02', NULL, NULL, NULL, 'addon', 'on_shelf', 8, NULL, 0, 4, 124, NULL, 'threshold_demo'),
    ('OF-LOW-T03', '旧版加装包-阈值演示-03', NULL, NULL, NULL, 'addon', 'on_shelf', 9, NULL, 0, 5, 136, NULL, 'threshold_demo'),
    ('OF-N-001', '标准套餐-001', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 44, NULL, 83, 5120, 61, NULL, 'normal'),
    ('OF-N-002', '标准套餐-002', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 49, NULL, 86, 5240, 62, NULL, 'normal'),
    ('OF-N-003', '标准套餐-003', NULL, NULL, NULL, 'addon', 'on_shelf', 54, NULL, 89, 5360, 63, NULL, 'normal'),
    ('OF-N-004', '标准套餐-004', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 59, NULL, 92, 5480, 64, NULL, 'normal'),
    ('OF-N-005', '标准套餐-005', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 64, NULL, 95, 5600, 65, NULL, 'normal'),
    ('OF-N-006', '标准套餐-006', NULL, NULL, NULL, 'addon', 'on_shelf', 69, NULL, 98, 5720, 66, NULL, 'normal'),
    ('OF-N-007', '标准套餐-007', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 74, NULL, 101, 5840, 67, NULL, 'normal'),
    ('OF-N-008', '标准套餐-008', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 79, NULL, 104, 5960, 68, NULL, 'normal'),
    ('OF-N-009', '标准套餐-009', NULL, NULL, NULL, 'addon', 'on_shelf', 84, NULL, 107, 6080, 69, NULL, 'normal'),
    ('OF-N-010', '标准套餐-010', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 89, NULL, 110, 6200, 70, NULL, 'normal'),
    ('OF-N-011', '标准套餐-011', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 94, NULL, 113, 6320, 71, NULL, 'normal'),
    ('OF-N-012', '标准套餐-012', NULL, NULL, NULL, 'addon', 'on_shelf', 99, NULL, 116, 6440, 72, NULL, 'normal'),
    ('OF-N-013', '标准套餐-013', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 104, NULL, 119, 6560, 73, NULL, 'normal'),
    ('OF-N-014', '标准套餐-014', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 109, NULL, 122, 6680, 74, NULL, 'normal'),
    ('OF-N-015', '标准套餐-015', NULL, NULL, NULL, 'addon', 'on_shelf', 114, NULL, 125, 6800, 75, NULL, 'normal'),
    ('OF-N-016', '标准套餐-016', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 119, NULL, 128, 6920, 76, NULL, 'normal'),
    ('OF-N-017', '标准套餐-017', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 124, NULL, 131, 7040, 77, NULL, 'normal'),
    ('OF-N-018', '标准套餐-018', NULL, NULL, NULL, 'addon', 'on_shelf', 129, NULL, 134, 7160, 78, NULL, 'normal'),
    ('OF-N-019', '标准套餐-019', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 134, NULL, 137, 7280, 79, NULL, 'normal'),
    ('OF-N-020', '标准套餐-020', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 39, NULL, 140, 7400, 80, NULL, 'normal'),
    ('OF-N-021', '标准套餐-021', NULL, NULL, NULL, 'addon', 'on_shelf', 44, NULL, 143, 7520, 81, NULL, 'normal'),
    ('OF-N-022', '标准套餐-022', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 49, NULL, 146, 7640, 82, NULL, 'normal'),
    ('OF-N-023', '标准套餐-023', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 54, NULL, 149, 7760, 83, NULL, 'normal'),
    ('OF-N-024', '标准套餐-024', NULL, NULL, NULL, 'addon', 'on_shelf', 59, NULL, 152, 7880, 84, NULL, 'normal'),
    ('OF-N-025', '标准套餐-025', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 64, NULL, 155, 8000, 85, NULL, 'normal'),
    ('OF-N-026', '标准套餐-026', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 69, NULL, 158, 8120, 86, NULL, 'normal'),
    ('OF-N-027', '标准套餐-027', NULL, NULL, NULL, 'addon', 'on_shelf', 74, NULL, 161, 8240, 87, NULL, 'normal'),
    ('OF-N-028', '标准套餐-028', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 79, NULL, 164, 8360, 88, NULL, 'normal'),
    ('OF-N-029', '标准套餐-029', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 84, NULL, 167, 8480, 89, NULL, 'normal'),
    ('OF-N-030', '标准套餐-030', NULL, NULL, NULL, 'addon', 'on_shelf', 89, NULL, 170, 8600, 90, NULL, 'normal'),
    ('OF-N-031', '标准套餐-031', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 94, NULL, 173, 8720, 91, NULL, 'normal'),
    ('OF-N-032', '标准套餐-032', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 99, NULL, 176, 8840, 92, NULL, 'normal'),
    ('OF-N-033', '标准套餐-033', NULL, NULL, NULL, 'addon', 'on_shelf', 104, NULL, 179, 8960, 93, NULL, 'normal'),
    ('OF-N-034', '标准套餐-034', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 109, NULL, 182, 9080, 94, NULL, 'normal'),
    ('OF-N-035', '标准套餐-035', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 114, NULL, 185, 9200, 95, NULL, 'normal'),
    ('OF-N-036', '标准套餐-036', NULL, NULL, NULL, 'addon', 'on_shelf', 119, NULL, 188, 9320, 96, NULL, 'normal'),
    ('OF-N-037', '标准套餐-037', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 124, NULL, 191, 9440, 97, NULL, 'normal'),
    ('OF-N-038', '标准套餐-038', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 129, NULL, 194, 9560, 98, NULL, 'normal'),
    ('OF-N-039', '标准套餐-039', NULL, NULL, NULL, 'addon', 'on_shelf', 134, NULL, 197, 9680, 99, NULL, 'normal'),
    ('OF-N-040', '标准套餐-040', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 39, NULL, 200, 9800, 100, NULL, 'normal'),
    ('OF-N-041', '标准套餐-041', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 44, NULL, 203, 9920, 101, NULL, 'normal'),
    ('OF-N-042', '标准套餐-042', NULL, NULL, NULL, 'addon', 'on_shelf', 49, NULL, 206, 10040, 102, NULL, 'normal'),
    ('OF-N-043', '标准套餐-043', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 54, NULL, 209, 10160, 103, NULL, 'normal'),
    ('OF-N-044', '标准套餐-044', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 59, NULL, 212, 10280, 104, NULL, 'normal'),
    ('OF-N-045', '标准套餐-045', NULL, NULL, NULL, 'addon', 'on_shelf', 64, NULL, 215, 10400, 105, NULL, 'normal'),
    ('OF-N-046', '标准套餐-046', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 69, NULL, 218, 10520, 106, NULL, 'normal'),
    ('OF-N-047', '标准套餐-047', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 74, NULL, 221, 10640, 107, NULL, 'normal'),
    ('OF-N-048', '标准套餐-048', NULL, NULL, NULL, 'addon', 'on_shelf', 79, NULL, 224, 10760, 108, NULL, 'normal'),
    ('OF-N-049', '标准套餐-049', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 84, NULL, 227, 10880, 109, NULL, 'normal'),
    ('OF-N-050', '标准套餐-050', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 89, NULL, 230, 11000, 110, NULL, 'normal'),
    ('OF-N-051', '标准套餐-051', NULL, NULL, NULL, 'addon', 'on_shelf', 94, NULL, 233, 11120, 111, NULL, 'normal'),
    ('OF-N-052', '标准套餐-052', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 99, NULL, 236, 11240, 112, NULL, 'normal'),
    ('OF-N-053', '标准套餐-053', NULL, NULL, NULL, 'addon', 'on_shelf', 104, NULL, 239, 11360, 113, NULL, 'normal'),
    ('OF-N-054', '标准套餐-054', NULL, NULL, NULL, 'addon', 'on_shelf', 109, NULL, 242, 11480, 114, NULL, 'normal'),
    ('OF-N-055', '标准套餐-055', NULL, NULL, NULL, 'main_pkg', 'on_shelf', 114, NULL, 245, 11600, 115, NULL, 'normal'),
    ('OF-RISK-002', '体验测试流量包0元-02', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 12, 0, 41, NULL, 'zero_fee'),
    ('OF-RISK-003', '体验测试流量包0元-03', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 13, 0, 44, NULL, 'zero_fee'),
    ('OF-RISK-004', '体验测试流量包0元-04', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 14, 0, 47, NULL, 'zero_fee'),
    ('OF-RISK-005', '体验测试流量包0元-05', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 15, 0, 50, NULL, 'zero_fee'),
    ('OF-RISK-006', '体验测试流量包0元-06', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 16, 0, 53, NULL, 'zero_fee'),
    ('OF-RISK-007', '体验测试流量包0元-07', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 17, 0, 56, NULL, 'zero_fee'),
    ('OF-RISK-008', '体验测试流量包0元-08', NULL, NULL, NULL, 'addon', 'on_shelf', 0, NULL, 18, 0, 59, NULL, 'zero_fee'),
    ('OF-CAMPUS-STU-29', '大学生专属套餐29', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 29, 29, 260, 7540, 12, 'personMainPrc', 'normal'),
    ('OF-CAMPUS-STU-39', '大学生畅学套餐39', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 39, 39, 410, 15990, 25, 'personMainPrc', 'normal'),
    ('OF-CAMPUS-STU-59', '校园青春大学生59', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 59, 59, 520, 30680, 30, 'personMainPrc', 'normal'),
    ('OF-CAMPUS-STU-0', '大学生体验套餐0元', 'personAddPrc', '个人附加资费', '个人', 'addon', 'on_shelf', 0, 0, 880, 0, 7, 'personAddPrc', 'zero_fee'),
    ('OF-CAMPUS-STU-19', '大学生流量加餐包19', 'personAddPrc', '个人附加资费', '个人', 'addon', 'on_shelf', 19, 19, 640, 12160, 18, 'personAddPrc', 'normal'),
    ('OF-CAMPUS-STU-OLD-49', '大学生经典套餐49', 'personMainPrc', '个人主资费', '个人', 'main_pkg', 'on_shelf', 49, 49, 300, 14700, 95, 'personMainPrc', 'normal'),
    ('OF-DEVICE-PHONE-01', '智能手机终端A1', 'deviceMainPrc', '终端销售', '终端', 'terminal', 'on_shelf', 0, 1999, 260, 519740, 120, 'deviceMainPrc', 'normal'),
    ('OF-DEVICE-IPTV-02', '宽带电视IPTV机顶盒', 'deviceMainPrc', '终端销售', '终端', 'terminal', 'on_shelf', 10, 0, 210, 54000, 210, 'deviceMainPrc', 'normal'),
    ('OF-DEVICE-BOX-03', '智能硬件机顶盒 Pro', 'deviceMainPrc', '终端销售', '终端', 'terminal', 'on_shelf', 0, 399, 90, 35910, 60, 'deviceMainPrc', 'normal'),
    ('OF-DEVICE-PHONE-04', '智能手机终端B2合约机', 'deviceMainPrc', '终端销售', '终端', 'terminal', 'on_shelf', 0, 2999, 40, 119960, 45, 'deviceMainPrc', 'normal'),
    ('OF-SIM-MAIN-01', '号卡主卡申办', 'simCardMainPrc', '号卡资费', '号卡', 'main_pkg', 'on_shelf', 29, 29, 420, 12180, 180, 'simCardMainPrc', 'normal'),
    ('OF-SIM-CHILD-02', '副卡办理', 'simCardMainPrc', '号卡资费', '号卡', 'sub_pkg', 'on_shelf', 10, 10, 350, 3500, 240, 'simCardMainPrc', 'normal'),
    ('OF-SIM-IOT-03', '物联卡流量卡', 'simCardMainPrc', '号卡资费', '号卡', 'main_pkg', 'on_shelf', 19, 19, 500, 9500, 150, 'simCardMainPrc', 'normal'),
    ('OF-SIM-FLOW-04', '大流量卡月享包', 'simCardMainPrc', '号卡资费', '号卡', 'main_pkg', 'on_shelf', 39, 39, 310, 12090, 90, 'simCardMainPrc', 'normal')
) ON DUPLICATE KEY UPDATE
    `offering_name` = VALUES(`offering_name`),
    `category_code` = VALUES(`category_code`),
    `category_name` = VALUES(`category_name`),
    `product_line` = VALUES(`product_line`),
    `offering_type` = VALUES(`offering_type`),
    `state` = VALUES(`state`),
    `monthly_fee` = VALUES(`monthly_fee`),
    `fixed_fee_amount` = VALUES(`fixed_fee_amount`),
    `sales_cnt_30d` = VALUES(`sales_cnt_30d`),
    `revenue_30d` = VALUES(`revenue_30d`),
    `shelf_days` = VALUES(`shelf_days`),
    `message_root_key` = VALUES(`message_root_key`),
    `category` = VALUES(`category`);
