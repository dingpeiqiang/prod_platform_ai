package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.mapper.OntologyAssetVersionMapper;
import com.sitech.prodai.mapper.OntologyInstanceMapper;
import com.sitech.prodai.mapper.OntologyVersionLogMapper;
import com.sitech.prodai.mapper.OpsWorkOrderMapper;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 智读回归：家庭融合测试稿应映射 3 条草稿（1 通过 / 2 待修正）。
 */
@ExtendWith(MockitoExtension.class)
class ZhiduBatchRegressionTest {

    @Mock
    private OpsSwrlReasoner opsSwrlReasoner;
    @Mock
    private Rdf4jOntologyStore rdf4jStore;
    @Mock
    private HttpOpsProductDataSource httpSource;
    @Mock
    private OpsWorkOrderMapper workOrderMapper;
    @Mock
    private OntologyInstanceMapper instanceMapper;
    @Mock
    private OntologyAssetVersionMapper assetVersionMapper;
    @Mock
    private OntologyVersionLogMapper versionLogMapper;

    private ProductOntologyService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
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

        OntologyVersionService versionService =
                new OntologyVersionService(assetVersionMapper, versionLogMapper);
        TemplateDeriveEngine deriveEngine =
                new TemplateDeriveEngine(opsRules, templateRegistry, projector, mapper);
        // P1-7：延迟解析回归运行器（reloadGraph SMOKE 回接），规避构造循环
        @SuppressWarnings("unchecked")
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
                workOrderMapper,
                instanceMapper,
                projector,
                new LastKnownGoodGuard(versionService),
                versionService,
                new RiskAuditService(),
                deriveEngine,
                new FactGraphSyncService(rdf4jStore),
                new LlmIntentExtractor(Optional.empty(), mapper),
                new SparqlConfigDiscoverer(rdf4jStore),
                regressionProvider
        );
        service.init();
        lazyRegression.set(new ProductConfigRegressionService(
                mapper, resourceLoader, service, projector, deriveEngine));
    }

    @Test
    @SuppressWarnings("unchecked")
    void familyFusionPasteMapsThreeDrafts() throws Exception {
        // 粘贴版夹具：仅含 A/B/C 三段套餐原文
        String text = Files.readString(Path.of("src/test/resources/testdata/zhidu_paste.txt"));
        Map<String, Object> body = service.batchFromDocument(text, null);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertEquals(3, body.get("total"), "应映射 3 条套餐草稿");
        int passed = ((Number) body.get("passedCount")).intValue();
        int pending = ((Number) body.get("pendingCount")).intValue();
        assertTrue(passed >= 1, "至少套餐A应合规通过，实际 passed=" + passed);
        assertTrue(pending >= 1, "零资费/缺月费应待修正，实际 pending=" + pending);
        assertEquals(3, passed + pending);

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertEquals(3, items.size());
        assertTrue(items.stream().allMatch(i -> i.get("confidence") != null));
        assertTrue(items.stream().anyMatch(i -> Boolean.TRUE.equals(i.get("needsConfirm"))
                || !Boolean.TRUE.equals(i.get("compliancePass"))));

        Map<String, Object> draftA = (Map<String, Object>) items.get(0).get("draft");
        Map<String, Object> draftB = (Map<String, Object>) items.get(1).get("draft");
        Map<String, Object> draftC = (Map<String, Object>) items.get(2).get("draft");

        assertEquals("家庭融合畅享158", String.valueOf(draftA.get("offeringName")));
        assertEquals(158.0, ((Number) draftA.get("monthlyFee")).doubleValue(), 0.01);
        assertEquals(158.0, ((Number) draftA.get("fixedFeeAmount")).doubleValue(), 0.01,
                "固费应与文档月费158对齐，不应残留模板128");
        assertEquals("40GB", String.valueOf(draftA.get("includeData")));
        assertEquals("500分钟", String.valueOf(draftA.get("includeVoice")));
        assertEquals("500M", String.valueOf(draftA.get("includeBroadband")));
        assertEquals("12", String.valueOf(draftA.get("contractMonths")));
        assertTrue(emptyish(draftA.get("bindExistingMainPkg")),
                "套餐名含「家庭融合畅享」不应误绑 OF-HF-128, actual=" + draftA.get("bindExistingMainPkg"));
        assertEquals("familyBasePrc", String.valueOf(draftA.get("categoryCode")));
        assertEquals("familyBasePrc", String.valueOf(draftA.get("messageRootKey")));
        assertEquals("TPL-FAMILY-BASE-128", String.valueOf(draftA.get("basedOnTemplate")));
        assertTrue(String.valueOf(draftA.get("successSmsImmediate")).contains("家庭融合畅享158"),
                "短信应使用当前套餐名，不应残留校园样例");
        Map<String, Object> msgA = (Map<String, Object>) items.get(0).get("messagePreview");
        assertTrue(msgA.containsKey("familyBasePrc"), "套餐A 报文根键应为 familyBasePrc, actual=" + msgA.keySet());

        assertEquals("家庭体验0元流量包", String.valueOf(draftB.get("offeringName")));
        assertEquals("家庭融合", String.valueOf(draftB.get("bizScenario")),
                "「5GB」不应误判为 5G 个人场景");
        assertEquals("5GB", String.valueOf(draftB.get("includeData")));
        assertEquals(0.0, ((Number) draftB.get("monthlyFee")).doubleValue(), 0.01);
        assertEquals("addon", String.valueOf(draftB.get("offeringType")));
        assertEquals("familyAddPrc", String.valueOf(draftB.get("categoryCode")));
        assertEquals("familyAddPrc", String.valueOf(draftB.get("messageRootKey")));
        assertEquals("TPL-FAMILY-ADD-20", String.valueOf(draftB.get("basedOnTemplate")),
                "加装包不应套用家庭基础128模板");
        assertEquals("ADDON", String.valueOf(draftB.get("mutexGroup")));
        assertTrue(String.valueOf(draftB.get("successSmsImmediate")).contains("家庭体验0元流量包"));
        Map<String, Object> msgB = (Map<String, Object>) items.get(1).get("messagePreview");
        assertTrue(msgB.containsKey("familyAddPrc"), "套餐B 报文根键应为 familyAddPrc, actual=" + msgB.keySet());

        assertEquals("家庭融合加装包", String.valueOf(draftC.get("offeringName")));
        assertEquals("addon", String.valueOf(draftC.get("offeringType")));
        assertEquals("familyAddPrc", String.valueOf(draftC.get("categoryCode")));
        assertEquals("familyAddPrc", String.valueOf(draftC.get("messageRootKey")));
        assertEquals("TPL-FAMILY-ADD-20", String.valueOf(draftC.get("basedOnTemplate")));
        assertTrue(emptyish(draftC.get("monthlyFee")),
                "套餐C 未写月费，不应被场景默认灌入, actual=" + draftC.get("monthlyFee"));
        assertTrue(emptyish(draftC.get("includeData")),
                "套餐C 不应灌入模板流量, actual=" + draftC.get("includeData"));
        assertEquals("宽带", String.valueOf(draftC.get("dependOn")));
        Map<String, Object> msgC = (Map<String, Object>) items.get(2).get("messagePreview");
        assertTrue(msgC.containsKey("familyAddPrc"), "套餐C 报文根键应为 familyAddPrc, actual=" + msgC.keySet());
    }

    private static boolean emptyish(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    @Test
    void compareSchemesReturnsRecommendation() {
        Map<String, Object> draft = Map.of(
                "offeringName", "5G体验套餐",
                "offeringType", "main_pkg",
                "bizScenario", "个人5G",
                "targetUser", "个人客户",
                "channelScope", "全渠道",
                "hasContract", "1",
                "contractMonths", 12,
                "repeatable", "false",
                "mutexGroup", "MAIN_PKG"
        );
        Map<String, Object> body = service.compareConfigSchemes(Map.of(
                "draft", draft,
                "text", "对比方案A 39元与方案B 59元，目标市场约15万户"
        ));
        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertTrue(body.get("comparisons") instanceof List<?>);
        assertTrue(((List<?>) body.get("comparisons")).size() >= 2);
        assertTrue(body.get("explanation") != null && !String.valueOf(body.get("explanation")).isBlank());
        assertTrue(body.get("recommended") instanceof Map<?, ?>);
    }
}
