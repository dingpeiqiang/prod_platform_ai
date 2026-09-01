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
 * MCP 工具聚合统计表（每小时/每天汇总）—— 对齐 Python {@code app/models/mcp_call_log.py::MCPToolStats}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_mcp_tool_stats")
public class McpToolStats {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("tool_name")
    private String toolName;

    @TableField("stat_date")
    private String statDate;

    @TableField("stat_hour")
    private Integer statHour;

    @TableField("total_calls")
    private Integer totalCalls = 0;

    @TableField("success_calls")
    private Integer successCalls = 0;

    @TableField("failed_calls")
    private Integer failedCalls = 0;

    @TableField("total_response_time_ms")
    private Double totalResponseTimeMs = 0.0;

    @TableField("avg_response_time_ms")
    private Double avgResponseTimeMs = 0.0;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
