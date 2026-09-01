package com.sitech.prodai.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
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
