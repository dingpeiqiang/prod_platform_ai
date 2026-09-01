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

/**
 * 提示词主表 —— 对齐 Python {@code app/models/prompt.py::Prompt}。
 * 版本行由 PromptVersionMapper 显式维护。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_prompts", autoResultMap = true)
public class Prompt {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("category")
    private String category = "general";

    @TableField("content")
    private String content;

    /** 模板变量定义：[{"name","description","default"}] */
    @TableField(value = "variables", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> variables;

    /** 可用工具：[{"code","name","description"}] */
    @TableField(value = "tools", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> tools;

    @TableField("is_template")
    private Boolean isTemplate = false;

    @TableField("version")
    private Integer version = 1;

    @TableField("is_active")
    private Boolean isActive = true;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
