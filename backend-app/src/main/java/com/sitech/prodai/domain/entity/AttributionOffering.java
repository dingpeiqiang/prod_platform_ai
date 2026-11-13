package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 归因商品（pmsdb.attribution_offering，本体 :Offering 事实来源）。
 * <p>归因证据链的根实体，其余证据表均按 offerid 关联本表。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("attribution_offering")
public class AttributionOffering {

    /** 商品编码 */
    @TableId(value = "offerid", type = IdType.INPUT)
    private String offerid;

    /** 商品名称 */
    @TableField("offering_name")
    private String offeringName;

    /** 阈值说明 */
    @TableField("threshold_note")
    private String thresholdNote;
}
