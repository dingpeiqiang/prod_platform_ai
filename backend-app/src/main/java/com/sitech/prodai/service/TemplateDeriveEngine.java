package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * derive_rules 推理引擎（P2-3，独立类，禁止并入 ProductOntologyService，§11.3/§14.4-1）。
 * <p>职责：平移 {@code ProductOntologyService#inferFields} 场景分支语义为模板驱动的推导流水线，
 * 并执行合并模板（{@link ProductTemplateRegistry}）的 {@code derive_rules}：
 * <ul>
 *   <li>{@code set_default}（if_missing）：推理面缺省补全，fillSource 记 {@code derive_rule}；</li>
 *   <li>{@code when + visible/hidden}：显隐裁决视图（不改动草稿值，仅输出 body.visibility）；</li>
 *   <li>{@code derive}：数值派生按 §4.5 归 SHACL/Java，引擎不硬解，仅记录跳过。</li>
 * </ul>
 * <p>单引擎契约（P2-7 已删除存量 Java inferFields 分支）：引擎输出 body 为唯一事实
 * （success/draft/inferredFields/appliedRules/recommendedTemplates/visibility/
 * templateRulesApplied/messageRootKey），draft 增量键仅限模板 set_default 新增字段。
 * <p>图谱仍为场景默认值单源（bizScenarios/templates），与 SMOKE 参数化通道一致；
 * R-C01/R-C02 门控、addon 品类推导、模板要素补全语义与存量逐一平移，保证回归基线不漂移。
 */
@Service
public class TemplateDeriveEngine {

    private static final Logger log = LoggerFactory.getLogger(TemplateDeriveEngine.class);

    private final OpsRulesService opsRules;
    private final ProductTemplateRegistry templateRegistry;
    private final ConfigMessageProjector messageProjector;
    private final ObjectMapper objectMapper;

    public TemplateDeriveEngine(OpsRulesService opsRules,
                                ProductTemplateRegistry templateRegistry,
                                ConfigMessageProjector messageProjector,
                                ObjectMapper objectMapper) {
        this.opsRules = opsRules;
        this.templateRegistry = templateRegistry;
        this.messageProjector = messageProjector;
        this.objectMapper = objectMapper;
    }

    /**
     * 模板驱动推导。{@code graph} 为显式入参（现行图谱经 {@code ProductOntologyService#loadGraph()}
     * 或 pending 图谱传入），引擎不反向依赖存量服务。
     */
    public Map<String, Object> derive(Map<String, Object> slots, Map<String, Object> draft,
                                      Map<String, Object> graph) {
        Map<String, Object> safeGraph = graph == null ? Map.of() : graph;
        Map<String, Object> result = deepCopy(draft == null ? Map.of() : draft);
        Map<String, String> fillSources = new LinkedHashMap<>();
        Set<String> appliedRules = new LinkedHashSet<>();
        Map<String, Object> safeSlots = slots == null ? Map.of() : slots;

        mergeSlots(safeSlots, result, fillSources);

        String scenario = str(firstNonEmpty(result.get("bizScenario"), safeSlots.get("bizScenario")));
        Map<String, Object> scenarioCfg = castMap(castMap(safeGraph.get("bizScenarios")).get(scenario));
        Map<String, Object> defaults = castMap(scenarioCfg.get("defaults"));
        // 智读批量带 sourceExcerpt：只补结构字段，不灌入模板资费/流量等，避免覆盖文档真实内容
        boolean fromDocument = !empty(safeSlots.get("sourceExcerpt"));
        Set<String> structuralKeys = Set.of(
                "offeringType", "mutexGroup", "targetUser", "productLine",
                "messageRootKey", "categoryCode", "channelScope");

        if ("家庭融合".equals(scenario) && opsRules.isConfigEnabled("R-C01")) {
            for (Map.Entry<String, Object> e : defaults.entrySet()) {
                if (fromDocument && !structuralKeys.contains(e.getKey())) {
                    continue;
                }
                if (empty(result.get(e.getKey()))) {
                    result.put(e.getKey(), e.getValue());
                    fillSources.put(e.getKey(), "scenario_default");
                    appliedRules.add("R-C01");
                }
            }
            if (!fromDocument && empty(result.get("includeBroadband"))) {
                result.put("includeBroadband",
                        defaults.getOrDefault("includeBroadband",
                                opsRules.configDefaultStr("includeBroadband", "500M")));
                fillSources.put("includeBroadband", "scenario_default");
                appliedRules.add("R-C01");
            }
        }

        boolean isCampus = "校园".equals(str(result.get("targetUser"))) || "校园体验".equals(scenario);
        String offeringType = str(firstNonEmpty(result.get("offeringType"), safeSlots.get("offeringType")));
        if (!fromDocument && opsRules.isConfigEnabled("R-C02") && isCampus
                && empty(result.get("monthlyFee")) && !"addon".equals(offeringType)) {
            result.put("monthlyFee", defaults.getOrDefault("monthlyFee",
                    opsRules.configDefaultNum("campusMonthlyFee", 59)));
            fillSources.put("monthlyFee", "template");
            appliedRules.add("R-C02");
            for (Map.Entry<String, Object> e : defaults.entrySet()) {
                if (!"monthlyFee".equals(e.getKey()) && empty(result.get(e.getKey()))) {
                    result.put(e.getKey(), e.getValue());
                    fillSources.put(e.getKey(), "scenario_default");
                }
            }
        } else if (!fromDocument && isCampus && "addon".equals(offeringType)) {
            for (Map.Entry<String, Object> e : defaults.entrySet()) {
                if ("monthlyFee".equals(e.getKey()) || "mutexGroup".equals(e.getKey())) {
                    continue;
                }
                if (empty(result.get(e.getKey()))) {
                    result.put(e.getKey(), e.getValue());
                    fillSources.put(e.getKey(), "scenario_default");
                }
            }
        }

        // 品类 / messageRootKey 推导（v2.2）——先于模板选择，避免加装包误用主套餐模板/报文
        if (empty(result.get("messageRootKey")) && !empty(scenarioCfg.get("messageRootKey"))) {
            result.put("messageRootKey", scenarioCfg.get("messageRootKey"));
            fillSources.put("messageRootKey", "scenario_default");
            appliedRules.add("R-C01");
        }
        if (empty(result.get("categoryCode")) && !empty(scenarioCfg.get("categoryCode"))) {
            result.put("categoryCode", scenarioCfg.get("categoryCode"));
            fillSources.put("categoryCode", "scenario_default");
        }
        // 加装/附加：品类走 familyAddPrc，避免家庭融合场景默认顶成 familyBasePrc
        if ("addon".equals(str(result.get("offeringType")))) {
            String cat = str(result.get("categoryCode"));
            String root = str(result.get("messageRootKey"));
            boolean familyCtx = "家庭融合".equals(scenario)
                    || "家庭".equals(str(result.get("targetUser")))
                    || "familyBasePrc".equals(cat)
                    || "familyBasePrc".equals(root)
                    || cat.isBlank();
            if (familyCtx && !"familyAddPrc".equals(cat)) {
                result.put("messageRootKey", "familyAddPrc");
                result.put("categoryCode", "familyAddPrc");
                fillSources.put("messageRootKey", "scenario_default");
                fillSources.put("categoryCode", "scenario_default");
            }
        }
        // 按品类选择模板：家庭附加 → TPL-FAMILY-ADD-20，勿套用畅享128主套餐模板
        Object templateId = resolveTemplateId(scenarioCfg, result);
        if (templateId != null && empty(result.get("basedOnTemplate"))) {
            result.put("basedOnTemplate", templateId);
            fillSources.put("basedOnTemplate", "template");
        }
        if (empty(result.get("channelScope"))) {
            result.put("channelScope", opsRules.configDefaultStr("channelScope", "全渠道"));
            fillSources.put("channelScope", "scenario_default");
        }
        boolean addonOffer = "addon".equals(str(result.get("offeringType")))
                || "familyAddPrc".equals(str(result.get("categoryCode")))
                || "personAddPrc".equals(str(result.get("categoryCode")));
        if (addonOffer && (empty(result.get("mutexGroup")) || "MAIN_PKG".equals(str(result.get("mutexGroup"))))) {
            result.put("mutexGroup", "ADDON");
            fillSources.put("mutexGroup", "scenario_default");
        } else if (empty(result.get("mutexGroup"))) {
            result.put("mutexGroup", defaults.getOrDefault("mutexGroup",
                    opsRules.configDefaultStr("mutexGroup", "MAIN_PKG")));
            fillSources.put("mutexGroup", "scenario_default");
        }
        // 模板要素补全（智读文档模式仅补 requiredElements，资费/流量以原文为准）
        Map<String, Object> template = castMap(castMap(safeGraph.get("templates")).get(str(templateId)));
        if (!template.isEmpty() && opsRules.isConfigEnabled("R-C02")) {
            List<String> templateKeys = fromDocument
                    ? List.of("requiredElements")
                    : List.of("fixedFeeAmount", "monthlyFee", "includeVoice", "includeData",
                    "includeBroadband", "downstreamBandwidth", "upstreamBandwidth", "requiredElements");
            for (String key : templateKeys) {
                if (empty(result.get(key)) && !empty(template.get(key))) {
                    result.put(key, template.get(key));
                    fillSources.put(key, "template");
                    appliedRules.add("R-C02");
                }
            }
            if (empty(result.get("messageRootKey")) && !empty(template.get("messageRootKey"))) {
                result.put("messageRootKey", template.get("messageRootKey"));
                fillSources.put("messageRootKey", "template");
                appliedRules.add("R-C02");
            }
        }

        result = messageProjector.applyCategoryDefaults(result);

        // 模板 derive_rules 接管（P2-3）：置后执行（if_missing），不抢占存量补全记账，保证并存 diff 干净
        Map<String, Object> deriveView = applyDeriveRules(result.get("categoryCode"), result, fillSources);

        DraftSceneNotices.enrich(result, fillSources);
        // 文档/用户已给月费时，固费与之对齐，避免模板 128 与月费 158 并存
        if (!empty(result.get("monthlyFee"))) {
            String feeSrc = fillSources.get("monthlyFee");
            String fixedSrc = fillSources.get("fixedFeeAmount");
            boolean feeFromUser = "user_said".equals(feeSrc);
            boolean fixedFromDefault = fixedSrc == null
                    || "template".equals(fixedSrc)
                    || "scenario_default".equals(fixedSrc);
            if (feeFromUser && (empty(result.get("fixedFeeAmount")) || fixedFromDefault
                    || !String.valueOf(result.get("fixedFeeAmount")).equals(String.valueOf(result.get("monthlyFee"))))) {
                result.put("fixedFeeAmount", result.get("monthlyFee"));
                fillSources.put("fixedFeeAmount", feeSrc != null ? feeSrc : "user_said");
            }
        }
        if (!empty(result.get("fixedFeeAmount")) && empty(result.get("monthlyFee"))) {
            result.put("monthlyFee", result.get("fixedFeeAmount"));
        }
        if (!empty(result.get("monthlyFee")) && empty(result.get("fixedFeeAmount"))) {
            result.put("fixedFeeAmount", result.get("monthlyFee"));
        }
        // 结构化要素块（供投影与展示）
        if (result.get("chargePlan") == null || castMap(result.get("chargePlan")).isEmpty()) {
            Map<String, Object> charge = new LinkedHashMap<>();
            if (!empty(result.get("fixedFeeAmount"))) {
                charge.put("fixedFeeAmount", result.get("fixedFeeAmount"));
            }
            if (!empty(result.get("chargeMode"))) {
                charge.put("chargeMode", result.get("chargeMode"));
            }
            if (!empty(result.get("accountItem"))) {
                charge.put("accountItem", result.get("accountItem"));
            }
            if (!charge.isEmpty()) {
                result.put("chargePlan", charge);
            }
        }
        if (result.get("releaseScope") == null || castMap(result.get("releaseScope")).isEmpty()) {
            Map<String, Object> release = new LinkedHashMap<>();
            if (!empty(result.get("channelScope"))) {
                release.put("channelScope", result.get("channelScope"));
            }
            if (!empty(result.get("regionScope"))) {
                release.put("regionScope", result.get("regionScope"));
            }
            if (!empty(result.get("regionDetail"))) {
                release.put("regionDetail", result.get("regionDetail"));
            }
            if (!release.isEmpty()) {
                result.put("releaseScope", release);
            }
        }
        result.put("fillSources", fillSources);

        List<Map<String, Object>> inferred = new ArrayList<>();
        for (Map.Entry<String, String> e : fillSources.entrySet()) {
            String src = e.getValue();
            if (!"scenario_default".equals(src) && !"template".equals(src)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("field", e.getKey());
            row.put("value", result.get(e.getKey()));
            row.put("fillSource", src);
            row.put("rule", "scenario_default".equals(src) ? "R-C01" : ("template".equals(src) ? "R-C02" : null));
            inferred.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("draft", result);
        body.put("inferredFields", inferred);
        body.put("appliedRules", new ArrayList<>(appliedRules).stream().sorted().collect(Collectors.toList()));
        body.put("recommendedTemplates", templateId == null ? List.of() : List.of(templateId));
        body.put("messageRootKey", result.get("messageRootKey"));
        // 引擎增量视图（存量 body 不含，diff 工具不比较）：显隐裁决 + derive_rules 执行记录
        body.put("visibility", deriveView.get("visibility"));
        body.put("templateRulesApplied", deriveView.get("templateRulesApplied"));
        return body;
    }

    // ------------------------------------------------------------------
    // 模板 derive_rules 执行（set_default / when+visible/hidden / derive）
    // ------------------------------------------------------------------

    /** 执行合并模板的 derive_rules；返回显隐视图与执行记录（不写入 body/draft 主干之外）。 */
    private Map<String, Object> applyDeriveRules(Object categoryCode, Map<String, Object> draft,
                                                 Map<String, String> fillSources) {
        Map<String, Object> view = new LinkedHashMap<>();
        Map<String, Object> visibility = new LinkedHashMap<>();
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        view.put("visibility", visibility);
        view.put("templateRulesApplied", applied);

        var found = templateRegistry.findByCategory(str(categoryCode));
        if (found.isEmpty() || !(found.get().get("derive_rules") instanceof List<?> rules)) {
            return view;
        }
        for (Object r : rules) {
            if (!(r instanceof Map<?, ?> rule)) {
                continue;
            }
            if (rule.get("set_default") instanceof Map<?, ?> defaults) {
                boolean ifMissing = !Boolean.FALSE.equals(rule.get("if_missing"));
                for (Map.Entry<?, ?> e : defaults.entrySet()) {
                    String key = String.valueOf(e.getKey());
                    if (!ifMissing || empty(draft.get(key))) {
                        draft.put(key, e.getValue());
                        fillSources.put(key, "derive_rule");
                        applied.add("set_default:" + key);
                    }
                }
                continue;
            }
            Object when = rule.get("when");
            if (when instanceof Map<?, ?> whenMap) {
                if (whenMatches(draft, whenMap)) {
                    if (rule.get("visible") instanceof List<?> visibleList) {
                        for (Object f : visibleList) {
                            visibility.put(String.valueOf(f), "visible");
                        }
                    }
                    if (rule.get("hidden") instanceof List<?> hiddenList) {
                        for (Object f : hiddenList) {
                            visibility.put(String.valueOf(f), "hidden");
                        }
                    }
                    applied.add("when:" + whenMap);
                }
                continue;
            }
            if (rule.containsKey("derive")) {
                // §4.5 三级承载：数值派生归 SHACL/Java，模板引擎不硬解
                skipped.add("derive:" + rule.get("derive"));
            }
        }
        if (!skipped.isEmpty()) {
            log.debug("[derive_rules] 数值派生跳过（归 SHACL/Java）: {}", skipped);
        }
        return view;
    }

    /** when 条件匹配：全部键值按字符串语义相等（draft 值统一 display 值域，§4.4 契约）。 */
    private boolean whenMatches(Map<String, Object> draft, Map<?, ?> when) {
        for (Map.Entry<?, ?> e : when.entrySet()) {
            String expected = String.valueOf(e.getValue());
            Object actual = draft.get(String.valueOf(e.getKey()));
            if (empty(actual) || !expected.equals(str(actual).trim())) {
                return false;
            }
        }
        return !when.isEmpty();
    }

    // ------------------------------------------------------------------
    // P3-6 溯源链回放（PROV-O derivedFrom，"为什么默认500M"类问题）
    // ------------------------------------------------------------------

    /**
     * 字段默认值溯源链回放：按实际补全生效顺序（高优先层在后）逐层解析 field 的可补给默认，
     * 返回各层来源 + 生效层 + 引用规则，可回答"为什么该字段是默认值"。
     * {@code graph} 为现行图谱（bizScenarios/templates），与 derive() 入参一致。
     */
    public Map<String, Object> explainFieldDefault(String field, Map<String, Object> graph) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (field == null || field.isBlank()) {
            body.put("field", "");
            body.put("message", "field 必填");
            return body;
        }
        Map<String, Object> safeGraph = graph == null ? Map.of() : graph;
        Map<String, Object> scenarioDefaults = flattenScenarioDefaults(safeGraph);
        List<Map<String, Object>> chain = new ArrayList<>();

        // 层1：用户显式/上游 slot（无 draft 无法判定，仅说明若携带则以用户为准）
        Map<String, Object> userLayer = new LinkedHashMap<>();
        userLayer.put("layer", "user_said");
        userLayer.put("source", "用户/上游槽位显式给定");
        userLayer.put("rule", null);
        chain.add(userLayer);

        // 层2：模板要素补全（R-C02，element set 内的字段）
        Map<String, Object> templateLayer = layerWithValue("template", "R-C02", field,
                () -> firstElementValue(field, safeGraph));
        if (!empty(values(templateLayer).get(field))) {
            chain.add(templateLayer);
        }

        // 层3：场景默认（R-C01，含 ops_rules configDefault 兜底）
        Map<String, Object> scenarioLayer = layerWithValue("scenario_default", "R-C01", field,
                () -> scenarioDefaults.get(field));
        if (!empty(values(scenarioLayer).get(field))) {
            chain.add(scenarioLayer);
        }

        // 层4：ops_rules 配置默认兜底（configDefault），不入 scenario_default 时也回答来源
        Object opsDefault = opsRules.configDefaults().get(field);
        if (!empty(opsDefault)) {
            Map<String, Object> opsLayer = new LinkedHashMap<>();
            opsLayer.put("layer", "ops_rules_default");
            opsLayer.put("source", "外置 ops_rules 配置阈值/defaults");
            opsLayer.put("rule", "R-C01");
            Map<String, Object> v = new LinkedHashMap<>();
            v.put(field, opsDefault);
            opsLayer.put("value", v);
            chain.add(opsLayer);
        }

        Object effective = null;
        String effectiveLayer = null;
        String effectiveRule = null;
        // 实际执行补全 = 高优先层在后（user→template→scenario→ops_rules），取最后一个非空层
        for (Map<String, Object> layer : chain) {
            Object candidate = values(layer).get(field);
            if (!empty(candidate)) {
                effective = candidate;
                effectiveLayer = (String) layer.get("layer");
                effectiveRule = (String) layer.get("rule");
            }
        }
        if (effective == null) {
            effectiveLayer = null;
            effectiveRule = null;
        }

        // 移除空值层（保留 user_said 作为语义锚点）
        chain.removeIf(l -> empty(values(l).get(field)) && !"user_said".equals(l.get("layer")));

        body.put("field", field);
        body.put("value", effective);
        body.put("sourceLayer", effectiveLayer);
        body.put("rule", effectiveRule);
        body.put("provenance", "derivedFrom <- " + (effectiveLayer == null ? "none" : effectiveLayer));
        body.put("chain", chain);
        String wm = effectiveLayer == null
                ? "未发现任何默认层为该字段补默认（可能由用户/上游给定，或属推理输出）"
                : "字段默认值由 " + effectiveLayer + " 层补全（规则 " + effectiveRule + "）";
        body.put("message", wm);
        return body;
    }

    /** 汇总所有场景 defaults：同字段取第一个非空（场景无关地回答字段默认来源）。 */
    private Map<String, Object> flattenScenarioDefaults(Map<String, Object> graph) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> biz = castMap(graph.get("bizScenarios"));
        for (Object sc : biz.values()) {
            Map<String, Object> defaults = castMap(castMap(sc).get("defaults"));
            for (Map.Entry<String, Object> e : defaults.entrySet()) {
                if (empty(out.get(e.getKey())) && !empty(e.getValue())) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        }
        return out;
    }

    /** 模板 element 集首个非空值（familyMain/bb/add 等各品类模板顶层字段）。 */
    private Object firstElementValue(String field, Map<String, Object> graph) {
        for (Map<String, Object> t : templateRegistry.allResolved()) {
            Object v = t.get(field);
            if (!empty(v)) {
                return v;
            }
        }
        return null;
    }

    private Map<String, Object> layerWithValue(String layer, String rule, String field,
                                               java.util.function.Supplier<Object> supplier) {
        Map<String, Object> layerBox = new LinkedHashMap<>();
        Object value;
        try {
            value = supplier.get();
        } catch (Exception e) {
            value = null;
        }
        Map<String, Object> valueBox = new LinkedHashMap<>();
        valueBox.put(field, value);
        layerBox.put("layer", layer);
        layerBox.put("rule", rule);
        layerBox.put("value", valueBox);
        return layerBox;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> values(Map<String, Object> layer) {
        return (Map<String, Object>) layer.getOrDefault("value", Map.of());
    }

    private void mergeSlots(Map<String, Object> safeSlots, Map<String, Object> result,
                            Map<String, String> fillSources) {
        if (truthy(safeSlots.get("clearBindExisting"))) {
            result.put("bindExistingMainPkg", "");
            fillSources.put("bindExistingMainPkg", "user_said");
        }
        for (Map.Entry<String, Object> entry : safeSlots.entrySet()) {
            String key = entry.getKey();
            if ("clearBindExisting".equals(key)) {
                continue;
            }
            Object value = entry.getValue();
            if ("bindExistingMainPkg".equals(key)) {
                result.put(key, value);
                if (!empty(value)) {
                    fillSources.put(key, "user_said");
                }
                continue;
            }
            if (!empty(value)) {
                result.put(key, value);
                fillSources.put(key, "user_said");
            }
        }
    }

    /** 按品类选择配置模板：家庭附加用 TPL-FAMILY-ADD-20，避免套用家庭基础 128 主套餐模板。 */
    private Object resolveTemplateId(Map<String, Object> scenarioCfg, Map<String, Object> draft) {
        String root = str(firstNonEmpty(draft.get("messageRootKey"), draft.get("categoryCode")));
        if ("familyAddPrc".equals(root) || ("addon".equals(str(draft.get("offeringType")))
                && ("家庭融合".equals(str(draft.get("bizScenario")))
                || "家庭".equals(str(draft.get("targetUser")))))) {
            return "TPL-FAMILY-ADD-20";
        }
        if ("familyBasePrc".equals(root)) {
            Object tpl = scenarioCfg.get("templateId");
            return empty(tpl) ? "TPL-FAMILY-BASE-128" : tpl;
        }
        return scenarioCfg.get("templateId");
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        return objectMapper.convertValue(source, new TypeReference<>() { });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new HashMap<>();
    }

    private boolean empty(Object value) {
        return value == null || str(value).isBlank();
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        String text = str(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("1", "true", "yes", "y", "是").contains(text);
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object firstNonEmpty(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            if (!empty(v)) {
                return v;
            }
        }
        return null;
    }
}
