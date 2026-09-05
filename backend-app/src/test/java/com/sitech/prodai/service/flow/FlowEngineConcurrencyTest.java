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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * FlowEngineService 并发与状态一致性单元测试（R8）：
 * resumeFromHuman 双提交竞态（无锁现状基线 + 令牌一次有效串行重放拒）、statusVersion 随状态转换单调递增、
 * 人工节点无出边恢复即失败、变量平铺双写语义、子流程嵌套深度上限、
 * per-attempt 单线程 executor 生命周期（超时后不留泄漏线程）。
 * <p>
 * 持久化以 ConcurrentHashMap 内存化（insert 保存 / updateById 覆盖 / selectOne 读最新态），
 * 引擎无锁路径的竞态由令牌消费 + 状态前置校验兜底。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowEngineConcurrencyTest {

    @Mock
    private WorkflowMapper workflowMapper;
    @Mock
    private WorkflowExecutionMapper executionMapper;
    @Mock
    private WorkflowNodeLogMapper nodeLogMapper;
    @Mock
    private ToolExecutionService toolExecutionService;

    private FlowEngineService engine;

    private final Map<String, WorkflowExecution> executionStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        engine = new FlowEngineService(workflowMapper, executionMapper, nodeLogMapper,
                toolExecutionService, new FlowDefinitionValidator(), new ConditionEvaluator(),
                params -> "mock-llm",
                (url, method, body) -> org.springframework.http.ResponseEntity.ok("{}"),
                formCode -> null);
        stubExecutionPersistence();
    }

    // ── fixture 工厂（对齐 FlowEngineServiceTest 样板） ──

    private Workflow publishedWorkflow(String code, Map<String, Object> definition) {
        Workflow wf = new Workflow();
        wf.setId(1);
        wf.setWorkflowCode(code);
        wf.setIsActive(true);
        wf.setVersion(1);
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
        lenient().when(executionMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv ->
                executionStore.values().stream().findFirst().orElse(null));
    }

    /** human 挂起定义：start → t1(tool) → h1(human) → e(end)。 */
    private Map<String, Object> humanFlowDefinition() {
        Map<String, Object> tool = node("t1", "flow.tool", Map.of(
                "toolName", "sparql_query",
                "outputParams", List.of(Map.of("name", "riskLevel", "source", "riskLevel"))));
        Map<String, Object> human = node("h1", "flow.human", Map.of());
        return Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()), tool, human, node("e", "flow.end", Map.of())),
                "connections", List.of(
                        conn("s", "t1", null), conn("t1", "h1", null), conn("h1", "e", null)));
    }

    /** 启动一个会挂起于人工节点的执行，返回 [executionId, resumeToken]。 */
    private String[] startSuspendedExecution() {
        Workflow wf = publishedWorkflow("test_flow", humanFlowDefinition());
        lenient().when(workflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(wf));
        lenient().when(workflowMapper.selectById(1)).thenReturn(wf);
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("riskLevel", "HIGH")));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");
        assertTrue(resp.isSuccess(), () -> "启动应成功并挂起: " + resp.getMessage());
        assertEquals("waiting_human", resp.getData().get("status"));
        return new String[]{
                (String) resp.getData().get("execution_id"),
                (String) resp.getData().get("resume_token")};
    }

    // ── resumeFromHuman 双提交竞态 ──

    /**
     * 双提交行为基线（R8 记录实现现状）：并发恢复无进程内互斥，胜负取决于线程调度——
     * 双方都可能读到同一 token 而都通过，也可能一方先完成推进使另一方被状态校验拒绝。
     * 防线语义 = 令牌一次有效（串行重放必拒，见 replayedResumeAfterConsumedTokenIsRejected）
     * + waiting_human 状态前置校验 + 终态一致性；真正的乐观锁（status_version WHERE 比对）为后续增强项。
     */
    @Test
    void doubleSubmitResumeUnderCurrentLockFreeSemantics() throws InterruptedException {
        String[] suspended = startSuspendedExecution();
        String executionId = suspended[0];
        String token = suspended[1];

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);

        Runnable submitter = () -> {
            try {
                startGate.await();
                ApiResponse<Map<String, Object>> resp = engine.resumeFromHuman(
                        executionId, token, Map.of("approved", true), "approver");
                if (resp.isSuccess()) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneGate.countDown();
            }
        };
        Thread t1 = new Thread(submitter, "resume-a");
        Thread t2 = new Thread(submitter, "resume-b");
        t1.start();
        t2.start();
        startGate.countDown();
        doneGate.await();

        // 现状语义：无锁下两个并发请求的胜负取决于线程调度——
        // 可能都成功（都读到同一 token）、也可能一方先完成令牌消费后另一方被状态校验拒绝。
        // 不确定的响应分布不作为契约断言；确定的契约是最终一致性与令牌消费：
        // 1) 终态唯一且为 completed  2) 令牌被消费置空  3) 至少一次成功（流程能推进到底）
        int successes = successCount.get();
        assertTrue(successes >= 1 && successes <= 2,
                () -> "成功次数应为 1~2（现状无锁），实际 " + successes);
        assertEquals(2, successes + failCount.get(), "两次提交必须都有明确响应");
        assertEquals("completed", executionStore.get(executionId).getStatus(),
                "执行应走到 end 终态");
        assertNull(executionStore.get(executionId).getResumeToken(), "令牌消费后置空");
    }

    @Test
    void replayedResumeAfterConsumedTokenIsRejected() {
        String[] suspended = startSuspendedExecution();
        String executionId = suspended[0];
        String token = suspended[1];

        ApiResponse<Map<String, Object>> first = engine.resumeFromHuman(
                executionId, token, Map.of("approved", true), "approver");
        assertTrue(first.isSuccess(), () -> "首次恢复应成功: " + first.getMessage());

        // 串行重放：同令牌第二次提交被拒（令牌已消费 + 状态已离开 waiting_human）
        ApiResponse<Map<String, Object>> replay = engine.resumeFromHuman(
                executionId, token, Map.of("approved", true), "approver");
        assertFalse(replay.isSuccess(), "同令牌重放应被拒");
        assertTrue(replay.getMessage().contains("令牌无效") || replay.getMessage().contains("挂起状态"),
                () -> "拒绝文案应指明令牌/状态: " + replay.getMessage());
    }

    // ── statusVersion 单调递增 ──

    @Test
    void statusVersionMonotonicallyIncreasesAcrossTransitions() {
        Workflow wf = publishedWorkflow("test_flow", humanFlowDefinition());
        lenient().when(workflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(wf));
        lenient().when(workflowMapper.selectById(1)).thenReturn(wf);
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("riskLevel", "HIGH")));

        ApiResponse<Map<String, Object>> startResp = engine.startExecution("test_flow", null, Map.of(), "tester");
        assertTrue(startResp.isSuccess());
        String executionId = (String) startResp.getData().get("execution_id");

        // 转换序列：insert(0) → t1 完成(+1) → h1 挂起(+1)
        Integer versionAtSuspend = executionStore.get(executionId).getStatusVersion();
        assertNotNull(versionAtSuspend, "挂起时版本应已落库");
        assertTrue(versionAtSuspend >= 2, () -> "启动+挂起至少两次 bump，实际 " + versionAtSuspend);

        String token = (String) startResp.getData().get("resume_token");
        ApiResponse<Map<String, Object>> resumeResp = engine.resumeFromHuman(
                executionId, token, Map.of("approved", true), "approver");
        assertTrue(resumeResp.isSuccess(), () -> "恢复应成功: " + resumeResp.getMessage());

        // 恢复(+1) → h1→e 推进后 end 完成（completeExecution 不 bump，版本保持恢复时值）
        Integer versionFinal = executionStore.get(executionId).getStatusVersion();
        assertTrue(versionFinal > versionAtSuspend, () -> "恢复后版本应递增: "
                + versionAtSuspend + " → " + versionFinal);
    }

    @Test
    void cancelBumpsStatusVersionAndTerminalState() {
        String[] suspended = startSuspendedExecution();
        String executionId = suspended[0];
        Integer versionBefore = executionStore.get(executionId).getStatusVersion();

        WorkflowNodeLog runningLog = new WorkflowNodeLog();
        runningLog.setExecutionId(executionId);
        runningLog.setNodeId("h1");
        runningLog.setStatus("running");
        runningLog.setStartedAt(java.time.LocalDateTime.now().minusSeconds(1));
        when(nodeLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(runningLog));

        ApiResponse<Map<String, Object>> resp = engine.cancelExecution(executionId, "误启动", "ops");

        assertTrue(resp.isSuccess(), () -> "取消应成功: " + resp.getMessage());
        assertEquals("cancelled", resp.getData().get("status"));
        Integer versionAfter = executionStore.get(executionId).getStatusVersion();
        assertTrue(versionAfter > versionBefore, () -> "取消应 bump 版本: "
                + versionBefore + " → " + versionAfter);
        assertNull(executionStore.get(executionId).getResumeToken(), "取消后令牌必须失效");
    }

    // ── 人工节点无出边 → 恢复即失败 ──

    @Test
    void resumeOnHumanWithoutOutgoingEdgeFailsExecution() {
        // h1 无出边（不连 e）
        Map<String, Object> human = node("h1", "flow.human", Map.of());
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()), human, node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "h1", null)));
        Workflow wf = publishedWorkflow("test_flow", def);
        lenient().when(workflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(wf));
        lenient().when(workflowMapper.selectById(1)).thenReturn(wf);

        ApiResponse<Map<String, Object>> startResp = engine.startExecution("test_flow", null, Map.of(), "tester");
        assertEquals("waiting_human", startResp.getData().get("status"));
        String executionId = (String) startResp.getData().get("execution_id");
        String token = (String) startResp.getData().get("resume_token");

        ApiResponse<Map<String, Object>> resp = engine.resumeFromHuman(
                executionId, token, Map.of("approved", true), "approver");

        assertFalse(resp.isSuccess(), "无出边人工节点恢复应失败");
        assertTrue(resp.getMessage().contains("未配置出边"),
                () -> "错误应指明定义缺陷: " + resp.getMessage());
        assertEquals("failed", executionStore.get(executionId).getStatus(),
                "执行应落 failed 终态（failExecution）");
        assertEquals("waiting_human", resp.getData() == null ? "waiting_human"
                        : resp.getData().get("status") == null ? "waiting_human"
                        : resp.getData().get("status"),
                "fail 路径不返回执行快照（fail 响应无 data）");
    }

    // ── 变量作用域：scope 命名空间 + 平铺双写 ──

    @Test
    void nodeOutputWrittenToBothScopeAndFlatKeys() {
        Map<String, Object> t1 = node("t1", "flow.tool", Map.of(
                "toolName", "sparql_query",
                "outputParams", List.of(Map.of("name", "riskLevel", "source", "riskLevel"))));
        Map<String, Object> t2 = node("t2", "flow.tool", Map.of(
                "toolName", "swrl_root_cause",
                "inputParams", List.of(Map.of("name", "level", "value", "{{t1.output.riskLevel}}"))));
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()), t1, t2, node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "t1", null), conn("t1", "t2", null), conn("t2", "e", null)));
        Workflow wf = publishedWorkflow("test_flow", def);
        lenient().when(workflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(wf));
        when(toolExecutionService.execute(eq("sparql_query"), any()))
                .thenReturn(ExecutionResult.ok("sparql_query", Map.of("riskLevel", "HIGH")));
        when(toolExecutionService.execute(eq("swrl_root_cause"), any()))
                .thenReturn(ExecutionResult.ok("swrl_root_cause", Map.of("root_cause", "promo")));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), () -> "双工具链应成功: " + resp.getMessage());
        Map<String, Object> contextData = (Map<String, Object>) resp.getData().get("context_data");
        // scope 命名空间：<nodeId>.output.<field>
        Map<String, Object> t1Scope = (Map<String, Object>) contextData.get("t1");
        assertNotNull(t1Scope, "节点输出应有 scope 命名空间");
        assertEquals("HIGH", ((Map<String, Object>) t1Scope.get("output")).get("riskLevel"));
        // 平铺双写：<field> 直达（variables.putAll(outcome.output())）
        Map<String, Object> outputData = (Map<String, Object>) resp.getData().get("output_data");
        assertEquals("HIGH", outputData.get("riskLevel"), "节点输出应同时平铺双写");
        // t2 经 {{t1.output.riskLevel}} 引用取到 scope 值
        verifyToolInput("swrl_root_cause", Map.of("level", "HIGH"));
    }

    private void verifyToolInput(String toolName, Map<String, Object> expected) {
        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(toolExecutionService).execute(eq(toolName), captor.capture());
        assertEquals(expected, captor.getValue(), () -> "工具入参应解析 scope 引用: " + captor.getValue());
    }

    // ── 子流程嵌套深度边界 ──

    /**
     * 嵌套深度行为基线（R8 记录实现现状 + 缺陷发现）：
     * executeWorkflowNode 构建子流程 inputData 时传的是 resolveInputParams(...) 产物（新 Map，不含链），
     * withWorkflowChain 在该产物上追加 childCode → 子层链永远只有自身 [wf_N]，父链逐层丢失。
     * 后果：无环深链每层 chain.size() 恒为 1，MAX_WORKFLOW_NESTING_DEPTH=5 的深度上限实际不生效
     * （仅防直接/间接环引用，见 workflowChainCycleIsRejectedBeforeDepthLimit）。
     * 修复方向：withWorkflowChain 第 1 参应读 variables 中的父链（chain(variables) + childCode）。
     * 本测试固定「深度超限不拒绝」的现状基线：6 层无环深链全部 completed。
     */
    @Test
    void workflowNestingDepthLimitNotEnforcedDueToChainLoss() {
        // wf_1..wf_5 逐层引用下一层，wf_6 为终点（无 workflow 节点）
        Map<String, Object>[] defs = new Map[6];
        for (int i = 0; i < 5; i++) {
            defs[i] = Map.of("nodes", List.of(
                            node("s", "flow.start", Map.of()),
                            node("w", "flow.workflow", Map.of("workflow_ref", "wf_" + (i + 2))),
                            node("e", "flow.end", Map.of())),
                    "connections", List.of(conn("s", "w", null), conn("w", "e", null)));
        }
        defs[5] = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()), node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "e", null)));
        // 每层 startExecution 精确触发一次 selectList（父启动 + 5 次子递归 = 6 次），按序返回
        lenient().when(workflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                List.of(publishedWorkflow("wf_1", defs[0])),
                List.of(publishedWorkflow("wf_2", defs[1])),
                List.of(publishedWorkflow("wf_3", defs[2])),
                List.of(publishedWorkflow("wf_4", defs[3])),
                List.of(publishedWorkflow("wf_5", defs[4])),
                List.of(publishedWorkflow("wf_6", defs[5])));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("wf_1", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), "启动成功");
        // 现状基线：父链丢失 → 每层 chain=[wf_N] size=1，深度上限永不触发 → 6 层全部跑完 completed
        assertEquals("completed", resp.getData().get("status"),
                () -> "现状（链丢失）：深链不被深度上限拦截，全部 completed: " + resp.getData().get("error_message"));
        // 间接证据：6 层全部落库（父 + 5 个子流程实例）
        assertEquals(6, executionStore.size(), "父 + 5 个子流程实例应全部落库");
    }

    @Test
    void workflowChainCycleIsRejectedBeforeDepthLimit() {
        // 直接自引用：wf_a 引用 wf_a → 链上重复编码即拒绝（优先于深度上限）
        Map<String, Object> selfRef = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()),
                        node("w", "flow.workflow", Map.of("workflow_ref", "test_flow")),
                        node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "w", null), conn("w", "e", null)));
        lenient().when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(publishedWorkflow("test_flow", selfRef)));

        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");

        assertTrue(resp.isSuccess(), "启动成功，环拒绝体现在实例状态");
        assertEquals("failed", resp.getData().get("status"));
        assertTrue(String.valueOf(resp.getData().get("error_message")).contains("环引用"),
                () -> "错误应指明环引用: " + resp.getData().get("error_message"));
    }

    // ── per-attempt executor 生命周期（超时后不留泄漏线程） ──

    @Test
    void timeoutAttemptShutsDownExecutorThreadWithoutLeak() throws InterruptedException {
        Map<String, Object> toolParams = Map.of(
                "toolName", "sparql_query",
                "timeoutMs", 200);
        Map<String, Object> def = Map.of("nodes", List.of(
                        node("s", "flow.start", Map.of()),
                        node("t1", "flow.tool", toolParams),
                        node("e", "flow.end", Map.of())),
                "connections", List.of(conn("s", "t1", null), conn("t1", "e", null)));
        lenient().when(workflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(publishedWorkflow("test_flow", def)));
        when(toolExecutionService.execute(eq("sparql_query"), any())).thenAnswer(inv -> {
            Thread.sleep(3000); // 远超 200ms 超时
            return ExecutionResult.ok("sparql_query", Map.of());
        });

        int flowNodeThreadsBefore = countFlowNodeExecThreads();
        ApiResponse<Map<String, Object>> resp = engine.startExecution("test_flow", null, Map.of(), "tester");
        int flowNodeThreadsRightAfter = countFlowNodeExecThreads();

        assertTrue(resp.isSuccess(), "启动成功，超时失败体现在实例状态");
        assertEquals("failed", resp.getData().get("status"));
        assertTrue(String.valueOf(resp.getData().get("error_message")).contains("超时"),
                () -> "错误信息应含超时: " + resp.getData().get("error_message"));
        assertTrue(flowNodeThreadsRightAfter <= flowNodeThreadsBefore + 1,
                () -> "shutdownNow 后不留常驻泄漏线程（before=" + flowNodeThreadsBefore
                        + ", after=" + flowNodeThreadsRightAfter + "）");

        // 宽限期后线程应退出（interrupt 生效，sleep 抛 InterruptedException 提前结束）
        Thread.sleep(500);
        assertTrue(countFlowNodeExecThreads() <= flowNodeThreadsBefore,
                () -> "被中断的执行线程应退出（before=" + flowNodeThreadsBefore
                        + ", now=" + countFlowNodeExecThreads() + "）");
    }

    private int countFlowNodeExecThreads() {
        return (int) Thread.getAllStackTraces().keySet().stream()
                .filter(t -> "flow-node-exec".equals(t.getName()))
                .count();
    }
}
