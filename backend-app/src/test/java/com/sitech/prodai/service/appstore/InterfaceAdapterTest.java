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
        Assertions.assertEquals("0", progress.get("resultCode"));
        Assertions.assertNotNull(progress.get("totalSteps"));
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
    @DisplayName("传参 mode=auto：回落 yml 配置（dev=mock），成功且无回落标注")
    void overrideAutoFallsBackToYml() {
        Map<String, Object> body = interfaceFacade.auditAsyncNotify(Map.of(
                "sales_id", "900117029",
                "mode", "auto"));
        Assertions.assertEquals(Boolean.TRUE, body.get("success"));
        Assertions.assertNull(body.get("note"));
    }
}
