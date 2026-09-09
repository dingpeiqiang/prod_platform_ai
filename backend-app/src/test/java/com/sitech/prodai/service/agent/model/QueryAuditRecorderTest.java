package com.sitech.prodai.service.agent.model;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询审计记录器（方案 §6-A3）单元测试：
 * 审计视图字段完整性（userScope 摘要/工具链/命中条数）、受限标记、
 * 失败工具与未知契约键的降级口径（-1），以及审计失败不阻断主流程。
 */
class QueryAuditRecorderTest {

    private final QueryAuditRecorder recorder = new QueryAuditRecorder();

    @Test
    void buildContainsScopeSummaryToolsAndHits() {
        UserScope scope = UserScope.of("zhang", List.of("电渠+厅店"), UserScope.SENSITIVITY_INTERNAL);
        QueryPlan plan = new QueryPlan("PRODUCT_QUERY", List.of("sparql_query", "metric_query"),
                Map.of("question", "问题"), "查套餐");
        List<ExecutionResult> results = List.of(
                ExecutionResult.ok("sparql_query", Map.of("entity_ids", List.of("A", "B", "C"))),
                ExecutionResult.ok("metric_query", Map.of("series", List.of(Map.of("date", "d1")))));

        Map<String, Object> audit = recorder.build(scope, "query", plan, results, null);
        recorder.record(audit);

        assertEquals("zhang", audit.get("scope_user"));
        assertEquals(java.util.Set.of("电渠+厅店"), audit.get("scope_channels"));
        assertEquals(UserScope.SENSITIVITY_INTERNAL, audit.get("scope_max_sensitivity"));
        assertEquals(true, audit.get("restricted"), "受限用户应标记 restricted=true");
        assertEquals("query", audit.get("scene"));
        assertEquals("PRODUCT_QUERY", audit.get("intent"));
        assertEquals(List.of("sparql_query", "metric_query"), audit.get("tools"));
        assertEquals("audit", QueryAuditRecorder.AUDIT_KEY, "落库 key 契约固定");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) audit.get("tool_hits");
        assertEquals(2, hits.size());
        assertEquals("sparql_query", hits.get(0).get("tool"));
        assertEquals(3, hits.get(0).get("hits"), "entity_ids 契约键 → 命中 3");
        assertEquals("metric_query", hits.get(1).get("tool"));
        assertEquals(1, hits.get(1).get("hits"), "series 契约键 → 命中 1");
    }

    @Test
    void buildMarksUnrestrictedWithoutScope() {
        Map<String, Object> audit = recorder.build(null, "rd", null, List.of(), null);

        assertEquals("anonymous", audit.get("scope_user"));
        assertEquals(false, audit.get("restricted"), "无 scope（未启用行权限）→ restricted=false");
        assertFalse(audit.containsKey("playbook"), "未走手册链路不带 playbook 标记");
    }

    @Test
    void buildCarriesPlaybookMarkerAndHandlesFailures() {
        UserScope scope = UserScope.unrestricted();
        QueryPlan plan = new QueryPlan("PRODUCT_OPS_QUERY", List.of("sparql_query"),
                Map.of(), "查趋势");
        List<ExecutionResult> results = List.of(
                ExecutionResult.fail("sparql_query", "超时"),
                ExecutionResult.ok("unknown_tool", Map.of("irrelevant", 1)));

        Map<String, Object> audit = recorder.build(scope, "ops", plan, results, "market-insight");

        assertEquals("market-insight", audit.get("playbook"));
        assertEquals(false, audit.get("restricted"), "unrestricted → restricted=false");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) audit.get("tool_hits");
        assertEquals(-1, hits.get(0).get("hits"), "失败工具 → hits=-1");
        assertEquals("fail", hits.get(0).get("status"));
        assertEquals(-1, hits.get(1).get("hits"), "无契约键 → hits=-1（未知口径如实降级）");
        assertEquals("ok", hits.get(1).get("status"));
    }

    @Test
    void recordNeverThrowsAndReturnsView() {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("k", "v");
        Map<String, Object> out = recorder.record(audit);
        assertEquals("v", out.get("k"), "record 返回原视图供透出/落库");
    }
}
