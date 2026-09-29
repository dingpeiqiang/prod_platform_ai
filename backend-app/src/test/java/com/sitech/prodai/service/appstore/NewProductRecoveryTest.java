package com.sitech.prodai.service.appstore;

import com.sitech.prodai.ProdAiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

/**
 * 新增销售品档案重启兜底回归（OfferSimV16Service.recoverNewProductProfile）：
 * 后端重启/多实例导致进程内 newProductArchive 丢失后，发起测试应能依据持久化的
 * config 环节结果重建档案，不得误报「销售品未收录」。
 *
 * <p>关键点：config 环节持久化的 result_json 为 V3.12 多商品批量信封
 * {@code {req_id, offers:[{offer_seq, offer_id, product_config}]}}，offer_id 位于
 * offers[] 元素内（顶层不带），兜底逻辑须遍历 offers[] 匹配并取内嵌 plan。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@DisplayName("新增销售品档案重启兜底")
class NewProductRecoveryTest {

    /** 未收录于 seed_offers.json 的新增品编码（900 前缀，避与存量 18 品冲突）。 */
    private static final String NEW_OFFER_ID = "900888001";

    @Autowired
    private OfferSimV16Service sim;

    @Autowired
    private NodeResultService nodeResult;

    @Test
    @DisplayName("批量信封 config 结果：offers[] 内 offer_id 命中即可重建并发起测试（不报未收录）")
    void recoverFromBatchEnvelopeOffers() {
        String reqId = "PLAN20260202000000001";
        // config 环节持久化结果为多商品信封，offer_id 埋在 offers[] 元素内（顶层无 offer_id）
        String envelope = "{\"req_id\":\"" + reqId + "\",\"offers\":[{\"offer_seq\":1,"
                + "\"offer_id\":\"" + NEW_OFFER_ID + "\",\"product_config\":\"{\\\"offer_id\\\":\\\""
                + NEW_OFFER_ID + "\\\",\\\"plan_json\\\":{\\\"套餐名称\\\":\\\"回归测试套餐\\\","
                + "\\\"套餐档位\\\":\\\"199元/月\\\",\\\"req_id\\\":\\\"" + reqId + "\\\"}}\"}]}";
        nodeResult.save(reqId, "config", envelope, "ok");

        Map<String, Object> body = sim.testOfferStart(Map.of("offerId", NEW_OFFER_ID));

        Assertions.assertNotEquals("4001", MapOpsStr.of(body.get("resultCode")),
                "批量信封内 offer_id 命中，不应报销售品未收录：" + body);
        Assertions.assertNotNull(body.get("globalId"), "应成功发起测试返回 globalId：" + body);
    }

    @Test
    @DisplayName("单品平面 config 结果：顶层 offer_id 命中仍可重建（兼容旧形态）")
    void recoverFromFlatConfig() {
        String reqId = "PLAN20260202000000002";
        String flat = "{\"offer_id\":\"900888002\",\"plan_json\":{\"套餐名称\":\"平面回归套餐\","
                + "\"套餐档位\":\"99元/月\",\"req_id\":\"" + reqId + "\"}}";
        nodeResult.save(reqId, "config", flat, "ok");

        Map<String, Object> body = sim.testOfferStart(Map.of("offerId", "900888002"));

        Assertions.assertNotEquals("4001", MapOpsStr.of(body.get("resultCode")),
                "单品平面形态顶层 offer_id 命中，不应报销售品未收录：" + body);
        Assertions.assertNotNull(body.get("globalId"), "应成功发起测试返回 globalId：" + body);
    }

    /** 仅测试内联用：安全取字符串（避免为一行引入额外 import）。 */
    private static final class MapOpsStr {
        private static String of(Object v) {
            return v == null ? "" : String.valueOf(v);
        }
    }
}
