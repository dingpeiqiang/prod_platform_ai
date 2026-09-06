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
import com.sitech.prodai.service.flow.event.FlowEventPublisher;
import com.sitech.prodai.service.flow.event.FlowNodeEvent;
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
    private final FormSchemaPort formSchemaPort;
    /** 节点级事件发布器（W1-1）：可选依赖，无监听器时零开销；W4 起支持运行时换装。 */
    private volatile FlowEventPublisher eventPublisher;

    public FlowEngineService(WorkflowMapper workflowMapper,
                             WorkflowExecutionMapper executionMapper,
                             WorkflowNodeLogMapper nodeLogMapper,
                             ToolExecutionService toolExecutionService,
                             FlowDefinitionValidator validator,
                             ConditionEvaluator conditionEvaluator,
                             LlmGateway llmService,
                             HttpGateway restClient,
                             FormSchemaPort formSchemaPort) {
        this(workflowMapper, executionMapper, nodeLogMapper, toolExecutionService, validator,
                conditionEvaluator, llmService, restClient, formSchemaPort, new FlowEventPublisher(List.of()));
    }

    public FlowEngineService(WorkflowMapper workflowMapper,
                             WorkflowExecutionMapper executionMapper,
                             WorkflowNodeLogMapper nodeLogMapper,
                             ToolExecutionService toolExecutionService,
                             FlowDefinitionValidator validator,
                             ConditionEvaluator conditionEvaluator,
                             LlmGateway llmService,
                             HttpGateway restClient,
                             FormSchemaPort formSchemaPort,
                             FlowEventPublisher eventPublisher) {
        this.workflowMapper = workflowMapper;
        this.executionMapper = executionMapper;
        this.nodeLogMapper = nodeLogMapper;
        this.toolExecutionService = toolExecutionService;
        this.validator = validator;
        this.conditionEvaluator = conditionEvaluator;
        this.llmService = llmService;
        this.restClient = restClient;
        this.formSchemaPort = formSchemaPort;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 运行时替换事件发布器（W4 透明化）：引擎为单例，SSE 请求到达时由对话侧桥接器
     * 换装「转发当前请求 emitter」的发布器，请求结束后还原为空发布器。
     * <p>
     * 并发约定：同一时刻仅一个智聊流式请求持有发布器（后装者覆盖前者），
     * 覆盖前旧发布器仍在监听列表内的场景由桥接器侧以"仅转发本会话 execution_id"约束隔离。
     */
    public void setEventPublisher(FlowEventPublisher publisher) {
        this.eventPublisher = publisher != null ? publisher : new FlowEventPublisher(List.of());
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
     * G4 表单规格端口：隔离 FormService/OntologyService，避免引擎直连本体服务。
     * 端口实现按 formCode 返回字段定义 [{fieldCode, fieldName, required, ...}]；表单不存在返回 null。
     */
    public interface FormSchemaPort {
        java.util.List<Map<String, Object>> fields(String formCode);
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
        return ApiResponse.ok(withFormSpec(execution, definition, executionToMap(execution)));
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

        // G4：人工恢复数据校验——必须覆盖表单全部必填字段（校验失败不消费令牌，可重新提交）
        Map<String, Object> humanParams = humanNode.get("action_params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String formError = validateHumanFormData(humanParams, formData);
        if (formError != null) {
            return ApiResponse.fail(formError);
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

    /**
     * G4：挂起响应附带表单规格（仅 waiting_human 且节点配置 form_code 时注入 form_spec 键）。
     */
    private Map<String, Object> withFormSpec(WorkflowExecution execution, Map<String, Object> definition,
                                             Map<String, Object> responseMap) {
        if (!"waiting_human".equals(execution.getStatus()) || execution.getCurrentNodeId() == null) {
            return responseMap;
        }
        Map<String, Map<String, Object>> nodeById = indexNodes(definition);
        Map<String, Object> humanNode = nodeById.get(execution.getCurrentNodeId());
        if (humanNode == null || !"flow.human".equals(str(humanNode.get("action")))) {
            return responseMap;
        }
        Map<String, Object> spec = formSpecOf(humanNode.get("action_params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of());
        if (spec != null) {
            responseMap.put("form_spec", spec);
        }
        return responseMap;
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
        // G1 防环上下文：以当前流程编码初始化工作流链（workflow 节点递归时链式追加）
        if (execution.getWorkflowCode() != null && !variables.containsKey(WORKFLOW_CHAIN_KEY)) {
            variables.put(WORKFLOW_CHAIN_KEY, new ArrayList<>(List.of(execution.getWorkflowCode())));
        }
        // flow 命名空间：与 ConditionEvaluator 的 ${flow.*} 语义对齐，使节点 {{flow.x}} 引用两处一致
        variables.put("flow", new LinkedHashMap<>(variables));

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
                publishBranchTaken(execution, node, branchId);
                execution.setCurrentNodeId(nextNodeByHandle(current, branchId, definition));
                execution.setStatusVersion(bumpVersion(execution));
                executionMapper.updateById(execution);
                current = execution.getCurrentNodeId();
                continue;
            }

            // human 节点：挂起等人工（恢复经 resumeExecution 携带表单数据续推）
            if ("flow.human".equals(action)) {
                suspendAtHuman(execution, node, variables, definition);
                publishSuspended(execution, node, definition);
                return;
            }

            NodeOutcome outcome = executeNode(execution, node, variables);
            publishNodeFinished(execution, node, outcome);

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
        log.info("[FlowEngine] 执行挂起于人工节点: {} node={} token={} form_code={}",
                execution.getExecutionId(), nodeId, execution.getResumeToken(),
                str(params.get("form_code")));
    }

    /**
     * G4：human 节点表单规格（挂起响应附带 form_code + fields，供前端渲染表单）。
     * form_code 未配置或端口未注入时返回 null（节点退化为通用人工确认，兼容旧定义）。
     */
    private Map<String, Object> formSpecOf(Map<String, Object> humanNodeParams) {
        String formCode = str(humanNodeParams.get("form_code"));
        if (formCode == null || formCode.isBlank() || formSchemaPort == null) {
            return null;
        }
        java.util.List<Map<String, Object>> fields = formSchemaPort.fields(formCode);
        if (fields == null) {
            log.warn("[FlowEngine] human 节点 form_code 未找到表单定义: {}", formCode);
            return null;
        }
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("form_code", formCode);
        spec.put("fields", fields);
        return spec;
    }

    /**
     * G4：人工恢复数据校验——提交数据必须覆盖表单全部必填字段。
     * form_code 未配置/表单不存在时跳过（与挂起侧同语义，兼容旧定义）。
     * 字段键兼容两种契约：端口适配器的 snake_case（field_code/field_name）与本体原始 camelCase。
     */
    private String validateHumanFormData(Map<String, Object> humanNodeParams, Map<String, Object> formData) {
        Map<String, Object> spec = formSpecOf(humanNodeParams);
        if (spec == null || formData == null) {
            return null;
        }
        Object fieldsObj = spec.get("fields");
        if (!(fieldsObj instanceof java.util.List<?> fields)) {
            return null;
        }
        java.util.List<String> missing = new ArrayList<>();
        for (Object fieldObj : fields) {
            if (!(fieldObj instanceof Map<?, ?> field)) {
                continue;
            }
            if (!Boolean.TRUE.equals(field.get("required"))) {
                continue;
            }
            String fieldCode = firstNonBlankStr(field.get("field_code"), field.get("fieldCode"));
            String fieldName = firstNonBlankStr(field.get("field_name"), field.get("fieldName"));
            Object value = fieldCode == null ? null : formData.get(fieldCode);
            boolean empty = value == null
                    || (value instanceof String s && s.isBlank())
                    || (value instanceof java.util.List<?> l && l.isEmpty());
            if (empty) {
                missing.add(fieldName == null ? String.valueOf(fieldCode) : fieldName);
            }
        }
        return missing.isEmpty() ? null : "表单必填字段未填写: " + String.join("、", missing);
    }

    /** 取第一个非空字符串（空串视为缺失）。 */
    private String firstNonBlankStr(Object... candidates) {
        for (Object c : candidates) {
            if (c != null && !String.valueOf(c).isBlank()) {
                return String.valueOf(c);
            }
        }
        return null;
    }

    private int bumpVersion(WorkflowExecution execution) {
        return execution.getStatusVersion() == null ? 1 : execution.getStatusVersion() + 1;
    }

    /** 单节点执行：按类型分派。condition/human 由状态机直接处理；此处覆盖 start/tool/llm/http。
     *  执行语义（P5）：timeoutMs 超时中断、retry.maxAttempts 失败重试、onFailure=continue 失败跳过下游。
     *  W1-3：每次尝试各写一条 node_log（attempt 递增），输入快照/超时与失败原因落 error_message。 */
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
        Map<String, Object> inputSnapshot = inputSnapshot(params, variables);
        NodeOutcome outcome = runWithTimeoutAndRetry(execution, node, inputSnapshot, action, params,
                variables, timeoutMs, maxAttempts);
        if (!outcome.success()) {
            // onFailure=continue：失败不中止流程（错误信息保留在节点日志中供审计）
            outcome = new NodeOutcome(false, outcome.nodeType(), failureMode, outcome.errorMessage(), outcome.output(), outcome.durationMs());
        }
        long totalMs = System.currentTimeMillis() - startMs;
        log.info("[FlowEngine] 节点 {} ({}) 完成，耗时 {}ms attempts<= {} failureMode={}",
                nodeId, action, totalMs, maxAttempts, failureMode);
        return withDuration(outcome, totalMs);
    }

    /** 超时 + 重试包装：单次尝试在独立线程执行（超时 interrupt），失败按 maxAttempts 重试。
     *  W1-3：每次尝试（含重试）各写一条 node_log，attempt 递增，失败原因落 error_message。 */
    private NodeOutcome runWithTimeoutAndRetry(WorkflowExecution execution, Map<String, Object> node,
                                               Map<String, Object> inputSnapshot, String action,
                                               Map<String, Object> params,
                                               Map<String, Object> variables, long timeoutMs, int maxAttempts) {
        NodeOutcome outcome = NodeOutcome.fail(action, "fail", "节点未执行");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long attemptStart = System.currentTimeMillis();
            outcome = runAttemptOnce(action, params, variables, timeoutMs);
            long attemptMs = System.currentTimeMillis() - attemptStart;
            persistAttemptLog(execution, node, outcome, inputSnapshot, attempt, attemptMs);
            if (outcome.success()) {
                return withDuration(outcome, attemptMs);
            }
            log.warn("[FlowEngine] 节点 {} 第 {}/{} 次尝试失败: {}", str(node.get("id")), attempt, maxAttempts, outcome.errorMessage());
        }
        return withDuration(outcome, outcome.durationMs());
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
            case "flow.workflow" -> executeWorkflowNode(params, variables);
            default -> NodeOutcome.fail(action, "fail", "未注册的节点类型: " + action);
        };
    }

    /** G1 防环上下文键：当前执行栈中已进入的子流程链（variables 内携带，链式透传）。 */
    private static final String WORKFLOW_CHAIN_KEY = "__workflow_chain";
    /** 嵌套深度上限（与 FlowDefinitionValidator.MAX_WORKFLOW_NESTING_DEPTH 同源语义）。 */
    private static final int MAX_WORKFLOW_NESTING_DEPTH = 5;

    /**
     * G1 workflow 节点：引用已发布子流程并同步执行到底（子流程完整落库，执行 ID 入节点输出）。
     * <p>
     * 防环：variables 携带工作流编码链（含当前流程），链上出现重复编码即拒绝（直接自引用）；
     * 跨流程间接环由深度上限（{@value MAX_WORKFLOW_NESTING_DEPTH}）兜底。
     * 入参：inputParams 解析后作为子流程 inputData；出参：子流程终态 + 输出数据按子流程命名空间平铺。
     */
    private NodeOutcome executeWorkflowNode(Map<String, Object> params, Map<String, Object> variables) {
        String workflowRef = str(params.get("workflow_ref"));
        if (workflowRef == null || workflowRef.isBlank()) {
            return NodeOutcome.fail("flow.workflow", "fail", "workflow 节点未配置 workflow_ref");
        }
        List<String> chain = workflowChain(variables);
        if (chain.contains(workflowRef)) {
            return NodeOutcome.fail("flow.workflow", "fail",
                    "检测到子流程环引用: " + String.join(" → ", chain) + " → " + workflowRef);
        }
        if (chain.size() >= MAX_WORKFLOW_NESTING_DEPTH) {
            return NodeOutcome.fail("flow.workflow", "fail",
                    "子流程嵌套超限 (>" + MAX_WORKFLOW_NESTING_DEPTH + "): " + String.join(" → ", chain));
        }

        Map<String, Object> inputData = withWorkflowChain(
                resolveInputParams(params.get("inputParams"), variables), workflowRef);
        log.info("[FlowEngine] workflow 节点启动子流程: ref={}, depth={}, inputKeys={}",
                workflowRef, chain.size(), inputData.keySet());
        ApiResponse<Map<String, Object>> resp = startExecution(workflowRef, null, inputData, null);
        if (resp == null || !resp.isSuccess() || resp.getData() == null) {
            String reason = resp == null ? "子流程引擎无响应" : resp.getMessage();
            return NodeOutcome.fail("flow.workflow", "fail", "子流程 " + workflowRef + " 执行失败: " + reason);
        }
        Map<String, Object> data = resp.getData();
        String status = String.valueOf(data.getOrDefault("status", "unknown"));
        if (!"completed".equals(status)) {
            return NodeOutcome.fail("flow.workflow", "fail",
                    "子流程 " + workflowRef + " 终态非 completed: " + status
                            + (data.get("error_message") == null ? "" : "，" + data.get("error_message")));
        }
        // 输出：子流程执行明细 + 输出数据（context 透传的变量平铺到节点命名空间）
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("workflow_code", workflowRef);
        output.put("execution_id", data.get("execution_id"));
        output.put("status", status);
        if (data.get("context_data") instanceof Map<?, ?> ctx) {
            output.put("output_data", ctx);
        }
        return NodeOutcome.success(output);
    }

    /** 从变量表提取当前工作流编码链（workflow 节点递归时链式透传）。 */
    private List<String> workflowChain(Map<String, Object> variables) {
        Object raw = variables.get(WORKFLOW_CHAIN_KEY);
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /** 工作流编码链入子流程 inputData（G1 防环上下文透传）。 */
    private Map<String, Object> withWorkflowChain(Map<String, Object> inputData, String childCode) {
        List<String> chain = new ArrayList<>(workflowChain(inputData));
        chain.add(childCode);
        Map<String, Object> out = new LinkedHashMap<>(inputData);
        out.put(WORKFLOW_CHAIN_KEY, chain);
        return out;
    }

    private NodeOutcome withDuration(NodeOutcome outcome, long durationMs) {
        return new NodeOutcome(outcome.success(), outcome.nodeType(), outcome.failureMode(),
                outcome.errorMessage(), outcome.output(), durationMs);
    }

    /**
     * llm 节点（W1-2 结构化输出增强）：prompt/system_prompt 模板变量注入后调用 LlmService。
     * <p>
     * 增量 action_params（完全向后兼容，缺省行为与旧定义一致）：
     * <ul>
     *   <li>{@code system_prompt}：系统提示词，支持 {{ref}} 注入；</li>
     *   <li>{@code response_format}：text（缺省）/ json；json 时解析 LLM 输出为 Map；</li>
     *   <li>{@code json_schema}：{required:[...], properties:{...}} 轻量契约校验（必填键 + 类型），失败按 retry 语义重试；</li>
     *   <li>{@code output_mode}：raw（缺省，输出 {response}）/ flatten（解析后的 JSON 顶层键加 llm_ 前缀平铺）。</li>
     * </ul>
     * LLM 只进节点不进引擎（铁律二）。
     */
    private NodeOutcome executeLlmNode(Map<String, Object> params, Map<String, Object> variables) {
        Object prompt = params.get("prompt");
        if (prompt == null || String.valueOf(prompt).isBlank()) {
            return NodeOutcome.fail("flow.llm", "fail", "llm 节点未配置 prompt");
        }
        Map<String, Object> llmParams = new LinkedHashMap<>();
        llmParams.put("prompt", renderTemplate(String.valueOf(prompt), variables));
        if (params.get("system_prompt") != null) {
            llmParams.put("system_prompt", renderTemplate(String.valueOf(params.get("system_prompt")), variables));
        }
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
        return buildLlmOutcome(params, resp);
    }

    /** llm 输出后处理：json 格式解析 + json_schema 校验 + flatten/raw 输出形态。 */
    private NodeOutcome buildLlmOutcome(Map<String, Object> params, Object resp) {
        boolean jsonMode = "json".equalsIgnoreCase(str(params.get("response_format")));
        if (!jsonMode) {
            return NodeOutcome.success(Map.of("response", resp));
        }
        Object parsed = parseJsonSafely(String.valueOf(resp));
        if (!(parsed instanceof Map<?, ?> parsedMap)) {
            return NodeOutcome.fail("flow.llm", "fail",
                    "LLM 输出不是合法 JSON 对象: " + truncate(String.valueOf(resp)));
        }
        Map<String, Object> json = new LinkedHashMap<>((Map<String, Object>) parsedMap);
        String schemaError = validateJsonSchema(params.get("json_schema"), json);
        if (schemaError != null) {
            return NodeOutcome.fail("flow.llm", "fail", "LLM 输出契约校验失败: " + schemaError);
        }
        return NodeOutcome.success(buildLlmOutput(params.get("output_mode"), json, resp));
    }

    /** 输出形态：flatten 时顶层键加 llm_ 前缀平铺（防覆盖既有变量）；raw 缺省仅 {response, response_json}。 */
    private Map<String, Object> buildLlmOutput(Object outputMode, Map<String, Object> json, Object resp) {
        Map<String, Object> output = new LinkedHashMap<>();
        if ("flatten".equalsIgnoreCase(str(outputMode))) {
            for (Map.Entry<String, Object> entry : json.entrySet()) {
                output.put("llm_" + entry.getKey(), entry.getValue());
            }
            output.put("response_json", json);
        } else {
            output.put("response", resp);
            output.put("response_json", json);
        }
        return output;
    }

    /**
     * json_schema 轻量契约校验（复用 ToolContractValidator 的必填键 + 类型校验思想）：
     * schema 为空直接通过；required 键必须存在；properties 声明的类型必须匹配（string/number/boolean/object/array）。
     */
    private String validateJsonSchema(Object schema, Map<String, Object> json) {
        if (!(schema instanceof Map<?, ?> schemaMap)) {
            return null;
        }
        java.util.List<String> problems = new ArrayList<>();
        if (schemaMap.get("required") instanceof List<?> required) {
            for (Object key : required) {
                if (!json.containsKey(String.valueOf(key)) || json.get(String.valueOf(key)) == null) {
                    problems.add("缺少必填字段: " + key);
                }
            }
        }
        if (schemaMap.get("properties") instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String field = String.valueOf(entry.getKey());
                String expectedType = entry.getValue() instanceof Map<?, ?> prop
                        ? str(prop.get("type")) : null;
                if (expectedType != null && json.containsKey(field) && json.get(field) != null
                        && !jsonTypeMatches(expectedType, json.get(field))) {
                    problems.add("字段 " + field + " 类型应为 " + expectedType);
                }
            }
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    /** JSON 类型宽松匹配：number 兼容整型/浮点，其余按 Java 类型直映射。 */
    private boolean jsonTypeMatches(String expectedType, Object value) {
        return switch (expectedType) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            default -> true;
        };
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

    // ── W1-3：节点输入快照 + 每次尝试独立留痕 ──

    /**
     * 输入快照：变量解析后的真实入参（脱敏策略与 toolInputView 一致——白名单思路，
     * 隐藏 ThinkingCopy.HIDDEN_INPUT_KEYS 声明的内部噪声键与引擎内部键，避免 node_log 膨胀/泄噪）。
     */
    private Map<String, Object> inputSnapshot(Map<String, Object> params, Map<String, Object> variables) {
        Map<String, Object> resolved = resolveInputParams(params.get("inputParams"), variables);
        if (resolved.isEmpty() && params.containsKey("prompt")) {
            // llm 节点：入参快照记录渲染后的 prompt（截断防膨胀）
            resolved.put("prompt", truncate(String.valueOf(renderTemplate(String.valueOf(params.get("prompt")), variables))));
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : resolved.entrySet()) {
            if (com.sitech.prodai.service.agent.tool.ThinkingCopy.hideInputKey(e.getKey())
                    || e.getKey().startsWith("__")) {
                continue; // 内部键（__workflow_chain 等）不入快照
            }
            snapshot.put(e.getKey(), e.getValue());
        }
        return snapshot;
    }

    /** 每次尝试（含重试）各写一条 node_log：attempt 递增，耗时/失败原因独立留痕。 */
    private void persistAttemptLog(WorkflowExecution execution, Map<String, Object> node, NodeOutcome outcome,
                                   Map<String, Object> inputSnapshot, int attempt, long attemptMs) {
        WorkflowNodeLog nodeLog = new WorkflowNodeLog();
        nodeLog.setExecutionId(execution.getExecutionId());
        nodeLog.setNodeId(str(node.get("id")));
        nodeLog.setNodeType(str(node.get("action")));
        nodeLog.setStatus(outcome.success() ? "completed" : "failed");
        nodeLog.setAttempt(attempt);
        nodeLog.setInputData(inputSnapshot.isEmpty() ? null : inputSnapshot);
        nodeLog.setOutputData(outcome.output() == null || outcome.output().isEmpty() ? null : outcome.output());
        nodeLog.setErrorMessage(outcome.errorMessage());
        nodeLog.setStartedAt(LocalDateTime.now().minus(Duration.ofMillis(attemptMs)));
        nodeLog.setEndedAt(LocalDateTime.now());
        nodeLog.setDurationMs(attemptMs);
        nodeLogMapper.insert(nodeLog);
    }

    // ── W1-1：节点级事件发布（事务提交后，异步隔离，失败仅告警） ──

    /** 节点执行终态事件（成功 → node_completed；失败 → node_failed）。 */
    private void publishNodeFinished(WorkflowExecution execution, Map<String, Object> node, NodeOutcome outcome) {
        eventPublisher.publish(FlowNodeEvent
                .of(outcome.success() ? FlowNodeEvent.NODE_COMPLETED : FlowNodeEvent.NODE_FAILED,
                        execution.getExecutionId(), execution.getWorkflowCode())
                .nodeId(str(node.get("id")))
                .nodeType(str(node.get("action")))
                .nodeName(str(node.get("name")))
                .status(outcome.success() ? "done" : "error")
                .errorMessage(outcome.errorMessage())
                .output(outcome.output())
                .build());
    }

    /** condition 分支命中事件（前端时间线显示"走了哪条边、为什么"）。 */
    private void publishBranchTaken(WorkflowExecution execution, Map<String, Object> node, String branchId) {
        eventPublisher.publish(FlowNodeEvent
                .of(FlowNodeEvent.BRANCH_TAKEN, execution.getExecutionId(), execution.getWorkflowCode())
                .nodeId(str(node.get("id")))
                .nodeType("flow.condition")
                .nodeName(str(node.get("name")))
                .status(branchId != null ? "done" : "error")
                .branchTaken(branchId)
                .build());
    }

    /** human 挂起事件（对话侧翻译为 done(AWAIT_*) 变体 + form_spec 下发）。 */
    private void publishSuspended(WorkflowExecution execution, Map<String, Object> node,
                                  Map<String, Object> definition) {
        Map<String, Map<String, Object>> nodeById = indexNodes(definition);
        Map<String, Object> humanNode = nodeById.get(execution.getCurrentNodeId());
        Map<String, Object> spec = humanNode == null ? null
                : formSpecOf(humanNode.get("action_params") instanceof Map<?, ?> p
                        ? (Map<String, Object>) p : Map.of());
        eventPublisher.publish(FlowNodeEvent
                .of(FlowNodeEvent.SUSPENDED, execution.getExecutionId(), execution.getWorkflowCode())
                .nodeId(str(node.get("id")))
                .nodeType("flow.human")
                .nodeName(str(node.get("name")))
                .status("suspended")
                .formSpec(spec)
                .build());
    }

    /** 执行实例终态事件（completed/failed 各一）。 */
    private void publishExecutionFinished(WorkflowExecution execution, boolean success) {
        eventPublisher.publish(FlowNodeEvent
                .of(success ? FlowNodeEvent.EXECUTION_COMPLETED : FlowNodeEvent.EXECUTION_FAILED,
                        execution.getExecutionId(), execution.getWorkflowCode())
                .status(success ? "completed" : "failed")
                .errorMessage(execution.getErrorMessage())
                .build());
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
        publishExecutionFinished(execution, true);
        log.info("[FlowEngine] 执行完成: {}", execution.getExecutionId());
    }

    private void failExecution(WorkflowExecution execution, String message) {
        execution.setStatus("failed");
        execution.setErrorMessage(message);
        execution.setEndTime(LocalDateTime.now());
        executionMapper.updateById(execution);
        publishExecutionFinished(execution, false);
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
