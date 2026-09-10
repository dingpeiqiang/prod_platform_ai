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

    /** P3 结构校验加严：场景缺主键三元组应判失败。 */
    @Test
    void rejectsScenarioMissingPrimaryKeys() {
        Map<String, Object> raw = baseGraph();
        raw.put("bizScenarios", Map.of(
                "坏场景", Map.of("scenarioId", "S1", "defaults", Map.of())));
        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
        assertFalse(vr.ok());
        assertTrue(vr.errors().stream().anyMatch(e -> e.contains("messageRootKey")));
        assertTrue(vr.errors().stream().anyMatch(e -> e.contains("categoryCode")));
    }

    /** P3 结构校验加严：derive_rules 条目缺动作键 / 条件非对象应判失败。 */
    @Test
    void rejectsDeriveRulesWithoutActionKey() {
        Map<String, Object> raw = baseGraph();
        raw.put("bizScenarios", Map.of(
                "场景A", validScenario(Map.of("derive_rules", List.of(
                        Map.of("when", Map.of("offeringType", "addon")),
                        Map.of("set_if_missing", "不是对象"))))));
        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
        assertFalse(vr.ok());
        assertTrue(vr.errors().stream().anyMatch(e -> e.contains("missing action key")));
        assertTrue(vr.errors().stream().anyMatch(e -> e.contains("set_if_missing must be an object")));
    }

    /** P3 结构校验加严：模板缺 templateId/categoryCode/messageRootKey 应判失败。 */
    @Test
    void rejectsTemplateMissingPrimaryKeys() {
        Map<String, Object> raw = baseGraph();
        raw.put("templates", Map.of("TPL-BAD", Map.of("templateId", "TPL-BAD", "name", "坏模板")));
        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
        assertFalse(vr.ok());
        assertTrue(vr.errors().stream().anyMatch(e -> e.contains("templates[TPL-BAD]")));
    }

    /** 合法 DSL 场景（含 derive_rules 全动作键）应通过。 */
    @Test
    void acceptsScenarioWithValidDeriveRules() {
        Map<String, Object> raw = baseGraph();
        raw.put("bizScenarios", Map.of(
                "场景B", validScenario(Map.of("derive_rules", List.of(
                        Map.of("fill_defaults", Map.of(), "rule", "none"),
                        Map.of("set_if_missing", Map.of("monthlyFee", 59),
                                "when", Map.of("offeringType", "!addon"),
                                "fill_source", "template", "rule", "R-C02"),
                        Map.of("derive_category", Map.of("categoryCode", "x", "messageRootKey", "x"),
                                "when_any", Map.of("targetUser", "家庭")),
                        Map.of("select_template", Map.of("when", Map.of("categoryCode", "x"),
                                "templateId", "TPL-X")))))));
        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
        assertTrue(vr.ok(), () -> String.join("; ", vr.errors()));
    }

    private static Map<String, Object> baseGraph() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("shelfOfferings", List.of(Map.of("offeringId", "OF-1", "offeringName", "套餐")));
        raw.put("opsGraph", Map.of());
        raw.put("bizScenarios", Map.of());
        raw.put("templates", Map.of());
        raw.put("equityGiftWhitelist", List.of());
        raw.put("riskRuleDefaults", Map.of());
        return raw;
    }

    private static Map<String, Object> validScenario(Map<String, Object> extra) {
        Map<String, Object> sc = new LinkedHashMap<>();
        sc.put("scenarioId", "SCENE_X");
        sc.put("messageRootKey", "personMainPrc");
        sc.put("categoryCode", "personMainPrc");
        sc.put("defaults", Map.of("offeringType", "main_pkg"));
        sc.putAll(extra);
        return sc;
    }
}
