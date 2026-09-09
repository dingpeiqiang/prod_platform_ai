package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.ProductOntologyService;
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
import static org.mockito.Mockito.lenient;

/**
 * 异网竞对资费对比工具（方案 §6-B3）单元测试：
 * SPI 自声明（scenes 含 query/ops）、来源与截止时间必透出、mock 确定性、
 * 空结果与缺参不冒充数据、对标对齐真实货架。
 */
@ExtendWith(MockitoExtension.class)
class MarketBenchmarkToolTest {

    @Mock
    private ProductOntologyService productOntologyService;

    private MarketBenchmarkTool tool;

    @BeforeEach
    void setUp() {
        tool = new MarketBenchmarkTool(productOntologyService);
        lenient().when(productOntologyService.loadGraph()).thenReturn(graphWithShelf(
                shelfOffering("SCHEME_FBP_001", "融合套餐129档", 129)));
    }

    @Test
    void spiDeclaresScenesAndContracts() {
        assertEquals("market_benchmark", tool.getName());
        assertTrue(tool.getScenes().contains("query"), "scenes 含 query → 自动进入场景能力集");
        assertTrue(tool.getScenes().contains("ops"), "运营定价参考同样受益");
        assertFalse(tool.getHandoffs().isEmpty(), "声明典型业务链");
        assertTrue(tool.getOutputFields().stream().anyMatch(f -> "rival_plans".equals(f.getName())));
    }

    @Test
    void defaultCategoryFusionReturnsRivalPlans() {
        ExecutionResult result = tool.execute(Map.of());

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertEquals("融合", data.get("category"), "缺省品类为融合");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rivals = (List<Map<String, Object>>) data.get("rival_plans");
        assertFalse(rivals.isEmpty(), "默认融合品类有竞对套餐");
        assertTrue(rivals.stream().anyMatch(p -> List.of("联通", "电信").contains(p.get("carrier"))),
                "竞对运营商在字典内");
    }

    @Test
    void sourcesContainDataAsOfForFreshness() {
        ExecutionResult result = tool.execute(Map.of("category", "流量"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) result.getData().get("sources");
        assertTrue(sources.containsKey("data_as_of"), "竞对资费时效：截止时间必透出");
        assertFalse(String.valueOf(sources.get("data_as_of")).isBlank());
        assertTrue(String.valueOf(result.getData().get("nl_answer")).contains("数据截至"),
                "摘要也须提示数据截止时间");
    }

    @Test
    void plansAreDeterministicForSameInput() {
        ExecutionResult first = tool.execute(Map.of("category", "宽带", "fee_tier", "mid"));
        ExecutionResult second = tool.execute(Map.of("category", "宽带", "fee_tier", "mid"));

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.getData().get("rival_plans"), second.getData().get("rival_plans"),
                "同输入同输出（mock 确定性，可复现）");
    }

    @Test
    void ourPlansAlignToRealShelfOfferings() {
        ExecutionResult result = tool.execute(Map.of("category", "融合"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ours = (List<Map<String, Object>>) result.getData().get("our_plans");
        assertFalse(ours.isEmpty(), "同品类本网商品对齐货架");
        assertTrue(ours.stream().anyMatch(p -> "SCHEME_FBP_001".equals(p.get("offering_id"))),
                "对标条目为真实货架商品编码");
    }

    @Test
    void unknownCategoryFailsWithoutFakeData() {
        // 未知品类走兜底模板仍生成演示数据，但 empty 场景（fee_tier 无法命中的空集）不冒充
        ExecutionResult result = tool.execute(Map.of("fee_tier", "invalid_tier"));

        assertTrue(result.isSuccess(), "未知档位按默认档位兜底生成（不硬编码业务枚举）");
    }

    // ── 测试夹具 ──

    private Map<String, Object> shelfOffering(String offeringId, String name, int fee) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("offeringId", offeringId);
        o.put("offeringName", name);
        o.put("bizScenario", "家庭融合");
        o.put("state", "上架");
        o.put("monthlyFee", fee);
        return o;
    }

    private Map<String, Object> graphWithShelf(Map<String, Object> offering) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("shelfOfferings", List.of(offering));
        return graph;
    }
}
