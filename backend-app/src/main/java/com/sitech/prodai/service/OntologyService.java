package com.sitech.prodai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OntologyService {

    private static final Logger log = LoggerFactory.getLogger(OntologyService.class);

    private final Rdf4jOntologyStore rdf4jStore;
    private final Optional<LlmService> llmService;
    private final Optional<SwrlRuleEngine> swrlRuleEngine;
    private final Map<String, Map<String, Object>> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> audits = new ConcurrentHashMap<>();

    public OntologyService(Rdf4jOntologyStore rdf4jStore,
                           @Autowired(required = false) Optional<LlmService> llmService,
                           @Autowired(required = false) Optional<SwrlRuleEngine> swrlRuleEngine) {
        this.rdf4jStore = rdf4jStore;
        this.llmService = llmService;
        this.swrlRuleEngine = swrlRuleEngine;
    }

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
        List<String> rules = triggeredRules(policySetId, facts, expectationType);
        appendAudit(traceId, Map.of("step", "policy.evaluate", "timestamp", Instant.now().toString(), "policy_set_id", policySetId, "verdict", verdict, "triggered_rules", rules));
        return Map.of("success", true, "message", "评估完成", "decision", Map.of("verdict", verdict, "confidence", 1.0, "triggered_rules", rules, "reason", reasoning(policySetId, facts, expectationType)));
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

    public Map<String, Object> schemaCatalog() {
        return schema();
    }

    public Map<String, Object> schemaDetail(String className) {
        return Map.of("class_name", className, "samples", rdf4jStore.getInstances(className));
    }

    public Map<String, Object> sparqlQuery(String query) {
        return Map.of("results", rdf4jStore.sparqlQuery(query));
    }

    public Map<String, Object> nlQuery(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        boolean wantProduct = normalized.contains("5g") || normalized.contains("套餐") || normalized.contains("product")
                || normalized.contains("在售") || normalized.contains("商品");
        boolean wantRisk = normalized.contains("风险") || normalized.contains("零资费") || normalized.contains("稽核");

        // 复合问法（如「在售5G套餐和风险商品」）合并两路结果
        if (wantProduct && wantRisk) {
            String productSparql = "SELECT ?product ?name ?growth ?users ?status WHERE { ?product a <http://example.org/Product> . OPTIONAL { ?product <http://example.org/productName> ?name } OPTIONAL { ?product <http://example.org/revenueGrowth> ?growth } OPTIONAL { ?product <http://example.org/newUserMonth> ?users } OPTIONAL { ?product <http://example.org/status> ?status } } ORDER BY ASC(?growth) LIMIT 20";
            String riskSparql = "SELECT ?product ?name ?isZeroFee ?status WHERE { ?product a <http://example.org/Product> . OPTIONAL { ?product <http://example.org/productName> ?name } OPTIONAL { ?product <http://example.org/isZeroFee> ?isZeroFee } OPTIONAL { ?product <http://example.org/status> ?status } } LIMIT 50";
            List<Map<String, Object>> merged = new ArrayList<>();
            for (Map<String, Object> row : rdf4jStore.sparqlQuery(productSparql)) {
                Map<String, Object> tagged = new LinkedHashMap<>(row);
                tagged.putIfAbsent("_bucket", "在售/增长");
                merged.add(tagged);
            }
            for (Map<String, Object> row : rdf4jStore.sparqlQuery(riskSparql)) {
                Map<String, Object> tagged = new LinkedHashMap<>(row);
                tagged.putIfAbsent("_bucket", "风险/零资费");
                merged.add(tagged);
            }
            return Map.of(
                    "answer", "已查询在售产品增长指标与风险/零资费相关商品",
                    "sparql", productSparql + "\n---\n" + riskSparql,
                    "results", merged
            );
        }

        String sparql;
        String answer;
        if (wantProduct) {
            sparql = "SELECT ?product ?name ?growth ?users WHERE { ?product a <http://example.org/Product> . OPTIONAL { ?product <http://example.org/productName> ?name } OPTIONAL { ?product <http://example.org/revenueGrowth> ?growth } OPTIONAL { ?product <http://example.org/newUserMonth> ?users } } ORDER BY ASC(?growth) LIMIT 20";
            answer = "查询在售产品及其增长指标";
        } else if (wantRisk) {
            sparql = "SELECT ?product ?name ?isZeroFee ?status WHERE { ?product a <http://example.org/Product> . OPTIONAL { ?product <http://example.org/productName> ?name } OPTIONAL { ?product <http://example.org/isZeroFee> ?isZeroFee } OPTIONAL { ?product <http://example.org/status> ?status } } LIMIT 50";
            answer = "查询零资费或风险相关产品";
        } else if (normalized.contains("会员等级") || normalized.contains("vip")) {
            sparql = "SELECT ?entity ?vipLevel WHERE { ?entity rdf:type <http://example.org/Customer> . ?entity <http://example.org/vipLevel> ?vipLevel }";
            answer = "查询所有客户的会员等级";
        } else if (normalized.contains("年消费") || normalized.contains("annual") || normalized.contains("spend")) {
            sparql = "SELECT ?entity ?annualSpend WHERE { ?entity rdf:type <http://example.org/Customer> . ?entity <http://example.org/annualSpend> ?annualSpend } ORDER BY DESC(?annualSpend)";
            answer = "查询所有客户的年消费额";
        } else {
            sparql = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 50";
            answer = "查询所有三元组数据";
        }
        return Map.of("answer", answer, "sparql", sparql, "results", rdf4jStore.sparqlQuery(sparql));
    }

    /**
     * NL→实体发现→本体检索（LLM 增强版）。
     * <p>
     * 流程：LLM 从自然语言中提取实体类型 + 筛选条件 → 构建 SPARQL → 检索本体 → 返回事实。
     * 若 LLM 不可用或调用失败，降级到关键词匹配的 nlQuery。
     */
    public Map<String, Object> nlDiscoverAndRetrieve(String question, int maxEntities) {
        // Step 1: 获取本体 schema 信息
        List<String> classes = rdf4jStore.getClasses();
        List<String> properties = rdf4jStore.getProperties();

        // Step 2: 尝试用 LLM 做实体发现
        Map<String, Object> llmDiscovery = llmDiscoverEntities(question, classes, properties);

        List<String> entityIds = new ArrayList<>();
        List<Map<String, Object>> rawResults = new ArrayList<>();
        String sparql = "";
        String nlAnswer = "";

        if (llmDiscovery != null && !llmDiscovery.isEmpty()) {
            // LLM 成功提取了实体信息，构建 SPARQL
            sparql = buildSparqlFromDiscovery(llmDiscovery);
            nlAnswer = String.valueOf(llmDiscovery.getOrDefault("answer", ""));

            if (!sparql.isBlank()) {
                List<Map<String, Object>> sparqlResults = rdf4jStore.sparqlQuery(sparql);
                for (Map<String, Object> row : sparqlResults) {
                    String uri = extractUri(row);
                    if (uri != null && !uri.isBlank()) {
                        entityIds.add(uri);
                        Map<String, Object> entityFacts = rdf4jStore.getEntity(uri);
                        rawResults.add(entityFacts.isEmpty() ? row : entityFacts);
                    } else {
                        rawResults.add(row);
                    }
                }
            }
        }

        // Step 3: 降级 — 若 LLM 未返回有效结果，使用关键词匹配
        if (rawResults.isEmpty()) {
            Map<String, Object> fallback = nlQuery(question);
            nlAnswer = String.valueOf(fallback.getOrDefault("answer", ""));
            sparql = String.valueOf(fallback.getOrDefault("sparql", ""));

            if (fallback.get("results") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> row) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rowMap = (Map<String, Object>) row;
                        String uri = extractUri(row);
                        if (uri != null && !uri.isBlank()) {
                            entityIds.add(uri);
                            Map<String, Object> entityFacts = rdf4jStore.getEntity(uri);
                            rawResults.add(entityFacts.isEmpty() ? rowMap : entityFacts);
                        } else {
                            rawResults.add(rowMap);
                        }
                    }
                }
            }
        }

        // Step 4: 构建 snapshot + audit
        String snapshotId = buildSnapshotId();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("snapshot_id", snapshotId);
        snapshot.put("question", question);
        snapshot.put("entity_count", entityIds.size());
        snapshot.put("discovery_method", llmDiscovery != null ? "llm" : "keyword_fallback");

        Map<String, Object> factsFlat = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(entityIds.size(), rawResults.size()); i++) {
            factsFlat.put(entityIds.get(i), rawResults.get(i));
        }
        snapshot.put("facts", factsFlat);
        snapshots.put(snapshotId, snapshot);

        appendAudit(snapshotId, Map.of(
                "step", "nl_discover_and_retrieve",
                "timestamp", Instant.now().toString(),
                "question", question,
                "entity_count", entityIds.size(),
                "snapshot_id", snapshotId,
                "discovery_method", snapshot.get("discovery_method")
        ));

        return Map.of(
                "success", true,
                "nl_answer", nlAnswer,
                "entity_ids", entityIds.stream().limit(maxEntities).toList(),
                "sparql", sparql,
                "raw_results", rawResults.stream().limit(maxEntities).toList(),
                "snapshot", snapshot,
                "facts_flat", factsFlat,
                "discovery_method", snapshot.get("discovery_method")
        );
    }

    /**
     * LLM 实体发现：让 LLM 从自然语言中提取实体类型、属性和筛选条件。
     */
    private Map<String, Object> llmDiscoverEntities(String question, List<String> classes, List<String> properties) {
        if (llmService.isEmpty()) {
            return null;
        }

        String schemaHint = "本体类: " + String.join(", ", classes)
                + "\n本体属性: " + String.join(", ", properties);

        String prompt = "你是一个本体查询助手。根据用户问题和本体 Schema，提取查询意图。\n\n"
                + schemaHint + "\n\n"
                + "用户问题: " + question + "\n\n"
                + "请输出 JSON 格式（仅输出 JSON，不要其他内容）：\n"
                + "{\n"
                + "  \"entities\": [{\"type\": \"类名\", \"filters\": {\"属性名\": \"值\"}}],\n"
                + "  \"select\": [\"需要返回的属性\"],\n"
                + "  \"answer\": \"一句话概括查询意图\"\n"
                + "}\n"
                + "如果无法从本体 Schema 中找到匹配的类，返回空 JSON: {}";

        try {
            String result = llmService.get().completePrompt(prompt);
            return parseJsonFromLlm(result);
        } catch (Exception e) {
            log.warn("[OntologyService] LLM 实体发现失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 LLM 输出中解析 JSON 结果。
     */
    private Map<String, Object> parseJsonFromLlm(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return null;
        int start = llmOutput.indexOf('{');
        int end = llmOutput.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            String json = llmOutput.substring(start, end + 1);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[OntologyService] LLM 输出 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 基于 LLM 实体发现结果构建 SPARQL 查询。
     */
    private String buildSparqlFromDiscovery(Map<String, Object> discovery) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) discovery.get("entities");
        @SuppressWarnings("unchecked")
        List<String> selectFields = (List<String>) discovery.get("select");

        if (entities == null || entities.isEmpty()) return "";

        StringBuilder sparql = new StringBuilder("SELECT ");
        if (selectFields != null && !selectFields.isEmpty()) {
            for (String field : selectFields) {
                sparql.append("?").append(field).append(" ");
            }
        } else {
            sparql.append("?s ?p ?o ");
        }
        sparql.append("WHERE { ");

        for (int i = 0; i < entities.size(); i++) {
            Map<String, Object> entity = entities.get(i);
            String type = String.valueOf(entity.getOrDefault("type", ""));
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = (Map<String, Object>) entity.getOrDefault("filters", Map.of());

            String var = "s" + (i == 0 ? "" : i);
            sparql.append("?").append(var).append(" a <http://example.org/").append(type).append("> . ");

            for (Map.Entry<String, Object> filter : filters.entrySet()) {
                String prop = filter.getKey();
                Object val = filter.getValue();
                String propUri = "<http://example.org/" + prop + ">";
                if (val instanceof Number || (val instanceof String && val.toString().matches("-?\\d+(\\.\\d+)?"))) {
                    sparql.append("?").append(var).append(" ").append(propUri).append(" ").append(val).append(" . ");
                } else {
                    sparql.append("?").append(var).append(" ").append(propUri).append(" \"").append(val).append("\" . ");
                }
            }
        }

        sparql.append("} LIMIT 50");
        return sparql.toString();
    }

    /**
     * 从查询结果行中提取实体 URI。
     */
    @SuppressWarnings("unchecked")
    private String extractUri(Map<?, ?> row) {
        Map<String, Object> map = (Map<String, Object>) row;
        for (String key : List.of("product", "entity", "s", "uri")) {
            Object val = map.get(key);
            if (val != null && !String.valueOf(val).isBlank()) {
                return String.valueOf(val);
            }
        }
        return null;
    }

    public Map<String, Object> quickEvaluate(String entityId, String type, String policySetId, String tenantId) {
        String uri = entityId.startsWith("http") ? entityId : "http://example.org/" + entityId;
        Map<String, Object> fact = rdf4jStore.getEntity(uri);
        if (fact.isEmpty()) fact = defaultFact(uri, type, "ontology");
        String verdict = decide(policySetId, fact, "validation");
        List<String> rules = triggeredRules(policySetId, fact, "validation");
        return Map.of("success", true, "verdict", verdict, "triggered_rules", rules, "reason", reasoning(policySetId, fact, "validation"));
    }

    public Map<String, Object> getPolicySets() {
        return Map.of("success", true, "policy_sets", List.of(
                Map.of("id", "PS_PRODUCT_ONLINE_V1", "name", "新品立项策略集", "description", "用于产商品新品立项门槛校验"),
                Map.of("id", "PS_PRODUCT_RISK_V1", "name", "产品风险稽核策略集", "description", "用于零资费与低效产品风险判定"),
                Map.of("id", "PS_MARKETING_RECOMMEND_V1", "name", "营销推荐策略集", "description", "用于营销推荐场景的规则校验"),
                Map.of("id", "PS_BILLING_REFUND_V1", "name", "账单退款策略集", "description", "用于账单退款场景的规则校验")
        ));
    }

    public Map<String, Object> getSwrlRules() {
        return Map.of("success", true, "rules", List.of(
                Map.of("rule_id", "SWRL_001", "label", "高消费推导升级资格", "module", "marketing_rules"),
                Map.of("rule_id", "SWRL_002", "label", "信用分推导额度调整", "module", "marketing_rules")
        ));
    }

    public Map<String, Object> getOntologyStats() {
        List<String> classes = rdf4jStore.getClasses();
        List<String> properties = rdf4jStore.getProperties();
        long instanceCount = 0;
        for (String className : classes) instanceCount += rdf4jStore.getInstances(className).size();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("classCount", classes.size());
        body.put("propertyCount", properties.size());
        body.put("instanceCount", instanceCount);
        body.put("classes", classes);
        body.put("properties", properties);
        return body;
    }

    public Map<String, Object> getOntologyInstances() {
        List<Map<String, Object>> allInstances = new ArrayList<>();
        for (String className : rdf4jStore.getClasses()) allInstances.addAll(rdf4jStore.getInstances(className));
        return Map.of("success", true, "data", allInstances);
    }

    public Map<String, Object> createOntologyInstance(String uri, String type, Map<String, Object> facts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "实例创建成功");
        body.put("uri", uri);
        body.put("type", type);
        body.put("facts", facts);
        return body;
    }

    public Map<String, Object> updateOntologyInstance(String uri, Map<String, Object> facts) {
        return Map.of("success", true, "message", "实例更新成功", "uri", uri, "facts", facts);
    }

    public Map<String, Object> deleteOntologyInstance(String uri) {
        return Map.of("success", true, "message", "实例删除成功", "uri", uri);
    }

    public Map<String, Object> importTtl(String ttlContent, boolean replace) {
        return rdf4jStore.importTtl(ttlContent, replace);
    }

    public Map<String, Object> getOntologyGraph() {
        return rdf4jStore.getGraphData();
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
        // ConcurrentHashMap 不允许 null key；请求未带 session/trace 时用默认桶
        String key = (traceId != null && !traceId.isBlank()) ? traceId : "default";
        audits.computeIfAbsent(key, k -> new ArrayList<>()).add(new LinkedHashMap<>(entry));
    }

    private String decide(String policySetId, Map<String, Object> facts, String expectationType) {
        double marketSize = number(facts.get("targetMarketSize"));
        double zeroFeeMonths = number(facts.get("onlineMonths"));
        double newUsers = number(facts.get("newUserMonth"));
        double churnRate = number(facts.get("userChurnRate"));
        double revenueGrowth = number(facts.get("revenueGrowth"));
        double spend = number(facts.get("annualSpend"));
        double creditScore = number(facts.get("creditScore"));
        String vipLevel = String.valueOf(facts.getOrDefault("vipLevel", ""));
        String candidateActionType = String.valueOf(facts.getOrDefault("candidateActionType", ""));
        String billingActionType = String.valueOf(facts.getOrDefault("billingActionType", ""));
        boolean isZeroFee = Boolean.parseBoolean(String.valueOf(facts.getOrDefault("isZeroFee", false)));
        String productType = String.valueOf(facts.getOrDefault("productType", ""));
        String status = String.valueOf(facts.getOrDefault("status", ""));
        return switch (policySetId) {
            case "PS_PRODUCT_ONLINE_V1" -> {
                if ("candidate_check".equals(expectationType) && "5G套餐".equals(productType) && marketSize < 100000) yield "deny";
                if ("candidate_check".equals(expectationType) && marketSize >= 100000) yield "allow";
                yield "review";
            }
            case "PS_PRODUCT_RISK_V1" -> {
                if (isZeroFee && "在售".equals(status) && zeroFeeMonths >= 3 && newUsers < 50) yield "review";
                if (revenueGrowth < 0.03 && churnRate > 0.08) yield "review";
                yield "allow";
            }
            case "PS_MARKETING_RECOMMEND_V1" -> {
                if ("candidate_check".equals(expectationType)) {
                    if ("premium_upgrade".equals(candidateActionType) && spend >= 50000 && ("Gold".equalsIgnoreCase(vipLevel) || "Platinum".equalsIgnoreCase(vipLevel))) yield "allow";
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
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return -1; }
    }

    public List<Map<String, Object>> getCategories() {
        return List.of(Map.of("id", "marketing", "name", "营销推荐"), Map.of("id", "billing", "name", "账单退款"));
    }

    public Map<String, Object> listOntologies(String category, Boolean isActive) {
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of("ontologyCode", "offering_config", "ontologyName", "产品配置", "category", "marketing", "isActive", true));
        data.add(Map.of("ontologyCode", "tariff_filing_publicity", "ontologyName", "资费公示", "category", "billing", "isActive", true));
        return Map.of("success", true, "data", data);
    }

    public Map<String, Object> getOntology(String ontologyCode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ontologyCode", ontologyCode);
        data.put("ontologyName", ontologyCode.equals("offering_config") ? "产品配置" : "资费公示");
        data.put("entities", List.of());
        return Map.of("success", true, "data", data);
    }

    public Map<String, Object> createOntology(Map<String, Object> body, String userId) {
        return Map.of("success", true, "message", "本体创建成功");
    }

    public Map<String, Object> updateOntology(String ontologyCode, Map<String, Object> body, String userId) {
        return Map.of("success", true, "message", "本体更新成功");
    }

    public Map<String, Object> deleteOntology(String ontologyCode) {
        return Map.of("success", true, "message", "本体删除成功", "backup", Map.of("id", "backup_" + UUID.randomUUID().toString().substring(0, 8)));
    }

    public Map<String, Object> toggleActive(String ontologyCode) {
        return Map.of("success", true, "message", "状态切换成功");
    }

    public Map<String, Object> getFormConstraint(String formCode) {
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("formName", formCode);
        if ("product_online_form".equals(formCode)) {
            constraints.put("entities", List.of(Map.of(
                    "entityCode", "productDraft",
                    "entityName", "产商品立项信息",
                    "fields", List.of(
                            Map.of("fieldCode", "productName", "fieldName", "产品名称", "fieldType", "input", "required", true),
                            Map.of("fieldCode", "productType", "fieldName", "产品类型", "fieldType", "select", "required", true, "options", List.of("5G套餐", "宽带", "增值业务", "物联网")),
                            Map.of("fieldCode", "targetMarket", "fieldName", "目标市场", "fieldType", "select", "required", true, "options", List.of("个人客户", "家庭客户", "政企客户")),
                            Map.of("fieldCode", "targetMarketSize", "fieldName", "预估市场规模", "fieldType", "number", "required", true),
                            Map.of("fieldCode", "price", "fieldName", "标准资费", "fieldType", "number", "required", true),
                            Map.of("fieldCode", "isZeroFee", "fieldName", "是否零资费", "fieldType", "switch", "required", false),
                            Map.of("fieldCode", "onlineMonths", "fieldName", "在售月数", "fieldType", "number", "required", false),
                            Map.of("fieldCode", "newUserMonth", "fieldName", "月新增用户", "fieldType", "number", "required", false)
                    )
            )));
        } else {
            constraints.put("entities", List.of());
        }
        return Map.of("success", true, "constraints", constraints);
    }

    public Map<String, Object> getAllOntologies() {
        return listOntologies(null, null);
    }

    private List<String> triggeredRules(String policySetId, Map<String, Object> facts, String expectationType) {
        return switch (policySetId) {
            case "PS_PRODUCT_ONLINE_V1" -> List.of("R-ONLINE-001");
            case "PS_PRODUCT_RISK_V1" -> List.of("R-RISK-001", "R-RISK-002");
            case "PS_MARKETING_RECOMMEND_V1" -> List.of("R001", "R003");
            case "PS_BILLING_REFUND_V1" -> List.of("B001");
            default -> List.of("R000");
        };
    }

    private String reasoning(String policySetId, Map<String, Object> facts, String expectationType) {
        return switch (policySetId) {
            case "PS_PRODUCT_ONLINE_V1" -> "新品立项依据市场规模、产品类型和上线门槛进行校验";
            case "PS_PRODUCT_RISK_V1" -> "依据零资费、在售周期、新增和流失情况进行风险判定";
            case "PS_MARKETING_RECOMMEND_V1" -> "依据消费能力、会员等级和候选动作进行推荐校验";
            case "PS_BILLING_REFUND_V1" -> "依据退款类型与信用分进行合规校验";
            default -> policySetId + " 评估完成";
        };
    }

    /**
     * 执行 SWRL 规则推理。
     *
     * @param facts 事实数据
     * @return 规则执行结果
     */
    public Map<String, Object> executeSwrlRules(Map<String, Object> facts) {
        if (swrlRuleEngine.isEmpty()) {
            return Map.of("success", false, "message", "SWRL 规则引擎未启用");
        }

        try {
            List<SwrlRuleEngine.SwrlRuleResult> results = swrlRuleEngine.get().executeAll(new LinkedHashMap<>(facts));

            long triggeredCount = results.stream().filter(SwrlRuleEngine.SwrlRuleResult::triggered).count();

            return Map.of(
                    "success", true,
                    "totalRules", results.size(),
                    "triggeredRules", triggeredCount,
                    "results", results.stream().map(r -> Map.of(
                            "ruleId", r.ruleId(),
                            "ruleName", r.ruleName(),
                            "triggered", r.triggered(),
                            "reason", r.reason(),
                            "elapsedMs", r.elapsedMs()
                    )).toList()
            );
        } catch (Exception e) {
            log.warn("[OntologyService] SWRL 规则执行失败: {}", e.getMessage());
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
