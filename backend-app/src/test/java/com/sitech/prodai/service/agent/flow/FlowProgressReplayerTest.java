package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 历史回放重建器单元测试（W6-2，设计文档 §5.2 观测闭环）。
 * <p>
 * 覆盖：node_logs → flow_progress 同构时间线重建、状态映射、
 * 分支/挂起标记、空输入与查询失败的降级行为。
 */
class FlowProgressReplayerTest {

    private FlowEngineService engine;
    private FlowProgressReplayer replayer;

    @BeforeEach
    void setUp() {
        engine = mock(FlowEngineService.class);
        replayer = new FlowProgressReplayer(engine);
    }

    @Test
    @DisplayName("executionId 为空时返回空时间线，不触发引擎查询")
    void blankExecutionIdReturnsEmptyWithoutQuery() {
        assertTrue(replayer.rebuild(null).isEmpty());
        assertTrue(replayer.rebuild("").isEmpty());
        assertTrue(replayer.rebuild("  ").isEmpty());
    }

    @Test
    @DisplayName("node_logs 完整重建：running/completed/failed 状态映射与同构载荷")
    void rebuildMapsNodeLogsToTimeline() {
        when(engine.getNodeLogs("exec-1")).thenReturn(ApiResponse.ok(Map.of(
                "node_logs", List.of(
                        nodeLog("exec-1", "start", "start", "completed", 1, null, null),
                        nodeLog("exec-1", "sparql-1", "sparql", "running", 1, null, null),
                        nodeLog("exec-1", "human-1", "human", "running", 1, null, null),
                        nodeLog("exec-1", "end", "end", "failed", 2, "上游数据缺失", null)))));

        List<Map<String, Object>> timeline = replayer.rebuild("exec-1");
        assertEquals(4, timeline.size());

        Map<String, Object> first = timeline.get(0);
        assertEquals("exec-1", first.get("execution_id"));
        assertEquals("start", first.get("node_id"));
        assertEquals("start", first.get("node_type"));
        assertEquals("done", first.get("status"));
        assertEquals(1, first.get("attempt"));

        assertEquals("running", timeline.get(1).get("status"));

        Map<String, Object> human = timeline.get(2);
        assertEquals("running", human.get("status"));
        assertEquals(Boolean.TRUE, human.get("flow_suspended"), "human 节点 running 即挂起点");

        Map<String, Object> failed = timeline.get(3);
        assertEquals("error", failed.get("status"));
        assertEquals(2, failed.get("attempt"));
        assertEquals("上游数据缺失", failed.get("error_message"));
    }

    @Test
    @DisplayName("branch_taken 非空时补 branch.id（对应实时 BRANCH_TAKEN 事件语义）")
    void branchTakenMappedToBranchObject() {
        when(engine.getNodeLogs("exec-2")).thenReturn(ApiResponse.ok(Map.of(
                "node_logs", List.of(nodeLog("exec-2", "cond-1", "condition", "completed", 1, null, "pass")))));

        List<Map<String, Object>> timeline = replayer.rebuild("exec-2");
        assertEquals(1, timeline.size());
        assertEquals("pass", ((Map<?, ?>) timeline.get(0).get("branch")).get("id"));
    }

    @Test
    @DisplayName("skipped/cancelled 状态映射：skipped → skipped，cancelled → error")
    void skippedAndCancelledStatusMapping() {
        when(engine.getNodeLogs("exec-3")).thenReturn(ApiResponse.ok(Map.of(
                "node_logs", List.of(
                        nodeLog("exec-3", "a", "sparql", "skipped", 1, null, null),
                        nodeLog("exec-3", "b", "sparql", "cancelled", 1, null, null)))));

        List<Map<String, Object>> timeline = replayer.rebuild("exec-3");
        assertEquals("skipped", timeline.get(0).get("status"));
        assertEquals("error", timeline.get(1).get("status"));
    }

    @Test
    @DisplayName("引擎查询失败/响应缺 node_logs 时降级为空时间线")
    void engineFailureDegradesToEmpty() {
        when(engine.getNodeLogs("exec-4")).thenReturn(ApiResponse.fail("执行实例不存在: exec-4"));
        assertTrue(replayer.rebuild("exec-4").isEmpty());

        when(engine.getNodeLogs("exec-5")).thenReturn(ApiResponse.ok(Map.of()));
        assertTrue(replayer.rebuild("exec-5").isEmpty());
    }

    @Test
    @DisplayName("引擎抛异常时降级为空时间线且不向上传播")
    void engineThrowsDegradesToEmpty() {
        when(engine.getNodeLogs(anyString())).thenThrow(new RuntimeException("DB down"));
        assertTrue(replayer.rebuild("exec-6").isEmpty());
    }

    @Test
    @DisplayName("attempt 缺失时补默认值 1；非 Map 条目跳过")
    void attemptDefaultsAndNonMapEntriesSkipped() {
        Map<String, Object> noAttempt = nodeLog("exec-7", "a", "sparql", "completed", null, null, null);
        noAttempt.remove("attempt");
        when(engine.getNodeLogs("exec-7")).thenReturn(ApiResponse.ok(Map.of(
                "node_logs", List.of(noAttempt, "not-a-map"))));

        List<Map<String, Object>> timeline = replayer.rebuild("exec-7");
        assertEquals(1, timeline.size());
        assertEquals(1, timeline.get(0).get("attempt"));
    }

    /** 构造 node_log（snake_case Map，与 FlowEngineService.nodeLogToMap 契约一致） */
    private Map<String, Object> nodeLog(String executionId, String nodeId, String nodeType, String status,
                                        Integer attempt, String errorMessage, String branchTaken) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", 1);
        map.put("execution_id", executionId);
        map.put("node_id", nodeId);
        map.put("node_type", nodeType);
        map.put("status", status);
        map.put("attempt", attempt);
        map.put("error_message", errorMessage);
        map.put("branch_taken", branchTaken);
        map.put("started_at", "2026-09-06T10:00:00");
        return map;
    }
}
