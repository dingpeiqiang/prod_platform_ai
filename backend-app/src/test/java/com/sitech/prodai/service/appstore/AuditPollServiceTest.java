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
 * 规格稽核结果轮询与落地服务契约测试（环节4b 后端化）。
 * <p>
 * 锁定：读 audit_pending 取 requestIds → 单次探测 B4（sqlId=20260626001）→ 完成查结论（20260626002）
 * → 整批 pass==1 时写 node_name=spec。mock 适配层「前 2 次处理中、第 3 次完成」的行为用于驱动多轮调用。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@DisplayName("规格稽核轮询落地契约")
class AuditPollServiceTest {

    @Autowired
    private AuditPollService auditPollService;

    @Autowired
    private NodeResultService nodeResult;

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

    /** 预置稽核发起临时态（含逐品 requestIds），供 pollOnce 自查。 */
    private void seedAuditPending(String reqId, String requestIds) {
        nodeResult.save(reqId, AuditPollService.NODE_AUDIT_PENDING,
                "{\"offers\":[{\"offer_seq\":1,\"offer_id\":\"900820592\",\"requestIds\":\"" + requestIds + "\"}]}",
                "pending");
    }

    @Test
    @DisplayName("无 audit_pending：返回 pending 且不落 spec")
    void pendingMissing() {
        String reqId = uniqReqId();
        Map<String, Object> out = auditPollService.pollOnce(reqId);
        Assertions.assertEquals(AuditPollService.STATE_PENDING, out.get("state"), "无发起记录应为处理中：" + out);
        Assertions.assertNull(nodeResult.latestRecord(reqId, AuditPollService.NODE_SPEC), "无发起记录不应落 spec");
    }

    @Test
    @DisplayName("稽核未完成：首次探测返回 pending，不落 spec")
    void stillProcessing() {
        String reqId = uniqReqId();
        // mock 适配层以 params 为键计数「前 2 次处理中、第 3 次完成」，故改用独立 requestId 保证首次必为 pending
        seedAuditPending(reqId, "REQ-" + reqId);
        Map<String, Object> out = auditPollService.pollOnce(reqId);
        Assertions.assertEquals(AuditPollService.STATE_PENDING, out.get("state"), "首次探测应为处理中：" + out);
        Assertions.assertNull(nodeResult.latestRecord(reqId, AuditPollService.NODE_SPEC), "未完成不应落 spec");
    }

    @Test
    @DisplayName("稽核完成通过：整批 pass==1 时写入 node_name=spec")
    void completedAndPersistSpec() {
        String reqId = uniqReqId();
        seedAuditPending(reqId, "REQ-" + reqId);
        Map<String, Object> out = null;
        // mock 前 2 次处理中、第 3 次完成；多轮调用直至 done（上限保护）
        for (int i = 0; i < 5; i++) {
            out = auditPollService.pollOnce(reqId);
            if (AuditPollService.STATE_DONE.equals(out.get("state"))) {
                break;
            }
        }
        Assertions.assertNotNull(out);
        Assertions.assertEquals(AuditPollService.STATE_DONE, out.get("state"), "多轮后应完成：" + out);
        Assertions.assertEquals(AuditPollService.VERDICT_PASS, out.get("verdict"), "mock 结论应通过：" + out);
        Assertions.assertEquals(Boolean.TRUE, out.get("saved"), "通过时应标记已落 spec：" + out);

        Map<String, Object> spec = nodeResult.latestRecord(reqId, AuditPollService.NODE_SPEC);
        Assertions.assertNotNull(spec, "通过后应存在 spec 记录");
        Assertions.assertEquals("ok", spec.get("status"));
        String json = String.valueOf(spec.get("result_json"));
        Assertions.assertTrue(json.contains("\"pass\":\"1\""), "spec envelope 应含 pass=1：" + json);
        Assertions.assertTrue(json.contains("audit_suggest"), "spec envelope 应含审批看板所需 audit_suggest：" + json);
    }
}
