package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户 LLM 配置 —— 对齐 Python {@code app/models/llm_user_config.py::LLMUserConfig}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_llm_user_configs")
public class LlmUserConfig {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("user_identifier")
    private String userIdentifier;

    @TableField("provider")
    private String provider = ModelProvider.CUSTOM.getValue();

    @TableField("model")
    private String model;

    @TableField("api_key")
    private String apiKey;

    @TableField("base_url")
    private String baseUrl;

    @TableField("auth_type")
    private String authType = "bearer";

    @TableField("auth_header")
    private String authHeader;

    @TableField("api_format")
    private String apiFormat = "openai";

    @TableField("is_full_url")
    private Boolean isFullUrl = false;

    @TableField("temperature")
    private Double temperature = 0.3;

    @TableField("max_tokens")
    private Integer maxTokens = 2048;

    @TableField("thinking")
    private Boolean thinking = false;

    @TableField("stream_enabled")
    private Boolean streamEnabled = true;

    @TableField("max_input_tokens")
    private Integer maxInputTokens = 180000;

    @TableField("is_active")
    private Boolean isActive = true;

    @TableField("config_name")
    private String configName;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;
}
