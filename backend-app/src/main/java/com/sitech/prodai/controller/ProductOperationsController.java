package com.sitech.prodai.controller;

import com.sitech.prodai.service.OntologyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/product-ops")
public class ProductOperationsController {

    private final OntologyService ontologyService;

    public ProductOperationsController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return Map.of(
                "success", true,
                "policySets", ontologyService.getPolicySets(),
                "ontologyStats", ontologyService.getOntologyStats(),
                "graph", ontologyService.getOntologyGraph()
        );
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam("q") String question) {
        return ontologyService.nlQuery(question);
    }

    @PostMapping("/product-online/check")
    public Map<String, Object> productOnlineCheck(@RequestBody Map<String, Object> request) {
        Map<String, Object> facts = castMap(request.get("facts"));
        String traceId = str(request.getOrDefault("trace_id", "product-online-trace"));
        String tenantId = str(request.getOrDefault("tenant_id", "product_ops"));
        return ontologyService.evaluate(facts, "PS_PRODUCT_ONLINE_V1", "candidate_check", traceId, tenantId);
    }

    @PostMapping("/product-risk/audit")
    public Map<String, Object> productRiskAudit(@RequestBody Map<String, Object> request) {
        List<Map<String, Object>> entities = castListMap(request.get("entities"));
        String traceId = str(request.getOrDefault("trace_id", "product-risk-trace"));
        String tenantId = str(request.getOrDefault("tenant_id", "product_ops"));
        return ontologyService.evaluateWithFacts(entities, "PS_PRODUCT_RISK_V1", traceId, tenantId);
    }

    @PostMapping("/compare")
    public Map<String, Object> compare(@RequestBody Map<String, Object> request) {
        String snapshotId = str(request.get("snapshot_id"));
        List<Map<String, Object>> patches = castListMap(request.get("patches"));
        String traceId = str(request.getOrDefault("trace_id", "product-compare-trace"));
        String tenantId = str(request.getOrDefault("tenant_id", "product_ops"));
        String policySetId = str(request.getOrDefault("policy_set_id", "PS_PRODUCT_ONLINE_V1"));
        return ontologyService.compareState(snapshotId, patches, policySetId, traceId, tenantId);
    }

    @PostMapping("/explain")
    public Map<String, Object> explain(@RequestBody Map<String, Object> request) {
        return ontologyService.explain(str(request.get("trace_id")), str(request.getOrDefault("audience", "business")), str(request.getOrDefault("tenant_id", "product_ops")));
    }

    @PostMapping("/nl-discover")
    public Map<String, Object> nlDiscover(@RequestBody Map<String, Object> request) {
        return ontologyService.nlDiscoverAndRetrieve(str(request.get("question")), intOrDefault(request.get("max_entities"), 10));
    }

    @GetMapping("/trace")
    public Map<String, Object> trace(@RequestParam("trace_id") String traceId) {
        return ontologyService.getTrace(traceId);
    }

    @GetMapping("/form-constraint")
    public Map<String, Object> formConstraint(@RequestParam("form_code") String formCode) {
        return ontologyService.getFormConstraint(formCode);
    }

    @GetMapping("/ontology/instances")
    public Map<String, Object> getOntologyInstances() {
        return ontologyService.getOntologyInstances();
    }

    @PostMapping("/ontology/instances")
    public Map<String, Object> createOntologyInstance(@RequestBody Map<String, Object> request) {
        String uri = str(request.get("uri"));
        String type = str(request.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = (Map<String, Object>) request.getOrDefault("facts", Map.of());
        return ontologyService.createOntologyInstance(uri, type, facts);
    }

    @PutMapping("/ontology/instances/{uri}")
    public Map<String, Object> updateOntologyInstance(
            @PathVariable("uri") String uri,
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = (Map<String, Object>) request.getOrDefault("facts", Map.of());
        return ontologyService.updateOntologyInstance(uri, facts);
    }

    @DeleteMapping("/ontology/instances/{uri}")
    public Map<String, Object> deleteOntologyInstance(@PathVariable("uri") String uri) {
        return ontologyService.deleteOntologyInstance(uri);
    }

    @GetMapping("/ontology/graph")
    public Map<String, Object> getOntologyGraph() {
        return ontologyService.getOntologyGraph();
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().collect(java.util.stream.Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue, (a, b) -> b, java.util.LinkedHashMap::new));
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListMap(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(item -> item instanceof Map<?, ?>).map(item -> castMap(item)).toList();
        }
        return List.of();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intOrDefault(Object value, int defaultValue) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return defaultValue; }
    }
}
