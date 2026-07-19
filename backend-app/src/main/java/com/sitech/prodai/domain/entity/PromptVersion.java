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

/**
 * 提示词版本历史 —— 对齐 Python {@code app/models/prompt.py::PromptVersion}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "prompt_versions", indexes = {
        @Index(name = "idx_pv_prompt_id", columnList = "prompt_id")
})
@EntityListeners(AuditingEntityListener.class)
public class PromptVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "prompt_id", nullable = false)
    private Integer promptId;

    @ManyToOne
    @JoinColumn(name = "prompt_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Prompt prompt;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "variables", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> variables;

    @Column(name = "tools", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> tools;

    @Column(name = "change_note", columnDefinition = "TEXT")
    private String changeNote;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
