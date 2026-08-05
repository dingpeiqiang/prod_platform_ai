package com.sitech.prodai.domain.entity;

import com.sitech.prodai.common.JsonConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行记录表 —— 对齐 Python {@code app/models/workflow.py::WorkflowExecution}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_workflow_executions", indexes = {
        @Index(name = "idx_we_workflow_id", columnList = "workflow_id"),
        @Index(name = "idx_we_workflow_code", columnList = "workflow_code"),
        @Index(name = "idx_we_execution_id", columnList = "execution_id", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class WorkflowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "workflow_id", nullable = false)
    private Integer workflowId;

    @ManyToOne
    @JoinColumn(name = "workflow_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Workflow workflow;

    @Column(name = "workflow_code", nullable = false, length = 100)
    private String workflowCode;

    @Column(name = "execution_id", unique = true, nullable = false, length = 100)
    private String executionId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "input_data", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> inputData;

    @Column(name = "output_data", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> outputData;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "execution_logs", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> executionLogs;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "trigger_type", length = 20)
    private String triggerType = "manual";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}