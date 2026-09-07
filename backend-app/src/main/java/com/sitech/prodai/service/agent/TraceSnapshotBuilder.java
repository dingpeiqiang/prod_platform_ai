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
     * 智读文件解析（rd_file_parse）过程留痕：文档解析引擎 → 套餐抽取引擎 → 合规校验 → 批量开单，
     * 让思考时间线展示完整执行链路（前端 STAGE_PREFIX 中非 llm/ontology 阶段统一加「校验」前缀，
     * 此处 stage 用空串走默认前缀即可，无需改前端）。
     */
    public static List<Map<String, Object>> rdFileParseTrace(ExecutionResult result) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return null;
        }
        Map<String, Object> data = result.getData();
        List<Map<String, Object>> trace = new ArrayList<>();

        // 环节① 文档解析（解析引擎 + trace_id 可回溯）
        Object parseEngine = data.get("parseEngine");
        if (parseEngine != null && !str(parseEngine).isBlank()) {
            String engineDesc = switch (str(parseEngine)) {
                case "docx" -> "解析 docx（OOXML）文档，抽取段落与表格文本";
                case "xlsx" -> "解析 xlsx 工作表，按行列抽取单元格";
                case "pdf" -> "PDFBox 解析 PDF 文档";
                case "pdfbox" -> "PDFBox 解析 PDF 文档";
                case "poi" -> "Apache POI 解析 Office 文档";
                case "tika" -> "Apache Tika 解析文档";
                case "csv" -> "解析 CSV，自动识别分隔符";
                case "markdown" -> "按 Markdown 纯文本读取";
                case "text" -> "按纯文本读取文档内容";
                default -> "解析引擎 " + parseEngine;
            };
            Object chars = data.get("extractedChars");
            if (chars instanceof Number n && n.longValue() > 0) {
                engineDesc += "，抽取 " + n + " 字符";
            }
            trace.add(parsePhase(engineDesc));
        }
        Object traceId = data.get("trace_id");
        if (traceId != null && !str(traceId).isBlank()) {
            trace.add(parsePhase("解析留痕已记录（trace_id=" + str(traceId) + "）"));
        }

        // 环节② 套餐抽取（抽取引擎 + 命中规则）
        Object extractEngine = data.get("extractEngine");
        Object itemCount = data.get("total") != null ? data.get("total")
                : (data.get("items") instanceof List<?> l ? l.size() : null);
        if (extractEngine != null && !str(extractEngine).isBlank()) {
            String engineDesc = switch (str(extractEngine)) {
                case "llm" -> "大模型按槽位约束抽取套餐字段";
                case "regex" -> "正则规则切分文档并抽取槽位";
                case "regex-fast" -> "正则快速抽取（关键槽位齐备，跳过 LLM）";
                case "regex-fallback" -> "大模型抽取失败，回退正则规则";
                default -> "抽取引擎 " + extractEngine;
            };
            if (itemCount instanceof Number n) {
                engineDesc += "，抽取 " + n + " 条套餐";
            }
            trace.add(parsePhase(engineDesc));
        }
        if (data.get("appliedRules") instanceof List<?> rules && !rules.isEmpty()) {
            trace.add(parsePhase("命中规则：" + rules.stream().map(String::valueOf)
                    .reduce((a, b) -> a + "、" + b).orElse("")));
        }

        // 环节③ 合规校验
        Object total = data.get("total");
        Object passed = data.get("passedCount");
        if (total instanceof Number t && passed instanceof Number p) {
            trace.add(parsePhase("合规校验：共 " + t + " 条草稿，通过 " + p + " 条，待修正 " + (t.intValue() - p.intValue()) + " 条"));
        }

        // 环节④ 批量开单
        Object woCount = data.get("workOrderCount");
        if (woCount instanceof Number w && w.intValue() > 0) {
            StringBuilder sb = new StringBuilder("已按草稿逐条创建配置工单，共 ").append(w).append(" 单");
            if (data.get("workOrderFailures") instanceof List<?> fails && !fails.isEmpty()) {
                sb.append("（失败 ").append(fails.size()).append(" 条）");
            }
            trace.add(parsePhase(sb.toString()));
        }

        return trace.isEmpty() ? null : trace;
    }

    /**
     * 按环节序号取智读解析留痕切片（手册步骤专用）：rd_file_parse 是一次执行、四环节串行
     * （① 文档解析 ② 套餐抽取 ③ 合规校验 ④ 批量开单），手册四个步骤各自只贴自己对应
     * 环节的留痕，避免每一步都重复完整链路。
     *
     * @param phaseIdx 环节序号 0..3，越界或链路无该环节产出时返回 null（前端自然降级为仅有 SOP 条目）
     */
    public static List<Map<String, Object>> rdFileParseTracePhase(ExecutionResult result, int phaseIdx) {
        List<Map<String, Object>> full = rdFileParseTrace(result);
        if (full == null || phaseIdx < 0) {
            return null;
        }
        // 四环节条目固定按序追加：[解析引擎, trace_id?, 抽取引擎, 命中规则?, 合规?, 开单?]，
        // 其中 trace_id/命中规则/合规/开单均为可选，需先定位各环节起始下标再切片。
        int extractStart = -1;
        int complianceIdx = -1;
        int createIdx = -1;
        for (int i = 0; i < full.size(); i++) {
            String message = str(full.get(i).get("message"));
            if (extractStart < 0 && message.startsWith("抽取引擎") || message.startsWith("大模型按槽位")
                    || message.startsWith("正则")) {
                extractStart = i;
            }
            if (message.startsWith("合规校验：")) {
                complianceIdx = i;
            }
            if (message.startsWith("已按草稿逐条创建配置工单")) {
                createIdx = i;
            }
        }
        int start;
        int end;
        switch (phaseIdx) {
            case 0 -> { // 文档解析
                start = 0;
                end = extractStart < 0 ? full.size() : extractStart;
            }
            case 1 -> { // 套餐抽取
                if (extractStart < 0) {
                    return null;
                }
                start = extractStart;
                end = complianceIdx < 0 ? full.size() : complianceIdx;
            }
            case 2 -> { // 合规校验
                if (complianceIdx < 0) {
                    return null;
                }
                start = complianceIdx;
                end = createIdx < 0 ? full.size() : createIdx;
            }
            case 3 -> { // 批量开单
                if (createIdx < 0) {
                    return null;
                }
                start = createIdx;
                end = full.size();
            }
            default -> {
                return null;
            }
        }
        return start >= end ? null : new ArrayList<>(full.subList(start, end));
    }

    /** 构造智读解析阶段条目（stage 留空，前端走默认「校验」前缀）。 */
    private static Map<String, Object> parsePhase(String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("stage", "parse");
        item.put("phase", "文档解析");
        item.put("message", message);
        return item;
    }

    /**
     * 手册步骤的差异化「输入/输出」：rd_file_parse 一次执行产出完整结果，但手册四步
     * 各自对应不同环节——输入/输出必须按环节取自真实数据，不能每步都贴同一份全量摘要。
     * <p>
     * 数据流视角（与 ConfigDocImportService 流水线一致）：
     * <ul>
     *   <li>环节① 文档解析：输入=文档标识/名称，输出=解析引擎+抽取字符数+trace_id</li>
     *   <li>环节② 套餐抽取：输入=解析出的文本，输出=抽取引擎+抽取条数+逐条草稿明细（draft_details）</li>
     *   <li>环节③ 合规校验：输入=全部草稿，输出=通过/待修正条数+逐条合规明细（compliance_details）</li>
     *   <li>环节④ 批量开单：输入=合规通过草稿，输出=工单数+逐条工单明细（work_order_details）+失败明细</li>
     * </ul>
     * input 带 from_step 供前端渲染「承接」行（上一环节产出）；
     * 明细列表均取自真实执行产物（items[].draft / workOrderId / issues），上限保护防长文档刷屏。
     */
    public static Map<String, Object> rdFileParsePhaseIo(ExecutionResult result, int phaseIdx) {
        if (result == null || !result.isSuccess() || result.getData() == null
                || phaseIdx < 0 || phaseIdx > 3) {
            return Map.of();
        }
        Map<String, Object> data = result.getData();
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> output = new LinkedHashMap<>();
        switch (phaseIdx) {
            case 0 -> { // 文档解析
                Object fileNames = data.get("fileNames");
                if (fileNames instanceof List<?> l && !l.isEmpty()) {
                    input.put("file_name", l.stream().map(String::valueOf)
                            .reduce((a, b) -> a + "、" + b).orElse(""));
                } else {
                    input.put("file_name", "上传的方案文档");
                }
                output.put("summary", phaseSummary(data, "parse"));
            }
            case 1 -> { // 套餐抽取
                input.put("from_step", "sop-step-0");
                input.put("requirement", "上一步解析出的文档全文");
                output.put("extractEngine", data.get("extractEngine"));
                if (data.get("total") instanceof Number n) {
                    output.put("total", n.intValue());
                }
                // 抽取明细：逐条「#序号 套餐名（月费）」，取自真实草稿
                List<String> draftLines = draftOutlineList(data);
                if (!draftLines.isEmpty()) {
                    output.put("draft_details", draftLines);
                }
                output.put("summary", phaseSummary(data, "extract"));
            }
            case 2 -> { // 合规校验
                input.put("from_step", "sop-step-1");
                Object total = data.get("total");
                input.put("requirement", total instanceof Number t
                        ? "上一步抽取的 " + t + " 条套餐草稿"
                        : "上一步抽取的全部套餐草稿");
                if (data.get("total") instanceof Number t) {
                    output.put("total", t.intValue());
                }
                if (data.get("passedCount") instanceof Number p) {
                    output.put("passedCount", p.intValue());
                }
                // 合规明细：逐条「#序号 套餐名：通过/待修正（R-C 编号）」
                List<String> complianceLines = complianceDetailList(data);
                if (!complianceLines.isEmpty()) {
                    output.put("compliance_details", complianceLines);
                }
                output.put("summary", phaseSummary(data, "compliance"));
            }
            case 3 -> { // 批量开单
                input.put("from_step", "sop-step-2");
                Object passed = data.get("passedCount");
                input.put("requirement", passed instanceof Number p && p.intValue() > 0
                        ? "上一步校验通过的 " + p + " 条草稿（待修正的同样开单，挂问题标签）"
                        : "上一步校验的全部草稿");
                if (data.get("workOrderCount") instanceof Number w) {
                    output.put("workOrderCount", w.intValue());
                }
                if (data.get("workOrderFailures") instanceof List<?> fails && !fails.isEmpty()) {
                    output.put("failureCount", fails.size());
                }
                // 开单明细：逐条「套餐名 → 工单号」，失败条目原样透出
                List<String> orderLines = workOrderDetailList(data);
                if (!orderLines.isEmpty()) {
                    output.put("work_order_details", orderLines);
                }
                output.put("summary", phaseSummary(data, "create"));
            }
            default -> {
                return Map.of();
            }
        }
        Map<String, Object> io = new LinkedHashMap<>();
        io.put("input", input);
        io.put("output", output);
        return io;
    }

    /** 手册环节「输出」摘要：各环节讲各自那一句话（与 trace 明细互补，不重复全量 nl_answer）。 */
    private static String phaseSummary(Map<String, Object> data, String phase) {
        return switch (phase) {
            case "parse" -> {
                Object engine = data.get("parseEngine");
                Object chars = data.get("extractedChars");
                StringBuilder sb = new StringBuilder("文档已解析");
                if (engine != null) {
                    sb.append("（引擎：").append(engine).append("）");
                }
                if (chars instanceof Number n && n.longValue() > 0) {
                    sb.append("，抽取 ").append(n).append(" 字符");
                }
                yield sb.toString();
            }
            case "extract" -> {
                Object engine = data.get("extractEngine");
                Object total = data.get("total");
                StringBuilder sb = new StringBuilder("已抽取");
                if (total instanceof Number n) {
                    sb.append(" ").append(n).append(" 条套餐草稿");
                }
                if (engine != null) {
                    sb.append("（抽取引擎：").append(engine).append("）");
                }
                yield sb.toString();
            }
            case "compliance" -> {
                Object total = data.get("total");
                Object passed = data.get("passedCount");
                yield total instanceof Number t && passed instanceof Number p
                        ? "合规校验完成：通过 " + p + " 条，待修正 " + (t.intValue() - p.intValue()) + " 条"
                        : "合规校验完成";
            }
            case "create" -> {
                Object woCount = data.get("workOrderCount");
                Object fails = data.get("workOrderFailures");
                StringBuilder sb = new StringBuilder("已批量创建配置工单");
                if (woCount instanceof Number w) {
                    sb.append(" ").append(w).append(" 单");
                }
                if (fails instanceof List<?> l && !l.isEmpty()) {
                    sb.append("（失败 ").append(l.size()).append(" 条）");
                }
                yield sb.toString();
            }
            default -> "执行完成";
        };
    }

    /** 上限保护：明细列表最多下发条数（超长文档场景防止展开区被明细淹没）。 */
    private static final int DETAIL_LIMIT = 8;

    /**
     * 抽取明细：逐条「#序号 套餐名（月费 XX 元）」，取自 items[].draft 真实草稿字段，
     * 无草稿名时回退原文摘录前段。空列表返回 empty（调用方不放下发键）。
     */
    private static List<String> draftOutlineList(Map<String, Object> data) {
        List<String> lines = new ArrayList<>();
        if (!(data.get("items") instanceof List<?> items)) {
            return lines;
        }
        for (Object o : items) {
            if (lines.size() >= DETAIL_LIMIT) {
                break;
            }
            if (!(o instanceof Map<?, ?> item)) {
                continue;
            }
            int idx = item.get("index") instanceof Number n ? n.intValue() : lines.size() + 1;
            String name = draftField(item, "offeringName", "offerName");
            if (name.isBlank()) {
                name = firstChars(str(item.get("sourceExcerpt")), 18);
            }
            if (name.isBlank()) {
                continue;
            }
            String fee = draftField(item, "monthlyFee", "fixedFeeAmount");
            lines.add("#" + idx + " " + name + (fee.isBlank() ? "" : "（月费 " + fee + " 元）"));
        }
        return lines;
    }

    /**
     * 合规明细：逐条「#序号 套餐名：通过/待修正（R-C 编号…）」，issues 取规则编号去重。
     */
    private static List<String> complianceDetailList(Map<String, Object> data) {
        List<String> lines = new ArrayList<>();
        if (!(data.get("items") instanceof List<?> items)) {
            return lines;
        }
        for (Object o : items) {
            if (lines.size() >= DETAIL_LIMIT) {
                break;
            }
            if (!(o instanceof Map<?, ?> item)) {
                continue;
            }
            int idx = item.get("index") instanceof Number n ? n.intValue() : lines.size() + 1;
            String name = draftField(item, "offeringName", "offerName");
            if (name.isBlank()) {
                name = firstChars(str(item.get("sourceExcerpt")), 18);
            }
            boolean pass = Boolean.TRUE.equals(item.get("compliancePass"));
            StringBuilder sb = new StringBuilder("#").append(idx).append(" ")
                    .append(name.isBlank() ? "未命名草稿" : name).append("：").append(pass ? "通过" : "待修正");
            if (!pass) {
                List<String> ruleIds = issueRuleIds(item.get("issues"));
                if (!ruleIds.isEmpty()) {
                    sb.append("（").append(String.join("、", ruleIds)).append("）");
                }
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    /**
     * 开单明细：优先逐条「套餐名 → 工单号」（items[].workOrderId 已回填），
     * 失败条目（workOrderFailures）原样透出。两类全空时返回 empty。
     */
    private static List<String> workOrderDetailList(Map<String, Object> data) {
        List<String> lines = new ArrayList<>();
        if (data.get("items") instanceof List<?> items) {
            for (Object o : items) {
                if (lines.size() >= DETAIL_LIMIT) {
                    break;
                }
                if (!(o instanceof Map<?, ?> item) || item.get("workOrderId") == null) {
                    continue;
                }
                String name = draftField(item, "offeringName", "offerName");
                if (name.isBlank()) {
                    name = firstChars(str(item.get("sourceExcerpt")), 18);
                }
                lines.add((name.isBlank() ? "未命名草稿" : name) + " → 工单 " + item.get("workOrderId"));
            }
        }
        if (data.get("workOrderFailures") instanceof List<?> fails) {
            for (Object f : fails) {
                if (lines.size() >= DETAIL_LIMIT + 4) {
                    break;
                }
                if (f != null && !str(f).isBlank()) {
                    lines.add("失败：" + f);
                }
            }
        }
        return lines;
    }

    /** 从 items[].draft 中取首个非空字段。 */
    private static String draftField(Map<?, ?> item, String... keys) {
        if (item.get("draft") instanceof Map<?, ?> draft) {
            for (String k : keys) {
                String v = str(draft.get(k));
                if (!v.isBlank() && !"null".equals(v)) {
                    return v;
                }
            }
        }
        return "";
    }

    /** 稽核问题 → 规则编号列表（去重，兼容 {ruleId|ruleCode} 对象与纯字符串两种形态）。 */
    private static List<String> issueRuleIds(Object issues) {
        List<String> ruleIds = new ArrayList<>();
        if (!(issues instanceof List<?> list)) {
            return ruleIds;
        }
        for (Object o : list) {
            String ruleId = null;
            if (o instanceof Map<?, ?> issue) {
                ruleId = firstNonBlank(str(issue.get("ruleId")), str(issue.get("ruleCode")));
            } else if (o != null) {
                ruleId = str(o);
            }
            if (ruleId != null && !ruleId.isBlank() && !"null".equals(ruleId) && !ruleIds.contains(ruleId)) {
                ruleIds.add(ruleId);
            }
        }
        return ruleIds;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /**
     * 手册步骤的差异化「输入/输出」：rd_config_chat 一次执行产出完整结果，但手册四步
     * 各自对应不同环节——输入/输出必须按环节取自真实数据（与 rdFileParsePhaseIo 同构）。
     * <p>
     * 数据流视角（与 RdConfigChatTool.execute 流水线一致）：
     * <ul>
     *   <li>环节① 品类识别：输入=话术，输出=识别品类（含兜底说明）</li>
     *   <li>环节② 草稿生成：输入=品类，输出=草稿要素明细（名称/月费/宽带/客群/渠道）</li>
     *   <li>环节③ 合规校验：输入=草稿，输出=通过/待修正+问题规则编号</li>
     *   <li>环节④ 落库开单：输入=草稿，输出=工单号（落库失败不开单）</li>
     * </ul>
     */
    public static Map<String, Object> rdConfigChatPhaseIo(ExecutionResult result, int phaseIdx) {
        if (result == null || !result.isSuccess() || result.getData() == null
                || phaseIdx < 0 || phaseIdx > 3) {
            return Map.of();
        }
        Map<String, Object> data = result.getData();
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> output = new LinkedHashMap<>();
        Map<?, ?> draft = data.get("draft") instanceof Map<?, ?> d ? d : Map.of();
        String name = firstNonBlank(draftField(Map.of("draft", draft), "offeringName", "offerName"), "");
        String fee = draftField(Map.of("draft", draft), "monthlyFee", "fixedFeeAmount");
        switch (phaseIdx) {
            case 0 -> { // 品类识别
                output.put("summary", "已识别产品品类（显式品类优先，未提供时由模板 matchers 兜底识别）");
            }
            case 1 -> { // 草稿生成
                input.put("from_step", "sop-step-0");
                input.put("requirement", "上一步识别的产品品类");
                // 草稿要素明细：名称/月费/宽带/客群/渠道逐项透出（与 normalize 摘要同源）
                List<String> draftLines = new ArrayList<>();
                Map<?, ?> d = draft;
                for (String key : new String[]{"offeringName", "offerName", "monthlyFee", "fixedFeeAmount",
                        "includeBroadband", "downstreamBandwidth", "targetUser", "channelScope", "bizScenario"}) {
                    String v = str(d.get(key));
                    if (!v.isBlank() && !"null".equals(v) && draftLines.size() < DETAIL_LIMIT) {
                        draftLines.add(key + "=" + v);
                    }
                }
                if (!draftLines.isEmpty()) {
                    output.put("draft_details", draftLines);
                }
                output.put("summary", name.isBlank()
                        ? "已生成配置草稿" + (fee.isBlank() ? "" : "（月费 " + fee + " 元）")
                        : "已生成配置草稿「" + name + "」");
            }
            case 2 -> { // 合规校验
                input.put("from_step", "sop-step-1");
                input.put("requirement", name.isBlank() ? "上一步生成的配置草稿" : "草稿「" + name + "」");
                boolean pass = Boolean.TRUE.equals(data.get("compliancePass"));
                output.put("compliancePass", pass);
                List<String> ruleIds = issueRuleIds(data.get("issues"));
                if (!pass && !ruleIds.isEmpty()) {
                    output.put("compliance_details", List.of("待修正：" + String.join("、", ruleIds)));
                }
                output.put("summary", pass ? "合规校验通过" : "合规校验未通过（补名后已重跑刷新结论）");
            }
            case 3 -> { // 落库开单
                input.put("from_step", "sop-step-2");
                input.put("requirement", name.isBlank() ? "合规结论生效的草稿" : "草稿「" + name + "」");
                Object woId = data.get("workOrderId");
                if (woId != null && !str(woId).isBlank()) {
                    output.put("work_order_details", List.of((name.isBlank() ? "配置草稿" : name) + " → 工单 " + woId));
                    output.put("workOrderId", str(woId));
                }
                output.put("summary", woId == null || str(woId).isBlank()
                        ? "草稿落库未成功，未开单（避免孤儿工单）"
                        : "已落库并创建配置工单");
            }
            default -> {
                return Map.of();
            }
        }
        Map<String, Object> io = new LinkedHashMap<>();
        io.put("input", input);
        io.put("output", output);
        return io;
    }

    /**
     * 手册步骤的差异化「输入/输出」：rd_config_discover 一次执行产出检索结果，手册三步
     * 对应 需求解析 → 检索 → 整理 三个环节（只读：无落库无开单环节）。
     * <ul>
     *   <li>环节① 需求解析：输入=话术，输出=检索要素说明</li>
     *   <li>环节② 历史检索：输入=检索要素，输出=命中数+命中明细（名称/月费）</li>
     *   <li>环节③ 清单整理：输入=命中结果，输出=可复用清单（复制为草稿桥接智聊）</li>
     * </ul>
     */
    public static Map<String, Object> rdDiscoverPhaseIo(ExecutionResult result, int phaseIdx) {
        if (result == null || !result.isSuccess() || result.getData() == null
                || phaseIdx < 0 || phaseIdx > 2) {
            return Map.of();
        }
        Map<String, Object> data = result.getData();
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> output = new LinkedHashMap<>();
        int hit = data.get("entity_ids") instanceof List<?> ids ? ids.size()
                : (data.get("items") instanceof List<?> items ? items.size() : 0);
        switch (phaseIdx) {
            case 0 -> { // 需求解析
                output.put("summary", "已从话术提取检索要素（品类/月费/客群等），语义检索为主、关键词兜底");
            }
            case 1 -> { // 历史检索
                input.put("from_step", "sop-step-0");
                input.put("requirement", "上一步解析出的检索要素");
                output.put("hitCount", hit);
                // 命中明细：逐条「#序号 方案名（月费）」，取自真实检索结果
                List<String> hitLines = new ArrayList<>();
                if (data.get("items") instanceof List<?> items) {
                    for (int i = 0; i < items.size() && hitLines.size() < DETAIL_LIMIT; i++) {
                        Object o = items.get(i);
                        if (!(o instanceof Map<?, ?> item)) {
                            continue;
                        }
                        String itemName = firstNonBlank(
                                str(firstNonEmpty(item.get("offeringName"), item.get("offerName"), item.get("name"))),
                                firstChars(str(firstNonEmpty(item.get("summary"), item.get("description"))), 18));
                        if (itemName.isBlank()) {
                            continue;
                        }
                        String itemFee = str(firstNonEmpty(item.get("monthlyFee"), item.get("fixedFeeAmount")));
                        hitLines.add("#" + (i + 1) + " " + itemName + (itemFee.isBlank() ? "" : "（月费 " + itemFee + " 元）"));
                    }
                }
                if (!hitLines.isEmpty()) {
                    output.put("hit_details", hitLines);
                }
                output.put("summary", hit > 0 ? "检索到 " + hit + " 条历史配置方案" : "未找到匹配的历史配置");
            }
            case 2 -> { // 清单整理
                input.put("from_step", "sop-step-1");
                input.put("requirement", hit > 0 ? "上一步检索到的 " + hit + " 条方案" : "上一步的检索结论");
                output.put("hitCount", hit);
                output.put("summary", hit > 0
                        ? hit + " 条命中方案已按相关性整理，可点击条目复制为草稿到智聊继续编辑"
                        : "无命中，建议放宽品类/月费条件");
            }
            default -> {
                return Map.of();
            }
        }
        Map<String, Object> io = new LinkedHashMap<>();
        io.put("input", input);
        io.put("output", output);
        return io;
    }

    /** 取首个非空字符串值（跨 Map 取值辅助，兼容单层键）。 */
    private static Object firstNonEmpty(Object... values) {
        for (Object v : values) {
            if (v != null && !str(v).isBlank() && !"null".equals(str(v))) {
                return v;
            }
        }
        return "";
    }

    /** 原文摘录兜底展示：压缩空白后截取前 max 字符。 */
    private static String firstChars(String text, int max) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
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
