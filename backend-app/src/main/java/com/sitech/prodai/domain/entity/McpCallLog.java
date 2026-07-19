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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * MCP 工具调用日志 —— 对齐 Python {@code app/models/mcp_call_log.py::MCPCallLog}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mcp_call_logs", indexes = {
        @Index(name = "idx_cl_tool_name", columnList = "tool_name"),
        @Index(name = "idx_cl_tool_category", columnList = "tool_category"),
        @Index(name = "idx_tool_timestamp", columnList = "tool_name, timestamp"),
        @Index(name = "idx_timestamp_desc", columnList = "timestamp")
    })
@EntityListeners(AuditingEntityListener.class)
public class McpCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "tool_category", length = 50)
    private String toolCategory;

    @Column(name = "success", nullable = false)
    private Boolean success = false;

    @Column(name = "execution_time_ms")
    private Double executionTimeMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "request_args", columnDefinition = "TEXT")
    private String requestArgs;

    @Column(name = "response_data", columnDefinition = "TEXT")
    private String responseData;
}
