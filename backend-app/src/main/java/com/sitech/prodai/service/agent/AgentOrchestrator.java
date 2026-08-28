package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.ChatPersistenceService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolOutputRenderer;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译层编排入口。
 * <p>
 * 编排"理解 → 执行 → 表达"三层的完整流程。
 * <p>
 * 去旧留新：新翻译链路自带会话持久化（复用 {@link ChatPersistenceService}），
 * 使 Agent 会话进入侧边栏历史并可恢复，不再依赖旧 {@code /api/v1/chat/agent/stream}。
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final Understander understander;
    private final Executor executor;
    private final Presenter presenter;
    private final SessionManager sessionManager;
    private final Optional<ChatPersistenceService> persistenceService;

    /** 已注册工具索引：工具名 → 工具（供工具自描述元数据查询） */
    private final Map<String, AgentTool> toolMap;

    public AgentOrchestrator(Understander understander,
                             Executor executor,
                             Presenter presenter,
                             SessionManager sessionManager,
                             Optional<ChatPersistenceService> persistenceService,
                             List<AgentTool> tools) {
        this.understander = understander;
        this.executor = executor;
        this.presenter = presenter;
        this.sessionManager = sessionManager;
        this.persistenceService = persistenceService;
        this.toolMap = new ConcurrentHashMap<>();
        if (tools != null) {
            for (AgentTool tool : tools) {
                this.toolMap.put(tool.getName(), tool);
            }
        }
    }

    /**
     * 处理一次完整的翻译请求。
     *
     * @param question  用户问题
     * @param sessionId 会话 ID（可选，null 时创建新会话）
     * @return 翻译结果（含 session_id, report, evidence, conclusion, suggested_follow_ups）
     */
    public Map<String, Object> process(String question, String sessionId) {
        return process(question, sessionId, null, null);
    }

    /**
     * 处理一次完整的翻译请求（支持结构化补参）。
     *
     * @param question  用户问题
     * @param sessionId 会话 ID（可选，null 时创建新会话）
     * @param params    用户补充的结构化参数（CLARIFY 澄清回传），合并进 resolvedParams
     * @return 翻译结果（含 session_id, report, evidence, conclusion, suggested_follow_ups）
     */
    public Map<String, Object> process(String question, String sessionId, Map<String, Object> params) {
        return process(question, sessionId, params, null);
    }

    /**
     * 处理一次完整的翻译请求（支持结构化补参 + 助手场景）。
     *
     * @param question  用户问题
     * @param sessionId 会话 ID（可选，null 时创建新会话）
     * @param params    用户补充的结构化参数（CLARIFY 澄清回传），合并进 resolvedParams
     * @param scene     助手场景（"rd" = 产商品研发；null/空 = 默认运营）。驱动理解层分支，运营路径不受影响
     * @return 翻译结果（含 session_id, report, evidence, conclusion, suggested_follow_ups）
     */
    public Map<String, Object> process(String question, String sessionId, Map<String, Object> params, String scene) {
        long startTime = System.currentTimeMillis();

        // Step 1: 获取会话上下文
        SessionContext context = sessionManager.getOrCreate(sessionId);
        context.setScene(scene);
        applySuppliedParams(context, params);
        context.addHistoryEntry("user", question);

        // Step 2: 理解层 — 自然语言 → 查询计划
        log.info("[AgentOrchestrator] 理解层处理: question={}", question);
        QueryPlan plan = understander.understand(question, context);
        log.info("[AgentOrchestrator] 查询计划: intent={}, tools={}, clarify={}",
                plan.getIntent(), plan.getTools(), plan.getClarify());

        // 澄清分支：不做工具执行，直接生成追问文案
        if (QueryPlan.INTENT_CLARIFY.equals(plan.getIntent())) {
            context.setLastIntent(plan.getIntent());
            context.setLastClarifyParams(plan.getClarify());
            context.setLastTools(plan.getTools());
            context.setLastParams(plan.getParams());
            String clarifyMessage = presenter.present(question, List.of(), context);
            context.addHistoryEntry("assistant", clarifyMessage);
            sessionManager.save(context);
            persistTurn(context, question, clarifyMessage, plan, List.of());

            Map<String, Object> clarifyResponse = new LinkedHashMap<>();
            clarifyResponse.put("session_id", context.getSessionId());
            clarifyResponse.put("report", clarifyMessage);
            clarifyResponse.put("intent", plan.getIntent());
            clarifyResponse.put("clarify", plan.getClarify());
            clarifyResponse.put("tools", plan.getTools());
            clarifyResponse.put("query_plan", buildQueryPlanView(plan));
            clarifyResponse.put("evidence", List.of());
            clarifyResponse.put("conclusion", "");
            clarifyResponse.put("suggested_follow_ups", List.of());
            clarifyResponse.put("elapsed_ms", System.currentTimeMillis() - startTime);
            return clarifyResponse;
        }

        context.setLastIntent(plan.getIntent());
        context.setLastTools(plan.getTools());
        context.setLastParams(plan.getParams());

        // Step 3: 执行层 — 查询计划 → 工具执行（含依赖编排与降级）
        log.info("[AgentOrchestrator] 执行层处理");
        List<ExecutionResult> results = executor.execute(plan, context);

        // 缓存执行结果作为证据（追问复用，避免重复查询/推理）
        for (ExecutionResult result : results) {
            if (result.isSuccess() && result.getData() != null) {
                context.cacheEvidence(result.getToolName(), result.getData());
                cacheBusinessEntity(context, result);
            }
        }

        // Step 4: 表达层 — 工具结果 → 自然语言（部分失败时生成部分结论）
        log.info("[AgentOrchestrator] 表达层处理");
        String report = presenter.present(question, results, context);
        List<String> followUps = presenter.suggestFollowUps(question, results);

        // 保存回答到会话历史
        context.addHistoryEntry("assistant", report);

        // 保存会话
        sessionManager.save(context);
        persistTurn(context, question, report, plan, results);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[AgentOrchestrator] 处理完成: sessionId={}, elapsed={}ms", context.getSessionId(), elapsed);

        // 构建响应
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", context.getSessionId());
        response.put("report", report);
        response.put("intent", plan.getIntent());
        response.put("tools", plan.getTools());
        response.put("query_plan", buildQueryPlanView(plan));
        response.put("evidence", buildEvidenceSummary(results));
        response.put("conclusion", extractConclusion(results));
        response.put("suggested_follow_ups", followUps);
        response.put("elapsed_ms", elapsed);

        return response;
    }

    /**
     * 缓存业务实体（如归因分析的商品对象），供后续追问 / 澄清复用。
     * <p>
     * 依据工具自描述的输出契约（BUSINESS_ENTITY_ID / BUSINESS_ENTITY_NAME）通用提取，
     * 不再对具体工具名 / 输出键做字符串硬编码。
     */
    private void cacheBusinessEntity(SessionContext context, ExecutionResult result) {
        if (context == null || result == null || !result.isSuccess() || result.getData() == null) {
            return;
        }
        AgentTool tool = toolMap.get(result.getToolName());
        if (tool == null) {
            return;
        }
        Map<String, Object> entity = ToolOutputRenderer.businessEntity(tool, result.getData());
        Object id = entity.get("id");
        Object name = entity.get("name");
        if (id != null && !String.valueOf(id).isBlank()) {
            context.resolveParam("offering", id);
        }
        if (name != null && !String.valueOf(name).isBlank()) {
            context.cacheEvidence("lastOffering", name);
        }
    }

    /**
     * 构建查询计划视图（含 steps / clarify 契约字段，向前兼容）。
     */
    private Map<String, Object> buildQueryPlanView(QueryPlan plan) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("intent", plan.getIntent());
        view.put("tools", plan.getTools());
        view.put("params", plan.getParams());
        if (plan.getClarify() != null && !plan.getClarify().isEmpty()) {
            view.put("clarify", plan.getClarify());
        }
        return view;
    }

    /**
     * 构建证据摘要（结构化对象，供前端 EvidenceCard 直接渲染）。
     * <p>
     * 结构：{ count, items: [{label, value, contribution}], summary }。
     * <p>
     * 依据工具自描述输出契约（SUMMARY 角色字段）通用提取，旧工具回落至 nl_answer/answer。
     */
    private Map<String, Object> buildEvidenceSummary(List<ExecutionResult> results) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        List<ExecutionResult> success = results.stream().filter(ExecutionResult::isSuccess).toList();

        for (ExecutionResult r : success) {
            if (r.getData() == null) {
                continue;
            }
            AgentTool tool = toolMap.get(r.getToolName());
            List<Map<String, Object>> toolItems = ToolOutputRenderer.evidenceItems(tool, r.getData());
            items.addAll(toolItems);
        }

        evidence.put("count", items.size());
        evidence.put("items", items);
        // 存在高风险条目（highlight）时提升为告警级强调
        boolean hasHighlight = items.stream()
                .anyMatch(i -> Boolean.TRUE.equals(i.get("highlight")));
        evidence.put("severity", hasHighlight ? "high" : "info");
        evidence.put("summary", items.isEmpty() ? "" : "本次分析依据 " + items.size() + " 项数据得出");
        evidence.put("title", "结论依据");
        return evidence;
    }

    /**
     * 提取结论摘要。
     */
    private String extractConclusion(List<ExecutionResult> results) {
        for (ExecutionResult result : results) {
            if (result.isSuccess() && result.getData() != null) {
                AgentTool tool = toolMap.get(result.getToolName());
                String conclusion = ToolOutputRenderer.conclusion(tool, result.getData());
                if (conclusion != null && !conclusion.isBlank()) {
                    return conclusion;
                }
            }
        }
        return "";
    }

    /**
     * 将一轮对话（用户提问 + 助手回答）持久化到数据库（去旧留新：新链路自带落库）。
     * <p>
     * metadata 键与前端 {@code chatApi.restoreMessageMetadata} 对齐：
     * intent_type / stream_text / query_plan / evidence_summary / content_type / done，
     * 便于会话历史与会话切换时还原三阶产物。
     */
    private void persistTurn(SessionContext context, String question,
                             String assistantReply, QueryPlan plan,
                             List<ExecutionResult> results) {
        if (persistenceService.isEmpty()) {
            return;
        }
        try {
            ChatPersistenceService svc = persistenceService.get();
            String sessionId = context.getSessionId();
            String userId = "default";
            String title = question.length() > 50 ? question.substring(0, 50) : question;
            svc.getOrCreateSession(sessionId, userId, title);

            // 用户消息
            if (question != null && !question.isBlank()) {
                svc.saveMessage(sessionId, "user", question, "text");
            }

            // 助手回复（携带三阶产物 metadata，供历史还原）
            if (assistantReply != null && !assistantReply.isBlank()) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("intent_type", plan != null ? plan.getIntent() : "");
                meta.put("stream_text", assistantReply);
                meta.put("content_type", "chat");
                meta.put("done", true);
                if (plan != null) {
                    meta.put("query_plan", toJson(buildQueryPlanView(plan)));
                }
                if (results != null && !results.isEmpty()) {
                    meta.put("evidence_summary", toJson(buildEvidenceSummary(results)));
                }
                svc.saveMessage(sessionId, "assistant", assistantReply, "text", meta);
            }
            log.info("[AgentOrchestrator] 会话已持久化: sessionId={}", sessionId);
        } catch (Exception e) {
            // 持久化失败不影响对话主流程（仅记录）
            log.warn("[AgentOrchestrator] 会话持久化失败: {}", e.getMessage());
        }
    }

    /** 转 JSON 字符串，供 metadata 序列化（失败时退回原值）。 */
    private String toJson(Object value) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 将用户补充的结构化参数合并到会话的已澄清参数（resolvedParams）中。
     * <p>
     * 用于 CLARIFY 澄清回传闭环：用户在追问补充后，值经结构化 params 传入，
     * 比仅靠 LLM 从 question 二次抽取更可靠。屏蔽内部"操作指令"型 key（cancel/delete）。
     */
    private void applySuppliedParams(SessionContext context, Map<String, Object> params) {
        if (context == null || params == null || params.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (key == null || key.isBlank() || value == null) {
                continue;
            }
            if ("cancel".equals(key) || "delete".equals(key)) {
                continue;
            }
            context.resolveParam(key, value);
        }
    }

    /**
     * 流式处理（支持结构化补参）：与一次性接口共用理解/执行编排。
     * <p>
     * 真流式：事件经 {@link StreamEmitter} 在流水线推进过程中即时回调推送 ——
     * thinking 在理解完成后立刻下发，tool 事件随每个工具开始/结束即时下发，
     * 不再先行攒齐全部事件再集中发送（旧实现导致前端一次性收到整流，无渐进效果）。
     *
     * @param question  用户问题
     * @param sessionId 会话 ID（可选）
     * @param params    用户补充的结构化参数（CLARIFY 澄清回传），合并进 resolvedParams
     * @param emitter   事件发射器（每产生一个事件即回调一次）
     */
    public void processStream(String question, String sessionId, Map<String, Object> params, StreamEmitter emitter) {
        processStream(question, sessionId, params, null, emitter);
    }

    /**
     * 流式处理（支持结构化补参 + 助手场景）：与一次性接口共用理解/执行编排。
     * <p>
     * 真流式：事件经 {@link StreamEmitter} 在流水线推进过程中即时回调推送 ——
     * thinking 在理解完成后立刻下发，tool 事件随每个工具开始/结束即时下发，
     * 不再先行攒齐全部事件再集中发送（旧实现导致前端一次性收到整流，无渐进效果）。
     *
     * @param question  用户问题
     * @param sessionId 会话 ID（可选）
     * @param params    用户补充的结构化参数（CLARIFY 澄清回传），合并进 resolvedParams
     * @param scene     助手场景（"rd" = 产商品研发；null/空 = 默认运营）。驱动理解层分支，运营路径不受影响
     * @param emitter   事件发射器（每产生一个事件即回调一次）
     */
    public void processStream(String question, String sessionId, Map<String, Object> params, String scene, StreamEmitter emitter) {
        long startTime = System.currentTimeMillis();

        SessionContext context = sessionManager.getOrCreate(sessionId);
        context.setScene(scene);
        applySuppliedParams(context, params);
        context.addHistoryEntry("user", question);

        // 阶段事件①：理解中 —— 先于 LLM 理解调用推送，思考时间线即刻起表并读秒
        emitter.emit("thinking", Map.of(
                "steps", List.of(thinkingStep("intent", intentStepName(context),
                        intentStepDesc(context),
                        Map.of("input", Map.of("question", question))))
        ));

        List<QueryPlan> plans = understander.understandAll(question, context);
        if (plans == null || plans.isEmpty()) {
            emitter.emit("error", Map.of("errorMessage",
                    "无法理解您的需求，请换个说法重试。", "error", "无法理解"));
            return;
        }
        if (plans.size() > 1) {
            // 混合意图：多个子计划分别处理、分别作答
            processStreamMulti(question, plans, context, emitter, startTime);
            return;
        }
        QueryPlan plan = plans.get(0);
        // 阶段事件①′：理解完成，原地更新 intent 步骤（补输出：已识别业务意图）
        emitter.emit("thinking", Map.of(
                "steps", List.of(thinkingStep("intent", intentStepName(context),
                        intentStepDesc(context),
                        Map.of("input", Map.of("question", question),
                                "output", Map.of("summary", "已识别业务意图：" + actionDisplay(plan))))),
                "intent", plan.getIntent()
        ));
        // 阶段事件②：计划确认 —— 将内部「查询计划中间语言」翻译为业务可读的筛查方案
        // （取代原先透传 raw queryPlan 给前端渲染内部码卡片，避免对业务人员造成困惑）
        emitter.emit("thinking", Map.of(
                "steps", List.of(thinkingStep("plan", "执行方案",
                        buildReadablePlan(plan),
                        Map.of("input", planInputView(plan),
                                "workflow", buildWorkflow(plan),
                                "output", Map.of("summary", buildReadablePlan(plan))))),
                "intent", plan.getIntent()
        ));

        // 澄清分支：thinking → text（追问文案）→ done
        if (QueryPlan.INTENT_CLARIFY.equals(plan.getIntent())) {
            context.setLastIntent(plan.getIntent());
            context.setLastClarifyParams(plan.getClarify());
            context.setLastTools(plan.getTools());
            context.setLastParams(plan.getParams());
            // 阶段事件③：生成中 —— 表达层为 LLM 长调用，先推步骤保持反馈
            emitter.emit("thinking", Map.of(
                    "steps", List.of(thinkingStep("generate", "组织追问",
                            "正在生成补充信息的询问…",
                            Map.of("input", Map.of("question", question)))),
                    "intent", plan.getIntent()
            ));
            String clarifyMessage = presenter.present(question, List.of(), context);
            // 阶段事件③′：追问生成完成，原地更新 generate 步骤（补输出：追问文案）
            emitter.emit("thinking", Map.of(
                    "steps", List.of(thinkingStep("generate", "组织追问",
                            "正在生成补充信息的询问…",
                            Map.of("input", Map.of("question", question),
                                    "output", Map.of("summary", clarifyMessage)))),
                    "intent", plan.getIntent()
            ));
            context.addHistoryEntry("assistant", clarifyMessage);
            sessionManager.save(context);
            persistTurn(context, question, clarifyMessage, plan, List.of());
            emitTextEvents(emitter, clarifyMessage);
            emitter.emit("done", Map.of(
                    "session_id", context.getSessionId(),
                    "intent", plan.getIntent(),
                    "clarify", plan.getClarify(),
                    "conclusion", "",
                    "evidence", List.of(),
                    "suggested_follow_ups", List.of(),
                    "elapsed_ms", System.currentTimeMillis() - startTime
            ));
            return;
        }

        context.setLastIntent(plan.getIntent());
        context.setLastTools(plan.getTools());
        context.setLastParams(plan.getParams());

        // 执行层：每个工具开始/结束即时下发 tool 事件，并同步缓存证据
        List<ExecutionResult> results = executor.execute(plan, context, new Executor.StepListener() {
            @Override
            public void onStepStart(String toolName) {
                emitter.emit("tool", Map.of("name", toolName, "status", "running"));
            }

            @Override
            public void onStepComplete(ExecutionResult result) {
                emitter.emit("tool", buildToolEvent(result));
                if (result.isSuccess() && result.getData() != null) {
                    context.cacheEvidence(result.getToolName(), result.getData());
                    cacheBusinessEntity(context, result);
                }
            }
        });

        // 阶段事件③：生成中 —— 报告生成为 LLM 长调用，先推"生成回答"步骤保持渐进反馈
        emitter.emit("thinking", Map.of(
                "steps", List.of(thinkingStep("generate", "汇总结果",
                        "正在汇总筛查结论与处置建议…",
                        Map.of("input", planInputView(plan)))),
                "intent", plan.getIntent()
        ));
        String report = presenter.present(question, results, context);
        List<String> followUps = presenter.suggestFollowUps(question, results);
        // 阶段事件③′：报告生成完成，原地更新 generate 步骤（补输出：业务结论）
        String conclusionText = extractConclusion(results);
        emitter.emit("thinking", Map.of(
                "steps", List.of(thinkingStep("generate", "汇总结果",
                        "正在汇总筛查结论与处置建议…",
                        Map.of("input", planInputView(plan),
                                "output", Map.of("summary", conclusionText.isBlank() ? report : conclusionText)))),
                "intent", plan.getIntent()
        ));
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, report, plan, results);

        emitTextEvents(emitter, report);
        emitter.emit("done", Map.of(
                "session_id", context.getSessionId(),
                "intent", plan.getIntent(),
                "conclusion", extractConclusion(results),
                "evidence", buildEvidenceSummary(results),
                "suggested_follow_ups", followUps,
                "elapsed_ms", System.currentTimeMillis() - startTime
        ));
    }

    /**
     * 流式事件发射器：编排每产生一个事件即回调一次，由入口层（SSE）即时推送。
     */
    @FunctionalInterface
    public interface StreamEmitter {
        void emit(String event, Map<String, Object> data);
    }

    /**
     * 混合意图流式处理：多个子计划分别走「理解→执行→表达」独立链路、分别作答。
     * <p>
     * 每个子计划的思考步骤 id 加索引前缀（如 {@code 0_intent / 1_plan}），使前端时间线
     * 呈现为多条互不冲突的链（分别展示、互不污染）；各子答案按子计划顺序拼接成最终正文，
     * evidence / conclusion / follow_ups 跨子计划合并。
     *
     * @param question  用户问题
     * @param plans     多个子计划（size &gt; 1）
     * @param context   会话上下文
     * @param emitter   事件发射器
     * @param startTime 起始时间戳（用于 elapsed_ms）
     */
    private void processStreamMulti(String question, List<QueryPlan> plans,
                                    SessionContext context, StreamEmitter emitter, long startTime) {
        log.info("[AgentOrchestrator] 混合意图：{} 个子计划分别处理", plans.size());

        List<ExecutionResult> allResults = new ArrayList<>();
        List<String> subReports = new ArrayList<>();
        List<String> allFollowUps = new ArrayList<>();
        QueryPlan firstPlan = plans.get(0);

        for (int i = 0; i < plans.size(); i++) {
            QueryPlan plan = plans.get(i);
            String pre = i + "_";
            String intentLabel = actionDisplay(plan);
            // 分组标记：前端据此在每个子任务前插入小节标题与间距，视觉分段
            String segment = "① ② ③ ④ ⑤".split(" ")[i] + " " + intentLabel;

            // 阶段事件①：该子任务的意图识别（带索引前缀 id，独立成链；首步骤携带 segment 供前端分组）
            emitter.emit("thinking", Map.of(
                    "steps", List.of(thinkingStep(pre + "intent", intentStepName(context),
                            intentStepDesc(context),
                            Map.of("segment", segment,
                                    "input", Map.of("question", question)))),
                    "intent", plan.getIntent()
            ));
            emitter.emit("thinking", Map.of(
                    "steps", List.of(thinkingStep(pre + "intent", intentStepName(context),
                            intentStepDesc(context),
                            Map.of("segment", segment,
                                    "input", Map.of("question", question),
                                    "output", Map.of("summary", "已识别业务意图：" + intentLabel)))),
                    "intent", plan.getIntent()
            ));
            // 阶段事件②：该子任务的执行方案
            emitter.emit("thinking", Map.of(
                    "steps", List.of(thinkingStep(pre + "plan", "执行方案",
                            buildReadablePlan(plan),
                            Map.of("segment", segment,
                                    "input", planInputView(plan),
                                    "workflow", buildWorkflow(plan),
                                    "output", Map.of("summary", buildReadablePlan(plan))))),
                    "intent", plan.getIntent()
            ));

            context.setLastIntent(plan.getIntent());
            context.setLastTools(plan.getTools());
            context.setLastParams(plan.getParams());

            // 执行层：该子计划工具逐步派发 tool 事件并缓存证据
            List<ExecutionResult> subResults = executeWithEvents(plan, context, emitter, segment);
            allResults.addAll(subResults);

            // 阶段事件③/③′：该子任务汇总
            emitter.emit("thinking", Map.of(
                    "steps", List.of(thinkingStep(pre + "generate", "汇总结果",
                            "正在汇总该子任务结论…",
                            Map.of("segment", segment,
                                    "input", planInputView(plan)))),
                    "intent", plan.getIntent()
            ));
            String subReport = presenter.present(question, subResults, context);
            String subConclusion = extractConclusion(subResults);
            emitter.emit("thinking", Map.of(
                    "steps", List.of(thinkingStep(pre + "generate", "汇总结果",
                            "正在汇总该子任务结论…",
                            Map.of("segment", segment,
                                    "input", planInputView(plan),
                                    "output", Map.of("summary", subConclusion.isBlank() ? subReport : subConclusion)))),
                    "intent", plan.getIntent()
            ));

            subReports.add(subReport);
            allFollowUps.addAll(presenter.suggestFollowUps(question, subResults));
        }

        // 合并各子答案为最终正文（分别作答）
        String report = String.join("\n\n", subReports);
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, report, firstPlan, allResults);

        emitTextEvents(emitter, report);
        emitter.emit("done", Map.of(
                "session_id", context.getSessionId(),
                "intent", firstPlan.getIntent(),
                "conclusion", extractConclusion(allResults),
                "evidence", buildEvidenceSummary(allResults),
                "suggested_follow_ups", allFollowUps,
                "elapsed_ms", System.currentTimeMillis() - startTime
        ));
    }

    /**
     * 执行单个子计划：逐步派发 tool 事件并同步缓存证据/业务实体。
     *
     * @param segment 分组标记（前端归组展示，tool 事件同样携带）
     */
    private List<ExecutionResult> executeWithEvents(QueryPlan plan, SessionContext context,
                                                    StreamEmitter emitter, String segment) {
        return executor.execute(plan, context, new Executor.StepListener() {
            @Override
            public void onStepStart(String toolName) {
                emitter.emit("tool", Map.of("name", toolName, "status", "running", "segment", segment));
            }

            @Override
            public void onStepComplete(ExecutionResult result) {
                Map<String, Object> event = buildToolEvent(result);
                event.put("segment", segment);
                emitter.emit("tool", event);
                if (result.isSuccess() && result.getData() != null) {
                    context.cacheEvidence(result.getToolName(), result.getData());
                    cacheBusinessEntity(context, result);
                }
            }
        });
    }

    /**
     * 工具执行结果 → tool 事件载荷（done/error 终态）。
     * <p>
     * 携带 input（该步骤实际入参）与 output（结构化业务摘要，供思考过程展示
     * 「输入 → 动作 → 输出」链条，让业务人员一眼看清该环节做了什么）。
     */
    private Map<String, Object> buildToolEvent(ExecutionResult result) {
        Map<String, Object> toolEvent = new LinkedHashMap<>();
        toolEvent.put("name", result.getToolName());
        toolEvent.put("status", result.isSuccess() ? "done" : "error");
        toolEvent.put("durationMs", result.getExecutionTimeMs());
        toolEvent.put("input", toolInputView(result));
        if (result.isSuccess() && result.getData() != null) {
            AgentTool tool = toolMap.get(result.getToolName());
            toolEvent.put("summary", ToolOutputRenderer.summary(tool, result.getData()));
            toolEvent.put("output", buildToolOutput(result));
        } else if (!result.isSuccess()) {
            toolEvent.put("errorMessage", result.getErrorMessage());
        }
        return toolEvent;
    }

    /**
     * 工具步骤入参视图：仅取工具自描述 {@code getParams()} 声明的"必要入参"，
     * 过滤掉 direct 兜底透传的无关 plan.params 噪声（如 指标/时间/业务意图 等，
     * 这些对工具执行并无作用），避免同一套参数在思考时间线多处重复。
     */
    private Map<String, Object> toolInputView(ExecutionResult result) {
        Map<String, Object> params = result.getParams();
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        AgentTool tool = toolMap.get(result.getToolName());
        if (tool == null) {
            return sanitizeParams(params);
        }
        List<ToolParam> declared = tool.getParams();
        if (declared == null || declared.isEmpty()) {
            return sanitizeParams(params);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (ToolParam tp : declared) {
            if (tp == null || tp.getName() == null) {
                continue;
            }
            Object value = params.get(tp.getName());
            if (value == null || str(value).isBlank()) {
                continue;
            }
            out.put(tp.getName(), value instanceof Object[] a ? java.util.Arrays.asList(a) : value);
        }
        return out;
    }

    /**
     * 参数清理：仅透传可序列化的业务参数，丢掉 null/空值/内部噪声键。
     */
    private Map<String, Object> sanitizeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() == "") {
                continue;
            }
            out.put(e.getKey(), e.getValue() instanceof Object[] a
                    ? java.util.Arrays.asList(a) : e.getValue());
        }
        return out;
    }

    /**
     * 结构化输出摘要：依据工具自描述输出契约（SUMMARY / COUNT / BUSINESS_ENTITY_NAME 等角色）
     * 通用组装业务可读的摘要与关键指标，供思考过程步骤渲染为「输出：…」。
     * <p>
     * 不再对具体工具名 / 输出键做字符串硬编码（counts 与实体名等键由工具契约声明，
     * 兼容前端既有的 output 键契约）。
     */
    private Map<String, Object> buildToolOutput(ExecutionResult result) {
        if (result == null || result.getData() == null) {
            return new LinkedHashMap<>();
        }
        AgentTool tool = toolMap.get(result.getToolName());
        if (tool == null) {
            return Map.of("summary", ToolOutputRenderer.summary(null, result.getData()));
        }
        return ToolOutputRenderer.outputEntries(tool, result.getData());
    }

    /** 构造业务化思考步骤载荷（供前端时间线渲染为「动作说明 + 输入」）。 */
    private static Map<String, Object> thinkingStep(String id, String title, String content, Map<String, Object> extra) {
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

    /** action 内部码 → 业务展示名（含产商品研发场景动作）。 */
    private static final Map<String, String> ACTION_DISPLAY = Map.of(
            "query", "数据查询",
            "root_cause", "异动归因",
            "risk_audit", "风险稽核",
            "online_check", "在架检查",
            "ops_monitor", "运营监控",
            "compare", "对比分析",
            "generate", "配置生成",
            "parse", "方案解析",
            "compliance", "合规校验",
            "discover", "配置查询"
    );

    /** intent 内部码 → 兜底业务展示名（params 缺 action 时使用，含研发/对话/澄清意图）。 */
    private static final Map<String, String> INTENT_DISPLAY = Map.ofEntries(
            Map.entry("SPARQL_QUERY", "数据查询"),
            Map.entry("SWRL_INFER", "推理分析"),
            Map.entry("product_ops_policy", "风险稽核"),
            Map.entry("product_ops_reason", "异动归因"),
            Map.entry("RD_CONFIG_CHAT", "对话配置"),
            Map.entry("RD_FILE_PARSE", "方案解析"),
            Map.entry("RD_COMPLIANCE", "合规校验"),
            Map.entry("RD_CONFIG_DISCOVER", "配置查询"),
            Map.entry("RD_SCHEME_COMPARE", "方案对比"),
            Map.entry("CHAT", "通用对话"),
            Map.entry("CLARIFY", "待补充信息"),
            Map.entry("REUSE_EVIDENCE", "证据复用")
    );

    /** 工具内部码 → 处理流程中可读的动作说明（供「制定方案」展示完整处理路径，含研发工具）。 */
    private static final Map<String, String> TOOL_STEP_DISPLAY = Map.of(
            "sparql_query", "检索经营事实",
            "swrl_root_cause", "执行异动归因推理",
            "swrl_risk_audit", "全量扫描风险并分级",
            "rule_explain", "解释业务规则",
            "ontology_explain", "解释本体概念",
            "rd_config_chat", "对话配置生成",
            "rd_file_parse", "方案文档解析",
            "rd_compliance", "合规校验",
            "rd_config_discover", "历史配置查询",
            "rd_scheme_compare", "多方案对比"
    );

    /**
     * 将查询计划翻译为业务可读的方案说明，取代透传内部码 queryPlan 给前端渲染卡片。
     */
    private static String buildReadablePlan(QueryPlan plan) {
        if (plan == null) {
            return "依据您的需求制定分析方案";
        }
        Map<String, Object> params = plan.getParams() != null ? plan.getParams() : Map.of();
        String action = str(params.get("action"));
        String actionLabel = ACTION_DISPLAY.getOrDefault(action, action);
        if (actionLabel == null || actionLabel.isBlank() || actionLabel.equals(action)) {
            actionLabel = INTENT_DISPLAY.getOrDefault(plan.getIntent(), "分析");
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
        sb.append("。方案共 ").append(planStepCount(plan)).append(" 步：");
        List<Map<String, Object>> workflow = buildWorkflow(plan);
        if (workflow.isEmpty()) {
            sb.append("直接生成结论");
        } else {
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

    /** 方案包含的可执行步骤数（无执行步骤也算 1 步用于文案通顺）。 */
    private static int planStepCount(QueryPlan plan) {
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
    private static List<Map<String, Object>> buildWorkflow(QueryPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> tools = new ArrayList<>();
        if (plan.getSteps() != null && !plan.getSteps().isEmpty()) {
            for (com.sitech.prodai.service.agent.model.ExecStep step : plan.getSteps()) {
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
            item.put("label", TOOL_STEP_DISPLAY.getOrDefault(tool, tool));
            out.add(item);
        }
        return out;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 是否为产商品研发场景（scene=rd）。 */
    private static boolean isRdScene(SessionContext context) {
        return context != null && "rd".equals(context.getScene());
    }

    /** 意图识别步骤标题：研发场景为「识别配置需求」，运营场景为「识别分析需求」。 */
    private static String intentStepName(SessionContext context) {
        return isRdScene(context) ? "识别配置需求" : "识别分析需求";
    }

    /** 意图识别步骤描述：研发场景围绕配置要素，运营场景围绕筛查目标。 */
    private static String intentStepDesc(SessionContext context) {
        return isRdScene(context)
                ? "正在理解您的需求，识别业务意图与配置要素…"
                : "正在理解您的需求，识别业务意图与筛查目标…";
    }

    /** 供意图识别步骤输出展示的业务动作名（如 异动归因 / 风险稽核 / 对话配置）。 */
    private static String actionDisplay(QueryPlan plan) {
        if (plan == null) {
            return "分析";
        }
        Map<String, Object> params = plan.getParams() != null ? plan.getParams() : Map.of();
        String action = str(params.get("action"));
        String label = ACTION_DISPLAY.getOrDefault(action, action);
        if (label == null || label.isBlank() || label.equals(action)) {
            label = INTENT_DISPLAY.getOrDefault(plan.getIntent(), "分析");
        }
        return (label == null || label.isBlank()) ? "分析" : label;
    }

    /**
     * 供方案步骤「输入」展示的参数子集：剔除原始问题与内部噪声键，仅保留业务可读的参数。
     */
    private static Map<String, Object> planInputView(QueryPlan plan) {
        Map<String, Object> params = plan.getParams() != null ? plan.getParams() : Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String key = e.getKey();
            if ("question".equals(key) || key == null || key.isBlank()
                    || e.getValue() == null || str(e.getValue()).isBlank()) {
                continue;
            }
            out.put(key, e.getValue());
        }
        return out;
    }

    /**
     * 将正文按块切分为多个 text 事件（打字机效果），末尾追加 text_done。
     */
    private void emitTextEvents(StreamEmitter emitter, String text) {
        int chunkSize = 48;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            emitter.emit("text", Map.of("chunk", text.substring(i, end)));
        }
        emitter.emit("text_done", Map.of());
    }

    /**
     * 获取理解层（供外部使用）。
     */
    public Understander getUnderstander() {
        return understander;
    }

    /**
     * 获取执行层（供外部使用）。
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * 获取表达层（供外部使用）。
     */
    public Presenter getPresenter() {
        return presenter;
    }

    /**
     * 获取会话管理器（供外部使用）。
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }
}