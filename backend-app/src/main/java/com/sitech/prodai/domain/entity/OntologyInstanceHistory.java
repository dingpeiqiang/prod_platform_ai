package com.sitech.prodai.domain.entity;

import jakarta.persistence.Column;
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

/**
 * 本体实例历史 —— 对齐 Python {@code app/models/ontology_instance.py::OntologyInstanceHistory}。
 *
 * <p>记录每个字段的变更历史，用于推荐系统、审计追踪、数据分析。
 * 字段名 form_instance_id 保持与 Python 兼容。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ontology_instance_history", indexes = {
        @Index(name = "idx_oih_form_instance_id", columnList = "form_instance_id"),
        @Index(name = "idx_oih_field_code", columnList = "field_code"),
        @Index(name = "idx_oih_user_id", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class OntologyInstanceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "form_instance_id", nullable = false)
    private Integer formInstanceId;

    @ManyToOne
    @JoinColumn(name = "form_instance_id", referencedColumnName = "id", insertable = false, updatable = false)
    private OntologyInstance ontologyInstance;

    @Column(name = "field_code", nullable = false, length = 100)
    private String fieldCode;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @Column(name = "user_id", length = 100)
    private String userId;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
