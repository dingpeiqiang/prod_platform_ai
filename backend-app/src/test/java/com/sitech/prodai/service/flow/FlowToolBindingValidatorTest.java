package com.sitech.prodai.service.flow;

import com.sitech.prodai.service.ToolExecutionService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolOutputField;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G2/G3 发布期工具守门测试（《产商品研发助手改造方案》§3.1 验收）：
 * 非法 toolName / outputParams 契约外字段在发布期被拒，运行期 0 配置类失败。
 */
class FlowToolBindingValidatorTest {

    private final FlowDefinitionValidator validator = new FlowDefinitionValidator(new StubToolExecutionService());

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

    private Map<String, Object> toolNode(String id, String toolName, List<Map<String, Object>> outputParams) {
        Map<String, Object> node = node(id, "flow.tool");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("toolName", toolName);
        if (outputParams != null) {
            params.put("outputParams", outputParams);
        }
        node.put("action_params", params);
        return node;
    }

    @Test
    void unknownToolRejectedAtPublishTime() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), toolNode("t", "ghost_tool", null), node("e", "flow.end")),
                List.of(conn("s", "t"), conn("t", "e"))));
        assertFalse(result.valid(), "未注册工具应在发布期被拒: " + result.problems());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("未注册") && p.contains("ghost_tool")),
                () -> "报错应指明工具名: " + result.problems());
    }

    @Test
    void knownToolPasses() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), toolNode("t", "sparql_query", null), node("e", "flow.end")),
                List.of(conn("s", "t"), conn("t", "e"))));
        assertTrue(result.valid(), () -> "已注册工具应通过: " + result.problems());
    }

    @Test
    void outputParamOutsideContractRejected() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), toolNode("t", "sparql_query",
                        List.of(Map.of("name", "x", "source", "ghost_field"))), node("e", "flow.end")),
                List.of(conn("s", "t"), conn("t", "e"))));
        assertFalse(result.valid(), "契约外字段应被拒: " + result.problems());
        assertTrue(result.problems().stream().anyMatch(p -> p.contains("输出契约外的字段")),
                () -> "报错应指明契约: " + result.problems());
    }

    @Test
    void outputParamInsideContractPasses() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), toolNode("t", "sparql_query",
                        List.of(Map.of("name", "rows", "source", "rows"),
                                Map.of("name", "whole", "source", "response"))), node("e", "flow.end")),
                List.of(conn("s", "t"), conn("t", "e"))));
        assertTrue(result.valid(), () -> "契约内字段与 response 透传应通过: " + result.problems());
    }

    @Test
    void dottedSourceChecksHeadSegment() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), toolNode("t", "sparql_query",
                        List.of(Map.of("name", "x", "source", "rows.items"))), node("e", "flow.end")),
                List.of(conn("s", "t"), conn("t", "e"))));
        assertTrue(result.valid(), () -> "嵌套路径首段命中契约即通过: " + result.problems());
    }

    @Test
    void toolWithoutContractSkipsOutputCheck() {
        FlowDefinitionValidator.ValidationResult result = validator.validate(definition(
                List.of(node("s", "flow.start"), toolNode("t", "no_contract_tool",
                        List.of(Map.of("name", "x", "source", "anything"))), node("e", "flow.end")),
                List.of(conn("s", "t"), conn("t", "e"))));
        assertTrue(result.valid(), () -> "未声明输出契约的旧工具应跳过 G3: " + result.problems());
    }

    /** 桩注册表：两个工具（sparql_query 声明契约 rows；no_contract_tool 无契约），其余视为未注册。 */
    private static class StubToolExecutionService extends ToolExecutionService {
        StubToolExecutionService() {
            super(List.of(new StubTool("sparql_query", List.of("rows")),
                    new StubTool("no_contract_tool", List.of())));
        }
    }

    private record StubTool(String name, List<String> fields) implements AgentTool {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "stub";
        }

        @Override
        public List<ToolOutputField> getOutputFields() {
            return fields.stream()
                    .map(f -> ToolOutputField.builder(f, ToolOutputField.Role.OTHER).build())
                    .toList();
        }

        @Override
        public ExecutionResult execute(Map<String, Object> params) {
            return ExecutionResult.fail(getName(), "stub 不执行");
        }
    }
}
