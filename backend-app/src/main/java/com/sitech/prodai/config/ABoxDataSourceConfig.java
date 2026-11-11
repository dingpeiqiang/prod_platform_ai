package com.sitech.prodai.config;

import com.sitech.prodai.service.ops.JdbcOpsProductDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ABox 生产数据源装配（R5）：
 * <ul>
 *   <li>{@code prodai.ontology.data-source=jdbc}：装配 {@link JdbcOpsProductDataSource}
 *       （业务系统只读库直连），图谱从业务库加载。是否定时刷新由
 *       {@code prodai.ontology.abox-sync-enabled} 控制（见 ABoxSyncScheduler）。</li>
 *   <li>其它取值（classpath / http / empty）：不装配 JDBC 源，走 classpath/HTTP（dev/demo）。</li>
 * </ul>
 * 自定义 SQL 可经 {@code aboxJdbcQuerySupplier} 注入（如联调期指定视图），默认查在架商品表。
 */
@Configuration
public class ABoxDataSourceConfig {

    @Bean
    @ConditionalOnProperty(prefix = "prodai.ontology", name = "data-source", havingValue = "jdbc")
    public JdbcOpsProductDataSource jdbcOpsProductDataSource(
            ProdAiProperties properties,
            ObjectProvider<String> aboxJdbcQuerySupplier) {
        String customQuery = aboxJdbcQuerySupplier.getIfAvailable();
        return new JdbcOpsProductDataSource(properties, customQuery);
    }
}
