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
 * 工作流主表 —— 对齐 Python {@code app/models/workflow.py::Workflow}。
 * 历史与执行记录由 WorkflowHistoryMapper / WorkflowExecutionMapper 显式维护。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_workflows", autoResultMap = true)
public class Workflow {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("workflow_code")
    private String workflowCode;

    @TableField("workflow_name")
    private String workflowName;

    @TableField("description")
    private String description;

    @TableField("category")
    private String category = "general";

    @TableField(value = "tags", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> tags;

    @TableField("priority")
    private Integer priority = 10;

    @TableField("is_active")
    private Boolean isActive = true;

    @TableField("is_in_library")
    private Boolean isInLibrary = false;

    /** 完整工作流配置（节点、边等） */
    @TableField(value = "workflow_data", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> workflowData;

    @TableField("version")
    private Integer version = 1;

    @TableField("execution_count")
    private Integer executionCount = 0;

    @TableField("last_execution_at")
    private LocalDateTime lastExecutionAt;

    @TableField("last_execution_status")
    private String lastExecutionStatus;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
