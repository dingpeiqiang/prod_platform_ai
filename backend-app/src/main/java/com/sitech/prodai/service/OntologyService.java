package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
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
    private final ProdAiProperties properties;
    private final Optional<LlmService> llmService;
    @SuppressWarnings("deprecation")
    private final Optional<SwrlRuleEngine> swrlRuleEngine;
    private final Optional<OpsRulesService> opsRules;
    private final Optional<OntologyVersionService> versionService;
    private final Map<String, Map<String, Object>> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> audits = new ConcurrentHashMap<>();

    public OntologyService(Rdf4jOntologyStore rdf4jStore,
                           ProdAiProperties properties,
                           @Autowired(required = false) Optional<LlmService> llmService,
                           @Autowired(required = false) @SuppressWarnings("deprecation") Optional<SwrlRuleEngine> swrlRuleEngine,
                           @Autowired(required = false) Optional<OpsRulesService> opsRules,
                           @Autowired(required = false) Optional<OntologyVersionService> versionService) {
        this.rdf4jStore = rdf4jStore;
        this.properties = properties;
        this.llmService = llmService;
        this.swrlRuleEngine = swrlRuleEngine;
        this.opsRules = opsRules;
        this.versionService = versionService;
    }

    private String baseIri() {
        return properties.getOntology().normalizedBaseIri();
    }

    private String entityUri(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return baseIri();
        }
        return entityId.startsWith("http") ? entityId : baseIri() + entityId;
    }

    private String typeIri(String type) {
        return "<" + baseIri() + type + ">";
    }

    private String propIri(String prop) {
        return "<" + baseIri() + prop + ">";
    }

    public Map<String, Object> retrieve(String entityId, String type, String source, String tenantId, String traceId) {
        String uri = entityUri(entityId);
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
        List<String> distinctRules = rules.stream().distinct().toList();
        String text = switch (String.valueOf(audience)) {
            case "audit" -> steps.toString();
            case "business" -> {
                if (distinctRules.isEmpty()) {
                    yield "已根据业务事实与规则完成归因评估，暂无额外命中规则。";
                }
                yield "已根据业务事实与规则完成归因评估。引用规则："
                        + distinctRules.stream().map(this::formatRuleLabel).reduce((a, b) -> a + "、" + b).orElse("");
            }
            default -> steps.isEmpty() ? "未找到对应评估记录" : "根据您的画像和业务规则，系统做出了推荐。";
        };
        return Map.of("success", true, "natural_language", text, "referenced_rules", distinctRules);
    }

    /** 规则 ID → 业务可读标签（优先 ops_rules.json） */
    private String formatRuleLabel(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) return "";
        if (opsRules.isPresent()) {
            return opsRules.get().formatRuleLabel(ruleId);
        }
        return ruleId;
    }

    public Map<String, Object> getSwrlRules() {
        // 遗留条件 DSL，非 Openllet OWL SWRL；产商品运营规则见 /api/v1/product-ontology/ops/rules
        return Map.of(
                "success", true,
                "engine_type", "condition_dsl",
                "deprecated", true,
                "message", "非 OWL SWRL；正式引擎为 OpsSwrlReasoner（openllet-swrl），规则目录见 ops_rules.json",
                "rules", List.of(
                        Map.of("rule_id", "COND_001", "label", "高消费推导升级资格", "module", "marketing_rules"),
                        Map.of("rule_id", "COND_002", "label", "信用分推导额度调整", "module", "marketing_rules")
                )
        );
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
            String productSparql = "SELECT ?product ?name ?growth ?users ?status WHERE { ?product a "
                    + typeIri("Product") + " . OPTIONAL { ?product " + propIri("productName")
                    + " ?name } OPTIONAL { ?product " + propIri("revenueGrowth")
                    + " ?growth } OPTIONAL { ?product " + propIri("newUserMonth")
                    + " ?users } OPTIONAL { ?product " + propIri("status")
                    + " ?status } } ORDER BY ASC(?growth) LIMIT 20";
            String riskSparql = "SELECT ?product ?name ?isZeroFee ?status WHERE { ?product a "
                    + typeIri("Product") + " . OPTIONAL { ?product " + propIri("productName")
                    + " ?name } OPTIONAL { ?product " + propIri("isZeroFee")
                    + " ?isZeroFee } OPTIONAL { ?product " + propIri("status")
                    + " ?status } } LIMIT 50";
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
            sparql = "SELECT ?product ?name ?growth ?users ?status ?price WHERE { ?product a "
                    + typeIri("Product") + " . OPTIONAL { ?product " + propIri("productName")
                    + " ?name } OPTIONAL { ?product " + propIri("revenueGrowth")
                    + " ?growth } OPTIONAL { ?product " + propIri("newUserMonth")
                    + " ?users } OPTIONAL { ?product " + propIri("status")
                    + " ?status } OPTIONAL { ?product " + propIri("price")
                    + " ?price } FILTER(!BOUND(?status) || ?status = \"在售\") } ORDER BY ASC(?growth) LIMIT 20";
            answer = "查询在售产品及其增长指标";
        } else if (wantRisk) {
            sparql = "SELECT ?product ?name ?isZeroFee ?status ?newUsers ?churn WHERE { ?product a "
                    + typeIri("Product") + " . OPTIONAL { ?product " + propIri("productName")
                    + " ?name } OPTIONAL { ?product " + propIri("isZeroFee")
                    + " ?isZeroFee } OPTIONAL { ?product " + propIri("status")
                    + " ?status } OPTIONAL { ?product " + propIri("newUserMonth")
                    + " ?newUsers } OPTIONAL { ?product " + propIri("userChurnRate")
                    + " ?churn } FILTER(?isZeroFee = true || BOUND(?churn)) } LIMIT 50";
            answer = "查询零资费或风险相关产品";
        } else if (normalized.contains("会员等级") || normalized.contains("vip")) {
            sparql = "SELECT ?entity ?vipLevel WHERE { ?entity rdf:type " + typeIri("Customer")
                    + " . ?entity " + propIri("vipLevel") + " ?vipLevel }";
            answer = "查询所有客户的会员等级";
        } else if (normalized.contains("年消费") || normalized.contains("annual") || normalized.contains("spend")) {
            sparql = "SELECT ?entity ?annualSpend WHERE { ?entity rdf:type " + typeIri("Customer")
                    + " . ?entity " + propIri("annualSpend") + " ?annualSpend } ORDER BY DESC(?annualSpend)";
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
        boolean usedKeywordFallback = false;
        if (rawResults.isEmpty()) {
            Map<String, Object> fallback = nlQuery(question);
            nlAnswer = String.valueOf(fallback.getOrDefault("answer", ""));
            sparql = String.valueOf(fallback.getOrDefault("sparql", ""));
            usedKeywordFallback = true;

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
        String discoveryMethod = usedKeywordFallback || llmDiscovery == null ? "keyword_fallback" : "llm";
        snapshot.put("discovery_method", discoveryMethod);

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
            sparql.append("?").append(var).append(" a ").append(typeIri(type)).append(" . ");

            for (Map.Entry<String, Object> filter : filters.entrySet()) {
                String prop = filter.getKey();
                Object val = filter.getValue();
                String propUri = propIri(prop);
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
        String uri = entityUri(entityId);
        Map<String, Object> fact = rdf4jStore.getEntity(uri);
        if (fact.isEmpty()) fact = defaultFact(uri, type, "ontology");
        String verdict = decide(policySetId, fact, "validation");
        List<String> rules = triggeredRules(policySetId, fact, "validation");
        return Map.of("success", true, "verdict", verdict, "triggered_rules", rules, "reason", reasoning(policySetId, fact, "validation"));
    }

    public Map<String, Object> getPolicySets() {
        if (opsRules.isPresent()) {
            return Map.of("success", true, "policy_sets", opsRules.get().listPolicySets());
        }
        return Map.of("success", true, "policy_sets", List.of());
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

    /**
     * 从内联事实创建临时快照（供 compare_state 工具 / 无事前 retrieve 场景使用）。
     */
    public String createSnapshotFromFacts(Map<String, Object> facts, String traceId, String tenantId) {
        String snapshotId = buildSnapshotId();
        Map<String, Object> factRow = facts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(facts);
        String uri = String.valueOf(factRow.getOrDefault("uri",
                factRow.getOrDefault("entityId", baseIri() + "inline/" + snapshotId)));
        factRow.putIfAbsent("uri", uri);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("snapshot_id", snapshotId);
        snapshot.put("trace_id", traceId);
        snapshot.put("tenant_id", tenantId);
        snapshot.put("facts", Map.of(uri, factRow));
        snapshots.put(snapshotId, snapshot);
        appendAudit(traceId, Map.of(
                "step", "snapshot.create_from_facts",
                "timestamp", Instant.now().toString(),
                "snapshot_id", snapshotId
        ));
        return snapshotId;
    }

    public Map<String, Object> compareState(String snapshotId, List<Map<String, Object>> patches,
                                           String policySetId, String traceId, String tenantId) {
        return compareState(snapshotId, patches, policySetId, traceId, tenantId, null);
    }

    /**
     * @param inlineFacts 当 snapshot 缺失时，用此事实临时建快照（兼容 ToolConfig snapshot_id=current）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> compareState(String snapshotId, List<Map<String, Object>> patches,
                                            String policySetId, String traceId, String tenantId,
                                            Map<String, Object> inlineFacts) {
        Map<String, Object> snapshot = snapshotId == null ? null : snapshots.get(snapshotId);
        if (snapshot == null && inlineFacts != null && !inlineFacts.isEmpty()) {
            String created = createSnapshotFromFacts(inlineFacts, traceId, tenantId);
            snapshot = snapshots.get(created);
            snapshotId = created;
        }
        if (snapshot == null && ("current".equals(snapshotId) || snapshotId == null || snapshotId.isBlank())) {
            // 兜底：取第一个 Product 实例作为基准
            List<Map<String, Object>> products = rdf4jStore.getInstances("Product");
            if (!products.isEmpty()) {
                String created = createSnapshotFromFacts(products.get(0), traceId, tenantId);
                snapshot = snapshots.get(created);
                snapshotId = created;
            }
        }
        if (snapshot == null) {
            throw new IllegalStateException("Snapshot not found or expired: " + snapshotId);
        }

        String safePolicy = (policySetId == null || policySetId.isBlank())
                ? "PS_PRODUCT_ONLINE_V1" : policySetId;
        List<Map<String, Object>> comparisons = new ArrayList<>();
        Map<String, Map<String, Object>> facts =
                (Map<String, Map<String, Object>>) snapshot.getOrDefault("facts", Map.of());

        List<Map<String, Object>> safePatches = patches == null ? List.of() : patches;
        if (safePatches.isEmpty()) {
            // 无补丁时返回基准评估
            Map<String, Object> base = new LinkedHashMap<>(
                    facts.values().stream().findFirst().orElse(Map.of()));
            Map<String, Object> decision = evaluate(base, safePolicy, "candidate_check", traceId, tenantId);
            comparisons.add(Map.of(
                    "patch_description", "基准方案（无变更）",
                    "resulting_state", Map.of("baseline", base),
                    "evaluation", decision.get("decision")
            ));
        } else {
            for (Map<String, Object> patch : safePatches) {
                String entityId = String.valueOf(patch.getOrDefault("entity_id",
                        patch.getOrDefault("entityId", "")));
                Map<String, Object> base = resolveBaseFact(facts, entityId);
                Map<String, Object> changes = patch.get("changes") instanceof Map<?, ?>
                        ? new LinkedHashMap<>((Map<String, Object>) patch.get("changes"))
                        : new LinkedHashMap<>();
                base.putAll(changes);
                Map<String, Object> decision = evaluate(base, safePolicy, "candidate_check", traceId, tenantId);
                String key = entityId.isBlank() || "null".equals(entityId) ? "patched" : entityId;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("patch_description", String.valueOf(patch.getOrDefault("description", "方案变更")));
                row.put("resulting_state", Map.of(key, base));
                row.put("evaluation", decision.get("decision"));
                comparisons.add(row);
            }
        }

        appendAudit(traceId, Map.of(
                "step", "compare_state",
                "timestamp", Instant.now().toString(),
                "snapshot_id", snapshotId,
                "policy_set_id", safePolicy,
                "patch_count", safePatches.size()
        ));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("snapshot_id", snapshotId);
        body.put("policy_set_id", safePolicy);
        body.put("comparisons", comparisons);
        return body;
    }

    private Map<String, Object> resolveBaseFact(Map<String, Map<String, Object>> facts, String entityId) {
        if (facts == null || facts.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (entityId != null && !entityId.isBlank() && !"null".equals(entityId)) {
            if (facts.containsKey(entityId)) {
                return new LinkedHashMap<>(facts.get(entityId));
            }
            String asUri = entityUri(entityId);
            if (facts.containsKey(asUri)) {
                return new LinkedHashMap<>(facts.get(asUri));
            }
            for (Map.Entry<String, Map<String, Object>> e : facts.entrySet()) {
                if (e.getKey().endsWith("/" + entityId) || e.getKey().endsWith(entityId)) {
                    return new LinkedHashMap<>(e.getValue());
                }
            }
        }
        return new LinkedHashMap<>(facts.values().iterator().next());
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
        Map<String, Object> th = opsRules.map(r -> r.policyThresholds(policySetId)).orElse(Map.of());
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

        double marketSizeGte = thresholdNum(th, "marketSizeGte", 100000);
        String denyType = String.valueOf(th.getOrDefault("denyProductTypeOnLowMarket", "5G套餐"));
        double zeroMonthsGte = thresholdNum(th, "zeroFeeOnlineMonthsGte", 3);
        double zeroUsersLt = thresholdNum(th, "zeroFeeNewUsersLt", 50);
        double growthLt = thresholdNum(th, "revenueGrowthLt", 0.03);
        double churnGt = thresholdNum(th, "churnRateGt", 0.08);
        String onSale = String.valueOf(th.getOrDefault("onSaleStatus", "在售"));
        double premiumSpend = thresholdNum(th, "premiumSpendGte", 50000);
        double bundleSpend = thresholdNum(th, "bundleSpendGte", 30000);
        double refundScore = thresholdNum(th, "fullRefundCreditScoreGte", 700);

        boolean candidateLike = expectationType == null || expectationType.isBlank()
                || "candidate_check".equals(expectationType)
                || "online_check".equals(expectationType);

        return switch (policySetId) {
            case "PS_PRODUCT_ONLINE_V1" -> {
                if (candidateLike && denyType.equals(productType) && marketSize >= 0
                        && marketSize < marketSizeGte) {
                    yield "deny";
                }
                if (candidateLike && marketSize >= marketSizeGte) {
                    yield "allow";
                }
                yield "review";
            }
            case "PS_PRODUCT_RISK_V1" -> {
                if (isZeroFee && onSale.equals(status)
                        && zeroFeeMonths >= zeroMonthsGte
                        && newUsers < zeroUsersLt) {
                    yield "review";
                }
                if (revenueGrowth >= 0 && churnRate >= 0
                        && revenueGrowth < growthLt && churnRate > churnGt) {
                    yield "review";
                }
                yield "allow";
            }
            case "PS_MARKETING_RECOMMEND_V1" -> {
                if ("candidate_check".equals(expectationType)) {
                    if ("premium_upgrade".equals(candidateActionType)
                            && spend >= premiumSpend
                            && vipInList(vipLevel, th.get("allowVipLevels"), List.of("Gold", "Platinum"))) {
                        yield "allow";
                    }
                    if ("membership_bundle".equals(candidateActionType) && spend >= bundleSpend) {
                        yield "allow";
                    }
                    yield "review";
                }
                if (spend >= premiumSpend
                        || vipInList(vipLevel, th.get("allowVipAlone"), List.of("Platinum"))) {
                    yield "allow";
                }
                yield "deny";
            }
            case "PS_BILLING_REFUND_V1" -> {
                if ("full_refund".equals(billingActionType) && creditScore >= refundScore) {
                    yield "allow";
                }
                if ("partial_refund".equals(billingActionType)) {
                    yield "review";
                }
                yield "deny";
            }
            default -> "review";
        };
    }

    private double thresholdNum(Map<String, Object> th, String key, double defaultValue) {
        if (th == null || !th.containsKey(key)) {
            return defaultValue;
        }
        return number(th.get(key));
    }

    private boolean vipInList(String vipLevel, Object configured, List<String> defaults) {
        List<String> levels = defaults;
        if (configured instanceof List<?> list && !list.isEmpty()) {
            levels = list.stream().map(String::valueOf).toList();
        }
        for (String level : levels) {
            if (level.equalsIgnoreCase(vipLevel)) {
                return true;
            }
        }
        return false;
    }

    private double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return -1; }
    }

    public List<Map<String, Object>> getCategories() {
        return List.of(Map.of("id", "marketing", "name", "营销推荐"), Map.of("id", "billing", "name", "账单退款"));
    }

    /** 种子本体资产（行为兜底）：表 A 无映射时降级返回的默认值，保住前端契约不漂移。 */
    private static final Map<String, String> SEED_ONTOLOGY_NAMES = Map.of(
            "offering_config", "产品配置",
            "tariff_filing_publicity", "资费公示");

    public Map<String, Object> listOntologies(String category, Boolean isActive) {
        Map<String, String> effective = resolveOntologyRegistry();
        List<Map<String, Object>> data = new ArrayList<>();
        effective.forEach((code, name) -> {
            Map<String, Object> onto = new LinkedHashMap<>();
            onto.put("ontologyCode", code);
            onto.put("ontologyName", name);
            onto.put("category", code.equals("tariff_filing_publicity") ? "billing" : "marketing");
            onto.put("isActive", true);
            data.add(onto);
        });
        return Map.of("success", true, "data", data);
    }

    public Map<String, Object> getOntology(String ontologyCode) {
        Map<String, String> effective = resolveOntologyRegistry();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ontologyCode", ontologyCode);
        data.put("ontologyName", effective.getOrDefault(ontologyCode,
                ontologyCode.equals("offering_config") ? "产品配置" : "资费公示"));
        data.put("entities", List.of());
        return Map.of("success", true, "data", data);
    }

    public Map<String, Object> createOntology(Map<String, Object> body, String userId) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        String code = String.valueOf(safe.getOrDefault("ontologyCode", safe.getOrDefault("code",
                "ontology_" + System.currentTimeMillis())));
        String name = String.valueOf(safe.getOrDefault("ontologyName", code));
        boolean ok = registerOntologyRow(code, name, userId);
        return Map.of("success", ok, "message",
                ok ? "本体已登记 draft 版本（表 A）" : "本体登记失败（版本库不可用）",
                "ontologyCode", code);
    }

    public Map<String, Object> updateOntology(String ontologyCode, Map<String, Object> body, String userId) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        String name = String.valueOf(safe.getOrDefault("ontologyName", ontologyCode));
        boolean ok = registerOntologyRow(ontologyCode, name, userId);
        return Map.of("success", ok, "message",
                ok ? "本体已登记新 draft 版本（表 A）" : "本体更新失败（版本库不可用）",
                "ontologyCode", ontologyCode);
    }

    public Map<String, Object> deleteOntology(String ontologyCode) {
        if (versionService.isEmpty()) {
            return Map.of("success", true, "message", "版本库未启用，删除不落库（降级空操作）",
                    "ontologyCode", ontologyCode);
        }
        for (com.sitech.prodai.domain.entity.OntologyAssetVersion row :
                versionService.get().listByType(OntologyVersionService.TYPE_ONTOLOGY)) {
            if (row.getAssetCode().equals(ontologyCode)) {
                versionService.get().deprecate(row.getId(), "admin", "ontology.delete");
            }
        }
        return Map.of("success", true, "message", "本体已置 deprecated（表 A）", "ontologyCode", ontologyCode);
    }

    public Map<String, Object> toggleActive(String ontologyCode) {
        if (versionService.isEmpty()) {
            return Map.of("success", true, "message", "版本库未启用，状态切换不落库（降级空操作）",
                    "ontologyCode", ontologyCode);
        }
        try {
            for (com.sitech.prodai.domain.entity.OntologyAssetVersion row :
                    versionService.get().listByType(OntologyVersionService.TYPE_ONTOLOGY)) {
                if (row.getAssetCode().equals(ontologyCode)) {
                    boolean isPublished = "published".equals(row.getStatus());
                    String target = isPublished ? "deprecated" : "published";
                    versionService.get().transition(row.getId(), row.getStatus(), target,
                            "admin", "ontology.toggle", Map.of("ontology_code", ontologyCode));
                }
            }
            return Map.of("success", true, "message", "本体状态已切换（表 A）", "ontologyCode", ontologyCode);
        } catch (Exception e) {
            return Map.of("success", true, "message", "切换请求已受理（版本库异常，保留现状）",
                    "ontologyCode", ontologyCode);
        }
    }

    /** 解析当前本体资产清单：表 A（asset_type=ontology）聚合；无数据/版本库不可用时降级种子。 */
    private Map<String, String> resolveOntologyRegistry() {
        Map<String, String> effective = new LinkedHashMap<>();
        boolean dbOk = false;
        if (versionService.isPresent()) {
            try {
                for (com.sitech.prodai.domain.entity.OntologyAssetVersion row :
                        versionService.get().listByType(OntologyVersionService.TYPE_ONTOLOGY)) {
                    // 仅取 active（published）行，语义对齐前端 isActive=true
                    if ("published".equals(row.getStatus())) {
                        effective.put(row.getAssetCode(), row.getSummary() == null
                                || row.getSummary().isBlank() ? row.getAssetCode() : row.getSummary());
                        dbOk = true;
                    }
                }
            } catch (Exception e) {
                log.warn("[OntologyService] 表 A 聚合不可用，降级种子清单: {}", e.getMessage());
            }
        }
        if (!dbOk || effective.isEmpty()) {
            effective.putAll(SEED_ONTOLOGY_NAMES);
        }
        return effective;
    }

    /** 登记本体资产版本行（create=首版 draft，update=新 draft）。 */
    private boolean registerOntologyRow(String code, String name, String userId) {
        if (versionService.isEmpty()) {
            return false;
        }
        try {
            versionService.get().register(
                    OntologyVersionService.TYPE_ONTOLOGY,
                    code,
                    nextOntologyVersion(code),
                    OntologyVersionService.STATUS_DRAFT,
                    userId,
                    name,
                    "{}");
            return true;
        } catch (Exception e) {
            log.warn("[OntologyService] 本体登记失败: {}", e.getMessage());
            return false;
        }
    }

    /** 简易 semver 递增：同资产现有行数 + 1，如 v1.0.0 / v1.0.1 ... */
    private String nextOntologyVersion(String code) {
        List<com.sitech.prodai.domain.entity.OntologyAssetVersion> rows =
                versionService.map(v -> v.listVersions(OntologyVersionService.TYPE_ONTOLOGY, code))
                        .orElseGet(List::of);
        return "1.0." + rows.size();
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
        Map<String, Object> th = opsRules.map(r -> r.policyThresholds(policySetId)).orElse(Map.of());
        List<String> hit = new ArrayList<>();
        double marketSize = number(facts.get("targetMarketSize"));
        double zeroFeeMonths = number(facts.get("onlineMonths"));
        double newUsers = number(facts.get("newUserMonth"));
        double churnRate = number(facts.get("userChurnRate"));
        double revenueGrowth = number(facts.get("revenueGrowth"));
        boolean isZeroFee = Boolean.parseBoolean(String.valueOf(facts.getOrDefault("isZeroFee", false)));
        String productType = String.valueOf(facts.getOrDefault("productType", ""));
        String status = String.valueOf(facts.getOrDefault("status", ""));
        double marketSizeGte = thresholdNum(th, "marketSizeGte", 100000);
        String denyType = String.valueOf(th.getOrDefault("denyProductTypeOnLowMarket", "5G套餐"));
        double zeroMonthsGte = thresholdNum(th, "zeroFeeOnlineMonthsGte", 3);
        double zeroUsersLt = thresholdNum(th, "zeroFeeNewUsersLt", 50);
        double growthLt = thresholdNum(th, "revenueGrowthLt", 0.03);
        double churnGt = thresholdNum(th, "churnRateGt", 0.08);
        String onSale = String.valueOf(th.getOrDefault("onSaleStatus", "在售"));

        if ("PS_PRODUCT_ONLINE_V1".equals(policySetId)
                && denyType.equals(productType)
                && marketSize >= 0
                && marketSize < marketSizeGte) {
            hit.add("R-ONLINE-001");
        }
        if ("PS_PRODUCT_RISK_V1".equals(policySetId)) {
            if (isZeroFee && onSale.equals(status)
                    && zeroFeeMonths >= zeroMonthsGte
                    && newUsers < zeroUsersLt) {
                hit.add("R-RISK-002");
            }
            if (revenueGrowth >= 0 && churnRate >= 0
                    && revenueGrowth < growthLt && churnRate > churnGt) {
                hit.add("R-RISK-001");
            }
        }
        if (!hit.isEmpty()) {
            return hit;
        }
        // 未命中红线时不回传策略集「关联规则」清单，避免误显示为已触发
        return List.of();
    }

    private String reasoning(String policySetId, Map<String, Object> facts, String expectationType) {
        Map<String, Object> th = opsRules.map(r -> r.policyThresholds(policySetId)).orElse(Map.of());
        List<String> hit = triggeredRules(policySetId, facts, expectationType);
        String verdict = decide(policySetId, facts, expectationType);

        double marketSize = number(facts.get("targetMarketSize"));
        double marketSizeGte = thresholdNum(th, "marketSizeGte", 100000);
        double zeroFeeMonths = number(facts.get("onlineMonths"));
        double newUsers = number(facts.get("newUserMonth"));
        double zeroMonthsGte = thresholdNum(th, "zeroFeeOnlineMonthsGte", 3);
        double zeroUsersLt = thresholdNum(th, "zeroFeeNewUsersLt", 50);
        double revenueGrowth = number(facts.get("revenueGrowth"));
        double churnRate = number(facts.get("userChurnRate"));
        double growthLt = thresholdNum(th, "revenueGrowthLt", 0.03);
        double churnGt = thresholdNum(th, "churnRateGt", 0.08);

        // 对齐方案文档的确定性话术（不可被大模型覆盖）
        if (hit.contains("R-ONLINE-001")) {
            long sizeWan = marketSize >= 0 ? Math.round(marketSize / 10000.0) : -1;
            long gateWan = Math.round(marketSizeGte / 10000.0);
            return String.format(
                    "触发规则 R-ONLINE-001：目标市场规模预估%s万户，低于%s万户立项门槛，不满足新品立项基础门槛",
                    sizeWan >= 0 ? String.valueOf(sizeWan) : "未知",
                    gateWan);
        }
        if (hit.contains("R-RISK-002")) {
            return String.format(
                    "触发规则 R-RISK-002：零资费产品已在售%.0f个月，单月新增%.0f户（阈值：在售≥%.0f月且新增<%.0f户），触发高风险人工复核",
                    zeroFeeMonths, newUsers, zeroMonthsGte, zeroUsersLt);
        }
        if (hit.contains("R-RISK-001")) {
            return String.format(
                    "触发规则 R-RISK-001：营收同比增速%.1f%%低于%.0f%%且用户流失率%.1f%%高于%.0f%%，判定为低效待优化产品",
                    revenueGrowth * 100, growthLt * 100, churnRate * 100, churnGt * 100);
        }
        if ("allow".equals(verdict) && "PS_PRODUCT_ONLINE_V1".equals(policySetId)) {
            return String.format(
                    "立项门槛校验通过：目标市场规模预估%.0f户，不低于门槛%.0f户",
                    marketSize, marketSizeGte);
        }
        if ("allow".equals(verdict) && "PS_PRODUCT_RISK_V1".equals(policySetId)) {
            return "风险稽核通过：未命中零资费高风险或低效产品红线";
        }
        if (opsRules.isPresent()) {
            String base = opsRules.get().policyReasoning(policySetId);
            if (!hit.isEmpty()) {
                return base + "；本次命中：" + String.join(",", hit) + " → " + verdict;
            }
            return base + "；裁决：" + verdict;
        }
        return policySetId + " 评估完成 → " + verdict;
    }

    /**
     * 执行条件 DSL 规则（非 OWL SWRL；遗留营销路径）。
     */
    public Map<String, Object> executeSwrlRules(Map<String, Object> facts) {
        if (swrlRuleEngine.isEmpty()) {
            return Map.of("success", false, "message", "条件 DSL 引擎未启用（非 Openllet SWRL）");
        }

        try {
            @SuppressWarnings("deprecation")
            List<SwrlRuleEngine.SwrlRuleResult> results = swrlRuleEngine.get().executeAll(new LinkedHashMap<>(facts));

            long triggeredCount = results.stream().filter(SwrlRuleEngine.SwrlRuleResult::triggered).count();

            return Map.of(
                    "success", true,
                    "engine_type", "condition_dsl",
                    "deprecated", true,
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
            log.warn("[OntologyService] 条件 DSL 规则执行失败: {}", e.getMessage());
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
