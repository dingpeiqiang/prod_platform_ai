package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.controller.AppStoreController;
import com.sitech.prodai.service.common.MapOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 产销品加载 AI 应用 11 接口契约测试（插件对接目标）。
 */
class AppStoreApiTest {

    private AppMockStore store;
    private BillingRuleCheckService billingRuleCheck;
    private SpecAuditService specAudit;
    private TestCaseService testCaseService;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        store = new AppMockStore(om);
        billingRuleCheck = new BillingRuleCheckService();
        specAudit = new SpecAuditService();
        testCaseService = new TestCaseService(store);
    }

    private AppStoreFacade facade() {
        return new AppStoreFacade(store, billingRuleCheck, specAudit, testCaseService);
    }

    private Map<String, Object> validCrmReq() {
        return Map.of(
                "product_name", "测试流量包",
                "fee_json", "{\"monthly_fee\":29}",
                "sale_scope", "anhui-all");
    }

    private Map<String, Object> validBillingReq() {
        return Map.of("product_id", "P20260001", "fee_json", "{\"monthly_fee\":29}");
    }

    @Test
    void interface1_query_product_config() {
        List<Map<String, Object>> result = store.queryProducts("流量", null, "online");
        assertTrue(result.size() >= 1);
        Map<String, Object> byId = store.queryProducts(null, "P20260001", "all").stream().findFirst().orElse(null);
        assertNotNull(byId);
        assertEquals("P20260001", byId.get("product_id"));
    }

    @Test
    void interface2_gen_crm_config_and_duplicate_reject() {
        Map<String, Object> first = facade().genCrm(validCrmReq());
        assertEquals(0, first.get("code"));
        assertNotNull(first.get("crm_config_id"));
        assertNotNull(first.get("crm_config_json"));
        // 重复性校验
        Map<String, Object> second = facade().genCrm(validCrmReq());
        assertEquals(1002, second.get("code"));
    }

    @Test
    void interface2_missing_required_params() {
        Map<String, Object> result = facade().genCrm(Map.of("product_name", "x"));
        assertEquals(1001, result.get("code"));
    }

    @Test
    void interface3_gen_billing_config() {
        Map<String, Object> result = facade().genBilling(validBillingReq());
        assertEquals(0, result.get("code"));
        assertNotNull(result.get("billing_config_id"));
        assertTrue(MapOps.str(result.get("billing_config_json")).contains("billing_events"));
    }

    @Test
    void interface4_check_billing_rule_overlap_conflict() {
        Map<String, Object> config = Map.of("discount_rules", List.of(
                Map.of("discount_type", "limited_time"),
                Map.of("discount_type", "long_term")));
        Map<String, Object> result = billingRuleCheck.verify(config, "all");
        assertEquals(0, result.get("code"));
        assertEquals(1, result.get("pass"));
        List<Map<String, Object>> risks = castList(result.get("risk_list"));
        assertEquals(1, risks.size());
        assertEquals("overlap_conflict", risks.get(0).get("risk_type"));
    }

    @Test
    void interface4_negative_fee_detected() {
        Map<String, Object> config = Map.of("fee_items", List.of(
                Map.of("fee_name", "月费", "amount", -10)));
        Map<String, Object> result = billingRuleCheck.verify(config, "fee");
        assertEquals(1, result.get("pass"));
    }

    @Test
    void interface5_spec_audit_pass_and_fail() {
        Map<String, Object> crm = Map.of(
                "product_name", "合法名称",
                "product_desc", "desc",
                "fee_json", "{}",
                "sale_scope", "anhui-all",
                "effect_date", "2026-01-01");
        Map<String, Object> billing = Map.of("product_id", "P1", "effect_date", "2026-01-01");
        Map<String, Object> ok = specAudit.audit(crm, billing, Map.of());
        assertEquals(0, ok.get("pass"));

        Map<String, Object> bad = specAudit.audit(
                Map.of("product_name", "非法!名称@很长很长很长", "sale_scope", "mars"), billing, Map.of());
        assertEquals(1, bad.get("pass"));
        assertTrue(castList(bad.get("error_list")).size() >= 2);
    }

    @Test
    void interface6_gen_test_cases() {
        Map<String, Object> result = testCaseService.generate(Map.of("product_name", "测试包"),
                Map.of("product_id", "P1"), "all");
        assertEquals(0, result.get("code"));
        assertTrue((int) result.get("case_count") >= 4);
        assertTrue(castList(result.get("case_ids")).size() == (int) result.get("case_count"));
    }

    @Test
    void interface7_execute_sync_and_env_guard() {
        Map<String, Object> gen = testCaseService.generate(Map.of("product_name", "测试包"),
                Map.of("product_id", "P1"), "acceptance");
        List<String> caseIds = stringList(gen.get("case_ids"));

        Map<String, Object> sync = testCaseService.execute(caseIds, "sit", "sync");
        assertEquals(0, sync.get("code"));
        assertEquals(caseIds.size(), sync.get("total"));

        Map<String, Object> blocked = testCaseService.execute(caseIds, "prod", "sync");
        assertEquals(4002, blocked.get("code"));
    }

    @Test
    void interface7_async_task_query() throws Exception {
        Map<String, Object> gen = testCaseService.generate(Map.of("product_name", "测试包"),
                Map.of("product_id", "P1"), "cancel");
        List<String> caseIds = stringList(gen.get("case_ids"));
        Map<String, Object> async = testCaseService.execute(caseIds, "uat", "async");
        String taskId = MapOps.str(async.get("task_id"));
        assertTrue(taskId.startsWith("T"));

        Map<String, Object> task = testCaseService.queryTask(taskId);
        assertEquals(0, task.get("code"));
        // 等待异步执行完成
        for (int i = 0; i < 50 && !"finished".equals(task.get("status")); i++) {
            Thread.sleep(100);
            task = testCaseService.queryTask(taskId);
        }
        assertEquals("finished", task.get("status"));
    }

    @Test
    void interface8_verify_acceptance() {
        Map<String, Object> result = facade().verify("P20260001", "new");
        assertEquals(0, result.get("pass"));
        assertNotNull(result.get("order_id"));

        Map<String, Object> missing = facade().verify("", "new");
        assertEquals(8001, missing.get("code"));

        Map<String, Object> badType = facade().verify("P20260001", "delete");
        assertEquals(8002, badType.get("code"));
    }

    @Test
    void interface9_submit_approval() {
        Map<String, Object> req = Map.of("product_id", "P20260001", "report", "测试报告内容",
                "approval_flow", "standard");
        Map<String, Object> result = facade().approve(req);
        assertEquals(0, result.get("code"));
        assertNotNull(result.get("approval_id"));
        assertEquals("submitted", result.get("status"));

        Map<String, Object> noReport = facade().approve(Map.of("product_id", "P20260001"));
        assertEquals(9001, noReport.get("code"));
    }

    @Test
    void interface10_query_monitor() {
        Map<String, Object> result = facade().monitor("P20260001", null, "all");
        assertEquals(0, result.get("code"));
        assertTrue((int) result.get("order_count") > 0);
        assertNotNull(result.get("alarm_list"));

        Map<String, Object> missing = facade().monitor("P-NOPE", null, "all");
        assertEquals(10002, missing.get("code"));
    }

    @Test
    void interface11_send_alert_and_monitor_sees_it() {
        Map<String, Object> req = Map.of("product_id", "P20260001",
                "alarm_level", "high", "content", "计费差错率超阈值");
        Map<String, Object> result = facade().alert(req);
        assertEquals(0, result.get("code"));
        String alertId = MapOps.str(result.get("alert_id"));
        assertEquals("sent", result.get("status"));

        List<Map<String, Object>> alarms = store.findAlerts("P20260001", null);
        assertEquals(1, alarms.size());
        assertEquals(alertId, alarms.get(0).get("alert_id"));
    }

    @Test
    void idempotency_replay() {
        String key = "idem-001";
        Map<String, Object> req = new HashMap<>(validCrmReq());
        req.put("idempotency_key", key);
        Map<String, Object> first = facade().genCrm(req);
        assertEquals(0, first.get("code"));
        Map<String, Object> replay = store.idempotentReplay(key);
        assertNotNull(replay);
        assertEquals(first.get("crm_config_id"), replay.get("crm_config_id"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private List<String> stringList(Object value) {
        List<String> result = new java.util.ArrayList<>();
        for (Object o : MapOps.castList(value)) {
            result.add(String.valueOf(o));
        }
        return result;
    }

    /** 轻量门面：复用 Controller 逻辑做契约断言（不入 HTTP） */
    private static class AppStoreFacade {
        private final AppStoreController controller;

        AppStoreFacade(AppMockStore store, BillingRuleCheckService b, SpecAuditService s, TestCaseService t) {
            this.controller = new AppStoreController(store, b, s, t, new NodeResultService());
        }

        Map<String, Object> genCrm(Map<String, Object> req) {
            return controller.genCrmConfig(req);
        }

        Map<String, Object> genBilling(Map<String, Object> req) {
            return controller.genBillingConfig(req);
        }

        Map<String, Object> verify(String productId, String type) {
            return controller.verifyAcceptance(Map.of("product_id", productId, "verify_type", type));
        }

        Map<String, Object> approve(Map<String, Object> req) {
            return controller.submitReleaseApproval(req);
        }

        Map<String, Object> monitor(String productId, String range, String metric) {
            return controller.queryProductMonitor(productId, range, metric);
        }

        Map<String, Object> alert(Map<String, Object> req) {
            return controller.sendAlert(req);
        }
    }
}
