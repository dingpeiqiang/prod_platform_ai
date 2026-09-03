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
import com.sitech.prodai.service.agent.tool.ThinkingCopy;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /** 连续确认上限：超过后按首选解读继续（U2 防死循环，复用 CLARIFY 上限语义） */
    private static final int MAX_CONFIRM_ROUNDS = 2;

    private final LlmService llmService;
    private final Map<String, AgentTool> toolMap;
    private final com.sitech.prodai.mapper.OpsWorkOrderMapper workOrderMapper;
    /** 流程路由注册表：理解层注入已发布流程清单供 LLM 选择 flow_execute。 */
    private final com.sitech.prodai.service.agent.flow.FlowIntentRouter flowIntentRouter;
    /** 能力注册表（单源）：场景 → 可见工具白名单，工具自声明场景后统一读取。 */
    private final com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry capabilityRegistry;

    public DefaultUnderstander(LlmService llmService, List<AgentTool> tools,
                               com.sitech.prodai.mapper.OpsWorkOrderMapper workOrderMapper,
                               com.sitech.prodai.service.agent.flow.FlowIntentRouter flowIntentRouter,
                               com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry capabilityRegistry) {
        this.llmService = llmService;
        this.toolMap = new LinkedHashMap<>();
        this.workOrderMapper = workOrderMapper;
        this.flowIntentRouter = flowIntentRouter;
        this.capabilityRegistry = capabilityRegistry;
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
        // 推理留痕：LLM 调用/解析/校验各环节追加到首个计划（多计划共享同一份理解日志）
        List<QueryPlan> plans = understandPlansInternal(question, context);
        return plans;
    }

    private List<QueryPlan> understandPlansInternal(String question, SessionContext context) {
        String llmResult = null;
        Exception lastError = null;
        boolean rdScene = isRdScene(context);
        int attemptsUsed = 0;
        for (int attempt = 1; attempt <= MAX_TRANSLATE_ATTEMPTS; attempt++) {
            attemptsUsed = attempt;
            try {
                List<Map<String, String>> history = toHistory(context);
                String systemPrompt = buildSystemPrompt(rdScene);
                // rd 场景注入会话工单清单（工单号/名称/状态）：LLM 判断「提交哪些单/是否重复提交」
                // 不能只凭历史文本（回执文案可能只提到部分单号），以 DB 实时状态为准
                if (rdScene) {
                    String woContext = buildWorkOrderContext(context);
                    if (!woContext.isEmpty()) {
                        systemPrompt = systemPrompt + "\n【当前会话工单实时状态】\n" + woContext;
                    }
                }
                llmResult = llmService.completeMessages(systemPrompt, history, question);
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
        final String llmRaw = llmResult;
        final int llmAttempts = attemptsUsed;
        java.util.function.BiConsumer<String, String> trace = (stage, message) -> {
            // 延迟挂载：解析成功前先暂存，解析出计划后统一注入（避免失败重试场景日志丢失）
            pendingTrace.add(Map.of("stage", stage, "message", message));
        };
        if (llmAttempts > 1) {
            trace.accept("llm", "大模型调用重试 " + llmAttempts + " 次后返回结果");
        } else {
            trace.accept("llm", "调用大模型理解需求，返回意图与工具选择");
        }
        if (llmRaw != null && !llmRaw.isBlank()) {
            trace.accept("llm", "大模型原始输出：" + summarizeLlmRaw(llmRaw));
        }

        if (llmResult == null && lastError != null) {
            throw new IllegalStateException("大模型不可用，理解层调用失败: " + lastError.getMessage(), lastError);
        }
        if (llmResult == null || llmResult.isBlank()) {
            log.error("[DefaultUnderstander] 大模型返回为空，翻译链终止");
            throw new IllegalStateException("大模型返回为空，无法理解用户需求");
        }

        List<QueryPlan> parsed = parseLlmResults(llmResult, question, rdScene, context);
        if (parsed == null || parsed.isEmpty()) {
            log.error("[DefaultUnderstander] 大模型输出无法解析为查询计划，翻译链终止");
            throw new IllegalStateException("大模型输出无法解析为查询计划，请重试或检查模型配置");
        }

        // 将本轮理解环节的推理日志挂到各子计划（多计划共享同一份理解日志）
        for (QueryPlan plan : parsed) {
            if (plan.getReasoningTrace() == null) {
                plan.setReasoningTrace(new ArrayList<>(pendingTrace));
            } else {
                plan.getReasoningTrace().addAll(pendingTrace);
            }
        }
        pendingTrace.clear();

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
            firstClarify.addTrace("validate", "检测到必填参数缺失，转入澄清流程等待补充");
            return List.of(firstClarify);
        }
        return validated;
    }

    /** 本轮理解环节暂存的推理日志（解析成功后统一注入计划；失败重试时日志不丢）。 */
    private final java.util.List<Map<String, Object>> pendingTrace =
            java.util.Collections.synchronizedList(new ArrayList<>());

    /** LLM 原始输出摘要：截断换行并限长（供推理日志展示，不透传完整 JSON 噪声）。 */
    private String summarizeLlmRaw(String raw) {
        String s = raw.replaceAll("\\s+", " ").trim();
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
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
        Map<String, Map<String, Object>> missingContracts = new LinkedHashMap<>();
        List<String> fromCache = new ArrayList<>();
        List<String> fromDefault = new ArrayList<>();
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
                    fromCache.add(param.getLabel() != null && !param.getLabel().isBlank()
                            ? param.getLabel() : param.getName());
                    continue;
                }
                // 有缺省值则不阻塞（U3：缺省回填属系统自行推断，记录假设供表达层回显）
                if (param.getDefaultValue() != null && !param.getDefaultValue().isBlank()) {
                    plan.getParams().put(param.getName(), param.getDefaultValue());
                    if (context != null) {
                        context.recordAssumption(param.getName(), param.getDefaultValue(), "未指定，按缺省值推断");
                    }
                    fromDefault.add(param.getLabel() != null && !param.getLabel().isBlank()
                            ? param.getLabel() : param.getName());
                    continue;
                }
                if (!missing.contains(param.getName())) {
                    missing.add(param.getName());
                    missingContracts.put(param.getName(), paramContract(param));
                }
            }
        }
        // 推理留痕：参数回填来源（缓存复用 / 缺省推断），让数据流可追溯
        if (!fromCache.isEmpty()) {
            plan.addTrace("params", "复用会话中已确认的参数：" + String.join("、", fromCache));
        }
        if (!fromDefault.isEmpty()) {
            plan.addTrace("params", "未指定的参数按缺省值推断：" + String.join("、", fromDefault));
        }

        if (missing.isEmpty()) {
            if (context != null) {
                context.resetClarifyRounds();
                // 本轮参数齐备：清空上一轮遗留假设，避免过期假设污染本轮结论
                context.clearAssumptions();
            }
            return plan;
        }


        // 超过澄清上限：按缺省值继续（缺省缺失时放弃该参数），防死循环（U3：明示该假设）
        if (context != null && context.exceedClarifyLimit()) {
            log.info("[DefaultUnderstander] 澄清轮次已达上限，按缺省继续: {}", missing);
            context.resetClarifyRounds();
            for (String name : missing) {
                context.recordAssumption(name, "（未提供）", "澄清超限，未按该参数过滤结果");
            }
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
        clarifyPlan.setClarifyContracts(missingContracts);
        clarifyPlan.setParams(new LinkedHashMap<>(plan.getParams()));
        clarifyPlan.setUserQuestion(question);
        log.info("[DefaultUnderstander] 必填参数缺失，生成澄清计划: {}", missing);
        return clarifyPlan;
    }

    /** 缺失参数的展示契约：业务名 / 说明 / 候选选项（供前端渲染选择题补参）。 */
    private Map<String, Object> paramContract(ToolParam param) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (param.getLabel() != null && !param.getLabel().isBlank()) {
            m.put("label", param.getLabel());
        }
        if (param.getDescription() != null && !param.getDescription().isBlank()) {
            m.put("description", param.getDescription());
        }
        if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
            m.put("options", param.getEnumValues());
        }
        return m;
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value);
        return !s.isBlank() && !"null".equals(s);
    }

    /** 翻译层可调用的真实工具白名单（防 LLM 编造工具名）已收敛至 AgentCapabilityRegistry（工具 getScenes() 自声明）。 */

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
    private List<QueryPlan> parseLlmResults(String llmResult, String question, boolean rdScene, SessionContext context) {
        if (llmResult == null || llmResult.isBlank()) {
            diagnose("解析失败", "空输出", List.of(), llmResult);
            return null;
        }

        // 尝试从 LLM 输出中提取 JSON
        int start = llmResult.indexOf('{');
        int end = llmResult.lastIndexOf('}');
        if (start < 0 || end <= start) {
            diagnose("解析失败", "输出中无 JSON 结构", List.of(), llmResult);
            return null;
        }

        Map<String, Object> parsed;
        try {
            String json = llmResult.substring(start, end + 1);
            ObjectMapper mapper = new ObjectMapper();
            parsed = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[DefaultUnderstander] LLM 输出解析失败: {}", e.getMessage());
            diagnose("解析失败", "JSON 解析异常: " + e.getMessage(), List.of(), llmResult);
            return null;
        }

        String intent = String.valueOf(parsed.getOrDefault("intent", "CHAT"));
        String action = String.valueOf(parsed.getOrDefault("action", ""));
        @SuppressWarnings("unchecked")
        List<String> tools = (List<String>) parsed.getOrDefault("tools", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> params = new LinkedHashMap<>((Map<String, Object>) parsed.getOrDefault("params", Map.of()));
        params.putIfAbsent("question", question);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> llmSteps = (List<Map<String, Object>>) parsed.get("steps");

        // 需求歧义（U2）：LLM 判定存在多种合理解读 → 生成确认计划，整轮暂停等用户选定
        if ("CONFIRM".equalsIgnoreCase(intent.trim())) {
            return confirmPlan(parsed, question, context);
        }

        // 闲聊：无工具即通用对话（intent 仅作展示标签，不再约束枚举）
        String legacy = intent.trim().toUpperCase(Locale.ROOT);
        if ("CHAT".equals(legacy) || tools.isEmpty()) {
            return List.of(chatPlan(question));
        }

        // 混合意图：intent 含 | 时拆分多意图（解析 LLM 已输出结构，非写死映射）
        if (intent.contains("|") && llmSteps == null) {
            return parseMultiIntents(intent, action, tools, params, question, rdScene);
        }

        // 守门：白名单过滤（未注册工具剔除；全被剔除 → 一次重选机会）
        List<String> sanitized = sanitizeTools(tools, List.of(), rdScene);
        if (sanitized.isEmpty()) {
            sanitized = retryToolSelection(intent, action, tools, params, question, rdScene, llmResult);
            if (sanitized == null || sanitized.isEmpty()) {
                diagnose(intent, action, tools, llmResult);
                return null;
            }
            pendingTrace.add(Map.of("stage", "llm",
                    "message", "大模型选中的工具均不在白名单，重新选择后命中：" + String.join("、", sanitized)));
        } else if (!sanitized.equals(tools)) {
            pendingTrace.add(Map.of("stage", "llm",
                    "message", "工具白名单校验：剔除未注册工具，保留 " + String.join("、", sanitized)));
        }

        // 兜底仲裁（rd 场景）：检索意图词命中而 LLM 选了草稿生成工具 → 强制改派配置查询。
        // 提示词已声明「查已有 vs 造新」最高优先级，此处防 LLM 偶发误判（如"找一下…月费39"
        // 被资费要素拽向 rd_config_chat，导致意外生成草稿并自动开工单）。
        if (rdScene && sanitized.contains("rd_config_chat")
                && isDiscoverIntentQuestion(question)) {
            log.info("[DefaultUnderstander] 兜底改派: 检索意图词命中，rd_config_chat → rd_config_discover, question={}", question);
            pendingTrace.add(Map.of("stage", "llm",
                    "message", "话术命中检索意图，工具由「生成配置草稿」改派为「检索历史配置」"));
            sanitized = List.of("rd_config_discover");
        }

        // flow_execute 守门：LLM 只能从已发布流程注册表（FlowIntentRouter）中选定 workflow_code，
        // 防幻觉流程编码穿透到执行层（确定性锚点 = workflow_code，方案 §12.2/12.3）。
        // 注册表中无该流程 → 剔除 flow_execute；剔除后无任何可用工具 → 返回 null 走 chatPlan 统一回复话术。
        if (sanitized.contains("flow_execute") && !hasRegisteredFlow(params)) {
            log.warn("[DefaultUnderstander] flow_execute 守门: workflow_code 未在已发布流程注册表中, params={}",
                    params.get("workflow_code"));
            pendingTrace.add(Map.of("stage", "llm",
                    "message", "选定的流程编码不在已发布流程清单中，已拦截（该需求将以统一话术说明无法办理）"));
            sanitized = sanitized.stream().filter(t -> !"flow_execute".equals(t)).toList();
            if (sanitized.isEmpty()) {
                diagnose(intent, action, tools, llmResult);
                return null;
            }
        }

        // 意图弱化为展示标签：保留 intent_type/action 供编排层还原 intentData 与文案生成
        String normalized = IntentRecognitionSupport.normalizeIntentType(intent);
        // rd 场景：LLM 输出的 intent 为自由文本（configure/configuration/product_config…），
        // 前端按工具名对齐的大写意图码注册后处理器（RD_CONFIG_CHAT 等），
        // 故以白名单命中的首个工具名确定性推导意图码，保证 done.intent 稳定可消费
        String intentCode = rdScene ? rdIntentFromTools(sanitized) : normalized;
        params.put("intent_type", intentCode);
        pendingTrace.add(Map.of("stage", "llm",
                "message", "识别业务意图「" + ThinkingCopy.actionDisplay(intentCode) + "」，"
                        + "安排执行工具：" + String.join("、",
                                sanitized.stream().map(t -> {
                                    ThinkingCopy.ToolCopy c = ThinkingCopy.toolCopy(t);
                                    return c != null ? c.title() : t;
                                }).toList())));
        // 前端随消息携带的结构化参数（ Orchestrator.applySuppliedParams 已入 resolvedParams ）
        // 前置合并到 plan.params：LLM 翻译时可能只抽取话术中的名称而丢弃精确 id（draft_id/client_id），
        // 此处以调用方传入的精确参数为准（仅在 LLM 未输出同名键时合并，不覆盖 LLM 显式结果）。
        // rd_draft_manage 场景额外合并 work_order_id：工单卡按钮操作会把单号经 resolvedParams 传入，
        // LLM 话术可能只用名称指代（如「修改 XX(副本)」），单号以用户显式操作为准。
        // 注意：offer 是查询类遗留缓存（上轮智查的商品），操作类意图（RD_DRAFT_MANAGE）下混入会污染
        // plan.params 与思考面板展示（「分析对象」显示成上轮工单），此处显式剔除。
        if (context != null && context.getResolvedParams() != null) {
            boolean isDraftManage = sanitized.contains("rd_draft_manage");
            for (Map.Entry<String, Object> e : context.getResolvedParams().entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if (isDraftManage && "offering".equals(e.getKey())) {
                    continue;
                }
                params.putIfAbsent(e.getKey(), e.getValue());
            }
        }
        // rd 场景透传会话 ID：AgentTool 接口无 context 参数，经 plan.params → executor direct 兜底
        // 透传给工具（如 rd_config_chat 草稿生成即开工单需绑定会话）。
        // session_id 是系统参数，必须以服务端 SessionContext 为准：LLM 可能幻觉输出同名键
        // （putIfAbsent 不覆盖会导致工具拿到空/错 sessionId 而跳过开单），故此处强制覆盖。
        if (rdScene && context != null && context.getSessionId() != null && !context.getSessionId().isBlank()) {
            params.put("session_id", context.getSessionId());
        }
        if (action != null && !action.isBlank()) {
            params.put("action", action);
        }

        QueryPlan plan = new QueryPlan(intentCode.isEmpty() ? legacy : intentCode,
                new ArrayList<>(sanitized), params, question);
        // LLM 给出 steps（含 inputFrom 跨工具数据流声明）时构建 ExecStep 依赖编排
        if (llmSteps != null && !llmSteps.isEmpty()) {
            List<com.sitech.prodai.service.agent.model.ExecStep> execSteps =
                    buildExecSteps(llmSteps, sanitized);
            if (execSteps == null) {
                diagnose(intent, action, tools, llmResult);
                return null;
            }
            plan.setSteps(execSteps);
        }
        return List.of(plan);
    }

    /**
     * 一次重选：LLM 引用的工具全不在白名单时，把合法能力清单回传 LLM 重选一次（防幻觉不扩散到执行层）。
     *
     * @return 重选后的合法工具列表；仍失败返回 null（由调用方诊断终止）
     */
    private List<String> retryToolSelection(String intent, String action, List<String> tools,
                                            Map<String, Object> params, String question, boolean rdScene,
                                            String llmResult) {
        log.warn("[DefaultUnderstander] LLM 引用工具全部未注册 {}，触发一次重选", tools);
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("您上次选择的工具均不存在。请从下列可用能力中重新选择并输出 JSON：\n");
            for (AgentTool tool : toolsOf(rdScene)) {
                sb.append("- ").append(tool.getName()).append("：").append(tool.getDescription()).append('\n');
            }
            sb.append("\n原始用户问题：").append(question)
                    .append("\n仅输出 JSON：{\"tools\": [\"工具名\"], \"params\": {...}}");
            String retry = llmService.completePrompt(sb.toString());
            if (retry == null || retry.isBlank()) {
                return null;
            }
            int start = retry.indexOf('{');
            int end = retry.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            Map<String, Object> parsed = new ObjectMapper().readValue(
                    retry.substring(start, end + 1), new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<String> retryTools = (List<String>) parsed.getOrDefault("tools", List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> retryParams = (Map<String, Object>) parsed.get("params");
            if (retryParams != null) {
                for (Map.Entry<String, Object> e : retryParams.entrySet()) {
                    params.putIfAbsent(e.getKey(), e.getValue());
                }
            }
            return sanitizeTools(retryTools, List.of(), rdScene);
        } catch (Exception e) {
            log.warn("[DefaultUnderstander] 重选失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * LLM steps 声明 → ExecStep 序列（守门：inputFrom 静态校验 + DAG 无环校验）。
     *
     * @param llmSteps LLM 输出的 steps 结构（[{tool, inputFrom: {param: "<上游工具>.<输出键>"}}]）
     * @param sanitized 白名单过滤后的合法工具（LLM steps 中的非法工具剔除）
     * @return ExecStep 序列；DAG 有环返回 null（拒绝整个计划）
     */
    private List<com.sitech.prodai.service.agent.model.ExecStep> buildExecSteps(
            List<Map<String, Object>> llmSteps, List<String> sanitized) {
        Map<String, com.sitech.prodai.service.agent.model.ExecStep> byTool = new LinkedHashMap<>();
        List<com.sitech.prodai.service.agent.model.ExecStep> out = new ArrayList<>();
        for (Map<String, Object> raw : llmSteps) {
            String tool = raw != null ? String.valueOf(raw.get("tool")) : null;
            if (tool == null || !sanitized.contains(tool) || byTool.containsKey(tool)) {
                continue;
            }
            com.sitech.prodai.service.agent.model.ExecStep step =
                    new com.sitech.prodai.service.agent.model.ExecStep(tool);
            Map<String, String> mappings = new LinkedHashMap<>();
            Object inputFrom = raw.get("inputFrom");
            if (inputFrom instanceof Map<?, ?> from) {
                for (Map.Entry<?, ?> e : from.entrySet()) {
                    String param = String.valueOf(e.getKey());
                    String source = String.valueOf(e.getValue());
                    if (!isValidResultRef(source, sanitized)) {
                        // 守门①：引用键不在上游输出契约中 → 剔除该映射（下游参数走常规 direct 透传）
                        log.warn("[DefaultUnderstander] inputFrom 引用非法已剔除: {} -> {}", param, source);
                        continue;
                    }
                    mappings.put(param, source);
                }
            }
            step.getParamMappings().putAll(mappings);
            byTool.put(tool, step);
            out.add(step);
        }
        // 守门②：DAG 无环校验（按 result: 依赖建图，拓扑排序检测环）
        if (hasDependencyCycle(out)) {
            log.error("[DefaultUnderstander] steps 依赖存在环，拒绝该计划: {}",
                    out.stream().map(com.sitech.prodai.service.agent.model.ExecStep::getTool).toList());
            return null;
        }
        // 与 sanitized 中未被 steps 覆盖的工具合并（保持白名单顺序，防遗漏）
        for (String tool : sanitized) {
            if (!byTool.containsKey(tool)) {
                out.add(new com.sitech.prodai.service.agent.model.ExecStep(tool));
            }
        }
        return out;
    }

    /** 守门①：result:<tool>.<key> 引用合法性静态校验（tool 已声明 + key 在其 getOutputFields 契约内）。 */
    private boolean isValidResultRef(String source, List<String> sanitized) {
        if (source == null || !source.startsWith("result:")) {
            return false;
        }
        String body = source.substring("result:".length());
        int dot = body.indexOf('.');
        String toolName = dot > 0 ? body.substring(0, dot) : body;
        String key = dot > 0 ? body.substring(dot + 1) : null;
        if (!sanitized.contains(toolName) || key == null || key.isBlank()) {
            return false;
        }
        AgentTool tool = toolMap.get(toolName);
        if (tool == null) {
            return false;
        }
        return tool.getOutputFields().stream().anyMatch(f -> key.equals(f.getName()) || key.equals(f.getOutputKey()));
    }

    /** 守门②：按 paramMappings 的 result: 依赖建图，检测环（有环即拒绝，防执行期死等）。 */
    private boolean hasDependencyCycle(List<com.sitech.prodai.service.agent.model.ExecStep> steps) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            index.put(steps.get(i).getTool(), i);
        }
        Map<String, List<String>> deps = new LinkedHashMap<>();
        for (com.sitech.prodai.service.agent.model.ExecStep step : steps) {
            List<String> d = new ArrayList<>();
            for (String source : step.getParamMappings().values()) {
                if (source != null && source.startsWith("result:")) {
                    String body = source.substring("result:".length());
                    String toolName = body.contains(".")
                            ? body.substring(0, body.indexOf('.')) : body;
                    if (index.containsKey(toolName)) {
                        d.add(toolName);
                    }
                }
            }
            deps.put(step.getTool(), d);
        }
        // P3-2 抽公共：DFS 三色环检测统一收口至 DagValidator（原 hasCycleDfs 已删除）
        return com.sitech.prodai.service.common.DagValidator.hasCycleDfs(deps);
    }

    /**
     * 混合意图拆分：将 {@code |} 拼接的 intent / action 一一配对，逐意图构造子计划。
     * <p>
     * 映射表已退役：tools 按白名单过滤，各子计划取各自真实工具（整组共享），
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
            // 每个子计划独立 params 副本，避免 intent_type / action 写入互相覆盖
            Map<String, Object> subParams = new LinkedHashMap<>(params);
            subParams.put("question", question);
            String normalized = IntentRecognitionSupport.normalizeIntentType(subIntent);
            subParams.put("intent_type", normalized);
            if (subAction != null && !subAction.isBlank()) {
                subParams.put("action", subAction);
            }
            List<String> sanitized = sanitizeTools(tools, List.of(), rdScene);
            if (!sanitized.isEmpty()) {
                plans.add(new QueryPlan(normalized.isEmpty() ? subIntent.trim() : normalized,
                        new ArrayList<>(sanitized), subParams, question));
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
     * @deprecated 映射表已退役（AI 原生改造 8.3 第二步）：意图→工具映射由 LLM 基于工具自描述
     * 直接输出，白名单守门保留。此方法仅为编译期参考保留一个迭代周期，下版本删除。
     */
    @Deprecated
    @SuppressWarnings("unused")
    private QueryPlan mapIntentToPlan(String intent, String action, List<String> tools,
                                      Map<String, Object> params, String question) {
        String normalized = IntentRecognitionSupport.normalizeIntentType(intent);
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

    /** 是否为产商品研发场景（scene=rd）。 */
    private boolean isRdScene(SessionContext context) {
        return context != null && "rd".equals(context.getScene());
    }

    /** 过滤未知工具，缺省时用推荐工具列表。 */
    private List<String> sanitizeTools(List<String> tools, List<String> recommended) {
        return sanitizeTools(tools, recommended, false);
    }

    /** 过滤未知工具（按场景从能力注册表取白名单），缺省时用推荐工具列表。 */
    private List<String> sanitizeTools(List<String> tools, List<String> recommended, boolean rdScene) {
        String scene = rdScene ? "rd" : com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry.DEFAULT_SCENE;
        List<String> filtered = tools.stream()
                .filter(t -> capabilityRegistry.isVisible(t, scene))
                .distinct()
                .toList();
        return filtered.isEmpty() ? recommended : filtered;
    }

    /** 研发工具名 → 大写意图码（rd_config_chat → RD_CONFIG_CHAT），与前端词典/后端文案码对齐。 */
    private String rdIntentFromTools(List<String> sanitizedTools) {
        for (String tool : sanitizedTools) {
            if (tool != null && capabilityRegistry.belongsToScene(tool, "rd")) {
                return tool.toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    /**
     * 检索意图判定：话术含明确的「找已有方案」动词，且不含创建意愿动词。
     * <p>与提示词【查已有 vs 造新分流】A0/A1 同源——检索动词权重高于资费要素。
     */
    private boolean isDiscoverIntentQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.trim();
        boolean hasCreate = List.of("做一个", "新做", "新建", "生成", "创建", "配一个", "上一款", "新出")
                .stream().anyMatch(q::contains);
        if (hasCreate) {
            return false;
        }
        return List.of("找一下", "查一下", "查找", "找找", "找下", "查询", "检索", "有没有", "看看")
                .stream().anyMatch(q::contains);
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
     * 需求歧义确认计划（U2，方案 11.6(b)）：LLM 判定需求存在多种合理解读时，
     * 不执行任何工具、整轮暂停，由用户在候选卡片中选定后重发。
     * <p>
     * 防死循环：连续 {@value MAX_CONFIRM_ROUNDS} 轮 CONFIRM 未收敛 → 取首个候选解读
     * 直接执行，并在结论中明示该假设（复用 CLARIFY 上限语义，独立计数）。
     *
     * @return CONFIRM 计划；超限退化时返回按首选解读构造的执行计划（附假设标记）
     */
    private List<QueryPlan> confirmPlan(Map<String, Object> parsed, String question, SessionContext context) {
        @SuppressWarnings("unchecked")
        List<String> candidates = (List<String>) parsed.getOrDefault("candidates", List.of());
        candidates = candidates == null ? List.of()
                : candidates.stream().filter(c -> c != null && !c.isBlank()).map(String::trim).toList();
        if (candidates.isEmpty()) {
            // 无有效候选 → 退化为通用对话，避免空确认卡片
            return List.of(chatPlan(question));
        }

        if (context != null && context.exceedConfirmLimit()) {
            String chosen = candidates.get(0);
            log.info("[DefaultUnderstander] 确认轮次已达上限，按首选解读继续: {}", chosen);
            context.resetConfirmRounds();
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("question", chosen);
            params.put("assumed_interpretation", chosen);
            QueryPlan plan = new QueryPlan("CHAT", List.of(), params, question);
            plan.setSteps(null);
            return List.of(plan);
        }
        if (context != null) {
            context.incrementConfirmRounds();
        }

        QueryPlan plan = new QueryPlan();
        plan.setIntent(QueryPlan.INTENT_CONFIRM);
        plan.setTools(List.of());
        plan.setParams(new LinkedHashMap<>());
        plan.setUserQuestion(question);
        plan.setCandidates(candidates);
        log.info("[DefaultUnderstander] 需求存在多种解读，生成确认计划: {}", candidates);
        return List.of(plan);
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
        StringBuilder sb = new StringBuilder();
        sb.append(rdScene
                ? "你是一个产商品研发智能助手，负责理解用户的需求，并将其翻译为可执行的研发配置计划。\n"
                : "你是一个产品运营智能助手，负责理解用户的问题，并将其翻译为可执行的查询计划。\n");
        sb                .append("\n请输出 JSON（仅输出 JSON，不要输出其他内容）：\n")
                .append("{\n")
                .append("  \"intent\": \"业务意图标签（从下方各能力的适用场景归纳，闲聊则为 CHAT）\",\n")
                .append("  \"action\": \"动作标签\",\n")
                .append("  \"tools\": [\"工具名列表，按执行顺序排列\"],\n")
                .append("  \"params\": {\"question\": \"原始问题\", ...各工具所需参数}\n")
                .append("}\n\n")
                .append("如果用户只是打招呼或闲聊，intent 设为 CHAT，tools 设为空列表。\n")
                .append("请从用户问题中抽取参数所需的实体填入 params。\n\n")
                .append("【何时需要用户确认（CONFIRM）——由你结合上下文自行判断，宁可多问不可错执行：\n")
                .append("1) 需求存在多种合理解读且无法从上下文唯一确定；\n")
                .append("2) 用户指令会修改/删除数据（如删除草稿、提交工单），且目标对象不明确（未指明名称/编号，或上下文中可能命中多个对象）；\n")
                .append("3) 用户指令中的实体名称模糊或与多个已知对象部分匹配。\n")
                .append("确认时输出 {\"intent\": \"CONFIRM\", \"candidates\": [\"解读1\", \"解读2\"]}，")
                .append("每条候选为一句可直接执行的完整表述（包含明确的对象名称/编码），不要猜测。\n")
                .append("若目标对象唯一明确（如名称精确匹配唯一草稿），无需确认直接执行。\n\n")
                .append("【工单操作（rd_draft_manage）参数抽取铁律：\n")
                .append("1) work_order_id 必须来自用户话术或【当前会话工单实时状态】中列出的真实工单号（WO 开头），严禁编造或使用示例号；\n")
                .append("2) 用户话术未带工单号但上下文（上一轮生成/复制草稿的回执、会话工单清单）存在唯一工单时，沿用该工单号；\n")
                .append("3) 提交动作批量语义（最高优先级）：用户只说「提交」「提交工单」「批量提交」「全部提交」等未点名具体某一个时，")
                .append("不要 CONFIRM、不要只挑一单，必须把【当前会话工单实时状态】中全部状态为 open/in_progress（待提交）的工单号")
                .append("用英文逗号拼接写入 work_order_id（如 \"WO1,WO2\"）一次性批量提交；\n")
                .append("4) 上下文存在多个工单但用户明确点名其中一个（含名称/编号区分）→ 只取命中的那个工单号，不掺入其他工单；\n")
                .append("5) 状态为 cancelled（已取消）的工单严禁纳入 submit 的 work_order_id；\n")
                .append("6) 用户要求修改工单/草稿的字段（如改资费名称、改月费）时：无论工单状态是否 done，一律调用 rd_draft_manage 且 action=update，")
                .append("携带 work_order_id 和 offering_name（修改后的新名称），严禁翻译成 submit；修改成功后工具会自动重开工单；\n")
                .append("7) 多轮增量修改（最高优先级）：用户在上一轮修改后追加细化（如只说「改成 198 元」「月费改 59」「名称加上家庭版」）时，")
                .append("继续调用 rd_draft_manage 且 action=update：work_order_id 沿用会话缓存/上一轮回执的单号，")
                .append("只携带本轮提到的字段（offering_name 或 monthly_fee），并把原始话术透传到 question 参数兜底；")
                .append("未提到的字段不要回传旧值，由工具基于草稿现状合并；\n")
                .append("8) 上下文无任何待提交工单且话术无工单号 → 不要调用工具，直接向用户说明需要先提供工单号或先生成配置草稿。\n\n")
                .append("【查已有 vs 造新分流（最高优先级，先于下方所有铁律判断）：\n")
                .append("A0) 用户想查找/查看/对比已存在的配置方案（标志词：找一下、查一下、找找、有没有、检索、看看、")
                .append("类似的历史方案、现有套餐有哪些），即使话术中带月费、资费、渠道等套餐要素，也属于配置查询：")
                .append("必须调用 rd_config_discover，严禁调用 rd_config_chat（查询不会生成草稿、不会开工单）；\n")
                .append("A1) 只有用户明确表达创建意愿（标志词：做一个、新做、新建、生成、创建、配一个、上一款、新出）")
                .append("且无检索意图词时，才调用 rd_config_chat 生成新配置草稿；\n\n")
                .append("【新配置需求 vs 工单操作分流（次高优先级）：\n")
                .append("A) 用户描述一个全新配置需求（典型句式如「给XX用户做一个XX套餐，月费XX，带XX，销售范围XX」「新做/新增/创建一个包含XX的套餐」），")
                .append("无论当前会话已有多少工单，这都属于生成新配置草稿：必须调用 rd_config_chat，严禁调用 rd_draft_manage，")
                .append("更严禁臆造 action=create（rd_draft_manage 根本不存在 create 动作）；\n")
                .append("B) rd_draft_manage 的 action 仅允许 delete / copy / update / submit 四种；只有当用户明确要求删除、复制、修改或提交")
                .append("【当前会话工单实时状态】里已存在的某个/某些工单时才可调用，且 work_order_id 必须命中真实工单号；\n")
                .append("B1) 用户话术为「复制工单「XX」（WOxxx）对应的配置草稿，生成副本…」时（工单卡复制按钮发出，")
                .append("可能附带「并按以下需求调整：XXX」），这是明确的 copy 操作：必须调用 rd_draft_manage 且 action=copy，")
                .append("携带该 work_order_id；话术中的补充需求（改名/调资费等）透传到 question 参数，供复制后字段修正；\n")
                .append("C) 用户有明确创建意愿（要求做/生成/新做新套餐）且话术里出现资费、月费、宽带速率、销售渠道等完整套餐要素")
                .append("而无「修改/提交/删除某工单」的指向词时，才视为新配置需求走 rd_config_chat；")
                .append("若用户只是想查询/找到已有方案（见 A0），即使带这些要素也必须走 rd_config_discover；\n")
                .append("D) 上下文已有工单不等于用户要操作工单：判断依据是话术语义（是否点名工单/是否带操作动词），而不是工单数量。\n\n可用能力：\n");
        for (AgentTool tool : toolsOf(rdScene)) {
            sb.append("- ").append(tool.getName())
                    .append("：").append(tool.getDescription());
            List<ToolParam> params = tool.getParams();
            if (params != null && !params.isEmpty()) {
                sb.append("（参数：").append(describeParams(params)).append("）");
            }
            sb.append('\n');
        }
        String flowList = buildFlowCapabilitySection();
        if (!flowList.isEmpty()) {
            sb.append('\n').append(flowList);
        }
        return sb.toString();
    }

    /**
     * 已发布固定流程能力清单（供 LLM 选择 flow_execute 的 workflow_code）。
     * <p>
     * 数据源为流程路由注册表（发布工作流时自动注册触发词），只注入名称/编码/触发词，
     * 参数契约不入 prompt（防膨胀）：LLM 缺参时由 validateParams 的 CLARIFY 机制补齐。
     * 注册表为空时不注入（用户不可执行任何流程，也不给 LLM 编造空间）。
     */
    private String buildFlowCapabilitySection() {
        Map<String, com.sitech.prodai.service.agent.flow.FlowIntentRouter.FlowRoute> routes =
                flowIntentRouter == null ? Map.of() : flowIntentRouter.listRoutes();
        if (routes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【可用固定流程（经 flow_execute 执行，workflow_code 必须取自本清单）】\n");
        for (com.sitech.prodai.service.agent.flow.FlowIntentRouter.FlowRoute route : routes.values()) {
            sb.append("- ").append(route.workflowCode())
                    .append("（").append(route.displayName() == null || route.displayName().isBlank()
                            ? route.workflowCode() : route.displayName()).append("）")
                    .append("：适用话术如「").append(String.join("、", route.keywords())).append("」\n");
        }
        sb.append("若用户需求命中上述某个流程，调用 flow_execute 并把 workflow_code 设为该流程编码；")
          .append("需求与所有流程均不匹配时，不要调用 flow_execute，改用其他能力或直接说明无法办理。\n");
        return sb.toString();
    }

    /**
     * flow_execute 守门校验：LLM 选定的 workflow_code 必须命中已发布流程注册表。
     * workflow_code 缺失（LLM 幻觉/漏填）同样视为不合法，由调用方剔除该工具。
     */
    private boolean hasRegisteredFlow(Map<String, Object> params) {
        Object code = params == null ? null : params.get("workflow_code");
        if (code == null || String.valueOf(code).isBlank()
                || "null".equalsIgnoreCase(String.valueOf(code))) {
            return false;
        }
        Map<String, com.sitech.prodai.service.agent.flow.FlowIntentRouter.FlowRoute> routes =
                flowIntentRouter == null ? Map.of() : flowIntentRouter.listRoutes();
        return routes.containsKey(String.valueOf(code).trim());
    }

    /** 场景白名单过滤后的可见工具列表（能力市场：LLM 只能看到本场景声明的工具）。 */
    private List<AgentTool> toolsOf(boolean rdScene) {
        String scene = rdScene ? "rd" : com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry.DEFAULT_SCENE;
        List<AgentTool> out = new ArrayList<>();
        for (AgentTool tool : capabilityRegistry.toolsOf(scene)) {
            if (toolMap.containsKey(tool.getName())) {
                out.add(tool);
            }
        }
        return out;
    }

    /** 参数契约 → prompt 片段：名称(说明[, 必填][, 取值])。 */
    private String describeParams(List<ToolParam> params) {
        StringBuilder sb = new StringBuilder();
        for (ToolParam p : params) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p.getName());
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                sb.append("=").append(p.getDescription());
            }
            if (p.isRequired()) {
                sb.append(", 必填");
            }
            if (p.getEnumValues() != null && !p.getEnumValues().isEmpty()) {
                sb.append(", 可选值:").append(p.getEnumValues());
            }
        }
        return sb.toString();
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

    /**
     * 会话工单实时清单 → 提示词上下文（工单号/名称/状态）。
     * <p>
     * LLM 判断「提交哪些单 / 是否重复提交」需要以 DB 实时工单状态为准；
     * 会话历史只有展示文案（可能不完整或不带单号），故查询层直接注入。
     * 查询失败不阻断理解（返回空串，退化为仅凭历史文本）。
     */
    private String buildWorkOrderContext(SessionContext context) {
        if (context == null || context.getSessionId() == null || context.getSessionId().isBlank()) {
            return "";
        }
        try {
            List<com.sitech.prodai.domain.entity.OpsWorkOrder> rows =
                    workOrderMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sitech.prodai.domain.entity.OpsWorkOrder>()
                            .eq(com.sitech.prodai.domain.entity.OpsWorkOrder::getSessionId, context.getSessionId())
                            .orderByDesc(com.sitech.prodai.domain.entity.OpsWorkOrder::getCreatedAt)
                            .last("LIMIT 50"));
            if (rows == null || rows.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            // 状态→动作语义（open=待处理可提交，done=已完成不可重复提交），LLM 不再自行猜测状态含义
            for (var e : rows) {
                sb.append("- ").append(e.getWorkOrderId())
                        .append("｜").append(e.getOfferingName() == null ? "" : e.getOfferingName())
                        .append("｜状态=").append(e.getStatus())
                        .append(switch (e.getStatus() == null ? "" : e.getStatus()) {
                            case "open", "in_progress" -> "（待提交）";
                            case "done" -> "（已提交）";
                            case "cancelled" -> "（已取消，勿再提交）";
                            default -> "";
                        }).append('\n');
            }
            return sb.toString();
        } catch (Exception ex) {
            log.warn("[DefaultUnderstander] 会话工单上下文查询失败（不影响理解）: {}", ex.getMessage());
            return "";
        }
    }
}