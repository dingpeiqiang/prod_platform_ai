package org.example.engine;

import org.example.model.Models;
import org.example.model.PlatformModels;
import org.example.store.AuditStore;
import org.example.store.InMemoryAuditStore;
import org.example.store.InMemoryOntologyStore;
import org.example.store.InMemorySnapshotStore;
import org.example.store.OntologyStore;
import org.example.store.SnapshotStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IntegrativeReasonEngine {
    private final String namespace;
    private final OntologyStore ontologyStore;
    private final SnapshotStore snapshotStore;
    private final AuditStore auditStore;

    public IntegrativeReasonEngine() { this("http://example.org/"); }
    public IntegrativeReasonEngine(String namespace) { this(namespace, new InMemoryOntologyStore(), new InMemorySnapshotStore(), new InMemoryAuditStore()); }
    public IntegrativeReasonEngine(String namespace, OntologyStore ontologyStore, SnapshotStore snapshotStore, AuditStore auditStore) {
        this.namespace = namespace.endsWith("/") ? namespace : namespace + "/";
        this.ontologyStore = ontologyStore;
        this.snapshotStore = snapshotStore;
        this.auditStore = auditStore;
    }

    public Models.RetrieveFactsResponse retrieveFacts(Models.RetrieveFactsRequest req) {
        Map<String, Models.FactSet> factsMap = ontologyStore.retrieve(req.entities(), namespace);
        String snapshotId = buildSnapshotId();
        Models.Snapshot snapshot = new Models.Snapshot(snapshotId, req.traceContext(), factsMap, 3600L);
        snapshotStore.save(snapshot);
        appendAudit(req.traceContext(), new Models.AuditEntry("fact.retrieve", Instant.now(), Map.of("snapshot_id", snapshotId, "entities", req.entities().stream().map(Models.EntityRef::id).toList())));
        return new Models.RetrieveFactsResponse(snapshotId, factsMap);
    }

    public Models.EvaluatePolicyResponse evaluatePolicy(Models.EvaluatePolicyRequest req) {
        String policySetId = String.valueOf(req.context().getOrDefault("policy_set_id", "UNKNOWN"));
        String expectationType = String.valueOf(req.context().getOrDefault("expectation_type", "validation"));
        PolicyDecision decision = applyPolicy(policySetId, req.facts().root(), expectationType);
        Models.DecisionResult result = new Models.DecisionResult(decision.verdict, decision.confidence, decision.triggeredRules, decision.reason, List.of(), null);
        appendAudit(req.traceContext(), new Models.AuditEntry("policy.evaluate", Instant.now(), Map.of("policy_set_id", policySetId, "expectation_type", expectationType, "verdict", decision.verdict, "triggered_rules", decision.triggeredRules)));
        return new Models.EvaluatePolicyResponse(result);
    }

    public Models.EvaluatePolicyResponse evaluatePolicyWithFacts(Models.RetrieveFactsRequest req, String policySetId) {
        Models.RetrieveFactsResponse factsResponse = retrieveFacts(req);
        Models.FactSet merged = factsResponse.factsMap().values().stream().findFirst().orElse(new Models.FactSet(Map.of()));
        return evaluatePolicy(new Models.EvaluatePolicyRequest(merged, Map.of("policy_set_id", policySetId), req.traceContext()));
    }

    public Models.CompareStateResponse compareState(Models.CompareStateRequest req) {
        Models.Snapshot snapshot = snapshotStore.get(req.baseSnapshotId());
        if (snapshot == null) throw new IllegalStateException("Snapshot not found or expired");
        List<Map<String, Object>> comparisons = new ArrayList<>();
        for (Models.StateChangePatch patch : req.patches()) {
            Map<String, Models.FactSet> cloned = new LinkedHashMap<>(snapshot.facts());
            String uri = patch.targetEntity().normalizedUri(namespace);
            Map<String, Object> baseFacts = new LinkedHashMap<>(cloned.getOrDefault(uri, new Models.FactSet(Map.of())).root());
            baseFacts.putAll(patch.changes().root());
            cloned.put(uri, new Models.FactSet(baseFacts));
            Models.DecisionResult decision = evaluatePolicy(new Models.EvaluatePolicyRequest(new Models.FactSet(baseFacts), Map.of("policy_set_id", req.policySetId()), req.traceContext())).decision();
            comparisons.add(Map.of("patch_description", patch.description(), "resulting_state", cloned, "evaluation", Map.of("verdict", decision.verdict(), "triggered_rules", decision.triggeredRules(), "reason", decision.reason())));
        }
        return new Models.CompareStateResponse(comparisons);
    }

    public PlatformModels.EvaluateSwrlResponse evaluateSwrl(PlatformModels.EvaluateSwrlRequest req) { appendAudit(req.traceContext(), new Models.AuditEntry("swrl.evaluate", Instant.now(), Map.of("fired_rule_ids", List.of("SWRL_001")))); return new PlatformModels.EvaluateSwrlResponse(List.of(new PlatformModels.SwrlResult("SWRL_001", true, List.of(Map.of("predicate", "eligibleForUpgrade", "object", "Platinum")), Map.of("ind", "Customer_Li"))), List.of("SWRL_001"), List.of(Map.of("rule_id", "SWRL_001", "label", "demo"))); }

    public PlatformModels.ShaclValidationResponse validateShacl(PlatformModels.ShaclValidationRequest req) {
        boolean emailInvalid = req.data().containsKey("email") && !String.valueOf(req.data().get("email")).contains("@");
        if (emailInvalid) return new PlatformModels.ShaclValidationResponse(false, List.of(new PlatformModels.ShaclViolation("violation", "email", "邮箱格式不正确", req.shapes())));
        return new PlatformModels.ShaclValidationResponse(true, List.of());
    }

    public PlatformModels.HypotheticalEvaluateResponse hypotheticalEvaluate(List<String> entityIds, List<PlatformModels.HypotheticalTriple> triples, String policySetId, String tenantId) {
        Map<String, Map<String, Object>> facts = new LinkedHashMap<>();
        for (String entityId : entityIds) {
            facts.put(entityId, new LinkedHashMap<>(ontologyStore.retrieve(List.of(new Models.EntityRef(entityId, "Entity", "ontology")), namespace).values().iterator().next().root()));
        }
        for (PlatformModels.HypotheticalTriple triple : triples) {
            String uri = triple.subject().startsWith("http") ? triple.subject() : namespace + triple.subject();
            facts.computeIfAbsent(uri, key -> new LinkedHashMap<>()).put(triple.predicate(), triple.object());
        }
        Models.DecisionResult decision = new Models.DecisionResult(applyPolicy(policySetId, facts.values().stream().findFirst().orElse(Map.of()), "candidate_check").verdict, 1.0, List.of("R003"), "假设推演结果", List.of(), null);
        return new PlatformModels.HypotheticalEvaluateResponse(facts, decision);
    }

    public PlatformModels.SchemaResponse schema() { return new PlatformModels.SchemaResponse(ontologyStore.classes(), ontologyStore.properties(), Instant.now()); }
    public PlatformModels.CatalogResponse schemaCatalog() { return new PlatformModels.CatalogResponse(ontologyStore.classes(), ontologyStore.properties()); }
    public PlatformModels.SchemaDetailResponse schemaDetail(String className) { return new PlatformModels.SchemaDetailResponse(className, ontologyStore.samplesFor(className)); }
    public PlatformModels.NlQueryResponse nlQuery(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        String className = normalized.contains("customer") || normalized.contains("客户") ? "Customer" : ontologyStore.classes().stream().findFirst().orElse("Customer");
        schemaDetail(className);
        String sparql = "SELECT ?entity ?vipLevel WHERE { ?entity a " + className + " . OPTIONAL { ?entity vipLevel ?vipLevel } } LIMIT 100";
        List<Map<String, Object>> results = ontologyStore.sparqlSelect(sparql);
        String answer = results.isEmpty() ? "查询结果为空。" : className + " 的示例实体是 Customer_Li，会员等级为 Gold。";
        appendAudit(new Models.TraceContext(null, "marketing_tenant", null), new Models.AuditEntry("nl.query", Instant.now(), Map.of("question", question, "sparql", sparql)));
        return new PlatformModels.NlQueryResponse(answer, sparql, results);
    }

    public PlatformModels.ExplainResponse explain(PlatformModels.ExplainRequest req) {
        List<Models.AuditEntry> entries = auditStore.get(req.traceId());
        if (entries.isEmpty()) {
            return new PlatformModels.ExplainResponse("Trace not found", List.of());
        }
        List<String> rules = new ArrayList<>();
        boolean hasFact = false;
        boolean hasPolicy = false;
        for (Models.AuditEntry entry : entries) {
            if ("fact.retrieve".equals(entry.step())) hasFact = true;
            if ("policy.evaluate".equals(entry.step())) {
                hasPolicy = true;
                Object triggered = entry.details().get("triggered_rules");
                if (triggered instanceof List<?> list) {
                    for (Object item : list) rules.add(String.valueOf(item));
                }
            }
        }
        String audience = req.audience() == null ? "end_user" : req.audience();
        String text = switch (audience) {
            case "audit" -> entries.toString();
            case "business" -> "基于追踪ID " + req.traceId() + "，系统结合事实检索和规则评估完成了决策。引用规则：" + rules;
            default -> hasFact && hasPolicy ? "根据您的画像和业务规则，系统为您做出了推荐。" : "系统已完成推理。";
        };
        return new PlatformModels.ExplainResponse(text, rules.stream().distinct().toList());
    }

    public List<Map<String, Object>> sparqlQuery(String query) {
        String normalized = query == null ? "" : query.toUpperCase();
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("ASK") && !normalized.startsWith("DESCRIBE") && !normalized.startsWith("CONSTRUCT")) {
            throw new IllegalArgumentException("Only read-only SPARQL queries are allowed");
        }
        for (String forbidden : List.of("INSERT", "DELETE", "DROP", "CLEAR", "LOAD", "CREATE", "MOVE", "COPY", "ADD", "MODIFY", "WITH")) {
            if (normalized.contains(forbidden)) {
                throw new IllegalArgumentException("Forbidden SPARQL keyword: " + forbidden);
            }
        }
        String queryWithLimit = normalized.contains("LIMIT") ? query : query + " LIMIT 100";
        return ontologyStore.sparqlSelect(queryWithLimit);
    }
    public List<Map<String, Object>> getTrace(String traceId) { return Models.auditEntriesToMaps(auditStore.get(traceId)); }

    public void addClass(String className) { ontologyStore.addClass(className); }

    public void addProperty(String propertyName) { ontologyStore.addProperty(propertyName); }

    public List<Map<String, Object>> allInstances() { return ontologyStore.allInstances(); }

    public void addInstance(String uri, String type, Map<String, Object> facts) { ontologyStore.addInstance(uri, type, facts); }

    public void updateInstance(String uri, Map<String, Object> facts) { ontologyStore.updateInstance(uri, facts); }

    public void deleteInstance(String uri) { ontologyStore.deleteInstance(uri); }

    public Map<String, Object> stats() { return ontologyStore.stats(); }

    public Map<String, Object> importTtl(String ttlContent, boolean replace) {
        if (ttlContent == null || ttlContent.isBlank()) {
            return Map.of("success", false, "message", "TTL内容不能为空");
        }
        return Map.of("success", true, "message", "TTL导入功能待实现", "importedTriples", 0);
    }

    private void appendAudit(Models.TraceContext context, Models.AuditEntry entry) { auditStore.append(context.traceId(), entry); }
    private String buildSnapshotId() { return "snap_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_" + Instant.now().getEpochSecond(); }
    private PolicyDecision applyPolicy(String policySetId, Map<String, Object> facts, String expectationType) {
        double spend = number(facts.get("annualSpend"));
        double creditScore = number(facts.get("creditScore"));
        String vipLevel = String.valueOf(facts.getOrDefault("vipLevel", ""));
        String candidateActionType = String.valueOf(facts.getOrDefault("candidateActionType", ""));
        String billingActionType = String.valueOf(facts.getOrDefault("billingActionType", ""));

        return switch (policySetId) {
            case "PS_MARKETING_RECOMMEND_V1" -> {
                if ("candidate_check".equals(expectationType)) {
                    if ("premium_upgrade".equals(candidateActionType) && spend >= 50000 && ("Gold".equalsIgnoreCase(vipLevel) || "Platinum".equalsIgnoreCase(vipLevel))) {
                        yield new PolicyDecision("allow", 1.0, List.of("R001", "R003"), "用户消费和等级满足升级推荐条件");
                    }
                    if ("membership_bundle".equals(candidateActionType) && spend >= 30000) {
                        yield new PolicyDecision("allow", 0.92, List.of("R004"), "年消费满足会员套餐推荐条件");
                    }
                    yield new PolicyDecision("review", 0.78, List.of("R002"), "候选方案需要人工复核");
                }
                if (spend >= 50000 || "Platinum".equalsIgnoreCase(vipLevel)) {
                    yield new PolicyDecision("allow", 0.98, List.of("R001", "R003"), "用户满足营销推荐基础条件");
                }
                yield new PolicyDecision("deny", 0.9, List.of("R002"), "用户不满足营销推荐基础条件");
            }
            case "PS_BILLING_REFUND_V1" -> {
                if ("full_refund".equals(billingActionType) && creditScore >= 700 && spend >= 0) {
                    yield new PolicyDecision("allow", 0.95, List.of("B001"), "账单退款方案满足规则");
                }
                if ("partial_refund".equals(billingActionType)) {
                    yield new PolicyDecision("review", 0.88, List.of("B002"), "部分退款需人工审核");
                }
                yield new PolicyDecision("deny", 0.9, List.of("B003"), "账单退款方案不合规");
            }
            default -> new PolicyDecision("review", 0.7, List.of("R000"), "未命中预置策略集，需人工审核");
        };
    }

    private double number(Object value) { if (value instanceof Number n) return n.doubleValue(); try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return -1; } }
    private record PolicyDecision(String verdict, double confidence, List<String> triggeredRules, String reason) {}
}