package com.sitech.prodai.dto;

public class RiskRulesRequest {

    private Integer zeroSalesShelfDays;
    private Integer zeroSalesDaysWindow;
    private Integer highRiskReviewDays;
    private Double lowRevenuePercentile;
    private String ruleVersion;

    public Integer getZeroSalesShelfDays() {
        return zeroSalesShelfDays;
    }

    public void setZeroSalesShelfDays(Integer zeroSalesShelfDays) {
        this.zeroSalesShelfDays = zeroSalesShelfDays;
    }

    public Integer getZeroSalesDaysWindow() {
        return zeroSalesDaysWindow;
    }

    public void setZeroSalesDaysWindow(Integer zeroSalesDaysWindow) {
        this.zeroSalesDaysWindow = zeroSalesDaysWindow;
    }

    public Integer getHighRiskReviewDays() {
        return highRiskReviewDays;
    }

    public void setHighRiskReviewDays(Integer highRiskReviewDays) {
        this.highRiskReviewDays = highRiskReviewDays;
    }

    public Double getLowRevenuePercentile() {
        return lowRevenuePercentile;
    }

    public void setLowRevenuePercentile(Double lowRevenuePercentile) {
        this.lowRevenuePercentile = lowRevenuePercentile;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }
}
