package com.sitech.prodai.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OntologyService {

    private final Rdf4jOntologyStore rdf4jStore;

    private final Map<String, Map<String, Object>> ontologies = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> audits = new ConcurrentHashMap<>();

    public OntologyService(Rdf4jOntologyStore rdf4jStore) {
        this.rdf4jStore = rdf4jStore;
        // 初始化本体元数据
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("ontologyCode", "Customer");
        customer.put("ontologyName", "Customer Ontology");
        customer.put("isActive", true);
        customer.put("category", "default");
        customer.put("entities", List.of(Map.of("entityCode", "Customer", "fields", List.of(
                Map.of("fieldCode", "vipLevel", "fieldName", "会员等级", "required", true, "fieldType", "input"),
                Map.of("fieldCode", "annualSpend", "fieldName", "年消费", "required", false, "fieldType", "input")))));
        ontologies.put("Customer", customer);
    }

    // ========== 本体管理（保留原有逻辑） ==========

    public Map<String, Object> getCategories() {
        return Map.of("success", true, "data", List.of(Map.of("code", "default", "name", "默认")));
    }

    public Map<String, Object> listOntologies(String category, Boolean isActive) {
        return Map.of("success", true, "data", new ArrayList<>(ontologies.values()));
    }

    public Map<String, Object> getOntology(String ontologyCode) {
        Map<String, Object> data = ontologies.get(ontologyCode);
        if (data == null) return Map.of("success", false, "data", null, "message", "not found");
        return Map.of("success", true, "data", data, "message", "ok");
    }

    public Map<String, Object> createOntology(Map<String, Object> body, String operator) {
        return Map.of("success", true, "data", body, "message", "created");
    }

    public Map<String, Object> updateOntology(String code, Map<String, Object> body, String operator) {
        return Map.of("success", true, "data", body, "message", "updated");
    }

    public Map<String, Object> deleteOntology(String code) {
        return Map.of("success", true, "message", "deleted");
    }

    public Map<String, Object> toggleActive(String code) {
        return Map.of("success", true, "message", "toggled");
    }

    public Map<String, Object> getFormConstraint(String formCode) {
        return Map.of("success", true, "constraints", Map.of("formName", formCode, "entities", List.of(
                Map.of("fields", List.of(
                        Map.of("fieldCode", "vipLevel", "fieldName", "会员等级", "required", true, "fieldType", "input"),
                        Map.of("fieldCode", "annualSpend", "fieldName", "年消费", "required", false, "fieldType", "input"))))));
    }

    public Map<String, Object> getAllOntologies() {
        return Map.of("success", true, "ontologies", new ArrayList<>(ontologies.values()));
    }

    // ========== 基于 RDF4J 的本体推理操作 ==========

    public Map<String, Object> retrieve(String entityId, String type, String source, String tenantId, String traceId) {
        String uri = entityId.startsWith("http") ? entityId : "http://example.org/" + entityId;
        Map<String, Object> fact = rdf4jStore.getEntity(uri);
        if (fact.isEmpty()) {
            fact = defaultFact(uri, type, source);
        }
        String snapshotId = buildSnapshotId();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("snapshot_id", snapshotId);
        snapshot.put("trace_id", traceId);
        snapshot.put("tenant_id", tenantId);
        snapshot.put("facts", Map.of(uri, fact));
        snapshots.put(snapshotId, snapshot);
        appendAudit(traceId, Map.of("step", "fact.retrieve", "timestamp", Instant.now().toString(), "snapshot_id", snapshotId, "entities", List.of(entityId)));
        return Map.of("success", true, "message", "获取成功", "snapshot_id", snapshotId, "facts_map", Map.of(uri, fact));
    }

    public Map<String, Object> evaluate(Map<String, Object> facts, String policySetId, String expectationType, String traceId, String tenantId) {
        String verdict = decide(policySetId, facts, expectationType);
        List<String> rules = switch (policySetId) {
            case "PS_BILLING_REFUND_V1" -> List.of("B001");
            case "PS_MARKETING_RECOMMEND_V1" -> List.of("R001", "R003");
            default -> List.of("R000");
        };
        appendAudit(traceId, Map.of("step", "policy.evaluate", "timestamp", Instant.now().toString(), "policy_set_id", policySetId, "verdict", verdict, "triggered_rules", rules));
        return Map.of("success", true, "message", "评估完成", "decision", Map.of("verdict", verdict, "confidence", 1.0, "triggered_rules", rules, "reason", policySetId + " 评估完成"));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateWithFacts(List<Map<String, Object>> entitiesReq, String policySetId, String traceId, String tenantId) {
        if (entitiesReq == null || entitiesReq.isEmpty()) return Map.of("success", false, "message", "entities is required");
        Map<String, Object> first = entitiesReq.get(0);
        String entityId = String.valueOf(first.get("id"));
        String type = String.valueOf(first.getOrDefault("type", "Entity"));
        Map<String, Object> retrieved = retrieve(entityId, type, String.valueOf(first.getOrDefault("source", "ontology")), tenantId, traceId);
        Map<String, Object> factsMap = (Map<String, Object>) retrieved.get("facts_map");
        Map<String, Object> facts = (Map<String, Object>) factsMap.values().stream().findFirst().orElse(Map.of());
        String expectationType = String.valueOf(first.getOrDefault("expectation_type", "validation"));
        return evaluate(facts, policySetId, expectationType, traceId, tenantId);
    }

    public Map<String, Object> explain(String traceId, String audience, String tenantId) {
        List<Map<String, Object>> steps = audits.getOrDefault(traceId, List.of());
        List<String> rules = new ArrayList<>();
        for (Map<String, Object> step : steps) {
            Object triggered = step.get("triggered_rules");
            if (triggered instanceof List<?> list) {
                for (Object item : list) rules.add(String.valueOf(item));
            }
        }
        String text = switch (String.valueOf(audience)) {
            case "audit" -> steps.toString();
            case "business" -> "基于追踪ID " + traceId + "，系统根据事实与规则完成了评估。引用规则：" + rules;
            default -> steps.isEmpty() ? "Trace not found" : "根据您的画像和业务规则，系统做出了推荐。";
        };
        return Map.of("success", true, "natural_language", text, "referenced_rules", rules.stream().distinct().toList());
    }

    public Map<String, Object> schema() {
        return Map.of("classes", rdf4jStore.getClasses(), "properties", rdf4jStore.getProperties());
    }

    public Map<String, Object> schemaDetail(String className) {
        List<Map<String, Object>> samples = rdf4jStore.getInstances(className);
        return Map.of("class_name", className, "samples", samples);
    }

    public Map<String, Object> schemaCatalog() {
        return Map.of("classes", rdf4jStore.getClasses(), "properties", rdf4jStore.getProperties());
    }

    public Map<String, Object> sparqlQuery(String query) {
        List<Map<String, Object>> results = rdf4jStore.sparqlQuery(query);
        return Map.of("results", results);
    }

    public Map<String, Object> nlQuery(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        // 简单的 NL→SPARQL 映射，可替换为 LLM 调用
        String sparql;
        String answer;
        if (normalized.contains("会员等级") || normalized.contains("vip")) {
            sparql = "SELECT ?entity ?vipLevel WHERE { ?entity rdf:type <http://example.org/Customer> . ?entity <http://example.org/vipLevel> ?vipLevel }";
            answer = "查询所有客户的会员等级";
        } else if (normalized.contains("年消费") || normalized.contains("annual") || normalized.contains("spend")) {
            sparql = "SELECT ?entity ?annualSpend WHERE { ?entity rdf:type <http://example.org/Customer> . ?entity <http://example.org/annualSpend> ?annualSpend } ORDER BY DESC(?annualSpend)";
            answer = "查询所有客户的年消费额";
        } else if (normalized.contains("信用") || normalized.contains("credit")) {
            sparql = "SELECT ?entity ?creditScore WHERE { ?entity rdf:type <http://example.org/Customer> . ?entity <http://example.org/creditScore> ?creditScore } ORDER BY DESC(?creditScore)";
            answer = "查询所有客户的信用分";
        } else {
            sparql = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 50";
            answer = "查询所有三元组数据";
        }
        List<Map<String, Object>> results = rdf4jStore.sparqlQuery(sparql);
        return Map.of("answer", answer, "sparql", sparql, "results", results);
    }

    public Map<String, Object> nlDiscoverAndRetrieve(String question, int maxEntities) {
        String normalized = question == null ? "" : question.toLowerCase();
        String sparql;
        String answer;
        String targetClass;

        if (normalized.contains("高消费") || normalized.contains("年消费") || normalized.contains("annual")) {
            targetClass = "Customer";
            sparql = "SELECT ?entity ?annualSpend WHERE { ?entity rdf:type <http://example.org/Customer> . ?entity <http://example.org/annualSpend> ?annualSpend FILTER(?annualSpend > 50000) } ORDER BY DESC(?annualSpend) LIMIT " + maxEntities;
            answer = "年消费超过5万的客户，按消费额降序排列";
        } else if (normalized.contains("vip") || normalized.contains("会员") || normalized.contains("gold")) {
            targetClass = "Customer";
            sparql = "SELECT ?entity ?vipLevel WHERE { ?entity rdf:type <http://example.org/Customer> . ?entity <http://example.org/vipLevel> ?vipLevel } LIMIT " + maxEntities;
            answer = "查询所有客户的会员等级信息";
        } else if (normalized.contains("客户") || normalized.contains("customer")) {
            targetClass = "Customer";
            sparql = "SELECT ?entity ?vipLevel ?annualSpend WHERE { ?entity rdf:type <http://example.org/Customer> . OPTIONAL { ?entity <http://example.org/vipLevel> ?vipLevel } OPTIONAL { ?entity <http://example.org/annualSpend> ?annualSpend } } LIMIT " + maxEntities;
            answer = "所有客户信息";
        } else if (normalized.contains("账户") || normalized.contains("account")) {
            targetClass = "Account";
            sparql = "SELECT ?entity ?accountStatus ?outstandingBalance WHERE { ?entity rdf:type <http://example.org/Account> . OPTIONAL { ?entity <http://example.org/accountStatus> ?accountStatus } OPTIONAL { ?entity <http://example.org/outstandingBalance> ?outstandingBalance } } LIMIT " + maxEntities;
            answer = "所有账户信息";
        } else {
            targetClass = "Customer";
            sparql = "SELECT ?entity WHERE { ?entity rdf:type ?type . ?type rdf:type owl:Class } LIMIT " + maxEntities;
            answer = "查询所有实体";
        }

        List<Map<String, Object>> rawResults = rdf4jStore.sparqlQuery(sparql);
        List<String> entityIds = new ArrayList<>();
        for (Map<String, Object> row : rawResults) {
            String entity = String.valueOf(row.get("entity"));
            if (!entity.isEmpty()) entityIds.add(entity);
        }

        // 获取第一个实体的详细信息作为 snapshot
        Map<String, Object> firstFact = Map.of();
        if (!entityIds.isEmpty()) {
            firstFact = rdf4jStore.getEntity(entityIds.get(0));
        }

        return Map.of("success", true, "nl_answer", answer, "entity_ids", entityIds, "sparql", sparql,
                "raw_results", rawResults,
                "snapshot", Map.of("snapshot_id", buildSnapshotId(), "facts_map", Map.of(entityIds.isEmpty() ? "" : entityIds.get(0), firstFact)),
                "facts_flat", firstFact);
    }

    public Map<String, Object> quickEvaluate(String entityId, String type, String policySetId, String tenantId) {
        String uri = entityId.startsWith("http") ? entityId : "http://example.org/" + entityId;
        Map<String, Object> fact = rdf4jStore.getEntity(uri);
        if (fact.isEmpty()) {
            fact = defaultFact(uri, type, "ontology");
        }
        String traceId = UUID.randomUUID().toString();
        String verdict = decide(policySetId, fact, "validation");
        List<String> rules = switch (policySetId) {
            case "PS_BILLING_REFUND_V1" -> List.of("B001");
            case "PS_MARKETING_RECOMMEND_V1" -> List.of("R001", "R003");
            default -> List.of("R000");
        };
        return Map.of("success", true, "verdict", verdict, "triggered_rules", rules, "reason", policySetId + " 评估完成");
    }

    public Map<String, Object> getPolicySets() {
        List<Map<String, Object>> sets = List.of(
                Map.of("id", "PS_MARKETING_RECOMMEND_V1", "name", "营销推荐策略集", "description", "用于营销推荐场景的规则校验"),
                Map.of("id", "PS_BILLING_REFUND_V1", "name", "账单退款策略集", "description", "用于账单退款场景的规则校验")
        );
        return Map.of("success", true, "policy_sets", sets);
    }

    public Map<String, Object> getSwrlRules() {
        List<Map<String, Object>> rules = List.of(
                Map.of("rule_id", "SWRL_001", "label", "高消费推导升级资格", "module", "marketing_rules"),
                Map.of("rule_id", "SWRL_002", "label", "信用分推导额度调整", "module", "marketing_rules")
        );
        return Map.of("success", true, "rules", rules);
    }

    public Map<String, Object> compareState(String snapshotId, List<Map<String, Object>> patches, String policySetId, String traceId, String tenantId) {
        Map<String, Object> snapshot = snapshots.get(snapshotId);
        if (snapshot == null) throw new IllegalStateException("Snapshot not found or expired");
        List<Map<String, Object>> comparisons = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> facts = (Map<String, Map<String, Object>>) snapshot.get("facts");
        for (Map<String, Object> patch : patches) {
            String entityId = String.valueOf(patch.get("entity_id"));
            Map<String, Object> base = new LinkedHashMap<>(facts.values().stream().findFirst().orElse(Map.of()));
            @SuppressWarnings("unchecked")
            Map<String, Object> changes = (Map<String, Object>) patch.getOrDefault("changes", Map.of());
            base.putAll(changes);
            Map<String, Object> decision = evaluate(base, policySetId, "candidate_check", traceId, tenantId);
            comparisons.add(Map.of("patch_description", String.valueOf(patch.getOrDefault("description", "")), "resulting_state", Map.of(entityId, base), "evaluation", decision.get("decision")));
        }
        return Map.of("success", true, "comparisons", comparisons);
    }

    public Map<String, Object> getTrace(String traceId) {
        return Map.of("trace_id", traceId, "steps", audits.getOrDefault(traceId, List.of()), "total_steps", audits.getOrDefault(traceId, List.of()).size());
    }

    // ========== 私有方法 ==========

    private Map<String, Object> defaultFact(String entityId, String type, String source) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("entityId", entityId);
        fact.put("entityType", type);
        fact.put("source", source);
        fact.put("vipLevel", "Gold");
        fact.put("annualSpend", 80000);
        fact.put("memberYears", 3);
        fact.put("creditScore", 750);
        return fact;
    }

    private String buildSnapshotId() {
        return "snap_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_" + Instant.now().getEpochSecond();
    }

    private void appendAudit(String traceId, Map<String, Object> entry) {
        audits.computeIfAbsent(traceId, key -> new ArrayList<>()).add(new LinkedHashMap<>(entry));
    }

    private String decide(String policySetId, Map<String, Object> facts, String expectationType) {
        double spend = number(facts.get("annualSpend"));
        double creditScore = number(facts.get("creditScore"));
        String vipLevel = String.valueOf(facts.getOrDefault("vipLevel", ""));
        String candidateActionType = String.valueOf(facts.getOrDefault("candidateActionType", ""));
        String billingActionType = String.valueOf(facts.getOrDefault("billingActionType", ""));
        return switch (policySetId) {
            case "PS_MARKETING_RECOMMEND_V1" -> {
                if ("candidate_check".equals(expectationType)) {
                    if ("premium_upgrade".equals(candidateActionType) && spend >= 50000 && ("Gold".equalsIgnoreCase(vipLevel) || "Platinum".equalsIgnoreCase(vipLevel)))
                        yield "allow";
                    if ("membership_bundle".equals(candidateActionType) && spend >= 30000) yield "allow";
                    yield "review";
                }
                if (spend >= 50000 || "Platinum".equalsIgnoreCase(vipLevel)) yield "allow";
                yield "deny";
            }
            case "PS_BILLING_REFUND_V1" -> {
                if ("full_refund".equals(billingActionType) && creditScore >= 700) yield "allow";
                if ("partial_refund".equals(billingActionType)) yield "review";
                yield "deny";
            }
            default -> "review";
        };
    }

    private double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return -1;
        }
    }
}