package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 产销品加载 AI 应用 V1.6 · 自研模拟服务（14 条路由的业务逻辑层）。
 * <p>
 * 契约来源：《产销品加载AI应用-开发工作清单.md》V1.6 + 插件\自研插件集V1.6\工具*.json。
 * 全部为模拟输出：种子数据 = 《产品信息.txt》18 个销售品（OfferSeedService）；
 * presetValue 取自 preset_map.json；写接口幂等；门禁接口（7/9）二次校验 confirmed。
 * <p>
 * 线程安全：ConcurrentHashMap + synchronized 写入。
 */
@Service
public class OfferSimV16Service {

    private static final Logger log = LoggerFactory.getLogger(OfferSimV16Service.class);

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final long AUDIT_TIMEOUT_MS = 60_000L;
    private static final long PROGRESS_TOTAL_MS = 75_000L;
    private static final long PROGRESS_MIN_MS = 60_000L;
    private static final long PROGRESS_MAX_MS = 90_000L;

    private final ObjectMapper objectMapper;
    private final OfferSeedService seed;
    private final NodeResultService nodeResult;

    /** 配置落地档案：offer_id -> 落地配置（工具7 写入，工具2/8 读取） */
    private final Map<String, Map<String, Object>> savedConfigs = new ConcurrentHashMap<>();
    /** plan_json 摘要 -> 落地响应快照（幂等） */
    private final Map<String, Map<String, Object>> planIdempotency = new ConcurrentHashMap<>();
    /** 测试任务：globalId -> 任务状态 */
    private final Map<String, Map<String, Object>> testTasks = new ConcurrentHashMap<>();
    /** 审批单：approval_id -> 审批状态库 */
    private final Map<String, Map<String, Object>> approvals = new ConcurrentHashMap<>();
    /** product_id -> approval_id（幂等） */
    private final Map<String, String> approvalByProduct = new ConcurrentHashMap<>();
    /** 告警库：alert_id -> 告警（工具11 写入，工具10 回显） */
    private final List<Map<String, Object>> alerts = new ArrayList<>();
    /** 演示场景开关（默认全通过）：offer_id -> 注入类型集合 */
    private final Map<String, java.util.Set<String>> demoSwitches = new ConcurrentHashMap<>();
    /** 计费冲突注入：offer_id -> 风险清单 */
    private final Map<String, List<Map<String, Object>>> feeConflictInjection = new ConcurrentHashMap<>();
    /** 测点不一致注入：offer_id -> 测点编码集合 */
    private final Map<String, java.util.Set<String>> pointMismatchInjection = new ConcurrentHashMap<>();
    /** 监控异常注入：offer_id -> error_count>0 */
    private final java.util.Set<String> monitorErrorInjection = ConcurrentHashMap.newKeySet();

    public OfferSimV16Service(ObjectMapper objectMapper, OfferSeedService seed, NodeResultService nodeResult) {
        this.objectMapper = objectMapper;
        this.seed = seed;
        this.nodeResult = nodeResult;
    }

    /* ================= 接口1：相似度分析 query_similar_offer ================= */

    public Map<String, Object> similarOfferQuery(Map<String, Object> req) {
        if (MapOps.empty(req.get("businessDesc"))) {
            return paramMissing("businessDesc 必填");
        }
        List<Map<String, Object>> list = seed.matchSimilar(MapOps.str(req.get("businessDesc")));
        if (list.isEmpty()) {
            Map<String, Object> body = ok();
            body.put("resultMsg", "未命中相似销售品");
            body.put("similarOfferList", List.of());
            return body;
        }
        Map<String, Object> body = ok();
        body.put("resultMsg", "命中 " + list.size() + " 个相似销售品");
        body.put("similarOfferList", list);
        return body;
    }

    /* ================= 接口2：实时规格稽核 realtime_spec_audit ================= */

    public Map<String, Object> auditRealtime(Map<String, Object> req, long startTime) {
        if (MapOps.empty(req.get("offer_id"))) {
            return paramMissing("offer_id 必填");
        }
        if (MapOps.empty(req.get("config_json"))) {
            return codeFail("5001", "config_json 缺失或非法 JSON");
        }
        String offerId = MapOps.str(req.get("offer_id")).trim();
        Map<String, Object> offer = seed.findOffer(offerId);
        if (offer == null) {
            return codeFail("5001", "销售品未收录: " + offerId);
        }
        Map<String, Object> config = parseConfig(MapOps.str(req.get("config_json")));
        if (config == null || config.isEmpty()) {
            return codeFail("5001", "config_json 非法 JSON");
        }
        if (System.currentTimeMillis() - startTime > AUDIT_TIMEOUT_MS) {
            return codeFail("TIMEOUT", "稽核超时（60s）");
        }

        List<Map<String, Object>> errorList = new ArrayList<>();
        if (injectReject(offerId)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("item", "互斥叠加");
            err.put("level", "error");
            err.put("desc", "配置含互斥叠加项，与销售品 " + MapOps.str(offer.get("offer_name")) + " 销售规则冲突");
            err.put("suggest", "移除互斥叠加项或调整为可叠加优惠");
            errorList.add(err);
        }

        Map<String, Object> body = ok();
        body.put("pass", errorList.isEmpty() ? "1" : "0");
        body.put("error_list", errorList);
        body.put("audit_summary", errorList.isEmpty()
                ? "稽核通过：配置符合《产品信息.txt》销售品 " + MapOps.str(offer.get("offer_name")) + " 规则"
                : "稽核驳回：存在 " + errorList.size() + " 项阻断问题，请整改后重试");
        body.put("resultCode", "0");
        return body;
    }

    /* ================= 接口3：配置落地 save_product_config ================= */

    public synchronized Map<String, Object> saveProductConfig(Map<String, Object> req) {
        if (MapOps.empty(req.get("plan_id"))) {
            return paramMissing("plan_id 必填");
        }
        if (MapOps.empty(req.get("plan_json"))) {
            return paramMissing("plan_json 必填");
        }
        if (!MapOps.truthy(req.get("confirmed"))) {
            Map<String, Object> body = ok();
            body.put("status", "NOT_CONFIRMED");
            body.put("save_result", Map.of("reason", "confirmed 非 true，拒绝写入"));
            return body;
        }
        String planJson = MapOps.str(req.get("plan_json"));
        Map<String, Object> replay = planIdempotency.get(planJson);
        if (replay != null) {
            return replay;
        }
        Map<String, Object> plan = parseConfig(planJson);
        if (plan == null || plan.isEmpty()) {
            return codeFail("5001", "plan_json 非法 JSON");
        }

        String planId = MapOps.str(req.get("plan_id")).trim();
        String offerId = firstNonEmptyText(plan.get("offer_id"), plan.get("similarOfferId"),
                pickSeedOfferId(planId));
        Map<String, Object> seedOffer = seed.findOffer(offerId);
        String productId = "P" + planId;
        Map<String, Object> config = buildSavedConfig(productId, offerId, plan, seedOffer);

        List<Map<String, Object>> saveResult = new ArrayList<>();
        saveResult.add(classifyResult("基础信息", !MapOps.empty(plan.get("offer_name")) || seedOffer != null));
        saveResult.add(classifyResult("资源配置", true));
        saveResult.add(classifyResult("营销资源", true));
        saveResult.add(classifyResult("销售规则", seedOffer != null));
        long failCount = saveResult.stream().filter(r -> !"success".equals(r.get("result"))).count();

        Map<String, Object> body = ok();
        body.put("product_id", productId);
        body.put("offer_id", offerId);
        body.put("save_result", saveResult);
        body.put("status", failCount == 0 ? "SUCCESS" : (failCount < saveResult.size() ? "PARTIAL" : "FAIL"));
        body.put("saved_at", LocalDateTime.now().format(TS));

        savedConfigs.put(offerId, config);
        planIdempotency.put(planJson, body);
        log.info("[OfferSimV16] 配置落地 product_id={} offer_id={} status={}", productId, offerId, body.get("status"));
        return body;
    }

    /* ================= 接口4：测试发起 offer_test ================= */

    public synchronized Map<String, Object> testOfferStart(Map<String, Object> req) {
        if (MapOps.empty(req.get("offerId"))) {
            return camelFail("4002", "offerId 必填");
        }
        String offerId = MapOps.str(req.get("offerId")).trim();
        Map<String, Object> offer = seed.findOffer(offerId);
        if (offer == null) {
            return camelFail("4001", "销售品未收录: " + offerId);
        }
        // 防重复发起：同 offerId 进行中任务直接返回原 globalId
        for (Map<String, Object> task : testTasks.values()) {
            if (offerId.equals(MapOps.str(task.get("offer_id"))) && !Boolean.TRUE.equals(task.get("done"))) {
                Map<String, Object> body = camelOk();
                body.put("resultMsg", "测试任务进行中");
                body.put("globalId", MapOps.str(task.get("global_id")));
                return body;
            }
        }
        String globalId = "50" + LocalDateTime.now().format(STAMP)
                + String.format("%010d", ThreadLocalRandom.current().nextLong(0, 10_000_000_000L));
        List<String> sceneNbrs = sceneNbrsOf(offer);
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("global_id", globalId);
        task.put("offer_id", offerId);
        task.put("scene_nbrs", sceneNbrs);
        task.put("created_at", System.currentTimeMillis());
        long duration = Math.min(Math.max(PROGRESS_TOTAL_MS, PROGRESS_MIN_MS), PROGRESS_MAX_MS);
        task.put("duration_ms", duration);
        task.put("done", false);
        task.put("failed", false);
        testTasks.put(globalId, task);

        Map<String, Object> body = camelOk();
        body.put("resultMsg", "测试任务已发起，场景数=" + sceneNbrs.size());
        body.put("globalId", globalId);
        return body;
    }

    /* ================= 接口5：查询测试场景 get_test_scenes ================= */

    public Map<String, Object> testScenes(Map<String, Object> req) {
        if (MapOps.empty(req.get("globalId"))) {
            return camelFail("4002", "globalId 必填");
        }
        Map<String, Object> task = testTasks.get(MapOps.str(req.get("globalId")).trim());
        if (task == null) {
            return camelFail("4002", "globalId 查无对应测试任务");
        }
        Map<String, Object> offer = seed.findOffer(MapOps.str(task.get("offer_id")));
        List<Map<String, Object>> scenes = new ArrayList<>();
        int sort = 1;
        for (String nbr : castStrList(task.get("scene_nbrs"))) {
            scenes.add(sceneItem(nbr, offer, sort++));
        }
        Map<String, Object> body = camelOk();
        body.put("resultCode", "0");
        body.put("testScenes", scenes);
        return body;
    }

    /* ================= 接口6：查询测试进度 get_test_progress ================= */

    public Map<String, Object> testProgress(Map<String, Object> req) {
        if (MapOps.empty(req.get("globalId"))) {
            return camelFail("4002", "globalId 必填");
        }
        Map<String, Object> task = testTasks.get(MapOps.str(req.get("globalId")).trim());
        if (task == null) {
            return camelFail("4002", "globalId 查无对应测试任务");
        }
        List<String> scenes = castStrList(task.get("scene_nbrs"));
        int totalSteps = scenes.size() + 2;
        long elapsed = System.currentTimeMillis() - MapOps.toLong(task.get("created_at"));
        long duration = MapOps.toLong(task.get("duration_ms"));

        boolean done = elapsed >= duration;
        boolean failed = injectTaskFailed(task);
        int finished = done ? scenes.size() : (int) Math.min(scenes.size(),
                Math.max(0, elapsed * scenes.size() / duration));
        int failedCount = done && failed ? 1 : 0;
        int failIndex = failedCount > 0 ? 2 : -1;

        Map<String, Object> body = camelOk();
        body.put("resultCode", "0");
        body.put("totalSteps", String.valueOf(totalSteps));
        body.put("activeIndex", String.valueOf(done ? totalSteps - 1 : Math.min(totalSteps - 1, 2 + finished)));
        body.put("done", String.valueOf(done));
        body.put("failed", String.valueOf(failed));
        body.put("failIndex", String.valueOf(failIndex));
        body.put("totalSceneCount", String.valueOf(scenes.size()));
        body.put("finishedSceneCount", String.valueOf(finished));
        body.put("failedSceneCount", String.valueOf(failedCount));
        return body;
    }

    /* ================= 接口7：查询测试结果 get_test_result ================= */

    public Map<String, Object> testResult(Map<String, Object> req) {
        if (MapOps.empty(req.get("globalId"))) {
            return camelFail("4002", "globalId 必填");
        }
        String globalId = MapOps.str(req.get("globalId")).trim();
        Map<String, Object> task = testTasks.get(globalId);
        if (task == null) {
            return camelFail("4002", "globalId 查无对应测试任务");
        }
        if (!Boolean.TRUE.equals(task.get("done"))) {
            return camelFail("4003", "测试未完成（done!=true），请先轮询接口6");
        }
        Map<String, Object> offer = seed.findOffer(MapOps.str(task.get("offer_id")));
        String offerId = MapOps.str(task.get("offer_id"));
        Map<String, Object> presets = seed.presetsOf(offerId);
        java.util.Set<String> mismatchPoints = pointMismatchInjection.getOrDefault(offerId, java.util.Set.of());

        List<Map<String, Object>> sceneResults = new ArrayList<>();
        int sort = 1;
        for (String nbr : castStrList(task.get("scene_nbrs"))) {
            sceneResults.add(sceneResult(nbr, offer, presets, mismatchPoints, sort++));
        }
        String orderId = "ORD" + globalId.substring(2, 16);
        String offerInstId = "OI" + globalId.substring(2, 16);

        Map<String, Object> body = camelOk();
        body.put("resultCode", "0");
        body.put("resultMsg", "测试完成，共 " + sceneResults.size() + " 个场景");
        body.put("testRequestId", "TR" + globalId.substring(2));
        body.put("testRequestName", MapOps.str(offer.get("offer_name")) + "_测试验证");
        body.put("offerName", MapOps.str(offer.get("offer_name")));
        body.put("orderId", orderId);
        body.put("offerInstId", offerInstId);
        body.put("testScenes", sceneResults);
        return body;
    }

    /* ================= 接口8：计费规则校验 check_billing_rule ================= */

    public Map<String, Object> billingRulesVerify(Map<String, Object> req) {
        if (MapOps.empty(req.get("config_json"))) {
            return codeFail("3001", "config_json 缺失或非法 JSON");
        }
        Map<String, Object> config = parseConfig(MapOps.str(req.get("config_json")));
        if (config == null || config.isEmpty()) {
            return codeFail("3001", "config_json 非法 JSON");
        }
        String scene = MapOps.str(req.get("check_scene"));
        if (!scene.isBlank() && !List.of("fee", "overlay", "superposition", "all").contains(scene)) {
            return codeFail("3002", "check_scene 非法: " + scene);
        }
        String offerId = firstNonEmptyText(config.get("offer_id"), config.get("similarOfferId"));
        List<Map<String, Object>> riskList = new ArrayList<>(feeConflictInjection.getOrDefault(offerId, List.of()));
        if (riskList.isEmpty() && negativeFee(config)) {
            Map<String, Object> risk = new LinkedHashMap<>();
            risk.put("risk_type", "negative_fee");
            risk.put("risk_desc", "存在负资费项（monthly_fee<0 或优惠金额为负）");
            risk.put("suggest", "修正资费金额为非负值");
            riskList.add(risk);
        }
        Map<String, Object> body = ok();
        body.put("pass", riskList.isEmpty() ? "1" : "0");
        body.put("risk_list", riskList);
        body.put("resultCode", "0");
        return body;
    }

    /* ================= 接口9：上线审批推送 submit_release_approval ================= */

    public synchronized Map<String, Object> approvalSubmit(Map<String, Object> req) {
        if (MapOps.empty(req.get("product_id"))) {
            return camelFail("PARAM_MISSING", "product_id 必填");
        }
        if (MapOps.empty(req.get("report_url"))) {
            return camelFail("PARAM_MISSING", "report_url 必填");
        }
        if (!MapOps.truthy(req.get("approve_confirmed"))) {
            Map<String, Object> body = camelOk();
            body.put("status", "NOT_CONFIRMED");
            return body;
        }
        String productId = MapOps.str(req.get("product_id")).trim();
        String existed = approvalByProduct.get(productId);
        if (existed != null) {
            Map<String, Object> body = camelOk();
            body.put("approval_id", existed);
            body.put("status", MapOps.str(approvals.get(existed).get("status")));
            body.put("idempotent", "true");
            return body;
        }
        String approvalId = "AP" + LocalDateTime.now().format(STAMP)
                + String.format("%04d", approvals.size() + 1);
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("approval_id", approvalId);
        approval.put("product_id", productId);
        approval.put("report_url", MapOps.str(req.get("report_url")));
        String flow = MapOps.str(req.get("approval_flow"));
        approval.put("approval_flow", flow.isBlank() ? "standard" : flow);
        approval.put("status", "审批中");
        approval.put("current_node", "产品经理审核");
        approval.put("approver", "产品经理");
        approval.put("opinion", "");
        approval.put("submit_time", LocalDateTime.now().format(TS));
        approval.put("update_time", LocalDateTime.now().format(TS));
        approvals.put(approvalId, approval);
        approvalByProduct.put(productId, approvalId);
        log.info("[OfferSimV16] 审批提交 approval_id={} product_id={}", approvalId, productId);

        Map<String, Object> body = camelOk();
        body.put("approval_id", approvalId);
        body.put("status", "审批中");
        return body;
    }

    /* ================= 接口10：审批进度查询 query_approval_status ================= */

    public Map<String, Object> approvalStatus(Map<String, Object> params) {
        String approvalId = MapOps.str(params.get("approval_id")).trim();
        String productId = MapOps.str(params.get("product_id")).trim();
        if (approvalId.isEmpty() && productId.isEmpty()) {
            return camelFail("PARAM_MISSING", "approval_id 与 product_id 至少一个非空");
        }
        Map<String, Object> approval;
        if (!approvalId.isEmpty()) {
            approval = approvals.get(approvalId);
        } else {
            String aid = approvalByProduct.get(productId);
            approval = aid == null ? null : approvals.get(aid);
        }
        if (approval == null) {
            return camelFail("40404", "审批单不存在");
        }
        Map<String, Object> body = camelOk();
        body.put("approval_id", MapOps.str(approval.get("approval_id")));
        body.put("status", MapOps.str(approval.get("status")));
        body.put("current_node", MapOps.str(approval.get("current_node")));
        body.put("approver", MapOps.str(approval.get("approver")));
        body.put("opinion", MapOps.str(approval.get("opinion")));
        body.put("submit_time", MapOps.str(approval.get("submit_time")));
        body.put("update_time", MapOps.str(approval.get("update_time")));
        return body;
    }

    /* ================= 接口11：监控查询 query_product_monitor ================= */

    public Map<String, Object> productMonitor(Map<String, Object> params) {
        String productId = MapOps.str(params.get("product_id")).trim();
        if (productId.isEmpty()) {
            return camelFail("PARAM_MISSING", "product_id 必填");
        }
        int seedNum = Math.abs(productId.hashCode());
        int orderCount = 100 + seedNum % 900;
        int errorCount = monitorErrorInjection.contains(productId) ? 3 : 0;
        double feeErrorRate = errorCount > 0 ? 0.012 : (seedNum % 5) / 1000.0;

        List<Map<String, Object>> alarmList = new ArrayList<>();
        synchronized (alerts) {
            for (Map<String, Object> a : alerts) {
                if (productId.equals(MapOps.str(a.get("product_id")))) {
                    alarmList.add(a);
                }
            }
        }

        Map<String, Object> body = camelOk();
        body.put("order_count", String.valueOf(orderCount));
        body.put("error_count", String.valueOf(errorCount));
        body.put("fee_error_rate", String.format(java.util.Locale.ROOT, "%.4f", feeErrorRate));
        body.put("date_range", MapOps.str(params.get("date_range")));
        body.put("metric", MapOps.firstNonEmpty(params.get("metric"), "all"));
        body.put("alarm_list", alarmList);
        return body;
    }

    /* ================= 接口12：异常告警 send_alert ================= */

    public synchronized Map<String, Object> sendAlert(Map<String, Object> req) {
        String productId = MapOps.str(req.get("product_id")).trim();
        String level = MapOps.str(req.get("alarm_level")).trim().toLowerCase(java.util.Locale.ROOT);
        String content = MapOps.str(req.get("content")).trim();
        if (productId.isEmpty()) {
            return camelFail("PARAM_MISSING", "product_id 必填");
        }
        if (level.isEmpty()) {
            return camelFail("PARAM_MISSING", "alarm_level 必填");
        }
        if (!List.of("high", "middle", "low").contains(level)) {
            return camelFail("40011", "alarm_level 非法: " + level);
        }
        if (content.isEmpty()) {
            return camelFail("PARAM_MISSING", "content 必填");
        }
        String alertId = "AL" + LocalDateTime.now().format(STAMP)
                + String.format("%04d", alerts.size() + 1);
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("alarm_id", alertId);
        alert.put("product_id", productId);
        alert.put("alarm_level", level);
        alert.put("content", content);
        alert.put("alarm_time", LocalDateTime.now().format(TS));
        alerts.add(alert);

        Map<String, Object> body = camelOk();
        body.put("alert_id", alertId);
        body.put("status", "sent");
        return body;
    }

    /* ================= 接口13：节点结果存储 save_node_result（V1.6 key 规范） ================= */

    public synchronized Map<String, Object> saveNodeResult(Map<String, Object> req) {
        String key = MapOps.str(req.get("key")).trim();
        String resultJson = MapOps.str(req.get("result_json"));
        String status = MapOps.str(req.get("status"));
        if (key.isEmpty() || !validKey(key)) {
            return legacyFail(5002, "invalid key format: " + key + "（须为 plan_id 或 EXEC{execution_id}_STAGE{n}）");
        }
        return nodeResultDelegate().saveV16(key, resultJson, status);
    }

    /* ================= 接口14：节点结果查询 query_node_result（V1.6 key 规范） ================= */

    public Map<String, Object> queryNodeResult(Map<String, Object> params) {
        String key = MapOps.str(params.get("key")).trim();
        if (key.isEmpty() || !validKey(key)) {
            return legacyFail(5002, "invalid key format: " + key + "（须为 plan_id 或 EXEC{execution_id}_STAGE{n}）");
        }
        return nodeResultDelegate().queryV16(key);
    }

    /* ================= 演示场景开关（清单 §2 #3） ================= */

    /** 注入稽核驳回用例（工具2 返回 pass=0） */
    public void injectAuditReject(String offerId, boolean enable) {
        switchOf(offerId, "audit_reject", enable);
    }

    /** 注入资费冲突用例（工具8 返回 pass=0） */
    public void injectFeeConflict(String offerId, boolean enable) {
        if (enable) {
            List<Map<String, Object>> risks = new ArrayList<>();
            Map<String, Object> risk = new LinkedHashMap<>();
            risk.put("risk_type", "overlap_conflict");
            risk.put("risk_desc", "叠加优惠与销售品资费互斥，叠加后月费低于边界价差阈值");
            risk.put("suggest", "调整叠加优惠或改用不互斥的优惠项");
            risks.add(risk);
            feeConflictInjection.put(offerId, risks);
        } else {
            feeConflictInjection.remove(offerId);
        }
    }

    /** 注入测点不一致用例（工具7 P_STATUS 返回 resultCode=1） */
    public void injectPointMismatch(String offerId, boolean enable) {
        if (enable) {
            pointMismatchInjection.put(offerId, java.util.Set.of("P_STATUS"));
        } else {
            pointMismatchInjection.remove(offerId);
        }
    }

    /** 注入监控异常用例（工具11 返回 error_count>0） */
    public void injectMonitorError(String offerId, boolean enable) {
        if (enable) {
            monitorErrorInjection.add(offerId);
        } else {
            monitorErrorInjection.remove(offerId);
        }
    }

    /* ---------------- 内部工具 ---------------- */

    private NodeResultService nodeResultDelegate() {
        return nodeResult;
    }

    private boolean validKey(String key) {
        // EXEC{execution_id}_STAGE{n}：execution_id 自身以 EXE 开头，整体形如 EXECEXE..._STAGE1；
        // 演示/工作流常直接写 EXE+时间戳+_STAGE{n}，两种前缀均放行
        if (key.matches("EXE\\w*_STAGE\\d+")) {
            return true;
        }
        return key.matches("PLAN\\d+");
    }

    private List<String> sceneNbrsOf(Map<String, Object> offer) {
        List<String> scenes = new ArrayList<>();
        scenes.add("S_O_TC");
        if (!"rights".equals(offer.get("series"))) {
            scenes.add("S_ADD_CARD");
        }
        scenes.add("S_U_TC");
        return scenes;
    }

    private Map<String, Object> sceneItem(String nbr, Map<String, Object> offer, int sort) {
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("testSceneId", "TS" + sort);
        scene.put("testSceneNbr", nbr);
        switch (nbr) {
            case "S_O_TC" -> {
                scene.put("testSceneName", "套餐新装");
                scene.put("testSceneDesc", "销售品 " + MapOps.str(offer.get("offer_name"))
                        + " 新装订购受理验证（生效规则：" + MapOps.str(offer.get("order_rule")) + "）");
            }
            case "S_ADD_CARD" -> {
                scene.put("testSceneName", "副卡加装");
                scene.put("testSceneDesc", MapOps.truthy(offer == null ? null : offer.get("allow_sub_card"))
                        ? "副卡共享规则：" + MapOps.str(castMap(offer.get("sub_card")).get("共享规则"))
                        : "该销售品不支持副卡加装");
            }
            default -> {
                scene.put("testSceneName", "套餐退订");
                scene.put("testSceneDesc", "退订规则：" + MapOps.str(offer.get("cancel_rule")));
            }
        }
        scene.put("sort", String.valueOf(sort));
        return scene;
    }

    private Map<String, Object> sceneResult(String nbr, Map<String, Object> offer,
                                            Map<String, Object> presets, java.util.Set<String> mismatch, int sort) {
        Map<String, Object> scene = sceneItem(nbr, offer, sort);
        List<Map<String, Object>> points = new ArrayList<>();
        int success = 0;
        int fail = 0;
        for (String code : seed.testPoints()) {
            String preset = MapOps.str(presets.get(code));
            String testValue = preset;
            boolean mismatched = mismatch.contains(code) && "S_O_TC".equals(nbr);
            if (mismatched) {
                testValue = "已失效";
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("testPointNbr", code);
            point.put("presetValue", preset);
            point.put("testValue", testValue);
            point.put("resultCode", mismatched ? "1" : "0");
            point.put("resultMsg", mismatched ? "实测值与预期值不一致" : "比对一致");
            if (mismatched) {
                fail++;
            } else {
                success++;
            }
            points.add(point);
        }
        scene.put("testCaseCount", String.valueOf(points.size()));
        scene.put("successTestCaseCount", String.valueOf(success));
        scene.put("failTestCaseCount", String.valueOf(fail));
        scene.put("testCasePointResults", points);
        scene.put("objTestSceneRel", "resultMsg=场景测试通过; summaryDesc="
                + MapOps.str(scene.get("testSceneDesc")) + "; suggestion=无");
        return scene;
    }

    private boolean negativeFee(Map<String, Object> config) {
        Object fee = config.get("monthly_fee");
        return fee instanceof Number n && n.doubleValue() < 0;
    }

    private Map<String, Object> buildSavedConfig(String productId, String offerId,
                                                 Map<String, Object> plan, Map<String, Object> seedOffer) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("product_id", productId);
        config.put("offer_id", offerId);
        config.put("offer_name", firstNonEmptyText(plan.get("offer_name"),
                seedOffer == null ? "" : MapOps.str(seedOffer.get("offer_name"))));
        config.put("in_fee", seedOffer == null ? plan.get("in_fee") : seedOffer.get("in_fee"));
        config.put("out_fee", seedOffer == null ? plan.get("out_fee") : seedOffer.get("out_fee"));
        config.put("order_rule", seedOffer == null ? "" : seedOffer.get("order_rule"));
        config.put("cancel_rule", seedOffer == null ? "" : seedOffer.get("cancel_rule"));
        config.put("plan_json", plan);
        return config;
    }

    private Map<String, Object> classifyResult(String category, boolean ok) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("category", category);
        item.put("result", ok ? "success" : "fail");
        item.put("reason", ok ? "" : "销售品未收录或字段缺失");
        return item;
    }

    private String pickSeedOfferId(String planId) {
        List<Map<String, Object>> all = seed.listOffers();
        int idx = Math.abs(planId.hashCode()) % all.size();
        return MapOps.str(all.get(idx).get("offer_id"));
    }

    private String firstNonEmptyText(Object... values) {
        for (Object v : values) {
            if (v != null && !MapOps.str(v).isBlank()) {
                return MapOps.str(v);
            }
        }
        return "";
    }

    private boolean injectReject(String offerId) {
        java.util.Set<String> switches = demoSwitches.get(offerId);
        return switches != null && switches.contains("audit_reject");
    }

    private boolean injectTaskFailed(Map<String, Object> task) {
        java.util.Set<String> switches = demoSwitches.get(MapOps.str(task.get("offer_id")));
        return switches != null && switches.contains("test_failed");
    }

    private void switchOf(String offerId, String name, boolean enable) {
        java.util.Set<String> set = demoSwitches.computeIfAbsent(offerId,
                k -> ConcurrentHashMap.newKeySet());
        if (enable) {
            set.add(name);
        } else {
            set.remove(name);
        }
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> castStrList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    /** V1.6 统一成功体：{resultCode:"0", resultMsg:"success"} */
    private static Map<String, Object> ok() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", "0");
        body.put("resultMsg", "success");
        return body;
    }

    private static Map<String, Object> camelOk() {
        return ok();
    }

    /** snake_case 业务失败体（工具2/3/7/8/13/14，错误码为数字字符串） */
    private static Map<String, Object> codeFail(String code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", code);
        body.put("resultMsg", msg);
        return body;
    }

    private static Map<String, Object> camelFail(String code, String msg) {
        return codeFail(code, msg);
    }

    private static Map<String, Object> paramMissing(String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", "PARAM_MISSING");
        body.put("resultMsg", msg);
        return body;
    }

    /** 接口13/14（平台复用对齐项）沿用既有 NodeResultService 契约：{code:int, msg} */
    private static Map<String, Object> legacyFail(int code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        return body;
    }
}
