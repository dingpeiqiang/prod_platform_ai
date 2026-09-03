package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程意图路由器测试（S1 对话即编排）：
 * 关键词命中路由引擎、未命中放行原链路、引擎失败降级文案、注册表管理。
 */
@ExtendWith(MockitoExtension.class)
class FlowIntentRouterTest {

    @Mock
    private FlowEngineService flowEngineService;

    private FlowIntentRouter newRouter() {
        FlowIntentRouter router = new FlowIntentRouter(flowEngineService);
        router.register("demo_linear_flow", "演示线性流程", List.of("演示流程", "跑一下"));
        return router;
    }

    @Test
    void keywordHitRoutesToEngineAndBuildsReply() {
        Map<String, Object> engineData = Map.of(
                "execution_id", "exec-001",
                "status", "completed",
                "output_data", Map.of("flow", Map.of("output", "共 5 条记录")));
        when(flowEngineService.startExecution(eq("demo_linear_flow"), isNull(),
                anyMap(), isNull())).thenReturn(ApiResponse.ok(engineData));
        FlowIntentRouter router = newRouter();

        var reply = router.tryRoute("帮我跑一下演示流程", null, null);

        assertTrue(reply.isPresent(), "关键词命中应路由引擎");
        Map<String, Object> body = reply.get();
        assertEquals("FLOW_EXEC", body.get("intent"));
        assertEquals("exec-001", body.get("session_id"), "execution_id 应回填 session_id");
        assertEquals("共 5 条记录", body.get("conclusion"), "结论应从 flow.output 提取");
        @SuppressWarnings("unchecked")
        Map<String, Object> matched = (Map<String, Object>) body.get("flow_matched");
        assertEquals("demo_linear_flow", matched.get("workflow_code"));
        assertEquals("演示流程", matched.get("hit_keyword"));
        verify(flowEngineService).startExecution(eq("demo_linear_flow"), isNull(), anyMap(), isNull());
    }

    @Test
    void keywordMissFallsThroughToLlmPipeline() {
        FlowIntentRouter router = newRouter();

        var reply = router.tryRoute("查一下畅越冰激凌月套餐的订购量", null, null);

        assertTrue(reply.isEmpty(), "未命中应放行原 LLM 链路");
        verify(flowEngineService, never()).startExecution(anyString(), anyInt(), anyMap(), anyString());
    }

    @Test
    void engineFailureDegradesToReportNotThrow() {
        when(flowEngineService.startExecution(eq("demo_linear_flow"), isNull(),
                anyMap(), isNull())).thenReturn(ApiResponse.fail("流程未发布或不可执行: demo_linear_flow"));
        FlowIntentRouter router = newRouter();

        var reply = router.tryRoute("跑一下演示流程", Map.of("city", "北京"), null);

        assertTrue(reply.isPresent(), "引擎失败也应返回降级响应而非抛异常");
        Map<String, Object> body = reply.get();
        String report = String.valueOf(body.get("report"));
        assertTrue(report.contains("执行失败"), () -> "降级文案: " + report);
        assertTrue(report.contains("流程未发布或不可执行"), "应透出引擎失败原因");
        @SuppressWarnings("unchecked")
        Map<String, Object> exec = (Map<String, Object>) body.get("flow_execution");
        assertEquals("failed", exec.get("status"));
    }

    @Test
    void waitingHumanStatusPromptsEditorContinuation() {
        Map<String, Object> engineData = Map.of(
                "execution_id", "exec-002",
                "status", "waiting_human");
        when(flowEngineService.startExecution(eq("demo_linear_flow"), isNull(),
                anyMap(), isNull())).thenReturn(ApiResponse.ok(engineData));
        FlowIntentRouter router = newRouter();

        var reply = router.tryRoute("演示流程", null, null);

        assertTrue(reply.isPresent());
        String report = String.valueOf(reply.get().get("report"));
        assertTrue(report.contains("人工节点"), () -> "waiting_human 文案: " + report);
        assertTrue(report.contains("exec-002"), "应提示执行 ID");
    }

    @Test
    void registerManagesRoutes() {
        FlowIntentRouter router = new FlowIntentRouter(flowEngineService);
        assertTrue(router.tryRoute("演示流程", null, null).isEmpty(), "空注册表不路由");

        router.register("", "x", List.of("k"));
        router.register("code_a", "A", List.of());
        router.register("code_b", "B", List.of("关键词B"));
        assertEquals(1, router.listRoutes().size(), "无效注册应被忽略");

        router.unregister("code_b");
        assertEquals(0, router.listRoutes().size());
    }
}
