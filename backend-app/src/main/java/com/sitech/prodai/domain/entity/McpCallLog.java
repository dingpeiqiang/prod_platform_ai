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
 * MCP 工具调用日志 —— 对齐 Python {@code app/models/mcp_call_log.py::MCPCallLog}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_mcp_call_logs")
public class McpCallLog {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("tool_name")
    private String toolName;

    @TableField("tool_category")
    private String toolCategory;

    @TableField("success")
    private Boolean success = false;

    @TableField("execution_time_ms")
    private Double executionTimeMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "timestamp", fill = FieldFill.INSERT)
    private LocalDateTime timestamp;

    @TableField("request_args")
    private String requestArgs;

    @TableField("response_data")
    private String responseData;
}
