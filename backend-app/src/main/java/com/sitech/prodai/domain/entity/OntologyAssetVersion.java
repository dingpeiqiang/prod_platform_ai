package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 本体资产版本库·主表（P1-5 方案 A，设计方案 §13.1）。
 * <p>纳入对象：template / message_projection / ops_rules / ttl / abox_snapshot。
 * {@code payload} 存资产源码全文，是回滚的唯一事实源。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_ontology_version")
public class OntologyAssetVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资产类型：template / message_projection / ops_rules / ttl / abox_snapshot */
    @TableField("asset_type")
    private String assetType;

    /** 资产编码：template_id / rules 文件名 / ttl 文件名等 */
    @TableField("asset_code")
    private String assetCode;

    /** 语义化版本（semver）；ttl 等无内建版本的资产用时间戳版本 */
    @TableField("version")
    private String version;

    /** 状态：draft / review / published / deprecated */
    @TableField("status")
    private String status = "draft";

    @TableField("author")
    private String author;

    @TableField("summary")
    private String summary;

    /** 源码全文（回滚唯一事实源） */
    @TableField("payload")
    private String payload;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("deprecated_at")
    private LocalDateTime deprecatedAt;
}
