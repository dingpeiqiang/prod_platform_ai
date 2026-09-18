package com.sitech.prodai.controller;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.appstore.FieldOntologyService;
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

import java.util.List;
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
@RequestMapping("/api/v1/appstore")
public class AppStoreV16Controller {

    private final OfferSimV16Service sim;
    private final NodeResultService nodeResultService;
    private final FieldOntologyService fieldOntologyService;
    private final ProductOntologyService productOntologyService;

    public AppStoreV16Controller(OfferSimV16Service sim, NodeResultService nodeResultService,
                                 FieldOntologyService fieldOntologyService,
                                 ProductOntologyService productOntologyService) {
        this.sim = sim;
        this.nodeResultService = nodeResultService;
        this.fieldOntologyService = fieldOntologyService;
        this.productOntologyService = productOntologyService;
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

    @Operation(summary = "配置落地", description = "解析执行方案JSON四类字段写入模拟CRM配置库；内部二次校验 confirmed==true 与存储 CONFIRMED 标记门禁；同 plan_json 幂等")
    @PostMapping("/product/config/save")
    public Map<String, Object> saveProductConfig(@RequestBody Map<String, Object> req,
                                                 jakarta.servlet.http.HttpServletRequest httpRequest) {
        return sim.saveProductConfig(req, externalBaseUrl(httpRequest));
    }

    @Operation(summary = "配置上线脚本下载", description = "V2.5：按 product_id 回放配置落地环节生成的 CRM/billing 落库 SQL 脚本（text/plain 附件下载，附件名 launch_{product_id}.sql）；未落地产品返回 404")
    @GetMapping("/product/config/script")
    public org.springframework.http.ResponseEntity<String> launchScript(@RequestParam("product_id") String product_id) {
        String script = sim.launchScriptOf(product_id);
        if (script == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=launch_" + product_id + ".sql")
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(script);
    }

    /**
     * V2.6 外部可下载基址：优先取反向代理透传头（X-Forwarded-Proto/X-Forwarded-Host），
     * 依次回退 Host 头、请求自身 scheme+host；结果形如 http://10.86.13.201:31281（无尾斜杠）。
     */
    private String externalBaseUrl(jakarta.servlet.http.HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        String host = firstNonBlank(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        if (host != null && !host.isBlank()) {
            return (proto == null || proto.isBlank() ? request.getScheme() : proto.trim()) + "://" + host.trim();
        }
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
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

    @Operation(summary = "查询测试结果", description = "按 globalId 生成逐场景测点比对明细（presetValue 取自《产品信息.txt》）与受理凭证；报告归档后出参回传 report_url 下载链接")
    @PostMapping("/test/offer/result")
    public Map<String, Object> testResult(@RequestBody Map<String, Object> req,
                                          jakarta.servlet.http.HttpServletRequest httpRequest) {
        return sim.testResult(req, externalBaseUrl(httpRequest));
    }

    @Operation(summary = "自动化测试报告下载", description = "按 globalId 回放测试完成时归档的《销售品自动化测试报告》Markdown（text/markdown 附件下载，附件名 test_report_{globalId}.md）；无该测试任务或报告未生成返回 404")
    @GetMapping("/test/offer/report")
    public org.springframework.http.ResponseEntity<String> testReport(@RequestParam("global_id") String global_id) {
        String report = sim.testReportOf(global_id);
        if (report == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=test_report_" + global_id + ".md")
                .header("Content-Type", "text/markdown; charset=utf-8")
                .body(report);
    }

    /* ================= 接口8：计费规则校验 check_billing_rule ================= */

    @Operation(summary = "计费规则校验", description = "内置规则引擎校验配置JSON，输出模拟风险清单（默认通过，支持构造冲突用例）")
    @PostMapping("/billing/rules/verify")
    public Map<String, Object> billingRulesVerify(@RequestBody Map<String, Object> req) {
        return sim.billingRulesVerify(req);
    }

    /* ================= 接口9：上线审批推送 submit_release_approval ================= */

    @Operation(summary = "上线审批推送", description = "生成模拟审批单号并写入审批状态库；校验 approve_confirmed==true 与 execution_id 四环节结果门禁；同 product_id 幂等")
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

    /* ================= 接口14：字段本体推理（field_ontology_reason） ================= */

    @Operation(summary = "字段本体推理", description = "V2.1 本体推理引擎：四类18字段本体注册表（枚举/格式/默认值/兜底口径）——"
            + "action=reason 一体推理（校验+修正回写+默认值补全，返回推理后fields_json供工作流闭环取值）；"
            + "action=validate 逐字段校验LLM补全合法性（非法返回violations供重填）；"
            + "action=complete 缺失字段按本体默认值推理补全（兜底口径字段不补全交上游判待补充）；"
            + "action=ontology 查询字段本体定义；"
            + "V2.0 action=group_check 融合组级校验（互斥/依赖/成员越界，返回group_violations供流程引导）")
    @PostMapping("/ontology/fields")
    public Map<String, Object> ontologyFields(@RequestBody Map<String, Object> req) {
        String action = MapOps.str(req.get("action"));
        String fieldsJson = resolveFieldsJson(req);
        if ("reason".equals(action)) {
            return fieldOntologyService.reason(fieldsJson);
        }
        if ("validate".equals(action)) {
            return fieldOntologyService.validate(fieldsJson);
        }
        if ("complete".equals(action)) {
            return fieldOntologyService.complete(fieldsJson);
        }
        if ("ontology".equals(action)) {
            return fieldOntologyService.ontology();
        }
        if ("group_check".equals(action)) {
            return fieldOntologyService.groupCheck(req);
        }
        Map<String, Object> fail = new java.util.LinkedHashMap<>();
        fail.put("code", 5101);
        fail.put("msg", "invalid action（须为 reason/validate/complete/ontology/group_check）");
        return fail;
    }

    /**
     * 兼容两种入参形态：fields（数组，技能包 cpcp_api.py 发送形态）与 fields_json（字符串，工作流回传形态）。
     * 数组形态统一序列化为 JSON 字符串后交由本体推理引擎处理。
     */
    private String resolveFieldsJson(Map<String, Object> req) {
        Object fields = req.get("fields");
        if (fields instanceof List<?> list && !list.isEmpty()) {
            return toJson(fields);
        }
        String fieldsJson = MapOps.str(req.get("fields_json"));
        return fieldsJson.isBlank() ? "[]" : fieldsJson;
    }

    /* ================= 接口15：异动根因本体推理（ops_root_cause） ================= */

    /**
     * 对接 work-flow 阶段1.1 wf_sub_07 节点706 CODE_OP_ROOT_CAUSE：
     * 由 {product_id} 调 productOntologyService.analyzeRootCause(offeringId)，
     * 并把 camelCase 字段适配为 workflow 契约的 snake_case 出参
     * （reason_engine/evidence_triples/swrl_fired/applied_rules/action_list）。
     */
    @Operation(summary = "异动根因本体推理", description = "由 product_id 对异动一指定位根因并推理处置动作，输出归因路径/证据三元组/命中规则/动作清单（契约出参 snake_case）")
    @PostMapping("/ops/root-cause")
    public Map<String, Object> opsRootCause(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        String productId = MapOps.str(safe.get("product_id"));
        Map<String, Object> r = productOntologyService.analyzeRootCause(productId, null);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        boolean ok = Boolean.TRUE.equals(r.get("success"))
                && (r.get("paths") instanceof List<?> list && !list.isEmpty());
        if (!ok) {
            out.put("backend_pending", "1");
            out.put("note", r.get("message") == null ? "根因本体推理未命中归因规则" : String.valueOf(r.get("message")));
            return out;
        }
        out.put("backend_pending", "0");
        out.put("reason_engine", MapOps.str(r.get("reasonEngine")));
        out.put("anomalies", r.get("anomalies"));
        out.put("paths", r.get("paths"));
        out.put("evidence_triples", r.get("evidenceTriples"));
        out.put("swrl_fired", r.get("swrlFiredRules") == null ? "" : toJson(r.get("swrlFiredRules")));
        out.put("applied_rules", r.get("appliedRules") == null ? "" : toJson(r.get("appliedRules")));
        out.put("action_list", r.get("actionList"));
        out.put("message", r.get("message") == null ? "" : String.valueOf(r.get("message")));
        return out;
    }

    /* ================= 接口16：创建工单闭环（create_work_order） ================= */

    /**
     * 对接 wf_sub_07 节点708 CODE_OP_CREATE_WO：
     * 由 {product_id} 建处置工单，取嵌套 workOrder.workOrderId 显影为 work_order_id 契约出参。
     */
    @Operation(summary = "创建运维工单", description = "由 product_id 建立处置工单并回写本体，返回 work_order_id（契约 snake_case）")
    @PostMapping("/ops/work-orders")
    public Map<String, Object> opsCreateWorkOrder(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        String productId = MapOps.str(safe.get("product_id"));
        Map<String, Object> r = productOntologyService.createWorkOrder(Map.of("offeringId", productId, "source", "workflow"));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Object woRaw = r.get("workOrder");
        String woId = null;
        if (woRaw instanceof Map<?, ?> wo) {
            Object wid = wo.get("workOrderId");
            woId = wid == null ? null : String.valueOf(wid);
        }
        if (Boolean.TRUE.equals(r.get("success")) && woId != null && !woId.isBlank()) {
            out.put("backend_pending", "0");
            out.put("work_order_id", woId);
            out.put("message", r.get("message") == null ? "工单已建立" : String.valueOf(r.get("message")));
        } else {
            out.put("backend_pending", "1");
            out.put("work_order_id", "");
            out.put("note", "建工单服务端点异常，工单未建立");
        }
        return out;
    }

    /* ================= 接口17：存量合规扫描（shelf_compliance） ================= */

    /**
     * 对接 wf_sub_10 节点1002 CODE_OP_SHELF_COMPLIANCE：
     * auditShelfCompliance 返回 items，适配为 workflow 读取的 rows / results，并带 message。
     */
    @Operation(summary = "存量合规扫描", description = "批量扫描在架存量产品执行 R-C* 规则校验，输出违规清单（items 适配为 rows/results）")
    @PostMapping("/shelf-compliance")
    public Map<String, Object> shelfCompliance(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (safe.get("offering_ids") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    ids.add(String.valueOf(o));
                }
            }
        }
        Map<String, Object> r = productOntologyService.auditShelfCompliance(ids);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Object rows = r.get("items");
        if (Boolean.TRUE.equals(r.get("success")) && rows instanceof List<?> l && !l.isEmpty()) {
            out.put("backend_pending", "0");
            out.put("rows", rows);
            out.put("results", rows);
            out.put("total", String.valueOf(r.get("total")));
            out.put("message", (r.get("failedCount") instanceof Number n && n.intValue() > 0)
                    ? "存量合规扫描完成，存在待整改项" : "存量合规扫描完成，全部通过");
        } else {
            out.put("backend_pending", "1");
            out.put("note", "存量合规扫描端点暂无可输出结论");
        }
        return out;
    }

    /* ================= 接口18：嵌套报文本体校验（validate_nested） ================= */

    /**
     * 对接 wf_sub_01 节点109 CODE_OP_VALIDATE_NESTED：
     * 入参 {template_id, payload} → validateNested；契约出参 valid/error_list/explain/message。
     */
    @Operation(summary = "嵌套报文本体校验", description = "模板轨本体校验闸：payload + template_id，出参 valid/error_list/explain/message")
    @PostMapping("/validate-nested")
    public Map<String, Object> validateNested(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        String templateId = MapOps.str(safe.get("template_id"));
        Object payload = safe.get("payload");
        Map<String, Object> call = new java.util.LinkedHashMap<>();
        call.put("template", templateId);
        call.put("payload", payload == null ? Map.of() : payload);
        Map<String, Object> r = productOntologyService.validateNested(call);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        boolean pass = Boolean.TRUE.equals(r.get("pass")) || Boolean.TRUE.equals(r.get("can_submit"));
        out.put("backend_pending", "0");
        out.put("valid", pass ? "1" : "0");
        out.put("error_list", r.get("violations") == null ? "[]" : toJson(r.get("violations")));
        out.put("explain", r.get("explain_hint") == null ? "" : toJson(r.get("explain_hint")));
        out.put("message", r.get("message") == null ? (pass ? "本体校验通过" : "本体校验未通过，见 error_list") : String.valueOf(r.get("message")));
        return out;
    }

    /* ================= 接口19：配置解释（explain） ================= */

    /**
     * 对接本体推理可见性（flow-A explain）：按 trace_id 生成业务视角解释，出参 explain/explanation。
     */
    @Operation(summary = "配置解释", description = "按 trace_id 生成业务视角配置解释，出参 explain")
    @PostMapping("/explain")
    public Map<String, Object> explain(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        String traceId = safe.get("trace_id") != null ? String.valueOf(safe.get("trace_id"))
                : safe.get("traceId") != null ? String.valueOf(safe.get("traceId")) : "";
        Map<String, Object> r = productOntologyService.explainConfig(traceId, "business");
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("backend_pending", "0");
        out.put("trace_id", r.get("trace_id") == null ? traceId : String.valueOf(r.get("trace_id")));
        out.put("explain", r.get("explanation") == null ? "" : String.valueOf(r.get("explanation")));
        out.put("message", r.get("message") == null ? "OK" : String.valueOf(r.get("message")));
        return out;
    }

    /* ================= 接口20：测试报告下载（download_test_report） ================= */

    /**
     * 对接 wf_sub_04 节点316 CODE_DOWNLOAD_TEST_REPORT：
     * 入参 {record_id, kind}；record_id 即测试 global_id，回退到既有 GET /test/offer/report 附件，
     * 但此处返回 download_url（+message）契约出参，与 workflow 读取一致。
     */
    @Operation(summary = "测试报告下载", description = "按 record_id(globalId) 返回正式版测试报告下载链接 download_url + message")
    @PostMapping("/report/download")
    public Map<String, Object> reportDownload(@RequestBody(required = false) Map<String, Object> req,
                                              jakarta.servlet.http.HttpServletRequest httpRequest) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        String recordId = MapOps.str(safe.get("record_id"));
        String report = sim.testReportOf(recordId);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (report == null || recordId.isBlank()) {
            out.put("backend_pending", "1");
            out.put("download_url", "");
            out.put("note", "无该测试任务或报告未生成，暂无下载链接");
            return out;
        }
        out.put("backend_pending", "0");
        out.put("download_url", externalBaseUrl(httpRequest)
                + "/api/v1/appstore/test/offer/report?global_id=" + recordId.trim());
        out.put("message", "正式版测试报告已生成，可点击链接下载");
        return out;
    }

    /* ================= 接口21：配置/上线脚本下载（download_launch_script） ================= */

    /**
     * 对接 wf_sub_06 节点621 CODE_DOWNLOAD_LAUNCH_SCRIPT：
     * 入参 {offer_id, approval_id, kind}；offer_id 即 product_id，回退到既有 GET /product/config/script 附件，
     * 此处返回 download_url（+message）契约出参。
     */
    @Operation(summary = "配置/上线脚本下载", description = "按 offer_id(product_id) 返回上线加载脚本下载链接 download_url + message")
    @PostMapping("/script/download")
    public Map<String, Object> scriptDownload(@RequestBody(required = false) Map<String, Object> req,
                                              jakarta.servlet.http.HttpServletRequest httpRequest) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        String offerId = MapOps.str(safe.get("offer_id"));
        String script = sim.launchScriptOf(offerId);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (script == null || offerId.isBlank()) {
            out.put("backend_pending", "1");
            out.put("download_url", "");
            out.put("note", "无该销售品上线脚本，暂无下载链接");
            return out;
        }
        out.put("backend_pending", "0");
        out.put("download_url", externalBaseUrl(httpRequest)
                + "/api/v1/appstore/product/config/script?product_id=" + offerId.trim());
        out.put("message", "配置/上线脚本已生成，可点击链接下载");
        return out;
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
