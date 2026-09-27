package com.sitech.prodai.service.appstore;

import com.sitech.prodai.ProdAiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

/**
 * 审批批量端点契约测试（《多商品批量接口契约.md》§3.6/§3.7）：
 * 锁定「一个需求单对应一张审批单（工单级 approval_id，仅顶层）；一张审批单含多商品」口径。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@DisplayName("审批批量端点契约")
class ApprovalBatchContractTest {

    @Autowired
    private OfferSimV16Service sim;

    @Autowired
    private NodeResultService nodeResult;

    @Test
    @DisplayName("§3.6 审批推送：approval_id 为工单级单值仅顶层，元素不再携带；整批 status=SUCCESS")
    void submitApprovalTopLevelApprovalId() {
        String reqId = "PLAN20260101000000001";
        Map<String, Object> body = sim.approvalSubmitBatch(Map.of(
                "req_id", reqId,
                "approval_type", "requirement",
                "approve_confirmed", "true",
                "offers", List.of(
                        Map.of("offer_seq", 1, "offer_id", "900117029", "report_url", "http://r/1"),
                        Map.of("offer_seq", 2, "offer_id", "900117030", "report_url", "http://r/2"))));

        Assertions.assertNotNull(body.get("approval_id"), "工单级 approval_id 应在顶层：" + body);
        Assertions.assertEquals("SUCCESS", body.get("status"), "提交成功整批应为 SUCCESS：" + body);
        Assertions.assertEquals(reqId, body.get("req_id"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> offers = (List<Map<String, Object>>) body.get("offers");
        Assertions.assertEquals(2, offers.size());
        for (Map<String, Object> o : offers) {
            Assertions.assertEquals("success", o.get("status"), "元素态应为 success（审批中归一）：" + o);
            Assertions.assertFalse(o.containsKey("approval_id"), "元素不应再携带 approval_id：" + o);
        }
    }

    @Test
    @DisplayName("§3.7 审批进度：按 req_id 返回全量商品，各元素冗余携带同一工单级 approval_id")
    void statusReturnsAllOffersSameApprovalId() {
        String reqId = "PLAN20260101000000002";
        // 预置该需求的商品清单（config 环节结果），供按 req_id 解析「全量商品」
        nodeResult.save(reqId, "config",
                "{\"offers\":[{\"offer_seq\":1,\"offer_id\":\"900117031\"},{\"offer_seq\":2,\"offer_id\":\"900117032\"}]}",
                "ok");
        sim.approvalSubmitBatch(Map.of(
                "req_id", reqId,
                "approval_type", "requirement",
                "approve_confirmed", "true",
                "offers", List.of(
                        Map.of("offer_seq", 1, "offer_id", "900117031", "report_url", "http://r/1"),
                        Map.of("offer_seq", 2, "offer_id", "900117032", "report_url", "http://r/2"))));

        Map<String, Object> body = sim.approvalStatusBatch(Map.of("req_id", reqId));
        Assertions.assertEquals(reqId, body.get("req_id"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> offers = (List<Map<String, Object>>) body.get("offers");
        Assertions.assertNotNull(offers);
        Assertions.assertEquals(2, offers.size(), "应按 req_id 返回全量商品：" + body);

        Object firstApprovalId = offers.get(0).get("approval_id");
        Assertions.assertNotNull(firstApprovalId, "元素应携带工单级 approval_id：" + offers.get(0));
        for (Map<String, Object> o : offers) {
            Assertions.assertEquals(firstApprovalId, o.get("approval_id"),
                    "同一需求下各元素应携带相同 approval_id：" + offers);
        }
    }
}
