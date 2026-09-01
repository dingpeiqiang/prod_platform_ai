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
 * 工作流版本历史表 —— 对齐 Python {@code app/models/workflow.py::WorkflowHistory}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_workflow_history", autoResultMap = true)
public class WorkflowHistory {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("workflow_id")
    private Integer workflowId;

    @TableField("workflow_code")
    private String workflowCode;

    @TableField("version")
    private Integer version;

    @TableField("workflow_name")
    private String workflowName;

    @TableField("description")
    private String description;

    @TableField(value = "workflow_data", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> workflowData;

    @TableField("category")
    private String category;

    @TableField(value = "tags", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> tags;

    @TableField("priority")
    private Integer priority;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("change_note")
    private String changeNote;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
