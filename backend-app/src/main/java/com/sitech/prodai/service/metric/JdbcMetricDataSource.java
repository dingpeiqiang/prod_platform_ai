package com.sitech.prodai.service.metric;

import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标宽表 JDBC 数据源（指标域 P0 生产源）：只读查询 dwd_prod_metric_daily。
 * <p>
 * 对齐 {@code JdbcOpsProductDataSource} 的只读/护栏风格：仅 SELECT、setMaxRows 护栏、
 * 异常上抛由调用方（MetricService）捕获降级；连接参数来自 prodai.metric.*。
 * <p>
 * 默认查询保证自然键（'ALL' 汇总行）存在（ETL 约定 §2），
 * 可用 customSql 覆盖（如指向数仓同构视图）。
 */
public class JdbcMetricDataSource implements MetricDataSource {

    private static final Logger log = LoggerFactory.getLogger(JdbcMetricDataSource.class);

    static final String DEFAULT_SQL =
            "SELECT stat_date, offering_id, region_id, channel_id, customer_segment, "
                    + "revenue, order_cnt, order_cnt_addon, order_cnt_main, new_users, churn_users, active_users "
                    + "FROM %s WHERE stat_date >= ? AND stat_date <= ?%s "
                    + "ORDER BY stat_date ASC";

    private final ProdAiProperties properties;
    private final String sql;
    private volatile long lastRowCount = 0;

    public JdbcMetricDataSource(ProdAiProperties properties) {
        this(properties, null);
    }

    public JdbcMetricDataSource(ProdAiProperties properties, String customSql) {
        this.properties = properties;
        this.sql = customSql == null || customSql.isBlank() ? null : customSql.trim();
    }

    @Override
    public String sourceId() {
        return "jdbc";
    }

    /** 最近一次成功加载的行数（可观测）。 */
    public long lastRowCount() {
        return lastRowCount;
    }

    @Override
    public List<Map<String, Object>> fetchDailyRows(List<String> offeringIds, LocalDate from, LocalDate to) {
        ProdAiProperties.Metric cfg = properties.getMetric();
        if (cfg.getJdbcUrl().isBlank()) {
            throw new IllegalStateException(
                    "prodai.metric.source=jdbc requires prodai.metric.jdbc-url. Refusing empty result.");
        }
        String offeringFilter = "";
        if (offeringIds != null && !offeringIds.isEmpty()) {
            offeringFilter = " AND offering_id IN (" + placeholders(offeringIds.size()) + ")";
        }
        String rendered = sql != null
                ? sql
                : String.format(DEFAULT_SQL, cfg.getWideTable(), offeringFilter);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = openConnection(cfg)) {
            conn.setReadOnly(true);
            try (PreparedStatement ps = conn.prepareStatement(rendered)) {
                ps.setMaxRows(cfg.getMaxRows());
                int idx = 1;
                ps.setDate(idx++, java.sql.Date.valueOf(from));
                ps.setDate(idx++, java.sql.Date.valueOf(to));
                if (offeringIds != null) {
                    for (String id : offeringIds) {
                        ps.setString(idx++, id);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    int columnCount = rs.getMetaData().getColumnCount();
                    String[] snakeNames = new String[columnCount + 1];
                    for (int i = 1; i <= columnCount; i++) {
                        snakeNames[i] = snake(rs.getMetaData().getColumnLabel(i));
                    }
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            if (value != null) {
                                row.put(snakeNames[i], value);
                            }
                        }
                        rows.add(row);
                        if (rows.size() >= cfg.getMaxRows()) {
                            log.warn("[JdbcMetricDataSource] 达到单次查询行数上限 {}，截断（可调 metric.max-rows）",
                                    cfg.getMaxRows());
                            break;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Metric JDBC query failed: " + e.getMessage(), e);
        }
        // MetricsContract-v1 契约校验：自然键缺失即失败（上抛由 MetricService 捕获降级），
        // 维度缺省补 'ALL'、指标缺省补 0（soft）
        MetricsContractValidator.ValidationResult validated =
                MetricsContractValidator.validateAndNormalize(rows);
        if (!validated.ok()) {
            throw new IllegalStateException("Metric contract violated: "
                    + String.join("; ", validated.errors()));
        }
        if (!validated.warnings().isEmpty()) {
            log.warn("[JdbcMetricDataSource] 契约软告警 {} 条（首条: {}）",
                    validated.warnings().size(), validated.warnings().get(0));
        }
        lastRowCount = validated.normalizedRows().size();
        return validated.normalizedRows();
    }

    private String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    private Connection openConnection(ProdAiProperties.Metric cfg) throws SQLException {
        try {
            Class.forName(cfg.getJdbcDriver());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Metric JDBC driver not found: " + cfg.getJdbcDriver(), e);
        }
        return DriverManager.getConnection(cfg.getJdbcUrl(), cfg.getJdbcUsername(), cfg.getJdbcPassword());
    }

    /** 列名 → snake_case（契约行键统一 snake_case，MetricService 侧自行 camel 兜底）。 */
    private String snake(String column) {
        if (column == null || column.isBlank()) {
            return "";
        }
        return column.trim().toLowerCase()
                .replaceAll("[\\W]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
