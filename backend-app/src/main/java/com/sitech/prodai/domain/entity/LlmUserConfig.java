package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "LLM 模型配置（pd_ai_llm_user_configs）：推理平台接入参数与运行时调优项")
public class LlmUserConfig {

    @TableId(type = IdType.AUTO)
    @Schema(description = "配置 ID（新增时不传，更新时必传）")
    private Integer id;

    @TableField("user_identifier")
    @Schema(description = "所属用户标识")
    private String userIdentifier;

    @TableField("provider")
    @Schema(description = "模型提供方：openai / azure / custom / local", example = "custom")
    private String provider = ModelProvider.CUSTOM.getValue();

    @TableField("model")
    @Schema(description = "模型名称", example = "deepseek-chat")
    private String model;

    @TableField("api_key")
    @Schema(description = "API 密钥（部分本地服务可空）")
    private String apiKey;

    @TableField("base_url")
    @Schema(description = "服务基础地址", example = "https://api.deepseek.com/v1")
    private String baseUrl;

    @TableField("auth_type")
    @Schema(description = "认证方式：bearer / header")
    private String authType = "bearer";

    @TableField("auth_header")
    @Schema(description = "自定义认证头名称（auth_type=header 时生效）")
    private String authHeader;

    @TableField("api_format")
    @Schema(description = "API 协议格式：openai 兼容等")
    private String apiFormat = "openai";

    @TableField("is_full_url")
    @Schema(description = "base_url 是否已含 /v1 等完整路径：true 后端仅追加 /chat/completions")
    private Boolean isFullUrl = false;

    @TableField("temperature")
    @Schema(description = "采样温度（0~2，越大越随机）", example = "0.3")
    private Double temperature = 0.3;

    @TableField("max_tokens")
    @Schema(description = "单次生成最大 token 数", example = "2048")
    private Integer maxTokens = 2048;

    @TableField("thinking")
    @Schema(description = "是否开启深度思考模式（需模型支持）")
    private Boolean thinking = false;

    @TableField("stream_enabled")
    @Schema(description = "是否启用流式输出（SSE）")
    private Boolean streamEnabled = true;

    @TableField("max_input_tokens")
    @Schema(description = "输入上下文最大 token 数（超限自动截断）", example = "180000")
    private Integer maxInputTokens = 180000;

    @TableField("is_active")
    @Schema(description = "是否为当前全局生效配置（唯一激活）")
    private Boolean isActive = true;

    @TableField("config_name")
    @Schema(description = "配置显示名称", example = "DeepSeek 生产环境")
    private String configName;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @TableField("last_used_at")
    @Schema(description = "最近使用时间")
    private LocalDateTime lastUsedAt;
}
