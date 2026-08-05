package com.sitech.prodai.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 追踪记录 —— 对齐 Python {@code app/models/trace_model.py::Trace}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_traces", indexes = {
        @Index(name = "idx_trace_service_name", columnList = "service_name"),
        @Index(name = "idx_trace_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class Trace {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "service_name", length = 100)
    private String serviceName = "harness";

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "total_duration_ms")
    private Double totalDurationMs;

    @Column(name = "span_count")
    private Integer spanCount = 0;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}