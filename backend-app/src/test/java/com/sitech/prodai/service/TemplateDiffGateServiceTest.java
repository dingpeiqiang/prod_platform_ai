package com.sitech.prodai.service;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * P2-6 回归门禁验证（§13.4）：
 * <ul>
 *   <li>存量运行态双引擎 diff 全过（字段级 diff 空 + R-C* 命中一致 + 期望报文节点引擎侧成立）；</li>
 *   <li>SMOKE 聚合：P1-7 回归断言与 diff 门禁失败合并上报（坏图谱可阻断）；</li>
 *   <li>启用策略（§13.7）：R-GATE 关闭时漂移仅 shadow 记录，不产出门禁失败。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TemplateDiffGateServiceTest {

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

    private ProductOntologyService service;
    private ProductConfigRegressionService regressionService;
    private TemplateDiffGateService diffGate;
    private OpsRulesService opsRules;
    private ObjectMapper mapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = new ObjectMapper();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setDemoEnabled(true);
        properties.getOntology().setDataSource("classpath");
        properties.getOntology().setGraphPath("classpath:ontology/mock_graph.json");
        properties.getOntology().setRulesPath("classpath:ontology/ops_rules.json");
        properties.getOntology().setLlmExtractEnabled(false);

        opsRules = new OpsRulesService(mapper, resourceLoader, properties);
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
        ConfigMessageProjector projector = new ConfigMessageProjector(mapper, resourceLoader);
        projector.init();

        OntologyVersionService versionService = new OntologyVersionService(assetVersionRepository, versionLogRepository);
        ObjectProvider<ProductConfigRegressionService> regressionProvider = mock(ObjectProvider.class);
        AtomicReference<ProductConfigRegressionService> lazyRegression = new AtomicReference<>();
        lenient().when(regressionProvider.getObject()).thenAnswer(inv -> lazyRegression.get());
        service = new ProductOntologyService(
                mapper,
                properties,
                opsSwrlReasoner,
                opsRules,
                graphLoader,
                extractionService,
                new ConfigDocumentParser(),
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
        regressionService = new ProductConfigRegressionService(mapper, resourceLoader, service, projector);
        lazyRegression.set(regressionService);
        TemplateDeriveEngine deriveEngine = new TemplateDeriveEngine(opsRules, templateRegistry, projector, mapper);
        diffGate = new TemplateDiffGateService(regressionService, service, deriveEngine, projector, opsRules);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dualEngineDiffShouldFullyPassOnLiveState() {
        Map<String, Object> report = diffGate.runAll(null);

        assertTrue(Boolean.TRUE.equals(report.get("success")),
                "双引擎 diff 应全过，失败明细=" + report.get("failures"));
        assertTrue(Boolean.TRUE.equals(report.get("blocking")), "R-GATE 默认启用阻断");
        assertEquals(6, ((Number) report.get("total")).intValue());
        assertEquals(6, ((Number) report.get("parityPassedCount")).intValue());
        List<Map<String, Object>> cases = (List<Map<String, Object>>) report.get("cases");
        assertTrue(cases.stream().allMatch(c -> Boolean.TRUE.equals(c.get("parityPassed"))),
                "全部用例 parityPassed");
        assertTrue(((List<Map<String, Object>>) report.get("failures")).isEmpty(),
                "发布门禁判据=parityPassed+期望报文一致；当前用例集应全过。"
                        + "engineAdditions 为模板 set_default 接管项（fillSource=derive_rule），单独列示不计入 parity（§12.5/§13.4）");
    }

    @Test
    @SuppressWarnings("unchecked")
    void smokeShouldAggregateRegressionAndGateFailures() {
        assertTrue(diffGate.smokeFailures(null).isEmpty(), "现行运行态 SMOKE 应直通");

        Map<String, Object> broken = new LinkedHashMap<>(service.loadGraph());
        broken.remove("bizScenarios");
        broken.remove("templates");

        List<Map<String, Object>> failures = diffGate.smokeFailures(broken);

        assertFalse(failures.isEmpty(), "坏图谱应聚合出失败断言");
        assertTrue(failures.stream().anyMatch(f -> "family_fusion_main_128".equals(String.valueOf(f.get("caseId")))),
                "应含 P1-7 回归断言失败");
    }

    @Test
    @SuppressWarnings("unchecked")
    void gateShouldTolerateDriftWhenBlockingDisabled() {
        ProductOntologyService driftedLegacy = spy(service);
        // 注入漂移：存量引擎结果 channelScope 改为异值 → valueMismatch 漂移（去字段只会变 engineAdditions，不计入 parity）
        doAnswer(inv -> {
            Map<String, Object> body = (Map<String, Object>) inv.callRealMethod();
            ((Map<String, Object>) body.get("draft")).put("channelScope", "营业前台");
            return body;
        }).when(driftedLegacy).inferFields(anyMap(), anyMap(), isNull());

        ProductConfigRegressionService shadowRegression =
                new ProductConfigRegressionService(mapper, new DefaultResourceLoader(), driftedLegacy, projector());
        OpsRulesService disabledRules = spy(opsRules);
        doReturn(false).when(disabledRules).isConfigEnabled("R-GATE");
        TemplateDeriveEngine deriveEngine = new TemplateDeriveEngine(
                disabledRules, registry(), projector(), mapper);
        TemplateDiffGateService shadowGate = new TemplateDiffGateService(
                shadowRegression, driftedLegacy, deriveEngine, projector(), disabledRules);

        // shadow：parity 面漂移被记录（parityPassedCount 掉出 total），但不产出门禁失败断言
        Map<String, Object> report = shadowGate.runAll(null);
        assertFalse(Boolean.TRUE.equals(report.get("blocking")));
        assertTrue(((Number) report.get("parityPassedCount")).intValue()
                        < ((Number) report.get("total")).intValue(),
                "漂移应被记录（parity 面掉出总数）");
        assertEquals(0, ((List<Map<String, Object>>) report.get("failures")).size(),
                "R-GATE 关闭时漂移仅 shadow 记录");
        // shadow SMOKE：仅当漂移同时击穿 P1-7 回归基线（channelScope 被断言）才报回归失败；
        // 门禁 diff 失败（check=derive_diff）在 shadow 下不得出现
        List<Map<String, Object>> shadowSmoke = shadowGate.smokeFailures(null);
        assertTrue(shadowSmoke.stream().noneMatch(f -> "derive_diff".equals(String.valueOf(f.get("check")))),
                "shadow 模式 SMOKE 不产出门禁 diff 失败断言");
        assertTrue(shadowSmoke.stream().allMatch(f -> !"derive_diff".equals(String.valueOf(f.get("check")))),
                "shadow 模式所有 smoke 失败均非门禁 diff 触发");

        // blocking：同一漂移产出门禁失败
        TemplateDiffGateService blockingGate = new TemplateDiffGateService(
                shadowRegression, driftedLegacy, deriveEngine, projector(), opsRules);
        Map<String, Object> blockingReport = blockingGate.runAll(null);
        assertTrue(((List<Map<String, Object>>) blockingReport.get("failures")).size() > 0,
                "阻断模式漂移应产生失败断言");
        assertFalse(Boolean.TRUE.equals(blockingReport.get("success")));
    }

    private ConfigMessageProjector projector() {
        ConfigMessageProjector projector = new ConfigMessageProjector(mapper, new DefaultResourceLoader());
        projector.init();
        return projector;
    }

    private ProductTemplateRegistry registry() {
        ProductTemplateRegistry registry = new ProductTemplateRegistry(mapper);
        registry.init();
        return registry;
    }
}
