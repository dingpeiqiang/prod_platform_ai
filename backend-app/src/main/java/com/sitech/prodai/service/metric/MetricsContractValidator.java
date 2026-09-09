package com.sitech.prodai.service.metric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标宽表契约校验（MetricsContract-v1）：与 {@code dwd_prod_metric_daily} /
 * {@link JdbcMetricDataSource} 行结构同构，对齐 {@code OpsGraphSchemaValidator} 风格。
 * <p>
 * soft = 缺省指标列按 0 补齐并告警；hard = 缺自然键列（stat_date/offering_id）
 * 或指标列类型不可数值化返回失败。口径单源为 metrics_registry.json（列名 = source.column）。
 */
public final class MetricsContractValidator {

    public static final String CONTRACT_VERSION = "MetricsContract-v1";

    /** 自然键列（hard 校验：缺失即失败）。 */
    public static final List<String> KEY_COLUMNS = List.of(
            "stat_date",
            "offering_id",
            "region_id",
            "channel_id",
            "customer_segment"
    );

    /** 指标列（soft 校验：缺失按 0 补齐；列名与字典 source.column 对应）。 */
    public static final List<String> METRIC_COLUMNS = List.of(
            "revenue",
            "order_cnt",
            "order_cnt_addon",
            "order_cnt_main",
            "new_users",
            "churn_users",
            "active_users"
    );

    private MetricsContractValidator() {
    }

    public record ValidationResult(
            boolean ok,
            List<Map<String, Object>> normalizedRows,
            List<String> warnings,
            List<String> errors
    ) {
    }

    /**
     * 校验并规范化一批宽表行：snake_case 键、自然键缺失即失败、指标列缺省补 0。
     * 空行集视为失败（调用方以「窗口无事实数据」如实兜底）。
     */
    public static ValidationResult validateAndNormalize(List<Map<String, Object>> rows) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            errors.add("metric rows is null or empty");
            return new ValidationResult(false, out, warnings, errors);
        }
        int rowNum = 0;
        for (Map<String, Object> row : rows) {
            rowNum++;
            if (row == null) {
                errors.add("row " + rowNum + " is null");
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            boolean keyMissing = false;
            for (String key : KEY_COLUMNS) {
                Object v = row.get(key);
                if (v == null || String.valueOf(v).isBlank()) {
                    // 汇总维度缺省回 'ALL'；自然键（stat_date/offering_id）缺失即失败
                    if (key.equals("stat_date") || key.equals("offering_id")) {
                        errors.add("row " + rowNum + " missing key column: " + key);
                        keyMissing = true;
                    } else {
                        warnings.add("row " + rowNum + " dimension " + key + " blank, filled 'ALL'");
                        normalized.put(key, "ALL");
                    }
                } else {
                    normalized.put(key, v);
                }
            }
            if (keyMissing) {
                continue;
            }
            for (String metric : METRIC_COLUMNS) {
                Object v = row.get(metric);
                if (v == null) {
                    warnings.add("row " + rowNum + " metric " + metric + " missing, filled 0");
                    normalized.put(metric, 0);
                    continue;
                }
                if (v instanceof Number) {
                    normalized.put(metric, v);
                } else {
                    try {
                        normalized.put(metric, Double.parseDouble(String.valueOf(v)));
                    } catch (NumberFormatException e) {
                        errors.add("row " + rowNum + " metric " + metric + " not numeric: " + v);
                    }
                }
            }
            out.add(normalized);
        }
        boolean ok = errors.isEmpty();
        return new ValidationResult(ok, out, warnings, errors);
    }

    /** 契约说明：列清单 / 版本 / 消费方，供 ETL 对接方联调。 */
    public static Map<String, Object> contractDescriptor() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", CONTRACT_VERSION);
        out.put("table", "dwd_prod_metric_daily");
        out.put("grain", "stat_date × offering_id × region_id × channel_id × customer_segment");
        out.put("keyColumns", KEY_COLUMNS);
        out.put("metricColumns", METRIC_COLUMNS);
        out.put("registry", "classpath:ontology/metrics_registry.json");
        out.put("consumer", "JdbcMetricDataSource (prodai.metric.source=jdbc)");
        out.put("etl", "T+1 幂等写入（自然键 ON DUPLICATE KEY UPDATE），须含（offering,'ALL','ALL','ALL'）汇总行");
        return out;
    }
}
