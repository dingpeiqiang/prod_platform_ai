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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 存量用户套包查询工具（方案 §6-B2）单元测试：
 * SPI 自声明（scenes 仅 query）、敏感度门控（SENSITIVITY_SENSITIVE 硬性约束）、
 * 输出脱敏（标识打码 + 无原始标识泄漏）、mock 订购面确定性、降级与失败不冒充数据。
 */
@ExtendWith(MockitoExtension.class)
class UserPlanQueryToolTest {

    @Mock
    private ProductOntologyService productOntologyService;

    private UserPlanQueryTool tool;

    @BeforeEach
    void setUp() {
        tool = new UserPlanQueryTool(productOntologyService);
        lenient().when(productOntologyService.loadGraph()).thenReturn(graphWithShelf(shelfOffering("SCHEME_FBP_001")));
    }

    @AfterEach
    void tearDown() {
        UserScopeContext.clear();
    }

    @Test
    void spiDeclaresQuerySceneOnly() {
        assertEquals("user_plan_query", tool.getName());
        assertTrue(tool.getScenes().contains("query"), "scenes 含 query → 自动进入场景能力集");
        assertFalse(tool.getScenes().contains("ops"), "用户套包仅查询助手口径（方案 §4.6）");
        assertFalse(tool.getHandoffs().isEmpty(), "声明典型业务链");
        assertTrue(tool.getOutputFields().stream().anyMatch(f -> "current_plans".equals(f.getName())));
    }

    @Test
    void sensitiveGateRejectsInternalSensitivityAccounts() {
        // 非 admin 默认 INTERNAL（=1）：无敏感数据权限 → 硬性拒绝，不返回降级数据
        UserScopeContext.bind(UserScope.of("cs_agent01", List.of("电渠+厅店"), UserScope.SENSITIVITY_INTERNAL));

        ExecutionResult result = tool.execute(Map.of("user_id", "13812341234"));

        assertFalse(result.isSuccess(), "敏感度不足必须拒绝");
        assertTrue(result.getErrorMessage().contains("权限"), "拒绝原因明示权限口径");
    }

    @Test
    void unrestrictedScopeCanQueryAndMasksUserId() {
        // 未启用行权限形态（本地开发）：unrestricted 全量可查
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult result = tool.execute(Map.of("user_id", "13812341234"));

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertEquals("138******34", data.get("user_masked"), "标识打码：保留前3后2（11位号码中间6位打码）");
        assertFalse(String.valueOf(data.get("nl_answer")).contains("13812341234"),
                "原始标识不得出现在任何输出面（含摘要）");
    }

    @Test
    void plansAreDeterministicForSameUser() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult first = tool.execute(Map.of("user_id", "13812341234"));
        ExecutionResult second = tool.execute(Map.of("user_id", "13812341234"));

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.getData().get("current_plans"), second.getData().get("current_plans"),
                "同输入同输出（mock 确定性，可复现）");
    }

    @Test
    void historyOnlyIncludedWhenRequested() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult without = tool.execute(Map.of("user_id", "13812341234"));
        assertNull(without.getData().get("history"), "默认不含变更记录");

        ExecutionResult with = tool.execute(Map.of("user_id", "13812341234", "include_history", "true"));
        assertTrue(with.isSuccess());
        assertTrue(with.getData().get("history") instanceof List<?>, "include_history=true 时返回变更记录");
    }

    @Test
    void failsWithoutUserId() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult result = tool.execute(Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("用户标识"));
    }

    @Test
    void plansAlignToRealShelfOfferings() {
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult result = tool.execute(Map.of("user_id", "13812341234"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) result.getData().get("current_plans");
        assertFalse(plans.isEmpty());
        assertTrue(plans.stream().anyMatch(p -> "main".equals(p.get("plan_type"))), "含主套包");
        for (Map<String, Object> p : plans) {
            assertTrue(List.of("main", "addon", "value").contains(p.get("plan_type")), "套包类型在字典内");
            assertTrue(p.containsKey("monthly_fee"), "月费随条目（对齐货架商品档案）");
        }
    }

    @Test
    void maskUserIdEdgeCases() {
        assertEquals("138******34", UserPlanQueryTool.maskUserId("13812341234"), "11 位：前3后2，中间 6 位打码");
        assertEquals("abc***********op", UserPlanQueryTool.maskUserId("abcdefghijklmnop"), "长串同口径：前3后2，中间全打码");
        assertEquals("abc*23", UserPlanQueryTool.maskUserId("abc123"), "6 位：前3后2，中间 1 位打码");
        assertEquals("****", UserPlanQueryTool.maskUserId("abc1"), "不足 6 位全打码");
        assertEquals("", UserPlanQueryTool.maskUserId(""));
        assertEquals("", UserPlanQueryTool.maskUserId(null));
    }

    // ── 测试夹具 ──

    private Map<String, Object> shelfOffering(String offeringId) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("offeringId", offeringId);
        o.put("offeringName", "家庭亲情网基础套餐");
        o.put("state", "上架");
        o.put("monthlyFee", 99);
        return o;
    }

    private Map<String, Object> graphWithShelf(Map<String, Object> offering) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("shelfOfferings", List.of(offering));
        return graph;
    }
}
