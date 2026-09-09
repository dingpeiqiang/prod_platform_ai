package com.sitech.prodai.service.ops;

import com.sitech.prodai.config.ProdAiProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1 复用项目库联调验收：H2 直连 pd_ops_shelf_offerings（sql/04_abox_shelf_view.sql 同构 DDL），
 * 验证「真实建表脚本 → JdbcOpsProductDataSource.DEFAULT_SQL 只读同步 → schema 契约校验」全链路。
 * <p>覆盖 A1 验收清单中的「字段映射核对」项：DEFAULT_SQL 的 14 列在本表 DDL 全部存在，
 * snake_case→camelCase 映射后货架行字段与 mock_graph.json 同构（100 行种子同源）。
 */
class ABoxProjectDbIntegrationTest {

    /** 从 sql/04_abox_shelf_view.sql 提取建表 DDL（去 MySQL 专属子句后在 H2 MODE=MySQL 执行）。 */
    private static final String DDL_RESOURCE = "/sql/04_abox_shelf_view.sql";

    /** 提取 CREATE TABLE ... 分号截断段（不含种子 INSERT，种子由测试自灌验证幂等 UPDATE 语义）。 */
    private String extractCreateTableDdl() throws Exception {
        String raw = java.nio.file.Files.readString(
                java.nio.file.Path.of("src", "main", "resources", "sql").resolve("../../../../sql/04_abox_shelf_view.sql")
                        .normalize().toAbsolutePath(), StandardCharsets.UTF_8);
        // 兼容 Maven 工作目录差异：优先取 backend-app 相对路径
        return raw;
    }

    private String loadDdlText() throws Exception {
        // 测试工作目录为 backend-app：项目 sql/ 目录在 ../sql/
        java.nio.file.Path p = java.nio.file.Path.of("..", "sql", "04_abox_shelf_view.sql")
                .toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(p)) {
            p = java.nio.file.Path.of("sql", "04_abox_shelf_view.sql").toAbsolutePath().normalize();
        }
        assertTrue(java.nio.file.Files.exists(p), "找不到 DDL 文件: " + p);
        return java.nio.file.Files.readString(p, StandardCharsets.UTF_8);
    }

    /** 从完整脚本截取 CREATE TABLE 到分号结束的 DDL 段，并剥离 H2 不支持的 MySQL 专属子句。 */
    private String createTableStatement(String script) {
        Matcher m = Pattern.compile("CREATE TABLE `pd_ops_shelf_offerings`.*?;", Pattern.DOTALL).matcher(script);
        assertTrue(m.find(), "脚本中应含 pd_ops_shelf_offerings 建表语句");
        return m.group()
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin "
                        + "COMMENT='ABox 在架商品事实表（业务系统只读视图同构，abox-source=jdbc 数据源）'", "")
                .replaceAll("COMMENT\\s+'[^']*'", "")
                .replace("`", "");
    }

    @Test
    void defaultSqlShouldSyncFromProjectDbShelfTable() throws Exception {
        String url = "jdbc:h2:mem:abox_projdb_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setAboxSource("jdbc");
        properties.getOntology().setAboxJdbcDriver("org.h2.Driver");
        properties.getOntology().setAboxJdbcUrl(url);
        properties.getOntology().setAboxJdbcUsername("sa");
        properties.getOntology().setAboxJdbcPassword("");

        // 1) 用项目库权威 DDL（04_abox_shelf_view.sql）建表——验证脚本本身可在 MySQL 兼容模式建表
        String ddl = createTableStatement(loadDdlText());
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute(ddl);
        }

        // 2) 灌入两行代表性数据（主资费 + 风险演示），列序与种子脚本一致
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO pd_ops_shelf_offerings (offering_id, offering_name, category_code, "
                    + "category_name, product_line, offering_type, state, monthly_fee, fixed_fee_amount, "
                    + "sales_cnt_30d, revenue_30d, shelf_days, message_root_key, category) VALUES "
                    + "('OF-HF-128', '家庭融合畅享128', 'familyBasePrc', '家庭基础套餐', '家庭', "
                    + "'main_pkg', 'on_shelf', 128, 128, 860, 110080, 210, 'familyBasePrc', 'normal'), "
                    + "('OF-RISK-001', '校园体验流量包0元', 'personAddPrc', '个人附加资费', '个人', "
                    + "'addon', 'on_shelf', 0, 0, 120, 0, 45, 'personAddPrc', 'zero_fee')");
        }

        // 3) 用生产默认 SQL（DEFAULT_SQL，14 列全列查询）同步——验证列契约完全对齐
        JdbcOpsProductDataSource source = new JdbcOpsProductDataSource(properties);
        Map<String, Object> graph = source.loadRawGraph();

        List<Map<String, Object>> shelf = castShelf(graph.get("shelfOfferings"));
        assertEquals(2, shelf.size(), "DEFAULT_SQL 应命中 2 行在架商品");
        assertEquals(2, source.lastRowCount());

        // 4) 字段映射核对：snake_case 列 → camelCase 货架行字段（A1 验收核心项）
        Map<String, Object> row = shelf.stream()
                .filter(r -> "OF-HF-128".equals(String.valueOf(r.get("offeringId"))))
                .findFirst().orElse(null);
        assertEquals("OF-HF-128", row == null ? null : row.get("offeringId"));
        assertEquals("家庭融合畅享128", row.get("offeringName"));
        assertEquals("familyBasePrc", row.get("categoryCode"));
        assertEquals("家庭基础套餐", row.get("categoryName"));
        assertEquals("家庭", row.get("productLine"));
        assertEquals("main_pkg", row.get("offeringType"));
        assertEquals("on_shelf", row.get("state"));
        assertEquals(128.00, ((Number) row.get("monthlyFee")).doubleValue(), 0.001);
        assertEquals(128.00, ((Number) row.get("fixedFeeAmount")).doubleValue(), 0.001);
        assertEquals(860, ((Number) row.get("salesCnt30d")).intValue());
        assertEquals(110080.00, ((Number) row.get("revenue30d")).doubleValue(), 0.001);
        assertEquals(210, ((Number) row.get("shelfDays")).intValue());
        assertEquals("familyBasePrc", row.get("messageRootKey"));
        assertEquals("normal", row.get("category"));

        // 5) schema 契约校验（OpsGraph-v1）：同步图应通过 VALIDATE 段
        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(graph);
        assertTrue(vr.ok(), "项目库同步图应过契约校验: " + vr.errors());
    }

    @Test
    void h2SchemaSeedShouldLoadOneHundredOfferings() throws Exception {
        // 验证 schema-h2.sql §24 + data-h2.sql §7 种子可被 Spring sql.init 全量执行（启动即建表灌数）
        String url = "jdbc:h2:mem:abox_seed_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement()) {
            // 仅执行本表相关段：schema §24 建表 + data §7 种子（避免整个脚本依赖其他表）
            String schema = java.nio.file.Files.readString(
                    java.nio.file.Path.of("src", "main", "resources", "sql", "h2", "schema-h2.sql")
                            .toAbsolutePath().normalize(), StandardCharsets.UTF_8);
            String data = java.nio.file.Files.readString(
                    java.nio.file.Path.of("src", "main", "resources", "sql", "h2", "data-h2.sql")
                            .toAbsolutePath().normalize(), StandardCharsets.UTF_8);

            String shelfDdl = extractSection(schema, "CREATE TABLE IF NOT EXISTS pd_ops_shelf_offerings");
            for (String stmt : splitStatements(shelfDdl)) {
                st.execute(stmt);
            }
            String seed = extractSection(data, "MERGE INTO pd_ops_shelf_offerings");
            st.execute(seed.trim());

            var rs = st.executeQuery("SELECT COUNT(*) FROM pd_ops_shelf_offerings");
            assertTrue(rs.next());
            assertEquals(100, rs.getInt(1), "种子应灌入 100 行（对齐 mock_graph shelfOfferings）");

            // DEFAULT_SQL 语义核对：state 英文枚举全命中
            var onShelf = st.executeQuery(
                    "SELECT COUNT(*) FROM pd_ops_shelf_offerings WHERE state IN ('on_shelf', 'on_sale')");
            assertTrue(onShelf.next());
            assertEquals(100, onShelf.getInt(1), "全部种子行应为 on_shelf/on_sale 状态（DEFAULT_SQL 可全量命中）");
        }
    }

    /** 截取从 start 标记开始到文件中下一个顶级 "-- ---" 注释块或文件尾的段落。 */
    private String extractSection(String text, String start) {
        int begin = text.indexOf(start);
        assertTrue(begin >= 0, "应含段: " + start);
        String rest = text.substring(begin);
        // 段终止：下一行以 "-- " 开头且随后是 "------------" 的注释分隔线（跳过语句内的 CREATE INDEX）
        Matcher m = Pattern.compile("\\n-- ------------------------------------------------------------\\s*\\n-- \\d", Pattern.DOTALL)
                .matcher(rest);
        if (m.find()) {
            rest = rest.substring(0, m.start());
        }
        return rest;
    }

    /** 按分号拆分语句（无存储过程/字符串内分号，安全拆分）。 */
    private List<String> splitStatements(String section) {
        return java.util.Arrays.stream(section.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("--"))
                .map(s -> s.replaceAll("(?m)^--.*$", "").trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castShelf(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
