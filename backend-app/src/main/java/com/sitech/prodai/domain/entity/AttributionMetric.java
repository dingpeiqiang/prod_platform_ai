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
 * 指标快照（pmsdb.attribution_metric，本体 sc:v-M-* 事实来源）。
 * <p>承载异动判据：metricDelta 或 anomalyFlag 触发 R-A01 点火。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("attribution_metric")
public class AttributionMetric {

    @TableId(value = "metric_id", type = IdType.AUTO)
    private Long metricId;

    /** 商品编码（本体 :hasMetric） */
    @TableField("offerid")
    private String offerid;

    /** 指标编码（本体 :metricCode） */
    @TableField("metric_code")
    private String metricCode;

    /** 指标值（本体 :metricValue） */
    @TableField("metric_value")
    private BigDecimal metricValue;

    /** 指标环比（本体 :metricDelta） */
    @TableField("metric_delta")
    private BigDecimal metricDelta;

    /** 异动标记（1 异动 / 0 正常，本体 :anomalyFlag） */
    @TableField("anomaly_flag")
    private Integer anomalyFlag;

    /** 指标统计日 */
    @TableField("stat_date")
    private LocalDateTime statDate;
}
