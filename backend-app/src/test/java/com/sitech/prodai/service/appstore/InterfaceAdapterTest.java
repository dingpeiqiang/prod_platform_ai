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
 * 产销品场景接口适配层测试（统一方案 §4.5.4/§4.5.5）：
 * 以 dev（mode=mock）装配，断言 B1~B4 内置确定性 mock 出参与 C1~C4 经门面归一后结构，
 * 保证 mock 模式可独立跑通且与 real 归一后契约同构。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@DisplayName("产销品场景接口适配层")
class InterfaceAdapterTest {

    @Autowired
    private InterfaceFacade interfaceFacade;

    @Autowired
    private OfferSeedService offerSeedService;

    @Test
    @DisplayName("dev 装配生效模式为 mock")
    void modeIsMock() {
        Assertions.assertEquals("mock", interfaceFacade.mode());
    }

    @Test
    @DisplayName("B1 上传：内置确定性 mock 返回 file_id 与 4 档 sales_id（900117029~032）")
    void b1UploadDeterministic() {
        Map<String, Object> body = interfaceFacade.auditFileUpload(
                Map.of("fileName", "audit.xlsx"));
        Assertions.assertEquals("0", body.get("resultCode"), "应成功：" + body.get("resultMsg"));
        Assertions.assertNotNull(body.get("file_id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> salesId = (Map<String, Object>) body.get("sales_id");
        Assertions.assertNotNull(salesId);
        Assertions.assertEquals(4, salesId.size(), "内置 mock 应为 4 档");

        boolean hasAll = salesId.values().stream()
                .map(v -> (Map<?, ?>) v)
                .map(m -> String.valueOf(m.get("offerId")))
                .allMatch(id -> List.of("900117029", "900117030", "900117031", "900117032").contains(id));
        Assertions.assertTrue(hasAll, "应含 900117029~032 四档：" + salesId.keySet());
    }

    @Test
    @DisplayName("B1 上传：传入真实 offers[] 时 sales_id 按真实销售品构造（不再用硬编码 900117029~032）")
    void b1UploadUsesRealOffers() {
        Map<String, Object> body = interfaceFacade.auditFileUpload(Map.of(
                "fileName", "audit.xlsx",
                "offers", List.of(
                        Map.of("ofr_id", "901000111", "offer_name", "真实套餐A"),
                        Map.of("ofr_id", "901000222", "offer_name", "真实套餐B"))));
        Assertions.assertEquals("0", body.get("resultCode"), "应成功：" + body.get("resultMsg"));
        @SuppressWarnings("unchecked")
        Map<String, Object> salesId = (Map<String, Object>) body.get("sales_id");
        Assertions.assertNotNull(salesId);
        Assertions.assertEquals(2, salesId.size(), "应按真实 offers 出 2 档：" + salesId);
        Assertions.assertTrue(salesId.containsKey("真实套餐A"));
        Assertions.assertTrue(salesId.containsKey("真实套餐B"));
        boolean noHardcoded = salesId.values().stream()
                .map(v -> (Map<?, ?>) v)
                .map(m -> String.valueOf(m.get("offerId")))
                .allMatch(id -> !List.of("900117029", "900117030", "900117031", "900117032").contains(id));
        Assertions.assertTrue(noHardcoded, "不应回退硬编码四档：" + salesId);
    }

    @Test
    @DisplayName("B2 通知：返回 request_id 与 sales_id")
    void b2Notify() {
        Map<String, Object> body = interfaceFacade.auditAsyncNotify(
                Map.of("sales_id", "900117029", "location", "0", "sheet_name", "套餐"));
        Assertions.assertEquals(Boolean.TRUE, body.get("success"));
        Assertions.assertNotNull(body.get("request_id"));
        Assertions.assertEquals("900117029", body.get("sales_id"));
    }

    @Test
    @DisplayName("B2 缺 sales_id 返回失败")
    void b2NotifyMissingParam() {
        Map<String, Object> body = interfaceFacade.auditAsyncNotify(Map.of());
        Assertions.assertEquals("1", body.get("resultCode"));
    }

    @Test
    @DisplayName("B3 插入：返回 resultCode/resultMsg")
    void b3Insert() {
        Map<String, Object> body = interfaceFacade.auditResultInsert(
                Map.of("records", List.of(Map.of("offerId", "900117029", "requestId", "req1"))));
        Assertions.assertEquals("0", body.get("resultCode"));
        Assertions.assertTrue(String.valueOf(body.get("resultMsg")).contains("1"));
    }

    @Test
    @DisplayName("B4 SQL 查询：execute_sql_resp[] 含 isAllFinished/totalCount，条数按 params 计；查完成态 sqlId 两阶段轮询 N→N→Y")
    void b4SqlQuery() {
        // sqlId=20260626001 为「查完成状态」：mock 按查询次数模拟 处理中→完成（两阶段轮询，N→N→Y）
        String[] expectedFinished = {"N", "N", "Y"};
        for (int i = 0; i < expectedFinished.length; i++) {
            Map<String, Object> body = interfaceFacade.auditSqlQuery(Map.of(
                    "sql_id", "20260626001",
                    "params", "requestIds:pollr1,pollr2,pollr3"));
            Assertions.assertEquals("0", body.get("resultCode"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("execute_sql_resp");
            Assertions.assertNotNull(rows);
            Assertions.assertEquals(1, rows.size());
            Assertions.assertEquals("3", rows.get(0).get("totalCount"));
            Assertions.assertEquals(expectedFinished[i], rows.get(0).get("isAllFinished"),
                    "第 " + (i + 1) + " 次查询的完成态不符：" + rows.get(0));
        }
    }

    @Test
    @DisplayName("A1 相似度：snake_case 入参兼容（business_desc）返回 similarOfferList")
    void a1SimilarSnakeCaseInput() {
        Map<String, Object> body = interfaceFacade.similarOfferQuery(Map.of(
                "business_desc", "新增5G畅享融合套餐含副卡"));
        Assertions.assertEquals("0", body.get("resultCode"), "应成功：" + body.get("resultMsg"));
        Assertions.assertNotNull(body.get("similarOfferList"));
    }

    @Test
    @DisplayName("C1~C4 链路：发起→场景→进度，经门面归一结构一致（存量种子销售品 900113046）")
    void c1ToC4Chain() {
        Map<String, Object> start = interfaceFacade.testOfferStart(Map.of("offer_id", "900113046"));
        String globalId = String.valueOf(start.get("globalId"));
        Assertions.assertNotNull(globalId);
        Assertions.assertTrue(globalId.startsWith("50"), "globalId 应以 50 开头：" + globalId);

        Map<String, Object> scenes = interfaceFacade.testScenes(Map.of("global_id", globalId));
        Assertions.assertEquals("0", scenes.get("resultCode"));
        Assertions.assertNotNull(scenes.get("testScenes"));

        Map<String, Object> progress = interfaceFacade.testProgress(Map.of("global_id", globalId));
        // C3 严格对齐 EOP 裸信封：{resultCode, resultObject:{...}, resultMsg}
        Assertions.assertEquals("0", progress.get("resultCode"));
        Object ro = progress.get("resultObject");
        Assertions.assertTrue(ro instanceof Map, "C3 出参应含 EOP resultObject：" + progress);
        Map<?, ?> progressBody = (Map<?, ?>) ro;
        Assertions.assertNotNull(progressBody.get("totalSteps"));
        Assertions.assertNotNull(progressBody.get("done"));
    }

    @Test
    @DisplayName("mock 兜底：已失效 globalId 合成终态报文（C3 done=true 全场景完成；C4 testScenes 全通过，不下发「查无对应测试任务」）")
    void mockFallbackSynthesizesTerminalReplyForLostTask() {
        String lostGlobalId = "50202609282105416868245365";
        Map<String, Object> progress = interfaceFacade.testProgress(Map.of("global_id", lostGlobalId));
        Assertions.assertEquals("0", progress.get("resultCode"),
                "mock 模式不应返回查无任务：" + progress.get("resultMsg"));
        Map<?, ?> progressBody = (Map<?, ?>) progress.get("resultObject");
        Assertions.assertNotNull(progressBody, "C3 应透传 EOP resultObject：" + progress);
        Assertions.assertEquals(Boolean.TRUE, progressBody.get("done"), "兜底任务应为终态：" + progressBody);
        Assertions.assertEquals(progressBody.get("totalSceneCount"), progressBody.get("finishedSceneCount"),
                "兜底任务应为全场景完成：" + progressBody);

        Map<String, Object> result = interfaceFacade.testResult(Map.of("global_id", lostGlobalId));
        Assertions.assertEquals("0", result.get("resultCode"),
                "mock 模式结果查询应成功：" + result.get("resultMsg"));
        Map<?, ?> resultBody = (Map<?, ?>) result.get("resultObject");
        Assertions.assertNotNull(resultBody, "C4 应透传 EOP resultObject：" + result);
        List<Map<String, Object>> scenes = MapOps.castListOfMaps(resultBody.get("testScenes"));
        Assertions.assertFalse(scenes.isEmpty(), "应返回逐场景结果：" + resultBody);
        for (Map<String, Object> scene : scenes) {
            for (Map<String, Object> point : MapOps.castListOfMaps(scene.get("testCasePointResults"))) {
                Assertions.assertEquals("0", MapOps.str(point.get("resultCode")),
                        "兜底任务测点应全部通过：" + point);
            }
        }
    }

    @Test
    @DisplayName("传参覆盖 mode=mock：即使 yml=mock 亦明确走 mock，成功且无回落标注")
    void overrideMockForcesMock() {
        Map<String, Object> body = interfaceFacade.similarOfferQuery(Map.of(
                "business_desc", "新增5G畅享融合套餐含副卡",
                "mode", "mock"));
        Assertions.assertEquals("0", body.get("resultCode"), "应成功：" + body.get("resultMsg"));
        Assertions.assertNull(body.get("note"), "mock 直走不应有回落标注");
    }

    @Test
    @DisplayName("传参覆盖 mode=real：经 EOP 网关调用；网关不可达/鉴权失败严格报错（REAL_CALL_FAILED，不回退 mock）")
    void overrideRealFailsWithoutFallback() {
        Map<String, Object> body = interfaceFacade.auditSqlQuery(Map.of(
                "sql_id", "20260626001",
                "params", "requestIds:r1",
                "mode", "real"));
        Assertions.assertEquals("REAL_CALL_FAILED", body.get("resultCode"), "real 失败应返回错误码：" + body);
        Assertions.assertNull(body.get("note"), "不得回退 mock 附标注");
        Assertions.assertNull(body.get("execute_sql_resp"), "不得返回模拟报文");
        Assertions.assertNotNull(body.get("resultMsg"), "应带失败原因（含 EOP 原始错误或网关地址）");
    }

    @Test
    @DisplayName("传参覆盖 mode=auto：回落 yml 配置（dev=mock），成功且无回落标注")
    void overrideAutoFallsBackToYml() {
        Map<String, Object> body = interfaceFacade.auditAsyncNotify(Map.of(
                "sales_id", "900117029",
                "mode", "auto"));
        Assertions.assertEquals(Boolean.TRUE, body.get("success"));
        Assertions.assertNull(body.get("note"));
    }

    @Test
    @DisplayName("衍生兜底：未收录/缺模板销售品按模板从存量同类型产品生成全量配置 JSON（offerModel）")
    void deriveOfferModelForUnknownOffer() {
        // 构造一个未收录销售品，请求某模板 → 应衍生同类型存量报文明文（非空、模板同构）
        Map<String, Object> derived = offerSeedService.reportOfOrDerive("000000000", "familyBasePrc");
        Assertions.assertFalse(derived.isEmpty(), "未收录品应能按模板衍生出同类型报文");
        Assertions.assertTrue(derived.containsKey("baseInfo"), "衍生报文应为模板同构（含 baseInfo）：" + derived.keySet());
    }

    @Test
    @DisplayName("衍生兜底：存量品优先取自身该模板报文，不做同类型替换")
    void preferOwnModelOverDonor() {
        Map<String, Object> own = offerSeedService.reportOf("900102308", "personMainPrc");
        Map<String, Object> resolved = offerSeedService.reportOfOrDerive("900102308", "personMainPrc");
        Assertions.assertNotNull(own);
        Assertions.assertEquals(own, resolved, "存量品命中时应原样返回自身报文");
    }

    @Test
    @DisplayName("A1 相似度：传 templateId 时返回项均含 offerModel 全量配置 JSON")
    void a1SimilarCarriesOfferModel() {
        Map<String, Object> body = interfaceFacade.similarOfferQuery(Map.of(
                "business_desc", "新增5G畅享融合套餐含宽带副卡高清",
                "templateId", "familyBasePrc"));
        Assertions.assertEquals("0", body.get("resultCode"), "应成功：" + body.get("resultMsg"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("similarOfferList");
        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty());
        for (Map<String, Object> row : list) {
            Assertions.assertTrue(row.containsKey("offerModel"),
                    "每项应携带衍生补全的 offerModel：" + row.get("similarOfferId"));
        }
    }

    @Test
    @DisplayName("A1 相似度：personMainPrc（个人主套餐）模板排除融合品，不得命中融合套餐900102307")
    void a1SimilarPersonMainPrcExcludesFusion() {
        Map<String, Object> body = interfaceFacade.similarOfferQuery(Map.of(
                "business_desc", "新增5G-C套餐299元，12个月有效期，次月1日生效，含220GB全国通用流量与2000分钟全国通话",
                "templateId", "personMainPrc"));
        Assertions.assertEquals("0", body.get("resultCode"), "应成功：" + body.get("resultMsg"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("similarOfferList");
        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty(), "个人主套餐模板应仍能命中同类型单品/基础套餐");
        for (Map<String, Object> row : list) {
            String id = MapOps.str(row.get("similarOfferId"));
            Assertions.assertFalse("900102307".equals(id), "不应命中融合套餐 900102307：" + row);
            Assertions.assertFalse(MapOps.str(row.get("similarityDesc")).contains("融合"),
                    "相似命中描述不应含融合子类：" + row);
        }
        // 主推荐应为单品/基础（同步价档 299 元），且不出融合组 offer_group
        Assertions.assertNull(body.get("offer_group"),
                "个人主套餐模板命中单品不应下发 offer_group 融合组：" + body.get("offer_group"));
        String best = MapOps.str(((Map<?, ?>) body.get("similarOffer")).get("similarOfferId"));
        Assertions.assertEquals("900102310", best, "299元单品套餐 900102310 应为最佳匹配");
    }

    @Test
    @DisplayName("A1 相似度：familyBasePrc（家庭/融合）模板仍允许命中融合套餐并下发 offer_group")
    void a1SimilarFamilyBaseAllowsFusion() {
        Map<String, Object> body = interfaceFacade.similarOfferQuery(Map.of(
                "business_desc", "新增5G-A融合套餐299元，含宽带与天翼高清",
                "templateId", "familyBasePrc"));
        Assertions.assertEquals("0", body.get("resultCode"), "应成功：" + body.get("resultMsg"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("similarOfferList");
        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty());
        // 主推荐应允许为融合套餐，且可下发 offer_group
        String best = MapOps.str(((Map<?, ?>) body.get("similarOffer")).get("similarOfferId"));
        Assertions.assertTrue(List.of("900102307", "900113046", "900102306", "900102313", "900113044").contains(best),
                "家庭/融合模板主推荐应可为融合品：" + best);
    }
}
