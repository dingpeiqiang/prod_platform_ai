package com.sitech.prodai.service.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.domain.entity.Workflow;
import com.sitech.prodai.domain.entity.WorkflowExecution;
import com.sitech.prodai.domain.entity.WorkflowNodeLog;
import com.sitech.prodai.mapper.WorkflowExecutionMapper;
import com.sitech.prodai.mapper.WorkflowMapper;
import com.sitech.prodai.mapper.WorkflowNodeLogMapper;
import com.sitech.prodai.service.ToolExecutionService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 固定流程引擎测试（设计文档 §8-P2-2~P2-5 验收）：
 * 定义期守门、线性执行、变量传递、条件分支路由与审计、人工挂起/恢复、llm 节点。
 */
@ExtendWith(MockitoExtension.class)
class FlowEngineServiceTest {

    @Mock
    private WorkflowMapper workflowMapper;
    @Mock
    private WorkflowExecutionMapper executionMapper;
    @Mock
    private WorkflowNodeLogMapper nodeLogMapper;
    @Mock
    private ToolExecutionService toolExecutionService;

    private ConditionEvaluator conditionEvaluator;
    private FlowEngineService.LlmGateway llmGateway;
    private FlowEngineService.HttpGateway httpGateway;
    private FlowEngineService engine;

    @BeforeEach
    void setUp() {
        conditionEvaluator = new ConditionEvaluator();
        llmGateway = params -> "mock-llm-response";
        httpGateway = (url, method, body) -> org.springframework.http.ResponseEntity.ok("{\"result\": \"ok\"}");
        engine = new FlowEngineService(workflowMapper, executionMapper, nodeLogMapper,
                toolExecutionService, new FlowDefinitionValidator(), conditionEvaluator, llmGateway, httpGateway);
    }

    private Workflow publishedWorkflow(Map<String, Object> definition) {
        Workflow wf = new Workflow();
        wf.setId(1);
        wf.setWorkflowCode("test_flow");
        wf.setIsActive(true);
        wf.setVersion(3);
        wf.setWorkflowData(definition);
        return wf;
    }

    private Map<String, Object> node(String id, String action, Map<String, Object> params) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("action", action);
        n.put("action_params", params);
        return n;
    }

    private Map<String, Object> conn(String source, String target, String handle) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("source", source);
        c.put("target", target);
        if (handle != null) {
            c.put("sourceHandle", handle);
        }
        return c;
    }

    private Map<String, Object> definition(Map<String, Object> toolParams) {
        return Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()),
                        node("t", "flow.tool", toolParams),
                        node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "t", null), conn("t", "e", null)));
    }

    private final Map<String, WorkflowExecution> executionStore = new java.util.concurrent.ConcurrentHashMap<>();

    /** 内存化 execution 持久化：insert 保存实例，updateById 覆盖同 executionId，selectOne 返回最新态。 */
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
        lenient().when(executionMapper.selectOne(any())).thenAnswer(inv ->
                executionStore.values().stream().findFirst().orElse(null));
    }

    @Test
    void linearFlowCompletesWithNodeLogs() {
        Map<String, Object> toolParams = Map.of(
                "toolName", "sparql_query",
                "inputParams", List.of(Map.of("name", "q", "value", "收入")));
        lenient().when(workflowMapper.selectList(any()))
                .thenReturn(List.of(publishedWorkflow(definition(toolParams))));
        stubExecutionPersistence();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("rows", 5)));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of("metric", "收入"), "tester");

        assertTrue(resp.isSuccess(), () -> "应执行成功: " + resp.getMessage());
        assertEquals("completed", resp.getData().get("status"));
        assertEquals(3, resp.getData().get("workflow_version"));

        // 节点留痕：start + tool 各一条（end 节点直接完成，不留执行记录）
        ArgumentCaptor<WorkflowNodeLog> logCaptor = ArgumentCaptor.forClass(WorkflowNodeLog.class);
        verify(nodeLogMapper, times(2)).insert(logCaptor.capture());
        assertTrue(logCaptor.getAllValues().stream().allMatch(l -> "completed".equals(l.getStatus())));
    }

    @Test
    void toolFailureFailsExecution() {
        lenient().when(workflowMapper.selectList(any()))
                .thenReturn(List.of(publishedWorkflow(definition(Map.of("toolName", "sparql_query")))));
        stubExecutionPersistence();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.fail("sparql_query", "本体库不可用"));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), "启动本身成功，失败体现在实例状态");
        assertEquals("failed", resp.getData().get("status"));
        assertNotNull(resp.getData().get("error_message"));
    }

    @Test
    void invalidDefinitionRejectedBeforeRun() {
        List<Map<String, Object>> nodes = List.of(
                node("s", "flow.start", Map.of()),
                node("t1", "flow.tool", Map.of("toolName", "x")),
                node("t2", "flow.tool", Map.of("toolName", "x")),
                node("e", "flow.end", Map.of()));
        List<Map<String, Object>> conns = List.of(
                conn("s", "t1", null), conn("t1", "t2", null), conn("t2", "t1", null), conn("t2", "e", null));
        Map<String, Object> cyclicDef = Map.of("nodes", nodes, "connections", conns);
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(publishedWorkflow(cyclicDef)));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertFalse(resp.isSuccess(), "环定义应被拒绝");
        assertTrue(resp.getMessage().contains("校验未通过"), () -> "拒绝原因: " + resp.getMessage());
        assertTrue(resp.getErrors() != null && !resp.getErrors().isEmpty(), "应返回问题明细");
        verify(toolExecutionService, never()).execute(any(), any());
        verify(executionMapper, never()).insert(any(WorkflowExecution.class));
    }

    @Test
    void variableReferenceResolvesAcrossNodes() {
        Map<String, Object> t1 = node("t1", "flow.tool", Map.of(
                "toolName", "sparql_query",
                "outputParams", List.of(Map.of("name", "entity_id", "source", "entity_id"))));
        Map<String, Object> t2 = node("t2", "flow.tool", Map.of(
                "toolName", "swrl_root_cause",
                "inputParams", List.of(Map.of("name", "entityId", "value", "{{t1.output.entity_id}}"))));
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()), t1, t2, node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "t1", null), conn("t1", "t2", null), conn("t2", "e", null)));

        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(publishedWorkflow(def)));
        stubExecutionPersistence();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("entity_id", "OFF_001")));
        when(toolExecutionService.execute(eq("swrl_root_cause"), any()))
                .thenReturn(ExecutionResult.ok("swrl_root_cause", Map.of("root_cause", "promotion")));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), () -> "双工具链应成功: " + resp.getMessage());
        verify(toolExecutionService).execute(eq("swrl_root_cause"), eq(Map.of("entityId", "OFF_001")));
    }

    @Test
    void unpublishedWorkflowRejected() {
        Workflow wf = publishedWorkflow(definition(Map.of("toolName", "x")));
        wf.setIsActive(null);
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(wf));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("未发布"));
    }

    @Test
    void conditionRoutesToMatchingBranch() {
        Map<String, Object> tool = node("t1", "flow.tool", Map.of(
                "toolName", "sparql_query",
                "outputParams", List.of(Map.of("name", "riskLevel", "source", "riskLevel"))));
        Map<String, Object> cond = node("c1", "flow.condition", Map.of("branches", List.of(
                Map.of("id", "high-risk", "expression", "${t1.output.riskLevel} == 'HIGH'"),
                Map.of("id", "pass", "expression", "default"))));
        Map<String, Object> human = node("h1", "flow.human", Map.of());
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()), tool, cond, human, node("e", "flow.end", Map.of())),
                "connections", List.of(
                        conn("s", "t1", null), conn("t1", "c1", null),
                        conn("c1", "h1", "high-risk"), conn("c1", "e", "pass")));

        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(publishedWorkflow(def)));
        stubExecutionPersistence();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("riskLevel", "HIGH")));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), () -> "启动应成功: " + resp.getMessage());
        assertEquals("waiting_human", resp.getData().get("status"));
        assertEquals("h1", resp.getData().get("current_node_id"));
        assertNotNull(resp.getData().get("resume_token"));

        // 分支命中依据落库（branch_taken = high-risk）：start + tool + condition + human 各一条
        ArgumentCaptor<WorkflowNodeLog> logCaptor = ArgumentCaptor.forClass(WorkflowNodeLog.class);
        verify(nodeLogMapper, times(4)).insert(logCaptor.capture());
        WorkflowNodeLog branchLog = logCaptor.getAllValues().stream()
                .filter(l -> "flow.condition".equals(l.getNodeType())).findFirst().orElse(null);
        assertNotNull(branchLog);
        assertEquals("high-risk", branchLog.getBranchTaken());
    }

    @Test
    void humanResumeContinuesFlow() {
        Map<String, Object> tool = node("t1", "flow.tool", Map.of(
                "toolName", "sparql_query",
                "outputParams", List.of(Map.of("name", "riskLevel", "source", "riskLevel"))));
        Map<String, Object> cond = node("c1", "flow.condition", Map.of("branches", List.of(
                Map.of("id", "high-risk", "expression", "${t1.output.riskLevel} == 'HIGH'"),
                Map.of("id", "pass", "expression", "default"))));
        Map<String, Object> human = node("h1", "flow.human", Map.of());
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()), tool, cond, human, node("e", "flow.end", Map.of())),
                "connections", List.of(
                        conn("s", "t1", null), conn("t1", "c1", null),
                        conn("c1", "h1", "high-risk"), conn("c1", "e", "pass"),
                        conn("h1", "e", null)));

        Workflow wf = publishedWorkflow(def);
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(wf));
        lenient().when(workflowMapper.selectById(1)).thenReturn(wf);
        stubExecutionPersistence();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("riskLevel", "HIGH")));

        ApiResponse<Map<String, Object>> startResp = engine.startExecution("test_flow", null, Map.of(), "tester");
        String executionId = (String) startResp.getData().get("execution_id");
        String token = (String) startResp.getData().get("resume_token");

        // 错误令牌被拒
        ApiResponse<Map<String, Object>> badResp = engine.resumeFromHuman(executionId, "wrong-token", Map.of(), "approver");
        assertFalse(badResp.isSuccess());

        // 正确令牌恢复 → 走到 end 完成
        ApiResponse<Map<String, Object>> okResp = engine.resumeFromHuman(
                executionId, token, Map.of("approved", true, "approver", "reviewer-a"), "approver");
        assertTrue(okResp.isSuccess(), () -> "恢复应成功: " + okResp.getMessage());
        assertEquals("completed", okResp.getData().get("status"));

        // 审计：human 节点输出含审批人与结论
        Map<String, Object> contextData = (Map<String, Object>) okResp.getData().get("context_data");
        Map<String, Object> humanScope = (Map<String, Object>) contextData.get("h1");
        Map<String, Object> humanOutput = (Map<String, Object>) humanScope.get("output");
        assertEquals(Boolean.TRUE, humanOutput.get("approved"));
        assertEquals("reviewer-a", humanOutput.get("approver"));
    }

    @Test
    void llmNodeRendersTemplateAndCompletes() {
        Map<String, Object> llmNode = node("l1", "flow.llm", Map.of("prompt", "请总结 {{flow.topic}}"));
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()), llmNode, node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "l1", null), conn("l1", "e", null)));
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(publishedWorkflow(def)));
        stubExecutionPersistence();

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of("topic", "收入"), "tester");

        assertTrue(resp.isSuccess(), () -> "llm 节点流程应成功: " + resp.getMessage());
        assertEquals("completed", resp.getData().get("status"));
        Map<String, Object> contextData = (Map<String, Object>) resp.getData().get("context_data");
        Map<String, Object> llmScope = (Map<String, Object>) contextData.get("l1");
        assertEquals("mock-llm-response", ((Map<String, Object>) llmScope.get("output")).get("response"));
    }

    // ── P5：执行语义（timeoutMs / retry / onFailure=continue） ──

    @Test
    void retryMaxAttemptsRetriesFailedToolThenSucceeds() {
        Map<String, Object> toolParams = Map.of(
                "toolName", "sparql_query",
                "retry", Map.of("maxAttempts", 2));
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()),
                        node("t1", "flow.tool", toolParams),
                        node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "t1", null), conn("t1", "e", null)));
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(publishedWorkflow(def)));
        stubExecutionPersistence();
        // 前两次失败，第三次成功 → 共 3 次调用（首次 + 2 次重试）
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.fail("sparql_query", "瞬态故障"))
                .thenReturn(ExecutionResult.fail("sparql_query", "瞬态故障"))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("rows", 1)));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), () -> "重试后应成功: " + resp.getMessage());
        assertEquals("completed", resp.getData().get("status"));
        verify(toolExecutionService, times(3)).execute(eq("sparql_query"), any());
    }

    @Test
    void retryExhaustedFailsExecution() {
        Map<String, Object> toolParams = Map.of(
                "toolName", "sparql_query",
                "retry", Map.of("maxAttempts", 2));
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()),
                        node("t1", "flow.tool", toolParams),
                        node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "t1", null), conn("t1", "e", null)));
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(publishedWorkflow(def)));
        stubExecutionPersistence();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.fail("sparql_query", "持续故障"));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), "启动成功，失败体现在实例状态");
        assertEquals("failed", resp.getData().get("status"));
        verify(toolExecutionService, times(3)).execute(eq("sparql_query"), any());
    }

    @Test
    void timeoutMsInterruptsSlowTool() {
        Map<String, Object> toolParams = Map.of(
                "toolName", "sparql_query",
                "timeoutMs", 300);
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()),
                        node("t1", "flow.tool", toolParams),
                        node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "t1", null), conn("t1", "e", null)));
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(publishedWorkflow(def)));
        stubExecutionPersistence();
        when(toolExecutionService.execute(eq("sparql_query"), any())).thenAnswer(inv -> {
            Thread.sleep(5000); // 远超 300ms 超时
            return ExecutionResult.ok("sparql_query", Map.of());
        });

        long begin = System.currentTimeMillis();
        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");
        long elapsed = System.currentTimeMillis() - begin;

        assertTrue(resp.isSuccess(), "启动成功，超时失败体现在实例状态");
        assertEquals("failed", resp.getData().get("status"));
        assertTrue(String.valueOf(resp.getData().get("error_message")).contains("超时"),
                () -> "错误信息应含超时: " + resp.getData().get("error_message"));
        assertTrue(elapsed < 3000, () -> "应在超时后快速返回，实际 " + elapsed + "ms");
    }

    @Test
    void onFailureContinueSkipsFailedNodeAndProceeds() {
        Map<String, Object> toolParams = Map.of(
                "toolName", "sparql_query",
                "onFailure", "continue");
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()),
                        node("t1", "flow.tool", toolParams),
                        node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "t1", null), conn("t1", "e", null)));
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(publishedWorkflow(def)));
        stubExecutionPersistence();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.fail("sparql_query", "下游暂不可用"));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), () -> "continue 模式流程应走完: " + resp.getMessage());
        assertEquals("completed", resp.getData().get("status"), "失败节点被跳过后应正常完成");
        // 节点日志仍记录 failed（审计：失败未吞）
        ArgumentCaptor<WorkflowNodeLog> logCaptor = ArgumentCaptor.forClass(WorkflowNodeLog.class);
        verify(nodeLogMapper, times(2)).insert(logCaptor.capture());
        WorkflowNodeLog toolLog = logCaptor.getAllValues().stream()
                .filter(l -> "t1".equals(l.getNodeId())).findFirst().orElse(null);
        assertNotNull(toolLog);
        assertEquals("failed", toolLog.getStatus());
        assertEquals("下游暂不可用", toolLog.getErrorMessage());
    }

    @Test
    void onFailureFailAbortsOnFailedNode() {
        // 默认 fail 模式：失败即中止（对照组）
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()),
                        node("t1", "flow.tool", Map.of("toolName", "sparql_query")),
                        node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "t1", null), conn("t1", "e", null)));
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(publishedWorkflow(def)));
        stubExecutionPersistence();
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.fail("sparql_query", "致命故障"));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), "启动成功，失败体现在实例状态");
        assertEquals("failed", resp.getData().get("status"));
    }

    // ── P4-1：取消执行 ──

    @Test
    void cancelRejectsUnknownExecution() {
        ApiResponse<Map<String, Object>> resp = engine.cancelExecution("ghost", null, "ops");
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("不存在"));
    }

    @Test
    void cancelRejectsTerminalExecution() {
        WorkflowExecution done = new WorkflowExecution();
        done.setExecutionId("exec_done");
        done.setStatus("completed");
        when(executionMapper.selectOne(any())).thenReturn(done);

        ApiResponse<Map<String, Object>> resp = engine.cancelExecution("exec_done", null, "ops");

        assertFalse(resp.isSuccess(), "completed 为终态应拒绝取消");
        assertTrue(resp.getMessage().contains("不可取消"));
        verify(executionMapper, never()).updateById(any(WorkflowExecution.class));
    }

    @Test
    void cancelTransitionsWaitingHumanToCancelledAndInvalidatesToken() {
        // 启动 → 挂起于 human → 取消
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()),
                        node("h1", "flow.human", Map.of()),
                        node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "h1", null), conn("h1", "e", null)));
        Workflow wf = publishedWorkflow(def);
        lenient().when(workflowMapper.selectList(any())).thenReturn(List.of(wf));
        lenient().when(workflowMapper.selectById(1)).thenReturn(wf);
        stubExecutionPersistence();

        ApiResponse<Map<String, Object>> startResp = engine.startExecution("test_flow", null, Map.of(), "tester");
        String executionId = (String) startResp.getData().get("execution_id");
        assertEquals("waiting_human", startResp.getData().get("status"));

        // stub：查 running 日志（closeRunningNodeLogAsCancelled 用）→ 返回挂起时的 human running 记录
        WorkflowNodeLog humanRunningLog = new WorkflowNodeLog();
        humanRunningLog.setExecutionId(executionId);
        humanRunningLog.setNodeId("h1");
        humanRunningLog.setStatus("running");
        humanRunningLog.setStartedAt(java.time.LocalDateTime.now().minusSeconds(5));
        when(nodeLogMapper.selectList(any())).thenReturn(List.of(humanRunningLog));

        ApiResponse<Map<String, Object>> cancelResp = engine.cancelExecution(executionId, "误启动", "ops");

        assertTrue(cancelResp.isSuccess(), () -> "取消应成功: " + cancelResp.getMessage());
        assertEquals("cancelled", cancelResp.getData().get("status"));
        assertNull(cancelResp.getData().get("resume_token"), "取消后令牌必须失效");

        // 挂起节点的 running 日志闭环为 cancelled
        ArgumentCaptor<WorkflowNodeLog> logCaptor = ArgumentCaptor.forClass(WorkflowNodeLog.class);
        verify(nodeLogMapper, atLeastOnce()).updateById(logCaptor.capture());
        assertTrue(logCaptor.getAllValues().stream()
                .anyMatch(l -> "cancelled".equals(l.getStatus()) && "h1".equals(l.getNodeId())));

        // 取消后人工恢复被拒
        ApiResponse<Map<String, Object>> resumeResp = engine.resumeFromHuman(
                executionId, "any-token", Map.of(), "approver");
        assertFalse(resumeResp.isSuccess(), "取消后 resume_token 已失效，人工恢复应被拒");
    }

    // ── P4-2：执行实例列表 ──

    @Test
    void listExecutionsReturnsPaginatedSnakeCase() {
        WorkflowExecution e1 = new WorkflowExecution();
        e1.setWorkflowCode("wf_a");
        e1.setExecutionId("exec_a");
        e1.setStatus("completed");
        WorkflowExecution e2 = new WorkflowExecution();
        e2.setWorkflowCode("wf_a");
        e2.setExecutionId("exec_b");
        e2.setStatus("failed");
        e2.setErrorMessage("节点 t1 失败");
        when(executionMapper.selectCount(any())).thenReturn(2L);
        when(executionMapper.selectList(any())).thenReturn(List.of(e1, e2));

        ApiResponse<Map<String, Object>> resp = engine.listExecutions("wf_a", 1, 20);

        assertTrue(resp.isSuccess());
        Map<String, Object> data = resp.getData();
        assertEquals(2L, data.get("total"));
        assertEquals(1, data.get("page"));
        assertEquals(20, data.get("page_size"));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("data");
        assertEquals(2, rows.size());
        assertEquals("exec_a", rows.get(0).get("execution_id"));
        assertEquals("failed", rows.get(1).get("status"));
        assertEquals("节点 t1 失败", rows.get(1).get("error_message"));
    }

    @Test
    void listExecutionsClampsPaginationBounds() {
        when(executionMapper.selectCount(any())).thenReturn(0L);
        when(executionMapper.selectList(any())).thenReturn(List.of());

        ApiResponse<Map<String, Object>> resp = engine.listExecutions(null, 0, 9999);

        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().get("page"), "page 下限钳位 1");
        assertEquals(100, resp.getData().get("page_size"), "page_size 上限钳位 100");
    }
}
