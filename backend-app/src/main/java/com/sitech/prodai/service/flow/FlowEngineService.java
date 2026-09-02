package com.sitech.prodai.service.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.domain.entity.Workflow;
import com.sitech.prodai.domain.entity.WorkflowExecution;
import com.sitech.prodai.domain.entity.WorkflowNodeLog;
import com.sitech.prodai.mapper.WorkflowExecutionMapper;
import com.sitech.prodai.mapper.WorkflowMapper;
import com.sitech.prodai.mapper.WorkflowNodeLogMapper;
import com.sitech.prodai.service.ToolExecutionService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 固定流程引擎 —— 持久化状态机（P2-2）。
 * <p>
 * 设计依据：《固定流程引擎设计文档》§4/§7。
 * <p>
 * 三条铁律的落地：
 * <ul>
 *   <li>全持久化：每节点完成即写 node_log + 合并 context_data，kill -9 可续跑（resume）</li>
 *   <li>LLM 只进节点不进引擎：调度/条件求值/参数解析全为确定性代码（P2 只支持 start/tool/end，
 *       llm/condition/human/http 节点在 P2-3~5 逐个补齐）</li>
 *   <li>定义期守门前置：启动执行前先跑 {@link FlowDefinitionValidator}，非法定义拒绝启动</li>
 * </ul>
 * <p>
 * P2-2 范围：start → tool → end 的线性执行 + 节点留痕 + 失败语义（fail 中止 / continue 跳过下游）。
 * condition/human/llm/http 节点与重试在后续步骤补齐（设计文档 §8 P2-3~P2-5）。
 */
@Service
public class FlowEngineService {

    private static final Logger log = LoggerFactory.getLogger(FlowEngineService.class);
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final WorkflowMapper workflowMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowNodeLogMapper nodeLogMapper;
    private final ToolExecutionService toolExecutionService;
    private final FlowDefinitionValidator validator;
    private final ConditionEvaluator conditionEvaluator;
    private final LlmGateway llmService;
    private final HttpGateway restClient;

    public FlowEngineService(WorkflowMapper workflowMapper,
                             WorkflowExecutionMapper executionMapper,
                             WorkflowNodeLogMapper nodeLogMapper,
                             ToolExecutionService toolExecutionService,
                             FlowDefinitionValidator validator,
                             ConditionEvaluator conditionEvaluator,
                             LlmGateway llmService,
                             HttpGateway restClient) {
        this.workflowMapper = workflowMapper;
        this.executionMapper = executionMapper;
        this.nodeLogMapper = nodeLogMapper;
        this.toolExecutionService = toolExecutionService;
        this.validator = validator;
        this.conditionEvaluator = conditionEvaluator;
        this.llmService = llmService;
        this.restClient = restClient;
    }

    /** LLM 网关抽象：隔离 LlmService 具体实现，便于单测替换。 */
    public interface LlmGateway {
        /** 执行一次 LLM 补全，返回文本结果；失败返回 null。 */
        Object chat(Map<String, Object> params);
    }

    /** HTTP 网关抽象：隔离 RestClient 具体实现，便于单测替换。 */
    public interface HttpGateway {
        org.springframework.http.ResponseEntity<String> execute(String url, String method, Map<String, Object> body);
    }

    /**
     * 启动执行：定义期守门 → 锁定版本 → 建实例 → 同步推进状态机。
     * P2 同步执行即可（节点为秒级真实工具调用）；异步化在 P2-4 引入 human 挂起时自然发生。
     */
    public ApiResponse<Map<String, Object>> startExecution(String workflowCode, Integer version, Map<String, Object> inputData, String user) {
        List<Workflow> found = workflowMapper.selectList(
                new LambdaQueryWrapper<Workflow>().eq(Workflow::getWorkflowCode, workflowCode));
        if (found.isEmpty()) {
            return ApiResponse.fail("工作流不存在: " + workflowCode);
        }
        Workflow workflow = found.get(0);
        if (version == null && workflow.getIsActive() == null) {
            // 未发布的工作流不允许启动执行（发布即绿灯的另一半：未发布不可跑）
            return ApiResponse.fail("工作流未发布，不允许执行: " + workflowCode);
        }
        int lockedVersion = version != null ? version : (workflow.getVersion() != null ? workflow.getVersion() : 1);

        Map<String, Object> definition = workflow.getWorkflowData();
        if (definition == null) {
            return ApiResponse.fail("工作流定义缺失: " + workflowCode);
        }
        // 编辑器原始形态（VueFlow nodes/edges）→ 引擎形态（action/action_params/connections），
        // 归一化后再守门（P3-1a2：编辑器执行入口切换后端引擎）
        if (EditorDefinitionNormalizer.needsNormalize(definition)) {
            definition = EditorDefinitionNormalizer.normalize(definition);
        }
        FlowDefinitionValidator.ValidationResult check = validator.validate(definition);
        if (!check.valid()) {
            return ApiResponse.fail("流程定义校验未通过，拒绝执行", check.problems());
        }

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflow.getId());
        execution.setWorkflowCode(workflowCode);
        execution.setExecutionId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        execution.setStatus("running");
        execution.setInputData(inputData != null ? inputData : new HashMap<>());
        execution.setStartTime(LocalDateTime.now());
        execution.setTriggeredBy(user);
        execution.setTriggerType("manual");
        execution.setExecutionLogs(new ArrayList<>());
        execution.setStatusVersion(0);
        execution.setWorkflowVersion(lockedVersion);
        execution.setContextData(new LinkedHashMap<>());
        executionMapper.insert(execution);

        runStateMachine(execution, definition);
        return ApiResponse.ok(executionToMap(execution));
    }

    /** 从最近节点续跑（失败恢复 / kill -9 后重启续跑），复用锁定的定义版本。 */
    public ApiResponse<Map<String, Object>> resumeExecution(String executionId, String user) {
        WorkflowExecution execution = findByExecutionId(executionId);
        if (execution == null) {
            return ApiResponse.fail("执行实例不存在: " + executionId);
        }
        if (!"failed".equals(execution.getStatus())) {
            return ApiResponse.fail("仅 failed 状态可续跑，当前: " + execution.getStatus());
        }
        Workflow workflow = workflowMapper.selectById(execution.getWorkflowId());
        if (workflow == null) {
            return ApiResponse.fail("关联工作流不存在");
        }
        execution.setStatus("running");
        execution.setErrorMessage(null);
        execution.setTriggeredBy(user);
        executionMapper.updateById(execution);

        Map<String, Object> definition = workflow.getWorkflowData();
        if (EditorDefinitionNormalizer.needsNormalize(definition)) {
            definition = EditorDefinitionNormalizer.normalize(definition);
        }
        runStateMachine(execution, definition);
        return ApiResponse.ok(executionToMap(execution));
    }

    /**
     * 人工节点恢复：校验一次有效的 resume_token → 表单数据写入该节点输出 → 状态机继续推进。
     * 并发防护：恢复以 status_version 乐观锁比对（双提交时后到者失败）。
     */
    public ApiResponse<Map<String, Object>> resumeFromHuman(String executionId, String resumeToken,
                                                            Map<String, Object> formData, String user) {
        WorkflowExecution execution = findByExecutionId(executionId);
        if (execution == null) {
            return ApiResponse.fail("执行实例不存在: " + executionId);
        }
        if (!"waiting_human".equals(execution.getStatus())) {
            return ApiResponse.fail("执行未处于人工挂起状态，当前: " + execution.getStatus());
        }
        if (execution.getResumeToken() == null || !execution.getResumeToken().equals(resumeToken)) {
            return ApiResponse.fail("恢复令牌无效（令牌一次有效，请勿重复提交）");
        }

        String humanNodeId = execution.getCurrentNodeId();
        Workflow workflow = workflowMapper.selectById(execution.getWorkflowId());
        if (workflow == null || humanNodeId == null) {
            return ApiResponse.fail("执行实例数据异常（缺关联流程或当前节点）");
        }
        Map<String, Object> definition = workflow.getWorkflowData();
        if (EditorDefinitionNormalizer.needsNormalize(definition)) {
            definition = EditorDefinitionNormalizer.normalize(definition);
        }
        Map<String, Map<String, Object>> nodeById = indexNodes(definition);
        Map<String, Object> humanNode = nodeById.get(humanNodeId);
        if (humanNode == null || !"flow.human".equals(str(humanNode.get("action")))) {
            return ApiResponse.fail("当前节点不是人工节点: " + humanNodeId);
        }

        // 人工节点输出 = 表单数据（按 nodeId.output 命名空间入上下文）
        Map<String, Object> context = execution.getContextData() != null
                ? execution.getContextData() : new LinkedHashMap<>();
        Map<String, Object> nodeScope = new LinkedHashMap<>();
        nodeScope.put("output", formData != null ? formData : Map.of());
        context.put(humanNodeId, nodeScope);

        // 关闭节点日志（挂起时开的 running 记录置 completed）
        closeHumanNodeLog(execution.getExecutionId(), humanNodeId);

        // 人工节点出边缺失 = 定义错误：直接失败（而非静默回到起点重跑全流程）
        String nextNodeId = nextNode(humanNode, definition);
        if (nextNodeId == null) {
            failExecution(execution, "人工节点 " + humanNodeId + " 未配置出边（定义错误），无法恢复推进");
            return ApiResponse.fail("人工节点 " + humanNodeId + " 未配置出边（定义错误），无法恢复推进");
        }

        execution.setStatus("running");
        execution.setResumeToken(null); // 令牌一次有效
        execution.setContextData(context);
        execution.setCurrentNodeId(nextNodeId);
        execution.setStatusVersion(bumpVersion(execution));
        execution.setTriggeredBy(user);
        executionMapper.updateById(execution);

        runStateMachine(execution, definition);
        return ApiResponse.ok(executionToMap(execution));
    }

    /** 将 human 节点最近的 running 日志置为 completed（恢复即闭环）。 */
    private void closeHumanNodeLog(String executionId, String nodeId) {
        WorkflowNodeLog latest = nodeLogMapper.selectList(new LambdaQueryWrapper<WorkflowNodeLog>()
                        .eq(WorkflowNodeLog::getExecutionId, executionId)
                        .eq(WorkflowNodeLog::getNodeId, nodeId)
                        .eq(WorkflowNodeLog::getStatus, "running")
                        .orderByDesc(WorkflowNodeLog::getId))
                .stream().findFirst().orElse(null);
        if (latest != null) {
            latest.setStatus("completed");
            latest.setEndedAt(LocalDateTime.now());
            latest.setDurationMs(Duration.between(latest.getStartedAt(), LocalDateTime.now()).toMillis());
            nodeLogMapper.updateById(latest);
        }
    }

    /** 状态机推进：从 current_node_id 起（首次为 start 节点）沿边执行，直到 end/挂起/失败。 */
    private void runStateMachine(WorkflowExecution execution, Map<String, Object> definition) {
        Map<String, Object> context = execution.getContextData() != null
                ? execution.getContextData() : new LinkedHashMap<>();
        Map<String, Object> variables = new LinkedHashMap<>(execution.getInputData() != null
                ? execution.getInputData() : Map.of());
        variables.putAll(context);

        Map<String, Map<String, Object>> nodeById = indexNodes(definition);
        String current = execution.getCurrentNodeId() != null ? execution.getCurrentNodeId() : findStartNode(nodeById);
        if (current == null) {
            failExecution(execution, "未找到 flow.start 节点");
            return;
        }

        while (current != null) {
            Map<String, Object> node = nodeById.get(current);
            if (node == null) {
                failExecution(execution, "节点不存在: " + current);
                return;
            }
            String action = str(node.get("action"));

            if ("flow.end".equals(action)) {
                completeExecution(execution, variables);
                return;
            }

            // condition 节点：求值分支 → 直接路由（无输出，分支依据落 node_log.branch_taken）
            if ("flow.condition".equals(action)) {
                String branchId = routeCondition(node, nodeById, definition, execution, variables);
                persistBranchLog(execution, node, branchId);
                execution.setCurrentNodeId(nextNodeByHandle(current, branchId, definition));
                execution.setStatusVersion(bumpVersion(execution));
                executionMapper.updateById(execution);
                current = execution.getCurrentNodeId();
                continue;
            }

            // human 节点：挂起等人工（恢复经 resumeExecution 携带表单数据续推）
            if ("flow.human".equals(action)) {
                suspendAtHuman(execution, node, variables, definition);
                return;
            }

            NodeOutcome outcome = executeNode(execution, node, variables);
            persistNodeLog(execution, node, outcome);

            if (!outcome.success() && !"continue".equals(outcome.failureMode())) {
                failExecution(execution, "节点 " + current + " 失败: " + outcome.errorMessage());
                return;
            }

            // 节点输出合并进上下文并落库（全持久化铁律）
            // 变量语义：{{<nodeId>.output.<field>}} —— 输出按 nodeId 命名空间存放
            Map<String, Object> nodeScope = new LinkedHashMap<>();
            nodeScope.put("output", outcome.output());
            context.put(current, nodeScope);
            variables.put(current, nodeScope);
            variables.putAll(outcome.output());
            execution.setContextData(new LinkedHashMap<>(context));
            execution.setCurrentNodeId(nextNode(node, definition));
            execution.setStatusVersion(bumpVersion(execution));
            executionMapper.updateById(execution);
            current = execution.getCurrentNodeId();
        }
        completeExecution(execution, variables);
    }

    /** condition 分支路由：按声明顺序求值，首个命中即返回；default 兜底必中（定义期已强制）。 */
    private String routeCondition(Map<String, Object> node, Map<String, Map<String, Object>> nodeById,
                                  Map<String, Object> definition, WorkflowExecution execution,
                                  Map<String, Object> variables) {
        Map<String, Object> params = node.get("action_params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        if (!(params.get("branches") instanceof List<?> branches)) {
            return null;
        }
        ConditionEvaluator.EvalContext evalCtx = buildEvalContext(nodeById.keySet(), variables, execution);
        for (Object raw : branches) {
            if (!(raw instanceof Map<?, ?> branch)) {
                continue;
            }
            String branchId = str(branch.get("id"));
            String expression = str(branch.get("expression"));
            if ("default".equals(expression)) {
                return branchId;
            }
            if (conditionEvaluator.evaluate(expression, evalCtx)) {
                return branchId;
            }
        }
        return null;
    }

    private ConditionEvaluator.EvalContext buildEvalContext(Set<String> nodeIds, Map<String, Object> variables,
                                                            WorkflowExecution execution) {
        Map<String, Object> nodes = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            if (variables.get(nodeId) instanceof Map<?, ?> scope) {
                nodes.put(nodeId, scope);
            }
        }
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("timestamp", LocalDateTime.now().toString());
        system.put("execution_id", execution.getExecutionId());
        return new ConditionEvaluator.EvalContext(nodes, variables, system);
    }

    /** 分支命中依据落 node_log（审计："为什么走这条边"有据可查）。 */
    private void persistBranchLog(WorkflowExecution execution, Map<String, Object> node, String branchId) {
        WorkflowNodeLog nodeLog = new WorkflowNodeLog();
        nodeLog.setExecutionId(execution.getExecutionId());
        nodeLog.setNodeId(str(node.get("id")));
        nodeLog.setNodeType("flow.condition");
        nodeLog.setStatus(branchId != null ? "completed" : "failed");
        nodeLog.setAttempt(1);
        nodeLog.setBranchTaken(branchId);
        nodeLog.setStartedAt(LocalDateTime.now());
        nodeLog.setEndedAt(LocalDateTime.now());
        nodeLog.setDurationMs(0L);
        if (branchId == null) {
            nodeLog.setErrorMessage("condition 无命中分支（default 兜底缺失或路由配置错误）");
        }
        nodeLogMapper.insert(nodeLog);
    }

    /** human 节点挂起：生成 resume_token（一次有效），节点状态落库，等人工恢复。 */
    private void suspendAtHuman(WorkflowExecution execution, Map<String, Object> node,
                                Map<String, Object> variables, Map<String, Object> definition) {
        String nodeId = str(node.get("id"));
        Map<String, Object> params = node.get("action_params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();

        WorkflowNodeLog nodeLog = new WorkflowNodeLog();
        nodeLog.setExecutionId(execution.getExecutionId());
        nodeLog.setNodeId(nodeId);
        nodeLog.setNodeType("flow.human");
        nodeLog.setStatus("running");
        nodeLog.setAttempt(1);
        nodeLog.setStartedAt(LocalDateTime.now());
        nodeLog.setBranchTaken("waiting_human");
        nodeLogMapper.insert(nodeLog);

        execution.setStatus("waiting_human");
        execution.setCurrentNodeId(nodeId);
        execution.setResumeToken(UUID.randomUUID().toString().replace("-", ""));
        execution.setContextData(new LinkedHashMap<>(variables));
        execution.setStatusVersion(bumpVersion(execution));
        executionMapper.updateById(execution);
        log.info("[FlowEngine] 执行挂起于人工节点: {} node={} token={}",
                execution.getExecutionId(), nodeId, execution.getResumeToken());
    }

    private int bumpVersion(WorkflowExecution execution) {
        return execution.getStatusVersion() == null ? 1 : execution.getStatusVersion() + 1;
    }

    /** 单节点执行：按类型分派。condition/human 由状态机直接处理；此处覆盖 start/tool/llm/http。
     *  执行语义（P5）：timeoutMs 超时中断、retry.maxAttempts 失败重试、onFailure=continue 失败跳过下游。 */
    private NodeOutcome executeNode(WorkflowExecution execution, Map<String, Object> node, Map<String, Object> variables) {
        String nodeId = str(node.get("id"));
        String action = str(node.get("action"));
        Map<String, Object> params = node.get("action_params") instanceof Map<?, ?> p
                ? new LinkedHashMap<>((Map<String, Object>) p) : Map.of();

        long timeoutMs = params.get("timeoutMs") instanceof Number n ? n.longValue() : DEFAULT_TIMEOUT_MS;
        int maxAttempts = 1;
        if (params.get("retry") instanceof Map<?, ?> retry && retry.get("maxAttempts") instanceof Number n) {
            maxAttempts = Math.max(1, n.intValue() + 1); // maxAttempts = 重试次数（首次之外再试 N 次）
        }
        String failureMode = str(params.getOrDefault("onFailure", "fail"));

        long startMs = System.currentTimeMillis();
        NodeOutcome outcome = runWithTimeoutAndRetry(nodeId, action, params, variables, timeoutMs, maxAttempts);
        if (!outcome.success()) {
            // onFailure=continue：失败不中止流程（错误信息保留在节点日志中供审计）
            outcome = new NodeOutcome(false, outcome.nodeType(), failureMode, outcome.errorMessage(), outcome.output(), outcome.durationMs());
        }
        long totalMs = System.currentTimeMillis() - startMs;
        log.info("[FlowEngine] 节点 {} ({}) 完成，耗时 {}ms attempts<= {} failureMode={}",
                nodeId, action, totalMs, maxAttempts, failureMode);
        return withDuration(outcome, totalMs);
    }

    /** 超时 + 重试包装：单次尝试在独立线程执行（超时 interrupt），失败按 maxAttempts 重试。 */
    private NodeOutcome runWithTimeoutAndRetry(String nodeId, String action, Map<String, Object> params,
                                               Map<String, Object> variables, long timeoutMs, int maxAttempts) {
        NodeOutcome outcome = NodeOutcome.fail(action, "fail", "节点未执行");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            outcome = runAttemptOnce(action, params, variables, timeoutMs);
            if (outcome.success()) {
                return outcome;
            }
            log.warn("[FlowEngine] 节点 {} 第 {}/{} 次尝试失败: {}", nodeId, attempt, maxAttempts, outcome.errorMessage());
        }
        return outcome;
    }

    /** 单次尝试：独立单线程执行（超时 interrupt + shutdownNow，不留泄漏线程）。 */
    private NodeOutcome runAttemptOnce(String action, Map<String, Object> params,
                                       Map<String, Object> variables, long timeoutMs) {
        java.util.concurrent.ExecutorService single = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "flow-node-exec");
            t.setDaemon(true);
            return t;
        });
        try {
            java.util.concurrent.Future<NodeOutcome> future = single.submit(
                    () -> dispatchNodeAction(action, params, variables));
            try {
                return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                return NodeOutcome.fail(action, "fail", "节点执行超时 (> " + timeoutMs + "ms)");
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.error("[FlowEngine] 节点执行异常", cause);
                return NodeOutcome.fail(action, "fail", "节点执行异常: " + cause.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return NodeOutcome.fail(action, "fail", "节点执行被中断");
            }
        } finally {
            single.shutdownNow();
        }
    }

    /** 节点动作分派（真实执行逻辑，供超时线程调用）。 */
    private NodeOutcome dispatchNodeAction(String action, Map<String, Object> params, Map<String, Object> variables) {
        return switch (action == null ? "" : action) {
            case "flow.start" -> NodeOutcome.success(Map.of());
            case "flow.tool" -> executeToolNode(str(params.get("__nodeId")), params, variables);
            case "flow.llm" -> executeLlmNode(params, variables);
            case "flow.http" -> executeHttpNode(params, variables);
            default -> NodeOutcome.fail(action, "fail", "未注册的节点类型: " + action);
        };
    }

    private NodeOutcome withDuration(NodeOutcome outcome, long durationMs) {
        return new NodeOutcome(outcome.success(), outcome.nodeType(), outcome.failureMode(),
                outcome.errorMessage(), outcome.output(), durationMs);
    }

    /** llm 节点：prompt 模板变量注入后调用 LlmService（LLM 只进节点，不进引擎调度——铁律二）。 */
    private NodeOutcome executeLlmNode(Map<String, Object> params, Map<String, Object> variables) {
        Object prompt = params.get("prompt");
        if (prompt == null || String.valueOf(prompt).isBlank()) {
            return NodeOutcome.fail("flow.llm", "fail", "llm 节点未配置 prompt");
        }
        String resolvedPrompt = renderTemplate(String.valueOf(prompt), variables);
        Map<String, Object> llmParams = new LinkedHashMap<>();
        llmParams.put("prompt", resolvedPrompt);
        if (params.get("model") != null) {
            llmParams.put("model", params.get("model"));
        }
        if (params.get("temperature") instanceof Number t) {
            llmParams.put("temperature", t.doubleValue());
        }
        Object resp = llmService.chat(llmParams);
        if (resp == null) {
            return NodeOutcome.fail("flow.llm", "fail", "LLM 调用失败（返回空）");
        }
        return NodeOutcome.success(Map.of("response", resp));
    }

    /** http 节点：POST/GET 外部系统（超时/重试语义在 P2-5b 的重试包装器中统一处理）。 */
    private NodeOutcome executeHttpNode(Map<String, Object> params, Map<String, Object> variables) {
        String url = str(params.get("url"));
        if (url == null || url.isBlank()) {
            return NodeOutcome.fail("flow.http", "fail", "http 节点未配置 url");
        }
        String method = str(params.getOrDefault("method", "POST"));
        Map<String, Object> body = resolveInputParams(params.get("inputParams"), variables);
        try {
            org.springframework.http.ResponseEntity<String> resp = restClient.execute(url, method, body);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return NodeOutcome.fail("flow.http", "fail",
                        "HTTP " + resp.getStatusCode().value() + ": " + truncate(resp.getBody()));
            }
            Object parsed = parseJsonSafely(resp.getBody());
            return NodeOutcome.success(parsed instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m)
                    : Map.of("response", String.valueOf(resp.getBody())));
        } catch (Exception e) {
            return NodeOutcome.fail("flow.http", "fail", "HTTP 调用失败: " + e.getMessage());
        }
    }

    /** tool 节点：inputParams 变量解析后直调真实 AgentTool（复用 ToolExecutionService 注册表）。 */
    private NodeOutcome executeToolNode(String nodeId, Map<String, Object> params, Map<String, Object> variables) {
        String toolName = str(params.get("toolName"));
        if (toolName == null || toolName.isBlank()) {
            return NodeOutcome.fail("flow.tool", "fail", "tool 节点未配置 toolName");
        }
        Map<String, Object> toolParams = resolveInputParams(params.get("inputParams"), variables);
        ExecutionResult result = toolExecutionService.execute(toolName, toolParams);
        if (result == null) {
            return NodeOutcome.fail("flow.tool", "fail", "工具不存在: " + toolName);
        }
        if (!result.isSuccess()) {
            return NodeOutcome.fail("flow.tool", "fail", result.getErrorMessage());
        }
        Map<String, Object> output = projectOutput(params.get("outputParams"), result.getData());
        return NodeOutcome.success(output);
    }

    /** inputParams: [{name, value}]，value 支持 {{nodeId.output.field}} / {{flow.x}} 变量引用。 */
    private Map<String, Object> resolveInputParams(Object inputParams, Map<String, Object> variables) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (!(inputParams instanceof List<?> list)) {
            return resolved;
        }
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> item)) {
                continue;
            }
            String name = str(item.get("name"));
            Object value = item.get("value");
            if (name != null) {
                resolved.put(name, resolveValue(value, variables));
            }
        }
        return resolved;
    }

    /** outputParams: [{name, source}]，从工具输出中提取声明字段。 */
    private Map<String, Object> projectOutput(Object outputParams, Map<String, Object> toolData) {
        Map<String, Object> projected = new LinkedHashMap<>();
        if (!(outputParams instanceof List<?> list)) {
            return toolData != null ? new LinkedHashMap<>(toolData) : Map.of();
        }
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> item)) {
                continue;
            }
            String name = str(item.get("name"));
            String source = str(item.get("source"));
            if (name == null) {
                continue;
            }
            if (source == null || "response".equals(source) || toolData == null) {
                projected.put(name, toolData);
                continue;
            }
            Object value = toolData;
            for (String part : source.split("\\.")) {
                if (value instanceof Map<?, ?> map) {
                    value = map.get(part);
                } else {
                    value = null;
                    break;
                }
            }
            projected.put(name, value);
        }
        return projected;
    }

    /** 变量解析：{{ref}} → 变量表取值；字面值原样返回。 */
    private Object resolveValue(Object value, Map<String, Object> variables) {
        if (!(value instanceof String s)) {
            return value;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\{\\{([^}]+)}}$").matcher(s.trim());
        if (!m.matches()) {
            return value;
        }
        String ref = m.group(1).trim();
        Object current = variables;
        for (String part : ref.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private void persistNodeLog(WorkflowExecution execution, Map<String, Object> node, NodeOutcome outcome) {
        WorkflowNodeLog nodeLog = new WorkflowNodeLog();
        nodeLog.setExecutionId(execution.getExecutionId());
        nodeLog.setNodeId(str(node.get("id")));
        nodeLog.setNodeType(str(node.get("action")));
        nodeLog.setStatus(outcome.success() ? "completed" : "failed");
        nodeLog.setAttempt(1);
        nodeLog.setOutputData(outcome.output());
        nodeLog.setErrorMessage(outcome.errorMessage());
        nodeLog.setStartedAt(LocalDateTime.now().minus(Duration.ofMillis(outcome.durationMs())));
        nodeLog.setEndedAt(LocalDateTime.now());
        nodeLog.setDurationMs(outcome.durationMs());
        nodeLogMapper.insert(nodeLog);
    }

    private String nextNode(Map<String, Object> node, Map<String, Object> definition) {
        return nextNodeByHandle(str(node.get("id")), null, definition);
    }

    /** 按边路由：condition 分支经 sourceHandle=branchId 匹配；普通节点取默认边。 */
    private String nextNodeByHandle(String nodeId, String branchHandle, Map<String, Object> definition) {
        if (!(definition.get("connections") instanceof List<?> connections)) {
            return null;
        }
        String fallback = null;
        for (Object raw : connections) {
            if (!(raw instanceof Map<?, ?> conn) || !nodeId.equals(str(conn.get("source")))) {
                continue;
            }
            String handle = str(conn.get("sourceHandle"));
            if (branchHandle != null) {
                if (branchHandle.equals(handle)) {
                    return str(conn.get("target"));
                }
            } else if (handle == null || handle.isBlank() || "output".equals(handle) || "source".equals(handle)) {
                return str(conn.get("target"));
            } else if (fallback == null) {
                fallback = str(conn.get("target"));
            }
        }
        return fallback;
    }

    /** prompt 模板渲染：{{ref}} 全量替换（与 inputParams 同一变量语义）。 */
    private String renderTemplate(String template, Map<String, Object> variables) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\{\\{([^}]+)}}").matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object value = resolveValue("{{" + m.group(1).trim() + "}}", variables);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Object parseJsonSafely(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }

    private String findStartNode(Map<String, Map<String, Object>> nodeById) {
        return nodeById.entrySet().stream()
                .filter(e -> "flow.start".equals(str(e.getValue().get("action"))))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    private Map<String, Map<String, Object>> indexNodes(Map<String, Object> definition) {
        Map<String, Map<String, Object>> nodeById = new LinkedHashMap<>();
        if (definition.get("nodes") instanceof List<?> nodes) {
            for (Object raw : nodes) {
                if (raw instanceof Map<?, ?> node && str(node.get("id")) != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) node;
                    nodeById.put(str(node.get("id")), typed);
                }
            }
        }
        return nodeById;
    }

    private void completeExecution(WorkflowExecution execution, Map<String, Object> variables) {
        execution.setStatus("completed");
        execution.setEndTime(LocalDateTime.now());
        execution.setOutputData(new LinkedHashMap<>(variables));
        execution.setCurrentNodeId(null);
        executionMapper.updateById(execution);
        log.info("[FlowEngine] 执行完成: {}", execution.getExecutionId());
    }

    private void failExecution(WorkflowExecution execution, String message) {
        execution.setStatus("failed");
        execution.setErrorMessage(message);
        execution.setEndTime(LocalDateTime.now());
        executionMapper.updateById(execution);
        log.warn("[FlowEngine] 执行失败: {} - {}", execution.getExecutionId(), message);
    }

    private WorkflowExecution findByExecutionId(String executionId) {
        return executionMapper.selectOne(new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId));
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 执行实例 → JSON Map（snake_case 契约）。 */
    public Map<String, Object> executionToMap(WorkflowExecution execution) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", execution.getId());
        map.put("workflow_code", execution.getWorkflowCode());
        map.put("execution_id", execution.getExecutionId());
        map.put("status", execution.getStatus());
        map.put("current_node_id", execution.getCurrentNodeId());
        map.put("resume_token", execution.getResumeToken());
        map.put("workflow_version", execution.getWorkflowVersion());
        map.put("input_data", execution.getInputData());
        map.put("output_data", execution.getOutputData());
        map.put("context_data", execution.getContextData());
        map.put("error_message", execution.getErrorMessage());
        map.put("start_time", execution.getStartTime());
        map.put("end_time", execution.getEndTime());
        return map;
    }

    /** 执行详情：状态 + 上下文（编辑器执行面板轮询数据源）。 */
    public ApiResponse<Map<String, Object>> getExecution(String executionId) {
        WorkflowExecution execution = findByExecutionId(executionId);
        if (execution == null) {
            return ApiResponse.fail("执行实例不存在: " + executionId);
        }
        return ApiResponse.ok(executionToMap(execution));
    }

    /** 可取消状态：终态（completed/failed/cancelled）不可取消。 */
    private static final Set<String> CANCELLABLE_STATUSES = Set.of("running", "waiting_human", "pending");

    /**
     * 取消执行（P4-1）：仅运行中/人工挂起/待执行状态可取消；终态拒绝。
     * 引擎为同步推进（无后台线程），取消语义 = 将非终态实例置为 cancelled 终态，
     * 清空 resume_token 防止取消后仍可人工恢复。
     */
    public ApiResponse<Map<String, Object>> cancelExecution(String executionId, String reason, String user) {
        WorkflowExecution execution = findByExecutionId(executionId);
        if (execution == null) {
            return ApiResponse.fail("执行实例不存在: " + executionId);
        }
        if (!CANCELLABLE_STATUSES.contains(execution.getStatus())) {
            return ApiResponse.fail("终态执行不可取消，当前: " + execution.getStatus());
        }

        if (execution.getCurrentNodeId() != null) {
            closeRunningNodeLogAsCancelled(execution.getExecutionId(), execution.getCurrentNodeId());
        }
        execution.setStatus("cancelled");
        execution.setResumeToken(null);
        execution.setNotes(reason != null ? reason : "人工取消");
        execution.setEndTime(LocalDateTime.now());
        if (execution.getStartTime() != null) {
            execution.setDurationSeconds((int) Duration.between(execution.getStartTime(), LocalDateTime.now()).getSeconds());
        }
        execution.setStatusVersion(bumpVersion(execution));
        execution.setTriggeredBy(user);
        executionMapper.updateById(execution);
        log.info("[FlowEngine] 执行已取消: {} node={} reason={}", executionId, execution.getCurrentNodeId(), reason);
        return ApiResponse.ok(executionToMap(execution));
    }

    /** 将取消节点最近的 running 日志置为 cancelled（挂起/运行中的节点留痕闭环）。 */
    private void closeRunningNodeLogAsCancelled(String executionId, String nodeId) {
        WorkflowNodeLog latest = nodeLogMapper.selectList(new LambdaQueryWrapper<WorkflowNodeLog>()
                        .eq(WorkflowNodeLog::getExecutionId, executionId)
                        .eq(WorkflowNodeLog::getNodeId, nodeId)
                        .eq(WorkflowNodeLog::getStatus, "running")
                        .orderByDesc(WorkflowNodeLog::getId))
                .stream().findFirst().orElse(null);
        if (latest != null) {
            latest.setStatus("cancelled");
            latest.setEndedAt(LocalDateTime.now());
            latest.setDurationMs(Duration.between(latest.getStartedAt(), LocalDateTime.now()).toMillis());
            nodeLogMapper.updateById(latest);
        }
    }

    /**
     * 执行实例列表（P4-2）：按 workflow_code 过滤 + 分页，start_time 倒序。
     * 运维可视化数据源（执行历史页面）。
     */
    public ApiResponse<Map<String, Object>> listExecutions(String workflowCode, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        LambdaQueryWrapper<WorkflowExecution> wrapper = new LambdaQueryWrapper<WorkflowExecution>()
                .eq(workflowCode != null && !workflowCode.isBlank(), WorkflowExecution::getWorkflowCode, workflowCode)
                .orderByDesc(WorkflowExecution::getId);
        long total = executionMapper.selectCount(wrapper);
        List<WorkflowExecution> records = executionMapper.selectList(wrapper
                .last("LIMIT " + safeSize + " OFFSET " + (long) (safePage - 1) * safeSize));
        List<Map<String, Object>> data = records.stream().map(this::executionToMap)
                .collect(java.util.stream.Collectors.toList());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total);
        response.put("page", safePage);
        response.put("page_size", safeSize);
        response.put("data", data);
        return ApiResponse.ok(response);
    }

    /** 节点执行记录列表（按 id 升序 = 执行时序；编辑器逐节点点亮的数据源）。 */
    public ApiResponse<Map<String, Object>> getNodeLogs(String executionId) {
        WorkflowExecution execution = findByExecutionId(executionId);
        if (execution == null) {
            return ApiResponse.fail("执行实例不存在: " + executionId);
        }
        List<WorkflowNodeLog> logs = nodeLogMapper.selectList(new LambdaQueryWrapper<WorkflowNodeLog>()
                .eq(WorkflowNodeLog::getExecutionId, executionId)
                .orderByAsc(WorkflowNodeLog::getId));
        List<Map<String, Object>> data = logs.stream().map(this::nodeLogToMap).collect(java.util.stream.Collectors.toList());
        return ApiResponse.ok(Map.of("node_logs", data));
    }

    /** 节点留痕 → JSON Map（snake_case 契约，对齐前端节点执行记录形态）。 */
    private Map<String, Object> nodeLogToMap(WorkflowNodeLog nodeLog) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", nodeLog.getId());
        map.put("execution_id", nodeLog.getExecutionId());
        map.put("node_id", nodeLog.getNodeId());
        map.put("node_type", nodeLog.getNodeType());
        map.put("status", nodeLog.getStatus());
        map.put("attempt", nodeLog.getAttempt());
        map.put("input_data", nodeLog.getInputData());
        map.put("output_data", nodeLog.getOutputData());
        map.put("error_message", nodeLog.getErrorMessage());
        map.put("branch_taken", nodeLog.getBranchTaken());
        map.put("started_at", nodeLog.getStartedAt());
        map.put("ended_at", nodeLog.getEndedAt());
        map.put("duration_ms", nodeLog.getDurationMs());
        return map;
    }

    /** 节点执行结果内部载体。 */
    record NodeOutcome(boolean success, String nodeType, String failureMode, String errorMessage,
                       Map<String, Object> output, long durationMs) {

        static NodeOutcome success(Map<String, Object> output) {
            return new NodeOutcome(true, null, null, null, output == null ? Map.of() : output, 0);
        }

        static NodeOutcome fail(String nodeType, String failureMode, String errorMessage) {
            return new NodeOutcome(false, nodeType, failureMode, errorMessage, Map.of(), 0);
        }
    }
}
