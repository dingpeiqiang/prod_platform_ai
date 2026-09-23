package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.common.MapOps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

/**
 * B1 稽核 Excel 数据源测试（统一方案 6.6）：按 req_id 还原 offers[]/relations[]。
 * <p>
 * 覆盖：config 节点内嵌 offers[] 时逐品生成行（一品多品）、内嵌 relations[] 归一、
 * 单销售品（无 offers[] 数组）向后兼容。
 */
@DisplayName("稽核 Excel 数据源")
class AuditRequirementSourceTest {

    private static final String REQ_ID = "PLAN20260915160844328";

    private static AuditRequirementSource sourceWith(String configJson, String demandJson) {
        NodeResultService svc = Mockito.mock(NodeResultService.class);
        if (configJson != null) {
            Mockito.when(svc.latestRecord(REQ_ID, "config"))
                    .thenReturn(Map.of("result_json", configJson));
        }
        if (demandJson != null) {
            Mockito.when(svc.latestRecord(REQ_ID, "requirement_report"))
                    .thenReturn(Map.of("result_json", demandJson));
        }
        return new AuditRequirementSource(svc);
    }

    @Test
    @DisplayName("config 内嵌 offers[] 时逐品生成 Excel 行（一品多品）")
    void multiOfferFromEmbeddedArray() {
        String config = """
                {"offers":[
                  {"offer_id":"900117029","offer_name":"5G畅享融合79元套餐202512","省级/市级*":"全省"},
                  {"offer_id":"900117030","offer_name":"5G畅享融合99元套餐202512","省级/市级*":"全省"}
                ]}""";
        String demand = """
                {"product_type":"融合","description":"一单清描述"}""";

        Map<String, Object> body = sourceWith(config, demand).resolve(REQ_ID);
        Assertions.assertNotNull(body);
        List<Map<String, Object>> offers = MapOps.castListOfMaps(body.get("offers"));
        Assertions.assertEquals(2, offers.size(), "应逐品生成两行");

        Map<String, Object> first = offers.get(0);
        Assertions.assertEquals("900117029", first.get("ofr_id"));
        Map<String, Object> f0 = MapOps.castMap(first.get("fields"));
        Assertions.assertEquals("5G畅享融合79元套餐202512", f0.get("套餐名称"));

        Map<String, Object> second = offers.get(1);
        Assertions.assertEquals("900117030", second.get("ofr_id"));
        Map<String, Object> f1 = MapOps.castMap(second.get("fields"));
        Assertions.assertEquals("5G畅享融合99元套餐202512", f1.get("套餐名称"));
        Assertions.assertEquals("一单清描述", f1.get("内容描述(套餐的关键信息，如资费价格、优惠折扣、账户/用户、包含的费用、业务等)"),
                "需求平面字段应兜底填充共享列");
    }

    @Test
    @DisplayName("内嵌 relations[] 归一为关联关系行")
    void relationsFromEmbeddedArray() {
        String config = """
                {"offer_id":"900117029","offer_name":"5G畅享融合79元套餐202512",
                 "relations":[
                   {"a_ofr_id":"900117029","a_name":"5G畅享融合79元套餐202512",
                    "relation":"100000","z_ofr_id":"7320110001600005","z_name":"5G体验10元副卡功能费201907"}
                 ]}""";

        Map<String, Object> body = sourceWith(config, null).resolve(REQ_ID);
        Assertions.assertNotNull(body);
        List<Map<String, Object>> offers = MapOps.castListOfMaps(body.get("offers"));
        Assertions.assertEquals(1, offers.size(), "无 offers[] 数组时回退单销售品");
        List<Map<String, Object>> relations = MapOps.castListOfMaps(body.get("relations"));
        Assertions.assertEquals(1, relations.size(), "应解析出一条关联关系");
        Assertions.assertEquals("7320110001600005", relations.get(0).get("z_ofr_id"));
        Assertions.assertEquals("100000", relations.get(0).get("relation"));
    }

    @Test
    @DisplayName("需求与配置均查无数据返回 null（调用方转 E35）")
    void noDataReturnsNull() {
        Assertions.assertNull(sourceWith(null, null).resolve(REQ_ID));
    }
}
