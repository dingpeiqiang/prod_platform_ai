package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.model.ExecStep;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.ThinkingCopy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 思考时间线静态视图构建器（R2-Phase5 从 AgentOrchestrator 拆出）。
 * <p>
 * 单一职责：仅做「内部模型 → 业务可读快照视图」的纯静态转换，
 * 不持有任何可变状态、不依赖注入 —— 同样的输入恒产出同样的视图，
 * 供编排层在 SSE 事件与持久化快照两处复用，保证实时流与历史回放同构。
 */
public final class TraceSnapshotBuilder {

    private TraceSnapshotBuilder() {
    }

    /** 构造业务化思考步骤载荷（供前端时间线渲染为「动作说明 + 输入」）。 */
    public static Map<String, Object> thinkingStep(String id, String title, String content, Map<String, Object> extra) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("type", "thinking");
        step.put("title", title);
        step.put("content", content);
        if (extra != null) {
            step.putAll(extra);
        }
        return step;
    }

    /** action / intent 内部码 → 业务展示名（含产商品研发场景动作，词典收敛至 ThinkingCopy）。 */
    public static String actionDisplay(QueryPlan plan) {
        if (plan == null) {
            return "分析";
        }
        Map<String, Object> params = plan.getParams() != null ? plan.getParams() : Map.of();
        String label = ThinkingCopy.actionDisplay(str(params.get("action")));
        if (label == null || label.isBlank() || label.equals(params.get("action"))) {
            label = ThinkingCopy.actionDisplay(plan.getIntent());
        }
        return (label == null || label.isBlank()) ? "分析" : label;
    }

    /**
     * 推理过程日志视图：理解层（LLM）写入 plan.reasoningTrace 的处理留痕，
     * 下发前端供思考时间线展开「LLM 处理日志」。空列表返回 null（不下发空字段）。
     */
    public static List<Map<String, Object>> traceView(QueryPlan plan) {
        List<Map<String, Object>> trace = plan != null ? plan.getReasoningTrace() : null;
        return trace != null && !trace.isEmpty() ? trace : null;
    }

    /**
     * 将查询计划翻译为业务可读的方案说明，取代透传内部码 queryPlan 给前端渲染卡片。
     */
    public static String buildReadablePlan(QueryPlan plan) {
        if (plan == null) {
            return "依据您的需求制定分析方案";
        }
        Map<String, Object> params = plan.getParams() != null ? plan.getParams() : Map.of();
        String actionLabel = ThinkingCopy.actionDisplay(str(params.get("action")));
        if (actionLabel == null || actionLabel.isBlank() || actionLabel.equals(params.get("action"))) {
            actionLabel = ThinkingCopy.actionDisplay(plan.getIntent());
        }
        if (actionLabel == null || actionLabel.isBlank()) {
            actionLabel = "分析";
        }

        StringBuilder sb = new StringBuilder("本次将执行").append(actionLabel);
        Object offering = params.get("offering");
        Object scope = (offering != null && !str(offering).isBlank())
                ? offering
                : params.get("offeringIds");
        if (scope != null && !str(scope).isBlank()) {
            sb.append("，对象：").append(scope);
        }
        Object metric = params.get("metric");
        if (metric != null && !str(metric).isBlank()) {
            sb.append("，指标：").append(metric);
        }
        Object time = params.get("time");
        if (time != null && !str(time).isBlank()) {
            sb.append("，时间范围：").append(time);
        }
        sb.append("。");
        List<Map<String, Object>> workflow = buildWorkflow(plan);
        if (workflow.isEmpty()) {
            sb.append("直接生成结论");
        } else {
            // 多环节方案才展开执行链；单环节时「方案」与后续工具步骤标题天然重复，仅讲本次动作
            if (workflow.size() > 1) {
                sb.append("方案共 ").append(workflow.size()).append(" 步：");
            }
            for (int i = 0; i < workflow.size(); i++) {
                Map<String, Object> item = workflow.get(i);
                if (i > 0) {
                    sb.append(" → ");
                }
                sb.append(str(item.get("label")));
            }
        }
        return sb.toString();
    }

    /** 方案包含的可执行环节数（无执行步骤算 1 步，供输出文案区分单/多环节）。 */
    public static int planStepCount(QueryPlan plan) {
        List<Map<String, Object>> workflow = buildWorkflow(plan);
        return Math.max(workflow.size(), 1);
    }

    /**
     * 构建可读的处理流程清单（按真实工具链粒度），供「制定方案」步骤展示本方案将依次执行的动作。
     * <p>
     * 依据 {@code plan.getSteps()}（ExecStep 序列）生成；无 steps 时回退到 plan.getTools()。
     *
     * @return 形如 [{step, tool, label}] 的有序清单；无可执行工具时返回空列表。
     */
    public static List<Map<String, Object>> buildWorkflow(QueryPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> tools = new ArrayList<>();
        if (plan.getSteps() != null && !plan.getSteps().isEmpty()) {
            for (ExecStep step : plan.getSteps()) {
                if (step.getTool() != null && !step.getTool().isBlank()) {
                    tools.add(step.getTool());
                }
            }
        }
        if (tools.isEmpty() && plan.getTools() != null) {
            tools.addAll(plan.getTools());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            String tool = tools.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("step", i + 1);
            item.put("tool", tool);
            ThinkingCopy.ToolCopy copy = ThinkingCopy.toolCopy(tool);
            item.put("label", copy != null ? copy.title() : tool);
            out.add(item);
        }
        return out;
    }

    static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 是否为产商品研发场景（scene=rd）。 */
    public static boolean isRdScene(SessionContext context) {
        return context != null && "rd".equals(context.getScene());
    }

    /** 意图识别步骤标题：研发场景为「识别配置需求」，运营场景为「识别分析需求」。 */
    public static String intentStepName(SessionContext context) {
        return isRdScene(context) ? "识别配置需求" : "识别分析需求";
    }

    /** 意图识别步骤描述：研发场景围绕配置要素，运营场景围绕筛查目标。 */
    public static String intentStepDesc(SessionContext context) {
        return isRdScene(context)
                ? "正在理解您的需求，识别业务意图与配置要素…"
                : "正在理解您的需求，识别业务意图与筛查目标…";
    }

    /** 汇总步骤描述：研发场景讲配置结论，运营场景讲筛查结论（避免研发用户读到「筛查」话术）。 */
    public static String generateStepDesc(SessionContext context) {
        return isRdScene(context)
                ? "正在整合各环节处理结果，生成配置结论与建议…"
                : "正在汇总筛查结论与处置建议…";
    }

    /**
     * 汇总步骤「输出」摘要：优先工具层结论（具体、与正文措辞不同），缺省给整合性短文案。
     * 不复述完整报告 —— 报告正文紧随其后打出，摘要里再放一遍会让用户读两遍同一结论。
     */
    public static String summarizeOutput(String conclusion, int stepCount) {
        if (conclusion != null && !conclusion.isBlank()) {
            return conclusion;
        }
        return stepCount > 0
                ? "已整合 " + stepCount + " 个环节的处理结果，生成结论与建议"
                : "已整合各环节结果，生成结论与建议";
    }

    /**
     * 「定下处理方案」步骤的目标文案：讲"怎么安排"而非"干什么"，
     * 与后续工具步骤的 goal（讲"为什么做这一步"）区分，避免业务人员读到重复话术。
     * 研发场景固定话术（配置类诉求一致）；运营场景按意图给目标。
     */
    public static String planStepGoal(QueryPlan plan, SessionContext context) {
        if (isRdScene(context)) {
            return "安排好先做什么、后做什么，让配置一次到位";
        }
        return ThinkingCopy.intentGoal(plan.getIntent());
    }

    /**
     * 「定下处理方案」步骤的输出文案：讲"定了什么"，即最终交付物/执行安排，
     * 与过程描述（怎么执行）区分，避免同一句话在「过程」「输出」两行重复出现。
     */
    public static String planStepOutput(QueryPlan plan, SessionContext context) {
        if (isRdScene(context)) {
            int n = planStepCount(plan);
            return n > 1
                    ? "方案已定：共 " + n + " 个环节，依次执行后交付配置结果"
                    : "方案已定，即将开始生成配置草稿";
        }
        return "分析路径已确定，即将开始执行";
    }

    /**
     * 供方案步骤「输入」展示的参数子集：剔除原始问题与内部噪声键（intent_type/action/text/draft 等），
     * 仅保留业务可读的范围参数（对象/指标/时间等）。
     * <p>
     * rd 场景的配置类诉求（如「月费158带500M宽带」）参数多为 text 话术整体，无结构化范围键，
     * 此时「输入」行会空缺显得敷衍 —— 故补一条「需求」= 用户话术摘要，保证每步输入可读。
     */
    public static Map<String, Object> planInputView(QueryPlan plan, String question) {
        Map<String, Object> params = plan.getParams() != null ? plan.getParams() : Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank() || ThinkingCopy.hideInputKey(key)
                    || e.getValue() == null || str(e.getValue()).isBlank()) {
                continue;
            }
            if (e.getValue() instanceof Map || e.getValue() instanceof List) {
                continue;
            }
            out.put(key, e.getValue());
        }
        if (out.isEmpty() && question != null && !question.isBlank()) {
            out.put("requirement", requirementSummary(question));
        }
        return out;
    }

    /** 用户需求摘要：截断至 40 字供「输入」行展示。 */
    public static String requirementSummary(String question) {
        String q = question.trim();
        return q.length() > 40 ? q.substring(0, 40) + "…" : q;
    }

    // ── 工作流数据流视图：每步「输入」= 上游节点「输出」的引用，形成可审计的传递链路 ──

    /**
     * 理解节点的结构化意图输出：动作 + 业务要素（plan.params 中的业务可读键）。
     * 这是下游 plan/execute/summarize 节点的唯一输入事实来源。
     */
    public static Map<String, Object> planIntentView(QueryPlan plan) {
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("action", actionDisplay(plan));
        Map<String, Object> params = plan.getParams() != null ? plan.getParams() : Map.of();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank() || ThinkingCopy.hideInputKey(key)
                    || e.getValue() == null || str(e.getValue()).isBlank()
                    || e.getValue() instanceof Map || e.getValue() instanceof List) {
                continue;
            }
            intent.put(key, e.getValue());
        }
        return intent;
    }

    /**
     * 方案/执行节点的「输入」视图：承接上游理解节点的结构化意图输出
     * （{action: ..., 客群: ..., 月费: ...}），而非复述用户原文。
     * 仅当结构化意图为空时才回退用户话术摘要。
     */
    public static Map<String, Object> upstreamIntentInput(QueryPlan plan, String question) {
        Map<String, Object> intent = planIntentView(plan);
        if (intent.size() > 1) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("from_step", "intent");
            out.put("structured_intent", intent);
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from_step", "intent");
        out.put("requirement", requirementSummary(question));
        return out;
    }

    /**
     * 澄清节点的「输入」视图：承接理解节点输出的要素缺口（missing 参数契约）。
     */
    public static Map<String, Object> clarifyInputView(QueryPlan plan, String question) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from_step", "intent");
        List<String> missing = plan.getClarify() != null ? plan.getClarify() : List.of();
        out.put("missing_params", String.join("、", missing));
        Map<String, Object> known = planIntentView(plan);
        if (known.size() > 1) {
            out.put("structured_intent", known);
        } else {
            out.put("requirement", requirementSummary(question));
        }
        return out;
    }

    /**
     * 本体推理步骤日志：从工具执行结果中提取本体处理过程（推理引擎/命中规则/归因路径/证据三元组），
     * 下发前端供思考时间线展开「本体处理日志」。非推理工具或无过程信息时返回 null。
     * <p>
     * 结构化阶段（stage/message/phase/detail）：前端据此渲染「本体推理阶段」时间线，
     * 而非一行摘要文本，让业务人员看懂本体推理的每一步逻辑。
     */
    public static List<Map<String, Object>> ontologyTraceView(ExecutionResult result) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return null;
        }
        Map<String, Object> data = result.getData();
        List<Map<String, Object>> trace = new ArrayList<>();

        // 阶段① 加载本体与规则集
        Object rulesVersion = data.get("opsRulesVersion");
        if (rulesVersion != null && !str(rulesVersion).isBlank()) {
            trace.add(ontologyPhase("加载本体与规则集", "载入业务规则集 " + rulesVersion + "，准备推理"));
        }

        // 阶段② 启动推理引擎（含引擎降级说明）
        Object engine = data.get("reasonEngine");
        if (engine != null && !str(engine).isBlank()) {
            String engineName = str(engine);
            String engineCn = "openllet-swrl".equals(engineName) ? "Openllet SWRL 推理机"
                    : engineName.startsWith("openllet-swrl+") ? "Openllet SWRL 推理机（部分回退 Java 规则）"
                    : "fallback-java".equals(engineName) ? "Java 规则引擎（SWRL 不可用已降级）"
                    : "Java 规则引擎";
            trace.add(ontologyPhase("启动本体推理引擎", "使用 " + engineCn + " 在知识图谱上执行规则推理"));
        }

        // 阶段③ 规则触发（SWRL 触发 + 业务规则命中，逐条列出）
        List<String> ruleLines = new ArrayList<>();
        Object firedRules = data.get("swrlFiredRules");
        if (firedRules instanceof List<?> fired && !fired.isEmpty()) {
            ruleLines.add("SWRL 规则触发：" + String.join("、", fired.stream().map(String::valueOf).toList()));
        }
        Object appliedRules = data.get("appliedRules");
        if (appliedRules instanceof List<?> applied && !applied.isEmpty()) {
            ruleLines.add("命中业务规则：" + String.join("、", applied.stream().map(String::valueOf).toList()));
        }
        if (!ruleLines.isEmpty()) {
            trace.add(ontologyPhase("规则匹配与触发", String.join("；", ruleLines)));
        }

        // 阶段④ 指标异动确认（归因场景）
        if (data.get("anomalies") instanceof List<?> anomalies && !anomalies.isEmpty()) {
            List<String> anomalyDesc = new ArrayList<>();
            for (Object a : anomalies) {
                if (a instanceof Map<?, ?> am && am.get("message") != null) {
                    anomalyDesc.add(str(am.get("message")));
                }
            }
            if (!anomalyDesc.isEmpty()) {
                trace.add(ontologyPhase("确认指标异动", String.join("；", anomalyDesc)));
            }
        }

        // 阶段⑤ 归因路径推理（按权重排序，逐条带证据）
        if (data.get("paths") instanceof List<?> paths && !paths.isEmpty()) {
            for (Object p : paths) {
                if (!(p instanceof Map<?, ?> pm) || pm.get("name") == null) {
                    continue;
                }
                String name = str(pm.get("name"));
                Object weight = pm.get("weight");
                Object ruleId = pm.get("ruleId");
                StringBuilder detail = new StringBuilder(name)
                        .append(weight != null ? "（影响权重 " + weight + "）" : "")
                        .append(ruleId != null && !str(ruleId).isBlank() ? " · 规则 " + ruleId : "");
                if (pm.get("evidence") instanceof List<?> ev && !ev.isEmpty()) {
                    detail.append(" · 依据：").append(ev.stream().map(String::valueOf)
                            .reduce((a, b) -> a + "、" + b).orElse(""));
                }
                Object isPrimary = pm.get("isPrimary");
                trace.add(ontologyPhase(
                        Boolean.TRUE.equals(isPrimary) || "1".equals(str(pm.get("rank"))) ? "定位主因" : "归因路径 " + str(pm.get("rank")),
                        detail.toString()));
            }
        }

        // 阶段⑥ 风险分层与处置建议（稽核场景）
        if (data.get("highCount") instanceof Number highN && data.get("scannedCount") instanceof Number scannedN) {
            StringBuilder sb = new StringBuilder("扫描在架商品 ").append(scannedN).append(" 个");
            if (data.get("mediumCount") instanceof Number medN) {
                sb.append("，高风险 ").append(highN).append(" 个、中风险 ").append(medN).append(" 个");
            }
            if (data.get("suggestDelistCount") instanceof Number delistN) {
                sb.append("，建议下架 ").append(delistN).append(" 个");
            }
            trace.add(ontologyPhase("规则逐条比对", sb.toString()));
        }

        // 阶段⑦ 证据三元组落库（结论可回溯）
        if (data.get("evidenceTriples") instanceof List<?> triples && !triples.isEmpty()) {
            trace.add(ontologyPhase("沉淀证据三元组",
                    "落库 " + triples.size() + " 条「主体-关系-客体」事实，结论可逐条回溯"));
        }

        // 无结构化阶段可用时回退到旧行为（一行摘要），保证兼容
        return trace.isEmpty() ? legacyOntologyTrace(data) : trace;
    }

    /** 构造结构化本体推理阶段条目。 */
    private static Map<String, Object> ontologyPhase(String phase, String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("stage", "ontology");
        item.put("phase", phase);
        item.put("message", message);
        return item;
    }

    /** 旧版一行式本体留痕（结构化阶段不可用时兜底）。 */
    private static List<Map<String, Object>> legacyOntologyTrace(Map<String, Object> data) {
        List<Map<String, Object>> trace = new ArrayList<>();
        Object engine = data.get("reasonEngine");
        if (engine != null && !str(engine).isBlank()) {
            trace.add(Map.of("stage", "ontology", "message", "本体推理引擎：" + engine));
        }
        if (data.get("swrlFiredRules") instanceof List<?> fired && !fired.isEmpty()) {
            trace.add(Map.of("stage", "ontology",
                    "message", "SWRL 规则触发：" + String.join("、", fired.stream().map(String::valueOf).toList())));
        }
        if (data.get("appliedRules") instanceof List<?> applied && !applied.isEmpty()) {
            trace.add(Map.of("stage", "ontology",
                    "message", "命中业务规则：" + String.join("、", applied.stream().map(String::valueOf).toList())));
        }
        if (data.get("paths") instanceof List<?> paths && !paths.isEmpty()) {
            List<String> pathDesc = new ArrayList<>();
            for (Object p : paths) {
                if (p instanceof Map<?, ?> pm && pm.get("name") != null) {
                    String name = str(pm.get("name"));
                    Object weight = pm.get("weight");
                    pathDesc.add(name + (weight != null ? "（权重 " + weight + "）" : ""));
                }
            }
            if (!pathDesc.isEmpty()) {
                trace.add(Map.of("stage", "ontology",
                        "message", "归因路径（按权重排序）：" + String.join(" → ", pathDesc)));
            }
        }
        if (data.get("evidenceTriples") instanceof List<?> triples && !triples.isEmpty()) {
            trace.add(Map.of("stage", "ontology",
                    "message", "证据三元组落库 " + triples.size() + " 条，支撑结论可回溯"));
        }
        return trace.isEmpty() ? null : trace;
    }

    /**
     * 数据查询（NL→SPARQL）过程留痕：让「查询经营数据」步骤展示本体查询的逻辑过程
     * （本体加载 → 实体发现方式 → 生成 SPARQL → 命中实体数）。
     */
    public static List<Map<String, Object>> ontologyQueryTrace(ExecutionResult result) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return null;
        }
        Map<String, Object> data = result.getData();
        List<Map<String, Object>> trace = new ArrayList<>();
        Object method = data.get("discovery_method");
        if (method != null && !str(method).isBlank()) {
            trace.add(ontologyPhase("实体发现",
                    "llm".equals(str(method))
                            ? "由大模型从问题中提取实体类型与筛选条件"
                            : "基于关键词匹配定位本体实体"));
        }
        Object sparql = data.get("sparql");
        if (sparql != null && !str(sparql).isBlank()) {
            trace.add(ontologyPhase("生成 SPARQL 查询", "在本体知识库执行语义查询"));
        }
        if (data.get("entity_ids") instanceof List<?> ids && !ids.isEmpty()) {
            trace.add(ontologyPhase("命中本体实体", "检索到 " + ids.size() + " 个实体及其关联事实"));
        }
        return trace.isEmpty() ? null : trace;
    }
}
