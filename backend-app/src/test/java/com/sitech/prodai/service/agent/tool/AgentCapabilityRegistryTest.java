package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.tool.flow.FlowExecuteTool;
import com.sitech.prodai.service.agent.tool.rd.RdConfigChatTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 能力注册表（单源）测试：场景 → 可见工具白名单的守门语义。
 * <p>
 * 验证：工具自声明场景（getScenes）→ 注册表索引构建正确；
 * 场景白名单隔离（rd/ops 互不可见，跨场景工具双向可见）；
 * 空白/未知场景回落运营场景；未注册工具不可见。
 */
class AgentCapabilityRegistryTest {

    private AgentCapabilityRegistry registry() {
        return new AgentCapabilityRegistry(List.of(
                new SparqlQueryTool(null),
                new RdConfigChatTool(null, null),
                new FlowExecuteTool(null)
        ));
    }

    @Test
    void sceneIndexBuiltFromToolSelfDeclaration() {
        AgentCapabilityRegistry r = registry();

        assertEquals(List.of("sparql_query", "flow_execute"),
                r.toolsOf("ops").stream().map(AgentTool::getName).toList());
        assertEquals(List.of("rd_config_chat", "flow_execute"),
                r.toolsOf("rd").stream().map(AgentTool::getName).toList());
        assertEquals(Set.of("ops", "rd"), r.scenesOf("flow_execute"));
    }

    @Test
    void visibilityGateIsolatesScenes() {
        AgentCapabilityRegistry r = registry();

        assertTrue(r.isVisible("sparql_query", "ops"));
        assertTrue(r.isVisible("flow_execute", "ops"));
        assertFalse(r.isVisible("rd_config_chat", "ops"), "rd 工具不应对 ops 场景可见");
        assertTrue(r.isVisible("rd_config_chat", "rd"));
        assertFalse(r.isVisible("sparql_query", "rd"), "ops 工具不应对 rd 场景可见");
        assertFalse(r.isVisible("fabricated_tool", "rd"), "编造工具名不可见");
        assertFalse(r.isVisible(null, "rd"));
        assertFalse(r.isVisible("", "rd"));
    }

    @Test
    void blankSceneFallsBackToOpsUnknownSceneIsEmpty() {
        AgentCapabilityRegistry r = registry();

        // 空白场景回落运营场景
        assertEquals(r.toolsOf("ops"), r.toolsOf(null));
        assertEquals(r.toolsOf("ops"), r.toolsOf(""));
        assertTrue(r.isVisible("sparql_query", null));
        assertFalse(r.isVisible("rd_config_chat", null));

        // 未知场景严格守门：返回空清单（防 ops 工具泄漏给未声明的任意场景）
        assertEquals(List.of(), r.toolsOf("unknown_scene"));
        assertFalse(r.isVisible("sparql_query", "unknown_scene"));
    }

    @Test
    void undeclaredSceneToolIsInvisibleEverywhere() {
        AgentTool undeclared = new AgentTool() {
            @Override
            public String getName() {
                return "silent_tool";
            }

            @Override
            public String getDescription() {
                return "未声明场景的工具";
            }

            @Override
            public ExecutionResult execute(Map<String, Object> params) {
                return ExecutionResult.fail(getName(), "not implemented");
            }
        };
        AgentCapabilityRegistry r = new AgentCapabilityRegistry(List.of(undeclared));

        assertEquals(List.of(), r.toolsOf("ops"));
        assertEquals(List.of(), r.toolsOf("rd"));
        assertFalse(r.isVisible("silent_tool", "ops"));
        assertEquals(Set.of(), r.scenesOf("silent_tool"));
    }

    @Test
    void belongsToSceneMatchesVisibility() {
        AgentCapabilityRegistry r = registry();

        assertTrue(r.belongsToScene("rd_config_chat", "rd"));
        assertFalse(r.belongsToScene("rd_config_chat", "ops"));
        assertTrue(r.belongsToScene("flow_execute", "rd"));
        assertTrue(r.belongsToScene("flow_execute", "ops"));
    }
}
