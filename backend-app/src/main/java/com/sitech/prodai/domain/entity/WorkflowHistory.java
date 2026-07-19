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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流版本历史表 —— 对齐 Python {@code app/models/workflow.py::WorkflowHistory}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workflow_history", indexes = {
        @Index(name = "idx_wh_workflow_id", columnList = "workflow_id"),
        @Index(name = "idx_wh_workflow_code", columnList = "workflow_code")
})
@EntityListeners(AuditingEntityListener.class)
public class WorkflowHistory {

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

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "workflow_name", nullable = false, length = 200)
    private String workflowName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "workflow_data", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> workflowData;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "tags", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> tags;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "change_note", columnDefinition = "TEXT")
    private String changeNote;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}