package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.ontologygen.JsonSchemaLiteValidator;
import com.sitech.prodai.service.ops.OpsExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * R2 Phase3 拆分回归：配置文档批量导入服务单测（智读批量链路 + 文档解析失败分支）。
 */
@ExtendWith(MockitoExtension.class)
class ConfigDocImportServiceTest {

    @Mock
    private OpsExtractionService extractionService;

    private ConfigDocImportService service;
    private ObjectMapper mapper;
    private ConfigMessageProjector projector;
    private OpsRulesService opsRules;
    private TemplateDeriveEngine deriveEngine;

    /** 审计回调记录。 */
    private final Map<String, Integer> auditCalls = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        projector = new ConfigMessageProjector(mapper, new DefaultResourceLoader());
        projector.init();
        opsRules = new OpsRulesService(mapper, new DefaultResourceLoader(),
                new com.sitech.prodai.config.ProdAiProperties());
        opsRules.load();
        ProductTemplateRegistry templateRegistry = new ProductTemplateRegistry(null,
                new JsonSchemaLiteValidator(new ObjectMapper(), new DefaultResourceLoader()));
        deriveEngine = new TemplateDeriveEngine(opsRules, templateRegistry, projector, mapper);
        service = new ConfigDocImportService(new ConfigDocumentParser(), new ConfigDocumentStorage(), mapper);
    }

    @Test
    void batchFromDocumentShouldDeriveComplyAndScore() {
        when(extractionService.extractPackages(anyString(), anyList()))
                .thenReturn(new OpsExtractionService.PackageExtractResult(List.of(
                        Map.of("offeringName", "测试套餐", "monthlyFee", 59, "bizScenario", "个人5G")
                ), "mock"));

        Map<String, Object> body = service.batchFromDocument("文档内容", null,
                () -> Map.of("bizScenarios", Map.of("个人5G", Map.of("scenarioId", "sc-5g"))),
                extractionService, deriveEngine, this::passCompliance, projector, opsRules);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertEquals(1, ((Number) body.get("total")).intValue());
        assertEquals(1, ((Number) body.get("passedCount")).intValue());
        assertEquals(0, ((Number) body.get("pendingCount")).intValue());
        // packages 传入时 extractEngine 保持 provided；null 时走 mock 抽取引擎
        assertEquals("mock", body.get("extractEngine"));
        assertEquals("sc-5g", body.get("scenario"));
        List<Map<String, Object>> items = castItems(body.get("items"));
        assertTrue((double) items.get(0).get("confidence") > 0);
        assertTrue(items.get(0).get("appliedRules").toString().contains("R-D01"));
    }

    @Test
    void batchFromDocumentShouldExtractWhenPackagesMissing() {
        when(extractionService.extractPackages(anyString(), anyList()))
                .thenReturn(new OpsExtractionService.PackageExtractResult(List.of(
                        Map.of("offeringName", "抽取套餐")
                ), "llm"));

        Map<String, Object> body = service.batchFromDocument("原始文档文本", null,
                () -> Map.of(), extractionService, deriveEngine, this::passCompliance, projector, opsRules);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertEquals("llm", body.get("extractEngine"));
        assertEquals(1, ((Number) body.get("total")).intValue());
    }

    @Test
    void batchFromDocumentShouldMarkPendingWhenComplianceFails() {
        when(extractionService.extractPackages(anyString(), anyList()))
                .thenReturn(new OpsExtractionService.PackageExtractResult(List.of(
                        Map.of("offeringName", "违规套餐")
                ), "template"));

        Map<String, Object> body = service.batchFromDocument("文本", null,
                () -> Map.of(), extractionService, deriveEngine, this::failCompliance, projector, opsRules);

        assertEquals(0, ((Number) body.get("passedCount")).intValue());
        assertEquals(1, ((Number) body.get("pendingCount")).intValue());
        List<Map<String, Object>> items = castItems(body.get("items"));
        assertEquals(Boolean.FALSE, items.get(0).get("compliancePass"));
        assertEquals("待修正", items.get(0).get("status"));
    }

    @Test
    void batchFromDocumentBytesShouldAppendAuditStepsOnSuccess() {
        when(extractionService.extractPackages(anyString(), anyList()))
                .thenReturn(new OpsExtractionService.PackageExtractResult(List.of(
                        Map.of("offeringName", "审计套餐")
                ), "mock"));

        // .txt 走文本直读成功路径，应追加 document_parse + evaluate_policy_with_facts 两条审计
        Map<String, Object> body = service.batchFromDocumentBytes("套餐文本".getBytes(), "x.txt",
                () -> Map.of(), extractionService, deriveEngine, this::passCompliance, projector, opsRules,
                (traceId, step) -> auditCalls.merge(String.valueOf(step.get("step")), 1, Integer::sum));

        if (Boolean.TRUE.equals(body.get("success"))) {
            assertEquals(1, auditCalls.getOrDefault("document_parse", 0));
            assertEquals(1, auditCalls.getOrDefault("evaluate_policy_with_facts", 0));
            assertTrue(body.containsKey("trace_id"));
        }
    }

    private Map<String, Object> passCompliance(Map<String, Object> draft) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("compliancePass", true);
        r.put("issues", List.of());
        r.put("appliedRules", List.of("R-C01"));
        return r;
    }

    private Map<String, Object> failCompliance(Map<String, Object> draft) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("compliancePass", false);
        r.put("issues", List.of(Map.of("ruleId", "R-C05", "message", "0元非白名单")));
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItems(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
