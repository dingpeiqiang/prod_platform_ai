package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SceneFlowRouter 单元测试（智聊重设计 W3-1，方案 §7.2 验收）：
 * 确定性映射（同输入=同路径）、协议轮短路、场景未配置回落、引擎结果组装同构。
 * <p>
 * 去旧留新（W3-2）：路由恒启用，原 enabled Flag 用例已移除。
 */
@ExtendWith(MockitoExtension.class)
class SceneFlowRouterTest {

    @Mock
    private FlowEngineService flowEngineService;

    private ProdAiProperties properties;
    private SceneFlowRouter router;

    @BeforeEach
    void setUp() {
        properties = new ProdAiProperties();
        var playbookRegistry = new com.sitech.prodai.service.agent.playbook.PlaybookRegistry();
        playbookRegistry.init();
        router = new SceneFlowRouter(properties, flowEngineService, playbookRegistry);
        // rd 场景工作流已删（手册全覆盖架空）；用 query 场景（query_reuse_v2 存量）验证确定性映射
        properties.getChatWorkflow().getSceneWorkflows().put("query", "query_reuse_v2");
    }

    private QueryPlan execPlan(String tool, String intent) {
        QueryPlan plan = new QueryPlan(intent, List.of(tool), Map.of("monthly_fee", 59), "配一个套餐");
        plan.setUserQuestion("配一个套餐");
        return plan;
    }

    private SessionContext rdSession() {
        SessionContext ctx = new SessionContext("s1");
        ctx.setScene("query");
        return ctx;
    }

    /** rd 会话：工作流配置已删除，但三本手册仍在 rd 场景声明适用域。 */
    private SessionContext plainRdSession() {
        SessionContext ctx = new SessionContext("s-rd");
        ctx.setScene("rd");
        return ctx;
    }

    // ── 确定性映射 ──

    /**
     * 未被手册覆盖的工具/意图（sparql_query/query 场景）：配置了工作流 → 确定性路由。
     * 注意：rd 场景的 rd_config_chat/RD_CONFIG_CHAT 已命中手册 chat-configure（手册优先），
     * 且 rd 场景工作流配置已删除——见 intentCoveredByPlaybookFallsThroughToOrchestration。
     */
    private QueryPlan unbookedPlan() {
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of("sparql_query"), Map.of("monthly_fee", 59), "查一下数据");
        plan.setUserQuestion("查一下数据");
        return plan;
    }

    @Test
    void routesRdPlanToConfiguredWorkflow() {
        when(flowEngineService.startExecution(eq("query_reuse_v2"), isNull(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed", "execution_id", "EX-1")));

        var reply = router.tryRoute(unbookedPlan(), rdSession(), "u1");

        assertTrue(reply.isPresent(), "query 已配置 → 应命中工作流");
        assertEquals("FLOW_EXEC", reply.get().get("intent"));
        assertEquals("query_reuse_v2",
                ((Map<?, ?>) reply.get().get("flow_matched")).get("workflow_code"));
        verify(flowEngineService).startExecution(eq("query_reuse_v2"), isNull(), any(), any());
    }

    @Test
    void sameInputMapsToSameWorkflowDeterministically() {
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed")));

        QueryPlan plan = unbookedPlan();
        for (int i = 0; i < 3; i++) {
            var reply = router.tryRoute(plan, rdSession(), "u1");
            assertTrue(reply.isPresent());
            assertEquals("query_reuse_v2",
                    ((Map<?, ?>) reply.get().get("flow_matched")).get("workflow_code"),
                    "同输入=同路径（确定性，无 LLM 参与）");
        }
    }

    // ── 短路条件（未命中回落动态编排） ──

    @Test
    void unconfiguredSceneFallsThrough() {
        SessionContext market = new SessionContext("s2");
        market.setScene("market_insight");
        var reply = router.tryRoute(execPlan("sparql_query", "SPARQL_QUERY"), market, "u1");

        assertTrue(reply.isEmpty(), "未配置工作流的场景 → 走动态编排");
    }

    @Test
    void opsIntentCoveredByPlaybookFallsThroughToOrchestration() {
        // ops 场景工作流配置已删除（去旧留新：运营问诊收拢到四本入口手册）：
        // ops 下主意图命中手册 → 走动态编排（LLM 照手册执行），固化工作流不再路由
        SessionContext ops = new SessionContext("s-ops");
        ops.setScene("ops");
        for (QueryPlan plan : List.of(
                execPlan("swrl_root_cause", "PRODUCT_OPS_REASON"),
                execPlan("swrl_risk_audit", "PRODUCT_OPS_POLICY"),
                execPlan("sparql_query", "PRODUCT_OPS_QUERY"))) {
            var reply = router.tryRoute(plan, ops, "u1");
            assertTrue(reply.isEmpty(), () -> plan.getIntent() + " 命中 ops 入口手册 → 不进固化工作流");
        }
        verify(flowEngineService, never()).startExecution(any(), any(), any(), any());
    }

    @Test
    void clarifyConfirmAndReuseIntentsNeverRoute() {
        for (String intent : List.of(QueryPlan.INTENT_CLARIFY, QueryPlan.INTENT_CONFIRM,
                QueryPlan.INTENT_REUSE_EVIDENCE)) {
            var reply = router.tryRoute(execPlan("sparql_query", intent), rdSession(), "u1");
            assertTrue(reply.isEmpty(), () -> intent + " 是对话协议轮，不应进工作流");
        }
        verify(flowEngineService, never()).startExecution(any(), any(), any(), any());
    }

    @Test
    void emptyToolsPlanNeverRoutes() {
        QueryPlan chat = new QueryPlan("CHAT", List.of(), Map.of(), "闲聊");
        var reply = router.tryRoute(chat, rdSession(), "u1");

        assertTrue(reply.isEmpty(), "无工具计划（纯对话）→ 走动态编排");
    }

    @Test
    void emptySceneWorkflowValueFallsThrough() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "");

        var reply = router.tryRoute(unbookedPlan(), rdSession(), "u1");

        assertTrue(reply.isEmpty(), "空串配置 = 该场景暂不启用");
    }

    @Test
    void unconfiguredRdSceneFallsThroughEvenWithPlaybookUnmatchedIntent() {
        // rd 场景工作流配置已删除（去旧留新：手册全覆盖架空固化工作流）：
        // rd 下未命中手册的意图 → 直接走动态编排（而非回落某个固化工作流）
        var reply = router.tryRoute(execPlan("rd_compliance", "RD_COMPLIANCE"), plainRdSession(), "u1");

        assertTrue(reply.isEmpty(), "rd 场景未配置工作流 → 走动态编排");
        verify(flowEngineService, never()).startExecution(any(), any(), any(), any());
    }

    @Test
    void intentCoveredByPlaybookFallsThroughToOrchestration() {
        // 手册声明 applies_to.intents（classpath 装载：doc-batch-import/chat-configure/discover-history）：
        // 意图命中手册 → 即使场景配置了工作流也回落动态编排（LLM 照手册执行）
        for (QueryPlan plan : List.of(
                execPlan("rd_file_parse", "RD_FILE_PARSE"),
                execPlan("rd_config_chat", "RD_CONFIG_CHAT"),
                execPlan("rd_config_discover", "RD_CONFIG_DISCOVER"))) {
            var reply = router.tryRoute(plan, plainRdSession(), "u1");
            assertTrue(reply.isEmpty(), () -> plan.getIntent() + " 命中手册 → 不进固化工作流");
        }
        verify(flowEngineService, never()).startExecution(any(), any(), any(), any());
    }

    // ── 入参透传与回复组装 ──

    @Test
    void inputDataCarriesParamsAndQuestion() {
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed")));

        router.tryRoute(unbookedPlan(), rdSession(), "u1");

        org.mockito.ArgumentCaptor<Map<String, Object>> inputCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(flowEngineService).startExecution(eq("query_reuse_v2"), isNull(), inputCaptor.capture(), any());
        assertEquals(59, inputCaptor.getValue().get("monthly_fee"), "QueryPlan 参数应透传为流程入参");
        assertEquals("查一下数据", inputCaptor.getValue().get("question"), "用户原话应透传");
    }

    @Test
    void waitingHumanReplyNotifiesInChatResume() {
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "waiting_human", "execution_id", "EX-9")));

        var reply = router.tryRoute(unbookedPlan(), rdSession(), "u1");

        assertTrue(reply.isPresent());
        String report = String.valueOf(reply.get().get("report"));
        assertTrue(report.contains("对话中回复确认"), () -> "挂起话术应引导对话内恢复: " + report);
        assertEquals("EX-9", reply.get().get("session_id"));
    }

    @Test
    void engineFailureProducesFailedFlowExecution() {
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.fail("工作流未发布"));

        var reply = router.tryRoute(unbookedPlan(), rdSession(), "u1");

        assertTrue(reply.isPresent());
        assertEquals("failed", ((Map<?, ?>) reply.get().get("flow_execution")).get("status"));
        assertTrue(String.valueOf(reply.get().get("report")).contains("工作流未发布"));
    }

    @Test
    void completedReplyExtractsFlowOutputConclusion() {
        Map<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("flow", Map.of("output", "草稿已落库并开单"));
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed", "output_data", outputData)));

        var reply = router.tryRoute(unbookedPlan(), rdSession(), "u1");

        assertTrue(reply.isPresent());
        assertEquals("草稿已落库并开单", reply.get().get("conclusion"), "结论应取 end 节点透传的 flow.output");
    }
}
