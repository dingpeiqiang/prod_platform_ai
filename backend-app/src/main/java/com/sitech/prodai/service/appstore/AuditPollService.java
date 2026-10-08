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
 * 规格稽核结果轮询与落地服务（环节4b 后端化）。
 * <p>
 * 背景：规格稽核为异步处理（B2 发起 → 外部产出 → B4 查询），原设计由对话工作流 wf_sub_12
 * 主动轮询并把最终结果写入 node_name=spec。改为「前台面板驱动」后，面板按 req_id 周期调用本服务
 * 的 {@link #pollOnce}，由后端承担 B4 轮询与 spec 落库，避免用户手动回复查询。
 * <p>
 * 设计要点（与 wf_sub_12 的 CODE_POLL_AUDIT/CODE_AUDIT_QUERY/CODE_PACK_RECORD 语义对齐）：
 * <ul>
 *   <li>数据来源：读取 req_id 下 node_name=audit_pending 的临时态结果（含 offers[{offer_seq,offer_id,requestIds}]）。</li>
 *   <li>单次探测（非长阻塞）：每次调用推进一轮 B4 查询，由前端按周期重复调用，接口保持幂等、无状态。</li>
 *   <li>完成判定：逐个 requestIds 查 B4 sqlId=20260626001，首行 isAllFinished∈{y,yes,true,1} 视为完成。</li>
 *   <li>结果落地：完成后再查 B4 sqlId=20260626002 取最终结论；仅整批 pass==1（全品通过）时
 *       才写 node_name=spec（门禁：环节5 与审批四环节硬校验以 spec 为准），pass!=1 不写。</li>
 * </ul>
 */
@Service
public class AuditPollService {

    private static final Logger log = LoggerFactory.getLogger(AuditPollService.class);

    /** 稽核发起临时态 node_name（由 wf_sub_03 写入 requestIds/sales_id）。 */
    static final String NODE_AUDIT_PENDING = "audit_pending";
    /** 规格稽核最终态 node_name（供环节5/审批四环节门禁读取）。 */
    static final String NODE_SPEC = "spec";

    /** B4 查完成状态 sqlId。 */
    private static final String SQL_ID_FINISH = "20260626001";
    /** B4 查最终结果 sqlId。 */
    private static final String SQL_ID_RESULT = "20260626002";

    /** 轮询状态：处理中 / 已完成。 */
    public static final String STATE_PENDING = "pending";
    public static final String STATE_DONE = "done";
    /** 落库结论：通过 / 不通过 / 未完成。 */
    public static final String VERDICT_PASS = "pass";
    public static final String VERDICT_FAIL = "fail";
    public static final String VERDICT_PENDING = "pending";

    private final NodeResultService nodeResultService;
    private final InterfaceFacade interfaceFacade;

    public AuditPollService(NodeResultService nodeResultService, InterfaceFacade interfaceFacade) {
        this.nodeResultService = nodeResultService;
        this.interfaceFacade = interfaceFacade;
    }

    /**
     * 单次轮询：读取稽核发起临时态 → 探测完成态 → 完成则取结论并（通过时）落 spec。
     *
     * @param reqId 需求单号（PLAN+yyyyMMddHHmmss+3位随机数）
     * @return {code, msg, state, verdict, pass, audit_summary, error_list, saved, offers[]}
     *         state=pending 表示稽核未完成；state=done 时 verdict=pass/fail。
     */
    public Map<String, Object> pollOnce(String reqId) {
        String req = reqId == null ? "" : reqId.trim();
        if (req.isEmpty()) {
            return fail(5002, "invalid req_id");
        }
        // 1) 自查发起临时态，取逐品 requestIds（无临时态说明尚未发起稽核）
        Map<String, Object> pending = nodeResultService.latestRecord(req, NODE_AUDIT_PENDING);
        if (pending == null) {
            return body(STATE_PENDING, VERDICT_PENDING, "未找到稽核发起记录（audit_pending），请先发起规格稽核", 5005);
        }
        List<Map<String, Object>> offers = extractOffers(MapOps.str(pending.get("result_json")));
        if (offers.isEmpty()) {
            return body(STATE_PENDING, VERDICT_PENDING, "稽核发起记录缺少 requestIds，请重新发起稽核", 5006);
        }

        // 2) 探测完成态：全部 requestIds 均 finished 才算完成
        for (Map<String, Object> offer : offers) {
            String requestIds = MapOps.str(offer.get("requestIds"));
            if (requestIds.isEmpty()) {
                continue;
            }
            if (!isFinished(requestIds)) {
                Map<String, Object> out = body(STATE_PENDING, VERDICT_PENDING, "稽核处理中，请稍候", 0);
                out.put("offers", offers);
                return out;
            }
        }

        // 3) 已完成：逐品查询最终结论
        Map<String, Object> queried = queryResults(offers);
        String batchPass = MapOps.str(queried.get("pass"));
        String verdict = "1".equals(batchPass) ? VERDICT_PASS : VERDICT_FAIL;

        Map<String, Object> out = body(STATE_DONE, verdict,
                VERDICT_PASS.equals(verdict) ? "稽核通过" : "稽核未通过，请按明细整改后重新发起", 0);
        out.put("pass", batchPass);
        out.put("error_list", queried.get("error_list"));
        out.put("audit_summary", queried.get("audit_summary"));
        out.put("offers", queried.get("offers"));

        // 4) 门禁：仅整批 pass==1 才写 spec（与 wf_sub_12 节点2212 一致）
        if (VERDICT_PASS.equals(verdict)) {
            String resultJson = buildSpecEnvelope(queried);
            Map<String, Object> saved = nodeResultService.save(req, NODE_SPEC, resultJson, "ok");
            out.put("saved", "0".equals(MapOps.str(saved.get("code"))));
            log.info("[AuditPollService] 稽核通过并落 spec req_id={} record_id={}", req, saved.get("record_id"));
        } else {
            out.put("saved", false);
        }
        return out;
    }

    /**
     * 从 audit_pending 的 result_json 提取逐品 offers（含 offer_seq/offer_id/requestIds）。
     * 兼容顶层直接给逗号串 requestIds 的旧形态（包装为单品）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractOffers(String resultJson) {
        List<Map<String, Object>> norm = new ArrayList<>();
        Object parsed = parseJson(resultJson);
        Object rawOffers = parsed instanceof Map<?, ?> map ? map.get("offers") : null;
        List<Object> src = rawOffers == null ? List.of() : MapOps.castList(rawOffers);
        if (src.isEmpty() && parsed instanceof Map<?, ?> m2 && !MapOps.empty(m2.get("requestIds"))) {
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
            o.put("offer_id", MapOps.str(o.get("offer_id")));
            o.put("requestIds", normalizeRequestIds(o.get("requestIds")));
            norm.add(o);
            i++;
        }
        return norm;
    }

    /** requestIds 归一化：list → 逗号串。 */
    private String normalizeRequestIds(Object v) {
        if (v instanceof List<?> list) {
            List<String> ids = new ArrayList<>();
            for (Object x : list) {
                String s = MapOps.str(x);
                if (!s.isEmpty()) {
                    ids.add(s);
                }
            }
            return String.join(",", ids);
        }
        return MapOps.str(v);
    }

    /** 单品完成判定：查 B4 sqlId=20260626001，首行 isAllFinished∈{y,yes,true,1}。 */
    private boolean isFinished(String requestIds) {
        Map<String, Object> resp = sqlQuery(SQL_ID_FINISH, requestIds);
        Map<String, Object> row = firstSqlRow(resp);
        String finished = MapOps.str(MapOps.firstNonEmpty(row.get("isAllFinished"), row.get("finished"))).toLowerCase();
        return "y".equals(finished) || "yes".equals(finished) || "true".equals(finished) || "1".equals(finished);
    }

    /** 逐品查询最终结论并按 wf_sub_12 语义聚合出 pass/error_list/audit_summary/offers。 */
    private Map<String, Object> queryResults(List<Map<String, Object>> offers) {
        List<Map<String, Object>> norm = new ArrayList<>();
        List<String> errLines = new ArrayList<>();
        List<String> sumLines = new ArrayList<>();
        String batchPass = "1";
        for (Map<String, Object> o : offers) {
            Object seq = o.get("offer_seq");
            String offerId = MapOps.str(o.get("offer_id"));
            String requestIds = MapOps.str(o.get("requestIds"));
            Map<String, Object> row = firstSqlRow(sqlQuery(SQL_ID_RESULT, requestIds));
            String itemPass = resolvePass(row);
            String err = stringify(row.get("error_list"));
            String summary = MapOps.str(MapOps.firstNonEmpty(row.get("audit_summary"), row.get("auditConclusion")));
            if (!"1".equals(itemPass)) {
                batchPass = "0";
            }
            if (!err.isEmpty()) {
                errLines.add("#" + seq + " " + offerId + "：" + err);
            }
            if (!summary.isEmpty()) {
                sumLines.add("#" + seq + " " + offerId + "：" + summary);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("offer_seq", seq);
            item.put("offer_id", offerId);
            item.put("pass", itemPass);
            item.put("error_list", err);
            item.put("audit_summary", summary);
            norm.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pass", batchPass);
        out.put("error_list", String.join("\n", errLines));
        out.put("audit_summary", String.join("；", sumLines));
        out.put("offers", norm);
        return out;
    }

    /** 单品结论归一化：优先 row.pass；缺失时按 error_list / isAllFinished 回退。 */
    private String resolvePass(Map<String, Object> row) {
        String raw = MapOps.str(row.get("pass"));
        if (!raw.isEmpty()) {
            return raw;
        }
        if (!MapOps.empty(row.get("error_list"))) {
            return "0";
        }
        String fin = MapOps.str(row.get("isAllFinished")).toLowerCase();
        if ("y".equals(fin) || "yes".equals(fin) || "true".equals(fin) || "1".equals(fin)) {
            return "1";
        }
        return "";
    }

    /**
     * 组 spec 落库 envelope：仅保留下游（审批 CODE_SUMMARY_APPROVAL）真正消费的字段。
     * pass / audit_summary / error_list / offers[] 为结构字段；audit_suggest 为审批看板展示文案，
     * 后端无 LLM 时用确定性文案（audit_summary 归并）填充，保证审批看板非空。
     */
    private String buildSpecEnvelope(Map<String, Object> queried) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("pass", queried.get("pass"));
        env.put("error_list", queried.get("error_list"));
        env.put("audit_summary", queried.get("audit_summary"));
        env.put("offers", queried.get("offers"));
        String summary = MapOps.str(queried.get("audit_summary"));
        String suggest = summary.isEmpty() ? "全部销售品稽核通过" : summary;
        env.put("audit_suggest", suggest);
        return toJson(env);
    }

    /* ---------------- B4 调用与解包 ---------------- */

    private Map<String, Object> sqlQuery(String sqlId, String requestIds) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("sqlId", sqlId);
        req.put("params", "requestIds:" + (requestIds == null ? "" : requestIds));
        try {
            return interfaceFacade.auditSqlQuery(req);
        } catch (RuntimeException e) {
            log.warn("[AuditPollService] B4 查询异常 sqlId={} requestIds={}: {}", sqlId, requestIds, e.getMessage());
            return Map.of();
        }
    }

    /** B4 返回 {resultCode, execute_sql_resp:[{...}]}，完成标志/结论在首行内。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> firstSqlRow(Map<String, Object> resp) {
        if (resp == null || resp.isEmpty()) {
            return Map.of();
        }
        Object rows = MapOps.firstNonEmpty(resp.get("execute_sql_resp"), resp.get("executeSqlResp"));
        List<Object> list = MapOps.castList(rows);
        if (!list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (rows instanceof Map<?, ?> m2) {
            return (Map<String, Object>) m2;
        }
        return resp;
    }

    /* ---------------- 工具方法 ---------------- */

    private Object parseJson(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        String t = text.trim();
        // 兼容 "key:value" 形态
        if (!t.startsWith("{") && !t.startsWith("[") && t.contains(":")) {
            t = t.substring(t.indexOf(':') + 1).trim();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(t, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String stringify(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof String s) {
            return s;
        }
        if (v instanceof List<?> || v instanceof Map<?, ?>) {
            return toJson(v);
        }
        return String.valueOf(v);
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
