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
 * 地市差异化政策查询工具（方案 §6-B3）单元测试：
 * SPI 自声明（scenes 含 query/ops）、缺地市拒绝、未知地市拒绝、
 * 来源与截止时间必透出、mock 确定性、政策类型过滤。
 */
@ExtendWith(MockitoExtension.class)
class CityPolicyQueryToolTest {

    @Mock
    private ProductOntologyService productOntologyService;

    private CityPolicyQueryTool tool;

    @BeforeEach
    void setUp() {
        tool = new CityPolicyQueryTool(productOntologyService);
        lenient().when(productOntologyService.loadGraph()).thenReturn(new LinkedHashMap<>());
    }

    @Test
    void spiDeclaresScenesAndContracts() {
        assertEquals("city_policy_query", tool.getName());
        assertTrue(tool.getScenes().contains("query"), "scenes 含 query → 自动进入场景能力集");
        assertTrue(tool.getScenes().contains("ops"), "运营铺市排期同样受益");
        assertFalse(tool.getHandoffs().isEmpty(), "声明典型业务链");
        assertTrue(tool.getOutputFields().stream().anyMatch(f -> "policies".equals(f.getName())));
    }

    @Test
    void failsWithoutCity() {
        ExecutionResult result = tool.execute(Map.of());

        assertFalse(result.isSuccess(), "缺地市必须拒绝");
        assertTrue(result.getErrorMessage().contains("地市"), "拒绝原因明示缺地市");
    }

    @Test
    void failsWithUnsupportedCity() {
        ExecutionResult result = tool.execute(Map.of("city", "火星"));

        assertFalse(result.isSuccess(), "未覆盖地市不冒充数据");
        assertTrue(result.getErrorMessage().contains("当前覆盖"), "拒绝时透出已覆盖地市口径");
    }

    @Test
    void returnsPoliciesForSupportedCity() {
        ExecutionResult result = tool.execute(Map.of("city", "昆明"));

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertEquals("昆明", data.get("city"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> policies = (List<Map<String, Object>>) data.get("policies");
        assertFalse(policies.isEmpty(), "支持地市有政策条目");
        assertTrue(policies.stream().allMatch(p -> List.of("地市补贴", "渠道佣金", "促销窗口", "准入要求")
                .contains(p.get("policy_type"))), "政策类型在字典内");
    }

    @Test
    void policyTypeFilterNarrowsResults() {
        ExecutionResult all = tool.execute(Map.of("city", "曲靖"));
        ExecutionResult filtered = tool.execute(Map.of("city", "曲靖", "policy_type", "地市补贴"));

        assertTrue(all.isSuccess());
        assertTrue(filtered.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filteredPolicies =
                (List<Map<String, Object>>) filtered.getData().get("policies");
        assertFalse(filteredPolicies.isEmpty(), "过滤后仍有条目");
        for (Map<String, Object> p : filteredPolicies) {
            assertEquals("地市补贴", p.get("policy_type"), "类型过滤后条目类型一致");
        }
    }

    @Test
    void sourcesContainDataAsOfForFreshness() {
        ExecutionResult result = tool.execute(Map.of("city", "玉溪"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) result.getData().get("sources");
        assertTrue(sources.containsKey("data_as_of"), "政策时效：截止时间必透出");
        assertEquals("玉溪", sources.get("city"));
        assertTrue(String.valueOf(result.getData().get("nl_answer")).contains("数据截至"),
                "摘要也须提示数据截止时间");
    }

    @Test
    void policiesAreDeterministicForSameCity() {
        ExecutionResult first = tool.execute(Map.of("city", "大理"));
        ExecutionResult second = tool.execute(Map.of("city", "大理"));

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.getData().get("policies"), second.getData().get("policies"),
                "同输入同输出（mock 确定性，可复现）");
    }
}
