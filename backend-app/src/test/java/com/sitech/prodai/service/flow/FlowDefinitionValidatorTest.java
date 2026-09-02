package com.sitech.prodai.service.flow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程定义期守门测试（设计文档 §8-P2-1 验收：非法定义全拒）。
 */
class FlowDefinitionValidatorTest {

    private final FlowDefinitionValidator validator = new FlowDefinitionValidator();

    private Map<String, Object> node(String id, String action) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("action", action);
        node.put("action_params", new LinkedHashMap<>());
        return node;
    }

    private Map<String, Object> conn(String source, String target) {
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("source", source);
        conn.put("target", target);
        return conn;
    }

    private Map<String, Object> definition(List<Map<String, Object>> nodes, List<Map<String, Object>> connections) {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("nodes", nodes);
        def.put("connections", connections);
        return def;
    }

    @Test
    void validLinearFlowPasses() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), node("t", "flow.tool"), node("e", "flow.end")),
                List.of(conn("s", "t"), conn("t", "e"))));
        assertTrue(result.valid(), () -> "合法线性流程应通过: " + result.problems());
    }

    @Test
    void cyclicFlowRejected() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), node("a", "flow.tool"), node("b", "flow.tool"), node("e", "flow.end")),
                List.of(conn("s", "a"), conn("a", "b"), conn("b", "a"), conn("b", "e"))));
        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("环")), "应检出环: " + result.problems());
    }

    @Test
    void conditionWithoutDefaultRejected() {
        Map<String, Object> cond = node("c", "flow.condition");
        Map<String, Object> branches = new LinkedHashMap<>();
        branches.put("branches", List.of(Map.of("id", "a", "expression", "${flow.x} == 1")));
        cond.put("action_params", branches);

        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), cond, node("e", "flow.end")),
                List.of(conn("s", "c"), conn("c", "e"))));
        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("default")), "缺 default 分支应被拒: " + result.problems());
    }

    @Test
    void conditionWithDisabledSyntaxRejected() {
        Map<String, Object> cond = node("c", "flow.condition");
        Map<String, Object> branches = new LinkedHashMap<>();
        branches.put("branches", List.of(
                Map.of("id", "a", "expression", "T(java.lang.Runtime) != null"),
                Map.of("id", "d", "expression", "default")));
        cond.put("action_params", branches);

        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), cond, node("e", "flow.end")),
                List.of(conn("s", "c"), conn("c", "e"))));
        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("禁用语法")), "SpEL 注入语法应被拒: " + result.problems());
    }

    @Test
    void unsupportedActionRejected() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), node("x", "workflow.execute_code"), node("e", "flow.end")),
                List.of(conn("s", "x"), conn("x", "e"))));
        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("不受引擎支持")), ": " + result.problems());
    }

    @Test
    void missingStartRejected() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("t", "flow.tool"), node("e", "flow.end")),
                List.of(conn("t", "e"))));
        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("flow.start")), ": " + result.problems());
    }

    @Test
    void danglingConnectionRejected() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), node("e", "flow.end")),
                List.of(conn("s", "e"), conn("e", "ghost"))));
        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("ghost")), ": " + result.problems());
    }

    @Test
    void unknownVariableReferenceRejected() {
        Map<String, Object> tool = node("t", "flow.tool");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("toolName", "sparql_query");
        params.put("inputParams", List.of(Map.of("name", "q", "value", "{{node-ghost.output.x}}")));
        tool.put("action_params", params);

        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), tool, node("e", "flow.end")),
                List.of(conn("s", "t"), conn("t", "e"))));
        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("node-ghost")), ": " + result.problems());
    }

    @Test
    void timeoutOutOfRangeRejected() {
        Map<String, Object> tool = node("t", "flow.tool");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("toolName", "sparql_query");
        params.put("timeoutMs", 999999);
        tool.put("action_params", params);

        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), tool, node("e", "flow.end")),
                List.of(conn("s", "t"), conn("t", "e"))));
        assertFalse(result.valid());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("timeoutMs")), ": " + result.problems());
    }

    @Test
    void emptyDefinitionRejected() {
        assertFalse(validator.validate(null).valid());
        assertFalse(validator.validate(new LinkedHashMap<>()).valid());
        Map<String, Object> emptyNodes = new LinkedHashMap<>();
        emptyNodes.put("nodes", new ArrayList<>());
        emptyNodes.put("connections", new ArrayList<>());
        assertFalse(validator.validate(emptyNodes).valid());
    }
}
