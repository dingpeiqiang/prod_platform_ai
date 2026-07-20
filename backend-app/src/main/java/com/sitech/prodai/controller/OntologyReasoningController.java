package com.sitech.prodai.controller;

import org.example.engine.IntegrativeReasonEngine;
import org.example.model.Models;
import org.example.model.Models.CompareStateRequest;
import org.example.model.Models.CompareStateResponse;
import org.example.model.Models.EvaluatePolicyRequest;
import org.example.model.Models.EvaluatePolicyResponse;
import org.example.model.Models.RetrieveFactsRequest;
import org.example.model.Models.RetrieveFactsResponse;
import org.example.model.PlatformModels;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reasoning")
public class OntologyReasoningController {
    private final IntegrativeReasonEngine engine;
    public OntologyReasoningController(IntegrativeReasonEngine engine) { this.engine = engine; }

    @PostMapping("/facts/retrieve") public RetrieveFactsResponse retrieveFacts(@RequestBody RetrieveFactsRequest request) { return engine.retrieveFacts(request); }
    @PostMapping("/policy/evaluate") public EvaluatePolicyResponse evaluatePolicy(@RequestBody EvaluatePolicyRequest request) { return engine.evaluatePolicy(request); }
    @PostMapping("/evaluate") public EvaluatePolicyResponse evaluateWithFacts(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked") List<Map<String, Object>> entities = (List<Map<String, Object>>) payload.getOrDefault("entities", List.of());
        var trace = new Models.TraceContext(null, asString(payload.get("tenant_id"), "marketing_tenant"), null);
        var refs = entities.stream().map(item -> new Models.EntityRef(asString(item.get("id"), null), asString(item.get("type"), "Entity"), asString(item.get("source"), "ontology"))).toList();
        return engine.evaluatePolicyWithFacts(new RetrieveFactsRequest(refs, Map.of("scope", payload.get("scope")), trace), asString(payload.get("policy_set_id"), "UNKNOWN"));
    }
    @PostMapping("/compare-state") public CompareStateResponse compareState(@RequestBody CompareStateRequest request) { return engine.compareState(request); }
    @PostMapping("/swrl/evaluate") public PlatformModels.EvaluateSwrlResponse evaluateSwrl(@RequestBody PlatformModels.EvaluateSwrlRequest request) { return engine.evaluateSwrl(request); }
    @PostMapping("/shacl/validate") public PlatformModels.ShaclValidationResponse validateShacl(@RequestBody PlatformModels.ShaclValidationRequest request) { return engine.validateShacl(request); }
    @PostMapping("/hypothetical/evaluate") public PlatformModels.HypotheticalEvaluateResponse hypotheticalEvaluate(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked") List<String> entityIds = (List<String>) payload.getOrDefault("entity_ids", List.of());
        @SuppressWarnings("unchecked") List<Map<String, Object>> rawTriples = (List<Map<String, Object>>) payload.getOrDefault("triples", List.of());
        List<PlatformModels.HypotheticalTriple> triples = rawTriples.stream().map(m -> new PlatformModels.HypotheticalTriple(asString(m.get("subject"), null), asString(m.get("predicate"), null), asString(m.get("object"), null), asBoolean(m.get("literal"), true))).toList();
        return engine.hypotheticalEvaluate(entityIds, triples, asString(payload.get("policy_set_id"), "UNKNOWN"), asString(payload.get("tenant_id"), "default_tenant"));
    }
    @GetMapping("/schema") public PlatformModels.SchemaResponse schema() { return engine.schema(); }
    @GetMapping("/schema/catalog") public PlatformModels.CatalogResponse schemaCatalog() { return engine.schemaCatalog(); }
    @PostMapping("/schema/detail") public PlatformModels.SchemaDetailResponse schemaDetail(@RequestBody PlatformModels.SchemaDetailRequest request) { return engine.schemaDetail(request.className()); }
    @PostMapping("/sparql/query") public PlatformModels.SparqlQueryResponse sparqlQuery(@RequestBody PlatformModels.SparqlQueryRequest request) { return new PlatformModels.SparqlQueryResponse(engine.sparqlQuery(request.query())); }
    @PostMapping("/nl/query") public PlatformModels.NlQueryResponse nlQuery(@RequestBody PlatformModels.NlQueryRequest request) { return engine.nlQuery(request.question()); }
    @PostMapping("/explain") public PlatformModels.ExplainResponse explain(@RequestBody PlatformModels.ExplainRequest request) { return engine.explain(request); }
    @PostMapping("/ontology/import-ttl") public Map<String, Object> importTtl(@RequestBody Map<String, Object> payload) { return engine.importTtl(asString(payload.get("ttlContent"), ""), asBoolean(payload.get("replace"), false)); }
    @GetMapping("/trace/{traceId}") public List<Map<String, Object>> trace(@PathVariable String traceId) { return engine.getTrace(traceId); }
    @GetMapping("/health") public Map<String, Object> health() { return Map.of("status", "ok"); }

    @GetMapping("/ontology/stats") public Map<String, Object> stats() { return engine.stats(); }

    @GetMapping("/ontology/overview") public Map<String, Object> overview() { return engine.stats(); }

    @PostMapping("/ontology/classes") public Map<String, Object> addClass(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) return Map.of("success", false, "message", "类名不能为空");
        engine.addClass(name);
        return Map.of("success", true, "message", "类 " + name + " 创建成功");
    }

    @PostMapping("/ontology/properties") public Map<String, Object> addProperty(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) return Map.of("success", false, "message", "属性名不能为空");
        engine.addProperty(name);
        return Map.of("success", true, "message", "属性 " + name + " 创建成功");
    }

    @GetMapping("/ontology/instances") public List<Map<String, Object>> allInstances() { return engine.allInstances(); }

    @PostMapping("/ontology/instances") public Map<String, Object> addInstance(@RequestBody Map<String, Object> body) {
        String uri = asString(body.get("uri"), "").trim();
        String type = asString(body.get("type"), "Entity").trim();
        if (uri.isEmpty()) return Map.of("success", false, "message", "实例URI不能为空");
        @SuppressWarnings("unchecked") Map<String, Object> facts = (Map<String, Object>) body.getOrDefault("facts", Map.of());
        engine.addInstance(uri, type, facts);
        return Map.of("success", true, "message", "实例 " + uri + " 创建成功");
    }

    @PutMapping("/ontology/instances/{uri}") public Map<String, Object> updateInstance(@PathVariable String uri, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked") Map<String, Object> facts = (Map<String, Object>) body.getOrDefault("facts", Map.of());
        engine.updateInstance(uri, facts);
        return Map.of("success", true, "message", "实例 " + uri + " 更新成功");
    }

    @DeleteMapping("/ontology/instances/{uri}") public Map<String, Object> deleteInstance(@PathVariable String uri) {
        engine.deleteInstance(uri);
        return Map.of("success", true, "message", "实例 " + uri + " 删除成功");
    }

    private static String asString(Object value, String defaultValue) { return value == null ? defaultValue : String.valueOf(value); }
    private static boolean asBoolean(Object value, boolean defaultValue) { return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value)); }
}