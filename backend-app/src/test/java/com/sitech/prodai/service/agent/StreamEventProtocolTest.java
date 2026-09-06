package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.flow.FlowIntentRouter;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * SSE 流式事件协议契约测试（R8）：
 * 锁定 8 类事件（thinking/workflow/tool/text/text_done/done/error/warning）的
 * 事件名与载荷字段 shape——前端按此契约渲染思考时间线/工具卡片/打字机正文，
 * 字段缺失或改名即前端渲染退化，故以契约快照断言固化。
 * <p>
 * warning 事件在流式路径由持久化失败触发，此处一并覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StreamEventProtocolTest {

    @Mock
    private Understander understander;
    @Mock
    private Executor executor;
    @Mock
    private Presenter presenter;
    @Mock
    private LlmService llmService;
    @Mock
    private FlowIntentRouter flowIntentRouter;
    @Mock
    private com.sitech.prodai.service.agent.flow.SceneFlowRouter sceneFlowRouter;

    private AgentOrchestrator orchestrator;

    private record Emitted(String event, Map<String, Object> data) {
    }

    private static final class RecordingEmitter implements AgentOrchestrator.StreamEmitter {
        final List<Emitted> events = new ArrayList<>();

        @Override
        public void emit(String event, Map<String, Object> data) {
            events.add(new Emitted(event, data == null ? Map.of() : data));
        }
    }

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(understander, executor, presenter,
                new SessionManager(Optional.empty()), Optional.empty(), Optional.of(llmService),
                List.of(stubSparqlTool()), flowIntentRouter, null, sceneFlowRouter);
    }

    private QueryPlan execPlan(String tool) {
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of(tool), Map.of("question", "q"), "q");
        return plan;
    }

    /**
     * 注册输出契约的 sparql_query 桩：声明 CONCLUSION 角色，
     * 供编排层 extractConclusion 走契约化提取（与 AgentOrchestratorTest 同款）。
     */
    private com.sitech.prodai.service.agent.tool.AgentTool stubSparqlTool() {
        return new com.sitech.prodai.service.agent.tool.AgentTool() {
            @Override
            public String getName() {
                return "sparql_query";
            }

            @Override
            public String getDescription() {
                return "SPARQL 事实查询";
            }

            @Override
            public List<com.sitech.prodai.service.agent.tool.ToolOutputField> getOutputFields() {
                return List.of(
                        com.sitech.prodai.service.agent.tool.ToolOutputField
                                .builder("rows", com.sitech.prodai.service.agent.tool.ToolOutputField.Role.COUNT)
                                .label("记录数").build(),
                        com.sitech.prodai.service.agent.tool.ToolOutputField
                                .builder("conclusion", com.sitech.prodai.service.agent.tool.ToolOutputField.Role.CONCLUSION)
                                .label("结论").type("string").build());
            }

            @Override
            public ExecutionResult execute(Map<String, Object> params) {
                return ExecutionResult.ok(getName(), Map.of());
            }
        };
    }

    private RecordingEmitter runNormalStream() {
        QueryPlan plan = execPlan("sparql_query");
        when(flowIntentRouter.tryRoute(any(), any(), isNull())).thenReturn(java.util.Optional.empty());
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        ExecutionResult result = ExecutionResult.ok("sparql_query",
                Map.of("rows", 2, "conclusion", "查询完成"));
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class), any(Executor.StepListener.class)))
                .thenAnswer(inv -> {
                    Executor.StepListener listener = inv.getArgument(2);
                    listener.onStepStart("sparql_query");
                    listener.onStepComplete(result);
                    return List.of(result);
                });
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("正文报告");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class)))
                .thenReturn(List.of("追问1"));

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("查数据", "sse-1", null, emitter);
        return emitter;
    }

    // ── thinking 事件契约 ──

    @Test
    void thinkingEventPayloadCarriesStepsWithCoreFields() {
        RecordingEmitter emitter = runNormalStream();

        List<Emitted> thinkings = emitter.events.stream()
                .filter(e -> "thinking".equals(e.event())).toList();
        assertTrue(thinkings.size() >= 4, "正常链路至少 4 次 thinking（intent 起表/更新、plan、generate×2），实际 "
                + thinkings.size());

        for (Emitted e : thinkings) {
            assertTrue(e.data().containsKey("steps"), "thinking 载荷必须携带 steps");
            Object stepsObj = e.data().get("steps");
            assertTrue(stepsObj instanceof List<?> && !((List<?>) stepsObj).isEmpty(), "steps 非空列表");
            Map<?, ?> step = (Map<?, ?>) ((List<?>) stepsObj).get(0);
            // thinkingStep 基础契约（TraceSnapshotBuilder.thinkingStep）
            assertNotNull(step.get("id"), "步骤 id 必填（前端关联更新）");
            assertEquals("thinking", step.get("type"));
            assertNotNull(step.get("title"));
            assertNotNull(step.get("content"));
        }
        // 步骤 id 覆盖 intent / plan / generate 三类
        Set<String> ids = new java.util.HashSet<>();
        for (Emitted e : thinkings) {
            List<?> steps = (List<?>) e.data().get("steps");
            ids.add(String.valueOf(((Map<?, ?>) steps.get(0)).get("id")));
        }
        assertTrue(ids.contains("intent"), "应含 intent 步骤: " + ids);
        assertTrue(ids.contains("plan"), "应含 plan 步骤: " + ids);
        assertTrue(ids.contains("generate"), "应含 generate 步骤: " + ids);
    }

    @Test
    void thinkingIntentStepCarriesStructuredIntentOutput() {
        RecordingEmitter emitter = runNormalStream();

        Emitted intentUpdated = emitter.events.stream()
                .filter(e -> "thinking".equals(e.event()))
                .filter(e -> {
                    List<?> steps = (List<?>) e.data().get("steps");
                    return "intent".equals(((Map<?, ?>) steps.get(0)).get("id"))
                            && ((Map<?, ?>) steps.get(0)).containsKey("output");
                })
                .findFirst().orElse(null);
        assertNotNull(intentUpdated, "intent 步骤应有补输出的更新事件");
        Map<?, ?> step = (Map<?, ?>) ((List<?>) intentUpdated.data().get("steps")).get(0);
        Map<?, ?> output = (Map<?, ?>) step.get("output");
        assertNotNull(output.get("summary"), "意图输出应含 summary");
        assertNotNull(output.get("structured_intent"), "意图输出应含结构化意图（下游节点输入源）");
        // 顶层 intent 字段随更新事件下发（前端高亮当前意图）
        assertNotNull(intentUpdated.data().get("intent"));
    }

    // ── workflow 事件契约 ──

    @Test
    void workflowEventPayloadCarriesNodesAndEdges() {
        RecordingEmitter emitter = runNormalStream();

        Emitted workflow = emitter.events.stream()
                .filter(e -> "workflow".equals(e.event())).findFirst().orElseThrow();
        Map<String, Object> view = workflow.data();
        assertEquals("turn", view.get("id"), "WorkflowGraph.toView 契约: id");
        assertEquals("本轮处理工作流", view.get("title"));
        assertTrue(view.get("nodes") instanceof List<?> nodes && !nodes.isEmpty());
        assertTrue(view.get("edges") instanceof List<?>);
        Map<?, ?> firstNode = (Map<?, ?>) ((List<?>) nodes(view)).get(0);
        assertNotNull(firstNode.get("id"));
        assertNotNull(firstNode.get("title"));
        assertNotNull(firstNode.get("kind"));
        assertNotNull(firstNode.get("depends_on"));
    }

    private static Object nodes(Map<String, Object> view) {
        return view.get("nodes");
    }

    // ── tool 事件契约 ──

    @Test
    void toolEventRunningAndDoneCarryContractFields() {
        RecordingEmitter emitter = runNormalStream();

        List<Emitted> tools = emitter.events.stream()
                .filter(e -> "tool".equals(e.event())).toList();
        assertEquals(2, tools.size(), "单工具应有一次 running + 一次终态");

        Map<String, Object> running = tools.get(0).data();
        assertEquals("sparql_query", running.get("name"));
        assertEquals("running", running.get("status"));

        Map<String, Object> done = tools.get(1).data();
        assertEquals("sparql_query", done.get("name"));
        assertEquals("done", done.get("status"));
        assertNotNull(done.get("durationMs"));
        assertNotNull(done.get("input"), "终态 tool 事件应含入参视图");
        assertNotNull(done.get("output"), "终态 tool 事件应含输出摘要");
        // 登记工具的业务文案四要素（ThinkingCopy 词典）
        assertEquals("查询经营数据", done.get("title"));
        assertNotNull(done.get("goal"));
        assertNotNull(done.get("manualHint"));
    }

    @Test
    void toolEventErrorCarriesErrorMessage() {
        QueryPlan plan = execPlan("sparql_query");
        when(flowIntentRouter.tryRoute(any(), any(), isNull())).thenReturn(java.util.Optional.empty());
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class), any(Executor.StepListener.class)))
                .thenAnswer(inv -> {
                    Executor.StepListener listener = inv.getArgument(2);
                    listener.onStepComplete(ExecutionResult.fail("sparql_query", "本体库不可用"));
                    return List.of(ExecutionResult.fail("sparql_query", "本体库不可用"));
                });
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("失败");

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("查数据", "sse-2", null, emitter);

        Map<String, Object> done = emitter.events.stream()
                .filter(e -> "tool".equals(e.event()) && !"running".equals(e.data().get("status")))
                .findFirst().orElseThrow().data();
        assertEquals("error", done.get("status"));
        assertEquals("本体库不可用", done.get("errorMessage"), "失败 tool 事件应透出 errorMessage");
    }

    // ── text / text_done 契约（打字机分块） ──

    @Test
    void textEventsChunkAt48CharsAndTerminateWithTextDone() {
        String longReport = "长".repeat(120);
        QueryPlan plan = execPlan("sparql_query");
        when(flowIntentRouter.tryRoute(any(), any(), isNull())).thenReturn(java.util.Optional.empty());
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class), any(Executor.StepListener.class)))
                .thenReturn(List.of(ExecutionResult.ok("sparql_query", Map.of())));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn(longReport);
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("查数据", "sse-3", null, emitter);

        List<Emitted> texts = emitter.events.stream()
                .filter(e -> "text".equals(e.event())).toList();
        assertEquals(3, texts.size(), "120 字按 48 切块应为 3 段");
        StringBuilder joined = new StringBuilder();
        for (Emitted e : texts) {
            Object chunk = e.data().get("chunk");
            assertTrue(chunk instanceof String s && !s.isEmpty(), "text 载荷含非空 chunk");
            joined.append((String) chunk);
        }
        assertEquals(longReport, joined.toString(), "分块拼接后应还原完整正文");
        assertEquals(48, ((String) texts.get(0).data().get("chunk")).length(), "首块固定 48 字符");
        assertEquals(24, ((String) texts.get(2).data().get("chunk")).length(), "尾块为余数");

        // text_done 紧随最后一个 text
        List<String> names = emitter.events.stream().map(Emitted::event).toList();
        int lastTextIdx = -1;
        for (int i = 0; i < names.size(); i++) {
            if ("text".equals(names.get(i))) {
                lastTextIdx = i;
            }
        }
        assertTrue(lastTextIdx >= 0, "应至少发出一个 text 事件");
        assertEquals("text_done", names.get(lastTextIdx + 1), "text 之后应紧跟 text_done");
        Map<String, Object> textDone = emitter.events.stream()
                .filter(e -> "text_done".equals(e.event())).findFirst().orElseThrow().data();
        assertTrue(textDone.isEmpty(), "text_done 载荷为空对象");
    }

    // ── done 事件契约 ──

    @Test
    void doneEventCarriesSessionIntentConclusionFollowUps() {
        RecordingEmitter emitter = runNormalStream();

        Map<String, Object> done = emitter.events.get(emitter.events.size() - 1).data();
        assertEquals("sse-1", done.get("session_id"));
        assertEquals("SPARQL_QUERY", done.get("intent"));
        assertEquals("查询完成", done.get("conclusion"));
        assertEquals(List.of("追问1"), done.get("suggested_follow_ups"));
        assertNotNull(done.get("elapsed_ms"));
    }

    // ── error 事件契约 ──

    @Test
    void errorEventCarriesMessageFields() {
        when(flowIntentRouter.tryRoute(any(), any(), isNull())).thenReturn(java.util.Optional.empty());
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of());

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("??", "sse-4", null, emitter);

        Map<String, Object> err = emitter.events.stream()
                .filter(e -> "error".equals(e.event())).findFirst().orElseThrow().data();
        assertNotNull(err.get("errorMessage"));
        assertNotNull(err.get("error"));
    }

    // ── warning 事件契约（持久化失败，流式路径） ──

    @Test
    void warningEventEmittedWhenPersistenceFails() {
        com.sitech.prodai.service.ChatPersistenceService persistence =
                org.mockito.Mockito.mock(com.sitech.prodai.service.ChatPersistenceService.class);
        org.mockito.Mockito.when(persistence.getOrCreateSession(any(), any(), any()))
                .thenThrow(new RuntimeException("DB down"));
        AgentOrchestrator persisting = new AgentOrchestrator(understander, executor, presenter,
                new SessionManager(Optional.empty()), java.util.Optional.of(persistence),
                java.util.Optional.empty(), List.of(stubSparqlTool()), flowIntentRouter,
                null, sceneFlowRouter);

        QueryPlan plan = execPlan("sparql_query");
        when(flowIntentRouter.tryRoute(any(), any(), isNull())).thenReturn(java.util.Optional.empty());
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class), any(Executor.StepListener.class)))
                .thenReturn(List.of(ExecutionResult.ok("sparql_query", Map.of())));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("正文");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());

        RecordingEmitter emitter = new RecordingEmitter();
        persisting.processStream("查数据", "sse-5", null, emitter);

        Emitted warn = emitter.events.stream()
                .filter(e -> "warning".equals(e.event())).findFirst().orElse(null);
        assertNotNull(warn, "持久化失败应向流推送 warning 事件");
        assertNotNull(warn.data().get("message"));
        assertTrue(String.valueOf(warn.data().get("message")).contains("存储异常"));
        assertEquals("DB down", warn.data().get("error"));
        // 主流程不受影响：done 仍正常终止
        assertEquals("done", emitter.events.get(emitter.events.size() - 1).event());
    }

    // ── 8 事件名封闭集合（协议不私自扩名） ──

    @Test
    void allEmittedEventNamesStayWithinProtocolSet() {
        RecordingEmitter emitter = runNormalStream();

        Set<String> allowed = Set.of("thinking", "workflow", "tool", "text", "text_done",
                "done", "error", "warning");
        for (Emitted e : emitter.events) {
            assertTrue(allowed.contains(e.event()), () -> "未登记事件名: " + e.event());
        }
    }
}
