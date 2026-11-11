package com.sitech.prodai.service;

import com.sitech.prodai.ProdAiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("配置合规 SWRL（R-C03~C09）")
class ConfigComplianceSwrlDiagTest {

    @Autowired
    private OpsSwrlReasoner reasoner;

    @Autowired
    private OpsRulesService opsRules;

    private OpsSwrlReasoner.ConfigComplianceResult run(Map<String, Object> draft) {
        return reasoner.reasonConfigCompliance(draft, Map.of(),
                opsRules.configRule("R-C03"), opsRules.configRule("R-C04"), opsRules.configRule("R-C05"),
                opsRules.configRule("R-C06"), opsRules.configRule("R-C07"), opsRules.configRule("R-C08"),
                opsRules.configRule("R-C09"), 9, 599);
    }

    private Map<String, Object> base() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("offeringName", "测试资费");
        d.put("messageRootKey", "broadBandMainPrc");
        d.put("fixedFeeAmount", 128);
        d.put("oneTimeFee", 0);
        d.put("channelScope", "全渠道");
        return d;
    }

    @Test
    @DisplayName("必填齐全+区间内 → 合规通过")
    void compliant() {
        OpsSwrlReasoner.ConfigComplianceResult r = run(base());
        Assertions.assertTrue(r.success(), r.message());
        Assertions.assertTrue(r.violations().isEmpty(), "应无违规: " + r.violations());
        Assertions.assertTrue(r.firedRules().contains("R-C08"),
                "fired=" + r.firedRules() + " reasoning=" + r.reasoning() + " msg=" + r.message());
    }

    @Test
    @DisplayName("R-C06 必填缺失")
    void missingRequired() {
        Map<String, Object> d = base();
        d.remove("offeringName");
        OpsSwrlReasoner.ConfigComplianceResult r = run(d);
        Assertions.assertTrue(r.success(), r.message());
        Assertions.assertTrue(r.violations().stream().anyMatch(v -> "R-C06".equals(v.get("ruleId"))));
    }

    @Test
    @DisplayName("R-C09 超上限")
    void feeAbove() {
        Map<String, Object> d = base();
        d.put("fixedFeeAmount", 999);
        OpsSwrlReasoner.ConfigComplianceResult r = run(d);
        Assertions.assertTrue(r.violations().stream().anyMatch(v -> "R-C09".equals(v.get("ruleId"))));
    }

    @Test
    @DisplayName("R-C05 零固费无合约")
    void highRiskZeroFee() {
        Map<String, Object> d = base();
        d.put("fixedFeeAmount", 0);
        d.put("oneTimeFee", 0);
        OpsSwrlReasoner.ConfigComplianceResult r = run(d);
        Assertions.assertTrue(r.violations().stream().anyMatch(v -> "R-C05".equals(v.get("ruleId"))));
    }

    @Test
    @DisplayName("R-C07 折扣100可重复")
    void abnormalDiscount() {
        Map<String, Object> d = base();
        d.put("discountPercent", 100);
        d.put("repeatable", true);
        OpsSwrlReasoner.ConfigComplianceResult r = run(d);
        Assertions.assertTrue(r.violations().stream().anyMatch(v -> "R-C07".equals(v.get("ruleId"))));
    }

    @Test
    @DisplayName("R-C04 附加缺依赖")
    void orphanAddon() {
        Map<String, Object> d = base();
        d.put("offeringType", "addon");
        OpsSwrlReasoner.ConfigComplianceResult r = run(d);
        Assertions.assertTrue(r.violations().stream().anyMatch(v -> "R-C04".equals(v.get("ruleId"))));
    }

    @Test
    @DisplayName("R-C05 白名单豁免：零元也不再判违规")
    void whitelistExempt() {
        Map<String, Object> d = base();
        d.put("fixedFeeAmount", 0);
        d.put("oneTimeFee", 0);
        d.put("bizScenario", "权益赠送");
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("equityGiftWhitelist", java.util.List.of("权益赠送"));
        OpsSwrlReasoner.ConfigComplianceResult r = reasoner.reasonConfigCompliance(d, graph,
                opsRules.configRule("R-C03"), opsRules.configRule("R-C04"), opsRules.configRule("R-C05"),
                opsRules.configRule("R-C06"), opsRules.configRule("R-C07"), opsRules.configRule("R-C08"),
                opsRules.configRule("R-C09"), 9, 599);
        Assertions.assertTrue(r.success(), r.message());
        Assertions.assertTrue(r.violations().stream().noneMatch(v -> "R-C05".equals(v.get("ruleId"))),
                "白名单豁免后不应命中 R-C05: " + r.violations());
    }

    @Test
    @DisplayName("R-C05 内测豁免：零元也不再判违规")
    void internalTestExempt() {
        Map<String, Object> d = base();
        d.put("fixedFeeAmount", 0);
        d.put("oneTimeFee", 0);
        d.put("channelScope", "内部验证");
        OpsSwrlReasoner.ConfigComplianceResult r = run(d);
        Assertions.assertTrue(r.success(), r.message());
        Assertions.assertTrue(r.violations().stream().noneMatch(v -> "R-C05".equals(v.get("ruleId"))),
                "内测豁免后不应命中 R-C05: " + r.violations());
    }

    @Test
    @DisplayName("违规实例不得被判为合规")
    void violationNotCompliant() {
        Map<String, Object> d = base();
        d.put("fixedFeeAmount", 999);
        OpsSwrlReasoner.ConfigComplianceResult r = run(d);
        Assertions.assertTrue(r.success(), r.message());
        Assertions.assertFalse(r.compliant(), "存在 R-C09 违规时不应判合规");
        Assertions.assertFalse(r.firedRules().contains("R-C08"), "fired=" + r.firedRules());
    }

    @Test
    @DisplayName("推理链非空")
    void reasoningChain() {
        Map<String, Object> d = base();
        d.put("fixedFeeAmount", 999);
        OpsSwrlReasoner.ConfigComplianceResult r = run(d);
        Assertions.assertFalse(r.reasoning().isEmpty());
        Assertions.assertTrue(r.reasoning().get(0).containsKey("condition"));
    }
}
