package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史回放重建器（W6-2，设计文档 §5.2 观测闭环）。
 * <p>
 * 职责：从引擎节点执行记录（node_logs）重建与实时 SSE {@code flow_progress}
 * 事件同构的时间线，随助手消息 metadata 落库（键 {@code flow_progress_timeline}），
 * 使历史回放时前端可等量还原工作流执行过程卡片。
 * <p>
 * 同构约定（对齐 {@link ChatFlowProgressBridge#toPayload} 的 snake_case 契约）：
 * <ul>
 *   <li>status 映射：node_log running → running / completed → done /
 *       failed → error / skipped → skipped / cancelled → error</li>
 *   <li>分支判定：node_log.branch_taken 非空时补 branch.id（对应 BRANCH_TAKEN 事件）</li>
 *   <li>挂起：node_log.status=running 且节点类型为 human 时标记 flow_suspended=true
 *       （对应 SUSPENDED 事件语义：human 节点运行中即会话挂起点）</li>
 * </ul>
 * 可靠性约定：重建/查询任何异常均降级为空时间线并 log.warn，
 * 不影响对话主链路与持久化（观测可靠性 &lt; 执行可靠性）。
 */
@Component
public class FlowProgressReplayer {

    private static final Logger log = LoggerFactory.getLogger(FlowProgressReplayer.class);

    /** metadata 落库键：flow_progress 同构时间线（JSON 数组） */
    public static final String TIMELINE_KEY = "flow_progress_timeline";

    /** human 节点类型：running 状态即会话挂起点（对应实时 SUSPENDED 事件语义） */
    private static final String NODE_TYPE_HUMAN = "human";

    private final FlowEngineService flowEngineService;

    public FlowProgressReplayer(FlowEngineService flowEngineService) {
        this.flowEngineService = flowEngineService;
    }

    /**
     * 从 node_logs 重建 flow_progress 同构时间线。
     *
     * @param executionId 流程执行实例 ID（未知或查询失败时返回空列表）
     * @return 时间线条目列表（每条与实时 flow_progress 载荷同构）；异常降级为空列表
     */
    public List<Map<String, Object>> rebuild(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return List.of();
        }
        try {
            ApiResponse<Map<String, Object>> resp = flowEngineService.getNodeLogs(executionId);
            if (resp == null || resp.getData() == null
                    || !(resp.getData().get("node_logs") instanceof List<?> logs)) {
                return List.of();
            }
            List<Map<String, Object>> timeline = new ArrayList<>();
            for (Object item : logs) {
                if (item instanceof Map<?, ?> nodeLog) {
                    timeline.add(toTimelineEntry(nodeLog));
                }
            }
            return timeline;
        } catch (Exception e) {
            log.warn("[FlowProgressReplayer] 时间线重建失败，降级为空: executionId={} error={}",
                    executionId, e.getMessage());
            return List.of();
        }
    }

    /** node_log（snake_case Map）→ flow_progress 同构时间线条目 */
    private Map<String, Object> toTimelineEntry(Map<?, ?> nodeLog) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("execution_id", nodeLog.get("execution_id"));
        entry.put("node_id", nodeLog.get("node_id"));
        entry.put("node_type", nodeLog.get("node_type"));
        entry.put("status", mapStatus(str(nodeLog.get("status"))));
        Object attempt = nodeLog.get("attempt");
        entry.put("attempt", attempt != null ? attempt : 1);
        String branchTaken = str(nodeLog.get("branch_taken"));
        if (!branchTaken.isBlank()) {
            entry.put("branch", Map.of("id", branchTaken));
        }
        String errorMessage = str(nodeLog.get("error_message"));
        if (!errorMessage.isBlank()) {
            entry.put("error_message", errorMessage);
        }
        if (isSuspensionPoint(nodeLog)) {
            entry.put("flow_suspended", true);
        }
        Object startedAt = nodeLog.get("started_at");
        if (startedAt != null) {
            entry.put("occurred_at", String.valueOf(startedAt));
        }
        return entry;
    }

    /** 挂起点判定：human 节点处于 running 状态（流程在此等待人工回复） */
    private boolean isSuspensionPoint(Map<?, ?> nodeLog) {
        return NODE_TYPE_HUMAN.equals(nodeLog.get("node_type"))
                && "running".equals(nodeLog.get("status"));
    }

    /** node_log 状态 → SSE flow_progress status（与 ChatFlowProgressBridge 语义一致） */
    private String mapStatus(String nodeLogStatus) {
        return switch (nodeLogStatus) {
            case "completed" -> "done";
            case "failed", "cancelled" -> "error";
            case "skipped" -> "skipped";
            default -> "running";
        };
    }

    private static String str(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
