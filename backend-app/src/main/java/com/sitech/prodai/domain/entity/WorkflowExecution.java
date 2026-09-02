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

    /** 运行上下文（各节点输出合并，恢复执行的数据源）——流程引擎（P2）新增。 */
    @TableField(value = "context_data", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> contextData;

    /** 当前推进到的节点——流程引擎（P2）新增。 */
    @TableField("current_node_id")
    private String currentNodeId;

    /** 人工节点恢复令牌（一次有效）——流程引擎（P2）新增。 */
    @TableField("resume_token")
    private String resumeToken;

    /** 乐观锁版本——流程引擎（P2）新增。 */
    @TableField("status_version")
    private Integer statusVersion = 0;

    /** 执行时锁定的流程定义版本（回滚安全）——流程引擎（P2）新增。 */
    @TableField("workflow_version")
    private Integer workflowVersion;

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
