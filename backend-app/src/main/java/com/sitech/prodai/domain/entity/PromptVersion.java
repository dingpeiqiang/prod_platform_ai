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
 * 提示词版本历史 —— 对齐 Python {@code app/models/prompt.py::PromptVersion}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_prompt_versions", autoResultMap = true)
public class PromptVersion {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("prompt_id")
    private Integer promptId;

    @TableField("version")
    private Integer version;

    @TableField("content")
    private String content;

    @TableField(value = "variables", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> variables;

    @TableField(value = "tools", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> tools;

    @TableField("change_note")
    private String changeNote;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
