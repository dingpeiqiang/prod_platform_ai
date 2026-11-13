package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 促销归因证据（pmsdb.attribution_promotion，本体 sc:v-PR-* 事实来源）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("attribution_promotion")
public class AttributionPromotion {

    @TableId(value = "evidence_id", type = IdType.AUTO)
    private Long evidenceId;

    /** 商品编码（本体 :participatesIn） */
    @TableField("offerid")
    private String offerid;

    /** 促销编码（本体 :promoId） */
    @TableField("promo_id")
    private String promoId;

    /** 促销名称（本体 :promoName） */
    @TableField("promo_name")
    private String promoName;

    /** 距到期天数（本体 :daysToExpire） */
    @TableField("days_to_expire")
    private Integer daysToExpire;

    /** 促销驱动订单占比（本体 :drivenOrderRatio） */
    @TableField("driven_order_ratio")
    private BigDecimal drivenOrderRatio;

    /** 候选权重提示（本体 :weightHint） */
    @TableField("weight_hint")
    private BigDecimal weightHint;

    /** 指标统计日 */
    @TableField("stat_date")
    private LocalDateTime statDate;
}
