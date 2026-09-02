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
 * 流程节点级执行记录 —— 固定流程引擎（P2）新增。
 * <p>
 * 审计与断点恢复依据：每个节点每次尝试一条记录，
 * 记录状态/入参出参/分支判定依据（branch_taken）/耗时。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_workflow_node_logs", autoResultMap = true)
public class WorkflowNodeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("execution_id")
    private String executionId;

    @TableField("node_id")
    private String nodeId;

    @TableField("node_type")
    private String nodeType;

    /** running / completed / skipped / failed */
    @TableField("status")
    private String status;

    @TableField("attempt")
    private Integer attempt = 1;

    @TableField(value = "input_data", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> inputData;

    @TableField(value = "output_data", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> outputData;

    @TableField("error_message")
    private String errorMessage;

    /** condition 命中的分支 id 及表达式原文 */
    @TableField("branch_taken")
    private String branchTaken;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
