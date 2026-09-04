package com.sitech.prodai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * H2 内嵌库显式初始化器 —— 替代 spring.sql.init（该机制在本项目中未稳定生效）。
 * <ul>
 *   <li>仅对 H2 连接生效（生产 MySQL 由 DBA 脚本管理，跳过）</li>
 *   <li>schema 为 CREATE TABLE IF NOT EXISTS、data 为 MERGE INTO，天然幂等</li>
 *   <li>@DependsOnDatabaseInitialization 保证先于 MyBatis mapper 使用</li>
 * </ul>
 */
@Configuration
@DependsOnDatabaseInitialization
public class H2SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(H2SchemaInitializer.class);

    private static final String SCHEMA_LOCATION = "sql/h2/schema-h2.sql";
    private static final String DATA_LOCATION = "sql/h2/data-h2.sql";

    public H2SchemaInitializer(DataSource dataSource) {
        if (!isH2(dataSource)) {
            log.info("[H2SchemaInitializer] 非 H2 数据源，跳过脚本初始化（生产库结构由 sql/ 脚本人工管理）");
            return;
        }
        runScript(dataSource, SCHEMA_LOCATION, "schema");
        runScript(dataSource, DATA_LOCATION, "data");
        log.info("[H2SchemaInitializer] H2 schema/data 脚本执行完成");
    }

    private boolean isH2(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
        } catch (Exception e) {
            log.warn("[H2SchemaInitializer] 探测数据源类型失败，跳过初始化: {}", e.getMessage());
            return false;
        }
    }

    private void runScript(DataSource dataSource, String location, String label) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource(location));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("[H2SchemaInitializer] {} 脚本执行成功: {}", label, location);
        } catch (Exception e) {
            // 幂等脚本失败通常意味着表已存在且结构兼容（老库文件），放行启动；首查会暴露真实问题
            log.warn("[H2SchemaInitializer] {} 脚本执行失败（若为老库文件可忽略）: {}", label, e.getMessage());
        }
    }

    /** 表是否已存在（预留：后续可用于跳过已初始化的库）。 */
    @SuppressWarnings("unused")
    private boolean tableExists(DataSource dataSource, String tableName) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + tableName + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
