package com.sitech.prodai.service.agent;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.ChatPersistenceService;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.bridge.ChatHumanBridge;
import com.sitech.prodai.service.agent.flow.SceneFlowRouter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * AgentOrchestrator 编排入口单元测试（R8）：
 * 非流式 process 的路由/澄清/确认/执行分支、持久化降级 warnings，
 * 流式 processStream 的事件序列与载荷契约（recording emitter 回放断言）。
 * <p>
 * 纯单元测试：Understander/Executor/Presenter 为接口 mock，
 * SessionManager 真实实例（内存 Map），persistenceService 用 Optional.empty() 或 mock。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentOrchestratorTest {

    @Mock
    private Understander understander;
    @Mock
    private Executor executor;
    @Mock
    private Presenter presenter;
    @Mock
    private ChatPersistenceService persistenceService;
    @Mock
    private LlmService llmService;
    @Mock
    private ChatHumanBridge chatHumanBridge;
    @Mock
    private SceneFlowRouter sceneFlowRouter;

    private SessionManager sessionManager;
    private AgentOrchestrator orchestrator;

    /** 事件录制器：按序记录 (event, payload)，供流式断言回放。 */
    private record Emitted(String event, Map<String, Object> data) {
    }

    private static final class RecordingEmitter implements AgentOrchestrator.StreamEmitter {
        final List<Emitted> events = new ArrayList<>();

        @Override
        public void emit(String event, Map<String, Object> data) {
            events.add(new Emitted(event, data == null ? Map.of() : data));
        }

        List<String> eventNames() {
            return events.stream().map(Emitted::event).toList();
        }
    }

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager(Optional.empty());
        orchestrator = new AgentOrchestrator(understander, executor, presenter, sessionManager,
                Optional.empty(), Optional.of(llmService), List.of(stubSparqlTool(), stubChatConfigTool()),
                chatHumanBridge, sceneFlowRouter);
    }

    /**
     * 注册输出契约的 sparql_query 桩：声明 CONCLUSION 角色，
     * 供编排层 extractConclusion / cacheBusinessEntity 走契约化提取（而非 legacy 键回落）。
     */
    private com.sitech.prodai.service.agent.tool.AgentTool stubSparqlTool() {
        com.sitech.prodai.service.agent.tool.AgentTool tool =
                new com.sitech.prodai.service.agent.tool.AgentTool() {
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
        return tool;
    }

    /**
     * 注册 rd_draft_generate 桩：声明与生产工具一致的参数契约
     * （text 必填、source=question），供手册直达链路 fillQuestionSlots 按契约补槽。
     */
    private com.sitech.prodai.service.agent.tool.AgentTool stubChatConfigTool() {
        com.sitech.prodai.service.agent.tool.AgentTool tool =
                new com.sitech.prodai.service.agent.tool.AgentTool() {
                    @Override
                    public String getName() {
                        return "rd_draft_generate";
                    }

                    @Override
                    public String getDescription() {
                        return "智聊配置草稿生成";
                    }

                    @Override
                    public List<com.sitech.prodai.service.agent.tool.ToolParam> getParams() {
                        return List.of(com.sitech.prodai.service.agent.tool.ToolParam
                                .builder("text").label("配置需求").required().source("question").build());
                    }

                    @Override
                    public ExecutionResult execute(Map<String, Object> params) {
                        return ExecutionResult.ok(getName(), Map.of());
                    }
                };
        return tool;
    }

    // ── fixture 工厂 ──

    private QueryPlan execPlan(String tool, Map<String, Object> params) {
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of(tool), params, "问题");
        plan.setUserQuestion("问题");
        return plan;
    }

    /** 指定意图码的计划（W3 场景工作流路由用例：rd 场景意图 = 工具名大写）。 */
    private QueryPlan execPlan(String tool, String intent) {
        QueryPlan plan = new QueryPlan(intent, List.of(tool), Map.of("question", "问题"), "问题");
        plan.setUserQuestion("问题");
        return plan;
    }

    private QueryPlan clarifyPlan(List<String> missing, Map<String, Map<String, Object>> contracts) {
        QueryPlan plan = new QueryPlan();
        plan.setIntent(QueryPlan.INTENT_CLARIFY);
        plan.setTools(List.of());
        plan.setClarify(missing);
        plan.setClarifyContracts(contracts);
        plan.setParams(new LinkedHashMap<>());
        plan.setUserQuestion("问题");
        return plan;
    }

    private QueryPlan confirmPlan(List<String> candidates) {
        QueryPlan plan = new QueryPlan();
        plan.setIntent(QueryPlan.INTENT_CONFIRM);
        plan.setTools(List.of());
        plan.setCandidates(candidates);
        plan.setParams(new LinkedHashMap<>());
        plan.setUserQuestion("问题");
        return plan;
    }

    // ── P5：非流式 process 主链路 ──

    @Test
    void processExecutesUnderstandExecutePresentChain() {
        QueryPlan plan = execPlan("sparql_query", Map.of("question", "问题"));
        when(understander.understand(eq("查数据"), any(SessionContext.class))).thenReturn(plan);
        ExecutionResult result = ExecutionResult.ok("sparql_query",
                Map.of("rows", 5, "conclusion", "共 5 条记录"));
        when(executor.execute(eq(plan), any(SessionContext.class))).thenReturn(List.of(result));
        when(presenter.present(eq("查数据"), anyList(), any(SessionContext.class))).thenReturn("共 5 条");
        when(presenter.suggestFollowUps(eq("查数据"), anyList(), any(SessionContext.class)))
                .thenReturn(List.of("看明细"));

        Map<String, Object> resp = orchestrator.process("查数据", "s1");

        assertEquals("s1", resp.get("session_id"));
        assertEquals("共 5 条", resp.get("report"));
        assertEquals("SPARQL_QUERY", resp.get("intent"));
        assertEquals("共 5 条记录", resp.get("conclusion"), "结论应从工具输出契约提取");
        assertEquals(List.of("看明细"), resp.get("suggested_follow_ups"));
        assertNotNull(resp.get("query_plan"));
        assertNotNull(resp.get("elapsed_ms"));
        assertTrue(((Number) resp.get("elapsed_ms")).longValue() >= 0);
        // 成功结果缓存为证据（追问复用）：工具原始输出全量缓存
        SessionContext ctx = sessionManager.getOrCreate("s1");
        assertEquals(Map.of("rows", 5, "conclusion", "共 5 条记录"), ctx.getCachedEvidence().get("sparql_query"));
        // 无持久化服务时不产生 warnings
        assertNull(resp.get("warnings"));
    }

    @Test
    void processClarifyBranchReturnsClarifyPayloadWithoutExecution() {
        QueryPlan plan = clarifyPlan(List.of("offerName"),
                Map.of("offerName", Map.of("label", "套餐名称")));
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("请补充套餐名称");

        Map<String, Object> resp = orchestrator.process("查套餐", "s2");

        assertEquals(QueryPlan.INTENT_CLARIFY, resp.get("intent"));
        assertEquals("请补充套餐名称", resp.get("report"));
        assertEquals(List.of("offerName"), resp.get("clarify"));
        assertNotNull(resp.get("clarify_contracts"), "澄清契约应透传给前端渲染选择题");
        verify(executor, never()).execute(any(QueryPlan.class), any(SessionContext.class));
        // 澄清状态写入上下文（补参回传合并的依据）
        SessionContext ctx = sessionManager.getOrCreate("s2");
        assertEquals(QueryPlan.INTENT_CLARIFY, ctx.getLastIntent());
        assertEquals(List.of("offerName"), ctx.getLastClarifyParams());
    }

    @Test
    void processConfirmBranchReturnsCandidates() {
        QueryPlan plan = confirmPlan(List.of("解读A", "解读B"));
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(llmService.completePrompt(any())).thenReturn("请确认您想做哪一种");

        Map<String, Object> resp = orchestrator.process("有歧义", "s3");

        assertEquals(QueryPlan.INTENT_CONFIRM, resp.get("intent"));
        assertEquals(List.of("解读A", "解读B"), resp.get("candidates"));
        assertEquals("请确认您想做哪一种", resp.get("report"), "确认话术由 LLM 生成");
        verify(executor, never()).execute(any(QueryPlan.class), any(SessionContext.class));
    }

    @Test
    void processConfirmBranchFallsBackToTemplateWhenLlmUnavailable() {
        QueryPlan plan = confirmPlan(List.of("解读A"));
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(llmService.completePrompt(any())).thenThrow(new RuntimeException("网关超时"));

        Map<String, Object> resp = orchestrator.process("有歧义", "s3b");

        String report = String.valueOf(resp.get("report"));
        assertTrue(report.contains("解读A"), () -> "LLM 失败应回退候选模板: " + report);
        assertTrue(report.contains("1."), () -> "模板应编号列出候选: " + report);
    }

    // ── W2：挂起态短路恢复 ──

    /** 会话挂起态绑定（ChatHumanBridge.buildBinding 产物的同构 fixture）。 */
    private Map<String, Object> suspensionBinding() {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("execution_id", "EX-100");
        binding.put("resume_token", "tok-abc");
        binding.put("node_id", "h1");
        binding.put("workflow_code", "chat_configure");
        return binding;
    }

    @Test
    void processSuspensionShortCircuitsToResumeWithoutUnderstand() {
        SessionContext ctx = sessionManager.getOrCreate("s-r1");
        ctx.setExecutionBinding(suspensionBinding());
        when(chatHumanBridge.resume(eq(ctx.getExecutionBinding()), eq("确认"), isNull(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed")));

        Map<String, Object> resp = orchestrator.process("确认", "s-r1");

        assertEquals("FLOW_RESUME", resp.get("intent"), "挂起态回复意图应为 FLOW_RESUME");
        // 无 output_data 业务字段时回退为通用完成文案（不拼空摘要）
        assertEquals("流程已按您的确认执行完成。", resp.get("report"));
        assertEquals("completed", ((Map<?, ?>) resp.get("flow_execution")).get("status"));
        // 短路：理解层/流程路由均不再触达
        verify(understander, never()).understand(any(), any());
        // 恢复成功后绑定应被清空（会话退出挂起态）
        assertNull(sessionManager.getOrCreate("s-r1").getExecutionBinding(), "恢复后绑定应清空");
    }

    @Test
    void processEngineRejectionClearsBindingAndReportsFailure() {
        SessionContext ctx = sessionManager.getOrCreate("s-r2");
        ctx.setExecutionBinding(suspensionBinding());
        when(chatHumanBridge.resume(any(), any(), any(), any()))
                .thenReturn(ApiResponse.fail("恢复令牌无效或已被使用"));

        Map<String, Object> resp = orchestrator.process("确认", "s-r2");

        assertEquals("FLOW_RESUME", resp.get("intent"));
        String report = String.valueOf(resp.get("report"));
        assertTrue(report.contains("流程恢复失败"), () -> "引擎拒绝应生成失败话术: " + report);
        assertNull(sessionManager.getOrCreate("s-r2").getExecutionBinding(),
                "引擎拒绝后应清空绑定，避免会话卡死挂起态");
    }

    @Test
    void processBridgeUnavailableFallsThroughToNormalChain() {
        AgentOrchestrator legacy = new AgentOrchestrator(understander, executor, presenter, sessionManager,
                Optional.empty(), Optional.of(llmService), List.of(stubSparqlTool()),
                null, sceneFlowRouter);
        SessionContext ctx = sessionManager.getOrCreate("s-r3");
        ctx.setExecutionBinding(suspensionBinding());
        QueryPlan plan = execPlan("sparql_query", Map.of("question", "问题"));
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(eq(plan), any(SessionContext.class))).thenReturn(List.of());
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("常规回答");

        Map<String, Object> resp = legacy.process("换个说法", "s-r3");

        assertEquals("SPARQL_QUERY", resp.get("intent"), "无桥接器时应放行常规链路（零行为变更兜底）");
        assertEquals("常规回答", resp.get("report"));
    }

    @Test
    void processResumeSuspendedAgainRefreshesBinding() {
        SessionContext ctx = sessionManager.getOrCreate("s-r4");
        ctx.setExecutionBinding(suspensionBinding());
        when(chatHumanBridge.resume(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "waiting_human")));

        Map<String, Object> resp = orchestrator.process("确认", "s-r4");

        assertEquals("FLOW_RESUME", resp.get("intent"));
        assertEquals("waiting_human", ((Map<?, ?>) resp.get("flow_execution")).get("status"));
    }

    @Test
    void streamSuspensionShortCircuitsToResumeWithoutUnderstand() {
        SessionContext ctx = sessionManager.getOrCreate("s-r5");
        ctx.setExecutionBinding(suspensionBinding());
        when(chatHumanBridge.resume(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed")));

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("确认", "s-r5", null, emitter);

        List<String> names = emitter.eventNames();
        assertEquals("thinking", names.get(0), "挂起态恢复首个事件仍为 thinking");
        assertEquals("done", names.get(names.size() - 1), "流以 done 终止");
        Map<String, Object> done = emitter.events.get(emitter.events.size() - 1).data();
        assertEquals("FLOW_RESUME", done.get("intent"));
        assertEquals("completed", ((Map<?, ?>) done.get("flow_execution")).get("status"));
        // 短路：理解层不再触达
        verify(understander, never()).understandAll(any(), any());
        assertFalse(sessionManager.getOrCreate("s-r5").hasPendingExecution(),
                "恢复完成后会话应退出挂起态");
    }

    @Test
    void streamSuspensionAgainEmitsBindingAndContractsInDone() {
        SessionContext ctx = sessionManager.getOrCreate("s-r6");
        ctx.setExecutionBinding(suspensionBinding());
        Map<String, Object> formSpec = Map.of("form_code", "approval_form",
                "fields", List.of(Map.of("field_code", "approved", "field_name", "是否同意", "required", true)));
        Map<String, Object> nextExecution = new LinkedHashMap<>();
        nextExecution.put("execution_id", "EX-200");
        nextExecution.put("resume_token", "tok-next");
        nextExecution.put("status", "waiting_human");
        nextExecution.put("current_node_id", "h2");
        when(chatHumanBridge.resume(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(nextExecution));
        Map<String, Object> newBinding = new LinkedHashMap<>();
        newBinding.put("execution_id", "EX-200");
        newBinding.put("resume_token", "tok-next");
        newBinding.put("form_spec", formSpec);
        when(chatHumanBridge.buildBinding(any())).thenReturn(newBinding);
        when(chatHumanBridge.toClarifyContracts(eq(formSpec)))
                .thenReturn(Map.of("approved", Map.of("label", "是否同意", "required", true)));

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("确认", "s-r6", null, emitter);

        Map<String, Object> done = emitter.events.get(emitter.events.size() - 1).data();
        assertEquals("FLOW_RESUME", done.get("intent"));
        assertNotNull(done.get("execution_binding"), "再次挂起应随 done 下发新绑定");
        assertEquals("EX-200", ((Map<?, ?>) done.get("execution_binding")).get("execution_id"));
        assertNotNull(done.get("clarify_contracts"), "form_spec 翻译产物应随 done 下发");
        // 绑定已刷新为下一道阶段门
        assertEquals("EX-200", sessionManager.getOrCreate("s-r6").getExecutionBinding().get("execution_id"));
    }

    @Test
    void streamFlowExecutionSuspensionCapturesBindingIntoContext() {
        Map<String, Object> flowReply = new LinkedHashMap<>();
        flowReply.put("intent", "FLOW_EXEC");
        flowReply.put("report", "流程到达确认节点");
        flowReply.put("flow_matched", Map.of("workflow_code", "chat_configure"));
        Map<String, Object> execMap = new LinkedHashMap<>();
        execMap.put("execution_id", "EX-300");
        execMap.put("resume_token", "tok-300");
        execMap.put("status", "waiting_human");
        execMap.put("current_node_id", "h1");
        flowReply.put("flow_execution", execMap);
        Map<String, Object> binding = suspensionBinding();
        binding.put("execution_id", "EX-300");
        binding.put("resume_token", "tok-300");
        when(understander.understandAll(any(), any(SessionContext.class)))
                .thenReturn(List.of(execPlan("rd_draft_generate", "RD_DRAFT_GENERATE")));
        when(sceneFlowRouter.tryRoute(any(QueryPlan.class), any(SessionContext.class), any()))
                .thenReturn(Optional.of(flowReply));
        when(chatHumanBridge.buildBinding(eq(execMap))).thenReturn(binding);

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("跑一下流程", "s-r7", null, emitter);

        Map<String, Object> done = emitter.events.get(emitter.events.size() - 1).data();
        assertEquals("FLOW_EXEC", done.get("intent"));
        assertNotNull(done.get("execution_binding"), "FLOW_EXEC 挂起应随 done 下发绑定");
        // 挂起绑定写入会话上下文：下一轮回复直接走 resume 短路
        SessionContext ctx = sessionManager.getOrCreate("s-r7");
        assertNotNull(ctx.getExecutionBinding(), "挂起后绑定应写入上下文");
        assertEquals("EX-300", ctx.getExecutionBinding().get("execution_id"));
        assertTrue(ctx.hasPendingExecution(), "绑定完整时应处于挂起态");
    }

    // ── W3：场景工作流路由（SceneFlowRouter 接线） ──

    @Test
    void streamSceneWorkflowRouteShortCircuitsExecutor() {
        Map<String, Object> sceneReply = new LinkedHashMap<>();
        sceneReply.put("intent", "FLOW_EXEC");
        sceneReply.put("report", "场景工作流已执行完成");
        sceneReply.put("flow_matched", Map.of("workflow_code", "query_reuse_v2"));
        sceneReply.put("flow_execution", Map.of("status", "completed"));
        when(understander.understandAll(any(), any(SessionContext.class)))
                .thenReturn(List.of(execPlan("rd_draft_generate", "RD_DRAFT_GENERATE")));
        when(sceneFlowRouter.tryRoute(any(QueryPlan.class), any(SessionContext.class), any()))
                .thenReturn(Optional.of(sceneReply));

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("配一个套餐", "s-w1", null, emitter);

        List<String> names = emitter.eventNames();
        assertEquals("thinking", names.get(0));
        assertEquals("done", names.get(names.size() - 1));
        Map<String, Object> done = emitter.events.get(emitter.events.size() - 1).data();
        assertEquals("FLOW_EXEC", done.get("intent"));
        assertEquals("completed", ((Map<?, ?>) done.get("flow_execution")).get("status"));
        // 短路：执行层/表达层不再触达
        verify(executor, never()).execute(any(QueryPlan.class), any(SessionContext.class), any());
        verify(presenter, never()).present(any(), anyList(), any());
    }

    @Test
    void processSceneWorkflowRouteShortCircuitsExecutor() {
        Map<String, Object> sceneReply = new LinkedHashMap<>();
        sceneReply.put("intent", "FLOW_EXEC");
        sceneReply.put("report", "场景工作流已执行完成");
        sceneReply.put("flow_matched", Map.of("workflow_code", "query_reuse_v2"));
        sceneReply.put("flow_execution", Map.of("status", "completed"));
        when(understander.understand(any(), any(SessionContext.class)))
                .thenReturn(execPlan("rd_draft_generate", "RD_DRAFT_GENERATE"));
        when(sceneFlowRouter.tryRoute(any(QueryPlan.class), any(SessionContext.class), any()))
                .thenReturn(Optional.of(sceneReply));

        Map<String, Object> resp = orchestrator.process("配一个套餐", "s-w2");

        assertEquals("FLOW_EXEC", resp.get("intent"));
        assertEquals("场景工作流已执行完成", resp.get("report"));
        verify(executor, never()).execute(any(QueryPlan.class), any(SessionContext.class));
        verify(presenter, never()).present(any(), anyList(), any());
    }

    @Test
    void sceneRouterMissFallsThroughToDynamicOrchestration() {
        QueryPlan plan = execPlan("sparql_query", Map.of("question", "问题"));
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(eq(plan), any(SessionContext.class))).thenReturn(List.of());
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("动态编排结果");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());
        when(sceneFlowRouter.tryRoute(any(QueryPlan.class), any(SessionContext.class), any()))
                .thenReturn(Optional.empty());

        Map<String, Object> resp = orchestrator.process("查数据", "s-w3");

        assertEquals("SPARQL_QUERY", resp.get("intent"), "路由未命中应回落动态编排（双轨兜底）");
        assertEquals("动态编排结果", resp.get("report"));
        verify(executor).execute(eq(plan), any(SessionContext.class));
    }

    @Test
    void processExecutorFailureStillPresentsPartialReport() {
        QueryPlan plan = execPlan("sparql_query", Map.of());
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(eq(plan), any(SessionContext.class)))
                .thenReturn(List.of(ExecutionResult.fail("sparql_query", "本体库不可用")));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("查询失败，请稍后重试");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());

        Map<String, Object> resp = orchestrator.process("查数据", "s4");

        assertEquals("查询失败，请稍后重试", resp.get("report"));
        assertEquals("", resp.get("conclusion"), "失败结果不产生结论");
        // 失败结果不缓存证据
        SessionContext ctx = sessionManager.getOrCreate("s4");
        assertTrue(ctx.getCachedEvidence().isEmpty(), "失败工具输出不应入证据缓存");
    }

    @Test
    void processPersistenceFailureAppendsWarningToResponse() {
        AgentOrchestrator persisting = new AgentOrchestrator(understander, executor, presenter,
                sessionManager, Optional.of(persistenceService), Optional.empty(), List.of(),
                null, sceneFlowRouter);
        QueryPlan plan = execPlan("sparql_query", Map.of());
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(eq(plan), any(SessionContext.class))).thenReturn(List.of());
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("结果");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());
        when(persistenceService.getOrCreateSession(any(), any(), any()))
                .thenThrow(new RuntimeException("DB 连接断开"));

        Map<String, Object> resp = persisting.process("查数据", "s5");

        List<String> warnings = (List<String>) resp.get("warnings");
        assertNotNull(warnings, "持久化失败应透传 warnings");
        assertTrue(warnings.get(0).contains("存储异常"), () -> "warning 文案: " + warnings);
        assertTrue(warnings.get(0).contains("DB 连接断开"));
    }

    // ── P5：流式 processStream 事件序列 ──

    @Test
    void streamNormalPathEmitsThinkingWorkflowToolTextDone() {
        QueryPlan plan = execPlan("sparql_query", Map.of("question", "问题"));
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        ExecutionResult result = ExecutionResult.ok("sparql_query", Map.of("rows", 3));
        when(executor.execute(eq(plan), any(SessionContext.class), any(Executor.StepListener.class)))
                .thenAnswer(inv -> {
                    Executor.StepListener listener = inv.getArgument(2);
                    listener.onStepStart("sparql_query");
                    listener.onStepComplete(result);
                    return List.of(result);
                });
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("查询完成");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("查数据", "s6", null, emitter);

        List<String> names = emitter.eventNames();
        // 事件骨架：thinking 起表 → workflow → thinking 更新 → tool(running) → tool(done)
        //          → thinking generate → text → text_done → done
        assertEquals("thinking", names.get(0));
        assertEquals("workflow", names.get(1));
        assertTrue(names.contains("tool"), "工具事件应即时下发");
        assertTrue(names.indexOf("workflow") < names.indexOf("text"), "工作流图应先于正文下发");
        assertEquals("text_done", names.get(names.size() - 2), "正文后紧跟 text_done");
        assertEquals("done", names.get(names.size() - 1), "流以 done 终止");

        // tool running/done 事件载荷契约
        Map<String, Object> runningPayload = emitter.events.stream()
                .filter(e -> "tool".equals(e.event()) && "running".equals(e.data().get("status")))
                .findFirst().orElseThrow().data();
        assertEquals("sparql_query", runningPayload.get("name"));
        Map<String, Object> donePayload = emitter.events.stream()
                .filter(e -> "tool".equals(e.event()) && "done".equals(e.data().get("status")))
                .findFirst().orElseThrow().data();
        assertEquals("sparql_query", donePayload.get("name"));
        assertEquals("done", donePayload.get("status"));
        assertNotNull(donePayload.get("durationMs"));
        assertNotNull(donePayload.get("output"), "done 态 tool 事件应含 output 摘要");

        // 终态 done 载荷契约
        Map<String, Object> terminal = emitter.events.get(emitter.events.size() - 1).data();
        assertEquals("s6", terminal.get("session_id"));
        assertEquals("SPARQL_QUERY", terminal.get("intent"));
        assertNotNull(terminal.get("conclusion"));
        assertNotNull(terminal.get("suggested_follow_ups"));
        assertNotNull(terminal.get("elapsed_ms"));
    }

    @Test
    void streamEmptyPlansEmitsErrorEvent() {
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of());

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("??", "s8", null, emitter);

        assertEquals(List.of("thinking", "error"), emitter.eventNames(), "空计划应发 error 并终止");
        Map<String, Object> err = emitter.events.get(1).data();
        assertNotNull(err.get("errorMessage"));
        assertNotNull(err.get("error"));
    }

    @Test
    void streamClarifyBranchEmitsThinkingTextDoneWithClarify() {
        QueryPlan plan = clarifyPlan(List.of("offerName"), Map.of());
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("请补充套餐名称");

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("查套餐", "s9", null, emitter);

        List<String> names = emitter.eventNames();
        assertTrue(names.contains("workflow"), "澄清分支也先下发工作流图");
        assertTrue(names.get(names.size() - 1).equals("done"));
        Map<String, Object> done = emitter.events.get(emitter.events.size() - 1).data();
        assertEquals(QueryPlan.INTENT_CLARIFY, done.get("intent"));
        assertEquals(List.of("offerName"), done.get("clarify"));
        verify(executor, never()).execute(any(QueryPlan.class), any(SessionContext.class), any());
    }

    @Test
    void streamConfirmBranchEmitsDoneWithCandidates() {
        QueryPlan plan = confirmPlan(List.of("甲", "乙"));
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        when(llmService.completePrompt(any())).thenThrow(new RuntimeException("no llm"));

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("歧义", "s10", null, emitter);

        assertEquals("done", emitter.eventNames().get(emitter.events.size() - 1));
        Map<String, Object> done = emitter.events.get(emitter.events.size() - 1).data();
        assertEquals(QueryPlan.INTENT_CONFIRM, done.get("intent"));
        assertEquals(List.of("甲", "乙"), done.get("candidates"));
    }

    @Test
    void streamMultiIntentSplitsPlansWithIndexedStepIdsAndSegment() {
        QueryPlan p1 = execPlan("sparql_query", Map.of("question", "问题"));
        QueryPlan p2 = execPlan("swrl_root_cause", Map.of("question", "问题"));
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(p1, p2));
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class), any(Executor.StepListener.class)))
                .thenAnswer(inv -> {
                    Executor.StepListener listener = inv.getArgument(2);
                    QueryPlan plan = inv.getArgument(0);
                    listener.onStepComplete(ExecutionResult.ok(plan.getTools().get(0), Map.of("rows", 1)));
                    return List.of(ExecutionResult.ok(plan.getTools().get(0), Map.of("rows", 1)));
                });
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("子答案");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("查数据并归因", "s11", null, emitter);

        List<String> names = emitter.eventNames();
        // 常规链路的 thinking 起表先发（理解调用前），随后 processStreamMulti 内再发 workflow 图
        assertEquals("thinking", names.get(0));
        assertEquals("workflow", names.get(1), "多意图进入分段处理时先下发工作流图（MULTI 分支标注）");
        assertTrue(names.contains("done"));
        // 每个子计划独立 thinking 链：0_intent / 1_plan 前缀
        boolean hasIndexedIntent = false;
        boolean hasSegment = false;
        for (Emitted e : emitter.events) {
            if (!"thinking".equals(e.event())) {
                continue;
            }
            Object stepsObj = e.data().get("steps");
            if (!(stepsObj instanceof List<?> steps) || steps.isEmpty()) {
                continue;
            }
            Map<?, ?> step = (Map<?, ?>) steps.get(0);
            String id = String.valueOf(step.get("id"));
            if (id.startsWith("0_") || id.startsWith("1_")) {
                hasIndexedIntent = true;
            }
            if (step.containsKey("segment") || e.data().containsKey("segment")) {
                hasSegment = true;
            }
        }
        assertTrue(hasIndexedIntent, "多意图 thinking 步骤 id 应带 0_/1_ 索引前缀");
        assertTrue(hasSegment, "多意图事件应携带 segment 分组标记");
        // 合并正文：两个子答案拼接（按子计划顺序）
        long textCount = emitter.events.stream().filter(e -> "text".equals(e.event())).count();
        assertTrue(textCount > 0, "合并正文应随 text 事件下发");
        verify(presenter, times(2)).present(any(), anyList(), any(SessionContext.class));
    }

    // ── 手册直达链路：session_id 注入契约 ──

    @Test
    void streamPlaybookPathInjectsSessionIdIntoPlanParams() {
        // 理解层 LLM 识别意图命中手册适用域 → 升级流式直达链路
        QueryPlan plan = new QueryPlan("RD_FILE_PARSE", List.of("rd_doc_parse"),
                Map.of("question", "导入文档"), "导入文档");
        plan.setUserQuestion("导入文档");
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("手册执行完毕");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());
        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("导入文档", "s-pb1", null, "rd", emitter);

        // 手册意图升级直达 doc-batch-import，执行层收到的 plan.params
        // 必须携带服务端 SessionContext 的 session_id——否则 rd_workorder_create 批量开单
        // 因 sessionId 空白短路，工单不落库，前端工单卡片无从展示
        ArgumentCaptor<QueryPlan> planCaptor = ArgumentCaptor.forClass(QueryPlan.class);
        verify(executor).execute(planCaptor.capture(), any(SessionContext.class), any(Executor.StepListener.class));
        assertEquals("s-pb1", planCaptor.getValue().getParams().get("session_id"),
                "手册链路 plan.params 应注入服务端 session_id（强制覆盖）");
    }

    @Test
    void processPlaybookPathInjectsSessionIdIntoPlanParams() {
        QueryPlan plan = new QueryPlan("RD_FILE_PARSE", List.of("rd_doc_parse"),
                Map.of("question", "导入文档"), "导入文档");
        plan.setUserQuestion("导入文档");
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        orchestrator.process("导入文档", "s-pb2", null, "rd");

        ArgumentCaptor<QueryPlan> planCaptor = ArgumentCaptor.forClass(QueryPlan.class);
        verify(executor).execute(planCaptor.capture(), any(SessionContext.class));
        assertEquals("s-pb2", planCaptor.getValue().getParams().get("session_id"),
                "手册同步链路 plan.params 应注入服务端 session_id（强制覆盖）");
    }

    // ── 手册直达链路：source=question 参数按契约补槽 ──

    @Test
    void streamPlaybookPathFillsQuestionSlotsFromContract() {
        // 手册链路工具契约声明 source=question 的参数（rd_draft_generate 的 text）
        // 必须以用户原话自动填充——否则工具因参数缺失报「缺少配置需求描述」，四步全部执行失败。
        // 理解层 LLM 识别配置意图命中智聊手册适用域 → 升级直达链路
        String utterance = "配置一个家庭融合套餐，月费158，带500M宽带";
        QueryPlan plan = new QueryPlan("RD_CONFIG_CHAT", List.of("rd_category_resolve", "rd_slot_extract", "rd_draft_generate"),
                Map.of("question", utterance), utterance);
        plan.setUserQuestion(utterance);
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("手册执行完毕");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());
        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream(utterance, "s-slot1", null, "rd", emitter);

        ArgumentCaptor<QueryPlan> planCaptor = ArgumentCaptor.forClass(QueryPlan.class);
        verify(executor).execute(planCaptor.capture(), any(SessionContext.class), any(Executor.StepListener.class));
        Map<String, Object> params = planCaptor.getValue().getParams();
        assertEquals(utterance, params.get("text"),
                "手册链路应按契约将 source=question 的 text 参数补为用户原话");
        assertEquals(utterance, params.get("question"),
                "plan.params 原始 question 键保持不变");
    }

    @Test
    void processPlaybookPathFillsQuestionSlotsFromContract() {
        String utterance = "配置一个家庭融合套餐，月费158，带500M宽带";
        QueryPlan plan = new QueryPlan("RD_CONFIG_CHAT", List.of("rd_category_resolve", "rd_slot_extract", "rd_draft_generate"),
                Map.of("question", utterance), utterance);
        plan.setUserQuestion(utterance);
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        orchestrator.process(utterance, "s-slot2", null, "rd");

        ArgumentCaptor<QueryPlan> planCaptor = ArgumentCaptor.forClass(QueryPlan.class);
        verify(executor).execute(planCaptor.capture(), any(SessionContext.class));
        assertEquals(utterance,
                planCaptor.getValue().getParams().get("text"),
                "手册同步链路同样应按契约补齐 source=question 的工具参数");
    }

    // ── 手册意图升级：LLM 识别的意图命中手册 applies_to.intents → 直达链路 ──

    @Test
    void processUpgradesToPlaybookWhenIntentHitsAppliesToIntents() {
        // 理解层 LLM 输出 ops 规范意图（如 analyze→PRODUCT_OPS_QUERY 归一化产物）：
        // 意图命中 market-insight.applies_to.intents → 升级走手册直达链路（runPlaybookPath），
        // 执行的是手册双工具链而非 LLM 自选链
        QueryPlan plan = new QueryPlan("PRODUCT_OPS_QUERY", List.of("sparql_query", "swrl_risk_audit"),
                Map.of("question", "问题"), "查一下在售5G套餐的增长趋势和风险商品");
        plan.setUserQuestion("查一下在售5G套餐的增长趋势和风险商品");
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class)))
                .thenReturn(List.of(ExecutionResult.ok("sparql_query", Map.of("rows", 3))));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("手册执行完毕");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());

        Map<String, Object> resp = orchestrator.process("查一下在售5G套餐的增长趋势和风险商品", "s-up1", null, "ops");

        assertEquals("market-insight", resp.get("playbook"), "意图命中手册适用域 → 回复带手册标记");
        ArgumentCaptor<QueryPlan> planCaptor = ArgumentCaptor.forClass(QueryPlan.class);
        verify(executor).execute(planCaptor.capture(), any(SessionContext.class));
        // 执行链从手册步骤序列保序去重提取（步骤 tool 是调用语句，非 LLM 自选子集），意图归位手册首项规范意图
        // market-insight v2 已前置 metric_query 指标仓步骤（趋势/环比打底），故工具链为三步
        assertEquals(List.of("metric_query", "sparql_query", "swrl_risk_audit"),
                planCaptor.getValue().getTools(), "升级后应按手册步骤序列提取的工具链执行");
        assertEquals("PRODUCT_OPS_QUERY", planCaptor.getValue().getIntent(),
                "意图归位手册 applies_to.intents 首项（直达链路计划意图）");
    }

    @Test
    void streamUpgradesToPlaybookWhenIntentHitsAppliesToIntents() {
        QueryPlan plan = new QueryPlan("PRODUCT_OPS_REASON", List.of("swrl_root_cause"),
                Map.of("question", "问题"), "为什么上月收入下滑");
        plan.setUserQuestion("为什么上月收入下滑");
        when(understander.understandAll(any(), any(SessionContext.class))).thenReturn(List.of(plan));
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class), any(Executor.StepListener.class)))
                .thenAnswer(inv -> {
                    Executor.StepListener listener = inv.getArgument(2);
                    listener.onStepComplete(ExecutionResult.ok("swrl_root_cause", Map.of("pathCount", 2)));
                    return List.of(ExecutionResult.ok("swrl_root_cause", Map.of("pathCount", 2)));
                });
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("手册执行完毕");
        when(presenter.suggestFollowUps(any(), anyList(), any(SessionContext.class))).thenReturn(List.of());

        RecordingEmitter emitter = new RecordingEmitter();
        orchestrator.processStream("为什么上月收入下滑", "s-up2", null, "ops", emitter);

        ArgumentCaptor<QueryPlan> planCaptor = ArgumentCaptor.forClass(QueryPlan.class);
        verify(executor).execute(planCaptor.capture(), any(SessionContext.class), any(Executor.StepListener.class));
        assertEquals(List.of("sparql_query", "swrl_root_cause", "ontology_explain"),
                planCaptor.getValue().getTools(), "流式升级后同样按手册步骤序列提取的工具链执行");
        // 时间线呈现 sop-step-N 手册步骤（动态编排是工具名步骤，无 sop-step）
        boolean hasSopStep = emitter.events.stream().filter(e -> "thinking".equals(e.event()))
                .anyMatch(e -> e.data().get("steps") instanceof List<?> steps && !steps.isEmpty()
                        && String.valueOf(((Map<?, ?>) steps.get(0)).get("id")).startsWith("sop-step-"));
        assertTrue(hasSopStep, "升级链路时间线应落地 sop-step-N 手册步骤");
    }

    @Test
    void processDoesNotUpgradeWhenIntentMissesAllPlaybooks() {
        // rd 场景未注册进手册意图的意图（如 RD_DRAFT_MANAGE）→ 不升级，回落常规动态编排
        QueryPlan plan = execPlan("rd_draft_manage", "RD_DRAFT_MANAGE");
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(eq(plan), any(SessionContext.class)))
                .thenReturn(List.of(ExecutionResult.ok("rd_draft_manage", Map.of())));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("常规链路回复");

        Map<String, Object> resp = orchestrator.process("改一下草稿", "s-up3", null, "rd");

        assertEquals("常规链路回复", resp.get("report"));
        assertNull(resp.get("playbook"), "未命中手册意图不应有手册标记");
        verify(executor).execute(eq(plan), any(SessionContext.class));
    }

    @Test
    void processDoesNotUpgradeClarifyOrConfirmIntents() {
        QueryPlan plan = clarifyPlan(List.of("offerName"), Map.of());
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("请补充套餐名称");

        Map<String, Object> resp = orchestrator.process("查套餐", "s-up4", null, "ops");

        assertEquals(QueryPlan.INTENT_CLARIFY, resp.get("intent"), "会话协作意图不参与手册升级");
        verify(executor, never()).execute(any(QueryPlan.class), any(SessionContext.class));
    }

    // ── 查询收敛 SOP（方案 §4.5，阶段 B1）：手册链路粗查规模判定分支 ──

    /** 构造手册直达命中：理解层计划意图命中 query-ask.applies_to.intents（SPARQL_QUERY）+ sparql_query 工具。 */
    private QueryPlan queryAskPlan() {
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of("sparql_query"),
                Map.of("question", "问题"), "查一下有哪些在售套餐");
        plan.setUserQuestion("查一下有哪些在售套餐");
        return plan;
    }

    /** sparql_query 结果桩：entity_ids 携带 hits 个命中（收敛判定输入）。 */
    private List<ExecutionResult> sparqlHits(int hits) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < hits; i++) {
            ids.add("OFF-" + i);
        }
        return List.of(ExecutionResult.ok("sparql_query", Map.of("entity_ids", ids, "raw_results", List.of())));
    }

    @Test
    void playbookCoarseHitsWithinThresholdPresentsDirectly() {
        // 命中 ≤5（舒适阈值）：走原手册呈现，响应无收敛字段
        QueryPlan plan = queryAskPlan();
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class)))
                .thenReturn(sparqlHits(5));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("命中 5 条，直接呈现");

        Map<String, Object> resp = orchestrator.process("查一下有哪些在售套餐", "s-refine1", null, "query");

        assertEquals("query-ask", resp.get("playbook"));
        assertEquals("命中 5 条，直接呈现", resp.get("report"));
        assertNull(resp.get("refine_hits"), "规模舒适不应触发收敛分支");
    }

    @Test
    void playbookCoarseHitsBeyondThresholdAsksRefineClarify() {
        // 命中 >20：转收敛追问（CLARIFY 契约形态），复用既有澄清响应契约
        QueryPlan plan = queryAskPlan();
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class)))
                .thenReturn(sparqlHits(30));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("命中较多，请补充筛选条件");

        Map<String, Object> resp = orchestrator.process("查一下有哪些在售套餐", "s-refine2", null, "query");

        assertEquals(QueryPlan.INTENT_CLARIFY, resp.get("intent"), "超阈值 → 收敛追问");
        assertEquals("query-ask", resp.get("playbook"), "收敛追问带手册来源标记");
        assertEquals(30, resp.get("refine_hits"), "命中数随响应透出（审计口径）");
        assertEquals(List.of("city", "monthly_fee"), resp.get("clarify"), "首轮追问地市 + 资费档位");
        assertNotNull(resp.get("clarify_contracts"), "追问契约随响应下发（前端渲染选择题）");
    }

    @Test
    void playbookRefineRoundsCapForcesDirectPresent() {
        // 已达收敛上限（meta.refine_rounds=2）：judge 回落 OK，直接呈现不再追问
        QueryPlan plan = queryAskPlan();
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class)))
                .thenReturn(sparqlHits(30));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("共 30 条，已按相关度排序呈现前 10 条");

        SessionContext ctx = sessionManager.getOrCreate("s-refine3");
        ctx.incrementRefineRounds();
        ctx.incrementRefineRounds();

        Map<String, Object> resp = orchestrator.process("查一下有哪些在售套餐", "s-refine3", null, "query");

        assertEquals("query-ask", resp.get("playbook"));
        assertEquals("共 30 条，已按相关度排序呈现前 10 条", resp.get("report"));
        assertNull(resp.get("clarify"), "收敛轮次达上限 → 强制降维呈现，不再追问");
    }

    @Test
    void playbookModerateHitsPresentsWithRefineSuggestion() {
        // 命中 6~20（REFINE）：照常呈现 + 收敛建议（refine_suggest），不阻断
        QueryPlan plan = queryAskPlan();
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class)))
                .thenReturn(sparqlHits(10));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("命中 10 条");

        Map<String, Object> resp = orchestrator.process("查一下有哪些在售套餐", "s-refine4", null, "query");

        assertEquals("命中 10 条", resp.get("report"), "REFINE 档不阻断，照常呈现");
        assertEquals(10, resp.get("refine_hits"));
        assertNotNull(resp.get("refine_suggest"), "收敛建议随响应透出（可选择性追问）");
        assertNull(resp.get("clarify"), "REFINE 档不生成强制追问");
    }

    @Test
    void playbookRefineNotAppliedToNonQueryPlaybooks() {
        // 非查询类手册（工具链不含 sparql_query）不做收敛判定——零感知
        QueryPlan plan = new QueryPlan("RD_DRAFT_MANAGE", List.of("rd_draft_manage"),
                Map.of("question", "问题"), "配置一个家庭融合套餐");
        plan.setUserQuestion("配置一个家庭融合套餐");
        when(understander.understand(any(), any(SessionContext.class))).thenReturn(plan);
        when(executor.execute(any(QueryPlan.class), any(SessionContext.class)))
                .thenReturn(List.of(ExecutionResult.ok("rd_draft_manage", Map.of())));
        when(presenter.present(any(), anyList(), any(SessionContext.class))).thenReturn("草稿已生成");

        Map<String, Object> resp = orchestrator.process("配置一个家庭融合套餐", "s-refine5", null, "rd");

        assertNull(resp.get("refine_hits"), "非查询手册不触发收敛判定");
        assertNull(resp.get("clarify"));
    }
}
