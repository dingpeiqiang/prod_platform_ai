package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.service.flow.FlowEngineService;
import com.sitech.prodai.service.flow.event.FlowNodeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatFlowProgressBridge 单元测试（智聊重设计 W4，方案 §5.1 验收）：
 * 引擎事件 → flow_progress 载荷翻译、executionId 会话隔离、注册/注销生命周期、
 * 转发异常隔离（引擎可靠性 &gt; 通知可靠性）。
 */
@ExtendWith(MockitoExtension.class)
class ChatFlowProgressBridgeTest {

    @Mock
    private FlowEngineService engine;

    private ChatFlowProgressBridge bridge;

    /** 事件录制器：按序记录 (event, payload)。 */
    private static final class RecordingSink
            implements java.util.function.BiConsumer<String, Map<String, Object>> {
        final List<String> names = new ArrayList<>();
        final List<Map<String, Object>> payloads = new ArrayList<>();

        @Override
        public void accept(String event, Map<String, Object> payload) {
            names.add(event);
            payloads.add(payload);
        }
    }

    @BeforeEach
    void setUp() {
        bridge = new ChatFlowProgressBridge();
    }

    private FlowNodeEvent event(String type, String executionId) {
        return FlowNodeEvent.of(type, executionId, "chat_configure_v2")
                .nodeId("compliance")
                .nodeType("rd_compliance")
                .nodeName("合规自检")
                .status("running")
                .build();
    }

    @Test
    void registeredSinkReceivesTranslatedFlowProgress() {
        RecordingSink sink = new RecordingSink();
        bridge.register("EX-1", sink, null);

        bridge.onEvent(event(FlowNodeEvent.NODE_STARTED, "EX-1"));

        assertEquals(List.of("flow_progress"), sink.names);
        Map<String, Object> payload = sink.payloads.get(0);
        assertEquals("EX-1", payload.get("execution_id"));
        assertEquals("chat_configure_v2", payload.get("workflow_code"));
        assertEquals("compliance", payload.get("node_id"));
        assertEquals("合规自检", payload.get("node_name"));
        assertEquals("running", payload.get("status"), "node_started 应翻译为 running");
        assertEquals(1, payload.get("attempt"));
        assertTrue(payload.containsKey("occurred_at"));
    }

    @Test
    void unregisteredExecutionIsFilteredOut() {
        RecordingSink sink = new RecordingSink();
        bridge.register("EX-1", sink, null);

        bridge.onEvent(event(FlowNodeEvent.NODE_COMPLETED, "EX-OTHER"));

        assertTrue(sink.names.isEmpty(), "非本会话 executionId 的事件不应转发（会话隔离）");
    }

    @Test
    void eventTypesMapToSseStatusContract() {
        RecordingSink sink = new RecordingSink();
        bridge.register("EX-1", sink, null);

        bridge.onEvent(event(FlowNodeEvent.NODE_STARTED, "EX-1"));
        bridge.onEvent(event(FlowNodeEvent.NODE_COMPLETED, "EX-1"));
        bridge.onEvent(event(FlowNodeEvent.NODE_FAILED, "EX-1"));
        bridge.onEvent(event(FlowNodeEvent.SUSPENDED, "EX-1"));
        bridge.onEvent(event(FlowNodeEvent.EXECUTION_COMPLETED, "EX-1"));

        assertEquals("running", sink.payloads.get(0).get("status"));
        assertEquals("done", sink.payloads.get(1).get("status"));
        assertEquals("error", sink.payloads.get(2).get("status"));
        assertEquals("suspended", sink.payloads.get(3).get("status"), "挂起事件保留 suspended 原文（前端渲染等待确认）");
        assertEquals("done", sink.payloads.get(4).get("status"));
    }

    @Test
    void branchTakenCarriesBranchIdInPayload() {
        RecordingSink sink = new RecordingSink();
        bridge.register("EX-1", sink, null);

        bridge.onEvent(FlowNodeEvent.of(FlowNodeEvent.BRANCH_TAKEN, "EX-1", "chat_configure_v2")
                .nodeId("pass-check").nodeType("flow.condition").nodeName("通过判定")
                .branchTaken("fix").build());

        @SuppressWarnings("unchecked")
        Map<String, Object> branch = (Map<String, Object>) sink.payloads.get(0).get("branch");
        assertEquals("fix", branch.get("id"), "分支命中应携带 branch.id（前端展示走了哪条边）");
    }

    @Test
    void suspendEventCarriesFormSpec() {
        RecordingSink sink = new RecordingSink();
        bridge.register("EX-1", sink, null);
        Map<String, Object> formSpec = new LinkedHashMap<>();
        formSpec.put("form_code", "offering_config");

        bridge.onEvent(FlowNodeEvent.of(FlowNodeEvent.SUSPENDED, "EX-1", "chat_configure_v2")
                .nodeId("confirm-gate").nodeType("flow.human").nodeName("确认落库")
                .status("suspended").formSpec(formSpec).build());

        Map<String, Object> payload = sink.payloads.get(0);
        assertEquals(formSpec, payload.get("form_spec"), "挂起事件应透传 form_spec");
    }

    @Test
    void unregisterStopsForwardingAndResetsEnginePublisher() {
        RecordingSink sink = new RecordingSink();
        bridge.register("EX-1", sink, engine);
        assertEquals(1, bridge.registeredCount());

        bridge.unregister("EX-1", engine);

        assertEquals(0, bridge.registeredCount());
        bridge.onEvent(event(FlowNodeEvent.NODE_STARTED, "EX-1"));
        assertTrue(sink.names.isEmpty(), "注销后不再转发");
    }

    @Test
    void sinkExceptionDoesNotBreakBridge() {
        RecordingSink sink = new RecordingSink();
        bridge.register("EX-1", (event, payload) -> {
            throw new RuntimeException("emitter 已断开");
        }, null);
        RecordingSink healthy = new RecordingSink();
        bridge.register("EX-2", healthy, null);

        bridge.onEvent(event(FlowNodeEvent.NODE_STARTED, "EX-1"));
        bridge.onEvent(event(FlowNodeEvent.NODE_STARTED, "EX-2"));

        assertEquals(List.of("flow_progress"), healthy.names,
                "单注册者异常不应影响其他注册者（引擎隔离约定）");
    }

    @Test
    void registerWithBlankExecutionIdOrSinkIsNoop() {
        bridge.register("", new RecordingSink(), null);
        bridge.register(null, new RecordingSink(), null);
        bridge.register("EX-3", null, null);

        assertEquals(0, bridge.registeredCount(), "非法注册参数应为空操作");
    }

    @Test
    void errorMessageIsPassedThroughWhenPresent() {
        RecordingSink sink = new RecordingSink();
        bridge.register("EX-1", sink, null);

        bridge.onEvent(FlowNodeEvent.of(FlowNodeEvent.NODE_FAILED, "EX-1", "chat_configure_v2")
                .nodeId("draft-llm").nodeType("flow.llm").nodeName("起草")
                .status("error").errorMessage("LLM 网关超时").build());

        assertEquals("LLM 网关超时", sink.payloads.get(0).get("error_message"));
    }

    @Test
    void noErrorMessageKeyWhenBlank() {
        RecordingSink sink = new RecordingSink();
        bridge.register("EX-1", sink, null);

        bridge.onEvent(event(FlowNodeEvent.NODE_COMPLETED, "EX-1"));

        assertNull(sink.payloads.get(0).get("error_message"), "无错误时不应出现 error_message 键");
    }
}
