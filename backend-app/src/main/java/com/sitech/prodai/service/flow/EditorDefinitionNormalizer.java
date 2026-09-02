package com.sitech.prodai.service.flow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑器定义 → 引擎定义 归一化器（P3-1a2）。
 * <p>
 * 设计依据：《固定流程引擎设计文档》§7（编辑器执行入口切换后端引擎）、
 * 《业务驱动改造方案》§12.3 铁律三（定义期守门前置：归一化之后仍走
 * {@link FlowDefinitionValidator#validate} 守门，本类只做形态翻译不做语义放行）。
 * <p>
 * 编辑器持久化的是 VueFlow 原始形态：{@code nodes: [{id, type, data:{...}}]} +
 * {@code edges: [{source, target, sourceHandle}]}，节点 type 为 start/end/llm/tool/
 * condition/userInput/form/http 等；引擎期望 {@code {id, action, action_params}} +
 * {@code connections: [{source, target, sourceHandle}]}。
 * <p>
 * 映射关系（编辑器 14 种 → 引擎 7 种，其余类型不映射、由守门拒绝）：
 * <pre>
 *   start      → flow.start     (data.parameters 原样进 action_params.inputParams)
 *   end        → flow.end       (data.outputParams 原样)
 *   llm        → flow.llm       (data.prompt/model/temperature + inputParams.value=refValue|defaultValue)
 *   tool       → flow.tool      (data.tool_type → toolName + inputParams.value=refValue|value)
 *   http       → flow.http      (data.httpUrl → url, data.httpMethod → method)
 *   condition  → flow.condition (data.branches → branches[{id, expression}]，else → default)
 *   userInput/form → flow.human (data.prompt → prompt)
 * </pre>
 * <p>
 * 本类为纯函数式静态工具（无状态、无外部依赖）；不认识的编辑器节点类型原样保留 type 字段，
 * 由 {@link FlowDefinitionValidator} 以"缺少 action"拒绝——归一化不吞错误。
 */
public final class EditorDefinitionNormalizer {

    /** 编辑器节点 type → 引擎 action 映射（P3 首发集）。 */
    private static final Map<String, String> TYPE_TO_ACTION = Map.of(
            "start", "flow.start",
            "end", "flow.end",
            "llm", "flow.llm",
            "tool", "flow.tool",
            "http", "flow.http",
            "condition", "flow.condition",
            "userInput", "flow.human",
            "form", "flow.human");

    private EditorDefinitionNormalizer() {
    }

    /**
     * 判断定义是否为编辑器原始形态（节点含 type 字段且不含 action 字段）。
     * 已是引擎形态（含 action）的定义直接返回 false，避免二次转换。
     */
    public static boolean needsNormalize(Map<String, Object> definition) {
        if (definition == null || !(definition.get("nodes") instanceof List<?> nodes)) {
            return false;
        }
        for (Object raw : nodes) {
            if (raw instanceof Map<?, ?> node) {
                return node.get("action") == null && node.get("type") != null;
            }
        }
        return false;
    }

    /**
     * 归一化：编辑器形态（nodes + edges）→ 引擎形态（nodes[action/action_params] + connections）。
     * 原定义不做修改（返回新 Map）；转换失败的节点保留原样交由守门报错。
     *
     * @param definition workflow_data 反序列化后的编辑器形态 Map
     * @return 引擎形态 Map（结构满足 FlowDefinitionValidator.validate 输入）
     */
    public static Map<String, Object> normalize(Map<String, Object> definition) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (definition == null) {
            return normalized;
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        if (definition.get("nodes") instanceof List<?> rawNodes) {
            for (Object raw : rawNodes) {
                if (raw instanceof Map<?, ?> node) {
                    nodes.add(normalizeNode(asStringMap(node)));
                }
            }
        }
        normalized.put("nodes", nodes);

        List<Map<String, Object>> connections = new ArrayList<>();
        // 编辑器持久化的边字段名是 edges；兼容旧数据里的 connections
        Object rawEdges = definition.get("edges") != null ? definition.get("edges") : definition.get("connections");
        if (rawEdges instanceof List<?> edges) {
            for (Object raw : edges) {
                if (raw instanceof Map<?, ?> edge) {
                    connections.add(normalizeEdge(asStringMap(edge)));
                }
            }
        }
        normalized.put("connections", connections);
        return normalized;
    }

    /** 单节点归一化：type → action，data → action_params；未映射类型原样透传（守门拒绝）。 */
    private static Map<String, Object> normalizeNode(Map<String, Object> node) {
        String type = str(node.get("type"));
        Map<String, Object> data = asStringMap(node.get("data"));
        String action = TYPE_TO_ACTION.get(type);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", str(node.get("id")));
        // 保留 label 供执行面板展示（引擎不消费，仅透传）
        if (data != null && data.get("label") != null) {
            result.put("label", data.get("label"));
        }
        if (action == null) {
            // 未映射类型：保留 type 原样，不生成 action —— 守门以"缺少 action"拒绝
            result.put("type", type);
            return result;
        }

        result.put("action", action);
        result.put("action_params", buildActionParams(action, data));
        return result;
    }

    /** 按目标 action 从编辑器 data 提取 action_params。 */
    private static Map<String, Object> buildActionParams(String action, Map<String, Object> data) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (data == null) {
            return params;
        }
        switch (action) {
            case "flow.start" -> {
                // data.parameters: [{name, type, description, default, required}] → inputParams 原样透传
                copyIfPresent(params, data, "parameters", "inputParams");
            }
            case "flow.end" -> copyIfPresent(params, data, "outputParams", "outputParams");
            case "flow.llm" -> {
                copyIfPresent(params, data, "prompt", "prompt");
                copyIfPresent(params, data, "model", "model");
                if (data.get("temperature") instanceof Number t) {
                    params.put("temperature", t.doubleValue());
                }
                params.put("inputParams", normalizeLlmInputs(data.get("inputParams")));
            }
            case "flow.tool" -> {
                // 编辑器 tool_type == tool_name == 工具注册名
                String toolName = firstNonBlank(data.get("tool_type"), data.get("tool_name"), data.get("toolType"), data.get("toolName"));
                params.put("toolName", toolName);
                params.put("inputParams", normalizeToolInputs(data.get("inputParams")));
                params.put("outputParams", data.get("outputParams"));
            }
            case "flow.http" -> {
                // 前端字段名是 httpUrl/httpMethod（非 url/method）
                params.put("url", firstNonBlank(data.get("httpUrl"), data.get("url")));
                params.put("method", firstNonBlank(data.get("httpMethod"), data.get("method")));
                params.put("inputParams", data.get("inputParams"));
            }
            case "flow.condition" -> params.put("branches", normalizeBranches(data.get("branches")));
            case "flow.human" -> {
                params.put("prompt", firstNonBlank(data.get("prompt"), data.get("message")));
                params.put("inputParams", data.get("inputParams"));
            }
            default -> {
                // 不可达（TYPE_TO_ACTION 之外不会进这里）
            }
        }
        return params;
    }

    /**
     * condition branches 归一化：
     * 编辑器 {@code [{type:'if/else_if/else', handle:'branch_xxx', conditions:[{variable, operator, value, valueType}]}]}
     * → 引擎 {@code [{id: handle, expression: SpEL}]}；else 分支 expression 固定为 "default"。
     * <p>
     * 操作符 → SpEL 翻译：==/!=/&gt;/&lt;/&gt;=/&lt;= 直译；contains/not_contains/starts_with/ends_with/matches
     * 翻译为 String 方法调用；is_empty/not_empty 翻译为长度/空判。多条件按前端语义 AND 连接。
     */
    static List<Map<String, Object>> normalizeBranches(Object rawBranches) {
        List<Map<String, Object>> branches = new ArrayList<>();
        if (!(rawBranches instanceof List<?> list)) {
            return branches;
        }
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> rawBranch)) {
                continue;
            }
            Map<String, Object> branch = asStringMap(rawBranch);
            Map<String, Object> out = new LinkedHashMap<>();
            String handle = str(branch.get("handle"));
            out.put("id", handle != null ? handle : "branch_" + branches.size());

            if ("else".equals(branch.get("type"))) {
                out.put("expression", "default");
            } else {
                out.put("expression", buildExpression(branch.get("conditions")));
            }
            branches.add(out);
        }
        return branches;
    }

    /** 单分支条件 → SpEL 表达式（${ref} 占位 + 操作符翻译；多条件 AND 连接）。 */
    private static String buildExpression(Object rawConditions) {
        if (!(rawConditions instanceof List<?> conditions) || conditions.isEmpty()) {
            // 无条件的 if 分支恒为真（与前端"无有效条件即展示"语义一致）
            return "true";
        }
        List<String> parts = new ArrayList<>();
        for (Object raw : conditions) {
            if (!(raw instanceof Map<?, ?> rawCond)) {
                continue;
            }
            Map<String, Object> cond = asStringMap(rawCond);
            String variable = str(cond.get("variable"));
            String operator = str(cond.get("operator"));
            if (variable == null || variable.isBlank() || operator == null || operator.isBlank()) {
                continue; // 未配置完整的条件行跳过（与前端 hasConditions 过滤一致）
            }
            parts.add(toSpelClause(variable, operator, cond));
        }
        if (parts.isEmpty()) {
            return "true";
        }
        return String.join(" and ", parts);
    }

    /** 单条件 → SpEL 子句。变量引用翻译为 ${nodeId.output.field}；操作符翻译为 SpEL。 */
    private static String toSpelClause(String variable, String operator, Map<String, Object> cond) {
        String ref = "${" + variable + "}";
        String rawValue = str(cond.get("value"));
        boolean isRefValue = "reference".equals(cond.get("valueType"));
        // 引用值同样是 ${ref} 占位；字面值按数字/布尔/字符串归类
        String valueLiteral = isRefValue ? (rawValue == null ? "null" : "${" + rawValue + "}")
                : literal(rawValue);

        return switch (operator) {
            case "==", "!=" -> ref + " " + operator + " " + valueLiteral;
            case ">", "<", ">=", "<=" -> ref + " " + operator + " " + valueLiteral;
            case "contains" -> ref + "?.toString().contains(" + valueLiteral + ")";
            case "not_contains" -> "!" + ref + "?.toString().contains(" + valueLiteral + ")";
            case "starts_with" -> ref + "?.toString().startsWith(" + valueLiteral + ")";
            case "ends_with" -> ref + "?.toString().endsWith(" + valueLiteral + ")";
            case "matches" -> ref + "?.toString().matches(" + valueLiteral + ")";
            case "is_empty" -> "(" + ref + " == null or " + ref + "?.toString().isEmpty() == true)";
            case "not_empty" -> "(" + ref + " != null and " + ref + "?.toString().isEmpty() == false)";
            default -> ref + " == " + valueLiteral; // 未知操作符退化为 ==（守门沙箱兜底）
        };
    }

    /** 字面值 → SpEL 字面量（数字/布尔直出，其余加单引号）。 */
    private static String literal(String raw) {
        if (raw == null || raw.isBlank()) {
            return "''";
        }
        String trimmed = raw.trim();
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return trimmed;
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return trimmed.toLowerCase();
        }
        return "'" + trimmed.replace("'", "\\'") + "'";
    }

    /**
     * llm 节点 inputParams 归一化：编辑器 {@code [{name, valueType, defaultValue|refValue}]}
     * → 引擎 {@code [{name, value}]}（value = refValue ?? defaultValue）。
     */
    static List<Map<String, Object>> normalizeLlmInputs(Object rawInputs) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(rawInputs instanceof List<?> inputs)) {
            return out;
        }
        for (Object raw : inputs) {
            if (!(raw instanceof Map<?, ?> item)) {
                continue;
            }
            Map<String, Object> input = asStringMap(item);
            String name = str(input.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            String value = firstNonBlank(input.get("refValue"), input.get("defaultValue"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("value", value);
            out.add(entry);
        }
        return out;
    }

    /**
     * tool 节点 inputParams 归一化：编辑器 {@code [{name, sourceType, value}]}
     * （sourceType=ref 时 value 已是 {{ref}} 字符串）→ 引擎 {@code [{name, value}]}。
     */
    static List<Map<String, Object>> normalizeToolInputs(Object rawInputs) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(rawInputs instanceof List<?> inputs)) {
            return out;
        }
        for (Object raw : inputs) {
            if (!(raw instanceof Map<?, ?> item)) {
                continue;
            }
            Map<String, Object> input = asStringMap(item);
            String name = str(input.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("value", input.get("value"));
            out.add(entry);
        }
        return out;
    }

    /** 边归一化：编辑器边字段与引擎一致（source/target/sourceHandle），仅做防御性拷贝。 */
    private static Map<String, Object> normalizeEdge(Map<String, Object> edge) {
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("source", str(edge.get("source")));
        conn.put("target", str(edge.get("target")));
        if (edge.get("sourceHandle") != null) {
            conn.put("sourceHandle", str(edge.get("sourceHandle")));
        }
        return conn;
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source,
                                      String fromKey, String toKey) {
        if (source.get(fromKey) instanceof Collection<?> col && !col.isEmpty()) {
            target.put(toKey, source.get(fromKey));
        } else if (source.get(fromKey) != null) {
            target.put(toKey, source.get(fromKey));
        }
    }

    private static String firstNonBlank(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate != null) {
                String s = String.valueOf(candidate);
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
