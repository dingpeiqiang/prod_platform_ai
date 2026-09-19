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
    private final OfferGroupSeedService groupSeed;
    private final NodeResultService nodeResult;

    /** 配置落地档案：offer_id -> 落地配置（工具7 写入，工具2/8 读取） */
    private final Map<String, Map<String, Object>> savedConfigs = new ConcurrentHashMap<>();
    /** 新增销售品独立档案：offer_id -> 从 plan_json 构造的产品档案（与种子同构；新增链路不复用存量编码） */
    private final Map<String, Map<String, Object>> newProductArchive = new ConcurrentHashMap<>();
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
    /** 审批矩阵节点定义（节点名 -> 审批角色），四节点矩阵：产品经理→资费主管→运营审核→IT支撑（上线审批） */
    private static final String[][] APPROVAL_MATRIX_NODES = {
        {"产品经理审核", "产品经理"},
        {"资费主管审核", "资费主管"},
        {"运营审核", "运营专员"},
        {"上线审批", "IT支撑"}
    };
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

    public OfferSimV16Service(ObjectMapper objectMapper, OfferSeedService seed,
                              OfferGroupSeedService groupSeed, NodeResultService nodeResult) {
        this.objectMapper = objectMapper;
        this.seed = seed;
        this.groupSeed = groupSeed;
        this.nodeResult = nodeResult;
    }

    /* ================= 接口1：相似度分析 query_similar_offer ================= */

    public Map<String, Object> similarOfferQuery(Map<String, Object> req) {
        if (MapOps.empty(req.get("businessDesc"))) {
            return paramMissing("businessDesc 必填");
        }
        // 最多返回 3 个相似销售品（按相似度降序），第 1 个作为主推荐高匹配产品。
        // templateId（可选）：需求分析模板轨传入，用于为相似品附上对应模板的存量逻辑模型报文 offerModel。
        String templateId = MapOps.str(req.get("templateId"));
        List<Map<String, Object>> similarList = seed.matchSimilar(MapOps.str(req.get("businessDesc")), 3, templateId);
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
        // V2.0 融合商品扩展：命中融合品（组定义内 offer_id）时出参内嵌 offer_group
        // （成员构成/角色/required/group_rules 逐字引用 seed_offer_groups.json，模型禁止自行推理成员关系）；
        // 单品命中时无 offer_group 键（单商品链路行为零变化）。
        Map<String, Object> offerGroup = groupSeed.groupOfSimilar(similarList);
        if (offerGroup != null) {
            body.put("offer_group", offerGroup);
            body.put("resultMsg", MapOps.str(body.get("resultMsg"))
                    + "；命中融合商品组 " + MapOps.str(offerGroup.get("group_id"))
                    + "（主商品 " + MapOps.str(offerGroup.get("main_offer_name"))
                    + "，成员 " + groupSeed.memberRoles(offerGroup).size() + " 个）");
        }
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
        Map<String, Object> offer = resolveOffer(offerId);
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

        // V2.0 融合商品扩展：config_json 为组结构（含 main_offer/member_offers）→ 组级检查项
        // （互斥/依赖/共享/退订联动，对照 seed_offer_groups.json group_rules 逐字核对），
        // error_list item=group:<role>；单品无组检查（行为零变化）。
        Map<String, Object> groupConfig = parseConfig(MapOps.str(req.get("config_json")));
        errorList.addAll(groupAuditChecks(groupConfig));

        // warning 级（如 OPTIONAL_DEPEND 依赖提示）不阻断：pass 与驳回话术仅按 error 级判定
        boolean hasErrorLevel = errorList.stream().anyMatch(e -> !"warning".equals(e.get("level")));
        Map<String, Object> body = ok();
        body.put("pass", hasErrorLevel ? "0" : "1");
        body.put("error_list", errorList);
        body.put("audit_summary", !hasErrorLevel
                ? "稽核通过：配置符合本次落地销售品 " + MapOps.str(offer.get("offer_name")) + " 规则"
                : "稽核驳回：存在 " + errorList.size() + " 项阻断问题，请整改后重试");
        body.put("resultCode", "0");
        return body;
    }

    /**
     * V2.0 融合组级稽核项：组结构 config_json 时对照组定义 group_rules 检查——
     * ① 成员越界：config member_offers 角色 ∉ 组定义成员角色集合 → error（对应验收 F3）；
     * ② 组级互斥：config member_offers 同角色重复（如两个权益包）→ error（对应验收 F6）；
     * ③ 必选成员缺失：required 成员未在 config 中 → error；
     * ④ 依赖缺失提示：OPTIONAL_DEPEND 成员单加且无主商品 → warning（可选依赖不阻断）。
     * 单品/解析失败返回空列表（不产出组类目，兼容单品链路）。
     */
    private List<Map<String, Object>> groupAuditChecks(Map<String, Object> config) {
        List<Map<String, Object>> errs = new ArrayList<>();
        if (config == null || config.get("member_offers") == null) {
            return errs;
        }
        Object mainObj = config.get("main_offer");
        String mainOfferId = "";
        if (mainObj instanceof Map<?, ?> mainMap) {
            mainOfferId = MapOps.str(castMap(mainMap).get("offer_id"));
        }
        Map<String, Object> group = groupSeed.findGroup(mainOfferId);
        if (group == null) {
            // 主商品未命中组定义：组维度无法核对，透传不产出组检查（交由上游 offer_group 数据源约束）
            return errs;
        }
        List<String> definedRoles = new ArrayList<>();
        List<String> definedOptional = new ArrayList<>();
        for (Map<String, Object> m : castMapList(group.get("members"))) {
            String role = MapOps.str(m.get("role"));
            definedRoles.add(role);
            if (!Boolean.TRUE.equals(m.get("required"))) {
                definedOptional.add(role);
            }
        }
        List<String> configRoles = new ArrayList<>();
        for (Map<String, Object> m : castMapList(config.get("member_offers"))) {
            configRoles.add(MapOps.str(m.get("role")));
        }
        // ① 成员越界
        for (String role : configRoles) {
            if (!definedRoles.contains(role)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("item", "group:" + role);
                err.put("level", "error");
                err.put("desc", "成员\"" + role + "\"不在融合组 " + MapOps.str(group.get("group_id"))
                        + " 定义内（组定义成员：" + String.join("/", definedRoles) + "）");
                err.put("suggest", "移除越界成员或修改需求成员构成（成员构成以 offer_group 下发为准）");
                errs.add(err);
            }
        }
        // ② 组级互斥：同角色重复成员（模拟两个权益包场景）
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String role : configRoles) {
            if (!seen.add(role)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("item", "group:" + role);
                err.put("level", "error");
                err.put("desc", "融合组内成员\"" + role + "\"重复配置，违反组级互斥约束");
                err.put("suggest", "移除重复成员，同角色成员仅保留一个");
                errs.add(err);
            }
        }
        // ③ 必选成员缺失（required=true 且未在主商品/成员中出现的角色）
        for (Map<String, Object> m : castMapList(group.get("members"))) {
            String role = MapOps.str(m.get("role"));
            if (Boolean.TRUE.equals(m.get("required")) && !"主卡套餐".equals(role)
                    && !configRoles.contains(role)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("item", "group:" + role);
                err.put("level", "error");
                err.put("desc", "融合组必选成员\"" + role + "\"缺失（组定义要求必选）");
                err.put("suggest", "补充成员\"" + role + "\"或修改需求成员构成");
                errs.add(err);
            }
        }
        // ④ OPTIONAL_DEPEND 依赖提示（warning 级，不阻断）
        for (Map<String, Object> m : castMapList(group.get("members"))) {
            if ("OPTIONAL_DEPEND".equals(MapOps.str(m.get("dependency")))
                    && configRoles.contains(MapOps.str(m.get("role")))) {
                Map<String, Object> warn = new LinkedHashMap<>();
                warn.put("item", "group:" + MapOps.str(m.get("role")));
                warn.put("level", "warning");
                warn.put("desc", "成员\"" + MapOps.str(m.get("role")) + "\"与主卡套餐为可选依赖关系（"
                        + MapOps.str(m.get("offer_id")) + "），请确认同步办理口径");
                warn.put("suggest", "按省内配置口径确认是否同步开通");
                errs.add(warn);
            }
        }
        return errs;
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

        // 方案key从 plan_json 的 req_id 键提取（统一键后 plan_id 不再独立传参）。
        // 新增产品链路：plan 无 offer_id/similarOfferId 溯源键时生成全新 offer_id（不复用存量编码）；
        // plan 带存量 offer_id 视为存量品重配，仍走原 ID。
        boolean existingReconfigure = MapOps.str(plan.get("offer_id")).isBlank()
                && MapOps.str(plan.get("similarOfferId")).isBlank();
        String planId = firstNonEmptyText(plan.get("req_id"), reqIdForGate);
        String offerId = firstNonEmptyText(plan.get("offer_id"), plan.get("similarOfferId"),
                generateNewOfferId(planId));
        // 新增产品：从 plan_json 构造独立档案入库存档（与种子同构，业务值全部来自需求，不从种子覆盖）
        if (existingReconfigure) {
            newProductArchive.put(offerId, buildOfferProfile(plan, offerId));
        }
        Map<String, Object> profile = newProductArchive.get(offerId);
        Map<String, Object> seedOffer = profile != null ? profile : seed.findOffer(offerId);
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
        saveResult.add(classifyResult("销售规则", existingReconfigure || seedOffer != null));
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
        // V2.0 融合商品扩展：组结构 plan_json（含 main_offer/member_offers 键）→ 出参内嵌 group
        // （主 offer_id + members[]{role, offer_id, product_id}）；单品入参无 group 键（行为零变化）。
        Map<String, Object> groupOut = buildGroupSaveResult(plan, groupMainOfferId(plan, offerId), productId);
        if (groupOut != null) {
            body.put("group", groupOut);
            log.info("[OfferSimV16] 融合组配置落地 group_id={} members={}", groupOut.get("group_id"),
                    groupOut.get("members"));
        }

        savedConfigs.put(offerId, config);
        productToOffer.put(productId, offerId);
        planIdempotency.put(planJson, body);
        log.info("[OfferSimV16] 配置落地 product_id={} offer_id={} status={}", productId, offerId, body.get("status"));
        return body;
    }

    /**
     * V2.0 融合组落地出参 group：plan_json 为组结构（含 member_offers 键）时生成——
     * {group_id, main_offer_id, members[]{role, offer_id, product_id, required}}，
     * 成员 offer_id 逐字引用组定义（"省内自定"成员按省侧编码规则生成 9 位模拟编码并保持幂等）；
     * 单品 plan 返回 null（出参无 group 键，兼容铁律）。
     */
    private Map<String, Object> buildGroupSaveResult(Map<String, Object> plan, String mainOfferId, String productId) {
        if (plan.get("member_offers") == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> group = groupSeed.findGroup(mainOfferId);
        out.put("group_id", group == null ? "GP" + mainOfferId : MapOps.str(group.get("group_id")));
        out.put("main_offer_id", mainOfferId);
        out.put("main_product_id", productId);
        List<Map<String, Object>> members = new ArrayList<>();
        List<Map<String, Object>> planMembers = castMapList(plan.get("member_offers"));
        List<Map<String, Object>> seedMembers = group == null ? List.of() : castMapList(group.get("members"));
        for (int i = 0; i < planMembers.size(); i++) {
            Map<String, Object> pm = planMembers.get(i);
            String role = MapOps.str(pm.get("role"));
            // 成员 offer_id 取组定义（逐字引用）；组定义缺失时按省侧规则生成模拟编码
            String memberOfferId = "";
            boolean required = true;
            for (Map<String, Object> sm : seedMembers) {
                if (role.equals(MapOps.str(sm.get("role")))) {
                    memberOfferId = MapOps.str(sm.get("offer_id"));
                    required = Boolean.TRUE.equals(sm.get("required"));
                    break;
                }
            }
            if (memberOfferId.isBlank() || "省内自定".equals(memberOfferId)) {
                memberOfferId = simulateMemberOfferId(mainOfferId, role);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", role);
            item.put("offer_id", memberOfferId);
            item.put("product_id", productId + "_M" + (i + 1));
            item.put("required", String.valueOf(required));
            item.put("status", "success");
            members.add(item);
        }
        out.put("members", members);
        return out;
    }

    /**
     * 省内自定成员 offer_id 模拟编码（幂等）：8 + 主 offer_id 末 5 位 + 角色序号（一位）。
     * 仅 POC 模拟；生产侧成员编码以产品域商品目录为准。
     */
    private String simulateMemberOfferId(String mainOfferId, String role) {
        String tail = mainOfferId.length() >= 5 ? mainOfferId.substring(mainOfferId.length() - 5) : mainOfferId;
        int seq = Math.abs((mainOfferId + role).hashCode()) % 9 + 1;
        return "8" + tail + seq;
    }

    /**
     * 组落地主商品 offer_id 归一：plan 顶层无 offer_id/similarOfferId 溯源键时，
     * 回退取 main_offer.offer_id（build_plan 组结构出参仅嵌套携带主商品编码）；
     * 均缺席时回退传入的 fallback（新增产品链路生成编码，组定义不命中 → 成员模拟编码）。
     */
    private String groupMainOfferId(Map<String, Object> plan, String fallback) {
        String top = firstNonEmptyText(plan.get("offer_id"), plan.get("similarOfferId"));
        if (!top.isBlank()) {
            return top;
        }
        if (plan.get("main_offer") instanceof Map<?, ?> mainMap) {
            String nested = MapOps.str(castMap(mainMap).get("offer_id"));
            if (!nested.isBlank()) {
                return nested;
            }
        }
        return fallback;
    }

    /* ================= 接口4：测试发起 offer_test ================= */

    public synchronized Map<String, Object> testOfferStart(Map<String, Object> req) {
        if (MapOps.empty(req.get("offerId"))) {
            return camelFail("4002", "offerId 必填");
        }
        String offerId = MapOps.str(req.get("offerId")).trim();
        Map<String, Object> offer = resolveOffer(offerId);
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
        Map<String, Object> offer = resolveOffer(MapOps.str(task.get("offer_id")));
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
        Map<String, Object> offer = resolveOffer(MapOps.str(task.get("offer_id")));
        String offerId = MapOps.str(task.get("offer_id"));
        // 预期值取 preset_map；新增销售品（preset_map 未收录）从落地档案按 10 测点自动生成
        Map<String, Object> presets = seed.presetsOf(offerId);
        if (presets == null || presets.isEmpty()) {
            presets = offer == null ? presets : buildPresetsFromPlan(offer);
        }
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
        body.put("testCases", buildFixedCases(orderId, offerInstId, sceneResults));
        body.put("report_url", testReportUrlOf(globalId, externalBaseUrl));
        // V2.0 融合商品扩展：融合品测试 → 出参内嵌 offer_group_check（组一致性核对结果，
        // E26 组核对数据源——模型仅逐字引用，禁止自行聚合改判）；单品无该键（行为零变化）。
        Map<String, Object> groupCheck = buildOfferGroupCheck(task, offer, sceneResults, orderId, offerInstId);
        if (groupCheck != null) {
            body.put("offer_group_check", groupCheck);
        }
        return body;
    }

    /**
     * V2.0 组一致性核对出参 offer_group_check：
     * {group_id, main_offer_id, main_offer_name, overallConclusion, members[]{role, offer_id,
     *  required, inst_id, scene, status}}——成员 inst_id=受理凭证派生（模拟），status 逐成员给出；
     * overallConclusion 由后端按组场景结果确定性生成（"全部成员验证通过"/"成员 X 验证未通过"），
     * 调用方禁止聚合改判。
     */
    private Map<String, Object> buildOfferGroupCheck(Map<String, Object> task, Map<String, Object> offer,
                                                     List<Map<String, Object>> sceneResults,
                                                     String orderId, String offerInstId) {
        Map<String, Object> group = groupSeed.findGroup(MapOps.str(task.get("offer_id")));
        if (group == null) {
            return null;
        }
        // 场景通过性：组场景（S_GROUP_BIND/S_ADDON_SUB）failTestCaseCount=0 即通过
        boolean groupScenesPass = true;
        for (Map<String, Object> scene : sceneResults) {
            String nbr = MapOps.str(scene.get("testSceneNbr"));
            if ("S_GROUP_BIND".equals(nbr) || "S_ADDON_SUB".equals(nbr)) {
                if (Integer.parseInt(MapOps.str(scene.get("failTestCaseCount"))) > 0) {
                    groupScenesPass = false;
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("group_id", MapOps.str(group.get("group_id")));
        out.put("main_offer_id", MapOps.str(group.get("main_offer_id")));
        out.put("main_offer_name", MapOps.str(group.get("main_offer_name")));
        out.put("order_id", orderId);
        out.put("offer_inst_id", offerInstId);
        List<Map<String, Object>> members = new ArrayList<>();
        int idx = 1;
        for (Map<String, Object> m : castMapList(group.get("members"))) {
            Map<String, Object> item = new LinkedHashMap<>();
            String role = MapOps.str(m.get("role"));
            item.put("role", role);
            item.put("offer_id", MapOps.str(m.get("offer_id")));
            item.put("required", String.valueOf(Boolean.TRUE.equals(m.get("required"))));
            item.put("inst_id", "主卡套餐".equals(role) ? offerInstId : offerInstId + "_M" + idx);
            item.put("scene", "主卡套餐".equals(role) ? "S_O_TC"
                    : (Boolean.TRUE.equals(m.get("required")) ? "S_GROUP_BIND" : "S_ADDON_SUB"));
            item.put("status", groupScenesPass ? "success" : "fail");
            members.add(item);
            idx++;
        }
        out.put("members", members);
        out.put("overallConclusion", groupScenesPass
                ? "融合组 " + MapOps.str(group.get("group_id")) + " 成员组合验证全部通过（"
                + members.size() + " 个成员，含组场景与组类测点）"
                : "融合组 " + MapOps.str(group.get("group_id")) + " 成员组合验证存在未通过项，请核对组场景测点明细");
        return out;
    }

    /**
     * 31 条固定用例逐条出参（V2.9）：K3 用例设计规范第4章 ACC/BILL/CUST 用例的
     * 判定依据（出参映射）在服务端确定性执行，消除调用方语义推断；
     * result 取值：✅ / ❌ / 本销售品未覆盖；数据源=测点出参与受理凭证，禁止虚构。
     */
    private List<Map<String, Object>> buildFixedCases(String orderId, String offerInstId,
                                                      List<Map<String, Object>> sceneResults) {
        Map<String, Map<String, Object>> pointByCode = new LinkedHashMap<>();
        for (Map<String, Object> scene : sceneResults) {
            for (Map<String, Object> point : MapOps.castListOfMaps(scene.get("testCasePointResults"))) {
                pointByCode.putIfAbsent(MapOps.str(point.get("testPointNbr")), point);
            }
        }
        java.util.function.BiFunction<String, String, String> pr = (code, covered) ->
                pointByCode.containsKey(code)
                        ? ("0".equals(MapOps.str(pointByCode.get(code).get("resultCode"))) ? "✅" : "❌")
                        : covered;
        boolean acc011Pass = !MapOps.empty(orderId) && !MapOps.empty(offerInstId);
        boolean scenesCovered = !sceneResults.isEmpty();
        List<Map<String, Object>> rows = new ArrayList<>();
        // (caseId, caseName, level, 判定值)
        List<Object[]> acc = List.of(
                new Object[]{"ACC-001", "销售品基础准入规则校验", "P0", pr.apply("P_EFF_DATE", "本销售品未覆盖")},
                new Object[]{"ACC-002", "产品互斥规则校验", "P0", pr.apply("P_MUTEX_REL", "本销售品未覆盖")},
                new Object[]{"ACC-003", "产品依赖规则校验", "P0", pr.apply("P_RELY_REL", "本销售品未覆盖")},
                new Object[]{"ACC-004", "订购操作能力校验", "P0", pr.apply("P_STATUS", "本销售品未覆盖")},
                new Object[]{"ACC-005", "变更操作能力校验", "P1", "本销售品未覆盖"},
                new Object[]{"ACC-006", "退订操作能力校验", "P0", "✅"},
                new Object[]{"ACC-007", "受理表单必填字段完整性", "P0", andAll(pr, "P_OFFER_NAME", "P_OFFER_TYPE", "P_PAY_MODE")},
                new Object[]{"ACC-008", "限购数量规则校验", "P1", pr.apply("P_ORD_CNT", "本销售品未覆盖")},
                new Object[]{"ACC-009", "地域受理范围校验", "P1", "本销售品未覆盖"},
                new Object[]{"ACC-010", "受理时段生效校验", "P1", pr.apply("P_EFF_DATE", "本销售品未覆盖")},
                new Object[]{"ACC-011", "模拟订购接口预测试", "P0", acc011Pass ? "✅" : (scenesEmpty(sceneResults) ? "本销售品未覆盖" : "❌")},
                new Object[]{"ACC-012", "模拟退订接口预测试", "P0", pr.apply("P_STATUS", "本销售品未覆盖")});
        List<Object[]> bill = List.of(
                new Object[]{"BILL-001", "基础资费金额合法性校验", "P0", "✅"},
                new Object[]{"BILL-002", "计费周期类型校验", "P0", "✅"},
                new Object[]{"BILL-003", "计费起算时间规则校验", "P0", "✅"},
                new Object[]{"BILL-004", "资源扣减规则校验", "P0", "✅"},
                new Object[]{"BILL-005", "阶梯/按量批价规则校验", "P1", "✅"},
                new Object[]{"BILL-006", "优惠叠加/捆绑减免校验", "P1", "✅"},
                new Object[]{"BILL-007", "账单展示项配置校验", "P1", "本销售品未覆盖"},
                new Object[]{"BILL-008", "模拟订购账单试算", "P0", "本销售品未覆盖"},
                new Object[]{"BILL-009", "退订费用结算试算", "P1", "本销售品未覆盖"},
                new Object[]{"BILL-010", "资费生效失效联动校验", "P0", andAll(pr, "P_EFF_DATE", "P_EXP_DATE")});
        List<Object[]> cust = List.of(
                new Object[]{"CUST-001", "客服产品基础视图完整性", "P0", andAll(pr, "P_OFFER_NAME", "P_OFFER_TYPE")},
                new Object[]{"CUST-002", "客户订单查询能力校验", "P0", acc011Pass ? "✅" : (scenesEmpty(sceneResults) ? "本销售品未覆盖" : "❌")},
                new Object[]{"CUST-003", "客服侧产品操作权限校验", "P1", "本销售品未覆盖"},
                new Object[]{"CUST-004", "产品资费对外说明话术校验", "P0", "✅"},
                new Object[]{"CUST-005", "产品生效失效规则话术校验", "P1", andAll(pr, "P_EFF_DATE", "P_EXP_DATE")},
                new Object[]{"CUST-006", "产品退订规则话术校验", "P1", "✅"},
                new Object[]{"CUST-007", "产品限制规则话术校验", "P1", andAll(pr, "P_MUTEX_REL", "P_RELY_REL", "P_ORD_CNT")},
                new Object[]{"CUST-008", "对外展示信息合规校验", "P0", "✅"},
                new Object[]{"CUST-009", "客服常见问题FAQ完备性", "P1", "本销售品未覆盖"});
        for (Object[] r : acc) {
            rows.add(caseRow(r));
        }
        for (Object[] r : bill) {
            rows.add(caseRow(r));
        }
        for (Object[] r : cust) {
            rows.add(caseRow(r));
        }
        return rows;
    }

    private Map<String, Object> caseRow(Object[] r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caseId", r[0]);
        row.put("caseName", r[1]);
        row.put("level", r[2]);
        row.put("result", r[3]);
        return row;
    }

    private boolean scenesEmpty(List<Map<String, Object>> sceneResults) {
        return sceneResults == null || sceneResults.isEmpty();
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

    /** 产品类型映射：种子/新档案 series/sub_type → 主套餐/流量包/增值业务（口径同模板） */
    private String productTypeOf(Map<String, Object> offer) {
        return switch (MapOps.str(offer.get("series"))) {
            case "rights" -> "增值业务";
            case "5g_a", "new" -> "主套餐";
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
        // V2.0 融合商品扩展：组级叠加校验（成员价格缺失/跨成员照搬检测）入 risk_list；
        // 单品无组级风险项（行为零变化）。
        riskList.addAll(groupFeeRisks(config));
        Map<String, Object> body = ok();
        body.put("pass", riskList.isEmpty() ? "1" : "0");
        body.put("risk_list", riskList);
        // V2.6：8 项资费比对明细（套餐月租/流量/语音/短信赠送量/三项套外资费/商品有效期），
        // 需求侧取落地配置 plan_json 原文值，系统侧取种子销售品计费规则（含折算规则括注），
        // 供环节3 输出模板逐行引用（禁止模板自行拼装）
        // V2.0 融合商品扩展：组结构 plan_json → compare_list 逐成员生成（member_role 键，
        // 行数=Σ各成员有值行；E27 阈值判定按成员内计算由脚本侧完成）；单品行 member_role
        // 键不新增（出参与 V2.7 逐字节一致，向后兼容铁律）。
        List<Map<String, Object>> compareList = buildFeeCompareList(config, offerId);
        if (isGroupPlan(planOrSelf(config))) {
            compareList = groupFeeCompareList(planOrSelf(config), compareList);
        }
        body.put("compare_list", compareList);
        body.put("resultCode", "0");
        return body;
    }

    /** plan_json 取值：顶层含 plan_json 键取之（环节1 出参形态），否则顶层自身（原文直传形态） */
    private Map<String, Object> planOrSelf(Map<String, Object> config) {
        Map<String, Object> plan = castMap(config.get("plan_json"));
        return plan.isEmpty() ? config : plan;
    }

    /** 组结构 plan 判定：含 member_offers 键（与脚本 _is_group_input 同构，脚本侧另含 main_offer 键） */
    private boolean isGroupPlan(Map<String, Object> plan) {
        return plan.get("member_offers") instanceof List<?> list && !list.isEmpty();
    }

    /**
     * V2.0 组级资费风险项：
     * ① 成员月功能费缺失（value 为空/待补充且非可选成员）→ risk（对应验收 F2 出口A 的组维度提示）；
     * ② 跨成员价格照搬检测：两成员同名价格字段值完全一致且非"省内自定" → risk（提示人工核对）。
     * 单品无风险项。
     */
    private List<Map<String, Object>> groupFeeRisks(Map<String, Object> config) {
        List<Map<String, Object>> risks = new ArrayList<>();
        Map<String, Object> plan = planOrSelf(config);
        if (!isGroupPlan(plan)) {
            return risks;
        }
        Map<String, Map<String, String>> valueByRole = new LinkedHashMap<>();
        valueByRole.put("主卡套餐", flatValuesOf(castMap(plan.get("main_offer")).get("fields")));
        for (Map<String, Object> m : castMapList(plan.get("member_offers"))) {
            valueByRole.put(MapOps.str(m.get("role")), flatValuesOf(m.get("fields")));
        }
        for (Map.Entry<String, Map<String, String>> e : valueByRole.entrySet()) {
            for (String feeField : List.of("月功能费", "套餐档位", "宽带月功能费")) {
                String v = e.getValue().get(feeField);
                if (v == null || v.isBlank() || "待补充".equals(v) || "省内自定".equals(v)) {
                    Map<String, Object> risk = new LinkedHashMap<>();
                    risk.put("risk_type", "member_fee_pending");
                    risk.put("risk_desc", "成员[" + e.getKey() + "]价格类字段\"" + feeField + "\"待补充，整体按出口A 处置");
                    risk.put("suggest", "补充成员[" + e.getKey() + "]的" + feeField + "（价格按成员独立，禁止跨成员照搬）");
                    risks.add(risk);
                }
            }
        }
        return risks;
    }

    /** fields 数组 → field->value 平面映射（占位值归一为空） */
    private Map<String, String> flatValuesOf(Object fieldsObj) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> f : MapOps.castListOfMaps(fieldsObj)) {
            String field = MapOps.str(f.get("field"));
            if (!field.isBlank()) {
                values.putIfAbsent(field, blankIfPlaceholder(f.get("value")));
            }
        }
        return values;
    }

    /**
     * V2.0 融合组 compare_list 逐成员生成：主商品沿用单品 8 项（member_role=主卡套餐），
     * 各成员按其 fields 有值行生成（member_role=成员角色），行数=Σ各成员有值行；
     * 主商品行不含 member_role 键以外的结构变化——member_role 键统一追加（组结构入参时）。
     */
    private List<Map<String, Object>> groupFeeCompareList(Map<String, Object> plan,
                                                          List<Map<String, Object>> mainCompareList) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> item : mainCompareList) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("member_role", "主卡套餐");
            row.putAll(item);
            list.add(row);
        }
        for (Map<String, Object> m : castMapList(plan.get("member_offers"))) {
            String role = MapOps.str(m.get("role"));
            Map<String, String> values = flatValuesOf(m.get("fields"));
            // 成员有值行逐项生成（空值行省略，E27 阈值按成员内判定由脚本侧完成）
            List<String[]> memberRows = List.of(
                    new String[]{"产品名称", "产品名称"},
                    new String[]{"月功能费", "月功能费"},
                    new String[]{"宽带速率", "宽带速率"},
                    new String[]{"路数", "路数"},
                    new String[]{"计费周期", "计费周期"});
            for (String[] pair : memberRows) {
                String v = values.get(pair[1]);
                if (v != null && !v.isBlank() && !"省内自定".equals(v)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("member_role", role);
                    row.put("project_name", role + "·" + pair[0]);
                    row.put("requirement_desc", v);
                    row.put("billing_desc", v);
                    row.put("result", "一致");
                    list.add(row);
                }
            }
        }
        return list;
    }

    /**
     * V2.6 资费校准 8 项比对明细：item/project_name、requirement_desc（需求侧）、
     * billing_desc（系统侧，含折算括注）、result（一致/不一致）。
     * 系统侧取值：命中种子销售品按其资费规则生成；未命中按落地配置自身值回显。
     * V2.7 兼容两种 config_json 形态：环节1 出参（含 plan_json/offer_id 键）与
     * plan_json 原文直传（{req_id, fields, pending_fields}，flow-B 环节3 约定形态）——
     * 顶层无 plan_json 键时按 plan_json 原文自身解析 fields，并从 fields 内提取
     * offer_id/similarOfferId 溯源键回查档案，避免两侧全空退化（E27 假阳性根因）。
     */
    private List<Map<String, Object>> buildFeeCompareList(Map<String, Object> config, String offerId) {
        Map<String, Object> plan = castMap(config.get("plan_json"));
        String resolvedOfferId = offerId;
        if (plan.isEmpty()) {
            // config_json=plan_json 原文直传：顶层即 plan 本体
            plan = config;
            resolvedOfferId = firstNonEmptyText(offerId,
                    config.get("offer_id"), config.get("similarOfferId"),
                    config.get("相似产品ID"), plan.get("offer_id"), plan.get("similarOfferId"));
        }
        Map<String, String> req = planFieldValues(plan);
        if (resolvedOfferId != null && !resolvedOfferId.isBlank() && !resolvedOfferId.equals(offerId)) {
            offerId = resolvedOfferId;
        }
        Map<String, Object> offer = resolveOffer(offerId);
        Map<String, Object> inFee = castMap(offer == null ? null : offer.get("in_fee"));
        Map<String, Object> outFee = castMap(offer == null ? null : offer.get("out_fee"));
        String transition = offer == null ? "" : MapOps.str(offer.get("transition_fee"));
        String validity = offer == null ? "" : MapOps.str(offer.get("validity"));
        boolean prorated = transition.contains("按天") || transition.contains("按日");

        // 需求侧兜底：执行方案未提取到的字段回退种子销售品描述（比对对象仍是需求语义）；
        // "无"为本体默认占位值，非真实资费，视同空值处理（防占位值污染比对结果）
        String monthFee = firstNonEmptyText(req.get("套餐档位"), blankIfPlaceholder(inFee.get("档位")));
        String flow = firstNonEmptyText(req.get("国内通用流量"), blankIfPlaceholder(inFee.get("国内通用流量")));
        String voice = firstNonEmptyText(req.get("国内语音拨打"), req.get("本地语音"),
                req.get("语音赠送量"), blankIfPlaceholder(inFee.get("国内语音拨打")));
        String sms = firstNonEmptyText(req.get("短信"), req.get("短信赠送量"),
                smsQuotaFromText(req.get("国内通用流量")), blankIfPlaceholder(inFee.get("国内语音接听")));
        String outFlow = firstNonEmptyText(req.get("套外流量-计费标准"), blankIfPlaceholder(outFee.get("套外流量")));
        String outVoice = firstNonEmptyText(req.get("套外语音-国内通话"), blankIfPlaceholder(outFee.get("套外语音")));
        String outSms = firstNonEmptyText(req.get("套外短彩信-短/彩信"), blankIfPlaceholder(outFee.get("套外短彩信")));
        String valid = firstNonEmptyText(req.get("套餐有效期"), blankIfPlaceholder(validity));

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

    /** 本体占位值归一："无/待补充/系统待生成"视同空值，其余原样返回（plan.fields 与档案侧共用） */
    private String blankIfPlaceholder(Object value) {
        String v = MapOps.str(value).trim();
        return (v.isEmpty() || "无".equals(v) || "待补充".equals(v) || "系统待生成".equals(v)) ? "" : v;
    }

    /**
     * plan 字段值平面映射（V2.8 兼容真实 plan_json 工件形态）：
     * 依次合并 ①顶层 fields[]（旧形态）②顶层 flat_fields[]（run_requirement 落盘形态）
     * ③嵌套 payload.optionalInfo 计费叶子（需求分析模板轨权威值）。同名键以先出现者为准。
     * 目的：消除"plan 无 fields 键 → 比对两侧全空 → E27 假阳性"根因。
     */
    private Map<String, String> planFieldValues(Map<String, Object> plan) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> f : MapOps.castListOfMaps(plan.get("fields"))) {
            String field = MapOps.str(f.get("field"));
            if (!field.isBlank()) {
                values.putIfAbsent(field, blankIfPlaceholder(f.get("value")));
            }
        }
        for (Map<String, Object> f : MapOps.castListOfMaps(plan.get("flat_fields"))) {
            String field = MapOps.str(f.get("field"));
            if (!field.isBlank()) {
                values.putIfAbsent(field, blankIfPlaceholder(f.get("value")));
            }
        }
        collectPayloadFeeValues(castMap(castMap(plan.get("payload")).get("optionalInfo")), values);
        return values;
    }

    /** 嵌套 payload.optionalInfo 计费叶子 → 平面字段（仅补齐未出现键，键名对齐本体注册表口径） */
    private void collectPayloadFeeValues(Map<String, Object> optionalInfo, Map<String, String> values) {
        if (optionalInfo.isEmpty()) {
            return;
        }
        Map<String, Object> print = castMap(optionalInfo.get("printContent"));
        Map<String, Object> acctMonth = castMap(optionalInfo.get("acctMonth"));
        Map<String, Object> gprs = castMap(optionalInfo.get("billGprsCfg"));
        Map<String, Object> voice = castMap(optionalInfo.get("billVoiceCfg"));
        Map<String, Object> sms = castMap(optionalInfo.get("billSmsCfg"));
        putIfAbsentVal(values, "套餐档位", blankIfPlaceholder(acctMonth.get("fixFee")));
        putIfAbsentVal(values, "套餐档位", blankIfPlaceholder(print.get("prcMonthFee")));
        putIfAbsentVal(values, "套餐有效期", blankIfPlaceholder(acctMonth.get("fixValidity")));
        putIfAbsentVal(values, "国内通用流量", blankIfPlaceholder(print.get("containResource")));
        putIfAbsentVal(values, "套外流量-计费标准", blankIfPlaceholder(gprs.get("outChargeMode")));
        putIfAbsentVal(values, "套外流量-计费标准", blankIfPlaceholder(print.get("chargeStandard")));
        putIfAbsentVal(values, "套外语音-国内通话", outChargeText(voice.get("outCharge")));
        putIfAbsentVal(values, "套外短彩信-短/彩信", outChargeText(sms.get("outCharge")));
        putIfAbsentVal(values, "语音赠送量", thresholdText(voice.get("theshold"), voice.get("resourceType")));
        putIfAbsentVal(values, "短信赠送量", thresholdText(sms.get("theshold"), sms.get("resourceType")));
    }

    /** 占位辅助：仅在值为非空且键未出现时写入 */
    private void putIfAbsentVal(Map<String, String> values, String key, String val) {
        if (val != null && !val.isBlank()) {
            values.putIfAbsent(key, val);
        }
    }

    /** 套外资费文本：数字→"X元"；已含"元/计费/阶梯"等描述则原样 */
    private String outChargeText(Object outCharge) {
        String v = blankIfPlaceholder(outCharge);
        if (v.isEmpty()) {
            return "";
        }
        return v.matches("\\d+(\\.\\d+)?") ? v + "元" : v;
    }

    /** 从国内通用流量描述中提取短信赠送额度（如"10分钟、10条短信"→"10条"）；无则空串 */
    private String smsQuotaFromText(Object flowText) {
        String t = blankIfPlaceholder(flowText);
        if (t.isEmpty()) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\s*条\\s*短信").matcher(t);
        return m.find() ? m.group(1) + "条短信" : "";
    }

    /** 赠送量文本：阈值+单位（如 1000 分钟 / 120GB）；resourceType 作语义补充 */
    private String thresholdText(Object threshold, Object resourceType) {
        String t = blankIfPlaceholder(threshold);
        if (t.isEmpty()) {
            return "";
        }
        String unit = MapOps.str(resourceType).contains("流量") ? "GB"
                : MapOps.str(resourceType).contains("语音") || MapOps.str(resourceType).contains("主叫") ? "分钟" : "";
        return unit.isEmpty() ? t : t + unit;
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

    /** 构建四节点审批矩阵初始态：全部"待审核"，首个节点"进行中" */
    private List<Map<String, Object>> buildApprovalMatrix() {
        List<Map<String, Object>> matrix = new ArrayList<>();
        for (int i = 0; i < APPROVAL_MATRIX_NODES.length; i++) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("node_seq", i + 1);
            node.put("node_name", APPROVAL_MATRIX_NODES[i][0]);
            node.put("approver", APPROVAL_MATRIX_NODES[i][1]);
            node.put("status", i == 0 ? "进行中" : "待审核");
            node.put("opinion", "");
            node.put("update_time", "");
            matrix.add(node);
        }
        return matrix;
    }

    /** 按审批单当前状态刷新审批矩阵（终态"通过"时全部节点置"已通过"并填意见） */
    private void refreshApprovalMatrix(Map<String, Object> approval) {
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) approval.get("approval_matrix");
        if (matrix == null || matrix.isEmpty()) {
            return;
        }
        boolean passed = "通过".equals(MapOps.str(approval.get("status")));
        for (Map<String, Object> node : matrix) {
            if (passed) {
                node.put("status", "已通过");
                node.put("opinion", "审核通过，同意上架");
                node.put("update_time", MapOps.str(approval.get("update_time")));
            }
        }
    }

    private List<Map<String, Object>> approvalMatrixOf(Map<String, Object> approval) {
        Object matrix = approval.get("approval_matrix");
        return matrix instanceof List ? (List<Map<String, Object>>) matrix : List.of();
    }

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
        // V9.1 审批双轨：approval_type=requirement（需求工单审批，flow-A0 需求提报后/需求分析前）
        // 此时执行主干四环节尚未执行，跳过四环节门禁；approval_type=launch（上线审批，flow-C）
        // 仍须四环节结果齐全才可推送。
        String approvalType = MapOps.str(req.get("approval_type")).trim();
        String reqId = MapOps.str(req.get("req_id")).trim();
        if (!"requirement".equals(approvalType)) {
            // LLM智能调度硬门禁②：approve_confirmed=true 还须存储中存在该 req_id 的
            // 四环节结果（config/spec/fee/test）且全部 status=ok，防止未走完执行主干直接发起审批
            // （V1.7 统一键：原 execution_id 参数合并为 req_id 单键）
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
        }
        String productId = MapOps.str(req.get("product_id")).trim();
        String existed = approvalByProduct.get(productId);
        if (existed != null) {
            Map<String, Object> existedApproval = approvals.get(existed);
            advanceApproval(existedApproval);
            Map<String, Object> body = camelOk();
            body.put("approval_id", existed);
            body.put("approval_type", MapOps.str(existedApproval.get("approval_type")));
            body.put("status", MapOps.str(existedApproval.get("status")));
            body.put("idempotent", "true");
            return body;
        }
        String approvalId = "AP" + LocalDateTime.now().format(STAMP)
                + String.format("%04d", approvals.size() + 1);
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("approval_id", approvalId);
        approval.put("product_id", productId);
        approval.put("approval_type", approvalType.isBlank() ? "launch" : approvalType);
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
        approval.put("approval_matrix", buildApprovalMatrix());
        approvals.put(approvalId, approval);
        approvalByProduct.put(productId, approvalId);
        log.info("[OfferSimV16] 审批提交 approval_id={} product_id={}", approvalId, productId);

        Map<String, Object> body = camelOk();
        body.put("approval_id", approvalId);
        body.put("approval_type", approval.get("approval_type"));
        body.put("status", "审批中");
        body.put("approval_matrix", approvalMatrixOf(approval));
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
            refreshApprovalMatrix(approval);
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
        body.put("approval_type", MapOps.str(approval.get("approval_type")));
        body.put("status", MapOps.str(approval.get("status")));
        body.put("current_node", MapOps.str(approval.get("current_node")));
        body.put("approver", MapOps.str(approval.get("approver")));
        body.put("opinion", MapOps.str(approval.get("opinion")));
        body.put("submit_time", MapOps.str(approval.get("submit_time")));
        body.put("update_time", MapOps.str(approval.get("update_time")));
        body.put("approval_matrix", approvalMatrixOf(approval));
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
        // V2.0 融合商品扩展：融合品追加组场景——S_GROUP_BIND（融合成员绑定，有 required 成员时）、
        // S_ADDON_SUB（可选成员加装/退订，有 required=false 成员时）；单品场景集合不变。
        Map<String, Object> group = groupSeed.findGroup(MapOps.str(offer.get("offer_id")));
        if (group != null) {
            if (!groupSeed.requiredRoles(group).isEmpty()) {
                scenes.add("S_GROUP_BIND");
            }
            boolean hasOptional = false;
            for (Map<String, Object> m : castMapList(group.get("members"))) {
                if (!Boolean.TRUE.equals(m.get("required"))) {
                    hasOptional = true;
                    break;
                }
            }
            if (hasOptional) {
                scenes.add("S_ADDON_SUB");
            }
        }
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
            // V2.0 融合组场景（K3 V2.1 增补：S_GROUP_BIND/S_ADDON_SUB，与现有 3 场景并存）
            case "S_GROUP_BIND" -> {
                scene.put("testSceneName", "融合成员绑定");
                scene.put("testSceneDesc", "销售品 " + MapOps.str(offer.get("offer_name"))
                        + " 融合组必选成员绑定验证（成员：" + groupMemberDesc(offer) + "）");
            }
            case "S_ADDON_SUB" -> {
                scene.put("testSceneName", "可选成员加装/退订");
                scene.put("testSceneDesc", "销售品 " + MapOps.str(offer.get("offer_name"))
                        + " 可选成员（副卡功能费等 required=false 成员）加装与退订联动验证");
            }
            default -> {
                scene.put("testSceneName", "套餐退订");
                scene.put("testSceneDesc", "退订规则：" + MapOps.str(offer.get("cancel_rule")));
            }
        }
        scene.put("sort", String.valueOf(sort));
        return scene;
    }

    /** 融合组成员描述（场景说明用）：主卡套餐 + 成员角色清单（逐字引用组定义） */
    private String groupMemberDesc(Map<String, Object> offer) {
        Map<String, Object> group = groupSeed.findGroup(MapOps.str(offer == null ? null : offer.get("offer_id")));
        if (group == null) {
            return "";
        }
        List<String> roles = new ArrayList<>();
        for (Map<String, Object> m : castMapList(group.get("members"))) {
            roles.add(MapOps.str(m.get("role")));
        }
        return String.join("/", roles);
    }

    /** 组场景附加测点（V2.0 K3 增补）：P_SHARE/P_GROUP_MUTEX/P_MEMBER_STATUS，预期值取组定义 preset */
    private List<String> extraGroupPoints(String nbr) {
        return switch (nbr) {
            case "S_GROUP_BIND" -> List.of("P_SHARE", "P_GROUP_MUTEX", "P_MEMBER_STATUS");
            case "S_ADDON_SUB" -> List.of("P_MEMBER_STATUS");
            default -> List.of();
        };
    }

    private Map<String, Object> sceneResult(String nbr, Map<String, Object> offer,
                                            Map<String, Object> presets, java.util.Set<String> mismatch, int sort) {
        Map<String, Object> scene = sceneItem(nbr, offer, sort);
        List<Map<String, Object>> points = new ArrayList<>();
        int success = 0;
        int fail = 0;
        // 组场景在 10 标准测点基础上追加组类测点（P_SHARE/P_GROUP_MUTEX/P_MEMBER_STATUS）
        List<String> pointCodes = new ArrayList<>(seed.testPoints());
        pointCodes.addAll(extraGroupPoints(nbr));
        for (String code : pointCodes) {
            String preset = presetOf(presets, nbr, code, offer);
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

    /** 测点预期值取值：标准测点走 preset_map；组类测点（P_SHARE 等）按组定义生成（逐字引用 preset/规则原文） */
    private String presetOf(Map<String, Object> presets, String nbr, String code, Map<String, Object> offer) {
        if (presets != null && presets.containsKey(code)) {
            return MapOps.str(presets.get(code));
        }
        Map<String, Object> group = groupSeed.findGroup(MapOps.str(offer == null ? null : offer.get("offer_id")));
        if (group == null) {
            return "";
        }
        return switch (code) {
            case "P_SHARE" -> MapOps.str(castMap(group.get("group_rules")).get("共享规则"));
            case "P_GROUP_MUTEX" -> {
                List<Map<String, Object>> mutex = castMapList(castMap(group.get("group_rules")).get("互斥"));
                yield mutex.isEmpty() ? "无组级互斥限制" : "组级互斥：" + mutex.size() + " 项";
            }
            case "P_MEMBER_STATUS" -> "生效（成员实例状态正常，退订联动："
                    + MapOps.str(castMap(group.get("group_rules")).get("退订联动")) + "）";
            default -> "";
        };
    }

    private boolean negativeFee(Map<String, Object> config) {
        Object fee = config.get("monthly_fee");
        return fee instanceof Number n && n.doubleValue() < 0;
    }

    /**
     * 落地配置组装：offer 传入统一解析后的销售品档案（新增品=plan 构造的独立档案，
     * 存量品=种子记录），in_fee/out_fee/销售规则全部取自该档案，业务值不再被种子覆盖。
     */
    private Map<String, Object> buildSavedConfig(String productId, String offerId,
                                                 Map<String, Object> plan, Map<String, Object> offer) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("product_id", productId);
        config.put("offer_id", offerId);
        config.put("offer_name", firstNonEmptyText(plan.get("offer_name"),
                offer == null ? "" : MapOps.str(offer.get("offer_name"))));
        config.put("in_fee", offer == null ? plan.get("in_fee") : offer.get("in_fee"));
        config.put("out_fee", offer == null ? plan.get("out_fee") : offer.get("out_fee"));
        config.put("order_rule", offer == null ? "" : offer.get("order_rule"));
        config.put("cancel_rule", offer == null ? "" : offer.get("cancel_rule"));
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
                                     Map<String, Object> config, Map<String, Object> offer) {
        // offer 为统一解析后的销售品档案（新增品=plan 构造的独立档案），为空时回退落地配置自身
        Map<String, Object> inFee = castMap(offer == null ? config.get("in_fee") : offer.get("in_fee"));
        Map<String, Object> outFee = castMap(offer == null ? config.get("out_fee") : offer.get("out_fee"));
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
        vars.put("month_fee", MapOps.str(offer == null ? config.get("in_fee") : offer.get("monthly_fee")));
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

    /**
     * 新增销售品 offer_id 生成（不复用存量编码）：9 + req_id 末 8 位数字，
     * 与存量 18 个 9 位 ID 冲突时追加校准位直至不重复；同 req_id 幂等（同 plan_json 幂等已保证）。
     */
    private String generateNewOfferId(String planId) {
        String digits = planId.replaceAll("\\D", "");
        String tail = digits.length() >= 8 ? digits.substring(digits.length() - 8)
                : String.format("%08d", Math.abs(planId.hashCode()) % 100_000_000);
        String candidate = "9" + tail;
        while (seed.exists(candidate)) {
            candidate = "9" + String.format("%08d", (Integer.parseInt(tail) + 1) % 100_000_000);
        }
        return candidate;
    }

    /**
     * 销售品档案统一解析：新增品独立档案优先，未命中再查存量种子库（存量链路行为不变）。
     */
    private Map<String, Object> resolveOffer(String offerId) {
        Map<String, Object> profile = offerId == null ? null : newProductArchive.get(offerId.trim());
        return profile != null ? profile : seed.findOffer(offerId);
    }

    /** plan 字段值提取：按字段名取首个非空 value（键名对齐本体注册表 24 字段；兼容 fields/flat_fields/payload 三形态，V2.8） */
    private String fieldOf(Map<String, Object> plan, String... names) {
        Map<String, String> req = planFieldValues(plan);
        for (String name : names) {
            String v = MapOps.str(req.get(name));
            if (!v.isBlank() && !"待补充".equals(v)) {
                return v;
            }
        }
        return "";
    }

    /**
     * 新增销售品档案构造（与种子记录同构）：业务值全部来自 plan_json（需求分析生成的完整落地报文），
     * 缺省按本体注册表默认口径补全；禁止回退种子数据。
     */
    private Map<String, Object> buildOfferProfile(Map<String, Object> plan, String offerId) {
        String name = firstNonEmptyText(fieldOf(plan, "套餐名称"), "新增销售品" + offerId);
        String tier = fieldOf(plan, "套餐档位");
        double monthlyFee = parseFeeYuanOf(tier);
        Map<String, Object> inFee = new LinkedHashMap<>();
        inFee.put("档位", tier.isBlank() ? "" : tier + "/月");
        inFee.put("计费周期", firstNonEmptyText(fieldOf(plan, "计费周期"), "自然月"));
        inFee.put("国内通用流量", firstNonEmptyText(fieldOf(plan, "国内通用流量"), "无"));
        inFee.put("国内语音拨打", firstNonEmptyText(fieldOf(plan, "本地语音"), "无"));
        inFee.put("短信", firstNonEmptyText(fieldOf(plan, "短信"), "无"));
        Map<String, Object> outFee = new LinkedHashMap<>();
        outFee.put("套外流量", firstNonEmptyText(fieldOf(plan, "套外流量-计费标准"), "无"));
        outFee.put("套外语音", firstNonEmptyText(fieldOf(plan, "套外语音-国内通话"), "无"));
        outFee.put("套外短彩信", firstNonEmptyText(fieldOf(plan, "套外短彩信-短/彩信"), "无"));
        boolean allowSubCard = "允许".equals(fieldOf(plan, "是否允许办理副卡"));
        Map<String, Object> subCard = new LinkedHashMap<>();
        subCard.put("允许办理", allowSubCard);
        subCard.put("共享规则", allowSubCard ? "副卡共享套餐内资源" : "包内资源限订购手机号使用");

        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("offer_id", offerId);
        offer.put("offer_name", name);
        offer.put("series", "new");
        offer.put("sub_type", firstNonEmptyText(fieldOf(plan, "套餐属性"), "主资费"));
        offer.put("monthly_fee", monthlyFee);
        offer.put("in_fee", inFee);
        offer.put("out_fee", outFee);
        String eff = firstNonEmptyText(fieldOf(plan, "新入网生效方式"), "立即生效");
        offer.put("transition_fee", firstNonEmptyText(fieldOf(plan, "过渡期资费规则"), "按日（当月实际天数）计扣"));
        offer.put("order_rule", firstNonEmptyText(fieldOf(plan, "适用用户"), "新老用户均可订购")
                + "；新用户" + eff);
        offer.put("change_rule", firstNonEmptyText(fieldOf(plan, "套餐变更范围"), "可变更至中国电信其他在售套餐"));
        offer.put("cancel_rule", firstNonEmptyText(fieldOf(plan, "退订规则"), "允许退订，次月生效"));
        offer.put("validity", firstNonEmptyText(fieldOf(plan, "套餐有效期"), "长期有效"));
        offer.put("allow_sub_card", allowSubCard);
        offer.put("sub_card", subCard);
        offer.put("flow_carry_over", "结转".equals(fieldOf(plan, "流量结转规则")));
        offer.put("billing_cycle", inFee.get("计费周期"));
        offer.put("pay_mode", firstNonEmptyText(fieldOf(plan, "付费方式"), "后付费"));
        offer.put("pay_channel", firstNonEmptyText(fieldOf(plan, "支付方式"), "账单支付"));
        offer.put("sale_channels", List.of("实体渠道", "电子渠道", "直销渠道"));
        offer.put("net_cutoff_limit", firstNonEmptyText(fieldOf(plan, "断网授权"),
                "套外流量使用至600元时暂停上网"));
        return offer;
    }

    /** 套餐档位金额解析："312元"/"312元/月" → 312.0；解析失败返回 0.0（不虚构价格） */
    private double parseFeeYuanOf(String tier) {
        if (tier == null || tier.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(tier.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    /**
     * 新增销售品测试预期值（presetValue）生成：从 plan_json（经落地档案兜底）按 10 个测点
     * 逐项生成，模拟测试自产自销比对；preset_map 未收录的新 offer_id 走本方法兜底。
     */
    private Map<String, Object> buildPresetsFromPlan(Map<String, Object> offer) {
        Map<String, Object> presets = new LinkedHashMap<>();
        String validity = MapOps.str(offer.get("validity"));
        presets.put("P_EFF_DATE", firstNonEmptyText(MapOps.str(offer.get("order_rule")),
                "新入网立即生效；老用户次月1日生效"));
        presets.put("P_EXP_DATE", validity.contains("长期") ? "长期有效" : "套餐有效期" + validity);
        presets.put("P_STATUS", "生效");
        presets.put("P_MAIN_PROD", MapOps.str(offer.get("offer_name")) + "（主产品，单产品构成）");
        presets.put("P_RELY_REL", "无前项依赖");
        presets.put("P_MUTEX_REL", "无互斥限制");
        presets.put("P_ORD_CNT", "同一用户累计订购1次");
        presets.put("P_OFFER_NAME", MapOps.str(offer.get("offer_name")));
        presets.put("P_OFFER_TYPE", MapOps.str(offer.get("offer_name")) + "（新增销售品类）");
        presets.put("P_PAY_MODE", firstNonEmptyText(MapOps.str(offer.get("pay_mode")), "后付费") + "、"
                + firstNonEmptyText(MapOps.str(offer.get("pay_channel")), "账单支付"));
        return presets;
    }

    /** 监控/告警场景销售品解析：product_id 若为落地档案登记的新产品则反查其 offer_id，否则按原 ID 直查存量库 */
    private Map<String, Object> resolveOfferByProduct(String productId) {
        String mappedOfferId = productToOffer.get(productId);
        Map<String, Object> offer = resolveOffer(mappedOfferId != null ? mappedOfferId : productId);
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

    /** Map 列表转换：List<Map> → List<Map<String,Object>>（融合组成员解析共用，V2.0） */
    private List<Map<String, Object>> castMapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) item).entrySet()) {
                        m.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    result.add(m);
                }
            }
        }
        return result;
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
