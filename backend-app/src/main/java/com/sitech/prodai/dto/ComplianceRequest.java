package com.sitech.prodai.dto;

import java.util.Map;

/**
 * 合规校验请求。支持三种输入：
 * <ul>
 *   <li>{@code draft}：未入库配置草稿</li>
 *   <li>{@code offeringId} / {@code text}：解析已入库（在架）套餐后校验</li>
 *   <li>组合：文案含「当前配置」时优先用草稿；否则优先按名称/编码解析在架套餐</li>
 * </ul>
 */
public class ComplianceRequest {

    /** 未入库配置草稿字段 */
    private Map<String, Object> draft;
    /** 已入库套餐编码 */
    private String offeringId;
    /** 自然语言，可含套餐名称/编码，或「校验当前配置」 */
    private String text;

    public Map<String, Object> getDraft() {
        return draft;
    }

    public void setDraft(Map<String, Object> draft) {
        this.draft = draft;
    }

    public String getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(String offeringId) {
        this.offeringId = offeringId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
