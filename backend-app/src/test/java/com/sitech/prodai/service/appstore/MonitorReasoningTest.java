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
}
