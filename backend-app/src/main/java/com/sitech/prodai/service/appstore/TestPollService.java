package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动化测试结果轮询与落地服务（环节6b 后端化）。
 * <p>
 * 背景：自动化测试为异步处理（C1/C2 发起 → 外部产出 → C3 进度 / C4 结果），原设计由对话工作流 wf_sub_13
 * 主动查进度并把最终结果写入 node_name=test。改为「前台面板驱动」后，面板按 req_id 周期调用本服务的
 * {@link #pollOnce}，由后端承担 C3 轮询与 test 落库，避免用户手动回复查询。
 * <p>
 * 设计要点（与 wf_sub_13 的 CODE_PROGRESS_LITE/CODE_EXTRACT_TEST_RESULTS/CODE_RENDER_TEST_REPORT/
 * CODE_PACK_TEST_RESULT 语义对齐）：
 * <ul>
 *   <li>数据来源：读取 req_id 下 node_name=test_pending 的临时态结果（含 offers[{offer_seq,offer_id,globalId}]）。</li>
 *   <li>单次探测（非长阻塞）：每次调用推进一轮 C3 查询，由前端按周期重复调用，接口保持幂等、无状态。</li>
 *   <li>完成判定：逐品查 C3 test/offer/progress，done==true 或 finishedSceneCount>=totalSceneCount>0 视为终态。</li>
 *   <li>结果落地：终态后再查 C4 test/offer/result 取逐场景/测点结论；仅 scene_failed==0（无失败场景）
 *       才写 node_name=test（门禁：审批四环节硬校验以 test 为准），有失败场景不写。</li>
 * </ul>
 */
@Service
public class TestPollService {

    private static final Logger log = LoggerFactory.getLogger(TestPollService.class);

    /** 测试发起临时态 node_name（由 wf_sub_04 写入 globalId/testScenes）。 */
    static final String NODE_TEST_PENDING = "test_pending";
    /** 自动化测试最终态 node_name（供审批四环节门禁读取）。 */
    static final String NODE_TEST = "test";

    /** 轮询状态：处理中 / 已完成。 */
    public static final String STATE_PENDING = "pending";
    public static final String STATE_DONE = "done";
    /** 落库结论：通过 / 未通过 / 未完成。 */
    public static final String VERDICT_PASS = "pass";
    public static final String VERDICT_FAIL = "fail";
    public static final String VERDICT_PENDING = "pending";

    private final NodeResultService nodeResultService;
    private final InterfaceFacade interfaceFacade;

    public TestPollService(NodeResultService nodeResultService, InterfaceFacade interfaceFacade) {
        this.nodeResultService = nodeResultService;
        this.interfaceFacade = interfaceFacade;
    }

    /**
     * 单次轮询：读取测试发起临时态 → 探测终态 → 终态则取结果并（通过时）落 test。
     *
     * @param reqId 需求单号（PLAN+yyyyMMddHHmmss+3位随机数）
     * @return {code, msg, state, verdict, pass, scene_count, scene_failed, point_pass, point_total, saved, offers[]}
     *         state=pending 表示测试未完成；state=done 时 verdict=pass/fail。
     */
    public Map<String, Object> pollOnce(String reqId) {
        String req = reqId == null ? "" : reqId.trim();
        if (req.isEmpty()) {
            return fail(5002, "invalid req_id");
        }
        // 1) 自查发起临时态，取逐品 globalId（无临时态说明尚未发起测试）
        Map<String, Object> pending = nodeResultService.latestRecord(req, NODE_TEST_PENDING);
        if (pending == null) {
            return body(STATE_PENDING, VERDICT_PENDING, "未找到测试发起记录（test_pending），请先发起自动化测试", 5005);
        }
        List<Map<String, Object>> offers = extractOffers(MapOps.str(pending.get("result_json")));
        if (offers.isEmpty()) {
            return body(STATE_PENDING, VERDICT_PENDING, "测试发起记录缺少 globalId，请重新发起测试", 5006);
        }

        // 2) 探测终态：全部 globalId 均 is_final 才算完成（done==true 或 全部场景已执行完）
        for (Map<String, Object> offer : offers) {
            String globalId = MapOps.str(offer.get("globalId"));
            if (globalId.isEmpty()) {
                continue;
            }
            if (!isFinal(globalId)) {
                Map<String, Object> out = body(STATE_PENDING, VERDICT_PENDING, "测试处理中，请稍候", 0);
                out.put("offers", offers);
                return out;
            }
        }

        // 3) 终态：逐品查询测试结果并聚合出 scene_failed
        Map<String, Object> queried = queryResults(offers);
        int sceneFailed = intOf(queried.get("scene_failed"));
        String verdict = sceneFailed == 0 ? VERDICT_PASS : VERDICT_FAIL;

        Map<String, Object> out = body(STATE_DONE, verdict,
                VERDICT_PASS.equals(verdict) ? "测试通过" : "测试未通过，请按失败场景整改后重新发起", 0);
        out.put("pass", VERDICT_PASS.equals(verdict) ? "1" : "0");
        out.put("scene_count", queried.get("scene_count"));
        out.put("scene_failed", queried.get("scene_failed"));
        out.put("point_pass", queried.get("point_pass"));
        out.put("point_total", queried.get("point_total"));
        out.put("test_report", queried.get("test_report"));
        out.put("offers", queried.get("offers"));

        // 4) 门禁：仅 scene_failed==0 才写 test（与 wf_sub_13 节点2334/2312 一致）
        if (VERDICT_PASS.equals(verdict)) {
            String resultJson = buildTestEnvelope(queried, req);
            Map<String, Object> saved = nodeResultService.save(req, NODE_TEST, resultJson, "ok");
            out.put("saved", "0".equals(MapOps.str(saved.get("code"))));
            log.info("[TestPollService] 测试通过并落 test req_id={} record_id={}", req, saved.get("record_id"));
        } else {
            out.put("saved", false);
        }
        return out;
    }

    /**
     * 从 test_pending 的 result_json 提取逐品 offers（含 offer_seq/offer_id/globalId）。
     * 兼容顶层直接给 globalId 或 global_id 的旧形态（包装为单品）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractOffers(String resultJson) {
        List<Map<String, Object>> norm = new ArrayList<>();
        Object parsed = parseJson(resultJson);
        Object rawOffers = parsed instanceof Map<?, ?> map ? map.get("offers") : null;
        List<Object> src = rawOffers == null ? List.of() : MapOps.castList(rawOffers);
        if (src.isEmpty() && parsed instanceof Map<?, ?> m2
                && !MapOps.empty(MapOps.firstNonEmpty(m2.get("globalId"), m2.get("global_id")))) {
            src = List.of(m2);
        }
        int i = 1;
        for (Object item : src) {
            if (!(item instanceof Map<?, ?> m)) {
                i++;
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>((Map<String, Object>) m);
            Object seq = o.get("offer_seq");
            if (seq == null || MapOps.str(seq).isEmpty()) {
                o.put("offer_seq", i);
            }
            o.put("offer_id", MapOps.str(MapOps.firstNonEmpty(o.get("offer_id"), o.get("offerId"))));
            o.put("globalId", MapOps.str(MapOps.firstNonEmpty(o.get("globalId"), o.get("global_id"))));
            norm.add(o);
            i++;
        }
        return norm;
    }

    /** 单品终态判定：查 C3 test/offer/progress，done==true 或 finishedSceneCount>=totalSceneCount>0。 */
    private boolean isFinal(String globalId) {
        Map<String, Object> resp = testProgress(globalId);
        if (resp == null || resp.isEmpty()) {
            return false;
        }
        // 后端逐品业务错误（resultCode != 0，如查无对应测试任务）视为未终态，避免误落 test
        String rc = MapOps.str(resp.get("resultCode")).trim();
        if (!rc.isEmpty() && !"0".equals(rc)) {
            return false;
        }
        Map<String, Object> ro = resultObjectOf(resp);
        boolean done = "true".equalsIgnoreCase(MapOps.str(ro.get("done")));
        int total = intOf(ro.get("totalSceneCount"));
        int finished = intOf(ro.get("finishedSceneCount"));
        return done || (total > 0 && finished >= total);
    }

    /** 逐品查询测试结果并按 wf_sub_13 语义聚合出 scene_failed/scene_count/point_pass/point_total/test_report/offers。 */
    private Map<String, Object> queryResults(List<Map<String, Object>> offers) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        StringBuilder report = new StringBuilder();
        int sceneCount = 0;
        int sceneFailed = 0;
        int pointTotal = 0;
        int pointPass = 0;
        int offerCount = 0;
        for (Map<String, Object> o : offers) {
            String globalId = MapOps.str(o.get("globalId"));
            if (globalId.isEmpty()) {
                continue;
            }
            Map<String, Object> ro = resultObjectOf(testResult(globalId));
            List<Map<String, Object>> scenes = MapOps.castListOfMaps(ro.get("testScenes"));
            int seq = intOf(o.get("offer_seq"));
            offerCount++;
            int offerSceneFailed = 0;
            int offerPointTotal = 0;
            int offerPointPass = 0;
            for (Map<String, Object> scene : scenes) {
                sceneCount++;
                List<Map<String, Object>> points = MapOps.castListOfMaps(scene.get("testCasePointResults"));
                offerPointTotal += points.size();
                int okPts = 0;
                for (Map<String, Object> p : points) {
                    if ("0".equals(MapOps.str(p.get("resultCode")).trim())) {
                        okPts++;
                    }
                }
                offerPointPass += okPts;
                if (sceneFailed(scene, points)) {
                    sceneFailed++;
                    offerSceneFailed++;
                }
            }
            pointTotal += offerPointTotal;
            pointPass += offerPointPass;
            report.append(renderOfferReport(seq, o, ro, scenes, offerSceneFailed, offerPointPass, offerPointTotal));
            summaries.add(offerSummary(seq, o, ro, scenes.size(), offerSceneFailed, offerPointTotal));
        }
        String overall = "# 测试结果报告\n\n■ 汇总：商品 " + offerCount + " 个 ｜ 场景 " + sceneCount
                + " 个（失败 " + sceneFailed + "）｜ 测点 " + pointPass + "/" + pointTotal + " 通过\n\n";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scene_count", String.valueOf(sceneCount));
        out.put("scene_failed", String.valueOf(sceneFailed));
        out.put("point_pass", String.valueOf(pointPass));
        out.put("point_total", String.valueOf(pointTotal));
        out.put("test_report", overall + report);
        out.put("offers", summaries);
        return out;
    }

    /** 场景失败判定：场景级 relResultCode∈{1,2} 失败；否则按测点 resultCode 聚合（全'0'通过，否则失败）。 */
    private boolean sceneFailed(Map<String, Object> scene, List<Map<String, Object>> points) {
        Object relObj = scene.get("objTestSceneRel");
        String rel;
        if (relObj instanceof Map<?, ?> relMap) {
            rel = MapOps.str(relMap.get("resultCode")).trim();
        } else {
            rel = MapOps.str(relObj).trim();
        }
        if ("1".equals(rel) || "2".equals(rel)) {
            return true;
        }
        List<String> rcs = new ArrayList<>();
        for (Map<String, Object> p : points) {
            rcs.add(MapOps.str(p.get("resultCode")).trim());
        }
        boolean anyNonEmpty = rcs.stream().anyMatch(rc -> !rc.isEmpty());
        if (anyNonEmpty) {
            return rcs.stream().filter(rc -> !rc.isEmpty()).anyMatch(rc -> !"0".equals(rc));
        }
        return false;
    }

    /** 逐品轻量摘要（对齐 CODE_PACK_TEST_RESULT._summary 字段名）。 */
    private Map<String, Object> offerSummary(int seq, Map<String, Object> o, Map<String, Object> ro,
                                             int sceneCount, int sceneFailed, int pointTotal) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("offer_seq", seq);
        item.put("offer_id", MapOps.str(MapOps.firstNonEmpty(ro.get("offerId"), o.get("offer_id"))));
        item.put("offer_name", MapOps.str(ro.get("offerName")));
        item.put("globalId", MapOps.str(ro.get("globalId")));
        item.put("test_request_id", MapOps.str(ro.get("testRequestId")));
        item.put("scene_count", sceneCount);
        item.put("scene_failed", sceneFailed);
        item.put("point_total", pointTotal);
        return item;
    }

    /** 逐品 Markdown 报告块（对齐 CODE_RENDER_TEST_REPORT._render 结构）。 */
    private String renderOfferReport(int seq, Map<String, Object> o, Map<String, Object> ro,
                                     List<Map<String, Object>> scenes, int failScene, int passPts, int totalPts) {
        String name = MapOps.firstNonEmpty(ro.get("offerName"), o.get("offer_id"), "销售品" + seq).toString();
        StringBuilder sb = new StringBuilder();
        sb.append("## 商品").append(seq).append("：").append(name).append('\n');
        sb.append("- 测试任务ID：").append(MapOps.str(ro.get("testRequestId"))).append('\n');
        sb.append("- 流水号：").append(MapOps.str(ro.get("globalId"))).append('\n');
        sb.append("- 销售品编码：").append(MapOps.str(MapOps.firstNonEmpty(ro.get("offerId"), o.get("offer_id")))).append('\n');
        sb.append("- 场景数：").append(scenes.size()).append("（失败 ").append(failScene).append("）\n");
        sb.append("- 测点：").append(passPts).append("/").append(totalPts).append(" 通过\n\n");
        for (Map<String, Object> scene : scenes) {
            List<Map<String, Object>> points = MapOps.castListOfMaps(scene.get("testCasePointResults"));
            boolean failed = sceneFailed(scene, points);
            int okPts = 0;
            for (Map<String, Object> p : points) {
                if ("0".equals(MapOps.str(p.get("resultCode")).trim())) {
                    okPts++;
                }
            }
            sb.append("### ").append(failed ? "❌" : "✅").append(' ')
                    .append(MapOps.str(scene.get("testSceneName"))).append(" · ")
                    .append(failed ? "失败" : "通过").append("（").append(okPts).append('/')
                    .append(points.size()).append(" 测点通过）\n");
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * 组 test 落库 envelope：仅保留下游（审批 CODE_SUMMARY_APPROVAL）真正消费的字段。
     * pass / test_report / req_id / 计数 / offers[] 轻量摘要为结构字段（对齐 CODE_PACK_TEST_RESULT），
     * 不落逐点明细，避免超 NodeResultService 64KB 上限被拒写。
     */
    private String buildTestEnvelope(Map<String, Object> queried, String reqId) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("pass", "1");
        env.put("test_report", queried.get("test_report"));
        env.put("req_id", reqId);
        env.put("scene_count", queried.get("scene_count"));
        env.put("scene_failed", queried.get("scene_failed"));
        env.put("point_pass", queried.get("point_pass"));
        env.put("point_total", queried.get("point_total"));
        env.put("offers", queried.get("offers"));
        return toJson(env);
    }

    /* ---------------- C3/C4 调用与解包 ---------------- */

    private Map<String, Object> testProgress(String globalId) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("global_id", globalId);
        try {
            return interfaceFacade.testProgress(req);
        } catch (RuntimeException e) {
            log.warn("[TestPollService] C3 查询异常 globalId={}: {}", globalId, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> testResult(String globalId) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("global_id", globalId);
        try {
            return interfaceFacade.testResult(req);
        } catch (RuntimeException e) {
            log.warn("[TestPollService] C4 查询异常 globalId={}: {}", globalId, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 取 EOP 裸信封业务体 resultObject（缺失时回退本体）。
     * 适配层 C3/C4 出参为 Hermes 裸信封 {@code {resultCode, resultObject:{...}, resultMsg}}。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resultObjectOf(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return Map.of();
        }
        Object ro = item.get("resultObject");
        if (ro instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return item;
    }

    /* ---------------- 工具方法 ---------------- */

    private Object parseJson(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        String t = text.trim();
        if (!t.startsWith("{") && !t.startsWith("[") && t.contains(":")) {
            t = t.substring(t.indexOf(':') + 1).trim();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(t, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private int intOf(Object v) {
        try {
            return Integer.parseInt(MapOps.str(v).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String toJson(Object v) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(v);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, Object> body(String state, String verdict, String msg, int code) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("msg", msg);
        out.put("state", state);
        out.put("verdict", verdict);
        return out;
    }

    private static Map<String, Object> fail(int code, String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("msg", msg);
        return out;
    }
}
