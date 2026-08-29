package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产商品运营规则单源：加载 {@code ops_rules.json}，供 ProductOntology / Openllet SWRL / 意图层共用。
 * <p>正式 OWL SWRL 引擎为 {@link OpsSwrlReasoner}；旧 {@link SwrlRuleEngine} 为伪条件 DSL，勿混用。
 */
@Service
public class OpsRulesService {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ProdAiProperties properties;

    private volatile Map<String, Object> rulesCache;

    public OpsRulesService(ObjectMapper objectMapper,
                           ResourceLoader resourceLoader,
                           ProdAiProperties properties) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        load();
    }

    public synchronized Map<String, Object> load() {
        if (rulesCache != null) {
            return rulesCache;
        }
        rulesCache = loadPending();
        return rulesCache;
    }

    /** 守卫 LOAD（P1-6）：解析最新文件为 pending，不触碰现行缓存；失败保留现行版本。 */
    public Map<String, Object> loadPending() {
        try {
            Resource resource = resourceLoader.getResource(properties.getOntology().getRulesPath());
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, new TypeReference<>() {});
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load ontology ops rules: " + e.getMessage(), e);
        }
    }

    /** 守卫 COMMIT（P1-6）：全部通过后原子切换缓存。 */
    public synchronized void swap(Map<String, Object> pending) {
        this.rulesCache = pending;
    }

    public String version() {
        return str(load().getOrDefault("version", "OpsRules"));
    }

    public Map<String, Object> catalogView() {
        Map<String, Object> rules = load();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("version", rules.getOrDefault("version", "OpsRules"));
        body.put("description", rules.get("description"));
        body.put("engines", castMap(rules.get("engines")));
        body.put("rootCause", castMap(rules.get("rootCause")));
        body.put("risk", castMap(rules.get("risk")));
        body.put("config", castMap(rules.get("config")));
        body.put("batch", castMap(rules.get("batch")));
        body.put("policy", castMap(rules.get("policy")));
        body.put("swrlEnabled", properties.getOntology().isSwrlEnabled());
        body.put("primarySwrlEngine", "openllet-swrl");
        body.put("productOpsOwlPath", properties.getOntology().getProductOpsOwlPath());
        body.put("configTtlPath", properties.getOntology().getConfigTtlPath());
        Map<String, Object> config = castMap(rules.get("config"));
        body.put("proposalMapping", castMap(config.get("proposalMapping")));
        body.put("engineNarrative", config.get("engineNarrative"));
        return body;
    }

    /** 方案文档规则别名（如 R-CONF-001），无则空串。 */
    public String configProposalAlias(String ruleId) {
        return str(configRule(ruleId).get("proposalAlias"));
    }

    public Map<String, Object> policySet(String policySetId) {
        return castMap(castMap(castMap(load().get("policy")).get("sets")).get(policySetId));
    }

    public Map<String, Object> policyThresholds(String policySetId) {
        return castMap(policySet(policySetId).get("thresholds"));
    }

    public List<Map<String, Object>> listPolicySets() {
        Map<String, Object> sets = castMap(castMap(load().get("policy")).get("sets"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : sets.entrySet()) {
            Map<String, Object> cfg = castMap(e.getValue());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getKey());
            row.put("name", cfg.getOrDefault("name", e.getKey()));
            row.put("description", cfg.getOrDefault("description", ""));
            out.add(row);
        }
        return out;
    }

    public List<String> policyTriggeredRules(String policySetId) {
        Map<String, Object> cfg = policySet(policySetId);
        Object rules = cfg.get("triggeredRules");
        if (!(rules instanceof List<?> list) || list.isEmpty()) {
            return List.of("R000");
        }
        return list.stream().map(this::str).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    public String policyReasoning(String policySetId) {
        Map<String, Object> cfg = policySet(policySetId);
        String reasoning = str(cfg.get("reasoning"));
        return reasoning.isBlank() ? policySetId + " 评估完成" : reasoning;
    }

    public Map<String, Object> rootCauseRule(String ruleId) {
        return castMap(castMap(castMap(load().get("rootCause")).get("rules")).get(ruleId));
    }

    public Map<String, Object> riskRule(String ruleId) {
        return castMap(castMap(castMap(load().get("risk")).get("rules")).get(ruleId));
    }

    public Map<String, Object> configRule(String ruleId) {
        return castMap(castMap(castMap(load().get("config")).get("rules")).get(ruleId));
    }

    public Map<String, Object> batchRule(String ruleId) {
        return castMap(castMap(castMap(load().get("batch")).get("rules")).get(ruleId));
    }

    public Map<String, Object> riskDefaults() {
        return castMap(castMap(load().get("risk")).get("defaults"));
    }

    public Map<String, Object> riskScoring() {
        return castMap(castMap(load().get("risk")).get("scoring"));
    }

    public int rootCauseTopN() {
        return (int) num(castMap(load().get("rootCause")).get("topN"), 3);
    }

    public boolean isRuleEnabled(Map<String, Object> rule) {
        if (rule == null || rule.isEmpty()) {
            return true;
        }
        Object enabled = rule.get("enabled");
        return enabled == null || truthy(enabled);
    }

    public boolean isRootCauseEnabled(String ruleId) {
        return isRuleEnabled(rootCauseRule(ruleId));
    }

    public boolean isRiskEnabled(String ruleId) {
        return isRuleEnabled(riskRule(ruleId));
    }

    public boolean isConfigEnabled(String ruleId) {
        return isRuleEnabled(configRule(ruleId));
    }

    /**
     * 是否应走 Openllet SWRL：全局开关 + 规则 enabled + engine=swrl。
     * 依次查 rootCause / risk / config 规则块。
     */
    public boolean preferSwrl(String ruleId) {
        if (!properties.getOntology().isSwrlEnabled()) {
            return false;
        }
        Map<String, Object> rule = rootCauseRule(ruleId);
        if (rule.isEmpty()) {
            rule = riskRule(ruleId);
        }
        if (rule.isEmpty()) {
            rule = configRule(ruleId);
        }
        if (rule.isEmpty() || !isRuleEnabled(rule)) {
            return false;
        }
        String engine = str(rule.getOrDefault("engine", "java")).toLowerCase();
        return "swrl".equals(engine) || "openllet".equals(engine) || "openllet-swrl".equals(engine);
    }

    /** 任一规则配置为 SWRL 则 true。 */
    public boolean preferSwrlAny(String... ruleIds) {
        if (ruleIds == null) {
            return false;
        }
        for (String id : ruleIds) {
            if (preferSwrl(id)) {
                return true;
            }
        }
        return false;
    }

    public double ruleNum(Map<String, Object> rule, String key, double defaultValue) {
        if (rule == null || !rule.containsKey(key)) {
            return defaultValue;
        }
        return num(rule.get(key), defaultValue);
    }

    public Set<String> ruleStringSet(Map<String, Object> rule, String key, Set<String> defaultValue) {
        if (rule == null || !(rule.get(key) instanceof List<?> list) || list.isEmpty()) {
            return defaultValue;
        }
        return list.stream()
                .map(this::str)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 配置槽位默认值（ops_rules.config.defaults）。 */
    public Map<String, Object> configDefaults() {
        return castMap(castMap(load().get("config")).get("defaults"));
    }

    public String configDefaultStr(String key, String fallback) {
        String v = str(configDefaults().get(key));
        return v.isBlank() ? fallback : v;
    }

    public double configDefaultNum(String key, double fallback) {
        return num(configDefaults().get(key), fallback);
    }

    public Map<String, String> extractionAliases() {
        Map<String, Object> raw = castMap(castMap(load().get("extraction")).get("aliases"));
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            String id = str(v);
            if (!k.isBlank() && !id.isBlank()) {
                out.put(k, id);
            }
        });
        return out;
    }

    public List<String> extractionTriggers(String key) {
        Object raw = castMap(load().get("extraction")).get(key);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(this::str).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    /**
     * P2-2 正则通用化：{@code extraction.slotPatterns}（slot -> 模式数组），补充内置正则外的可配置抽取。
     * 每条模式：{@code {pattern, group, template, guard}}——pattern 正则取指定捕获组，
     * template 用 {@code {v}} 占位格式化，guard 为可选文本前置条件。
     */
    public Map<String, List<Map<String, Object>>> extractionSlotPatterns() {
        Object raw = castMap(load().get("extraction")).get("slotPatterns");
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        map.forEach((slot, specs) -> {
            if (slot == null || !(specs instanceof List<?> list)) {
                return;
            }
            List<Map<String, Object>> rows = list.stream()
                    .filter(s -> s instanceof Map<?, ?>)
                    .map(s -> castMap(s))
                    .collect(Collectors.toList());
            if (!rows.isEmpty()) {
                out.put(str(slot), rows);
            }
        });
        return out;
    }

    /**
     * 话术命中别名时返回商品 ID；未配置别名则返回空。
     * 按别名 key 长度降序匹配，避免短词误伤。
     */
    public String resolveAliasOfferingId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return extractionAliases().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .filter(e -> text.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /** R-B04 等风险分类：以规则 categories 为准（演示分类写进规则/数据，不靠代码分支）。 */
    public Set<String> riskCategories(String ruleId, Set<String> fallback) {
        return new LinkedHashSet<>(ruleStringSet(riskRule(ruleId), "categories", fallback));
    }

    /** 首页货架预览优先展示的 offeringId（ops_rules.ui）。 */
    public List<String> previewOfferingIds() {
        Map<String, Object> ui = castMap(load().get("ui"));
        if (ui.isEmpty()) {
            ui = castMap(load().get("demo"));
        }
        return stringList(ui.get("previewOfferingIds"));
    }

    public int previewLimit(int defaultLimit) {
        Map<String, Object> ui = castMap(load().get("ui"));
        if (ui.isEmpty()) {
            ui = castMap(load().get("demo"));
        }
        Object lim = ui.get("previewLimit");
        if (lim instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        return defaultLimit;
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(this::str).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    public int riskScore(String riskLevel, boolean urgent) {
        Map<String, Object> scoring = riskScoring();
        int base = switch (String.valueOf(riskLevel)) {
            case "HIGH" -> (int) num(scoring.get("high"), 92);
            case "MEDIUM" -> (int) num(scoring.get("medium"), 70);
            default -> (int) num(scoring.get("low"), 40);
        };
        if (urgent) {
            int bump = (int) num(scoring.get("urgentBump"), 5);
            int cap = (int) num(scoring.get("urgentCap"), 99);
            return Math.min(cap, base + bump);
        }
        return base;
    }

    /** 规则 ID → 业务可读标签（优先读外置 name）。 */
    public String formatRuleLabel(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return "";
        }
        String name = firstNonBlank(
                str(rootCauseRule(ruleId).get("name")),
                str(riskRule(ruleId).get("name")),
                str(configRule(ruleId).get("name")),
                str(batchRule(ruleId).get("name"))
        );
        if (name.isBlank()) {
            return ruleId;
        }
        return name + "（" + ruleId + "）";
    }

    /** 仅业务名称，不含规则编码（给业务人员看的思考过程用） */
    public String formatRuleName(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return "";
        }
        String name = firstNonBlank(
                str(rootCauseRule(ruleId).get("name")),
                str(riskRule(ruleId).get("name")),
                str(configRule(ruleId).get("name")),
                str(batchRule(ruleId).get("name"))
        );
        return name.isBlank() ? ruleId : name;
    }

    public List<String> ruleIds(String section) {
        Map<String, Object> rules = castMap(castMap(load().get(section)).get("rules"));
        return new ArrayList<>(rules.keySet());
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private boolean truthy(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).trim().toLowerCase();
        return Set.of("1", "true", "yes", "y", "是").contains(s);
    }

    private double num(Object v, double d) {
        if (v == null) {
            return d;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v).replaceAll("[^\\d.-]", ""));
        } catch (Exception e) {
            return d;
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), val));
            return out;
        }
        return new LinkedHashMap<>();
    }
}
