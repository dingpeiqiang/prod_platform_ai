package com.sitech.prodai.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * MyBatis Plus 配置 —— 替代原 JpaConfig。
 * <ul>
 *   <li>分页插件：替代 Spring Data Pageable</li>
 *   <li>MetaObjectHandler：替代 @EnableJpaAuditing（@CreatedDate / @LastModifiedDate 自动填充）</li>
 *   <li>JSON 字段序列化：实体上通过 @TableField(typeHandler = ...) 指定</li>
 * </ul>
 */
@Configuration
@MapperScan("com.sitech.prodai.mapper")
public class MybatisPlusConfig {

    /**
     * 分页方言按实际数据源自动选择：
     * H2（MODE=MySQL）用 H2 方言；其余（MySQL/GoldenDB）用 MYSQL 方言。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataSource dataSource) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(resolveDbType(dataSource)));
        return interceptor;
    }

    private DbType resolveDbType(DataSource dataSource) {
        try {
            DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
            String productName = metaData.getDatabaseProductName().toLowerCase();
            if (productName.contains("h2")) {
                return DbType.H2;
            }
        } catch (SQLException ignored) {
            // 探测失败时回退 MySQL 方言（H2 MySQL 模式下 LIMIT 语法一致）
        }
        return DbType.MYSQL;
    }

    /**
     * 审计字段自动填充（对齐原 JPA Auditing）：
     * 实体字段标注 @TableField(fill = FieldFill.INSERT) → createdAt，
     * @TableField(fill = FieldFill.INSERT_UPDATE) → updatedAt。
     */
    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
