package com.sitech.prodai.service.appstore;

import com.sitech.prodai.ProdAiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动化测试结果轮询与落地服务契约测试（环节6b 后端化）。
 * <p>
 * 锁定：读 test_pending 取 globalId → 单次探测 C3（done==true 或 全部场景执行完）→ 终态查 C4 结果
 * → scene_failed==0 时写 node_name=test。mock 适配层「查无对应测试任务时合成终态全通过任务」的行为
 * 用于驱动完成分支。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@DisplayName("自动化测试轮询落地契约")
class TestPollServiceTest {

    @Autowired
    private TestPollService testPollService;

    @Autowired
    private NodeResultService nodeResult;

    @Autowired
    private InterfaceFacade interfaceFacade;

    /**
     * 生成当次运行唯一的 req_id（PLAN + yyyyMMddHHmmss + 3 位随机数 = 17 位数字），
     * 避免共享 H2 持久库跨运行污染断言。
     */
    private String uniqReqId() {
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = (int) (Math.random() * 1000);
        return "PLAN" + ts + String.format("%03d", rnd);
    }

    /** 预置测试发起临时态（含逐品 globalId），供 pollOnce 自查。 */
    private void seedTestPending(String reqId, String globalId) {
        nodeResult.save(reqId, TestPollService.NODE_TEST_PENDING,
                "{\"offers\":[{\"offer_seq\":1,\"offer_id\":\"900102308\",\"globalId\":\"" + globalId + "\"}]}",
                "pending");
    }

    @Test
    @DisplayName("无 test_pending：返回 pending 且不落 test")
    void pendingMissing() {
        String reqId = uniqReqId();
        Map<String, Object> out = testPollService.pollOnce(reqId);
        Assertions.assertEquals(TestPollService.STATE_PENDING, out.get("state"), "无发起记录应为处理中：" + out);
        Assertions.assertNull(nodeResult.latestRecord(reqId, TestPollService.NODE_TEST), "无发起记录不应落 test");
    }

    @Test
    @DisplayName("测试未完成：真实在途任务首次探测返回 pending，不落 test")
    void stillProcessing() {
        String reqId = uniqReqId();
        // 经适配层发起一个真实在途测试任务（mock 时长 20s），C3 首次必为 done=false
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("offer_id", "900102308");
        Map<String, Object> started = interfaceFacade.testOfferStart(start);
        String globalId = String.valueOf(started.get("globalId"));
        Assertions.assertFalse(globalId.isBlank() || "null".equals(globalId), "应成功发起测试任务：" + started);

        seedTestPending(reqId, globalId);
        Map<String, Object> out = testPollService.pollOnce(reqId);
        Assertions.assertEquals(TestPollService.STATE_PENDING, out.get("state"), "在途任务首次探测应为处理中：" + out);
        Assertions.assertNull(nodeResult.latestRecord(reqId, TestPollService.NODE_TEST), "未完成不应落 test");
    }

    @Test
    @DisplayName("测试完成通过：scene_failed==0 时写入 node_name=test")
    void completedAndPersistTest() {
        String reqId = uniqReqId();
        // globalId 内嵌旧时间戳（chars 2..16=yyyyMMddHHmmss），使 mock 依发起时刻派生的任务 elapsed>=duration
        // 立即终态（done=true），无需真实等待 20s；offer_id 取种子库销售品保证可解析、全场景通过。
        String globalId = "502020010100000000000000";
        seedTestPending(reqId, globalId);
        Map<String, Object> out = testPollService.pollOnce(reqId);
        Assertions.assertNotNull(out);
        Assertions.assertEquals(TestPollService.STATE_DONE, out.get("state"), "应完成：" + out);
        Assertions.assertEquals(TestPollService.VERDICT_PASS, out.get("verdict"), "mock 结论应通过：" + out);
        Assertions.assertEquals(Boolean.TRUE, out.get("saved"), "通过时应标记已落 test：" + out);
        Assertions.assertEquals("0", String.valueOf(out.get("scene_failed")), "通过时失败场景应为 0：" + out);

        Map<String, Object> test = nodeResult.latestRecord(reqId, TestPollService.NODE_TEST);
        Assertions.assertNotNull(test, "通过后应存在 test 记录");
        Assertions.assertEquals("ok", test.get("status"));
        String json = String.valueOf(test.get("result_json"));
        Assertions.assertTrue(json.contains("\"pass\":\"1\""), "test envelope 应含 pass=1：" + json);
        Assertions.assertTrue(json.contains("test_report"), "test envelope 应含审批看板所需 test_report：" + json);
        Assertions.assertTrue(json.contains("\"offers\""), "test envelope 应含逐品摘要 offers：" + json);
    }
}
