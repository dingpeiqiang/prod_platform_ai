package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.ChatPersistenceService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public AgentOrchestrator(Understander understander,
                             Executor executor,
                             Presenter presenter,
                             SessionManager sessionManager,
                             Optional<ChatPersistenceService> persistenceService) {
        this.understander = understander;
        this.executor = executor;
        this.presenter = presenter;
        this.sessionManager = sessionManager;
        this.persistenceService = persistenceService;
    }

    /**
     * 处理一次完整的翻译请求。
     *
     * @param question  用户问题
     * @param sessionId 会话 ID（可选，null 时创建新会话）
     * @return 翻译结果（含 session_id, report, evidence, conclusion, suggested_follow_ups）
     */
    public Map<String, Object> process(String question, String sessionId) {
        return process(question, sessionId, null);
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
        long startTime = System.currentTimeMillis();

        // Step 1: 获取会话上下文
        SessionContext context = sessionManager.getOrCreate(sessionId);
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
     */
    private void cacheBusinessEntity(SessionContext context, ExecutionResult result) {
        if ("swrl_root_cause".equals(result.getToolName()) && result.getData() != null) {
            Object offeringId = result.getData().get("offeringId");
            Object offeringName = result.getData().get("offeringName");
            if (offeringId != null && !String.valueOf(offeringId).isBlank()) {
                context.resolveParam("offering", offeringId);
            }
            if (offeringName != null && !String.valueOf(offeringName).isBlank()) {
                context.cacheEvidence("lastOffering", offeringName);
            }
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
     */
    private Map<String, Object> buildEvidenceSummary(List<ExecutionResult> results) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        List<ExecutionResult> success = results.stream().filter(ExecutionResult::isSuccess).toList();

        for (ExecutionResult r : success) {
            if (r.getData() == null) {
                continue;
            }
            Object raw = r.getData().getOrDefault("nl_answer",
                    r.getData().getOrDefault("answer", null));
            Object contribution = r.getData().get("contribution");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", r.getToolName());
            item.put("value", raw != null ? String.valueOf(raw) : "执行完成");
            if (contribution != null) {
                item.put("contribution", contribution);
            }
            items.add(item);
        }

        evidence.put("count", items.size());
        evidence.put("items", items);
        evidence.put("summary", success.isEmpty() ? "" : "工具执行完成，共 " + items.size() + " 项证据");
        evidence.put("title", "翻译层证据摘要");
        return evidence;
    }

    /**
     * 提取结论摘要。
     */
    private String extractConclusion(List<ExecutionResult> results) {
        for (ExecutionResult result : results) {
            if (result.isSuccess() && result.getData() != null) {
                Object conclusion = result.getData().get("conclusion");
                if (conclusion != null) {
                    return String.valueOf(conclusion);
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
        long startTime = System.currentTimeMillis();

        SessionContext context = sessionManager.getOrCreate(sessionId);
        applySuppliedParams(context, params);
        context.addHistoryEntry("user", question);

        QueryPlan plan = understander.understand(question, context);
        emitter.emit("thinking", Map.of(
                "steps", List.of(
                        Map.of("label", "正在理解您的需求..."),
                        Map.of("label", "已确认查询计划", "meta", Map.of("intent", plan.getIntent()))
                ),
                "intent", plan.getIntent(),
                "queryPlan", buildQueryPlanView(plan)
        ));

        // 澄清分支：thinking → text（追问文案）→ done
        if (QueryPlan.INTENT_CLARIFY.equals(plan.getIntent())) {
            context.setLastIntent(plan.getIntent());
            context.setLastClarifyParams(plan.getClarify());
            context.setLastTools(plan.getTools());
            context.setLastParams(plan.getParams());
            String clarifyMessage = presenter.present(question, List.of(), context);
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

        String report = presenter.present(question, results, context);
        List<String> followUps = presenter.suggestFollowUps(question, results);
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
     * 工具执行结果 → tool 事件载荷（done/error 终态）。
     */
    private Map<String, Object> buildToolEvent(ExecutionResult result) {
        Map<String, Object> toolEvent = new LinkedHashMap<>();
        toolEvent.put("name", result.getToolName());
        toolEvent.put("status", result.isSuccess() ? "done" : "error");
        toolEvent.put("durationMs", result.getExecutionTimeMs());
        if (result.isSuccess() && result.getData() != null) {
            toolEvent.put("summary", result.getData().getOrDefault("nl_answer",
                    result.getData().getOrDefault("answer", "执行完成")));
        } else if (!result.isSuccess()) {
            toolEvent.put("errorMessage", result.getErrorMessage());
        }
        return toolEvent;
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