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
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ontology-mvp")
public class OntologyMvpController {

    private final OntologyMvpService ontologyMvpService;

    public OntologyMvpController(OntologyMvpService ontologyMvpService) {
        this.ontologyMvpService = ontologyMvpService;
    }

    @GetMapping("/graph")
    public Map<String, Object> graph() {
        return ontologyMvpService.getGraphSummary();
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return ontologyMvpService.getOntologyMeta();
    }

    @PostMapping("/config/infer")
    public Map<String, Object> infer(@RequestBody(required = false) InferRequest request) {
        InferRequest safe = request == null ? new InferRequest() : request;
        return ontologyMvpService.inferFields(safe.getSlots(), safe.getDraft());
    }

    @PostMapping("/config/compliance")
    public Map<String, Object> compliance(@RequestBody ComplianceRequest request) {
        if (request == null || request.getDraft() == null) {
            throw new IllegalArgumentException("draft is required");
        }
        return ontologyMvpService.checkCompliance(request.getDraft());
    }

    @PostMapping("/config/chat")
    public Map<String, Object> chatConfigure(@RequestBody ChatConfigureRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        return ontologyMvpService.chatConfigure(request.getText(), request.getDraft());
    }

    @PostMapping("/config/batch")
    public Map<String, Object> batch(@RequestBody(required = false) BatchDocumentRequest request) {
        BatchDocumentRequest safe = request == null ? new BatchDocumentRequest() : request;
        return ontologyMvpService.batchFromDocument(safe.getDocumentText(), safe.getPackages());
    }

    @GetMapping("/ops/dashboard")
    public Map<String, Object> dashboard() {
        return ontologyMvpService.getOpsDashboard();
    }

    @PostMapping("/ops/root-cause")
    public Map<String, Object> rootCause(@RequestBody(required = false) RootCauseRequest request) {
        RootCauseRequest safe = request == null ? new RootCauseRequest() : request;
        return ontologyMvpService.analyzeRootCause(safe.getOfferingId());
    }

    @PostMapping("/ops/risk-audit")
    public Map<String, Object> riskAudit(@RequestBody(required = false) RiskAuditRequest request) {
        RiskAuditRequest safe = request == null ? new RiskAuditRequest() : request;
        return ontologyMvpService.auditRisks(safe.getOfferingIds());
    }

    @GetMapping("/ops/risk-rules")
    public Map<String, Object> getRiskRules() {
        return ontologyMvpService.updateRiskRules(null);
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
        return ontologyMvpService.updateRiskRules(payload);
    }

    @PostMapping("/ops/risk-rules/reset")
    public Map<String, Object> resetRiskRules() {
        return ontologyMvpService.resetRiskRules();
    }
}
