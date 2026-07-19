package com.sitech.prodai.domain.entity;

import com.sitech.prodai.common.JsonConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本体定义 —— 对齐 Python {@code app/models/ontology.py::Ontology}。
 *
 * <p>AI 原生架构核心：Ontology = Schema/Class（定义数据结构和约束）。
 * 表名 ontologies，字段对齐 Python SQLAlchemy 列定义。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ontologies")
@EntityListeners(AuditingEntityListener.class)
public class Ontology {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ontology_code", unique = true, nullable = false, length = 100)
    private String ontologyCode;

    @Column(name = "ontology_name", nullable = false, length = 200)
    private String ontologyName;

    @Column(name = "category", length = 100)
    private String category = "general";

    /** 实体定义（字段结构），JSON 列，对齐 Python Column(JSON) */
    @Column(name = "entities", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> entities;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
