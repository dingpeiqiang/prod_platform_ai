package com.sitech.prodai.domain.entity;

import com.sitech.prodai.common.JsonConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.Map;

/**
 * MCP 工具定义 —— 对齐 Python {@code app/models/mcp_call_log.py::MCPToolDefinition}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_mcp_tool_definitions", indexes = {
        @Index(name = "idx_tool_name", columnList = "tool_name", unique = true),
        @Index(name = "idx_tool_code", columnList = "tool_code"),
        @Index(name = "idx_tool_category", columnList = "category"),
        @Index(name = "idx_tool_enabled", columnList = "is_enabled")
})
@EntityListeners(AuditingEntityListener.class)
public class McpToolDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tool_name", unique = true, nullable = false, length = 100)
    private String toolName;

    @Column(name = "tool_code", unique = true, length = 100)
    private String toolCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    @Column(name = "input_schema", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> inputSchema;

    @Column(name = "output_schema", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> outputSchema;

    @Column(name = "tool_type", length = 20)
    private String toolType = "url";

    @Column(name = "protocol", length = 10)
    private String protocol = "http";

    @Column(name = "request_method", length = 16)
    private String requestMethod = "POST";

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "auth_type", length = 20)
    private String authType = "none";

    @Column(name = "auth_info", columnDefinition = "TEXT")
    private String authInfo;

    @Column(name = "need_summary", nullable = false)
    private Boolean needSummary = false;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "config", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> config;

    @Column(name = "extra_metadata", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> extraMetadata;

    @Column(name = "total_calls", nullable = false)
    private Integer totalCalls = 0;

    @Column(name = "last_called_at")
    private LocalDateTime lastCalledAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
