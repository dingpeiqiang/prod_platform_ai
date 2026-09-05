package com.sitech.prodai.service.ops;

import com.sitech.prodai.config.ProdAiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5：JdbcOpsProductDataSource 单元测试。
 * 纯 Mockito 无 H2 容器：覆盖 sourceId/缺 URL 拒绝/snake→camel 映射/emptyGraph 同构 + schema 校验链。
 */
class JdbcOpsProductDataSourceTest {

    private ProdAiProperties jdbcProperties() {
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setAboxSource("jdbc");
        properties.getOntology().setAboxJdbcUrl("jdbc:h2:mem:abox_test;MODE=MySQL");
        properties.getOntology().setAboxJdbcUsername("sa");
        properties.getOntology().setAboxJdbcPassword("");
        return properties;
    }

    @Test
    void sourceIdShouldBeJdbc() {
        JdbcOpsProductDataSource source = new JdbcOpsProductDataSource(jdbcProperties());
        assertEquals("jdbc", source.sourceId());
    }

    @Test
    void shouldRefuseEmptyJdbcUrl() {
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setAboxSource("jdbc");
        JdbcOpsProductDataSource source = new JdbcOpsProductDataSource(properties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, source::loadRawGraph);
        assertTrue(ex.getMessage().contains("abox-jdbc-url"), "应拒绝未配置 jdbc-url: " + ex.getMessage());
    }

    @Test
    void snakeToCamelShouldMapColumnNames() {
        assertEquals("offeringId", JdbcOpsProductDataSource.snakeToCamel("offering_id"));
        assertEquals("offeringName", JdbcOpsProductDataSource.snakeToCamel("OFFERING_NAME"));
        assertEquals("monthlyFee", JdbcOpsProductDataSource.snakeToCamel("monthly-fee"));
        assertEquals("state", JdbcOpsProductDataSource.snakeToCamel("state"));
        assertEquals("", JdbcOpsProductDataSource.snakeToCamel(""));
        assertEquals("", JdbcOpsProductDataSource.snakeToCamel(null));
    }

    @Test
    void loadRawGraphShouldReturnEmptyShelfOnBadDriver() {
        ProdAiProperties properties = jdbcProperties();
        properties.getOntology().setAboxJdbcDriver("com.not.exist.FakeDriver");
        JdbcOpsProductDataSource source = new JdbcOpsProductDataSource(properties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, source::loadRawGraph);
        assertTrue(ex.getMessage().contains("driver not found"), "驱动缺失应上抛由守卫 LOAD 回退: " + ex.getMessage());
    }

    @Test
    void loadRawGraphShouldQueryViaH2() throws Exception {
        // H2 内存库直接当业务系统只读库，验证 SQL→Graph 全链路映射
        ProdAiProperties properties = jdbcProperties();
        properties.getOntology().setAboxJdbcDriver("org.h2.Driver");
        properties.getOntology().setAboxJdbcUrl("jdbc:h2:mem:abox_it_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        String sql = "SELECT offering_id, offering_name, category, state, monthly_fee FROM t_shelf";
        JdbcOpsProductDataSource source = new JdbcOpsProductDataSource(properties, sql);

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                properties.getOntology().getAboxJdbcUrl(), "sa", "");
             java.sql.Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t_shelf (offering_id VARCHAR(64), offering_name VARCHAR(200), "
                    + "category VARCHAR(50), state VARCHAR(20), monthly_fee DECIMAL(10,2))");
            st.execute("INSERT INTO t_shelf VALUES ('OF-001', '家庭融合主套餐', 'family', 'on_shelf', 128.00)");
            st.execute("INSERT INTO t_shelf VALUES ('OF-002', '校园副卡', 'campus', 'on_shelf', 19.00)");
        }

        Map<String, Object> graph = source.loadRawGraph();

        // 与 mock_graph 同构：六个契约顶层键齐备
        for (String key : OpsGraphSchemaValidator.REQUIRED_TOP_KEYS) {
            assertTrue(graph.containsKey(key), "缺契约顶层键: " + key);
        }
        List<Map<String, Object>> shelf = castShelf(graph.get("shelfOfferings"));
        assertEquals(2, shelf.size());
        assertEquals("OF-001", shelf.get(0).get("offeringId"));
        assertEquals("家庭融合主套餐", shelf.get(0).get("offeringName"));
        assertEquals(128.00, ((Number) shelf.get(0).get("monthlyFee")).doubleValue(), 0.001);
        assertEquals(2, source.lastRowCount());

        // 经 schema 校验应通过（JDBC 源 + validator 组合语义与 http 源一致）
        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(graph);
        assertTrue(vr.ok(), "JDBC 加载图应过契约校验: " + vr.errors());
    }

    @Test
    void maxRowsGuardShouldTruncate() throws Exception {
        ProdAiProperties properties = jdbcProperties();
        properties.getOntology().setAboxJdbcDriver("org.h2.Driver");
        properties.getOntology().setAboxJdbcUrl("jdbc:h2:mem:abox_trunc_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        properties.getOntology().setAboxMaxRows(5);
        JdbcOpsProductDataSource source = new JdbcOpsProductDataSource(properties,
                "SELECT offering_id, state FROM t_shelf");

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                properties.getOntology().getAboxJdbcUrl(), "sa", "");
             java.sql.Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t_shelf (offering_id VARCHAR(64), state VARCHAR(20))");
            for (int i = 1; i <= 20; i++) {
                st.execute("INSERT INTO t_shelf VALUES ('OF-" + i + "', 'on_shelf')");
            }
        }

        Map<String, Object> graph = source.loadRawGraph();
        assertEquals(5, castShelf(graph.get("shelfOfferings")).size(), "应按 abox-max-rows 截断");
    }

    @Test
    void emptyResultSetShouldStillPassSchemaValidation() throws Exception {
        ProdAiProperties properties = jdbcProperties();
        properties.getOntology().setAboxJdbcDriver("org.h2.Driver");
        properties.getOntology().setAboxJdbcUrl("jdbc:h2:mem:abox_empty_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcOpsProductDataSource source = new JdbcOpsProductDataSource(properties,
                "SELECT offering_id, offering_name, state FROM t_shelf");

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                properties.getOntology().getAboxJdbcUrl(), "sa", "");
             java.sql.Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t_shelf (offering_id VARCHAR(64), offering_name VARCHAR(200), state VARCHAR(20))");
        }

        Map<String, Object> graph = source.loadRawGraph();
        assertTrue(castShelf(graph.get("shelfOfferings")).isEmpty());
        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(graph);
        // 空货架是 warning 而非 error（业务库暂无在架商品属正常态）
        assertTrue(vr.ok(), "空结果集应过契约校验（软警告）: " + vr.errors());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castShelf(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
