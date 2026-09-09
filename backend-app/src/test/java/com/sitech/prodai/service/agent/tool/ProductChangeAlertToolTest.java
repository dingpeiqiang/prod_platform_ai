package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.domain.entity.ChangeAlert;
import com.sitech.prodai.domain.entity.ProductSubscription;
import com.sitech.prodai.mapper.ChangeAlertMapper;
import com.sitech.prodai.mapper.ProductSubscriptionMapper;
import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.UserScope;
import com.sitech.prodai.service.agent.model.UserScopeContext;
import com.sitech.prodai.service.changesub.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 商品变更订阅提醒工具（方案 §6-C3）单元测试：
 * SPI 自声明、未登录拒绝、订阅登记回执、提醒查询透出、动作字典、工具层契约。
 * （Mapper 层持久化语义由 SubscriptionService 集成面覆盖，本类聚焦工具编排与信任模型。）
 */
@ExtendWith(MockitoExtension.class)
class ProductChangeAlertToolTest {

    @Mock
    private ProductOntologyService productOntologyService;

    @Mock
    private ProductSubscriptionMapper subscriptionMapper;

    @Mock
    private ChangeAlertMapper alertMapper;

    private ProductChangeAlertTool tool;

    @BeforeEach
    void setUp() {
        tool = new ProductChangeAlertTool(new SubscriptionService(subscriptionMapper, alertMapper),
                productOntologyService);
        lenient().when(productOntologyService.loadGraph()).thenReturn(Map.of("shelfOfferings", List.of()));
    }

    @AfterEach
    void tearDown() {
        UserScopeContext.clear();
    }

    private void bindUser(String userId) {
        UserScopeContext.bind(UserScope.of(userId, List.of("电渠+厅店"), UserScope.SENSITIVITY_INTERNAL));
    }

    @Test
    void spiDeclaresScenesAndContracts() {
        assertEquals("product_change_alert", tool.getName());
        assertTrue(tool.getScenes().contains("query"), "scenes 含 query → 自动进入场景能力集");
        assertTrue(tool.getScenes().contains("ops"), "运营盯盘同样受益");
        assertFalse(tool.getHandoffs().isEmpty(), "声明典型业务链");
        assertTrue(tool.getOutputFields().stream().anyMatch(f -> "alerts".equals(f.getName())),
                "变更提醒为 ITEMS 契约字段");
        assertTrue(tool.getParams().stream().anyMatch(p -> "action".equals(p.getName())),
                "action 动作参数声明（subscribe/unsubscribe/list_alerts）");
    }

    @Test
    void failsWithoutLoginUser() {
        // 未绑定 UserScope（回落 unrestricted，userId 空）→ 拒绝：订阅人必须来自服务端登录态
        UserScopeContext.bind(UserScope.unrestricted());

        ExecutionResult result = tool.execute(Map.of("action", "list_alerts"));

        assertFalse(result.isSuccess(), "未识别登录用户必须拒绝（订阅人不可伪造）");
        assertTrue(result.getErrorMessage().contains("登录"), "拒绝原因明示需登录");
    }

    @Test
    void subscribeRegistersAndReturnsReceipt() {
        bindUser("agent01");
        when(productOntologyService.resolveOfferingId(any(), any())).thenReturn("OF-PER-99");

        ExecutionResult result = tool.execute(Map.of(
                "action", "subscribe", "offering_name", "5G畅享套餐"));

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertEquals("subscribe", data.get("action"));
        assertTrue(String.valueOf(data.get("nl_answer")).contains("OF-PER-99"),
                "订阅回执含商品编码");
        assertTrue(String.valueOf(data.get("nl_answer")).contains("订阅成功"),
                "回执明示订阅结果");
    }

    @Test
    void subscribeFailsWhenOfferingUnresolvable() {
        bindUser("agent01");
        when(productOntologyService.resolveOfferingId(any(), any())).thenReturn(null);

        ExecutionResult result = tool.execute(Map.of(
                "action", "subscribe", "offering_name", "不存在的商品XYZ"));

        assertTrue(result.isSuccess());
        assertTrue(String.valueOf(result.getData().get("nl_answer")).contains("无法定位"),
                "无法解析商品时如实告知（不冒充订阅成功）");
    }

    @Test
    void unknownActionFails() {
        bindUser("agent01");

        ExecutionResult result = tool.execute(Map.of("action", "delete_all"));

        assertFalse(result.isSuccess(), "动作字典外必须拒绝（白名单守门）");
        assertTrue(result.getErrorMessage().contains("未知动作"));
    }

    @Test
    void listAlertsReturnsEmptyGracefully() {
        bindUser("agent01");
        when(subscriptionMapper.selectList(any())).thenReturn(List.of());
        when(alertMapper.selectList(any())).thenReturn(List.of());
        when(alertMapper.selectCount(any())).thenReturn(0L);

        ExecutionResult result = tool.execute(Map.of("action", "list_alerts"));

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertTrue(((List<?>) data.get("alerts")).isEmpty());
        assertTrue(String.valueOf(data.get("nl_answer")).contains("暂无"),
                "空态明示无提醒并引导订阅（不冒充）");
    }

    @Test
    void listAlertsShowsRecords() {
        bindUser("agent01");
        ChangeAlert alert = new ChangeAlert();
        alert.setOfferingId("OF-A");
        alert.setOfferingName("套餐A");
        alert.setChangeType("fee_change");
        alert.setOldValue("128");
        alert.setNewValue("99");
        alert.setSubscriber("agent01");
        alert.setReadFlag(false);
        alert.setCreatedAt(LocalDateTime.of(2026, 9, 9, 10, 0));
        when(subscriptionMapper.selectList(any())).thenReturn(List.of());
        when(alertMapper.selectList(any())).thenReturn(List.of(alert));
        when(alertMapper.selectCount(any())).thenReturn(1L);

        ExecutionResult result = tool.execute(Map.of());

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) data.get("alerts");
        assertEquals(1, alerts.size());
        assertEquals("fee_change", alerts.get(0).get("change_type"));
        assertEquals("99", alerts.get(0).get("new_value"));
        assertTrue(String.valueOf(data.get("nl_answer")).contains("128→99"),
                "摘要串述变更要点（旧值→新值）");
    }
}
