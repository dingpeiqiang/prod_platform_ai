package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 翻译层编排入口。
 * <p>
 * 编排"理解 → 执行 → 表达"三层的完整流程。
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final Understander understander;
    private final Executor executor;
    private final Presenter presenter;
    private final SessionManager sessionManager;

    public AgentOrchestrator(Understander understander,
                             Executor executor,
                             Presenter presenter,
                             SessionManager sessionManager) {
        this.understander = understander;
        this.executor = executor;
        this.presenter = presenter;
        this.sessionManager = sessionManager;
    }

    /**
     * 处理一次完整的翻译请求。
     *
     * @param question  用户问题
     * @param sessionId 会话 ID（可选，null 时创建新会话）
     * @return 翻译结果（含 session_id, report, evidence, conclusion, suggested_follow_ups）
     */
    public Map<String, Object> process(String question, String sessionId) {
        long startTime = System.currentTimeMillis();

        // Step 1: 获取会话上下文
        SessionContext context = sessionManager.getOrCreate(sessionId);
        context.addHistoryEntry("user", question);

        // Step 2: 理解层 — 自然语言 → 查询计划
        log.info("[AgentOrchestrator] 理解层处理: question={}", question);
        QueryPlan plan = understander.understand(question, context);
        context.setLastIntent(plan.getIntent());
        context.setLastTools(plan.getTools());
        context.setLastParams(plan.getParams());
        log.info("[AgentOrchestrator] 查询计划: intent={}, tools={}", plan.getIntent(), plan.getTools());

        // Step 3: 执行层 — 查询计划 → 工具执行
        log.info("[AgentOrchestrator] 执行层处理");
        List<ExecutionResult> results = executor.execute(plan);

        // 缓存执行结果作为证据
        for (ExecutionResult result : results) {
            if (result.isSuccess() && result.getData() != null) {
                context.cacheEvidence(result.getToolName(), result.getData());
            }
        }

        // Step 4: 表达层 — 工具结果 → 自然语言
        log.info("[AgentOrchestrator] 表达层处理");
        String report = presenter.present(question, results, context);
        List<String> followUps = presenter.suggestFollowUps(question, results);

        // 保存回答到会话历史
        context.addHistoryEntry("assistant", report);

        // 保存会话
        sessionManager.save(context);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[AgentOrchestrator] 处理完成: sessionId={}, elapsed={}ms", context.getSessionId(), elapsed);

        // 构建响应
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", context.getSessionId());
        response.put("report", report);
        response.put("intent", plan.getIntent());
        response.put("tools", plan.getTools());
        response.put("query_plan", Map.of(
                "intent", plan.getIntent(),
                "tools", plan.getTools(),
                "params", plan.getParams()
        ));
        response.put("evidence", buildEvidenceSummary(results));
        response.put("conclusion", extractConclusion(results));
        response.put("suggested_follow_ups", followUps);
        response.put("elapsed_ms", elapsed);

        return response;
    }

    /**
     * 构建证据摘要。
     */
    private List<Map<String, Object>> buildEvidenceSummary(List<ExecutionResult> results) {
        return results.stream()
                .filter(ExecutionResult::isSuccess)
                .map(r -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("tool", r.getToolName());
                    summary.put("success", true);
                    if (r.getData() != null) {
                        summary.put("summary", r.getData().getOrDefault("nl_answer",
                                r.getData().getOrDefault("answer", "执行完成")));
                    }
                    return summary;
                })
                .toList();
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