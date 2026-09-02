package com.sitech.prodai.service.flow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编辑器定义归一化测试（P3-1a2 验收）：
 * 编辑器 VueFlow 形态 → 引擎 action/action_params 形态，归一化后可过守门。
 */
class EditorDefinitionNormalizerTest {

    private Map<String, Object> editorNode(String id, String type, Map<String, Object> data) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("position", Map.of("x", 1, "y", 2));
        node.put("data", data);
        return node;
    }

    private Map<String, Object> edge(String source, String target, String handle) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", "edge-" + source + "-" + target);
        edge.put("source", source);
        edge.put("target", target);
        if (handle != null) {
            edge.put("sourceHandle", handle);
        }
        return edge;
    }

    @Test
    void needsNormalizeDetectsEditorShape() {
        Map<String, Object> editorDef = Map.of(
                "nodes", List.of(editorNode("s", "start", new LinkedHashMap<>())),
                "edges", List.of());
        assertTrue(EditorDefinitionNormalizer.needsNormalize(editorDef));

        Map<String, Object> engineDef = Map.of(
                "nodes", List.of(Map.of("id", "s", "action", "flow.start", "action_params", Map.of())),
                "connections", List.of());
        assertFalse(EditorDefinitionNormalizer.needsNormalize(engineDef));
        assertFalse(EditorDefinitionNormalizer.needsNormalize(null));
    }

    @Test
    void linearToolFlowNormalizesAndPassesValidator() {
        Map<String, Object> toolData = new LinkedHashMap<>();
        toolData.put("label", "查询工具");
        toolData.put("tool_type", "sparql_query");
        toolData.put("tool_name", "sparql_query");
        toolData.put("inputParams", List.of(
                Map.of("name", "q", "sourceType", "input", "value", "收入"),
                Map.of("name", "entityId", "sourceType", "ref", "value", "{{s.output.input}}")));
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("nodes", List.of(
                editorNode("s", "start", Map.of("label", "开始", "parameters", List.of(
                        Map.of("name", "input", "type", "string", "default", "", "required", true)))),
                editorNode("t", "tool", toolData),
                editorNode("e", "end", Map.of("label", "结束"))));
        def.put("edges", List.of(edge("s", "t", null), edge("t", "e", null)));

        Map<String, Object> normalized = EditorDefinitionNormalizer.normalize(def);
        FlowDefinitionValidator.ValidationResult check = new FlowDefinitionValidator().validate(normalized);
        assertTrue(check.valid(), () -> "归一化后应过守门: " + check.problems());

        // 形态断言：action/toolName/变量引用直传
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) normalized.get("nodes");
        assertEquals("flow.start", nodes.get(0).get("action"));
        assertEquals("flow.tool", nodes.get(1).get("action"));
        @SuppressWarnings("unchecked")
        Map<String, Object> toolParams = (Map<String, Object>) nodes.get(1).get("action_params");
        assertEquals("sparql_query", toolParams.get("toolName"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputParams = (List<Map<String, Object>>) toolParams.get("inputParams");
        assertEquals("收入", inputParams.get(0).get("value"));
        assertEquals("{{s.output.input}}", inputParams.get(1).get("value"));
        // connections 由 edges 生成
        assertTrue(normalized.get("connections") instanceof List<?> conns && ((List<?>) conns).size() == 2);
        // 原定义不被修改
        assertTrue(def.containsKey("edges"));
        assertFalse(def.containsKey("connections"));
    }

    @Test
    void conditionBranchesNormalizeToSpelWithDefault() {
        Map<String, Object> condData = new LinkedHashMap<>();
        condData.put("label", "判断风险");
        condData.put("branches", List.of(
                Map.of("type", "if", "handle", "branch_abc", "conditions", List.of(
                        Map.of("variable", "t1.output.riskLevel", "operator", "==",
                                "valueType", "input", "value", "HIGH"))),
                Map.of("type", "else_if", "handle", "branch_def", "conditions", List.of(
                        Map.of("variable", "t1.output.riskLevel", "operator", "contains",
                                "valueType", "input", "value", "MEDIUM"))),
                Map.of("type", "else", "handle", "branch_else", "conditions", List.of())));
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("nodes", List.of(
                editorNode("s", "start", Map.of("label", "开始")),
                editorNode("c1", "condition", condData),
                editorNode("e", "end", Map.of("label", "结束"))));
        def.put("edges", List.of(
                edge("s", "c1", null), edge("c1", "e", "branch_abc"), edge("c1", "e", "branch_else")));

        Map<String, Object> normalized = EditorDefinitionNormalizer.normalize(def);
        FlowDefinitionValidator.ValidationResult check = new FlowDefinitionValidator().validate(normalized);
        assertTrue(check.valid(), () -> "condition 归一化后应过守门: " + check.problems());

        @SuppressWarnings("unchecked")
        Map<String, Object> condParams = (Map<String, Object>) ((List<Map<String, Object>>) normalized.get("nodes"))
                .get(1).get("action_params");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) condParams.get("branches");
        assertEquals(3, branches.size());
        assertEquals("branch_abc", branches.get(0).get("id"));
        assertEquals("${t1.output.riskLevel} == 'HIGH'", branches.get(0).get("expression"));
        assertEquals("${t1.output.riskLevel}?.toString().contains('MEDIUM')", branches.get(1).get("expression"));
        assertEquals("default", branches.get(2).get("expression"));
    }

    @Test
    void llmHttpHumanNodesNormalize() {
        Map<String, Object> llmData = new LinkedHashMap<>();
        llmData.put("label", "总结");
        llmData.put("model", "qwen-plus");
        llmData.put("temperature", 0.7);
        llmData.put("prompt", "请总结 {{s.output.input}}");
        llmData.put("inputParams", List.of(
                Map.of("name", "topic", "valueType", "reference", "refValue", "{{s.output.input}}"),
                Map.of("name", "limit", "valueType", "input", "defaultValue", "10")));

        Map<String, Object> httpData = new LinkedHashMap<>();
        httpData.put("label", "查询");
        httpData.put("httpMethod", "GET");
        httpData.put("httpUrl", "https://api.example.com/data");

        Map<String, Object> humanData = new LinkedHashMap<>();
        humanData.put("label", "人工确认");
        humanData.put("prompt", "请确认处理结果");

        Map<String, Object> def = new LinkedHashMap<>();
        def.put("nodes", List.of(
                editorNode("s", "start", Map.of("label", "开始")),
                editorNode("l1", "llm", llmData),
                editorNode("h1", "http", httpData),
                editorNode("u1", "userInput", humanData),
                editorNode("e", "end", Map.of("label", "结束"))));
        def.put("edges", List.of(
                edge("s", "l1", null), edge("l1", "h1", null),
                edge("h1", "u1", null), edge("u1", "e", null)));

        Map<String, Object> normalized = EditorDefinitionNormalizer.normalize(def);
        FlowDefinitionValidator.ValidationResult check = new FlowDefinitionValidator().validate(normalized);
        assertTrue(check.valid(), () -> "llm/http/human 归一化后应过守门: " + check.problems());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) normalized.get("nodes");
        @SuppressWarnings("unchecked")
        Map<String, Object> llmParams = (Map<String, Object>) nodes.get(1).get("action_params");
        assertEquals("flow.llm", nodes.get(1).get("action"));
        assertEquals("请总结 {{s.output.input}}", llmParams.get("prompt"));
        assertEquals(0.7, ((Number) llmParams.get("temperature")).doubleValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> llmInputs = (List<Map<String, Object>>) llmParams.get("inputParams");
        assertEquals("{{s.output.input}}", llmInputs.get(0).get("value"));
        assertEquals("10", llmInputs.get(1).get("value"));

        @SuppressWarnings("unchecked")
        Map<String, Object> httpParams = (Map<String, Object>) nodes.get(2).get("action_params");
        assertEquals("flow.http", nodes.get(2).get("action"));
        assertEquals("https://api.example.com/data", httpParams.get("url"));
        assertEquals("GET", httpParams.get("method"));

        @SuppressWarnings("unchecked")
        Map<String, Object> humanParams = (Map<String, Object>) nodes.get(3).get("action_params");
        assertEquals("flow.human", nodes.get(3).get("action"));
        assertEquals("请确认处理结果", humanParams.get("prompt"));
    }

    @Test
    void unsupportedEditorTypesPreservedAndRejectedByValidator() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("nodes", List.of(
                editorNode("s", "start", Map.of("label", "开始")),
                editorNode("k", "knowledgeBase", Map.of("label", "知识库")),
                editorNode("e", "end", Map.of("label", "结束"))));
        def.put("edges", List.of(edge("s", "k", null), edge("k", "e", null)));

        Map<String, Object> normalized = EditorDefinitionNormalizer.normalize(def);
        // 归一化不吞错误：未映射类型保留原样，由守门拒绝
        assertFalse(new FlowDefinitionValidator().validate(normalized).valid());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) normalized.get("nodes");
        assertEquals("knowledgeBase", nodes.get(1).get("type"));
        assertFalse(nodes.get(1).containsKey("action"));
    }

    @Test
    void conditionOperatorsTranslateToSpel() {
        List<Map<String, Object>> cases = List.of(
                Map.of("variable", "a.output.x", "operator", ">", "valueType", "input", "value", "100"),
                Map.of("variable", "a.output.x", "operator", "starts_with", "valueType", "input", "value", "OFF"),
                Map.of("variable", "a.output.x", "operator", "is_empty", "valueType", "input", "value", ""),
                Map.of("variable", "a.output.x", "operator", "not_empty", "valueType", "input", "value", ""),
                Map.of("variable", "a.output.x", "operator", "==", "valueType", "reference", "value", "b.output.y"));
        for (int i = 0; i < cases.size(); i++) {
            List<Map<String, Object>> branchResult = EditorDefinitionNormalizer.normalizeBranches(
                    List.of(Map.of("type", "if", "handle", "h" + i, "conditions", List.of(cases.get(i)))));
            String expression = String.valueOf(branchResult.get(0).get("expression"));
            switch (String.valueOf(cases.get(i).get("operator"))) {
                case ">" -> assertEquals("${a.output.x} > 100", expression);
                case "starts_with" -> assertEquals("${a.output.x}?.toString().startsWith('OFF')", expression);
                case "is_empty" -> assertEquals("(${a.output.x} == null or ${a.output.x}?.toString().isEmpty() == true)", expression);
                case "not_empty" -> assertEquals("(${a.output.x} != null and ${a.output.x}?.toString().isEmpty() == false)", expression);
                case "==" -> assertEquals("${a.output.x} == ${b.output.y}", expression);
                default -> throw new IllegalStateException("未覆盖的操作符: " + i);
            }
        }
    }

    @Test
    void emptyConditionsTreatedAsTrue() {
        List<Map<String, Object>> result = EditorDefinitionNormalizer.normalizeBranches(
                List.of(Map.of("type", "if", "handle", "h", "conditions", new ArrayList<>())));
        assertEquals("true", result.get(0).get("expression"));
    }
}
