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
import java.util.List;
import java.util.Map;

/**
 * 工作流执行记录表 —— 对齐 Python {@code app/models/workflow.py::WorkflowExecution}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_workflow_executions", autoResultMap = true)
public class WorkflowExecution {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("workflow_id")
    private Integer workflowId;

    @TableField("workflow_code")
    private String workflowCode;

    @TableField("execution_id")
    private String executionId;

    @TableField("status")
    private String status = "pending";

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("duration_seconds")
    private Integer durationSeconds;

    @TableField(value = "input_data", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> inputData;

    @TableField(value = "output_data", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> outputData;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "execution_logs", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> executionLogs;

    @TableField("triggered_by")
    private String triggeredBy;

    @TableField("trigger_type")
    private String triggerType = "manual";

    @TableField("notes")
    private String notes;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
