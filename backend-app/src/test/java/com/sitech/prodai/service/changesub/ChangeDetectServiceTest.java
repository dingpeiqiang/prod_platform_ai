package com.sitech.prodai.service.changesub;

import com.sitech.prodai.config.ProdAiProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商品变更检测服务（方案 §6-C3）单元测试：
 * 开关守卫、月费/状态字段级 diff、新上架/下架集差、护栏截断、纯函数确定性。
 */
class ChangeDetectServiceTest {

    private ChangeDetectService serviceWith(boolean enabled) {
        ProdAiProperties properties = new ProdAiProperties();
        properties.getChangeSub().setDetectEnabled(enabled);
        return new ChangeDetectService(properties);
    }

    private Map<String, Object> offering(String id, String name, Object fee, String state) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("offeringId", id);
        o.put("offeringName", name);
        o.put("monthlyFee", fee);
        o.put("state", state);
        return o;
    }

    @Test
    void disabledSwitchYieldsNoChanges() {
        List<Map<String, Object>> before = List.of(
                offering("OF-A", "套餐A", 128, "on_shelf"));
        List<Map<String, Object>> after = List.of(
                offering("OF-A", "套餐A", 99, "on_shelf"));

        assertTrue(serviceWith(false).detect("r1", before, after).isEmpty(),
                "开关关闭：即使有变更也不产出（默认关零风险）");
    }

    @Test
    void feeChangeDetected() {
        List<Map<String, Object>> before = List.of(
                offering("OF-A", "套餐A", 128, "on_shelf"));
        List<Map<String, Object>> after = List.of(
                offering("OF-A", "套餐A", 99, "on_shelf"));

        List<Map<String, Object>> changes = serviceWith(true).detect("r1", before, after);
        assertEquals(1, changes.size());
        Map<String, Object> c = changes.get(0);
        assertEquals("OF-A", c.get("offering_id"));
        assertEquals(ChangeDetectService.TYPE_FEE_CHANGE, c.get("change_type"));
        assertEquals("128", c.get("old_value"), "整数值月费去小数点展示");
        assertEquals("99", c.get("new_value"));
    }

    @Test
    void stateChangeDetected() {
        List<Map<String, Object>> before = List.of(
                offering("OF-B", "套餐B", 59, "on_shelf"));
        List<Map<String, Object>> after = List.of(
                offering("OF-B", "套餐B", 59, "on_sale"));

        List<Map<String, Object>> changes = serviceWith(true).detect("r1", before, after);
        assertEquals(1, changes.size());
        assertEquals(ChangeDetectService.TYPE_STATE_CHANGE, changes.get(0).get("change_type"));
        assertEquals("on_shelf", changes.get(0).get("old_value"));
        assertEquals("on_sale", changes.get(0).get("new_value"));
    }

    @Test
    void newShelfAndOffShelfDetected() {
        List<Map<String, Object>> before = List.of(
                offering("OF-A", "套餐A", 128, "on_shelf"));
        List<Map<String, Object>> after = List.of(
                offering("OF-C", "套餐C", 88, "on_shelf"));

        List<Map<String, Object>> changes = serviceWith(true).detect("r1", before, after);
        assertEquals(2, changes.size(), "新上架 1 + 下架 1");
        Map<String, Object> added = changes.stream()
                .filter(c -> "OF-C".equals(c.get("offering_id"))).findFirst().orElseThrow();
        assertEquals(ChangeDetectService.TYPE_STATE_CHANGE, added.get("change_type"));
        assertEquals("", added.get("old_value"), "新上架旧状态为空");
        assertEquals("on_shelf", added.get("new_value"));
        Map<String, Object> removed = changes.stream()
                .filter(c -> "OF-A".equals(c.get("offering_id"))).findFirst().orElseThrow();
        assertEquals("on_shelf", removed.get("old_value"));
        assertEquals("off_shelf", removed.get("new_value"), "下架推导为 off_shelf");
    }

    @Test
    void noChangeWhenIdentical() {
        List<Map<String, Object>> same = List.of(
                offering("OF-A", "套餐A", 128, "on_shelf"),
                offering("OF-B", "套餐B", 59.5, "on_sale"));
        List<Map<String, Object>> before = new java.util.ArrayList<>(same);
        List<Map<String, Object>> after = new java.util.ArrayList<>(same);

        assertTrue(serviceWith(true).detect("r1", before, after).isEmpty(), "同快照无变更");
    }

    @Test
    void guardLimitsAlertsPerDetect() {
        List<Map<String, Object>> before = new java.util.ArrayList<>();
        List<Map<String, Object>> after = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            after.add(offering("OF-" + i, "商品" + i, 100, "on_shelf"));
        }
        ProdAiProperties properties = new ProdAiProperties();
        properties.getChangeSub().setDetectEnabled(true);
        properties.getChangeSub().setMaxAlertsPerDetect(50);

        List<Map<String, Object>> changes = new ChangeDetectService(properties).detect("r1", before, after);
        assertEquals(50, changes.size(), "单次检测提醒产出受护栏约束");
    }

    @Test
    void nullSafeForEmptyOrDirtyInputs() {
        ChangeDetectService service = serviceWith(true);
        assertTrue(service.detect("r1", null, null).isEmpty(), "空输入安全");
        assertTrue(service.detect("r1",
                List.of(new LinkedHashMap<>(Map.of("offeringName", "无编码脏条目"))),
                List.of()).isEmpty(), "无编码条目跳过不抛错");
    }

    @Test
    void deterministicForSameInput() {
        List<Map<String, Object>> before = List.of(
                offering("OF-A", "套餐A", 128, "on_shelf"));
        List<Map<String, Object>> after = List.of(
                offering("OF-A", "套餐A", 99, "on_sale"));

        List<Map<String, Object>> first = serviceWith(true).detect("r1", before, after);
        List<Map<String, Object>> second = serviceWith(true).detect("r1", before, after);
        assertEquals(first, second, "同输入同输出（纯函数可复现）");
        assertFalse(first.isEmpty());
    }
}
