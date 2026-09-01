package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sitech.prodai.common.JsonTypeHandlers;
import com.sitech.prodai.common.SpanStatusTypeHandler;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 追踪 Span —— 对齐 Python {@code app/models/trace_model.py::Span}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_spans", autoResultMap = true)
public class Span {

    /** String 主键（UUID），IdType.INPUT 手动赋值 */
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    @TableField("trace_id")
    private String traceId;

    @TableField("parent_span_id")
    private String parentSpanId;

    @TableField("name")
    private String name;

    @TableField("component")
    private String component = "harness";

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("duration_ms")
    private Double durationMs;

    @TableField(value = "status", typeHandler = SpanStatusTypeHandler.class)
    private SpanStatus status = SpanStatus.OK;

    @TableField(value = "tags", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> tags;

    @TableField(value = "logs", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> logs;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
