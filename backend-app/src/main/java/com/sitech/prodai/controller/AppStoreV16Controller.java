package com.sitech.prodai.controller;

import com.sitech.prodai.config.AppStoreInterfaceProperties;
import com.sitech.prodai.service.OpsRulesService;
import com.sitech.prodai.service.OpsSwrlReasoner;
import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.appstore.AuditExcelGenerator;
import com.sitech.prodai.service.appstore.AuditRequirementSource;
import com.sitech.prodai.service.appstore.FieldOntologyService;
import com.sitech.prodai.service.appstore.InterfaceFacade;
import com.sitech.prodai.service.appstore.NodeResultService;
import com.sitech.prodai.service.appstore.OntologySwrlCheckService;
import com.sitech.prodai.service.appstore.OfferSimV16Service;
import com.sitech.prodai.service.common.MapOps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final OntologySwrlCheckService ontologySwrlCheckService;
    private final ProductOntologyService productOntologyService;
    private final com.sitech.prodai.service.appstore.ConfigTemplateService configTemplateService;
    private final com.sitech.prodai.service.appstore.RequirementDocService requirementDocService;
    private final AuditExcelGenerator auditExcelGenerator;
    private final AuditRequirementSource auditRequirementSource;
    private final InterfaceFacade interfaceFacade;
    private final AppStoreInterfaceProperties interfaceProperties;
    private final OpsRulesService opsRules;
    private final OpsSwrlReasoner opsSwrlReasoner;
    private final com.sitech.prodai.service.appstore.AuditPollService auditPollService;

    public AppStoreV16Controller(OfferSimV16Service sim, NodeResultService nodeResultService,
                                 FieldOntologyService fieldOntologyService,
                                 OntologySwrlCheckService ontologySwrlCheckService,
                                 ProductOntologyService productOntologyService,
                                 com.sitech.prodai.service.appstore.ConfigTemplateService configTemplateService,
                                 com.sitech.prodai.service.appstore.RequirementDocService requirementDocService,
                                 AuditExcelGenerator auditExcelGenerator,
                                 AuditRequirementSource auditRequirementSource,
                                 InterfaceFacade interfaceFacade,
                                 AppStoreInterfaceProperties interfaceProperties,
                                 OpsRulesService opsRules,
                                 OpsSwrlReasoner opsSwrlReasoner,
                                 com.sitech.prodai.service.appstore.AuditPollService auditPollService) {
        this.sim = sim;
        this.nodeResultService = nodeResultService;
        this.fieldOntologyService = fieldOntologyService;
        this.ontologySwrlCheckService = ontologySwrlCheckService;
        this.productOntologyService = productOntologyService;
        this.configTemplateService = configTemplateService;
        this.requirementDocService = requirementDocService;
        this.auditExcelGenerator = auditExcelGenerator;
        this.auditRequirementSource = auditRequirementSource;
        this.interfaceFacade = interfaceFacade;
        this.interfaceProperties = interfaceProperties;
        this.opsRules = opsRules;
        this.opsSwrlReasoner = opsSwrlReasoner;
        this.auditPollService = auditPollService;
    }

    /* ================= 模板 schema 下发 get_template_schema ================= */

    @Operation(summary = "模板schema下发", description = "按模板标识返回逻辑模型模板 schema 原文（x-label/x-required JSON-Schema 形态），供需求分析 get_template 节点 HTTP 获取")
    @PostMapping("/template/schema")
    public Map<String, Object> templateSchema(@RequestBody Map<String, Object> req) {
        return configTemplateService.schemaOf(MapOps.str(req.get("templateId")));
    }

    @Operation(summary = "模板清单", description = "返回 6 套逻辑模型模板清单（template_id/中文名/产品类型）")
    @GetMapping("/template/list")
    public Map<String, Object> templateList() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("resultCode", "0");
        body.put("templates", configTemplateService.list());
        return body;
    }

    @Operation(summary = "适配层模式显影", description = "返回产销品场景接口适配层当前生效模式（mock/real/mixed），供现场联调与健康检查")
    @GetMapping("/interface/mode")
    public Map<String, Object> interfaceMode() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("resultCode", "0");
        body.put("mode", interfaceFacade.mode());
        return body;
    }

    /* ================= 接口1：相似度分析 query_similar_offer ================= */

    @Operation(summary = "相似度分析", description = "以 18 销售品为相似产品库，按业务需求描述返回相似销售品列表及相似度评分（经适配层，mock/real 可切换）")
    @PostMapping("/similar/offer/query")
    public Map<String, Object> similarOfferQuery(@RequestBody Map<String, Object> req) {
        return interfaceFacade.similarOfferQuery(req);
    }

    /* ================= 现场接口 B1~B4（智能稽核，经适配层 mock/real 可切换） ================= */

    @Operation(summary = "智能稽核文件上传", description = "入参 req_id + chat_id（无前端文件）：服务端按 req_id 查需求（requirement_report）与配置（requirement）信息，生成稽核 Excel（可选包+关联关系 sheet）后上传，解析销售品清单返回 file_id 与 sales_id（B1，适配层 mock/real 可切换）；可选 mode=real/mock 覆盖本次模式")
    @PostMapping(value = "/audit/file/upload", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> auditFileUpload(@RequestBody Map<String, Object> req,
                                               jakarta.servlet.http.HttpServletRequest httpRequest) {
        String reqId = MapOps.str(req.get("req_id"));
        Map<String, Object> source = auditRequirementSource.resolve(reqId);
        if (source == null) {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("resultCode", "E35");
            body.put("resultMsg", "稽核文件生成失败：按 req_id=" + reqId + " 查无需求/配置数据（无可稽核销售品）");
            return body;
        }
        List<Map<String, Object>> offers = MapOps.castListOfMaps(source.get("offers"));
        List<Map<String, Object>> relations = MapOps.castListOfMaps(source.get("relations"));
        Map<String, Object> call = new java.util.LinkedHashMap<>();
        // real 模式：EOP 规范按 {req_id, chat_id} 由后端自行生成稽核 Excel（无前端文件上传）；
        // mock 模式：忽略该两字段，仍按内置确定性报文返回（行为零变化）。
        call.put("req_id", reqId);
        call.put("chat_id", MapOps.str(req.get("chat_id")));
        // 真实销售品行（按 req_id 还原）：供 mock/real 适配层构造 sales_id 清单，避免 mock 返回硬编码清单
        call.put("offers", offers);
        try {
            call.put("bytes", auditExcelGenerator.generate(offers, relations));
            call.put("fileName", MapOps.str(req.getOrDefault("fileName", "稽核需求.xlsx")));
        } catch (RuntimeException e) {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("resultCode", "E35");
            body.put("resultMsg", "稽核文件生成失败：" + e.getMessage());
            return body;
        }
        String mode = MapOps.str(req.get("mode"));
        if (!mode.isBlank()) {
            call.put("mode", mode);
        }
        Map<String, Object> out = interfaceFacade.auditFileUpload(call);
        enrichAuditDownload(out, httpRequest, call);
        return out;
    }

    /**
     * 为 B1 上传回执补齐 file_name / download_url：以文件名（含时间戳）作为唯一定位键，
     * 下载链接指向 {@code GET /audit/file/download?file_name=...}，不依赖 EOP 的 file_id。
     * file_name 优先取适配层实际落盘名，回退到本次生成的原始文件名。
     */
    private void enrichAuditDownload(Map<String, Object> out, jakarta.servlet.http.HttpServletRequest httpRequest,
                                     Map<String, Object> call) {
        if (out == null) {
            return;
        }
        String fileName = MapOps.str(out.get("file_name"));
        if (fileName.isBlank()) {
            fileName = MapOps.str(call.get("fileName"));
        }
        if (fileName.isBlank()) {
            fileName = "稽核需求.xlsx";
        }
        out.put("file_name", fileName);
        out.put("download_url", externalBaseUrl(httpRequest)
                + "/api/v1/appstore/audit/file/download?file_name="
                + java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * B1 稽核文件下载：按 file_name（原名_yyyyMMddHHmmss.ext）从 upload-dir 定位唯一文件后附件返回。
     * file_name 缺失时返回 400 说明；文件不存在返回 404。
     */
    @Operation(summary = "智能稽核文件下载", description = "按 file_name（含时间戳，B1 上传回执返回）下载稽核 Excel 附件")
    @GetMapping("/audit/file/download")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> auditFileDownload(
            @RequestParam(value = "file_name", required = false) String fileName) {
        String name = AppStoreInterfaceProperties.sanitizeAuditFileName(fileName);
        if (name.isBlank()) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
        Path path = Paths.get(interfaceProperties.getEop().getUploadDir()).resolve(name).normalize();
        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(path);
        if (!resource.exists()) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''"
                        + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8))
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(resource);
    }

    @Operation(summary = "异步报告生成并通知", description = "按销售品通知 AI 能力中心启动稽核报告生成，返回 request_id 供轮询（B2，GET query）")
    @RequestMapping(value = "/audit/async/notify", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> auditAsyncNotify(@RequestBody(required = false) Map<String, Object> body,
                                                @RequestParam(required = false) String sales_id,
                                                @RequestParam(required = false) String salesId,
                                                @RequestParam(required = false) String location,
                                                @RequestParam(required = false) String sheet_name,
                                                @RequestParam(required = false) String sheetName,
                                                @RequestParam(required = false) String file_id,
                                                @RequestParam(required = false) String fileId,
                                                @RequestParam(required = false) String mode) {
        Map<String, Object> req = new java.util.LinkedHashMap<>();
        if (body != null) {
            req.putAll(body);
        }
        putIfPresent(req, "sales_id", sales_id);
        putIfPresent(req, "sales_id", salesId);
        putIfPresent(req, "location", location);
        putIfPresent(req, "sheet_name", sheet_name);
        putIfPresent(req, "sheet_name", sheetName);
        putIfPresent(req, "file_id", file_id);
        putIfPresent(req, "file_id", fileId);
        putIfPresent(req, "mode", mode);
        return interfaceFacade.auditAsyncNotify(req);
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.putIfAbsent(key, value);
        }
    }

    @Operation(summary = "插入AI稽核结果", description = "按 records[] 插入 AI 稽核结果（状态 1000 处理中），保证回调时 DB 已有记录（B3）")
    @PostMapping("/audit/result/insert")
    public Map<String, Object> auditResultInsert(@RequestBody Map<String, Object> req) {
        return interfaceFacade.auditResultInsert(req);
    }

    @Operation(summary = "通用SQL查询", description = "按 sql_id 轮询稽核任务是否完成（20260626001）/查询最终稽核结果（20260626002），出参 execute_sql_resp[]（B4）")
    @PostMapping("/audit/sql-query")
    public Map<String, Object> auditSqlQuery(@RequestBody Map<String, Object> req) {
        return interfaceFacade.auditSqlQuery(req);
    }

    @Operation(summary = "稽核结果轮询并落地", description = "环节4b 后端化：按 req_id 读 audit_pending 取 requestIds → 单次探测 B4 完成态（sqlId=20260626001）→ 完成后查最终结论（20260626002）→ 整批 pass==1 时写 node_name=spec。供前台面板周期调用，替代对话主动查询")
    @PostMapping("/audit/poll-result")
    public Map<String, Object> auditPollResult(@RequestBody Map<String, Object> req) {
        return auditPollService.pollOnce(MapOps.str(req.get("req_id")));
    }

    /* ================= 接口2：实时规格稽核 realtime_spec_audit ================= */

    @Operation(summary = "实时规格稽核", description = "V3.12 多商品批量：入参 {req_id, offers:[{offer_seq, offer_id, config_json}]}，逐品对照规则库实时稽核，聚合返回 {req_id, status, offers:[{offer_seq, offer_id, pass, error_list, audit_summary}]}")
    @PostMapping("/audit/realtime")
    public Map<String, Object> auditRealtime(@RequestBody Map<String, Object> req) {
        return sim.auditRealtimeBatch(req, System.currentTimeMillis());
    }

    /* ================= 端点 2e：需求文档解析 requirement/parse-xlsx（V3.10，统一方案 6.6） ================= */

    @Operation(summary = "需求文档解析", description = "解析需求文档 .xlsx（可选包宽表 + 关联关系 A/Z 端表），返回结构化需求 offers[]（offer_seq/ofr_id/offer_name/fields）+ relations[]（a_ofr_id/relation/z_ofr_id）+ source_sheets[]；解析失败/无销售品/关系枚举越界返回 resultCode=E41")
    @PostMapping(value = "/requirement/parse-xlsx", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> requirementParseXlsx(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return requirementDocService.parse(null, null);
        }
        try {
            return requirementDocService.parse(file.getBytes(), file.getOriginalFilename());
        } catch (java.io.IOException e) {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("resultCode", "E41");
            body.put("resultMsg", "需求文档读取失败：" + e.getMessage());
            return body;
        }
    }

    /* ================= 接口3：配置落地 save_product_config ================= */

    @Operation(summary = "配置落地", description = "V3.12 多商品批量：入参 {req_id, offers:[{offer_seq, plan_json, offer_id}]}，逐品解析执行方案JSON写入模拟CRM配置库（同 plan_json 幂等），聚合返回 {req_id, status, offers:[{offer_seq, offer_id, save_result, status, saved_at, product_config, script_url, group?}]}")
    @PostMapping("/product/config/save")
    public Map<String, Object> saveProductConfig(@RequestBody Map<String, Object> req,
                                                 jakarta.servlet.http.HttpServletRequest httpRequest) {
        return sim.saveProductConfigBatch(req, externalBaseUrl(httpRequest));
    }

    @Operation(summary = "配置上线脚本下载", description = "V2.5：按 offer_id 回放配置落地环节生成的 CRM/billing 落库 SQL 脚本（text/plain 附件下载，附件名 launch_{offer_id}.sql）；未落地产品返回 404")
    @GetMapping("/product/config/script")
    public org.springframework.http.ResponseEntity<String> launchScript(@RequestParam("offer_id") String offerId) {
        String script = sim.launchScriptOf(offerId);
        if (script == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=launch_" + offerId + ".sql")
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

    /* ---- V3.12 多商品批量聚合辅助（测试类端点经适配层逐品调用后在此聚合） ---- */

    /** offers[] 缺失时的参数级错误信封。 */
    private Map<String, Object> offersMissing() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("resultCode", "PARAM_MISSING");
        body.put("resultMsg", "offers 必填（多商品数组，元素含 offer_seq/offer_id 等）");
        return body;
    }

    /** 组装批量外层信封：{resultCode, resultMsg, req_id, status, offers[]}；整批态按逐品 status 聚合。 */
    private Map<String, Object> batchBody(Map<String, Object> req, List<Map<String, Object>> items) {
        long success = items.stream().filter(i -> "success".equals(MapOps.str(i.get("status")))).count();
        String status = items.isEmpty() ? "FAIL"
                : (success == items.size() ? "SUCCESS" : (success == 0 ? "FAIL" : "PARTIAL"));
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("resultCode", "0");
        body.put("resultMsg", "success");
        body.put("req_id", MapOps.str(req == null ? null : req.get("req_id")));
        body.put("status", status);
        body.put("offers", items);
        return body;
    }

    /* ================= 接口4：测试发起 offer_test ================= */

    @Operation(summary = "销售品测试发起", description = "V3.12 多商品批量：入参 {req_id, offers:[{offer_seq, offer_id}]}，逐品经适配层发起异步测试（内部逐品调冻结的 C1 单体契约），聚合返回 {req_id, status, offers:[{offer_seq, offer_id, globalId}]}")
    @PostMapping("/test/offer/start")
    public Map<String, Object> offerTest(@RequestBody Map<String, Object> req) {
        List<Map<String, Object>> offers = MapOps.castListOfMaps(req == null ? null : req.get("offers"));
        if (offers.isEmpty()) {
            return offersMissing();
        }
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (Map<String, Object> offer : offers) {
            String offerId = MapOps.str(MapOps.firstNonEmpty(offer.get("offer_id"), offer.get("offerId"))).trim();
            Map<String, Object> call = new java.util.LinkedHashMap<>();
            call.put("offer_id", offerId);
            if (req.containsKey("mode")) {
                call.put("mode", req.get("mode"));
            }
            Map<String, Object> r = interfaceFacade.testOfferStart(call);
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("offer_seq", offer.get("offer_seq"));
            item.put("offer_id", offerId);
            String globalId = MapOps.str(MapOps.firstNonEmpty(r.get("globalId"), r.get("global_id")));
            item.put("globalId", globalId);
            item.put("status", globalId.isBlank() ? "fail" : "success");
            if (r.containsKey("resultMsg")) {
                item.put("resultMsg", r.get("resultMsg"));
            }
            items.add(item);
        }
        return batchBody(req, items);
    }

    /* ================= 接口5：查询测试场景 get_test_scenes ================= */

    @Operation(summary = "查询测试场景", description = "V3.12 多商品批量：入参 {req_id, offers:[{offer_seq, globalId}]}，逐品返回受理类场景集合（S_O_TC/S_ADD_CARD/S_U_TC）")
    @PostMapping("/test/offer/scenes")
    public Map<String, Object> testScenes(@RequestBody Map<String, Object> req) {
        List<Map<String, Object>> offers = MapOps.castListOfMaps(req == null ? null : req.get("offers"));
        if (offers.isEmpty()) {
            return offersMissing();
        }
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (Map<String, Object> offer : offers) {
            String globalId = MapOps.str(MapOps.firstNonEmpty(offer.get("globalId"), offer.get("global_id"))).trim();
            Map<String, Object> call = new java.util.LinkedHashMap<>();
            call.put("global_id", globalId);
            if (req.containsKey("mode")) {
                call.put("mode", req.get("mode"));
            }
            Map<String, Object> r = interfaceFacade.testScenes(call);
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("offer_seq", offer.get("offer_seq"));
            item.put("globalId", globalId);
            // 回填真实商品标识（源自测试任务关联的配置销售品），供 6b 封包 test_pending 后展示商品分组
            item.put("offer_id", MapOps.firstNonEmpty(r.get("offer_id"), offer.get("offer_id"), offer.get("offerId")));
            item.put("offer_name", MapOps.firstNonEmpty(r.get("offer_name"), offer.get("offer_name")));
            item.put("testScenes", r.get("testScenes"));
            item.put("status", MapOps.empty(r.get("testScenes")) ? "fail" : "success");
            items.add(item);
        }
        return batchBody(req, items);
    }

    /* ================= 接口6：查询测试进度 get_test_progress ================= */

    @Operation(summary = "查询测试进度", description = "V3.12 多商品批量：入参 {req_id, offers:[{offer_seq, globalId}]}，逐品推进模拟测试进度状态机（总步骤=场景数+2），支持轮询")
    @PostMapping("/test/offer/progress")
    public Map<String, Object> testProgress(@RequestBody Map<String, Object> req) {
        List<Map<String, Object>> offers = MapOps.castListOfMaps(req == null ? null : req.get("offers"));
        if (offers.isEmpty()) {
            return offersMissing();
        }
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (Map<String, Object> offer : offers) {
            String globalId = MapOps.str(MapOps.firstNonEmpty(offer.get("globalId"), offer.get("global_id"))).trim();
            Map<String, Object> call = new java.util.LinkedHashMap<>();
            call.put("global_id", globalId);
            if (req.containsKey("mode")) {
                call.put("mode", req.get("mode"));
            }
            Map<String, Object> r = interfaceFacade.testProgress(call);
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("offer_seq", offer.get("offer_seq"));
            item.put("globalId", globalId);
            item.put("status", "success");
            // 透传 EOP 裸信封（resultCode/resultObject/resultMsg）——服务出参与 EOP 真实服务逐字一致
            for (String k : java.util.List.of("resultCode", "resultObject", "resultMsg")) {
                if (r.containsKey(k)) {
                    item.put(k, r.get(k));
                }
            }
            items.add(item);
        }
        return batchBody(req, items);
    }

    /* ================= 接口7：查询测试结果 get_test_result ================= */

    @Operation(summary = "查询测试结果", description = "V3.12 多商品批量：入参 {req_id, offers:[{offer_seq, globalId}]}，逐品生成逐场景测点比对明细与受理凭证；报告归档后出参回传 report_url 下载链接")
    @PostMapping("/test/offer/result")
    public Map<String, Object> testResult(@RequestBody Map<String, Object> req,
                                          jakarta.servlet.http.HttpServletRequest httpRequest) {
        List<Map<String, Object>> offers = MapOps.castListOfMaps(req == null ? null : req.get("offers"));
        if (offers.isEmpty()) {
            return offersMissing();
        }
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (Map<String, Object> offer : offers) {
            String globalId = MapOps.str(MapOps.firstNonEmpty(offer.get("globalId"), offer.get("global_id"))).trim();
            Map<String, Object> call = new java.util.LinkedHashMap<>();
            call.put("global_id", globalId);
            call.put("external_base_url", externalBaseUrl(httpRequest));
            if (req.containsKey("mode")) {
                call.put("mode", req.get("mode"));
            }
            Map<String, Object> r = interfaceFacade.testResult(call);
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("offer_seq", offer.get("offer_seq"));
            item.put("globalId", globalId);
            boolean okResult = "0".equals(MapOps.str(r.get("resultCode")));
            item.put("status", okResult ? "success" : "fail");
            // 透传 EOP 裸信封（resultCode/resultObject/resultMsg）——服务出参与 EOP 真实服务逐字一致
            for (String k : java.util.List.of("resultCode", "resultObject", "resultMsg")) {
                if (r.containsKey(k)) {
                    item.put(k, r.get(k));
                }
            }
            items.add(item);
        }
        return batchBody(req, items);
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

    @Operation(summary = "计费规则校验", description = "V3.12 多商品批量：入参 {req_id, check_scene, offers:[{offer_seq, config_json}]}，逐品内置规则引擎校验配置JSON，聚合返回 {req_id, status, offers:[{offer_seq, pass, risk_list, compare_list}]}")
    @PostMapping("/billing/rules/verify")
    public Map<String, Object> billingRulesVerify(@RequestBody Map<String, Object> req) {
        return sim.billingRulesVerifyBatch(req);
    }

    /* ================= 接口9：上线审批推送 submit_release_approval ================= */

    @Operation(summary = "上线审批推送", description = "V3.12 多商品批量：入参 {req_id, approval_flow, offers:[{offer_seq, offer_id, report_url}]}，一个需求单对应一张审批单（工单级 approval_id），工单内含多商品；逐品生成审批单号并四环节门禁校验（同 offer_id 幂等），聚合返回 {approval_id, req_id, status, offers:[{offer_seq, approval_type, status, approval_matrix}]}（approval_id 为工单级单值，仅顶层；商品级状态见 offers[].status）")
    @PostMapping("/approval/submit")
    public Map<String, Object> approvalSubmit(@RequestBody Map<String, Object> req) {
        return sim.approvalSubmitBatch(req);
    }

    /* ================= 接口10：审批进度查询 query_approval_status ================= */

    @Operation(summary = "审批工单查询", description = "V3.12 多商品批量：入参 {req_id, chat_id}（或 offers[] 兜底），按 req_id 关联返回该需求全量商品（一张审批单内含多商品，工单级进度各元素一致）；聚合返回 {req_id, status, offers:[{offer_seq, approval_id, offer_id, status, current_node, ...}]}（各元素冗余携带同一工单级 approval_id）")
    @GetMapping("/approval/status")
    public Map<String, Object> approvalStatus(@RequestParam(required = false) String req_id,
                                              @RequestParam(required = false) String chat_id,
                                              @RequestParam(required = false) String approval_id,
                                              @RequestParam(required = false) String offer_id) {
        return sim.approvalStatusBatch(Map.of("req_id", MapOps.str(req_id),
                "chat_id", MapOps.str(chat_id),
                "approval_id", MapOps.str(approval_id),
                "offer_id", MapOps.str(offer_id)));
    }

    /* ================= 接口11：监控查询 query_product_monitor ================= */

    @Operation(summary = "监控查询", description = "V3.12 多商品批量：入参 {req_id, offers:[{offer_seq, offer_id}]}（或按 req_id 关联解析），逐品返回模拟运行指标（订单量/异常量/计费差错率/告警列表），聚合返回 {req_id, status, offers:[...]}")
    @GetMapping("/product/monitor")
    public Map<String, Object> productMonitor(@RequestParam(required = false) String offer_id,
                                              @RequestParam(required = false) String req_id,
                                              @RequestParam(required = false) String date_range,
                                              @RequestParam(required = false, defaultValue = "all") String metric) {
        Map<String, Object> req = new java.util.LinkedHashMap<>();
        req.put("offer_id", MapOps.str(offer_id));
        req.put("req_id", MapOps.str(req_id));
        req.put("date_range", MapOps.str(date_range));
        req.put("metric", metric);
        return sim.productMonitorBatch(req);
    }

    /**
     * 演示辅助：注入/解除监控异常（工具11 返回 error_count>0），用于复现 wf_sub_07 异常闭环分支。
     * POST JSON {offer_id, enable}；enable 缺省 true。非契约接口，仅供演示预置。
     */
    @Operation(summary = "注入监控异常(演示)", description = "按 offer_id 注入/解除监控异常，使监控查询返回 error_count>0，复现异常告警闭环分支")
    @PostMapping("/product/monitor/inject")
    public Map<String, Object> monitorInject(@RequestBody Map<String, Object> req) {
        String offerId = MapOps.str(req.get("offer_id")).trim();
        if (offerId.isEmpty()) {
            return Map.of("resultCode", "PARAM_MISSING", "resultMsg", "offer_id 必填");
        }
        Object enableRaw = req.get("enable");
        boolean enable = enableRaw == null || Boolean.parseBoolean(String.valueOf(enableRaw));
        sim.injectMonitorError(offerId, enable);
        return Map.of("resultCode", "000000", "resultMsg", "ok",
                "offer_id", offerId, "error_count_injected", String.valueOf(enable));
    }

    /**
     * 监控运维一站式聚合（wf_sub_07 节点702 用）：监控查询 → 逐品 SWRL 告警分级(R-E00~E04)
     * → 命中即推送告警 → P0/P1 建工单闭环 → 根因推理。重逻辑下沉后端，前端工作流仅 1 个 HTTP 节点。
     * <p>
     * POST JSON {req_id, offer_id?, date_range?}。offer_id 缺省时后端按 req_id 关联解析全部商品。
     * 返回 {req_id, status, any_abnormal, offers:[逐品 分级/告警/工单/根因], monitor_lines, summary}。
     */
    @Operation(summary = "监控运维聚合分析", description = "监控查询+SWRL告警分级+告警推送+建工单+根因，供 wf_sub_07 单节点调用")
    @PostMapping("/product/monitor/analyze")
    public Map<String, Object> monitorAnalyze(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        Map<String, Object> batch = sim.productMonitorBatch(safe);
        List<Map<String, Object>> offers = MapOps.castListOfMaps(batch.get("offers"));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("req_id", MapOps.str(safe.get("req_id")));
        if (offers.isEmpty()) {
            out.put("status", "fail");
            out.put("any_abnormal", "false");
            out.put("offers", List.of());
            out.put("monitor_lines", "");
            out.put("summary", MapOps.str(batch.getOrDefault("resultMsg", "未查询到商品")));
            return out;
        }

        Map<String, Object> alertDefaults = opsRules.alertDefaults();
        Map<String, Object> e01 = opsRules.alertRule("R-E01");
        Map<String, Object> e02 = opsRules.alertRule("R-E02");
        Map<String, Object> e03 = opsRules.alertRule("R-E03");
        Map<String, Object> e04 = opsRules.alertRule("R-E04");

        List<Map<String, Object>> results = new java.util.ArrayList<>();
        List<String> lines = new java.util.ArrayList<>();
        List<Map<String, Object>> reasoningAll = new java.util.ArrayList<>();
        List<Map<String, Object>> metricAnomaliesAll = new java.util.ArrayList<>();
        List<Map<String, Object>> alertHintsAll = new java.util.ArrayList<>();
        List<Map<String, Object>> suggestionsAll = new java.util.ArrayList<>();
        boolean anyAbnormal = false;
        int alertCount = 0;
        int woCount = 0;
        for (Map<String, Object> offer : offers) {
            String offerId = MapOps.str(offer.get("offer_id"));
            String offerName = MapOps.str(offer.get("offer_name"));
            Map<String, Object> facts = new java.util.LinkedHashMap<>();
            facts.put("order_count", offer.get("order_count"));
            facts.put("order_trend", offer.get("order_trend"));
            facts.put("error_count", offer.get("error_count"));
            facts.put("error_trend", offer.get("error_trend"));
            facts.put("fee_error_rate", offer.get("fee_error_rate"));
            facts.put("fee_trend", offer.get("fee_trend"));

            OpsSwrlReasoner.SwrlAlertResult grade =
                    opsSwrlReasoner.gradeAlert(offerId, facts, e01, e02, e03, e04, alertDefaults);

            Map<String, Object> row = new java.util.LinkedHashMap<>(offer);
            row.put("alert_level", grade.level());
            row.put("need_work_order", grade.needWorkOrder());
            row.put("sla_minutes", grade.slaMinutes());
            row.put("grade_reasons", String.join("；", grade.reasons()));
            row.put("fired_rules", String.join(",", grade.firedRules()));
            // 告警分级本体推理链（product-alert.ttl R-E00~E04）：逐品 ruleId/condition/conclusion，
            // 供 wf_sub_07 LLM 节点「本体推理依据」逐字引用（对齐 wf_sub_05 reasoning_chain 做法）。
            List<Map<String, Object>> reasoning = alertReasoningRows(grade, facts);
            row.put("reasoning_chain", reasoning);
            for (Map<String, Object> r : reasoning) {
                Map<String, Object> tagged = new java.util.LinkedHashMap<>(r);
                tagged.put("offer_id", offerId);
                tagged.put("offer_name", offerName);
                reasoningAll.add(tagged);
            }

            // 指标异常（本体口径）：逐指标 value/trend/delta/anomaly；异常明细单独出口供 LLM 引用。
            List<Map<String, Object>> metricAnomalies = MapOps.castListOfMaps(offer.get("metric_anomalies"));
            List<Map<String, Object>> anomalies = new java.util.ArrayList<>();
            for (Map<String, Object> m : metricAnomalies) {
                Map<String, Object> a = new java.util.LinkedHashMap<>();
                a.put("offer_id", offerId);
                a.put("offer_name", offerName);
                a.put("metric", m.get("metric"));
                a.put("name", m.get("name"));
                a.put("value", m.get("value"));
                a.put("unit", m.get("unit"));
                a.put("delta_pct", m.get("delta_pct"));
                a.put("trend", m.get("trend"));
                a.put("threshold_ref", m.get("threshold_ref"));
                a.put("condition", "环比 " + MapOps.str(m.get("delta_pct")) + "（趋势" + MapOps.str(m.get("trend")) + "）"
                        + "，越界阈值来自 ops_rules.metricAnomaly." + MapOps.str(m.get("metric")));
                anomalies.add(a);
                metricAnomaliesAll.add(a);
            }
            row.put("metric_anomalies", anomalies);

            // 告警提示（本体 R-E 规则的面向用户提示语）：命中即给出可读提示，未命中给出「正常」提示。
            List<Map<String, Object>> hints = alertHintRows(offerId, offerName, grade, facts);
            row.put("alert_hints", hints);
            alertHintsAll.addAll(hints);

            // 优化建议：由本体 R-O01~R-O05 依据指标异常布尔 SWRL 推理给出（阈值/文案外置 ops_rules）。
            Map<String, Boolean> flags = suggestionFlags(metricAnomalies);
            Map<String, Map<String, Object>> sugRules = new java.util.LinkedHashMap<>();
            for (String rid : List.of("R-O01", "R-O02", "R-O03", "R-O04", "R-O05")) {
                sugRules.put(rid, opsRules.suggestionRule(rid));
            }
            OpsSwrlReasoner.SwrlSuggestionResult sug = opsSwrlReasoner.reasonSuggestions(offerId, flags, sugRules);
            List<Map<String, Object>> suggestions = suggestionRows(offerId, offerName, sug);
            row.put("suggestions", suggestions);
            row.put("suggestion_engine", sug.engine());
            suggestionsAll.addAll(suggestions);

            if (grade.alerted()) {
                anyAbnormal = true;
                String content = buildAlertContent(offerName, offerId, grade, facts);
                Map<String, Object> alertReq = new java.util.LinkedHashMap<>();
                alertReq.put("offer_id", offerId);
                alertReq.put("alarm_level", gradeToAlarmLevel(grade.level()));
                alertReq.put("content", content);
                Map<String, Object> alertResp = sim.sendAlert(alertReq);
                row.put("alert_id", alertResp.get("alert_id"));
                if (!MapOps.empty(alertResp.get("alert_id"))) {
                    alertCount++;
                }

                if (grade.needWorkOrder()) {
                    Map<String, Object> wo = productOntologyService.createWorkOrder(
                            Map.of("offeringId", offerId, "source", "workflow_alert"));
                    String woId = extractWorkOrderId(wo);
                    row.put("work_order_id", woId);
                    row.put("wo_note", MapOps.str(wo.getOrDefault("message", "")));
                    if (!woId.isEmpty()) {
                        woCount++;
                    }
                }

                Map<String, Object> rc = productOntologyService.analyzeRootCause(offerId, null);
                row.put("root_cause_paths", rc.get("paths"));
                row.put("root_cause_action", rc.get("actionList"));
                row.put("root_cause_engine", rc.getOrDefault("reasonEngine", ""));
                row.put("root_cause_pending", Boolean.TRUE.equals(rc.get("success")) ? "0" : "1");

                lines.add(formatLine(row));
            } else {
                lines.add(formatLine(row));
            }
            results.add(row);
        }

        out.put("status", "success");
        out.put("any_abnormal", anyAbnormal ? "true" : "false");
        out.put("alert_count", String.valueOf(alertCount));
        out.put("work_order_count", String.valueOf(woCount));
        out.put("offers", results);
        out.put("monitor_lines", String.join("\n", lines));
        out.put("reasoning_chain", reasoningAll);
        out.put("metric_anomalies", metricAnomaliesAll);
        out.put("alert_hints", alertHintsAll);
        out.put("suggestions", suggestionsAll);
        // 监控预警（面向用户的结构化预警矩阵）：由本体指标异常 → R-O 建议推理链聚合，
        // 跨品按异常严重度取 Top2，供 wf_sub_07 报告「监控预警」段逐条渲染「推理链 + 采纳引导」。
        out.put("alerts", buildMonitorAlerts(results));
        out.put("summary", anyAbnormal
                ? "监控完成：发现异常商品，已按 SWRL 分级推送告警并闭环建单"
                : "监控完成：各商品运行正常");
        return out;
    }

    private String buildAlertContent(String offerName, String offerId, OpsSwrlReasoner.SwrlAlertResult grade,
                                     Map<String, Object> facts) {
        return "【" + grade.level() + " 告警】" + (offerName.isEmpty() ? offerId : offerName)
                + " 异常：" + String.join("；", grade.reasons())
                + "；订单量 " + MapOps.str(facts.get("order_count")) + "，异常量 " + MapOps.str(facts.get("error_count"))
                + "，计费差错率 " + MapOps.str(facts.get("fee_error_rate"))
                + "；SLA " + grade.slaMinutes() + " 分钟";
    }

    /**
     * 告警分级推理链行（product-alert.ttl R-E00~E04，逐品）：
     * 命中规则给出条件与结论；未命中规则补「已评估→未命中」行（带实际取值与阈值），
     * 使正常商品同样具备规则维度推理过程，供 wf_sub_07 报告「本体推理依据」逐字引用。
     */
    private List<Map<String, Object>> alertReasoningRows(OpsSwrlReasoner.SwrlAlertResult grade,
                                                         Map<String, Object> facts) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        String err = MapOps.str(facts.get("error_count"));
        String fee = MapOps.str(facts.get("fee_error_rate"));
        java.util.Set<String> fired = new java.util.LinkedHashSet<>(grade.firedRules());
        rows.add(reasonRow("R-E00", "监控指标已采集：异常量 " + err + "，计费差错率 " + fee,
                fired.contains("R-E00") ? "命中 → 触发告警评估" : "未命中 → 指标正常"));
        rows.add(reasonRow("R-E01", "异常量 " + err + " ≥ 10 或计费差错率 " + fee + " ≥ 0.05",
                fired.contains("R-E01") ? "命中 → 判为 P0 严重告警" : "未命中 → 未达 P0"));
        rows.add(reasonRow("R-E02", "异常量 " + err + " ≥ 1 或计费差错率 " + fee + " ≥ 0.01",
                fired.contains("R-E02") ? "命中 → 判为 P1 重要告警" : "未命中 → 未达 P1"));
        rows.add(reasonRow("R-E03", "指标趋势劣化（订单量下降/异常量上升/计费差错率上升）",
                fired.contains("R-E03") ? "命中 → 判为 P2 提示告警" : "未命中 → 趋势平稳"));
        rows.add(reasonRow("R-E04", "P0/P1 强制建单，P2 观察即可",
                fired.contains("R-E04") ? "命中 → 已建处置工单" : "未命中 → 无需建单"));
        return rows;
    }

    private Map<String, Object> reasonRow(String ruleId, String condition, String conclusion) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("condition", condition);
        row.put("conclusion", conclusion);
        return row;
    }

    /** 由指标异常明细映射为 R-O 建议所需布尔（revenueDrop/orderDrop/churnRise/attachDrop/arpuDrop/activeDrop）。 */
    private Map<String, Boolean> suggestionFlags(List<Map<String, Object>> metricAnomalies) {
        Map<String, Boolean> flags = new java.util.LinkedHashMap<>();
        for (Map<String, Object> m : metricAnomalies) {
            String metric = MapOps.str(m.get("metric"));
            String trend = MapOps.str(m.get("trend"));
            switch (metric) {
                case "revenue" -> flags.put("revenueDrop", "下降".equals(trend));
                case "order_cnt" -> flags.put("orderDrop", "下降".equals(trend));
                case "new_users" -> flags.put("newUsersDrop", "下降".equals(trend));
                case "churn_users" -> flags.put("churnRise", "上升".equals(trend));
                case "active_users" -> flags.put("activeDrop", "下降".equals(trend));
                case "arpu" -> flags.put("arpuDrop", "下降".equals(trend));
                case "attach_rate" -> flags.put("attachDrop", "下降".equals(trend));
                default -> { }
            }
        }
        return flags;
    }

    /**
     * 告警提示行（面向用户的可读提示）：命中规则给出「提示语 + 依据指标 + 处置时限」，
     * 未命中给出「正常」提示，使报告「告警提示」段有逐规则口径。
     */
    private List<Map<String, Object>> alertHintRows(String offerId, String offerName,
                                                    OpsSwrlReasoner.SwrlAlertResult grade,
                                                    Map<String, Object> facts) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        java.util.Set<String> fired = new java.util.LinkedHashSet<>(grade.firedRules());
        if (fired.contains("R-E01")) {
            rows.add(hintRow(offerId, offerName, "R-E01", "P0",
                    "【P0 严重告警】异常量 " + MapOps.str(facts.get("error_count"))
                            + "，计费差错率 " + MapOps.str(facts.get("fee_error_rate"))
                            + "；请立即介入，" + grade.slaMinutes() + " 分钟内响应"));
        } else if (fired.contains("R-E02")) {
            rows.add(hintRow(offerId, offerName, "R-E02", "P1",
                    "【P1 重要告警】异常量 " + MapOps.str(facts.get("error_count"))
                            + "，计费差错率 " + MapOps.str(facts.get("fee_error_rate"))
                            + "；请当日处理，" + grade.slaMinutes() + " 分钟内闭环"));
        } else if (fired.contains("R-E03")) {
            rows.add(hintRow(offerId, offerName, "R-E03", "P2",
                    "【P2 提示告警】指标趋势劣化（无实量异常）；观察即可，处置时限 " + grade.slaMinutes() + " 分钟"));
        } else {
            rows.add(hintRow(offerId, offerName, "R-E00", "正常",
                    "【运行正常】异常量 " + MapOps.str(facts.get("error_count"))
                            + "，计费差错率 " + MapOps.str(facts.get("fee_error_rate")) + "，未触发告警"));
        }
        return rows;
    }

    /** 监控预警展示上限（跨品按异常严重度取 Top N）。 */
    private static final int MONITOR_ALERT_TOP_N = 2;

    /**
     * 监控预警矩阵（面向用户的结构化预警，供 wf_sub_07「监控预警」段直接渲染）。
     * <p>
     * 口径：以后端本体 R-O 建议的 firedRules 为锚，逐（商品, 建议规则）成一条预警；
     * 每条预警内嵌其支撑指标异常明细与**本体推理链**（指标越界 → R-O 规则条件 → 建议结论），
     * 并按支撑异常的最大环比重排，跨品取 Top N，供用户「采纳预警 → 发起销售品优化工单」。
     * 无指标异常时返回空数组。
     */
    private List<Map<String, Object>> buildMonitorAlerts(List<Map<String, Object>> results) {
        List<Map<String, Object>> alerts = new java.util.ArrayList<>();
        for (Map<String, Object> row : results) {
            String offerId = MapOps.str(row.get("offer_id"));
            String offerName = MapOps.str(row.get("offer_name"));
            List<Map<String, Object>> anomalies = MapOps.castListOfMaps(row.get("metric_anomalies"));
            List<Map<String, Object>> suggestions = MapOps.castListOfMaps(row.get("suggestions"));
            for (Map<String, Object> sug : suggestions) {
                String ruleId = MapOps.str(sug.get("ruleId"));
                if (ruleId.isEmpty() || "R-O00".equals(ruleId)) {
                    continue;
                }
                // 支撑该建议的指标异常（metrics_registry.thresholdRef 指向同一 R-O 规则）
                List<Map<String, Object>> support = new java.util.ArrayList<>();
                double maxAbsDelta = 0;
                for (Map<String, Object> a : anomalies) {
                    if (!ruleId.equals(MapOps.str(a.get("threshold_ref")))) {
                        continue;
                    }
                    support.add(a);
                    maxAbsDelta = Math.max(maxAbsDelta, Math.abs(parsePercent(a.get("delta_pct"))));
                }
                if (support.isEmpty()) {
                    continue;
                }
                Map<String, Object> alert = new java.util.LinkedHashMap<>();
                alert.put("offer_id", offerId);
                alert.put("offer_name", offerName);
                alert.put("ruleId", ruleId);
                alert.put("name", MapOps.str(sug.get("name")));
                alert.put("advice", MapOps.str(sug.get("advice")));
                alert.put("support_metrics", support);
                alert.put("reasoning_chain", suggestionReasoningChain(offerName, offerId, sug, support));
                alert.put("cta", "回复【发起优化工单】采纳该预警建议，发起销售品优化工单");
                alert.put("_severity", maxAbsDelta);
                alerts.add(alert);
            }
        }
        alerts.sort((a, b) -> Double.compare(
                Double.parseDouble(String.valueOf(b.get("_severity"))),
                Double.parseDouble(String.valueOf(a.get("_severity")))));
        int top = Math.min(MONITOR_ALERT_TOP_N, alerts.size());
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (int i = 0; i < top; i++) {
            Map<String, Object> a = alerts.get(i);
            a.remove("_severity");
            Map<String, Object> ordered = new java.util.LinkedHashMap<>();
            ordered.put("alert_no", String.valueOf(i + 1));
            ordered.putAll(a);
            out.add(ordered);
        }
        return out;
    }

    /**
     * 逐条预警的本体推理链（metric 异常 → R-O 规则条件 → 建议结论），逐字可引用、可追溯。
     * 链式三步：(1) 指标越界（含实测环比与阈值）；(2) R-O 规则命中（条件来自 ops_rules.suggestions）；
     * (3) 建议结论（advice，来自本体 R-O01~R-O05）。
     */
    private List<Map<String, Object>> suggestionReasoningChain(String offerName, String offerId,
                                                               Map<String, Object> sug,
                                                               List<Map<String, Object>> support) {
        List<Map<String, Object>> chain = new java.util.ArrayList<>();
        String nm = offerName.isEmpty() ? offerId : offerName;
        for (Map<String, Object> a : support) {
            chain.add(reasonRow("R-A·指标异常",
                    nm + "·" + MapOps.str(a.get("name")) + " 环比 " + MapOps.str(a.get("delta_pct"))
                            + "（趋势" + MapOps.str(a.get("trend")) + "），越界阈值来自 metrics_registry."
                            + MapOps.str(a.get("metric")) + ".anomaly.thresholdRef=" + MapOps.str(a.get("threshold_ref")),
                    "命中 → 该指标记为异常"));
        }
        chain.add(reasonRow(MapOps.str(sug.get("ruleId")),
                "条件：" + MapOps.str(sug.get("condition")),
                "命中 → 归入【" + MapOps.str(sug.get("name")) + "】"));
        chain.add(reasonRow("R-O·结论",
                "本体 R-O 建议推理输出：" + MapOps.str(sug.get("advice")),
                "结论 → 生成优化建议，可发起销售品优化工单"));
        return chain;
    }

    /** 解析 "12.34%" 形态的环比百分数为数值（无 % 号按原值）。 */
    private double parsePercent(Object v) {
        String s = MapOps.str(v).replace("%", "").trim();
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Map<String, Object> hintRow(String offerId, String offerName, String ruleId, String level, String hint) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("offer_id", offerId);
        row.put("offer_name", offerName);
        row.put("ruleId", ruleId);
        row.put("level", level);
        row.put("hint", hint);
        return row;
    }

    /**
     * 优化建议行：由本体 R-O 推理结果 + ops_rules.suggestions 文案拼装（逐命中规则一行）。
     * 无命中时给出「维持现有运营」占位行，保证报告建议段非空。
     */
    private List<Map<String, Object>> suggestionRows(String offerId, String offerName,
                                                     OpsSwrlReasoner.SwrlSuggestionResult sug) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (String ruleId : sug.firedRules()) {
            Map<String, Object> cfg = opsRules.suggestionRule(ruleId);
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("offer_id", offerId);
            row.put("offer_name", offerName);
            row.put("ruleId", ruleId);
            row.put("name", MapOps.str(cfg.get("name")));
            row.put("condition", MapOps.str(cfg.get("condition")));
            row.put("advice", MapOps.str(cfg.get("advice")));
            row.put("engine", sug.engine());
            rows.add(row);
        }
        if (rows.isEmpty()) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("offer_id", offerId);
            row.put("offer_name", offerName);
            row.put("ruleId", "R-O00");
            row.put("name", "维持运营");
            row.put("condition", "各指标均在阈值内");
            row.put("advice", "维持现有运营策略，保持指标监控");
            row.put("engine", sug.engine());
            rows.add(row);
        }
        return rows;
    }

    private String gradeToAlarmLevel(String level) {
        if ("P0".equals(level)) return "high";
        if ("P1".equals(level)) return "middle";
        return "low";
    }

    private String formatLine(Map<String, Object> row) {
        String level = MapOps.str(row.get("alert_level"));
        return "- " + (MapOps.str(row.get("offer_name")).isEmpty() ? MapOps.str(row.get("offer_id"))
                : MapOps.str(row.get("offer_name")))
                + "：订单量 " + MapOps.str(row.get("order_count")) + "（" + MapOps.str(row.get("order_trend")) + "）"
                + "，异常量 " + MapOps.str(row.get("error_count")) + "（" + MapOps.str(row.get("error_trend")) + "）"
                + "，计费差错率 " + MapOps.str(row.get("fee_error_rate"))
                + "，等级 " + ("NONE".equals(level) ? "正常" : level)
                + (MapOps.empty(row.get("alert_id")) ? "" : "，告警单 " + MapOps.str(row.get("alert_id")))
                + (MapOps.empty(row.get("work_order_id")) ? "" : "，工单 " + MapOps.str(row.get("work_order_id")));
    }

    @SuppressWarnings("unchecked")
    private String extractWorkOrderId(Map<String, Object> wo) {
        Object raw = wo.get("workOrder");
        if (raw instanceof Map<?, ?> m) {
            Object wid = m.get("workOrderId");
            return wid == null ? "" : String.valueOf(wid);
        }
        Object flat = wo.get("work_order_id");
        return flat == null ? "" : String.valueOf(flat);
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

    @Operation(summary = "节点结果查询", description = "后续环节按需求单号查询上游环节结果 JSON（POST JSON 体，兼容 GET 查询参数）")
    @RequestMapping(value = "/result/query", method = {RequestMethod.POST, RequestMethod.GET})
    public Map<String, Object> queryNodeResult(@RequestBody(required = false) Map<String, Object> req,
                                               @RequestParam(required = false) String req_id,
                                               @RequestParam(required = false) String node_name,
                                               @RequestParam(required = false) String latest_only) {
        String reqId = req != null ? MapOps.str(req.get("req_id")) : req_id;
        String node = req != null ? MapOps.str(req.get("node_name")) : node_name;
        String latest = req != null ? MapOps.str(req.get("latest_only")) : latest_only;
        if (latest == null || latest.isEmpty()) {
            latest = "1";
        }
        return nodeResultService.query(reqId, node, latest);
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

    /* ================= 接口14b：配置要素本体SWRL校验（ontology_swrl_check，P2） ================= */

    /**
     * 对接 wf_sub_01 节点108（要素校验与方案合并）本体逻辑校验（P2 仅记录不阻断）：
     * 把该商品已合并要素映射为本体内联事实图，代理调用本体平台同步 SWRL 推理，
     * 归一返回 checked/diagnostics/statistics/results 等 snake_case 出参，由节点108 写入 meta.onto_check。
     * 本体不可用/无映射/异常时返回 checked=0（不阻断），由调用方按附加信息展示。
     */
    @Operation(summary = "配置要素本体SWRL校验", description = "P2：elements→本体内联事实图→本体平台同步SWRL推理，出参 checked/diagnostics/statistics/results（仅记录不阻断）")
    @PostMapping("/ontology/swrl-check")
    public Map<String, Object> ontologySwrlCheck(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        return ontologySwrlCheckService.check(safe);
    }

    /* ================= 接口15：异动根因本体推理（ops_root_cause） ================= */

    /**
     * 对接 work-flow 阶段1.1 wf_sub_07 节点706 CODE_OP_ROOT_CAUSE：
     * 由 {offer_id} 调 productOntologyService.analyzeRootCause(offeringId)，
     * 并把 camelCase 字段适配为 workflow 契约的 snake_case 出参
     * （reason_engine/evidence_triples/swrl_fired/applied_rules/action_list）。
     */
    @Operation(summary = "异动根因本体推理", description = "由 offer_id 对异动一指定位根因并推理处置动作，输出归因路径/证据三元组/命中规则/动作清单（契约出参 snake_case）")
    @PostMapping("/ops/root-cause")
    public Map<String, Object> opsRootCause(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        String offerId = MapOps.str(safe.get("offer_id"));
        Map<String, Object> r = productOntologyService.analyzeRootCause(offerId, null);
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
     * 由 {offer_id} 建处置工单，取嵌套 workOrder.workOrderId 显影为 work_order_id 契约出参。
     */
    @Operation(summary = "创建运维工单", description = "由 offer_id 建立处置工单并回写本体，返回 work_order_id（契约 snake_case）")
    @PostMapping("/ops/work-orders")
    public Map<String, Object> opsCreateWorkOrder(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> safe = req == null ? Map.of() : req;
        String offerId = MapOps.str(safe.get("offer_id"));
        Map<String, Object> r = productOntologyService.createWorkOrder(Map.of("offeringId", offerId, "source", "workflow"));
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
     * 入参 {offer_id, approval_id, kind}；回退到既有 GET /product/config/script 附件，
     * 此处返回 download_url（+message）契约出参。
     */
    @Operation(summary = "配置/上线脚本下载", description = "按 offer_id 返回上线加载脚本下载链接 download_url + message")
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
                + "/api/v1/appstore/product/config/script?offer_id=" + offerId.trim());
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
