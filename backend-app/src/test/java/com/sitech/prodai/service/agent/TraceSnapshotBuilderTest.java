package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TraceSnapshotBuilder 单元测试：覆盖思考时间线静态视图的核心分支
 * （R2-Phase5 从 AgentOrchestrator 拆出的行为零变更验证）。
 */
class TraceSnapshotBuilderTest {

    // ── thinkingStep ──

    @Test
    void thinkingStepMergesExtraAndDefaults() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("goal", "目标");
        Map<String, Object> step = TraceSnapshotBuilder.thinkingStep("intent", "识别需求", "内容", extra);
        assertEquals("intent", step.get("id"));
        assertEquals("thinking", step.get("type"));
        assertEquals("识别需求", step.get("title"));
        assertEquals("内容", step.get("content"));
        assertEquals("目标", step.get("goal"));
    }

    @Test
    void thinkingStepWithNullExtraKeepsBaseFields() {
        Map<String, Object> step = TraceSnapshotBuilder.thinkingStep("plan", "标题", "内容", null);
        assertEquals(4, step.size());
        assertFalse(step.containsKey("goal"));
    }

    // ── actionDisplay ──

    @Test
    void actionDisplayNullPlanFallsBack() {
        assertEquals("分析", TraceSnapshotBuilder.actionDisplay(null));
    }

    @Test
    void actionDisplayPrefersActionParamOverIntent() {
        QueryPlan plan = new QueryPlan("SWRL_INFER", List.of(), Map.of("action", "compare"), "q");
        assertEquals("对比分析", TraceSnapshotBuilder.actionDisplay(plan));
    }

    @Test
    void actionDisplayFallsBackToIntentWhenActionUnknown() {
        QueryPlan plan = new QueryPlan("SWRL_INFER", List.of(), Map.of("action", "unknown_action"), "q");
        assertEquals("推理分析", TraceSnapshotBuilder.actionDisplay(plan));
    }

    // ── traceView ──

    @Test
    void traceViewNullPlanReturnsNull() {
        assertNull(TraceSnapshotBuilder.traceView(null));
    }

    @Test
    void traceViewEmptyTraceReturnsNull() {
        QueryPlan plan = new QueryPlan();
        assertNull(TraceSnapshotBuilder.traceView(plan));
    }

    @Test
    void traceViewReturnsTraceWhenPresent() {
        QueryPlan plan = new QueryPlan();
        plan.addTrace("llm", "调用大模型理解需求");
        List<Map<String, Object>> trace = TraceSnapshotBuilder.traceView(plan);
        assertEquals(1, trace.size());
        assertEquals("llm", trace.get(0).get("stage"));
    }

    // ── buildWorkflow / planStepCount ──

    @Test
    void buildWorkflowNullPlanReturnsEmpty() {
        assertTrue(TraceSnapshotBuilder.buildWorkflow(null).isEmpty());
    }

    @Test
    void buildWorkflowPrefersStepsOverTools() {
        QueryPlan plan = new QueryPlan("SWRL_INFER", List.of("swrl_root_cause"), Map.of(), "q");
        // QueryPlan 构造器已把 tools 转成 steps，这里只验证 step 粒度展开
        List<Map<String, Object>> workflow = TraceSnapshotBuilder.buildWorkflow(plan);
        assertEquals(1, workflow.size());
        assertEquals("swrl_root_cause", workflow.get(0).get("tool"));
        assertEquals("分析异动原因", workflow.get(0).get("label"));
        assertEquals(1, workflow.get(0).get("step"));
    }

    @Test
    void buildWorkflowUnknownToolFallsBackToInternalName() {
        QueryPlan plan = new QueryPlan();
        plan.setTools(List.of("unknown_tool"));
        List<Map<String, Object>> workflow = TraceSnapshotBuilder.buildWorkflow(plan);
        assertEquals(1, workflow.size());
        assertEquals("unknown_tool", workflow.get(0).get("label"));
    }

    @Test
    void planStepCountAtLeastOne() {
        assertEquals(1, TraceSnapshotBuilder.planStepCount(null));
        assertEquals(1, TraceSnapshotBuilder.planStepCount(new QueryPlan()));
    }

    // ── buildReadablePlan ──

    @Test
    void buildReadablePlanNullPlanFallback() {
        assertEquals("依据您的需求制定分析方案", TraceSnapshotBuilder.buildReadablePlan(null));
    }

    @Test
    void buildReadablePlanIncludesScopeMetricTime() {
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of("sparql_query"),
                Map.of("offering", "宽带A", "metric", "激活量", "time", "2026-08", "action", "SPARQL_QUERY"), "q");
        String text = TraceSnapshotBuilder.buildReadablePlan(plan);
        assertTrue(text.contains("数据查询"));
        assertTrue(text.contains("对象：宽带A"));
        assertTrue(text.contains("指标：激活量"));
        assertTrue(text.contains("时间范围：2026-08"));
        assertTrue(text.contains("查询经营数据"));
    }

    @Test
    void buildReadablePlanWithoutToolsSaysDirectConclusion() {
        QueryPlan plan = new QueryPlan("CHAT", List.of(), Map.of(), "q");
        assertTrue(TraceSnapshotBuilder.buildReadablePlan(plan).contains("直接生成结论"));
    }

    // ── 场景话术 ──

    @Test
    void isRdSceneTrueOnlyForRd() {
        SessionContext rd = new SessionContext("s1");
        rd.setScene("rd");
        SessionContext ops = new SessionContext("s2");
        ops.setScene("ops");
        assertTrue(TraceSnapshotBuilder.isRdScene(rd));
        assertFalse(TraceSnapshotBuilder.isRdScene(ops));
        assertFalse(TraceSnapshotBuilder.isRdScene(null));
    }

    @Test
    void intentStepNameDiffersByScene() {
        SessionContext rd = new SessionContext("s1");
        rd.setScene("rd");
        SessionContext ops = new SessionContext("s2");
        assertEquals("识别配置需求", TraceSnapshotBuilder.intentStepName(rd));
        assertEquals("识别分析需求", TraceSnapshotBuilder.intentStepName(ops));
    }

    @Test
    void intentStepDescDiffersByScene() {
        SessionContext rd = new SessionContext("s1");
        rd.setScene("rd");
        SessionContext ops = new SessionContext("s2");
        assertTrue(TraceSnapshotBuilder.intentStepDesc(rd).contains("配置要素"));
        assertTrue(TraceSnapshotBuilder.intentStepDesc(ops).contains("筛查目标"));
    }

    @Test
    void generateStepDescDiffersByScene() {
        SessionContext rd = new SessionContext("s1");
        rd.setScene("rd");
        SessionContext ops = new SessionContext("s2");
        assertTrue(TraceSnapshotBuilder.generateStepDesc(rd).contains("配置结论"));
        assertTrue(TraceSnapshotBuilder.generateStepDesc(ops).contains("筛查结论"));
    }

    // ── summarizeOutput ──

    @Test
    void summarizeOutputPrefersConclusion() {
        assertEquals("结论A", TraceSnapshotBuilder.summarizeOutput("结论A", 3));
    }

    @Test
    void summarizeOutputBlankConclusionFallsBackWithCount() {
        assertEquals("已整合 3 个环节的处理结果，生成结论与建议",
                TraceSnapshotBuilder.summarizeOutput("  ", 3));
        assertEquals("已整合各环节结果，生成结论与建议",
                TraceSnapshotBuilder.summarizeOutput(null, 0));
    }

    // ── planStepGoal / planStepOutput ──

    @Test
    void planStepGoalRdSceneFixedPhrase() {
        SessionContext rd = new SessionContext("s1");
        rd.setScene("rd");
        QueryPlan plan = new QueryPlan("RD_CONFIG_CHAT", List.of(), Map.of(), "q");
        assertEquals("安排好先做什么、后做什么，让配置一次到位",
                TraceSnapshotBuilder.planStepGoal(plan, rd));
    }

    @Test
    void planStepGoalOpsSceneUsesIntentGoal() {
        SessionContext ops = new SessionContext("s2");
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of(), Map.of(), "q");
        assertEquals("先拿到准确的数据，再基于数据回答您的问题",
                TraceSnapshotBuilder.planStepGoal(plan, ops));
    }

    @Test
    void planStepOutputRdSceneSingleStep() {
        SessionContext rd = new SessionContext("s1");
        rd.setScene("rd");
        QueryPlan plan = new QueryPlan("RD_CONFIG_CHAT", List.of(), Map.of(), "q");
        assertEquals("方案已定，即将开始生成配置草稿",
                TraceSnapshotBuilder.planStepOutput(plan, rd));
    }

    @Test
    void planStepOutputOpsSceneFixedPhrase() {
        SessionContext ops = new SessionContext("s2");
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of(), Map.of(), "q");
        assertEquals("分析路径已确定，即将开始执行",
                TraceSnapshotBuilder.planStepOutput(plan, ops));
    }

    // ── planIntentView / upstreamIntentInput / clarifyInputView ──

    @Test
    void planIntentViewFiltersHiddenAndBlankKeys() {
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of(),
                Map.of("action", "query", "question", "原文", "metric", "激活量", "empty", ""), "q");
        Map<String, Object> intent = TraceSnapshotBuilder.planIntentView(plan);
        assertEquals("数据查询", intent.get("action"));
        assertEquals("激活量", intent.get("metric"));
        assertFalse(intent.containsKey("question"));
        assertFalse(intent.containsKey("empty"));
    }

    @Test
    void upstreamIntentInputUsesStructuredIntentWhenAvailable() {
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of(), Map.of("metric", "激活量"), "用户问题原文");
        Map<String, Object> input = TraceSnapshotBuilder.upstreamIntentInput(plan, "用户问题原文");
        assertEquals("intent", input.get("from_step"));
        assertTrue(input.containsKey("structured_intent"));
        assertFalse(input.containsKey("requirement"));
    }

    @Test
    void upstreamIntentInputFallsBackToRequirementWhenEmpty() {
        QueryPlan plan = new QueryPlan("SPARQL_QUERY", List.of(), Map.of(), "用户问题原文");
        Map<String, Object> input = TraceSnapshotBuilder.upstreamIntentInput(plan, "用户问题原文");
        assertEquals("intent", input.get("from_step"));
        assertEquals("用户问题原文", input.get("requirement"));
    }

    @Test
    void clarifyInputViewCarriesMissingParams() {
        QueryPlan plan = new QueryPlan("CLARIFY", List.of(), Map.of("metric", "激活量"), "q");
        plan.setClarify(List.of("offering", "time"));
        Map<String, Object> input = TraceSnapshotBuilder.clarifyInputView(plan, "q");
        assertEquals("intent", input.get("from_step"));
        assertEquals("offering、time", input.get("missing_params"));
        assertTrue(input.containsKey("structured_intent"));
    }

    @Test
    void clarifyInputViewFallsBackToRequirement() {
        QueryPlan plan = new QueryPlan("CLARIFY", List.of(), Map.of(), "用户问题原文");
        plan.setClarify(List.of("offering"));
        Map<String, Object> input = TraceSnapshotBuilder.clarifyInputView(plan, "用户问题原文");
        assertEquals("offering", input.get("missing_params"));
        assertEquals("用户问题原文", input.get("requirement"));
    }

    // ── requirementSummary ──

    @Test
    void requirementSummaryTruncatesLongQuestion() {
        String longQuestion = "这是一段特别长的用户需求描述超过四十字符用来验证截断逻辑是否正确生效的话术内容继续补充长度";
        String summary = TraceSnapshotBuilder.requirementSummary(longQuestion);
        assertEquals(41, summary.length());
        assertTrue(summary.endsWith("…"));
    }

    @Test
    void requirementSummaryKeepsShortQuestion() {
        assertEquals("短需求", TraceSnapshotBuilder.requirementSummary("短需求"));
    }

    // ── ontologyTraceView（结构化阶段视图）──

    @Test
    void ontologyTraceViewNullOrFailedReturnsNull() {
        assertNull(TraceSnapshotBuilder.ontologyTraceView(null));
        assertNull(TraceSnapshotBuilder.ontologyTraceView(ExecutionResult.fail("swrl_root_cause", "boom")));
    }

    @Test
    void ontologyTraceViewEmitsStructuredPhases() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("opsRulesVersion", "OpsRules-v1.2");
        data.put("reasonEngine", "openllet-swrl");
        data.put("swrlFiredRules", List.of("R-A01"));
        data.put("appliedRules", List.of("R-A02"));
        data.put("anomalies", List.of(Map.of("message", "累计收入环比 -18%")));
        data.put("paths", List.of(Map.of("name", "渠道归因", "weight", 0.8, "ruleId", "R-A02",
                "evidence", List.of("订购量变化 -25%"), "isPrimary", true, "rank", 1)));
        data.put("evidenceTriples", List.of(Map.of("s", "a", "p", "b", "o", "c")));
        ExecutionResult result = ExecutionResult.ok("swrl_root_cause", data);
        List<Map<String, Object>> trace = TraceSnapshotBuilder.ontologyTraceView(result);

        assertEquals(6, trace.size());
        // 全部条目为 stage=ontology 且携带结构化 phase
        for (Map<String, Object> item : trace) {
            assertEquals("ontology", item.get("stage"));
            assertTrue(item.containsKey("phase"));
        }
        assertEquals("加载本体与规则集", trace.get(0).get("phase"));
        assertTrue(String.valueOf(trace.get(0).get("message")).contains("OpsRules-v1.2"));
        assertEquals("启动本体推理引擎", trace.get(1).get("phase"));
        assertTrue(String.valueOf(trace.get(1).get("message")).contains("Openllet SWRL"));
        assertEquals("规则匹配与触发", trace.get(2).get("phase"));
        assertTrue(String.valueOf(trace.get(2).get("message")).contains("R-A01"));
        assertEquals("确认指标异动", trace.get(3).get("phase"));
        assertTrue(String.valueOf(trace.get(3).get("message")).contains("-18%"));
        assertEquals("定位主因", trace.get(4).get("phase"));
        assertTrue(String.valueOf(trace.get(4).get("message")).contains("渠道归因"));
        assertTrue(String.valueOf(trace.get(4).get("message")).contains("R-A02"));
        assertEquals("沉淀证据三元组", trace.get(5).get("phase"));
        assertTrue(String.valueOf(trace.get(5).get("message")).contains("1 条"));
    }

    @Test
    void ontologyTraceViewRiskAuditEmitsComparePhase() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reasonEngine", "java-rules");
        data.put("scannedCount", 80);
        data.put("highCount", 3L);
        data.put("mediumCount", 5L);
        data.put("suggestDelistCount", 2L);
        ExecutionResult result = ExecutionResult.ok("swrl_risk_audit", data);
        List<Map<String, Object>> trace = TraceSnapshotBuilder.ontologyTraceView(result);

        boolean hasCompare = trace.stream()
                .anyMatch(t -> "规则逐条比对".equals(t.get("phase"))
                        && String.valueOf(t.get("message")).contains("80"));
        assertTrue(hasCompare);
    }

    @Test
    void ontologyTraceViewPlainResultReturnsNull() {
        ExecutionResult result = ExecutionResult.ok("sparql_query", Map.of("nl_answer", "普通结果"));
        assertNull(TraceSnapshotBuilder.ontologyTraceView(result));
    }

    // ── ontologyQueryTrace（数据查询过程留痕）──

    @Test
    void ontologyQueryTraceEmitsDiscoverAndSparqlPhases() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("discovery_method", "llm");
        data.put("sparql", "SELECT ?p WHERE { ?p a Offering }");
        data.put("entity_ids", List.of("OF-1", "OF-2"));
        ExecutionResult result = ExecutionResult.ok("sparql_query", data);
        List<Map<String, Object>> trace = TraceSnapshotBuilder.ontologyQueryTrace(result);

        assertEquals(3, trace.size());
        assertEquals("ontology", trace.get(0).get("stage"));
        assertEquals("实体发现", trace.get(0).get("phase"));
        assertTrue(String.valueOf(trace.get(0).get("message")).contains("大模型"));
        assertEquals("生成 SPARQL 查询", trace.get(1).get("phase"));
        assertEquals("命中本体实体", trace.get(2).get("phase"));
        assertTrue(String.valueOf(trace.get(2).get("message")).contains("2 个"));
    }

    @Test
    void ontologyQueryTracePlainResultReturnsNull() {
        assertNull(TraceSnapshotBuilder.ontologyQueryTrace(null));
        assertNull(TraceSnapshotBuilder.ontologyQueryTrace(ExecutionResult.fail("sparql_query", "boom")));
        assertNull(TraceSnapshotBuilder.ontologyQueryTrace(ExecutionResult.ok("sparql_query", Map.of("nl_answer", "空"))));
    }
}
