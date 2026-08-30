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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * P2-4 合规裁剪验证（§11.4 务实档）：
 * <ul>
 *   <li>等价性：P1-7 六用例 裁剪后合规报告与存量 issues 逐条等价（ruleId/field/level/message）；</li>
 *   <li>裁剪生效：bindings 未声明的规则（如 R-C09）被剔除并重算 pass/appliedRules；</li>
 *   <li>轻量字段约束：P2-1 约束元数据对草稿已携带值做枚举/互斥校验（templateIssues 增量视图）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TemplateComplianceServiceTest {

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
    private TemplateDeriveEngine deriveEngine;
    private TemplateComplianceService complianceService;

    @BeforeEach
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
        deriveEngine = new TemplateDeriveEngine(opsRules, templateRegistry, projector, mapper);
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
                new RiskAuditService(),
                deriveEngine,
                regressionProvider
        );
        service.init();
        complianceService = new TemplateComplianceService(
                service, templateRegistry, new TemplateConstraintCompiler(templateRegistry), projector, opsRules);
    }

    @Test
    @SuppressWarnings("unchecked")
    void complianceReportShouldBeEquivalentToLegacyOnRegressionCaseSet() throws Exception {
        Map<String, Object> caseDoc = mapper.readValue(
                resourceLoader.getResource(CASES_PATH).getInputStream(),
                new TypeReference<Map<String, Object>>() { });
        List<Map<String, Object>> cases = (List<Map<String, Object>>) caseDoc.get("cases");

        List<String> failures = new ArrayList<>();
        for (Map<String, Object> c : cases) {
            String caseId = String.valueOf(c.get("case_id"));
            Map<String, Object> draft = (Map<String, Object>) deriveEngine.derive(
                    castMap(c.get("slots")), castMap(c.get("draft")), service.loadGraph()).get("draft");

            Map<String, Object> legacy = service.checkCompliance(draft);
            Map<String, Object> cut = complianceService.checkComplianceByTemplate(draft, null);
            Map<String, Object> equivalence = (Map<String, Object>) cut.get("equivalence");

            // 等价性：issues 逐条（ruleId/field/level/message）+ 结论一致
            assertEquals(legacy.get("issues"), cut.get("issues"),
                    caseId + " 裁剪后 issues 应与存量等价");
            assertEquals(legacy.get("compliancePass"), cut.get("compliancePass"),
                    caseId + " compliancePass 应一致");
            assertEquals(legacy.get("appliedRules"), cut.get("appliedRules"),
                    caseId + " appliedRules 应一致");
            if (!Boolean.TRUE.equals(equivalence.get("issuesEquivalent"))) {
                failures.add(caseId + " 等价性报告异常: " + equivalence);
            }
            assertFalse(cut.containsKey("canSubmit") && !Boolean.TRUE.equals(cut.get("canSubmit"))
                    && Boolean.TRUE.equals(legacy.get("canSubmit")), caseId + " canSubmit 不应漂移");
        }
        assertTrue(failures.isEmpty(), "等价性失败=" + failures);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cutShouldRemoveRulesOutsideBindingsAndRecompute() {
        // 构造存量命中：R-C09 资费越界（HIGH）→ 存量 fail；channelScope 给足避免 R-C06 必填干扰
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("categoryCode", "familyBasePrc");
        draft.put("messageRootKey", "familyBasePrc");
        draft.put("offeringName", "越界套餐");
        draft.put("channelScope", "全渠道");
        draft.put("fixedFeeAmount", 9999);

        Map<String, Object> legacy = service.checkCompliance(draft);
        assertFalse(Boolean.TRUE.equals(legacy.get("compliancePass")), "存量应命中 HIGH 拦截");

        // 模拟品类裁剪面剔除 R-C09：HIGH 消失后 pass 应重算
        Map<String, Object> legacyBody = new LinkedHashMap<>(legacy);
        Map<String, Object> cut = complianceService.applyCut(legacyBody, Set.of("R-C06"));
        assertEquals(Boolean.TRUE, cut.get("compliancePass"), "R-C09 被裁剪后应重新过审");
        assertTrue(((List<Map<String, Object>>) cut.get("issues")).isEmpty(), "R-C09 不在裁剪面内应全剔除");
        assertEquals(1, ((List<?>) cut.get("cutIssues")).size(), "R-C09 应进入裁剪记录");
        assertEquals(List.of("R-C08"), cut.get("appliedRules"), "pass 语义与存量一致（R-C08 开启标记）");
    }

    @Test
    @SuppressWarnings("unchecked")
    void templateFieldChecksShouldSurfaceEnumAndMutexViolations() {
        // calcMode 枚举越域 + chnClassLimit 跨互斥组（营业前台 vs 电子渠道）→ templateIssues 增量视图
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("categoryCode", "familyBasePrc");
        draft.put("messageRootKey", "familyBasePrc");
        draft.put("offeringName", "约束演示");
        draft.put("fixedFeeAmount", 128);
        draft.put("calcMode", "乱写的收费方式");
        draft.put("chnClassLimit", List.of("营业前台", "电子渠道"));

        Map<String, Object> body = complianceService.checkComplianceByTemplate(draft, null);
        List<Map<String, Object>> templateIssues = (List<Map<String, Object>>) body.get("templateIssues");

        assertTrue(templateIssues.stream().anyMatch(i -> "calcMode".equals(i.get("field"))
                        && "枚举值域".equals(i.get("issueType"))),
                "calcMode 枚举越域应被模板约束捕获: " + templateIssues);
        assertTrue(templateIssues.stream().anyMatch(i -> "chnClassLimit".equals(i.get("field"))
                        && "互斥组合".equals(i.get("issueType"))),
                "chnClassLimit 跨互斥组应被模板约束捕获: " + templateIssues);
        assertTrue(templateIssues.stream().allMatch(i -> "TEMPLATE_FIELD".equals(i.get("ruleId"))
                        && "LOW".equals(i.get("issueLevel"))),
                "模板字段约束应为独立 LOW 级增量视图");
        // 并存窗口不阻断：issues 与存量等价，compliancePass 不受 templateIssues 影响
        Map<String, Object> legacy = service.checkCompliance(draft);
        assertEquals(legacy.get("compliancePass"), body.get("compliancePass"),
                "templateIssues 不得影响 compliancePass（先验证后阻断）");
        assertEquals(Boolean.TRUE, ((Map<String, Object>) body.get("equivalence")).get("issuesEquivalent"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }
}
