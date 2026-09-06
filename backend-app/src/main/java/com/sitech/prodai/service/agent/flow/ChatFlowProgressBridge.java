package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.service.flow.event.FlowEventListener;
import com.sitech.prodai.service.flow.event.FlowEventPublisher;
import com.sitech.prodai.service.flow.event.FlowNodeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 智聊流程进度桥接器（智聊重设计 W4，方案 §5.1）—— 引擎事件 → SSE {@code flow_progress}。
 * <p>
 * 职责链：引擎节点事件（异步投递）→ 本类翻译为 SSE 载荷 → 转发给「当前流式请求」
 * 注册的 emitter 回调。既有事件契约零改动（thinking/tool/text/done 原样保留）。
 * <p>
 * 会话隔离：每个流式请求注册 (executionId → 回调)，仅转发与注册 executionId 匹配的
 * 事件——引擎为单例 + 异步投递，未隔离时跨请求事件会串流。请求结束注销。
 * <p>
 * 可靠性约定：翻译/转发任何异常仅 log.warn（与引擎"通知可靠性 &lt; 执行可靠性"一致），
 * 绝不影响引擎状态机推进。
 */
@Component
public class ChatFlowProgressBridge implements FlowEventListener {

    private static final Logger log = LoggerFactory.getLogger(ChatFlowProgressBridge.class);

    /** 引擎事件类型 → SSE status 映射（设计文档 §5.2）。 */
    private static final Map<String, String> STATUS_BY_TYPE = Map.of(
            FlowNodeEvent.NODE_STARTED, "running",
            FlowNodeEvent.NODE_COMPLETED, "done",
            FlowNodeEvent.NODE_FAILED, "error",
            FlowNodeEvent.BRANCH_TAKEN, "done",
            FlowNodeEvent.SUSPENDED, "suspended",
            FlowNodeEvent.EXECUTION_COMPLETED, "done",
            FlowNodeEvent.EXECUTION_FAILED, "error");

    /** 当前注册的转发目标：executionId → emitter 回调（event, payload）。 */
    private final ConcurrentHashMap<String, BiConsumer<String, Map<String, Object>>> sinks =
            new ConcurrentHashMap<>();

    /** 引擎事件发布器：持有本监听器，随注册/注销换装进引擎（挂接/摘除一体）。 */
    private volatile FlowEventPublisher publisher = new FlowEventPublisher(List.of(this));

    /**
     * 注册流式请求的转发目标（在调用 startExecution 之前）。
     * <p>
     * 幂等换装：仅当引擎当前发布器不是本桥的发布器时装入，避免重复包装。
     *
     * @param executionId 引擎执行实例 ID（挂起场景在 startExecution 返回前未知时可先注册，
     *                    回来后以真实 executionId 再注册一次）
     * @param sink        emitter 回调：(事件名, 载荷)
     */
    public void register(String executionId, BiConsumer<String, Map<String, Object>> sink,
                         com.sitech.prodai.service.flow.FlowEngineService engine) {
        if (executionId == null || executionId.isBlank() || sink == null) {
            return;
        }
        sinks.put(executionId, sink);
        if (engine != null) {
            engine.setEventPublisher(publisher);
        }
    }

    /** 请求结束注销（引擎发布器还原为空发布，零开销）。 */
    public void unregister(String executionId, com.sitech.prodai.service.flow.FlowEngineService engine) {
        if (executionId != null) {
            sinks.remove(executionId);
        }
        if (engine != null && sinks.isEmpty()) {
            engine.setEventPublisher(null);
        }
    }

    /** 当前注册的转发目标数（观测/测试用）。 */
    public int registeredCount() {
        return sinks.size();
    }

    /** 引擎事件入口：翻译为 flow_progress 载荷并转发给匹配的注册者。 */
    @Override
    public void onEvent(FlowNodeEvent event) {
        try {
            BiConsumer<String, Map<String, Object>> sink = sinks.get(event.getExecutionId());
            if (sink == null) {
                return;
            }
            sink.accept("flow_progress", toPayload(event));
        } catch (Exception e) {
            log.warn("[ChatFlowProgressBridge] flow_progress 转发失败（不影响执行）: nodeId={} error={}",
                    event.getNodeId(), e.getMessage());
        }
    }

    /** 引擎事件 → SSE flow_progress 载荷（设计文档 §5.1 契约，snake_case）。 */
    private Map<String, Object> toPayload(FlowNodeEvent event) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("execution_id", event.getExecutionId());
        payload.put("workflow_code", event.getWorkflowCode());
        payload.put("node_id", event.getNodeId());
        payload.put("node_name", event.getNodeName());
        payload.put("node_type", event.getNodeType());
        payload.put("status", STATUS_BY_TYPE.getOrDefault(event.getType(), event.getStatus()));
        payload.put("attempt", event.getAttempt());
        if (event.getBranchTaken() != null) {
            payload.put("branch", Map.of("id", event.getBranchTaken()));
        }
        if (event.getErrorMessage() != null && !event.getErrorMessage().isBlank()) {
            payload.put("error_message", event.getErrorMessage());
        }
        if (event.getFormSpec() != null) {
            payload.put("form_spec", event.getFormSpec());
        }
        payload.put("occurred_at", String.valueOf(event.getOccurredAt()));
        return payload;
    }
}
