package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Openllet SWRL：归因 R-A01~A05 + 风险 R-B* 冒烟。
 */
class OpsSwrlReasonerTest {

    private OpsSwrlReasoner reasoner;

    @BeforeEach
    void setUp() {
        ProdAiProperties props = new ProdAiProperties();
        props.getOntology().setSwrlEnabled(true);
        props.getOntology().setProductOpsOwlPath("classpath:ontology/product-ops.ttl");
        reasoner = new OpsSwrlReasoner(new DefaultResourceLoader(), props);
    }

    @Test
    void rootCause_firesA01A02A03() {
        Map<String, Object> node = sampleAnomalyNode();
        Map<String, Object> on = Map.of("enabled", true);
        OpsSwrlReasoner.SwrlFireResult r = reasoner.reasonRootCause(
                "OF-HF-128", "家庭融合畅享128", node,
                Map.of("enabled", true, "metricDeltaLte", -0.10, "honorAnomalyFlag", true),
                Map.of("enabled", true, "orderDeltaLte", -0.20, "contribRatioGte", 0.30),
                Map.of("enabled", true, "daysToExpireLte", 7, "drivenOrderRatioGte", 0.25),
                Map.of("enabled", true, "priceGapRatioGte", 0.15, "penetrationDeltaPpGt", 0),
                Map.of("enabled", true, "minWeightHint", 0.08)
        );
        assertTrue(r.success(), r.message());
        assertEquals("openllet-swrl", r.engine());
        assertTrue(r.anomalyFired());
        assertTrue(r.firedRules().contains("R-A01"));
        assertTrue(r.firedRules().contains("R-A02"));
        assertTrue(r.firedRules().contains("R-A03"));
        assertFalse(r.channelCandidates().isEmpty());
        assertFalse(r.promotionCandidates().isEmpty());
    }

    @Test
    void risk_zeroFeeNoContract_andUrgent() {
        Map<String, Object> offering = new LinkedHashMap<>();
        offering.put("offeringId", "OF-RISK-001");
        offering.put("monthlyFee", 0);
        offering.put("oneTimeFee", 0);
        offering.put("hasContract", false);
        offering.put("shelfDays", 45);
        offering.put("salesCnt30d", 120);
        offering.put("revenue30d", 0);
        offering.put("repeatable", false);

        Map<String, Object> flags = Map.of(
                "inWhitelist", false,
                "onShelf", true,
                "lowEffCategoryFlag", false,
                "lowRevenueFlag", false
        );
        Map<String, Object> enabled = Map.of("enabled", true);
        Map<String, Object> defaults = Map.of("zeroSalesShelfDays", 180, "highRiskReviewDays", 30);

        OpsSwrlReasoner.SwrlRiskResult r = reasoner.reasonRiskOffering(
                offering, flags, enabled, enabled, enabled, enabled, enabled, defaults);
        assertTrue(r.success(), r.message());
        assertTrue(r.firedRules().contains("R-B01"));
        assertTrue(r.firedRules().contains("R-B02"));
        assertTrue(r.firedRules().contains("R-B05"));
        assertEquals("HIGH", r.riskLevel());
        assertTrue(r.urgent());
    }

    private Map<String, Object> sampleAnomalyNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("metrics", List.of(Map.of(
                "metricCode", "累计收入",
                "metricValue", 38200,
                "metricDelta", -0.18,
                "anomaly", true
        )));
        node.put("channels", List.of(Map.of(
                "channelId", "CH-HALL",
                "name", "营业厅",
                "orderDelta", -0.35,
                "contribRatio", 0.42,
                "weightHint", 0.42
        )));
        node.put("promotions", List.of(Map.of(
                "promoId", "PR-HF-GIFT",
                "name", "家庭融合加装礼",
                "daysToExpire", 5,
                "drivenOrderRatio", 0.31,
                "weightHint", 0.31
        )));
        node.put("competitors", List.of(Map.of(
                "competitorId", "CP-F120",
                "name", "友商融合120",
                "priceGap", 20,
                "priceGapRatio", 0.156,
                "penetrationDeltaPp", 2.1,
                "weightHint", 0.18
        )));
        node.put("behaviors", List.of(Map.of(
                "behaviorId", "UB-CHURN",
                "name", "主角成员退订率上升",
                "weightHint", 0.09
        )));
        return node;
    }
}
