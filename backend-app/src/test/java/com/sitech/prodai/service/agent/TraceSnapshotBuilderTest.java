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
        assertEquals("需求识别", TraceSnapshotBuilder.intentStepName(rd));
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
        // missing_params 为结构化数组（规范 §3.1），前端拼接展示
        assertEquals(List.of("offering", "time"), input.get("missing_params"));
        assertTrue(input.containsKey("structured_intent"));
    }

    @Test
    void clarifyInputViewFallsBackToRequirement() {
        QueryPlan plan = new QueryPlan("CLARIFY", List.of(), Map.of(), "用户问题原文");
        plan.setClarify(List.of("offering"));
        Map<String, Object> input = TraceSnapshotBuilder.clarifyInputView(plan, "用户问题原文");
        assertEquals(List.of("offering"), input.get("missing_params"));
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
        assertEquals(3, out0.get("total"));
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
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoRdDocParseCarriesParseDetails() {
        // 单文件：引擎 + 字符数 + 文档名摘要
        Map<String, Object> parse = new LinkedHashMap<>();
        parse.put("parseEngine", "csv");
        parse.put("extractedChars", 1024);
        parse.put("fileName", "智慧社区融合方案.csv");
        parse.put("document_text", "一、融合套餐概述……");
        Map<String, Object> out = (Map<String, Object>) TraceSnapshotBuilder
                .opsAnalysisPhaseIo(ExecutionResult.ok("rd_doc_parse", parse), 0).get("output");
        assertEquals("csv", out.get("parseEngine"));
        assertEquals(1024, out.get("extractedChars"));
        assertTrue(String.valueOf(out.get("summary")).contains("智慧社区融合方案.csv"));
        assertTrue(String.valueOf(out.get("summary")).contains("1024 字"));

        // 多文件：parse_details 逐份明细行（结构化 schema {seq,title,state?,extra?}，前端「解析明细」渲染契约）
        Map<String, Object> multi = new LinkedHashMap<>();
        multi.put("parseEngine", "docx");
        multi.put("fileNames", List.of("a.docx", "b.docx"));
        multi.put("fileDetails", List.of(
                Map.of("file_name", "a.docx", "parse_engine", "docx", "extracted_chars", 500),
                Map.of("file_name", "b.docx", "parse_engine", "docx", "extracted_chars", 300)));
        Map<String, Object> outMulti = (Map<String, Object>) TraceSnapshotBuilder
                .opsAnalysisPhaseIo(ExecutionResult.ok("rd_doc_parse", multi), 0).get("output");
        List<Map<String, Object>> parseRows = (List<Map<String, Object>>) outMulti.get("parse_details");
        assertEquals(2, parseRows.size());
        assertEquals("a.docx", parseRows.get(0).get("title"));
        assertEquals(1, parseRows.get(0).get("seq"));
        assertEquals("引擎 docx，抽取 500 字", parseRows.get(0).get("extra"));
        assertEquals(2, parseRows.get(1).get("seq"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoRdDraftExtractCarriesItemDetails() {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringName", "智慧社区融合套餐");
        draft.put("monthlyFee", 198);
        draft.put("categoryName", "融合套餐");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", 0);
        item.put("draft", draft);
        item.put("compliancePass", true);
        item.put("status", "PENDING_CONFIRM");
        item.put("sourceExcerpt", "198元/月，含40GB流量与500M宽带");
        Map<String, Object> extract = new LinkedHashMap<>();
        extract.put("extractEngine", "llm");
        extract.put("items", List.of(item));
        Map<String, Object> io = TraceSnapshotBuilder.opsAnalysisPhaseIo(ExecutionResult.ok("rd_draft_extract", extract), 1);
        assertEquals("sop-step-0", ((Map<String, Object>) io.get("input")).get("from_step"));
        Map<String, Object> out = (Map<String, Object>) io.get("output");
        assertEquals("llm", out.get("extractEngine"));
        assertEquals(1, out.get("total"));
        assertTrue(String.valueOf(out.get("summary")).contains("1 条套餐草稿"));
        // draft_details 明细行（结构化 schema {seq,title,state?,extra?}，前端「草稿明细」渲染契约）
        List<Map<String, Object>> draftRows = (List<Map<String, Object>>) out.get("draft_details");
        assertEquals(1, draftRows.size());
        assertEquals(1, draftRows.get(0).get("seq"));
        assertEquals("智慧社区融合套餐", draftRows.get(0).get("title"));
        assertEquals("预判通过", draftRows.get(0).get("state"));
        assertEquals("月费 198 元｜原文：198元/月，含40GB流量与500M宽带", draftRows.get(0).get("extra"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoRdComplianceCarriesBatchAndSingleDetails() {
        // 批量形态：逐条结论 + 问题规则编号
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringName", "智慧社区融合套餐");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("draft", draft);
        item.put("compliancePass", false);
        item.put("status", "FAILED");
        item.put("issues", List.of(Map.of("ruleId", "R-B01"), Map.of("ruleId", "R-B02")));
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("items", List.of(item));
        batch.put("total", 1);
        batch.put("passedCount", 0);
        batch.put("pendingCount", 1);
        Map<String, Object> outBatch = (Map<String, Object>) TraceSnapshotBuilder
                .opsAnalysisPhaseIo(ExecutionResult.ok("rd_compliance", batch), 2).get("output");
        assertEquals(1, outBatch.get("total"));
        // compliance_details 明细行（结构化 schema {seq,title,state?,extra?}，前端「合规明细」渲染契约）
        List<Map<String, Object>> complianceRows = (List<Map<String, Object>>) outBatch.get("compliance_details");
        assertEquals(1, complianceRows.size());
        assertEquals(1, complianceRows.get(0).get("seq"));
        assertEquals("智慧社区融合套餐", complianceRows.get(0).get("title"));
        assertEquals("未通过", complianceRows.get(0).get("state"));
        assertEquals("问题规则 R-B01、R-B02", complianceRows.get(0).get("extra"));

        // 单草稿形态：config 内 issues 提取
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("issues", List.of(Map.of("ruleId", "R-A01")));
        Map<String, Object> single = new LinkedHashMap<>();
        single.put("compliance_pass", true);
        single.put("config", config);
        Map<String, Object> outSingle = (Map<String, Object>) TraceSnapshotBuilder
                .opsAnalysisPhaseIo(ExecutionResult.ok("rd_compliance", single), 1).get("output");
        assertEquals(Boolean.TRUE, outSingle.get("compliancePass"));
        assertEquals(List.of("R-A01"), outSingle.get("issueRules"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoRdWorkorderCreateCarriesOrderDetails() {
        Map<String, Object> wo = new LinkedHashMap<>();
        wo.put("workOrderId", "WO-20260908-001");
        wo.put("title", "智慧社区融合套餐配置");
        wo.put("offeringName", "智慧社区融合套餐");
        wo.put("status", "CREATED");
        wo.put("compliancePass", true);
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("workOrders", List.of(wo));
        create.put("workOrderCount", 1);
        create.put("workOrderFailures", List.of(Map.of("index", 1, "reason", "合规校验未通过")));
        Map<String, Object> io = TraceSnapshotBuilder.opsAnalysisPhaseIo(ExecutionResult.ok("rd_workorder_create", create), 3);
        assertEquals("sop-step-2", ((Map<String, Object>) io.get("input")).get("from_step"));
        Map<String, Object> out = (Map<String, Object>) io.get("output");
        assertEquals(1, out.get("workOrderCount"));
        assertEquals(1, ((List<Object>) out.get("workOrderFailures")).size());
        // work_order_details 明细行（结构化 schema {seq,title,state?,extra?}，前端「工单明细」渲染契约）
        List<Map<String, Object>> orderRows = (List<Map<String, Object>>) out.get("work_order_details");
        assertEquals(1, orderRows.size());
        assertEquals(1, orderRows.get(0).get("seq"));
        assertEquals("智慧社区融合套餐配置", orderRows.get(0).get("title"));
        assertEquals("已创建", orderRows.get(0).get("state"));
        assertEquals("工单号 WO-20260908-001", orderRows.get(0).get("extra"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoInvalidInputsReturnEmpty() {
        ExecutionResult result = ExecutionResult.ok("sparql_query", Map.of());
        assertTrue(TraceSnapshotBuilder.opsAnalysisPhaseIo(null, 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.opsAnalysisPhaseIo(ExecutionResult.fail("sparql_query", "boom"), 0).isEmpty());
        assertTrue(TraceSnapshotBuilder.opsAnalysisPhaseIo(result, -1).isEmpty());
        // 未登记专属分支的工具走 default：非空但只给缺省承接（不再返回空 Map 断裂承接链，规范 §3.4）
        Map<String, Object> io = TraceSnapshotBuilder.opsAnalysisPhaseIo(
                ExecutionResult.ok("no_such_tool", Map.of()), 1);
        assertEquals("sop-step-0", ((Map<String, Object>) io.get("input")).get("from_step"));
        assertEquals("已按手册完成本环节处理",
                ((Map<String, Object>) io.get("output")).get("summary"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoDefaultBranchKeepsCarryChainAndBranchLabel() {
        // default 分支（未登记专属分支的 rd 原子工具）：无 nl_answer 也不断裂承接链、给缺省文案（规范 §3.4）
        Map<String, Object> io = TraceSnapshotBuilder.opsAnalysisPhaseIo(
                ExecutionResult.ok("rd_category_resolve", Map.of()), 2);
        assertEquals("sop-step-1", ((Map<String, Object>) io.get("input")).get("from_step"));
        Map<String, Object> out = (Map<String, Object>) io.get("output");
        assertEquals("已按手册完成本环节处理", out.get("summary"));
        assertEquals("按手册执行", out.get("branch_taken"));

        // 专属分支统一携带分支标签（与 plan/generate 步骤结构对齐）
        Map<String, Object> sparql = new LinkedHashMap<>();
        sparql.put("entity_ids", List.of("e1"));
        Map<String, Object> outSparql = (Map<String, Object>) TraceSnapshotBuilder
                .opsAnalysisPhaseIo(ExecutionResult.ok("sparql_query", sparql), 0).get("output");
        assertEquals("按手册执行", outSparql.get("branch_taken"));
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

    // ── rd_slot_extract（智聊链路业务参数抽取）：IO + 过程留痕 ──

    @Test
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoRdSlotExtractCarriesInputAndSlotDetails() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category_code", "familyBasePrc");
        data.put("slot_engine", "llm");
        data.put("slot_count", 3);
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("monthlyFee", 59);
        slots.put("bizScenario", "校园体验");
        slots.put("targetUser", "");
        data.put("slots", slots);
        data.put("missing_slots", List.of("targetUser"));
        data.put("nl_answer", "已抽取业务参数（引擎 llm）：monthlyFee=59.0，bizScenario=校园体验；话术未提及：targetUser");
        ExecutionResult result = ExecutionResult.ok("rd_slot_extract", data);

        // 输入：承接品类码 + 需求话术（不再显示"无需额外参数"）
        Map<String, Object> io = TraceSnapshotBuilder.opsAnalysisPhaseIo(result, 1);
        assertEquals("sop-step-0", ((Map<String, Object>) io.get("input")).get("from_step"));
        assertEquals("familyBasePrc", ((Map<String, Object>) io.get("input")).get("category_code"));
        assertTrue(String.valueOf(((Map<String, Object>) io.get("input")).get("requirement")).contains("配置需求"));

        // 输出：引擎 + 计数 + 缺失要素 + 逐槽位明细行（{seq,title,state?,extra?} 契约）
        Map<String, Object> out = (Map<String, Object>) io.get("output");
        assertEquals("llm", out.get("slotEngine"));
        assertEquals(3, out.get("slotCount"));
        assertEquals(List.of("targetUser"), out.get("missingSlots"));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("slot_details");
        assertEquals(3, rows.size());
        assertEquals("月费", rows.get(0).get("title"));
        assertEquals("值=59", rows.get(0).get("extra"));
        assertEquals("业务场景", rows.get(1).get("title"));
        assertEquals("目标客群", rows.get(2).get("title"));
        assertEquals("未提及", rows.get(2).get("state"));
        // nl_answer 65 字截断到 60：断言截断不异常即可（含省略号），且兜底 summary 不为空
        assertTrue(String.valueOf(out.get("summary")).length() <= 61);
        assertFalse(String.valueOf(out.get("summary")).isBlank());
    }

    @Test
    @SuppressWarnings("unchecked")
    void opsAnalysisPhaseIoRdSlotExtractEmptySlotsFallsBack() {
        // 无 slots/missing_slots：仅给 summary 兜底，不给明细（不渲染空明细块）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("slot_engine", "regex");
        data.put("slot_count", 0);
        data.put("slots", Map.of());
        data.put("nl_answer", "未从话术中抽取到配置要素，请补充月费/客群等关键信息");
        Map<String, Object> out = (Map<String, Object>) TraceSnapshotBuilder
                .opsAnalysisPhaseIo(ExecutionResult.ok("rd_slot_extract", data), 0).get("output");
        assertTrue(String.valueOf(out.get("summary")).contains("请补充"));
        assertFalse(out.containsKey("slot_details"));
        assertFalse(out.containsKey("missingSlots"));
    }

    @Test
    void slotExtractTraceEmitsPhasesByEngine() {
        // llm 引擎：正则快抽 → LLM 补抽 → 白名单过滤 → 缺要素判定（4 阶段，stage=parse）
        Map<String, Object> llmData = new LinkedHashMap<>();
        llmData.put("slot_engine", "llm");
        llmData.put("missing_slots", List.of("targetUser"));
        List<Map<String, Object>> llmTrace = TraceSnapshotBuilder
                .slotExtractTrace(ExecutionResult.ok("rd_slot_extract", llmData));
        assertEquals(4, llmTrace.size());
        for (Map<String, Object> item : llmTrace) {
            assertEquals("parse", item.get("stage"));
            assertTrue(item.containsKey("phase"));
        }
        assertEquals("正则配置快抽", llmTrace.get(0).get("phase"));
        assertEquals("LLM 补抽", llmTrace.get(1).get("phase"));
        assertEquals("白名单过滤", llmTrace.get(2).get("phase"));
        assertEquals("缺要素判定", llmTrace.get(3).get("phase"));
        assertTrue(String.valueOf(llmTrace.get(3).get("message")).contains("目标客群"),
                "缺要素应转业务名展示");

        // regex-fast 短路：正则快抽 → 快抽短路判定 → 缺要素判定（3 阶段，无 LLM 阶段）
        Map<String, Object> fastData = new LinkedHashMap<>();
        fastData.put("slot_engine", "regex-fast");
        fastData.put("missing_slots", List.of());
        List<Map<String, Object>> fastTrace = TraceSnapshotBuilder
                .slotExtractTrace(ExecutionResult.ok("rd_slot_extract", fastData));
        assertEquals(3, fastTrace.size());
        assertEquals("快抽短路判定", fastTrace.get(1).get("phase"));
        assertTrue(String.valueOf(fastTrace.get(2).get("message")).contains("满足"));

        // regex-fallback：含 LLM 补抽失败说明
        Map<String, Object> fbData = new LinkedHashMap<>();
        fbData.put("slot_engine", "regex-fallback");
        fbData.put("missing_slots", List.of("monthlyFee"));
        List<Map<String, Object>> fbTrace = TraceSnapshotBuilder
                .slotExtractTrace(ExecutionResult.ok("rd_slot_extract", fbData));
        assertEquals(3, fbTrace.size());
        assertEquals("LLM 补抽失败", fbTrace.get(1).get("phase"));
    }

    @Test
    void slotExtractTraceNullOrFailedReturnsTraceWithRegexOnly() {
        // 失败结果：不渲染过程留痕（与本体留痕口径一致）
        assertNull(TraceSnapshotBuilder.slotExtractTrace(ExecutionResult.fail("rd_slot_extract", "boom")));
    }
}
