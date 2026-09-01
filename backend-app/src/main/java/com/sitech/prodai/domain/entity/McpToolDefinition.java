package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sitech.prodai.common.JsonTypeHandlers;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MCP 工具定义 —— 对齐 Python {@code app/models/mcp_call_log.py::MCPToolDefinition}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_mcp_tool_definitions", autoResultMap = true)
public class McpToolDefinition {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("tool_name")
    private String toolName;

    @TableField("tool_code")
    private String toolCode;

    @TableField("description")
    private String description;

    @TableField("category")
    private String category;

    @TableField("is_enabled")
    private Boolean isEnabled = true;

    @TableField("is_public")
    private Boolean isPublic = true;

    @TableField(value = "input_schema", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> inputSchema;

    @TableField(value = "output_schema", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> outputSchema;

    @TableField("tool_type")
    private String toolType = "url";

    @TableField("protocol")
    private String protocol = "http";

    @TableField("request_method")
    private String requestMethod = "POST";

    @TableField("url")
    private String url;

    @TableField("auth_type")
    private String authType = "none";

    @TableField("auth_info")
    private String authInfo;

    @TableField("need_summary")
    private Boolean needSummary = false;

    @TableField("prompt")
    private String prompt;

    @TableField(value = "config", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> config;

    @TableField(value = "extra_metadata", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> extraMetadata;

    @TableField("total_calls")
    private Integer totalCalls = 0;

    @TableField("last_called_at")
    private LocalDateTime lastCalledAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
