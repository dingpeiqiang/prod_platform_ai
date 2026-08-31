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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产商品运营处置工单（稽核/归因闭环回写）。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_ops_work_orders", indexes = {
        @Index(name = "idx_owo_offering", columnList = "offering_id"),
        @Index(name = "idx_owo_status", columnList = "status"),
        @Index(name = "idx_owo_session", columnList = "session_id"),
        @Index(name = "idx_owo_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class OpsWorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false, unique = true, length = 64)
    private String workOrderId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "offering_id", length = 64)
    private String offeringId;

    @Column(name = "offering_name", length = 255)
    private String offeringName;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Convert(converter = JsonConverters.JsonListConverter.class)
    @Column(name = "actions", columnDefinition = "TEXT")
    private List<Object> actions = new ArrayList<>();

    @Column(name = "status", nullable = false, length = 32)
    private String status = "open";

    @Column(name = "source", length = 64)
    private String source;

    /** 来源会话 ID：用于研发助手消息窗口按会话聚合展示商品配置工单 */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "hypo_mode", length = 32)
    private String hypoMode;

    @Convert(converter = JsonConverters.JsonMapConverter.class)
    @Column(name = "payload", columnDefinition = "TEXT")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
