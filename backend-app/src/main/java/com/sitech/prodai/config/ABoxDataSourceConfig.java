package com.sitech.prodai.config;

import com.sitech.prodai.service.ops.JdbcOpsProductDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ABox 生产数据源装配（R5）：
 * <ul>
 *   <li>{@code prodai.ontology.abox-source=mock}（默认）：不装配 JDBC 源，走 classpath/HTTP（dev/demo）。</li>
 *   <li>{@code prodai.ontology.abox-source=jdbc}：装配 {@link JdbcOpsProductDataSource}（业务系统只读库直连），
 *       并由 ABoxSyncScheduler 首次全量 + 定时刷新（失败回退 last-known-good）。</li>
 * </ul>
 * 自定义 SQL 可经 {@code aboxJdbcQuerySupplier} 注入（如联调期指定视图），默认查在架商品表。
 */
@Configuration
public class ABoxDataSourceConfig {

    @Bean
    @ConditionalOnProperty(prefix = "prodai.ontology", name = "abox-source", havingValue = "jdbc")
    public JdbcOpsProductDataSource jdbcOpsProductDataSource(
            ProdAiProperties properties,
            ObjectProvider<String> aboxJdbcQuerySupplier) {
        String customQuery = aboxJdbcQuerySupplier.getIfAvailable();
        return new JdbcOpsProductDataSource(properties, customQuery);
    }
}
