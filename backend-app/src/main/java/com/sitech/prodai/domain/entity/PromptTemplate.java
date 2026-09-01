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
 * 预设模板库 —— 对齐 Python {@code app/models/prompt.py::PromptTemplate}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_prompt_templates", autoResultMap = true)
public class PromptTemplate {

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

    @TableField(value = "variables", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> variables;

    @TableField(value = "tools", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> tools;

    @TableField(value = "tags", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> tags;

    @TableField("is_builtin")
    private Boolean isBuiltin = false;

    @TableField("is_active")
    private Boolean isActive = true;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
