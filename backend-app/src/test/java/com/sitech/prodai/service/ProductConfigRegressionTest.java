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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * P1-7 双品类验证：回归用例集断言全过（家庭行为回归不变）+ SMOKE 回接守卫（坏图谱注入可回退）。
 * <p>覆盖 P1 验收清单：
 * <ul>
 *   <li>用例集（家庭融合/校园/5G/宽带六典型）在现行运行态全过；</li>
 *   <li>reloadGraph 全链路（含 SMOKE）成功提交；</li>
 *   <li>人为注入坏图谱（剔除 bizScenarios/templates）→ SMOKE 阻断、COMMIT 不执行、last-known-good 保留。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProductConfigRegressionTest {

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
    private OntologyVersionService versionService;

    @BeforeEach
    @SuppressWarnings("unchecked")
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
        OpsExtractionService extractionService =
                new OpsExtractionService(mapper, properties, opsRules, Optional.empty());
        ConfigDocumentParser documentParser = new ConfigDocumentParser();
        ConfigMessageProjector projector = new ConfigMessageProjector(mapper, resourceLoader);
        // 单测无 Spring 容器：手动触发 @PostConstruct 装载投影配置（品类默认值/报文映射）
        projector.init();

        versionService = new OntologyVersionService(assetVersionRepository, versionLogRepository);
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
        regressionService = new ProductConfigRegressionService(mapper, resourceLoader, service, projector);
        lazyRegression.set(regressionService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void regressionCaseSetShouldFullyPassOnLiveState() {
        Map<String, Object> report = regressionService.runAll();

        assertTrue(Boolean.TRUE.equals(report.get("success")),
                "回归用例集应全过，失败明细=" + report.get("failures"));
        assertEquals(6, ((Number) report.get("total")).intValue());
        assertEquals(0, ((Number) report.get("failedCount")).intValue());

        List<Map<String, Object>> cases = (List<Map<String, Object>>) report.get("cases");
        // 双品类主体验证：familyBasePrc / broadBandMainPrc 全链路
        Map<String, Object> family = byCaseId(cases, "family_fusion_main_128");
        assertTrue(Boolean.TRUE.equals(family.get("passed")), "家庭主套餐用例失败: " + family.get("failures"));
        assertEquals("familyBasePrc", String.valueOf(family.get("messageRootKey")));
        Map<String, Object> broadband = byCaseId(cases, "broadband_main_500m");
        assertTrue(Boolean.TRUE.equals(broadband.get("passed")), "宽带主资费用例失败: " + broadband.get("failures"));
        assertEquals("broadBandMainPrc", String.valueOf(broadband.get("messageRootKey")));
        // 校园/5G 回归 + 负例（R-C04/R-C05 拦截）
        assertTrue(Boolean.TRUE.equals(byCaseId(cases, "campus_addon_19").get("passed")));
        assertFalse(Boolean.TRUE.equals(byCaseId(cases, "campus_trial_zero_fee").get("compliancePass")),
                "0元非白名单用例应被 R-C05 拦截");
        assertFalse(Boolean.TRUE.equals(byCaseId(cases, "family_fusion_addon_no_depend").get("compliancePass")),
                "缺主资费依赖用例应被 R-C04 拦截");
        assertTrue(Boolean.TRUE.equals(byCaseId(cases, "five_g_main_99").get("passed")));
    }

    @Test
    void smokeAgainstGoodPendingGraphShouldPass() {
        Map<String, Object> pending = new LinkedHashMap<>(service.loadGraph());

        List<Map<String, Object>> failures = regressionService.smokeAgainstGraph(pending);

        assertTrue(failures.isEmpty(), "pending 正常图谱 SMOKE 应直通，失败=" + failures);
    }

    @Test
    void smokeAgainstBrokenPendingGraphShouldBlock() {
        // 人为注入坏图谱：剔除场景与模板要素（模拟坏模板/坏图谱发布）
        Map<String, Object> broken = new LinkedHashMap<>(service.loadGraph());
        broken.remove("bizScenarios");
        broken.remove("templates");

        List<Map<String, Object>> failures = regressionService.smokeAgainstGraph(broken);

        assertFalse(failures.isEmpty(), "坏图谱应触发 SMOKE 断言失败");
        assertTrue(failures.stream().anyMatch(f -> "family_fusion_main_128".equals(String.valueOf(f.get("caseId")))),
                "家庭基线断言应命中坏图谱");
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardShouldRejectBrokenGraphAndRetainLastKnownGood() {
        // 模拟 reloadGraph 守卫接线：SMOKE 段用 P1-7 用例集（版本服务用 mock，专注守卫行为）
        OntologyVersionService mockedVersionService = mock(OntologyVersionService.class);
        LastKnownGoodGuard guard = new LastKnownGoodGuard(mockedVersionService);

        Map<String, Object> broken = new LinkedHashMap<>(service.loadGraph());
        broken.remove("bizScenarios");
        broken.remove("templates");
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("graph", broken);
        pending.put("sourceId", "broken-source");

        AtomicInteger commitCount = new AtomicInteger();
        LastKnownGoodGuard.GuardRequest request = LastKnownGoodGuard.GuardRequest
                .builder("abox_snapshot", "product_graph", () -> pending, p -> commitCount.incrementAndGet())
                .validator(p -> List.of())
                .smoke(p -> regressionService.smokeAgainstGraph(
                        p.get("graph") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of()))
                .version("broken-1")
                .payload("broken-payload")
                .build();

        Map<String, Object> report = guard.execute(request);

        assertFalse(Boolean.TRUE.equals(report.get("success")));
        assertEquals("SMOKE", report.get("step"), "应在 SMOKE 阶段阻断");
        assertEquals(0, commitCount.get(), "SMOKE 失败不得切换现行图谱（last-known-good 保留）");
        verify(mockedVersionService).register(any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void reloadGraphWithSmokeShouldCommitOnHealthyGraph() {
        Map<String, Object> report = service.reloadGraph();

        assertTrue(Boolean.TRUE.equals(report.get("success")),
                "正常图谱热重载（含 SMOKE）应提交，报告=" + report);
        assertEquals("COMMIT", report.get("step"));
        assertFalse(service.getGraphSummary().isEmpty(), "COMMIT 后 graphCache 应已切换");
    }

    private Map<String, Object> byCaseId(List<Map<String, Object>> cases, String caseId) {
        return cases.stream()
                .filter(c -> caseId.equals(String.valueOf(c.get("caseId"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少用例: " + caseId));
    }
}
