package com.sitech.prodai.service.ops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 运营事实图契约校验：与 mock_graph / ProductCenter /ops-graph 同构。
 * soft = 缺省键自动补齐；hard = 类型错误 / 条目缺主键返回失败原因。
 */
public final class OpsGraphSchemaValidator {

    public static final String CONTRACT_VERSION = "OpsGraph-v1";

    public static final List<String> REQUIRED_TOP_KEYS = List.of(
            "shelfOfferings",
            "opsGraph",
            "bizScenarios",
            "templates",
            "equityGiftWhitelist",
            "riskRuleDefaults"
    );

    private OpsGraphSchemaValidator() {
    }

    public record ValidationResult(
            boolean ok,
            Map<String, Object> normalized,
            List<String> warnings,
            List<String> errors
    ) {
    }

    /**
     * 规范化并校验。空/null 图视为失败。
     */
    @SuppressWarnings("unchecked")
    public static ValidationResult validateAndNormalize(Map<String, Object> raw) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (raw == null) {
            errors.add("ops-graph body is null");
            return new ValidationResult(false, OpsProductGraphLoader.emptyGraph(), warnings, errors);
        }

        Map<String, Object> out = new LinkedHashMap<>(raw);
        for (String key : REQUIRED_TOP_KEYS) {
            if (!out.containsKey(key) || out.get(key) == null) {
                warnings.add("missing key filled with default: " + key);
                out.put(key, defaultFor(key));
            }
        }

        if (!(out.get("shelfOfferings") instanceof List<?>)) {
            errors.add("shelfOfferings must be an array");
        } else {
            validateShelfOfferings((List<?>) out.get("shelfOfferings"), warnings, errors);
        }
        if (!(out.get("opsGraph") instanceof Map<?, ?>)) {
            errors.add("opsGraph must be an object");
        } else {
            validateOpsGraph((Map<?, ?>) out.get("opsGraph"), warnings);
        }
        if (!(out.get("bizScenarios") instanceof Map<?, ?>)) {
            errors.add("bizScenarios must be an object");
        } else {
            validateBizScenarios((Map<?, ?>) out.get("bizScenarios"), warnings, errors);
        }
        if (!(out.get("templates") instanceof Map<?, ?>)) {
            errors.add("templates must be an object");
        } else {
            validateTemplates((Map<?, ?>) out.get("templates"), warnings, errors);
        }
        if (!(out.get("equityGiftWhitelist") instanceof List<?>)) {
            errors.add("equityGiftWhitelist must be an array");
        }
        if (!(out.get("riskRuleDefaults") instanceof Map<?, ?>)) {
            errors.add("riskRuleDefaults must be an object");
        }

        boolean ok = errors.isEmpty();
        if (!ok) {
            // 失败时仍返回已补齐结构，便于调用方日志诊断
            for (String key : REQUIRED_TOP_KEYS) {
                if (!(out.get(key) instanceof List || out.get(key) instanceof Map)) {
                    out.put(key, defaultFor(key));
                }
            }
        }
        out.putIfAbsent("_contractVersion", CONTRACT_VERSION);
        return new ValidationResult(ok, out, warnings, errors);
    }

    /**
     * 判断 product-center-base-url 是否指向本服务发布的 product-center（易形成同进程自指）。
     */
    public static boolean looksLikeLocalProductCenter(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return false;
        String lower = baseUrl.trim().toLowerCase(Locale.ROOT);
        boolean localHost = lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("0.0.0.0")
                || lower.contains("[::1]");
        boolean productCenterPath = lower.contains("/api/v1/product-center")
                || lower.endsWith("/product-center")
                || lower.contains("/product-center/");
        return localHost && productCenterPath;
    }

    public static Map<String, Object> contractDescriptor() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", CONTRACT_VERSION);
        out.put("requiredTopKeys", REQUIRED_TOP_KEYS);
        out.put("endpoint", "/api/v1/product-center/ops-graph");
        out.put("consumer", "HttpOpsProductDataSource (prodai.ontology.data-source=http)");
        out.put("shelfOfferingRequiredFields", List.of("offeringId|id", "offeringName|name"));
        out.put("bizScenarioRequiredFields", List.of("scenarioId", "messageRootKey", "categoryCode", "defaults"));
        out.put("deriveRuleActions", SCENARIO_ACTIONS);
        out.put("templateRequiredFields", List.of("templateId", "categoryCode", "messageRootKey"));
        return out;
    }

    private static void validateShelfOfferings(List<?> shelf, List<String> warnings, List<String> errors) {
        if (shelf.isEmpty()) {
            warnings.add("shelfOfferings is empty");
            return;
        }
        int idx = 0;
        for (Object item : shelf) {
            if (!(item instanceof Map<?, ?> row)) {
                errors.add("shelfOfferings[" + idx + "] must be an object");
                idx++;
                continue;
            }
            Object id = firstNonBlank(row.get("offeringId"), row.get("id"), row.get("offerId"));
            Object name = firstNonBlank(row.get("offeringName"), row.get("name"), row.get("productName"));
            if (id == null) {
                errors.add("shelfOfferings[" + idx + "] missing offeringId/id");
            }
            if (name == null) {
                warnings.add("shelfOfferings[" + idx + "] missing offeringName/name");
            }
            idx++;
        }
    }

    private static void validateOpsGraph(Map<?, ?> opsGraph, List<String> warnings) {
        if (opsGraph.isEmpty()) {
            warnings.add("opsGraph is empty");
            return;
        }
        boolean hasStructure = opsGraph.containsKey("offerings")
                || opsGraph.containsKey("nodes")
                || opsGraph.containsKey("entities")
                || opsGraph.containsKey("metrics")
                || opsGraph.containsKey("products");
        // mock_graph 风格：顶层直接以 offeringId 为 key
        boolean offeringKeyed = opsGraph.keySet().stream().anyMatch(k -> {
            String s = String.valueOf(k);
            return s.startsWith("OF-") || s.startsWith("http") || (s.length() >= 4 && Character.isLetterOrDigit(s.charAt(0)));
        });
        if (!hasStructure && !offeringKeyed) {
            warnings.add("opsGraph has no offerings/nodes/entities/metrics/products key");
        }
    }

    /** 场景条目允许的动作键（derive_rules DSL 契约，与 TemplateDeriveEngine 解释器对齐）。 */
    private static final List<String> SCENARIO_ACTIONS = List.of(
            "fill_defaults", "set_if_missing", "derive_category", "skip_fields", "select_template");

    /**
     * bizScenarios 契约加严（P3 结构校验）：每场景必须声明主键三元组
     * （scenarioId/messageRootKey/categoryCode），defaults 必须为对象，
     * templateId 指向的模板存在性由调用方跨块校验（此处仅查缺失）。
     * derive_rules 为可选 DSL 块，逐条校验动作键合法性。
     */
    private static void validateBizScenarios(Map<?, ?> scenarios, List<String> warnings, List<String> errors) {
        if (scenarios.isEmpty()) {
            warnings.add("bizScenarios is empty");
            return;
        }
        int idx = 0;
        for (Map.Entry<?, ?> e : scenarios.entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> sc)) {
                errors.add("bizScenarios[" + name + "] must be an object");
                idx++;
                continue;
            }
            if (blank(sc.get("scenarioId"))) {
                errors.add("bizScenarios[" + name + "] missing scenarioId");
            }
            if (blank(sc.get("messageRootKey"))) {
                errors.add("bizScenarios[" + name + "] missing messageRootKey");
            }
            if (blank(sc.get("categoryCode"))) {
                errors.add("bizScenarios[" + name + "] missing categoryCode");
            }
            if (!(sc.get("defaults") instanceof Map<?, ?>)) {
                errors.add("bizScenarios[" + name + "] defaults must be an object");
            }
            validateDeriveRules(name, sc.get("derive_rules"), errors);
            idx++;
        }
    }

    /** derive_rules 逐条校验：必须为数组、条目为对象、含合法动作键、条件/动作子结构类型正确。 */
    private static void validateDeriveRules(String scenarioName, Object rulesObj, List<String> errors) {
        if (rulesObj == null) {
            return;
        }
        if (!(rulesObj instanceof List<?> rules)) {
            errors.add("bizScenarios[" + scenarioName + "] derive_rules must be an array");
            return;
        }
        int idx = 0;
        for (Object r : rules) {
            if (!(r instanceof Map<?, ?> rule)) {
                errors.add("bizScenarios[" + scenarioName + "].derive_rules[" + idx + "] must be an object");
                idx++;
                continue;
            }
            boolean hasAction = SCENARIO_ACTIONS.stream().anyMatch(rule::containsKey);
            if (!hasAction) {
                errors.add("bizScenarios[" + scenarioName + "].derive_rules[" + idx
                        + "] missing action key (fill_defaults/set_if_missing/derive_category/"
                        + "skip_fields/select_template)");
            }
            for (String key : List.of("when", "when_any")) {
                if (rule.get(key) != null && !(rule.get(key) instanceof Map<?, ?>)) {
                    errors.add("bizScenarios[" + scenarioName + "].derive_rules[" + idx + "]." + key
                            + " must be an object");
                }
            }
            if (rule.get("fill_defaults") != null && !(rule.get("fill_defaults") instanceof Map<?, ?>)) {
                errors.add("bizScenarios[" + scenarioName + "].derive_rules[" + idx
                        + "].fill_defaults must be an object");
            }
            if (rule.get("set_if_missing") != null && !(rule.get("set_if_missing") instanceof Map<?, ?>)) {
                errors.add("bizScenarios[" + scenarioName + "].derive_rules[" + idx
                        + "].set_if_missing must be an object");
            }
            if (rule.get("derive_category") != null && !(rule.get("derive_category") instanceof Map<?, ?>)) {
                errors.add("bizScenarios[" + scenarioName + "].derive_rules[" + idx
                        + "].derive_category must be an object");
            }
            if (rule.get("select_template") != null && !(rule.get("select_template") instanceof Map<?, ?>)) {
                errors.add("bizScenarios[" + scenarioName + "].derive_rules[" + idx
                        + "].select_template must be an object");
            }
            idx++;
        }
    }

    /** templates 契约加严：主键三元组（templateId/name/messageRootKey/categoryCode）。 */
    private static void validateTemplates(Map<?, ?> templates, List<String> warnings, List<String> errors) {
        if (templates.isEmpty()) {
            warnings.add("templates is empty");
            return;
        }
        int idx = 0;
        for (Map.Entry<?, ?> e : templates.entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> tpl)) {
                errors.add("templates[" + name + "] must be an object");
                idx++;
                continue;
            }
            if (blank(tpl.get("templateId"))) {
                errors.add("templates[" + name + "] missing templateId");
            }
            if (blank(tpl.get("categoryCode"))) {
                errors.add("templates[" + name + "] missing categoryCode");
            }
            if (blank(tpl.get("messageRootKey"))) {
                errors.add("templates[" + name + "] missing messageRootKey");
            }
            idx++;
        }
    }

    private static boolean blank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private static Object firstNonBlank(Object... values) {
        if (values == null) return null;
        for (Object v : values) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return v;
        }
        return null;
    }

    private static Object defaultFor(String key) {
        return switch (key) {
            case "shelfOfferings", "equityGiftWhitelist" -> List.of();
            default -> Map.of();
        };
    }
}
