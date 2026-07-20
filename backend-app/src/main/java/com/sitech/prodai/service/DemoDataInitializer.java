package com.sitech.prodai.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final Rdf4jOntologyStore rdf4jStore;

    public DemoDataInitializer(Rdf4jOntologyStore rdf4jStore) {
        this.rdf4jStore = rdf4jStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedClasses();
        seedProperties();
        seedProducts();
        seedChannels();
        seedIndicators();
        seedCompetitors();
    }

    private void seedClasses() {
        for (String className : List.of("Product", "OperationIndicator", "SalesChannel", "MarketSegment", "CompetitorProduct", "RiskRule", "OperationActivity")) {
            rdf4jStore.addClass(className);
        }
    }

    private void seedProperties() {
        for (String property : List.of("productName", "productType", "status", "price", "isZeroFee", "onlineMonths", "targetMarketSize", "newUserMonth", "userChurnRate", "revenueGrowth", "channelName", "competitorName", "activityName")) {
            rdf4jStore.addProperty(property);
        }
    }

    private void seedProducts() {
        seedInstance("http://example.org/product/PROD_5G_001", "Product", Map.of(
                "productName", "畅享5G套餐A",
                "productType", "5G套餐",
                "status", "在售",
                "price", 39,
                "isZeroFee", false,
                "onlineMonths", 12,
                "targetMarketSize", 150000,
                "newUserMonth", 1200,
                "userChurnRate", 0.05,
                "revenueGrowth", 0.021
        ));
        seedInstance("http://example.org/product/PROD_5G_002", "Product", Map.of(
                "productName", "畅享5G套餐B",
                "productType", "5G套餐",
                "status", "在售",
                "price", 59,
                "isZeroFee", false,
                "onlineMonths", 10,
                "targetMarketSize", 180000,
                "newUserMonth", 2400,
                "userChurnRate", 0.04,
                "revenueGrowth", 0.067
        ));
        seedInstance("http://example.org/product/PROD_5G_003", "Product", Map.of(
                "productName", "青春5G套餐",
                "productType", "5G套餐",
                "status", "在售",
                "price", 29,
                "isZeroFee", true,
                "onlineMonths", 4,
                "targetMarketSize", 80000,
                "newUserMonth", 38,
                "userChurnRate", 0.12,
                "revenueGrowth", 0.01
        ));
        seedInstance("http://example.org/product/PROD_BB_001", "Product", Map.of(
                "productName", "千兆宽带套餐",
                "productType", "宽带",
                "status", "在售",
                "price", 99,
                "isZeroFee", false,
                "onlineMonths", 18,
                "targetMarketSize", 90000,
                "newUserMonth", 860,
                "userChurnRate", 0.06,
                "revenueGrowth", 0.035
        ));
        seedInstance("http://example.org/product/PROD_BB_002", "Product", Map.of(
                "productName", "家庭融合套餐",
                "productType", "宽带",
                "status", "在售",
                "price", 129,
                "isZeroFee", false,
                "onlineMonths", 22,
                "targetMarketSize", 120000,
                "newUserMonth", 920,
                "userChurnRate", 0.05,
                "revenueGrowth", 0.028
        ));
        seedInstance("http://example.org/product/PROD_IOT_001", "Product", Map.of(
                "productName", "物联网基础包",
                "productType", "物联网",
                "status", "在售",
                "price", 19,
                "isZeroFee", true,
                "onlineMonths", 6,
                "targetMarketSize", 60000,
                "newUserMonth", 25,
                "userChurnRate", 0.09,
                "revenueGrowth", 0.005
        ));
    }

    private void seedChannels() {
        seedInstance("http://example.org/channel/CH_001", "SalesChannel", Map.of("channelName", "旗舰厅", "commissionChanged", true, "monthlyVolumeDrop", 0.42));
        seedInstance("http://example.org/channel/CH_002", "SalesChannel", Map.of("channelName", "线上营业厅", "commissionChanged", false, "monthlyVolumeDrop", 0.08));
        seedInstance("http://example.org/channel/CH_003", "SalesChannel", Map.of("channelName", "代理渠道", "commissionChanged", true, "monthlyVolumeDrop", 0.31));
    }

    private void seedIndicators() {
        seedInstance("http://example.org/indicator/IND_001", "OperationIndicator", Map.of("indicatorName", "营收增长率", "indicatorCode", "REVENUE_GROWTH", "currentMonthValue", 0.021));
        seedInstance("http://example.org/indicator/IND_002", "OperationIndicator", Map.of("indicatorName", "月新增用户", "indicatorCode", "NEW_USER_MONTH", "currentMonthValue", 38));
        seedInstance("http://example.org/indicator/IND_003", "OperationIndicator", Map.of("indicatorName", "用户流失率", "indicatorCode", "USER_CHURN", "currentMonthValue", 0.12));
    }

    private void seedCompetitors() {
        seedInstance("http://example.org/competitor/CP_001", "CompetitorProduct", Map.of("competitorName", "友商5G畅销包", "price", 19, "sellingPoint", "短期促销"));
        seedInstance("http://example.org/competitor/CP_002", "CompetitorProduct", Map.of("competitorName", "友商千兆宽带", "price", 89, "sellingPoint", "合约赠送"));
        seedInstance("http://example.org/competitor/CP_003", "CompetitorProduct", Map.of("competitorName", "友商物联网包", "price", 15, "sellingPoint", "大流量"));
    }

    private void seedInstance(String uri, String type, Map<String, Object> facts) {
        Map<String, Object> payload = new LinkedHashMap<>(facts);
        payload.put("type", type);
        rdf4jStore.addInstance(uri, type, payload);
    }
}
