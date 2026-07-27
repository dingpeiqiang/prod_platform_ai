package com.sitech.prodai.controller;

import com.sitech.prodai.dto.BatchDocumentRequest;
import com.sitech.prodai.dto.ChatConfigureRequest;
import com.sitech.prodai.dto.ComplianceRequest;
import com.sitech.prodai.dto.InferRequest;
import com.sitech.prodai.dto.RiskAuditRequest;
import com.sitech.prodai.dto.RiskRulesRequest;
import com.sitech.prodai.dto.RootCauseRequest;
import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.ProductOntologyService;
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
@RestController
@RequestMapping("/api/v1/product-ontology")
public class ProductOntologyController {

    private final ProductOntologyService productOntologyService;
    private final OntologyService ontologyService;

    public ProductOntologyController(
            ProductOntologyService productOntologyService,
            OntologyService ontologyService
    ) {
        this.productOntologyService = productOntologyService;
        this.ontologyService = ontologyService;
    }

    private Map<String, Object> ok(Map<String, Object> body) {
        return productOntologyService.withModeMeta(body);
    }

    @GetMapping("/graph")
    public Map<String, Object> graph() {
        return productOntologyService.getGraphSummary();
    }

    @PostMapping("/graph/reload")
    public Map<String, Object> reloadGraph() {
        productOntologyService.reloadGraph();
        return ok(Map.of(
                "success", true,
                "message", "graph reloaded"
        ));
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return productOntologyService.getOntologyMeta();
    }

    @PostMapping("/config/infer")
    public Map<String, Object> infer(@RequestBody(required = false) InferRequest request) {
        InferRequest safe = request == null ? new InferRequest() : request;
        return ok(productOntologyService.inferFields(safe.getSlots(), safe.getDraft()));
    }

    @PostMapping("/config/compliance")
    public Map<String, Object> compliance(@RequestBody(required = false) ComplianceRequest request) {
        ComplianceRequest safe = request == null ? new ComplianceRequest() : request;
        return ok(productOntologyService.checkComplianceSmart(
                safe.getOfferingId(), safe.getText(), safe.getDraft()));
    }

    @PostMapping("/config/chat")
    public Map<String, Object> chatConfigure(@RequestBody ChatConfigureRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        return ok(productOntologyService.chatConfigure(request.getText(), request.getDraft()));
    }

    @PostMapping("/config/batch")
    public Map<String, Object> batch(@RequestBody(required = false) BatchDocumentRequest request) {
        BatchDocumentRequest safe = request == null ? new BatchDocumentRequest() : request;
        return ok(productOntologyService.batchFromDocument(safe.getDocumentText(), safe.getPackages()));
    }

    /** 智查：语义/关键词发现历史配置方案 */
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

    /** 一键复制为配置草稿并合规校验 */
    @PostMapping("/config/copy-as-draft")
    public Map<String, Object> copyAsDraft(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String offeringId = body.get("offering_id") != null ? String.valueOf(body.get("offering_id"))
                : body.get("offeringId") != null ? String.valueOf(body.get("offeringId")) : null;
        String text = body.get("text") != null ? String.valueOf(body.get("text")) : null;
        return ok(productOntologyService.copyAsDraft(offeringId, text));
    }

    /** 智读：选择文件后立即上传，返回 file_id 供发送时映射 */
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

    /** 智读：按已上传 file_id 解析并批量映射（发送消息时调用） */
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
    @PostMapping("/config/batch-upload")
    public Map<String, Object> batchUpload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        return ok(productOntologyService.batchFromDocumentBytes(file.getBytes(), file.getOriginalFilename()));
    }

    /** 知识自迭代：合规草稿沉淀至本体/事实图 */
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
    @GetMapping("/config/drafts")
    public Map<String, Object> listDrafts(
            @RequestParam(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "status", required = false) String status
    ) {
        return ok(productOntologyService.listConfigDrafts(sessionId, userId, status));
    }

    @GetMapping("/config/drafts/{draftId}")
    public Map<String, Object> getDraft(@PathVariable("draftId") Long draftId) {
        return ok(productOntologyService.getConfigDraft(draftId));
    }

    @PostMapping("/config/drafts")
    public Map<String, Object> saveDraft(@RequestBody(required = false) Map<String, Object> request) {
        return ok(productOntologyService.saveConfigDraft(request));
    }

    @PutMapping("/config/drafts/{draftId}")
    public Map<String, Object> updateDraft(
            @PathVariable("draftId") Long draftId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        Map<String, Object> body = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        body.put("draftId", draftId);
        return ok(productOntologyService.saveConfigDraft(body));
    }

    @DeleteMapping("/config/drafts/{draftId}")
    public Map<String, Object> deleteDraft(@PathVariable("draftId") Long draftId) {
        return ok(productOntologyService.deleteConfigDraft(draftId));
    }

    /** 智检通过后提交：沉淀本体 + 资费备案工单 */
    @PostMapping("/config/submit")
    public Map<String, Object> submitDraft(@RequestBody(required = false) Map<String, Object> request) {
        return ok(productOntologyService.submitConfigDraft(request));
    }

    /** 多方案对比（合规 + 收益估算 + 推荐说明） */
    @PostMapping("/config/compare")
    public Map<String, Object> compareSchemes(@RequestBody(required = false) Map<String, Object> request) {
        return ok(productOntologyService.compareConfigSchemes(request));
    }

    @GetMapping("/config/trace")
    public Map<String, Object> configTrace(@RequestParam("trace_id") String traceId) {
        return ok(productOntologyService.getConfigTrace(traceId));
    }

    @PostMapping("/config/explain")
    public Map<String, Object> configExplain(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String traceId = body.get("trace_id") != null ? String.valueOf(body.get("trace_id"))
                : body.get("traceId") != null ? String.valueOf(body.get("traceId")) : "";
        String audience = body.get("audience") != null ? String.valueOf(body.get("audience")) : "business";
        return ok(productOntologyService.explainConfig(traceId, audience));
    }

    @GetMapping("/ops/dashboard")
    public Map<String, Object> dashboard() {
        return productOntologyService.getOpsDashboard();
    }

    @PostMapping("/ops/root-cause")
    public Map<String, Object> rootCause(@RequestBody(required = false) RootCauseRequest request) {
        RootCauseRequest safe = request == null ? new RootCauseRequest() : request;
        return ok(productOntologyService.analyzeRootCause(safe.getOfferingId(), safe.getText()));
    }

    @PostMapping("/ops/risk-audit")
    public Map<String, Object> riskAudit(@RequestBody(required = false) RiskAuditRequest request) {
        RiskAuditRequest safe = request == null ? new RiskAuditRequest() : request;
        return productOntologyService.auditRisks(safe.getOfferingIds());
    }

    @GetMapping("/ops/risk-rules")
    public Map<String, Object> getRiskRules() {
        return ok(productOntologyService.updateRiskRules(null));
    }

    @GetMapping("/ops/rules")
    public Map<String, Object> getOpsRules() {
        return ok(productOntologyService.getOpsRulesCatalog());
    }

    /** 热重载 classpath/文件侧 ops_rules.json（内存覆盖阈值保留）。 */
    @PostMapping("/ops/rules/reload")
    public Map<String, Object> reloadOpsRules() {
        return ok(productOntologyService.reloadOpsRules());
    }

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

    @PostMapping("/ops/risk-rules/reset")
    public Map<String, Object> resetRiskRules() {
        return ok(productOntologyService.resetRiskRules());
    }

    /**
     * 立项/策略多方案对比（原 {@code /api/v1/product-ops/compare}）。
     * body: { snapshot_id?, patches, policy_set_id?, current_facts?, trace_id?, tenant_id? }
     */
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

    @GetMapping("/ops/alerts")
    public Map<String, Object> listAlerts(@RequestParam(value = "offering_id", required = false) String offeringId) {
        return productOntologyService.listOpsAlerts(offeringId);
    }

    @GetMapping("/ops/work-orders")
    public Map<String, Object> listWorkOrders(@RequestParam(value = "status", required = false) String status) {
        return productOntologyService.listWorkOrders(status);
    }

    @PostMapping("/ops/work-orders")
    public Map<String, Object> createWorkOrder(@RequestBody(required = false) Map<String, Object> body) {
        return ok(productOntologyService.createWorkOrder(body));
    }

    /** 工单状态流转：open → in_progress → done / cancelled */
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
    @PostMapping("/ops/batch-audit")
    public Map<String, Object> batchAudit(@RequestBody(required = false) Map<String, Object> body) {
        String trigger = body != null && body.get("trigger") != null
                ? String.valueOf(body.get("trigger")) : "manual";
        return productOntologyService.runBatchRiskAudit(trigger);
    }

    @GetMapping("/ops/batch-audit")
    public Map<String, Object> lastBatchAudit() {
        return productOntologyService.getLastBatchAudit();
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
