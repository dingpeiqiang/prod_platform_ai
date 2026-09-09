package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 归因评估审计查询工具（方案 §6-B5 拆分项）单元测试：
 * SPI 自声明、缺省 traceId 兜底（agent-trace）、输出透出 trace_id 与引用规则、
 * 服务失败不冒充、描述与 ontology_explain 职责分离。
 */
@ExtendWith(MockitoExtension.class)
class AttributionQueryToolTest {

    @Mock
    private OntologyService ontologyService;

    private AttributionQueryTool tool;

    @BeforeEach
    void setUp() {
        tool = new AttributionQueryTool(ontologyService);
    }

    @Test
    void spiDeclaresScenesAndSeparationFromConceptExplain() {
        assertEquals("attribution_query", tool.getName());
        assertTrue(tool.getScenes().contains("query"));
        assertTrue(tool.getScenes().contains("ops"));
        assertTrue(tool.getDescription().contains("ontology_explain"),
                "描述须声明概念解释走 ontology_explain（职责分离）");
        assertFalse(tool.getHandoffs().isEmpty());
    }

    @Test
    void defaultsToAgentTraceAndExplains() {
        when(ontologyService.explain(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "success", true,
                        "natural_language", "已根据业务事实与规则完成归因评估。引用规则：R-A01",
                        "referenced_rules", List.of("R-A01", "R-A01", "R-A02")));

        ExecutionResult result = tool.execute(Map.of());

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertEquals("agent-trace", data.get("trace_id"), "缺省轨迹 ID 兜底 agent-trace");
        assertEquals(List.of("R-A01", "R-A01", "R-A02"), data.get("referenced_rules"),
                "引用规则原样透出服务端结果（去重职责在服务端 explain）");
        assertTrue(String.valueOf(data.get("natural_language")).contains("R-A01"));
    }

    @Test
    void passesTraceIdAndAudienceThrough() {
        when(ontologyService.explain("trace-123", "audit", "agent"))
                .thenReturn(Map.of("success", true, "natural_language", "明细流水", "referenced_rules", List.of()));

        ExecutionResult result = tool.execute(Map.of("trace_id", "trace-123", "audience", "audit"));

        assertTrue(result.isSuccess());
        assertEquals("trace-123", result.getData().get("trace_id"));
        assertEquals("明细流水", result.getData().get("natural_language"));
    }

    @Test
    void nullServiceResultFailsWithoutFakeData() {
        when(ontologyService.explain(anyString(), anyString(), anyString())).thenReturn(null);

        ExecutionResult result = tool.execute(Map.of("trace_id", "missing"));

        assertFalse(result.isSuccess(), "服务无结果必须拒绝，不冒充空流水");
    }

    @Test
    void referencedRulesFallsBackToEmptyListWhenNotList() {
        when(ontologyService.explain(anyString(), anyString(), anyString()))
                .thenReturn(Map.of("success", true, "natural_language", "x", "referenced_rules", "not-a-list"));

        ExecutionResult result = tool.execute(Map.of());

        assertTrue(result.isSuccess());
        assertEquals(List.of(), result.getData().get("referenced_rules"), "非列表引用规则兜底空列表");
    }
}
