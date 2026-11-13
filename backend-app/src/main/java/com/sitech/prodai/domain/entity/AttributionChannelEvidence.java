package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
 * 渠道归因证据（外部库 newpmsdb.attribution_channel_evidence，本体 sc:v-CH-* 事实来源）。
 * <p>每条为某商品某渠道的异动证据快照：仅读，供归因追溯按 offerid 查询。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("attribution_channel_evidence")
public class AttributionChannelEvidence {

    @TableId(type = IdType.AUTO)
    private Long evidenceId;

    /** 商品编码（本体 :offeringId） */
    @TableField("offerid")
    private String offerid;

    /** 渠道编码（本体 :channelId） */
    @TableField("channel_id")
    private String channelId;

    /** 渠道名称（本体 :channelName） */
    @TableField("channel_name")
    private String channelName;

    /** 渠道订单量环比（负值表示下降，本体 :orderDelta） */
    @TableField("order_delta")
    private BigDecimal orderDelta;

    /** 渠道贡献占比（本体 :contribRatio） */
    @TableField("contrib_ratio")
    private BigDecimal contribRatio;

    /** 候选权重提示（本体 :weightHint） */
    @TableField("weight_hint")
    private BigDecimal weightHint;

    /** 指标统计日 */
    @TableField(value = "stat_date", fill = FieldFill.INSERT)
    private LocalDateTime statDate;
}
