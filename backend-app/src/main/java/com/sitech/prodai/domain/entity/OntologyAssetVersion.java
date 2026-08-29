package com.sitech.prodai.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 本体资产版本库·主表（P1-5 表 A，设计方案 §13.1）。
 * <p>纳入对象：template / message_projection / ops_rules / ttl / abox_snapshot——
 * 凡注册进 Registry 或供给 checkCompliance 配置面的资源都登记版本行；
 * P1 先登记 template / ops_rules / ttl；message_projection（P1 不接管）与 abox_snapshot（P3-2 启用）随对应任务启用。
 * <p>{@code payload} 存资产源码全文，是回滚的唯一事实源。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_ontology_version", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oav_type_code_version",
                columnNames = {"asset_type", "asset_code", "version"})
}, indexes = {
        @Index(name = "idx_oav_asset", columnList = "asset_type, asset_code"),
        @Index(name = "idx_oav_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class OntologyAssetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 资产类型：template / message_projection / ops_rules / ttl / abox_snapshot */
    @Column(name = "asset_type", nullable = false, length = 32)
    private String assetType;

    /** 资产编码：template_id / rules 文件名 / ttl 文件名等 */
    @Column(name = "asset_code", nullable = false, length = 128)
    private String assetCode;

    /** 语义化版本（semver）；ttl 等无内建版本的资产用时间戳版本 */
    @Column(name = "version", nullable = false, length = 64)
    private String version;

    /** 状态：draft / review / published / deprecated */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "draft";

    @Column(name = "author", length = 64)
    private String author;

    @Column(name = "summary", length = 512)
    private String summary;

    /** 源码全文（回滚唯一事实源） */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deprecated_at")
    private LocalDateTime deprecatedAt;
}
