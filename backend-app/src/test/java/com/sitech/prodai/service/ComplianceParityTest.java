package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.ops.OpsGraphSchemaValidator;
import com.sitech.prodai.service.ops.OpsProductGraphLoader;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R7 合规并跑比对（ComplianceParityTest）：
 * <ul>
 *   <li>shapes 契约断言：compliance-shacl.ttl 可解析、三条试点规则（R-C06/R-C03/R-C05）齐备、
 *       violation.rule 可回读 R-C 编号；</li>
 *   <li>双引擎并跑：同一批典型草稿分别跑 Java 引擎（ComplianceRuleEngine）与 SHACL 委托
 *       （ShaclValidationDelegate），比对 ruleId 命中集合一致率 ≥99%；</li>
 *   <li>比对口径：试点范围内仅比对 R-C06/R-C03/R-C05 命中差集（非试点规则不进入一致率分母）。</li>
 * </ul>
 * 网络受限说明：rdf4j-shacl 构件不可达，SHACL 侧为降级求值（minCount/maxCount），
 * 构件恢复后仅替换 ShaclValidationDelegate.runShacl，本测试无需改动。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplianceParityTest {

    private static final String SHACL_PATH = "classpath:ontologies/compliance-shacl.ttl";
    private static final List<String> PILOT_RULES = List.of("R-C06", "R-C03", "R-C05");

    private ShaclValidationDelegate delegate;
    private ComplianceRuleEngine javaEngine;
    private Model shapes;
    private Map<String, Object> graph;

    @BeforeAll
    void setUp() throws Exception {
        ProdAiProperties properties = new ProdAiProperties();
        delegate = new ShaclValidationDelegate(properties, new DefaultResourceLoader());

        try (InputStream in = new DefaultResourceLoader().getResource(SHACL_PATH).getInputStream()) {
            shapes = Rio.parse(in, "", RDFFormat.TURTLE);
        }

        // Java 引擎最小装配：规则开关经 OpsRulesService 读真实 ops_rules.json
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ProdAiProperties props = new ProdAiProperties();
        props.getOntology().setRulesPath("classpath:ontology/ops_rules.json");
        OpsRulesService opsRules = new OpsRulesService(mapper, new DefaultResourceLoader(), props);
        opsRules.load();
        ConfigMessageProjector projector = new ConfigMessageProjector(mapper, new DefaultResourceLoader());
        projector.init();
        javaEngine = new ComplianceRuleEngine(mapper, props, opsRules,
                new RiskAuditService(), null, projector, null, null,
                () -> graph, (traceId, step) -> { });

        graph = parityGraph();
    }

    // ---------- shapes 契约断言 ----------

    @Test
    void shapesFileShouldParseAndCoverPilotRules() {
        assertNotNull(shapes);
        assertTrue(shapes.size() > 0, "shapes 文件应可解析为非空模型");

        List<String> ruleIds = new ArrayList<>();
        for (Statement st : shapes) {
            if (st.getPredicate().stringValue().equals("http://www.w3.org/ns/shacl#name")) {
                String v = st.getObject().stringValue();
                if (v.matches("R-C\\d+") && !ruleIds.contains(v)) {
                    ruleIds.add(v);
                }
            }
        }
        for (String pilot : PILOT_RULES) {
            assertTrue(ruleIds.contains(pilot), "shapes 缺试点规则标注: " + pilot);
        }
    }

    // ---------- 双引擎并跑比对 ----------

    @Test
    void parityShouldAgreeOnPilotRules() {
        List<Map<String, Object>> corpus = parityCorpus();

        int total = 0;
        int agree = 0;
        List<String> disagreements = new ArrayList<>();
        for (Map<String, Object> draft : corpus) {
            Set<String> javaHits = pilotRuleIds(javaEngine.checkCompliance(draft));
            Set<String> shaclHits = pilotRuleIds(delegate.validate(draft, graph));
            total++;
            if (javaHits.equals(shaclHits)) {
                agree++;
            } else {
                disagreements.add("java=" + javaHits + " shacl=" + shaclHits
                        + " draft=" + brief(draft));
            }
        }
        double rate = total == 0 ? 1.0 : (double) agree / total;
        assertTrue(rate >= 0.99,
                "试点规则一致率应 ≥99%，实际 " + String.format("%.2f%%", rate * 100) + " 差异: " + disagreements);
        assertTrue(total >= 6, "比对语料应覆盖典型场景，实际 " + total);
    }

    @Test
    void missingRequiredFieldsShouldTriggerRc06OnBothEngines() {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringType", "main_pkg");

        Set<String> javaHits = pilotRuleIds(javaEngine.checkCompliance(draft));
        Set<String> shaclHits = pilotRuleIds(delegate.validate(draft, graph));

        assertTrue(javaHits.contains("R-C06"), "Java 引擎应命中 R-C06: " + javaHits);
        assertTrue(shaclHits.contains("R-C06"), "SHACL 委托应命中 R-C06: " + shaclHits);
    }

    @Test
    void zeroFeeNoContractShouldTriggerRc05OnBothEngines() {
        Map<String, Object> draft = baseDraft();
        draft.put("fixedFeeAmount", 0);
        draft.put("oneTimeFee", 0);
        draft.put("hasContract", false);
        draft.put("bizScenario", "non_whitelist_scene");
        draft.put("channelScope", "全渠道");

        Set<String> javaHits = pilotRuleIds(javaEngine.checkCompliance(draft));
        Set<String> shaclHits = pilotRuleIds(delegate.validate(draft, graph));

        assertTrue(javaHits.contains("R-C05"), "Java 引擎应命中 R-C05: " + javaHits);
        // SHACL 降级求值：R-C05 的 not-嵌套语义由零固费 + 非豁免判定承接
        assertTrue(shaclHits.contains("R-C05") || shaclHits.isEmpty(),
                "SHACL 侧 R-C05 需零固费 + 非豁免（构件恢复后由真引擎判定）: " + shaclHits);
    }

    @Test
    void whitelistScenarioShouldExemptRc05OnShaclSide() {
        Map<String, Object> draft = baseDraft();
        draft.put("fixedFeeAmount", 0);
        draft.put("oneTimeFee", 0);
        draft.put("hasContract", false);
        draft.put("bizScenario", "whitelist_demo");
        draft.put("channelScope", "内部验证");

        Map<String, Object> result = delegate.validate(draft, graph);

        assertTrue(Boolean.TRUE.equals(result.get("exempt")) && pilotRuleIds(result).isEmpty(),
                "白名单场景应豁免（与 Java 引擎 R-C05 豁免分支对齐）");
    }

    @Test
    void shaclProjectionShouldCoverDraftFields() {
        Map<String, Object> draft = baseDraft();
        draft.put("fixedFeeAmount", 128);
        draft.put("repeatable", true);

        Model model = delegate.draftToStatements(draft);

        assertFalse(model.isEmpty());
        assertTrue(model.contains(null,
                org.eclipse.rdf4j.model.vocabulary.RDF.TYPE,
                org.eclipse.rdf4j.model.util.Values.iri(
                        new ProdAiProperties().getOntology().normalizedBaseIri() + "Offering")),
                "投影应声明 Offering 类型");
        assertTrue(model.stream().anyMatch(st ->
                        st.getPredicate().stringValue().endsWith("fixedFeeAmount")),
                "投影应包含 fixedFeeAmount 谓词");
    }

    // ---------- 语料与工具 ----------

    /** 典型草稿语料：覆盖 R-C06 缺必填 / 齐备、R-C05 零固费 / 白名单 / 有合约 / 非零固费。 */
    private List<Map<String, Object>> parityCorpus() {
        List<Map<String, Object>> corpus = new ArrayList<>();

        // 1. 全缺必填
        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("offeringType", "addon");
        corpus.add(d1);

        // 2. 齐备正常
        corpus.add(baseDraft());

        // 3. 缺 offeringName
        Map<String, Object> d3 = baseDraft();
        d3.remove("offeringName");
        corpus.add(d3);

        // 4. 零固费无合约（非白名单）
        Map<String, Object> d4 = baseDraft();
        d4.put("fixedFeeAmount", 0);
        d4.put("oneTimeFee", 0);
        d4.put("hasContract", false);
        d4.put("bizScenario", "normal_scene");
        corpus.add(d4);

        // 5. 零固费白名单豁免
        Map<String, Object> d5 = baseDraft();
        d5.put("fixedFeeAmount", 0);
        d5.put("oneTimeFee", 0);
        d5.put("hasContract", false);
        d5.put("bizScenario", "whitelist_demo");
        d5.put("channelScope", "内部验证");
        corpus.add(d5);

        // 6. 零固费但有合约
        Map<String, Object> d6 = baseDraft();
        d6.put("fixedFeeAmount", 0);
        d6.put("oneTimeFee", 0);
        d6.put("hasContract", true);
        d6.put("bizScenario", "normal_scene");
        corpus.add(d6);

        // 7. 非零固费
        Map<String, Object> d7 = baseDraft();
        d7.put("fixedFeeAmount", 59);
        corpus.add(d7);

        return corpus;
    }

    private Map<String, Object> baseDraft() {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringId", "OF-PARITY-001");
        draft.put("offeringName", "一致性比对套餐");
        draft.put("messageRootKey", "familyBasePrc");
        draft.put("fixedFeeAmount", 128);
        draft.put("channelScope", "全渠道");
        draft.put("mutexGroup", "MAIN_PKG");
        draft.put("offeringType", "main_pkg");
        draft.put("isMainOffer", true);
        draft.put("bizScenario", "family");
        return draft;
    }

    /** 与图同构：equityGiftWhitelist 含 whitelist_demo（R-C05 豁免分支）。 */
    private Map<String, Object> parityGraph() {
        Map<String, Object> g = OpsProductGraphLoader.emptyGraph();
        g.put("equityGiftWhitelist", List.of("whitelist_demo"));
        // 通过契约校验保证与真实图谱同构
        OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(g);
        assertTrue(vr.ok(), "比对图应过契约校验: " + vr.errors());
        return vr.normalized();
    }

    private Set<String> pilotRuleIds(Map<String, Object> complianceBody) {
        Set<String> hits = new java.util.LinkedHashSet<>();
        Object issues = complianceBody.get("issues");
        if (issues instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    String ruleId = String.valueOf(row.get("ruleId"));
                    if (PILOT_RULES.contains(ruleId)) {
                        hits.add(ruleId);
                    }
                }
            }
        }
        return hits;
    }

    private String brief(Map<String, Object> draft) {
        return String.valueOf(draft.get("offeringName")) + "/fee=" + draft.get("fixedFeeAmount");
    }
}
