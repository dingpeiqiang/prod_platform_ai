package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.UserScope;
import com.sitech.prodai.service.agent.model.UserScopeContext;
import org.junit.jupiter.api.AfterEach;
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
 * 政企 B2B 商品目录工具（方案 §6-C2）单元测试：
 * SPI 自声明（scenes 含 query/ops + catalog_items 契约）、行权限客户归属过滤
 * （客户经理仅见名下客户，全量角色可见 ALL）、行业/规模/月费过滤、
 * mock 确定性、来源与截止时间必透出。
 */
@ExtendWith(MockitoExtension.class)
class GovEnterpriseDirectoryToolTest {

    @Mock
    private ProductOntologyService productOntologyService;

    private GovEnterpriseDirectoryTool tool;

    @BeforeEach
    void setUp() {
        tool = new GovEnterpriseDirectoryTool(productOntologyService);
        lenient().when(productOntologyService.loadGraph()).thenReturn(new LinkedHashMap<>());
    }

    @AfterEach
    void tearDown() {
        UserScopeContext.clear();
    }

    @Test
    void spiDeclaresScenesAndContracts() {
        assertEquals("gov_enterprise_directory", tool.getName());
        assertTrue(tool.getScenes().contains("query"), "scenes 含 query → 自动进入场景能力集");
        assertTrue(tool.getScenes().contains("ops"), "运营盘点同样受益");
        assertFalse(tool.getHandoffs().isEmpty(), "声明典型业务链");
        assertTrue(tool.getOutputFields().stream().anyMatch(f -> "catalog_items".equals(f.getName())),
                "目录条目为 ITEMS 契约字段");
        assertTrue(tool.getOutputFields().stream().anyMatch(f -> "visible_customers".equals(f.getName())),
                "可见客户范围为契约字段（行权限透出）");
    }

    @Test
    void unrestrictedScopeSeesAllCustomers() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult result = tool.execute(Map.of());

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertEquals("ALL", data.get("visible_customers"), "客户归属不限（admin/普通角色）时透出 ALL");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("catalog_items");
        assertFalse(items.isEmpty(), "全量可见时有目录条目");
        assertTrue(items.stream().allMatch(i -> i.get("customer_id") instanceof String),
                "条目携带归属客户字段（行权限过滤依据）");
    }

    @Test
    void accountManagerScopeFiltersByOwnership() {
        // 客户经理 am01 名下客户（与 UserScopeResolver ACCOUNT_MANAGER_CUSTOMERS 同源口径）
        UserScopeContext.bind(UserScope.of("am01", List.of("政企"),
                List.of("GE-CUST-001", "GE-CUST-002", "GE-CUST-003"), UserScope.SENSITIVITY_INTERNAL));

        ExecutionResult result = tool.execute(Map.of());

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("catalog_items");
        assertFalse(items.isEmpty(), "名下客户有归属条目");
        assertTrue(items.stream().allMatch(i ->
                        List.of("GE-CUST-001", "GE-CUST-002", "GE-CUST-003").contains(i.get("customer_id"))),
                "客户经理仅见名下归属客户条目（行权限在本层强制，不依赖 LLM）");
        assertEquals(java.util.Set.of("GE-CUST-001", "GE-CUST-002", "GE-CUST-003"), data.get("visible_customers"),
                "可见客户范围透出名下客户列表");
    }

    @Test
    void industryFilterNarrowsResults() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult all = tool.execute(Map.of());
        ExecutionResult filtered = tool.execute(Map.of("industry", "教育"));

        assertTrue(all.isSuccess());
        assertTrue(filtered.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) filtered.getData().get("catalog_items");
        assertFalse(items.isEmpty(), "过滤后仍有条目");
        for (Map<String, Object> i : items) {
            assertEquals("教育", i.get("industry"), "行业过滤后条目行业一致");
        }
    }

    @Test
    void scaleFilterNarrowsResults() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult filtered = tool.execute(Map.of("scale", "小微"));

        assertTrue(filtered.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) filtered.getData().get("catalog_items");
        assertFalse(items.isEmpty(), "规模过滤后有命中条目（字典三档恒有命中）");
        for (Map<String, Object> i : items) {
            assertEquals("小微", i.get("customer_scale"), "规模过滤后条目规模一致");
        }
    }

    @Test
    void maxFeeFilterNarrowsResults() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult filtered = tool.execute(Map.of("max_monthly_fee", 200));

        assertTrue(filtered.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) filtered.getData().get("catalog_items");
        assertFalse(items.isEmpty(), "月费上限过滤后有命中条目");
        for (Map<String, Object> i : items) {
            assertTrue(((Number) i.get("monthly_fee")).doubleValue() <= 200, "条目月费不超上限");
        }
    }

    @Test
    void deterministicForSameInput() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult first = tool.execute(Map.of("industry", "政务"));
        ExecutionResult second = tool.execute(Map.of("industry", "政务"));

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.getData().get("catalog_items"), second.getData().get("catalog_items"),
                "同输入同输出（mock 确定性，可复现）");
    }

    @Test
    void sourcesContainDataAsOfForFreshness() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult result = tool.execute(Map.of());

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) result.getData().get("sources");
        assertTrue(sources.containsKey("data_as_of"), "目录时效：截止时间必透出");
        assertTrue(String.valueOf(result.getData().get("nl_answer")).contains("数据截至"),
                "摘要也须提示数据截止时间");
    }

    @Test
    void failsWhenFilterExcludesAll() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult result = tool.execute(Map.of("max_monthly_fee", 1));

        assertFalse(result.isSuccess(), "过滤后无条目必须拒绝（不冒充数据）");
        assertTrue(result.getErrorMessage().contains("未找到匹配"), "拒绝原因明示过滤口径");
    }
}
