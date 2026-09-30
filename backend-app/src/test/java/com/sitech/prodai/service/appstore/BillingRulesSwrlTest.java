package com.sitech.prodai.service.appstore;

import com.sitech.prodai.ProdAiApplication;
import com.sitech.prodai.service.common.MapOps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

/**
 * wf_sub_05 资费校准 SWRL 接入测试：R-C09 固费区间命中并入 risk_list，并输出推理链（reasoning_chain）。
 * 单点校验 billingRulesVerifyBatch 的出参契约（逐品 + 整批聚合）。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@DisplayName("资费校准 SWRL 接入")
class BillingRulesSwrlTest {

    @Autowired
    private OfferSimV16Service sim;

    private Map<String, Object> batchOf(String configJson) {
        return sim.billingRulesVerifyBatch(Map.of(
                "req_id", "PLAN20260101000000005",
                "check_scene", "all",
                "offers", List.of(Map.of("offer_seq", 1, "offer_id", "900117099", "config_json", configJson))));
    }

    @Test
    @DisplayName("固费超上限（1000>599）→ R-C09 命中并入 risk_list，整批 pass=0")
    void feeAboveRangeHit() {
        Map<String, Object> body = batchOf("{\"offer_id\":\"900117099\",\"offer_name\":\"测试套餐\",\"monthly_fee\":1000,\"channelScope\":\"全渠道\"}");
        Assertions.assertNotNull(body.get("reasoning_chain"), "整批应含聚合推理链：" + body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> offers = (List<Map<String, Object>>) body.get("offers");
        Assertions.assertNotNull(offers);
        Map<String, Object> item = offers.get(0);
        Assertions.assertEquals("fail", item.get("status"), "存在风险时元素 status 应为 fail：" + item);
        Assertions.assertEquals("openllet-swrl", item.get("engine"), "SWRL 生效时 engine 应为 openllet-swrl：" + item);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> risks = (List<Map<String, Object>>) item.get("risk_list");
        Assertions.assertTrue(risks.stream().anyMatch(r -> "R-C09".equals(r.get("risk_type"))),
                "risk_list 应含 R-C09 命中：" + risks);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reasoning = (List<Map<String, Object>>) item.get("reasoning_chain");
        Assertions.assertTrue(reasoning.stream().anyMatch(r -> "R-C09".equals(r.get("ruleId"))),
                "推理链应含 R-C09：" + reasoning);
    }

    @Test
    @DisplayName("真实报文形态（product_config 为转义 JSON 字符串，plan_json 亦为字符串，固费 79 元）→ 无 R-C05/R-C09 假阳性")
    void feeFromEscapedProductConfig() {
        // 回归：wf_sub_05 实际入参形态——offers[] 元素内 product_config 是「序列化 JSON 字符串」，
        // 其内 plan_json 又是「序列化 JSON 字符串」（optionalInfo 直接在 plan_json 顶层）。
        // 不解包这两层字符串则月固费解析为空 → fixedFeeAmount=0 → R-C05/R-C09 全量误判。
        String planJson = "{\"baseInfo\":{\"prodPrcName\":\"5G畅享主套餐79元档\"},"
                + "\"optionalInfo\":{\"acctMonth\":{\"fixFee\":\"79\"},\"printContent\":{\"prcMonthFee\":\"79\"}},"
                + "\"req_id\":\"PLAN20260101000000005\"}";
        Map<String, Object> productConfig = new java.util.LinkedHashMap<>();
        productConfig.put("offer_id", "900117099");
        productConfig.put("offer_name", "5G畅享主套餐79元档");
        productConfig.put("plan_json", planJson);
        Map<String, Object> element = new java.util.LinkedHashMap<>();
        element.put("offer_seq", 1);
        element.put("offer_id", "900117099");
        element.put("product_config", simJson(productConfig));
        Map<String, Object> body = sim.billingRulesVerifyBatch(Map.of(
                "req_id", "PLAN20260101000000005",
                "check_scene", "all",
                "offers", List.of(element)));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> offers = (List<Map<String, Object>>) body.get("offers");
        Map<String, Object> item = offers.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> risks = (List<Map<String, Object>>) item.get("risk_list");
        Assertions.assertTrue(risks.stream().noneMatch(r -> "R-C05".equals(r.get("risk_type"))),
                "固费 79 元应正解析，不应命中 R-C05：" + risks);
        Assertions.assertTrue(risks.stream().noneMatch(r -> "R-C09".equals(r.get("risk_type"))),
                "固费 79 元在区间内，不应命中 R-C09：" + risks);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reasoning = (List<Map<String, Object>>) item.get("reasoning_chain");
        Assertions.assertTrue(reasoning.stream()
                        .filter(r -> "R-C09".equals(r.get("ruleId")))
                        .anyMatch(r -> MapOps.str(r.get("condition")).contains("79")),
                "R-C09 通过行应带解析出的固费取值 79：" + reasoning);
    }

    private static String simJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("固费在区间内（199）→ 无 R-C09 命中，pass=1，推理链含 R-C08 通过且逐规则补通过行")
    void feeWithinRangePass() {
        Map<String, Object> body = batchOf("{\"offer_id\":\"900117099\",\"offer_name\":\"测试套餐\",\"messageRootKey\":\"MAIN_PKG\",\"monthly_fee\":199,\"channelScope\":\"全渠道\"}");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> offers = (List<Map<String, Object>>) body.get("offers");
        Map<String, Object> item = offers.get(0);
        Assertions.assertEquals("success", item.get("status"), "无风险时元素 status 应为 success：" + item);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> risks = (List<Map<String, Object>>) item.get("risk_list");
        Assertions.assertTrue(risks.stream().noneMatch(r -> "R-C09".equals(r.get("risk_type"))),
                "无风险时不应含 R-C09：" + risks);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reasoning = (List<Map<String, Object>>) item.get("reasoning_chain");
        Assertions.assertTrue(reasoning.stream().anyMatch(r -> "R-C08".equals(r.get("ruleId"))),
                "合规通过时推理链应含 R-C08：" + reasoning);
        for (String rid : List.of("R-C03", "R-C05", "R-C07", "R-C09")) {
            Assertions.assertTrue(reasoning.stream().anyMatch(r -> rid.equals(r.get("ruleId"))),
                    "通过场景推理链应补齐未命中规则 " + rid + " 的通过行：" + reasoning);
        }
        Assertions.assertTrue(reasoning.stream()
                        .filter(r -> "R-C09".equals(r.get("ruleId")))
                        .anyMatch(r -> MapOps.str(r.get("condition")).contains("199")),
                "R-C09 通过行应带实际固费取值：" + reasoning);
    }
}
