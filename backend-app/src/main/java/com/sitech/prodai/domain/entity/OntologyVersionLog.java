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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本体资产版本库·动作日志（P1-5 方案 B，设计方案 §13.1）。
 * P3-5 "泛化审计一张表"（方案 §13.6）：domain 区分审计域（version / risk / config / batch）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_ontology_version_log", autoResultMap = true)
public class OntologyVersionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 外键：pd_ai_ontology_version.id（仅版本键控审计非空；非版本审计可空） */
    @TableField("version_id")
    private Long versionId;

    /** 审计域：version / risk / config / batch */
    @TableField("domain")
    private String domain = "version";

    /** 配置链路 trace_id（仅 domain=config 有值；其余可空） */
    @TableField("trace_id")
    private String traceId;

    /** 动作：publish / rollback / deprecate / reload / override / config_step / batch_audit */
    @TableField("action")
    private String action;

    @TableField("operator")
    private String operator;

    /** 动作明细 JSON（diff 报告、失败原因等） */
    @TableField(value = "detail", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> detail = new LinkedHashMap<>();

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
