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
 * 用户行为归因证据（pmsdb.attribution_user_behavior，本体 sc:v-UB-* 事实来源）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("attribution_user_behavior")
public class AttributionUserBehavior {

    @TableId(value = "evidence_id", type = IdType.AUTO)
    private Long evidenceId;

    /** 商品编码（本体 :influencedBy） */
    @TableField("offerid")
    private String offerid;

    /** 行为样本编码（本体 :behaviorId） */
    @TableField("behavior_id")
    private String behaviorId;

    /** 行为名称（本体 :behaviorName） */
    @TableField("behavior_name")
    private String behaviorName;

    /** 候选权重提示（本体 :weightHint） */
    @TableField("weight_hint")
    private BigDecimal weightHint;

    /** 指标统计日 */
    @TableField("stat_date")
    private LocalDateTime statDate;
}
