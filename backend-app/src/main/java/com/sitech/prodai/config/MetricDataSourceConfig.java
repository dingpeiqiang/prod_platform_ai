package com.sitech.prodai.config;

import com.sitech.prodai.service.metric.JdbcMetricDataSource;
import com.sitech.prodai.service.metric.MockMetricDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 指标数据源装配（指标域 P0）：
 * <ul>
 *   <li>{@code prodai.metric.source=mock}（默认）：装配 {@link MockMetricDataSource}
 *       （由 opsGraph 事实快照确定性生成演示时序，dev/demo 不依赖 DB）。</li>
 *   <li>{@code prodai.metric.source=jdbc}：装配 {@link JdbcMetricDataSource}
 *       （只读查询指标宽表 dwd_prod_metric_daily，T+1 ETL 写入；URL 经 prodai.metric.jdbc-url 注入）。</li>
 * </ul>
 * 两源均始终声明（@ConditionalOnProperty 互斥装配），MetricService 按 source 路由，
 * 切换数据源零代码改动（与 OpsProductGraphLoader 同思路）。
 */
@Configuration
public class MetricDataSourceConfig {

    @Bean
    @ConditionalOnProperty(prefix = "prodai.metric", name = "source", havingValue = "jdbc")
    public JdbcMetricDataSource jdbcMetricDataSource(ProdAiProperties properties) {
        return new JdbcMetricDataSource(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "prodai.metric", name = "source",
            havingValue = "mock", matchIfMissing = true)
    public MockMetricDataSource mockMetricDataSource(ProdAiProperties properties) {
        return new MockMetricDataSource(properties);
    }
}
