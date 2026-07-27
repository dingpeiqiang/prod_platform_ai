package com.sitech.prodai.service.ops;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsGraphSchemaValidatorTest {

    @Test
    void acceptsMockCompatibleGraph() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("shelfOfferings", List.of(Map.of(
                "offeringId", "OF-1",
                "offeringName", "测试套餐"
        )));
        raw.put("opsGraph", Map.of("OF-1", Map.of("name", "测试套餐")));
        raw.put("bizScenarios", Map.of());
        raw.put("templates", Map.of());
        raw.put("equityGiftWhitelist", List.of());
        raw.put("riskRuleDefaults", Map.of());

        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
        assertTrue(vr.ok(), () -> String.join("; ", vr.errors()));
    }

    @Test
    void rejectsShelfWithoutId() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("shelfOfferings", List.of(Map.of("offeringName", "无ID")));
        raw.put("opsGraph", Map.of());
        raw.put("bizScenarios", Map.of());
        raw.put("templates", Map.of());
        raw.put("equityGiftWhitelist", List.of());
        raw.put("riskRuleDefaults", Map.of());

        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
        assertFalse(vr.ok());
        assertTrue(vr.errors().stream().anyMatch(e -> e.contains("offeringId")));
    }

    @Test
    void detectsLocalProductCenter() {
        assertTrue(OpsGraphSchemaValidator.looksLikeLocalProductCenter(
                "http://localhost:6174/api/v1/product-center"));
        assertFalse(OpsGraphSchemaValidator.looksLikeLocalProductCenter(
                "https://boss.example.com/api/v1/product-center"));
        assertFalse(OpsGraphSchemaValidator.looksLikeLocalProductCenter(""));
    }
}
