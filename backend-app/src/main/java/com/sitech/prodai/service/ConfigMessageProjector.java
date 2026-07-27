package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 配置方案业务属性 ↔ 六类逻辑模型报文投影。
 * 报文是投影层，不进入 OWL 类骨架。
 */
@Component
public class ConfigMessageProjector {

    private static final Logger log = LoggerFactory.getLogger(ConfigMessageProjector.class);
    private static final String DEFAULT_PATH = "classpath:ontology/config_message_projection.json";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private Map<String, Object> projection = Map.of();

    public ConfigMessageProjector(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        load(DEFAULT_PATH);
    }

    public void load(String path) {
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                log.warn("[ConfigMessageProjector] projection not found: {}", path);
                projection = Map.of();
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                projection = objectMapper.readValue(in, new TypeReference<>() {
                });
            }
            log.info("[ConfigMessageProjector] loaded categories={}", categories().keySet());
        } catch (Exception e) {
            log.warn("[ConfigMessageProjector] load failed: {}", e.getMessage());
            projection = Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> categories() {
        Object raw = projection.get("categories");
        return raw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public Map<String, Object> categoryMeta(String messageRootKey) {
        Object raw = categories().get(messageRootKey);
        return raw instanceof Map<?, ?> m ? castMap(m) : Map.of();
    }

    /**
     * 将业务草稿投影为规范报文：{ messageRootKey: { baseInfo, releaseInfo, ... } }
     */
    public Map<String, Object> toMessage(Map<String, Object> draftInput) {
        Map<String, Object> draft = flattenDraft(draftInput);
        String rootKey = resolveMessageRootKey(draft);
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map<String, Object> mapping : mappingsFor(rootKey)) {
            Object value = resolveBusinessValue(draft, mapping);
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            putPath(body, str(mapping.get("path")), normalizeMessageValue(value));
        }
        // expRuleId 兼容：规范样例用 expRuleId，映射表用 cancelRuleId
        Object cancel = resolveBusinessValue(draft, Map.of("business", "cancelEffectMode", "aliases", List.of("expRuleId")));
        if (cancel != null && !String.valueOf(cancel).isBlank()) {
            putPath(body, "baseInfo.expRuleId", String.valueOf(cancel));
            putPath(body, "baseInfo.cancelRuleId", String.valueOf(cancel));
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put(rootKey, body);
        return wrapped;
    }

    /**
     * 从规范报文反投影为扁平业务草稿。
     */
    public Map<String, Object> fromMessage(Map<String, Object> message) {
        if (message == null || message.isEmpty()) {
            return new LinkedHashMap<>();
        }
        String rootKey = null;
        Map<String, Object> body = null;
        for (String key : categories().keySet()) {
            if (message.containsKey(key) && message.get(key) instanceof Map<?, ?>) {
                rootKey = key;
                body = castMap(message.get(key));
                break;
            }
        }
        if (rootKey == null) {
            // 允许直接传入内部 body
            for (Map.Entry<String, Object> e : message.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> && Set.of("baseInfo", "releaseInfo", "optionalInfo")
                        .stream().anyMatch(k -> castMap(e.getValue()).containsKey(k))) {
                    rootKey = e.getKey();
                    body = castMap(e.getValue());
                    break;
                }
            }
        }
        if (rootKey == null || body == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("messageRootKey", rootKey);
        draft.put("categoryCode", rootKey);
        Map<String, Object> meta = categoryMeta(rootKey);
        if (!meta.isEmpty()) {
            draft.put("categoryName", meta.get("label"));
            draft.put("productLine", meta.get("productLine"));
            draft.put("isMainOffer", meta.get("isMainOffer"));
        }
        for (Map<String, Object> mapping : mappingsFor(rootKey)) {
            Object value = getPath(body, str(mapping.get("path")));
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            String business = str(mapping.get("business"));
            draft.put(business, value);
            if ("offerName".equals(business)) {
                draft.put("offeringName", value);
            }
            if ("fixedFeeAmount".equals(business)) {
                draft.put("monthlyFee", value);
            }
        }
        Object expRule = getPath(body, "baseInfo.expRuleId");
        if (expRule != null && !String.valueOf(expRule).isBlank()) {
            draft.put("cancelEffectMode", expRule);
        }
        return draft;
    }

    public Map<String, Object> applyCategoryDefaults(Map<String, Object> draftInput) {
        Map<String, Object> draft = new LinkedHashMap<>(draftInput == null ? Map.of() : draftInput);
        String rootKey = resolveMessageRootKey(draft);
        draft.put("messageRootKey", rootKey);
        draft.put("categoryCode", firstNonBlank(str(draft.get("categoryCode")), rootKey));
        Map<String, Object> meta = categoryMeta(rootKey);
        if (!meta.isEmpty()) {
            if (blank(draft.get("categoryName"))) {
                draft.put("categoryName", meta.get("label"));
            }
            if (blank(draft.get("productLine"))) {
                draft.put("productLine", meta.get("productLine"));
            }
            if (blank(draft.get("isMainOffer"))) {
                draft.put("isMainOffer", meta.get("isMainOffer"));
            }
            if (blank(draft.get("requiredElements"))) {
                draft.put("requiredElements", meta.get("requiredElements"));
            }
        }
        Map<String, Object> defaults = castMap(castMap(projection.get("defaultsByCategory")).get(rootKey));
        boolean noContract = "0".equals(str(draft.get("hasContract")))
                || "false".equalsIgnoreCase(str(draft.get("hasContract")));
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            if (blank(draft.get(e.getKey()))) {
                if (noContract && "contractMonths".equals(e.getKey())) {
                    continue;
                }
                draft.put(e.getKey(), e.getValue());
            }
        }
        // 兼容旧字段
        if (blank(draft.get("fixedFeeAmount")) && !blank(draft.get("monthlyFee"))) {
            draft.put("fixedFeeAmount", draft.get("monthlyFee"));
        }
        if (blank(draft.get("monthlyFee")) && !blank(draft.get("fixedFeeAmount"))) {
            draft.put("monthlyFee", draft.get("fixedFeeAmount"));
        }
        if (blank(draft.get("offerName")) && !blank(draft.get("offeringName"))) {
            draft.put("offerName", draft.get("offeringName"));
        }
        if (blank(draft.get("offeringName")) && !blank(draft.get("offerName"))) {
            draft.put("offeringName", draft.get("offerName"));
        }
        syncOfferingType(draft);
        return draft;
    }

    public String resolveMessageRootKey(Map<String, Object> draft) {
        if (draft == null) {
            return "personMainPrc";
        }
        String explicit = firstNonBlank(
                str(draft.get("messageRootKey")),
                str(draft.get("categoryCode")));
        if (categories().containsKey(explicit)) {
            return explicit;
        }
        String offeringType = str(draft.get("offeringType")).toLowerCase(Locale.ROOT);
        String scenario = str(firstNonBlank(str(draft.get("bizScenario")), str(draft.get("scenario"))));
        String target = str(draft.get("targetUser"));
        String productLine = str(draft.get("productLine"));

        if (scenario.contains("家庭") || "家庭".equals(target) || "家庭".equals(productLine)
                || "fusion".equals(offeringType)) {
            return "addon".equals(offeringType) ? "familyAddPrc" : "familyBasePrc";
        }
        if (scenario.contains("宽带") || "宽带".equals(productLine)
                || str(draft.get("includeBroadband")).contains("M")
                || str(draft.get("downstreamBandwidth")).length() > 0) {
            return "addon".equals(offeringType) ? "broadBandOptSpeedPrc" : "broadBandMainPrc";
        }
        if ("addon".equals(offeringType) || scenario.contains("附加")) {
            return "personAddPrc";
        }
        return "personMainPrc";
    }

    private void syncOfferingType(Map<String, Object> draft) {
        if (!blank(draft.get("offeringType"))) {
            return;
        }
        String root = str(draft.get("messageRootKey"));
        Map<String, Object> meta = categoryMeta(root);
        boolean main = Boolean.TRUE.equals(meta.get("isMainOffer"))
                || "true".equalsIgnoreCase(str(meta.get("isMainOffer")));
        if ("familyBasePrc".equals(root)) {
            draft.put("offeringType", "fusion");
        } else if (main) {
            draft.put("offeringType", "main_pkg");
        } else {
            draft.put("offeringType", "addon");
        }
    }

    private List<Map<String, Object>> mappingsFor(String rootKey) {
        List<Map<String, Object>> list = new ArrayList<>();
        Object shared = projection.get("sharedMappings");
        if (shared instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    list.add(castMap(m));
                }
            }
        }
        Object cat = castMap(projection.get("categoryMappings")).get(rootKey);
        if (cat instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    list.add(castMap(m));
                }
            }
        }
        return list;
    }

    private Object resolveBusinessValue(Map<String, Object> draft, Map<String, Object> mapping) {
        String business = str(mapping.get("business"));
        if (!blank(draft.get(business))) {
            return draft.get(business);
        }
        Object aliases = mapping.get("aliases");
        if (aliases instanceof List<?> list) {
            for (Object a : list) {
                if (!blank(draft.get(String.valueOf(a)))) {
                    return draft.get(String.valueOf(a));
                }
            }
        }
        // nested chargePlan / releaseScope
        Object nested = getNested(draft, business);
        return blank(nested) ? null : nested;
    }

    private Object getNested(Map<String, Object> draft, String business) {
        Map<String, Object> charge = castMap(draft.get("chargePlan"));
        if (!charge.isEmpty() && charge.containsKey(business)) {
            return charge.get(business);
        }
        if ("fixedFeeAmount".equals(business) && charge.containsKey("fixedFeeAmount")) {
            return charge.get("fixedFeeAmount");
        }
        Map<String, Object> release = castMap(draft.get("releaseScope"));
        if (!release.isEmpty() && release.containsKey(business)) {
            return release.get(business);
        }
        Map<String, Object> sales = castMap(draft.get("salesPolicy"));
        if (!sales.isEmpty() && sales.containsKey(business)) {
            return sales.get(business);
        }
        return null;
    }

    private Map<String, Object> flattenDraft(Map<String, Object> draftInput) {
        Map<String, Object> draft = applyCategoryDefaults(draftInput);
        // lift nested blocks
        mergeIfAbsent(draft, castMap(draft.get("chargePlan")));
        mergeIfAbsent(draft, castMap(draft.get("releaseScope")));
        mergeIfAbsent(draft, castMap(draft.get("salesPolicy")));
        mergeIfAbsent(draft, castMap(draft.get("networkCapability")));
        mergeIfAbsent(draft, castMap(draft.get("familyOfferPolicy")));
        mergeIfAbsent(draft, castMap(draft.get("printNotice")));
        mergeIfAbsent(draft, castMap(draft.get("smsNotice")));
        Object prefs = draft.get("preferentialPlans");
        if (prefs instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            mergeIfAbsent(draft, castMap(first));
        }
        return draft;
    }

    private static void mergeIfAbsent(Map<String, Object> target, Map<String, Object> src) {
        for (Map.Entry<String, Object> e : src.entrySet()) {
            if (blank(target.get(e.getKey())) && !blank(e.getValue())) {
                target.put(e.getKey(), e.getValue());
            }
        }
    }

    private static Object normalizeMessageValue(Object value) {
        if (value instanceof Boolean b) {
            return b ? "是" : "否";
        }
        if (value instanceof Number n) {
            if (n.doubleValue() == Math.rint(n.doubleValue())) {
                return String.valueOf(n.longValue());
            }
            return String.valueOf(n);
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static void putPath(Map<String, Object> root, String path, Object value) {
        if (path == null || path.isBlank()) {
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur.get(parts[i]);
            if (!(next instanceof Map<?, ?>)) {
                Map<String, Object> child = new LinkedHashMap<>();
                cur.put(parts[i], child);
                cur = child;
            } else {
                cur = (Map<String, Object>) next;
            }
        }
        cur.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private static Object getPath(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("\\.");
        Object cur = root;
        for (String part : parts) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(part);
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static boolean blank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return "";
        }
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
