package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.ProdAiApplication;
import com.sitech.prodai.controller.AppStoreV16Controller;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * wf_sub_07 监控运维回归：
 * 商品名称应取自 plan.baseInfo.prodPrcName，不得兜底成「新增销售品&lt;id&gt;」。
 * （对应根因：buildOfferProfile 名称来源不含 baseInfo.prodPrcName）
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@DisplayName("监控运维：商品名称来源")
class MonitorReasoningTest {

    private static final String REQ_ID = "PLAN20260929000000077";
    private static final String OFFER_ID = "900917168";
    private static final String OFFER_NAME = "5G畅享79元套餐";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private OfferSimV16Service sim;

    @Autowired
    private AppStoreV16Controller controller;

    /** 建档：套餐名仅存在于 baseInfo.prodPrcName（无 fields[套餐名称]），复现名称丢失根因。
     *  plan 不带 offer_id（走新增产品既有链路），offer_id 由 req 注入（对齐 wf_sub_02 注入形态）。 */
    private void setupOffer() throws Exception {
        Map<String, Object> baseInfo = new LinkedHashMap<>();
        baseInfo.put("prodPrcName", OFFER_NAME);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("req_id", REQ_ID);
        plan.put("baseInfo", baseInfo);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("req_id", REQ_ID);
        req.put("offer_id", OFFER_ID);
        req.put("plan_json", MAPPER.writeValueAsString(plan));
        sim.saveProductConfig(req, "http://localhost");
    }

    @Test
    @DisplayName("监控查询商品名称取自 baseInfo.prodPrcName")
    void offerNameFromBaseInfo() throws Exception {
        setupOffer();
        Map<String, Object> body = sim.productMonitor(Map.of("offer_id", OFFER_ID));
        Assertions.assertEquals(OFFER_NAME, String.valueOf(body.get("offer_name")),
                "offer_name 应取 baseInfo.prodPrcName：" + body);
    }

    @Test
    @DisplayName("商品名不因名称缺失而兜底成新增销售品<id>")
    void offerNameNotFallback() throws Exception {
        setupOffer();
        Map<String, Object> body = sim.productMonitor(Map.of("offer_id", OFFER_ID));
        Assertions.assertFalse(String.valueOf(body.get("offer_name")).startsWith("新增销售品"),
                "不应兜底成「新增销售品<id>」：" + body.get("offer_name"));
    }

    @Test
    @DisplayName("监控聚合分析返回本体推理依据 reasoning_chain（R-E00~E04）")
    void analyzeReturnsReasoningChain() throws Exception {
        setupOffer();
        Map<String, Object> body = controller.monitorAnalyze(Map.of("offer_id", OFFER_ID));
        Object rc = body.get("reasoning_chain");
        Assertions.assertTrue(rc instanceof List, "应返回 reasoning_chain 数组：" + body);
        List<?> rows = (List<?>) rc;
        Assertions.assertFalse(rows.isEmpty(), "reasoning_chain 不应为空");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) rows.get(0);
        Assertions.assertNotNull(first.get("ruleId"), "推理行应含 ruleId");
        Assertions.assertNotNull(first.get("conclusion"), "推理行应含 conclusion");
    }

    @Test
    @DisplayName("监控查询输出多维指标概览（收入/流失/ARPU/绑定率等）与阈值引用")
    void monitorReturnsEnrichedMetrics() throws Exception {
        setupOffer();
        Map<String, Object> body = sim.productMonitor(Map.of("offer_id", OFFER_ID));
        Object metrics = body.get("metrics");
        Assertions.assertTrue(metrics instanceof List, "应返回 metrics 数组：" + body.keySet());
        List<?> rows = (List<?>) metrics;
        Assertions.assertTrue(rows.size() >= 8, "指标数应≥8（对齐 metrics_registry）：" + rows.size());
        boolean hasRevenue = false;
        for (Object o : rows) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            Assertions.assertNotNull(m.get("value"), "指标应含 value");
            Assertions.assertNotNull(m.get("delta_pct"), "指标应含 delta_pct");
            Assertions.assertNotNull(m.get("threshold_ref"), "指标应含 threshold_ref（本体规则引用）");
            Assertions.assertNotNull(m.get("anomaly"), "指标应含 anomaly 标记");
            if ("revenue".equals(m.get("metric"))) {
                hasRevenue = true;
            }
        }
        Assertions.assertTrue(hasRevenue, "应包含 revenue 收入指标");
    }

    @Test
    @DisplayName("注入监控异常后输出指标异常数据与告警提示")
    void analyzeReturnsMetricAnomaliesAndHints() throws Exception {
        setupOffer();
        sim.injectMonitorError(OFFER_ID, true);
        Map<String, Object> body = controller.monitorAnalyze(Map.of("offer_id", OFFER_ID));
        Object anomalies = body.get("metric_anomalies");
        Assertions.assertTrue(anomalies instanceof List, "应返回 metric_anomalies 数组：" + body.keySet());
        Assertions.assertFalse(((List<?>) anomalies).isEmpty(), "注入异常后指标异常不应为空");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstAnomaly = (Map<String, Object>) ((List<?>) anomalies).get(0);
        Assertions.assertNotNull(firstAnomaly.get("metric"), "异常行应含 metric");
        Assertions.assertNotNull(firstAnomaly.get("condition"), "异常行应含 condition");

        Object hints = body.get("alert_hints");
        Assertions.assertTrue(hints instanceof List, "应返回 alert_hints 数组");
        Assertions.assertFalse(((List<?>) hints).isEmpty(), "告警提示不应为空");
    }

    @Test
    @DisplayName("监控聚合分析返回本体优化建议 suggestions（R-O 规则）")
    void analyzeReturnsSuggestions() throws Exception {
        setupOffer();
        sim.injectMonitorError(OFFER_ID, true);
        Map<String, Object> body = controller.monitorAnalyze(Map.of("offer_id", OFFER_ID));
        Object suggestions = body.get("suggestions");
        Assertions.assertTrue(suggestions instanceof List, "应返回 suggestions 数组：" + body.keySet());
        List<?> rows = (List<?>) suggestions;
        Assertions.assertFalse(rows.isEmpty(), "优化建议不应为空");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) rows.get(0);
        Assertions.assertNotNull(first.get("ruleId"), "建议行应含 ruleId");
        Assertions.assertTrue(String.valueOf(first.get("ruleId")).startsWith("R-O"),
                "建议规则应为 R-O 前缀：" + first.get("ruleId"));
        Assertions.assertNotNull(first.get("advice"), "建议行应含 advice");
    }
}
