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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    // ── rdFileParseTrace（智读文件解析四环节留痕，含批量开单环节）──

    @Test
    void rdFileParseTraceEmitsWorkOrderPhase() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parseEngine", "docx");
        data.put("extractEngine", "llm");
        data.put("total", 3);
        data.put("passedCount", 2);
        data.put("workOrderCount", 3);
        ExecutionResult result = ExecutionResult.ok("rd_file_parse", data);
        List<Map<String, Object>> trace = TraceSnapshotBuilder.rdFileParseTrace(result);

        // 有解析/抽取/合规/开单留痕，末条为开单结果且含工单数
        assertEquals("文档解析", trace.get(0).get("phase"));
        assertTrue(String.valueOf(trace.get(trace.size() - 1).get("message")).contains("3 单"));
    }

    @Test
    void rdFileParseTraceIncludesFailureCount() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workOrderCount", 2);
        data.put("workOrderFailures", List.of("#1: 草稿为空"));
        List<Map<String, Object>> trace = TraceSnapshotBuilder.rdFileParseTrace(
                ExecutionResult.ok("rd_file_parse", data));
        assertTrue(String.valueOf(trace.get(trace.size() - 1).get("message")).contains("失败 1 条"));
    }

    @Test
    void rdFileParseTraceNullOrFailedReturnsNull() {
        assertNull(TraceSnapshotBuilder.rdFileParseTrace(null));
        assertNull(TraceSnapshotBuilder.rdFileParseTrace(ExecutionResult.fail("rd_file_parse", "boom")));
        // 无任何结构化环节键 → 空留痕返回 null（前端自然降级）
        assertNull(TraceSnapshotBuilder.rdFileParseTrace(
                ExecutionResult.ok("rd_file_parse", Map.of("nl_answer", "空"))));
    }

    // ── rdFileParseTracePhase（手册步骤按环节切片，避免四步重复全量留痕）──

    @Test
    void rdFileParseTracePhaseSlicesFullChain() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parseEngine", "docx");
        data.put("extractedChars", 1200);
        data.put("trace_id", "tr-001");
        data.put("extractEngine", "llm");
        data.put("total", 3);
        data.put("appliedRules", List.of("R1", "R2"));
        data.put("passedCount", 2);
        data.put("workOrderCount", 3);
        ExecutionResult result = ExecutionResult.ok("rd_file_parse", data);

        // 环节①：解析引擎 + trace_id
        List<Map<String, Object>> p0 = TraceSnapshotBuilder.rdFileParseTracePhase(result, 0);
        assertEquals(2, p0.size());
        assertTrue(String.valueOf(p0.get(0).get("message")).contains("docx"));
        assertTrue(String.valueOf(p0.get(1).get("message")).contains("tr-001"));

        // 环节②：抽取引擎 + 命中规则
        List<Map<String, Object>> p1 = TraceSnapshotBuilder.rdFileParseTracePhase(result, 1);
        assertEquals(2, p1.size());
        assertTrue(String.valueOf(p1.get(0).get("message")).contains("3 条套餐"));
        assertTrue(String.valueOf(p1.get(1).get("message")).contains("R1"));

        // 环节③：仅合规校验
        List<Map<String, Object>> p2 = TraceSnapshotBuilder.rdFileParseTracePhase(result, 2);
        assertEquals(1, p2.size());
        assertTrue(String.valueOf(p2.get(0).get("message")).contains("通过 2 条"));

        // 环节④：仅开单
        List<Map<String, Object>> p3 = TraceSnapshotBuilder.rdFileParseTracePhase(result, 3);
        assertEquals(1, p3.size());
        assertTrue(String.valueOf(p3.get(0).get("message")).contains("3 单"));
    }

    @Test
    void rdFileParseTracePhaseMissingPhaseReturnsNull() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parseEngine", "docx");
        ExecutionResult result = ExecutionResult.ok("rd_file_parse", data);

        // 环节①有产出
        assertNotNull(TraceSnapshotBuilder.rdFileParseTracePhase(result, 0));
        // 环节②③④无结构化产出 → 各自返回 null（前端该步骤自然降级为仅有 SOP 条目）
        assertNull(TraceSnapshotBuilder.rdFileParseTracePhase(result, 1));
        assertNull(TraceSnapshotBuilder.rdFileParseTracePhase(result, 2));
        assertNull(TraceSnapshotBuilder.rdFileParseTracePhase(result, 3));
        // 越界与空链路
        assertNull(TraceSnapshotBuilder.rdFileParseTracePhase(result, 4));
        assertNull(TraceSnapshotBuilder.rdFileParseTracePhase(result, -1));
        assertNull(TraceSnapshotBuilder.rdFileParseTracePhase(null, 0));
    }

    // ── rdFileParsePhaseIo（手册步骤按环节差异化输入/输出，输入承接上一环节产出）──

    @Test
    void rdFileParsePhaseIoDiffersPerPhase() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parseEngine", "docx");
        data.put("extractedChars", 1200);
        data.put("fileNames", List.of("家庭融合方案.docx"));
        data.put("extractEngine", "llm");
        data.put("total", 3);
        data.put("passedCount", 2);
        data.put("workOrderCount", 3);
        data.put("workOrderFailures", List.of("#4: 工单创建失败"));
        ExecutionResult result = ExecutionResult.ok("rd_file_parse", data);

        // 环节①：输入=文档名（无 from_step，链路起点）；输出=解析摘要
        Map<String, Object> io0 = TraceSnapshotBuilder.rdFileParsePhaseIo(result, 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> in0 = (Map<String, Object>) io0.get("input");
        @SuppressWarnings("unchecked")
        Map<String, Object> out0 = (Map<String, Object>) io0.get("output");
        assertNull(in0.get("from_step"));
        assertEquals("家庭融合方案.docx", in0.get("file_name"));
        assertTrue(String.valueOf(out0.get("summary")).contains("docx"));
        assertTrue(String.valueOf(out0.get("summary")).contains("1200"));

        // 环节②：承接环节①；输出带抽取引擎与条数
        Map<String, Object> io1 = TraceSnapshotBuilder.rdFileParsePhaseIo(result, 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> in1 = (Map<String, Object>) io1.get("input");
        @SuppressWarnings("unchecked")
        Map<String, Object> out1 = (Map<String, Object>) io1.get("output");
        assertEquals("sop-step-0", in1.get("from_step"));
        assertEquals("llm", out1.get("extractEngine"));
        assertEquals(3, out1.get("total"));
        assertTrue(String.valueOf(out1.get("summary")).contains("3 条套餐草稿"));

        // 环节③：承接环节②；输出带合规统计
        Map<String, Object> io2 = TraceSnapshotBuilder.rdFileParsePhaseIo(result, 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> in2 = (Map<String, Object>) io2.get("input");
        @SuppressWarnings("unchecked")
        Map<String, Object> out2 = (Map<String, Object>) io2.get("output");
        assertEquals("sop-step-1", in2.get("from_step"));
        assertTrue(String.valueOf(in2.get("requirement")).contains("3 条"));
        assertEquals(2, out2.get("passedCount"));
        assertTrue(String.valueOf(out2.get("summary")).contains("待修正 1 条"));

        // 环节④：承接环节③；输出带工单数与失败数
        Map<String, Object> io3 = TraceSnapshotBuilder.rdFileParsePhaseIo(result, 3);
        @SuppressWarnings("unchecked")
        Map<String, Object> in3 = (Map<String, Object>) io3.get("input");
        @SuppressWarnings("unchecked")
        Map<String, Object> out3 = (Map<String, Object>) io3.get("output");
        assertEquals("sop-step-2", in3.get("from_step"));
        assertTrue(String.valueOf(in3.get("requirement")).contains("2 条"));
        assertEquals(3, out3.get("workOrderCount"));
        assertEquals(1, out3.get("failureCount"));
        assertTrue(String.valueOf(out3.get("summary")).contains("3 单"));
    }

    @Test
    void rdFileParsePhaseIoInvalidInputsReturnEmpty() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parseEngine", "docx");
        ExecutionResult result = ExecutionResult.ok("rd_file_parse", data);

        assertTrue(TraceSnapshotBuilder.rdFileParsePhaseIo(null, 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.rdFileParsePhaseIo(ExecutionResult.fail("rd_file_parse", "boom"), 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.rdFileParsePhaseIo(result, -1).isEmpty());
        assertTrue(TraceSnapshotBuilder.rdFileParsePhaseIo(result, 4).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rdFileParsePhaseIoCarriesRealDetails() {
        Map<String, Object> draft1 = new LinkedHashMap<>();
        draft1.put("offeringName", "全家享融合套餐");
        draft1.put("monthlyFee", 129);
        Map<String, Object> draft2 = new LinkedHashMap<>();
        draft2.put("offeringName", "畅越冰激凌套餐");
        draft2.put("monthlyFee", 99);
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("index", 1);
        item1.put("draft", draft1);
        item1.put("compliancePass", true);
        item1.put("workOrderId", "WO-001");
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("index", 2);
        item2.put("draft", draft2);
        item2.put("compliancePass", false);
        item2.put("issues", List.of(Map.of("ruleId", "R-C03"), "R-C07"));
        item2.put("workOrderId", "WO-002");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("extractEngine", "llm");
        data.put("total", 2);
        data.put("passedCount", 1);
        data.put("workOrderCount", 2);
        data.put("workOrderFailures", List.of("#3: 草稿为空（文档段落未抽到有效配置字段）"));
        data.put("items", List.of(item1, item2));
        ExecutionResult result = ExecutionResult.ok("rd_file_parse", data);

        // 环节②：抽取明细逐条带套餐名与月费
        Map<String, Object> out1 = (Map<String, Object>) TraceSnapshotBuilder.rdFileParsePhaseIo(result, 1).get("output");
        List<String> draftDetails = (List<String>) out1.get("draft_details");
        assertEquals(2, draftDetails.size());
        assertTrue(draftDetails.get(0).contains("全家享融合套餐"));
        assertTrue(draftDetails.get(0).contains("129"));
        assertTrue(draftDetails.get(1).contains("畅越冰激凌套餐"));

        // 环节③：合规明细逐条带通过/待修正与规则编号
        Map<String, Object> out2 = (Map<String, Object>) TraceSnapshotBuilder.rdFileParsePhaseIo(result, 2).get("output");
        List<String> complianceDetails = (List<String>) out2.get("compliance_details");
        assertEquals(2, complianceDetails.size());
        assertTrue(complianceDetails.get(0).contains("通过"));
        assertTrue(complianceDetails.get(1).contains("待修正"));
        assertTrue(complianceDetails.get(1).contains("R-C03"));
        assertTrue(complianceDetails.get(1).contains("R-C07"));

        // 环节④：开单明细逐条带工单号，失败条目原样透出
        Map<String, Object> out3 = (Map<String, Object>) TraceSnapshotBuilder.rdFileParsePhaseIo(result, 3).get("output");
        List<String> orderDetails = (List<String>) out3.get("work_order_details");
        assertEquals(3, orderDetails.size());
        assertTrue(orderDetails.get(0).contains("全家享融合套餐"));
        assertTrue(orderDetails.get(0).contains("WO-001"));
        assertTrue(orderDetails.get(1).contains("WO-002"));
        assertTrue(orderDetails.get(2).startsWith("失败："));

        // 环节①：无明细键（解析环节本就没有草稿产出）
        Map<String, Object> out0 = (Map<String, Object>) TraceSnapshotBuilder.rdFileParsePhaseIo(result, 0).get("output");
        assertFalse(out0.containsKey("draft_details"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rdFileParsePhaseIoDetailsFallBackToExcerptWhenDraftUnnamed() {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("monthlyFee", 59);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", 1);
        item.put("draft", draft);
        item.put("compliancePass", true);
        item.put("sourceExcerpt", "套餐三：月费59元含30GB流量");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", 1);
        data.put("passedCount", 1);
        data.put("items", List.of(item));
        ExecutionResult result = ExecutionResult.ok("rd_file_parse", data);

        // 无套餐名 → 回退原文摘录
        Map<String, Object> out1 = (Map<String, Object>) TraceSnapshotBuilder.rdFileParsePhaseIo(result, 1).get("output");
        List<String> draftDetails = (List<String>) out1.get("draft_details");
        assertEquals(1, draftDetails.size());
        assertTrue(draftDetails.get(0).contains("套餐三：月费59元含30GB流量"));

        // 开单明细：无 workOrderId 回填 → 仅失败列表为空时不发放键
        Map<String, Object> out3 = (Map<String, Object>) TraceSnapshotBuilder.rdFileParsePhaseIo(result, 3).get("output");
        assertFalse(out3.containsKey("work_order_details"));
    }

    // ── 智聊手册（chat-configure）：rd_config_chat 四环节差异化 IO ──

    @Test
    @SuppressWarnings("unchecked")
    void rdConfigChatPhaseIoCarriesDraftAndComplianceDetails() {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringName", "家庭基础套餐·158元·500M");
        draft.put("monthlyFee", 158);
        draft.put("includeBroadband", "500M");
        draft.put("targetUser", "家庭");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("draft", draft);
        data.put("compliancePass", false);
        data.put("issues", List.of(Map.of("ruleId", "R-C03")));
        data.put("workOrderId", "WO-C01");
        ExecutionResult result = ExecutionResult.ok("rd_config_chat", data);

        // 环节①：品类识别（无 from_step 承接）
        Map<String, Object> io0 = TraceSnapshotBuilder.rdConfigChatPhaseIo(result, 0);
        assertFalse(((Map<String, Object>) io0.get("input")).containsKey("from_step"));

        // 环节②：草稿要素明细逐项透出
        Map<String, Object> out1 = (Map<String, Object>) TraceSnapshotBuilder.rdConfigChatPhaseIo(result, 1).get("output");
        List<String> draftDetails = (List<String>) out1.get("draft_details");
        assertTrue(draftDetails.stream().anyMatch(l -> l.contains("offeringName=家庭基础套餐·158元·500M")));
        assertTrue(draftDetails.stream().anyMatch(l -> l.contains("monthlyFee=158")));
        assertTrue(String.valueOf(out1.get("summary")).contains("家庭基础套餐"));

        // 环节③：合规未通过 → 问题规则编号透出
        Map<String, Object> out2 = (Map<String, Object>) TraceSnapshotBuilder.rdConfigChatPhaseIo(result, 2).get("output");
        assertEquals(false, out2.get("compliancePass"));
        List<String> complianceDetails = (List<String>) out2.get("compliance_details");
        assertTrue(complianceDetails.get(0).contains("R-C03"));

        // 环节④：工单号明细
        Map<String, Object> out3 = (Map<String, Object>) TraceSnapshotBuilder.rdConfigChatPhaseIo(result, 3).get("output");
        List<String> orderDetails = (List<String>) out3.get("work_order_details");
        assertTrue(orderDetails.get(0).contains("WO-C01"));
    }

    @Test
    void rdConfigChatPhaseIoInvalidInputsReturnEmpty() {
        ExecutionResult result = ExecutionResult.ok("rd_config_chat", Map.of("draft", Map.of()));
        assertTrue(TraceSnapshotBuilder.rdConfigChatPhaseIo(null, 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.rdConfigChatPhaseIo(ExecutionResult.fail("rd_config_chat", "boom"), 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.rdConfigChatPhaseIo(result, -1).isEmpty());
        assertTrue(TraceSnapshotBuilder.rdConfigChatPhaseIo(result, 4).isEmpty());
    }

    // ── 智查手册（discover-history）三环节差异化 IO ──

    @Test
    @SuppressWarnings("unchecked")
    void rdDiscoverPhaseIoCarriesHitDetails() {
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("name", "校园畅越套餐");
        item1.put("monthlyFee", 39);
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("name", "校园基础套餐");
        item2.put("fixedFeeAmount", 29);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", List.of(item1, item2));
        data.put("entity_ids", List.of(item1, item2));
        ExecutionResult result = ExecutionResult.ok("rd_config_discover", data);

        // 环节②：命中数 + 命中明细逐条带名称与月费
        Map<String, Object> out1 = (Map<String, Object>) TraceSnapshotBuilder.rdDiscoverPhaseIo(result, 1).get("output");
        assertEquals(2, out1.get("hitCount"));
        List<String> hitDetails = (List<String>) out1.get("hit_details");
        assertEquals(2, hitDetails.size());
        assertTrue(hitDetails.get(0).contains("校园畅越套餐"));
        assertTrue(hitDetails.get(0).contains("39"));
        assertTrue(hitDetails.get(1).contains("校园基础套餐"));

        // 环节③：整理结论
        Map<String, Object> out2 = (Map<String, Object>) TraceSnapshotBuilder.rdDiscoverPhaseIo(result, 2).get("output");
        assertTrue(String.valueOf(out2.get("summary")).contains("2"));

        // 空命中：环节③ 提示放宽条件
        Map<String, Object> emptyData = new LinkedHashMap<>();
        emptyData.put("items", List.of());
        emptyData.put("entity_ids", List.of());
        Map<String, Object> emptyOut2 = (Map<String, Object>) TraceSnapshotBuilder
                .rdDiscoverPhaseIo(ExecutionResult.ok("rd_config_discover", emptyData), 2).get("output");
        assertTrue(String.valueOf(emptyOut2.get("summary")).contains("放宽"));
    }

    @Test
    void rdDiscoverPhaseIoInvalidInputsReturnEmpty() {
        ExecutionResult result = ExecutionResult.ok("rd_config_discover", Map.of());
        assertTrue(TraceSnapshotBuilder.rdDiscoverPhaseIo(null, 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.rdDiscoverPhaseIo(ExecutionResult.fail("rd_config_discover", "boom"), 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.rdDiscoverPhaseIo(result, -1).isEmpty());
        assertTrue(TraceSnapshotBuilder.rdDiscoverPhaseIo(result, 3).isEmpty());
    }

    // ── 运营手册（ops 四入口手册）：ops 工具差异化 IO（按工具名分发） ──

    @Test
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoCarriesFactsRiskAndExplainDetails() {
        // 环节① 事实查询（sparql_query，首步无 from_step）
        Map<String, Object> sparql = new LinkedHashMap<>();
        sparql.put("entity_ids", List.of("e1", "e2", "e3"));
        sparql.put("nl_answer", "查到 3 个在架商品及其经营事实");
        ExecutionResult sparqlResult = ExecutionResult.ok("sparql_query", sparql);
        Map<String, Object> io0 = TraceSnapshotBuilder.opsAnalysisPhaseIo(sparqlResult, 0);
        assertFalse(io0.get("input").toString().contains("from_step"), "首步不应有 from_step");
        Map<String, Object> out0 = (Map<String, Object>) io0.get("output");
        assertEquals(3, out0.get("hitCount"));
        assertTrue(String.valueOf(out0.get("summary")).contains("3 个在架商品"));

        // 环节② 根因归因（swrl_root_cause，承接查询 sop-step-0）
        Map<String, Object> cause = new LinkedHashMap<>();
        cause.put("offeringName", "5G新通话");
        cause.put("pathCount", 2);
        cause.put("reasonEngine", "openllet-swrl");
        cause.put("remark", "根因：资费高于同类均值");
        ExecutionResult causeResult = ExecutionResult.ok("swrl_root_cause", cause);
        Map<String, Object> io1 = TraceSnapshotBuilder.opsAnalysisPhaseIo(causeResult, 1);
        assertEquals("sop-step-0", ((Map<String, Object>) io1.get("input")).get("from_step"));
        assertTrue(String.valueOf(((Map<String, Object>) io1.get("input")).get("requirement")).contains("5G新通话"));
        Map<String, Object> out1 = (Map<String, Object>) io1.get("output");
        assertEquals(2, out1.get("pathCount"));
        assertEquals("openllet-swrl", out1.get("reasonEngine"));
        assertTrue(String.valueOf(out1.get("summary")).contains("资费高于同类均值"));

        // 环节③ 风险稽核（swrl_risk_audit，承接查询 sop-step-0——root-cause 手册拆分后稽核不再必然接归因）
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("total", 3);
        audit.put("scannedCount", 20);
        audit.put("highCount", 2);
        audit.put("mediumCount", 1);
        audit.put("suggestDelistCount", 2);
        ExecutionResult auditResult = ExecutionResult.ok("swrl_risk_audit", audit);
        Map<String, Object> io2 = TraceSnapshotBuilder.opsAnalysisPhaseIo(auditResult, 1);
        assertEquals("sop-step-0", ((Map<String, Object>) io2.get("input")).get("from_step"));
        Map<String, Object> out2 = (Map<String, Object>) io2.get("output");
        assertEquals(2, out2.get("highCount"));
        assertEquals(2, out2.get("suggestDelistCount"));
        assertTrue(String.valueOf(out2.get("summary")).contains("高风险 2 个"));

        // 环节④ 规则解释（rule_explain，risk-audit/online-check 手册引入）
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("ruleId", "R-B01");
        rule.put("label", "高风险商品：综合风险分≥80 建议下架");
        ExecutionResult ruleResult = ExecutionResult.ok("rule_explain", rule);
        Map<String, Object> ioR = TraceSnapshotBuilder.opsAnalysisPhaseIo(ruleResult, 2);
        assertEquals("sop-step-1", ((Map<String, Object>) ioR.get("input")).get("from_step"));
        Map<String, Object> outR = (Map<String, Object>) ioR.get("output");
        assertEquals("R-B01", outR.get("ruleId"));
        assertTrue(String.valueOf(outR.get("summary")).contains("高风险商品"));

        // 环节⑤ 本体解释（ontology_explain，承接前序各步）
        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("natural_language", "风险等级依据 R-A03 判定");
        explain.put("referenced_rules", List.of("R-A03", "R-A04"));
        ExecutionResult explainResult = ExecutionResult.ok("ontology_explain", explain);
        Map<String, Object> io3 = TraceSnapshotBuilder.opsAnalysisPhaseIo(explainResult, 2);
        assertEquals("sop-step-1", ((Map<String, Object>) io3.get("input")).get("from_step"));
        Map<String, Object> out3 = (Map<String, Object>) io3.get("output");
        assertTrue(String.valueOf(out3.get("summary")).contains("R-A03"));
        assertEquals(List.of("R-A03", "R-A04"), out3.get("referenced_rules"));
    }

    @Test
    void opsAnalysisPhaseIoInvalidInputsReturnEmpty() {
        ExecutionResult result = ExecutionResult.ok("sparql_query", Map.of());
        assertTrue(TraceSnapshotBuilder.opsAnalysisPhaseIo(null, 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.opsAnalysisPhaseIo(ExecutionResult.fail("sparql_query", "boom"), 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.opsAnalysisPhaseIo(result, -1).isEmpty());
        assertTrue(TraceSnapshotBuilder.opsAnalysisPhaseIo(ExecutionResult.ok("no_such_tool", Map.of()), 0).isEmpty(),
                "未注册分发口径的工具返回空 IO");
    }

    @Test
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoZeroPathsAndNoHighRiskHonestSummaries() {
        // 归因零命中：如实告知不臆造（手册 fallback 约束）
        Map<String, Object> cause = new LinkedHashMap<>();
        cause.put("pathCount", 0);
        Map<String, Object> out1 = (Map<String, Object>) TraceSnapshotBuilder
                .opsAnalysisPhaseIo(ExecutionResult.ok("swrl_root_cause", cause), 1).get("output");
        assertTrue(String.valueOf(out1.get("summary")).contains("未命中归因路径"));

        // 稽核零风险：明确「未发现高风险」而非空泛结论
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("highCount", 0);
        audit.put("suggestDelistCount", 0);
        Map<String, Object> out2 = (Map<String, Object>) TraceSnapshotBuilder
                .opsAnalysisPhaseIo(ExecutionResult.ok("swrl_risk_audit", audit), 2).get("output");
        assertTrue(String.valueOf(out2.get("summary")).contains("未发现高风险"));
    }
}
