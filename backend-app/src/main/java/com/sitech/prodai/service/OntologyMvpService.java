package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.ops.OpsExtractionService;
import com.sitech.prodai.service.ops.OpsProductGraphLoader;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
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
 * <p>规则阈值与启用开关统一读 {@link OpsRulesService}（ops_rules.json）。
 * 事实数据统一来自 {@link OpsProductGraphLoader}；演示与生产仅差 graph-path / data-source 配置。
 */
@Service
public class OntologyMvpService {

    private static final Pattern NUM_PATTERN = Pattern.compile("[\\d.]+");

    private static final List<Map<String, String>> ONTOLOGY_CLASSES = List.of(
            metaClass("OfferingConfig", "商品配置草稿"),
            metaClass("ConfigScheme", "历史配置方案"),
            metaClass("ProductConfigTemplate", "产品配置模板"),
            metaClass("ConfigItem", "配置项"),
            metaClass("ParamConstraint", "参数约束"),
            metaClass("TariffElement", "资费要素"),
            metaClass("ComplianceRule", "合规规则"),
            metaClass("BusinessScene", "业务场景"),
            metaClass("ProductElement", "产品要素属性"),
            metaClass("ConfigRule", "配置规则"),
            metaClass("TargetUser", "目标用户"),
            metaClass("BizScenario", "业务场景(兼容)"),
            metaClass("MarketPolicy", "营销政策"),
            metaClass("PricePlan", "商品定价"),
            metaClass("GoodsRelation", "商品关系"),
            metaClass("ConfigTemplate", "配置模板(兼容)"),
            metaClass("ComplianceIssue", "合规问题")
    );

    private final ObjectMapper objectMapper;
    private final ProdAiProperties properties;
    private final OpsSwrlReasoner opsSwrlReasoner;
    private final OpsRulesService opsRules;
    private final OpsProductGraphLoader graphLoader;
    private final OpsExtractionService extractionService;
    private final ConfigDocumentParser documentParser;
    private final Rdf4jOntologyStore rdf4jStore;

    private Map<String, Object> graphCache;
    private String graphSourceId = "empty";
    private final Map<String, Object> riskRuleOverrides = new ConcurrentHashMap<>();
    /** 配置场景审计链路（内存）；对齐方案 get_trace / explain。 */
    private final Map<String, List<Map<String, Object>>> configTraces = new ConcurrentHashMap<>();

    public OntologyMvpService(ObjectMapper objectMapper,
                              ProdAiProperties properties,
                              OpsSwrlReasoner opsSwrlReasoner,
                              OpsRulesService opsRules,
                              OpsProductGraphLoader graphLoader,
                              OpsExtractionService extractionService,
                              ConfigDocumentParser documentParser,
                              Rdf4jOntologyStore rdf4jStore) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.opsSwrlReasoner = opsSwrlReasoner;
        this.opsRules = opsRules;
        this.graphLoader = graphLoader;
        this.extractionService = extractionService;
        this.documentParser = documentParser;
        this.rdf4jStore = rdf4jStore;
    }

    @PostConstruct
    public void init() {
        loadGraph();
        opsRules.load();
    }

    /** 在响应中标注是否演示模式及数据来源，便于前后端识别假数据边界。 */
    public Map<String, Object> withModeMeta(Map<String, Object> body) {
        if (body == null) {
            body = new LinkedHashMap<>();
        }
        body.put("demoMode", properties.getOntology().isDemoEnabled());
        body.put("dataSource", graphSourceId);
        body.put("dataSourceMode", properties.getOntology().getDataSource());
        return body;
    }

    public boolean isDemoEnabled() {
        return properties.getOntology().isDemoEnabled();
    }

    /** 清除事实图缓存，下次请求重新加载（HTTP/文件热更新）。 */
    public synchronized void reloadGraph() {
        graphCache = null;
        loadGraph();
    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> loadGraph() {
        if (graphCache != null) {
            return graphCache;
        }
        OpsProductGraphLoader.LoadedGraph loaded = graphLoader.load();
        Map<String, Object> raw = new LinkedHashMap<>(loaded.graph());
        graphSourceId = loaded.sourceId();
        // 事实图原样使用；演示扩容写在 mock_graph.json，不在代码造数
        raw.put("shelfOfferings", castListOfMaps(raw.get("shelfOfferings")));
        graphCache = raw;
        return graphCache;
    }

    public Map<String, Object> loadOpsRules() {
        return opsRules.load();
    }

    /** 外置规则全集（只读视图） */
    public Map<String, Object> getOpsRulesCatalog() {
        return opsRules.catalogView();
    }

    public Map<String, Object> getGraphSummary() {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> rules = riskRules();
        List<Map<String, Object>> offerings = castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> opsGraph = castMap(graph.get("opsGraph"));
        int anomalyCount = opsGraph == null ? 0 : opsGraph.size();

        Set<String> previewCats = Set.of("zero_fee", "low_eff", "abnormal_discount", "whitelist", "threshold_demo");
        Set<String> previewIds = new LinkedHashSet<>(opsRules.previewOfferingIds());
        int previewLimit = opsRules.previewLimit(20);
        List<Map<String, Object>> shelfPreview = offerings.stream()
                .filter(o -> {
                    String category = str(o.get("category"));
                    if (previewCats.contains(category)) {
                        return true;
                    }
                    return previewIds.contains(str(o.get("offeringId")));
                })
                .limit(previewLimit)
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
        if (shelfPreview.isEmpty()) {
            shelfPreview = offerings.stream().limit(20).map(o -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("offeringId", o.get("offeringId"));
                row.put("offeringName", o.get("offeringName"));
                row.put("state", o.get("state"));
                row.put("monthlyFee", o.get("monthlyFee"));
                row.put("category", o.get("category"));
                return row;
            }).collect(Collectors.toList());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("scenarios", new ArrayList<>(castMap(graph.get("bizScenarios")).keySet()));
        body.put("templates", new ArrayList<>(castMap(graph.get("templates")).keySet()));
        body.put("shelfCount", offerings.size());
        body.put("anomalyOfferingCount", anomalyCount);
        body.put("ruleVersion", rules.getOrDefault("ruleVersion", "RiskRules-v1.2"));
        body.put("riskRules", rules);
        body.put("opsRulesVersion", opsRules.version());
        body.put("shelfOfferings", shelfPreview);
        body.put("classes", ONTOLOGY_CLASSES);
        body.put("relations", List.of(
                "hasElement", "hasPricePlan", "constrainedBy", "forTargetUser",
                "inScenario", "appliesPolicy", "basedOnTemplate", "hasRelation",
                "hasIssue", "blocksCombination", "suggestsDefault", "definesElement"
        ));
        body.put("ruleSets", Map.of(
                "config", opsRules.ruleIds("config"),
                "batch", opsRules.ruleIds("batch"),
                "opsRootCause", opsRules.ruleIds("rootCause"),
                "opsRisk", opsRules.ruleIds("risk")
        ));
        body.put("engines", castMap(opsRules.load().get("engines")));
        return withModeMeta(body);
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
        body.put("opsRules", getOpsRulesCatalog());
        return withModeMeta(body);
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

        if ("家庭融合".equals(scenario) && opsRules.isConfigEnabled("R-C01")) {
            for (Map.Entry<String, Object> e : defaults.entrySet()) {
                if (empty(result.get(e.getKey()))) {
                    result.put(e.getKey(), e.getValue());
                    fillSources.put(e.getKey(), "scenario_default");
                    appliedRules.add("R-C01");
                }
            }
            if (empty(result.get("includeBroadband"))) {
                result.put("includeBroadband",
                        defaults.getOrDefault("includeBroadband",
                                opsRules.configDefaultStr("includeBroadband", "500M")));
                fillSources.put("includeBroadband", "scenario_default");
                appliedRules.add("R-C01");
            }
        }

        boolean isCampus = "校园".equals(str(result.get("targetUser"))) || "校园体验".equals(scenario);
        String offeringType = str(firstNonEmpty(result.get("offeringType"), safeSlots.get("offeringType")));
        if (opsRules.isConfigEnabled("R-C02") && isCampus && empty(result.get("monthlyFee")) && !"addon".equals(offeringType)) {
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
            result.put("channelScope", opsRules.configDefaultStr("channelScope", "全渠道"));
            fillSources.put("channelScope", "scenario_default");
        }
        if (empty(result.get("mutexGroup"))) {
            result.put("mutexGroup", defaults.getOrDefault("mutexGroup",
                    opsRules.configDefaultStr("mutexGroup", "MAIN_PKG")));
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

        if (opsRules.isConfigEnabled("R-C06")) {
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
        }

        String mutexGroup = str(firstNonEmpty(draft.get("mutexGroup"), "MAIN_PKG"));
        Object bindId = draft.get("bindExistingMainPkg");
        Map<String, Map<String, Object>> shelf = castListOfMaps(graph.get("shelfOfferings")).stream()
                .collect(Collectors.toMap(o -> str(o.get("offeringId")), o -> o, (a, b) -> a, LinkedHashMap::new));
        if (opsRules.isConfigEnabled("R-C03") && !empty(bindId) && shelf.containsKey(str(bindId))) {
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

        if (opsRules.isConfigEnabled("R-C04")
                && "addon".equals(str(draft.get("offeringType"))) && empty(draft.get("dependOn"))) {
            issues.add(issue("R-C04", "规则漏洞", "HIGH", "dependOn",
                    "附加包缺少依赖的主服务/宽带",
                    List.of("offeringType=addon", "dependOn=empty"), null));
        }

        double monthly = num(draft.get("monthlyFee"), -1);
        double oneTime = num(draft.get("oneTimeFee"), 0);
        String scenario = str(draft.get("bizScenario"));
        List<Object> whitelist = castList(graph.get("equityGiftWhitelist"));
        if (opsRules.isConfigEnabled("R-C05")
                && monthly == 0 && oneTime == 0 && !truthy(draft.get("hasContract"))) {
            if (!whitelist.contains(scenario) && !"内部验证".equals(str(draft.get("channelScope")))) {
                issues.add(issue("R-C05", "高风险资费", "HIGH", "monthlyFee",
                        "月费/一次性费均为0且无合约，非权益赠送白名单",
                        List.of("monthlyFee=0", "oneTimeFee=0", "hasContract=0"), null));
            }
        }

        double discount = num(draft.get("discountPercent"), -1);
        if (opsRules.isConfigEnabled("R-C07")
                && discount == 100 && truthy(draft.get("repeatable"))) {
            issues.add(issue("R-C07", "异常优惠漏洞", "HIGH", "discountPercent",
                    "优惠折扣100%且可重复订购，存在异常优惠漏洞",
                    List.of("discountPercent=100", "repeatable=true"), null));
        }

        // R-C09 ≈ 方案 R-CONF-001：资费上下限
        if (opsRules.isConfigEnabled("R-C09") && monthly >= 0) {
            double minFee = opsRules.configDefaultNum("monthlyFeeMin", 9);
            double maxFee = opsRules.configDefaultNum("monthlyFeeMax", 599);
            if (monthly < minFee || monthly > maxFee) {
                issues.add(issue("R-C09", "资费区间违规", "HIGH", "monthlyFee",
                        "月费取值超出合规范围，允许区间为" + (int) minFee + "-" + (int) maxFee + "元",
                        List.of("monthlyFee=" + monthly, "min=" + minFee, "max=" + maxFee), null));
            }
        }

        // R-C03 扩展 ≈ 方案 R-CONF-002：折扣与赠费并存需复核
        double freeFee = num(firstNonEmpty(draft.get("freeFeeAmount"), draft.get("giftFee")), -1);
        if (opsRules.isConfigEnabled("R-C03") && discount > 0 && freeFee > 0) {
            issues.add(issue("R-C03", "资费冲突", "MEDIUM", "discountPercent",
                    "同时配置了折扣与赠费，存在资费冲突风险，需人工复核（方案别名 R-CONF-002）",
                    List.of("discountPercent=" + discount, "freeFeeAmount=" + freeFee), null));
        }

        boolean hasHigh = issues.stream().anyMatch(i -> "HIGH".equals(i.get("issueLevel")));
        boolean requiredOk = issues.stream().noneMatch(i -> "R-C06".equals(i.get("ruleId")));
        boolean compliancePass = !hasHigh && requiredOk;

        List<String> applied = compliancePass
                ? (opsRules.isConfigEnabled("R-C08") ? List.of("R-C08") : List.of())
                : issues.stream().map(i -> str(i.get("ruleId"))).distinct().sorted().collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("issues", issues);
        body.put("compliancePass", compliancePass);
        body.put("appliedRules", applied);
        body.put("canSubmit", compliancePass);
        return body;
    }

    /**
     * 按套餐信息做合规校验，支持已入库（在架）与未入库（草稿）两类来源。
     * <ul>
     *   <li>文案含「当前配置/当前草稿/未入库」且草稿有实质字段 → 校验未入库草稿</li>
     *   <li>能解析到在架编码/名称 → 校验已入库套餐</li>
     *   <li>否则若草稿有实质字段 → 校验未入库草稿</li>
     * </ul>
     */
    public Map<String, Object> checkComplianceSmart(String offeringId, String text, Map<String, Object> draftInput) {
        Map<String, Object> draft = draftInput == null ? Map.of() : draftInput;
        String q = text == null ? "" : text.trim();
        boolean preferDraft = q.matches("(?s).*(当前配置|当前草稿|未入库|校验当前).*")
                || "校验当前配置是否符合在架规则".equals(q);
        boolean forceShelf = q.matches("(?s).*(已入库|在架商品|在架套餐).*");

        String oid = resolveOfferingId(offeringId, q);
        Map<String, Object> targetDraft;
        String source;
        String sourceLabel;
        String resolvedId = null;
        String resolvedName = null;

        if (!forceShelf && preferDraft && hasDraftContent(draft)) {
            targetDraft = new LinkedHashMap<>(draft);
            source = "draft";
            sourceLabel = "未入库草稿";
            resolvedName = str(firstNonEmpty(draft.get("offeringName"), "当前草稿"));
        } else if (oid != null) {
            Map<String, Object> shelf = findShelfOffering(oid);
            if (shelf == null) {
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("success", false);
                fail.put("message", "已解析到编码 " + oid + "，但图谱中无对应在架套餐");
                fail.put("offeringId", oid);
                fail.put("query", q);
                return fail;
            }
            targetDraft = shelfOfferingToDraft(shelf);
            source = "shelf";
            sourceLabel = "已入库（在架）";
            resolvedId = oid;
            resolvedName = str(shelf.get("offeringName"));
        } else if (hasDraftContent(draft)) {
            targetDraft = new LinkedHashMap<>(draft);
            source = "draft";
            sourceLabel = "未入库草稿";
            resolvedName = str(firstNonEmpty(draft.get("offeringName"), "当前草稿"));
        } else {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", empty(q)
                    ? "请提供套餐名称/编码，或先通过智聊/智读生成未入库草稿后再校验"
                    : "未能解析套餐，也未找到可校验的未入库草稿。可试：「校验校园体验流量包0元是否符合在架规则」，或先配置后再说「校验当前配置」");
            fail.put("query", q);
            fail.put("hintExamples", List.of(
                    "校验校园体验流量包0元是否符合在架规则",
                    "校验家庭融合畅享128是否符合在架规则",
                    "校验当前配置是否符合在架规则"
            ));
            return fail;
        }

        Map<String, Object> compliance = checkCompliance(targetDraft);
        Map<String, Object> body = new LinkedHashMap<>(compliance);
        body.put("success", true);
        body.put("source", source);
        body.put("sourceLabel", sourceLabel);
        body.put("offeringId", resolvedId);
        body.put("offeringName", resolvedName);
        body.put("draft", targetDraft);
        body.put("query", q.isEmpty() ? null : q);
        body.put("intent", "compliance_check");
        return body;
    }

    private boolean hasDraftContent(Map<String, Object> draft) {
        if (draft == null || draft.isEmpty()) {
            return false;
        }
        return !empty(draft.get("offeringName"))
                || !empty(draft.get("monthlyFee"))
                || !empty(draft.get("bizScenario"))
                || !empty(draft.get("targetUser"))
                || !empty(draft.get("channelScope"))
                || !empty(draft.get("offeringType"))
                || !empty(draft.get("includeBroadband"))
                || !empty(draft.get("bindExistingMainPkg"));
    }

    private Map<String, Object> findShelfOffering(String offeringId) {
        if (empty(offeringId)) {
            return null;
        }
        return castListOfMaps(loadGraph().get("shelfOfferings")).stream()
                .filter(o -> offeringId.equals(str(o.get("offeringId"))))
                .findFirst()
                .orElse(null);
    }

    /** 将已入库在架套餐映射为合规校验所需的配置字段。 */
    private Map<String, Object> shelfOfferingToDraft(Map<String, Object> shelf) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringId", shelf.get("offeringId"));
        draft.put("offeringName", shelf.get("offeringName"));
        draft.put("offeringType", firstNonEmpty(shelf.get("offeringType"), "main_pkg"));
        draft.put("monthlyFee", shelf.get("monthlyFee"));
        draft.put("oneTimeFee", firstNonEmpty(shelf.get("oneTimeFee"), 0));
        draft.put("mutexGroup", firstNonEmpty(shelf.get("mutexGroup"), "MAIN_PKG"));
        draft.put("hasContract", shelf.get("hasContract"));
        draft.put("discountPercent", shelf.get("discountPercent"));
        draft.put("repeatable", shelf.get("repeatable"));
        if (!empty(shelf.get("dependOn"))) {
            draft.put("dependOn", shelf.get("dependOn"));
        }
        if (!empty(shelf.get("bindExistingMainPkg"))) {
            draft.put("bindExistingMainPkg", shelf.get("bindExistingMainPkg"));
        }

        String whitelistTag = str(shelf.get("whitelistTag"));
        String bizScenario = str(firstNonEmpty(shelf.get("bizScenario"), whitelistTag));
        if (bizScenario.isEmpty() && "whitelist".equals(str(shelf.get("category")))) {
            bizScenario = "权益赠送";
        }
        draft.put("bizScenario", bizScenario);

        String targetUser = str(firstNonEmpty(shelf.get("targetUser"), shelf.get("targetCustomerGroup")));
        if (targetUser.isEmpty()) {
            String name = str(shelf.get("offeringName"));
            if (name.contains("家庭")) {
                targetUser = "家庭";
            } else if (name.contains("校园")) {
                targetUser = "校园";
            } else {
                targetUser = "个人";
            }
        }
        draft.put("targetUser", targetUser);
        draft.put("channelScope", firstNonEmpty(shelf.get("channelScope"), "全渠道"));
        draft.put("state", shelf.get("state"));
        draft.put("fillSources", Map.of("_source", "shelf"));
        return draft;
    }

    /**
     * 智查：按关键词检索在架商品与配置模板（对齐 nl_discover_and_retrieve 配置侧）。
     */
    public Map<String, Object> discoverConfigs(String query, int limit) {
        String q = query == null ? "" : query.trim();
        int lim = limit <= 0 ? 20 : Math.min(limit, 50);
        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> offerings = castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> templates = castMap(graph.get("templates"));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> o : offerings) {
            int score = matchScore(q, o);
            if (score <= 0 && !q.isBlank()) {
                continue;
            }
            if (q.isBlank()) {
                score = 1;
            }
            Map<String, Object> row = toQueryCard(o, score);
            items.add(row);
        }
        items.sort((a, b) -> Integer.compare((int) num(b.get("score"), 0), (int) num(a.get("score"), 0)));
        if (items.size() > lim) {
            items = new ArrayList<>(items.subList(0, lim));
        }

        List<Map<String, Object>> tplHits = new ArrayList<>();
        for (Map.Entry<String, Object> e : templates.entrySet()) {
            Map<String, Object> t = castMap(e.getValue());
            String blob = (str(t.get("templateId")) + " " + str(t.get("name"))).toLowerCase(Locale.ROOT);
            if (q.isBlank() || blob.contains(q.toLowerCase(Locale.ROOT))
                    || q.contains("模板") || q.contains("套餐")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("template_id", t.get("templateId"));
                row.put("name", t.get("name"));
                row.put("monthly_fee", t.get("monthlyFee"));
                tplHits.add(row);
            }
        }

        String traceId = "cfg-discover-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "nl_discover_and_retrieve",
                "query", q,
                "hit_count", items.size(),
                "timestamp", Instant.now().toString()
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("query", q);
        body.put("items", items);
        body.put("templates", tplHits.stream().limit(10).collect(Collectors.toList()));
        body.put("trace_id", traceId);
        body.put("total", items.size());
        return body;
    }

    /**
     * 一键复制为新配置草稿 + 合规校验（对齐 retrieve_facts → copy → evaluate_policy）。
     */
    public Map<String, Object> copyAsDraft(String offeringId, String text) {
        String oid = resolveOfferingId(offeringId, text);
        Map<String, Object> shelf = findShelfOffering(oid);
        if (shelf == null) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "未找到可复制的历史方案：" + firstNonEmpty(offeringId, text));
            return fail;
        }

        Map<String, Object> sourceDraft = shelfOfferingToDraft(shelf);
        Map<String, Object> draft = deepCopy(sourceDraft);
        draft.remove("state");
        draft.put("status", "draft");
        draft.put("copiedFrom", shelf.get("offeringId"));
        String baseName = str(firstNonEmpty(draft.get("offeringName"), "配置方案"));
        draft.put("offeringName", baseName + " (副本)");
        draft.put("offeringId", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> fill = draft.get("fillSources") instanceof Map<?, ?>
                ? new LinkedHashMap<>((Map<String, Object>) draft.get("fillSources"))
                : new LinkedHashMap<>();
        fill.put("_source", "copy_as_draft");
        fill.put("copiedFrom", shelf.get("offeringId"));
        draft.put("fillSources", fill);

        // 关联模板
        Map<String, Object> infer = inferFields(Map.of(
                "bizScenario", draft.get("bizScenario"),
                "targetUser", draft.get("targetUser"),
                "offeringType", draft.get("offeringType")
        ), draft);
        @SuppressWarnings("unchecked")
        Map<String, Object> inferredDraft = (Map<String, Object>) infer.get("draft");
        if (inferredDraft != null) {
            draft = inferredDraft;
            draft.put("status", "draft");
            draft.put("copiedFrom", shelf.get("offeringId"));
            if (!str(draft.get("offeringName")).contains("副本")) {
                draft.put("offeringName", baseName + " (副本)");
            }
        }

        Map<String, Object> compliance = checkCompliance(draft);
        List<Map<String, Object>> diffs = compareDraftFields(sourceDraft, draft);

        String traceId = "cfg-copy-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "retrieve_facts",
                "offering_id", shelf.get("offeringId"),
                "timestamp", Instant.now().toString()
        ));
        appendConfigAudit(traceId, Map.of(
                "step", "copy_as_draft",
                "copied_from", shelf.get("offeringId"),
                "timestamp", Instant.now().toString()
        ));
        appendConfigAudit(traceId, Map.of(
                "step", "evaluate_policy",
                "compliance_pass", compliance.get("compliancePass"),
                "applied_rules", compliance.get("appliedRules"),
                "timestamp", Instant.now().toString()
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("source", "shelf");
        body.put("source_offering_id", shelf.get("offeringId"));
        body.put("source_offering_name", shelf.get("offeringName"));
        body.put("draft", draft);
        body.put("diffs", diffs);
        body.put("issues", compliance.get("issues"));
        body.put("compliancePass", compliance.get("compliancePass"));
        body.put("appliedRules", compliance.get("appliedRules"));
        body.put("canSubmit", compliance.get("canSubmit"));
        body.put("trace_id", traceId);
        return body;
    }

    /** 智读：先解析文档再批量映射。 */
    public Map<String, Object> batchFromDocumentBytes(byte[] bytes, String fileName) {
        ConfigDocumentParser.ParseResult parsed = documentParser.parse(bytes, fileName);
        if (!parsed.success()) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", parsed.message());
            fail.put("parseEngine", parsed.engine());
            return fail;
        }
        Map<String, Object> body = batchFromDocument(parsed.text(), null);
        body.put("parseEngine", parsed.engine());
        body.put("fileName", fileName);
        body.put("extractedChars", parsed.text() == null ? 0 : parsed.text().length());
        String traceId = "cfg-batch-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "document_parse",
                "file_name", fileName,
                "engine", parsed.engine(),
                "timestamp", Instant.now().toString()
        ));
        appendConfigAudit(traceId, Map.of(
                "step", "evaluate_policy_with_facts",
                "total", body.get("total"),
                "passed", body.get("passedCount"),
                "timestamp", Instant.now().toString()
        ));
        body.put("trace_id", traceId);
        return body;
    }

    /**
     * 知识自迭代：合规通过的草稿沉淀至事实图 + RDF ConfigScheme。
     */
    public synchronized Map<String, Object> publishConfigDraft(Map<String, Object> draftInput) {
        Map<String, Object> draft = draftInput == null ? Map.of() : deepCopy(draftInput);
        Map<String, Object> compliance = checkCompliance(draft);
        if (!Boolean.TRUE.equals(compliance.get("compliancePass"))) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "合规未通过，拒绝沉淀至本体");
            fail.put("issues", compliance.get("issues"));
            fail.put("compliancePass", false);
            return fail;
        }

        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> shelf = castListOfMaps(graph.get("shelfOfferings"));
        String newId = str(draft.get("offeringId"));
        if (newId.isBlank()) {
            newId = "OF-DRAFT-" + Instant.now().toEpochMilli();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("offeringId", newId);
        row.put("offeringName", firstNonEmpty(draft.get("offeringName"), newId));
        row.put("state", "上架");
        row.put("monthlyFee", draft.get("monthlyFee"));
        row.put("oneTimeFee", firstNonEmpty(draft.get("oneTimeFee"), 0));
        row.put("mutexGroup", firstNonEmpty(draft.get("mutexGroup"), "MAIN_PKG"));
        row.put("offeringType", firstNonEmpty(draft.get("offeringType"), "main_pkg"));
        row.put("shelfDays", 0);
        row.put("salesCnt30d", 0);
        row.put("revenue30d", 0);
        row.put("hasContract", draft.get("hasContract"));
        row.put("strategicTag", false);
        row.put("category", "normal");
        row.put("bizScenario", draft.get("bizScenario"));
        row.put("targetUser", draft.get("targetUser"));
        row.put("channelScope", draft.get("channelScope"));
        row.put("discountPercent", draft.get("discountPercent"));
        row.put("basedOnTemplate", draft.get("basedOnTemplate"));
        row.put("copiedFrom", draft.get("copiedFrom"));
        shelf.add(0, row);
        graph.put("shelfOfferings", shelf);
        graphCache = graph;

        String baseIri = properties.getOntology().normalizedBaseIri() + "config/";
        String uri = baseIri + newId;
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("schemeId", newId);
        facts.put("schemeName", row.get("offeringName"));
        facts.put("status", "已上线");
        facts.put("productType", row.get("offeringType"));
        facts.put("monthlyFee", row.get("monthlyFee"));
        facts.put("basedOnTemplate", draft.get("basedOnTemplate"));
        facts.put("appliesScene", draft.get("bizScenario"));
        Object copiedFrom = draft.get("copiedFrom");
        if (!empty(copiedFrom)) {
            facts.put("similarTo", baseIri + copiedFrom);
        }
        rdf4jStore.addClass("ConfigScheme");
        rdf4jStore.addProperty("basedOnTemplate");
        rdf4jStore.addProperty("similarTo");
        rdf4jStore.addProperty("appliesScene");
        rdf4jStore.addInstance(uri, "ConfigScheme", facts);

        String traceId = "cfg-publish-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "knowledge_iterate",
                "offering_id", newId,
                "uri", uri,
                "timestamp", Instant.now().toString()
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("offeringId", newId);
        body.put("uri", uri);
        body.put("shelfCount", shelf.size());
        body.put("trace_id", traceId);
        body.put("message", "已沉淀至事实图与配置本体 ConfigScheme");
        return body;
    }

    public Map<String, Object> getConfigTrace(String traceId) {
        List<Map<String, Object>> steps = configTraces.getOrDefault(traceId, List.of());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", !steps.isEmpty());
        body.put("trace_id", traceId);
        body.put("steps", steps);
        if (steps.isEmpty()) {
            body.put("message", "trace not found");
        }
        return body;
    }

    public Map<String, Object> explainConfig(String traceId, String audience) {
        List<Map<String, Object>> steps = configTraces.getOrDefault(traceId, List.of());
        String aud = audience == null || audience.isBlank() ? "business" : audience;
        StringBuilder sb = new StringBuilder();
        if ("business".equalsIgnoreCase(aud)) {
            sb.append("配置审计说明（业务视角）：\n");
        } else {
            sb.append("配置审计说明（技术视角）：\n");
        }
        if (steps.isEmpty()) {
            sb.append("未找到 trace=").append(traceId);
        } else {
            for (Map<String, Object> step : steps) {
                sb.append("- ").append(step.getOrDefault("step", "?"));
                if (step.containsKey("compliance_pass")) {
                    sb.append(" → 合规=").append(step.get("compliance_pass"));
                }
                if (step.containsKey("applied_rules")) {
                    sb.append(" 规则=").append(step.get("applied_rules"));
                }
                if (step.containsKey("query")) {
                    sb.append(" 查询=").append(step.get("query"));
                }
                if (step.containsKey("offering_id")) {
                    sb.append(" 商品=").append(step.get("offering_id"));
                }
                sb.append('\n');
            }
            sb.append("\n规则引擎说明：配置侧使用 Java R-C*（方案别名见 proposalMapping），非 Drools；")
                    .append("Openllet SWRL 仅用于运营归因。");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("trace_id", traceId);
        body.put("audience", aud);
        body.put("explanation", sb.toString().trim());
        body.put("proposalMapping", castMap(castMap(opsRules.load().get("config")).get("proposalMapping")));
        return body;
    }

    private int matchScore(String query, Map<String, Object> offering) {
        if (query == null || query.isBlank()) {
            return 1;
        }
        String q = query.toLowerCase(Locale.ROOT);
        String id = str(offering.get("offeringId")).toLowerCase(Locale.ROOT);
        String name = str(offering.get("offeringName")).toLowerCase(Locale.ROOT);
        String cat = str(offering.get("category")).toLowerCase(Locale.ROOT);
        String type = str(offering.get("offeringType")).toLowerCase(Locale.ROOT);
        int score = 0;
        if (id.equals(q) || name.equals(q)) {
            score += 100;
        }
        if (!id.isBlank() && (q.contains(id) || id.contains(q))) {
            score += 40;
        }
        if (!name.isBlank() && (name.contains(q) || q.contains(name))) {
            score += 50;
        }
        for (String token : q.split("[\\s,，、]+")) {
            if (token.length() < 2) {
                continue;
            }
            if (name.contains(token) || id.contains(token)) {
                score += 15;
            }
            if (("校园".equals(token) || "学生".equals(token) || "大学".equals(token))
                    && (name.contains("校园") || name.contains("青春") || name.contains("学生"))) {
                score += 25;
            }
            if (("5g".equals(token) || "套餐".equals(token)) && (name.contains("5g") || name.contains("畅享"))) {
                score += 10;
            }
            if (("家庭".equals(token) || "融合".equals(token)) && (name.contains("家庭") || name.contains("融合"))) {
                score += 20;
            }
            if (token.matches("\\d+") && str(offering.get("monthlyFee")).contains(token)) {
                score += 30;
            }
        }
        if (q.contains("在售") || q.contains("在架") || q.contains("上线")) {
            if ("上架".equals(str(offering.get("state")))) {
                score += 5;
            }
        }
        if (score == 0 && (cat.contains(q) || type.contains(q))) {
            score = 5;
        }
        return score;
    }

    private Map<String, Object> toQueryCard(Map<String, Object> o, int score) {
        Map<String, Object> row = new LinkedHashMap<>();
        String id = str(o.get("offeringId"));
        row.put("id", id);
        row.put("code", id);
        row.put("name", o.get("offeringName"));
        row.put("offeringId", id);
        row.put("offering_id", id);
        row.put("monthlyFee", o.get("monthlyFee"));
        row.put("state", o.get("state"));
        row.put("category", o.get("category"));
        row.put("score", score);
        row.put("desc", "月费" + o.get("monthlyFee") + "元 | " + o.get("state")
                + " | " + firstNonEmpty(o.get("offeringType"), o.get("category")));
        row.put("template", o.get("basedOnTemplate"));
        // 前端复制时优先走后端 copy-as-draft，不再依赖本地 data
        row.put("source", "shelf");
        return row;
    }

    private List<Map<String, Object>> compareDraftFields(Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, Object>> diffs = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String k : keys) {
            if ("fillSources".equals(k)) {
                continue;
            }
            String a = str(before.get(k));
            String b = str(after.get(k));
            if (!a.equals(b)) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("field", k);
                d.put("before", before.get(k));
                d.put("after", after.get(k));
                diffs.add(d);
            }
        }
        return diffs;
    }

    private void appendConfigAudit(String traceId, Map<String, Object> step) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        configTraces.computeIfAbsent(traceId, k -> new ArrayList<>()).add(new LinkedHashMap<>(step));
    }

    public Map<String, Object> chatConfigure(String text, Map<String, Object> draft) {
        OpsExtractionService.SlotExtractResult extracted = extractionService.extractSlots(text == null ? "" : text);
        Map<String, Object> slots = extracted.slots();
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
        body.put("slotEngine", extracted.engine());
        body.put("draft", inferredDraft);
        body.put("inferredFields", infer.get("inferredFields"));
        body.put("recommendedTemplates", infer.get("recommendedTemplates"));
        body.put("issues", compliance.get("issues"));
        body.put("compliancePass", compliance.get("compliancePass"));
        body.put("appliedRules", applied.stream().sorted().collect(Collectors.toList()));
        body.put("canSubmit", compliance.get("canSubmit"));
        String traceId = "cfg-chat-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "chat_configure",
                "text", text == null ? "" : text,
                "timestamp", Instant.now().toString()
        ));
        appendConfigAudit(traceId, Map.of(
                "step", "evaluate_policy",
                "compliance_pass", compliance.get("compliancePass"),
                "applied_rules", compliance.get("appliedRules"),
                "timestamp", Instant.now().toString()
        ));
        body.put("trace_id", traceId);
        return body;
    }

    public Map<String, Object> batchFromDocument(String documentText, List<Map<String, Object>> packages) {
        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> pkgs = packages;
        String extractEngine = "provided";
        if (pkgs == null || pkgs.isEmpty()) {
            pkgs = List.of();
            OpsExtractionService.PackageExtractResult extracted =
                    extractionService.extractPackages(documentText, List.of());
            pkgs = extracted.packages();
            extractEngine = extracted.engine();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (int idx = 0; idx < pkgs.size(); idx++) {
            Map<String, Object> slots = deepCopy(pkgs.get(idx));
            // 业务场景以文档抽取结果为准，不再默认灌入校园体验
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

        String scenarioId = null;
        if (!items.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstDraft = (Map<String, Object>) items.get(0).get("draft");
            String bizScenario = firstDraft == null ? null : str(firstDraft.get("bizScenario"));
            if (bizScenario != null && !bizScenario.isBlank()) {
                Map<String, Object> scenarioMeta = castMap(castMap(graph.get("bizScenarios")).get(bizScenario));
                scenarioId = str(scenarioMeta.get("scenarioId"));
                if (scenarioId == null || scenarioId.isBlank()) {
                    scenarioId = bizScenario;
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", items.size());
        body.put("passedCount", passed.size());
        body.put("pendingCount", items.size() - passed.size());
        body.put("items", items);
        body.put("appliedRules", opsRules.ruleIds("batch").stream()
                .filter(id -> opsRules.isRuleEnabled(opsRules.batchRule(id)))
                .toList());
        body.put("confirmableDrafts", confirmable);
        body.put("scenario", scenarioId);
        body.put("extractEngine", extractEngine);
        return body;
    }

    public Map<String, Object> getOpsDashboard() {
        Map<String, Object> risk = auditRisks(null);
        Map<String, Object> rules = riskRules();
        List<Map<String, Object>> alerts = buildAnomalyAlerts();
        long anomalyCount = alerts.stream().filter(a -> "anomaly".equals(a.get("type"))).count();

        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("id", "alert-risk");
        a2.put("type", "risk");
        a2.put("tag", "风险");
        a2.put("text", "高风险在架商品 " + risk.getOrDefault("highCount", 0) + " 个待处置");
        alerts.add(a2);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("anomalyOfferingCount", anomalyCount);
        body.put("highRiskCount", risk.getOrDefault("highCount", 0));
        body.put("mediumRiskCount", risk.getOrDefault("mediumCount", 0));
        body.put("suggestDelistCount", risk.getOrDefault("suggestDelistCount", 0));
        body.put("shelfCount", risk.getOrDefault("scannedCount", 0));
        body.put("ruleVersion", rules.getOrDefault("ruleVersion", "RiskRules-v1.2"));
        body.put("lastAuditAt", risk.get("auditedAt"));
        body.put("alerts", alerts);
        return withModeMeta(body);
    }

    /** 从 opsGraph 指标事实生成异动告警，无事实则不造假 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildAnomalyAlerts() {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> opsGraph = castMap(graph.get("opsGraph"));
        List<Map<String, Object>> shelf = castListOfMaps(graph.get("shelfOfferings"));
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Map.Entry<String, Object> e : opsGraph.entrySet()) {
            String oid = e.getKey();
            Map<String, Object> node = castMap(e.getValue());
            String name = shelf.stream()
                    .filter(o -> oid.equals(str(o.get("offeringId"))))
                    .map(o -> str(o.get("offeringName")))
                    .filter(s -> !s.isBlank())
                    .findFirst()
                    .orElse(oid);
            for (Map<String, Object> m : castListOfMaps(node.get("metrics"))) {
                if (!isMetricAnomaly(m)) continue;
                String metric = str(m.get("metricCode"));
                String detail;
                Object deltaObj = m.get("metricDelta");
                if (deltaObj != null) {
                    detail = metric + "环比 " + Math.round(num(deltaObj, 0) * 100) + "%";
                } else {
                    detail = metric + "异动 " + m.getOrDefault("metricDeltaPp", "") + "pp";
                }
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("id", "alert-" + oid + "-" + metric);
                alert.put("type", "anomaly");
                alert.put("tag", "异动");
                alert.put("offeringId", oid);
                alert.put("offeringName", name);
                alert.put("text", oid + " " + detail);
                alert.put("actionText", "分析" + name + "本月收入下滑原因");
                alerts.add(alert);
                break;
            }
        }
        return alerts;
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

    /** @deprecated 使用 {@link OpsExtractionService#extractSlots(String)}；保留兼容调用。 */
    @Deprecated
    public Map<String, Object> parseSlotsFromText(String text) {
        return extractionService.extractSlots(text == null ? "" : text).slots();
    }

    /**
     * 按编码或自然语言解析产商品。优先精确编码，再按在架名称/别名匹配。
     * @return offeringId，无法解析时返回 null
     */
    public String resolveOfferingId(String offeringId, String text) {
        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> shelf = castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> opsGraph = castMap(graph.get("opsGraph"));

        if (!empty(offeringId)) {
            String oid = offeringId.trim();
            boolean onShelf = shelf.stream().anyMatch(o -> oid.equals(str(o.get("offeringId"))));
            boolean inOps = opsGraph.containsKey(oid);
            if (onShelf || inOps) {
                return oid;
            }
        }

        String q = text == null ? "" : text.trim();
        if (q.isEmpty()) {
            return null;
        }

        // 文本中直接出现编码
        for (Map<String, Object> o : shelf) {
            String oid = str(o.get("offeringId"));
            if (!oid.isEmpty() && q.contains(oid)) {
                return oid;
            }
        }
        for (String oid : opsGraph.keySet()) {
            if (q.contains(oid)) {
                return oid;
            }
        }

        // 按名称最长匹配，避免短词误伤
        String bestId = null;
        int bestLen = 0;
        for (Map<String, Object> o : shelf) {
            String name = str(o.get("offeringName"));
            if (name.length() >= 2 && q.contains(name) && name.length() > bestLen) {
                bestId = str(o.get("offeringId"));
                bestLen = name.length();
            }
        }
        if (bestId != null) {
            return bestId;
        }

        // 别名：来自 ops_rules.extraction.aliases，仅当图中存在该 ID 时生效
        String aliasId = opsRules.resolveAliasOfferingId(q);
        if (aliasId != null
                && (opsGraph.containsKey(aliasId)
                || shelf.stream().anyMatch(o -> aliasId.equals(o.get("offeringId"))))) {
            return aliasId;
        }
        return null;
    }

    public Map<String, Object> analyzeRootCause(String offeringId) {
        return analyzeRootCause(offeringId, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeRootCause(String offeringId, String text) {
        String oid = resolveOfferingId(offeringId, text);
        if (oid == null) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", empty(offeringId) && empty(text)
                    ? "请提供产商品编码或名称"
                    : "无法从请求解析产商品，请使用在架编码或完整商品名称");
            fail.put("offeringId", offeringId);
            fail.put("query", text);
            return fail;
        }

        Map<String, Object> graph = loadGraph();
        Map<String, Object> node = castMap(castMap(graph.get("opsGraph")).get(oid));
        Map<String, Object> offering = castListOfMaps(graph.get("shelfOfferings")).stream()
                .filter(o -> oid.equals(str(o.get("offeringId"))))
                .findFirst()
                .orElse(null);
        if (node.isEmpty() || offering == null) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "未找到商品异动图谱节点 " + oid + "，当前仅对 opsGraph 中有事实的商品做归因");
            fail.put("offeringId", oid);
            fail.put("offeringName", offering == null ? null : offering.get("offeringName"));
            return fail;
        }

        List<Map<String, Object>> anomalies = new ArrayList<>();
        List<Map<String, Object>> candidates = new ArrayList<>();
        List<Map<String, Object>> triples = new ArrayList<>();
        String reasonEngine = "java-rules";
        List<String> swrlFired = new ArrayList<>();

        Map<String, Object> a01 = opsRules.rootCauseRule("R-A01");
        Map<String, Object> a02 = opsRules.rootCauseRule("R-A02");

        // R-A01 / R-A02：按 ops_rules.engine 优先 Openllet；否则或失败时回退 Java
        boolean trySwrl = opsRules.preferSwrl("R-A01") || opsRules.preferSwrl("R-A02");
        OpsSwrlReasoner.SwrlFireResult swrl = trySwrl
                ? opsSwrlReasoner.reasonRootCauseA01A02(
                oid,
                str(offering.get("offeringName")),
                node,
                a01,
                a02
        )
                : new OpsSwrlReasoner.SwrlFireResult(
                false, "java-rules", false, List.of(), List.of(), List.of(), "规则配置为 java，跳过 SWRL");
        if (swrl.success() && "openllet-swrl".equals(swrl.engine())) {
            reasonEngine = "openllet-swrl";
            swrlFired.addAll(swrl.firedRules());
            anomalies.addAll(swrl.anomalies());
            for (Map<String, Object> chCand : swrl.channelCandidates()) {
                Map<String, Object> c = new LinkedHashMap<>(chCand);
                double orderDelta = num(c.get("orderDelta"), 0);
                double contrib = num(c.get("contribRatio"), 0);
                c.put("path", List.of(
                        oid + "-hasMetric->" + (anomalies.isEmpty() ? "异动指标" : anomalies.get(0).get("metricCode")),
                        oid + "-soldOn->" + c.get("id"),
                        "Metric-relatedToChannel->" + c.get("id")
                ));
                Map<String, Object> drill = new LinkedHashMap<>();
                drill.put("orderDelta", orderDelta);
                drill.put("contribRatio", contrib);
                Object trend = c.get("trend");
                if (trend != null) {
                    drill.put("trend", trend);
                } else {
                    drill.put("trend", List.of());
                }
                c.put("drill", drill);
                candidates.add(c);
                triples.add(triple(oid, "soldOn", c.get("id")));
                triples.add(triple(c.get("id"), "orderDelta", orderDelta));
                triples.add(triple(c.get("id"), "contribRatio", contrib));
            }
        } else {
            reasonEngine = trySwrl
                    ? (swrl.engine() == null ? "java-rules" : swrl.engine())
                    : "java-rules";
            if (opsRules.isRuleEnabled(a01)) {
                for (Map<String, Object> m : castListOfMaps(node.get("metrics"))) {
                    if (!isMetricAnomaly(m)) continue;
                    Object deltaObj = m.get("metricDelta");
                    if (deltaObj != null) {
                        double delta = num(deltaObj, 0);
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("metricCode", m.get("metricCode"));
                        row.put("metricValue", m.get("metricValue"));
                        row.put("metricDelta", delta);
                        row.put("ruleId", "R-A01");
                        row.put("anomalyFlag", true);
                        row.put("engine", "java-rules");
                        row.put("message", m.get("metricCode") + "环比 " + Math.round(delta * 100) + "%");
                        anomalies.add(row);
                    } else {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("metricCode", m.get("metricCode"));
                        row.put("metricValue", m.get("metricValue"));
                        row.put("metricDeltaPp", m.get("metricDeltaPp"));
                        row.put("ruleId", "R-A01");
                        row.put("anomalyFlag", true);
                        row.put("engine", "java-rules");
                        row.put("message", m.get("metricCode") + "异动 " + m.get("metricDeltaPp") + "pp");
                        anomalies.add(row);
                    }
                }
            }
            if (!anomalies.isEmpty() && opsRules.isRuleEnabled(a02)) {
                double orderDeltaLte = opsRules.ruleNum(a02, "orderDeltaLte", -0.20);
                double contribGte = opsRules.ruleNum(a02, "contribRatioGte", 0.30);
                for (Map<String, Object> ch : castListOfMaps(node.get("channels"))) {
                    double orderDelta = num(ch.get("orderDelta"), 0);
                    double contrib = num(ch.get("contribRatio"), 0);
                    if (orderDelta <= orderDeltaLte && contrib >= contribGte) {
                        double weight = num(ch.get("weightHint"), contrib);
                        Map<String, Object> c = new LinkedHashMap<>();
                        c.put("type", "Channel");
                        c.put("id", ch.get("channelId"));
                        c.put("name", ch.get("name"));
                        c.put("score", weight);
                        c.put("weight", weight);
                        c.put("ruleId", "R-A02");
                        c.put("engine", "java-rules");
                        c.put("evidence", List.of(
                                "订购量变化 " + Math.round(orderDelta * 100) + "%",
                                "渠道贡献占比 " + Math.round(contrib * 100) + "%"
                        ));
                        c.put("path", List.of(
                                oid + "-hasMetric->" + anomalies.get(0).get("metricCode"),
                                oid + "-soldOn->" + ch.get("channelId"),
                                "Metric-relatedToChannel->" + ch.get("channelId")
                        ));
                        Map<String, Object> drill = new LinkedHashMap<>();
                        drill.put("orderDelta", orderDelta);
                        drill.put("contribRatio", contrib);
                        Object trend = ch.get("trend");
                        if (trend != null) {
                            drill.put("trend", trend);
                        } else {
                            drill.put("trend", List.of());
                        }
                        c.put("drill", drill);
                        candidates.add(c);
                        triples.add(triple(oid, "soldOn", ch.get("channelId")));
                        triples.add(triple(ch.get("channelId"), "orderDelta", orderDelta));
                        triples.add(triple(ch.get("channelId"), "contribRatio", contrib));
                    }
                }
            }
        }

        if (anomalies.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("offeringId", oid);
            body.put("offeringName", offering.get("offeringName"));
            body.put("anomalies", List.of());
            body.put("candidates", List.of());
            body.put("paths", List.of());
            body.put("actionList", List.of());
            body.put("evidenceTriples", List.of());
            body.put("appliedRules", List.of());
            body.put("reasonEngine", reasonEngine);
            body.put("swrlMessage", swrl.message());
            body.put("opsRulesVersion", opsRules.version());
            body.put("message", "图谱中未检出达到阈值的异动指标，无法继续归因");
            body.put("snapshotAt", Instant.now().toString());
            return body;
        }

        Map<String, Object> a03 = opsRules.rootCauseRule("R-A03");
        if (opsRules.isRuleEnabled(a03)) {
            int daysLte = (int) opsRules.ruleNum(a03, "daysToExpireLte", 7);
            double drivenGte = opsRules.ruleNum(a03, "drivenOrderRatioGte", 0.25);
            for (Map<String, Object> pr : castListOfMaps(node.get("promotions"))) {
                int days = (int) num(pr.get("daysToExpire"), 999);
                double driven = num(pr.get("drivenOrderRatio"), 0);
                if (days <= daysLte && driven >= drivenGte) {
                    double weight = num(pr.get("weightHint"), driven);
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("type", "Promotion");
                    c.put("id", pr.get("promoId"));
                    c.put("name", pr.get("name"));
                    c.put("score", weight);
                    c.put("weight", weight);
                    c.put("ruleId", "R-A03");
                    c.put("engine", "java-rules");
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
        }

        Map<String, Object> a04 = opsRules.rootCauseRule("R-A04");
        if (opsRules.isRuleEnabled(a04)) {
            double gapGte = opsRules.ruleNum(a04, "priceGapRatioGte", 0.15);
            double penetGt = opsRules.ruleNum(a04, "penetrationDeltaPpGt", 0);
            for (Map<String, Object> cp : castListOfMaps(node.get("competitors"))) {
                double gapRatio = num(cp.get("priceGapRatio"), 0);
                double penet = num(cp.get("penetrationDeltaPp"), 0);
                if (gapRatio >= gapGte && penet > penetGt) {
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
        }

        Map<String, Object> a05 = opsRules.rootCauseRule("R-A05");
        if (opsRules.isRuleEnabled(a05)) {
            double minWeight = opsRules.ruleNum(a05, "minWeightHint", 0.08);
            for (Map<String, Object> ub : castListOfMaps(node.get("behaviors"))) {
                double weight = num(ub.get("weightHint"), minWeight);
                if (weight < minWeight) continue;
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
        }

        int topN = opsRules.rootCauseTopN();
        candidates.sort((a, b) -> Double.compare(num(b.get("score"), 0), num(a.get("score"), 0)));
        List<Map<String, Object>> top3 = candidates.stream().limit(Math.max(1, topN)).collect(Collectors.toList());

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
        anomaly.put("metric", anomalies.get(0).get("metricCode"));
        if (anomalies.get(0).containsKey("metricDelta")) {
            anomaly.put("delta", anomalies.get(0).get("metricDelta"));
        }
        if (anomalies.get(0).containsKey("metricDeltaPp")) {
            anomaly.put("deltaPp", anomalies.get(0).get("metricDeltaPp"));
        }
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
        if (Boolean.TRUE.equals(a05.get("includeWhenRanked"))
                && top3.stream().anyMatch(c -> "R-A05".equals(str(c.get("ruleId"))))) {
            applied.add("R-A05");
        }

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
        body.put("opsRulesVersion", opsRules.version());
        body.put("reasonEngine", reasonEngine);
        body.put("swrlFiredRules", swrlFired);
        body.put("swrlMessage", swrl.message());
        body.put("snapshotAt", snapshotAt);
        if (paths.isEmpty()) {
            body.put("message", "已确认异动，但未命中渠道/促销/竞品等归因规则");
        }
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

            if (opsRules.isRuleEnabled(opsRules.riskRule("R-B01"))
                    && monthly == 0 && oneTime == 0 && !inWhitelist) {
                risks.add(riskFeature("R-B01", "零元资费", "月费与一次性费均为0且非权益赠送白名单"));
                evidenceTriples.add(triple(o.get("offeringId"), "hasPricePlan", "PP-" + o.get("offeringId")));
                evidenceTriples.add(triple("PP-" + o.get("offeringId"), "monthlyFee", 0));
                evidenceTriples.add(triple("PP-" + o.get("offeringId"), "oneTimeFee", 0));
                if (opsRules.isRuleEnabled(opsRules.riskRule("R-B02"))
                        && "上架".equals(str(o.get("state"))) && !truthy(o.get("hasContract"))) {
                    risks.add(riskFeature("R-B02", "零元无合约在架", "零元资费已上架且无合约约束"));
                    evidenceTriples.add(triple(o.get("offeringId"), "hasContract", false));
                    riskLevel = "HIGH";
                    Map<String, Object> act = castMap(riskActions.get("零元资费"));
                    actions.add(str(act.getOrDefault("defaultAction", "建议立即下架或转验证渠道")));
                }
            }

            Map<String, Object> b01 = opsRules.riskRule("R-B01");
            double fullDiscGte = opsRules.ruleNum(b01, "fullDiscountPercentGte", 100);
            if (opsRules.isRuleEnabled(b01)
                    && num(o.get("discountPercent"), -1) >= fullDiscGte
                    && truthy(o.get("repeatable"))
                    && empty(o.get("targetCustomerGroup"))) {
                risks.add(riskFeature("R-B01", "异常全额赠送", "折扣100% + 可重复订购 + 无目标客户群"));
                riskLevel = "HIGH";
                Map<String, Object> act = castMap(riskActions.get("异常全额赠送"));
                actions.add(str(act.getOrDefault("defaultAction", "限售 + 复核优惠规则")));
            }

            if (opsRules.isRuleEnabled(opsRules.riskRule("R-B03"))
                    && num(o.get("salesCnt30d"), -1) == 0 && num(o.get("shelfDays"), 0) > zeroShelfDays) {
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
            Map<String, Object> b04 = opsRules.riskRule("R-B04");
            Set<String> lowCats = opsRules.riskCategories("R-B04", Set.of("low_eff"));
            if (opsRules.isRuleEnabled(b04)
                    && lowCats.contains(category)
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
            if (opsRules.isRuleEnabled(opsRules.riskRule("R-B05"))
                    && "HIGH".equals(riskLevel) && num(o.get("shelfDays"), 0) > reviewDays) {
                risks.add(riskFeature("R-B05", "预警升级", "高风险且上架超过" + reviewDays + "天未复核"));
                actions.add("紧急复核");
                urgent = true;
            }

            if (!risks.isEmpty()) {
                int score = opsRules.riskScore(riskLevel, urgent);
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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", results.size());
        body.put("scannedCount", scannedCount);
        body.put("highCount", highCount);
        body.put("mediumCount", mediumCount);
        body.put("suggestDelistCount", suggestDelistCount);
        body.put("items", results);
        body.put("appliedRules", opsRules.ruleIds("risk"));
        body.put("ruleVersion", rules.getOrDefault("ruleVersion", "RiskRules-v1.2"));
        body.put("riskRules", rules);
        body.put("riskScoring", opsRules.riskScoring());
        body.put("auditedAt", Instant.now().toString());
        return withModeMeta(body);
    }

    private Map<String, Object> riskFeature(String ruleId, String feature, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("feature", feature);
        row.put("message", message);
        return row;
    }

    private Map<String, Object> riskRules() {
        Map<String, Object> fromFile = opsRules.riskDefaults();
        Map<String, Object> fromGraph = castMap(loadGraph().get("riskRuleDefaults"));
        Map<String, Object> base = new LinkedHashMap<>();
        // 外置 ops_rules 优先，图谱 defaults 作兼容回退
        base.putAll(fromGraph);
        base.putAll(fromFile);
        base.putAll(riskRuleOverrides);
        return base;
    }


    private boolean isMetricAnomaly(Map<String, Object> metric) {
        Map<String, Object> a01 = opsRules.rootCauseRule("R-A01");
        if (!opsRules.isRuleEnabled(a01)) {
            return false;
        }
        Object deltaObj = metric.get("metricDelta");
        double deltaLte = opsRules.ruleNum(a01, "metricDeltaLte", -0.10);
        if (deltaObj != null && num(deltaObj, 0) <= deltaLte) {
            return true;
        }
        boolean honorFlag = a01.get("honorAnomalyFlag") == null || truthy(a01.get("honorAnomalyFlag"));
        return honorFlag && truthy(metric.get("anomaly"));
    }

    private static Map<String, String> metaClass(String code, String name) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("classCode", code);
        row.put("className", name);
        return row;
    }

    private Map<String, Object> issue(String ruleId, String issueType, String level, String field,
                                      String message, List<String> evidence, List<Map<String, Object>> triples) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        String alias = opsRules.configProposalAlias(ruleId);
        if (!alias.isBlank()) {
            row.put("proposalAlias", alias);
        }
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
