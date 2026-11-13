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
 * 竞品归因证据（pmsdb.attribution_competitor，本体 sc:v-CP-* 事实来源）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("attribution_competitor")
public class AttributionCompetitor {

    @TableId(value = "evidence_id", type = IdType.AUTO)
    private Long evidenceId;

    /** 商品编码（本体 :competesWith） */
    @TableField("offerid")
    private String offerid;

    /** 竞品编码（本体 :competitorId） */
    @TableField("competitor_id")
    private String competitorId;

    /** 竞品名称（本体 :competitorName） */
    @TableField("competitor_name")
    private String competitorName;

    /** 绝对价差（本体 :priceGap） */
    @TableField("price_gap")
    private BigDecimal priceGap;

    /** 价差比（本体 :priceGapRatio） */
    @TableField("price_gap_ratio")
    private BigDecimal priceGapRatio;

    /** 竞品渗透率变化（百分点，本体 :penetrationDeltaPp） */
    @TableField("penetration_delta_pp")
    private BigDecimal penetrationDeltaPp;

    /** 候选权重提示（本体 :weightHint） */
    @TableField("weight_hint")
    private BigDecimal weightHint;

    /** 指标统计日 */
    @TableField("stat_date")
    private LocalDateTime statDate;
}
