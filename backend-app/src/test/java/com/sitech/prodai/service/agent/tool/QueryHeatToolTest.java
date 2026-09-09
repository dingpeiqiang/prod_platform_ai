package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.queryheat.QueryHeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 查询热度分析工具（方案 §6-C4）单元测试：
 * SPI 自声明（scenes=ops 运营专属 + ITEMS 契约）、聚合结果透出、
 * 空态如实拒绝不冒充、聚合失败降级、摘要串述高频要点。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryHeatToolTest {

    @Mock
    private QueryHeatService queryHeatService;

    private QueryHeatTool tool;

    @BeforeEach
    void setUp() {
        tool = new QueryHeatTool(queryHeatService);
    }

    private Map<String, Object> okHeat() {
        Map<String, Object> heat = new LinkedHashMap<>();
        heat.put("success", true);
        heat.put("total_questions", 5);
        heat.put("top_questions", List.of(
                Map.of("question", "查一下129的融合套餐", "count", 3L),
                Map.of("question", "5G套餐有什么权益", "count", 2L)));
        heat.put("top_keywords", List.of(Map.of("keyword", "129", "count", 2L)));
        heat.put("daily_counts", List.of(Map.of("date", "2026-09-09", "count", 5L)));
        heat.put("sources", Map.of("data_source", "pd_ai_chat_messages(role=user)"));
        return heat;
    }

    @Test
    void spiDeclaresOpsOnlySceneAndContracts() {
        assertEquals("query_heat", tool.getName());
        assertTrue(tool.getScenes().contains("ops"), "运营视角专属");
        assertFalse(tool.getScenes().contains("query"), "query 场景不开放（用户无需看到自己贡献的热度）");
        assertFalse(tool.getHandoffs().isEmpty(), "声明典型业务链（热度→指标核查→商品全景）");
        assertTrue(tool.getOutputFields().stream().anyMatch(f -> "top_questions".equals(f.getName())),
                "高频问题为 ITEMS 契约字段");
        assertTrue(tool.getOutputFields().stream().anyMatch(f -> "total_questions".equals(f.getName())),
                "问题总量为 COUNT 契约字段");
    }

    @Test
    void passesLimitThroughToService() {
        when(queryHeatService.queryHeat(eq(20), any())).thenReturn(okHeat());

        ExecutionResult result = tool.execute(Map.of("limit", 20));

        assertTrue(result.isSuccess());
    }

    @Test
    void exposesAggregatedHeatData() {
        when(queryHeatService.queryHeat(any(), any())).thenReturn(okHeat());

        ExecutionResult result = tool.execute(Map.of());

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertEquals(5, data.get("total_questions"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) data.get("top_questions");
        assertEquals(2, top.size());
        assertEquals("查一下129的融合套餐", top.get(0).get("question"));
        assertTrue(String.valueOf(data.get("nl_answer")).contains("129"),
                "摘要串述高频问题要点");
        assertTrue(String.valueOf(data.get("nl_answer")).contains("3 次"),
                "摘要含出现次数");
    }

    @Test
    void failsWhenNoQuestionData() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("success", true);
        empty.put("total_questions", 0);
        empty.put("top_questions", List.of());
        when(queryHeatService.queryHeat(any(), any())).thenReturn(empty);

        ExecutionResult result = tool.execute(Map.of());

        assertFalse(result.isSuccess(), "无历史会话数据 → 如实拒绝（不冒充零热度结论）");
        assertTrue(result.getErrorMessage().contains("暂无查询记录"));
    }

    @Test
    void failsWhenServiceReportsUnavailable() {
        when(queryHeatService.queryHeat(any(), any()))
                .thenReturn(Map.of("success", false, "message", "消息存储未装配（内存形态），查询热度不可用"));

        ExecutionResult result = tool.execute(Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未装配"));
    }
}
