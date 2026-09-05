package com.sitech.prodai.service.ops;

import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ABox 生产 JDBC 事实源（R5）：业务系统只读库直连，SQL → ops-graph 同构映射。
 * <p>只读同步模式：仅 SELECT，不写业务库；首次全量 + 定时刷新由 ABoxSyncScheduler 驱动，
 * 经 OpsProductGraphLoader 路由后走 LastKnownGoodGuard（LOAD→VALIDATE→SMOKE→COMMIT），
 * 同步失败自动回退 last-known-good。
 * <p>默认契约：{@code SELECT offering_id, offering_name, ... FROM <table>}，
 * 列名 snake_case 自动转 lowerCamelCase 映射为货架行字段；缺列由 OpsGraphSchemaValidator 软补齐。
 */
public class JdbcOpsProductDataSource implements OpsProductDataSource {

    private static final Logger log = LoggerFactory.getLogger(JdbcOpsProductDataSource.class);

    /** 默认查询：业务系统在架商品表（只读视图/表，字段缺失列由 schema validator 软补齐）。 */
    static final String DEFAULT_SQL =
            "SELECT offering_id, offering_name, category, category_code, category_name, "
                    + "offering_type, product_line, message_root_key, state, monthly_fee, "
                    + "fixed_fee_amount, sales_cnt_30d, revenue_30d, shelf_days "
                    + "FROM pd_ops_shelf_offerings WHERE state IN ('on_shelf', 'on_sale')";

    private final ProdAiProperties properties;
    private final String sql;
    private volatile long lastRowCount = 0;

    public JdbcOpsProductDataSource(ProdAiProperties properties) {
        this(properties, null);
    }

    public JdbcOpsProductDataSource(ProdAiProperties properties, String sql) {
        this.properties = properties;
        this.sql = sql == null || sql.isBlank() ? DEFAULT_SQL : sql.trim();
    }

    @Override
    public String sourceId() {
        return "jdbc";
    }

    /** 最近一次成功加载的行数（可观测：abox_row_count）。 */
    public long lastRowCount() {
        return lastRowCount;
    }

    @Override
    public Map<String, Object> loadRawGraph() {
        ProdAiProperties.Ontology cfg = properties.getOntology();
        if (cfg.getAboxJdbcUrl().isBlank()) {
            throw new IllegalStateException(
                    "prodai.ontology.abox-source=jdbc requires prodai.ontology.abox-jdbc-url. "
                            + "Refusing empty graph.");
        }
        List<Map<String, Object>> rows = queryShelfOfferings(cfg);
        lastRowCount = rows.size();
        Map<String, Object> graph = OpsProductGraphLoader.emptyGraph();
        graph.put("shelfOfferings", rows);
        log.info("[JdbcOpsProductDataSource] 已加载: jdbc 声明式查询命中 {} 行（上限 {}）",
                rows.size(), cfg.getAboxMaxRows());
        return graph;
    }

    /** 执行只读查询并映射为货架行列表；SQL/连接异常上抛（由守卫 LOAD 捕获回退）。 */
    private List<Map<String, Object>> queryShelfOfferings(ProdAiProperties.Ontology cfg) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = openConnection(cfg)) {
            conn.setReadOnly(true);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setMaxRows(cfg.getAboxMaxRows());
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    String[] camelNames = new String[columnCount + 1];
                    for (int i = 1; i <= columnCount; i++) {
                        camelNames[i] = snakeToCamel(meta.getColumnLabel(i));
                    }
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            if (value != null) {
                                row.put(camelNames[i], value);
                            }
                        }
                        ensureOfferingId(row);
                        rows.add(row);
                        if (rows.size() >= cfg.getAboxMaxRows()) {
                            log.warn("[JdbcOpsProductDataSource] 达到单次同步行数上限 {}，截断（可调 abox-max-rows）",
                                    cfg.getAboxMaxRows());
                            break;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ABox JDBC query failed: " + e.getMessage(), e);
        }
        return rows;
    }

    private Connection openConnection(ProdAiProperties.Ontology cfg) throws SQLException {
        try {
            Class.forName(cfg.getAboxJdbcDriver());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("ABox JDBC driver not found: " + cfg.getAboxJdbcDriver(), e);
        }
        return DriverManager.getConnection(cfg.getAboxJdbcUrl(), cfg.getAboxJdbcUsername(), cfg.getAboxJdbcPassword());
    }

    /** shelfOfferings 硬校验要求 offeringId 非空：兼容 id/offer_id 列名，缺省时跳过标记。 */
    private void ensureOfferingId(Map<String, Object> row) {
        if (row.containsKey("offeringId")) {
            return;
        }
        for (String alias : List.of("id", "offerId", "offer_id")) {
            Object v = row.get(alias) == null ? row.get(snakeToCamel(alias)) : row.get(alias);
            if (v != null) {
                row.put("offeringId", v);
                return;
            }
        }
    }

    /** snake_case / kebab-case 列名 → lowerCamelCase（JSON 传输契约统一 camelCase）。 */
    static String snakeToCamel(String column) {
        if (column == null || column.isBlank()) {
            return "";
        }
        String[] parts = column.trim().toLowerCase().split("[_\\-]");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)))
                        .append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
