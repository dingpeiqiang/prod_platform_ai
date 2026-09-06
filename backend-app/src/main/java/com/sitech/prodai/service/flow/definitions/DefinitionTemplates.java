package com.sitech.prodai.service.flow.definitions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景工作流定义构建 helper（W3-2）：收敛 Map 组装样板，保证定义类只表达"图"本身。
 * 节点/连接契约对齐 {@code FlowDefinitionValidator} 与《工作流配置规范》。
 */
final class DefinitionTemplates {

    private DefinitionTemplates() {
    }

    /** 定义骨架：id/name/version + nodes + connections。 */
    static Map<String, Object> definition(String code, String name,
                                          List<Map<String, Object>> nodes,
                                          List<Map<String, Object>> connections) {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("id", code);
        def.put("name", name);
        def.put("version", "1.0.0");
        def.put("nodes", nodes);
        def.put("connections", connections);
        return def;
    }

    /** 节点：id + action + 展示名 + action_params。 */
    static Map<String, Object> node(String id, String action, String name, Map<String, Object> params) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("action", action);
        node.put("name", name);
        node.put("action_params", params);
        return node;
    }

    /** 默认出边。 */
    static Map<String, Object> edge(String source, String target) {
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("source", source);
        conn.put("target", target);
        return conn;
    }

    /** condition 分支出边（sourceHandle = 分支 id）。 */
    static Map<String, Object> branchEdge(String source, String branchId, String target) {
        Map<String, Object> conn = edge(source, target);
        conn.put("sourceHandle", branchId);
        return conn;
    }

    /** inputParams 项：{name, value}，value 支持 {{ref}}。 */
    static Map<String, Object> input(String name, Object value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    /** outputParams 项：{name, source}，source 为工具输出契约内的点分路径。 */
    static Map<String, Object> output(String name, String source) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("source", source);
        return item;
    }

    /** condition 分支项：{id, expression, label}；expression="default" 为兜底分支。 */
    static Map<String, Object> branch(String id, String expression, String label) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("expression", expression);
        item.put("label", label);
        return item;
    }

    /** 可变 List 收集器（保持声明顺序，避免 List.of 长度上限）。 */
    static <T> List<T> listOf(T... items) {
        return new ArrayList<>(List.of(items));
    }
}
