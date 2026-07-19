package com.sitech.prodai.controller;

import com.sitech.prodai.service.OntologyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class OntologyMvpController {

    private final OntologyService ontologyService;

    public OntologyMvpController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @PostMapping("/facts/retrieve")
    public Map<String, Object> retrieveFacts(@RequestBody Map<String, Object> request) {
        Map<String, Object> entity = firstEntity(request);
        return ontologyService.retrieve(
                asString(entity.get("id"), "Customer_Li"),
                asString(entity.get("type"), "Customer"),
                asString(entity.get("source"), "ontology"),
                asString(trace(request).get("tenant_id"), "marketing_tenant"),
                asString(trace(request).get("trace_id"), "")
        );
    }

    @PostMapping("/policy/evaluate")
    public Map<String, Object> evaluatePolicy(@RequestBody Map<String, Object> request) {
        Map<String, Object> facts = castMap(request.getOrDefault("facts", Map.of()));
        Map<String, Object> context = castMap(request.getOrDefault("context", Map.of()));
        String traceId = trace(request).containsKey("trace_id") ? asString(trace(request).get("trace_id"), "") : asString(trace(request).get("trace_id"), "");
        String tenantId = asString(trace(request).get("tenant_id"), "marketing_tenant");
        return ontologyService.evaluate(
                facts,
                asString(context.get("policy_set_id"), "UNKNOWN"),
                asString(context.get("expectation_type"), "validation"),
                traceId,
                tenantId
        );
    }

    @PostMapping("/evaluate")
    public Map<String, Object> evaluateWithFacts(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) request.getOrDefault("entities", List.of());
        Map<String, Object> trace = trace(request);
        return ontologyService.evaluateWithFacts(
                entities,
                asString(request.get("policy_set_id"), "UNKNOWN"),
                asString(trace.get("trace_id"), ""),
                asString(trace.get("tenant_id"), "marketing_tenant")
        );
    }

    @PostMapping("/compare-state")
    public Map<String, Object> compareState(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patches = (List<Map<String, Object>>) request.getOrDefault("patches", List.of());
        Map<String, Object> trace = trace(request);
        return ontologyService.compareState(
                asString(request.get("base_snapshot_id"), ""),
                patches,
                asString(request.get("policy_set_id"), "UNKNOWN"),
                asString(trace.get("trace_id"), ""),
                asString(trace.get("tenant_id"), "marketing_tenant")
        );
    }

    @PostMapping("/swrl/evaluate")
    public Map<String, Object> evaluateSwrl(@RequestBody Map<String, Object> request) {
        return Map.of("results", List.of(), "fired_rule_ids", List.of(), "rules", List.of());
    }

    @PostMapping("/shacl/validate")
    public Map<String, Object> validateShacl(@RequestBody Map<String, Object> request) {
        Map<String, Object> data = castMap(request.getOrDefault("data", Map.of()));
        boolean emailInvalid = data.containsKey("email") && !String.valueOf(data.get("email")).contains("@");
        if (emailInvalid) {
            return Map.of("conforms", false, "results", List.of(Map.of("severity", "violation", "path", "email", "message", "邮箱格式不正确", "source_shape", asString(request.get("shapes"), "default"))));
        }
        return Map.of("conforms", true, "results", List.of());
    }

    @PostMapping("/hypothetical/evaluate")
    public Map<String, Object> hypotheticalEvaluate(@RequestBody Map<String, Object> request) {
        return Map.of("facts", Map.of(), "decision", Map.of("verdict", "review", "confidence", 0.7, "triggered_rules", List.of("R000"), "reason", "假设推演结果"));
    }

    @GetMapping("/schema")
    public Map<String, Object> schema() {
        return ontologyService.schema();
    }

    @GetMapping("/schema/catalog")
    public Map<String, Object> schemaCatalog() {
        return ontologyService.schemaCatalog();
    }

    @PostMapping("/schema/detail")
    public Map<String, Object> schemaDetail(@RequestBody Map<String, Object> request) {
        return ontologyService.schemaDetail(asString(request.get("class_name"), "Customer"));
    }

    @PostMapping("/sparql/query")
    public Map<String, Object> sparqlQuery(@RequestBody Map<String, Object> request) {
        return ontologyService.sparqlQuery(asString(request.get("query"), "SELECT * WHERE { ?s ?p ?o } LIMIT 100"));
    }

    @PostMapping("/nl/query")
    public Map<String, Object> nlQuery(@RequestBody Map<String, Object> request) {
        return ontologyService.nlQuery(asString(request.get("question"), ""));
    }

    @PostMapping("/nl-discover")
    public Map<String, Object> nlDiscover(@RequestBody Map<String, Object> request) {
        return ontologyService.nlDiscoverAndRetrieve(
                asString(request.get("question"), ""),
                request.get("max_entities") instanceof Number n ? n.intValue() : 5
        );
    }

    @PostMapping("/quick-evaluate")
    public Map<String, Object> quickEvaluate(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) request.getOrDefault("entities", List.of());
        String entityId = "Customer_Li";
        String type = "Customer";
        if (!entities.isEmpty()) {
            entityId = asString(entities.get(0).get("id"), "Customer_Li");
            type = asString(entities.get(0).get("type"), "Customer");
        }
        return ontologyService.quickEvaluate(
                entityId, type,
                asString(request.get("policy_set_id"), "UNKNOWN"),
                asString(request.get("tenant_id"), "marketing_tenant")
        );
    }

    @GetMapping("/policy/sets")
    public Map<String, Object> policySets() {
        return ontologyService.getPolicySets();
    }

    @GetMapping("/swrl/rules")
    public Map<String, Object> swrlRules() {
        return ontologyService.getSwrlRules();
    }

    @PostMapping("/explain")
    public Map<String, Object> explain(@RequestBody Map<String, Object> request) {
        return ontologyService.explain(asString(request.get("trace_id"), ""), asString(request.get("audience"), "end_user"), asString(request.get("tenant_id"), "marketing_tenant"));
    }

    @GetMapping("/trace/{traceId}")
    public Map<String, Object> trace(@PathVariable String traceId) {
        return ontologyService.getTrace(traceId);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    private static Map<String, Object> firstEntity(Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) request.getOrDefault("entities", List.of());
        if (!entities.isEmpty()) {
            return entities.get(0);
        }
        return Map.of("id", request.getOrDefault("entity_id", "Customer_Li"), "type", request.getOrDefault("entity_type", "Customer"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> trace(Map<String, Object> request) {
        Object trace = request.get("trace_context");
        return trace instanceof Map<?, ?> map ? castMap(map) : Map.of();
    }

    private static String asString(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
