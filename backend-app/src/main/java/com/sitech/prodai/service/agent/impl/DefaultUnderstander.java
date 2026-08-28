package com.sitech.prodai.service.agent.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.Understander;
import com.sitech.prodai.service.agent.model.ExecStep;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 默认理解层实现。
 * <p>
 * 理解过程：
 * 1. 意图识别 → 用户想做什么
 * 2. 实体抽取 → 涉及哪些实体
 * 3. 查询计划生成 → 需要调用哪些工具
 * <p>
 * 参数完整性（设计文档 3.4 节）：校验必填参数，缺失时优先复用
 * context 缓存 / 已澄清参数，仍缺失则生成 CLARIFY 澄清计划。
 */
@Component
public class DefaultUnderstander implements Understander {

    private static final Logger log = LoggerFactory.getLogger(DefaultUnderstander.class);

    /** 大模型空响应重试上限与间隔（DeepSeek 推理模型偶发返回空 choices） */
    private static final int MAX_TRANSLATE_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000L;

    private final LlmService llmService;
    private final Map<String, AgentTool> toolMap;

    public DefaultUnderstander(LlmService llmService, List<AgentTool> tools) {
        this.llmService = llmService;
        this.toolMap = new LinkedHashMap<>();
        if (tools != null) {
            for (AgentTool tool : tools) {
                this.toolMap.put(tool.getName(), tool);
            }
        }
    }

    @Override
    public QueryPlan understand(String question, SessionContext context) {
        List<QueryPlan> plans = understandAll(question, context);
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        if (plans.size() > 1) {
            // 单计划调用方（如非流式 process）只取首个子计划以兼容现状；
            // 多意图完整处理见 processStream（按 understandAll 逐条执行）。
            log.info("[DefaultUnderstander] 混合意图拆分为 {} 个子计划，understand 仅返回首个: {}",
                    plans.size(), plans.get(0).getIntent());
        }
        return plans.get(0);
    }

    @Override
    public List<QueryPlan> understandAll(String question, SessionContext context) {
        if (question == null || question.isBlank()) {
            return List.of(chatPlan(question));
        }
        return understandPlans(question, context);
    }

    /**
     * 理解层完整流水线：重试调用大模型 → 解析为（多）查询计划 → 逐计划参数校验。
     * <p>
     * 混合意图（LLM 用 {@code |} 拼接多意图/动作）在 {@link #parseLlmResults} 中拆分
     * 为多个子计划；任一子计划缺失必填参数时整体转入澄清（澄清前不执行任何子意图）。
     */
    private List<QueryPlan> understandPlans(String question, SessionContext context) {
        // 理解层仅依赖大模型：大模型不可用时直接抛错，不做任何关键词/通用对话兜底。
        // DeepSeek 推理模型偶发返回空 choices，此处做有限重试，仍失败则终止翻译链。
        String llmResult = null;
        Exception lastError = null;
        boolean rdScene = isRdScene(context);
        for (int attempt = 1; attempt <= MAX_TRANSLATE_ATTEMPTS; attempt++) {
            try {
                List<Map<String, String>> history = toHistory(context);
                llmResult = llmService.completeMessages(buildSystemPrompt(rdScene), history, question);
            } catch (Exception e) {
                lastError = e;
                log.warn("[DefaultUnderstander] 大模型调用失败（第 {} 次尝试）: {}", attempt, e.getMessage());
            }
            if (llmResult != null && !llmResult.isBlank()) {
                log.info("[DefaultUnderstander] 大模型原始输出（第 {} 次尝试）: {}", attempt, llmResult);
                break;
            }
            log.warn("[DefaultUnderstander] 大模型返回为空（第 {} 次尝试）", attempt);
            llmResult = null;
            if (attempt < MAX_TRANSLATE_ATTEMPTS) {
                sleep(RETRY_DELAY_MS);
            }
        }

        if (llmResult == null && lastError != null) {
            throw new IllegalStateException("大模型不可用，理解层调用失败: " + lastError.getMessage(), lastError);
        }
        if (llmResult == null || llmResult.isBlank()) {
            log.error("[DefaultUnderstander] 大模型返回为空，翻译链终止");
            throw new IllegalStateException("大模型返回为空，无法理解用户需求");
        }

        List<QueryPlan> parsed = parseLlmResults(llmResult, question, rdScene);
        if (parsed == null || parsed.isEmpty()) {
            log.error("[DefaultUnderstander] 大模型输出无法解析为查询计划，翻译链终止");
            throw new IllegalStateException("大模型输出无法解析为查询计划，请重试或检查模型配置");
        }

        // 参数完整性校验 — 缺失必填参数时转入澄清分支（而非直接报错）
        List<QueryPlan> validated = new ArrayList<>();
        QueryPlan firstClarify = null;
        for (QueryPlan plan : parsed) {
            QueryPlan v = validateParams(plan, context, question);
            if (QueryPlan.INTENT_CLARIFY.equals(v.getIntent())) {
                // 任一子计划需澄清 → 整体转入澄清（等待补参后整轮重来），避免部分执行
                if (firstClarify == null) {
                    firstClarify = v;
                }
            } else {
                validated.add(v);
            }
        }
        if (firstClarify != null) {
            return List.of(firstClarify);
        }
        return validated;
    }

    /**
     * 参数完整性校验（设计文档 3.4 节）：
     * 校验优先级：params 已填 → context.cachedEvidence / resolvedParams 缓存 → defaultValue。
     * 仍缺失的必填参数 → 生成 CLARIFY 意图；超过澄清上限则按缺省值继续（防死循环）。
     */
    private QueryPlan validateParams(QueryPlan plan, SessionContext context, String question) {
        List<String> tools = plan.getTools();
        if (tools == null || tools.isEmpty()) {
            return plan;
        }

        List<String> missing = new ArrayList<>();
        for (String toolName : tools) {
            AgentTool tool = toolMap.get(toolName);
            if (tool == null) {
                continue;
            }
            for (ToolParam param : tool.getParams()) {
                if (!param.isRequired()) {
                    continue;
                }
                if (hasValue(plan.getParams().get(param.getName()))) {
                    continue;
                }
                // 缓存优先级：resolvedParams（用户已补齐） > cachedEvidence（上轮证据）
                Object cached = context != null ? context.getResolvedParams().get(param.getName()) : null;
                if (!hasValue(cached) && context != null) {
                    cached = context.getCachedEvidence().get(param.getName());
                }
                if (hasValue(cached)) {
                    plan.getParams().put(param.getName(), cached);
                    continue;
                }
                // 有缺省值则不阻塞
                if (param.getDefaultValue() != null && !param.getDefaultValue().isBlank()) {
                    plan.getParams().put(param.getName(), param.getDefaultValue());
                    continue;
                }
                if (!missing.contains(param.getName())) {
                    missing.add(param.getName());
                }
            }
        }

        if (missing.isEmpty()) {
            if (context != null) {
                context.resetClarifyRounds();
            }
            return plan;
        }

        // 超过澄清上限：按缺省值继续（缺省缺失时放弃该参数），防死循环
        if (context != null && context.exceedClarifyLimit()) {
            log.info("[DefaultUnderstander] 澄清轮次已达上限，按缺省继续: {}", missing);
            context.resetClarifyRounds();
            return plan;
        }
        if (context != null) {
            context.incrementClarifyRounds();
        }

        // 生成 CLARIFY 澄清计划
        QueryPlan clarifyPlan = new QueryPlan();
        clarifyPlan.setIntent(QueryPlan.INTENT_CLARIFY);
        clarifyPlan.setTools(List.of());
        clarifyPlan.setClarify(missing);
        clarifyPlan.setParams(new LinkedHashMap<>(plan.getParams()));
        clarifyPlan.setUserQuestion(question);
        log.info("[DefaultUnderstander] 必填参数缺失，生成澄清计划: {}", missing);
        return clarifyPlan;
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value);
        return !s.isBlank() && !"null".equals(s);
    }

    /** 翻译层可调用的真实工具白名单（防 LLM 编造工具名） */
    private static final Set<String> KNOWN_TOOLS = Set.of(
            "sparql_query",
            "swrl_root_cause",
            "swrl_risk_audit",
            "rule_explain",
            "ontology_explain"
    );

    /** 产商品研发场景工具白名单（scene=rd 时使用；不影响运营白名单） */
    private static final Set<String> RD_KNOWN_TOOLS = Set.of(
            "rd_config_chat",
            "rd_file_parse",
            "rd_compliance",
            "rd_config_discover",
            "rd_scheme_compare"
    );

    /**
     * 解析 LLM 的意图理解结果：意图归一化 → 查询计划（支持混合意图）。
     * <p>
     * 兼容三类输出：
     * 1. 新版 product_ops_* 业务意图命名（prompt 引导）
     * 2. 旧版 SPARQL_QUERY / SWRL_INFER / RULE_EXPLAIN 枚举（历史兼容）
     * 3. 混合意图：intent / action 用 {@code |} 拼接多个值（如 "product_ops_query | product_ops_policy"），
     *    此处按 {@code |} 拆分并逐意图映射为独立子计划。
     *
     * @return 子计划列表；单意图时仅含一个元素；无法解析时返回 null。
     */
    private List<QueryPlan> parseLlmResults(String llmResult, String question, boolean rdScene) {
        if (llmResult == null || llmResult.isBlank()) {
            return null;
        }

        // 尝试从 LLM 输出中提取 JSON
        int start = llmResult.indexOf('{');
        int end = llmResult.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }

        Map<String, Object> parsed;
        try {
            String json = llmResult.substring(start, end + 1);
            ObjectMapper mapper = new ObjectMapper();
            parsed = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[DefaultUnderstander] LLM 输出解析失败: {}", e.getMessage());
            return null;
        }

        String intent = String.valueOf(parsed.getOrDefault("intent", "CHAT"));
        String action = String.valueOf(parsed.getOrDefault("action", ""));
        @SuppressWarnings("unchecked")
        List<String> tools = (List<String>) parsed.getOrDefault("tools", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> params = new LinkedHashMap<>((Map<String, Object>) parsed.getOrDefault("params", Map.of()));
        params.putIfAbsent("question", question);

        // 混合意图：intent 含 | 时拆分多意图，逐意图独立映射
        if (intent.contains("|")) {
            return parseMultiIntents(intent, action, tools, params, question, rdScene);
        }

        // 旧版枚举 → 业务意图（仅运营场景支持；研发场景不识别旧枚举）
        String legacy = intent.trim().toUpperCase(Locale.ROOT);
        if (!rdScene) {
            if ("RULE_EXPLAIN".equals(legacy)) {
                return List.of(new QueryPlan("RULE_EXPLAIN", sanitizeTools(tools, List.of("rule_explain")), params, question));
            } else if ("ONTOLOGY_EXPLAIN".equals(legacy)) {
                return List.of(new QueryPlan("ONTOLOGY_EXPLAIN", sanitizeTools(tools, List.of("ontology_explain")), params, question));
            } else if ("CHAT".equals(legacy)) {
                return List.of(chatPlan(question));
            }
            if ("SPARQL_QUERY".equals(legacy)) intent = "product_ops_query";
            else if ("SWRL_INFER".equals(legacy)) {
                intent = "product_ops_reason";
                if (tools.contains("swrl_risk_audit")) intent = "product_ops_policy";
            }
        } else if ("CHAT".equals(legacy)) {
            return List.of(chatPlan(question));
        }

        QueryPlan plan = rdScene
                ? mapRdIntentToPlan(intent, action, tools, params, question)
                : mapIntentToPlan(intent, action, tools, params, question);
        if (plan == null) {
            diagnose(intent, action, tools, llmResult);
            return null;
        }
        return List.of(plan);
    }

    /**
     * 混合意图拆分：将 {@code |} 拼接的 intent / action 一一配对，逐意图映射为子计划。
     * <p>
     * tools / params 为整体共享（各子计划经 sanitizeTools 过滤出各自真实工具），
     * action 与 intent 位置对齐，缺失时补空串。
     */
    private List<QueryPlan> parseMultiIntents(String intent, String action, List<String> tools,
                                              Map<String, Object> params, String question, boolean rdScene) {
        List<String> intents = splitBar(intent);
        List<String> actions = splitBar(action);
        List<QueryPlan> plans = new ArrayList<>();
        for (int i = 0; i < intents.size(); i++) {
            String subIntent = intents.get(i);
            String subAction = i < actions.size() ? actions.get(i) : "";
            // 每个子计划独立 params 副本，避免 mapIntentToPlan 向共享 map 写入 intent_type 互相覆盖
            Map<String, Object> subParams = new LinkedHashMap<>(params);
            subParams.put("question", question);
            QueryPlan plan = rdScene
                    ? mapRdIntentToPlan(subIntent, subAction, tools, subParams, question)
                    : mapIntentToPlan(subIntent, subAction, tools, subParams, question);
            if (plan != null) {
                plans.add(plan);
            }
        }
        if (plans.isEmpty()) {
            diagnose(intent, action, tools == null ? List.of() : tools, null);
            return null;
        }
        log.info("[DefaultUnderstander] 混合意图拆分为 {} 个子计划: {}", plans.size(),
                plans.stream().map(QueryPlan::getIntent).toList());
        return plans;
    }

    /** 按 {@code |} 拆分为非空片段列表。 */
    private List<String> splitBar(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : value.split("\\|")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 意图无法映射时统一诊断：记日志 + 落盘（供定位混合/未知意图）。 */
    private void diagnose(String intent, String action, List<String> tools, String llmResult) {
        log.error("[DefaultUnderstander] 意图无法映射到执行计划（将终止）: intent={}, action={}, tools={}, normalized={}",
                intent, action, tools, IntentRecognitionSupport.normalizeIntentType(intent));
        writeDiagnosis(llmResult, intent, action, tools);
    }

    /**
     * 将归一化意图映射为查询计划（LLM 结果专用，含工具白名单过滤）。
     */
    private QueryPlan mapIntentToPlan(String intent, String action, List<String> tools,
                                      Map<String, Object> params, String question) {
        String normalized = IntentRecognitionSupport.normalizeIntentType(intent);
        // 保留业务意图标签与动作，供上层（AgentOrchestrator）反向还原 intentData
        params.put("intent_type", normalized);
        if (action != null && !action.isBlank()) {
            params.put("action", action);
        }

        return switch (normalized) {
            case "product_ops_query", "product_ops_monitor", "product_ops_compare" -> new QueryPlan(
                    "SPARQL_QUERY", sanitizeTools(tools, List.of("sparql_query")), params, question);
            case "product_ops_reason" -> new QueryPlan(
                    "SWRL_INFER", sanitizeTools(tools, List.of("sparql_query", "swrl_root_cause")), params, question);
            case "product_ops_policy" -> {
                if ("risk_audit".equals(action) || tools.contains("swrl_risk_audit")) {
                    yield new QueryPlan("SWRL_INFER", sanitizeTools(tools, List.of("swrl_risk_audit")), params, question);
                }
                yield new QueryPlan("SPARQL_QUERY", sanitizeTools(tools, List.of("sparql_query")), params, question);
            }
            default -> null;
        };
    }

    /**
     * 产商品研发场景：将归一化意图映射为查询计划（scene=rd 专用，含研发工具白名单过滤）。
     * 仅当 scene=rd 时调用，不影响运营路径的 {@link #mapIntentToPlan}。
     */
    private QueryPlan mapRdIntentToPlan(String intent, String action, List<String> tools,
                                        Map<String, Object> params, String question) {
        String normalized = IntentRecognitionSupport.normalizeIntentType(intent);
        params.put("intent_type", normalized);
        if (action != null && !action.isBlank()) {
            params.put("action", action);
        }

        return switch (normalized) {
            case "rd_config_chat" -> new QueryPlan(
                    "RD_CONFIG_CHAT", sanitizeTools(tools, List.of("rd_config_chat"), true), params, question);
            case "rd_file_parse" -> new QueryPlan(
                    "RD_FILE_PARSE", sanitizeTools(tools, List.of("rd_file_parse"), true), params, question);
            case "rd_compliance" -> new QueryPlan(
                    "RD_COMPLIANCE", sanitizeTools(tools, List.of("rd_compliance"), true), params, question);
            case "rd_config_discover" -> new QueryPlan(
                    "RD_CONFIG_DISCOVER", sanitizeTools(tools, List.of("rd_config_discover"), true), params, question);
            case "rd_scheme_compare" -> new QueryPlan(
                    "RD_SCHEME_COMPARE", sanitizeTools(tools, List.of("rd_scheme_compare"), true), params, question);
            default -> null;
        };
    }

    /** 是否为产商品研发场景（scene=rd）。 */
    private boolean isRdScene(SessionContext context) {
        return context != null && "rd".equals(context.getScene());
    }

    /** 过滤未知工具，缺省时用推荐工具列表。 */
    private List<String> sanitizeTools(List<String> tools, List<String> recommended) {
        return sanitizeTools(tools, recommended, false);
    }

    /** 过滤未知工具（可按场景选用不同白名单：rdScene=true 用研发白名单），缺省时用推荐工具列表。 */
    private List<String> sanitizeTools(List<String> tools, List<String> recommended, boolean rdScene) {
        Set<String> whitelist = rdScene ? RD_KNOWN_TOOLS : KNOWN_TOOLS;
        List<String> filtered = tools.stream()
                .filter(whitelist::contains)
                .distinct()
                .toList();
        return filtered.isEmpty() ? recommended : filtered;
    }

    /**
     * 通用对话：不调用工具，直接 LLM 回复。
     */
    private QueryPlan chatPlan(String question) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("question", question);
        return new QueryPlan("CHAT", List.of(), params, question);
    }

    /**
     * 诊断落盘：意图无法映射到执行计划时，把 LLM 原始输出与解析出的 intent/action/tools 追加写入
     * {@code ./logs/understand-diagnosis.log}（相对运行目录，与现有 FILE 日志同目录），
     * 便于定位混合意图/未知意图导致的理解失败。写盘失败不影响主流程（仅记录）。
     */
    private void writeDiagnosis(String llmResult, String intent, String action, List<String> tools) {
        String line = String.format(
                "[%s] intent=%s | action=%s | tools=%s | raw=%s%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                intent, action, tools == null ? "null" : tools, llmResult);
        try {
            Path dir = Paths.get("").toAbsolutePath().resolve("logs");
            Files.createDirectories(dir);
            Files.write(dir.resolve("understand-diagnosis.log"),
                    line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("[DefaultUnderstander] 诊断日志写盘失败: {}", e.getMessage());
        }
    }

    private String buildSystemPrompt(boolean rdScene) {
        return rdScene ? buildRdSystemPrompt() : buildOpsSystemPrompt();
    }

    /** 运营场景：保留原 prompt（scene 非 rd 时行为 100% 不变）。 */
    private String buildOpsSystemPrompt() {
        return "你是一个产品运营智能助手，负责理解用户的问题，并将其翻译为可执行的查询计划。\n\n"
                + "请输出 JSON（仅输出 JSON，不要输出其他内容）：\n"
                + "{\n"
                + "  \"intent\": \"product_ops_query | product_ops_reason | product_ops_policy | product_ops_monitor | product_ops_compare | CHAT\",\n"
                + "  \"action\": \"query | root_cause | risk_audit | online_check | ops_monitor | compare\",\n"
                + "  \"tools\": [\"工具名列表\"],\n"
                + "  \"params\": {\"question\": \"原始问题\", \"offering\": \"商品/套餐\", \"metric\": \"指标\", \"time\": \"时间范围\"}\n"
                + "}\n\n"
                + "意图与工具对应关系：\n"
                + "- product_ops_query: 商品数据/销量/指标查询 → tools: [\"sparql_query\"]\n"
                + "- product_ops_reason: 业务指标异动归因 → tools: [\"sparql_query\", \"swrl_root_cause\"]\n"
                + "- product_ops_policy: 风险稽核（action=risk_audit 或 online_check）→ tools: [\"swrl_risk_audit\"]\n"
                + "- product_ops_monitor: 运营监控/告警 → tools: [\"sparql_query\"]\n"
                + "- product_ops_compare: 方案对比/假设推演 → tools: [\"sparql_query\"]\n\n"
                + "可用工具：\n"
                + "- sparql_query: SPARQL 查询 RDF 知识库（查询数据、商品列表、指标）\n"
                + "- swrl_root_cause: SWRL 归因推理（分析原因、根因定位）\n"
                + "- swrl_risk_audit: SWRL 风险稽核（风险筛查、合规检查）\n"
                + "- rule_explain: 业务规则解释\n"
                + "- ontology_explain: 本体概念解释\n\n"
                + "如果用户只是打招呼或闲聊，intent 设为 CHAT，tools 设为空列表。请从问题中抽取 offering/metric/time 等实体填入 params。";
    }

    /** 产商品研发场景：引导 LLM 输出研发工具意图（scene=rd 专用）。 */
    private String buildRdSystemPrompt() {
        return "你是一个产商品研发智能助手，负责理解用户的需求，并将其翻译为可执行的研发配置计划。\n\n"
                + "请输出 JSON（仅输出 JSON，不要输出其他内容）：\n"
                + "{\n"
                + "  \"intent\": \"rd_config_chat | rd_file_parse | rd_compliance | rd_config_discover | rd_scheme_compare | CHAT\",\n"
                + "  \"action\": \"generate | parse | compliance | discover | compare\",\n"
                + "  \"tools\": [\"工具名列表\"],\n"
                + "  \"params\": {\"question\": \"原始问题\", \"text\": \"研发需求描述\", \"draft\": \"已有配置草稿\"}\n"
                + "}\n\n"
                + "意图与工具对应关系：\n"
                + "- rd_config_chat: 对话生成产商品配置草稿 → tools: [\"rd_config_chat\"]\n"
                + "- rd_file_parse: 解析方案文档/批量映射配置草稿 → tools: [\"rd_file_parse\"]\n"
                + "- rd_compliance: 对配置草稿做合规校验 → tools: [\"rd_compliance\"]\n"
                + "- rd_config_discover: 检索历史产商品配置方案 → tools: [\"rd_config_discover\"]\n"
                + "- rd_scheme_compare: 多候选方案对比（资费/收益）→ tools: [\"rd_scheme_compare\"]\n\n"
                + "可用工具：\n"
                + "- rd_config_chat: 对话配置生成（text 为必填）\n"
                + "- rd_file_parse: 文档解析（file_id 或 document_text）\n"
                + "- rd_compliance: 合规校验（draft 或 text）\n"
                + "- rd_config_discover: 历史配置检索（question 为必填）\n"
                + "- rd_scheme_compare: 多方案对比（text 描述多个资费档位）\n\n"
                + "如果用户只是打招呼或闲聊，intent 设为 CHAT，tools 设为空列表。请从问题中抽取 text/draft 等填入 params。";
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Map<String, String>> toHistory(SessionContext context) {
        if (context == null || context.getHistory() == null) {
            return null;
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> entry : context.getHistory()) {
            Map<String, String> converted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : entry.entrySet()) {
                converted.put(e.getKey(), String.valueOf(e.getValue()));
            }
            result.add(converted);
        }
        return result;
    }
}