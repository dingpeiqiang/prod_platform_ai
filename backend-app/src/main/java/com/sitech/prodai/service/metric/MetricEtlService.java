package com.sitech.prodai.service.metric;

import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 指标宽表 T+1 ETL 写入器（指标域 P3 本地替身）：模拟 BOSS/CRM 数仓 T+1 灌宽表的外部依赖。
 * <p>
 * 数据来源：{@link MockMetricDataSource}（与 demo 同一确定性生成器，即"上游数仓"的本地替身），
 * 经 {@link MetricsContractValidator} 契约校验后幂等写入主库 dwd_prod_metric_daily
 * （H2 MODE=MySQL / GoldenDB 均支持 ON DUPLICATE KEY UPDATE，对齐 sql/02_metric_schema.sql ETL 约定 §1）。
 * <p>
 * 触发方式：定时（prodai.metric.etl-enabled=true 时按 cron，默认关）+ 手动
 * （POST /api/v1/product-center/metrics/etl，联调/演示用）。
 * 写入后生产可切 {@code prodai.metric.source=jdbc}（同库）验证 mock→ETL→jdbc 全链路。
 */
@Service
public class MetricEtlService {

    private static final Logger log = LoggerFactory.getLogger(MetricEtlService.class);

    /** 幂等策略（对齐 sql/02_metric_schema.sql ETL 约定 §1，H2/MySQL 可移植实现）：先清窗口再批量插入。 */
    private static final String INSERT_SQL =
            "INSERT INTO dwd_prod_metric_daily (stat_date, offering_id, region_id, channel_id, customer_segment, "
                    + "revenue, order_cnt, order_cnt_addon, order_cnt_main, new_users, churn_users, active_users) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String DELETE_WINDOW_SQL =
            "DELETE FROM dwd_prod_metric_daily WHERE stat_date >= ? AND stat_date <= ?";

    private final ProdAiProperties properties;
    private final MockMetricDataSource mockDataSource;
    private final JdbcTemplate jdbcTemplate;

    /** 最近一次 ETL 执行摘要（可观测/端点展示）。 */
    private volatile Map<String, Object> lastRun = Map.of();

    public MetricEtlService(ProdAiProperties properties,
                            MockMetricDataSource mockDataSource,
                            ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbcTemplateProvider) {
        this.properties = properties;
        this.mockDataSource = mockDataSource;
        this.jdbcTemplate = jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable();
    }

    /** 最近一次执行摘要：{success, rows, window, mode, message}。 */
    public Map<String, Object> lastRun() {
        return lastRun;
    }

    /**
     * 定时 T+1 灌数（默认关：dev/demo 手动触发即可，生产由真实数仓 ETL 替代本类）。
     */
    @Scheduled(cron = "${prodai.metric.etl-cron:0 30 2 * * ?}")
    public void runScheduled() {
        run("scheduled");
    }

    /**
     * 手动/定时执行 ETL：生成近 etl-days 天宽表数据 → 契约校验 → 幂等落库。
     *
     * @param mode 触发来源标记（scheduled/manual）
     * @return 执行摘要（含写入行数与窗口）
     */
    public synchronized Map<String, Object> run(String mode) {
        if (jdbcTemplate == null) {
            lastRun = Map.of("success", false, "mode", mode,
                    "message", "JdbcTemplate 未装配（数据源不可用），ETL 不可执行");
            return lastRun;
        }
        int days = properties.getMetric().getEtlDays();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        try {
            List<Map<String, Object>> rows = mockDataSource.fetchDailyRows(null, from, to);
            MetricsContractValidator.ValidationResult validated =
                    MetricsContractValidator.validateAndNormalize(rows);
            if (!validated.ok()) {
                // ETL 约定 §3：数据守卫失败阻断当日发布
                lastRun = Map.of("success", false, "mode", mode, "window", from + " ~ " + to,
                        "message", "契约校验失败阻断写入: " + String.join("; ", validated.errors()));
                log.warn("[MetricEtl] {}", lastRun.get("message"));
                return lastRun;
            }
            if (!validated.warnings().isEmpty()) {
                log.warn("[MetricEtl] 契约软告警 {} 条（首条: {}）",
                        validated.warnings().size(), validated.warnings().get(0));
            }
            int written = upsertWindow(validated.normalizedRows(), from, to);
            lastRun = Map.of("success", true, "mode", mode, "rows", written,
                    "window", from + " ~ " + to,
                    "message", "ETL 完成（幂等覆盖 " + days + " 天窗口）");
            log.info("[MetricEtl] {} 写入 {} 行，窗口 {} ~ {}", mode, written, from, to);
            return lastRun;
        } catch (Exception e) {
            lastRun = Map.of("success", false, "mode", mode,
                    "message", "ETL 执行失败: " + e.getMessage());
            log.error("[MetricEtl] ETL 执行失败: {}", e.getMessage(), e);
            return lastRun;
        }
    }

    /** 幂等写入：先清窗口再批量插入（窗口级全量覆盖，删除行不残留）。 */
    private int upsertWindow(List<Map<String, Object>> rows, LocalDate from, LocalDate to) {
        jdbcTemplate.update(DELETE_WINDOW_SQL, Date.valueOf(from), Date.valueOf(to));
        List<Object[]> batch = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            batch.add(new Object[]{
                    Date.valueOf(String.valueOf(row.get("stat_date"))),
                    row.get("offering_id"),
                    row.get("region_id"),
                    row.get("channel_id"),
                    row.get("customer_segment"),
                    row.get("revenue"),
                    row.get("order_cnt"),
                    row.get("order_cnt_addon"),
                    row.get("order_cnt_main"),
                    row.get("new_users"),
                    row.get("churn_users"),
                    row.get("active_users")
            });
        }
        int[] results = jdbcTemplate.batchUpdate(INSERT_SQL, batch);
        int written = 0;
        for (int r : results) {
            written += Math.max(0, r);
        }
        return written;
    }
}
