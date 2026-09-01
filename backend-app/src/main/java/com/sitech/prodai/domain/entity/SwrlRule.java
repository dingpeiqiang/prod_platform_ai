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

@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_swrl_rules")
public class SwrlRule {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("rule_id")
    private String ruleId;

    @TableField("rule_name")
    private String ruleName;

    @TableField("module")
    private String module;

    @TableField("description")
    private String description;

    @TableField("condition_expr")
    private String conditionExpr;

    @TableField("action_expr")
    private String actionExpr;

    @TableField("enabled")
    private Boolean enabled = true;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
