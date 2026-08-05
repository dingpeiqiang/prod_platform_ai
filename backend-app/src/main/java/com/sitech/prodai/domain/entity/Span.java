package com.sitech.prodai.domain.entity;

import com.sitech.prodai.common.JsonConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 追踪 Span —— 对齐 Python {@code app/models/trace_model.py::Span}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_spans", indexes = {
        @Index(name = "idx_span_trace_id", columnList = "trace_id"),
        @Index(name = "idx_span_parent_id", columnList = "parent_span_id"),
        @Index(name = "idx_span_component", columnList = "component"),
        @Index(name = "idx_span_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class Span {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "trace_id", nullable = false, length = 36)
    private String traceId;

    @Column(name = "parent_span_id", length = 36)
    private String parentSpanId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "component", length = 100)
    private String component = "harness";

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_ms")
    private Double durationMs;

    @Convert(converter = SpanStatus.SpanStatusConverter.class)
    @Column(name = "status", length = 20)
    private SpanStatus status = SpanStatus.OK;

    @Column(name = "tags", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> tags;

    @Column(name = "logs", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> logs;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}