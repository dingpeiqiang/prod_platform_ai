package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.OntologyExplainTool;
import com.sitech.prodai.service.agent.tool.RuleExplainTool;
import com.sitech.prodai.service.agent.tool.SparqlQueryTool;
import com.sitech.prodai.service.agent.tool.SwrlRiskAuditTool;
import com.sitech.prodai.service.agent.tool.SwrlRootCauseTool;
import com.sitech.prodai.service.agent.tool.ToolContractValidator;
import com.sitech.prodai.service.agent.tool.ToolOutputField;
import com.sitech.prodai.service.agent.tool.ToolOutputRenderer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具自描述契约测试（设计文档「工具执行结果 Schema 契约」的契约测试落地）。
 * <p>
 * 验证每个工具：业务标签非空、输出字段契约合法（无重复名 / 业务实体成对）、
 * 通用渲染器能依据契约正确提取摘要 / 结论 / 计数 / 业务实体。
 */
class AgentToolContractTest {

    private final List<AgentTool> tools = List.of(
            new SparqlQueryTool(null),
            new SwrlRootCauseTool(null),
            new SwrlRiskAuditTool(null),
            new RuleExplainTool(null),
            new OntologyExplainTool(null)
    );

    @Test
    void allToolsDeclareNonBlankLabels() {
        for (AgentTool tool : tools) {
            assertNotNull(tool.getLabel(), tool.getName() + " 未声明业务标签");
            assertFalse(tool.getLabel().isBlank(), tool.getName() + " 业务标签为空");
        }
    }

    @Test
    void allToolsPassContractValidation() {
        for (AgentTool tool : tools) {
            List<String> problems = ToolContractValidator.validate(tool);
            assertTrue(problems.isEmpty(), tool.getName() + " 契约问题: " + problems);
        }
    }

    @Test
    void outputFieldNamesAreUniqueWithinTool() {
        for (AgentTool tool : tools) {
            List<String> names = tool.getOutputFields().stream()
                    .map(ToolOutputField::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
            assertEquals(names.size(), names.stream().distinct().count(),
                    tool.getName() + " 存在重复输出字段名");
        }
    }

    @Test
    void rendererExtractsRootCauseBusinessEntity() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("offeringId", "PKG001");
        data.put("offeringName", "家庭融合畅享128");
        data.put("paths", List.of(1, 2, 3));
        data.put("message", "已确认异动，但未命中归因规则");

        SwrlRootCauseTool tool = new SwrlRootCauseTool(null);
        Map<String, Object> entity = ToolOutputRenderer.businessEntity(tool, data);
        assertEquals("PKG001", entity.get("id"));
        assertEquals("家庭融合畅享128", entity.get("name"));

        // 依据声明的实体名 + 计数生成的粗粒度摘要
        String summary = ToolOutputRenderer.summary(tool, data);
        assertTrue(summary.contains("家庭融合畅享128"));
        assertTrue(summary.contains("3"));

        // 下发前端 output 对象需兼容既有契约：offeringName / pathCount / remark
        Map<String, Object> out = ToolOutputRenderer.outputEntries(tool, data);
        assertEquals("家庭融合畅享128", String.valueOf(out.get("offeringName")));
        assertEquals(3L, ((Number) out.get("pathCount")).longValue());
        assertEquals("已确认异动，但未命中归因规则", String.valueOf(out.get("remark")));
    }

    @Test
    void rendererExtractsRiskAuditCounts() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", 12);
        data.put("highCount", 3);
        data.put("scannedCount", 100);

        SwrlRiskAuditTool tool = new SwrlRiskAuditTool(null);
        List<Map<String, Object>> counts = ToolOutputRenderer.counts(tool, data);
        Map<String, Long> byName = new LinkedHashMap<>();
        for (Map<String, Object> c : counts) {
            byName.put(String.valueOf(c.get("name")), ((Number) c.get("value")).longValue());
        }
        assertEquals(3L, byName.get("highCount"));
        assertEquals(12L, byName.get("total"));
    }

    @Test
    void rendererFallsBackToLegacySummaryKeys() {
        // 未声明契约的旧工具行为保持兼容：nl_answer / answer
        AgentTool legacyTool = new AgentTool() {
            @Override
            public String getName() {
                return "legacy";
            }

            @Override
            public String getDescription() {
                return "legacy";
            }

            @Override
            public com.sitech.prodai.service.agent.model.ExecutionResult execute(Map<String, Object> params) {
                return null;
            }
        };
        Map<String, Object> data = Map.of("nl_answer", "共查询到 5 条记录");
        assertEquals("共查询到 5 条记录", ToolOutputRenderer.summary(legacyTool, data));
    }
}
