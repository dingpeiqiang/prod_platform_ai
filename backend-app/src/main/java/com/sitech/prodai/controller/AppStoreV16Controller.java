package com.sitech.prodai.controller;

import com.sitech.prodai.service.appstore.NodeResultService;
import com.sitech.prodai.service.appstore.OfferSimV16Service;
import com.sitech.prodai.service.common.MapOps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 产销品加载 AI 应用 V1.6 · 自研模拟接口（14 条路由）。
 * <p>
 * 契约以 插件\自研插件集V1.6\工具*.json 为准（路径/入参/出参逐项一致）；
 * 出参 snake_case（工具1/4/6/7/9 部分业务字段为 camelCase，与导出契约保持一致）；
 * 模拟种子数据 = 《产品信息.txt》18 个销售品。
 */
@Tag(name = "产销品加载V1.6", description = "自研模拟插件集：相似度分析、实时稽核、配置落地、测试发起/场景/进度/结果、计费校验、审批推送/查询、监控、告警、节点结果存取")
@RestController
@RequestMapping("/api/v1")
public class AppStoreV16Controller {

    private final OfferSimV16Service sim;
    private final NodeResultService nodeResultService;

    public AppStoreV16Controller(OfferSimV16Service sim, NodeResultService nodeResultService) {
        this.sim = sim;
        this.nodeResultService = nodeResultService;
    }

    /* ================= 接口1：相似度分析 query_similar_offer ================= */

    @Operation(summary = "相似度分析", description = "以 18 销售品为相似产品库，按业务需求描述返回相似销售品列表及相似度评分")
    @PostMapping("/similar/offer/query")
    public Map<String, Object> similarOfferQuery(@RequestBody Map<String, Object> req) {
        return sim.similarOfferQuery(req);
    }

    /* ================= 接口2：实时规格稽核 realtime_spec_audit ================= */

    @Operation(summary = "实时规格稽核", description = "按销售品ID与配置JSON对照规则库实时稽核，同步返回通过/驳回+问题明细+整改建议")
    @PostMapping("/audit/realtime")
    public Map<String, Object> auditRealtime(@RequestBody Map<String, Object> req) {
        return sim.auditRealtime(req, System.currentTimeMillis());
    }

    /* ================= 接口3：配置落地 save_product_config ================= */

    @Operation(summary = "配置落地", description = "解析执行方案JSON四类字段写入模拟CRM配置库；内部二次校验 confirmed==true；同 plan_json 幂等")
    @PostMapping("/product/config/save")
    public Map<String, Object> saveProductConfig(@RequestBody Map<String, Object> req) {
        return sim.saveProductConfig(req);
    }

    /* ================= 接口4：测试发起 offer_test ================= */

    @Operation(summary = "销售品测试发起", description = "按销售品ID异步模拟测试执行（新装/副卡加装/退订），返回 globalId")
    @PostMapping("/test/offer/start")
    public Map<String, Object> offerTest(@RequestBody Map<String, Object> req) {
        return sim.testOfferStart(req);
    }

    /* ================= 接口5：查询测试场景 get_test_scenes ================= */

    @Operation(summary = "查询测试场景", description = "按 globalId 返回受理类场景集合（S_O_TC/S_ADD_CARD/S_U_TC）")
    @PostMapping("/test/offer/scenes")
    public Map<String, Object> testScenes(@RequestBody Map<String, Object> req) {
        return sim.testScenes(req);
    }

    /* ================= 接口6：查询测试进度 get_test_progress ================= */

    @Operation(summary = "查询测试进度", description = "按 globalId 推进模拟测试进度状态机（总步骤=场景数+2），支持轮询")
    @PostMapping("/test/offer/progress")
    public Map<String, Object> testProgress(@RequestBody Map<String, Object> req) {
        return sim.testProgress(req);
    }

    /* ================= 接口7：查询测试结果 get_test_result ================= */

    @Operation(summary = "查询测试结果", description = "按 globalId 生成逐场景测点比对明细（presetValue 取自《产品信息.txt》）与受理凭证")
    @PostMapping("/test/offer/result")
    public Map<String, Object> testResult(@RequestBody Map<String, Object> req) {
        return sim.testResult(req);
    }

    /* ================= 接口8：计费规则校验 check_billing_rule ================= */

    @Operation(summary = "计费规则校验", description = "内置规则引擎校验配置JSON，输出模拟风险清单（默认通过，支持构造冲突用例）")
    @PostMapping("/billing/rules/verify")
    public Map<String, Object> billingRulesVerify(@RequestBody Map<String, Object> req) {
        return sim.billingRulesVerify(req);
    }

    /* ================= 接口9：上线审批推送 submit_release_approval ================= */

    @Operation(summary = "上线审批推送", description = "生成模拟审批单号并写入审批状态库；校验 approve_confirmed==true；同 product_id 幂等")
    @PostMapping("/approval/submit")
    public Map<String, Object> approvalSubmit(@RequestBody Map<String, Object> req) {
        return sim.approvalSubmit(req);
    }

    /* ================= 接口10：审批进度查询 query_approval_status ================= */

    @Operation(summary = "审批进度查询", description = "按 approval_id（优先）/product_id 查询审批单当前状态、环节与意见")
    @GetMapping("/approval/status")
    public Map<String, Object> approvalStatus(@RequestParam(required = false) String approval_id,
                                              @RequestParam(required = false) String product_id) {
        return sim.approvalStatus(Map.of("approval_id", MapOps.str(approval_id),
                "product_id", MapOps.str(product_id)));
    }

    /* ================= 接口11：监控查询 query_product_monitor ================= */

    @Operation(summary = "监控查询", description = "按销售品返回模拟运行指标（订单量/异常量/计费差错率/告警列表）")
    @GetMapping("/product/monitor")
    public Map<String, Object> productMonitor(@RequestParam("product_id") String product_id,
                                              @RequestParam(required = false) String date_range,
                                              @RequestParam(required = false, defaultValue = "all") String metric) {
        return sim.productMonitor(Map.of("product_id", product_id,
                "date_range", MapOps.str(date_range), "metric", metric));
    }

    /* ================= 接口12：异常告警 send_alert ================= */

    @Operation(summary = "异常告警", description = "生成模拟告警单号并写入告警库（供监控查询回显闭环）")
    @PostMapping("/alert/send")
    public Map<String, Object> sendAlert(@RequestBody Map<String, Object> req) {
        return sim.sendAlert(req);
    }

    /* ================= 接口13：节点结果存储 save_node_result（V1.6 key 规范） ================= */

    @Operation(summary = "节点结果存储V1.6", description = "按 key（plan_id 或 EXEC{execution_id}_STAGE{n}）存储环节结果 JSON；同键覆盖；非法 key 返回 5002")
    @PostMapping("/node/result/save")
    public Map<String, Object> saveNodeResultV16(@RequestBody Map<String, Object> req) {
        return sim.saveNodeResult(req);
    }

    /* ================= 接口14：节点结果查询 query_node_result（V1.6 key 规范） ================= */

    @Operation(summary = "节点结果查询V1.6", description = "按 key 查询环节结果 JSON；查无返回 5005")
    @GetMapping("/node/result/query")
    public Map<String, Object> queryNodeResultV16(@RequestParam("key") String key) {
        return sim.queryNodeResult(Map.of("key", MapOps.str(key)));
    }
}
