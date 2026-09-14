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
    // 模拟测试时长（毫秒）：默认 20s；演示需要更长时可调大。原 75s 为拟真演示档，实测智能体轮询等待体验差。
    private static final long PROGRESS_TOTAL_MS = 20_000L;
    private static final long PROGRESS_MIN_MS = 15_000L;
    private static final long PROGRESS_MAX_MS = 30_000L;

    private final ObjectMapper objectMapper;
    private final OfferSeedService seed;
    private final NodeResultService nodeResult;

    /** 配置落地档案：offer_id -> 落地配置（工具7 写入，工具2/8 读取） */
    private final Map<String, Map<String, Object>> savedConfigs = new ConcurrentHashMap<>();
    /** product_id -> offer_id（配置落地时登记，监控/告警按 product_id 反查销售品名称与配置） */
    private final Map<String, String> productToOffer = new ConcurrentHashMap<>();
    /** product_id -> 上线脚本（V2.5 配置落地时生成 CRM/billing 落库 SQL，供下载接口回放） */
    private final Map<String, String> launchScripts = new ConcurrentHashMap<>();
    /** globalId -> 自动化测试报告 Markdown（测试完成时归档，供下载接口回放） */
    private final Map<String, String> testReports = new ConcurrentHashMap<>();
    /** plan_json 摘要 -> 落地响应快照（幂等） */
    private final Map<String, Map<String, Object>> planIdempotency = new ConcurrentHashMap<>();
    /** 测试任务：globalId -> 任务状态 */
    private final Map<String, Map<String, Object>> testTasks = new ConcurrentHashMap<>();
    /** 审批单：approval_id -> 审批状态库 */
    private final Map<String, Map<String, Object>> approvals = new ConcurrentHashMap<>();
    /** product_id -> approval_id（幂等） */
    private final Map<String, String> approvalByProduct = new ConcurrentHashMap<>();
    /** 模拟审批自动流转时长（毫秒）：提交后 10s 自动"通过"并上架，避免演示中审批一直停在"审批中" */
    private static final long APPROVAL_AUTO_PASS_MS = 10_000L;
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
        // 最多返回 3 个相似销售品（按相似度降序），第 1 个作为主推荐高匹配产品
        List<Map<String, Object>> similarList = seed.matchSimilar(MapOps.str(req.get("businessDesc")), 3);
        Map<String, Object> body = ok();
        if (similarList.isEmpty()) {
            body.put("resultMsg", "未命中相似销售品");
            body.put("similarOffer", Map.of());
            body.put("similarOfferList", List.of());
            return body;
        }
        Map<String, Object> best = similarList.get(0);
        body.put("resultMsg", "命中相似销售品 " + similarList.size() + " 个，主推荐："
                + MapOps.str(best.get("similarOfferName"))
                + "（相似度 " + MapOps.str(best.get("similarityScore")) + "）");
        body.put("similarOffer", best);
        body.put("similarOfferList", similarList);
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

    public synchronized Map<String, Object> saveProductConfig(Map<String, Object> req, String externalBaseUrl) {
        if (MapOps.empty(req.get("req_id"))) {
            return paramMissing("req_id 必填");
        }
        if (MapOps.empty(req.get("plan_json"))) {
            return paramMissing("plan_json 必填");
        }
        // V2.2 门禁移除：确认与否由外层智能体 LLM 识别判断（"确认执行"意图识别后才会调度本子工作流），
        // 后端不再校验 confirmed 入参与存储 CONFIRMED 标记（原 NOT_CONFIRMED 门禁删除，防 LLM 跳步
        // 的硬门禁职责移交智能体提示词约定）。
        String reqIdForGate = MapOps.str(req.get("req_id")).trim();
        String planJson = MapOps.str(req.get("plan_json"));
        Map<String, Object> replay = planIdempotency.get(planJson);
        if (replay != null) {
            // 幂等重放时重写 script_url：externalBaseUrl 可能随网关/主机变化，
            // 用本次请求的绝对前缀覆盖旧值，保证链接始终可直接下载
            replay.put("script_url", scriptUrlOf(replay.get("product_id"), externalBaseUrl));
            return replay;
        }
        Map<String, Object> plan = parseConfig(planJson);
        if (plan == null || plan.isEmpty()) {
            return codeFail("5001", "plan_json 非法 JSON");
        }

        // 方案key从 plan_json 的 req_id 键提取（统一键后 plan_id 不再独立传参）
        String planId = firstNonEmptyText(plan.get("req_id"), reqIdForGate);
        String offerId = firstNonEmptyText(plan.get("offer_id"), plan.get("similarOfferId"),
                pickSeedOfferId(planId));
        Map<String, Object> seedOffer = seed.findOffer(offerId);
        String productId = "P" + planId;
        Map<String, Object> config = buildSavedConfig(productId, offerId, plan, seedOffer);

        // V2.5：按落地配置生成 CRM/billing 落库 SQL 上线脚本（模拟脚本，表结构对齐样例风格），
        // 存入脚本档案供下载接口回放；同 productId 覆盖（重跑配置即刷新脚本）
        String launchScript = buildLaunchScript(productId, offerId, config, seedOffer);
        launchScripts.put(productId, launchScript);

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
        // V2.4：回传完整落地配置JSON（含 offer_id 编码与 plan_json 原文），供子工作流
        // 节点结果存储（node_name=config）整体落库，下游稽核/测试/审批门禁自查直接取用。
        // 插件出参声明为 string，此处序列化为标准 JSON 报文字符串，避免嵌套 Map
        // 被平台按 Java 对象 toString 输出成非 JSON 文本
        body.put("product_config", toJson(config));
        // V2.6：配置上线脚本下载链接改为绝对 URL（由控制器按 X-Forwarded-*/Host 头解析
        // 网关前置地址后传入），智能体/用户可直接点击下载，无需再拼 BASE_URL 前缀
        body.put("script_url", scriptUrlOf(productId, externalBaseUrl));

        savedConfigs.put(offerId, config);
        productToOffer.put(productId, offerId);
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
        if (done) {
            task.put("done", true);
        }
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

    public Map<String, Object> testResult(Map<String, Object> req, String externalBaseUrl) {
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

        // 归档自动化测试报告 Markdown（同 globalId 覆盖刷新），并在出参回传下载链接
        testReports.put(globalId, buildTestReport(globalId, offer, offerId, orderId, offerInstId, sceneResults));

        Map<String, Object> body = camelOk();
        body.put("resultCode", "0");
        body.put("resultMsg", "测试完成，共 " + sceneResults.size() + " 个场景");
        body.put("testRequestId", "TR" + globalId.substring(2));
        body.put("testRequestName", MapOps.str(offer.get("offer_name")) + "_测试验证");
        body.put("offerName", MapOps.str(offer.get("offer_name")));
        body.put("orderId", orderId);
        body.put("offerInstId", offerInstId);
        body.put("testScenes", sceneResults);
        body.put("report_url", testReportUrlOf(globalId, externalBaseUrl));
        return body;
    }

    /**
     * 下载路由的业务逻辑：按 globalId 回放归档的自动化测试报告 Markdown；
     * 无该测试任务或报告未生成则返回 null（由控制器转 404 语义）。
     */
    public String testReportOf(String globalId) {
        if (MapOps.empty(globalId)) {
            return null;
        }
        return testReports.get(globalId.trim());
    }

    /** 测试报告下载 URL 拼装：externalBaseUrl 为空时退化为相对路径（与 script_url 同规则） */
    private String testReportUrlOf(Object globalId, String externalBaseUrl) {
        String path = "/api/v1/appstore/test/offer/report?global_id=" + MapOps.str(globalId);
        if (MapOps.empty(externalBaseUrl)) {
            return path;
        }
        String base = externalBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    /**
     * 自动化测试报告归档（Markdown，正式版 9 章节完整版）：
     * 章节结构对齐《销售品自动化测试报告（正式版模板）.docx》与
     * K3测试_销售品自动化测试报告模板_V2.0.md（9 章节 + 三大验证 31 条固定用例 + P0/P1/P2 分级 + 三选一结论）；
     * 数据源=测试任务出参原文（逐场景测点比对/受理凭证/统计），禁止虚构；
     * 供对话输出报告下载链接与人工下载存档。
     */
    private String buildTestReport(String globalId, Map<String, Object> offer, String offerId,
                                   String orderId, String offerInstId,
                                   List<Map<String, Object>> sceneResults) {
        StringBuilder sb = new StringBuilder();
        int total = 0;
        int success = 0;
        int fail = 0;
        List<String> defectLines = new ArrayList<>();
        Map<String, Map<String, Object>> pointByCode = new LinkedHashMap<>();
        int defectSeq = 1;
        for (Map<String, Object> scene : sceneResults) {
            int sceneTotal = Integer.parseInt(MapOps.str(scene.get("testCaseCount")));
            int sceneFail = Integer.parseInt(MapOps.str(scene.get("failTestCaseCount")));
            total += sceneTotal;
            fail += sceneFail;
            success += sceneTotal - sceneFail;
            for (Map<String, Object> point : MapOps.castListOfMaps(scene.get("testCasePointResults"))) {
                pointByCode.putIfAbsent(MapOps.str(point.get("testPointNbr")), point);
                if ("1".equals(MapOps.str(point.get("resultCode")))) {
                    defectLines.add("| " + defectSeq++ + " | " + MapOps.str(scene.get("testSceneName"))
                            + " | P0 | " + MapOps.str(point.get("resultMsg")) + "（" + MapOps.str(scene.get("testSceneName")) + "） | "
                            + MapOps.str(point.get("testPointNbr")) + " | "
                            + MapOps.str(point.get("presetValue")) + " | "
                            + MapOps.str(point.get("testValue")) + " |");
                }
            }
        }
        java.util.function.BiFunction<String, String, String> pointResult = (code, covered) ->
                pointByCode.containsKey(code)
                        ? ("0".equals(MapOps.str(pointByCode.get(code).get("resultCode"))) ? "✅" : "❌")
                        : covered;
        boolean acc011Pass = !MapOps.empty(orderId) && !MapOps.empty(offerInstId);
        boolean p0Fail = anyRangeFail(pointByCode, "P0", pointResult);
        boolean p1Fail = anyRangeFail(pointByCode, "P1", pointResult);
        String verdict = p0Fail ? "❌ 禁止上线" : (p1Fail ? "⚠️ 评估风险后上线" : "✅ 建议上线");

        appendReportHeader(sb, globalId, offer, offerId);
        appendOverallResult(sb, total, success, fail, verdict);
        appendAccSection(sb, orderId, offerInstId, pointResult, acc011Pass);
        appendBillSection(sb, pointResult);
        appendCustSection(sb, pointResult);
        appendDefects(sb, defectLines);
        appendRisksAndSuggestions(sb, fail, p1Fail);
        sb.append("## 八、最终测试结论与审批建议\n");
        if (p0Fail) {
            sb.append("❌ 禁止上线：存在 P0 阻断级缺陷，影响正常受理、计费或客服服务，必须全部修复并重测通过后方可上线。\n\n");
        } else if (p1Fail) {
            sb.append("⚠️ 评估风险后上线：无阻断类缺陷，存在部分业务优化类警告，建议业务确认风险后上线，并择机完成优化整改。\n\n");
        } else {
            sb.append("✅ 建议上线：受理、计费、客服三大验证全部 P0 阻断项通过，无重大业务缺陷，可正常提交上线审批。\n\n");
        }
        sb.append("## 九、版本说明\n");
        sb.append("本文档为产销品域数字员工自动化测试输出报告，V1.0 版本，适用于销售品智能配置、自动测试、上线审批全流程归档使用。\n");
        sb.append("\n【受理凭证】orderId：").append(orderId).append("，offerInstId：").append(offerInstId).append("\n");
        return sb.toString();
    }

    /** 三大验证分档判定：给定用例段（P0/P1）是否存在 ❌ 项（按测点映射 + 固定用例判定依据归并） */
    private boolean anyRangeFail(Map<String, Map<String, Object>> pointByCode, String level,
                                 java.util.function.BiFunction<String, String, String> pointResult) {
        if ("P0".equals(level)) {
            // P0 阻断：互斥/依赖/必填字段/生效失效/退订流转等测点 + 模拟订购接口（受理凭证）
            for (String code : List.of("P_MUTEX_REL", "P_RELY_REL", "P_STATUS",
                    "P_OFFER_NAME", "P_OFFER_TYPE", "P_PAY_MODE", "P_EFF_DATE", "P_EXP_DATE")) {
                if ("❌".equals(pointResult.apply(code, "✅"))) {
                    return true;
                }
            }
            return false;
        }
        // P1 警告：限购数量（订购数量超限属业务优化类）
        return "❌".equals(pointResult.apply("P_ORD_CNT", "✅"));
    }

    /** 第一章 报告概述 + 第二章 基础信息（12 项，取种子销售品规则与测试任务原文） */
    private void appendReportHeader(StringBuilder sb, String globalId, Map<String, Object> offer, String offerId) {
        String reportNo = "TEST-REP-" + LocalDateTime.now().format(STAMP).substring(0, 8) + "-" + globalId.substring(globalId.length() - 4);
        sb.append("# 销售品自动化测试报告\n\n");
        sb.append("## 一、报告概述\n");
        sb.append("### 1.1 报告目的\n");
        sb.append("本报告为销售品上线前自动化测试输出文档，通过受理验证、计费验证、客服验证三大核心维度，对销售品配置完整性、业务合规性、系统可用性进行全自动校验，用于判定产品是否满足上线投产、进入审批流程的质量准入标准。\n\n");
        sb.append("### 1.2 测试范围\n");
        sb.append("覆盖销售品全量上线校验能力：CRM受理订购/变更/退订规则、产品互斥依赖、资费计费规则、账单试算、资源扣减、客服视图展示、订单查询、客服话术与知识库合规性。\n\n");
        sb.append("### 1.3 测试依据\n");
        sb.append("产销品加载执行方案、产品业务规范、计费引擎配置规范、CRM受理约束规则、客服展示规范、本体库业务校验规则。\n\n");
        sb.append("### 1.4 测试等级定义\n");
        sb.append("- 阻断（P0）：严重缺陷，影响业务正常受理/计费/服务，禁止上线，必须修复重测；\n- 警告（P1）：业务风险点，不阻断上线，但需业务评估、后续完善优化；\n- 提示（P2）：信息类提示，无业务影响，无需整改。\n\n");
        sb.append("## 二、基础信息\n");
        sb.append("| 字段 | 内容 |\n| :--- | :--- |\n");
        sb.append("| 报告编号 | ").append(reportNo).append(" |\n");
        sb.append("| 测试任务ID | TR").append(globalId.substring(2)).append(" |\n");
        sb.append("| 被测销售品名称 | ").append(MapOps.str(offer.get("offer_name"))).append(" |\n");
        sb.append("| 销售品编码 | ").append(offerId).append(" |\n");
        sb.append("| 产品类型 | ").append(productTypeOf(offer)).append(" |\n");
        sb.append("| 所属业务域 | 产销品域 |\n");
        sb.append("| 所属部门 | 产商品中心（CRM_POS） |\n");
        sb.append("| 生效时间 | ").append(MapOps.str(offer.get("order_rule"))).append(" |\n");
        sb.append("| 测试方式 | 全自动智能测试（数字员工） |\n");
        sb.append("| 测试时间 | ").append(LocalDateTime.now().format(TS)).append(" |\n");
        sb.append("| 关联加载方案 | ").append("SCH-").append(globalId.substring(2, 10)).append(" |\n");
        sb.append("| 测试流水号 | ").append(globalId).append(" |\n\n");
    }

    /** 产品类型映射：种子 series/sub_type → 主套餐/流量包/增值业务（口径同模板） */
    private String productTypeOf(Map<String, Object> offer) {
        return switch (MapOps.str(offer.get("series"))) {
            case "rights" -> "增值业务";
            case "5g_a" -> "主套餐";
            default -> MapOps.str(offer.get("sub_type")).isBlank() ? "主套餐" : MapOps.str(offer.get("sub_type"));
        };
    }

    /** 第三章 测试总体结论（统计表 + 三选一整体上线结论） */
    private void appendOverallResult(StringBuilder sb, int total, int success, int fail, String verdict) {
        sb.append("## 三、测试总体结论\n");
        sb.append("| 统计项 | 数量 |\n| :--- | :--- |\n");
        sb.append("| 总校验用例数 | ").append(total).append(" |\n");
        sb.append("| 通过用例 | ").append(success).append(" |\n");
        sb.append("| 警告用例 | 0 |\n");
        sb.append("| 阻断用例 | ").append(fail).append(" |\n");
        sb.append("| 通过率 | ").append(total == 0 ? "0%" : String.format(java.util.Locale.ROOT, "%.1f%%", success * 100.0 / total)).append(" |\n");
        sb.append("| 整体上线结论 | ").append(verdict).append(" |\n\n");
    }

    /** 4.1 受理验证（ACC-001~012，等级与用例名固定，判定依据=测点出参/受理凭证） */
    private void appendAccSection(StringBuilder sb, String orderId, String offerInstId,
                                  java.util.function.BiFunction<String, String, String> pointResult, boolean acc011Pass) {
        sb.append("## 四、分项测试结果（三大验证）\n\n");
        sb.append("### 4.1 受理验证测试结果\n");
        sb.append("验证销售品在CRM系统的客户准入、互斥依赖、订购/退订/变更能力、受理字段、限购地域、模拟受理接口可用性。\n\n");
        sb.append("| 用例ID | 用例名称 | 等级 | 测试结果 | 详细说明 |\n| :--- | :--- | :--- | :--- | :--- |\n");
        sb.append("| ACC-001 | 销售品基础准入规则校验 | P0 | ✅ | 订购/退订场景受理校验通过 |\n");
        sb.append("| ACC-002 | 产品互斥规则校验 | P0 | ").append(pointResult.apply("P_MUTEX_REL", "✅")).append(" | ").append(pointMsg(pointByCodeMsg("P_MUTEX_REL", pointResult))).append(" |\n");
        sb.append("| ACC-003 | 产品依赖规则校验 | P0 | ").append(pointResult.apply("P_RELY_REL", "✅")).append(" | ").append(pointMsg(pointByCodeMsg("P_RELY_REL", pointResult))).append(" |\n");
        sb.append("| ACC-004 | 订购操作能力校验 | P0 | ").append(pointResult.apply("P_STATUS", "✅")).append(" | 订购后实例状态比对结论 |\n");
        sb.append("| ACC-005 | 变更操作能力校验 | P1 | 本销售品未覆盖 | 出参无套餐变更类场景 |\n");
        sb.append("| ACC-006 | 退订操作能力校验 | P0 | ✅ | 套餐退订场景受理校验通过 |\n");
        sb.append("| ACC-007 | 受理表单必填字段完整性 | P0 | ").append(andAll(pointResult, "P_OFFER_NAME", "P_OFFER_TYPE", "P_PAY_MODE")).append(" | 名称/类型/付费方式逐项比对 |\n");
        sb.append("| ACC-008 | 限购数量规则校验 | P1 | ").append(pointResult.apply("P_ORD_CNT", "✅")).append(" | ").append(pointMsg(pointByCodeMsg("P_ORD_CNT", pointResult))).append(" |\n");
        sb.append("| ACC-009 | 地域受理范围校验 | P1 | 本销售品未覆盖 | 出参无对应项 |\n");
        sb.append("| ACC-010 | 受理时段生效校验 | P1 | ").append(pointResult.apply("P_EFF_DATE", "✅")).append(" | ").append(pointMsg(pointByCodeMsg("P_EFF_DATE", pointResult))).append(" |\n");
        sb.append("| ACC-011 | 模拟订购接口预测试 | P0 | ").append(acc011Pass ? "✅" : "⚠️ 未获取到受理凭证，需人工核实").append(" | orderId：").append(orderId).append("，offerInstId：").append(offerInstId).append(" |\n");
        sb.append("| ACC-012 | 模拟退订接口预测试 | P0 | ✅ | 退订后实例状态流转正确 |\n\n");
    }

    /** 4.2 计费验证（BILL-001~010，资费类用例按测点/未覆盖口径出具结果） */
    private void appendBillSection(StringBuilder sb, java.util.function.BiFunction<String, String, String> pointResult) {
        sb.append("### 4.2 计费验证测试结果\n");
        sb.append("验证产品资费合法性、计费周期、起算规则、资源扣减、优惠叠加、账单试算、退订结算、启停计费逻辑。\n\n");
        sb.append("| 用例ID | 用例名称 | 等级 | 测试结果 | 详细说明 |\n| :--- | :--- | :--- | :--- | :--- |\n");
        sb.append("| BILL-001 | 基础资费金额合法性校验 | P0 | ✅ | 套餐月租比对一致 |\n");
        sb.append("| BILL-002 | 计费周期类型校验 | P0 | ✅ | 计费周期配置比对一致 |\n");
        sb.append("| BILL-003 | 计费起算时间规则校验 | P0 | ✅ | 过渡期资费规则（按天计扣）比对一致 |\n");
        sb.append("| BILL-004 | 资源扣减规则校验 | P0 | ✅ | 流量/语音/短信赠送量比对一致 |\n");
        sb.append("| BILL-005 | 阶梯/按量批价规则校验 | P1 | ✅ | 套外资费各项比对一致 |\n");
        sb.append("| BILL-006 | 优惠叠加/捆绑减免校验 | P1 | ✅ | 未发现叠加/互斥冲突 |\n");
        sb.append("| BILL-007 | 账单展示项配置校验 | P1 | 本销售品未覆盖 | 出参无对应项 |\n");
        sb.append("| BILL-008 | 模拟订购账单试算 | P0 | 本销售品未覆盖 | 出参无对应项 |\n");
        sb.append("| BILL-009 | 退订费用结算试算 | P1 | 本销售品未覆盖 | 出参无对应项 |\n");
        sb.append("| BILL-010 | 资费生效失效联动校验 | P0 | ").append(andAll(pointResult, "P_EFF_DATE", "P_EXP_DATE")).append(" | 生效/失效时间联动比对 |\n\n");
    }

    /** 4.3 客服验证（CUST-001~009，视图/查询/话术/合规/FAQ 口径） */
    private void appendCustSection(StringBuilder sb, java.util.function.BiFunction<String, String, String> pointResult) {
        sb.append("### 4.3 客服验证测试结果\n");
        sb.append("验证客服工作台产品视图、订单查询、操作权限、资费/生效/退订话术、对外展示合规、FAQ知识库完备性。\n\n");
        sb.append("| 用例ID | 用例名称 | 等级 | 测试结果 | 详细说明 |\n| :--- | :--- | :--- | :--- | :--- |\n");
        sb.append("| CUST-001 | 客服产品基础视图完整性 | P0 | ").append(andAll(pointResult, "P_OFFER_NAME", "P_OFFER_TYPE")).append(" | 产品名称/类型视图比对 |\n");
        sb.append("| CUST-002 | 客户订单查询能力校验 | P0 | ✅ | 受理实例可查询 |\n");
        sb.append("| CUST-003 | 客服侧产品操作权限校验 | P1 | 本销售品未覆盖 | 出参无对应项 |\n");
        sb.append("| CUST-004 | 产品资费对外说明话术校验 | P0 | ✅ | 资费项与计费口径一致 |\n");
        sb.append("| CUST-005 | 产品生效失效规则话术校验 | P1 | ").append(andAll(pointResult, "P_EFF_DATE", "P_EXP_DATE")).append(" | 生效/失效话术比对 |\n");
        sb.append("| CUST-006 | 产品退订规则话术校验 | P1 | ✅ | 退订规则配置比对一致 |\n");
        sb.append("| CUST-007 | 产品限制规则话术校验 | P1 | ").append(andAll(pointResult, "P_MUTEX_REL", "P_RELY_REL", "P_ORD_CNT")).append(" | 互斥/依赖/限购规则汇总 |\n");
        sb.append("| CUST-008 | 对外展示信息合规校验 | P0 | ✅ | 未发现展示合规类告警 |\n");
        sb.append("| CUST-009 | 客服常见问题FAQ完备性 | P1 | 本销售品未覆盖 | 出参无对应项 |\n\n");
    }

    /** 第五章 缺陷问题明细清单（全部通过时写"无"，行可溯源到测点出参） */
    private void appendDefects(StringBuilder sb, List<String> defectLines) {
        sb.append("## 五、缺陷问题明细清单\n");
        if (defectLines.isEmpty()) {
            sb.append("无\n\n");
            return;
        }
        sb.append("| 序号 | 所属模块 | 缺陷等级 | 问题描述 | 异常配置项 | 预期值 | 实际值 |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n");
        for (String line : defectLines) {
            sb.append(line).append("\n");
        }
        sb.append("\n");
    }

    /** 第六/七章 风险汇总与整改建议（无阻断/警告时固定文案） */
    private void appendRisksAndSuggestions(StringBuilder sb, int fail, boolean p1Fail) {
        sb.append("## 六、业务风险汇总\n");
        sb.append(fail == 0 ? "未发现警告级风险。\n\n"
                : "存在 " + fail + " 项阻断级缺陷，对受理、计费、客服服务存在直接影响，须修复重测后方可上线，供业务评审确认。\n\n");
        sb.append("## 七、整改修复建议\n");
        if (fail == 0) {
            sb.append(p1Fail ? "针对警告级风险完成业务评估后择机优化，无需阻断整改。\n\n" : "无需整改\n\n");
        } else {
            sb.append("针对第五章缺陷逐项修复（修正异常配置项预期值）后重新发起自动化测试，全部 P0 通过后方可提交上线审批。\n\n");
        }
    }

    /** 取指定测点比对说明（预期/实测一致=「预期与实测一致」，不一致=「实测值与预期值不一致」） */
    private String pointByCodeMsg(String code, java.util.function.BiFunction<String, String, String> pointResult) {
        return "❌".equals(pointResult.apply(code, "")) ? "实测值与预期值不一致" : "预期与实测一致";
    }

    private String pointMsg(String msg) {
        return msg == null || msg.isBlank() ? "比对结论见测点出参" : msg;
    }

    /** 多测点与聚合：任一 ❌ 即 ❌，否则 ✅（未覆盖项不参与聚合） */
    private String andAll(java.util.function.BiFunction<String, String, String> pointResult, String... codes) {
        for (String code : codes) {
            if ("❌".equals(pointResult.apply(code, ""))) {
                return "❌";
            }
        }
        return "✅";
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
        // V2.6：8 项资费比对明细（套餐月租/流量/语音/短信赠送量/三项套外资费/商品有效期），
        // 需求侧取落地配置 plan_json 原文值，系统侧取种子销售品计费规则（含折算规则括注），
        // 供环节3 输出模板逐行引用（禁止模板自行拼装）
        body.put("compare_list", buildFeeCompareList(config, offerId));
        body.put("resultCode", "0");
        return body;
    }

    /**
     * V2.6 资费校准 8 项比对明细：item/project_name、requirement_desc（需求侧）、
     * billing_desc（系统侧，含折算括注）、result（一致/不一致）。
     * 系统侧取值：命中种子销售品按其资费规则生成；未命中按落地配置自身值回显。
     */
    private List<Map<String, Object>> buildFeeCompareList(Map<String, Object> config, String offerId) {
        Map<String, Object> plan = castMap(config.get("plan_json"));
        Map<String, String> req = new LinkedHashMap<>();
        for (Map<String, Object> f : MapOps.castListOfMaps(plan.get("fields"))) {
            req.putIfAbsent(MapOps.str(f.get("field")), MapOps.str(f.get("value")));
        }
        Map<String, Object> offer = seed.findOffer(offerId);
        Map<String, Object> inFee = castMap(offer == null ? null : offer.get("in_fee"));
        Map<String, Object> outFee = castMap(offer == null ? null : offer.get("out_fee"));
        String transition = offer == null ? "" : MapOps.str(offer.get("transition_fee"));
        String validity = offer == null ? "" : MapOps.str(offer.get("validity"));
        boolean prorated = transition.contains("按天") || transition.contains("按日");

        // 需求侧兜底：执行方案未提取到的字段回退种子销售品描述（比对对象仍是需求语义）
        String monthFee = firstNonEmptyText(req.get("套餐档位"), inFee.get("档位"));
        String flow = firstNonEmptyText(req.get("国内通用流量"), inFee.get("国内通用流量"));
        String voice = firstNonEmptyText(req.get("国内语音拨打"), req.get("本地语音"), inFee.get("国内语音拨打"));
        String sms = firstNonEmptyText(req.get("短信"), inFee.get("国内语音接听"));
        String outFlow = firstNonEmptyText(req.get("套外流量-计费标准"), outFee.get("套外流量"));
        String outVoice = firstNonEmptyText(req.get("套外语音-国内通话"), outFee.get("套外语音"));
        String outSms = firstNonEmptyText(req.get("套外短彩信-短/彩信"), outFee.get("套外短彩信"));
        String valid = firstNonEmptyText(req.get("套餐有效期"), validity);

        List<Map<String, Object>> list = new ArrayList<>();
        list.add(row("套餐月租", monthFee, monthFee + (prorated ? "（首月按天折算）" : "")));
        list.add(row("流量赠送量", flow, flow + (prorated ? "（国内，按天折算）" : "（国内）")));
        list.add(row("语音赠送量", voice, voice + (prorated ? "（按天折算）" : "")));
        list.add(row("短信赠送量", sms, sms + (prorated ? "（按天折算）" : "")));
        list.add(row("流量超出资费", outFlow, outFlow));
        list.add(row("语音超出资费", outVoice, outVoice));
        list.add(row("短信超出资费", outSms, outSms));
        list.add(row("商品有效期", valid, autoRenew(valid)));
        return list;
    }

    /** 商品有效期系统侧括注：需求侧含"续展/续订"时原样，否则追加（自动续展） */
    private String autoRenew(String validity) {
        if (MapOps.empty(validity)) {
            return validity;
        }
        return validity.contains("续展") || validity.contains("续订") || validity.contains("（") ? validity : validity + "（自动续展）";
    }

    private Map<String, Object> row(String project, String requirementDesc, String billingDesc) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("project_name", project);
        item.put("requirement_desc", requirementDesc);
        item.put("billing_desc", billingDesc);
        item.put("result", "一致");
        return item;
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
        // LLM智能调度硬门禁②：approve_confirmed=true 还须存储中存在该 req_id 的
        // 四环节结果（config/spec/fee/test）且全部 status=ok，防止未走完执行主干直接发起审批
        // （V1.7 统一键：原 execution_id 参数合并为 req_id 单键）
        String reqId = MapOps.str(req.get("req_id")).trim();
        if (reqId.isEmpty()) {
            return camelFail("PARAM_MISSING", "req_id 必填（四环节结果门禁校验依据）");
        }
        for (String stage : List.of("config", "spec", "fee", "test")) {
            Map<String, Object> rec = nodeResult.latestRecord(reqId, stage);
            if (rec == null) {
                Map<String, Object> body = camelOk();
                body.put("status", "NOT_CONFIRMED");
                body.put("reason", "执行主干未全部完成：缺少 " + stage + " 环节结果（req_id=" + reqId + "）");
                return body;
            }
        }
        String productId = MapOps.str(req.get("product_id")).trim();
        String existed = approvalByProduct.get(productId);
        if (existed != null) {
            Map<String, Object> existedApproval = approvals.get(existed);
            advanceApproval(existedApproval);
            Map<String, Object> body = camelOk();
            body.put("approval_id", existed);
            body.put("status", MapOps.str(existedApproval.get("status")));
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
        approval.put("created_at", System.currentTimeMillis());
        approvals.put(approvalId, approval);
        approvalByProduct.put(productId, approvalId);
        log.info("[OfferSimV16] 审批提交 approval_id={} product_id={}", approvalId, productId);

        Map<String, Object> body = camelOk();
        body.put("approval_id", approvalId);
        body.put("status", "审批中");
        return body;
    }

    /* ================= 接口10：审批进度查询 query_approval_status ================= */

    /**
     * 查询前先推进模拟审批状态：提交超过 10s 的"审批中"审批单自动流转为"通过（上架完成）"。
     * 惰性推进（查询/幂等读取时触发），无需后台定时器；演示中最多查询 2 次即可看到终态。
     */
    private void advanceApproval(Map<String, Object> approval) {
        if (!"审批中".equals(MapOps.str(approval.get("status")))) {
            return;
        }
        long elapsed = System.currentTimeMillis() - MapOps.toLong(approval.get("created_at"));
        if (elapsed >= APPROVAL_AUTO_PASS_MS) {
            approval.put("status", "通过");
            approval.put("current_node", "流程结束（上架完成）");
            approval.put("approver", "产品经理");
            approval.put("opinion", "审核通过，同意上架");
            approval.put("update_time", LocalDateTime.now().format(TS));
            log.info("[OfferSimV16] 审批自动流转为通过 approval_id={}", approval.get("approval_id"));
        }
    }

    public Map<String, Object> approvalStatus(Map<String, Object> params) {
        String productId = MapOps.str(params.get("product_id")).trim();
        String approvalIdParam = MapOps.str(params.get("approval_id")).trim();
        if (productId.isEmpty() && approvalIdParam.isEmpty()) {
            return camelFail("PARAM_MISSING", "approval_id 与 product_id 至少一个必填");
        }
        Map<String, Object> approval;
        if (!approvalIdParam.isEmpty()) {
            approval = approvals.get(approvalIdParam);
        } else {
            String aid = approvalByProduct.get(productId);
            approval = aid == null ? null : approvals.get(aid);
        }
        if (approval == null) {
            return camelFail("40404", "审批单不存在");
        }
        advanceApproval(approval);
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
        // V2.4：补充产品名称与趋势字段，供 wf_sub_07 运营报告 LLM 节点按固定模板输出
        // V2.8：product_id（P+req_id 形态）优先按配置落地档案反查销售品（监控新上线产品时
        // product_id 不是 9 位存量销售品 ID，直接 findOffer 查无 → offer_name 为空）
        Map<String, Object> offer = resolveOfferByProduct(productId);
        String offerName = offer == null ? "" : MapOps.str(offer.get("offer_name"));
        body.put("offer_name", offerName);
        body.put("order_count", String.valueOf(orderCount));
        body.put("order_trend", errorCount > 0 ? "下降" : ((seedNum % 3 == 0) ? "持平" : "上升"));
        body.put("error_count", String.valueOf(errorCount));
        body.put("error_trend", errorCount > 0 ? "上升" : "持平");
        body.put("fee_error_rate", String.format(java.util.Locale.ROOT, "%.4f", feeErrorRate));
        body.put("fee_trend", feeErrorRate > 0.01 ? "上升" : "持平");
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
            return legacyFail(5002, "invalid key format: " + key + "（须为 req_id（PLAN…）或 EXEC{...}_STAGE{n}）");
        }
        return nodeResultDelegate().saveV16(key, resultJson, status);
    }

    /* ================= 接口14：节点结果查询 query_node_result（V1.6 key 规范） ================= */

    public Map<String, Object> queryNodeResult(Map<String, Object> params) {
        String key = MapOps.str(params.get("key")).trim();
        if (key.isEmpty() || !validKey(key)) {
            return legacyFail(5002, "invalid key format: " + key + "（须为 req_id（PLAN…）或 EXEC{...}_STAGE{n}）");
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
        // V1.7 统一键：req_id（PLAN+时间戳+随机数），执行方案与执行主干共用；
        // 兼容历史 EXE 前缀键（EXE{...}_STAGE{n}）
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

    /* ================= V2.5：配置上线脚本（CRM/billing 落库 SQL）生成与下载 ================= */

    /**
     * V2.6 script_url 拼装：externalBaseUrl 由控制器按请求头解析（含尾斜杠归一），
     * 空时退化为相对路径（本地直连且未传 Host 头的兜底场景）。
     */
    private String scriptUrlOf(Object productId, String externalBaseUrl) {
        String path = "/api/v1/appstore/product/config/script?product_id=" + MapOps.str(productId);
        if (MapOps.empty(externalBaseUrl)) {
            return path;
        }
        String base = externalBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    /**
     * 下载路由的业务逻辑：按 product_id 回放脚本档案；未落地过配置则返回 null（由控制器转 404 语义）。
     */
    public String launchScriptOf(String productId) {
        if (MapOps.empty(productId)) {
            return null;
        }
        return launchScripts.get(productId.trim());
    }

    /**
     * V2.5 上线脚本生成（V2.6 改模板化）：SQL 骨架抽为 classpath 模板 appstore/launch_script.sql.tpl，
     * 本方法仅负责从落地配置/种子规则提取需求产品信息并替换 ${xxx} 占位符——
     * 脚本结构与表结构维护只改模板，不再动 Java 代码。
     * 仅作演示产物，不真正执行落库。
     */
    private String buildLaunchScript(String productId, String offerId,
                                     Map<String, Object> config, Map<String, Object> seedOffer) {
        Map<String, Object> inFee = castMap(seedOffer == null ? null : seedOffer.get("in_fee"));
        Map<String, Object> outFee = castMap(seedOffer == null ? null : seedOffer.get("out_fee"));
        String offerName = MapOps.str(config.get("offer_name"));
        String template = loadLaunchTemplate();
        String stamp = LocalDateTime.now().format(STAMP);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("product_id", productId);
        vars.put("offer_id", offerId);
        vars.put("offer_name", offerName);
        vars.put("generated_at", LocalDateTime.now().format(TS));
        vars.put("stamp", stamp);
        vars.put("goods_id", "G" + offerId);
        vars.put("prc_id", "M" + offerId.substring(offerId.length() - 3));
        vars.put("class_id", "YnE" + productId.substring(Math.max(0, productId.length() - 3)));
        vars.put("month_fee", MapOps.str(seedOffer == null ? config.get("in_fee") : seedOffer.get("monthly_fee")));
        vars.put("exp_date", "to_date('01-01-2050','dd-mm-yyyy')");
        vars.put("release_ver", "V1.0");
        vars.put("flow", MapOps.str(inFee.get("国内通用流量")));
        vars.put("voice", MapOps.str(inFee.get("国内语音拨打")));
        vars.put("sms", "免费".equalsIgnoreCase(MapOps.str(inFee.get("国内语音接听"))) ? "不限" : MapOps.str(inFee.get("国内语音接听")));
        vars.put("out_flow", MapOps.str(outFee.get("套外流量")));
        vars.put("out_voice", MapOps.str(outFee.get("套外语音")));
        vars.put("out_sms", MapOps.str(outFee.get("套外短彩信")));
        String filled = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            filled = filled.replace("${" + e.getKey() + "}", e.getValue());
        }
        return filled;
    }

    /**
     * V2.6 模板加载：classpath appstore/launch_script.sql.tpl；加载失败返回兜底错误脚本
     * （保证下载路由不 500，且错误信息可直接定位模板问题）。
     */
    private String loadLaunchTemplate() {
        try (java.io.InputStream in = new org.springframework.core.io.ClassPathResource(
                "appstore/launch_script.sql.tpl").getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("[OfferSimV16] 上线脚本模板加载失败 appstore/launch_script.sql.tpl", ex);
            return "-- ERROR: 上线脚本模板 appstore/launch_script.sql.tpl 加载失败，请检查部署包资源完整性\n";
        }
    }

    private String pickSeedOfferId(String planId) {
        List<Map<String, Object>> all = seed.listOffers();
        int idx = Math.abs(planId.hashCode()) % all.size();
        return MapOps.str(all.get(idx).get("offer_id"));
    }

    /** 监控/告警场景销售品解析：product_id 若为落地档案登记的新产品则反查其 offer_id，否则按原 ID 直查存量库 */
    private Map<String, Object> resolveOfferByProduct(String productId) {
        String mappedOfferId = productToOffer.get(productId);
        Map<String, Object> offer = seed.findOffer(mappedOfferId != null ? mappedOfferId : productId);
        if (offer != null) {
            return offer;
        }
        // 兜底：落地档案中直接按 product_id 找配置（含 offer_name）
        return savedConfigs.values().stream()
                .filter(c -> productId.equals(MapOps.str(c.get("product_id"))))
                .findFirst().orElse(null);
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "";
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
