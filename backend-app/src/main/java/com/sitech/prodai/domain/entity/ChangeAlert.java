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
 * 商品变更提醒（方案 §6-C3，对应缺口 C5）：变更检测产生的提醒记录。
 * <p>数据源：ChangeDetectService 对比新旧货架快照（月费/状态字段级 diff），
 * 命中订阅的商品时落一条提醒行——出口 v1 为本表（工具查询透出），
 * 生产可加站内信/短信通道（监听者扩展，发布与检测零改动）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_change_alerts")
public class ChangeAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品编码 */
    @TableField("offering_id")
    private String offeringId;

    /** 商品名称 */
    @TableField("offering_name")
    private String offeringName;

    /** 变更类型：fee_change（月费）/ state_change（状态） */
    @TableField("change_type")
    private String changeType;

    /** 变更前值（快照旧值） */
    @TableField("old_value")
    private String oldValue;

    /** 变更后值（快照新值） */
    @TableField("new_value")
    private String newValue;

    /** 检测来源快照版本（pd_ai_ontology_version.version） */
    @TableField("detected_version")
    private String detectedVersion;

    /** 提醒接收人（订阅者登录名；空=广播） */
    @TableField("subscriber")
    private String subscriber;

    /** 是否已读（v1 出口=工具查询即置已读；预留前端铃铛扩展） */
    @TableField("read_flag")
    private Boolean readFlag;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
