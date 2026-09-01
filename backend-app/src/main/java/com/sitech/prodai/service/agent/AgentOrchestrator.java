package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.ChatPersistenceService;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ThinkingCopy;
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
    private final Optional<LlmService> llmService;

    /** 已注册工具索引：工具名 → 工具（供工具自描述元数据查询） */
    private final Map<String, AgentTool> toolMap;

    public AgentOrchestrator(Understander understander,
                             Executor executor,
                             Presenter presenter,
                             SessionManager sessionManager,
                             Optional<ChatPersistenceService> persistenceService,
                             Optional<LlmService> llmService,
                             List<AgentTool> tools) {
        this.understander = understander;
        this.executor = executor;
        this.presenter = presenter;
        this.sessionManager = sessionManager;
        this.persistenceService = persistenceService;
        this.llmService = llmService;
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
            if (plan.getClarifyContracts() != null && !plan.getClarifyContracts().isEmpty()) {
                clarifyResponse.put("clarify_contracts", plan.getClarifyContracts());
            }
            clarifyResponse.put("tools", plan.getTools());
            clarifyResponse.put("query_plan", buildQueryPlanView(plan));
            clarifyResponse.put("conclusion", "");
            clarifyResponse.put("suggested_follow_ups", List.of());
            clarifyResponse.put("elapsed_ms", System.currentTimeMillis() - startTime);
            return clarifyResponse;
        }

        // 确认分支（U2）：需求存在多种解读，暂停等用户在候选卡片中选定
        if (QueryPlan.INTENT_CONFIRM.equals(plan.getIntent())) {
            context.setLastIntent(plan.getIntent());
            context.setLastTools(plan.getTools());
            context.setLastParams(plan.getParams());
            String confirmMessage = buildConfirmMessage(plan.getCandidates());
            context.addHistoryEntry("assistant", confirmMessage);
            sessionManager.save(context);
            persistTurn(context, question, confirmMessage, plan, List.of());

            Map<String, Object> confirmResponse = new LinkedHashMap<>();
            confirmResponse.put("session_id", context.getSessionId());
            confirmResponse.put("report", confirmMessage);
            confirmResponse.put("intent", plan.getIntent());
            confirmResponse.put("candidates", plan.getCandidates());
            confirmResponse.put("tools", plan.getTools());
            confirmResponse.put("query_plan", buildQueryPlanView(plan));
            confirmResponse.put("conclusion", "");
            confirmResponse.put("suggested_follow_ups", List.of());
            confirmResponse.put("elapsed_ms", System.currentTimeMillis() - startTime);
            return confirmResponse;
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
        List<String> followUps = presenter.suggestFollowUps(question, results, context);

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
        // 工单号随证据缓存：工单卡裸操作（提交/删除/复制无工单号话术）供理解层从上下文补齐，
        // 避免 LLM 从话术中抽不到工单号时幻觉编造。
        // 注意：rd_draft_manage 的提交回执缓存的是「刚操作完」的工单号，下一轮参数合并时
        // 该缓存会被 LLM 显式抽取 / 前端结构化参数覆盖（putIfAbsent 语义），仅在 LLM 未抽取时兜底
        Object woId = firstNonNull(result.getData().get("work_order_id"), result.getData().get("workOrderId"));
        if (woId != null && !String.valueOf(woId).isBlank() && !"null".equals(String.valueOf(woId))) {
            context.resolveParam("work_order_id", String.valueOf(woId));
            context.cacheEvidence("lastWorkOrderId", String.valueOf(woId));
        }
        // 提交成功后不再延续单工单号语义：清掉单号缓存，避免下一轮裸「提交」时
        // LLM 被残留单号误导而漏掉其他待提交工单（批量语义由提示词 + 会话工单上下文驱动）
        if (result.getData().get("action") instanceof String act && "submit".equals(act)
                && Boolean.TRUE.equals(result.getData().get("success"))) {
            context.getResolvedParams().remove("work_order_id");
        }
        // 修改成功后缓存最新资费名称：多轮增量修改（如下一轮「月费改成 59」）时
        // LLM/工具沿用最新名称，避免用旧值覆盖
        if (result.getData().get("action") instanceof String act2 && "update".equals(act2)
                && Boolean.TRUE.equals(result.getData().get("success"))
                && result.getData().get("changed_fields") instanceof Map<?, ?> cf
                && cf.get("offeringName") != null && !String.valueOf(cf.get("offeringName")).isBlank()) {
            context.resolveParam("offering_name", String.valueOf(cf.get("offeringName")));
            context.cacheEvidence("lastOfferingName", String.valueOf(cf.get("offeringName")));
        }
    }

    /** 取首个非空值（工具输出键兜底）。 */
    private Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null && !String.valueOf(v).isBlank() && !"null".equals(String.valueOf(v))) {
                return v;
            }
        }
        return null;
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
        if (plan.getClarifyContracts() != null && !plan.getClarifyContracts().isEmpty()) {
            view.put("clarify_contracts", plan.getClarifyContracts());
        }
        if (plan.getCandidates() != null && !plan.getCandidates().isEmpty()) {
            view.put("candidates", plan.getCandidates());
        }
        return view;
    }

    /**
     * 确认分支文案（U2）：LLM 生成歧义确认话术，失败时回退固定模板。
     */
    private String buildConfirmMessage(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "您的需求存在多种理解，请告诉我您想做哪一个。";
        }
        String generated = llmConfirmMessage(candidates);
        if (generated != null && !generated.isBlank()) {
            return generated;
        }
        StringBuilder sb = new StringBuilder("您的需求可能有以下几种理解，请确认想执行哪一种：\n");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append(i + 1).append(". ").append(candidates.get(i)).append('\n');
        }
        return sb.toString().trim();
    }

    /** LLM 生成歧义确认话术；不可用/失败返回 null（调用方回退模板）。 */
    private String llmConfirmMessage(List<String> candidates) {
        try {
            if (llmService.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("用户需求存在多种合理解读，需要向用户确认。候选解读：\n");
            for (int i = 0; i < candidates.size(); i++) {
                sb.append(i + 1).append(". ").append(candidates.get(i)).append('\n');
            }
            sb.append("\n请用一句自然、友好的中文请用户确认想执行哪一种。只输出确认话术本身，不要输出其他内容。");
            String generated = llmService.get().completePrompt(sb.toString());
            if (generated != null && !generated.isBlank()) {
                return generated.trim();
            }
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 确认话术 LLM 生成失败，回退模板: {}", e.getMessage());
        }
        return null;
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
     * intent_type / stream_text / query_plan / content_type / done /
     * reasoning_full（思考时间线）/ tool_results（工具卡片输入输出）/
     * clarify（澄清参数列表）/ clarify_contracts（澄清契约）/ candidates（确认候选），
     * 便于会话历史与会话切换时完整还原消息快照（与实时会话一致）。
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
                    // 思考时间线快照：与实时 reasoning 步骤同构（intent/plan/tool/generate），
                    // 前端 normalizeReasoningList 直接消费，保证历史回放与实时渲染一致
                    meta.put("reasoning_full", toJson(buildReasoningSnapshot(context, plan, results, assistantReply)));
                    // 澄清分支：持久化追问参数列表与契约（实时 done 事件携带，历史回放等量还原）
                    if (plan.getClarify() != null && !plan.getClarify().isEmpty()) {
                        meta.put("clarify", toJson(plan.getClarify()));
                    }
                    if (plan.getClarifyContracts() != null && !plan.getClarifyContracts().isEmpty()) {
                        meta.put("clarify_contracts", toJson(plan.getClarifyContracts()));
                    }
                    // 确认分支（U2）：持久化歧义候选解读列表（实时 done 事件携带）
                    if (plan.getCandidates() != null && !plan.getCandidates().isEmpty()) {
                        meta.put("candidates", toJson(plan.getCandidates()));
                    }
                }
                // 工具执行卡片快照：name/status/summary/input/output，供历史还原 toolResults
                if (results != null && !results.isEmpty()) {
                    meta.put("tool_results", toJson(results.stream().map(this::buildToolResultSnapshot).toList()));
                }
                svc.saveMessage(sessionId, "assistant", assistantReply, "text", meta);
            }
            log.info("[AgentOrchestrator] 会话已持久化: sessionId={}", sessionId);
        } catch (Exception e) {
            // 持久化失败不影响对话主流程（仅记录）
            log.warn("[AgentOrchestrator] 会话持久化失败: {}", e.getMessage());
        }
    }

    /**
     * 构建本轮思考时间线快照：理解 → 计划 → 工具 → 汇总，与实时 SSE thinking/tool 步骤同构。
     * <p>
     * 实时流包含 intent / plan / tool / generate 四类步骤，历史回放须等量还原，
     * 否则历史会话的思考时间线比实时会话短（快照不完整）。
     */
    private List<Map<String, Object>> buildReasoningSnapshot(SessionContext context, QueryPlan plan,
                                                             List<ExecutionResult> results, String report) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (plan == null) {
            return steps;
        }
        // ① 意图识别
        Map<String, Object> intentStep = new LinkedHashMap<>();
        intentStep.put("id", "intent");
        intentStep.put("type", "thinking");
        intentStep.put("title", intentStepName(context));
        intentStep.put("content", intentStepDesc(context));
        intentStep.put("status", "done");
        intentStep.put("category", "understand");
        intentStep.put("goal", "先听懂您要做什么，再决定怎么办");
        intentStep.put("input", Map.of("question", ""));
        intentStep.put("output", Map.of("summary", "已明确：本次要执行「" + actionDisplay(plan) + "」"));
        steps.add(intentStep);
        // ② 处理方案
        Map<String, Object> planStep = new LinkedHashMap<>();
        planStep.put("id", "plan");
        planStep.put("type", "thinking");
        planStep.put("title", "定下处理方案");
        planStep.put("content", buildReadablePlan(plan));
        planStep.put("status", "done");
        planStep.put("category", "understand");
        planStep.put("goal", ThinkingCopy.intentGoal(plan.getIntent()));
        planStep.put("input", planInputView(plan));
        planStep.put("workflow", buildWorkflow(plan));
        planStep.put("output", Map.of("summary", buildReadablePlan(plan)));
        steps.add(planStep);
        // ③ 工具步骤：与实时 tool 事件同构（title/goal/manualHint/input/output/elapsed）
        if (results != null) {
            for (ExecutionResult result : results) {
                Map<String, Object> toolEvent = buildToolEvent(result);
                Map<String, Object> toolStep = new LinkedHashMap<>();
                toolStep.put("id", "tool_" + result.getToolName());
                toolStep.put("type", "tool");
                toolStep.put("title", toolEvent.getOrDefault("title", result.getToolName()));
                toolStep.put("goal", toolEvent.get("goal"));
                toolStep.put("manualHint", toolEvent.get("manualHint"));
                toolStep.put("status", result.isSuccess() ? "done" : "error");
                toolStep.put("elapsed", result.getExecutionTimeMs() / 1000.0);
                toolStep.put("result", result.isSuccess()
                        ? toolEvent.getOrDefault("summary", "执行完成")
                        : toolEvent.getOrDefault("errorMessage", "执行失败"));
                Map<String, Object> io = new LinkedHashMap<>();
                io.put("input", toolEvent.get("input"));
                io.put("output", toolEvent.get("output"));
                toolStep.put("io", io);
                steps.add(toolStep);
            }
        }
        // ④ 汇总步骤：实时流的 generate 步骤（含结论输出），历史回放等量还原
        Map<String, Object> generateStep = new LinkedHashMap<>();
        generateStep.put("id", "generate");
        generateStep.put("type", "thinking");
        generateStep.put("title", "汇总结果");
        generateStep.put("content", "正在汇总筛查结论与处置建议…");
        generateStep.put("status", "done");
        generateStep.put("goal", "把各环节结果整合成您能直接使用的结论与建议");
        generateStep.put("input", planInputView(plan));
        generateStep.put("output", Map.of("summary", report != null && !report.isBlank() ? report : "已完成"));
        steps.add(generateStep);
        return steps;
    }

    /**
     * 构建单个工具执行结果快照（与前端 tool 事件 toolEntry 字段对齐）。
     * <p>
     * 补齐 title/goal/manualHint（与实时 buildToolEvent 同源），保证历史回放的
     * 工具卡片文案与实时会话一致；前端 restoreMessageMetadata 映射为 toolResults。
     */
    private Map<String, Object> buildToolResultSnapshot(ExecutionResult result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", result.getToolName());
        snapshot.put("status", result.isSuccess() ? "done" : "error");
        snapshot.put("elapsedMs", result.getExecutionTimeMs());
        // 与实时 tool 事件同源的业务文案（title/goal/manualHint）
        Map<String, Object> toolEvent = buildToolEvent(result);
        if (toolEvent.containsKey("title")) {
            snapshot.put("title", toolEvent.get("title"));
        }
        if (toolEvent.containsKey("goal")) {
            snapshot.put("goal", toolEvent.get("goal"));
        }
        if (toolEvent.containsKey("manualHint")) {
            snapshot.put("manualHint", toolEvent.get("manualHint"));
        }
        if (result.isSuccess() && result.getData() != null) {
            snapshot.put("summary", toolEvent.getOrDefault("summary", ""));
            snapshot.put("input", toolEvent.getOrDefault("input", Map.of()));
            snapshot.put("output", toolEvent.getOrDefault("output", Map.of()));
        } else if (!result.isSuccess()) {
            snapshot.put("errorMessage", result.getErrorMessage());
        }
        return snapshot;
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
                        Map.of("goal", "先听懂您要做什么，再决定怎么办",
                                "input", Map.of("question", question))))
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
        // 阶段事件①′：理解完成，原地更新 intent 步骤（补输出：已明确的业务动作）
        emitter.emit("thinking", Map.of(
                "steps", List.of(thinkingStep("intent", intentStepName(context),
                        intentStepDesc(context),
                        Map.of("goal", "先听懂您要做什么，再决定怎么办",
                                "input", Map.of("question", question),
                                "output", Map.of("summary", "已明确：本次要执行「" + actionDisplay(plan) + "」")))),
                "intent", plan.getIntent()
        ));
        // 阶段事件②：计划确认 —— 将内部「查询计划中间语言」翻译为业务可读的筛查方案
        // （取代原先透传 raw queryPlan 给前端渲染内部码卡片，避免对业务人员造成困惑）
        emitter.emit("thinking", Map.of(
                "steps", List.of(thinkingStep("plan", "定下处理方案",
                        buildReadablePlan(plan),
                        Map.of("goal", ThinkingCopy.intentGoal(plan.getIntent()),
                                "input", planInputView(plan),
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
                            Map.of("goal", "信息不全时先问清楚，避免答非所问",
                                    "input", Map.of("question", question)))),
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
            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("session_id", context.getSessionId());
            donePayload.put("intent", plan.getIntent());
            donePayload.put("clarify", plan.getClarify());
            if (plan.getClarifyContracts() != null && !plan.getClarifyContracts().isEmpty()) {
                donePayload.put("clarify_contracts", plan.getClarifyContracts());
            }
            donePayload.put("conclusion", "");
            donePayload.put("suggested_follow_ups", List.of());
            donePayload.put("elapsed_ms", System.currentTimeMillis() - startTime);
            emitter.emit("done", donePayload);
            return;
        }

        // 确认分支（U2）：需求存在多种解读，暂停等用户在候选卡片中选定
        if (QueryPlan.INTENT_CONFIRM.equals(plan.getIntent())) {
            context.setLastIntent(plan.getIntent());
            context.setLastTools(plan.getTools());
            context.setLastParams(plan.getParams());
            String confirmMessage = buildConfirmMessage(plan.getCandidates());
            context.addHistoryEntry("assistant", confirmMessage);
            sessionManager.save(context);
            persistTurn(context, question, confirmMessage, plan, List.of());
            emitTextEvents(emitter, confirmMessage);
            emitter.emit("done", Map.of(
                    "session_id", context.getSessionId(),
                    "intent", plan.getIntent(),
                    "candidates", plan.getCandidates() != null ? plan.getCandidates() : List.of(),
                    "conclusion", "",
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
                        Map.of("goal", "把各环节结果整合成您能直接使用的结论与建议",
                                "input", planInputView(plan)))),
                "intent", plan.getIntent()
        ));
        String report = presenter.present(question, results, context);
        List<String> followUps = presenter.suggestFollowUps(question, results, context);
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
                                    "goal", "先听懂您要做什么，再决定怎么办",
                                    "input", Map.of("question", question)))),
                    "intent", plan.getIntent()
            ));
            emitter.emit("thinking", Map.of(
                    "steps", List.of(thinkingStep(pre + "intent", intentStepName(context),
                            intentStepDesc(context),
                            Map.of("segment", segment,
                                    "goal", "先听懂您要做什么，再决定怎么办",
                                    "input", Map.of("question", question),
                                    "output", Map.of("summary", "已明确：本次要执行「" + intentLabel + "」")))),
                    "intent", plan.getIntent()
            ));
            // 阶段事件②：该子任务的执行方案
            emitter.emit("thinking", Map.of(
                    "steps", List.of(thinkingStep(pre + "plan", "定下处理方案",
                            buildReadablePlan(plan),
                            Map.of("segment", segment,
                                    "goal", ThinkingCopy.intentGoal(plan.getIntent()),
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
                                    "goal", "把各环节结果整合成您能直接使用的结论与建议",
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
            allFollowUps.addAll(presenter.suggestFollowUps(question, subResults, context));
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
     * 携带 input（该步骤实际入参，业务键过滤后）与 output（结构化业务摘要），
     * 并附 title（业务动作名）/ goal（这步为什么存在）/ manualHint（人工替代做法），
     * 让业务人员一眼看清该环节做了什么、为什么做、人工该怎么做。
     */
    private Map<String, Object> buildToolEvent(ExecutionResult result) {
        Map<String, Object> toolEvent = new LinkedHashMap<>();
        toolEvent.put("name", result.getToolName());
        toolEvent.put("status", result.isSuccess() ? "done" : "error");
        toolEvent.put("durationMs", result.getExecutionTimeMs());
        toolEvent.put("input", toolInputView(result));
        ThinkingCopy.ToolCopy copy = ThinkingCopy.toolCopy(result.getToolName());
        if (copy == null) {
            // 未登记工具：LLM 按 ToolCopy 四要素同构现场生成一次并进程内缓存
            // （新工具上线当天即有业务文案，词典登记转为事后润色而非上线前置）
            copy = generatedCopyCache.computeIfAbsent(result.getToolName(), this::generateToolCopy);
        }
        if (copy != null) {
            toolEvent.put("title", copy.title());
            toolEvent.put("goal", copy.goal());
            toolEvent.put("manualHint", copy.manualHint());
        } else {
            AgentTool labeledTool = toolMap.get(result.getToolName());
            if (labeledTool != null && labeledTool.getLabel() != null && !labeledTool.getLabel().isBlank()) {
                toolEvent.put("title", labeledTool.getLabel());
            }
        }
        if (result.isSuccess() && result.getData() != null) {
            AgentTool tool = toolMap.get(result.getToolName());
            toolEvent.put("summary", ToolOutputRenderer.summary(tool, result.getData()));
            toolEvent.put("output", buildToolOutput(result));
        } else if (!result.isSuccess()) {
            toolEvent.put("errorMessage", result.getErrorMessage());
        }
        return toolEvent;
    }

    /** 未登记工具的 LLM 生成文案缓存（key=toolName；null 表示生成失败，回退工具 label）。 */
    private final Map<String, ThinkingCopy.ToolCopy> generatedCopyCache = new ConcurrentHashMap<>();

    /**
     * LLM 按 ToolCopy 四要素（title/goal/manualHint/category）同构生成未登记工具的业务文案。
     * 失败返回 null（computeIfAbsent 不缓存 null → 下次可重试），由调用方回退工具 label。
     */
    private ThinkingCopy.ToolCopy generateToolCopy(String toolName) {
        AgentTool tool = toolMap.get(toolName);
        if (tool == null) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("请为以下工具生成业务人员可读的说明文案。\n")
                    .append("工具名：").append(tool.getName()).append("\n")
                    .append("工具描述：").append(tool.getDescription()).append("\n");
            List<ToolParam> params = tool.getParams();
            if (params != null && !params.isEmpty()) {
                sb.append("入参：");
                for (ToolParam tp : params) {
                    sb.append(tp.getName()).append("(").append(tp.getDescription()).append(") ");
                }
                sb.append("\n");
            }
            sb.append("\n仅输出 JSON：\n")
                    .append("{\"title\": \"这步干什么（≤12字）\", ")
                    .append("\"goal\": \"为什么做这步（一句话，业务视角）\", ")
                    .append("\"manualHint\": \"AI不可用时人工怎么做（一句话，可具体步骤）\", ")
                    .append("\"category\": \"understand|lookup|verify|reason|generate 之一\"}");
            String raw = llmService.map(l -> l.completePrompt(sb.toString())).orElse(null);
            return parseToolCopy(raw);
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 未登记工具文案 LLM 生成失败（{}）: {}", toolName, e.getMessage());
            return null;
        }
    }

    /** 解析 LLM 生成的四要素 JSON；非法输出返回 null。 */
    private ThinkingCopy.ToolCopy parseToolCopy(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            Map<String, Object> m = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(raw.substring(start, end + 1),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            String title = str(m.get("title"));
            if (title.isBlank()) {
                return null;
            }
            ThinkingCopy.Category category;
            try {
                category = ThinkingCopy.Category.valueOf(
                        String.valueOf(m.getOrDefault("category", "lookup")).toUpperCase());
            } catch (IllegalArgumentException e) {
                category = ThinkingCopy.Category.LOOKUP;
            }
            return new ThinkingCopy.ToolCopy(title, str(m.get("goal")),
                    str(m.get("manualHint")).isBlank() ? null : str(m.get("manualHint")), category);
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 工具文案 LLM 输出解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 工具步骤入参视图：仅取工具自描述 {@code getParams()} 声明的"必要入参"，
     * 过滤掉 direct 兜底透传的无关 plan.params 噪声（如 指标/时间/业务意图 等，
     * 这些对工具执行并无作用），并隐藏内部码键（question/text/draft 等业务无需重复阅读的原始值），
     * 避免同一套参数在思考时间线多处重复、或以技术形态干扰业务阅读。
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
            if (ThinkingCopy.hideInputKey(tp.getName())) {
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

    /** action / intent 内部码 → 业务展示名（含产商品研发场景动作，词典收敛至 ThinkingCopy）。 */
    private static String actionDisplay(QueryPlan plan) {
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
     * 将查询计划翻译为业务可读的方案说明，取代透传内部码 queryPlan 给前端渲染卡片。
     */
    private static String buildReadablePlan(QueryPlan plan) {
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
            ThinkingCopy.ToolCopy copy = ThinkingCopy.toolCopy(tool);
            item.put("label", copy != null ? copy.title() : tool);
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

    /**
     * 供方案步骤「输入」展示的参数子集：剔除原始问题与内部噪声键（intent_type/action/text/draft 等），
     * 仅保留业务可读的范围参数（对象/指标/时间等）。
     */
    private static Map<String, Object> planInputView(QueryPlan plan) {
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