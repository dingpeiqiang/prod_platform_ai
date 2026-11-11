package com.sitech.prodai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 数据库脚本初始化器 —— 按数据源类型选择对应方言的 DDL / 种子脚本。
 * <ul>
 *   <li>H2：sql/h2/schema-h2.sql + sql/h2/data-h2.sql（MERGE INTO 幂等）</li>
 *   <li>MySQL / GoldenDB：sql/mysql/schema-mysql.sql + sql/mysql/data-mysql.sql
 *       （需 distributed by 方言，INSERT IGNORE 幂等）</li>
 *   <li>@DependsOnDatabaseInitialization 保证先于 MyBatis mapper 使用</li>
 * </ul>
 */
@Configuration
@DependsOnDatabaseInitialization
public class DatabaseSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    public DatabaseSchemaInitializer(DataSource dataSource) {
        String product = productName(dataSource);
        if (product.contains("h2")) {
            init(dataSource, "sql/h2/schema-h2.sql", "sql/h2/data-h2.sql", "H2");
        } else if (product.contains("mysql")) {
            init(dataSource, "sql/mysql/schema-mysql.sql", "sql/mysql/data-mysql.sql", "MySQL");
        } else {
            log.info("[DatabaseSchemaInitializer] 未识别的数据源 {}，跳过脚本初始化（结构由 DBA 管理）", product);
        }
    }

    private String productName(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (Exception e) {
            log.warn("[DatabaseSchemaInitializer] 探测数据源类型失败，跳过初始化: {}", e.getMessage());
            return "";
        }
    }

    private void init(DataSource dataSource, String schema, String data, String label) {
        runScript(dataSource, schema, label + " schema");
        runScript(dataSource, data, label + " data");
    }

    private void runScript(DataSource dataSource, String location, String label) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource(location));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("[DatabaseSchemaInitializer] {} 脚本执行成功: {}", label, location);
        } catch (Exception e) {
            // 幂等脚本失败通常意味着表已存在且结构兼容，放行启动；首查会暴露真实问题
            log.warn("[DatabaseSchemaInitializer] {} 脚本执行失败（若表已存在可忽略）: {}", label, e.getMessage());
        }
    }
}
