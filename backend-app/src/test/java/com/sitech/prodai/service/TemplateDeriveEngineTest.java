package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.repository.OntologyAssetVersionRepository;
import com.sitech.prodai.repository.OntologyInstanceRepository;
import com.sitech.prodai.repository.OntologyVersionLogRepository;
import com.sitech.prodai.repository.OpsWorkOrderRepository;
import com.sitech.prodai.service.ops.ClasspathOpsProductDataSource;
import com.sitech.prodai.service.ops.HttpOpsProductDataSource;
import com.sitech.prodai.service.ops.OpsExtractionService;
import com.sitech.prodai.service.ops.OpsProductGraphLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * P2-3 灰度 diff 验证（§12.5 先并存后切换）：P1-7 回归用例集逐条跑
 * 存量 {@code inferFields} vs {@link TemplateDeriveEngine}，字段级 diff 全过。
 * <p>判据：{@code parityPassed}=true（核心推导面零漂移）；引擎增量仅限
 * fillSource=derive_rule 的模板 set_default 接管项（可追溯，评审归档）。
 */
@ExtendWith(MockitoExtension.class)
class TemplateDeriveEngineTest {

    private static final String CASES_PATH = "classpath:ontology/regression_cases.json";

    @Mock
    private OpsSwrlReasoner opsSwrlReasoner;
    @Mock
    private OpsWorkOrderRepository workOrderRepository;
    @Mock
    private OntologyInstanceRepository instanceRepository;
    @Mock
    private OntologyAssetVersionRepository assetVersionRepository;
    @Mock
    private OntologyVersionLogRepository versionLogRepository;
    @Mock
    private Rdf4jOntologyStore rdf4jStore;
    @Mock
    private HttpOpsProductDataSource httpSource;

    private ObjectMapper mapper;
    private ResourceLoader resourceLoader;
    private ProductOntologyService service;
    private TemplateDeriveEngine engine;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = new ObjectMapper();
        resourceLoader = new DefaultResourceLoader();
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setDemoEnabled(true);
        properties.getOntology().setDataSource("classpath");
        properties.getOntology().setGraphPath("classpath:ontology/mock_graph.json");
        properties.getOntology().setRulesPath("classpath:ontology/ops_rules.json");
        properties.getOntology().setLlmExtractEnabled(false);

        OpsRulesService opsRules = new OpsRulesService(mapper, resourceLoader, properties);
        opsRules.load();
        ClasspathOpsProductDataSource classpathSource =
                new ClasspathOpsProductDataSource(mapper, resourceLoader, properties);
        OpsProductGraphLoader graphLoader =
                new OpsProductGraphLoader(properties, classpathSource, httpSource);
        ProductTemplateRegistry templateRegistry = new ProductTemplateRegistry(mapper);
        templateRegistry.init();
        ProductExtractionTemplateSupport templateSupport =
                new ProductExtractionTemplateSupport(templateRegistry);
        OpsExtractionService extractionService =
                new OpsExtractionService(mapper, properties, opsRules, templateSupport, Optional.empty());
        ConfigDocumentParser documentParser = new ConfigDocumentParser();
        ConfigMessageProjector projector = new ConfigMessageProjector(mapper, resourceLoader);
        projector.init();

        OntologyVersionService versionService =
                new OntologyVersionService(assetVersionRepository, versionLogRepository);
        ObjectProvider<ProductConfigRegressionService> regressionProvider =
                mock(ObjectProvider.class);
        AtomicReference<ProductConfigRegressionService> lazyRegression = new AtomicReference<>();
        lenient().when(regressionProvider.getObject()).thenAnswer(inv -> lazyRegression.get());
        service = new ProductOntologyService(
                mapper,
                properties,
                opsSwrlReasoner,
                opsRules,
                graphLoader,
                extractionService,
                documentParser,
                new ConfigDocumentStorage(),
                rdf4jStore,
                workOrderRepository,
                instanceRepository,
                projector,
                new LastKnownGoodGuard(versionService),
                versionService,
                regressionProvider
        );
        service.init();
        engine = new TemplateDeriveEngine(opsRules, templateRegistry, projector, mapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deriveEngineShouldReachParityWithLegacyOnRegressionCaseSet() throws Exception {
        Map<String, Object> caseDoc = mapper.readValue(
                resourceLoader.getResource(CASES_PATH).getInputStream(),
                new TypeReference<Map<String, Object>>() { });
        List<Map<String, Object>> cases = (List<Map<String, Object>>) caseDoc.get("cases");
        assertEquals(6, cases.size(), "P1-7 回归用例集应完整加载");

        Map<String, Object> graph = service.loadGraph();
        List<String> failures = new ArrayList<>();
        for (Map<String, Object> c : cases) {
            String caseId = String.valueOf(c.get("case_id"));
            Map<String, Object> slots = castMap(c.get("slots"));
            Map<String, Object> draft = castMap(c.get("draft"));

            Map<String, Object> legacyBody = service.inferFields(slots, draft);
            Map<String, Object> engineBody = engine.derive(slots, draft, graph);
            Map<String, Object> report = DeriveDiffUtil.diff(legacyBody, engineBody);

            if (!Boolean.TRUE.equals(report.get("parityPassed"))) {
                failures.add(DeriveDiffUtil.summarize(caseId, report) + " detail=" + report);
            }
            // 引擎增量必须可追溯：全部来自模板 set_default（fillSource=derive_rule）
            for (Object item : (List<?>) report.get("engineAdditions")) {
                Map<String, Object> addition = (Map<String, Object>) item;
                if (!"derive_rule".equals(addition.get("trace"))) {
                    failures.add(caseId + " 不可追溯增量: " + addition);
                }
            }
        }
        assertTrue(failures.isEmpty(), "derive_rules 引擎应与存量推理语义对齐，失败=" + failures);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deriveEngineShouldExposeTemplateRuleViews() {
        Map<String, Object> graph = service.loadGraph();
        // 家庭融合主套餐：公共骨架 set_default（expDate/effRuleId 等）应被模板接管
        Map<String, Object> body = engine.derive(Map.of(),
                new LinkedHashMap<>(Map.of(
                        "offeringName", "全家享融合128",
                        "bizScenario", "家庭融合")), graph);

        assertEquals(Boolean.TRUE, body.get("success"));
        assertNotNull(body.get("visibility"), "显隐裁决视图应输出");
        assertNotNull(body.get("templateRulesApplied"), "derive_rules 执行记录应输出");

        List<String> applied = (List<String>) body.get("templateRulesApplied");
        assertTrue(applied.stream().anyMatch(r -> r.startsWith("set_default:expDate")),
                "模板 set_default 应执行，actual=" + applied);

        Map<String, Object> draft = (Map<String, Object>) body.get("draft");
        assertEquals("familyBasePrc", String.valueOf(draft.get("categoryCode")),
                "品类推导语义应与存量一致");
        assertEquals("20991231", String.valueOf(draft.get("expDate")),
                "公共骨架 set_default 应接管 expDate");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deriveEngineVisibilityShouldFollowWhenRules() {
        Map<String, Object> graph = service.loadGraph();
        // fixValidity=长期有效（模板 set_default 补全后）→ fixValidityVAlue 应隐藏
        Map<String, Object> body = engine.derive(Map.of(),
                new LinkedHashMap<>(Map.of(
                        "offeringName", "全家享融合128",
                        "bizScenario", "家庭融合")), graph);
        Map<String, Object> visibility = (Map<String, Object>) body.get("visibility");

        assertTrue("hidden".equals(String.valueOf(visibility.get("fixValidityVAlue"))),
                "fixValidity 长期有效时 fixValidityVAlue 应隐藏，visibility=" + visibility);
        // 显隐视图不得改动草稿值：fixValidityVAlue 不应被写入 draft
        Map<String, Object> draft = (Map<String, Object>) body.get("draft");
        assertFalse(draft.containsKey("fixValidityVAlue") && draft.get("fixValidityVAlue") != null,
                "显隐视图仅为渲染裁决，不得注入草稿值");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }
}
