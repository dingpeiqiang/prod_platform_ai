package com.sitech.prodai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>
 * 开关：受 spring.sql.init.mode 控制，语义与 Spring 官方一致：
 * <ul>
 *   <li>never：完全跳过（生产/外部库默认，结构由 DBA 脚本人工执行）</li>
 *   <li>embedded：仅当数据源为内嵌库（H2）时执行，外部库（MySQL）跳过</li>
 *   <li>always：总是执行</li>
 * </ul>
 * 之所以自建初始化器而不依赖 Spring 的 spring.sql.init，是为了按数据源方言选择
 * H2/MySQL 脚本；但开关仍复用该配置项，避免两套口径。
 */
@Configuration
@DependsOnDatabaseInitialization
public class DatabaseSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    public DatabaseSchemaInitializer(DataSource dataSource,
                                     @Value("${spring.sql.init.mode:embedded}") String mode) {
        String product = productName(dataSource);
        if (!shouldInitialize(mode, product)) {
            log.info("[DatabaseSchemaInitializer] spring.sql.init.mode={}，数据源 {}，跳过脚本初始化（结构由 DBA 管理）",
                    mode, product);
            return;
        }
        if (product.contains("h2")) {
            init(dataSource, "sql/h2/schema-h2.sql", "sql/h2/data-h2.sql", "H2");
        } else if (product.contains("mysql")) {
            init(dataSource, "sql/mysql/schema-mysql.sql", "sql/mysql/data-mysql.sql", "MySQL");
        } else {
            log.info("[DatabaseSchemaInitializer] 未识别的数据源 {}，跳过脚本初始化（结构由 DBA 管理）", product);
        }
    }

    private boolean shouldInitialize(String mode, String product) {
        if ("never".equalsIgnoreCase(mode)) {
            return false;
        }
        if ("embedded".equalsIgnoreCase(mode)) {
            return product.contains("h2");
        }
        return "always".equalsIgnoreCase(mode);
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
