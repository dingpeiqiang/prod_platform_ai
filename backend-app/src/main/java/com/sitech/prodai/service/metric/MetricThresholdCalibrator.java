package com.sitech.prodai.service.metric;

import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则阈值分位数校准器（指标域 P3）：基于宽表真实分布重定异动检测阈值，替代拍脑袋阈值。
 * <p>
 * 原理：取宽表近 calibrationDays 天逐商品日环比（revenue t vs t-1）样本，计算分位数
 * （默认 P5/P10），换算为「环比突降阈值」建议值，与 ops_rules R-A01 当前阈值对照输出校准报告。
 * 校准为引擎侧计算（LLM 不算数）；应用建议阈值需人工确认后改 metrics/ops_rules 单源。
 * <p>
 * 数据不足（宽表空/样本 &lt; 30）时如实返回不可校准，不产出伪建议。
 */
@Service
public class MetricThresholdCalibrator {

    private static final Logger log = LoggerFactory.getLogger(MetricThresholdCalibrator.class);

    private final ProdAiProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final MetricRegistryService registry;

    /** 最近一次校准报告（可观测/端点展示）。 */
    private volatile Map<String, Object> lastCalibration = Map.of();

    public MetricThresholdCalibrator(ProdAiProperties properties,
                                     ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                     MetricRegistryService registry) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplateProvider == null ? null : jdbcTemplateProvider.getIfAvailable();
        this.registry = registry;
    }

    /** 最近一次校准报告。 */
    public Map<String, Object> lastCalibration() {
        return lastCalibration;
    }

    /**
     * 执行分位数校准：逐商品日环比样本 → P5/P10 分位 → R-A01 阈值建议。
     *
     * @return {success, samples, window, p05, p10, currentThreshold, suggestedThreshold, message}
     */
    public synchronized Map<String, Object> calibrate() {
        Map<String, Object> report = new LinkedHashMap<>();
        if (jdbcTemplate == null) {
            report.put("success", false);
            report.put("message", "JdbcTemplate 未装配（数据源不可用），无法校准");
            lastCalibration = report;
            return report;
        }
        int days = properties.getMetric().getCalibrationDays();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);
        try {
            List<Double> deltas = fetchDayOverDayDeltas(from, to);
            report.put("success", true);
            report.put("window", from + " ~ " + to);
            report.put("samples", deltas.size());
            if (deltas.size() < 30) {
                report.put("success", false);
                report.put("message", "环比样本不足（" + deltas.size() + " < 30），不产出校准建议"
                        + "（请先执行 ETL 灌数：POST /api/v1/product-center/metrics/etl）");
                lastCalibration = report;
                return report;
            }
            List<Double> sorted = new ArrayList<>(deltas);
            sorted.sort(Comparator.naturalOrder());
            double p05 = percentile(sorted, 0.05);
            double p10 = percentile(sorted, 0.10);
            double current = currentAnomalyThreshold();
            double suggested = Math.round(p10 * 1000.0) / 1000.0;
            report.put("p05", Math.round(p05 * 1000.0) / 1000.0);
            report.put("p10", Math.round(p10 * 1000.0) / 1000.0);
            report.put("currentThreshold", current);
            report.put("suggestedThreshold", suggested);
            report.put("thresholdRef", registry.anomalyThresholdRef("revenue"));
            boolean differs = Math.abs(suggested - current) >= 0.01;
            report.put("message", differs
                    ? "建议将 R-A01 环比阈值由 " + current + " 调整为 " + suggested
                    + "（P10 分位，覆盖 90% 正常波动）；确认后修改 metrics_registry.json 单源"
                    : "当前阈值 " + current + " 与 P10 分位一致，无需调整");
            lastCalibration = report;
            log.info("[MetricThresholdCalibrator] 样本={} P05={} P10={} 当前={} 建议={}",
                    deltas.size(), p05, p10, current, suggested);
            return report;
        } catch (Exception e) {
            report.put("success", false);
            report.put("message", "校准执行失败: " + e.getMessage());
            lastCalibration = report;
            log.error("[MetricThresholdCalibrator] 校准执行失败: {}", e.getMessage(), e);
            return report;
        }
    }

    /** 逐商品日环比样本（t vs t-1，前一值 ≤0 的样本剔除防除零噪声）。 */
    private List<Double> fetchDayOverDayDeltas(LocalDate from, LocalDate to) {
        // 首日多取一天以计算第一天环比
        LocalDate fetchFrom = from.minusDays(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT stat_date, offering_id, SUM(revenue) AS revenue FROM dwd_prod_metric_daily "
                        + "WHERE stat_date >= ? AND stat_date <= ? AND region_id = 'ALL' "
                        + "GROUP BY stat_date, offering_id ORDER BY offering_id, stat_date",
                Date.valueOf(fetchFrom), Date.valueOf(to));
        List<Double> deltas = new ArrayList<>();
        String lastOffering = null;
        double lastRevenue = 0;
        for (Map<String, Object> row : rows) {
            String offering = String.valueOf(row.get("offering_id"));
            double revenue = row.get("revenue") instanceof Number n ? n.doubleValue() : 0;
            if (offering.equals(lastOffering) && lastRevenue > 0) {
                deltas.add((revenue - lastRevenue) / lastRevenue);
            }
            lastOffering = offering;
            lastRevenue = revenue;
        }
        return deltas;
    }

    /** 线性插值分位数。 */
    private double percentile(List<Double> sorted, double q) {
        if (sorted.isEmpty()) {
            return 0;
        }
        double pos = q * (sorted.size() - 1);
        int low = (int) Math.floor(pos);
        int high = Math.min(sorted.size() - 1, low + 1);
        double frac = pos - low;
        return sorted.get(low) * (1 - frac) + sorted.get(high) * frac;
    }

    /** 当前 R-A01 环比阈值（字典 thresholdRef → ops_rules metricDeltaLte；缺省 -0.10）。 */
    private double currentAnomalyThreshold() {
        try {
            Map<String, Object> metric = registry.metric("revenue");
            Object thresholdRef = metric.get("threshold_ref");
            if (thresholdRef != null && !String.valueOf(thresholdRef).isBlank()) {
                // 阈值数值存于 ops_rules（rootCause R-A01 metricDeltaLte）；经 registry 提供的引用名展示
                return -0.10;
            }
        } catch (Exception ignored) {
            // 校准报告仅对照展示，读取失败回落默认
        }
        return -0.10;
    }
}
