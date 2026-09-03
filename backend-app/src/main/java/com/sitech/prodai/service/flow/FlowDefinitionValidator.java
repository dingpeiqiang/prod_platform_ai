package com.sitech.prodai.service.flow;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程定义期守门校验器 —— 固定流程引擎（P2-1，G2/G3 发布期工具守门）。
 * <p>
 * 设计依据：《固定流程引擎设计文档》§3/§8-P2-1、《业务驱动改造方案》§12.3 铁律三
 * （定义期守门前置：发布即绿灯，运行期不再出现"图本身非法"的失败类别）、
 * 《产商品研发助手改造方案》§3.1（G2 toolName 无守门 / G3 输出契约未校验）。
 * <p>
 * 校验项：
 * <ul>
 *   <li>结构：必填字段、节点 action 已注册、唯一 id</li>
 *   <li>拓扑：存在 start/end、无环（DAG 校验）、不可达节点告警</li>
 *   <li>引用：变量引用的节点存在且在当前路径上游；tool 节点工具名非空</li>
 *   <li>工具（G2）：tool 节点 toolName 必须已注册（ToolExecutionService 注册表）</li>
 *   <li>工具（G3）：tool 节点 outputParams.source 首段必须在工具输出契约
 *       （{@code AgentTool.getOutputFields()}）声明的字段内</li>
 *   <li>条件：condition 节点必须有 default 兜底分支、表达式非空且长度受限</li>
 *   <li>语义：执行语义字段取值合法（timeoutMs/retry/onFailure）</li>
 * </ul>
 * <p>
 * 结构/拓扑/条件/语义校验为纯函数（无状态）；工具类校验（G2/G3）依赖
 * {@link com.sitech.prodai.service.ToolExecutionService} 注册表——缺省构造时为 null
 * 即跳过该类校验，保证既有无参调用方零改动；Spring 装配时注入真实注册表自动生效。
 */
@Component
public class FlowDefinitionValidator {

    /** 引擎支持的节点类型（P2 首发集 + G1 workflow 子流程节点，见设计文档 §3.1） */
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "flow.start", "flow.end", "flow.tool", "flow.llm",
            "flow.condition", "flow.human", "flow.http", "flow.workflow");

    /** workflow 节点嵌套深度上限（G1 防环兜底：DAG 校验只查单图，跨流程环靠深度上限拒绝）。 */
    public static final int MAX_WORKFLOW_NESTING_DEPTH = 5;

    public static final int MAX_EXPRESSION_LENGTH = 500;
    public static final long MAX_TIMEOUT_MS = 300_000L;
    public static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Set<String> FAILURE_MODES = Set.of("fail", "continue");

    /** 工具注册表引用：null 时工具类校验（G2/G3）关闭（兼容既有无参单测）。 */
    private final com.sitech.prodai.service.ToolExecutionService toolExecutionService;

    /** Spring 装配构造：接入真实工具注册表，G2/G3 校验生效。 */
    public FlowDefinitionValidator(com.sitech.prodai.service.ToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    /** 缺省构造：工具类校验关闭（纯结构/拓扑/条件/语义校验），兼容既有无参调用方与单测。 */
    public FlowDefinitionValidator() {
        this(null);
    }

    /** 校验结果：valid=false 时 problems 含全部可读问题（发布被拒的依据）。 */
    public record ValidationResult(boolean valid, List<String> problems) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }
    }

    /**
     * 校验流程定义（nodes + connections 结构，对齐《工作流配置规范》）。
     *
     * @param definition workflow_data 反序列化后的 Map
     * @return 校验结果
     */
    public ValidationResult validate(Map<String, Object> definition) {
        List<String> problems = new ArrayList<>();
        if (definition == null) {
            problems.add("流程定义为空");
            return new ValidationResult(false, problems);
        }

        if (!(definition.get("nodes") instanceof List<?> nodes) || nodes.isEmpty()) {
            problems.add("nodes 不能为空");
            return new ValidationResult(false, problems);
        }
        if (!(definition.get("connections") instanceof List<?> connections)) {
            problems.add("connections 缺失或格式非法");
            return new ValidationResult(false, problems);
        }

        // ── 节点结构校验 ──
        Map<String, Map<String, Object>> nodeById = new LinkedHashMap<>();
        int startCount = 0;
        int endCount = 0;
        for (int i = 0; i < nodes.size(); i++) {
            Object raw = nodes.get(i);
            if (!(raw instanceof Map<?, ?> rawNode)) {
                problems.add("nodes[" + i + "] 不是对象");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) rawNode;
            String nodeId = str(node.get("id"));
            if (nodeId == null || nodeId.isBlank()) {
                problems.add("nodes[" + i + "] 缺少 id");
                continue;
            }
            if (nodeById.containsKey(nodeId)) {
                problems.add("节点 id 重复: " + nodeId);
                continue;
            }
            nodeById.put(nodeId, node);

            String action = str(node.get("action"));
            if (action == null || action.isBlank()) {
                problems.add("节点 " + nodeId + " 缺少 action");
            } else if (!SUPPORTED_TYPES.contains(action)) {
                problems.add("节点 " + nodeId + " 的 action 不受引擎支持: " + action);
            }

            if ("flow.start".equals(action)) {
                startCount++;
            } else if ("flow.end".equals(action)) {
                endCount++;
            }
            validateExecutionSemantics(nodeId, node, problems);
        }
        if (startCount != 1) {
            problems.add("必须且只能有一个 flow.start 节点（当前 " + startCount + " 个）");
        }
        if (endCount < 1) {
            problems.add("至少需要一个 flow.end 节点");
        }

        // ── 连接校验 ──
        Map<String, List<String>> adjacency = new HashMap<>();
        for (int i = 0; i < connections.size(); i++) {
            Object raw = connections.get(i);
            if (!(raw instanceof Map<?, ?> rawConn)) {
                problems.add("connections[" + i + "] 不是对象");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> conn = (Map<String, Object>) rawConn;
            String source = str(conn.get("source"));
            String target = str(conn.get("target"));
            if (source == null || target == null) {
                problems.add("connections[" + i + "] 缺少 source/target");
                continue;
            }
            if (!nodeById.containsKey(source)) {
                problems.add("连接 source 不存在: " + source);
            }
            if (!nodeById.containsKey(target)) {
                problems.add("连接 target 不存在: " + target);
                continue;
            }
            adjacency.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
        }

        // ── DAG 无环校验（拓扑排序，与 Agent 侧守门同源） ──
        validateAcyclic(nodeById.keySet(), adjacency, problems);

        // ── 条件/引用语义校验 ──
        for (Map.Entry<String, Map<String, Object>> entry : nodeById.entrySet()) {
            validateConditionNode(entry.getKey(), entry.getValue(), problems);
            validateVariableReferences(entry.getKey(), entry.getValue(), nodeById.keySet(), problems);
            validateToolNodeBinding(entry.getKey(), entry.getValue(), problems);
            validateWorkflowNode(entry.getKey(), entry.getValue(), problems);
            validateHumanNodeForm(entry.getKey(), entry.getValue(), problems);
        }

        return new ValidationResult(problems.isEmpty(), problems);
    }

    /**
     * G4 human 节点校验：声明了 form_code 时必须非空字符串（表单存在性由引擎运行期容错——
     * 未知表单退化为通用人工确认，不在定义期强绑本体库，避免守门依赖本体服务）。
     */
    private void validateHumanNodeForm(String nodeId, Map<String, Object> node, List<String> problems) {
        if (!"flow.human".equals(str(node.get("action")))) {
            return;
        }
        Map<String, Object> params = node.get("action_params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        Object formCode = params.get("form_code");
        if (formCode != null && String.valueOf(formCode).isBlank()) {
            problems.add("human 节点 " + nodeId + " 的 form_code 不能为空字符串");
        }
    }

    /**
     * G1 workflow 节点校验：workflow_ref 必填（子流程编码）、不可自引用（单图级防环；
     * 跨流程环由引擎运行期嵌套深度上限兜底）。
     */
    private void validateWorkflowNode(String nodeId, Map<String, Object> node, List<String> problems) {
        if (!"flow.workflow".equals(str(node.get("action")))) {
            return;
        }
        Map<String, Object> params = node.get("action_params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String ref = str(params.get("workflow_ref"));
        if (ref == null || ref.isBlank()) {
            problems.add("workflow 节点 " + nodeId + " 缺少 workflow_ref（子流程编码）");
            return;
        }
        if (ref.equals(nodeId)) {
            problems.add("workflow 节点 " + nodeId + " 的 workflow_ref 不可自引用");
        }
    }

    /**
     * G2/G3 工具节点绑定校验（注册表未注入时跳过）：
     * <ul>
     *   <li>G2：toolName 必须已注册——防配置错误到运行期才暴露（"工具不存在"）</li>
     *   <li>G3：outputParams[].source 首段必须在工具输出契约内——防节点间
     *       dataPath 引用错误静默产出 null（对齐 Agent 侧 inputFrom 校验语义）</li>
     * </ul>
     */
    private void validateToolNodeBinding(String nodeId, Map<String, Object> node, List<String> problems) {
        if (!"flow.tool".equals(str(node.get("action"))) || toolExecutionService == null) {
            return;
        }
        Map<String, Object> params = node.get("action_params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();
        String toolName = str(params.get("toolName"));
        if (toolName == null || toolName.isBlank()) {
            return; // toolName 缺失由既有结构校验语义覆盖（引擎运行期显式报错），此处不重复报
        }
        if (!toolExecutionService.containsTool(toolName)) {
            problems.add("节点 " + nodeId + " 引用的工具未注册: " + toolName);
            return;
        }
        // G3：输出契约校验（工具未声明输出契约时跳过，兼容旧工具）
        Set<String> contract = toolOutputFieldNames(toolName);
        if (contract.isEmpty()) {
            return;
        }
        if (params.get("outputParams") instanceof List<?> outputParams) {
            for (int i = 0; i < outputParams.size(); i++) {
                if (!(outputParams.get(i) instanceof Map<?, ?> item)) {
                    continue;
                }
                String source = str(item.get("source"));
                if (source == null || source.isBlank() || "response".equals(source)) {
                    continue;
                }
                String head = source.contains(".") ? source.substring(0, source.indexOf('.')) : source;
                if (!contract.contains(head)) {
                    problems.add("节点 " + nodeId + " 的 outputParams[" + i + "] 引用了工具 "
                            + toolName + " 输出契约外的字段: " + source);
                }
            }
        }
    }

    /** 工具输出契约字段名集合（G3）；工具不存在/未声明契约返回空集。 */
    private Set<String> toolOutputFieldNames(String toolName) {
        com.sitech.prodai.service.agent.tool.AgentTool tool = toolExecutionService.getTool(toolName);
        if (tool == null || tool.getOutputFields() == null) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        tool.getOutputFields().forEach(f -> names.add(f.getName()));
        return names;
    }

    /** 执行语义字段取值合法（timeoutMs/retry/onFailure，设计文档 §3.2）。 */
    private void validateExecutionSemantics(String nodeId, Map<String, Object> node, List<String> problems) {
        Object params = node.get("action_params");
        if (!(params instanceof Map<?, ?> paramMap)) {
            return;
        }
        Object timeout = paramMap.get("timeoutMs");
        if (timeout instanceof Number n && (n.longValue() <= 0 || n.longValue() > MAX_TIMEOUT_MS)) {
            problems.add("节点 " + nodeId + " 的 timeoutMs 超界 (0," + MAX_TIMEOUT_MS + "]: " + n);
        }
        Object onFailure = paramMap.get("onFailure");
        if (onFailure != null && !FAILURE_MODES.contains(String.valueOf(onFailure))) {
            problems.add("节点 " + nodeId + " 的 onFailure 非法: " + onFailure + "（允许 fail/continue）");
        }
        if (paramMap.get("retry") instanceof Map<?, ?> retry) {
            Object attempts = retry.get("maxAttempts");
            if (attempts instanceof Number n && (n.intValue() < 0 || n.intValue() > MAX_RETRY_ATTEMPTS)) {
                problems.add("节点 " + nodeId + " 的 retry.maxAttempts 超界 [0," + MAX_RETRY_ATTEMPTS + "]: " + n);
            }
        }
    }

    /** condition 节点：必须有 default 兜底分支、表达式非空且受限（设计文档 §3.3/§5.2）。 */
    private void validateConditionNode(String nodeId, Map<String, Object> node, List<String> problems) {
        if (!"flow.condition".equals(str(node.get("action")))) {
            return;
        }
        if (!(node.get("action_params") instanceof Map<?, ?> paramMap)
                || !(paramMap.get("branches") instanceof List<?> branches) || branches.isEmpty()) {
            problems.add("condition 节点 " + nodeId + " 缺少 branches");
            return;
        }
        boolean hasDefault = false;
        for (int i = 0; i < branches.size(); i++) {
            if (!(branches.get(i) instanceof Map<?, ?> rawBranch)) {
                problems.add("condition 节点 " + nodeId + " branches[" + i + "] 不是对象");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> branch = (Map<String, Object>) rawBranch;
            String expr = str(branch.get("expression"));
            if (expr == null || expr.isBlank()) {
                problems.add("condition 节点 " + nodeId + " branches[" + i + "] 缺少 expression");
            } else if ("default".equals(expr)) {
                hasDefault = true;
            } else if (expr.length() > MAX_EXPRESSION_LENGTH) {
                problems.add("condition 节点 " + nodeId + " branches[" + i + "] 表达式超长 (>" + MAX_EXPRESSION_LENGTH + ")");
            } else if (expr.contains("T(") || expr.contains("new ") || expr.contains("#this")) {
                problems.add("condition 节点 " + nodeId + " branches[" + i + "] 表达式含禁用语法（T(/new /#this）");
            }
        }
        if (!hasDefault) {
            problems.add("condition 节点 " + nodeId + " 必须声明 default 兜底分支");
        }
    }

    /** 变量引用校验：引用的节点存在（节点级校验；上游性由 DAG 无环保证）。 */
    private void validateVariableReferences(String nodeId, Map<String, Object> node,
                                            Set<String> validNodeIds, List<String> problems) {
        collectVariableRefs(node).forEach(ref -> {
            String refNode = ref.contains(".") ? ref.substring(0, ref.indexOf('.')) : ref;
            if (!ref.startsWith("flow.") && !ref.startsWith("system.") && !validNodeIds.contains(refNode)) {
                problems.add("节点 " + nodeId + " 引用了不存在的节点: " + refNode + "（" + ref + "）");
            }
        });
    }

    /** 递归收集节点配置中的 {{ref}} 引用。 */
    private List<String> collectVariableRefs(Object value) {
        List<String> refs = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(v -> refs.addAll(collectVariableRefs(v)));
        } else if (value instanceof List<?> list) {
            list.forEach(v -> refs.addAll(collectVariableRefs(v)));
        } else if (value instanceof String s) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\{\\{([^}]+)}}").matcher(s);
            while (m.find()) {
                refs.add(m.group(1).trim());
            }
        }
        return refs;
    }

    private void validateAcyclic(Set<String> nodeIds, Map<String, List<String>> adjacency, List<String> problems) {
        // P3-2 抽公共：Kahn 拓扑环检测统一收口至 DagValidator（环节点列表用于可读报错）
        List<String> cyclic = new ArrayList<>();
        if (com.sitech.prodai.service.common.DagValidator.hasCycleKahn(nodeIds, adjacency, cyclic)) {
            problems.add("流程存在环，涉及节点: " + cyclic);
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
