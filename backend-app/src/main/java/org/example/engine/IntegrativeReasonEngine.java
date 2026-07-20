package org.example.engine;

import org.example.model.Models;
import org.example.model.PlatformModels;
import org.example.store.AuditStore;
import org.example.store.InMemoryAuditStore;
import org.example.store.InMemorySnapshotStore;
import org.example.store.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IntegrativeReasonEngine {
    private static final Logger log = LoggerFactory.getLogger(IntegrativeReasonEngine.class);

    private final String namespace;
    private final SnapshotStore snapshotStore;
    private final AuditStore auditStore;

    public IntegrativeReasonEngine() { this("http://example.org/"); }
    public IntegrativeReasonEngine(String namespace) { this(namespace, new InMemorySnapshotStore(), new InMemoryAuditStore()); }
    public IntegrativeReasonEngine(String namespace, SnapshotStore snapshotStore, AuditStore auditStore) {
        this.namespace = namespace.endsWith("/") ? namespace : namespace + "/";
        this.snapshotStore = snapshotStore;
        this.auditStore = auditStore;
        log.info("[IntegrativeReasonEngine] 初始化完成, namespace={}", this.namespace);
    }

    public Models.RetrieveFactsResponse retrieveFacts(Models.RetrieveFactsRequest req) {
        log.info("[retrieveFacts] 开始检索事实, entities={}, traceId={}", req.entities(), req.traceContext().traceId());
        long start = System.currentTimeMillis();
        Map<String, Models.FactSet> factsMap = new LinkedHashMap<>();
        for (Models.EntityRef entity : req.entities()) {
            factsMap.put(entity.normalizedUri(namespace), new Models.FactSet(Map.of()));
        }
        String snapshotId = buildSnapshotId();
        Models.Snapshot snapshot = new Models.Snapshot(snapshotId, req.traceContext(), factsMap, 3600L);
        snapshotStore.save(snapshot);
        appendAudit(req.traceContext(), new Models.AuditEntry("fact.retrieve", Instant.now(), Map.of("snapshot_id", snapshotId, "entities", req.entities().stream().map(Models.EntityRef::id).toList())));
        long duration = System.currentTimeMillis() - start;
        log.info("[retrieveFacts] 完成检索, snapshotId={}, factsCount={}, duration={}ms", snapshotId, factsMap.size(), duration);
        return new Models.RetrieveFactsResponse(snapshotId, factsMap);
    }

    public Models.EvaluatePolicyResponse evaluatePolicy(Models.EvaluatePolicyRequest req) {
        String policySetId = String.valueOf(req.context().getOrDefault("policy_set_id", "UNKNOWN"));
        String expectationType = String.valueOf(req.context().getOrDefault("expectation_type", "validation"));
        log.info("[evaluatePolicy] 开始策略评估, policySetId={}, expectationType={}, traceId={}", policySetId, expectationType, req.traceContext().traceId());
        long start = System.currentTimeMillis();
        PolicyDecision decision = applyPolicy(policySetId, req.facts().root(), expectationType);
        Models.DecisionResult result = new Models.DecisionResult(decision.verdict, decision.confidence, decision.triggeredRules, decision.reason, List.of(), null);
        appendAudit(req.traceContext(), new Models.AuditEntry("policy.evaluate", Instant.now(), Map.of("policy_set_id", policySetId, "expectation_type", expectationType, "verdict", decision.verdict, "triggered_rules", decision.triggeredRules)));
        long duration = System.currentTimeMillis() - start;
        log.info("[evaluatePolicy] 完成评估, verdict={}, confidence={}, triggeredRules={}, reason={}, duration={}ms",
                decision.verdict, decision.confidence, decision.triggeredRules, decision.reason, duration);
        return new Models.EvaluatePolicyResponse(result);
    }

    public Models.EvaluatePolicyResponse evaluatePolicyWithFacts(Models.RetrieveFactsRequest req, String policySetId) {
        log.info("[evaluatePolicyWithFacts] 开始事实+策略评估, policySetId={}", policySetId);
        Models.RetrieveFactsResponse factsResponse = retrieveFacts(req);
        Models.FactSet merged = factsResponse.factsMap().values().stream().findFirst().orElse(new Models.FactSet(Map.of()));
        return evaluatePolicy(new Models.EvaluatePolicyRequest(merged, Map.of("policy_set_id", policySetId), req.traceContext()));
    }

    public Models.CompareStateResponse compareState(Models.CompareStateRequest req) {
        log.info("[compareState] 开始状态比较, baseSnapshotId={}, patchesCount={}, policySetId={}", req.baseSnapshotId(), req.patches().size(), req.policySetId());
        long start = System.currentTimeMillis();
        Models.Snapshot snapshot = snapshotStore.get(req.baseSnapshotId());
        if (snapshot == null) {
            log.error("[compareState] Snapshot不存在或已过期, snapshotId={}", req.baseSnapshotId());
            throw new IllegalStateException("Snapshot not found or expired");
        }
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
        long duration = System.currentTimeMillis() - start;
        log.info("[compareState] 完成状态比较, comparisonsCount={}, duration={}ms", comparisons.size(), duration);
        return new Models.CompareStateResponse(comparisons);
    }

    public PlatformModels.EvaluateSwrlResponse evaluateSwrl(PlatformModels.EvaluateSwrlRequest req) {
        log.info("[evaluateSwrl] 开始SWRL规则评估, ruleRefs={}", req.ruleRefs());
        appendAudit(req.traceContext(), new Models.AuditEntry("swrl.evaluate", Instant.now(), Map.of("fired_rule_ids", List.of("SWRL_001"))));
        PlatformModels.EvaluateSwrlResponse response = new PlatformModels.EvaluateSwrlResponse(
                List.of(new PlatformModels.SwrlResult("SWRL_001", true, List.of(Map.of("predicate", "eligibleForUpgrade", "object", "Platinum")), Map.of("ind", "Customer_Li"))),
                List.of("SWRL_001"),
                List.of(Map.of("rule_id", "SWRL_001", "label", "demo"))
        );
        log.info("[evaluateSwrl] 完成SWRL评估, firedRuleIds={}", response.firedRuleIds());
        return response;
    }

    public PlatformModels.ShaclValidationResponse validateShacl(PlatformModels.ShaclValidationRequest req) {
        log.info("[validateShacl] 开始SHACL验证, dataKeys={}, shapes={}", req.data().keySet(), req.shapes());
        boolean emailInvalid = req.data().containsKey("email") && !String.valueOf(req.data().get("email")).contains("@");
        if (emailInvalid) {
            log.warn("[validateShacl] 验证失败: 邮箱格式不正确");
            return new PlatformModels.ShaclValidationResponse(false, List.of(new PlatformModels.ShaclViolation("violation", "email", "邮箱格式不正确", req.shapes())));
        }
        log.info("[validateShacl] 验证通过");
        return new PlatformModels.ShaclValidationResponse(true, List.of());
    }

    public PlatformModels.HypotheticalEvaluateResponse hypotheticalEvaluate(List<String> entityIds, List<PlatformModels.HypotheticalTriple> triples, String policySetId, String tenantId) {
        log.info("[hypotheticalEvaluate] 开始假设推演, entityIds={}, triplesCount={}, policySetId={}, tenantId={}",
                entityIds, triples.size(), policySetId, tenantId);
        Map<String, Map<String, Object>> facts = new LinkedHashMap<>();
        for (String entityId : entityIds) {
            facts.put(entityId, new LinkedHashMap<>(ontologyStore.retrieve(List.of(new Models.EntityRef(entityId, "Entity", "ontology")), namespace).values().iterator().next().root()));
        }
        for (PlatformModels.HypotheticalTriple triple : triples) {
            String uri = triple.subject().startsWith("http") ? triple.subject() : namespace + triple.subject();
            facts.computeIfAbsent(uri, key -> new LinkedHashMap<>()).put(triple.predicate(), triple.object());
        }
        Models.DecisionResult decision = new Models.DecisionResult(
                applyPolicy(policySetId, facts.values().stream().findFirst().orElse(Map.of()), "candidate_check").verdict,
                1.0, List.of("R003"), "假设推演结果", List.of(), null
        );
        log.info("[hypotheticalEvaluate] 完成假设推演, verdict={}, factsCount={}", decision.verdict(), facts.size());
        return new PlatformModels.HypotheticalEvaluateResponse(facts, decision);
    }

    public PlatformModels.SchemaResponse schema() {
        log.debug("[schema] 获取Schema信息");
        return new PlatformModels.SchemaResponse(ontologyStore.classes(), ontologyStore.properties(), Instant.now());
    }

    public PlatformModels.CatalogResponse schemaCatalog() {
        log.debug("[schemaCatalog] 获取Schema目录");
        return new PlatformModels.CatalogResponse(ontologyStore.classes(), ontologyStore.properties());
    }

    public PlatformModels.SchemaDetailResponse schemaDetail(String className) {
        log.debug("[schemaDetail] 获取Schema详情, className={}", className);
        return new PlatformModels.SchemaDetailResponse(className, ontologyStore.samplesFor(className));
    }

    public PlatformModels.NlQueryResponse nlQuery(String question) {
        log.info("[nlQuery] 开始自然语言查询, question={}", question);
        String normalized = question == null ? "" : question.toLowerCase();
        String className = normalized.contains("customer") || normalized.contains("客户") ? "Customer" : ontologyStore.classes().stream().findFirst().orElse("Customer");
        schemaDetail(className);
        String sparql = "SELECT ?entity ?vipLevel WHERE { ?entity a " + className + " . OPTIONAL { ?entity vipLevel ?vipLevel } } LIMIT 100";
        List<Map<String, Object>> results = ontologyStore.sparqlSelect(sparql);
        String answer = results.isEmpty() ? "查询结果为空。" : className + " 的示例实体是 Customer_Li，会员等级为 Gold。";
        appendAudit(new Models.TraceContext(null, "marketing_tenant", null), new Models.AuditEntry("nl.query", Instant.now(), Map.of("question", question, "sparql", sparql)));
        log.info("[nlQuery] 完成自然语言查询, sparql={}, resultsCount={}", sparql, results.size());
        return new PlatformModels.NlQueryResponse(answer, sparql, results);
    }

    public PlatformModels.ExplainResponse explain(PlatformModels.ExplainRequest req) {
        log.info("[explain] 开始生成解释, traceId={}, audience={}", req.traceId(), req.audience());
        List<Models.AuditEntry> entries = auditStore.get(req.traceId());
        if (entries.isEmpty()) {
            log.warn("[explain] Trace不存在, traceId={}", req.traceId());
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
        log.info("[explain] 完成解释生成, referencedRules={}", rules);
        return new PlatformModels.ExplainResponse(text, rules.stream().distinct().toList());
    }

    public List<Map<String, Object>> sparqlQuery(String query) {
        log.info("[sparqlQuery] 开始SPARQL查询, query={}", query != null ? query.substring(0, Math.min(100, query.length())) + (query.length() > 100 ? "..." : "") : null);
        String normalized = query == null ? "" : query.toUpperCase();
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("ASK") && !normalized.startsWith("DESCRIBE") && !normalized.startsWith("CONSTRUCT")) {
            log.error("[sparqlQuery] 查询类型不支持, query={}", query);
            throw new IllegalArgumentException("Only read-only SPARQL queries are allowed");
        }
        for (String forbidden : List.of("INSERT", "DELETE", "DROP", "CLEAR", "LOAD", "CREATE", "MOVE", "COPY", "ADD", "MODIFY", "WITH")) {
            if (normalized.contains(forbidden)) {
                log.error("[sparqlQuery] 包含禁用关键词: {}", forbidden);
                throw new IllegalArgumentException("Forbidden SPARQL keyword: " + forbidden);
            }
        }
        String queryWithLimit = normalized.contains("LIMIT") ? query : query + " LIMIT 100";
        List<Map<String, Object>> results = ontologyStore.sparqlSelect(queryWithLimit);
        log.info("[sparqlQuery] 完成SPARQL查询, resultsCount={}", results.size());
        return results;
    }

    public List<Map<String, Object>> getTrace(String traceId) {
        log.debug("[getTrace] 获取审计日志, traceId={}", traceId);
        List<Map<String, Object>> entries = Models.auditEntriesToMaps(auditStore.get(traceId));
        log.debug("[getTrace] 获取审计日志完成, entriesCount={}", entries.size());
        return entries;
    }

    public void addClass(String className) {
        log.info("[addClass] 添加类, className={}", className);
        ontologyStore.addClass(className);
        log.info("[addClass] 类添加成功, className={}", className);
    }

    public void addProperty(String propertyName) {
        log.info("[addProperty] 添加属性, propertyName={}", propertyName);
        ontologyStore.addProperty(propertyName);
        log.info("[addProperty] 属性添加成功, propertyName={}", propertyName);
    }

    public List<Map<String, Object>> allInstances() {
        log.debug("[allInstances] 本体推理平台已移除");
        return List.of();
    }

    public void addInstance(String uri, String type, Map<String, Object> facts) { throw new UnsupportedOperationException("本体推理平台已移除"); }
    public void updateInstance(String uri, Map<String, Object> facts) { throw new UnsupportedOperationException("本体推理平台已移除"); }
    public void deleteInstance(String uri) { throw new UnsupportedOperationException("本体推理平台已移除"); }

    public Map<String, Object> stats() {
        log.debug("[stats] 获取统计信息");
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("classCount", 0);
        stats.put("propertyCount", 0);
        stats.put("instanceCount", 0);
        stats.put("classes", List.of());
        stats.put("properties", List.of());
        log.debug("[stats] 统计信息: classCount={}, propertyCount={}, instanceCount={}", 0, 0, 0);
        return stats;
    }

    public Map<String, Object> importTtl(String ttlContent, boolean replace) {
        log.info("[importTtl] 本体推理平台已移除, 不再支持TTL导入");
        return Map.of("success", false, "message", "本体推理平台已移除");
    }

    public Map<String, Object> getGraphData() {
        log.debug("[getGraphData] 获取本体图数据");
        return ontologyStore.getGraphData();
    }

    private void appendAudit(Models.TraceContext context, Models.AuditEntry entry) { auditStore.append(context.traceId(), entry); }
    private String buildSnapshotId() { return "snap_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_" + Instant.now().getEpochSecond(); }
    private PolicyDecision applyPolicy(String policySetId, Map<String, Object> facts, String expectationType) {
        double spend = number(facts.get("annualSpend"));
        double creditScore = number(facts.get("creditScore"));
        String vipLevel = String.valueOf(facts.getOrDefault("vipLevel", ""));
        String candidateActionType = String.valueOf(facts.getOrDefault("candidateActionType", ""));
        String billingActionType = String.valueOf(facts.getOrDefault("billingActionType", ""));

        log.debug("[applyPolicy] 策略参数: policySetId={}, expectationType={}, spend={}, creditScore={}, vipLevel={}, candidateActionType={}, billingActionType={}",
                policySetId, expectationType, spend, creditScore, vipLevel, candidateActionType, billingActionType);

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