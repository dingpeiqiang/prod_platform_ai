package com.sitech.prodai.controller;

import com.sitech.prodai.dto.BatchDocumentRequest;
import com.sitech.prodai.dto.ChatConfigureRequest;
import com.sitech.prodai.dto.ComplianceRequest;
import com.sitech.prodai.dto.InferRequest;
import com.sitech.prodai.dto.RiskAuditRequest;
import com.sitech.prodai.dto.RiskRulesRequest;
import com.sitech.prodai.dto.RootCauseRequest;
import com.sitech.prodai.service.OntologyMvpService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本体运营 API。响应带 {@code demoMode}/{@code dataSource}（标识当前数据配置，非另一套逻辑）。
 */
@RestController
@RequestMapping("/api/v1/ontology-mvp")
public class OntologyMvpController {

    private final OntologyMvpService ontologyMvpService;

    public OntologyMvpController(OntologyMvpService ontologyMvpService) {
        this.ontologyMvpService = ontologyMvpService;
    }

    private Map<String, Object> ok(Map<String, Object> body) {
        return ontologyMvpService.withModeMeta(body);
    }

    @GetMapping("/graph")
    public Map<String, Object> graph() {
        return ontologyMvpService.getGraphSummary();
    }

    @PostMapping("/graph/reload")
    public Map<String, Object> reloadGraph() {
        ontologyMvpService.reloadGraph();
        return ok(Map.of(
                "success", true,
                "message", "graph reloaded"
        ));
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return ontologyMvpService.getOntologyMeta();
    }

    @PostMapping("/config/infer")
    public Map<String, Object> infer(@RequestBody(required = false) InferRequest request) {
        InferRequest safe = request == null ? new InferRequest() : request;
        return ok(ontologyMvpService.inferFields(safe.getSlots(), safe.getDraft()));
    }

    @PostMapping("/config/compliance")
    public Map<String, Object> compliance(@RequestBody(required = false) ComplianceRequest request) {
        ComplianceRequest safe = request == null ? new ComplianceRequest() : request;
        return ok(ontologyMvpService.checkComplianceSmart(
                safe.getOfferingId(), safe.getText(), safe.getDraft()));
    }

    @PostMapping("/config/chat")
    public Map<String, Object> chatConfigure(@RequestBody ChatConfigureRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        return ok(ontologyMvpService.chatConfigure(request.getText(), request.getDraft()));
    }

    @PostMapping("/config/batch")
    public Map<String, Object> batch(@RequestBody(required = false) BatchDocumentRequest request) {
        BatchDocumentRequest safe = request == null ? new BatchDocumentRequest() : request;
        return ok(ontologyMvpService.batchFromDocument(safe.getDocumentText(), safe.getPackages()));
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
        return ok(ontologyMvpService.discoverConfigs(q, limit));
    }

    /** 一键复制为配置草稿并合规校验 */
    @PostMapping("/config/copy-as-draft")
    public Map<String, Object> copyAsDraft(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String offeringId = body.get("offering_id") != null ? String.valueOf(body.get("offering_id"))
                : body.get("offeringId") != null ? String.valueOf(body.get("offeringId")) : null;
        String text = body.get("text") != null ? String.valueOf(body.get("text")) : null;
        return ok(ontologyMvpService.copyAsDraft(offeringId, text));
    }

    /** 智读：上传 Word/PDF/Excel 等文件后批量映射 */
    @PostMapping("/config/batch-upload")
    public Map<String, Object> batchUpload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        return ok(ontologyMvpService.batchFromDocumentBytes(file.getBytes(), file.getOriginalFilename()));
    }

    /** 知识自迭代：合规草稿沉淀至本体/事实图 */
    @PostMapping("/config/publish")
    public Map<String, Object> publish(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = body.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) body.get("draft")
                : body;
        return ok(ontologyMvpService.publishConfigDraft(draft));
    }

    @GetMapping("/config/trace")
    public Map<String, Object> configTrace(@RequestParam("trace_id") String traceId) {
        return ok(ontologyMvpService.getConfigTrace(traceId));
    }

    @PostMapping("/config/explain")
    public Map<String, Object> configExplain(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String traceId = body.get("trace_id") != null ? String.valueOf(body.get("trace_id"))
                : body.get("traceId") != null ? String.valueOf(body.get("traceId")) : "";
        String audience = body.get("audience") != null ? String.valueOf(body.get("audience")) : "business";
        return ok(ontologyMvpService.explainConfig(traceId, audience));
    }

    @GetMapping("/ops/dashboard")
    public Map<String, Object> dashboard() {
        return ontologyMvpService.getOpsDashboard();
    }

    @PostMapping("/ops/root-cause")
    public Map<String, Object> rootCause(@RequestBody(required = false) RootCauseRequest request) {
        RootCauseRequest safe = request == null ? new RootCauseRequest() : request;
        return ok(ontologyMvpService.analyzeRootCause(safe.getOfferingId(), safe.getText()));
    }

    @PostMapping("/ops/risk-audit")
    public Map<String, Object> riskAudit(@RequestBody(required = false) RiskAuditRequest request) {
        RiskAuditRequest safe = request == null ? new RiskAuditRequest() : request;
        return ontologyMvpService.auditRisks(safe.getOfferingIds());
    }

    @GetMapping("/ops/risk-rules")
    public Map<String, Object> getRiskRules() {
        return ok(ontologyMvpService.updateRiskRules(null));
    }

    @GetMapping("/ops/rules")
    public Map<String, Object> getOpsRules() {
        return ok(ontologyMvpService.getOpsRulesCatalog());
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
        return ok(ontologyMvpService.updateRiskRules(payload));
    }

    @PostMapping("/ops/risk-rules/reset")
    public Map<String, Object> resetRiskRules() {
        return ok(ontologyMvpService.resetRiskRules());
    }
}
