package com.sitech.prodai.controller;

import com.sitech.prodai.service.appstore.AppMockStore;
import com.sitech.prodai.service.appstore.BillingRuleCheckService;
import com.sitech.prodai.service.appstore.NodeResultService;
import com.sitech.prodai.service.appstore.SpecAuditService;
import com.sitech.prodai.service.appstore.TestCaseService;
import com.sitech.prodai.service.common.MapOps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产销品加载 AI 应用 · 插件对接接口（11 个，契约见《产销品加载AI应用-开发工作清单.md》）。
 * <p>
 * 统一约定：HTTP/HTTPS + JSON（snake_case 出参）；出参含统一状态字段 {@code code}/{@code msg}；
 * 写入类接口支持 {@code idempotency_key} 幂等头/字段；长任务（用例执行）走 {@code task_id} 异步模式。
 */
@Tag(name = "应用商店", description = "应用商店智能配置：商品配置查询、CRM/计费配置生成、规则校验、测试用例、验收与告警")
@RestController
@RequestMapping("/api/v1/appstore")
public class AppStoreController {

    private final AppMockStore store;
    private final BillingRuleCheckService billingRuleCheck;
    private final SpecAuditService specAudit;
    private final TestCaseService testCaseService;
    private final NodeResultService nodeResultService;

    public AppStoreController(AppMockStore store,
                              BillingRuleCheckService billingRuleCheck,
                              SpecAuditService specAudit,
                              TestCaseService testCaseService,
                              NodeResultService nodeResultService) {
        this.store = store;
        this.billingRuleCheck = billingRuleCheck;
        this.specAudit = specAudit;
        this.testCaseService = testCaseService;
        this.nodeResultService = nodeResultService;
    }

    /* ================= 接口1：产销品配置查询 ================= */

    @Operation(summary = "商品配置查询", description = "按关键字查询商品配置")
    @GetMapping("/products/config/query")
    public Map<String, Object> queryProductConfig(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String product_id,
                                                  @RequestParam(required = false, defaultValue = "all") String status) {
        List<Map<String, Object>> list = store.queryProducts(keyword, product_id, status);
        Map<String, Object> body = store.ok("total", list.size());
        body.put("list", list);
        return body;
    }

    /* ================= 接口2：CRM 配置数据生成 ================= */

    @Operation(summary = "CRM 配置生成", description = "AI 生成 CRM 配置")
    @PostMapping("/crm/config/generate")
    public Map<String, Object> genCrmConfig(@RequestBody Map<String, Object> req) {
        Map<String, Object> replay = store.idempotentReplay(MapOps.str(req.get("idempotency_key")));
        if (replay != null) {
            return replay;
        }
        String productName = MapOps.str(req.get("product_name"));
        if (MapOps.empty(req.get("product_name")) || MapOps.empty(req.get("fee_json"))
                || MapOps.empty(req.get("sale_scope"))) {
            return store.fail(1001, "product_name/fee_json/sale_scope 必填");
        }
        // 重复性校验：同名产品已生成过 CRM 配置则拒绝
        Map<String, Object> existed = store.findCrmConfigByProduct(productName);
        if (existed != null) {
            return store.fail(1002, "产品 [" + productName + "] 已存在 CRM 配置: "
                    + MapOps.str(existed.get("crm_config_id")));
        }

        String crmConfigId = store.nextCrmConfigId();
        Map<String, Object> crmConfig = new LinkedHashMap<>();
        crmConfig.put("crm_config_id", crmConfigId);
        crmConfig.put("product_name", productName);
        crmConfig.put("product_id", MapOps.str(req.get("product_id")));
        crmConfig.put("product_desc", MapOps.firstNonEmpty(req.get("product_desc"), productName));
        crmConfig.put("fee_json", req.get("fee_json"));
        crmConfig.put("sale_scope", req.get("sale_scope"));
        crmConfig.put("effect_date", MapOps.str(req.get("effect_date")));
        crmConfig.put("expire_date", MapOps.str(req.get("expire_date")));
        crmConfig.put("catalog", "产品目录/增值业务/" + productName);
        crmConfig.put("attrs", MapOps.castListOfMaps(req.get("attrs")));
        crmConfig.put("status", "generated");
        crmConfig.put("created_at", store.now());
        store.putCrmConfig(crmConfigId, crmConfig);

        // 同步登记产销品档案（接口1 可查询）
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("product_id", MapOps.firstNonEmpty(req.get("product_id"), store.nextProductId()));
        product.put("product_name", productName);
        product.put("spec_json", MapOps.str(req.get("spec_json")));
        product.put("fee_json", req.get("fee_json"));
        product.put("sale_scope", req.get("sale_scope"));
        product.put("status", "draft");
        product.put("created_at", store.now());
        store.putProduct(product);

        Map<String, Object> body = store.ok("crm_config_id", crmConfigId);
        body.put("crm_config_json", toJson(crmConfig));
        body.put("status", "generated");
        store.idempotentSave(MapOps.str(req.get("idempotency_key")), body);
        return body;
    }

    /* ================= 接口3：计费配置数据生成 ================= */

    @Operation(summary = "计费配置生成", description = "AI 生成计费配置")
    @PostMapping("/billing/config/generate")
    public Map<String, Object> genBillingConfig(@RequestBody Map<String, Object> req) {
        Map<String, Object> replay = store.idempotentReplay(MapOps.str(req.get("idempotency_key")));
        if (replay != null) {
            return replay;
        }
        if (MapOps.empty(req.get("product_id")) || MapOps.empty(req.get("fee_json"))) {
            return store.fail(2001, "product_id/fee_json 必填");
        }
        String billingConfigId = store.nextBillingConfigId();
        Map<String, Object> billingConfig = new LinkedHashMap<>();
        billingConfig.put("billing_config_id", billingConfigId);
        billingConfig.put("product_id", req.get("product_id"));
        billingConfig.put("fee_json", req.get("fee_json"));
        billingConfig.put("discount_rules", MapOps.castListOfMaps(req.get("discount_rules")));
        billingConfig.put("billing_events", List.of(
                Map.of("event", "order", "trigger", "订购成功"),
                Map.of("event", "monthly_bill", "trigger", "每月1日出账"),
                Map.of("event", "cancel", "trigger", "退订生效")));
        billingConfig.put("account_period", "自然月");
        billingConfig.put("effect_date", MapOps.str(req.get("effect_date")));
        billingConfig.put("status", "generated");
        store.putBillingConfig(billingConfigId, billingConfig);

        Map<String, Object> body = store.ok("billing_config_id", billingConfigId);
        body.put("billing_config_json", toJson(billingConfig));
        body.put("status", "generated");
        store.idempotentSave(MapOps.str(req.get("idempotency_key")), body);
        return body;
    }

    /* ================= 接口4：计费规则校验 ================= */

    @Operation(summary = "计费规则校验", description = "校验计费规则合法性")
    @PostMapping("/rules/verify")
    public Map<String, Object> checkBillingRule(@RequestBody Map<String, Object> req) {
        Map<String, Object> billingConfig = store.parseJson(MapOps.str(req.get("billing_config_json")));
        if (billingConfig == null) {
            billingConfig = MapOps.castMap(req.get("billing_config"));
        }
        if (billingConfig == null || billingConfig.isEmpty()) {
            return store.fail(3001, "billing_config_json 必填且须为合法 JSON");
        }
        return billingRuleCheck.verify(billingConfig, MapOps.str(req.get("check_scene")));
    }

    /** 规则配置（与知识库规则同步；可配置化支撑） */
    @Operation(summary = "计费规则配置", description = "配置计费规则")
    @PostMapping("/rules/config")
    public Map<String, Object> configBillingRules(@RequestBody Map<String, Object> req) {
        return billingRuleCheck.updateRules(
                req.get("overlay_limit") == null ? null : (int) MapOps.num(req.get("overlay_limit"), 3),
                req.get("boundary_price_ratio") == null ? null : MapOps.num(req.get("boundary_price_ratio"), 0.5),
                MapOps.castListOfMaps(req.get("mutex_pairs")));
    }

    /* ================= 接口5：配置规格稽核 ================= */

    @Operation(summary = "商品规格审计", description = "审计商品规格")
    @PostMapping("/spec/audit")
    public Map<String, Object> checkProductSpec(@RequestBody Map<String, Object> req) {
        Map<String, Object> crmConfig = store.parseJson(MapOps.str(req.get("crm_config_json")));
        if (crmConfig == null) {
            crmConfig = MapOps.castMap(req.get("crm_config"));
        }
        Map<String, Object> billingConfig = store.parseJson(MapOps.str(req.get("billing_config_json")));
        if (billingConfig == null) {
            billingConfig = MapOps.castMap(req.get("billing_config"));
        }
        if (crmConfig == null || crmConfig.isEmpty() || billingConfig == null || billingConfig.isEmpty()) {
            return store.fail(5001, "crm_config_json/billing_config_json 必填且须为合法 JSON");
        }
        Map<String, Object> template = store.parseJson(MapOps.str(req.get("audit_template")));
        if (template == null) {
            template = MapOps.castMap(req.get("audit_template"));
        }
        return specAudit.audit(crmConfig, billingConfig, template);
    }

    /* ================= 接口6：测试用例生成 ================= */

    @Operation(summary = "测试用例生成", description = "AI 生成测试用例")
    @PostMapping("/cases/generate")
    public Map<String, Object> genTestCases(@RequestBody Map<String, Object> req) {
        Map<String, Object> crmConfig = store.parseJson(MapOps.str(req.get("crm_config_json")));
        if (crmConfig == null) {
            crmConfig = MapOps.castMap(req.get("crm_config"));
        }
        Map<String, Object> billingConfig = store.parseJson(MapOps.str(req.get("billing_config_json")));
        if (billingConfig == null) {
            billingConfig = MapOps.castMap(req.get("billing_config"));
        }
        if (crmConfig == null || crmConfig.isEmpty() || billingConfig == null || billingConfig.isEmpty()) {
            return store.fail(6001, "crm_config_json/billing_config_json 必填且须为合法 JSON");
        }
        return testCaseService.generate(crmConfig, billingConfig, MapOps.str(req.get("case_type")));
    }

    /* ================= 接口7：测试用例执行 ================= */

    @Operation(summary = "测试用例执行", description = "执行已生成的测试用例")
    @PostMapping("/cases/execute")
    public Map<String, Object> runTestCases(@RequestBody Map<String, Object> req) {
        List<String> caseIds = new ArrayList<>();
        for (Object o : MapOps.castList(req.get("case_ids"))) {
            caseIds.add(MapOps.str(o));
        }
        if (caseIds.isEmpty()) {
            return store.fail(7001, "case_ids 必填");
        }
        return testCaseService.execute(caseIds, MapOps.str(req.get("env")),
                MapOps.str(req.get("execute_mode")));
    }

    /** 执行结果回查（可选开发项：异步任务状态查询） */
    @Operation(summary = "任务查询", description = "按任务 ID 查询异步任务结果")
    @GetMapping("/tasks/{task_id}")
    public Map<String, Object> queryTask(@PathVariable("task_id") String taskId) {
        return testCaseService.queryTask(taskId);
    }

    /* ================= 接口8：受理验证 ================= */

    @Operation(summary = "订单验收核验", description = "订单验收核验")
    @PostMapping("/order/verify")
    public Map<String, Object> verifyAcceptance(@RequestBody Map<String, Object> req) {
        String productId = MapOps.str(req.get("product_id"));
        if (productId.isBlank()) {
            return store.fail(8001, "product_id 必填");
        }
        String verifyType = MapOps.str(req.get("verify_type"));
        String type = verifyType.isBlank() ? "new" : verifyType.toLowerCase();
        if (!List.of("new", "change", "cancel").contains(type)) {
            return store.fail(8002, "verify_type 非法: " + verifyType);
        }
        if (store.findProduct(productId) == null) {
            return store.fail(8003, "产销品不存在: " + productId);
        }

        String orderId = store.nextOrderId();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("order_id", orderId);
        order.put("product_id", productId);
        order.put("verify_type", type);
        order.put("status", "success");
        order.put("created_at", store.now());
        store.putVerifyOrder(order);

        Map<String, Object> body = store.ok("pass", 0);
        body.put("order_id", orderId);
        return body;
    }

    /* ================= 接口9：上线审批推送 ================= */

    @Operation(summary = "发布审批提交", description = "提交发布审批")
    @PostMapping("/approval/submit")
    public Map<String, Object> submitReleaseApproval(@RequestBody Map<String, Object> req) {
        Map<String, Object> replay = store.idempotentReplay(MapOps.str(req.get("idempotency_key")));
        if (replay != null) {
            return replay;
        }
        String productId = MapOps.str(req.get("product_id"));
        String report = MapOps.str(req.get("report"));
        String reportUrl = MapOps.str(req.get("report_url"));
        if (productId.isBlank() || (report.isBlank() && reportUrl.isBlank())) {
            return store.fail(9001, "product_id 与 report/report_url 必填");
        }
        if (store.findProduct(productId) == null) {
            return store.fail(9002, "产销品不存在: " + productId);
        }
        String flow = MapOps.str(req.get("approval_flow"));
        String flowType = flow.isBlank() ? "standard" : flow.toLowerCase();
        if (!List.of("standard", "urgent").contains(flowType)) {
            return store.fail(9003, "approval_flow 非法: " + flow);
        }

        String approvalId = store.nextApprovalId();
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("approval_id", approvalId);
        approval.put("product_id", productId);
        approval.put("approval_flow", flowType);
        approval.put("report", report.isBlank() ? reportUrl : report);
        approval.put("status", "submitted");
        approval.put("created_at", store.now());
        store.putApproval(approval);

        Map<String, Object> body = store.ok("approval_id", approvalId);
        body.put("status", "submitted");
        store.idempotentSave(MapOps.str(req.get("idempotency_key")), body);
        return body;
    }

    /* ================= 接口10：产销品监控查询 ================= */

    @Operation(summary = "商品监控", description = "按商品 ID 查询监控数据")
    @GetMapping("/product/monitor")
    public Map<String, Object> queryProductMonitor(@RequestParam("product_id") String productId,
                                                   @RequestParam(required = false) String date_range,
                                                   @RequestParam(required = false, defaultValue = "all") String metric) {
        if (productId == null || productId.isBlank()) {
            return store.fail(10001, "product_id 必填");
        }
        Map<String, Object> product = store.findProduct(productId);
        if (product == null) {
            return store.fail(10002, "产销品不存在: " + productId);
        }
        // Mock 指标：订单量/差错率确定性生成（按 productId 稳定）
        int seed = Math.abs(productId.hashCode());
        int orderCount = 100 + seed % 900;
        int errorCount = seed % 8;
        double feeErrorRate = (seed % 5) / 1000.0;

        List<Map<String, Object>> alarmList = new ArrayList<>();
        for (Map<String, Object> alert : store.findAlerts(productId, null)) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("alarm_id", alert.get("alert_id"));
            a.put("alarm_level", alert.get("alarm_level"));
            a.put("content", alert.get("content"));
            a.put("created_at", alert.get("created_at"));
            alarmList.add(a);
        }

        Map<String, Object> body = store.ok("order_count", orderCount);
        body.put("error_count", errorCount);
        body.put("fee_error_rate", feeErrorRate);
        if (!"all".equalsIgnoreCase(metric)) {
            // 指定单一指标时仍全量返回，便于插件归纳；metric 仅作为过滤提示
            body.put("metric", metric);
        }
        body.put("date_range", date_range == null ? "" : date_range);
        body.put("alarm_list", alarmList);
        return body;
    }

    /* ================= 接口11：异常告警推送 ================= */

    @Operation(summary = "告警发送", description = "发送告警通知")
    @PostMapping("/alert/send")
    public Map<String, Object> sendAlert(@RequestBody Map<String, Object> req) {
        String productId = MapOps.str(req.get("product_id"));
        String content = MapOps.str(req.get("content"));
        if (productId.isBlank() || content.isBlank()) {
            return store.fail(11001, "product_id/content 必填");
        }
        String level = MapOps.str(req.get("alarm_level"));
        String alarmLevel = level.isBlank() ? "low" : level.toLowerCase();
        if (!List.of("high", "middle", "low").contains(alarmLevel)) {
            return store.fail(11002, "alarm_level 非法: " + level);
        }

        String alertId = store.nextAlertId();
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("alert_id", alertId);
        alert.put("product_id", productId);
        alert.put("alarm_level", alarmLevel);
        alert.put("content", content);
        alert.put("status", "sent");
        alert.put("created_at", store.now());
        store.addAlert(alert);

        Map<String, Object> body = store.ok("alert_id", alertId);
        body.put("status", "sent");
        return body;
    }

    /* ================= 接口12：节点结果存储（save_node_result） ================= */

    @Operation(summary = "节点结果存储", description = "各子工作流把环节结果 JSON 按需求单号存入（同键覆盖，支持重跑）")
    @PostMapping("/result/save")
    public Map<String, Object> saveNodeResult(@RequestBody Map<String, Object> req) {
        return nodeResultService.save(
                MapOps.str(req.get("req_id")),
                MapOps.str(req.get("node_name")),
                MapOps.str(req.get("result_json")),
                MapOps.str(req.get("status")));
    }

    /* ================= 接口13：节点结果查询（query_node_result） ================= */

    @Operation(summary = "节点结果查询", description = "后续环节按需求单号查询上游环节结果 JSON")
    @GetMapping("/result/query")
    public Map<String, Object> queryNodeResult(@RequestParam("req_id") String reqId,
                                               @RequestParam(required = false) String node_name,
                                               @RequestParam(required = false, defaultValue = "1") String latest_only) {
        return nodeResultService.query(reqId, node_name, latest_only);
    }

    /* ---------------- 工具 ---------------- */

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
