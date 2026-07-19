package com.sitech.prodai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 配置 —— 启用审计（@CreatedDate / @LastModifiedDate 自动填充），
 * 对齐 Python SQLAlchemy 的 {@code server_default=func.now()} 与 {@code onupdate=func.now()}。
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.sitech.prodai.repository")
public class JpaConfig {
}
