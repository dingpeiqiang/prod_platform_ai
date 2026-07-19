package com.sitech.prodai.domain.entity;

import com.sitech.prodai.common.JsonConverters;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
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
 * 提示词主表 —— 对齐 Python {@code app/models/prompt.py::Prompt}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "prompts", indexes = {
        @Index(name = "idx_prompt_code", columnList = "code", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class Prompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code", unique = true, nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 50)
    private String category = "general";

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 模板变量定义：[{"name","description","default"}] */
    @Column(name = "variables", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> variables;

    /** 可用工具：[{"code","name","description"}] */
    @Column(name = "tools", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> tools;

    @Column(name = "is_template")
    private Boolean isTemplate = false;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "prompt", cascade = CascadeType.ALL)
    private List<PromptVersion> versions;
}
