package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 追踪记录 —— 对齐 Python {@code app/models/trace_model.py::Trace}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_traces")
public class Trace {

    /** String 主键（UUID），IdType.INPUT 手动赋值 */
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    @TableField("service_name")
    private String serviceName = "harness";

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("total_duration_ms")
    private Double totalDurationMs;

    @TableField("span_count")
    private Integer spanCount = 0;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
