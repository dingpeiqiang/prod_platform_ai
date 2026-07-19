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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 本体实例 —— 对齐 Python {@code app/models/ontology_instance.py::OntologyInstance}。
 *
 * <p>OntologyInstance = Object/Record（存储符合本体约束的实际业务数据），
 * 取代旧的 FormInstance 概念。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ontology_instances", indexes = {
        @Index(name = "idx_oi_ontology_code", columnList = "ontology_code"),
        @Index(name = "idx_oi_user_id", columnList = "user_id"),
        @Index(name = "idx_oi_session_id", columnList = "session_id")
})
@EntityListeners(AuditingEntityListener.class)
public class OntologyInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ontology_code", nullable = false, length = 100)
    private String ontologyCode;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    /** 实例数据（符合本体约束的字段值），JSON 列 */
    @Column(name = "data", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> data;

    /** draft / submitted / cancelled */
    @Column(name = "status", length = 50)
    private String status = "draft";

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
