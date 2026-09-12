package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.common.MapOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 产销品加载 AI 应用 V1.6 · 14 条路由契约测试。
 * <p>
 * 依据：《产销品加载AI应用-开发工作清单.md》V1.6 + 插件\自研插件集V1.6\工具*.json。
 * 覆盖：seed 数据 18 销售品、presetValue 抽查（900102308/900117022）、
 * 门禁 NOT_CONFIRMED、幂等、错误码（4001/4002/4003/5002/5005）、演示注入开关。
 */
class AppStoreV16Test {

    private ObjectMapper om;
    private OfferSeedService seed;
    private OfferSimV16Service sim;

    @BeforeEach
    void setUp() {
        om = new ObjectMapper();
        seed = new OfferSeedService(om);
        seed.load();
        sim = new OfferSimV16Service(om, seed, new NodeResultService());
    }

    /* ================= 种子数据 ================= */

    @Test
    void seed_has_18_offers() {
        assertEquals(18, seed.count());
        assertTrue(seed.exists("900102308"));
        assertTrue(seed.exists("900117022"));
        assertEquals(10, seed.testPoints().size());
    }

    @Test
    void preset_spot_check_two_series() {
        Map<String, Object> p5g = seed.presetsOf("900102308");
        assertEquals("5G-A套餐199元", p5g.get("P_OFFER_NAME"));
        assertEquals("生效", p5g.get("P_STATUS"));
        assertTrue(MapOps.str(p5g.get("P_MAIN_PROD")).contains("5G-A套餐199元"));

        Map<String, Object> pRights = seed.presetsOf("900117022");
        assertEquals("19.9元权益随心选娱乐版", pRights.get("P_OFFER_NAME"));
        assertTrue(MapOps.str(pRights.get("P_PAY_MODE")).contains("预付费"));
    }

    /* ================= 接口1 相似度分析 ================= */

    @Test
    void interface1_similar_query_hit() {
        Map<String, Object> r = sim.similarOfferQuery(Map.of("businessDesc", "想要一个199元的5G-A套餐"));
        assertEquals("0", r.get("resultCode"));
        List<Map<String, Object>> list = MapOps.castListOfMaps(r.get("similarOfferList"));
        assertFalse(list.isEmpty());
        // score 降序且第一名命中 5G-A 系列
        assertTrue(MapOps.str(list.get(0).get("similarOfferId")).startsWith("9001"));
        assertTrue(Double.parseDouble(MapOps.str(list.get(0).get("similarityScore"))) >=
                Double.parseDouble(MapOps.str(list.get(list.size() - 1).get("similarityScore"))));
    }

    @Test
    void interface1_missing_param() {
        Map<String, Object> r = sim.similarOfferQuery(Map.of());
        assertEquals("PARAM_MISSING", r.get("resultCode"));
    }

    /* ================= 接口2 实时规格稽核 ================= */

    @Test
    void interface2_audit_pass_by_default() {
        String config = "{\"offer_id\":\"900102308\",\"offer_name\":\"5G-A套餐199元\"}";
        Map<String, Object> r = sim.auditRealtime(Map.of("offer_id", "900102308", "config_json", config), System.currentTimeMillis());
        assertEquals("1", r.get("pass"));
        assertTrue(MapOps.castListOfMaps(r.get("error_list")).isEmpty());
    }

    @Test
    void interface2_audit_reject_with_injection() {
        sim.injectAuditReject("900102308", true);
        String config = "{\"offer_id\":\"900102308\"}";
        Map<String, Object> r = sim.auditRealtime(Map.of("offer_id", "900102308", "config_json", config), System.currentTimeMillis());
        assertEquals("0", r.get("pass"));
        assertFalse(MapOps.castListOfMaps(r.get("error_list")).isEmpty());
    }

    @Test
    void interface2_invalid_json() {
        Map<String, Object> r = sim.auditRealtime(Map.of("offer_id", "900102308", "config_json", "not-json"), System.currentTimeMillis());
        assertEquals("5001", r.get("resultCode"));
    }

    /* ================= 接口3 配置落地 ================= */

    @Test
    void interface3_not_confirmed_gate() {
        Map<String, Object> r = sim.saveProductConfig(Map.of(
                "plan_id", "PLAN20260912001", "plan_json", "{\"offer_name\":\"x\"}", "confirmed", "false"));
        assertEquals("NOT_CONFIRMED", r.get("status"));
    }

    @Test
    void interface3_save_and_idempotent() {
        String planJson = "{\"offer_id\":\"900102308\",\"offer_name\":\"5G-A套餐199元\"}";
        Map<String, Object> first = sim.saveProductConfig(Map.of(
                "plan_id", "PLAN20260912001", "plan_json", planJson, "confirmed", "true"));
        assertEquals("SUCCESS", first.get("status"));
        assertEquals("900102308", first.get("offer_id"));
        assertNotNull(first.get("product_id"));
        List<Map<String, Object>> saveResult = MapOps.castListOfMaps(first.get("save_result"));
        assertEquals(4, saveResult.size());

        // 幂等：同 plan_json 返回相同 offer_id/product_id
        Map<String, Object> second = sim.saveProductConfig(Map.of(
                "plan_id", "PLAN20260912002", "plan_json", planJson, "confirmed", "true"));
        assertEquals(first.get("offer_id"), second.get("offer_id"));
        assertEquals(first.get("product_id"), second.get("product_id"));
    }

    @Test
    void interface3_missing_param() {
        Map<String, Object> r = sim.saveProductConfig(Map.of("confirmed", "true"));
        assertEquals("PARAM_MISSING", r.get("resultCode"));
    }

    /* ================= 接口4~7 测试链路 ================= */

    @Test
    void interface4_start_unknown_offer() {
        Map<String, Object> r = sim.testOfferStart(Map.of("offerId", "999999999"));
        assertEquals("4001", r.get("resultCode"));
    }

    @Test
    void interface4_start_returns_global_id_format() {
        Map<String, Object> r = sim.testOfferStart(Map.of("offerId", "900102308"));
        assertEquals("0", r.get("resultCode"));
        String globalId = MapOps.str(r.get("globalId"));
        assertTrue(globalId.matches("50\\d{24}"), "globalId 格式: " + globalId);
    }

    @Test
    void interface5_scenes_fusion_has_add_card_rights_not() {
        Map<String, Object> start5g = sim.testOfferStart(Map.of("offerId", "900113046"));
        Map<String, Object> scenes5g = sim.testScenes(Map.of("globalId", start5g.get("globalId")));
        List<Map<String, Object>> list5g = MapOps.castListOfMaps(scenes5g.get("testScenes"));
        assertTrue(list5g.stream().anyMatch(s -> "S_ADD_CARD".equals(s.get("testSceneNbr"))));

        Map<String, Object> startRights = sim.testOfferStart(Map.of("offerId", "900117022"));
        Map<String, Object> scenesRights = sim.testScenes(Map.of("globalId", startRights.get("globalId")));
        List<Map<String, Object>> listRights = MapOps.castListOfMaps(scenesRights.get("testScenes"));
        assertTrue(listRights.stream().noneMatch(s -> "S_ADD_CARD".equals(s.get("testSceneNbr"))));
        assertEquals(2, listRights.size());
    }

    @Test
    void interface5_unknown_global_id() {
        Map<String, Object> r = sim.testScenes(Map.of("globalId", "50xx"));
        assertEquals("4002", r.get("resultCode"));
    }

    @Test
    void interface6_progress_state_machine() throws InterruptedException {
        Map<String, Object> start = sim.testOfferStart(Map.of("offerId", "900102308"));
        String globalId = MapOps.str(start.get("globalId"));
        Map<String, Object> p = sim.testProgress(Map.of("globalId", globalId));
        assertEquals("0", p.get("resultCode"));
        assertEquals("5", p.get("totalSteps"));
        assertEquals("3", p.get("totalSceneCount"));
        assertEquals("false", p.get("done"));
    }

    @Test
    void interface7_result_requires_done() {
        Map<String, Object> start = sim.testOfferStart(Map.of("offerId", "900102308"));
        Map<String, Object> r = sim.testResult(Map.of("globalId", start.get("globalId")));
        assertEquals("4003", r.get("resultCode"));
    }

    @Test
    void interface7_result_preset_consistency() {
        Map<String, Object> start = sim.testOfferStart(Map.of("offerId", "900102308"));
        String globalId = MapOps.str(start.get("globalId"));
        Map<String, Object> task = testTask(globalId);
        task.put("done", true);
        Map<String, Object> r = sim.testResult(Map.of("globalId", globalId));
        assertEquals("0", r.get("resultCode"));
        assertNotNull(MapOps.str(r.get("orderId")));
        assertFalse(MapOps.str(r.get("orderId")).isBlank());
        assertFalse(MapOps.str(r.get("offerInstId")).isBlank());
        List<Map<String, Object>> scenes = MapOps.castListOfMaps(r.get("testScenes"));
        assertFalse(scenes.isEmpty());
        Map<String, Object> firstScene = scenes.get(0);
        List<Map<String, Object>> points = MapOps.castListOfMaps(firstScene.get("testCasePointResults"));
        assertEquals(10, points.size());
        Map<String, Object> namePoint = points.stream()
                .filter(p -> "P_OFFER_NAME".equals(p.get("testPointNbr"))).findFirst().orElseThrow();
        assertEquals(seed.presetsOf("900102308").get("P_OFFER_NAME"), namePoint.get("presetValue"));
        assertEquals("0", namePoint.get("resultCode"));
    }

    @Test
    void interface7_point_mismatch_injection() {
        sim.injectPointMismatch("900102308", true);
        Map<String, Object> start = sim.testOfferStart(Map.of("offerId", "900102308"));
        String globalId = MapOps.str(start.get("globalId"));
        testTask(globalId).put("done", true);
        Map<String, Object> r = sim.testResult(Map.of("globalId", globalId));
        List<Map<String, Object>> scenes = MapOps.castListOfMaps(r.get("testScenes"));
        Map<String, Object> firstScene = scenes.get(0);
        assertEquals("1", firstScene.get("failTestCaseCount"));
    }

    /* ================= 接口8 计费规则校验 ================= */

    @Test
    void interface8_billing_pass_and_conflict() {
        Map<String, Object> okR = sim.billingRulesVerify(Map.of(
                "config_json", "{\"offer_id\":\"900102308\",\"monthly_fee\":199}"));
        assertEquals("1", okR.get("pass"));
        assertTrue(MapOps.castListOfMaps(okR.get("risk_list")).isEmpty());

        sim.injectFeeConflict("900102308", true);
        Map<String, Object> bad = sim.billingRulesVerify(Map.of(
                "config_json", "{\"offer_id\":\"900102308\",\"monthly_fee\":199}"));
        assertEquals("0", bad.get("pass"));
        assertFalse(MapOps.castListOfMaps(bad.get("risk_list")).isEmpty());
    }

    @Test
    void interface8_invalid_json() {
        Map<String, Object> r = sim.billingRulesVerify(Map.of("config_json", "bad"));
        assertEquals("3001", r.get("resultCode"));
    }

    /* ================= 接口9/10 审批 ================= */

    @Test
    void interface9_not_confirmed_gate() {
        Map<String, Object> r = sim.approvalSubmit(Map.of(
                "product_id", "P1", "report_url", "http://x", "approve_confirmed", "false"));
        assertEquals("NOT_CONFIRMED", r.get("status"));
    }

    @Test
    void interface9_submit_idempotent_and_queryable() {
        Map<String, Object> first = sim.approvalSubmit(Map.of(
                "product_id", "P20260001", "report_url", "http://r/1", "approve_confirmed", "true"));
        assertEquals("0", first.get("resultCode"));
        String approvalId = MapOps.str(first.get("approval_id"));
        assertFalse(approvalId.isBlank());

        Map<String, Object> second = sim.approvalSubmit(Map.of(
                "product_id", "P20260001", "report_url", "http://r/2", "approve_confirmed", "true"));
        assertEquals(approvalId, second.get("approval_id"));

        Map<String, Object> byId = sim.approvalStatus(Map.of("approval_id", approvalId));
        assertEquals("审批中", byId.get("status"));
        assertNotNull(byId.get("current_node"));

        Map<String, Object> byProduct = sim.approvalStatus(Map.of("product_id", "P20260001"));
        assertEquals(approvalId, byProduct.get("approval_id"));
    }

    /* ================= 接口11/12 监控与告警 ================= */

    @Test
    void interface11_monitor_and_alert_loopback() {
        Map<String, Object> sent = sim.sendAlert(Map.of(
                "product_id", "900102308", "alarm_level", "high", "content", "计费异常"));
        assertEquals("0", sent.get("resultCode"));
        String alertId = MapOps.str(sent.get("alert_id"));

        Map<String, Object> r = sim.productMonitor(Map.of("product_id", "900102308"));
        assertEquals("0", r.get("resultCode"));
        assertEquals("0", r.get("error_count"));
        List<Map<String, Object>> alarms = MapOps.castListOfMaps(r.get("alarm_list"));
        assertTrue(alarms.stream().anyMatch(a -> alertId.equals(a.get("alarm_id"))));
    }

    @Test
    void interface11_monitor_error_injection() {
        sim.injectMonitorError("900102308", true);
        Map<String, Object> r = sim.productMonitor(Map.of("product_id", "900102308"));
        assertEquals("3", r.get("error_count"));
    }

    @Test
    void interface12_invalid_level() {
        Map<String, Object> r = sim.sendAlert(Map.of(
                "product_id", "900102308", "alarm_level", "urgent", "content", "x"));
        assertEquals("40011", r.get("resultCode"));
    }

    /* ================= 接口13/14 节点结果 ================= */

    @Test
    void interface13_invalid_key_rejected() {
        Map<String, Object> r = sim.saveNodeResult(Map.of(
                "key", "BAD KEY!", "result_json", "{}"));
        assertEquals(5002, r.get("code"));
    }

    @Test
    void interface13_14_save_and_query_roundtrip() {
        String key = "PLAN20260912001";
        Map<String, Object> save = sim.saveNodeResult(Map.of(
                "key", key, "result_json", "{\"step\":1}", "status", "ok"));
        assertEquals(0, save.get("code"));

        Map<String, Object> query = sim.queryNodeResult(Map.of("key", key));
        assertEquals(0, query.get("code"));
        Map<String, Object> record = MapOps.castMap(query.get("record"));
        assertEquals(key, record.get("key"));

        String execKey = "EXE202609120001_STAGE1";
        assertEquals(0, sim.saveNodeResult(Map.of(
                "key", execKey, "result_json", "{}")).get("code"));
        assertEquals(0, sim.queryNodeResult(Map.of("key", execKey)).get("code"));
    }

    @Test
    void interface14_not_found() {
        Map<String, Object> r = sim.queryNodeResult(Map.of("key", "PLAN20990101001"));
        assertEquals(5005, r.get("code"));
    }

    /* ---------------- 工具 ---------------- */

    /** 通过反射拿到 testTasks 中的任务对象，便于测试中快进 done 状态（避免真实等待 60~90s）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> testTask(String globalId) {
        try {
            var field = OfferSimV16Service.class.getDeclaredField("testTasks");
            field.setAccessible(true);
            Map<String, Map<String, Object>> tasks = (Map<String, Map<String, Object>>) field.get(sim);
            return tasks.get(globalId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
