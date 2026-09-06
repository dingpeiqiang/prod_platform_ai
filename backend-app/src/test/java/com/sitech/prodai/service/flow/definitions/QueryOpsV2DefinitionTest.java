package com.sitech.prodai.service.flow.definitions;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.domain.entity.Workflow;
import com.sitech.prodai.domain.entity.WorkflowExecution;
import com.sitech.prodai.mapper.WorkflowExecutionMapper;
import com.sitech.prodai.mapper.WorkflowMapper;
import com.sitech.prodai.mapper.WorkflowNodeLogMapper;
import com.sitech.prodai.service.ToolExecutionService;
import com.sitech.prodai.service.WorkflowService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.flow.ConditionEvaluator;
import com.sitech.prodai.service.flow.FlowDefinitionValidator;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W5 场景工作流定义测试：query_reuse_v2（查询复用）+ ops_analysis_v2（运营问诊）。
 * <p>
 * 覆盖：定义期守门（G2/G3 工具契约）、Seeder 幂等落库、快乐路径（工具调用顺序）、
 * 全 fail-fast 语义（任一工具失败 → 整单 failed）。
 * <p>
 * 断言约定（与 ChatConfigureV2DefinitionTest 一致）：引擎 startExecution 总是
 * ApiResponse.ok（启动成功），失败体现在 data.status="failed" + data.error_message。
 */
@ExtendWith(MockitoExtension.class)
class QueryOpsV2DefinitionTest {

    @Mock
    private WorkflowMapper workflowMapper;
    @Mock
    private WorkflowExecutionMapper executionMapper;
    @Mock
    private WorkflowNodeLogMapper nodeLogMapper;
    @Mock
    private ToolExecutionService toolExecutionService;
    @Mock
    private WorkflowService workflowService;

    private final Map<String, WorkflowExecution> executionStore = new ConcurrentHashMap<>();

    private FlowDefinitionValidator validator;
    private FlowEngineService engine;

    @BeforeEach
    void setUp() {
        validator = new FlowDefinitionValidator(toolExecutionService);
        engine = new FlowEngineService(workflowMapper, executionMapper, nodeLogMapper,
                toolExecutionService, validator, new ConditionEvaluator(),
                params -> "{}",
                (url, method, body) -> org.springframework.http.ResponseEntity.ok("{}"),
                formCode -> null);
    }

    // ── 1. 定义期守门：四个定义都过带工具注册表的 validator（G2/G3 生效） ──

    @Test
    void allSceneWorkflowDefinitionsPassDefinitionValidation() {
        stubRegistryWhitelist();

        for (String code : SceneWorkflowDefinitions.allCodes()) {
            FlowDefinitionValidator.ValidationResult check = validator.validate(SceneWorkflowDefinitions.definition(code));
            assertTrue(check.valid(), () -> "定义 " + code + " 应过定义期校验: " + check.problems());
        }
    }

    // ── 2. Seeder：逐个落库（allCodes 含四个编码） ──

    @Test
    void seederCoversAllFourDefinitions() {
        stubRegistryWhitelist();
        when(workflowService.createWorkflow(any(), eq("system"))).thenReturn(ApiResponse.ok(Map.of()));
        SceneWorkflowSeeder seeder = new SceneWorkflowSeeder(workflowService, validator);

        seeder.seed();

        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(workflowService, org.mockito.Mockito.times(SceneWorkflowDefinitions.allCodes().size()))
                .createWorkflow(captor.capture(), eq("system"));
        List<Object> codes = captor.getAllValues().stream().map(p -> p.get("workflowCode")).toList();
        assertTrue(codes.contains(SceneWorkflowDefinitions.QUERY_REUSE_CODE), "应落库查询复用流程: " + codes);
        assertTrue(codes.contains(SceneWorkflowDefinitions.OPS_ANALYSIS_CODE), "应落库运营问诊流程: " + codes);
    }

    // ── 3. query_reuse_v2 快乐路径：discover → sparql → compare 线性执行 ──

    @Test
    void queryReuseHappyPathExecutesToolsInOrder() {
        stubWorkflow(SceneWorkflowDefinitions.QUERY_REUSE_CODE, SceneWorkflowDefinitions.queryReuse());
        stubExecutionPersistence();
        stubRegistryWhitelist();
        when(toolExecutionService.execute(eq("rd_config_discover"), any()))
                .thenReturn(ExecutionResult.ok("rd_config_discover", Map.of("nl_answer", "命中2条", "items", List.of(Map.of("name", "家庭融合158")))));
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("nl_answer", "查到3条事实", "raw_results", List.of(Map.of("s", "a")))));
        when(toolExecutionService.execute(eq("rd_scheme_compare"), any()))
                .thenReturn(ExecutionResult.ok("rd_scheme_compare", Map.of("nl_answer", "推荐方案A", "comparisons", List.of())));

        ApiResponse<Map<String, Object>> start = engine.startExecution(
                SceneWorkflowDefinitions.QUERY_REUSE_CODE, null,
                Map.of("question", "找一下家庭融合套餐并对比资费"), "tester");

        assertTrue(start.isSuccess(), () -> "启动应成功: " + start.getMessage());
        assertEquals("completed", start.getData().get("status"), "纯查询链路无人工节点，应一次跑完");

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(toolExecutionService);
        inOrder.verify(toolExecutionService).execute(eq("rd_config_discover"), any());
        inOrder.verify(toolExecutionService).execute(eq("sparql_query"), any());
        inOrder.verify(toolExecutionService).execute(eq("rd_scheme_compare"), any());
    }

    // ── 4. query_reuse_v2 fail-fast：sparql 失败 → 整单 failed，compare 不执行 ──

    @Test
    void queryReuseToolFailureFailsWholeExecution() {
        stubWorkflow(SceneWorkflowDefinitions.QUERY_REUSE_CODE, SceneWorkflowDefinitions.queryReuse());
        stubExecutionPersistence();
        stubRegistryWhitelist();
        when(toolExecutionService.execute(eq("rd_config_discover"), any()))
                .thenReturn(ExecutionResult.ok("rd_config_discover", Map.of("nl_answer", "命中2条", "items", List.of())));
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.fail("sparql_query", "知识库不可用"));

        ApiResponse<Map<String, Object>> start = engine.startExecution(
                SceneWorkflowDefinitions.QUERY_REUSE_CODE, null,
                Map.of("question", "查一下校园套餐"), "tester");

        assertTrue(start.isSuccess(), "启动本身成功，失败体现在实例状态");
        assertEquals("failed", start.getData().get("status"), "fail-fast：任一工具失败整单失败");
        String error = String.valueOf(start.getData().get("error_message"));
        assertTrue(error.contains("sparql") || error.contains("失败"),
                () -> "错误信息应指明失败节点: " + error);
        verify(toolExecutionService, org.mockito.Mockito.never()).execute(eq("rd_scheme_compare"), any());
    }

    // ── 5. ops_analysis_v2 快乐路径：sparql → root-cause → risk-audit → explain 线性执行 ──

    @Test
    void opsAnalysisHappyPathExecutesToolsInOrder() {
        stubWorkflow(SceneWorkflowDefinitions.OPS_ANALYSIS_CODE, SceneWorkflowDefinitions.opsAnalysis());
        stubExecutionPersistence();
        stubRegistryWhitelist();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("nl_answer", "查到5个商品", "raw_results", List.of())));
        when(toolExecutionService.execute(eq("swrl_root_cause"), any()))
                .thenReturn(ExecutionResult.ok("swrl_root_cause", Map.of("nl_answer", "根因：资费超同类均值")));
        when(toolExecutionService.execute(eq("swrl_risk_audit"), any()))
                .thenReturn(ExecutionResult.ok("swrl_risk_audit", Map.of("nl_answer", "2个商品命中风险", "items", List.of())));
        when(toolExecutionService.execute(eq("ontology_explain"), any()))
                .thenReturn(ExecutionResult.ok("ontology_explain", Map.of("natural_language", "风险等级依据本体规则解释")));

        ApiResponse<Map<String, Object>> start = engine.startExecution(
                SceneWorkflowDefinitions.OPS_ANALYSIS_CODE, null,
                Map.of("question", "分析一下哪些商品有下架风险"), "tester");

        assertTrue(start.isSuccess(), () -> "启动应成功: " + start.getMessage());
        assertEquals("completed", start.getData().get("status"), "问诊链路无人工节点，应一次跑完");

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(toolExecutionService);
        inOrder.verify(toolExecutionService).execute(eq("sparql_query"), any());
        inOrder.verify(toolExecutionService).execute(eq("swrl_root_cause"), any());
        inOrder.verify(toolExecutionService).execute(eq("swrl_risk_audit"), any());
        inOrder.verify(toolExecutionService).execute(eq("ontology_explain"), any());
    }

    // ── 6. ops_analysis_v2 fail-fast：root-cause 失败 → 整单 failed，后续不执行 ──

    @Test
    void opsAnalysisToolFailureFailsWholeExecution() {
        stubWorkflow(SceneWorkflowDefinitions.OPS_ANALYSIS_CODE, SceneWorkflowDefinitions.opsAnalysis());
        stubExecutionPersistence();
        stubRegistryWhitelist();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("nl_answer", "查到5个商品", "raw_results", List.of())));
        when(toolExecutionService.execute(eq("swrl_root_cause"), any()))
                .thenReturn(ExecutionResult.fail("swrl_root_cause", "推理引擎超时"));

        ApiResponse<Map<String, Object>> start = engine.startExecution(
                SceneWorkflowDefinitions.OPS_ANALYSIS_CODE, null,
                Map.of("question", "分析商品风险"), "tester");

        assertTrue(start.isSuccess(), "启动本身成功，失败体现在实例状态");
        assertEquals("failed", start.getData().get("status"), "fail-fast：任一工具失败整单失败");
        verify(toolExecutionService, org.mockito.Mockito.never()).execute(eq("swrl_risk_audit"), any());
        verify(toolExecutionService, org.mockito.Mockito.never()).execute(eq("ontology_explain"), any());
    }

    // ── 测试脚手架（与 ChatConfigureV2DefinitionTest 同构） ──

    private Workflow workflow(String code, Map<String, Object> definition) {
        Workflow wf = new Workflow();
        wf.setId(code.hashCode());
        wf.setWorkflowCode(code);
        wf.setIsActive(true);
        wf.setVersion(1);
        wf.setWorkflowData(definition);
        return wf;
    }

    /** 单工作流打桩：selectList 恒返回该定义（本类各用例只启动单层流程，无子流程交替）。 */
    private void stubWorkflow(String code, Map<String, Object> definition) {
        Workflow wf = workflow(code, definition);
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(wf));
        lenient().when(workflowMapper.selectById(wf.getId())).thenReturn(wf);
    }

    private void stubExecutionPersistence() {
        executionStore.clear();
        lenient().doAnswer(inv -> {
            WorkflowExecution e = inv.getArgument(0);
            executionStore.put(e.getExecutionId(), e);
            return 1;
        }).when(executionMapper).insert(any(WorkflowExecution.class));
        lenient().doAnswer(inv -> {
            WorkflowExecution e = inv.getArgument(0);
            executionStore.merge(e.getExecutionId(), e, (oldV, newV) -> newV);
            return 1;
        }).when(executionMapper).updateById(any(WorkflowExecution.class));
        lenient().when(executionMapper.selectOne(any())).thenAnswer(inv -> {
            String targetId = extractExecutionId(inv.getArgument(0));
            if (targetId != null) {
                return executionStore.get(targetId);
            }
            return executionStore.values().stream().findFirst().orElse(null);
        });
    }

    private String extractExecutionId(Object wrapper) {
        if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> abstractWrapper) {
            Map<String, Object> pairs = abstractWrapper.getParamNameValuePairs();
            if (pairs != null && !pairs.isEmpty()) {
                return String.valueOf(pairs.values().iterator().next());
            }
        }
        return null;
    }

    /** G2/G3 打桩：W5 相关五工具全部已注册；getTool 返回 null = 跳过 G3 输出契约深度校验。 */
    private void stubRegistryWhitelist() {
        lenient().when(toolExecutionService.containsTool(any())).thenReturn(false);
        for (String tool : List.of("rd_config_discover", "rd_scheme_compare", "sparql_query",
                "swrl_root_cause", "swrl_risk_audit", "ontology_explain",
                "rd_compliance", "rd_draft_manage")) {
            lenient().when(toolExecutionService.containsTool(tool)).thenReturn(true);
        }
        lenient().when(toolExecutionService.getTool(any())).thenReturn(null);
    }
}
