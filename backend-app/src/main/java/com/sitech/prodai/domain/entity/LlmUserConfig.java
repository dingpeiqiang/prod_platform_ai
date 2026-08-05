package com.sitech.prodai.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 用户 LLM 配置 —— 对齐 Python {@code app/models/llm_user_config.py::LLMUserConfig}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_llm_user_configs", indexes = {
        @Index(name = "idx_llm_user_identifier", columnList = "user_identifier")
})
@EntityListeners(AuditingEntityListener.class)
public class LlmUserConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_identifier", nullable = false, length = 100)
    private String userIdentifier;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider = ModelProvider.CUSTOM.getValue();

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    @Column(name = "base_url", columnDefinition = "TEXT")
    private String baseUrl;

    @Column(name = "auth_type", nullable = false, length = 20)
    private String authType = "bearer";

    @Column(name = "auth_header", length = 50)
    private String authHeader;

    @Column(name = "api_format", nullable = false, length = 50)
    private String apiFormat = "openai";

    @Column(name = "is_full_url", nullable = false)
    private Boolean isFullUrl = false;

    @Column(name = "temperature", nullable = false)
    private Double temperature = 0.3;

    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens = 2048;

    @Column(name = "thinking", nullable = false)
    private Boolean thinking = false;

    @Column(name = "max_input_tokens")
    private Integer maxInputTokens = 180000;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "config_name", length = 100)
    private String configName;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
