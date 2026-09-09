package com.sitech.prodai.service.metric;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 运营指标数据源契约（指标域 P0）：多源统一产出日粒度指标行。
 * <p>
 * 行结构（snake_case 传输契约，同 OpsProductDataSource 风格）：
 * <pre>
 * { stat_date: "2026-09-01", offering_id: "OF-HF-128",
 *   region_id: "ALL", channel_id: "ALL", customer_segment: "ALL",
 *   revenue: 38200.00, order_cnt: 120, new_users: 45, churn_users: 12,
 *   active_users: 3800, order_cnt_addon: null, order_cnt_main: null }
 * </pre>
 * <p>
 * 实现类：
 * <ul>
 *   <li>{@link MockMetricDataSource} — dev/demo：由 opsGraph 时序种子确定性生成（不依赖 DB）</li>
 *   <li>{@link JdbcMetricDataSource} — 生产：只读查询指标宽表 dwd_prod_metric_daily（T+1）</li>
 * </ul>
 * 只读语义：实现不得写库；结果按 stat_date 升序返回。
 */
public interface MetricDataSource {

    /** 数据源标识：mock / jdbc。 */
    String sourceId();

    /**
     * 拉取指标行。
     *
     * @param offeringIds 商品范围；空/缺省 = 全量商品
     * @param from        起始日期（含）
     * @param to          截止日期（含）
     * @return 指标行列表（stat_date 升序）；无数据返回空列表（不冒充有数）
     */
    List<Map<String, Object>> fetchDailyRows(List<String> offeringIds, LocalDate from, LocalDate to);
}
