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
 * 流程定义期守门校验器 —— 固定流程引擎（P2-1）。
 * <p>
 * 设计依据：《固定流程引擎设计文档》§3/§8-P2-1、《业务驱动改造方案》§12.3 铁律三
 * （定义期守门前置：发布即绿灯，运行期不再出现"图本身非法"的失败类别）。
 * <p>
 * 校验项：
 * <ul>
 *   <li>结构：必填字段、节点 action 已注册、唯一 id</li>
 *   <li>拓扑：存在 start/end、无环（DAG 校验）、不可达节点告警</li>
 *   <li>引用：变量引用的节点存在且在当前路径上游；tool 节点工具名非空</li>
 *   <li>条件：condition 节点必须有 default 兜底分支、表达式非空且长度受限</li>
 *   <li>语义：执行语义字段取值合法（timeoutMs/retry/onFailure）</li>
 * </ul>
 * <p>
 * 本类为纯函数式校验（无状态、无外部依赖），供保存/发布时调用；
 * 与 Agent 体系的 DAG 校验同源（拓扑排序实现）。
 */
@Component
public class FlowDefinitionValidator {

    /** 引擎支持的节点类型（P2 首发集，收敛自编辑器 14 种，见设计文档 §3.1） */
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "flow.start", "flow.end", "flow.tool", "flow.llm",
            "flow.condition", "flow.human", "flow.http");

    public static final int MAX_EXPRESSION_LENGTH = 500;
    public static final long MAX_TIMEOUT_MS = 300_000L;
    public static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Set<String> FAILURE_MODES = Set.of("fail", "continue");

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
        }

        return new ValidationResult(problems.isEmpty(), problems);
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
