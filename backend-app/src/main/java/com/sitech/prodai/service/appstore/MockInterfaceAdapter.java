package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 适配层 mock 实现（开发环境默认，统一方案 §4.5.4/§4.5.5）。
 * <p>
 * A1（相似度分析）与 C1~C4（测试发起/场景/进度/结果）包装现有 {@link OfferSimV16Service}
 * （18 销售品种子 + 融合组种子），入参兼容 camelCase（工作流裸传）与 snake_case；
 * B1~B4（智能稽核上传/异步通知/结果插入/SQL 查询）为现场新增能力，此处以内置确定性 mock
 * 按接口规范出参结构生成（含 900117029~032 四档），不依赖外部文件。
 * <p>
 * 出参结构与 {@link EopInterfaceAdapter} 归一后结构完全一致（§4.5.5 第 4 点），保证 mock/real 同构。
 */
public class MockInterfaceAdapter implements InterfaceFacade {

    private static final Logger log = LoggerFactory.getLogger(MockInterfaceAdapter.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** B2 内置 mock 四档销售品（与需求文档 900117029~032 对齐，确定性）。 */
    private static final List<String[]> MOCK_OFFERS = List.of(
            new String[]{"900117029", "5G畅享融合套餐129元档", "0"},
            new String[]{"900117030", "5G畅享融合套餐159元档", "1"},
            new String[]{"900117031", "5G畅享融合套餐199元档", "2"},
            new String[]{"900117032", "5G畅享融合套餐239元档", "3"});

    private final OfferSimV16Service sim;

    /** B4 稽核轮询模拟：同一 requestIds 前 N 次返回「处理中」，之后返回完成，复现轮询两阶段。
     *  键=requestIds 参数串；值=该批次已查询次数。N 由 AUDIT_POLL_PENDING_TIMES 控制。 */
    private static final int AUDIT_POLL_PENDING_TIMES = 2;
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> auditPollAttempts =
            new java.util.concurrent.ConcurrentHashMap<>();

    public MockInterfaceAdapter(OfferSimV16Service sim) {
        this.sim = sim;
    }

    @Override
    public String mode() {
        return "mock";
    }

    /* ================= A1 相似度分析 ================= */

    @Override
    public Map<String, Object> similarOfferQuery(Map<String, Object> req) {
        // 兼容 camelCase（businessDesc，工作流裸传）与 snake_case（business_desc）
        String desc = firstNonBlank(MapOps.str(req.get("businessDesc")), MapOps.str(req.get("business_desc")));
        String templateId = firstNonBlank(MapOps.str(req.get("templateId")), MapOps.str(req.get("template_id")));
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("businessDesc", desc);
        if (!templateId.isBlank()) {
            call.put("templateId", templateId);
        }
        return sim.similarOfferQuery(call);
    }

    /* ================= B1 智能稽核文件上传（内置确定性 mock） ================= */

    @Override
    public Map<String, Object> auditFileUpload(Map<String, Object> req) {
        String fileName = MapOps.str(req.get("fileName"));
        if (fileName.isBlank()) {
            fileName = MapOps.str(req.get("file_name"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        String fileId = "file_" + LocalDateTime.now().format(STAMP) + "_mock0001";
        body.put("file_id", fileId);
        Map<String, Object> salesId = new LinkedHashMap<>();
        for (String[] row : MOCK_OFFERS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("offerId", row[0]);
            item.put("location", Integer.parseInt(row[2]));
            item.put("sheet_name", "套餐");
            salesId.put(row[1], item);
        }
        body.put("sales_id", salesId);
        body.put("resultCode", "0");
        body.put("resultMsg", "文件解析成功，识别销售品 " + MOCK_OFFERS.size() + " 个"
                + (fileName.isBlank() ? "" : "（" + fileName + "）"));
        log.info("[MockInterfaceAdapter] B1 上传 mock 解析: file_id={}, offers={}", fileId, MOCK_OFFERS.size());
        return body;
    }

    /* ================= B2 异步报告生成并通知（内置确定性 mock） ================= */

    @Override
    public Map<String, Object> auditAsyncNotify(Map<String, Object> req) {
        String salesId = firstNonBlank(MapOps.str(req.get("sales_id")), MapOps.str(req.get("salesId")));
        if (salesId.isBlank()) {
            return fail("sales_id 必填");
        }
        String fileId = firstNonBlank(MapOps.str(req.get("file_id")), MapOps.str(req.get("fileId")));
        String requestId = LocalDateTime.now().format(STAMP) + "_mock" + String.format("%04d",
                ThreadLocalRandom.current().nextInt(0, 10000));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "稽核任务已开始处理，完成后将自动通知外部系统");
        body.put("request_id", requestId);
        body.put("sales_id", salesId);
        body.put("resultCode", "0");
        if (!fileId.isBlank()) {
            body.put("file_id", fileId);
        }
        return body;
    }

    /* ================= B3 插入 AI 稽核结果（内置确定性 mock） ================= */

    @Override
    public Map<String, Object> auditResultInsert(Map<String, Object> req) {
        Object records = req.get("records");
        int count = records instanceof List<?> list ? list.size() : 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", "0");
        body.put("resultMsg", "处理成功（mock 落表 " + count + " 条，状态 1000 处理中）");
        return body;
    }

    /* ================= B4 通用 SQL 查询（内置确定性 mock） ================= */

    @Override
    public Map<String, Object> auditSqlQuery(Map<String, Object> req) {
        String sqlId = firstNonBlank(MapOps.str(req.get("sql_id")), MapOps.str(req.get("sqlId")));
        String params = MapOps.str(req.get("params"));
        int total = countRequestIds(params);
        // 查完成状态的 sqlId（20260626001）按查询次数模拟「处理中→完成」，复现轮询两阶段；
        // 查最终结果的 sqlId（20260626002）与其余直接返回完成。
        boolean pollSql = "20260626001".equals(sqlId);
        int attempt = pollSql
                ? auditPollAttempts.merge(params == null ? "" : params, 1, Integer::sum)
                : Integer.MAX_VALUE;
        boolean finished = attempt > AUDIT_POLL_PENDING_TIMES;
        int finishedCount = finished ? total : Math.max(0, total - 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("finishedCount", String.valueOf(finishedCount));
        row.put("isAllFinished", finished ? "Y" : "N");
        row.put("processingCount", finished ? "0" : "1");
        row.put("totalCount", String.valueOf(total));
        if (finished && "20260626002".equals(sqlId)) {
            // 查最终稽核结果：附确定性结论摘要（mock）
            row.put("auditConclusion", "全部销售品稽核通过（mock）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", "0");
        body.put("resultMsg", "成功");
        body.put("execute_sql_resp", List.of(row));
        return body;
    }

    private int countRequestIds(String params) {
        if (params == null || params.isBlank()) {
            return MOCK_OFFERS.size();
        }
        int idx = params.indexOf(':');
        String list = idx >= 0 ? params.substring(idx + 1) : params;
        int count = 0;
        for (String s : list.split(",")) {
            if (!s.isBlank()) {
                count++;
            }
        }
        return Math.max(count, 1);
    }

    /* ================= C1 销售品测试发起 ================= */

    @Override
    public Map<String, Object> testOfferStart(Map<String, Object> req) {
        return sim.testOfferStart(withOfferId(req));
    }

    /* ================= C2 查询测试场景 ================= */

    @Override
    public Map<String, Object> testScenes(Map<String, Object> req) {
        return sim.testScenes(withGlobalId(req));
    }

    /* ================= C3 查询测试进度 ================= */

    @Override
    public Map<String, Object> testProgress(Map<String, Object> req) {
        return sim.testProgress(withGlobalId(req));
    }

    /* ================= C4 查询测试结果 ================= */

    @Override
    public Map<String, Object> testResult(Map<String, Object> req) {
        String baseUrl = MapOps.str(req.get("external_base_url"));
        return sim.testResult(withGlobalId(req), baseUrl);
    }

    /* ---------------- 入参归一 ---------------- */

    private Map<String, Object> withOfferId(Map<String, Object> req) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("offerId", firstNonBlank(MapOps.str(req.get("offerId")), MapOps.str(req.get("offer_id"))));
        return call;
    }

    private Map<String, Object> withGlobalId(Map<String, Object> req) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("globalId", firstNonBlank(MapOps.str(req.get("globalId")), MapOps.str(req.get("global_id"))));
        return call;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", "1");
        body.put("resultMsg", msg);
        return body;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }
}
