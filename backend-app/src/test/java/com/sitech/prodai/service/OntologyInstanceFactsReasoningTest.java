package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.ProdAiApplication;
import com.sitech.prodai.domain.entity.OntologyInstance;
import com.sitech.prodai.mapper.OntologyInstanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实例存储 → 推理闭环：事实落在 H2（pd_ai_ontology_instance），
 * 推理时由 {@link OntologyInstanceFactsService} 实时读取，再经声明式映射注入本体。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("实例存储 → SWRL 推理闭环")
class OntologyInstanceFactsReasoningTest {

    @Autowired
    private OntologyInstanceFactsService factsService;

    @Autowired
    private OntologyInstanceMapper instanceMapper;

    @Autowired
    private OpsSwrlReasoner reasoner;

    @Autowired
    private OpsRulesService opsRules;

    @Autowired
    private ObjectMapper objectMapper;

    private Long insertedId;

    @AfterEach
    void cleanup() {
        if (insertedId != null) {
            instanceMapper.deleteById(insertedId);
            insertedId = null;
        }
    }

    /** 写入一条配置草稿实例（data_json 存 _draft_json 全量 + 平铺摘要列）。 */
    private Long persistDraft(Map<String, Object> draft) throws Exception {
        OntologyInstance entity = new OntologyInstance();
        entity.setOntologyCode(OntologyInstanceFactsService.OFFERING_CONFIG_CODE);
        entity.setStatus("draft");
        entity.setUserId("test");
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("offeringName", String.valueOf(draft.getOrDefault("offeringName", "")));
        store.put("_draft_json", objectMapper.writeValueAsString(draft));
        entity.setData(store);
        instanceMapper.insert(entity);
        insertedId = entity.getId();
        return insertedId;
    }

    private Map<String, Object> baseDraft(String offeringId) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("offeringId", offeringId);
        d.put("offeringName", "存储闭环测试资费");
        d.put("messageRootKey", "broadBandMainPrc");
        d.put("fixedFeeAmount", 128);
        d.put("oneTimeFee", 0);
        d.put("channelScope", "全渠道");
        return d;
    }

    private OpsSwrlReasoner.ConfigComplianceResult reason(Long draftId) {
        return reasoner.reasonStoredConfigCompliance(draftId, Map.of(),
                opsRules.configRule("R-C03"), opsRules.configRule("R-C04"), opsRules.configRule("R-C05"),
                opsRules.configRule("R-C06"), opsRules.configRule("R-C07"), opsRules.configRule("R-C08"),
                opsRules.configRule("R-C09"), 9, 599);
    }

    @Test
    @DisplayName("从 H2 读取事实 → 合规通过")
    void storedFactsCompliant() throws Exception {
        Long id = persistDraft(baseDraft("OF-STORE-OK"));

        Map<String, Object> facts = factsService.loadConfigFacts(id);
        Assertions.assertEquals("OF-STORE-OK", facts.get("offeringId"), "事实应能按主键读回: " + facts);
        Assertions.assertEquals(128, ((Number) facts.get("fixedFeeAmount")).intValue());

        OpsSwrlReasoner.ConfigComplianceResult r = reason(id);
        Assertions.assertTrue(r.success(), r.message());
        Assertions.assertTrue(r.violations().isEmpty(), "应无违规: " + r.violations());
        Assertions.assertTrue(r.firedRules().contains("R-C08"), "fired=" + r.firedRules());
    }

    @Test
    @DisplayName("从 H2 读取事实 → 命中 R-C09 超上限")
    void storedFactsFeeAbove() throws Exception {
        Map<String, Object> draft = baseDraft("OF-STORE-ABOVE");
        draft.put("fixedFeeAmount", 999);
        Long id = persistDraft(draft);

        OpsSwrlReasoner.ConfigComplianceResult r = reason(id);
        Assertions.assertTrue(r.success(), r.message());
        Assertions.assertTrue(r.violations().stream().anyMatch(v -> "R-C09".equals(v.get("ruleId"))),
                "应命中 R-C09: " + r.violations());
    }

    @Test
    @DisplayName("从 H2 读取事实 → 命中 R-C06 必填缺失")
    void storedFactsMissingRequired() throws Exception {
        Map<String, Object> draft = baseDraft("OF-STORE-MISS");
        draft.remove("offeringName");
        Long id = persistDraft(draft);

        OpsSwrlReasoner.ConfigComplianceResult r = reason(id);
        Assertions.assertTrue(r.success(), r.message());
        Assertions.assertTrue(r.violations().stream().anyMatch(v -> "R-C06".equals(v.get("ruleId"))),
                "应命中 R-C06: " + r.violations());
    }

    @Test
    @DisplayName("实例不存在 → 返回 no-instance，不抛异常")
    void missingInstanceFallsBack() {
        OpsSwrlReasoner.ConfigComplianceResult r = reason(-1L);
        Assertions.assertFalse(r.success());
        Assertions.assertEquals("no-instance", r.engine());
    }
}
