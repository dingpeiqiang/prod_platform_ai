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
     * 手册 SOP → 思考步骤 trace（手册层思考过程呈现的核心）：
     * 把手册的每个步骤渲染为一条 trace 记录，方案步骤展开后即见手册的
     * 「操作步骤 + 每步操作方法 + 使用的工具」——LLM 是照此执行的，展示的也是真实依据。
     *
     * @param sop renderSop 输出（含「第N步 X——方法（工具：t）」行）
     * @return 形如 [{stage:"sop", message:"第1步 …"}]；无法解析时 null（前端自然降级）
     */
    public static List<Map<String, Object>> sopTraceView(String sop) {
        if (sop == null || sop.isBlank()) {
            return null;
        }
        List<Map<String, Object>> trace = new ArrayList<>();
        for (String line : sop.split("\n")) {
            String t = line.trim();
            if (t.startsWith("第") && t.contains("步 ")) {
                trace.add(Map.of("stage", "sop", "message", t));
            } else if (t.startsWith("护栏：")) {
                trace.add(Map.of("stage", "sop", "message", t));
            }
        }
        return trace.isEmpty() ? null : trace;
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

    /** 意图识别步骤标题：研发场景为「需求识别」，运营场景为「识别分析需求」。 */
    public static String intentStepName(SessionContext context) {
        return isRdScene(context) ? "需求识别" : "识别分析需求";
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
     * 「方案规划」步骤的目标文案：讲"怎么安排"而非"干什么"，
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
     * 「方案规划」步骤的输出文案：讲"定了什么"，即最终交付物/执行安排，
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
        out.put("missing_params", missing);
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

    /** 槽位抽取引擎 → 业务可读名称（rd_slot_extract 过程留痕用）。 */
    private static String slotEngineName(String engine) {
        return switch (engine == null ? "" : engine) {
            case "regex-fast" -> "配置正则快抽（命中关键槽位，跳过 LLM）";
            case "llm" -> "正则快抽 + LLM 补抽";
            case "regex-fallback" -> "正则抽取（LLM 补抽失败，回退正则结果）";
            case "regex" -> "配置正则抽取（LLM 补抽未启用）";
            default -> engine;
        };
    }

    /** 槽位键 → 业务展示名（rd_slot_extract 过程留痕用，与 LLM 抽取白名单同源）。 */
    private static final Map<String, String> SLOT_LABELS = Map.ofEntries(
            Map.entry("offeringName", "商品名称"),
            Map.entry("monthlyFee", "月费"),
            Map.entry("targetUser", "目标客群"),
            Map.entry("includeBroadband", "宽带"),
            Map.entry("channelScope", "渠道"),
            Map.entry("bizScenario", "业务场景"),
            Map.entry("includeData", "流量"),
            Map.entry("includeVoice", "语音"),
            Map.entry("offeringType", "商品类型"),
            Map.entry("hasContract", "合约"),
            Map.entry("contractMonths", "合约期"),
            Map.entry("repeatable", "可重复订购"),
            Map.entry("discountPercent", "折扣"),
            Map.entry("dependOn", "依赖项"),
            Map.entry("bindExistingMainPkg", "绑定主套餐"),
            Map.entry("clearBindExisting", "解除绑定"));

    /** 槽位键 → 业务展示名。 */
    public static String slotLabel(String key) {
        String label = key != null ? SLOT_LABELS.get(key) : null;
        return label != null ? label : key;
    }

    /**
     * 业务参数抽取（rd_slot_extract）过程留痕：把「正则快抽 → LLM 补抽 → 白名单过滤 →
     * 缺要素判定」四阶段按实际引擎逐阶段展开，让业务人员看懂抽取逻辑而非一行文案。
     * 阶段逐条判定（非一刀切），仅呈现实际发生的处理路径。
     */
    public static List<Map<String, Object>> slotExtractTrace(ExecutionResult result) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return null;
        }
        Map<String, Object> data = result.getData();
        List<Map<String, Object>> trace = new ArrayList<>();
        String engine = str(data.get("slot_engine"));
        List<String> missing = data.get("missing_slots") instanceof List<?> m
                ? m.stream().map(String::valueOf).toList() : List.of();

        // 阶段① 正则快抽（regex/regex-fast/regex-fallback/llm 均先执行配置正则）
        if ("regex".equals(engine)) {
            trace.add(slotPhase("正则配置快抽",
                    "按配置正则模式抽取槽位（LLM 补抽未启用），仅能识别话术中的显式要素"));
        } else {
            trace.add(slotPhase("正则配置快抽",
                    "按配置正则模式（ops_rules.slotPatterns + 内置正则）先抽取显式要素"));
        }

        // 阶段② LLM 补抽（regex-fast 短路 / regex 禁用时不发生；regex-fallback 表示尝试过但失败）
        switch (engine == null ? "" : engine) {
            case "regex-fast" ->
                    trace.add(slotPhase("快抽短路判定",
                            "关键槽位（场景/月费/客群/宽带）已齐备，跳过 LLM 补抽，缩短首包时间"));
            case "regex-fallback" ->
                    trace.add(slotPhase("LLM 补抽失败",
                            "大模型补抽未返回有效槽位，回退采用正则结果，不影响已抽要素"));
            case "llm" -> {
                trace.add(slotPhase("LLM 补抽",
                        "正则未抽齐关键槽位，调用大模型按槽位白名单补抽（未提及字段不编造）"));
                trace.add(slotPhase("白名单过滤",
                        "LLM 返回字段经模板槽位白名单过滤，仅保留合法槽位并合并"));
            }
            default -> {
                // 无引擎标识：不渲染后续阶段，仅保留正则阶段说明
            }
        }

        // 阶段③ 缺要素判定（模板 required_slots 口径，missing_slots 明示不静默）
        if (missing.isEmpty()) {
            trace.add(slotPhase("缺要素判定",
                    "话术要素满足模板 required_slots 要求，进入下一环节草稿生成"));
        } else {
            trace.add(slotPhase("缺要素判定",
                    "按模板 required_slots 判定话术未提及：" + missing.stream()
                            .map(TraceSnapshotBuilder::slotLabel).reduce((a, b) -> a + "、" + b).orElse("")
                            + "（缺省补全，可补充说明）"));
        }
        return trace;
    }

    /** 构造结构化槽位抽取阶段条目（stage=parse，前端「解析」前缀渲染）。 */
    private static Map<String, Object> slotPhase(String phase, String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("stage", "parse");
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
     * 手册步骤的差异化「输入/输出」：ops 四本入口手册与 rd 原子工具链（一个工具一个环节，
     * 步骤与工具 1:1）步骤各对应一个真实工具执行，每步输入承接上一环节产出
     * （from_step = sop-step-(N-1)），输出只讲本环节结论。
     * <p>
     * 按工具名分发（非按手册分发）：同一工具在不同手册中语义一致（查询=事实查询、
     * 稽核=风险筛查、rd 解析=文档解析），工具输出契约不变，环节 IO 只需认工具与步骤序号——
     * 手册拆分/新增不再要求本方法同步改分支。
     * <ul>
     *   <li>sparql_query：输入=话术，输出=命中实体数+事实摘要（step0 为链路首步，无 from_step）</li>
     *   <li>swrl_root_cause：输入=分析对象（承接查询），输出=归因路径数+引擎标识</li>
     *   <li>swrl_risk_audit：输入=筛查范围（承接查询），输出=风险等级统计+建议下架数</li>
     *   <li>rule_explain：输入=命中规则（承接稽核/研判），输出=规则语义+编号</li>
     *   <li>ontology_explain：输入=问诊结论（承接前序各步），输出=解释文案+引用规则</li>
     *   <li>rd_slot_extract：输入=承接品类码+配置需求话术，输出=引擎+槽位明细（含缺失要素标注）</li>
     *   <li>rd_config_search：输出=命中数+命中明细（名称/月费/状态/品类，供前端渲染配置卡片）</li>
     *   <li>其余 rd 原子工具：无专属分支，走 default 全量摘要（summary 取 nl_answer）</li>
     * </ul>
     */
    public static Map<String, Object> opsAnalysisPhaseIo(ExecutionResult result, int phaseIdx) {
        if (result == null || !result.isSuccess() || result.getData() == null || phaseIdx < 0) {
            return Map.of();
        }
        Map<String, Object> data = result.getData();
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> output = new LinkedHashMap<>();
        String tool = result.getToolName();
        if (phaseIdx > 0) {
            input.put("from_step", "sop-step-" + (phaseIdx - 1));
        }
        switch (tool == null ? "" : tool) {
            case "sparql_query" -> { // 事实查询（market-insight/root-cause/risk-audit/online-check 首步）
                int hits = data.get("entity_ids") instanceof List<?> ids ? ids.size() : 0;
                output.put("total", hits);
                String answer = str(data.get("nl_answer"));
                if (!answer.isBlank() && !"null".equals(answer)) {
                    output.put("summary", firstChars(answer, 60));
                } else {
                    output.put("summary", hits > 0
                            ? "已在知识库命中 " + hits + " 个实体及关联事实"
                            : "知识库未命中相关实体");
                }
            }
            case "swrl_root_cause" -> { // 根因归因（root-cause 第2步）
                String entity = firstNonBlank(str(data.get("offeringName")), str(data.get("offeringId")));
                input.put("requirement", entity.isBlank() ? "上一步查询到的经营事实" : "商品「" + entity + "」的经营事实");
                Object pathCount = data.get("pathCount") != null ? data.get("pathCount")
                        : (data.get("paths") instanceof List<?> paths ? paths.size() : null);
                if (pathCount != null) {
                    output.put("pathCount", pathCount);
                }
                String engine = str(data.get("reasonEngine"));
                if (!engine.isBlank() && !"null".equals(engine)) {
                    output.put("reasonEngine", engine);
                }
                String remark = str(data.get("remark"));
                String rootCause = firstNonBlank(remark.isBlank() ? "" : remark, str(data.get("nl_answer")));
                output.put("summary", pathCount != null && String.valueOf(pathCount).matches("\\d+") && "0".equals(String.valueOf(pathCount))
                        ? "未命中归因路径，如实告知不臆造原因"
                        : firstNonBlank(rootCause.isBlank() ? "" : firstChars(rootCause, 40),
                                "已完成 SWRL 归因推理"));
            }
            case "swrl_risk_audit" -> { // 风险稽核（market-insight 第2步 / risk-audit 第3步）
                input.put("requirement", "上一步查询圈定的在架商品范围");
                for (String key : new String[]{"total", "scannedCount", "highCount", "mediumCount", "suggestDelistCount"}) {
                    if (data.get(key) instanceof Number n) {
                        output.put(key, n.intValue());
                    }
                }
                int high = data.get("highCount") instanceof Number h ? h.intValue() : 0;
                int delist = data.get("suggestDelistCount") instanceof Number d ? d.intValue() : 0;
                output.put("summary", high > 0 || delist > 0
                        ? "稽核命中：高风险 " + high + " 个，建议下架 " + delist + " 个（处置建议供人工决策）"
                        : "全量筛查完成，未发现高风险商品");
            }
            case "rule_explain" -> { // 规则解释（risk-audit 第3步 / online-check 第2步）
                input.put("requirement", "稽核/研判命中的风险或门槛规则编号");
                String ruleId = str(data.get("ruleId"));
                String label = str(data.get("label"));
                if (!ruleId.isBlank() && !"null".equals(ruleId)) {
                    output.put("ruleId", ruleId);
                }
                String ruleSummary = firstNonBlank(
                        label.isBlank() ? "" : firstChars(label, 40),
                        ruleId.isBlank() || "null".equals(ruleId) ? "" : "已解释规则 " + ruleId);
                output.put("summary", ruleSummary.isBlank() ? "已给出规则语义解释" : ruleSummary);
                if (data.get("referenced_rules") instanceof List<?> rules && !rules.isEmpty()) {
                    output.put("referenced_rules", rules.stream().map(String::valueOf).limit(DETAIL_LIMIT).toList());
                }
            }
            case "ontology_explain" -> { // 本体概念解释（root-cause 第3步 / online-check 第3步）
                input.put("requirement", "前序结论中涉及的本体概念/规则");
                String explanation = firstNonBlank(str(data.get("natural_language")), str(data.get("concept")));
                output.put("summary", explanation.isBlank() || "null".equals(explanation)
                        ? "已给出问诊结论涉及的本体规则解释"
                        : firstChars(explanation, 40));
                if (data.get("referenced_rules") instanceof List<?> rules && !rules.isEmpty()) {
                    output.put("referenced_rules", rules.stream().map(String::valueOf).limit(DETAIL_LIMIT).toList());
                }
            }
            case "rd_slot_extract" -> { // 业务参数抽取（智聊链路，承接品类识别）
                // 输入：承接上游品类码 + 配置需求话术（前端「输入」行不再显示"无需额外参数"）
                if (!str(data.get("category_code")).isBlank() && !"null".equals(str(data.get("category_code")))) {
                    input.put("category_code", str(data.get("category_code")));
                }
                input.put("requirement", "用户配置需求话术（按模板 required_slots 判定缺要素）");
                // 输出：引擎 + 抽取计数 + 逐槽位明细行（结构化 schema {seq,title,state?,extra?}）
                String engine = str(data.get("slot_engine"));
                if (!engine.isBlank() && !"null".equals(engine)) {
                    output.put("slotEngine", engine);
                }
                if (data.get("slot_count") instanceof Number n) {
                    output.put("slotCount", n.intValue());
                }
                List<String> missingSlots = data.get("missing_slots") instanceof List<?> m
                        ? m.stream().map(String::valueOf).toList() : List.of();
                if (!missingSlots.isEmpty()) {
                    output.put("missingSlots", missingSlots.stream().limit(DETAIL_LIMIT).toList());
                }
                if (data.get("slots") instanceof Map<?, ?> slots && !slots.isEmpty()) {
                    List<Map<String, Object>> slotRows = new ArrayList<>();
                    int seq = 0;
                    for (Map.Entry<?, ?> e : slots.entrySet()) {
                        if (seq >= DETAIL_LIMIT) {
                            break;
                        }
                        String key = str(e.getKey());
                        String value = str(e.getValue());
                        slotRows.add(detailRow(++seq, slotLabel(key), missingSlots.contains(key) ? "未提及" : null,
                                value.isBlank() ? "" : "值=" + value));
                    }
                    output.put("slot_details", slotRows);
                }
                String answer = str(data.get("nl_answer"));
                output.put("summary", !answer.isBlank() && !"null".equals(answer)
                        ? firstChars(answer, 60) : "已抽取业务参数");
            }
            case "rd_config_search" -> { // 历史配置检索（discover-history 链路）
                int hits = data.get("items") instanceof List<?> l ? l.size()
                        : data.get("entity_ids") instanceof List<?> ids ? ids.size() : 0;
                output.put("total", hits);
                // 命中明细随步骤输出透出（名称/月费/状态/品类要素），供前端渲染可点击的配置卡片
                if (data.get("items") instanceof List<?> list && !list.isEmpty()) {
                    output.put("items", list.stream().limit(DETAIL_LIMIT).toList());
                }
                String answer = str(data.get("nl_answer"));
                output.put("summary", !answer.isBlank() && !"null".equals(answer)
                        ? firstChars(answer, 60)
                        : hits > 0 ? "检索到 " + hits + " 条配置方案" : "未找到匹配的历史配置");
            }
            case "rd_doc_parse" -> { // 文档解析（智读链路环节①）
                String parseEngine = str(data.get("parseEngine"));
                Object chars = data.get("extractedChars");
                if (!parseEngine.isBlank() && !"null".equals(parseEngine)) {
                    output.put("parseEngine", parseEngine);
                }
                if (chars instanceof Number n) {
                    output.put("extractedChars", n.intValue());
                }
                // 结构化文档 IR 概要（表格块/sheet 名）：随解析明细透出，供用户确认解析结构完整性
                DocumentIrSummary ir = documentIrSummary(data.get("document"));
                if (ir != null) {
                    output.put("document", data.get("document"));
                }
                // 解析明细（结构化行：文件名 + 引擎 + 字符数），前端按统一 schema 渲染
                List<Map<String, Object>> parseRows = new ArrayList<>();
                if (data.get("fileDetails") instanceof List<?> details && !details.isEmpty()) {
                    int seq = 0;
                    for (Object d : details.stream().limit(DETAIL_LIMIT).toList()) {
                        if (d instanceof Map<?, ?> fd) {
                            String engine = str(fd.get("parse_engine"));
                            Object c = fd.get("extracted_chars");
                            parseRows.add(detailRow(++seq, str(fd.get("file_name")), null,
                                    "引擎 " + (engine.isBlank() ? parseEngine : engine)
                                            + (c instanceof Number cn ? "，抽取 " + cn.intValue() + " 字" : "")));
                        }
                    }
                } else if (!str(data.get("fileName")).isBlank() && !"null".equals(str(data.get("fileName")))) {
                    parseRows.add(detailRow(1, str(data.get("fileName")), null,
                            "引擎 " + parseEngine + (chars instanceof Number n ? "，抽取 " + n.intValue() + " 字" : "")));
                } else if (ir != null) {
                    parseRows.add(detailRow(1, "文档解析", null, "引擎 " + parseEngine
                            + (chars instanceof Number n ? "，抽取 " + n.intValue() + " 字" : "")));
                }
                // 无 fileDetails/fileName 的纯文本/IR 链路：按 IR 块结构逐表给明细（多 sheet 不再合并成一行长文本）
                if (parseRows.isEmpty() && ir != null) {
                    int seq = 0;
                    for (String tableTitle : ir.tableTitles) {
                        parseRows.add(detailRow(++seq, tableTitle, null,
                                "表格 " + ir.tableColCounts.get(seq - 1) + " 列"));
                    }
                }
                if (!parseRows.isEmpty()) {
                    output.put("parse_details", parseRows);
                }
                String fileName = str(data.get("fileName"));
                StringBuilder summary = new StringBuilder();
                if (!fileName.isBlank() && !"null".equals(fileName)) {
                    summary.append(fileName).append("：");
                }
                summary.append("已解析文档");
                if (!parseEngine.isBlank() && !"null".equals(parseEngine)) {
                    summary.append("（引擎 ").append(parseEngine);
                    if (chars instanceof Number n) {
                        summary.append("，抽取 ").append(n.intValue()).append(" 字");
                    }
                    summary.append("）");
                }
                if (ir != null) {
                    summary.append(ir.structureLabel);
                }
                output.put("summary", summary.toString());
            }
            case "rd_draft_extract" -> { // 套餐抽取（智读链路环节②）
                output.put("extractEngine", str(data.get("extractEngine")));
                int total = data.get("total") instanceof Number n ? n.intValue()
                        : data.get("items") instanceof List<?> l ? l.size() : 0;
                int passed = data.get("passedCount") instanceof Number n ? n.intValue() : 0;
                output.put("total", total);
                output.put("passedCount", passed);
                output.put("pendingCount", data.get("pendingCount") instanceof Number n ? n.intValue() : total - passed);
                // 逐条套餐要素明细（结构化行：名称/月费/合规预判/原文摘录），前端按统一 schema 渲染
                if (data.get("items") instanceof List<?> list && !list.isEmpty()) {
                    List<Map<String, Object>> detailRows = new ArrayList<>();
                    int seq = 0;
                    for (Object o : list.stream().limit(DETAIL_LIMIT).toList()) {
                        if (!(o instanceof Map<?, ?> item)) {
                            continue;
                        }
                        String name = "";
                        Object fee = null;
                        if (item.get("draft") instanceof Map<?, ?> draft) {
                            name = firstNonBlank(str(draft.get("offeringName")), str(draft.get("offerName")));
                            fee = draft.get("monthlyFee") != null ? draft.get("monthlyFee") : draft.get("fixedFeeAmount");
                        }
                        String excerpt = str(item.get("sourceExcerpt"));
                        String extra = fee != null ? "月费 " + fee + " 元" : "";
                        if (!excerpt.isBlank()) {
                            extra = extra.isBlank() ? firstChars(excerpt, 40) : extra + "｜原文：" + firstChars(excerpt, 40);
                        }
                        detailRows.add(detailRow(++seq, name.isBlank() ? "草稿" : name,
                                Boolean.TRUE.equals(item.get("compliancePass")) ? "预判通过" : "待修正", extra));
                    }
                    output.put("draft_details", detailRows);
                }
                String answer = str(data.get("nl_answer"));
                output.put("summary", !answer.isBlank() && !"null".equals(answer)
                        ? firstChars(answer, 60) : "已抽取 " + total + " 条套餐草稿");
            }
            case "rd_compliance" -> { // 合规校验（智读批量 / 智聊单草稿）
                if (data.get("items") instanceof List<?> list && !list.isEmpty()) {
                    // 批量形态：计数 + 逐条结论明细（结构化行：名称/结论/问题规则编号）
                    output.put("total", data.get("total"));
                    output.put("passedCount", data.get("passedCount"));
                    output.put("pendingCount", data.get("pendingCount"));
                    List<Map<String, Object>> detailRows = new ArrayList<>();
                    int seq = 0;
                    for (Object o : list.stream().limit(DETAIL_LIMIT).toList()) {
                        if (!(o instanceof Map<?, ?> item)) {
                            continue;
                        }
                        String name = "";
                        if (item.get("draft") instanceof Map<?, ?> draft) {
                            name = firstNonBlank(str(draft.get("offeringName")), str(draft.get("offerName")));
                        }
                        String issuePart = "";
                        if (item.get("issues") instanceof List<?> issues && !issues.isEmpty()) {
                            issuePart = issues.stream()
                                    .map(i -> i instanceof Map<?, ?> im ? str(im.get("ruleId")) : str(i))
                                    .filter(s -> !s.isBlank() && !"null".equals(s))
                                    .limit(DETAIL_LIMIT)
                                    .reduce((a, b) -> a + "、" + b)
                                    .map(ids -> "问题规则 " + ids)
                                    .orElse("");
                        }
                        detailRows.add(detailRow(++seq, name.isBlank() ? "草稿" : name,
                                Boolean.TRUE.equals(item.get("compliancePass")) ? "通过" : "未通过", issuePart));
                    }
                    output.put("compliance_details", detailRows);
                } else if (data.get("config") instanceof Map<?, ?> config) {
                    // 单草稿形态：校验结论 + 问题规则编号
                    output.put("compliancePass", data.get("compliance_pass"));
                    if (config.get("issues") instanceof List<?> issues) {
                        List<String> ruleIds = issues.stream()
                                .map(i -> i instanceof Map<?, ?> im ? str(im.get("ruleId")) : str(i))
                                .filter(s -> !s.isBlank() && !"null".equals(s))
                                .limit(DETAIL_LIMIT)
                                .toList();
                        if (!ruleIds.isEmpty()) {
                            output.put("issueRules", ruleIds);
                        }
                    }
                }
                String answer = str(data.get("nl_answer"));
                output.put("summary", !answer.isBlank() && !"null".equals(answer)
                        ? firstChars(answer, 60) : "已完成合规校验");
            }
            case "rd_workorder_create" -> { // 批量落库开单（智读/智聊链路终点）
                output.put("workOrderCount", data.get("workOrderCount") instanceof Number n ? n.intValue()
                        : data.get("workOrders") instanceof List<?> l ? l.size() : 0);
                // 逐单明细（结构化行：工单号/标题/合规标签），前端按统一 schema 渲染
                if (data.get("workOrders") instanceof List<?> list && !list.isEmpty()) {
                    List<Map<String, Object>> detailRows = new ArrayList<>();
                    int seq = 0;
                    for (Object o : list.stream().limit(DETAIL_LIMIT).toList()) {
                        if (!(o instanceof Map<?, ?> wo)) {
                            continue;
                        }
                        String woId = firstNonBlank(str(wo.get("workOrderId")), str(wo.get("id")));
                        String title = str(wo.get("title"));
                        detailRows.add(detailRow(++seq, title.isBlank() ? "配置工单" : title,
                                Boolean.TRUE.equals(wo.get("compliancePass")) ? "已创建" : "稽核未通过",
                                woId.isBlank() ? "" : "工单号 " + woId));
                    }
                    output.put("work_order_details", detailRows);
                }
                if (data.get("workOrderFailures") instanceof List<?> failures && !failures.isEmpty()) {
                    output.put("workOrderFailures", failures.stream().limit(DETAIL_LIMIT).toList());
                }
                String answer = str(data.get("nl_answer"));
                output.put("summary", !answer.isBlank() && !"null".equals(answer)
                        ? firstChars(answer, 60) : "开单环节完成");
            }
            default -> {
                // rd 原子工具等未登记分支：全量摘要（summary 取 nl_answer 首段）；
                // 无摘要也保留 from_step 承接链（避免步骤间承接行断裂），输出给缺省文案
                String answer = str(data.get("nl_answer"));
                output.put("summary", answer.isBlank() || "null".equals(answer)
                        ? "已按手册完成本环节处理" : firstChars(answer, 60));
            }
        }
        // 手册步骤统一携带分支标签（与 plan/generate 步骤结构对齐，规范 §3.1）
        output.putIfAbsent("branch_taken", "按手册执行");
        return io(input, output);
    }

    /** 统一明细行 schema（规范 §3.3）：{seq, title, state?, extra?}。 */
    private static Map<String, Object> detailRow(int seq, String title, String state, String extra) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", seq);
        row.put("title", title);
        if (state != null && !state.isBlank()) {
            row.put("state", state);
        }
        if (extra != null && !extra.isBlank()) {
            row.put("extra", extra);
        }
        return row;
    }

    /**
     * 手册步骤（sop-step-N）思考快照（规范 §5.3 回放同构）：
     * 与实时 onStepComplete 落地的思考步骤同构（id/title/content/goal/input/output/trace），
     * 供 persistTurn 把手册链路的 sop 步骤写入 reasoning_full —— 历史回放不再缺失手册环节。
     *
     * @param stepIdx 手册步骤序号（0 起）
     * @param sopStep 手册步骤结构 {do, how, tool}（parseSopSteps 产出）
     * @param result  该步骤对应的真实工具执行结果
     * @param trace   该步骤的留痕（toolTraceOf 产出）
     * @param io      该步骤的输入/输出视图（stepIoOf 产出）
     */
    public static Map<String, Object> sopStepThinking(int stepIdx, Map<String, Object> sopStep,
                                                      ExecutionResult result,
                                                      List<Map<String, Object>> trace,
                                                      Map<String, Object> io) {
        boolean success = result.isSuccess();
        String doText = str(sopStep.getOrDefault("do", result.getToolName()));
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "sop-step-" + stepIdx);
        step.put("type", "thinking");
        step.put("title", "第" + (stepIdx + 1) + "步 " + doText);
        step.put("content", success ? "已完成：" + doText : "执行失败：" + doText);
        step.put("status", success ? "done" : "error");
        step.put("goal", str(sopStep.getOrDefault("tool", "")));
        if (io != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> phaseInput = (Map<String, Object>) io.get("input");
            @SuppressWarnings("unchecked")
            Map<String, Object> phaseOutput = (Map<String, Object>) io.get("output");
            if (success) {
                if (phaseInput != null && !phaseInput.isEmpty()) {
                    step.put("input", phaseInput);
                }
                if (phaseOutput != null && !phaseOutput.isEmpty()) {
                    step.put("output", phaseOutput);
                }
            } else {
                step.put("output", Map.of("summary", "执行失败：" + str(result.getErrorMessage())));
            }
        }
        if (!step.containsKey("output")) {
            step.put("output", Map.of("summary", "已按手册完成本环节处理"));
        }
        if (trace != null && !trace.isEmpty()) {
            step.put("trace", trace);
        }
        return step;
    }

    private static Map<String, Object> io(Map<String, Object> input, Map<String, Object> output) {
        Map<String, Object> io = new LinkedHashMap<>();
        io.put("input", input);
        io.put("output", output);
        return io;
    }

    /** 原文摘录兜底展示：压缩空白后截取前 max 字符。 */
    private static String firstChars(String text, int max) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** 取首个非空字符串。 */
    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /** 明细列表上限（展示保护，防长列表刷屏）。 */
    private static final int DETAIL_LIMIT = 8;

    /** Document IR 概要（结构化文档解析产物）：块计数 + 表格标题/列数快照。 */
    private record DocumentIrSummary(int tableCount, int paragraphCount, int headingCount,
                                     List<String> tableTitles, List<Integer> tableColCounts,
                                     String structureLabel) {
    }

    /**
     * 从工具输出的 document 键（DocumentIR.toMap 形态）提取解析结构概要：
     * 「N 个工作表/表格 · M 段落 · K 字」式结构标签 + 表格标题明细来源。
     */
    private static DocumentIrSummary documentIrSummary(Object documentObj) {
        if (!(documentObj instanceof Map<?, ?> doc)) {
            return null;
        }
        if (!(doc.get("blocks") instanceof List<?> blocks) || blocks.isEmpty()) {
            return null;
        }
        int tables = 0;
        int paragraphs = 0;
        int headings = 0;
        List<String> tableTitles = new ArrayList<>();
        List<Integer> tableCols = new ArrayList<>();
        for (Object o : blocks) {
            if (!(o instanceof Map<?, ?> b)) {
                continue;
            }
            String type = str(b.get("type"));
            switch (type) {
                case "table" -> {
                    tables++;
                    String sheet = str(b.get("sheet_name"));
                    String title = str(b.get("title"));
                    tableTitles.add(firstNonBlank(sheet.isBlank() ? "" : "工作表：" + sheet,
                            title.isBlank() ? "" : title)
                            .isBlank() ? "表格 " + tables : firstNonBlank(
                            sheet.isBlank() ? "" : "工作表：" + sheet, title));
                    int cols = b.get("headers") instanceof List<?> h ? h.size()
                            : b.get("rows") instanceof List<?> r && !r.isEmpty() && r.get(0) instanceof List<?> row
                            ? row.size() : 0;
                    tableCols.add(cols);
                }
                case "heading" -> headings++;
                case "paragraph" -> paragraphs++;
                default -> {
                }
            }
        }
        if (tables == 0 && paragraphs == 0 && headings == 0) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (tables > 0) {
            parts.add(tables + " 个表格");
        }
        if (headings > 0) {
            parts.add(headings + " 个标题");
        }
        if (paragraphs > 0) {
            parts.add(paragraphs + " 段落");
        }
        return new DocumentIrSummary(tables, paragraphs, headings, tableTitles, tableCols,
                parts.isEmpty() ? "" : "，结构化 " + String.join(" · ", parts));
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
