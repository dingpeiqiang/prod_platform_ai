package com.sitech.prodai.dto;

public class RootCauseRequest {

    /** 产商品编码，可与 text 二选一；均未提供时由服务解析失败 */
    private String offeringId;
    /** 用户自然语言，用于按名称/编码解析产商品 */
    private String text;

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
