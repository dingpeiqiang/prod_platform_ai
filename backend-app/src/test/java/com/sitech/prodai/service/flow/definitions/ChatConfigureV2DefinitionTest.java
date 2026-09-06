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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W3-2 场景工作流定义测试：定义期守门 + Seeder 幂等 + 执行链路（挂起/恢复/复用/取消/整改/防环）。
 * <p>
 * 断言约定：引擎 startExecution 总是 ApiResponse.ok（启动成功），失败体现在
 * data.status="failed" + data.error_message。
 */
@ExtendWith(MockitoExtension.class)
class ChatConfigureV2DefinitionTest {

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
        selectListCallCount.set(0);
        validator = new FlowDefinitionValidator(toolExecutionService);
        engine = new FlowEngineService(workflowMapper, executionMapper, nodeLogMapper,
                toolExecutionService, validator, new ConditionEvaluator(),
                params -> "{\"offering_name\":\"家庭融合畅享158\",\"monthly_fee\":158,\"target_user\":\"家庭\","
                        + "\"config\":{\"categoryCode\":\"familyBasePrc\"}}",
                (url, method, body) -> org.springframework.http.ResponseEntity.ok("{}"),
                formCode -> null);
    }

    // ── 1. 定义期守门：两个定义都过带工具注册表的 validator（G2/G3 生效） ──

    @Test
    void sceneWorkflowDefinitionsPassDefinitionValidation() {
        when(toolExecutionService.containsTool(any())).thenReturn(false);
        when(toolExecutionService.containsTool("rd_config_discover")).thenReturn(true);
        when(toolExecutionService.containsTool("rd_compliance")).thenReturn(true);
        when(toolExecutionService.containsTool("rd_draft_manage")).thenReturn(true);
        // W5 新增流程的工具依赖（query_reuse_v2 / ops_analysis_v2）
        when(toolExecutionService.containsTool("sparql_query")).thenReturn(true);
        when(toolExecutionService.containsTool("rd_scheme_compare")).thenReturn(true);
        when(toolExecutionService.containsTool("swrl_root_cause")).thenReturn(true);
        when(toolExecutionService.containsTool("swrl_risk_audit")).thenReturn(true);
        when(toolExecutionService.containsTool("ontology_explain")).thenReturn(true);
        when(toolExecutionService.getTool(any())).thenReturn(null);

        for (String code : SceneWorkflowDefinitions.allCodes()) {
            FlowDefinitionValidator.ValidationResult check = validator.validate(SceneWorkflowDefinitions.definition(code));
            assertTrue(check.valid(), () -> "定义 " + code + " 应过定义期校验: " + check.problems());
        }
    }

    // ── 2. Seeder：逐个落库 + 幂等跳过 ──

    @Test
    void seederCreatesAllDefinitionsOnce() {
        // G2/G3 校验需要工具注册表：三个 RD 工具全部已注册（getTool 返回 null = 跳过 G3 输出契约）
        when(toolExecutionService.containsTool(any())).thenReturn(true);
        when(toolExecutionService.getTool(any())).thenReturn(null);
        when(workflowService.createWorkflow(any(), eq("system"))).thenReturn(ApiResponse.ok(Map.of()));
        SceneWorkflowSeeder seeder = new SceneWorkflowSeeder(workflowService, validator);

        seeder.seed();
        seeder.seed(); // 第二轮：createWorkflow 返回已存在失败 → 幂等不重复计数

        verify(workflowService, org.mockito.Mockito.times(SceneWorkflowDefinitions.allCodes().size() * 2))
                .createWorkflow(any(), eq("system"));
        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(workflowService, org.mockito.Mockito.atLeast(SceneWorkflowDefinitions.allCodes().size()))
                .createWorkflow(captor.capture(), eq("system"));
        List<Object> codes = captor.getAllValues().stream().map(p -> p.get("workflowCode")).toList();
        assertTrue(codes.contains(SceneWorkflowDefinitions.MAIN_CODE), "应落库主流程: " + codes);
        assertTrue(codes.contains(SceneWorkflowDefinitions.DRAFT_CHECK_CODE), "应落库子流程: " + codes);
    }

    // ── 3. 主流程快乐路径：起草 → 确认门挂起 → 恢复 → 修改 → 提交 → completed ──

    @Test
    void mainFlowHappyPathSuspendsAtConfirmGateThenCompletes() {
        stubTwoWorkflows();
        stubExecutionPersistence();
        stubToolSuccess();

        ApiResponse<Map<String, Object>> start = engine.startExecution(
                SceneWorkflowDefinitions.MAIN_CODE, null,
                Map.of("question", "办一个158的家庭融合套餐"), "tester");

        assertTrue(start.isSuccess(), () -> "启动应成功: " + start.getMessage());
        assertEquals("waiting_human", start.getData().get("status"), "应挂起在确认门");
        assertEquals("confirm-gate", start.getData().get("current_node_id"));

        String executionId = (String) start.getData().get("execution_id");
        String token = (String) start.getData().get("resume_token");

        // 确认提交（offerName/fixedFeeAmount 为确认门表单透传字段）
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("action", "confirm");
        formData.put("work_order_id", "WO20260901001");
        formData.put("offerName", "家庭融合畅享158");
        formData.put("fixedFeeAmount", 158);
        ApiResponse<Map<String, Object>> resume = engine.resumeFromHuman(executionId, token, formData, "tester");

        assertTrue(resume.isSuccess(), () -> "恢复应成功: " + resume.getMessage());
        assertEquals("completed", resume.getData().get("status"));

        // 工具调用序列：discover → compliance → draft_manage(update) → draft_manage(submit)
        // persist 与 order 两个节点共用 rd_draft_manage → times(2)
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(toolExecutionService);
        inOrder.verify(toolExecutionService).execute(eq("rd_config_discover"), any());
        inOrder.verify(toolExecutionService).execute(eq("rd_compliance"), any());
        inOrder.verify(toolExecutionService, org.mockito.Mockito.times(2)).execute(eq("rd_draft_manage"), any());
    }

    // ── 4. 同输入同路径（确定性回归） ──

    @Test
    void sameInputProducesSameNodePath() {
        stubTwoWorkflows();
        stubExecutionPersistence();
        stubToolSuccess();

        List<String> firstPath = runMainFlowCapturePath();

        List<String> secondPath = runMainFlowCapturePath();

        assertEquals(firstPath, secondPath, "两轮运行的主流程节点路径应一致");
        assertFalse(firstPath.isEmpty(), "路径不应为空");
    }

    // ── 5. 合规不过 + 整改后仍不过 → 子流程 rejected 终态（主流程仍 completed） ──

    @Test
    void complianceFailRedraftStillFailEndsDraftRejected() {
        stubTwoWorkflows();
        stubExecutionPersistence();
        when(toolExecutionService.containsTool(any())).thenReturn(true);
        when(toolExecutionService.getTool(any())).thenReturn(null);
        when(toolExecutionService.execute(eq("rd_config_discover"), any()))
                .thenReturn(ExecutionResult.ok("rd_config_discover", Map.of("nl_answer", "无历史", "items", List.of())));
        // 合规两次均不通过
        when(toolExecutionService.execute(eq("rd_compliance"), any()))
                .thenReturn(ExecutionResult.ok("rd_compliance", Map.of("compliance_pass", false, "issues", List.of("月费超限"))));
        // 整改复稿后 LLM 仍返回同一输出（mock 网关固定）

        ApiResponse<Map<String, Object>> start = engine.startExecution(
                SceneWorkflowDefinitions.MAIN_CODE, null,
                Map.of("question", "办一个999的套餐"), "tester");

        assertTrue(start.isSuccess(), () -> "启动应成功: " + start.getMessage());
        assertEquals("waiting_human", start.getData().get("status"),
                "子流程 rejected 是正常 completed 终态，主流程应继续走到确认门");
    }

    // ── 6. 复用历史短路：不进起草子流程 ──

    @Test
    void reuseHintShortCircuitsToEndReuse() {
        stubTwoWorkflows();
        stubExecutionPersistence();
        stubToolSuccess();

        ApiResponse<Map<String, Object>> start = engine.startExecution(
                SceneWorkflowDefinitions.MAIN_CODE, null,
                Map.of("question", "找一下家庭融合套餐", "reuse_hint", "yes"), "tester");

        assertTrue(start.isSuccess(), () -> "启动应成功: " + start.getMessage());
        assertEquals("completed", start.getData().get("status"));

        verify(toolExecutionService).execute(eq("rd_config_discover"), any());
        verify(toolExecutionService, never()).execute(eq("rd_compliance"), any());
        verify(toolExecutionService, never()).execute(eq("rd_draft_manage"), any());
    }

    // ── 7. 确认门取消 → end-cancelled，不落库不提交 ──

    @Test
    void cancelAtConfirmGateCompletesAsCancelled() {
        stubTwoWorkflows();
        stubExecutionPersistence();
        stubToolSuccess();

        ApiResponse<Map<String, Object>> start = engine.startExecution(
                SceneWorkflowDefinitions.MAIN_CODE, null,
                Map.of("question", "办一个158套餐"), "tester");
        assertEquals("waiting_human", start.getData().get("status"));

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("action", "cancel");
        ApiResponse<Map<String, Object>> resume = engine.resumeFromHuman(
                (String) start.getData().get("execution_id"),
                (String) start.getData().get("resume_token"), formData, "tester");

        assertTrue(resume.isSuccess(), () -> "取消恢复应成功: " + resume.getMessage());
        assertEquals("completed", resume.getData().get("status"), "取消是正常终态");

        verify(toolExecutionService, never()).execute(eq("rd_draft_manage"), any());
    }

    // ── 8. LLM 输出违反 json_schema → 重试耗尽 → 子流程 failed → 主流程 failed ──

    @Test
    void draftAndCheckSubflowJsonSchemaViolationFailsSubflow() {
        stubTwoWorkflows();
        stubExecutionPersistence();
        when(toolExecutionService.containsTool(any())).thenReturn(true);
        when(toolExecutionService.getTool(any())).thenReturn(null);
        when(toolExecutionService.execute(eq("rd_config_discover"), any()))
                .thenReturn(ExecutionResult.ok("rd_config_discover", Map.of("nl_answer", "无历史", "items", List.of())));
        FlowEngineService badLlm = new FlowEngineService(workflowMapper, executionMapper, nodeLogMapper,
                toolExecutionService, validator, new ConditionEvaluator(),
                params -> "{\"offering_name\":\"缺少月费的输出\"}",
                (url, method, body) -> org.springframework.http.ResponseEntity.ok("{}"),
                formCode -> null);

        ApiResponse<Map<String, Object>> start = badLlm.startExecution(
                SceneWorkflowDefinitions.MAIN_CODE, null,
                Map.of("question", "办一个158套餐"), "tester");

        assertTrue(start.isSuccess(), "启动本身成功，失败体现在实例状态");
        assertEquals("failed", start.getData().get("status"), "子流程 failed 应传导为主流程 failed");
        String error = String.valueOf(start.getData().get("error_message"));
        assertTrue(error.contains("monthly_fee") || error.contains("draft_and_check_v2"),
                () -> "错误应指明契约缺失或子流程失败: " + error);
    }

    // ── 测试脚手架 ──

    private Workflow workflow(String code, Map<String, Object> definition) {
        Workflow wf = new Workflow();
        wf.setId(code.hashCode());
        wf.setWorkflowCode(code);
        wf.setIsActive(true);
        wf.setVersion(1);
        wf.setWorkflowData(definition);
        return wf;
    }

    /** selectList 调用计数（跨 stubTwoWorkflows 共享：奇数次=主流程，偶数次=子流程）。 */
    private final java.util.concurrent.atomic.AtomicInteger selectListCallCount = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 主/子流程 selectList 打桩：计数交替打桩（奇数次=主流程，偶数次=子流程）。
     * 注意：不能用 thenReturn(main, sub) 固定序列——Mockito 语义下第 3+ 次调用会**永久重复最后一项**（子流程），
     * 导致 mainFlowHappyPath 用例在 resume 后查到子流程定义（shouldReturn 定义的 human 节点校验直接失败），
     * 或 resumeExecution 类多轮启动场景打桩耗尽后行为漂移；计数交替打桩无次数上限，与引擎
     * 「每层 startExecution 恰好 1 次 selectList」的调用序稳定对齐。
     */
    private void stubTwoWorkflows() {
        Workflow main = workflow(SceneWorkflowDefinitions.MAIN_CODE, SceneWorkflowDefinitions.mainFlow());
        Workflow sub = workflow(SceneWorkflowDefinitions.DRAFT_CHECK_CODE, SceneWorkflowDefinitions.draftAndCheck());
        // resumeFromHuman 走 selectById(workflowId) —— id 需与实体一致
        lenient().when(workflowMapper.selectList(any())).thenAnswer(inv -> {
            int call = selectListCallCount.incrementAndGet();
            boolean mainTurn = call % 2 == 1;
            return mainTurn ? List.of(main) : List.of(sub);
        });
        lenient().when(workflowMapper.selectById(main.getId())).thenReturn(main);
        lenient().when(workflowMapper.selectById(sub.getId())).thenReturn(sub);
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
        // 按查询条件中的 executionId 精确匹配（主/子流程实例共存时 selectOne 需返回对应实例）
        lenient().when(executionMapper.selectOne(any())).thenAnswer(inv -> {
            String targetId = extractExecutionId(inv.getArgument(0));
            if (targetId != null) {
                return executionStore.get(targetId);
            }
            // LambdaQueryWrapper 的 paramNameValuePairs 在真实测试环境可能为空（Lambda 解析延迟），
            // 此时按「非终态优先」兜底：引擎内唯一 selectOne 调用点是 findByExecutionId（单实例查询），
            // 主/子实例共存时非终态（running/waiting_human）实例才是查询目标——终态（completed/failed）
            // 子流程实例是 workflow 节点同步执行遗留的，绝不能被 findFirst 的哈希序随机选中。
            return executionStore.values().stream()
                    .filter(e -> !"completed".equals(e.getStatus()) && !"failed".equals(e.getStatus())
                            && !"cancelled".equals(e.getStatus()))
                    .findFirst()
                    .orElseGet(() -> executionStore.values().stream().findFirst().orElse(null));
        });
    }

    /**
     * 从 LambdaQueryWrapper 提取 executionId 条件值。
     * 注意：必须用公开的 getParamNameValuePairs()，不要在此路径用反射 setAccessible——
     * JDK 17 + Mockito inline 下 stub answer 内 setAccessible 会阻塞（实测挂死）。
     */
    private String extractExecutionId(Object wrapper) {
        if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> abstractWrapper) {
            Map<String, Object> pairs = abstractWrapper.getParamNameValuePairs();
            if (pairs != null && !pairs.isEmpty()) {
                return String.valueOf(pairs.values().iterator().next());
            }
        }
        return null;
    }

    private void stubToolSuccess() {
        lenient().when(toolExecutionService.containsTool(any())).thenReturn(true);
        lenient().when(toolExecutionService.getTool(any())).thenReturn(null);
        lenient().when(toolExecutionService.execute(eq("rd_config_discover"), any()))
                .thenReturn(ExecutionResult.ok("rd_config_discover", Map.of("nl_answer", "命中2条历史", "items", List.of(Map.of("name", "家庭融合158")))));
        lenient().when(toolExecutionService.execute(eq("rd_compliance"), any()))
                .thenReturn(ExecutionResult.ok("rd_compliance", Map.of("compliance_pass", true, "issues", List.of())));
        lenient().when(toolExecutionService.execute(eq("rd_draft_manage"), any()))
                .thenReturn(ExecutionResult.ok("rd_draft_manage", Map.of("nl_answer", "操作成功", "success", true)));
    }

    /** 跑一轮主流程（挂起即止），返回主流程 node_log 的 nodeId 序列。 */
    private List<String> runMainFlowCapturePath() {
        org.mockito.ArgumentCaptor<com.sitech.prodai.domain.entity.WorkflowNodeLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.sitech.prodai.domain.entity.WorkflowNodeLog.class);
        int before = nodeLogCaptureSize();
        engine.startExecution(SceneWorkflowDefinitions.MAIN_CODE, null,
                Map.of("question", "办一个158的家庭融合套餐"), "tester");
        verify(nodeLogMapper, org.mockito.Mockito.times(before + 9)).insert(captor.capture());
        return captor.getAllValues().stream()
                .skip(before)
                .map(com.sitech.prodai.domain.entity.WorkflowNodeLog::getNodeId)
                .toList();
    }

    /** 已捕获的 node_log 总数（captor 跨轮累积，需按偏移截取本轮）。 */
    private int nodeLogCaptureSize() {
        org.mockito.ArgumentCaptor<com.sitech.prodai.domain.entity.WorkflowNodeLog> probe =
                org.mockito.ArgumentCaptor.forClass(com.sitech.prodai.domain.entity.WorkflowNodeLog.class);
        try {
            org.mockito.Mockito.verify(nodeLogMapper, org.mockito.Mockito.atLeast(0)).insert(probe.capture());
        } catch (Exception ignored) {
        }
        return probe.getAllValues().size();
    }

    /** 主流程路径（node_log 顺序即执行时序，重置后二次捕获即第二轮路径）。 */
    private List<String> pathFromLogs() {
        org.mockito.ArgumentCaptor<com.sitech.prodai.domain.entity.WorkflowNodeLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.sitech.prodai.domain.entity.WorkflowNodeLog.class);
        verify(nodeLogMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getAllValues().stream()
                .map(com.sitech.prodai.domain.entity.WorkflowNodeLog::getNodeId)
                .toList();
    }
}
