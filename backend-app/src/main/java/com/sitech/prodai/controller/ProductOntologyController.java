package com.sitech.prodai.controller;

import com.sitech.prodai.dto.BatchDocumentRequest;
import com.sitech.prodai.dto.ChatConfigureRequest;
import com.sitech.prodai.dto.ComplianceRequest;
import com.sitech.prodai.dto.InferRequest;
import com.sitech.prodai.dto.RiskAuditRequest;
import com.sitech.prodai.dto.RiskRulesRequest;
import com.sitech.prodai.dto.RootCauseRequest;
import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.OntologyStore;
import com.sitech.prodai.service.ProductConfigRegressionService;
import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.ProductTemplateRegistry;
import com.sitech.prodai.service.ProductTemplateService;
import com.sitech.prodai.service.TemplateComplianceService;
import com.sitech.prodai.service.TemplateDeriveEngine;
import com.sitech.prodai.service.queryheat.QueryHeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产商品本体 API（配置+运营）。响应带 {@code demoMode}/{@code dataSource}（标识当前数据配置，非另一套逻辑）。
 */
@Tag(name = "产商品本体", description = "产商品本体 API（配置+运营）：图谱、模板配置、实例管理、草稿发布、运营分析与工单")
@RestController
@RequestMapping("/api/v1/product-ontology")
public class ProductOntologyController {

    private final ProductOntologyService productOntologyService;
    private final OntologyService ontologyService;
    private final OntologyStore ontologyStore;
    private final ProductTemplateRegistry templateRegistry;
    private final ProductConfigRegressionService regressionService;
    private final ProductTemplateService templateService;
    private final TemplateComplianceService templateComplianceService;
    private final TemplateDeriveEngine deriveEngine;
    private final QueryHeatService queryHeatService;

    public ProductOntologyController(
            ProductOntologyService productOntologyService,
            OntologyService ontologyService,
            OntologyStore ontologyStore,
            ProductTemplateRegistry templateRegistry,
            ProductConfigRegressionService regressionService,
            ProductTemplateService templateService,
            TemplateComplianceService templateComplianceService,
            TemplateDeriveEngine deriveEngine,
            QueryHeatService queryHeatService
    ) {
        this.productOntologyService = productOntologyService;
        this.ontologyService = ontologyService;
        this.ontologyStore = ontologyStore;
        this.templateRegistry = templateRegistry;
        this.regressionService = regressionService;
        this.templateService = templateService;
        this.templateComplianceService = templateComplianceService;
        this.deriveEngine = deriveEngine;
        this.queryHeatService = queryHeatService;
    }

    private Map<String, Object> ok(Map<String, Object> body) {
        return productOntologyService == null ? body : productOntologyService.withModeMeta(body);
    }

    @Operation(summary = "图谱概览", description = "返回事实图谱摘要（节点/边统计与样例）")
    @GetMapping("/graph")
    public Map<String, Object> graph() {
        return productOntologyService.getGraphSummary();
    }

    /** 事务式热重载事实图（P1-6 last-known-good 守卫）：失败保留现行图谱并返回差异报告。 */
    @Operation(summary = "热重载图谱", description = "事务式热重载事实图：失败保留现行图谱并返回差异报告")
    @PostMapping("/graph/reload")
    public Map<String, Object> reloadGraph() {
        return ok(productOntologyService.reloadGraph());
    }

    @Operation(summary = "本体元数据", description = "返回本体元信息与产品模板清单（productTemplates）")
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> body = new LinkedHashMap<>(productOntologyService.getOntologyMeta());
        // §9.2 模板清单：productTemplates 数组
        body.put("productTemplates", templateRegistry.list());
        return body;
    }

    /** §9.1 模板渲染 schema 下发：前端 DynamicForm 直接消费。 */
    @Operation(summary = "模板表单 Schema", description = "按品类下发模板渲染 schema，前端 DynamicForm 直接消费")
    @GetMapping("/config/template/{category}")
    public Map<String, Object> template(@PathVariable("category") String category) {
        Map<String, Object> schema = templateRegistry.buildFormSchema(category);
        Map<String, Object> body = new LinkedHashMap<>();
        if (schema == null) {
            body.put("success", false);
            body.put("message", "未识别的产品品类模板: " + category);
            return ok(body);
        }
        templateRegistry.findByCategory(category).ifPresent(template ->
                body.put("template", Map.of(
                        "template_id", String.valueOf(template.get("template_id")),
                        "template_name", String.valueOf(template.get("template_name")),
                        "version", String.valueOf(template.get("version")),
                        "status", String.valueOf(template.get("status")),
                        "category_code", String.valueOf(template.get("category_code")),
                        "message_root_key", String.valueOf(template.get("message_root_key")))));
        body.put("success", true);
        body.put("schema", schema);
        return ok(body);
    }

    /** 模板注册中心热重载（增量注册：新增产品只落地模板文件）。 */
    @Operation(summary = "重载模板注册中心", description = "热重载模板注册中心（增量注册：新增产品只落地模板文件）")
    @PostMapping("/config/template/reload")
    public Map<String, Object> reloadTemplates() {
        Map<String, Object> report = templateRegistry.reload();
        Map<String, Object> body = new LinkedHashMap<>(report);
        body.put("success", true);
        body.put("message", "templates reloaded");
        return ok(body);
    }

    // ---------------- 模板生命周期状态机（P2-5，§13.3） ----------------

    /**
     * P3-1 新建产品类型模板草稿入库。
     * body：模板 payload（可直接传模板 JSON；亦兼容 {template:{...}, author, summary}）。
     * 仅允许全新 template_id；生效需 submit-review → publish（研发自助新增类型）。
     */
    @Operation(summary = "新建模板草稿", description = "新建产品类型模板草稿入库，仅允许全新 template_id；生效需 submit-review → publish")
    @PostMapping("/config/template")
    public Map<String, Object> createTemplate(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        Object templateRaw = safe.get("template");
        Map<String, Object> payload;
        if (templateRaw instanceof Map<?, ?> m) {
            payload = new LinkedHashMap<>();
            m.forEach((k, v) -> payload.put(String.valueOf(k), v));
        } else {
            payload = new LinkedHashMap<>(safe);
            payload.remove("author");
            payload.remove("summary");
            payload.remove("operator");
        }
        Object authorRaw = safe.get("author") != null ? safe.get("author") : safe.get("operator");
        String author = authorRaw != null ? String.valueOf(authorRaw) : null;
        String summary = safe.get("summary") != null ? String.valueOf(safe.get("summary")) : null;
        return ok(templateService.createTemplate(payload, author, summary));
    }

    /** P3-1 修订（版本号++）：PUT 别名对齐 §9.3 完整版契约，语义与 saveDraft 收敛。 */
    @Operation(summary = "修订模板", description = "修订模板（版本号++），语义与保存草稿一致")
    @PutMapping("/config/template/{templateId}")
    public Map<String, Object> updateTemplate(@PathVariable("templateId") String templateId,
                                              @RequestBody Map<String, Object> payload,
                                              @RequestParam(value = "author", required = false) String author,
                                              @RequestParam(value = "summary", required = false) String summary) {
        return ok(templateService.saveDraft(templateId, payload, author, summary));
    }

    /** 编辑新 draft（版本号++）：body 传模板 JSON payload。 */
    @Operation(summary = "保存模板新版本草稿", description = "编辑新 draft（版本号++），body 传模板 JSON payload")
    @PostMapping("/config/template/{templateId}/versions")
    public Map<String, Object> saveTemplateDraft(@PathVariable("templateId") String templateId,
                                                 @RequestBody Map<String, Object> payload,
                                                 @RequestParam(value = "author", required = false) String author,
                                                 @RequestParam(value = "summary", required = false) String summary) {
        return ok(templateService.saveDraft(templateId, payload, author, summary));
    }

    /** 版本列表 + 动作日志（表 A/表 B 视图）。 */
    @Operation(summary = "模板版本列表", description = "返回版本列表与动作日志（表 A/表 B 视图）")
    @GetMapping("/config/template/{templateId}/versions")
    public Map<String, Object> templateVersions(@PathVariable("templateId") String templateId) {
        return ok(templateService.versions(templateId));
    }

    /** P3-1a 版本对比：from/to 两版本 payload 字段级 diff（added/removed/changed）。 */
    @Operation(summary = "模板版本对比", description = "from/to 两版本 payload 字段级 diff（added/removed/changed）")
    @GetMapping("/config/template/{templateId}/diff")
    public Map<String, Object> diffTemplateVersions(@PathVariable("templateId") String templateId,
                                                    @RequestParam("from") String fromVersion,
                                                    @RequestParam("to") String toVersion) {
        return ok(templateService.diffVersions(templateId, fromVersion, toVersion));
    }

    /** draft ──review──► review。 */
    @Operation(summary = "提交评审", description = "draft ──review──► review 状态流转")
    @PostMapping("/config/template/{templateId}/submit-review")
    public Map<String, Object> submitTemplateReview(@PathVariable("templateId") String templateId,
                                                    @RequestParam("version") String version,
                                                    @RequestParam(value = "operator", required = false) String operator) {
        return ok(templateService.submitReview(templateId, version, operator));
    }

    /** review ──publish(dryrun通过)──► published：P1-6 四步守卫，失败保留现行。 */
    @Operation(summary = "发布模板", description = "review ──publish(dryrun通过)──► published，四步守卫失败保留现行")
    @PostMapping("/config/template/{templateId}/publish")
    public Map<String, Object> publishTemplate(@PathVariable("templateId") String templateId,
                                               @RequestParam("version") String version,
                                               @RequestParam(value = "operator", required = false) String operator) {
        return ok(templateService.publish(templateId, version, operator));
    }

    /** rollback：取表 A 目标版本 payload → 守卫三步 → 成功记 rollback 日志。 */
    @Operation(summary = "回滚模板", description = "取目标版本 payload → 守卫三步 → 成功记 rollback 日志")
    @PostMapping("/config/template/{templateId}/rollback")
    public Map<String, Object> rollbackTemplate(@PathVariable("templateId") String templateId,
                                                @RequestParam("to") String toVersion,
                                                @RequestParam(value = "operator", required = false) String operator) {
        return ok(templateService.rollback(templateId, toVersion, operator));
    }

    /** published ──deprecate──► deprecated。 */
    @Operation(summary = "废弃模板", description = "published ──deprecate──► deprecated 状态流转")
    @PostMapping("/config/template/{templateId}/deprecate")
    public Map<String, Object> deprecateTemplate(@PathVariable("templateId") String templateId,
                                                 @RequestParam("version") String version,
                                                 @RequestParam(value = "operator", required = false) String operator,
                                                 @RequestParam(value = "reason", required = false) String reason) {
        return ok(templateService.deprecate(templateId, version, operator, reason));
    }

    // ---------------- 实例 CRUD + SPARQL（P3-1a，§10 P3） ----------------

    /** 实例列表：type 可选过滤（如 ConfigScheme / ShelfOffering）。 */
    @Operation(summary = "实例列表", description = "本体实例列表，type 可选过滤（如 ConfigScheme / ShelfOffering）")
    @GetMapping("/instances")
    public Map<String, Object> listInstances(@RequestParam(value = "type", required = false) String type) {
        List<Map<String, Object>> rows = type == null || type.isBlank()
                ? ontologyStore.allInstances()
                : ontologyStore.samplesFor(type);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", rows.size());
        body.put("type", type);
        body.put("instances", rows);
        return ok(body);
    }

    /** 实例详情：按完整 URI 查询单个实例。 */
    @Operation(summary = "实例详情", description = "按完整 URI 查询单个实例")
    @GetMapping("/instances/{uri}")
    public Map<String, Object> getInstance(@PathVariable("uri") String uri) {
        Map<String, Object> entity = ontologyStore.getEntity(uri);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", !entity.isEmpty());
        body.put("uri", uri);
        body.put("instance", entity);
        if (entity.isEmpty()) {
            body.put("message", "实例不存在: " + uri);
        }
        return ok(body);
    }

    /** 新建实例：body = { uri, type, facts }；URI 已存在时返回失败（幂等创建）。 */
    @Operation(summary = "新建实例", description = "body = { uri, type, facts }；URI 已存在时返回失败（幂等创建）")
    @PostMapping("/instances")
    public Map<String, Object> createInstance(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        String uri = strOrNull(safe.get("uri"), safe.get("instance_uri"));
        String type = strOrNull(safe.get("type"), safe.get("instance_type"));
        if (uri == null || type == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "uri 与 type 必填");
            return ok(body);
        }
        if (!ontologyStore.getEntity(uri).isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "实例已存在: " + uri);
            body.put("uri", uri);
            return ok(body);
        }
        Map<String, Object> facts = castMap(safe.get("facts"));
        ontologyStore.addInstance(uri, type, facts);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "实例已创建");
        body.put("uri", uri);
        body.put("type", type);
        body.put("instance", ontologyStore.getEntity(uri));
        return ok(body);
    }

    /** 更新实例：body = { facts }（部分更新，merge 语义）。 */
    @Operation(summary = "更新实例", description = "body = { facts }（部分更新，merge 语义）")
    @PutMapping("/instances/{uri}")
    public Map<String, Object> updateInstance(@PathVariable("uri") String uri,
                                              @RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        Map<String, Object> existing = ontologyStore.getEntity(uri);
        if (existing.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "实例不存在: " + uri);
            return ok(body);
        }
        Map<String, Object> facts = castMap(safe.get("facts"));
        ontologyStore.updateInstance(uri, facts);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "实例已更新");
        body.put("uri", uri);
        body.put("instance", ontologyStore.getEntity(uri));
        return ok(body);
    }

    /** 删除实例：按完整 URI 删除（幂等，不存在也返回成功）。 */
    @Operation(summary = "删除实例", description = "按完整 URI 删除（幂等，不存在也返回成功）")
    @DeleteMapping("/instances/{uri}")
    public Map<String, Object> deleteInstance(@PathVariable("uri") String uri) {
        Map<String, Object> existing = ontologyStore.getEntity(uri);
        ontologyStore.deleteInstance(uri);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", existing.isEmpty() ? "实例不存在（幂等删除）" : "实例已删除");
        body.put("uri", uri);
        return ok(body);
    }

    /**
     * 裸 SPARQL 查询（P3-1a「SPARQL 可回答为什么」）：body = { query }。
     * 只读 SELECT/ASK；空 query 返回全量实例列表（对齐 store.sparqlSelect 语义）。
     */
    @Operation(summary = "SPARQL 查询", description = "只读 SELECT/ASK 查询；空 query 返回全量实例列表")
    @PostMapping("/sparql")
    public Map<String, Object> sparql(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        String query = strOrNull(safe.get("query"), safe.get("sparql"));
        List<Map<String, Object>> rows = ontologyStore.sparqlSelect(query == null ? "" : query);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("query", query);
        body.put("total", rows.size());
        body.put("results", rows);
        return ok(body);
    }

    /** P1-7 验收核对：运行双品类回归用例集（家庭融合/校园/5G/宽带），返回逐条断言报告。 */
    @Operation(summary = "运行回归用例", description = "运行双品类回归用例集（家庭融合/校园/5G/宽带），返回逐条断言报告")
    @GetMapping("/config/regression/run")
    public Map<String, Object> runRegression() {
        return ok(regressionService.runAll());
    }

    /** P2-6 收敛后回归门禁报告（评审入口）；P2-7 后为引擎侧回归断言（含报文节点判据）。 */
    @Operation(summary = "回归门禁报告", description = "收敛后回归门禁报告（评审入口），含报文节点判据")
    @GetMapping("/config/regression/diff")
    public Map<String, Object> regressionDiff() {
        return ok(regressionService.runAll());
    }

    @Operation(summary = "字段推理", description = "derive_rules 引擎按 slots/draft 推理缺失字段")
    @PostMapping("/config/infer")
    public Map<String, Object> infer(@RequestBody(required = false) InferRequest request) {
        InferRequest safe = request == null ? new InferRequest() : request;
        // P2-7 主链路：derive_rules 引擎接管推理（Java inferFields 分支已清理）
        return ok(deriveEngine.derive(safe.getSlots(), safe.getDraft(),
                productOntologyService.loadGraph()));
    }

    @Operation(summary = "合规智检", description = "存量 smart 源解析（草稿/在架/LLM 兜底）+ 模板裁剪面与轻量字段约束校验")
    @PostMapping("/config/compliance")
    public Map<String, Object> compliance(@RequestBody(required = false) ComplianceRequest request) {
        ComplianceRequest safe = request == null ? new ComplianceRequest() : request;
        // 先做存量 smart 源解析（草稿/在架/LLM 兜底），再套用 P2-4 模板裁剪面与轻量字段约束。
        // 解析失败（如未提供套餐/草稿）时原样返回存量提示。
        Map<String, Object> smart = productOntologyService.checkComplianceSmart(
                safe.getOfferingId(), safe.getText(), safe.getDraft());
        if (!Boolean.TRUE.equals(smart.get("success"))
                || !(smart.get("draft") instanceof Map<?, ?> resolvedDraft)) {
            return ok(smart);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = (Map<String, Object>) resolvedDraft;
        Map<String, Object> body = templateComplianceService.checkComplianceByTemplate(draft, null);
        // 回填 smart 解析元信息（source/sourceLabel/offeringId/offeringName/query/intent）
        for (String key : List.of("source", "sourceLabel", "offeringId", "offeringName",
                "query", "intent")) {
            if (smart.containsKey(key)) {
                body.put(key, smart.get(key));
            }
        }
        body.put("draft", draft);
        body.put("success", true);
        return ok(body);
    }

    @Operation(summary = "对话式配置", description = "自然语言对话生成/修订配置草稿（text 必填）")
    @PostMapping("/config/chat")
    public Map<String, Object> chatConfigure(@RequestBody ChatConfigureRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        return ok(productOntologyService.chatConfigure(request.getText(), request.getDraft()));
    }

    @Operation(summary = "批量映射文档", description = "从文档文本批量映射生成配置")
    @PostMapping("/config/batch")
    public Map<String, Object> batch(@RequestBody(required = false) BatchDocumentRequest request) {
        BatchDocumentRequest safe = request == null ? new BatchDocumentRequest() : request;
        return ok(productOntologyService.batchFromDocument(safe.getDocumentText(), safe.getPackages()));
    }

    /** 智查：语义/关键词发现历史配置方案 */
    @Operation(summary = "智查发现", description = "语义/关键词发现历史配置方案")
    @PostMapping("/config/discover")
    public Map<String, Object> discover(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String q = body.get("q") != null ? String.valueOf(body.get("q"))
                : body.get("question") != null ? String.valueOf(body.get("question")) : "";
        int limit = 20;
        Object lim = body.get("limit");
        if (lim instanceof Number n) {
            limit = n.intValue();
        } else if (lim != null) {
            try {
                limit = Integer.parseInt(String.valueOf(lim));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return ok(productOntologyService.discoverConfigs(q, limit));
    }

    /** 一键复制为配置草稿并合规校验（带 session_id 时复制即开配置工单；带 requirement 时按补充需求修正副本字段） */
    @Operation(summary = "复制为草稿", description = "一键复制为配置草稿并合规校验（可携带 session_id/requirement）")
    @PostMapping("/config/copy-as-draft")
    public Map<String, Object> copyAsDraft(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String offeringId = body.get("offering_id") != null ? String.valueOf(body.get("offering_id"))
                : body.get("offeringId") != null ? String.valueOf(body.get("offeringId")) : null;
        String text = body.get("text") != null ? String.valueOf(body.get("text")) : null;
        String sessionId = body.get("session_id") != null ? String.valueOf(body.get("session_id"))
                : body.get("sessionId") != null ? String.valueOf(body.get("sessionId")) : null;
        String requirement = body.get("requirement") != null ? String.valueOf(body.get("requirement"))
                : body.get("question") != null ? String.valueOf(body.get("question")) : null;
        return ok(productOntologyService.copyAsDraft(offeringId, text, sessionId, requirement));
    }

    /** 智读：选择文件后立即上传，返回 file_id 供发送时映射 */
    @Operation(summary = "上传配置文件", description = "智读：选择文件后立即上传，返回 file_id 供发送时映射")
    @PostMapping(value = "/config/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadConfigFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            String name = file == null ? "null" : file.getOriginalFilename();
            long size = file == null ? -1L : file.getSize();
            org.slf4j.LoggerFactory.getLogger(ProductOntologyController.class)
                    .warn("[智读上传] 空文件 part: name={}, size={}, empty={}", name, size, file != null && file.isEmpty());
            throw new IllegalArgumentException("file is required");
        }
        org.slf4j.LoggerFactory.getLogger(ProductOntologyController.class)
                .info("[智读上传] name={}, size={}", file.getOriginalFilename(), file.getSize());
        Map<String, Object> stored = productOntologyService.uploadConfigDocument(file);
        org.slf4j.LoggerFactory.getLogger(ProductOntologyController.class)
                .info("[智读上传] 成功 fileId={}, fileName={}", stored.get("fileId"), stored.get("fileName"));
        return ok(stored);
    }

    /** 智读：按 file_id 下载原文件（消息附件可下载；fileName 支持原文件名回显/落盘） */
    @Operation(summary = "下载配置文件", description = "智读：按 file_id 下载原文件（消息附件可下载）")
    @GetMapping("/config/files/{fileId}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadConfigFile(
            @PathVariable String fileId,
            @org.springframework.web.bind.annotation.RequestParam(value = "fileName", required = false) String fileName) {
        var storage = productOntologyService.documentStorage();
        java.nio.file.Path path = storage.resolve(fileId);
        String downloadName = (fileName == null || fileName.isBlank())
                ? path.getFileName().toString()
                : fileName;
        String encoded = java.net.URLEncoder.encode(downloadName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        org.springframework.core.io.Resource resource =
                new org.springframework.core.io.FileSystemResource(path);
        String mime = "application/octet-stream";
        try {
            String probed = java.nio.file.Files.probeContentType(path);
            if (probed != null && !probed.isBlank()) {
                mime = probed;
            }
        } catch (java.io.IOException ignored) {
        }
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, mime)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .body(resource);
    }

    /** 智读：按已上传 file_id 解析并批量映射（发送消息时调用） */
    @Operation(summary = "按文件批量映射", description = "智读：按已上传 file_id 解析并批量映射（发送消息时调用）")
    @PostMapping("/config/batch-by-file")
    public Map<String, Object> batchByFile(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String fileId = body.get("file_id") != null ? String.valueOf(body.get("file_id"))
                : body.get("fileId") != null ? String.valueOf(body.get("fileId")) : null;
        String fileName = body.get("fileName") != null ? String.valueOf(body.get("fileName"))
                : body.get("file_name") != null ? String.valueOf(body.get("file_name"))
                : body.get("filename") != null ? String.valueOf(body.get("filename")) : null;
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("file_id is required");
        }
        return ok(productOntologyService.batchFromUploadedFile(fileId, fileName));
    }

    /** 智读：上传 Word/PDF/Excel 等文件后批量映射（兼容旧入口：上传+映射一步完成） */
    @Operation(summary = "上传并批量映射", description = "智读：上传 Word/PDF/Excel 等文件后批量映射（兼容旧入口）")
    @PostMapping("/config/batch-upload")
    public Map<String, Object> batchUpload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        return ok(productOntologyService.batchFromDocumentBytes(file.getBytes(), file.getOriginalFilename()));
    }

    /** 知识自迭代：合规草稿沉淀至本体/事实图 */
    @Operation(summary = "沉淀配置草稿", description = "知识自迭代：合规草稿沉淀至本体/事实图")
    @PostMapping("/config/publish")
    public Map<String, Object> publish(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = body.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) body.get("draft")
                : body;
        return ok(productOntologyService.publishConfigDraft(draft));
    }

    /** 配置草稿持久化（刷新可恢复） */
    @Operation(summary = "草稿列表", description = "配置草稿持久化列表（刷新可恢复），可按会话/用户/状态过滤")
    @GetMapping("/config/drafts")
    public Map<String, Object> listDrafts(
            @RequestParam(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "status", required = false) String status
    ) {
        return ok(productOntologyService.listConfigDrafts(sessionId, userId, status));
    }

    @Operation(summary = "草稿详情", description = "按 ID 查询配置草稿")
    @GetMapping("/config/drafts/{draftId}")
    public Map<String, Object> getDraft(@PathVariable("draftId") Long draftId) {
        return ok(productOntologyService.getConfigDraft(draftId));
    }

    @Operation(summary = "保存草稿", description = "新建配置草稿")
    @PostMapping("/config/drafts")
    public Map<String, Object> saveDraft(@RequestBody(required = false) Map<String, Object> request) {
        return ok(productOntologyService.saveConfigDraft(request));
    }

    @Operation(summary = "更新草稿", description = "更新指定 ID 的配置草稿")
    @PutMapping("/config/drafts/{draftId}")
    public Map<String, Object> updateDraft(
            @PathVariable("draftId") Long draftId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        Map<String, Object> body = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        body.put("draftId", draftId);
        return ok(productOntologyService.saveConfigDraft(body));
    }

    @Operation(summary = "删除草稿", description = "删除指定 ID 的配置草稿")
    @DeleteMapping("/config/drafts/{draftId}")
    public Map<String, Object> deleteDraft(@PathVariable("draftId") Long draftId) {
        return ok(productOntologyService.deleteConfigDraft(draftId));
    }

    /** 智检通过后提交：沉淀本体 + 生成工单 */
    @Operation(summary = "提交草稿", description = "智检通过后提交：沉淀本体 + 生成工单")
    @PostMapping("/config/submit")
    public Map<String, Object> submitDraft(@RequestBody(required = false) Map<String, Object> request) {
        return ok(productOntologyService.submitConfigDraft(request));
    }

    /** 多方案对比（合规 + 收益估算 + 推荐说明） */
    @Operation(summary = "多方案对比", description = "配置多方案对比（合规 + 收益估算 + 推荐说明）")
    @PostMapping("/config/compare")
    public Map<String, Object> compareSchemes(@RequestBody(required = false) Map<String, Object> request) {
        return ok(productOntologyService.compareConfigSchemes(request));
    }

    @Operation(summary = "配置追溯", description = "按 trace_id 查询配置全链路追溯")
    @GetMapping("/config/trace")
    public Map<String, Object> configTrace(@RequestParam("trace_id") String traceId) {
        return ok(productOntologyService.getConfigTrace(traceId));
    }

    /** P3-6 溯源链回放：回答"字段默认值为什么是 X"（PROV-O derivedFrom）。 */
    @Operation(summary = "字段溯源", description = "P3-6 溯源链回放：回答「字段默认值为什么是 X」（PROV-O derivedFrom）")
    @GetMapping("/config/provenance/{field}")
    public Map<String, Object> fieldProvenance(@PathVariable("field") String field) {
        return ok(productOntologyService.explainFieldDefault(field));
    }

    @Operation(summary = "配置解释", description = "按 trace_id 生成业务/技术视角的配置解释")
    @PostMapping("/config/explain")
    public Map<String, Object> configExplain(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String traceId = body.get("trace_id") != null ? String.valueOf(body.get("trace_id"))
                : body.get("traceId") != null ? String.valueOf(body.get("traceId")) : "";
        String audience = body.get("audience") != null ? String.valueOf(body.get("audience")) : "business";
        return ok(productOntologyService.explainConfig(traceId, audience));
    }

    @Operation(summary = "运营看板", description = "返回运营总览看板数据")
    @GetMapping("/ops/dashboard")
    public Map<String, Object> dashboard() {
        return productOntologyService.getOpsDashboard();
    }

    @Operation(summary = "收入总览", description = "返回运营收入总览数据")
    @GetMapping("/ops/revenue-overview")
    public Map<String, Object> revenueOverview() {
        return ok(productOntologyService.getOpsRevenueOverview());
    }

    @Operation(summary = "根因分析", description = "对指定商品或文本进行运营根因分析")
    @PostMapping("/ops/root-cause")
    public Map<String, Object> rootCause(@RequestBody(required = false) RootCauseRequest request) {
        RootCauseRequest safe = request == null ? new RootCauseRequest() : request;
        return ok(productOntologyService.analyzeRootCause(safe.getOfferingId(), safe.getText()));
    }

    @Operation(summary = "风险稽核", description = "对商品清单批量执行风险稽核")
    @PostMapping("/ops/risk-audit")
    public Map<String, Object> riskAudit(@RequestBody(required = false) RiskAuditRequest request) {
        RiskAuditRequest safe = request == null ? new RiskAuditRequest() : request;
        return productOntologyService.auditRisks(safe.getOfferingIds());
    }

    @Operation(summary = "查询风险规则", description = "返回当前风险规则阈值配置")
    @GetMapping("/ops/risk-rules")
    public Map<String, Object> getRiskRules() {
        return ok(productOntologyService.updateRiskRules(null));
    }

    @Operation(summary = "运营规则清单", description = "返回运营规则目录")
    @GetMapping("/ops/rules")
    public Map<String, Object> getOpsRules() {
        return ok(productOntologyService.getOpsRulesCatalog());
    }

    /** 热重载 classpath/文件侧 ops_rules.json（内存覆盖阈值保留）。 */
    @Operation(summary = "重载运营规则", description = "热重载 classpath/文件侧 ops_rules.json（内存覆盖阈值保留）")
    @PostMapping("/ops/rules/reload")
    public Map<String, Object> reloadOpsRules() {
        return ok(productOntologyService.reloadOpsRules());
    }

    @Operation(summary = "更新风险规则", description = "更新风险规则阈值（部分字段可选）")
    @PostMapping("/ops/risk-rules")
    public Map<String, Object> updateRiskRules(@RequestBody(required = false) RiskRulesRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request != null) {
            if (request.getZeroSalesShelfDays() != null) {
                payload.put("zeroSalesShelfDays", request.getZeroSalesShelfDays());
            }
            if (request.getZeroSalesDaysWindow() != null) {
                payload.put("zeroSalesDaysWindow", request.getZeroSalesDaysWindow());
            }
            if (request.getHighRiskReviewDays() != null) {
                payload.put("highRiskReviewDays", request.getHighRiskReviewDays());
            }
            if (request.getLowRevenuePercentile() != null) {
                payload.put("lowRevenuePercentile", request.getLowRevenuePercentile());
            }
            if (request.getRuleVersion() != null) {
                payload.put("ruleVersion", request.getRuleVersion());
            }
        }
        return ok(productOntologyService.updateRiskRules(payload));
    }

    @Operation(summary = "重置风险规则", description = "恢复风险规则默认阈值")
    @PostMapping("/ops/risk-rules/reset")
    public Map<String, Object> resetRiskRules() {
        return ok(productOntologyService.resetRiskRules());
    }

    /**
     * 立项/策略多方案对比（原 {@code /api/v1/product-ops/compare}）。
     * body: { snapshot_id?, patches, policy_set_id?, current_facts?, trace_id?, tenant_id? }
     */
    @Operation(summary = "策略状态对比", description = "立项/策略多方案对比（快照 + patches 推演）")
    @PostMapping("/ops/compare")
    public Map<String, Object> comparePolicyState(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String snapshotId = strOrNull(body.get("snapshot_id"), body.get("snapshotId"));
        List<Map<String, Object>> patches = castListMap(body.get("patches"));
        String policySetId = strOr(
                body.get("policy_set_id") != null ? body.get("policy_set_id") : body.get("policySetId"),
                "PS_PRODUCT_ONLINE_V1");
        String traceId = strOr(
                body.get("trace_id") != null ? body.get("trace_id") : body.get("traceId"),
                "product-compare-trace");
        String tenantId = strOr(
                body.get("tenant_id") != null ? body.get("tenant_id") : body.get("tenantId"),
                "product_ops");
        Map<String, Object> inlineFacts = null;
        Object factsRaw = body.get("current_facts") != null ? body.get("current_facts") : body.get("facts");
        if (factsRaw instanceof Map<?, ?>) {
            inlineFacts = castMap(factsRaw);
        }
        return ok(ontologyService.compareState(snapshotId, patches, policySetId, traceId, tenantId, inlineFacts));
    }

    /**
     * 假设推演：退市 / 改价后重跑风险稽核（不写回事实图）。
     * body: { mode: "delist"|"price"|"risk", patches: [{offeringId, changes, description?}] }
     */
    @Operation(summary = "假设推演", description = "退市/改价后重跑风险稽核（不写回事实图）")
    @PostMapping("/ops/hypothetical")
    public Map<String, Object> hypothetical(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        String mode = safe.get("mode") != null ? String.valueOf(safe.get("mode")) : "delist";
        List<Map<String, Object>> patches = new ArrayList<>();
        if (safe.get("patches") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    patches.add(row);
                }
            }
        } else if (safe.get("offeringId") != null) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("offeringId", String.valueOf(safe.get("offeringId")));
            if (safe.get("changes") instanceof Map<?, ?> ch) {
                Map<String, Object> changes = new LinkedHashMap<>();
                ch.forEach((k, v) -> changes.put(String.valueOf(k), v));
                one.put("changes", changes);
            } else if ("price".equalsIgnoreCase(mode)) {
                one.put("changes", Map.of("monthlyFee", safe.getOrDefault("monthlyFee", 19)));
            } else {
                one.put("changes", Map.of("state", "下架"));
            }
            patches.add(one);
        }
        return ok(productOntologyService.evaluateHypothetical(patches, mode));
    }

    @Operation(summary = "运营告警列表", description = "返回运营告警，可按商品过滤")
    @GetMapping("/ops/alerts")
    public Map<String, Object> listAlerts(@RequestParam(value = "offering_id", required = false) String offeringId) {
        return productOntologyService.listOpsAlerts(offeringId);
    }

    @Operation(summary = "工单列表", description = "分页查询配置工单，支持状态/会话/关键字过滤")
    @GetMapping("/ops/work-orders")
    public Map<String, Object> listWorkOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "q", required = false) String q
    ) {
        return productOntologyService.listWorkOrders(status, sessionId, page, size, q);
    }

    @Operation(summary = "创建工单", description = "创建配置工单")
    @PostMapping("/ops/work-orders")
    public Map<String, Object> createWorkOrder(@RequestBody(required = false) Map<String, Object> body) {
        return ok(productOntologyService.createWorkOrder(body));
    }

    /** 工单状态流转：open → in_progress → done / cancelled */
    @Operation(summary = "工单状态流转", description = "open → in_progress → done / cancelled")
    @PutMapping("/ops/work-orders/{workOrderId}")
    public Map<String, Object> updateWorkOrder(
            @PathVariable("workOrderId") String workOrderId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        String status = safe.get("status") != null ? String.valueOf(safe.get("status")) : "";
        String remark = safe.get("remark") != null ? String.valueOf(safe.get("remark")) : null;
        return productOntologyService.updateWorkOrderStatus(workOrderId, status, remark);
    }

    /** 手动触发全量风险批量稽核（对齐方案每日定时筛查）。 */
    @Operation(summary = "触发批量稽核", description = "手动触发全量风险批量稽核（对齐每日定时筛查）")
    @PostMapping("/ops/batch-audit")
    public Map<String, Object> batchAudit(@RequestBody(required = false) Map<String, Object> body) {
        String trigger = body != null && body.get("trigger") != null
                ? String.valueOf(body.get("trigger")) : "manual";
        return productOntologyService.runBatchRiskAudit(trigger);
    }

    @Operation(summary = "最近批量稽核结果", description = "返回最近一次批量稽核报告")
    @GetMapping("/ops/batch-audit")
    public Map<String, Object> lastBatchAudit() {
        return productOntologyService.getLastBatchAudit();
    }

    /** 查询热度分析（C4）：会话审计数据聚合高频查询词 → 商品运营洞察（只聚合不落明细）。 */
    @Operation(summary = "查询热度分析", description = "会话审计数据聚合高频查询词 → 商品运营洞察（只聚合不落明细）")
    @GetMapping("/ops/query-heat")
    public Map<String, Object> queryHeat(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "days", required = false) Integer days
    ) {
        return ok(queryHeatService.queryHeat(limit, days));
    }

    private Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private List<Map<String, Object>> castListMap(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                out.add(castMap(item));
            }
        }
        return out;
    }

    private String strOr(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        String s = String.valueOf(value);
        return s.isBlank() || "null".equals(s) ? defaultValue : s;
    }

    private String strOrNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value == null) continue;
            String s = String.valueOf(value);
            if (!s.isBlank() && !"null".equals(s)) return s;
        }
        return null;
    }
}
