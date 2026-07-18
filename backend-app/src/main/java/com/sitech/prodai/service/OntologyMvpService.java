package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 本体平台 + 推理引擎（对齐 Python OntologyMvpService）。
 * 配置规则 R-C01~C08 / R-D01~D05；运营规则 R-A01~A05 / R-B01~B05。
 */
@Service
public class OntologyMvpService {

    private static final Pattern NUM_PATTERN = Pattern.compile("[\\d.]+");
    private static final Pattern FEE_PATTERN = Pattern.compile("月费\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern YUAN_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元");
    private static final Pattern BB_PATTERN = Pattern.compile("(\\d+)\\s*[Mm](?:宽带)?");
    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?:叫|名称[是为]?)\\s*[「\"]?([^「」\"，。\\s]+)[」\"]?");
    private static final Pattern MONTHS_PATTERN = Pattern.compile("(\\d+)\\s*个?月");

    private static final List<Map<String, String>> ONTOLOGY_CLASSES = List.of(
            metaClass("OfferingConfig", "商品配置草稿"),
            metaClass("ProductElement", "产品要素属性"),
            metaClass("ConfigRule", "配置规则"),
            metaClass("TargetUser", "目标用户"),
            metaClass("BizScenario", "业务场景"),
            metaClass("MarketPolicy", "营销政策"),
            metaClass("PricePlan", "商品定价"),
            metaClass("GoodsRelation", "商品关系"),
            metaClass("ConfigTemplate", "配置模板"),
            metaClass("ComplianceIssue", "合规问题")
    );

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ProdAiProperties properties;

    private Map<String, Object> graphCache;
    private final Map<String, Object> riskRuleOverrides = new ConcurrentHashMap<>();

    public OntologyMvpService(ObjectMapper objectMapper,
                              ResourceLoader resourceLoader,
                              ProdAiProperties properties) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        loadGraph();
    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> loadGraph() {
        if (graphCache != null) {
            return graphCache;
        }
        try {
            Resource resource = resourceLoader.getResource(properties.getOntology().getGraphPath());
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> raw = objectMapper.readValue(in, new TypeReference<>() {});
                List<Map<String, Object>> base = castListOfMaps(raw.get("shelfOfferings"));
                Map<String, Object> plan = castMap(raw.get("samplePlan"));
                raw.put("shelfOfferings", expandShelfOfferings(base, plan));
                graphCache = raw;
                return graphCache;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load ontology mock graph: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getGraphSummary() {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> rules = riskRules();
        List<Map<String, Object>> offerings = castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> opsGraph = castMap(graph.get("opsGraph"));
        int anomalyCount = opsGraph == null ? 0 : opsGraph.size();

        List<Map<String, Object>> shelfPreview = offerings.stream()
                .filter(o -> {
                    String category = str(o.get("category"));
                    return Set.of("zero_fee", "low_eff", "abnormal_discount", "whitelist").contains(category)
                            || "OF-HF-128".equals(o.get("offeringId"));
                })
                .map(o -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("offeringId", o.get("offeringId"));
                    row.put("offeringName", o.get("offeringName"));
                    row.put("state", o.get("state"));
                    row.put("monthlyFee", o.get("monthlyFee"));
                    row.put("category", o.get("category"));
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("scenarios", new ArrayList<>(castMap(graph.get("bizScenarios")).keySet()));
        body.put("templates", new ArrayList<>(castMap(graph.get("templates")).keySet()));
        body.put("shelfCount", offerings.size());
        body.put("anomalyOfferingCount", anomalyCount);
        body.put("ruleVersion", rules.getOrDefault("ruleVersion", "RiskRules-v1.2"));
        body.put("riskRules", rules);
        body.put("shelfOfferings", shelfPreview);
        body.put("classes", ONTOLOGY_CLASSES);
        body.put("relations", List.of(
                "hasElement", "hasPricePlan", "constrainedBy", "forTargetUser",
                "inScenario", "appliesPolicy", "basedOnTemplate", "hasRelation",
                "hasIssue", "blocksCombination", "suggestsDefault", "definesElement"
        ));
        body.put("ruleSets", Map.of(
                "config", List.of("R-C01", "R-C02", "R-C03", "R-C04", "R-C05", "R-C06", "R-C07", "R-C08"),
                "batch", List.of("R-D01", "R-D02", "R-D03", "R-D04", "R-D05"),
                "opsRootCause", List.of("R-A01", "R-A02", "R-A03", "R-A04", "R-A05"),
                "opsRisk", List.of("R-B01", "R-B02", "R-B03", "R-B04", "R-B05")
        ));
        return body;
    }

    public Map<String, Object> getOntologyMeta() {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("classes", ONTOLOGY_CLASSES);
        body.put("bizScenarios", castMap(graph.get("bizScenarios")));
        body.put("templates", castMap(graph.get("templates")));
        body.put("equityGiftWhitelist", castList(graph.get("equityGiftWhitelist")));
        body.put("riskRuleDefaults", riskRules());
        return body;
    }

    public Map<String, Object> inferFields(Map<String, Object> slots, Map<String, Object> draft) {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> result = deepCopy(draft == null ? Map.of() : draft);
        Map<String, String> fillSources = new LinkedHashMap<>();
        Set<String> appliedRules = new LinkedHashSet<>();
        Map<String, Object> safeSlots = slots == null ? Map.of() : slots;

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

        String scenario = str(firstNonEmpty(result.get("bizScenario"), safeSlots.get("bizScenario")));
        Map<String, Object> scenarioCfg = castMap(castMap(graph.get("bizScenarios")).get(scenario));
        Map<String, Object> defaults = castMap(scenarioCfg.get("defaults"));

        if ("家庭融合".equals(scenario)) {
            for (Map.Entry<String, Object> e : defaults.entrySet()) {
                if (empty(result.get(e.getKey()))) {
                    result.put(e.getKey(), e.getValue());
                    fillSources.put(e.getKey(), "scenario_default");
                    appliedRules.add("R-C01");
                }
            }
            if (empty(result.get("includeBroadband"))) {
                result.put("includeBroadband", defaults.getOrDefault("includeBroadband", "500M"));
                fillSources.put("includeBroadband", "scenario_default");
                appliedRules.add("R-C01");
            }
        }

        boolean isCampus = "校园".equals(str(result.get("targetUser"))) || "校园体验".equals(scenario);
        String offeringType = str(firstNonEmpty(result.get("offeringType"), safeSlots.get("offeringType")));
        if (isCampus && empty(result.get("monthlyFee")) && !"addon".equals(offeringType)) {
            result.put("monthlyFee", defaults.getOrDefault("monthlyFee", 59));
            fillSources.put("monthlyFee", "template");
            appliedRules.add("R-C02");
            for (Map.Entry<String, Object> e : defaults.entrySet()) {
                if (!"monthlyFee".equals(e.getKey()) && empty(result.get(e.getKey()))) {
                    result.put(e.getKey(), e.getValue());
                    fillSources.put(e.getKey(), "scenario_default");
                }
            }
        } else if (isCampus && "addon".equals(offeringType)) {
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

        Object templateId = scenarioCfg.get("templateId");
        if (templateId != null && empty(result.get("basedOnTemplate"))) {
            result.put("basedOnTemplate", templateId);
            fillSources.put("basedOnTemplate", "template");
        }
        if (empty(result.get("channelScope"))) {
            result.put("channelScope", "全渠道");
            fillSources.put("channelScope", "scenario_default");
        }
        if (empty(result.get("mutexGroup"))) {
            result.put("mutexGroup", defaults.getOrDefault("mutexGroup", "MAIN_PKG"));
            fillSources.put("mutexGroup", "scenario_default");
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
        return body;
    }

    public Map<String, Object> checkCompliance(Map<String, Object> draftInput) {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> draft = draftInput == null ? Map.of() : draftInput;
        List<Map<String, Object>> issues = new ArrayList<>();

        List<String[]> required = List.of(
                new String[]{"offeringName", "商品名称"},
                new String[]{"monthlyFee", "月费"},
                new String[]{"targetUser", "目标用户"},
                new String[]{"channelScope", "销售渠道"}
        );
        for (String[] item : required) {
            if (empty(draft.get(item[0]))) {
                issues.add(issue("R-C06", "必填缺失", "MEDIUM", item[0],
                        "缺少必填字段：" + item[1], List.of(item[0] + "=empty"), null));
            }
        }

        String mutexGroup = str(firstNonEmpty(draft.get("mutexGroup"), "MAIN_PKG"));
        Object bindId = draft.get("bindExistingMainPkg");
        Map<String, Map<String, Object>> shelf = castListOfMaps(graph.get("shelfOfferings")).stream()
                .collect(Collectors.toMap(o -> str(o.get("offeringId")), o -> o, (a, b) -> a, LinkedHashMap::new));
        if (!empty(bindId) && shelf.containsKey(str(bindId))) {
            Map<String, Object> existing = shelf.get(str(bindId));
            String offeringType = str(draft.get("offeringType"));
            boolean typeMatch = offeringType.isEmpty()
                    || "main_pkg".equals(offeringType)
                    || "fusion".equals(offeringType);
            if (mutexGroup.equals(str(existing.get("mutexGroup"))) && typeMatch) {
                List<Map<String, Object>> triples = new ArrayList<>();
                triples.add(triple(firstNonEmpty(draft.get("offeringName"), "当前草稿"), "mutexGroup", mutexGroup));
                triples.add(triple(bindId, "mutexGroup", existing.get("mutexGroup")));
                triples.add(triple(firstNonEmpty(draft.get("offeringName"), "当前草稿"),
                        "blocksCombination", existing.get("offeringName")));
                issues.add(issue("R-C03", "资费/关系冲突", "HIGH", "mutexGroup",
                        "与在架商品 " + existing.get("offeringName") + "(" + bindId + ") 同属互斥组 "
                                + mutexGroup + "，不可同时上架",
                        List.of(
                                "当前草稿—互斥组—" + mutexGroup,
                                bindId + "—互斥组—" + existing.get("mutexGroup"),
                                "当前草稿—冲突对象—" + existing.get("offeringName")
                        ),
                        triples));
            }
        }

        if ("addon".equals(str(draft.get("offeringType"))) && empty(draft.get("dependOn"))) {
            issues.add(issue("R-C04", "规则漏洞", "HIGH", "dependOn",
                    "附加包缺少依赖的主服务/宽带",
                    List.of("offeringType=addon", "dependOn=empty"), null));
        }

        double monthly = num(draft.get("monthlyFee"), -1);
        double oneTime = num(draft.get("oneTimeFee"), 0);
        String scenario = str(draft.get("bizScenario"));
        List<Object> whitelist = castList(graph.get("equityGiftWhitelist"));
        if (monthly == 0 && oneTime == 0 && !truthy(draft.get("hasContract"))) {
            if (!whitelist.contains(scenario) && !"内部验证".equals(str(draft.get("channelScope")))) {
                issues.add(issue("R-C05", "高风险资费", "HIGH", "monthlyFee",
                        "月费/一次性费均为0且无合约，非权益赠送白名单",
                        List.of("monthlyFee=0", "oneTimeFee=0", "hasContract=0"), null));
            }
        }

        double discount = num(draft.get("discountPercent"), -1);
        if (discount == 100 && truthy(draft.get("repeatable"))) {
            issues.add(issue("R-C07", "异常优惠漏洞", "HIGH", "discountPercent",
                    "优惠折扣100%且可重复订购，存在异常优惠漏洞",
                    List.of("discountPercent=100", "repeatable=true"), null));
        }

        boolean hasHigh = issues.stream().anyMatch(i -> "HIGH".equals(i.get("issueLevel")));
        boolean requiredOk = issues.stream().noneMatch(i -> "R-C06".equals(i.get("ruleId")));
        boolean compliancePass = !hasHigh && requiredOk;

        List<String> applied = compliancePass
                ? List.of("R-C08")
                : issues.stream().map(i -> str(i.get("ruleId"))).distinct().sorted().collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("issues", issues);
        body.put("compliancePass", compliancePass);
        body.put("appliedRules", applied);
        body.put("canSubmit", compliancePass);
        return body;
    }

    public Map<String, Object> chatConfigure(String text, Map<String, Object> draft) {
        Map<String, Object> slots = parseSlotsFromText(text == null ? "" : text);
        Map<String, Object> infer = inferFields(slots, draft);
        @SuppressWarnings("unchecked")
        Map<String, Object> inferredDraft = (Map<String, Object>) infer.get("draft");
        Map<String, Object> compliance = checkCompliance(inferredDraft);

        Set<String> applied = new LinkedHashSet<>();
        castList(infer.get("appliedRules")).forEach(r -> applied.add(str(r)));
        castList(compliance.get("appliedRules")).forEach(r -> applied.add(str(r)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("intent", "create_offering_config");
        body.put("slots", slots);
        body.put("draft", inferredDraft);
        body.put("inferredFields", infer.get("inferredFields"));
        body.put("recommendedTemplates", infer.get("recommendedTemplates"));
        body.put("issues", compliance.get("issues"));
        body.put("compliancePass", compliance.get("compliancePass"));
        body.put("appliedRules", applied.stream().sorted().collect(Collectors.toList()));
        body.put("canSubmit", compliance.get("canSubmit"));
        return body;
    }

    public Map<String, Object> batchFromDocument(String documentText, List<Map<String, Object>> packages) {
        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> pkgs = packages;
        if (pkgs == null || pkgs.isEmpty()) {
            pkgs = defaultCampusPackages();
            if (documentText != null && !documentText.isBlank()) {
                List<Map<String, Object>> extracted = extractPackagesMock(documentText);
                if (!extracted.isEmpty()) {
                    pkgs = extracted;
                }
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (int idx = 0; idx < pkgs.size(); idx++) {
            Map<String, Object> slots = deepCopy(pkgs.get(idx));
            if (empty(slots.get("bizScenario"))) {
                slots.put("bizScenario", "校园体验");
            }
            Map<String, Object> infer = inferFields(slots, null);
            @SuppressWarnings("unchecked")
            Map<String, Object> draft = (Map<String, Object>) infer.get("draft");
            Map<String, Object> compliance = checkCompliance(draft);

            Set<String> applied = new LinkedHashSet<>();
            castList(infer.get("appliedRules")).forEach(r -> applied.add(str(r)));
            castList(compliance.get("appliedRules")).forEach(r -> applied.add(str(r)));
            applied.add("R-D01");
            applied.add("R-D02");
            applied.add("R-D04");

            boolean pass = Boolean.TRUE.equals(compliance.get("compliancePass"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", idx + 1);
            item.put("sourceExcerpt", slots.getOrDefault("sourceExcerpt", ""));
            item.put("draft", draft);
            item.put("inferredFields", infer.get("inferredFields"));
            item.put("issues", compliance.get("issues"));
            item.put("compliancePass", pass);
            item.put("status", pass ? "通过" : "待修正");
            item.put("appliedRules", applied.stream().sorted().collect(Collectors.toList()));
            items.add(item);
        }

        List<Map<String, Object>> passed = items.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("compliancePass")))
                .collect(Collectors.toList());
        List<Map<String, Object>> confirmable = passed.stream().map(i -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i.get("index"));
            @SuppressWarnings("unchecked")
            Map<String, Object> draft = (Map<String, Object>) i.get("draft");
            row.put("offeringName", draft == null ? null : draft.get("offeringName"));
            return row;
        }).collect(Collectors.toList());

        Map<String, Object> campus = castMap(castMap(graph.get("bizScenarios")).get("校园体验"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", items.size());
        body.put("passedCount", passed.size());
        body.put("pendingCount", items.size() - passed.size());
        body.put("items", items);
        body.put("appliedRules", List.of("R-D01", "R-D02", "R-D03", "R-D04", "R-D05"));
        body.put("confirmableDrafts", confirmable);
        body.put("scenario", campus.get("scenarioId"));
        return body;
    }

    public Map<String, Object> getOpsDashboard() {
        Map<String, Object> root = analyzeRootCause("OF-HF-128");
        Map<String, Object> risk = auditRisks(null);
        Map<String, Object> rules = riskRules();

        List<Map<String, Object>> alerts = new ArrayList<>();
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("id", "alert-hf-128");
        a1.put("type", "anomaly");
        a1.put("tag", "异动");
        a1.put("offeringId", "OF-HF-128");
        a1.put("text", "OF-HF-128 累计收入环比 -18%");
        alerts.add(a1);
        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("id", "alert-risk");
        a2.put("type", "risk");
        a2.put("tag", "风险");
        a2.put("text", "高风险在架商品 " + risk.getOrDefault("highCount", 0) + " 个待处置");
        alerts.add(a2);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("anomalyOfferingCount", Boolean.TRUE.equals(root.get("success")) ? 1 : 0);
        body.put("highRiskCount", risk.getOrDefault("highCount", 0));
        body.put("mediumRiskCount", risk.getOrDefault("mediumCount", 0));
        body.put("suggestDelistCount", risk.getOrDefault("suggestDelistCount", 0));
        body.put("shelfCount", risk.getOrDefault("scannedCount", 0));
        body.put("ruleVersion", rules.getOrDefault("ruleVersion", "RiskRules-v1.2"));
        body.put("lastAuditAt", risk.get("auditedAt"));
        body.put("alerts", alerts);
        return body;
    }

    public Map<String, Object> updateRiskRules(Map<String, Object> overrides) {
        if (overrides != null) {
            Set<String> allowed = Set.of(
                    "zeroSalesShelfDays", "zeroSalesDaysWindow",
                    "highRiskReviewDays", "lowRevenuePercentile", "ruleVersion"
            );
            for (Map.Entry<String, Object> e : overrides.entrySet()) {
                if (allowed.contains(e.getKey()) && e.getValue() != null) {
                    riskRuleOverrides.put(e.getKey(), e.getValue());
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("riskRules", riskRules());
        return body;
    }

    public Map<String, Object> resetRiskRules() {
        riskRuleOverrides.clear();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("riskRules", riskRules());
        return body;
    }

    public Map<String, Object> parseSlotsFromText(String text) {
        Map<String, Object> slots = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return slots;
        }

        if (containsAny(text, "家庭融合", "家庭用户", "融合套餐")) {
            slots.put("bizScenario", "家庭融合");
            slots.put("targetUser", "家庭");
            slots.put("offeringType", "fusion");
        } else if (containsAny(text, "校园", "大学生", "迎新")) {
            slots.put("bizScenario", "校园体验");
            slots.put("targetUser", "校园");
            slots.put("offeringType", "main_pkg");
        } else if (text.contains("5G") || text.contains("5g")) {
            slots.put("bizScenario", "5G个人主套餐");
            slots.put("targetUser", "个人");
            slots.put("offeringType", "main_pkg");
        }

        Matcher feeM = FEE_PATTERN.matcher(text);
        if (feeM.find()) {
            slots.put("monthlyFee", Double.parseDouble(feeM.group(1)));
        } else {
            Matcher yuanM = YUAN_PATTERN.matcher(text);
            if (yuanM.find()) {
                slots.put("monthlyFee", Double.parseDouble(yuanM.group(1)));
            }
        }

        Matcher bbM = BB_PATTERN.matcher(text);
        if (bbM.find() && (text.contains("宽带") || text.contains("家庭"))) {
            slots.put("includeBroadband", bbM.group(1) + "M");
        }

        if (text.contains("全渠道")) {
            slots.put("channelScope", "全渠道");
        } else if (text.contains("电渠") && text.contains("厅店")) {
            slots.put("channelScope", "电渠+厅店");
        } else if (text.contains("电渠")) {
            slots.put("channelScope", "仅电渠");
        }

        Matcher nameM = NAME_PATTERN.matcher(text);
        if (nameM.find()) {
            slots.put("offeringName", nameM.group(1));
        } else if (text.contains("家庭融合畅享158")) {
            slots.put("offeringName", "家庭融合畅享158");
        }

        if (containsAny(text, "不加128", "不加畅享128", "不绑128", "单独上", "取消绑定", "解除互斥")) {
            slots.put("bindExistingMainPkg", "");
            slots.put("clearBindExisting", true);
        } else if (containsAny(text, "再绑", "再加", "一起上", "畅享128", "OF-HF-128")) {
            slots.put("bindExistingMainPkg", "OF-HF-128");
        }

        if (text.contains("无合约") || text.contains("没有合约")) {
            slots.put("hasContract", "0");
        }
        if (text.contains("有合约") || text.contains("协议期") || text.contains("补协议")) {
            slots.put("hasContract", "1");
            Matcher monthsM = MONTHS_PATTERN.matcher(text);
            if (monthsM.find()) {
                slots.put("contractMonths", Integer.parseInt(monthsM.group(1)));
            }
        }
        if (text.contains("可重复")) {
            slots.put("repeatable", "true");
        }
        if (text.contains("不可重复") || text.contains("不能重复")) {
            slots.put("repeatable", "false");
        }
        if (text.contains("0元") || text.contains("零元")) {
            slots.put("monthlyFee", 0);
        }
        if (text.contains("内部验证")) {
            slots.put("channelScope", "内部验证");
        }
        return slots;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeRootCause(String offeringId) {
        String oid = empty(offeringId) ? "OF-HF-128" : offeringId;
        Map<String, Object> graph = loadGraph();
        Map<String, Object> node = castMap(castMap(graph.get("opsGraph")).get(oid));
        Map<String, Object> offering = castListOfMaps(graph.get("shelfOfferings")).stream()
                .filter(o -> oid.equals(str(o.get("offeringId"))))
                .findFirst()
                .orElse(null);
        if (node.isEmpty() || offering == null) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "未找到商品图谱节点 " + oid);
            return fail;
        }

        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (Map<String, Object> m : castListOfMaps(node.get("metrics"))) {
            Object deltaObj = m.get("metricDelta");
            if (deltaObj != null && num(deltaObj, 0) <= -0.10) {
                double delta = num(deltaObj, 0);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("metricCode", m.get("metricCode"));
                row.put("metricValue", m.get("metricValue"));
                row.put("metricDelta", delta);
                row.put("ruleId", "R-A01");
                row.put("anomalyFlag", true);
                row.put("message", m.get("metricCode") + "环比 " + Math.round(delta * 100) + "%");
                anomalies.add(row);
            } else if (truthy(m.get("anomaly"))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("metricCode", m.get("metricCode"));
                row.put("metricValue", m.get("metricValue"));
                row.put("metricDeltaPp", m.get("metricDeltaPp"));
                row.put("ruleId", "R-A01");
                row.put("anomalyFlag", true);
                row.put("message", m.get("metricCode") + "异动 " + m.get("metricDeltaPp") + "pp");
                anomalies.add(row);
            }
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        List<Map<String, Object>> triples = new ArrayList<>();

        for (Map<String, Object> ch : castListOfMaps(node.get("channels"))) {
            double orderDelta = num(ch.get("orderDelta"), 0);
            double contrib = num(ch.get("contribRatio"), 0);
            if (orderDelta <= -0.20 && contrib >= 0.30) {
                double weight = num(ch.get("weightHint"), contrib);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("type", "Channel");
                c.put("id", ch.get("channelId"));
                c.put("name", ch.get("name"));
                c.put("score", weight);
                c.put("weight", weight);
                c.put("ruleId", "R-A02");
                c.put("evidence", List.of(
                        "订购量变化 " + Math.round(orderDelta * 100) + "%",
                        "渠道贡献占比 " + Math.round(contrib * 100) + "%"
                ));
                c.put("path", List.of(
                        oid + "-hasMetric->累计收入",
                        oid + "-soldOn->" + ch.get("channelId"),
                        "Metric-relatedToChannel->" + ch.get("channelId")
                ));
                Map<String, Object> drill = new LinkedHashMap<>();
                drill.put("orderDelta", orderDelta);
                drill.put("contribRatio", contrib);
                Object trend = ch.get("trend");
                if (trend == null) {
                    drill.put("trend", List.of(
                            Map.of("label", "T-2", "value", 100),
                            Map.of("label", "T-1", "value", 82),
                            Map.of("label", "T0", "value", Math.round(100 * (1 + orderDelta)))
                    ));
                } else {
                    drill.put("trend", trend);
                }
                c.put("drill", drill);
                candidates.add(c);
                triples.add(triple(oid, "soldOn", ch.get("channelId")));
                triples.add(triple(ch.get("channelId"), "orderDelta", orderDelta));
                triples.add(triple(ch.get("channelId"), "contribRatio", contrib));
            }
        }

        for (Map<String, Object> pr : castListOfMaps(node.get("promotions"))) {
            int days = (int) num(pr.get("daysToExpire"), 999);
            double driven = num(pr.get("drivenOrderRatio"), 0);
            if (days <= 7 && driven >= 0.25) {
                double weight = num(pr.get("weightHint"), driven);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("type", "Promotion");
                c.put("id", pr.get("promoId"));
                c.put("name", pr.get("name"));
                c.put("score", weight);
                c.put("weight", weight);
                c.put("ruleId", "R-A03");
                c.put("evidence", List.of(
                        days + " 日后到期",
                        "历史带动订购占比 " + Math.round(driven * 100) + "%"
                ));
                c.put("path", List.of(
                        oid + "-participatesIn->" + pr.get("promoId"),
                        pr.get("promoId") + "-daysToExpire->" + days
                ));
                candidates.add(c);
                triples.add(triple(oid, "participatesIn", pr.get("promoId")));
                triples.add(triple(pr.get("promoId"), "daysToExpire", days));
                triples.add(triple(pr.get("promoId"), "drivenOrderRatio", driven));
            }
        }

        for (Map<String, Object> cp : castListOfMaps(node.get("competitors"))) {
            double gapRatio = num(cp.get("priceGapRatio"), 0);
            double penet = num(cp.get("penetrationDeltaPp"), 0);
            if (gapRatio >= 0.15 && penet > 0) {
                double weight = num(cp.get("weightHint"), Math.round(gapRatio * 100.0) / 100.0);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("type", "Competitor");
                c.put("id", cp.get("competitorId"));
                c.put("name", cp.get("name"));
                c.put("score", weight);
                c.put("weight", weight);
                c.put("ruleId", "R-A04");
                c.put("evidence", List.of(
                        "月费低 " + cp.get("priceGap") + " 元（约 " + String.format("%.1f", gapRatio * 100) + "%）",
                        "本地渗透率 +" + cp.get("penetrationDeltaPp") + "pp"
                ));
                c.put("path", List.of(
                        oid + "-competesWith->" + cp.get("competitorId"),
                        cp.get("competitorId") + "-priceGapRatio->" + gapRatio
                ));
                candidates.add(c);
                triples.add(triple(oid, "competesWith", cp.get("competitorId")));
                triples.add(triple(cp.get("competitorId"), "priceGap", cp.get("priceGap")));
                triples.add(triple(cp.get("competitorId"), "penetrationDeltaPp", cp.get("penetrationDeltaPp")));
            }
        }

        for (Map<String, Object> ub : castListOfMaps(node.get("behaviors"))) {
            double weight = num(ub.get("weightHint"), 0.08);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("type", "UserBehavior");
            c.put("id", ub.get("behaviorId"));
            c.put("name", ub.get("name"));
            c.put("score", weight);
            c.put("weight", weight);
            c.put("ruleId", "R-A05");
            c.put("evidence", List.of(str(ub.get("name")), "行为佐证"));
            c.put("path", List.of(oid + "-influencedBy->" + ub.get("behaviorId")));
            candidates.add(c);
        }

        candidates.sort((a, b) -> Double.compare(num(b.get("score"), 0), num(a.get("score"), 0)));
        List<Map<String, Object>> top3 = candidates.stream().limit(3).collect(Collectors.toList());

        Map<String, Object> suggestionsMap = castMap(graph.get("actionSuggestions"));
        List<String> actionList = new ArrayList<>();
        for (Map<String, Object> c : top3) {
            for (Object a : castList(suggestionsMap.get(str(c.get("type"))))) {
                String action = str(a);
                if (!actionList.contains(action)) {
                    actionList.add(action);
                }
            }
        }

        List<Map<String, Object>> paths = new ArrayList<>();
        for (int i = 0; i < top3.size(); i++) {
            Map<String, Object> c = top3.get(i);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("rank", i + 1);
            p.put("rootCauseType", c.get("type"));
            p.put("name", c.get("name"));
            p.put("weight", c.get("weight"));
            p.put("ruleId", c.get("ruleId"));
            p.put("evidence", c.get("evidence"));
            p.put("path", c.getOrDefault("path", List.of()));
            p.put("drill", c.get("drill"));
            p.put("isPrimary", i == 0);
            paths.add(p);
        }

        Map<String, Object> workOrder = new LinkedHashMap<>();
        workOrder.put("title", offering.get("offeringName") + "产品优化工单草稿");
        workOrder.put("offeringId", oid);
        workOrder.put("anomalySummary", anomalies.isEmpty() ? "指标异动" : anomalies.get(0).get("message"));
        workOrder.put("actions", actionList);
        workOrder.put("rootCauses", paths.stream().map(p -> {
            Map<String, Object> rc = new LinkedHashMap<>();
            rc.put("type", p.get("rootCauseType"));
            rc.put("name", p.get("name"));
            rc.put("ruleId", p.get("ruleId"));
            return rc;
        }).collect(Collectors.toList()));
        workOrder.put("status", "draft");
        workOrder.put("source", "ontology_rules");

        String snapshotAt = Instant.now().toString();
        Map<String, Object> reportEvidence = new LinkedHashMap<>();
        reportEvidence.put("intent", "root_cause_analysis");
        reportEvidence.put("offeringId", oid);
        reportEvidence.put("offeringName", offering.get("offeringName"));
        Map<String, Object> anomaly = new LinkedHashMap<>();
        anomaly.put("metric", anomalies.isEmpty() ? "累计收入" : anomalies.get(0).get("metricCode"));
        anomaly.put("delta", anomalies.isEmpty() ? -0.18 : anomalies.get(0).getOrDefault("metricDelta", -0.18));
        reportEvidence.put("anomaly", anomaly);
        reportEvidence.put("rootCauses", paths.stream().map(p -> {
            Map<String, Object> rc = new LinkedHashMap<>();
            rc.put("type", p.get("rootCauseType"));
            rc.put("name", p.get("name"));
            rc.put("score", p.get("weight"));
            rc.put("rule", p.get("ruleId"));
            return rc;
        }).collect(Collectors.toList()));
        reportEvidence.put("snapshotAt", snapshotAt);

        Set<String> applied = new LinkedHashSet<>();
        anomalies.forEach(a -> applied.add(str(a.get("ruleId"))));
        top3.forEach(c -> applied.add(str(c.get("ruleId"))));
        applied.add("R-A05");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("offeringId", oid);
        body.put("offeringName", offering.get("offeringName"));
        body.put("anomalies", anomalies);
        body.put("candidates", candidates);
        body.put("paths", paths);
        body.put("actionList", actionList);
        body.put("workOrder", workOrder);
        body.put("evidenceTriples", triples);
        body.put("reportEvidence", reportEvidence);
        body.put("market", castMap(node.get("market")));
        body.put("graphScope", Map.of(
                "center", oid,
                "nodes", List.of("Metric", "Channel", "Promotion", "Competitor", "UserBehavior", "MarketScope")
        ));
        body.put("appliedRules", applied.stream().sorted().collect(Collectors.toList()));
        body.put("snapshotAt", snapshotAt);
        return body;
    }

    public Map<String, Object> auditRisks(List<String> offeringIds) {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> rules = riskRules();
        List<Map<String, Object>> allOfferings = castListOfMaps(graph.get("shelfOfferings"));
        int scannedCount = allOfferings.size();
        List<Map<String, Object>> offerings = allOfferings;
        if (offeringIds != null && !offeringIds.isEmpty()) {
            Set<String> idSet = new LinkedHashSet<>(offeringIds);
            offerings = allOfferings.stream()
                    .filter(o -> idSet.contains(str(o.get("offeringId"))))
                    .collect(Collectors.toList());
        }

        Set<String> whitelist = castList(graph.get("equityGiftWhitelist")).stream()
                .map(this::str)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Object> riskActions = castMap(graph.get("riskActions"));
        int zeroShelfDays = (int) num(rules.get("zeroSalesShelfDays"), 180);
        int reviewDays = (int) num(rules.get("highRiskReviewDays"), 30);
        double lowPct = num(rules.get("lowRevenuePercentile"), 0.05);

        List<Double> allRevenues = allOfferings.stream()
                .map(o -> num(o.get("revenue30d"), 0))
                .sorted()
                .collect(Collectors.toList());
        int cutoffIdx = allRevenues.isEmpty() ? 0 : Math.max(0, (int) (allRevenues.size() * lowPct) - 1);
        double lowThreshold = allRevenues.isEmpty() ? 0 : allRevenues.get(cutoffIdx);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> o : offerings) {
            List<Map<String, Object>> risks = new ArrayList<>();
            String riskLevel = "LOW";
            List<String> actions = new ArrayList<>();
            List<Map<String, Object>> evidenceTriples = new ArrayList<>();
            boolean suggestDelist = false;

            double monthly = num(o.get("monthlyFee"), -1);
            double oneTime = num(o.get("oneTimeFee"), 0);
            String wlTag = str(o.get("whitelistTag"));
            String name = str(o.get("offeringName"));
            boolean inWhitelist = whitelist.contains(wlTag)
                    || whitelist.stream().anyMatch(name::contains);

            if (monthly == 0 && oneTime == 0 && !inWhitelist) {
                risks.add(riskFeature("R-B01", "零元资费", "月费与一次性费均为0且非权益赠送白名单"));
                evidenceTriples.add(triple(o.get("offeringId"), "hasPricePlan", "PP-" + o.get("offeringId")));
                evidenceTriples.add(triple("PP-" + o.get("offeringId"), "monthlyFee", 0));
                evidenceTriples.add(triple("PP-" + o.get("offeringId"), "oneTimeFee", 0));
                if ("上架".equals(str(o.get("state"))) && !truthy(o.get("hasContract"))) {
                    risks.add(riskFeature("R-B02", "零元无合约在架", "零元资费已上架且无合约约束"));
                    evidenceTriples.add(triple(o.get("offeringId"), "hasContract", false));
                    riskLevel = "HIGH";
                    Map<String, Object> act = castMap(riskActions.get("零元资费"));
                    actions.add(str(act.getOrDefault("defaultAction", "建议立即下架或转验证渠道")));
                }
            }

            if (num(o.get("discountPercent"), -1) >= 100
                    && truthy(o.get("repeatable"))
                    && empty(o.get("targetCustomerGroup"))) {
                risks.add(riskFeature("R-B01", "异常全额赠送", "折扣100% + 可重复订购 + 无目标客户群"));
                riskLevel = "HIGH";
                Map<String, Object> act = castMap(riskActions.get("异常全额赠送"));
                actions.add(str(act.getOrDefault("defaultAction", "限售 + 复核优惠规则")));
            }

            if (num(o.get("salesCnt30d"), -1) == 0 && num(o.get("shelfDays"), 0) > zeroShelfDays) {
                risks.add(riskFeature("R-B03", "长期零销",
                        "近30日销量0且在架" + o.get("shelfDays") + "天（阈值>" + zeroShelfDays + "）"));
                if (!"HIGH".equals(riskLevel)) {
                    riskLevel = "MEDIUM";
                }
                Map<String, Object> act = castMap(riskActions.get("长期零销"));
                actions.add(str(act.getOrDefault("defaultAction", "建议下架/归档")));
                suggestDelist = true;
                evidenceTriples.add(triple(o.get("offeringId"), "salesCnt30d", 0));
                evidenceTriples.add(triple(o.get("offeringId"), "shelfDays", o.get("shelfDays")));
            }

            String category = str(o.get("category"));
            if (Set.of("low_eff", "threshold_demo").contains(category)
                    && num(o.get("revenue30d"), 0) <= lowThreshold
                    && !truthy(o.get("strategicTag"))) {
                risks.add(riskFeature("R-B04", "低效产商品", "近90日收入贡献排名后5%且无战略标签"));
                if ("LOW".equals(riskLevel)) {
                    riskLevel = "MEDIUM";
                }
                Map<String, Object> act = castMap(riskActions.get("低效产商品"));
                actions.add(str(act.getOrDefault("defaultAction", "纳入优胜劣汰池")));
                suggestDelist = true;
            }

            boolean urgent = false;
            if ("HIGH".equals(riskLevel) && num(o.get("shelfDays"), 0) > reviewDays) {
                risks.add(riskFeature("R-B05", "预警升级", "高风险且上架超过" + reviewDays + "天未复核"));
                actions.add("紧急复核");
                urgent = true;
            }

            if (!risks.isEmpty()) {
                int score = "HIGH".equals(riskLevel) ? 92 : ("MEDIUM".equals(riskLevel) ? 70 : 40);
                if (urgent) {
                    score = Math.min(99, score + 5);
                }
                List<String> uniqueActions = actions.stream().distinct().collect(Collectors.toList());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("offeringId", o.get("offeringId"));
                row.put("offeringName", o.get("offeringName"));
                row.put("state", o.get("state"));
                row.put("monthlyFee", o.get("monthlyFee"));
                row.put("oneTimeFee", o.get("oneTimeFee"));
                row.put("shelfDays", o.get("shelfDays"));
                row.put("salesCnt30d", o.get("salesCnt30d"));
                row.put("revenue30d", o.get("revenue30d"));
                row.put("hasContract", o.get("hasContract"));
                row.put("strategicTag", o.get("strategicTag"));
                row.put("riskLevel", riskLevel);
                row.put("riskScore", score);
                row.put("urgent", urgent);
                row.put("suggestDelist", suggestDelist);
                row.put("risks", risks);
                row.put("actions", uniqueActions);
                row.put("evidenceTriples", evidenceTriples);
                Map<String, Object> disposition = new LinkedHashMap<>();
                disposition.put("defaultAction", uniqueActions.isEmpty() ? "关注" : uniqueActions.get(0));
                disposition.put("needConfirm", "HIGH".equals(riskLevel));
                row.put("disposition", disposition);
                results.add(row);
            }
        }

        results.sort((a, b) -> Integer.compare(
                ((Number) b.get("riskScore")).intValue(),
                ((Number) a.get("riskScore")).intValue()));

        long highCount = results.stream().filter(r -> "HIGH".equals(r.get("riskLevel"))).count();
        long mediumCount = results.stream().filter(r -> "MEDIUM".equals(r.get("riskLevel"))).count();
        long suggestDelistCount = results.stream().filter(r -> Boolean.TRUE.equals(r.get("suggestDelist"))).count();

        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("manualSampleRate", 0.05);
        coverage.put("manualHitEstimate", Math.max(1, (int) (scannedCount * 0.05 * 0.3)));
        coverage.put("ruleFullCoverage", 1.0);
        coverage.put("ruleHitCount", results.size());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", results.size());
        body.put("scannedCount", scannedCount);
        body.put("highCount", highCount);
        body.put("mediumCount", mediumCount);
        body.put("suggestDelistCount", suggestDelistCount);
        body.put("items", results);
        body.put("appliedRules", List.of("R-B01", "R-B02", "R-B03", "R-B04", "R-B05"));
        body.put("ruleVersion", rules.getOrDefault("ruleVersion", "RiskRules-v1.2"));
        body.put("riskRules", rules);
        body.put("coverageCompare", coverage);
        body.put("auditedAt", Instant.now().toString());
        return body;
    }

    private List<Map<String, Object>> defaultCampusPackages() {
        List<Map<String, Object>> pkgs = new ArrayList<>();
        pkgs.add(pkg(
                "校园青春59", 59, "20GB", "200分钟", "校园", "电渠+厅店", "校园体验", "main_pkg",
                "1", 12, null, null,
                "套餐A：校园青春59元；含20GB+200分钟；目标校园；电渠+厅店"));
        Map<String, Object> b = pkg(
                "校园体验0元流量包", 0, "5GB", null, "校园", "全渠道", "校园体验", "addon",
                "0", null, "true", 100,
                "套餐B：校园体验0元流量包；无合约；可重复订购");
        pkgs.add(b);
        Map<String, Object> c = pkg(
                "校园融合加装包", null, null, null, "校园", "电渠+厅店", "校园体验", "addon",
                null, null, null, null,
                "套餐C：校园融合加装包；依赖宽带；未写月费");
        c.put("dependOn", "");
        pkgs.add(c);
        return pkgs;
    }

    private Map<String, Object> pkg(String name, Object fee, String data, String voice,
                                    String target, String channel, String scenario, String type,
                                    String contract, Integer months, String repeatable, Integer discount,
                                    String excerpt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offeringName", name);
        if (fee != null) {
            m.put("monthlyFee", fee);
        }
        if (data != null) {
            m.put("includeData", data);
        }
        if (voice != null) {
            m.put("includeVoice", voice);
        }
        m.put("targetUser", target);
        m.put("channelScope", channel);
        m.put("bizScenario", scenario);
        m.put("offeringType", type);
        if (contract != null) {
            m.put("hasContract", contract);
        }
        if (months != null) {
            m.put("contractMonths", months);
        }
        if (repeatable != null) {
            m.put("repeatable", repeatable);
        }
        if (discount != null) {
            m.put("discountPercent", discount);
        }
        m.put("sourceExcerpt", excerpt);
        return m;
    }

    private List<Map<String, Object>> extractPackagesMock(String documentText) {
        if (documentText.contains("校园青春") || documentText.contains("套餐A") || documentText.contains("0元")) {
            return defaultCampusPackages();
        }
        return List.of();
    }

    private Map<String, Object> riskFeature(String ruleId, String feature, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("feature", feature);
        row.put("message", message);
        return row;
    }

    private Map<String, Object> riskRules() {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> base = new LinkedHashMap<>(castMap(graph.get("riskRuleDefaults")));
        base.putAll(riskRuleOverrides);
        return base;
    }

    private static Map<String, String> metaClass(String code, String name) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("classCode", code);
        row.put("className", name);
        return row;
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> expandShelfOfferings(List<Map<String, Object>> base,
                                                           Map<String, Object> plan) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> o : base) {
            byId.put(str(o.get("offeringId")), deepCopy(o));
        }
        int total = (int) num(plan.get("total"), 80);
        int needZero = (int) num(plan.get("zeroFee"), 8);
        int needDisc = (int) num(plan.get("abnormalDiscount"), 5);
        int needLow = (int) num(plan.get("lowEff"), 7);

        for (int i = 1; i <= needZero; i++) {
            String oid = String.format("OF-RISK-%03d", i);
            if (byId.containsKey(oid)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offeringId", oid);
            row.put("offeringName", "体验测试流量包0元-" + String.format("%02d", i));
            row.put("state", "上架");
            row.put("monthlyFee", 0);
            row.put("oneTimeFee", 0);
            row.put("mutexGroup", "ADDON");
            row.put("offeringType", "addon");
            row.put("shelfDays", 35 + i * 3);
            row.put("salesCnt30d", 10 + i);
            row.put("revenue30d", 0);
            row.put("hasContract", false);
            row.put("strategicTag", false);
            row.put("category", "zero_fee");
            row.put("nameHint", "体验");
            byId.put(oid, row);
        }

        for (int i = 1; i <= needDisc; i++) {
            String oid = String.format("OF-DISC-%03d", i);
            if (byId.containsKey(oid)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offeringId", oid);
            row.put("offeringName", "全额赠送可重复包-" + String.format("%02d", i));
            row.put("state", "上架");
            row.put("monthlyFee", 19);
            row.put("oneTimeFee", 0);
            row.put("discountPercent", 100);
            row.put("repeatable", true);
            row.put("targetCustomerGroup", "");
            row.put("mutexGroup", "ADDON");
            row.put("offeringType", "addon");
            row.put("shelfDays", 30 + i * 2);
            row.put("salesCnt30d", 20 + i);
            row.put("revenue30d", 50);
            row.put("hasContract", false);
            row.put("strategicTag", false);
            row.put("category", "abnormal_discount");
            byId.put(oid, row);
        }

        long lowExisting = byId.values().stream().filter(o -> "low_eff".equals(o.get("category"))).count();
        int lowSeq = 1;
        while (lowExisting < needLow) {
            String oid = String.format("OF-LOW-%03d", lowSeq++);
            if (byId.containsKey(oid)) {
                continue;
            }
            int idx = (int) lowExisting + 1;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offeringId", oid);
            row.put("offeringName", "旧版加装包-长期零销-" + String.format("%02d", idx));
            row.put("state", "上架");
            row.put("monthlyFee", 5 + idx);
            row.put("oneTimeFee", 0);
            row.put("mutexGroup", "ADDON");
            row.put("offeringType", "addon");
            row.put("shelfDays", 190 + idx * 14);
            row.put("salesCnt30d", 0);
            row.put("revenue30d", 0);
            row.put("hasContract", false);
            row.put("strategicTag", false);
            row.put("category", "low_eff");
            byId.put(oid, row);
            lowExisting++;
        }

        for (int i = 1; i <= 3; i++) {
            String oid = String.format("OF-LOW-T%02d", i);
            if (byId.containsKey(oid)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offeringId", oid);
            row.put("offeringName", "旧版加装包-阈值演示-" + String.format("%02d", i));
            row.put("state", "上架");
            row.put("monthlyFee", 6 + i);
            row.put("oneTimeFee", 0);
            row.put("mutexGroup", "ADDON");
            row.put("offeringType", "addon");
            row.put("shelfDays", 100 + i * 12);
            row.put("salesCnt30d", 0);
            row.put("revenue30d", 2 + i);
            row.put("hasContract", false);
            row.put("strategicTag", false);
            row.put("category", "threshold_demo");
            byId.put(oid, row);
        }

        if (byId.containsKey("OF-RISK-001")) {
            byId.get("OF-RISK-001").put("offeringName", "校园体验流量包0元");
        }
        if (byId.containsKey("OF-LOW-019")) {
            Map<String, Object> low = byId.get("OF-LOW-019");
            low.put("offeringName", "旧版彩铃包-2019");
            low.put("shelfDays", 287);
            low.put("salesCnt30d", 0);
            low.put("revenue30d", 0);
            low.put("category", "low_eff");
        }

        int seed = 1;
        while (byId.size() < total) {
            String oid = String.format("OF-N-%03d", seed);
            if (!byId.containsKey(oid)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("offeringId", oid);
                row.put("offeringName", "标准套餐-" + String.format("%03d", seed));
                row.put("state", "上架");
                row.put("monthlyFee", 39 + (seed % 20) * 5);
                row.put("oneTimeFee", 0);
                row.put("mutexGroup", seed % 3 == 0 ? "ADDON" : "MAIN_PKG");
                row.put("offeringType", seed % 3 == 0 ? "addon" : "main_pkg");
                row.put("shelfDays", 60 + seed);
                row.put("salesCnt30d", 80 + seed * 3);
                row.put("revenue30d", 5000 + seed * 120);
                row.put("hasContract", true);
                row.put("strategicTag", seed % 7 == 0);
                row.put("category", "normal");
                byId.put(oid, row);
            }
            seed++;
        }

        List<String> priority = List.of("OF-HF-128", "OF-RISK-001", "OF-LOW-019", "OF-GIFT-WL", "OF-DISC-001");
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String pid : priority) {
            Map<String, Object> item = byId.remove(pid);
            if (item != null) {
                ordered.add(item);
            }
        }
        ordered.addAll(byId.values().stream()
                .sorted(Comparator.comparing(o -> str(o.get("offeringId"))))
                .collect(Collectors.toList()));
        return ordered;
    }

    private Map<String, Object> issue(String ruleId, String issueType, String level, String field,
                                      String message, List<String> evidence, List<Map<String, Object>> triples) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("issueType", issueType);
        row.put("issueLevel", level);
        row.put("field", field);
        row.put("message", message);
        row.put("evidence", evidence);
        if (triples != null) {
            row.put("triples", triples);
        }
        return row;
    }

    private Map<String, Object> triple(Object s, Object p, Object o) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("s", s);
        t.put("p", p);
        t.put("o", o);
        return t;
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        return objectMapper.convertValue(source, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListOfMaps(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Object> castList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
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

    private double num(Object value, double defaultValue) {
        if (value == null || "".equals(value)) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        String text = str(value).replace("元", "").replace("/月", "").trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            Matcher m = NUM_PATTERN.matcher(text);
            if (m.find()) {
                return Double.parseDouble(m.group());
            }
            return defaultValue;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object firstNonEmpty(Object a, Object b) {
        return empty(a) ? b : a;
    }
}
