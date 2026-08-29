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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本体资产版本库·动作日志（P1-5 表 B，设计方案 §13.1）。
 * <p>由原无引用死代码 {@code pd_ai_ontology_instance_history} 接线改造而来（"接线而非新造"），
 * 列收敛为 version_id / action / operator / detail / created_at。
 * <p>action 取值：publish / rollback / deprecate / reload / override；
 * 发布成功与失败均落行，失败行保留 review 态供复盘。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_ontology_version_log", indexes = {
        @Index(name = "idx_ovl_version_id", columnList = "version_id"),
        @Index(name = "idx_ovl_action", columnList = "action"),
        @Index(name = "idx_ovl_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class OntologyVersionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外键：pd_ai_ontology_version.id */
    @Column(name = "version_id", nullable = false)
    private Long versionId;

    /** 动作：publish / rollback / deprecate / reload / override */
    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "operator", length = 64)
    private String operator;

    /** 动作明细 JSON（diff 报告、失败原因等） */
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    @Column(name = "detail", columnDefinition = "TEXT")
    private Map<String, Object> detail = new LinkedHashMap<>();

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
