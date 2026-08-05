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
 * MCP 工具聚合统计表（每小时/每天汇总）—— 对齐 Python {@code app/models/mcp_call_log.py::MCPToolStats}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_mcp_tool_stats", indexes = {
        @Index(name = "idx_tool_date_hour", columnList = "tool_name, stat_date, stat_hour", unique = true),
        @Index(name = "idx_ts_tool_name", columnList = "tool_name"),
        @Index(name = "idx_ts_stat_date", columnList = "stat_date")
})
@EntityListeners(AuditingEntityListener.class)
public class McpToolStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "stat_date", nullable = false, length = 20)
    private String statDate;

    @Column(name = "stat_hour")
    private Integer statHour;

    @Column(name = "total_calls", nullable = false)
    private Integer totalCalls = 0;

    @Column(name = "success_calls", nullable = false)
    private Integer successCalls = 0;

    @Column(name = "failed_calls", nullable = false)
    private Integer failedCalls = 0;

    @Column(name = "total_response_time_ms", nullable = false)
    private Double totalResponseTimeMs = 0.0;

    @Column(name = "avg_response_time_ms", nullable = false)
    private Double avgResponseTimeMs = 0.0;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}