package com.sitech.prodai.service.agent;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.ChatPersistenceService;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.bridge.ChatHumanBridge;
import com.sitech.prodai.service.agent.flow.SceneFlowRouter;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ThinkingCopy;
import com.sitech.prodai.service.agent.tool.ToolOutputRenderer;
import com.sitech.prodai.service.agent.tool.ToolOutputField;
import com.sitech.prodai.service.agent.tool.ToolParam;
import com.sitech.prodai.service.agent.workflow.WorkflowGraphView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    /** 对话内 human 挂起桥接器（W2）：挂起态翻译为 clarify_contracts，用户回复走 resume 短路。 */
    private final ChatHumanBridge chatHumanBridge;
    /** 场景工作流路由器（W3）：理解层计划确定性映射到场景工作流，未命中走动态编排。 */
    private final SceneFlowRouter sceneFlowRouter;
    /** 流程进度桥接器（W4）：引擎节点事件 → SSE flow_progress（可选，null 时静默跳过）。 */
    private final com.sitech.prodai.service.agent.flow.ChatFlowProgressBridge progressBridge;
    /** 历史回放重建器（W6-2）：node_logs → flow_progress_timeline 随 metadata 落库（可选，null 时跳过）。 */
    private final com.sitech.prodai.service.agent.flow.FlowProgressReplayer progressReplayer;
    /** 手册注册表：工具名单注入（MCP 可解析约束），编排消费方按需渲染 SOP。 */
    private final com.sitech.prodai.service.agent.playbook.PlaybookRegistry playbookRegistry;
    /** 查询审计记录器（方案 §6-A3）：userScope 摘要 + 工具链 + 命中条数，落日志/透出响应。 */
    private final com.sitech.prodai.service.agent.model.QueryAuditRecorder queryAuditRecorder;

    /** 已注册工具索引：工具名 → 工具（供工具自描述元数据查询） */
    private final Map<String, AgentTool> toolMap;

    /** 附件-only 摘要追问生成器（豆包式：解析后 LLM 总结 + 开放追问），可选注入。 */
    private final com.sitech.prodai.service.agent.impl.DocSummaryFollowupGenerator docSummaryFollowupGenerator;

    /** 手册意图升级的来源计划（请求级暂存）：手册直达阶段①呈现理解层 LLM 识别过程用。 */
    private QueryPlan upgradedPlan;

    public AgentOrchestrator(Understander understander,
                             Executor executor,
                             Presenter presenter,
                             SessionManager sessionManager,
                             Optional<ChatPersistenceService> persistenceService,
                             Optional<LlmService> llmService,
                             List<AgentTool> tools,
                             ChatHumanBridge chatHumanBridge,
                             SceneFlowRouter sceneFlowRouter) {
        this(understander, executor, presenter, sessionManager, persistenceService,
                llmService, tools, chatHumanBridge, sceneFlowRouter, null, null);
    }

    public AgentOrchestrator(Understander understander,
                             Executor executor,
                             Presenter presenter,
                             SessionManager sessionManager,
                             Optional<ChatPersistenceService> persistenceService,
                             Optional<LlmService> llmService,
                             List<AgentTool> tools,
                             ChatHumanBridge chatHumanBridge,
                             SceneFlowRouter sceneFlowRouter,
                             com.sitech.prodai.service.agent.flow.ChatFlowProgressBridge progressBridge) {
        this(understander, executor, presenter, sessionManager, persistenceService,
                llmService, tools, chatHumanBridge, sceneFlowRouter, progressBridge, null);
    }

    public AgentOrchestrator(Understander understander,
                             Executor executor,
                             Presenter presenter,
                             SessionManager sessionManager,
                             Optional<ChatPersistenceService> persistenceService,
                             Optional<LlmService> llmService,
                             List<AgentTool> tools,
                             ChatHumanBridge chatHumanBridge,
                             SceneFlowRouter sceneFlowRouter,
                             com.sitech.prodai.service.agent.flow.ChatFlowProgressBridge progressBridge,
                             com.sitech.prodai.service.agent.flow.FlowProgressReplayer progressReplayer) {
        this(understander, executor, presenter, sessionManager, persistenceService,
                llmService, tools, chatHumanBridge, sceneFlowRouter,
                progressBridge, progressReplayer, null);
    }

    @Autowired
    public AgentOrchestrator(Understander understander,
                             Executor executor,
                             Presenter presenter,
                             SessionManager sessionManager,
                             Optional<ChatPersistenceService> persistenceService,
                             Optional<LlmService> llmService,
                             List<AgentTool> tools,
                             ChatHumanBridge chatHumanBridge,
                             SceneFlowRouter sceneFlowRouter,
                             com.sitech.prodai.service.agent.flow.ChatFlowProgressBridge progressBridge,
                             com.sitech.prodai.service.agent.flow.FlowProgressReplayer progressReplayer,
                             @org.springframework.lang.Nullable
                             com.sitech.prodai.service.agent.playbook.PlaybookRegistry playbookRegistry) {
        this(understander, executor, presenter, sessionManager, persistenceService,
                llmService, tools, chatHumanBridge, sceneFlowRouter,
                progressBridge, progressReplayer, playbookRegistry, null);
    }

    /** 全参构造（审计记录器可选，null 时兜底自建，测试兼容路径零改动）。 */
    public AgentOrchestrator(Understander understander,
                             Executor executor,
                             Presenter presenter,
                             SessionManager sessionManager,
                             Optional<ChatPersistenceService> persistenceService,
                             Optional<LlmService> llmService,
                             List<AgentTool> tools,
                             ChatHumanBridge chatHumanBridge,
                             SceneFlowRouter sceneFlowRouter,
                             com.sitech.prodai.service.agent.flow.ChatFlowProgressBridge progressBridge,
                             com.sitech.prodai.service.agent.flow.FlowProgressReplayer progressReplayer,
                              @org.springframework.lang.Nullable
                              com.sitech.prodai.service.agent.playbook.PlaybookRegistry playbookRegistry,
                              @org.springframework.lang.Nullable
                              com.sitech.prodai.service.agent.model.QueryAuditRecorder queryAuditRecorder) {
        this.understander = understander;
        this.executor = executor;
        this.presenter = presenter;
        this.sessionManager = sessionManager;
        this.persistenceService = persistenceService;
        this.llmService = llmService;
        this.chatHumanBridge = chatHumanBridge != null ? chatHumanBridge : new ChatHumanBridge(null) {
            // 兜底空实现：未注入桥接器时挂起恢复恒不可用（零行为变更），
            // 覆写 resume 恒返 null，规避父类对 null 引擎的空指针
            @Override
            public ApiResponse<Map<String, Object>> resume(Map<String, Object> binding, String question,
                                                           Map<String, Object> formData, String user) {
                return null;
            }

            @Override
            public Map<String, Object> buildBinding(Map<String, Object> executionMap) {
                return null;
            }
        };
        this.sceneFlowRouter = sceneFlowRouter;
        this.progressBridge = progressBridge;
        this.progressReplayer = progressReplayer;
        // PlaybookRegistry 装配收敛（去旧留新）：复用 Spring 单例 Bean（与 SceneFlowRouter/
        // DefaultUnderstander 同一实例）；未注入时兜底自建（旧构造器链路的测试兼容路径）
        if (playbookRegistry != null) {
            this.playbookRegistry = playbookRegistry;
        } else {
            this.playbookRegistry = new com.sitech.prodai.service.agent.playbook.PlaybookRegistry();
            this.playbookRegistry.init();
        }
        // 查询审计记录器（A3）：null 时兜底自建（审计为无状态纯组件，自建零风险）
        this.queryAuditRecorder = queryAuditRecorder != null
                ? queryAuditRecorder : new com.sitech.prodai.service.agent.model.QueryAuditRecorder();
        // 附件-only 摘要追问生成器：LLM 可用时走自然语言总结+追问，不可用时生成器内部回退模板
        this.docSummaryFollowupGenerator = llmService.isPresent()
                ? new com.sitech.prodai.service.agent.impl.DocSummaryFollowupGenerator(llmService.get())
                : null;
        this.toolMap = new ConcurrentHashMap<>();
        if (tools != null) {
            for (AgentTool tool : tools) {
                this.toolMap.put(tool.getName(), tool);
            }
            // 手册装载门禁（MCP 约束）：工具装配完成后注入名单，手册里的工具引用必须可解析
            this.playbookRegistry.registerKnownTools(this.toolMap.keySet());
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

        // 行权限（方案 §4.4）：绑定请求级权限上下文（服务端解析产物，SessionContext 持有），
        // finally 清理防线程池复用串号；数据访问出口（SPARQL/指标）经 UserScopeContext 读取
        extractUserScope(context, params);
        com.sitech.prodai.service.agent.model.UserScopeContext.bind(context.getUserScope());
        try {
            return doProcess(question, context, params, scene, startTime);
        } finally {
            com.sitech.prodai.service.agent.model.UserScopeContext.clear();
        }
    }

    /** process 的权限绑定后主链路（拆出仅为 try-finally 收口清晰）。 */
    private Map<String, Object> doProcess(String question, SessionContext context,
                                           Map<String, Object> params, String scene, long startTime) {

        // W2 挂起态短路：会话绑定待恢复工作流 → 直接走 ChatHumanBridge.resume（不过理解层 LLM）
        if (context.hasPendingExecution()) {
            Map<String, Object> reply = resumePendingExecution(context, question, params, startTime);
            if (reply != null) {
                return reply;
            }
        }

        // 附件-only 分支（豆包式文件处理）：用户只上传附件未输入文本 → 先解析文件，
        // 再由 LLM 生成「内容总结 + 开放式追问」，不抢跑手册意图识别
        if (isAttachmentOnly(question, params)) {
            return attachmentOnlyPath(question, params, context, startTime);
        }

        // Step 2: 理解层 — 自然语言 → 查询计划
        // 语义判定统一收口理解层 LLM（intent_guide 归口 + examples 正反样例），
        // 命中 applies_to.intents 经手册意图升级直达链路
        log.info("[AgentOrchestrator] 理解层处理: question={}", question);
        QueryPlan plan = understander.understand(question, context);
        log.info("[AgentOrchestrator] 查询计划: intent={}, tools={}, clarify={}",
                plan.getIntent(), plan.getTools(), plan.getClarify());

        // W3 场景工作流路由先于手册升级：场景工作流（用户自建/存量）优先级更高，
        // 命中即短路；未命中再判手册意图升级（sop-step-N 时间线），最后回落动态编排
        java.util.Optional<Map<String, Object>> sceneReply = sceneFlowRouter.tryRoute(plan, context, null);
        if (sceneReply.isPresent()) {
            Map<String, Object> reply = sceneReply.get();
            reply.putIfAbsent("session_id", context.getSessionId());
            String report = String.valueOf(reply.getOrDefault("report", ""));
            context.addHistoryEntry("assistant", report);
            // 挂起绑定捕获必须先于 persistTurn：persistTurn 落库时读取 executionBinding
            // 写入消息 metadata（SessionManager 快照恢复依赖），顺序颠倒会导致跨轮恢复失效
            captureSuspensionBinding(context, reply);
            sessionManager.save(context);
            persistTurn(context, question, reply, null);
            reply.put("elapsed_ms", System.currentTimeMillis() - startTime);
            return reply;
        }

        // 手册意图升级：LLM 识别的意图（经归一化）命中手册 applies_to.intents →
        // 升级走手册直达链路（sop-step-N 时间线 + 环节 IO）；
        // 未命中回落常规动态编排（SOP 已由理解层注入 prompt，LLM 照手册自由编排）
        Map<String, Object> upgradeReply = playbookUpgradePath(plan, context, params, startTime);
        if (upgradeReply != null) {
            return upgradeReply;
        }

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
            clarifyResponse.put("query_plan", buildQueryPlanView(plan, context));
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
            confirmResponse.put("query_plan", buildQueryPlanView(plan, context));
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

        // 保存会话（持久化失败时记录 warnings，随响应透传给前端）
        sessionManager.save(context);
        List<String> warnings = new ArrayList<>();
        persistTurn(context, question, report, plan, results, null, warnings, null, null);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[AgentOrchestrator] 处理完成: sessionId={}, elapsed={}ms", context.getSessionId(), elapsed);

        // 构建响应
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", context.getSessionId());
        response.put("report", report);
        response.put("intent", plan.getIntent());
        response.put("tools", plan.getTools());
        response.put("query_plan", buildQueryPlanView(plan, context));
        response.put("conclusion", extractConclusion(results));
        response.put("suggested_follow_ups", followUps);
        response.put("elapsed_ms", elapsed);
        // 查询审计（方案 §6-A3）：userScope 摘要 + 工具链 + 命中条数随响应透出（前端/落库对账）
        response.put(com.sitech.prodai.service.agent.model.QueryAuditRecorder.AUDIT_KEY,
                queryAuditRecorder.record(queryAuditRecorder.build(
                        context.getUserScope(), scene, plan, results, null)));
        if (!warnings.isEmpty()) {
            response.put("warnings", warnings);
        }

        return response;
    }

    /**
     * 手册意图升级：理解层 LLM 识别的意图（经 IntentRecognitionSupport 归一化）命中
     * 手册 applies_to.intents → 升级走手册直达链路。
     * <p>
     * 命中后照直达链路执行（sop-step-N 时间线 + 环节 IO 差异化），
     * 不落回动态编排的常规视图。
     * 工具兜底不升级（LLM 自选工具 ≠ 认领整本手册，宁走动态编排不冒进步骤视图）。
     *
     * @return 手册直达链路回复；未命中手册意图返回 null（调用方回落常规链路）
     */
    private Map<String, Object> playbookUpgradePath(QueryPlan plan, SessionContext context,
                                                    Map<String, Object> params, long startTime) {
        if (plan == null || context == null || plan.getTools() == null || plan.getTools().isEmpty()) {
            return null;
        }
        String intent = plan.getIntent();
        // 澄清/确认/证据复用是会话协作意图，不参与手册路由
        if (QueryPlan.INTENT_CLARIFY.equals(intent) || QueryPlan.INTENT_CONFIRM.equals(intent)
                || QueryPlan.INTENT_REUSE_EVIDENCE.equals(intent)) {
            return null;
        }
        String playbookCode = playbookRegistry.route(context.getScene(), intent, plan.getTools(),
                plan.getUserQuestion());
        if (playbookCode == null) {
            return null;
        }
        log.info("[AgentOrchestrator] 手册意图升级: playbook={} intent={} question={}",
                playbookCode, intent, plan.getUserQuestion());
        // 升级来源计划暂存：手册直达链路呈现理解层 LLM 识别过程（reasoningTrace）用
        this.upgradedPlan = plan;
        try {
            return runPlaybookPath(playbookCode, plan.getUserQuestion(), params, context, startTime);
        } finally {
            this.upgradedPlan = null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runPlaybookPath(String playbookCode, String question,
                                                Map<String, Object> params,
                                                SessionContext context, long startTime) {
        Map<String, Object> book = playbookRegistry.get(playbookCode);
        if (book == null || !(book.get("applies_to") instanceof Map<?, ?> at)) {
            return null;
        }
        List<String> tools = playbookTools(book);
        String intent = at.get("intents") instanceof List<?> intents && !intents.isEmpty()
                ? String.valueOf(intents.get(0)) : (tools.isEmpty() ? null : tools.get(0).toUpperCase());
        if (intent == null || tools.isEmpty()) {
            return null;
        }
        Map<String, Object> planParams = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        planParams.putIfAbsent("question", question);
        injectSessionId(planParams, context);
        fillQuestionSlots(tools, planParams, question);
        QueryPlan plan = new QueryPlan(intent, tools, planParams, question);
        plan.setUserQuestion(question);
        // 思考时间线 trace 透传理解层 reasoningTrace（与流式 runPlaybookStream 阶段①同语义）：
        // 持久化快照 buildReasoningSnapshot 经 traceView(plan) 读取，历史回放呈现完整判定链
        if (upgradedPlan != null && upgradedPlan.getReasoningTrace() != null) {
            plan.setReasoningTrace(new ArrayList<>(upgradedPlan.getReasoningTrace()));
        }
        // 手册步骤结构随 plan 下传：persistTurn 据此把 sop-step-N 写入 reasoning_full（回放同构）
        plan.setSopSteps(parseSopSteps(playbookRegistry.renderSop(playbookCode)));
        // 手册步骤声明 input_from 时构建依赖编排（跨工具数据流：result: 来源解析，
        // 如智聊链路开单承接草稿生成/合规校验产出）；未声明时保持 direct 全参透传
        List<com.sitech.prodai.service.agent.model.ExecStep> execSteps = playbookExecSteps(book, tools);
        if (!execSteps.isEmpty()) {
            plan.setSteps(execSteps);
        }
        context.setLastIntent(intent);
        context.setLastTools(tools);
        context.setLastParams(planParams);

        log.info("[AgentOrchestrator] 手册直达执行: playbook={} intent={} tools={}",
                playbookCode, intent, tools);
        List<ExecutionResult> results = executor.execute(plan, context);
        for (ExecutionResult result : results) {
            if (result.isSuccess() && result.getData() != null) {
                context.cacheEvidence(result.getToolName(), result.getData());
                cacheBusinessEntity(context, result);
            }
        }
        // 查询收敛 SOP（方案 §4.5，阶段 B1）：粗查命中规模超阈值 → 澄清收敛或降维呈现，
        // 判定口径收拢 QueryRefinePolicy（手册链路与动态编排共用的确定性实现）
        Map<String, Object> refineReply = refinePolicyBranch(playbookCode, plan, question, results, context, startTime);
        if (refineReply != null) {
            return refineReply;
        }
        String report = presenter.present(question, results, context);
        List<String> followUps = presenter.suggestFollowUps(question, results, context);
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, report, plan, results, null, new ArrayList<>(), null, null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", context.getSessionId());
        response.put("report", report);
        response.put("intent", intent);
        response.put("tools", tools);
        response.put("query_plan", buildQueryPlanView(plan, context));
        response.put("conclusion", extractConclusion(results));
        response.put("suggested_follow_ups", followUps);
        response.put("playbook", playbookCode);
        response.put("elapsed_ms", System.currentTimeMillis() - startTime);
        // 查询审计（方案 §6-A3）：手册直达链路同样落审计（playbook 标记来源）
        response.put(com.sitech.prodai.service.agent.model.QueryAuditRecorder.AUDIT_KEY,
                queryAuditRecorder.record(queryAuditRecorder.build(
                        context.getUserScope(), context.getScene(), plan, results, playbookCode)));
        return response;
    }

    /**
     * 查询收敛 SOP 分支（方案 §4.5，阶段 B1）：手册链路粗查结果落袋后的确定性规模判定。
     * <p>
     * 仅对手册工具链含 sparql_query 的查询类手册生效（其余手册零感知）：
     * <ul>
     *   <li>OK（≤5 条，或已达收敛轮次上限）：返回 null，走原手册呈现；</li>
     *   <li>REFINE（5~20 条）：呈现粗查结果，随响应带收敛追问建议（clarify 维度提示，
     *       不阻断——用户可继续追问也可就此打住）；</li>
     *   <li>FORCE_SUMMARY（>20 条）：转 CLARIFY 计划生成收敛追问（地市 > 资费档位优先，
     *       至多 {@link QueryRefinePolicy#MAX_REFINE_ROUNDS} 轮，超限由 judge 回落 OK 降维呈现），
     *       复用既有澄清分支响应契约（clarify / clarify_contracts / query_plan），前端零改动。</li>
     * </ul>
     *
     * @return 非 null = 已按收敛分支处理完毕（调用方直接返回）；null = 规模舒适，继续原链路
     */
    private Map<String, Object> refinePolicyBranch(String playbookCode, QueryPlan plan, String question,
                                                   List<ExecutionResult> results, SessionContext context,
                                                   long startTime) {
        if (results == null || results.isEmpty() || context == null) {
            return null;
        }
        // 手册工具链不含 sparql_query（非查询类手册）或粗查失败 → 不做收敛判定
        if (plan.getTools() == null || !plan.getTools().contains("sparql_query")) {
            return null;
        }
        ExecutionResult coarse = results.stream()
                .filter(r -> "sparql_query".equals(r.getToolName())).findFirst().orElse(null);
        if (coarse == null || !coarse.isSuccess()) {
            return null;
        }
        int hits = extractEntityHits(coarse);
        int rounds = refineRoundsOf(context);
        com.sitech.prodai.service.agent.model.QueryRefinePolicy.Verdict verdict =
                com.sitech.prodai.service.agent.model.QueryRefinePolicy.judge(hits, rounds);
        log.info("[AgentOrchestrator] 查询收敛判定: playbook={} hits={} rounds={} verdict={}",
                playbookCode, hits, rounds, verdict);
        return switch (verdict) {
            case OK -> null;
            case FORCE_SUMMARY -> buildRefineClarifyResponse(playbookCode, plan, question, hits, rounds,
                    context, startTime);
            case REFINE -> buildRefineHintResponse(playbookCode, plan, question, hits, context, startTime);
        };
    }

    /**
     * FORCE_SUMMARY 分支：命中过多 → 生成收敛追问响应（CLARIFY 计划形态）。
     * 追问维度由 {@link QueryRefinePolicy#nextDimensions} 按优先级与已答维度推导；
     * 收敛轮次超限时 judge 已回落 OK，不会走到这里。
     */
    private Map<String, Object> buildRefineClarifyResponse(String playbookCode, QueryPlan plan, String question,
                                                           int hits, int rounds, SessionContext context,
                                                           long startTime) {
        List<String> dims = com.sitech.prodai.service.agent.model.QueryRefinePolicy.nextDimensions(
                refineAnsweredOf(context));
        if (dims.isEmpty()) {
            return null; // 无可追问维度 → 回落原链路（LLM 表达层自然摘要呈现）
        }
        context.incrementRefineRounds();
        QueryPlan refinePlan = new QueryPlan();
        refinePlan.setIntent(QueryPlan.INTENT_CLARIFY);
        refinePlan.setTools(plan.getTools());
        refinePlan.setClarify(dims);
        refinePlan.setClarifyContracts(
                com.sitech.prodai.service.agent.model.QueryRefinePolicy.clarifyContracts(dims));
        refinePlan.setParams(new LinkedHashMap<>(plan.getParams()));
        refinePlan.setUserQuestion(question);
        refinePlan.addTrace("refine", "粗查命中 " + hits + " 条（超过 "
                + com.sitech.prodai.service.agent.model.QueryRefinePolicy.REFINE_THRESHOLD
                + "），先澄清收敛再精查（第 " + (rounds + 1) + "/"
                + com.sitech.prodai.service.agent.model.QueryRefinePolicy.MAX_REFINE_ROUNDS + " 轮）");
        context.setLastIntent(QueryPlan.INTENT_CLARIFY);
        context.setLastClarifyParams(dims);
        context.setLastTools(plan.getTools());
        context.setLastParams(refinePlan.getParams());
        String clarifyMessage = presenter.present(question, List.of(), context);
        context.addHistoryEntry("assistant", clarifyMessage);
        sessionManager.save(context);
        persistTurn(context, question, clarifyMessage, refinePlan, List.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", context.getSessionId());
        response.put("report", clarifyMessage);
        response.put("intent", QueryPlan.INTENT_CLARIFY);
        response.put("clarify", dims);
        response.put("clarify_contracts", refinePlan.getClarifyContracts());
        response.put("tools", plan.getTools());
        response.put("query_plan", buildQueryPlanView(refinePlan, null));
        response.put("conclusion", "");
        response.put("suggested_follow_ups", List.of());
        response.put("playbook", playbookCode);
        response.put("refine_hits", hits);
        response.put("elapsed_ms", System.currentTimeMillis() - startTime);
        return response;
    }

    /**
     * REFINE 分支：命中偏多 → 照常呈现粗查结果（降维口径：TOP N + 总量声明），
     * 随响应透出收敛提示（suggest 维度 + 命中数），用户可选择性追问；不阻断、不二次查询。
     */
    private Map<String, Object> buildRefineHintResponse(String playbookCode, QueryPlan plan, String question,
                                                        int hits, SessionContext context, long startTime) {
        // 表达层降维口径：从证据缓存取粗查结果，注入 TOP N 提示（LLM 据此收敛输出）
        List<ExecutionResult> coarseResults = refineCoarseResults(context);
        context.cacheEvidence("refine_summary_hint",
                com.sitech.prodai.service.agent.model.QueryRefinePolicy.summaryPrompt(hits));
        String report = presenter.present(question, coarseResults, context);
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, report, plan, coarseResults);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", context.getSessionId());
        response.put("report", report);
        response.put("intent", plan.getIntent());
        response.put("tools", plan.getTools());
        response.put("query_plan", buildQueryPlanView(plan, context));
        response.put("conclusion", extractConclusion(coarseResults));
        response.put("suggested_follow_ups", List.of());
        response.put("playbook", playbookCode);
        response.put("refine_hits", hits);
        response.put("refine_suggest",
                com.sitech.prodai.service.agent.model.QueryRefinePolicy.clarifyContracts(
                        com.sitech.prodai.service.agent.model.QueryRefinePolicy.nextDimensions(
                                refineAnsweredOf(context))));
        response.put("elapsed_ms", System.currentTimeMillis() - startTime);
        return response;
    }

    /** 手册链路粗查结果重取（收敛分支复用：证据缓存 → 单元素结果列表）。 */
    @SuppressWarnings("unchecked")
    private List<ExecutionResult> refineCoarseResults(SessionContext context) {
        Object cached = context.getCachedEvidence() != null
                ? context.getCachedEvidence().get("sparql_query") : null;
        return cached instanceof Map<?, ?> m
                ? List.of(ExecutionResult.ok("sparql_query", new LinkedHashMap<>((Map<String, Object>) m)))
                : List.of();
    }

    /**
     * 从 sparql_query 结果提取命中条数：entity_ids / raw_results 契约键优先，
     * 回落 entity_count/total 数值键（与 QueryAuditRecorder.extractHitCount 同口径）。
     */
    private static int extractEntityHits(ExecutionResult coarse) {
        if (coarse.getData() == null) {
            return -1;
        }
        for (String key : List.of("entity_ids", "raw_results")) {
            if (coarse.getData().get(key) instanceof List<?> list) {
                return list.size();
            }
        }
        if (coarse.getData().get("entity_count") instanceof Number n) {
            return n.intValue();
        }
        return -1;
    }

    /** 本轮已进行的收敛澄清轮数（与参数补全门的 clarifyRounds 独立计数）。 */
    private static int refineRoundsOf(SessionContext context) {
        Object rounds = context.getMeta().get("refine_rounds");
        return rounds instanceof Number n ? n.intValue() : 0;
    }

    /** 已回答的收敛维度（用户澄清回传写入 resolvedParams 的收敛键）。 */
    private static Map<String, Object> refineAnsweredOf(SessionContext context) {
        Map<String, Object> answered = new LinkedHashMap<>();
        for (String dim : com.sitech.prodai.service.agent.model.QueryRefinePolicy.REFINE_DIMENSIONS) {
            Object val = context.getResolvedParams().get(dim);
            if (val != null) {
                answered.put(dim, val);
            }
        }
        return answered;
    }

    /**
     * 手册意图升级（流式）：理解层 LLM 识别的意图命中手册 applies_to.intents →
     * 升级走流式手册直达链路（sop-step-N 时间线 + 环节 IO）。
     * <p>
     * 与 {@link #playbookUpgradePath} 同判据；工具兜底不升级（LLM 自选工具 ≠ 认领整本手册）。
     *
     * @return true = 已按手册链路流式处理完毕；false = 未命中手册意图，调用方回落常规链路
     */
    private boolean playbookUpgradeStream(QueryPlan plan, SessionContext context, Map<String, Object> params,
                                          StreamEmitter emitter, long startTime) {
        if (plan == null || context == null || plan.getTools() == null || plan.getTools().isEmpty()) {
            return false;
        }
        String intent = plan.getIntent();
        if (QueryPlan.INTENT_CLARIFY.equals(intent) || QueryPlan.INTENT_CONFIRM.equals(intent)
                || QueryPlan.INTENT_REUSE_EVIDENCE.equals(intent)) {
            return false;
        }
        String playbookCode = playbookRegistry.route(context.getScene(), intent, plan.getTools(),
                plan.getUserQuestion());
        if (playbookCode == null) {
            return false;
        }
        log.info("[AgentOrchestrator] 手册意图升级(流式): playbook={} intent={} question={}",
                playbookCode, intent, plan.getUserQuestion());
        // 升级来源计划暂存：手册直达阶段①呈现理解层 LLM 识别过程（reasoningTrace）用
        this.upgradedPlan = plan;
        try {
            return runPlaybookStream(playbookCode, plan.getUserQuestion(), params, context, emitter, startTime);
        } finally {
            this.upgradedPlan = null;
        }
    }

    /**
     * 手册直达流式链路：思考时间线四步与常规链路同构，差异在「依据」——
     * 阶段①呈现完整识别链（理解层 LLM 处理日志 + 路由归口判定），
     * 方案步骤的 trace 就是手册 SOP 的操作步骤，
     * 执行层各工具开始/结束实时下发 tool 事件。手册执行过程对用户完整可见。
     *
     * @return true = 已按手册链路处理完毕；false = 手册声明不完整，调用方回落常规链路
     */
    private boolean runPlaybookStream(String playbookCode, String question, Map<String, Object> params,
                                      SessionContext context, StreamEmitter emitter, long startTime) {
        Map<String, Object> book = playbookRegistry.get(playbookCode);
        if (book == null || !(book.get("applies_to") instanceof Map<?, ?> at)) {
            return false;
        }
        List<String> tools = playbookTools(book);
        String intent = at.get("intents") instanceof List<?> intents && !intents.isEmpty()
                ? String.valueOf(intents.get(0)) : (tools.isEmpty() ? null : tools.get(0).toUpperCase());
        if (intent == null || tools.isEmpty()) {
            return false;
        }
        log.info("[AgentOrchestrator] 流式手册直达: playbook={} intent={} tools={}", playbookCode, intent, tools);

        // ── 阶段① 识别：呈现「话术为何归入本手册」的判定链 ——
        // trace 直接透传理解层 reasoningTrace（两阶段：阶段A LLM 判意图 + 命中手册归口说明），
        // 编排层不重复叙述判定（理解层已含命中说明，再补一条属同义反复）
        String bookTitle = String.valueOf(book.getOrDefault("title", playbookCode));
        List<Object> intentTrace = new ArrayList<>();
        if (upgradedPlan != null && upgradedPlan.getReasoningTrace() != null) {
            intentTrace.addAll(upgradedPlan.getReasoningTrace());
        }
        Map<String, Object> intentExtra = new LinkedHashMap<>();
        intentExtra.put("goal", "先听懂您要做什么，再决定怎么办");
        intentExtra.put("input", Map.of("question", question));
        intentExtra.put("output", Map.of(
                "summary", "已明确：本次要执行「" + bookTitle + "」",
                "structured_intent", Map.of("action", bookTitle),
                "playbook", playbookCode));
        intentExtra.put("trace", intentTrace);
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("intent", "需求识别",
                        "按「" + bookTitle + "」标准作业程序处理", intentExtra)),
                "intent", intent
        ));

        // ── 阶段② 方案：手册总览（1 条）——交代手册与总步数，SOP 明细在总览 trace 内完整可见 ──
        // 手册步骤不预读成静态思考步骤（空壳假步骤），改为随执行动态落地：
        // 每个真实 tool 事件在 onStepComplete 中落一条带手册步骤标题的思考步骤（真实耗时 + 真实产出 trace）
        String sop = playbookRegistry.renderSop(playbookCode);
        List<Map<String, Object>> sopSteps = parseSopSteps(sop);
        int sopStepCount = Math.max(sopSteps.size(), 1);
        Map<String, Object> planExtra = new LinkedHashMap<>();
        planExtra.put("goal", "照手册办事：" + bookTitle);
        planExtra.put("input", Map.of("from_step", "intent", "structured_intent", Map.of("action", bookTitle)));
        planExtra.put("output", Map.of("summary", "手册共 " + sopStepCount + " 步，按序执行"));
        List<Map<String, Object>> planTrace = TraceSnapshotBuilder.sopTraceView(sop);
        if (planTrace != null) {
            planExtra.put("trace", planTrace);
        }
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("plan", "方案规划",
                        "按手册「" + bookTitle + "」执行，共 " + sopStepCount + " 步", planExtra)),
                "intent", intent
        ));

        // ── 阶段③ 执行：工具开始/结束实时下发 tool 事件；完成时按手册步骤落地真实思考步骤 ──
        // 工具已原子化（一个工具一个环节，步骤与工具 1:1），
        // 每次真实工具完成即落地对应的手册步骤（按序推进）
        // 待落步骤队列：工具名 → 该工具尚未落地的手册步骤序号（按序消费）
        Map<String, java.util.ArrayDeque<Integer>> pendingStepsByTool = new LinkedHashMap<>();
        for (int i = 0; i < sopSteps.size(); i++) {
            String tool = String.valueOf(sopSteps.get(i).getOrDefault("tool", ""));
            if (!tool.isBlank()) {
                pendingStepsByTool.computeIfAbsent(tool, k -> new java.util.ArrayDeque<>()).add(i);
            }
        }
        Map<String, Object> planParams = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        planParams.putIfAbsent("question", question);
        injectSessionId(planParams, context);
        fillQuestionSlots(tools, planParams, question);
        QueryPlan plan = new QueryPlan(intent, tools, planParams, question);
        plan.setUserQuestion(question);
        // 手册步骤结构随 plan 下传：persistTurn 据此把 sop-step-N 写入 reasoning_full（回放同构）
        plan.setSopSteps(sopSteps);
        // 手册步骤声明 input_from 时构建依赖编排（跨工具数据流：result: 来源解析，
        // 如智聊链路开单承接草稿生成/合规校验产出）；未声明时保持 direct 全参透传
        List<com.sitech.prodai.service.agent.model.ExecStep> execSteps = playbookExecSteps(book, tools);
        if (!execSteps.isEmpty()) {
            plan.setSteps(execSteps);
        }
        context.setLastIntent(intent);
        context.setLastTools(tools);
        context.setLastParams(planParams);

        List<ExecutionResult> results = executor.execute(plan, context, new Executor.StepListener() {
            @Override
            public void onStepStart(String toolName) {
                // playbook=true：告知前端本链路 tool 事件只驱动工具卡片，
                // 不再自动生成 tool_<name> 思考条目（与手册步骤时间线重复，且解析只发生一次）
                emitter.emit("tool", Map.of("name", toolName, "status", "running", "playbook", playbookCode));
            }

            @Override
            public void onStepComplete(ExecutionResult result) {
                Map<String, Object> toolEvent = buildToolEvent(result);
                toolEvent.put("playbook", playbookCode);
                emitter.emit("tool", toolEvent);
                if (result.isSuccess() && result.getData() != null) {
                    context.cacheEvidence(result.getToolName(), result.getData());
                    cacheBusinessEntity(context, result);
                }
                // 按序消费该工具的待落手册步骤（一次真实执行收尾全部对应步骤）
                java.util.ArrayDeque<Integer> queue = pendingStepsByTool.get(result.getToolName());
                if (queue == null || queue.isEmpty()) {
                    return;
                }
                List<Integer> consumed = new ArrayList<>();
                // 手册路径下步骤与工具 1:1（工具原子化）：一次真实执行收尾该工具名下全部待落手册步骤
                while (!queue.isEmpty()) {
                    consumed.add(queue.poll());
                }
                for (int stepIdx : consumed) {
                    Map<String, Object> sopStep = stepIdx < sopSteps.size() ? sopSteps.get(stepIdx) : Map.of();
                    List<Map<String, Object>> stepTrace = new ArrayList<>();
                    stepTrace.add(Map.of("stage", "sop", "message", String.valueOf(sopStep.getOrDefault("how", "按手册执行"))));
                    if (result.isSuccess()) {
                        List<Map<String, Object>> toolTrace = stepTraceOf(result, stepIdx);
                        if (toolTrace != null) {
                            stepTrace.addAll(toolTrace);
                        }
                    } else {
                        stepTrace.add(Map.of("stage", "llm", "message", "执行失败：" + result.getErrorMessage()));
                    }
                    // 输入/输出按环节差异化：原子工具链下每步输入承接上一环节产出，输出只讲自己环节的结论
                    Map<String, Object> phaseIo = stepIoOf(result, stepIdx);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> phaseInput = (Map<String, Object>) phaseIo.getOrDefault("input", Map.of());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> phaseOutput = (Map<String, Object>) phaseIo.getOrDefault("output", Map.of());
                    Map<String, Object> stepExtra = new LinkedHashMap<>();
                    stepExtra.put("goal", String.valueOf(sopStep.getOrDefault("tool", "")));
                    if (result.isSuccess()) {
                        if (!phaseInput.isEmpty()) {
                            stepExtra.put("input", phaseInput);
                        }
                        if (!phaseOutput.isEmpty()) {
                            stepExtra.put("output", phaseOutput);
                        }
                    } else {
                        stepExtra.put("output", Map.of("summary", "执行失败：" + result.getErrorMessage()));
                    }
                    if (!stepExtra.containsKey("output")) {
                        stepExtra.put("output", Map.of("summary", TraceSnapshotBuilder.summarizeOutput(
                                result.isSuccess() ? extractConclusion(List.of(result)) : "", 1)));
                    }
                    stepExtra.put("trace", stepTrace);
                    emitter.emit("thinking", Map.of(
                            "steps", List.of(TraceSnapshotBuilder.thinkingStep(
                                    "sop-step-" + stepIdx,
                                    "第" + (stepIdx + 1) + "步 " + sopStep.getOrDefault("do", result.getToolName()),
                                    result.isSuccess() ? "已完成：" + sopStep.getOrDefault("do", result.getToolName())
                                            : "执行失败：" + sopStep.getOrDefault("do", result.getToolName()),
                                    stepExtra)),
                            "intent", intent
                    ));
                }
            }
        });

        // 查询收敛 SOP（方案 §4.5，阶段 B1）：流式手册链路与同步链路同判定。
        // FORCE_SUMMARY → 发 thinking（收敛追问说明）+ text（追问文案）+ done（clarify 契约）后返回；
        // REFINE → 注入降维呈现提示后继续原链路（不中断流）
        Map<String, Object> streamRefineReply = refinePolicyBranch(playbookCode, plan, question, results, context, startTime);
        if (streamRefineReply != null) {
            emitter.emit("thinking", Map.of(
                    "steps", List.of(TraceSnapshotBuilder.thinkingStep("generate", "收敛查询范围",
                            "命中结果较多，先确认筛选条件再精查",
                            Map.of("goal", "多轮收敛查询：先澄清后精查，避免一次性倾倒大量条目",
                                    "input", Map.of("question", question, "refine_hits", streamRefineReply.get("refine_hits")),
                                    "output", Map.of("summary", "已生成收敛追问，待补充后继续")))),
                    "intent", intent
            ));
            String refineMsg = String.valueOf(streamRefineReply.get("report"));
            context.addHistoryEntry("assistant", refineMsg);
            sessionManager.save(context);
            persistTurn(context, question, refineMsg, plan, List.of(), emitter);
            emitTextEvents(emitter, refineMsg);
            Map<String, Object> refineDone = new LinkedHashMap<>();
            refineDone.put("session_id", context.getSessionId());
            refineDone.put("intent", QueryPlan.INTENT_CLARIFY);
            refineDone.put("playbook", playbookCode);
            refineDone.put("clarify", streamRefineReply.get("clarify"));
            refineDone.put("clarify_contracts", streamRefineReply.get("clarify_contracts"));
            refineDone.put("refine_hits", streamRefineReply.get("refine_hits"));
            refineDone.put("conclusion", "");
            refineDone.put("suggested_follow_ups", List.of());
            refineDone.put("elapsed_ms", System.currentTimeMillis() - startTime);
            emitter.emit("done", refineDone);
            return true;
        }

        // ── 阶段④ 汇总：与常规链路同构 ──
        Map<String, Object> generateExtra = new LinkedHashMap<>();
        generateExtra.put("goal", "把手册各环节结果整合成您能直接使用的结论与建议");
        generateExtra.put("input", upstreamResultsInput(results));
        generateExtra.put("trace", List.of(Map.of(
                "stage", "llm",
                "message", "调用大模型汇总 " + results.size() + " 个环节的处理结果（各环节产出已随 tool 事件下发）")));
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("generate", "结果汇总",
                        TraceSnapshotBuilder.generateStepDesc(context), generateExtra)),
                "intent", intent
        ));
        String report = presenter.present(question, results, context);
        List<String> followUps = presenter.suggestFollowUps(question, results, context);
        String conclusionText = extractConclusion(results);
        Map<String, Object> generateDoneExtra = new LinkedHashMap<>();
        generateDoneExtra.put("input", upstreamResultsInput(results));
        generateDoneExtra.put("output", Map.of(
                "summary", TraceSnapshotBuilder.summarizeOutput(conclusionText, results.size()),
                "branch_taken", WorkflowGraphView.branchLabel("EXECUTE")));
        generateDoneExtra.put("trace", List.of(Map.of(
                "stage", "llm",
                "message", "大模型已按「结论先行 + 依据支撑」结构生成回答，依据来自手册各步骤的实际产出")));
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("generate", "结果汇总",
                        TraceSnapshotBuilder.generateStepDesc(context), generateDoneExtra)),
                "intent", intent
        ));

        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, report, plan, results, emitter);
        emitTextEvents(emitter, report);
        Map<String, Object> donePayload = new LinkedHashMap<>();
        donePayload.put("session_id", context.getSessionId());
        donePayload.put("intent", intent);
        donePayload.put("playbook", playbookCode);
        donePayload.put("conclusion", conclusionText);
        donePayload.put("suggested_follow_ups", presenter.suggestFollowUps(question, results, context));
        donePayload.put("elapsed_ms", System.currentTimeMillis() - startTime);
        // 查询审计（方案 §6-A3）：手册直达链路同样随 done 事件透出
        donePayload.put(com.sitech.prodai.service.agent.model.QueryAuditRecorder.AUDIT_KEY,
                queryAuditRecorder.record(queryAuditRecorder.build(
                        context.getUserScope(), context.getScene(), plan, results, playbookCode)));
        emitter.emit("done", donePayload);
        return true;
    }

    /** 工具产出 → 过程留痕（与 buildToolEvent 同源：本体/规则推理、数据查询等环节明细）。 */
    private List<Map<String, Object>> toolTraceOf(ExecutionResult result) {
        return switch (result.getToolName()) {
            case "sparql_query" -> TraceSnapshotBuilder.ontologyQueryTrace(result);
            default -> TraceSnapshotBuilder.ontologyTraceView(result);
        };
    }

    /**
     * 手册步骤 → 该步骤的留痕：工具已原子化（一个工具一个环节，步骤与工具 1:1），
     * 每步贴该工具真实执行的全量留痕，无环节切片。
     */
    private List<Map<String, Object>> stepTraceOf(ExecutionResult result, int stepIdx) {
        return toolTraceOf(result);
    }

    /**
     * 手册步骤 → 该步骤的输入/输出视图：工具已原子化（步骤与工具 1:1），
     * 环节切片退化为全量——ops 与 rd 原子工具统一走 opsAnalysisPhaseIo
     * （按工具名分发，输入承接上一环节产出、输出讲本环节结论），其余回退空。
     */
    private Map<String, Object> stepIoOf(ExecutionResult result, int stepIdx) {
        return switch (result.getToolName()) {
            case "sparql_query", "swrl_root_cause", "swrl_risk_audit", "ontology_explain", "rule_explain",
                 "rd_doc_parse", "rd_draft_extract", "rd_compliance", "rd_workorder_create",
                 "rd_category_resolve", "rd_draft_generate", "rd_config_search" ->
                    TraceSnapshotBuilder.opsAnalysisPhaseIo(result, stepIdx);
            default -> Map.of();
        };
    }

    /**
     * 工具名 → 手册步骤序号（首个绑定该工具的步骤）：回放快照按工具结果重建手册步骤时定位序号。
     * 步骤与工具 1:1（工具原子化），多步骤共用同一工具时取首步（与执行层只执行一次一致）。
     */
    private static Integer sopStepIndexOf(List<Map<String, Object>> sopSteps, String toolName) {
        for (int i = 0; i < sopSteps.size(); i++) {
            if (toolName != null && toolName.equals(String.valueOf(sopSteps.get(i).getOrDefault("tool", "")).trim())) {
                return i;
            }
        }
        return null;
    }

    /**
     * 手册步骤序列 → 工具链（保序去重）：步骤 tool 是调用语句（配置独一份在 AgentTool 注册表），
     * 编排层照手册步骤顺序调用工具；多步骤共用同一工具时只执行一次。
     */
    private static List<String> playbookTools(Map<String, Object> book) {
        List<String> tools = new ArrayList<>();
        if (book.get("steps") instanceof List<?> steps) {
            for (Object o : steps) {
                if (o instanceof Map<?, ?> step) {
                    Object toolVal = step.get("tool");
                    String tool = toolVal == null ? "" : String.valueOf(toolVal).trim();
                    if (!tool.isBlank() && !tools.contains(tool)) {
                        tools.add(tool);
                    }
                }
            }
        }
        return tools;
    }

    /**
     * 手册步骤序列 → ExecStep 依赖编排：步骤声明 input_from（参数名 → "上游工具.输出键"）
     * 时生成 result:<上游工具>.<输出键> 来源映射（执行层经 ParamResolver 解析前序步骤产出），
     * 解决 direct 全参透传无法表达跨工具数据流的问题（如智聊链路开单承接草稿生成产出）。
     * <p>
     * 校验口径与理解层 buildExecSteps 一致：上游工具必须在手册工具链内、
     * 引用键必须在工具输出契约中（非法映射剔除，回落 direct 透传）。
     */
    private com.sitech.prodai.service.agent.model.ExecStep playbookExecStep(
            Map<?, ?> step, List<String> tools) {
        Object toolVal = step.get("tool");
        String tool = toolVal == null ? "" : String.valueOf(toolVal).trim();
        com.sitech.prodai.service.agent.model.ExecStep execStep =
                new com.sitech.prodai.service.agent.model.ExecStep(tool);
        if (!(step.get("input_from") instanceof Map<?, ?> from)) {
            return execStep;
        }
        for (Map.Entry<?, ?> e : from.entrySet()) {
            String param = String.valueOf(e.getKey());
            String ref = String.valueOf(e.getValue()).trim();
            int dot = ref.indexOf('.');
            String upstreamTool = dot > 0 ? ref.substring(0, dot) : ref;
            String key = dot > 0 ? ref.substring(dot + 1) : "";
            if (!tools.contains(upstreamTool) || key.isBlank()) {
                log.warn("[AgentOrchestrator] 手册步骤 input_from 引用非法，剔除: {} -> {}", param, ref);
                continue;
            }
            AgentTool upstream = toolMap.get(upstreamTool);
            if (upstream == null || upstream.getOutputFields().stream()
                    .noneMatch(f -> key.equals(f.getName()) || key.equals(f.getOutputKey()))) {
                log.warn("[AgentOrchestrator] 手册步骤 input_from 引用键不在上游输出契约，剔除: {} -> {}", param, ref);
                continue;
            }
            execStep.getParamMappings().put(param, "result:" + upstreamTool + "." + key);
        }
        return execStep;
    }

    /**
     * 手册步骤序列 → ExecStep 序列（保序去重，与 playbookTools 口径一致）：
     * 步骤声明 input_from 时生成 result: 来源映射，跨工具数据流走执行层依赖解析。
     */
    private List<com.sitech.prodai.service.agent.model.ExecStep> playbookExecSteps(
            Map<String, Object> book, List<String> tools) {
        List<com.sitech.prodai.service.agent.model.ExecStep> out = new ArrayList<>();
        if (book.get("steps") instanceof List<?> steps) {
            for (Object o : steps) {
                if (!(o instanceof Map<?, ?> step)) {
                    continue;
                }
                Object toolVal = step.get("tool");
                String tool = toolVal == null ? "" : String.valueOf(toolVal).trim();
                if (tool.isBlank() || !tools.contains(tool)
                        || out.stream().anyMatch(s -> tool.equals(s.getTool()))) {
                    continue; // 多步骤共用同一工具时只执行一次
                }
                out.add(playbookExecStep(step, tools));
            }
        }
        return out;
    }

    /** SOP 文本 → 步骤结构列表：[{do, how, tool}]（「第N步 X——Y（工具：t）」行解析）。 */
    private static List<Map<String, Object>> parseSopSteps(String sop) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (sop == null || sop.isBlank()) {
            return steps;
        }
        for (String line : sop.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("第") || !t.contains("步 ")) {
                continue;
            }
            int stepNoEnd = t.indexOf("步 ");
            String title = t.substring(stepNoEnd + 2).trim();
            String how = "";
            String tool = "";
            // 结构：标题——方法（工具：t）；约束：p
            int dash = title.indexOf("——");
            if (dash > 0) {
                how = title.substring(dash + 2).trim();
                title = title.substring(0, dash).trim();
            }
            int toolStart = how.indexOf("（工具：");
            if (toolStart >= 0) {
                int toolEnd = how.indexOf("）", toolStart);
                if (toolEnd > toolStart) {
                    tool = how.substring(toolStart + 4, toolEnd).trim();
                    how = (how.substring(0, toolStart) + how.substring(toolEnd + 1)).trim();
                }
            }
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("do", title);
            step.put("how", how);
            step.put("tool", tool);
            steps.add(step);
        }
        return steps;
    }

    /**
     * rd 场景透传会话 ID：AgentTool 接口无 context 参数，经 plan.params → executor direct 兜底
     * 透传给工具（如 rd_workorder_create 开单需绑定会话，否则不开单）。
     * session_id 是系统参数，必须以服务端 SessionContext 为准：请求参数可能缺省或带错值，
     * 故此处强制覆盖（与常规链路 DefaultUnderstander 的注入策略一致）。
     */
    private void injectSessionId(Map<String, Object> planParams, SessionContext context) {
        if (context != null && context.getSessionId() != null && !context.getSessionId().isBlank()) {
            planParams.put("session_id", context.getSessionId());
        }
    }

    /**
     * 手册直达链路按工具参数契约自动补槽：理解层 LLM 未必为手册链路抽取槽位，
     * 工具声明的 source=question 必填参数（如 rd_draft_generate 的 text、
     * rd_config_search 的 question）可能拿不到值——executor 的 direct 兜底只透传 plan.params
     * 同名键，而 plan.params 里只有 question，参数名不一致即触发「缺少配置需求描述」类失败。
     * <p>
     * 修复策略：遍历手册工具链上每个工具的参数契约，凡 source=question 的参数
     * （理解层本应从用户原话抽取）直接以用户原话填充；请求 params 已显式携带的键不覆盖。
     */
    private void fillQuestionSlots(List<String> tools, Map<String, Object> planParams, String question) {
        if (question == null || question.isBlank()) {
            return;
        }
        for (String toolName : tools) {
            AgentTool tool = toolMap.get(toolName);
            if (tool == null) {
                continue;
            }
            for (ToolParam param : tool.getParams()) {
                if ("question".equals(param.getSource()) && param.getName() != null
                        && !planParams.containsKey(param.getName())) {
                    planParams.put(param.getName(), question);
                }
            }
        }
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
    private Map<String, Object> buildQueryPlanView(QueryPlan plan, SessionContext context) {
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
        persistTurn(context, question, assistantReply, plan, results, null, null, null, null);
    }

    /** 流式便捷重载：持久化失败时通过 emitter 推送 warning 事件 */
    private void persistTurn(SessionContext context, String question,
                             String assistantReply, QueryPlan plan,
                             List<ExecutionResult> results, StreamEmitter emitter) {
        persistTurn(context, question, assistantReply, plan, results, emitter, null, null, null);
    }

    /**
     * FLOW_EXEC 专用重载（S3-E 历史回放）：固定流程回复无 QueryPlan，
     * 但需将 flow_matched / flow_execution 随 metadata 落库，
     * 否则刷新/切换会话后执行明细卡片丢失。
     */
    private void persistTurn(SessionContext context, String question,
                             Map<String, Object> flowReply, StreamEmitter emitter) {
        persistTurn(context, question,
                String.valueOf(flowReply.getOrDefault("report", "")),
                null, List.of(), emitter, null,
                flowReply.get("flow_matched"), flowReply.get("flow_execution"));
    }

    private static final String PERSIST_WARNING_MESSAGE =
            "本轮对话未能保存到历史记录（存储异常），请检查数据服务";

    /** 从 flow_execution 快照中提取 execution_id（W6-2 时间线重建用；缺失返回 null） */
    private static String extractExecutionId(Object flowExecution) {
        if (flowExecution instanceof Map<?, ?> execMap && execMap.get("execution_id") != null) {
            return String.valueOf(execMap.get("execution_id"));
        }
        return null;
    }

    /**
     * @param emitter  流式入口传 emitter，持久化失败时同步推送 warning 事件（前端可见）
     * @param warnings 非流式入口传收集器，失败时追加（调用方随响应体透传）；可传 null
     * @param flowMatched    FLOW_EXEC 专用：命中的流程摘要（其余链路传 null）
     * @param flowExecution  FLOW_EXEC 专用：引擎执行明细快照（其余链路传 null）
     */
    private void persistTurn(SessionContext context, String question,
                             String assistantReply, QueryPlan plan,
                             List<ExecutionResult> results, StreamEmitter emitter,
                             List<String> warnings,
                             Object flowMatched, Object flowExecution) {
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
                meta.put("intent_type", plan != null ? plan.getIntent()
                        : (flowMatched != null ? "FLOW_EXEC" : ""));
                meta.put("stream_text", assistantReply);
                meta.put("content_type", "chat");
                meta.put("done", true);
                if (plan != null) {
                    meta.put("query_plan", toJson(buildQueryPlanView(plan, context)));
                    // 思考时间线快照：与实时 reasoning 步骤同构（intent/plan/tool/generate），
                    // 前端 normalizeReasoningList 直接消费，保证历史回放与实时渲染一致
                    meta.put("reasoning_full", toJson(buildReasoningSnapshot(context, plan, results, assistantReply, question)));
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
                // FLOW_EXEC 执行明细快照（S3-E）：与实时 done 事件的 flow_matched/flow_execution 同源，
                // 前端 restoreMessageMetadata 据此在历史回放时还原执行明细卡片
                if (flowMatched != null) {
                    meta.put("flow_matched", toJson(flowMatched));
                }
                if (flowExecution != null) {
                    meta.put("flow_execution", toJson(flowExecution));
                }
                // W6-2 历史回放时间线：从 node_logs 重建与实时 flow_progress 同构的时间线，
                // 随 metadata 落库，前端 restoreMessageMetadata 据此还原节点级执行过程
                if (progressReplayer != null) {
                    String executionId = extractExecutionId(flowExecution);
                    if (executionId != null) {
                        List<Map<String, Object>> timeline = progressReplayer.rebuild(executionId);
                        if (!timeline.isEmpty()) {
                            meta.put(com.sitech.prodai.service.agent.flow.FlowProgressReplayer.TIMELINE_KEY,
                                    toJson(timeline));
                        }
                    }
                }
                // W2 挂起绑定持久化：会话当前挂起态（execution_id + resume_token + form_spec），
                // SessionManager 快照恢复时回读，跨轮/重启不依赖内存 TTL
                if (context.getExecutionBinding() != null) {
                    meta.put("execution_binding", toJson(context.getExecutionBinding()));
                }
                // 查询审计（方案 §6-A3）：随消息 metadata 落库（历史回放可对账，审计视图不含敏感明细）
                meta.put(com.sitech.prodai.service.agent.model.QueryAuditRecorder.AUDIT_KEY,
                        toJson(queryAuditRecorder.build(
                                context.getUserScope(), context.getScene(), plan, results, null)));
                svc.saveMessage(sessionId, "assistant", assistantReply, "text", meta);
            }
            log.info("[AgentOrchestrator] 会话已持久化: sessionId={}", sessionId);
        } catch (Exception e) {
            // 持久化失败不影响对话主流程，但必须让用户可感知（会话不会进历史）
            log.warn("[AgentOrchestrator] 会话持久化失败: {}", e.getMessage());
            if (warnings != null) {
                warnings.add(PERSIST_WARNING_MESSAGE + ": " + e.getMessage());
            }
            if (emitter != null) {
                try {
                    emitter.emit("warning", Map.of(
                            "message", PERSIST_WARNING_MESSAGE,
                            "error", String.valueOf(e.getMessage())));
                } catch (Exception ignored) {
                    // 连接已断开，无需再通知
                }
            }
        }
    }

    /**
     * 构建本轮思考时间线快照：理解 → 计划 → 工具 → 汇总，与实时 SSE thinking/tool 步骤同构。
     * <p>
     * 实时流包含 intent / plan / tool / generate 四类步骤，历史回放须等量还原，
     * 否则历史会话的思考时间线比实时会话短（快照不完整）。
     */
    private List<Map<String, Object>> buildReasoningSnapshot(SessionContext context, QueryPlan plan,
                                                             List<ExecutionResult> results, String report, String question) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (plan == null) {
            return steps;
        }
        // ① 意图识别：输出 = 结构化意图（下游各节点的输入来源）
        Map<String, Object> intentOutput = new LinkedHashMap<>();
        intentOutput.put("summary", "已明确：本次要执行「" + TraceSnapshotBuilder.actionDisplay(plan) + "」");
        intentOutput.put("structured_intent", TraceSnapshotBuilder.planIntentView(plan));
        Map<String, Object> intentStep = new LinkedHashMap<>();
        intentStep.put("id", "intent");
        intentStep.put("type", "thinking");
        intentStep.put("title", TraceSnapshotBuilder.intentStepName(context));
        intentStep.put("content", TraceSnapshotBuilder.intentStepDesc(context));
        intentStep.put("status", "done");
        intentStep.put("category", "understand");
        intentStep.put("goal", "先听懂您要做什么，再决定怎么办");
        intentStep.put("input", Map.of("question", TraceSnapshotBuilder.requirementSummary(question)));
        intentStep.put("output", intentOutput);
        List<Map<String, Object>> trace = TraceSnapshotBuilder.traceView(plan);
        if (trace != null) {
            intentStep.put("trace", trace);
        }
        steps.add(intentStep);
        // ② 处理方案：输入 = ①的结构化意图（数据流承接）
        Map<String, Object> planStep = new LinkedHashMap<>();
        planStep.put("id", "plan");
        planStep.put("type", "thinking");
        planStep.put("title", "方案规划");
        planStep.put("content", TraceSnapshotBuilder.buildReadablePlan(plan));
        planStep.put("status", "done");
        planStep.put("category", "understand");
        planStep.put("goal", TraceSnapshotBuilder.planStepGoal(plan, context));
        planStep.put("input", TraceSnapshotBuilder.upstreamIntentInput(plan, question));
        planStep.put("workflow", TraceSnapshotBuilder.buildWorkflow(plan));
        planStep.put("output", Map.of("summary", TraceSnapshotBuilder.planStepOutput(plan, context),
                "branch_taken", WorkflowGraphView.branchLabel(WorkflowGraphView.takenBranch(plan))));
        if (trace != null) {
            planStep.put("trace", trace);
        }
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
        // ③′ 手册步骤（sop-step-N）：手册链路（plan.sopSteps 非空）回放等量还原 ——
        // 实时流在 onStepComplete 逐条落地手册步骤思考条目，快照缺失会导致历史回放时间线比实时短。
        // 步骤与工具 1:1（工具原子化）：每条手册步骤贴对应工具的真实留痕与环节 IO
        List<Map<String, Object>> sopSteps = plan.getSopSteps();
        if (sopSteps != null && !sopSteps.isEmpty() && results != null) {
            for (ExecutionResult result : results) {
                Integer stepIdx = sopStepIndexOf(sopSteps, result.getToolName());
                if (stepIdx == null) {
                    continue;
                }
                Map<String, Object> sopStep = sopSteps.get(stepIdx);
                List<Map<String, Object>> stepTrace = new ArrayList<>();
                stepTrace.add(Map.of("stage", "sop", "message",
                        String.valueOf(sopStep.getOrDefault("how", "按手册执行"))));
                if (result.isSuccess()) {
                    List<Map<String, Object>> toolTrace = toolTraceOf(result);
                    if (toolTrace != null) {
                        stepTrace.addAll(toolTrace);
                    }
                } else {
                    stepTrace.add(Map.of("stage", "llm", "message", "执行失败：" + result.getErrorMessage()));
                }
                steps.add(TraceSnapshotBuilder.sopStepThinking(stepIdx, sopStep, result,
                        stepTrace, stepIoOf(result, stepIdx)));
            }
        }
        // ④ 汇总步骤：实时流的 generate 步骤（含结论输出），历史回放等量还原
        // 输入 = ③各工具的实际产出（数据流承接）；输出 = 整合性短文案（与实时流同构，不复述完整报告）
        Map<String, Object> generateStep = new LinkedHashMap<>();
        generateStep.put("id", "generate");
        generateStep.put("type", "thinking");
        generateStep.put("title", "结果汇总");
        generateStep.put("content", TraceSnapshotBuilder.generateStepDesc(context));
        generateStep.put("status", "done");
        generateStep.put("goal", "把各环节结果整合成您能直接使用的结论与建议");
        generateStep.put("input", upstreamResultsInput(results));
        generateStep.put("output", Map.of(
                "summary", TraceSnapshotBuilder.summarizeOutput(extractConclusion(results), results != null ? results.size() : 0),
                "branch_taken", WorkflowGraphView.branchLabel(WorkflowGraphView.takenBranch(plan))));
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
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
        // 权限上下文不进业务参数面：applySuppliedParams 先于 extractUserScope 执行，
        // __user_scope__ 若经 resolveParam 混入 resolvedParams 会随 plan.params 透传给
        // 工具入参/LLM 规划（时间线可见"输入里只有 __user_scope__"即此泄漏佐证），此处清理
        context.getResolvedParams().remove("__user_scope__");
        context.getMeta().remove("last___user_scope__");
    }

    /**
     * 行权限（方案 §4.4）：从请求参数中提取 Controller 注入的 UserScope（服务端唯一生产入口），
     * 写入 SessionContext（随会话刷新，权限变更即时生效），并从 params 移除——
     * 防止作为普通参数透传给工具入参/LLM 规划（权限不进业务参数面）。
     */
    private void extractUserScope(SessionContext context, Map<String, Object> params) {
        if (params == null) {
            return;
        }
        Object raw = params.remove("__user_scope__");
        if (raw instanceof com.sitech.prodai.service.agent.model.UserScope scope) {
            context.setUserScope(scope);
        }
    }
    // ── W2 对话内挂起恢复（ChatHumanBridge 接线） ──

    /**
     * 挂起态短路恢复（一次性接口）：用户回复文本/表单数据 → ChatHumanBridge.resume → 引擎续推。
     * binding 为 null（非法态）时返回 null 放行常规链路。
     */
    private Map<String, Object> resumePendingExecution(SessionContext context, String question,
                                                       Map<String, Object> params, long startTime) {
        Map<String, Object> reply = resumePendingExecution(context, question, params);
        if (reply == null) {
            return null;
        }
        reply.putIfAbsent("session_id", context.getSessionId());
        String report = String.valueOf(reply.getOrDefault("report", ""));
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, reply, null);
        reply.put("elapsed_ms", System.currentTimeMillis() - startTime);
        return reply;
    }

    /**
     * 挂起态短路恢复（流式接口）：事件契约与 FLOW_EXEC 链路一致（thinking → text* → text_done → done）。
     * 返回 false 表示 binding 非法（放行常规链路）。
     */
    private boolean resumePendingExecution(SessionContext context, String question,
                                           Map<String, Object> params, StreamEmitter emitter, long startTime) {
        Map<String, Object> reply = resumePendingExecution(context, question, params);
        if (reply == null) {
            return false;
        }
        reply.putIfAbsent("session_id", context.getSessionId());
        String report = String.valueOf(reply.getOrDefault("report", ""));
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("intent", "继续未完成的流程",
                        "会话中有等待您确认的流程节点，直接续推执行",
                        Map.of("goal", "挂起态回复语义由流程定义，无需重新理解",
                                "input", Map.of("question", question),
                                "output", Map.of("summary", report))))
        ));
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, reply, emitter);
        emitTextEvents(emitter, report);
        Map<String, Object> donePayload = new LinkedHashMap<>();
        donePayload.put("session_id", context.getSessionId());
        donePayload.put("intent", reply.getOrDefault("intent", "FLOW_RESUME"));
        donePayload.put("flow_execution", reply.get("flow_execution"));
        // 恢复后若再次挂起（下一道阶段门），刷新绑定并随 done 下发新表单
        captureSuspensionBinding(context, reply);
        appendBindingToDone(donePayload, context);
        donePayload.put("conclusion", reply.getOrDefault("conclusion", ""));
        donePayload.put("suggested_follow_ups", reply.getOrDefault("suggested_follow_ups", List.of()));
        donePayload.put("elapsed_ms", System.currentTimeMillis() - startTime);
        emitter.emit("done", donePayload);
        return true;
    }

    /**
     * 挂起恢复共用体：文本+表单数据映射 → resumeFromHuman → 回复组装。
     * 桥接器缺失 / binding 非法 / 引擎拒绝（令牌失效等）时返回 null 放行常规链路
     * （拒绝后清空绑定，避免会话卡死在挂起态）。
     */
    private Map<String, Object> resumePendingExecution(SessionContext context, String question,
                                                       Map<String, Object> params) {
        if (chatHumanBridge == null) {
            return null;
        }
        Map<String, Object> binding = context.getExecutionBinding();
        ApiResponse<Map<String, Object>> resp = chatHumanBridge.resume(binding, question, params, null);
        if (resp == null) {
            return null;
        }
        context.setExecutionBinding(null);
        if (resp.getData() == null) {
            Map<String, Object> failReply = new LinkedHashMap<>();
            failReply.put("intent", "FLOW_RESUME");
            failReply.put("report", "流程恢复失败：" + (resp.getMessage() == null ? "未知原因" : resp.getMessage()));
            failReply.put("flow_execution", Map.of("status", "failed", "error_message", resp.getMessage()));
            failReply.put("conclusion", "");
            failReply.put("suggested_follow_ups", List.of("重新发起该流程"));
            return failReply;
        }
        Map<String, Object> data = resp.getData();
        String status = String.valueOf(data.getOrDefault("status", "unknown"));
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("intent", "FLOW_RESUME");
        reply.put("flow_execution", data);
        if ("completed".equals(status)) {
            String outcome = flowOutcomeSummary(data);
            reply.put("report", "流程已按您的确认执行完成" + (outcome.isEmpty() ? "。" : "：" + outcome));
            reply.put("conclusion", "执行完成");
            reply.put("suggested_follow_ups", List.of("查看执行明细"));
        } else if ("waiting_human".equals(status)) {
            reply.put("report", "流程进入下一个确认节点，请查看表单并回复。");
            reply.put("conclusion", "");
            reply.put("suggested_follow_ups", List.of());
        } else {
            reply.put("report", "流程执行状态：" + status
                    + (data.get("error_message") == null ? "" : "，错误：" + data.get("error_message")));
            reply.put("conclusion", "");
            reply.put("suggested_follow_ups", List.of());
        }
        return reply;
    }

    /**
     * 从执行终态 output_data 提取业务结果摘要（人话化）：
     * 工单号/商品名来自 create-draft 类工具输出，提交结果来自 order 类节点 answer。
     * 任何异常降级为空串，不影响恢复主链路。
     */
    private String flowOutcomeSummary(Map<String, Object> execData) {
        try {
            if (!(execData.get("output_data") instanceof Map<?, ?> out)) {
                return "";
            }
            String workOrderId = strField(out, "work_order_id");
            String offeringName = strField(out, "offering_name");
            String orderAnswer = strField(out, "order_answer");
            StringBuilder sb = new StringBuilder();
            if (!offeringName.isBlank()) {
                sb.append("已提交「").append(offeringName).append("」");
            }
            if (!workOrderId.isBlank()) {
                sb.append(sb.length() > 0 ? "，" : "").append("工单 ").append(workOrderId);
            }
            if (!orderAnswer.isBlank()) {
                sb.append(sb.length() > 0 ? "，" : "").append(orderAnswer);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 执行 output_data 顶层字符串字段安全读取（兼容各节点输出透传） */
    private String strField(Map<?, ?> data, String key) {
        Object v = data.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * 挂起绑定捕获：流程回复 status=waiting_human 时，经桥接器把挂起信息写入
     * SessionContext.executionBinding（非挂起态清空既有绑定）。
     */
    private void captureSuspensionBinding(SessionContext context, Map<String, Object> flowReply) {
        if (chatHumanBridge == null) {
            return;
        }
        Map<String, Object> binding = null;
        if (flowReply.get("flow_execution") instanceof Map<?, ?> exec) {
            @SuppressWarnings("unchecked")
            Map<String, Object> execMap = (Map<String, Object>) exec;
            binding = chatHumanBridge.buildBinding(execMap);
        }
        context.setExecutionBinding(binding);
    }

    /** done 载荷追加挂起信息：execution_binding + clarify_contracts（form_spec 翻译产物）。 */
    private void appendBindingToDone(Map<String, Object> donePayload, SessionContext context) {
        Map<String, Object> binding = context.getExecutionBinding();
        if (binding == null) {
            return;
        }
        donePayload.put("execution_binding", binding);
        if (chatHumanBridge != null && binding.get("form_spec") instanceof Map<?, ?> spec) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedSpec = (Map<String, Object>) spec;
            Map<String, Map<String, Object>> contracts = chatHumanBridge.toClarifyContracts(typedSpec);
            if (!contracts.isEmpty()) {
                donePayload.put("clarify_contracts", contracts);
            }
        }
    }

    // ── W4 流程进度桥接（引擎事件 → SSE flow_progress） ──

    /**
     * 注册流式请求的进度转发目标（场景链路 tryRoute 之前调用）。
     * <p>
     * startExecution 前执行 ID 未知 → 先以会话级临时键注册（保证引擎发布器换装生效），
     * 返回后如拿到真实 execution_id，由引擎侧事件按 executionId 匹配转发。
     * 桥接器缺失（单测注入 null）时返回 null，调用方跳过注销。
     *
     * @return 注册键（注销时回传），桥接器缺失返回 null
     */
    private String registerProgressSink(StreamEmitter emitter) {
        if (progressBridge == null || emitter == null) {
            return null;
        }
        String key = "sink-" + System.nanoTime();
        progressBridge.register(key, (eventName, payload) ->
                emitter.emit(eventName, payload), null);
        return key;
    }

    /** 注销进度转发目标（场景链路 finally 调用）；progressExecutionId 为 null 时静默跳过。 */
    private void unregisterProgressSink(String progressExecutionId) {
        if (progressBridge == null || progressExecutionId == null) {
            return;
        }
        progressBridge.unregister(progressExecutionId, null);
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

        // 行权限（方案 §4.4）：与同步链路同构绑定 + finally 清理
        extractUserScope(context, params);
        com.sitech.prodai.service.agent.model.UserScopeContext.bind(context.getUserScope());
        try {
            doProcessStream(question, context, params, scene, emitter, startTime);
        } finally {
            com.sitech.prodai.service.agent.model.UserScopeContext.clear();
        }
    }

    /** processStream 的权限绑定后主链路（拆出仅为 try-finally 收口清晰）。 */
    private void doProcessStream(String question, SessionContext context, Map<String, Object> params,
                                 String scene, StreamEmitter emitter, long startTime) {

        // W2 挂起态短路：会话绑定待恢复工作流 → 直接走 ChatHumanBridge.resume（不过理解层 LLM）
        if (context.hasPendingExecution() && resumePendingExecution(context, question, params, emitter, startTime)) {
            return;
        }

        // 附件-only 分支（流式）：只上传附件未输入文本 → 解析 + 总结 + 开放追问
        if (isAttachmentOnly(question, params)) {
            attachmentOnlyStream(question, params, context, emitter, startTime);
            return;
        }

        // 阶段事件①：理解中 —— 先于 LLM 理解调用推送，思考时间线即刻起表并读秒
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("intent", TraceSnapshotBuilder.intentStepName(context),
                        TraceSnapshotBuilder.intentStepDesc(context),
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

        // W3 场景工作流路由先于手册升级：场景工作流（用户自建/存量）优先级更高，命中即短路
        String progressExecutionId = registerProgressSink(emitter);
        try {
            java.util.Optional<Map<String, Object>> sceneReply = sceneFlowRouter.tryRoute(plan, context, null);
            if (sceneReply.isPresent()) {
                Map<String, Object> reply = sceneReply.get();
                reply.putIfAbsent("session_id", context.getSessionId());
                String report = String.valueOf(reply.getOrDefault("report", ""));
                emitter.emit("thinking", Map.of(
                        "steps", List.of(TraceSnapshotBuilder.thinkingStep("intent",
                                TraceSnapshotBuilder.intentStepName(context),
                                TraceSnapshotBuilder.intentStepDesc(context),
                                Map.of("goal", "已明确：本次要执行「" + TraceSnapshotBuilder.actionDisplay(plan) + "」",
                                        "input", Map.of("question", question),
                                        "output", Map.of("summary", "命中场景工作流，进入固化链路执行")))),
                        "intent", plan.getIntent()
                ));
                context.addHistoryEntry("assistant", report);
                // 挂起绑定捕获先于 persistTurn（同同步链路，保证 metadata 落库）
                captureSuspensionBinding(context, reply);
                sessionManager.save(context);
                persistTurn(context, question, reply, emitter);
                emitTextEvents(emitter, report);
                Map<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("session_id", context.getSessionId());
                donePayload.put("intent", reply.getOrDefault("intent", "FLOW_EXEC"));
                donePayload.put("flow_matched", reply.get("flow_matched"));
                donePayload.put("flow_execution", reply.get("flow_execution"));
                appendBindingToDone(donePayload, context);
                donePayload.put("conclusion", reply.getOrDefault("conclusion", ""));
                donePayload.put("suggested_follow_ups",
                        reply.getOrDefault("suggested_follow_ups", List.of()));
                donePayload.put("elapsed_ms", System.currentTimeMillis() - startTime);
                emitter.emit("done", donePayload);
                return;
            }
        } finally {
            unregisterProgressSink(progressExecutionId);
        }

        // 手册意图升级：LLM 识别的意图（经归一化）命中手册 applies_to.intents →
        // 升级流式手册直达链路（sop-step-N 时间线 + 环节 IO）；
        // 未命中回落常规动态编排（SOP 已由理解层注入 prompt，LLM 照手册自由编排）
        if (playbookUpgradeStream(plan, context, params, emitter, startTime)) {
            return;
        }

        // 工作流定义：本轮真实业务流程（节点+分支条件+数据流），随首个 thinking 事件一次性下发
        emitter.emit("workflow", WorkflowGraphView.build(plan, WorkflowGraphView.takenBranch(plan)));
        // 阶段事件①′：理解完成，原地更新 intent 步骤（补输出：已明确的业务动作）
        // 输出 = 结构化意图（动作 + 业务要素），作为下游 plan/execute/summarize 的唯一输入来源
        Map<String, Object> intentOutput = new LinkedHashMap<>();
        intentOutput.put("summary", "已明确：本次要执行「" + TraceSnapshotBuilder.actionDisplay(plan) + "」");
        intentOutput.put("structured_intent", TraceSnapshotBuilder.planIntentView(plan));
        Map<String, Object> intentExtra = new LinkedHashMap<>();
        intentExtra.put("goal", "先听懂您要做什么，再决定怎么办");
        intentExtra.put("input", Map.of("question", question));
        intentExtra.put("output", intentOutput);
        intentExtra.put("trace", TraceSnapshotBuilder.traceView(plan));
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("intent", TraceSnapshotBuilder.intentStepName(context),
                        TraceSnapshotBuilder.intentStepDesc(context), intentExtra)),
                "intent", plan.getIntent()
        ));
        // 阶段事件②：计划确认 —— 将内部「查询计划中间语言」翻译为业务可读的筛查方案
        // （取代原先透传 raw queryPlan 给前端渲染内部码卡片，避免对业务人员造成困惑）
        // 输入 = ①的结构化意图（承接上游输出，而非复述用户原文）
        Map<String, Object> planExtra = new LinkedHashMap<>();
        planExtra.put("goal", TraceSnapshotBuilder.planStepGoal(plan, context));
        planExtra.put("input", TraceSnapshotBuilder.upstreamIntentInput(plan, question));
        planExtra.put("workflow", TraceSnapshotBuilder.buildWorkflow(plan));
        planExtra.put("output", Map.of(
                "summary", TraceSnapshotBuilder.planStepOutput(plan, context),
                "branch_taken", WorkflowGraphView.branchLabel(WorkflowGraphView.takenBranch(plan))));
        planExtra.put("trace", TraceSnapshotBuilder.traceView(plan));
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("plan", "方案规划",
                        TraceSnapshotBuilder.buildReadablePlan(plan), planExtra)),
                "intent", plan.getIntent()
        ));

        // 澄清分支：thinking → text（追问文案）→ done
        if (QueryPlan.INTENT_CLARIFY.equals(plan.getIntent())) {
            context.setLastIntent(plan.getIntent());
            context.setLastClarifyParams(plan.getClarify());
            context.setLastTools(plan.getTools());
            context.setLastParams(plan.getParams());
            // 阶段事件③：生成中 —— 表达层为 LLM 长调用，先推步骤保持反馈
            // 输入 = ①意图输出中的要素缺口（missing），而非用户原文复述
            emitter.emit("thinking", Map.of(
                    "steps", List.of(TraceSnapshotBuilder.thinkingStep("generate", "组织追问",
                            "正在生成补充信息的询问…",
                            Map.of("goal", "信息不全时先问清楚，避免答非所问",
                                    "input", TraceSnapshotBuilder.clarifyInputView(plan, question)))),
                    "intent", plan.getIntent()
            ));
            String clarifyMessage = presenter.present(question, List.of(), context);
            // 阶段事件③′：追问生成完成，原地更新 generate 步骤（补输出：整合性短文案，追问文案由正文承载）
            List<String> missingParams = plan.getClarify() != null ? plan.getClarify() : List.of();
            emitter.emit("thinking", Map.of(
                    "steps", List.of(TraceSnapshotBuilder.thinkingStep("generate", "组织追问",
                            "正在生成补充信息的询问…",
                            Map.of("goal", "信息不全时先问清楚，避免答非所问",
                                    "input", TraceSnapshotBuilder.clarifyInputView(plan, question),
                                    "output", Map.of("summary", missingParams.isEmpty()
                                            ? "已生成追问，待您补充后继续"
                                            : "已生成追问，待补充：" + String.join("、", missingParams),
                                            "branch_taken", WorkflowGraphView.branchLabel("CLARIFY"))))),
                    "intent", plan.getIntent()
            ));
            context.addHistoryEntry("assistant", clarifyMessage);
            sessionManager.save(context);
            persistTurn(context, question, clarifyMessage, plan, List.of(), emitter);
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
            persistTurn(context, question, confirmMessage, plan, List.of(), emitter);
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
        // 输入 = 执行层各工具的实际产出（承接上游），不再是用户原文复述
        Map<String, Object> generateExtra = new LinkedHashMap<>();
        generateExtra.put("goal", "把各环节结果整合成您能直接使用的结论与建议");
        generateExtra.put("input", upstreamResultsInput(results));
        generateExtra.put("trace", List.of(Map.of(
                "stage", "llm",
                "message", "调用大模型汇总" + results.size() + " 个环节的处理结果，组织成结论与建议")));
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("generate", "结果汇总",
                        TraceSnapshotBuilder.generateStepDesc(context), generateExtra)),
                "intent", plan.getIntent()
        ));
        String report = presenter.present(question, results, context);
        List<String> followUps = presenter.suggestFollowUps(question, results, context);
        // 阶段事件③′：报告生成完成，原地更新 generate 步骤（补输出：整合性短文案 + LLM 处理日志）
        // 输出摘要不再复述完整报告 —— 报告正文随 text 事件紧跟其后打出，复述会让用户读两遍同一结论
        String conclusionText = extractConclusion(results);
        Map<String, Object> generateDoneExtra = new LinkedHashMap<>();
        generateDoneExtra.put("input", upstreamResultsInput(results));
        generateDoneExtra.put("output", Map.of(
                "summary", TraceSnapshotBuilder.summarizeOutput(conclusionText, results.size()),
                "branch_taken", WorkflowGraphView.branchLabel("EXECUTE")));
        generateDoneExtra.put("trace", List.of(Map.of(
                "stage", "llm",
                "message", "大模型已按「结论先行 + 依据支撑」结构生成回答，依据来自上一步工具的实际产出")));
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("generate", "结果汇总",
                        TraceSnapshotBuilder.generateStepDesc(context), generateDoneExtra)),
                "intent", plan.getIntent()
        ));
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, report, plan, results, emitter);

        emitTextEvents(emitter, report);
        Map<String, Object> donePayload = new LinkedHashMap<>();
        donePayload.put("session_id", context.getSessionId());
        donePayload.put("intent", plan.getIntent());
        donePayload.put("conclusion", extractConclusion(results));
        donePayload.put("suggested_follow_ups", followUps);
        donePayload.put("elapsed_ms", System.currentTimeMillis() - startTime);
        // 查询审计（方案 §6-A3）：随 done 事件透出（前端 metadata 管道已具备）
        donePayload.put(com.sitech.prodai.service.agent.model.QueryAuditRecorder.AUDIT_KEY,
                queryAuditRecorder.record(queryAuditRecorder.build(
                        context.getUserScope(), context.getScene(), plan, results, null)));
        emitter.emit("done", donePayload);
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
        // 工作流定义：多意图分支（每个子计划独立走 理解→方案→执行→汇总 链路）
        emitter.emit("workflow", WorkflowGraphView.build(firstPlan, "MULTI"));

        for (int i = 0; i < plans.size(); i++) {
            QueryPlan plan = plans.get(i);
            String pre = i + "_";
            String intentLabel = TraceSnapshotBuilder.actionDisplay(plan);
            // 分组标记：前端据此在每个子任务前插入小节标题与间距，视觉分段
            String segment = "① ② ③ ④ ⑤".split(" ")[i] + " " + intentLabel;

            // 阶段事件①：该子任务的意图识别（带索引前缀 id，独立成链；首步骤携带 segment 供前端分组）
            // 输出 = 该子意图的结构化要素（作为该子链下游节点的输入来源）；trace = 理解层 LLM 处理留痕
            Map<String, Object> subIntentOutput = new LinkedHashMap<>();
            subIntentOutput.put("summary", "已明确：本次要执行「" + intentLabel + "」");
            subIntentOutput.put("structured_intent", TraceSnapshotBuilder.planIntentView(plan));
            Map<String, Object> subIntentExtra = new LinkedHashMap<>();
            subIntentExtra.put("segment", segment);
            subIntentExtra.put("goal", "先听懂您要做什么，再决定怎么办");
            subIntentExtra.put("input", Map.of("question", question));
            subIntentExtra.put("output", subIntentOutput);
            subIntentExtra.put("trace", TraceSnapshotBuilder.traceView(plan));
            emitter.emit("thinking", Map.of(
                    "steps", List.of(TraceSnapshotBuilder.thinkingStep(pre + "intent", TraceSnapshotBuilder.intentStepName(context),
                            TraceSnapshotBuilder.intentStepDesc(context), subIntentExtra)),
                    "intent", plan.getIntent()
            ));
            // 阶段事件②：该子任务的执行方案（输入 = ①子意图的结构化要素）
            Map<String, Object> subPlanExtra = new LinkedHashMap<>();
            subPlanExtra.put("segment", segment);
            subPlanExtra.put("goal", TraceSnapshotBuilder.planStepGoal(plan, context));
            subPlanExtra.put("input", TraceSnapshotBuilder.upstreamIntentInput(plan, question));
            subPlanExtra.put("workflow", TraceSnapshotBuilder.buildWorkflow(plan));
            subPlanExtra.put("output", Map.of("summary", TraceSnapshotBuilder.planStepOutput(plan, context)));
            subPlanExtra.put("trace", TraceSnapshotBuilder.traceView(plan));
            emitter.emit("thinking", Map.of(
                    "steps", List.of(TraceSnapshotBuilder.thinkingStep(pre + "plan", "方案规划",
                            TraceSnapshotBuilder.buildReadablePlan(plan), subPlanExtra)),
                    "intent", plan.getIntent()
            ));

            context.setLastIntent(plan.getIntent());
            context.setLastTools(plan.getTools());
            context.setLastParams(plan.getParams());

            // 执行层：该子计划工具逐步派发 tool 事件并缓存证据
            List<ExecutionResult> subResults = executeWithEvents(plan, context, emitter, segment);
            allResults.addAll(subResults);

            // 阶段事件③/③′：该子任务汇总（输入 = 该子链各工具的实际产出；输出 = 整合性短文案，不复述正文）
            emitter.emit("thinking", Map.of(
                    "steps", List.of(TraceSnapshotBuilder.thinkingStep(pre + "generate", "结果汇总",
                            TraceSnapshotBuilder.generateStepDesc(context),
                            Map.of("segment", segment,
                                    "goal", "把各环节结果整合成您能直接使用的结论与建议",
                                    "input", upstreamResultsInput(subResults)))),
                    "intent", plan.getIntent()
            ));
            String subReport = presenter.present(question, subResults, context);
            String subConclusion = extractConclusion(subResults);
            emitter.emit("thinking", Map.of(
                    "steps", List.of(TraceSnapshotBuilder.thinkingStep(pre + "generate", "结果汇总",
                            TraceSnapshotBuilder.generateStepDesc(context),
                            Map.of("segment", segment,
                                    "input", upstreamResultsInput(subResults),
                                    "output", Map.of("summary", TraceSnapshotBuilder.summarizeOutput(subConclusion, subResults.size()),
                                            "branch_taken", WorkflowGraphView.branchLabel("MULTI"))))),
                    "intent", plan.getIntent()
            ));

            subReports.add(subReport);
            allFollowUps.addAll(presenter.suggestFollowUps(question, subResults, context));
        }

        // 合并各子答案为最终正文（分别作答）
        String report = String.join("\n\n", subReports);
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, report, firstPlan, allResults, emitter);

        emitTextEvents(emitter, report);
        Map<String, Object> multiDonePayload = new LinkedHashMap<>();
        multiDonePayload.put("session_id", context.getSessionId());
        multiDonePayload.put("intent", firstPlan.getIntent());
        multiDonePayload.put("conclusion", extractConclusion(allResults));
        multiDonePayload.put("suggested_follow_ups", allFollowUps);
        multiDonePayload.put("elapsed_ms", System.currentTimeMillis() - startTime);
        // 查询审计（方案 §6-A3）：混合意图按子计划合并一次落审计
        multiDonePayload.put(com.sitech.prodai.service.agent.model.QueryAuditRecorder.AUDIT_KEY,
                queryAuditRecorder.record(queryAuditRecorder.build(
                        context.getUserScope(), context.getScene(), firstPlan, allResults, null)));
        emitter.emit("done", multiDonePayload);
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
        // 本体/规则推理日志：从工具产出中提取推理引擎、命中规则、归因路径等过程留痕；
        // 数据查询类工具（NL→SPARQL）下发实体发现/查询执行留痕，体现本体查询逻辑
        List<Map<String, Object>> toolTrace = switch (result.getToolName()) {
            case "sparql_query" -> TraceSnapshotBuilder.ontologyQueryTrace(result);
            default -> TraceSnapshotBuilder.ontologyTraceView(result);
        };
        if (toolTrace != null) {
            toolEvent.put("trace", toolTrace);
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
            String title = TraceSnapshotBuilder.str(m.get("title"));
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
            return new ThinkingCopy.ToolCopy(title, TraceSnapshotBuilder.str(m.get("goal")),
                    TraceSnapshotBuilder.str(m.get("manualHint")).isBlank() ? null : TraceSnapshotBuilder.str(m.get("manualHint")), category);
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
            if (value == null || TraceSnapshotBuilder.str(value).isBlank()) {
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

    /**
     * 汇总节点的「输入」视图：承接执行层各工具的实际产出摘要（from_step=tool_*）。
     */
    private Map<String, Object> upstreamResultsInput(List<ExecutionResult> results) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from_step", "execute");
        List<Map<String, Object>> upstream = new ArrayList<>();
        if (results != null) {
            for (ExecutionResult result : results) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("from_step", "tool_" + result.getToolName());
                if (result.isSuccess() && result.getData() != null) {
                    item.put("summary", ToolOutputRenderer.summary(toolMap.get(result.getToolName()), result.getData()));
                } else {
                    item.put("summary", "执行失败：" + TraceSnapshotBuilder.str(result.getErrorMessage()));
                }
                upstream.add(item);
            }
        }
        out.put("upstream_outputs", upstream);
        return out;
    }

    // ── 附件-only 分支（豆包式文件处理：先解析 → 总结 → 开放式追问） ──

    /** 前端占位话术识别：仅上传附件时前端可能补「导入文档：xxx」，视为空文本。 */
    private static final java.util.regex.Pattern PLACEHOLDER_ATTACHMENT_PREFIX =
            java.util.regex.Pattern.compile("^导入文档[:：]\\s*[^，。；]{1,120}$");

    /**
     * 判定"附件-only"：请求携带已上传文件（file_id/file_ids）且用户未输入实质文本。
     * 前端自动补的「导入文档：<文件名>」占位话术也按空文本处理（不作为业务话术参与意图识别）。
     */
    private boolean isAttachmentOnly(String question, Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        boolean hasFile = hasFileParam(params.get("file_id")) || hasFileParam(params.get("file_ids"));
        if (!hasFile) {
            return false;
        }
        String q = question == null ? "" : question.trim();
        return q.isEmpty() || PLACEHOLDER_ATTACHMENT_PREFIX.matcher(q).matches();
    }

    private boolean hasFileParam(Object value) {
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value).trim();
        return !s.isEmpty() && !"null".equalsIgnoreCase(s);
    }

    /**
     * 附件-only 同步链路：仅执行 rd_doc_parse（解析环节，不做抽取/合规/开单），
     * 解析产物由 LLM 生成「总结 + 开放式追问」。回复后用户下一轮自由回答时，
     * 解析全文已在 cachedEvidence/resolvedParams 中，手册链路可无缝承接。
     */
    private Map<String, Object> attachmentOnlyPath(String question, Map<String, Object> params,
                                                   SessionContext context, long startTime) {
        List<String> fileNames = fileNamesOf(params);
        log.info("[AgentOrchestrator] 附件-only: fileIds={} files={}",
                params.get("file_ids"), fileNames);

        List<ExecutionResult> results = executeDocParse(question, params, context);
        ExecutionResult parseResult = results.isEmpty() ? null : results.get(0);
        if (parseResult == null || !parseResult.isSuccess()) {
            String error = parseResult != null && parseResult.getErrorMessage() != null
                    ? parseResult.getErrorMessage() : "文档解析失败";
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("session_id", context.getSessionId());
            reply.put("report", "文件解析失败：" + error);
            reply.put("intent", "RD_FILE_PARSE");
            reply.put("conclusion", "");
            reply.put("suggested_follow_ups", List.of("请重新上传文件或换一份文件试试"));
            reply.put("elapsed_ms", System.currentTimeMillis() - startTime);
            return reply;
        }

        // 跨轮续接：解析全文写入 resolvedParams（随 query_plan.params 落库，重启/过期后仍可恢复）
        rememberDocumentText(context, parseResult.getData());
        String report = docSummaryFollowupGenerator.generate(parseResult.getData(), fileNames);
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, report, planOf(parseResult, question, params, context), results, null, new ArrayList<>(), null, null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", context.getSessionId());
        response.put("report", report);
        response.put("intent", "RD_FILE_PARSE");
        response.put("tools", List.of("rd_doc_parse"));
        response.put("conclusion", extractConclusion(results));
        response.put("suggested_follow_ups", List.of());
        response.put("attachment_only", true);
        response.put("elapsed_ms", System.currentTimeMillis() - startTime);
        return response;
    }

    /**
     * 附件-only 流式链路：thinking（识别/解析）→ tool（rd_doc_parse）→ text（总结+追问）→ done。
     * 事件契约与常规链路一致，前端零改动渲染。
     */
    private void attachmentOnlyStream(String question, Map<String, Object> params,
                                      SessionContext context, StreamEmitter emitter, long startTime) {
        List<String> fileNames = fileNamesOf(params);
        log.info("[AgentOrchestrator] 附件-only(流式): fileIds={} files={}",
                params.get("file_ids"), fileNames);

        // 阶段① 识别：不做话术判定，如实告知判定依据（附件 + 无文本）
        Map<String, Object> intentExtra = new LinkedHashMap<>();
        intentExtra.put("goal", "检测到您上传了文件，先看看里面有什么");
        intentExtra.put("input", Map.of("files", String.join("、", fileNames)));
        intentExtra.put("output", Map.of("summary", "上传了文件但未说明用途，先解析内容再与您确认下一步"));
        intentExtra.put("trace", List.of(Map.of(
                "stage", "sop",
                "message", "检测到附件「" + String.join("、", fileNames) + "」，未附带指令，先解析文件内容")));
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("intent", "需求识别",
                        "解析上传的文件内容，再确认您想做什么", intentExtra)),
                "intent", "RD_FILE_PARSE"
        ));

        // 阶段② 执行：仅解析环节（tool 事件复用通用渲染，前端展示解析卡片）
        emitter.emit("tool", Map.of("name", "rd_doc_parse", "status", "running"));
        List<ExecutionResult> results = executeDocParse(question, params, context);
        ExecutionResult parseResult = results.isEmpty() ? null : results.get(0);
        emitter.emit("tool", buildToolEvent(parseResult != null ? parseResult
                : ExecutionResult.fail("rd_doc_parse", "文档解析失败")));
        if (parseResult == null || !parseResult.isSuccess()) {
            String error = parseResult != null && parseResult.getErrorMessage() != null
                    ? parseResult.getErrorMessage() : "文档解析失败";
            emitter.emit("error", Map.of("errorMessage", "文件解析失败：" + error, "error", error));
            return;
        }

        // 跨轮续接：解析全文写入 resolvedParams（同同步链路）
        rememberDocumentText(context, parseResult.getData());

        // 阶段③ 生成：LLM 总结 + 开放式追问（打字机输出）
        String report = docSummaryFollowupGenerator.generate(parseResult.getData(), fileNames);
        Map<String, Object> generateExtra = new LinkedHashMap<>();
        generateExtra.put("goal", "总结文件内容，与您确认下一步");
        generateExtra.put("input", Map.of("from_step", "tool_rd_doc_parse"));
        generateExtra.put("trace", List.of(Map.of(
                "stage", "llm",
                "message", "大模型基于解析内容生成摘要，并询问您接下来想做什么")));
        emitter.emit("thinking", Map.of(
                "steps", List.of(TraceSnapshotBuilder.thinkingStep("generate", "内容摘要",
                        "基于解析结果总结文件内容", generateExtra)),
                "intent", "RD_FILE_PARSE"
        ));
        context.addHistoryEntry("assistant", report);
        sessionManager.save(context);
        persistTurn(context, question, report, planOf(parseResult, question, params, context), results, emitter);

        emitTextEvents(emitter, report);
        Map<String, Object> donePayload = new LinkedHashMap<>();
        donePayload.put("session_id", context.getSessionId());
        donePayload.put("intent", "RD_FILE_PARSE");
        donePayload.put("conclusion", extractConclusion(results));
        donePayload.put("suggested_follow_ups", List.of());
        donePayload.put("attachment_only", true);
        donePayload.put("elapsed_ms", System.currentTimeMillis() - startTime);
        emitter.emit("done", donePayload);
    }

    /** 构造仅含 rd_doc_parse 的单工具计划并执行（复用依赖编排与证据缓存惯例）。 */
    private List<ExecutionResult> executeDocParse(String question, Map<String, Object> params,
                                                  SessionContext context) {
        Map<String, Object> planParams = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        if (question != null && !question.isBlank()) {
            planParams.putIfAbsent("question", question);
        }
        injectSessionId(planParams, context);
        QueryPlan plan = new QueryPlan("RD_FILE_PARSE", List.of("rd_doc_parse"), planParams, question);
        plan.setUserQuestion(question);
        context.setLastIntent(plan.getIntent());
        context.setLastTools(plan.getTools());
        context.setLastParams(planParams);
        List<ExecutionResult> results = executor.execute(plan, context);
        for (ExecutionResult result : results) {
            if (result.isSuccess() && result.getData() != null) {
                context.cacheEvidence(result.getToolName(), result.getData());
                cacheBusinessEntity(context, result);
            }
        }
        return results;
    }

    /** 解析全文写入 resolvedParams + cachedEvidence 顶层：下一轮理解层/参数补全门可直接命中。 */
    private void rememberDocumentText(SessionContext context, Map<String, Object> parseData) {
        if (context == null || parseData == null) {
            return;
        }
        Object text = parseData.get("document_text");
        if (text instanceof String s && !s.isBlank()) {
            context.resolveParam("document_text", s);
            // 平铺到证据缓存顶层：ParamCompletionGate 按顶层参数名查 cachedEvidence（非工具名复合键），
            // 不平铺则下一轮必填参数校验查不到解析全文，误转澄清（上下文断链）
            context.cacheEvidence("document_text", s);
        } else {
            log.warn("[AgentOrchestrator] 附件-only 解析产物缺少 document_text，跨轮上下文可能断链: keys={}",
                    parseData.keySet());
        }
    }

    /** 附件-only 场景的最小查询计划视图（供 persistTurn 落库与前端展示）。 */
    private QueryPlan planOf(ExecutionResult parseResult, String question, Map<String, Object> params,
                             SessionContext context) {
        Map<String, Object> planParams = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        if (question != null && !question.isBlank()) {
            planParams.putIfAbsent("question", question);
        }
        // 落库投影必须含 document_text：SessionManager 恢复时从 query_plan.params 回放 resolvedParams，
        // 缺失则重启/TTL 过期后下一轮拿不到解析全文（上下文断链根因）
        if (context != null && planParams.get("document_text") == null) {
            Object remembered = context.getResolvedParams().get("document_text");
            if (remembered != null) {
                planParams.put("document_text", remembered);
            }
        }
        QueryPlan plan = new QueryPlan("RD_FILE_PARSE", List.of("rd_doc_parse"), planParams, question);
        plan.setUserQuestion(question);
        return plan;
    }

    /** 从请求参数提取文件名列表（file_name 优先，缺省用「文档」称呼）。 */
    private List<String> fileNamesOf(Map<String, Object> params) {
        List<String> names = new ArrayList<>();
        if (params != null) {
            Object fileName = params.get("file_name");
            if (hasFileParam(fileName)) {
                for (String raw : String.valueOf(fileName).split("[,，;；]")) {
                    if (!raw.isBlank()) {
                        names.add(raw.trim());
                    }
                }
            }
        }
        if (names.isEmpty()) {
            names.add("文档");
        }
        return names;
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
