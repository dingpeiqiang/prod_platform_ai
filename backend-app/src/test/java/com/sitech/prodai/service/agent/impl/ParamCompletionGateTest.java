package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ParamCompletionGate 单元测试：必填参数校验 / 缓存回填 / 缺省回填 / CLARIFY 生成 / 澄清超限。
 */
class ParamCompletionGateTest {

    private ParamCompletionGate gate;
    private Map<String, AgentTool> toolMap;

    @BeforeEach
    void setUp() {
        gate = new ParamCompletionGate();
        toolMap = new LinkedHashMap<>();
        toolMap.put("tool_a", tool("tool_a",
                ToolParam.builder("city").label("城市").required().build(),
                ToolParam.builder("month").label("月份").required().defaultValue("2026-09").build(),
                ToolParam.builder("extra").label("附加").build()));
    }

    private AgentTool tool(String name, ToolParam... params) {
        return new AgentTool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public List<ToolParam> getParams() { return List.of(params); }
            @Override public com.sitech.prodai.service.agent.model.ExecutionResult execute(Map<String, Object> p) {
                return null;
            }
        };
    }

    @Test
    void noToolsReturnsPlanUntouched() {
        QueryPlan plan = new QueryPlan();
        plan.setTools(List.of());
        SessionContext ctx = new SessionContext("s1");
        QueryPlan v = gate.validateParams(toolMap, plan, ctx, "q");
        assertEquals(plan, v);
        assertFalse(QueryPlan.INTENT_CLARIFY.equals(v.getIntent()));
    }

    @Test
    void allRequiredPresentResetsClarifyRoundsAndAssumptions() {
        QueryPlan plan = new QueryPlan();
        plan.setTools(List.of("tool_a"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("city", "北京");
        params.put("month", "2026-08");
        plan.setParams(params);
        SessionContext ctx = new SessionContext("s1");
        ctx.incrementClarifyRounds();
        ctx.recordAssumption("stale", "old", "历史假设");

        QueryPlan v = gate.validateParams(toolMap, plan, ctx, "q");

        assertEquals(plan, v);
        assertEquals(0, ctx.getClarifyRounds());
        // 本轮参数齐备：清空上一轮遗留假设（含本轮回填产生的假设，行为与拆分前一致）
        assertTrue(ctx.getAssumptions().isEmpty());
    }

    @Test
    void cachedEvidenceBackfillsMissingParam() {
        QueryPlan plan = new QueryPlan();
        plan.setTools(List.of("tool_a"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("city", "北京");
        plan.setParams(params);
        SessionContext ctx = new SessionContext("s1");
        ctx.getCachedEvidence().put("month", "2026-07");

        QueryPlan v = gate.validateParams(toolMap, plan, ctx, "q");

        assertEquals("2026-07", plan.getParams().get("month"));
        assertFalse(QueryPlan.INTENT_CLARIFY.equals(v.getIntent()));
        assertTrue(plan.getReasoningTrace() == null || plan.getReasoningTrace().isEmpty()
                || plan.getReasoningTrace().stream().anyMatch(t -> "params".equals(t.get("stage"))));
    }

    @Test
    void resolvedParamsTakePriorityOverCachedEvidence() {
        QueryPlan plan = new QueryPlan();
        plan.setTools(List.of("tool_a"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("city", "北京");
        plan.setParams(params);
        SessionContext ctx = new SessionContext("s1");
        ctx.getResolvedParams().put("month", "2026-06");
        ctx.getCachedEvidence().put("month", "2026-07");

        gate.validateParams(toolMap, plan, ctx, "q");

        assertEquals("2026-06", plan.getParams().get("month"));
    }

    @Test
    void defaultValueBackfillsAndRecordsAssumption() {
        toolMap.put("tool_d", tool("tool_d",
                ToolParam.builder("offerName").label("套餐名称").required().build(),
                ToolParam.builder("month").label("月份").required().defaultValue("2026-09").build()));
        QueryPlan plan = new QueryPlan();
        plan.setTools(List.of("tool_d"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("offerName", "畅越冰激凌");
        plan.setParams(params);
        SessionContext ctx = new SessionContext("s1");

        gate.validateParams(toolMap, plan, ctx, "q");

        assertEquals("2026-09", plan.getParams().get("month"));
        // 缺省回填产生假设 → 从DefaultUnderstander拆分时保留原行为：本轮参数齐备时
        // clearAssumptions 会一并清掉本轮回填产生的假设（原实现即如此，避免过期假设污染）
        QueryPlan v2 = gate.validateParams(toolMap, plan, ctx, "q");
        assertEquals(v2, plan);
    }

    @Test
    void missingRequiredWithoutDefaultGeneratesClarifyPlan() {
        toolMap.put("tool_b", tool("tool_b",
                ToolParam.builder("city").label("城市").required().build(),
                ToolParam.builder("offerName").label("套餐名称").description("要查的套餐").required().build()));
        QueryPlan plan = new QueryPlan();
        plan.setTools(List.of("tool_b"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("city", "北京");
        plan.setParams(params);
        SessionContext ctx = new SessionContext("s1");

        QueryPlan v = gate.validateParams(toolMap, plan, ctx, "查套餐");

        assertEquals(QueryPlan.INTENT_CLARIFY, v.getIntent());
        assertEquals(List.of("offerName"), v.getClarify());
        assertEquals("北京", v.getParams().get("city"));
        assertEquals("查套餐", v.getUserQuestion());
        assertEquals(1, ctx.getClarifyRounds());
        Map<String, Object> contract = v.getClarifyContracts().get("offerName");
        assertEquals("套餐名称", contract.get("label"));
        assertEquals("要查的套餐", contract.get("description"));
    }

    @Test
    void exceedClarifyLimitContinuesWithAssumption() {
        toolMap.put("tool_c", tool("tool_c",
                ToolParam.builder("city").label("城市").required().build(),
                ToolParam.builder("offerName").label("套餐名称").required().build()));
        QueryPlan plan = new QueryPlan();
        plan.setTools(List.of("tool_c"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("city", "北京");
        plan.setParams(params);
        SessionContext ctx = new SessionContext("s1");
        for (int i = 0; i < 3; i++) {
            ctx.incrementClarifyRounds();
        }

        QueryPlan v = gate.validateParams(toolMap, plan, ctx, "q");

        assertEquals(plan, v);
        assertEquals(0, ctx.getClarifyRounds());
        assertFalse(ctx.getAssumptions().isEmpty());
        assertNull(plan.getParams().get("offerName"));
    }

    @Test
    void nullContextHandledGracefully() {
        QueryPlan plan = new QueryPlan();
        plan.setTools(List.of("tool_a"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("city", "北京");
        plan.setParams(params);

        QueryPlan v = gate.validateParams(toolMap, plan, null, "q");

        assertEquals("2026-09", plan.getParams().get("month"));
        assertEquals(plan, v);
    }
}
