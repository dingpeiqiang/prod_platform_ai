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
        }
        if (!(out.get("templates") instanceof Map<?, ?>)) {
            errors.add("templates must be an object");
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
