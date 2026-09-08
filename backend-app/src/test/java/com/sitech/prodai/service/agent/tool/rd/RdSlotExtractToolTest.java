package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.service.ProductExtractionTemplateSupport;
import com.sitech.prodai.service.ProductTemplateRegistry;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.ops.OpsExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * rd_slot_extract 业务参数抽取原子工具单测（智聊手册环节①）：
 * 槽位透传与缺失要素判定（模板 required_slots 声明优先，未声明回落缺省口径）。
 */
@ExtendWith(MockitoExtension.class)
class RdSlotExtractToolTest {

    @Mock
    private OpsExtractionService extractionService;
    @Mock
    private ProductExtractionTemplateSupport templateSupport;
    @Mock
    private ProductTemplateRegistry templateRegistry;

    private RdSlotExtractTool tool;

    @BeforeEach
    void setUp() {
        tool = new RdSlotExtractTool(extractionService, templateSupport, templateRegistry);
        lenient().when(templateSupport.matchCategory(anyString())).thenReturn(null);
    }

    @Test
    void extractsSlotsAndReportsEngineAndCount() {
        when(extractionService.extractSlots("月费59的校园套餐"))
                .thenReturn(new OpsExtractionService.SlotExtractResult(
                        Map.of("monthlyFee", 59, "bizScenario", "校园体验"), "llm"));

        ExecutionResult result = tool.execute(Map.of("text", "月费59的校园套餐"));

        assertTrue(result.isSuccess(), () -> "应成功: " + result.getErrorMessage());
        Map<?, ?> slots = (Map<?, ?>) result.getData().get("slots");
        assertEquals(59, ((Number) slots.get("monthlyFee")).intValue());
        assertEquals("llm", result.getData().get("slot_engine"));
        assertEquals(2, ((Number) result.getData().get("slot_count")).intValue());
        // 未声明模板 → 缺省口径（monthlyFee/targetUser）；monthlyFee 已抽，缺 targetUser
        assertEquals(List.of("targetUser"), result.getData().get("missing_slots"));
        assertTrue(String.valueOf(result.getData().get("nl_answer")).contains("话术未提及：targetUser"),
                "缺要素应不静默: " + result.getData().get("nl_answer"));
    }

    @Test
    void templateRequiredSlotsDeclaredInTemplateWin() {
        when(extractionService.extractSlots("做一个20元流量包"))
                .thenReturn(new OpsExtractionService.SlotExtractResult(
                        Map.of("monthlyFee", 20), "regex-fast"));
        // 模板声明 required_slots=两个键，判定按声明（缺省口径不掺入）
        when(templateSupport.requiredSlots("personAddPrc"))
                .thenReturn(java.util.Set.of("monthlyFee", "includeData"));
        lenient().when(templateSupport.matchCategory(anyString())).thenReturn("personAddPrc");

        ExecutionResult result = tool.execute(Map.of("text", "做一个20元流量包", "category_code", "personAddPrc"));

        assertTrue(result.isSuccess());
        assertEquals(List.of("includeData"), result.getData().get("missing_slots"),
                "缺要素按模板 required_slots 声明判定");
    }

    @Test
    void noSlotsExtractedAsksForMoreInfo() {
        when(extractionService.extractSlots("帮我配个套餐")).thenReturn(
                new OpsExtractionService.SlotExtractResult(Map.of(), "regex"));

        ExecutionResult result = tool.execute(Map.of("text", "帮我配个套餐"));

        assertTrue(result.isSuccess());
        assertTrue(String.valueOf(result.getData().get("nl_answer")).contains("请补充"),
                "零命中应提示补充关键信息");
    }

    @Test
    void missingTextFails() {
        ExecutionResult result = tool.execute(Map.of());
        assertTrue(!result.isSuccess(), "缺话术应失败");
    }
}
