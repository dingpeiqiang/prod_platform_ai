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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SceneFlowRouter 单元测试（智聊重设计 W3-1，方案 §7.2 验收）：
 * 协议轮短路、手册优先分流、场景未配置回落、引擎结果组装同构。
 * <p>
 * 去旧留新（手册层全面取代）：rd/ops/query 三场景工作流配置均已移除
 * （chat-configure/doc-batch-import/discover-history/ops 四本/query-ask 手册接管主链路），
 * 固化路由仅剩「用户经配置显式启用某场景」的通用机制——用注册内存映射验证之。
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
    }

    private QueryPlan execPlan(String tool, String intent) {
        QueryPlan plan = new QueryPlan(intent, List.of(tool), Map.of("monthly_fee", 59), "配一个套餐");
        plan.setUserQuestion("配一个套餐");
        return plan;
    }

    private SessionContext session(String scene) {
        SessionContext ctx = new SessionContext("s1");
        ctx.setScene(scene);
        return ctx;
    }

    // ── 手册优先分流（场景工作流配置已全部移除，手册是唯一业务路由） ──

    @Test
    void queryAskIntentCoveredByPlaybookFallsThroughToOrchestration() {
        // query 场景主意图命中手册 query-ask（scene=query, intents=[SPARQL_QUERY, PRODUCT_OPS_QUERY]）：
        // 意图命中手册 → 回落动态编排（LLM 照手册执行），固化工作流已退役
        SessionContext query = session("query");
        for (QueryPlan plan : List.of(
                execPlan("sparql_query", "SPARQL_QUERY"),
                execPlan("sparql_query", "PRODUCT_OPS_QUERY"))) {
            var reply = router.tryRoute(plan, query, "u1");
            assertTrue(reply.isEmpty(), () -> plan.getIntent() + " 命中 query-ask 手册 → 不进固化工作流");
        }
        verify(flowEngineService, never()).startExecution(any(), any(), any(), any());
    }

    @Test
    void opsIntentCoveredByPlaybookFallsThroughToOrchestration() {
        // ops 场景工作流配置已删除（去旧留新：运营问诊收拢到四本入口手册）：
        // ops 下主意图命中手册 → 走动态编排（LLM 照手册执行），固化工作流不再路由
        SessionContext ops = session("ops");
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
    void unconfiguredScenesFallThrough() {
        // 三场景工作流配置均已移除（手册层全面取代）：任何意图都不再进固化工作流
        for (String scene : List.of("query", "rd", "market_insight")) {
            var reply = router.tryRoute(execPlan("rd_config_search", "QUERY_ARCHIVE"), session(scene), "u1");
            assertTrue(reply.isEmpty(), () -> scene + " 未配置工作流 → 走动态编排");
        }
        verify(flowEngineService, never()).startExecution(any(), any(), any(), any());
    }

    // ── 通用机制：用户经配置显式启用某场景工作流（自建流程保留出口） ──

    @Test
    void explicitlyConfiguredSceneRoutesDeterministically() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        when(flowEngineService.startExecution(eq("my_custom_flow"), isNull(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed", "execution_id", "EX-1")));

        var reply = router.tryRoute(execPlan("rd_config_search", "QUERY_ARCHIVE"), session("query"), "u1");

        assertTrue(reply.isPresent(), "显式配置的场景工作流 → 确定性路由");
        assertEquals("FLOW_EXEC", reply.get().get("intent"));
        assertEquals("my_custom_flow",
                ((Map<?, ?>) reply.get().get("flow_matched")).get("workflow_code"));
    }

    @Test
    void sameInputMapsToSameWorkflowDeterministically() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed")));

        QueryPlan plan = execPlan("rd_config_search", "QUERY_ARCHIVE");
        for (int i = 0; i < 3; i++) {
            var reply = router.tryRoute(plan, session("query"), "u1");
            assertTrue(reply.isPresent());
            assertEquals("my_custom_flow",
                    ((Map<?, ?>) reply.get().get("flow_matched")).get("workflow_code"),
                    "同输入=同路径（确定性，无 LLM 参与）");
        }
    }

    @Test
    void emptySceneWorkflowValueFallsThrough() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "");

        var reply = router.tryRoute(execPlan("rd_config_search", "QUERY_ARCHIVE"), session("query"), "u1");

        assertTrue(reply.isEmpty(), "空串配置 = 该场景暂不启用");
    }

    // ── 协议轮短路 ──

    @Test
    void clarifyConfirmAndReuseIntentsNeverRoute() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        for (String intent : List.of(QueryPlan.INTENT_CLARIFY, QueryPlan.INTENT_CONFIRM,
                QueryPlan.INTENT_REUSE_EVIDENCE)) {
            var reply = router.tryRoute(execPlan("sparql_query", intent), session("query"), "u1");
            assertTrue(reply.isEmpty(), () -> intent + " 是对话协议轮，不应进工作流");
        }
        verify(flowEngineService, never()).startExecution(any(), any(), any(), any());
    }

    @Test
    void emptyToolsPlanNeverRoutes() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        QueryPlan chat = new QueryPlan("CHAT", List.of(), Map.of(), "闲聊");
        var reply = router.tryRoute(chat, session("query"), "u1");

        assertTrue(reply.isEmpty(), "无工具计划（纯对话）→ 走动态编排");
    }

    // ── 入参透传与回复组装（FlowReplyBuilder 统一组装） ──

    @Test
    void inputDataCarriesParamsAndQuestion() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed")));

        router.tryRoute(execPlan("rd_config_search", "QUERY_ARCHIVE"), session("query"), "u1");

        org.mockito.ArgumentCaptor<Map<String, Object>> inputCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(flowEngineService).startExecution(eq("my_custom_flow"), isNull(), inputCaptor.capture(), any());
        assertEquals(59, inputCaptor.getValue().get("monthly_fee"), "QueryPlan 参数应透传为流程入参");
        assertEquals("配一个套餐", inputCaptor.getValue().get("question"), "用户原话应透传");
    }

    @Test
    void waitingHumanReplyNotifiesInChatResume() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "waiting_human", "execution_id", "EX-9")));

        var reply = router.tryRoute(execPlan("rd_config_search", "QUERY_ARCHIVE"), session("query"), "u1");

        assertTrue(reply.isPresent());
        String report = String.valueOf(reply.get().get("report"));
        assertTrue(report.contains("对话中回复确认"), () -> "挂起话术应引导对话内恢复: " + report);
        assertEquals("EX-9", reply.get().get("session_id"));
    }

    @Test
    void engineFailureProducesFailedFlowExecution() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.fail("工作流未发布"));

        var reply = router.tryRoute(execPlan("rd_config_search", "QUERY_ARCHIVE"), session("query"), "u1");

        assertTrue(reply.isPresent());
        assertEquals("failed", ((Map<?, ?>) reply.get().get("flow_execution")).get("status"));
        assertTrue(String.valueOf(reply.get().get("report")).contains("工作流未发布"));
    }

    @Test
    void completedReplyExtractsFlowOutputConclusion() {
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        Map<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("flow", Map.of("output", "草稿已落库并开单"));
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed", "output_data", outputData)));

        var reply = router.tryRoute(execPlan("rd_config_search", "QUERY_ARCHIVE"), session("query"), "u1");

        assertTrue(reply.isPresent());
        assertEquals("草稿已落库并开单", reply.get().get("conclusion"), "结论应取 end 节点透传的 flow.output");
    }

    @Test
    void completedReplyFallsBackToNodeNaturalLanguageAnswers() {
        // 复现线上问题形态：end 节点未透传 flow.output，output_data 只有各节点输出命名空间——
        // 结论应取节点自然语言产出（nl_answer/*_answer 族），而非整 Map toString 噪声
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        Map<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("discover", Map.of("output", Map.of("discover_answer", "命中2条历史配置")));
        outputData.put("sparql", Map.of("output", Map.of("sparql_answer", "智享99元5G套餐包含 60GB 流量、1000分钟通话")));
        outputData.put("compare", Map.of("output", Map.of("compare_answer", "推荐方案A")));
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed", "output_data", outputData)));

        var reply = router.tryRoute(execPlan("rd_config_search", "QUERY_ARCHIVE"), session("query"), "u1");

        assertTrue(reply.isPresent());
        String conclusion = String.valueOf(reply.get().get("conclusion"));
        assertTrue(conclusion.contains("60GB 流量"), () -> "结论应含节点自然语言答案: " + conclusion);
        assertFalse(conclusion.contains("{"), () -> "结论不应是 Map toString 串: " + conclusion);
        String report = String.valueOf(reply.get().get("report"));
        assertTrue(report.contains("已执行完成"), () -> "完成话术保留: " + report);
        assertTrue(report.contains("推荐方案A"), () -> "报告应附节点产出概要: " + report);
        assertFalse(report.contains("耗时详情"), () -> "不应再只报耗时: " + report);
    }

    @Test
    void completedReplyWithoutReadableOutputLeavesConclusionEmpty() {
        // 无任何可读产出（全噪声键）→ 结论留空而非 Map toString
        properties.getChatWorkflow().getSceneWorkflows().put("query", "my_custom_flow");
        Map<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("flow", Map.of());
        outputData.put("n1", Map.of("output", Map.of("some_binary", List.of(1, 2))));
        when(flowEngineService.startExecution(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed", "output_data", outputData)));

        var reply = router.tryRoute(execPlan("rd_config_search", "QUERY_ARCHIVE"), session("query"), "u1");

        assertTrue(reply.isPresent());
        assertEquals("", reply.get().get("conclusion"), "无可读产出 → 结论留空");
    }
}
