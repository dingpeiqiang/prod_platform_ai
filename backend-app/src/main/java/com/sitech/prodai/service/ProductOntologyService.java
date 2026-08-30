package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import com.sitech.prodai.domain.entity.OntologyInstance;
import com.sitech.prodai.domain.entity.OpsWorkOrder;
import com.sitech.prodai.repository.OntologyInstanceRepository;
import com.sitech.prodai.repository.OpsWorkOrderRepository;
import com.sitech.prodai.service.ops.OpsExtractionService;
import com.sitech.prodai.service.ops.OpsGraphSchemaValidator;
import com.sitech.prodai.service.ops.OpsProductGraphLoader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 产商品配置与运营本体推理引擎。
 * 配置规则 R-C01~C08 / R-D01~D05（Java）；运营归因 R-A01~A05 / 风险 R-B01~B05 优先 Openllet SWRL，失败回退 Java。
 * <p>规则阈值与启用开关统一读 {@link OpsRulesService}（ops_rules.json）。
 * 事实数据统一来自 {@link OpsProductGraphLoader}；演示与生产仅差 graph-path / data-source 配置。
 */
@Service
public class ProductOntologyService {

    private static final Logger log = LoggerFactory.getLogger(ProductOntologyService.class);
    private static final Pattern NUM_PATTERN = Pattern.compile("[\\d.]+");

    private static final List<Map<String, String>> ONTOLOGY_CLASSES = List.of(
            // 主体层
            metaClass("PricingProduct", "产商品资费"),
            metaClass("ProductCategory", "产品品类"),
            metaClass("OfferCompatibility", "资费相容关系"),
            // 方案层
            metaClass("ConfigScheme", "配置方案"),
            metaClass("ConfigChange", "配置变更"),
            metaClass("OfferingConfig", "商品配置草稿(兼容)"),
            // 要素层
            metaClass("SalesPolicy", "销售策略"),
            metaClass("ReleaseScope", "发布范围"),
            metaClass("NetworkCapability", "网络能力"),
            metaClass("FamilyOfferPolicy", "家庭资费策略"),
            metaClass("ChargePlan", "固费收费方案"),
            metaClass("PreferentialPlan", "优惠方案"),
            metaClass("AccountPreferential", "账务优惠"),
            metaClass("FloorGuarantee", "保底优惠"),
            metaClass("CdrPreferential", "话单优惠"),
            metaClass("ResourceEntitlement", "资源权益"),
            metaClass("DataResource", "流量资源"),
            metaClass("VoiceResource", "语音资源"),
            metaClass("SmsResource", "短信资源"),
            metaClass("PrintNotice", "免填单告知"),
            metaClass("SmsNotice", "短信告知"),
            metaClass("ValueAddedEquity", "增值权益"),
            // 管控层
            metaClass("BusinessConstraint", "业务约束"),
            metaClass("ComplianceRule", "合规规则"),
            metaClass("BusinessScene", "业务场景"),
            metaClass("CodeDictionary", "业务码表")
    );

    private final ObjectMapper objectMapper;
    private final ProdAiProperties properties;
    private final OpsSwrlReasoner opsSwrlReasoner;
    private final OpsRulesService opsRules;
    private final OpsProductGraphLoader graphLoader;
    private final OpsExtractionService extractionService;
    private final ConfigDocumentParser documentParser;
    private final ConfigDocumentStorage documentStorage;
    private final Rdf4jOntologyStore rdf4jStore;
    private final OpsWorkOrderRepository workOrderRepository;
    private final OntologyInstanceRepository instanceRepository;
    private final ConfigMessageProjector messageProjector;
    private final LastKnownGoodGuard lastKnownGoodGuard;
    private final OntologyVersionService versionService;
    /** 延迟解析回归运行器（P1-7 SMOKE 回接）：规避与 ProductConfigRegressionService 的构造循环依赖。 */
    private final ObjectProvider<ProductConfigRegressionService> regressionServiceProvider;

    private static final String OFFERING_CONFIG_CODE = "offering_config";
    private static final String DRAFT_JSON_KEY = "_draft_json";
    private static final String CLIENT_ID_KEY = "client_id";

    private Map<String, Object> graphCache;
    private String graphSourceId = "empty";
    private final RiskAuditService riskAudit;
    private final TemplateDeriveEngine deriveEngine;
    /** 配置场景审计链路（内存）；对齐方案 get_trace / explain。 */
    private final Map<String, List<Map<String, Object>>> configTraces = new ConcurrentHashMap<>();
    /** 最近一次批量稽核快照（定时/手动）。 */
    private volatile Map<String, Object> lastBatchAudit = new LinkedHashMap<>();

    public ProductOntologyService(ObjectMapper objectMapper,
                              ProdAiProperties properties,
                              OpsSwrlReasoner opsSwrlReasoner,
                              OpsRulesService opsRules,
                              OpsProductGraphLoader graphLoader,
                              OpsExtractionService extractionService,
                              ConfigDocumentParser documentParser,
                              ConfigDocumentStorage documentStorage,
                              Rdf4jOntologyStore rdf4jStore,
                              OpsWorkOrderRepository workOrderRepository,
                              OntologyInstanceRepository instanceRepository,
                              ConfigMessageProjector messageProjector,
                              LastKnownGoodGuard lastKnownGoodGuard,
                              OntologyVersionService versionService,
                              RiskAuditService riskAudit,
                              TemplateDeriveEngine deriveEngine,
                              ObjectProvider<ProductConfigRegressionService> regressionServiceProvider) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.opsSwrlReasoner = opsSwrlReasoner;
        this.opsRules = opsRules;
        this.graphLoader = graphLoader;
        this.extractionService = extractionService;
        this.documentParser = documentParser;
        this.documentStorage = documentStorage;
        this.rdf4jStore = rdf4jStore;
        this.workOrderRepository = workOrderRepository;
        this.instanceRepository = instanceRepository;
        this.messageProjector = messageProjector;
        this.lastKnownGoodGuard = lastKnownGoodGuard;
        this.versionService = versionService;
        this.riskAudit = riskAudit;
        this.deriveEngine = deriveEngine;
        this.regressionServiceProvider = regressionServiceProvider;
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

    /**
     * 事务式热重载事实图（P1-6 last-known-good 守卫）：
     * LOAD（解析新源）→ VALIDATE（OpsGraphSchemaValidator）→ SMOKE（P1-7 回归用例集断言）→ COMMIT（原子切换 graphCache）。
     * 任一步失败保留现行图谱并返回 success:false + 差异报告；成功/失败均登记版本库表 A + 表 B。
     */
    public synchronized Map<String, Object> reloadGraph() {
        String version = "r" + Instant.now().toEpochMilli();
        LastKnownGoodGuard.GuardRequest request = LastKnownGoodGuard.GuardRequest
                .builder(OntologyVersionService.TYPE_ABOX_SNAPSHOT, "product_graph",
                        () -> {
                            OpsProductGraphLoader.LoadedGraph loaded = graphLoader.load();
                            Map<String, Object> raw = new LinkedHashMap<>(loaded.graph());
                            raw.put("shelfOfferings", castListOfMaps(raw.get("shelfOfferings")));
                            Map<String, Object> pending = new LinkedHashMap<>();
                            pending.put("graph", raw);
                            pending.put("sourceId", loaded.sourceId());
                            return pending;
                        },
                        commit -> {
                            graphCache = castMap(commit.get("graph"));
                            graphSourceId = String.valueOf(commit.get("sourceId"));
                        })
                .validator(pending -> {
                    OpsGraphSchemaValidator.ValidationResult vr =
                            OpsGraphSchemaValidator.validateAndNormalize(castMap(pending.get("graph")));
                    return vr.ok() ? List.of() : vr.errors();
                })
                // P1-7 回接：SMOKE 用 pending 图谱跑回归用例集，任一断言失败阻断切换
                .smoke(pending -> regressionServiceProvider.getObject()
                        .smokeAgainstGraph(castMap(pending.get("graph"))))
                .version(version)
                .summary("事实图热重载（last-known-good 守卫）")
                .payloadFrom(pending -> {
                    try {
                        return objectMapper.writeValueAsString(pending.get("graph"));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .build();
        return lastKnownGoodGuard.execute(request);
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

    /** 外置规则全集（只读视图）+ 风险阈值生效态 / 审计 */
    public Map<String, Object> getOpsRulesCatalog() {
        Map<String, Object> body = new LinkedHashMap<>(opsRules.catalogView());
        body.putAll(riskRulesAdminView());
        return body;
    }

    /**
     * 事务式热重载 ops_rules.json（P1-6 守卫；不落盘改写；覆盖阈值仍保留）。
     * LOAD/VALIDATE 失败保留现行规则集；成功/失败均登记版本库表 A + 表 B。
     */
    public Map<String, Object> reloadOpsRules() {
        String version = "r" + Instant.now().toEpochMilli();
        LastKnownGoodGuard.GuardRequest request = LastKnownGoodGuard.GuardRequest
                .builder(OntologyVersionService.TYPE_OPS_RULES, "ops_rules",
                        opsRules::loadPending,
                        opsRules::swap)
                .validator(pending -> pending == null || pending.get("version") == null
                        ? List.of("ops_rules missing required key: version")
                        : List.of())
                .version(version)
                .summary("ops_rules 热重载（last-known-good 守卫）")
                .payloadFrom(pending -> {
                    try {
                        return objectMapper.writeValueAsString(pending);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .build();
        Map<String, Object> report = lastKnownGoodGuard.execute(request);
        if (Boolean.TRUE.equals(report.get("success"))) {
            appendRiskRuleAudit("reload_file", Map.of(
                    "rulesPath", properties.getOntology().getRulesPath(),
                    "version", opsRules.version()
            ));
        }
        Map<String, Object> body = new LinkedHashMap<>(report);
        if (Boolean.TRUE.equals(report.get("success"))) {
            body.putAll(getOpsRulesCatalog());
        }
        return body;
    }

    private Map<String, Object> riskRulesAdminView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("riskEffective", riskRules());
        view.put("riskDefaults", opsRules.riskDefaults());
        view.put("riskOverrides", riskAudit.overrides());
        // P3-5 ① 审计链：优先表 B risk 域回读，空则回退内存态
        List<Map<String, Object>> domainLogs = versionService.riskAuditLogs();
        view.put("riskAuditLog", domainLogs.isEmpty() ? riskAudit.snapshotAudit() : domainLogs);
        view.put("opsRulesVersion", opsRules.version());
        view.put("rulesPath", properties.getOntology().getRulesPath());
        view.put("swrlEnabled", properties.getOntology().isSwrlEnabled());
        return view;
    }

    private synchronized void appendRiskRuleAudit(String action, Map<String, Object> detail) {
        riskAudit.append(action, detail, riskRules());
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
        body.put("configSchemes", castListOfMaps(graph.get("configSchemes")));
        body.put("productCategories", messageProjector.categories());
        body.put("relations", List.of(
                "configuresProduct", "belongsToCategory", "hasSalesPolicy", "hasReleaseScope",
                "hasNetworkCapability", "hasFamilyOfferPolicy", "hasChargePlan", "hasPreferentialPlan",
                "hasResourceEntitlement", "hasPrintNotice", "hasSmsNotice", "hasValueAddedEquity",
                "hasOfferCompatibility", "hasConfigChange", "governedBy", "appliesScene", "similarTo"
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
        body.put("ontologyVersion", "2.2");
        body.put("productCategories", messageProjector.categories());
        body.put("bizScenarios", castMap(graph.get("bizScenarios")));
        body.put("templates", castMap(graph.get("templates")));
        body.put("configSchemes", castListOfMaps(graph.get("configSchemes")));
        body.put("equityGiftWhitelist", castList(graph.get("equityGiftWhitelist")));
        body.put("riskRuleDefaults", riskRules());
        body.put("opsRules", getOpsRulesCatalog());
        return withModeMeta(body);
    }

    public Map<String, Object> checkCompliance(Map<String, Object> draftInput) {
        return checkCompliance(draftInput, null);
    }

    /** 图谱参数化变体（P1-7 SMOKE 回接）：用 pending 图谱跑合规断言，不触碰现行 graphCache。 */
    public Map<String, Object> checkCompliance(Map<String, Object> draftInput, Map<String, Object> graphOverride) {
        Map<String, Object> graph = graphOverride != null ? graphOverride : loadGraph();
        Map<String, Object> draft = messageProjector.applyCategoryDefaults(
                draftInput == null ? Map.of() : draftInput);
        List<Map<String, Object>> issues = new ArrayList<>();

        if (opsRules.isConfigEnabled("R-C06")) {
            List<String[]> required = List.of(
                    new String[]{"offeringName", "资费名称"},
                    new String[]{"messageRootKey", "产品品类"},
                    new String[]{"fixedFeeAmount", "固费金额"},
                    new String[]{"channelScope", "销售渠道"}
            );
            for (String[] item : required) {
                Object val = draft.get(item[0]);
                if ("fixedFeeAmount".equals(item[0])) {
                    val = firstNonEmpty(draft.get("fixedFeeAmount"), draft.get("monthlyFee"),
                            castMap(draft.get("chargePlan")).get("fixedFeeAmount"));
                }
                if ("offeringName".equals(item[0])) {
                    val = firstNonEmpty(draft.get("offeringName"), draft.get("offerName"));
                }
                if ("channelScope".equals(item[0])) {
                    val = firstNonEmpty(draft.get("channelScope"),
                            castMap(draft.get("releaseScope")).get("channelScope"));
                }
                if (empty(val)) {
                    issues.add(issue("R-C06", "必填缺失", "MEDIUM", item[0],
                            "缺少必填字段：" + item[1], List.of(item[0] + "=empty"), null));
                }
            }
            // 附加品类需依赖主资费
            if (Boolean.FALSE.equals(draft.get("isMainOffer"))
                    || "false".equalsIgnoreCase(str(draft.get("isMainOffer")))) {
                if (empty(draft.get("dependOn")) && empty(draft.get("sourceOfferRef"))) {
                    // 由 R-C04 专门处理，此处不重复
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

        boolean isAddon = "addon".equals(str(draft.get("offeringType")))
                || Boolean.FALSE.equals(draft.get("isMainOffer"))
                || "false".equalsIgnoreCase(str(draft.get("isMainOffer")));
        if (opsRules.isConfigEnabled("R-C04")
                && isAddon && empty(draft.get("dependOn")) && empty(draft.get("sourceOfferRef"))) {
            issues.add(issue("R-C04", "规则漏洞", "HIGH", "dependOn",
                    "附加资费缺少依赖的主资费/相容关系（dependOn 或 sourceOfferRef）",
                    List.of("isMainOffer=false", "dependOn=empty"), null));
        }

        double monthly = resolveFixedFee(draft);
        double oneTime = num(draft.get("oneTimeFee"), 0);
        String scenario = str(draft.get("bizScenario"));
        List<Object> whitelist = castList(graph.get("equityGiftWhitelist"));
        if (opsRules.isConfigEnabled("R-C05")
                && monthly == 0 && oneTime == 0 && !truthy(draft.get("hasContract"))) {
            if (!whitelist.contains(scenario) && !"内部验证".equals(str(draft.get("channelScope")))) {
                issues.add(issue("R-C05", "高风险资费", "HIGH", "fixedFeeAmount",
                        "固费/一次性费均为0且无合约，非权益赠送白名单",
                        List.of("fixedFeeAmount=0", "oneTimeFee=0", "hasContract=0"), null));
            }
        }

        double discount = num(firstNonEmpty(draft.get("discountPercent"), draft.get("prefDiscount")), -1);
        if (discount > 1 && discount <= 100) {
            // 百分数转比例，兼容旧草稿
        } else if (discount > 0 && discount <= 1) {
            discount = discount * 100;
        }
        boolean repeatable = truthy(draft.get("repeatable"))
                || "是".equals(str(draft.get("repeatChargeFlag")));
        if (opsRules.isConfigEnabled("R-C07")
                && discount == 100 && repeatable) {
            issues.add(issue("R-C07", "异常优惠漏洞", "HIGH", "prefDiscount",
                    "优惠折扣100%且可重复订购，存在异常优惠漏洞",
                    List.of("prefDiscount=100", "repeatable=true"), null));
        }

        // R-C09 ≈ 方案 R-CONF-001：资费上下限
        if (opsRules.isConfigEnabled("R-C09") && monthly >= 0) {
            double minFee = opsRules.configDefaultNum("monthlyFeeMin", 9);
            double maxFee = opsRules.configDefaultNum("monthlyFeeMax", 599);
            if (monthly < minFee || monthly > maxFee) {
                issues.add(issue("R-C09", "资费区间违规", "HIGH", "fixedFeeAmount",
                        "固费取值超出合规范围，允许区间为" + (int) minFee + "-" + (int) maxFee + "元",
                        List.of("fixedFeeAmount=" + monthly, "min=" + minFee, "max=" + maxFee), null));
            }
        }

        // R-C03 扩展 ≈ 方案 R-CONF-002：折扣与赠费并存需复核
        double freeFee = num(firstNonEmpty(draft.get("freeFeeAmount"), draft.get("giftFee"), draft.get("prefFee")), -1);
        if (opsRules.isConfigEnabled("R-C03") && discount > 0 && freeFee > 0) {
            issues.add(issue("R-C03", "资费冲突", "MEDIUM", "prefDiscount",
                    "同时配置了折扣与赠费，存在资费冲突风险，需人工复核（方案别名 R-CONF-002）",
                    List.of("prefDiscount=" + discount, "prefFee=" + freeFee), null));
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
        body.put("messageRootKey", draft.get("messageRootKey"));
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
                || !empty(draft.get("offerName"))
                || !empty(draft.get("monthlyFee"))
                || !empty(draft.get("fixedFeeAmount"))
                || !empty(draft.get("bizScenario"))
                || !empty(draft.get("messageRootKey"))
                || !empty(draft.get("categoryCode"))
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
        draft.put("offerName", shelf.get("offeringName"));
        draft.put("offeringType", firstNonEmpty(shelf.get("offeringType"), "main_pkg"));
        draft.put("monthlyFee", shelf.get("monthlyFee"));
        draft.put("fixedFeeAmount", firstNonEmpty(shelf.get("fixedFeeAmount"), shelf.get("monthlyFee")));
        draft.put("oneTimeFee", firstNonEmpty(shelf.get("oneTimeFee"), 0));
        draft.put("mutexGroup", firstNonEmpty(shelf.get("mutexGroup"), "MAIN_PKG"));
        draft.put("hasContract", shelf.get("hasContract"));
        draft.put("discountPercent", shelf.get("discountPercent"));
        draft.put("repeatable", shelf.get("repeatable"));
        draft.put("messageRootKey", shelf.get("messageRootKey"));
        draft.put("categoryCode", shelf.get("categoryCode"));
        draft.put("categoryName", shelf.get("categoryName"));
        draft.put("productLine", shelf.get("productLine"));
        draft.put("chargePlan", shelf.get("chargePlan"));
        draft.put("releaseScope", shelf.get("releaseScope"));
        draft.put("familyOfferPolicy", shelf.get("familyOfferPolicy"));
        draft.put("networkCapability", shelf.get("networkCapability"));
        draft.put("workOrderId", shelf.get("workOrderId"));
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
        draft.put("channelScope", firstNonEmpty(shelf.get("channelScope"),
                castMap(shelf.get("releaseScope")).get("channelScope"), "全渠道"));
        draft.put("state", shelf.get("state"));
        draft.put("fillSources", Map.of("_source", "shelf"));
        return messageProjector.applyCategoryDefaults(draft);
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
        List<Map<String, Object>> schemes = castListOfMaps(graph.get("configSchemes"));

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
            String blob = (str(t.get("templateId")) + " " + str(t.get("name"))
                    + " " + str(t.get("messageRootKey")) + " " + str(t.get("categoryCode")))
                    .toLowerCase(Locale.ROOT);
            if (q.isBlank() || blob.contains(q.toLowerCase(Locale.ROOT))
                    || q.contains("模板") || q.contains("套餐") || q.contains("资费")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("template_id", t.get("templateId"));
                row.put("name", t.get("name"));
                row.put("monthly_fee", firstNonEmpty(t.get("fixedFeeAmount"), t.get("monthlyFee")));
                row.put("message_root_key", t.get("messageRootKey"));
                row.put("category_code", t.get("categoryCode"));
                tplHits.add(row);
            }
        }

        List<Map<String, Object>> schemeHits = new ArrayList<>();
        for (Map<String, Object> s : schemes) {
            String blob = (str(s.get("schemeId")) + " " + str(s.get("schemeName"))
                    + " " + str(s.get("messageRootKey")) + " " + str(s.get("categoryName"))
                    + " " + str(s.get("productLine"))).toLowerCase(Locale.ROOT);
            if (q.isBlank() || blob.contains(q.toLowerCase(Locale.ROOT))
                    || q.contains("方案") || q.contains("配置")) {
                Map<String, Object> row = new LinkedHashMap<>(s);
                row.put("score", q.isBlank() ? 1 : (blob.contains(q.toLowerCase(Locale.ROOT)) ? 40 : 5));
                schemeHits.add(row);
            }
        }
        if (schemeHits.size() > 10) {
            schemeHits = new ArrayList<>(schemeHits.subList(0, 10));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("query", q);
        body.put("total", items.size());
        body.put("items", items);
        body.put("templates", tplHits.stream().limit(10).collect(Collectors.toList()));
        body.put("configSchemes", schemeHits);
        body.put("productCategories", messageProjector.categories());
        String traceId = "cfg-discover-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "nl_discover_and_retrieve",
                "query", q,
                "hit_count", items.size(),
                "timestamp", Instant.now().toString()
        ));
        body.put("trace_id", traceId);
        return withModeMeta(body);
    }

    /**
     * 市场洞察：从 shelfOfferings + opsGraph 指标组装增长/风险视图。
     * <p>
     * 与异动归因、风险稽核同源事实图，避免仅依赖 RDF 种子（生产默认不灌）导致 0 条。
     */
    public Map<String, Object> marketInsight(String question, int limit) {
        String q = question == null ? "" : question.trim();
        int lim = limit <= 0 ? 20 : Math.min(limit, 50);
        String qLower = q.toLowerCase(Locale.ROOT);
        boolean wantRisk = q.contains("风险") || q.contains("零资费") || q.contains("稽核") || q.contains("低效");
        boolean wantGrowth = q.contains("增长") || q.contains("趋势") || q.contains("在售")
                || q.contains("套餐") || qLower.contains("5g") || q.contains("商品") || q.contains("洞察")
                || q.contains("市场");

        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> offerings = castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> opsGraph = castMap(graph.get("opsGraph"));
        Map<String, Object> templates = castMap(graph.get("templates"));

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> o : offerings) {
            int score = matchScore(q, o);
            boolean riskish = isRiskishOffering(o);
            if (wantRisk && riskish) {
                score += 45;
            }
            if (score <= 0 && !q.isBlank()) {
                if (wantGrowth && isOnShelf(o)) {
                    score = 8;
                } else {
                    continue;
                }
            }
            if (q.isBlank()) {
                score = Math.max(score, 1);
            }
            String id = str(o.get("offeringId"));
            if (!id.isBlank()) {
                seen.add(id);
            }
            String bucket = riskish ? "风险/零资费" : "在售/增长";
            rows.add(toMarketInsightRow(o, opsGraph.get(id), score, bucket));
        }

        // 货架无 5G 实体时，用模板补一条可演示的增长样本
        if (qLower.contains("5g") && rows.stream().noneMatch(r -> str(r.get("name")).toLowerCase(Locale.ROOT).contains("5g"))) {
            for (Object raw : templates.values()) {
                Map<String, Object> t = castMap(raw);
                String name = str(t.get("name"));
                if (!name.toLowerCase(Locale.ROOT).contains("5g")) {
                    continue;
                }
                String tid = str(t.get("templateId"));
                if (seen.contains(tid)) {
                    continue;
                }
                Map<String, Object> synthetic = new LinkedHashMap<>();
                synthetic.put("offeringId", tid);
                synthetic.put("offeringName", name);
                synthetic.put("state", "上架");
                synthetic.put("monthlyFee", firstNonEmpty(t.get("fixedFeeAmount"), t.get("monthlyFee")));
                synthetic.put("category", "normal");
                synthetic.put("offeringType", "main_pkg");
                rows.add(toMarketInsightRow(synthetic, null, 35, "在售/增长"));
                seen.add(tid);
            }
        }

        rows.sort((a, b) -> {
            int sc = Integer.compare((int) num(b.get("_score"), 0), (int) num(a.get("_score"), 0));
            if (sc != 0) {
                return sc;
            }
            Double ga = parseInsightGrowth(a.get("growth"));
            Double gb = parseInsightGrowth(b.get("growth"));
            if (ga == null && gb == null) {
                return 0;
            }
            if (ga == null) {
                return 1;
            }
            if (gb == null) {
                return -1;
            }
            return Double.compare(ga, gb);
        });
        if (rows.size() > lim) {
            rows = new ArrayList<>(rows.subList(0, lim));
        }
        for (Map<String, Object> row : rows) {
            row.remove("_score");
        }

        String answer;
        if (wantGrowth && wantRisk) {
            answer = "已查询在售产品增长指标与风险/零资费相关商品";
        } else if (wantRisk) {
            answer = "已查询零资费或风险相关产品";
        } else {
            answer = "已查询在售产品及其增长指标";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("nl_answer", answer);
        body.put("raw_results", rows);
        body.put("entity_ids", rows.stream().map(r -> str(r.get("product"))).filter(s -> !s.isBlank()).toList());
        body.put("sparql", "ops-graph shelfOfferings + opsGraph.metrics");
        body.put("discovery_method", "ops_graph");
        body.put("count", rows.size());
        body.put("question", q);
        return withModeMeta(body);
    }

    private boolean isOnShelf(Map<String, Object> o) {
        String st = str(o.get("state"));
        return st.isBlank() || "上架".equals(st) || "在售".equals(st);
    }

    private boolean isRiskishOffering(Map<String, Object> o) {
        String cat = str(o.get("category"));
        if ("zero_fee".equals(cat) || "low_eff".equals(cat) || "abnormal_discount".equals(cat)) {
            return true;
        }
        return num(o.get("monthlyFee"), -1) == 0 && !"whitelist".equals(cat);
    }

    private Map<String, Object> toMarketInsightRow(Map<String, Object> offering, Object opsNodeRaw, int score, String bucket) {
        Map<String, Object> opsNode = castMap(opsNodeRaw);
        String id = str(offering.get("offeringId"));
        String name = str(firstNonEmpty(offering.get("offeringName"), offering.get("name"), id));
        Double growth = null;
        Double users = null;
        for (Map<String, Object> m : castListOfMaps(opsNode.get("metrics"))) {
            Object delta = m.get("metricDelta");
            if (delta != null && growth == null) {
                growth = num(delta, 0);
            }
            String code = str(m.get("metricCode"));
            if (("累计收入".equals(code) || "收入".equals(code) || code.contains("收入")) && delta != null) {
                growth = num(delta, 0);
            }
            if (users == null && (code.contains("用户") || code.contains("新增") || "订购量".equals(code))) {
                users = num(m.get("metricValue"), Double.NaN);
                if (users.isNaN()) {
                    users = null;
                }
            }
        }
        if (growth == null && offering.get("revenue30d") != null && offering.get("salesCnt30d") != null) {
            double sales = num(offering.get("salesCnt30d"), 0);
            // 无环比时用销量相对占位，避免面板空白
            growth = sales <= 0 ? -0.05 : Math.min(0.2, sales / 5000.0);
        }
        if (users == null) {
            double sales = num(offering.get("salesCnt30d"), Double.NaN);
            if (!Double.isNaN(sales)) {
                users = sales;
            }
        }

        boolean zeroFee = Boolean.TRUE.equals(offering.get("isZeroFee"))
                || "zero_fee".equals(str(offering.get("category")))
                || num(offering.get("monthlyFee"), -1) == 0;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("product", id);
        row.put("entity", id);
        row.put("uri", id);
        row.put("name", name);
        row.put("productName", name);
        row.put("status", firstNonEmpty(offering.get("state"), "在售"));
        row.put("price", firstNonEmpty(offering.get("fixedFeeAmount"), offering.get("monthlyFee")));
        row.put("growth", growth);
        row.put("revenueGrowth", growth);
        row.put("users", users);
        row.put("newUserMonth", users);
        row.put("isZeroFee", zeroFee);
        row.put("category", offering.get("category"));
        row.put("_bucket", bucket);
        row.put("_score", score);
        return row;
    }

    private Double parseInsightGrowth(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw).replace("%", "").trim());
        } catch (Exception e) {
            return null;
        }
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

        // 关联模板（P2-7 主链路切换：derive_rules 引擎接管推理）
        Map<String, Object> infer = deriveEngine.derive(Map.of(
                "bizScenario", draft.get("bizScenario"),
                "targetUser", draft.get("targetUser"),
                "offeringType", draft.get("offeringType")
        ), draft, loadGraph());
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
        body.put("messageRootKey", draft.get("messageRootKey"));
        body.put("messagePreview", messageProjector.toMessage(draft));
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

    /** 智读：选择文件后预上传，发送时按 fileId 解析映射（不再二次传原文）。 */
    public Map<String, Object> uploadConfigDocument(org.springframework.web.multipart.MultipartFile file) {
        return documentStorage.store(file);
    }

    public Map<String, Object> batchFromUploadedFile(String fileId, String fileName) {
        byte[] bytes = documentStorage.readBytes(fileId);
        String name = (fileName == null || fileName.isBlank()) ? fileId : fileName;
        Map<String, Object> body = batchFromDocumentBytes(bytes, name);
        body.put("file_id", fileId);
        body.put("fileId", fileId);
        return body;
    }

    /**
     * 知识自迭代：合规通过的草稿沉淀至事实图 + RDF ConfigScheme。
     */
    public synchronized Map<String, Object> publishConfigDraft(Map<String, Object> draftInput) {
        Map<String, Object> draft = messageProjector.applyCategoryDefaults(
                draftInput == null ? Map.of() : deepCopy(draftInput));
        String newId = str(draft.get("offeringId"));
        if (newId.isBlank()) {
            newId = "OF-DRAFT-" + Instant.now().toEpochMilli();
        }
        Map<String, Object> compliance = checkCompliance(draft);
        if (!Boolean.TRUE.equals(compliance.get("compliancePass"))) {
            // P1-6 持久快照：合规失败行留 review 态供复盘
            registerDraftVersion(draft, newId, false, Map.of(
                    "step", "compliance",
                    "issues", compliance.get("issues") == null ? List.of() : compliance.get("issues")));
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "合规未通过，拒绝沉淀至本体");
            fail.put("issues", compliance.get("issues"));
            fail.put("compliancePass", false);
            return fail;
        }

        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> shelf = castListOfMaps(graph.get("shelfOfferings"));
        double fee = resolveFixedFee(draft);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("offeringId", newId);
        row.put("offeringName", firstNonEmpty(draft.get("offeringName"), draft.get("offerName"), newId));
        row.put("state", "上架");
        row.put("monthlyFee", fee >= 0 ? fee : draft.get("monthlyFee"));
        row.put("fixedFeeAmount", fee >= 0 ? fee : draft.get("fixedFeeAmount"));
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
        row.put("regionScope", draft.get("regionScope"));
        row.put("discountPercent", firstNonEmpty(draft.get("discountPercent"), draft.get("prefDiscount")));
        row.put("basedOnTemplate", draft.get("basedOnTemplate"));
        row.put("copiedFrom", draft.get("copiedFrom"));
        row.put("messageRootKey", draft.get("messageRootKey"));
        row.put("categoryCode", draft.get("categoryCode"));
        row.put("categoryName", draft.get("categoryName"));
        row.put("productLine", draft.get("productLine"));
        row.put("workOrderId", draft.get("workOrderId"));
        row.put("chargePlan", draft.get("chargePlan"));
        row.put("releaseScope", draft.get("releaseScope"));
        row.put("familyOfferPolicy", draft.get("familyOfferPolicy"));
        row.put("networkCapability", draft.get("networkCapability"));
        row.put("printNotice", draft.get("printNotice"));
        row.put("smsNotice", draft.get("smsNotice"));
        row.put("dependOn", draft.get("dependOn"));
        shelf.add(0, row);
        graph.put("shelfOfferings", shelf);

        List<Map<String, Object>> schemes = castListOfMaps(graph.get("configSchemes"));
        Map<String, Object> schemeRow = new LinkedHashMap<>();
        schemeRow.put("schemeId", newId);
        schemeRow.put("schemeName", row.get("offeringName"));
        schemeRow.put("status", "已上线");
        schemeRow.put("messageRootKey", row.get("messageRootKey"));
        schemeRow.put("categoryCode", row.get("categoryCode"));
        schemeRow.put("categoryName", row.get("categoryName"));
        schemeRow.put("productLine", row.get("productLine"));
        schemeRow.put("fixedFeeAmount", row.get("fixedFeeAmount"));
        schemeRow.put("monthlyFee", row.get("monthlyFee"));
        schemeRow.put("bizScenario", row.get("bizScenario"));
        schemeRow.put("channelScope", row.get("channelScope"));
        schemeRow.put("basedOnTemplate", row.get("basedOnTemplate"));
        schemeRow.put("workOrderId", row.get("workOrderId"));
        schemeRow.put("chargePlan", row.get("chargePlan"));
        schemeRow.put("releaseScope", row.get("releaseScope"));
        schemes.add(0, schemeRow);
        graph.put("configSchemes", schemes);
        graphCache = graph;

        Map<String, Object> messagePreview = messageProjector.toMessage(draft);

        String baseIri = properties.getOntology().normalizedBaseIri() + "config/";
        String uri = baseIri + newId;
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("schemeId", newId);
        facts.put("workOrderId", draft.get("workOrderId"));
        facts.put("status", "已上线");
        facts.put("version", firstNonEmpty(draft.get("version"), "V1.0"));
        facts.put("messageRootKey", draft.get("messageRootKey"));
        facts.put("categoryCode", draft.get("categoryCode"));
        facts.put("fixedFeeAmount", row.get("fixedFeeAmount"));
        facts.put("channelScope", row.get("channelScope"));
        facts.put("appliesScene", draft.get("bizScenario"));
        Object copiedFrom = draft.get("copiedFrom");
        if (!empty(copiedFrom)) {
            facts.put("similarTo", baseIri + copiedFrom);
        }
        rdf4jStore.addClass("ConfigScheme");
        rdf4jStore.addProperty("similarTo");
        rdf4jStore.addProperty("appliesScene");
        rdf4jStore.addProperty("messageRootKey");
        rdf4jStore.addProperty("fixedFeeAmount");
        rdf4jStore.addInstance(uri, "ConfigScheme", facts);

        String traceId = "cfg-publish-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "knowledge_iterate",
                "offering_id", newId,
                "uri", uri,
                "message_root_key", str(draft.get("messageRootKey")),
                "timestamp", Instant.now().toString()
        ));
        // P1-6 持久快照：发布成功登记 published（表 A + 表 B）
        registerDraftVersion(draft, newId, true, Map.of(
                "uri", uri,
                "trace_id", traceId,
                "message_root_key", str(draft.get("messageRootKey"))));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("offeringId", newId);
        body.put("schemeId", newId);
        body.put("uri", uri);
        body.put("shelfCount", shelf.size());
        body.put("trace_id", traceId);
        body.put("messageRootKey", draft.get("messageRootKey"));
        body.put("messagePreview", messagePreview);
        body.put("message", "已沉淀至事实图与配置本体 ConfigScheme，并生成报文投影");
        return body;
    }

    /** P1-6 持久快照：发布成功登记 published / 合规失败登记 review，均落版本库表 A + 表 B（登记失败不阻断主流程）。 */
    private void registerDraftVersion(Map<String, Object> draft, String newId, boolean success, Map<String, Object> detail) {
        try {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(draft);
            } catch (Exception e) {
                log.warn("[版本库] 草稿序列化失败，跳过登记: {}", e.getMessage());
                return;
            }
            OntologyAssetVersion row = versionService.register(
                    OntologyVersionService.TYPE_ABOX_SNAPSHOT, newId,
                    String.valueOf(firstNonEmpty(draft.get("version"), "V1.0")),
                    success ? OntologyVersionService.STATUS_PUBLISHED : OntologyVersionService.STATUS_REVIEW,
                    "config_publish", "知识自迭代配置发布草稿", payload);
            Map<String, Object> logDetail = new LinkedHashMap<>(detail == null ? Map.of() : detail);
            logDetail.put("success", success);
            versionService.log(row.getId(), "publish", "config_publish", logDetail);
        } catch (Exception e) {
            log.warn("[版本库] 配置草稿登记失败（不影响发布结果）: {}", e.getMessage());
        }
    }

    public Map<String, Object> getConfigTrace(String traceId) {
        List<Map<String, Object>> steps = configSteps(traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", !steps.isEmpty());
        body.put("trace_id", traceId);
        body.put("steps", steps);
        if (steps.isEmpty()) {
            body.put("message", "trace not found");
        }
        return body;
    }

    /** P3-5 ① 审计链来源：优先表 B config 域回读，空则回退内存态（重启后由表 B 复原链路）。 */
    private List<Map<String, Object>> configSteps(String traceId) {
        List<Map<String, Object>> fromDb = versionService.configTrace(traceId);
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        return configTraces.getOrDefault(traceId, List.of());
    }

    public Map<String, Object> explainConfig(String traceId, String audience) {
        List<Map<String, Object>> steps = configSteps(traceId);
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

    /**
     * 持久化配置草稿（JPA pd_ai_ontology_instance），绑定 session/user，刷新可恢复。
     */
    public Map<String, Object> saveConfigDraft(Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        @SuppressWarnings("unchecked")
        Map<String, Object> draftInput = req.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) req.get("draft")
                : (req.containsKey("offeringName") || req.containsKey("offerName") ? req : Map.of());
        Map<String, Object> draft = messageProjector.applyCategoryDefaults(
                draftInput == null || draftInput.isEmpty() ? Map.of() : deepCopy(draftInput));
        String sessionId = str(firstNonEmpty(req.get("sessionId"), req.get("session_id")));
        String userId = str(firstNonEmpty(req.get("userId"), req.get("user_id"), "anonymous"));
        String clientId = str(firstNonEmpty(req.get("clientId"), req.get("client_id"), draft.get("clientId")));
        Long draftId = parseLong(req.get("draftId") != null ? req.get("draftId") : req.get("draft_id"));

        OntologyInstance entity = null;
        if (draftId != null) {
            entity = instanceRepository.findByIdAndOntologyCode(draftId, OFFERING_CONFIG_CODE).orElse(null);
        }
        if (entity == null && !clientId.isBlank()) {
            entity = findDraftByClientId(clientId, sessionId, userId);
        }
        if (entity == null) {
            entity = new OntologyInstance();
            entity.setOntologyCode(OFFERING_CONFIG_CODE);
            entity.setStatus("draft");
        }
        entity.setUserId(userId);
        if (!sessionId.isBlank()) {
            entity.setSessionId(sessionId);
        }
        if (!"submitted".equals(entity.getStatus()) && !"filing".equals(entity.getStatus())) {
            entity.setStatus("draft");
        }

        Map<String, Object> store = new LinkedHashMap<>();
        store.put(CLIENT_ID_KEY, clientId.isBlank() ? "P" + Instant.now().toEpochMilli() : clientId);
        store.put("offeringName", firstNonEmpty(draft.get("offeringName"), draft.get("offerName"), ""));
        store.put("monthlyFee", String.valueOf(firstNonEmpty(draft.get("monthlyFee"), draft.get("fixedFeeAmount"), "")));
        store.put("bizScenario", str(draft.get("bizScenario")));
        store.put("channelScope", str(draft.get("channelScope")));
        store.put("compliancePass", String.valueOf(req.getOrDefault("compliancePass", draft.get("compliancePass"))));
        try {
            store.put(DRAFT_JSON_KEY, objectMapper.writeValueAsString(draft));
        } catch (Exception e) {
            throw new IllegalStateException("serialize draft failed: " + e.getMessage(), e);
        }
        entity.setData(store);
        OntologyInstance saved = instanceRepository.save(entity);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("draftId", saved.getId());
        body.put("clientId", store.get(CLIENT_ID_KEY));
        body.put("status", saved.getStatus());
        body.put("sessionId", saved.getSessionId());
        body.put("draft", draft);
        body.put("message", "配置草稿已持久化");
        return body;
    }

    public Map<String, Object> listConfigDrafts(String sessionId, String userId, String status) {
        List<OntologyInstance> rows;
        if (sessionId != null && !sessionId.isBlank()) {
            rows = instanceRepository.findTop50ByOntologyCodeAndSessionIdOrderByIdDesc(
                    OFFERING_CONFIG_CODE, sessionId.trim());
        } else if (userId != null && !userId.isBlank()) {
            rows = instanceRepository.findTop50ByOntologyCodeAndUserIdOrderByIdDesc(
                    OFFERING_CONFIG_CODE, userId.trim());
        } else if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            rows = instanceRepository.findTop50ByOntologyCodeAndStatusOrderByIdDesc(
                    OFFERING_CONFIG_CODE, status.trim());
        } else {
            rows = instanceRepository.findTop50ByOntologyCodeOrderByIdDesc(OFFERING_CONFIG_CODE);
        }
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)
                && (sessionId != null && !sessionId.isBlank() || userId != null && !userId.isBlank())) {
            String st = status.trim();
            rows = rows.stream().filter(r -> st.equalsIgnoreCase(r.getStatus())).collect(Collectors.toList());
        }
        List<Map<String, Object>> items = rows.stream().map(this::toDraftSummary).collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", items.size());
        body.put("items", items);
        return body;
    }

    public Map<String, Object> getConfigDraft(Long draftId) {
        OntologyInstance entity = instanceRepository.findByIdAndOntologyCode(draftId, OFFERING_CONFIG_CODE)
                .orElse(null);
        if (entity == null) {
            return Map.of("success", false, "message", "草稿不存在: " + draftId);
        }
        Map<String, Object> body = new LinkedHashMap<>(toDraftSummary(entity));
        body.put("success", true);
        body.put("draft", readDraftJson(entity));
        return body;
    }

    public Map<String, Object> deleteConfigDraft(Long draftId) {
        OntologyInstance entity = instanceRepository.findByIdAndOntologyCode(draftId, OFFERING_CONFIG_CODE)
                .orElse(null);
        if (entity == null) {
            return Map.of("success", false, "message", "草稿不存在: " + draftId);
        }
        instanceRepository.delete(entity);
        return Map.of("success", true, "message", "草稿已删除", "draftId", draftId);
    }

    /**
     * 智检通过后闭环：合规 → 沉淀本体 → 生成资费备案工单 → 草稿状态 filing。
     */
    public Map<String, Object> submitConfigDraft(Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        @SuppressWarnings("unchecked")
        Map<String, Object> draftInput = req.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) req.get("draft")
                : Map.of();
        Long draftId = parseLong(req.get("draftId") != null ? req.get("draftId") : req.get("draft_id"));
        Map<String, Object> draft = deepCopy(draftInput);
        if (draft.isEmpty() && draftId != null) {
            OntologyInstance existing = instanceRepository.findByIdAndOntologyCode(draftId, OFFERING_CONFIG_CODE)
                    .orElse(null);
            if (existing != null) {
                draft = readDraftJson(existing);
            }
        }
        if (draft.isEmpty()) {
            return Map.of("success", false, "message", "缺少可提交的配置草稿");
        }

        Map<String, Object> persistReq = new LinkedHashMap<>(req);
        persistReq.put("draft", draft);
        Map<String, Object> saved = saveConfigDraft(persistReq);
        Long persistedId = parseLong(saved.get("draftId"));

        Map<String, Object> compliance = checkCompliance(draft);
        if (!Boolean.TRUE.equals(compliance.get("compliancePass"))) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "合规未通过，拒绝提交备案");
            fail.put("issues", compliance.get("issues"));
            fail.put("compliancePass", false);
            fail.put("draftId", persistedId);
            return fail;
        }

        if (empty(draft.get("offeringId"))) {
            draft.put("offeringId", "OF-DRAFT-" + Instant.now().toEpochMilli());
        }
        Map<String, Object> published = publishConfigDraft(draft);
        if (!Boolean.TRUE.equals(published.get("success"))) {
            Map<String, Object> fail = new LinkedHashMap<>(published);
            fail.put("draftId", persistedId);
            return fail;
        }

        String offeringId = str(published.get("offeringId"));
        String offeringName = str(firstNonEmpty(draft.get("offeringName"), draft.get("offerName"), offeringId));
        Map<String, Object> woReq = new LinkedHashMap<>();
        woReq.put("offeringId", offeringId);
        woReq.put("offeringName", offeringName);
        woReq.put("source", "rd_filing");
        woReq.put("title", offeringName + "资费备案申请");
        woReq.put("summary", "研发助手合规通过后自动发起备案：月费="
                + firstNonEmpty(draft.get("monthlyFee"), draft.get("fixedFeeAmount"), "-")
                + "，场景=" + firstNonEmpty(draft.get("bizScenario"), "-"));
        woReq.put("actions", List.of(
                "提交资费备案申请",
                "初审复核字段完整性",
                "通过后更新产商品状态为待上线/在售"
        ));
        Map<String, Object> woBody = createWorkOrder(woReq);
        @SuppressWarnings("unchecked")
        Map<String, Object> workOrder = woBody.get("workOrder") instanceof Map<?, ?>
                ? (Map<String, Object>) woBody.get("workOrder")
                : Map.of();

        final Map<String, Object> draftSnapshot = deepCopy(draft);
        final String workOrderId = str(workOrder.get("workOrderId"));
        if (persistedId != null) {
            instanceRepository.findByIdAndOntologyCode(persistedId, OFFERING_CONFIG_CODE).ifPresent(entity -> {
                entity.setStatus("filing");
                entity.setSubmittedAt(LocalDateTime.now());
                Map<String, Object> store = new LinkedHashMap<>();
                if (entity.getData() != null) {
                    entity.getData().forEach(store::put);
                }
                store.put("offeringId", offeringId);
                store.put("workOrderId", workOrderId);
                store.put("compliancePass", "true");
                try {
                    store.put(DRAFT_JSON_KEY, objectMapper.writeValueAsString(draftSnapshot));
                } catch (Exception ignored) {
                    // keep previous json
                }
                entity.setData(store);
                instanceRepository.save(entity);
            });
        }

        String traceId = "cfg-submit-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "submit_filing",
                "offering_id", offeringId,
                "draft_id", persistedId == null ? "" : String.valueOf(persistedId),
                "work_order_id", str(workOrder.get("workOrderId")),
                "publish_trace", str(published.get("trace_id")),
                "timestamp", Instant.now().toString()
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "已提交：合规通过 → 沉淀本体 → 生成资费备案工单");
        body.put("draftId", persistedId);
        body.put("offeringId", offeringId);
        body.put("workOrder", workOrder);
        body.put("published", published);
        body.put("compliancePass", true);
        body.put("status", "filing");
        body.put("trace_id", traceId);
        return body;
    }

    /**
     * 多方案对比：对基础草稿应用资费/字段补丁，逐案合规 + 粗算收益，输出可解释推荐。
     */
    public Map<String, Object> compareConfigSchemes(Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        @SuppressWarnings("unchecked")
        Map<String, Object> baseDraft = req.get("draft") instanceof Map<?, ?>
                ? deepCopy((Map<String, Object>) req.get("draft"))
                : new LinkedHashMap<>();
        if (baseDraft.isEmpty()) {
            baseDraft.put("offeringName", "候选方案");
            baseDraft.put("offeringType", "main_pkg");
            baseDraft.put("bizScenario", "个人5G");
            baseDraft.put("targetUser", "个人客户");
            baseDraft.put("channelScope", "全渠道");
            baseDraft.put("hasContract", "1");
            baseDraft.put("contractMonths", 12);
            baseDraft.put("repeatable", "false");
            baseDraft.put("mutexGroup", "MAIN_PKG");
        }

        List<Map<String, Object>> patches = new ArrayList<>();
        if (req.get("patches") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    patches.add(row);
                }
            }
        }
        if (patches.isEmpty() && req.get("fees") instanceof List<?> fees) {
            int idx = 0;
            for (Object fee : fees) {
                idx++;
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("description", "方案" + (char) ('A' + idx - 1) + "：" + fee + "元");
                p.put("changes", Map.of("monthlyFee", fee, "fixedFeeAmount", fee));
                patches.add(p);
            }
        }
        String text = str(req.getOrDefault("text", req.get("question")));
        if (patches.isEmpty() && !text.isBlank()) {
            Matcher pm = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元").matcher(text);
            List<Double> fees = new ArrayList<>();
            while (pm.find()) {
                fees.add(Double.parseDouble(pm.group(1)));
            }
            int idx = 0;
            for (Double fee : fees) {
                idx++;
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("description", "方案" + (char) ('A' + idx - 1) + "：" + fee.intValue() + "元");
                p.put("changes", Map.of("monthlyFee", fee, "fixedFeeAmount", fee));
                patches.add(p);
            }
        }
        if (patches.isEmpty()) {
            patches.add(Map.of(
                    "description", "方案A：39元",
                    "changes", Map.of("monthlyFee", 39, "fixedFeeAmount", 39)));
            patches.add(Map.of(
                    "description", "方案B：59元",
                    "changes", Map.of("monthlyFee", 59, "fixedFeeAmount", 59)));
        }

        double marketScale = num(req.get("marketScale"), 150000);
        Matcher mm = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*万").matcher(text);
        if (mm.find()) {
            marketScale = Double.parseDouble(mm.group(1)) * 10000;
        }

        List<Map<String, Object>> comparisons = new ArrayList<>();
        int rank = 0;
        for (Map<String, Object> patch : patches) {
            rank++;
            Map<String, Object> variant = deepCopy(baseDraft);
            @SuppressWarnings("unchecked")
            Map<String, Object> changes = patch.get("changes") instanceof Map<?, ?>
                    ? (Map<String, Object>) patch.get("changes")
                    : Map.of();
            changes.forEach(variant::put);
            if (empty(variant.get("offeringName"))) {
                variant.put("offeringName", "候选方案" + rank);
            } else if (patches.size() > 1) {
                variant.put("offeringName",
                        str(baseDraft.getOrDefault("offeringName", "候选方案")) + "-方案" + (char) ('A' + rank - 1));
            }
            Map<String, Object> compliance = checkCompliance(variant);
            double fee = resolveFixedFee(variant);
            if (fee < 0) {
                fee = num(variant.get("monthlyFee"), 0);
            }
            // 粗算：转化率随价格下降，年营收 = 市场规模 * 转化率 * 月费 * 12
            double conv = Math.max(0.02, Math.min(0.12, 0.10 - fee / 1000.0));
            double annualRevenue = marketScale * conv * Math.max(fee, 0) * 12;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", firstNonEmpty(patch.get("description"), "方案" + rank));
            row.put("draft", variant);
            row.put("monthlyFee", fee);
            row.put("compliancePass", compliance.get("compliancePass"));
            row.put("issues", compliance.get("issues"));
            row.put("appliedRules", compliance.get("appliedRules"));
            row.put("conversionRate", Math.round(conv * 1000) / 1000.0);
            row.put("estimatedAnnualRevenue", Math.round(annualRevenue));
            row.put("marketScale", marketScale);
            row.put("verdict", Boolean.TRUE.equals(compliance.get("compliancePass")) ? "allow" : "deny");
            comparisons.add(row);
        }

        comparisons.sort((a, b) -> {
            boolean ap = Boolean.TRUE.equals(a.get("compliancePass"));
            boolean bp = Boolean.TRUE.equals(b.get("compliancePass"));
            if (ap != bp) {
                return ap ? -1 : 1;
            }
            return Double.compare(num(b.get("estimatedAnnualRevenue"), 0), num(a.get("estimatedAnnualRevenue"), 0));
        });

        Map<String, Object> recommended = comparisons.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("compliancePass")))
                .findFirst()
                .orElse(comparisons.isEmpty() ? Map.of() : comparisons.get(0));

        StringBuilder explanation = new StringBuilder();
        explanation.append("多方案对比说明：\n");
        explanation.append("- 市场规模估算：").append((long) marketScale).append(" 户\n");
        for (Map<String, Object> c : comparisons) {
            explanation.append("- ").append(c.get("label"))
                    .append("：月费 ").append(c.get("monthlyFee"))
                    .append("，合规=").append(c.get("compliancePass"))
                    .append("，预估年营收 ").append(c.get("estimatedAnnualRevenue"))
                    .append("，转化率 ").append(c.get("conversionRate"))
                    .append('\n');
        }
        if (!recommended.isEmpty()) {
            explanation.append("\n推荐：").append(recommended.get("label"))
                    .append(Boolean.TRUE.equals(recommended.get("compliancePass"))
                            ? "（合规通过且预期收益更优）"
                            : "（相对较优，但仍需修正合规项）");
        }

        String traceId = "cfg-compare-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "compare_state",
                "variants", comparisons.size(),
                "recommended", str(recommended.get("label")),
                "timestamp", Instant.now().toString()
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("comparisons", comparisons);
        body.put("recommended", recommended);
        body.put("explanation", explanation.toString().trim());
        body.put("trace_id", traceId);
        body.put("marketScale", marketScale);
        return body;
    }

    private OntologyInstance findDraftByClientId(String clientId, String sessionId, String userId) {
        List<OntologyInstance> candidates;
        if (sessionId != null && !sessionId.isBlank()) {
            candidates = instanceRepository.findTop50ByOntologyCodeAndSessionIdOrderByIdDesc(
                    OFFERING_CONFIG_CODE, sessionId);
        } else if (userId != null && !userId.isBlank()) {
            candidates = instanceRepository.findTop50ByOntologyCodeAndUserIdOrderByIdDesc(
                    OFFERING_CONFIG_CODE, userId);
        } else {
            candidates = instanceRepository.findTop50ByOntologyCodeOrderByIdDesc(OFFERING_CONFIG_CODE);
        }
        for (OntologyInstance row : candidates) {
            if (clientId.equals(row.getData() == null ? null : row.getData().get(CLIENT_ID_KEY))) {
                return row;
            }
        }
        return null;
    }

    private Map<String, Object> toDraftSummary(OntologyInstance entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("draftId", entity.getId());
        row.put("status", entity.getStatus());
        row.put("sessionId", entity.getSessionId());
        row.put("userId", entity.getUserId());
        row.put("submittedAt", entity.getSubmittedAt() == null ? null : entity.getSubmittedAt().toString());
        Map<String, String> data = entity.getData() == null ? Map.of() : entity.getData();
        row.put("clientId", data.get(CLIENT_ID_KEY));
        row.put("offeringName", data.getOrDefault("offeringName", ""));
        row.put("monthlyFee", data.getOrDefault("monthlyFee", ""));
        row.put("bizScenario", data.getOrDefault("bizScenario", ""));
        row.put("channelScope", data.getOrDefault("channelScope", ""));
        row.put("compliancePass", "true".equalsIgnoreCase(data.get("compliancePass")));
        row.put("offeringId", data.get("offeringId"));
        row.put("workOrderId", data.get("workOrderId"));
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDraftJson(OntologyInstance entity) {
        if (entity.getData() == null) {
            return new LinkedHashMap<>();
        }
        String json = entity.getData().get(DRAFT_JSON_KEY);
        if (json == null || json.isBlank()) {
            Map<String, Object> flat = new LinkedHashMap<>();
            entity.getData().forEach((k, v) -> {
                if (!DRAFT_JSON_KEY.equals(k) && !CLIENT_ID_KEY.equals(k)) {
                    flat.put(k, v);
                }
            });
            return flat;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("parse draft json failed id={}: {}", entity.getId(), e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 智读抽取置信度：关键字段齐全度 + 原文片段 + 合规结果。 */
    private double estimateExtractConfidence(Map<String, Object> slots, Map<String, Object> draft, boolean pass) {
        double score = 0.35;
        if (!empty(slots.get("sourceExcerpt")) || !empty(draft.get("sourceExcerpt"))) {
            score += 0.15;
        }
        String[] keys = {"offeringName", "offerName", "monthlyFee", "fixedFeeAmount", "targetUser", "channelScope"};
        int hit = 0;
        for (String k : keys) {
            if (!empty(draft.get(k)) || !empty(slots.get(k))) {
                hit++;
            }
        }
        score += Math.min(0.35, hit * 0.06);
        if (pass) {
            score += 0.15;
        }
        return Math.round(Math.min(0.99, score) * 100.0) / 100.0;
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
        String rootKey = str(offering.get("messageRootKey")).toLowerCase(Locale.ROOT);
        String catCode = str(offering.get("categoryCode")).toLowerCase(Locale.ROOT);
        String catName = str(offering.get("categoryName")).toLowerCase(Locale.ROOT);
        String productLine = str(offering.get("productLine")).toLowerCase(Locale.ROOT);
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
        if (!rootKey.isBlank() && (q.contains(rootKey) || rootKey.contains(q))) {
            score += 35;
        }
        if (!catName.isBlank() && (q.contains(catName) || catName.contains(q))) {
            score += 30;
        }
        for (String token : q.split("[\\s,，、]+")) {
            if (token.length() < 2) {
                continue;
            }
            if (name.contains(token) || id.contains(token) || rootKey.contains(token)
                    || catCode.contains(token) || catName.contains(token) || productLine.contains(token)) {
                score += 15;
            }
            if (("校园".equals(token) || "学生".equals(token) || "大学".equals(token))
                    && (name.contains("校园") || name.contains("青春") || name.contains("学生")
                    || "personaddprc".equals(rootKey))) {
                score += 25;
            }
            if (("风险".equals(token) || "零资费".equals(token) || "低效".equals(token))
                    && isRiskishOffering(offering)) {
                score += 35;
            }
            if (("5g".equals(token) || "套餐".equals(token)) && (name.contains("5g") || name.contains("畅享")
                    || "personmainprc".equals(rootKey))) {
                score += 10;
            }
            if (("家庭".equals(token) || "融合".equals(token)) && (name.contains("家庭") || name.contains("融合")
                    || rootKey.startsWith("family"))) {
                score += 20;
            }
            if (("宽带".equals(token) || "提速".equals(token)) && (name.contains("宽带") || name.contains("提速")
                    || rootKey.contains("broadband"))) {
                score += 20;
            }
            if (token.matches("\\d+") && (str(offering.get("monthlyFee")).contains(token)
                    || str(offering.get("fixedFeeAmount")).contains(token))) {
                score += 30;
            }
        }
        if (q.contains("在售") || q.contains("在架") || q.contains("上线")) {
            if ("上架".equals(str(offering.get("state")))) {
                score += 5;
            }
        }
        if (score == 0 && (cat.contains(q) || type.contains(q) || rootKey.contains(q))) {
            score = 5;
        }
        return score;
    }

    private Map<String, Object> toQueryCard(Map<String, Object> o, int score) {
        Map<String, Object> row = new LinkedHashMap<>();
        String id = str(o.get("offeringId"));
        Object fee = firstNonEmpty(o.get("fixedFeeAmount"), o.get("monthlyFee"));
        row.put("id", id);
        row.put("code", id);
        row.put("name", o.get("offeringName"));
        row.put("offeringId", id);
        row.put("offering_id", id);
        row.put("monthlyFee", fee);
        row.put("fixedFeeAmount", fee);
        row.put("state", o.get("state"));
        row.put("category", o.get("category"));
        row.put("messageRootKey", o.get("messageRootKey"));
        row.put("categoryCode", o.get("categoryCode"));
        row.put("categoryName", o.get("categoryName"));
        row.put("productLine", o.get("productLine"));
        row.put("score", score);
        row.put("desc", "固费" + fee + "元 | " + o.get("state")
                + " | " + firstNonEmpty(o.get("categoryName"), o.get("messageRootKey"), o.get("offeringType")));
        row.put("template", o.get("basedOnTemplate"));
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
        // P3-5 ① 配置链路落盘表 B（config 域，回读覆盖内存；表不可用不阻断）
        try {
            versionService.recordLog(OntologyVersionService.DOMAIN_CONFIG, traceId,
                    "config_step", step);
        } catch (RuntimeException e) {
            log.warn("[审计落盘] config trace {} 落盘失败（不影响链路）: {}", traceId, e.getMessage());
        }
    }

    public Map<String, Object> chatConfigure(String text, Map<String, Object> draft) {
        OpsExtractionService.SlotExtractResult extracted = extractionService.extractSlots(text == null ? "" : text);
        Map<String, Object> slots = extracted.slots();
        Map<String, Object> infer = deriveEngine.derive(slots, draft, loadGraph());
        @SuppressWarnings("unchecked")
        Map<String, Object> inferredDraft = (Map<String, Object>) infer.get("draft");
        Map<String, Object> compliance = checkCompliance(inferredDraft);

        Set<String> applied = new LinkedHashSet<>();
        castList(infer.get("appliedRules")).forEach(r -> applied.add(str(r)));
        castList(compliance.get("appliedRules")).forEach(r -> applied.add(str(r)));

        Map<String, Object> messagePreview = messageProjector.toMessage(inferredDraft);

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
        body.put("messageRootKey", inferredDraft == null ? null : inferredDraft.get("messageRootKey"));
        body.put("messagePreview", messagePreview);
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
            Map<String, Object> infer = deriveEngine.derive(slots, null, graph);
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
            double confidence = estimateExtractConfidence(slots, draft, pass);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", idx + 1);
            item.put("sourceExcerpt", slots.getOrDefault("sourceExcerpt", ""));
            item.put("draft", draft);
            item.put("inferredFields", infer.get("inferredFields"));
            item.put("issues", compliance.get("issues"));
            item.put("compliancePass", pass);
            item.put("status", pass ? "通过" : "待修正");
            item.put("confidence", confidence);
            item.put("needsConfirm", confidence < 0.75 || !pass);
            item.put("appliedRules", applied.stream().sorted().collect(Collectors.toList()));
            item.put("messageRootKey", draft == null ? null : draft.get("messageRootKey"));
            item.put("categoryName", draft == null ? null : draft.get("categoryName"));
            item.put("messagePreview", draft == null ? Map.of() : messageProjector.toMessage(draft));
            items.add(item);
        }

        List<Map<String, Object>> passed = items.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("compliancePass")))
                .collect(Collectors.toList());
        List<Map<String, Object>> confirmable = passed.stream()
                .filter(i -> !Boolean.TRUE.equals(i.get("needsConfirm")) || num(i.get("confidence"), 0) >= 0.75)
                .map(i -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("index", i.get("index"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> draft = (Map<String, Object>) i.get("draft");
                    row.put("offeringName", draft == null ? null : draft.get("offeringName"));
                    row.put("confidence", i.get("confidence"));
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
        Map<String, Object> alertPack = listOpsAlerts(null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts = new ArrayList<>(
                (List<Map<String, Object>>) alertPack.getOrDefault("items", List.of()));

        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("id", "alert-risk");
        a2.put("type", "risk");
        a2.put("tag", "风险");
        a2.put("severity", "HIGH");
        a2.put("text", "高风险在架商品 " + risk.getOrDefault("highCount", 0) + " 个待处置");
        a2.put("actionText", "筛查所有在架的0元资费风险商品");
        a2.put("occurredAt", Instant.now().toString());
        alerts.add(a2);

        long anomalyCount = alerts.stream().filter(a -> "anomaly".equals(a.get("type"))).count();

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
        body.put("workOrderCount", listWorkOrders().get("total"));
        body.put("lastBatchAudit", lastBatchAuditSummary());
        return withModeMeta(body);
    }

    /**
     * 运营监控告警列表（异动为主，可按 offeringId 过滤）。
     */
    public Map<String, Object> listOpsAlerts(String offeringId) {
        List<Map<String, Object>> alerts = buildAnomalyAlerts();
        if (offeringId != null && !offeringId.isBlank()) {
            String oid = offeringId.trim();
            alerts = alerts.stream()
                    .filter(a -> oid.equals(str(a.get("offeringId"))))
                    .collect(Collectors.toList());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", alerts.size());
        body.put("items", alerts);
        body.put("generatedAt", Instant.now().toString());
        return withModeMeta(body);
    }

    /**
     * 生成处置工单：持久化到 DB，并回写内存事实图 dispositionStatus。
     */
    public Map<String, Object> createWorkOrder(Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        String offeringId = str(req.getOrDefault("offeringId", req.get("offering_id")));
        String source = str(req.getOrDefault("source", "manual"));
        String title = str(req.get("title"));
        String summary = str(req.getOrDefault("summary", req.getOrDefault("anomalySummary", "")));
        List<Object> actions = castList(req.get("actions")).stream()
                .map(this::str)
                .filter(s -> !s.isBlank())
                .map(s -> (Object) s)
                .collect(Collectors.toList());
        if (actions.isEmpty() && req.get("action") != null) {
            actions = List.of(str(req.get("action")));
        }

        Map<String, Object> offering = findShelfOffering(offeringId);
        String offeringName = (offering == null || offering.isEmpty())
                ? str(req.getOrDefault("offeringName", offeringId))
                : str(offering.getOrDefault("offeringName", offeringId));
        if (title.isBlank()) {
            title = offeringName + ("risk".equals(source) || source.contains("risk")
                    ? "风险处置工单" : "产品优化工单");
        }
        if (actions.isEmpty()) {
            actions = List.of("跟进处置", "同步渠道与产品运营复核");
        }

        String woId = "WO" + Instant.now().toEpochMilli();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (req.get("impacts") != null) {
            payload.put("impacts", req.get("impacts"));
        }
        if (req.get("rootCauses") != null) {
            payload.put("rootCauses", req.get("rootCauses"));
        }
        if (req.get("hypoMode") != null) {
            payload.put("hypoMode", req.get("hypoMode"));
        }

        OpsWorkOrder entity = new OpsWorkOrder();
        entity.setWorkOrderId(woId);
        entity.setTitle(title);
        entity.setOfferingId(offeringId);
        entity.setOfferingName(offeringName);
        entity.setSummary(summary.isBlank() ? title : summary);
        entity.setActions(actions);
        entity.setStatus("open");
        entity.setSource(source.isBlank() ? "ops_assistant" : source);
        entity.setHypoMode(str(req.get("hypoMode")));
        entity.setPayload(payload);
        OpsWorkOrder saved = workOrderRepository.save(entity);

        Map<String, Object> wo = toWorkOrderMap(saved);

        // 回写内存图（演示闭环可见）
        synchronized (this) {
            Map<String, Object> graph = loadGraph();
            List<Map<String, Object>> shelf = castListOfMaps(graph.get("shelfOfferings"));
            for (Map<String, Object> o : shelf) {
                if (offeringId.equals(str(o.get("offeringId")))) {
                    o.put("dispositionStatus", "work_order_open");
                    o.put("lastWorkOrderId", woId);
                    break;
                }
            }
            graph.put("shelfOfferings", shelf);
            List<Map<String, Object>> graphOrders = castListOfMaps(graph.get("workOrders"));
            graphOrders.add(0, new LinkedHashMap<>(wo));
            graph.put("workOrders", graphOrders);
            graphCache = graph;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "处置工单已持久化并回写本体事实");
        body.put("workOrder", wo);
        body.put("persisted", true);
        return withModeMeta(body);
    }

    public Map<String, Object> listWorkOrders() {
        return listWorkOrders(null);
    }

    public Map<String, Object> listWorkOrders(String status) {
        List<OpsWorkOrder> rows;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            rows = workOrderRepository.findTop50ByStatusOrderByCreatedAtDesc(status.trim().toLowerCase(Locale.ROOT));
        } else {
            rows = workOrderRepository.findTop50ByOrderByCreatedAtDesc();
        }
        List<Map<String, Object>> items = rows.stream().map(this::toWorkOrderMap).collect(Collectors.toList());
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("open", workOrderRepository.countByStatus("open"));
        counts.put("in_progress", workOrderRepository.countByStatus("in_progress"));
        counts.put("done", workOrderRepository.countByStatus("done"));
        counts.put("cancelled", workOrderRepository.countByStatus("cancelled"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", items.size());
        body.put("statusFilter", status == null || status.isBlank() ? "all" : status);
        body.put("counts", counts);
        body.put("items", items);
        return withModeMeta(body);
    }

    /**
     * 工单状态流转：open → in_progress → done / cancelled。
     * 完成后回写货架 dispositionStatus=work_order_done。
     */
    public Map<String, Object> updateWorkOrderStatus(String workOrderId, String status, String remark) {
        String wid = workOrderId == null ? "" : workOrderId.trim();
        String next = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        Set<String> allowed = Set.of("open", "in_progress", "done", "cancelled");
        if (wid.isBlank()) {
            return withModeMeta(Map.of("success", false, "message", "workOrderId 不能为空"));
        }
        if (!allowed.contains(next)) {
            return withModeMeta(Map.of(
                    "success", false,
                    "message", "非法状态，允许：open / in_progress / done / cancelled"
            ));
        }

        Optional<OpsWorkOrder> found = workOrderRepository.findByWorkOrderId(wid);
        if (found.isEmpty()) {
            return withModeMeta(Map.of("success", false, "message", "工单不存在: " + wid));
        }
        OpsWorkOrder entity = found.get();
        String prev = entity.getStatus();
        if (!isValidTransition(prev, next)) {
            return withModeMeta(Map.of(
                    "success", false,
                    "message", "不允许从 " + prev + " 流转到 " + next,
                    "workOrder", toWorkOrderMap(entity)
            ));
        }

        entity.setStatus(next);
        Map<String, Object> payload = entity.getPayload() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.getPayload());
        List<Object> history = castList(payload.get("statusHistory"));
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("from", prev);
        step.put("to", next);
        step.put("at", Instant.now().toString());
        if (remark != null && !remark.isBlank()) {
            step.put("remark", remark);
        }
        history = new ArrayList<>(history);
        history.add(step);
        payload.put("statusHistory", history);
        if (remark != null && !remark.isBlank()) {
            payload.put("lastRemark", remark);
        }
        entity.setPayload(payload);
        OpsWorkOrder saved = workOrderRepository.save(entity);

        syncWorkOrderToGraph(saved);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", statusLabel(next));
        body.put("workOrder", toWorkOrderMap(saved));
        body.put("previousStatus", prev);
        return withModeMeta(body);
    }

    private boolean isValidTransition(String from, String to) {
        if (from == null || from.isBlank()) {
            from = "open";
        }
        if (from.equals(to)) {
            return true;
        }
        return switch (from) {
            case "open" -> Set.of("in_progress", "cancelled", "done").contains(to);
            case "in_progress" -> Set.of("done", "cancelled", "open").contains(to);
            case "done", "cancelled" -> Set.of("open", "in_progress").contains(to); // 允许重开
            default -> true;
        };
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "open" -> "工单已重开/待处理";
            case "in_progress" -> "工单已进入处理中";
            case "done" -> "工单已完成，处置结果已回写本体";
            case "cancelled" -> "工单已取消";
            default -> "工单状态已更新";
        };
    }

    private void syncWorkOrderToGraph(OpsWorkOrder saved) {
        String offeringId = str(saved.getOfferingId());
        String woId = saved.getWorkOrderId();
        String st = saved.getStatus();
        String disposition = switch (st) {
            case "in_progress" -> "work_order_in_progress";
            case "done" -> "work_order_done";
            case "cancelled" -> "work_order_cancelled";
            default -> "work_order_open";
        };
        synchronized (this) {
            Map<String, Object> graph = loadGraph();
            List<Map<String, Object>> shelf = castListOfMaps(graph.get("shelfOfferings"));
            for (Map<String, Object> o : shelf) {
                if (offeringId.equals(str(o.get("offeringId")))) {
                    o.put("dispositionStatus", disposition);
                    o.put("lastWorkOrderId", woId);
                    o.put("lastWorkOrderStatus", st);
                    break;
                }
            }
            graph.put("shelfOfferings", shelf);
            List<Map<String, Object>> graphOrders = castListOfMaps(graph.get("workOrders"));
            boolean updated = false;
            for (int i = 0; i < graphOrders.size(); i++) {
                if (woId.equals(str(graphOrders.get(i).get("workOrderId")))) {
                    graphOrders.set(i, toWorkOrderMap(saved));
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                graphOrders.add(0, toWorkOrderMap(saved));
            }
            graph.put("workOrders", graphOrders);
            graphCache = graph;
        }
    }

    /**
     * 全量在售风险批量稽核（定时任务 / 手动触发）。
     */
    public Map<String, Object> runBatchRiskAudit(String trigger) {
        long start = System.currentTimeMillis();
        Map<String, Object> risk = auditRisks(null);
        Map<String, Object> alerts = listOpsAlerts(null);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("trigger", trigger == null || trigger.isBlank() ? "manual" : trigger);
        snapshot.put("auditedAt", Instant.now().toString());
        snapshot.put("elapsedMs", System.currentTimeMillis() - start);
        snapshot.put("scannedCount", risk.get("scannedCount"));
        snapshot.put("highCount", risk.get("highCount"));
        snapshot.put("mediumCount", risk.get("mediumCount"));
        snapshot.put("suggestDelistCount", risk.get("suggestDelistCount"));
        snapshot.put("totalRiskItems", risk.get("total"));
        snapshot.put("alertCount", alerts.get("total"));
        snapshot.put("ruleVersion", risk.get("ruleVersion"));
        snapshot.put("items", risk.get("items"));
        lastBatchAudit = snapshot;
        // P3-5 ① 批量稽核快照落盘表 B（batch 域，重启可回读最近一次；items 过大不入 detail）
        try {
            Map<String, Object> persisted = new LinkedHashMap<>(lastBatchAuditSummary());
            versionService.recordLog(OntologyVersionService.DOMAIN_BATCH, null, "batch_audit", persisted);
        } catch (RuntimeException e) {
            log.warn("[审计落盘] batch 稽核快照落盘失败（不影响结果）: {}", e.getMessage());
        }
        log.info("[OpsBatchAudit] trigger={} scanned={} high={} medium={} delist={} {}ms",
                snapshot.get("trigger"), snapshot.get("scannedCount"), snapshot.get("highCount"),
                snapshot.get("mediumCount"), snapshot.get("suggestDelistCount"), snapshot.get("elapsedMs"));

        Map<String, Object> body = new LinkedHashMap<>(snapshot);
        body.put("success", true);
        body.put("message", "批量风险稽核完成");
        body.put("alerts", alerts.get("items"));
        return withModeMeta(body);
    }

    public Map<String, Object> getLastBatchAudit() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        // P3-5 ① 优先表 B batch 域回读（重启不丢），空则回退内存态
        Map<String, Object> dbSnapshot = versionService.latestBatchAudit().orElse(null);
        boolean available = (dbSnapshot != null && !dbSnapshot.isEmpty())
                || (lastBatchAudit != null && !lastBatchAudit.isEmpty());
        if (!available) {
            body.put("available", false);
            body.put("message", "尚无批量稽核记录，可调用 POST /ops/batch-audit 触发");
            body.put("lastBatchAudit", Map.of());
        } else {
            body.put("available", true);
            if (dbSnapshot != null && !dbSnapshot.isEmpty()) {
                body.put("lastBatchAudit", dbSnapshot);
                body.put("source", "table_b");
                body.put("items", new ArrayList<>());
            } else {
                body.put("lastBatchAudit", lastBatchAuditSummary());
                body.put("source", "memory");
                body.put("items", lastBatchAudit.get("items"));
            }
        }
        return withModeMeta(body);
    }

    private Map<String, Object> lastBatchAuditSummary() {
        if (lastBatchAudit == null || lastBatchAudit.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>(lastBatchAudit);
        summary.remove("items");
        return summary;
    }

    private Map<String, Object> toWorkOrderMap(OpsWorkOrder e) {
        Map<String, Object> wo = new LinkedHashMap<>();
        wo.put("id", e.getId());
        wo.put("workOrderId", e.getWorkOrderId());
        wo.put("title", e.getTitle());
        wo.put("offeringId", e.getOfferingId());
        wo.put("offeringName", e.getOfferingName());
        wo.put("summary", e.getSummary());
        wo.put("actions", e.getActions() == null ? List.of() : e.getActions());
        wo.put("status", e.getStatus());
        wo.put("source", e.getSource());
        wo.put("hypoMode", e.getHypoMode());
        if (e.getPayload() != null) {
            wo.putAll(e.getPayload());
        }
        wo.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : Instant.now().toString());
        wo.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
        return wo;
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
                double delta = num(m.get("metricDelta"), Double.NaN);
                String detail;
                String severity = "MEDIUM";
                if (!Double.isNaN(delta)) {
                    long pct = Math.round(delta * 100);
                    detail = metric + "环比 " + pct + "%";
                    if (delta <= -0.15) {
                        severity = "HIGH";
                        detail = name + "当月" + metric + "环比下降" + Math.abs(pct) + "%";
                    } else if (delta < 0) {
                        detail = name + "当月" + metric + "环比下降" + Math.abs(pct) + "%";
                    }
                } else {
                    detail = metric + "异动 " + m.getOrDefault("metricDeltaPp", "") + "pp";
                }
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("id", "alert-" + oid + "-" + metric);
                alert.put("type", "anomaly");
                alert.put("tag", "异动");
                alert.put("severity", severity);
                alert.put("offeringId", oid);
                alert.put("offeringName", name);
                alert.put("metricCode", metric);
                if (!Double.isNaN(delta)) {
                    alert.put("metricDelta", delta);
                }
                alert.put("text", detail);
                alert.put("actionText", "分析" + name + "本月收入下滑原因");
                alert.put("occurredAt", Instant.now().toString());
                alert.put("status", "open");
                alerts.add(alert);
                break;
            }
        }
        return alerts;
    }

    public Map<String, Object> updateRiskRules(Map<String, Object> overrides) {
        Set<String> allowed = Set.of(
                "zeroSalesShelfDays", "zeroSalesDaysWindow",
                "highRiskReviewDays", "lowRevenuePercentile", "ruleVersion"
        );
        Map<String, Object> applied = riskAudit.apply(overrides, allowed);
        if (!applied.isEmpty()) {
            appendRiskRuleAudit("update", applied);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("riskRules", riskRules());
        body.putAll(riskRulesAdminView());
        return body;
    }

    public Map<String, Object> resetRiskRules() {
        Map<String, Object> before = riskAudit.clear();
        appendRiskRuleAudit("reset", Map.of("cleared", before));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("riskRules", riskRules());
        body.putAll(riskRulesAdminView());
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
        Map<String, Object> a03 = opsRules.rootCauseRule("R-A03");
        Map<String, Object> a04 = opsRules.rootCauseRule("R-A04");
        Map<String, Object> a05 = opsRules.rootCauseRule("R-A05");

        // R-A01~A05：按 ops_rules.engine 优先 Openllet SWRL；失败回退 Java
        boolean trySwrl = opsRules.preferSwrlAny("R-A01", "R-A02", "R-A03", "R-A04", "R-A05");
        OpsSwrlReasoner.SwrlFireResult swrl = trySwrl
                ? opsSwrlReasoner.reasonRootCause(oid, str(offering.get("offeringName")), node, a01, a02, a03, a04, a05)
                : OpsSwrlReasoner.SwrlFireResult.skipJava("规则配置为 java，跳过 SWRL");
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
                drill.put("trend", trend != null ? trend : List.of());
                c.put("drill", drill);
                candidates.add(c);
                triples.add(triple(oid, "soldOn", c.get("id")));
                triples.add(triple(c.get("id"), "orderDelta", orderDelta));
                triples.add(triple(c.get("id"), "contribRatio", contrib));
            }
            for (Map<String, Object> prCand : swrl.promotionCandidates()) {
                Map<String, Object> c = enrichPromoCandidate(oid, prCand);
                candidates.add(c);
                triples.add(triple(oid, "participatesIn", c.get("id")));
                triples.add(triple(c.get("id"), "daysToExpire", c.get("daysToExpire")));
                triples.add(triple(c.get("id"), "drivenOrderRatio", c.get("drivenOrderRatio")));
            }
            for (Map<String, Object> cpCand : swrl.competitorCandidates()) {
                Map<String, Object> c = enrichCompetitorCandidate(oid, cpCand);
                candidates.add(c);
                triples.add(triple(oid, "competesWith", c.get("id")));
                triples.add(triple(c.get("id"), "priceGap", c.get("priceGap")));
                triples.add(triple(c.get("id"), "penetrationDeltaPp", c.get("penetrationDeltaPp")));
            }
            for (Map<String, Object> ubCand : swrl.behaviorCandidates()) {
                Map<String, Object> c = enrichBehaviorCandidate(oid, ubCand);
                candidates.add(c);
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
                        drill.put("trend", trend != null ? trend : List.of());
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

        // Java 回退：仅当整次 SWRL 归因失败时补齐 A03~A05（成功时规则已在本体中求值）
        boolean swrlRootOk = swrl.success() && "openllet-swrl".equals(swrl.engine());
        if (!swrlRootOk && opsRules.isRuleEnabled(a03)) {
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
                    c.put("daysToExpire", days);
                    c.put("drivenOrderRatio", driven);
                    c.put("evidence", List.of(
                            days + " 日后到期",
                            "历史带动订购占比 " + Math.round(driven * 100) + "%"
                    ));
                    candidates.add(enrichPromoCandidate(oid, c));
                    triples.add(triple(oid, "participatesIn", pr.get("promoId")));
                    triples.add(triple(pr.get("promoId"), "daysToExpire", days));
                    triples.add(triple(pr.get("promoId"), "drivenOrderRatio", driven));
                }
            }
        }

        if (!swrlRootOk && opsRules.isRuleEnabled(a04)) {
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
                    c.put("engine", "java-rules");
                    c.put("priceGap", cp.get("priceGap"));
                    c.put("priceGapRatio", gapRatio);
                    c.put("penetrationDeltaPp", cp.get("penetrationDeltaPp"));
                    c.put("evidence", List.of(
                            "月费低 " + cp.get("priceGap") + " 元（约 " + String.format("%.1f", gapRatio * 100) + "%）",
                            "本地渗透率 +" + cp.get("penetrationDeltaPp") + "pp"
                    ));
                    candidates.add(enrichCompetitorCandidate(oid, c));
                    triples.add(triple(oid, "competesWith", cp.get("competitorId")));
                    triples.add(triple(cp.get("competitorId"), "priceGap", cp.get("priceGap")));
                    triples.add(triple(cp.get("competitorId"), "penetrationDeltaPp", cp.get("penetrationDeltaPp")));
                }
            }
        }

        if (!swrlRootOk && opsRules.isRuleEnabled(a05)) {
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
                c.put("engine", "java-rules");
                c.put("evidence", List.of(str(ub.get("name")), "行为佐证"));
                candidates.add(enrichBehaviorCandidate(oid, c));
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
        return auditRisksOn(loadGraph(), offeringIds);
    }

    /**
     * 假设评估：对货架副本打 patch（改价/下架）后重跑风险稽核，不污染持久图。
     *
     * @param patches [{offeringId, changes:{monthlyFee, state, ...}, description?}]
     * @param mode    delist | price | risk
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateHypothetical(List<Map<String, Object>> patches, String mode) {
        String safeMode = mode == null || mode.isBlank() ? "risk" : mode.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> original = loadGraph();
        Map<String, Object> mutated = deepCopy(original);
        List<Map<String, Object>> shelf = castListOfMaps(mutated.get("shelfOfferings"));
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> o : shelf) {
            byId.put(str(o.get("offeringId")), o);
        }

        List<String> targetIds = new ArrayList<>();
        List<Map<String, Object>> applied = new ArrayList<>();
        List<Map<String, Object>> workingPatches = new ArrayList<>();
        if (patches != null) {
            workingPatches.addAll(patches);
        }

        if (workingPatches.isEmpty()) {
            // 默认：对建议下架 / 高风险项做退市推演（最多 3 条）
            Map<String, Object> beforeScan = auditRisksOn(original, null);
            List<Map<String, Object>> items = castListOfMaps(beforeScan.get("items"));
            for (Map<String, Object> item : items) {
                if (Boolean.TRUE.equals(item.get("suggestDelist"))
                        || "HIGH".equals(str(item.get("riskLevel")))) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("offeringId", str(item.get("offeringId")));
                    p.put("description", "退市假设：" + item.get("offeringName"));
                    p.put("changes", Map.of("state", "下架"));
                    workingPatches.add(p);
                    if (workingPatches.size() >= 3) {
                        break;
                    }
                }
            }
        }

        for (Map<String, Object> patch : workingPatches) {
            String oid = str(patch.getOrDefault("offeringId",
                    patch.getOrDefault("entity_id", patch.get("entityId"))));
            if (oid.isBlank()) {
                continue;
            }
            Map<String, Object> offering = byId.get(oid);
            if (offering == null) {
                continue;
            }
            Map<String, Object> beforeRow = new LinkedHashMap<>(offering);
            Map<String, Object> changes = castMap(patch.get("changes"));
            if (changes.isEmpty() && ("delist".equals(safeMode) || "退市".equals(safeMode))) {
                changes = Map.of("state", "下架");
            }
            if (changes.isEmpty() && "price".equals(safeMode)) {
                double fee = num(offering.get("monthlyFee"), 0);
                changes = Map.of("monthlyFee", fee <= 0 ? 19 : fee);
            }
            offering.putAll(changes);
            // 下架后不再计入在架稽核：标记 state
            if ("下架".equals(str(offering.get("state"))) || "停售".equals(str(offering.get("state")))) {
                offering.put("state", "下架");
            }
            targetIds.add(oid);
            Map<String, Object> appliedRow = new LinkedHashMap<>();
            appliedRow.put("offeringId", oid);
            appliedRow.put("offeringName", offering.get("offeringName"));
            appliedRow.put("description", patch.getOrDefault("description",
                    "delist".equals(safeMode) ? "退市假设" : "资费/状态假设"));
            appliedRow.put("before", Map.of(
                    "state", beforeRow.get("state"),
                    "monthlyFee", beforeRow.get("monthlyFee"),
                    "revenue30d", beforeRow.get("revenue30d"),
                    "salesCnt30d", beforeRow.get("salesCnt30d")
            ));
            appliedRow.put("after", Map.of(
                    "state", offering.get("state"),
                    "monthlyFee", offering.get("monthlyFee"),
                    "revenue30d", offering.get("revenue30d"),
                    "salesCnt30d", offering.get("salesCnt30d")
            ));
            applied.add(appliedRow);
        }

        mutated.put("shelfOfferings", shelf);

        Map<String, Object> before = auditRisksOn(original, targetIds.isEmpty() ? null : targetIds);
        // 假设后：对全量扫描，但影响摘要聚焦 targetIds；下架商品从「在架」稽核中排除
        Map<String, Object> afterGraph = deepCopy(mutated);
        List<Map<String, Object>> afterShelf = castListOfMaps(afterGraph.get("shelfOfferings"));
        afterShelf.removeIf(o -> {
            String st = str(o.get("state"));
            return "下架".equals(st) || "停售".equals(st) || "已退市".equals(st);
        });
        afterGraph.put("shelfOfferings", afterShelf);
        Map<String, Object> after = auditRisksOn(afterGraph, null);

        List<Map<String, Object>> impacts = new ArrayList<>();
        for (Map<String, Object> row : applied) {
            Map<String, Object> beforeSnap = castMap(row.get("before"));
            double revenue = num(beforeSnap.get("revenue30d"), 0);
            double sales = num(beforeSnap.get("salesCnt30d"), 0);
            Map<String, Object> impact = new LinkedHashMap<>();
            impact.put("offeringId", row.get("offeringId"));
            impact.put("offeringName", row.get("offeringName"));
            impact.put("revenueImpact30d", -revenue);
            impact.put("salesImpact30d", -sales);
            impact.put("userMigrationHint", sales <= 0
                    ? "近30日无新增，迁转压力小"
                    : "涉及近30日办理约" + (long) sales + "笔，建议平滑迁转至低价在售套餐");
            impact.put("conclusion", revenue < 5000
                    ? "营收影响极小，建议启动退市并做好用户引导"
                    : "营收有一定影响，建议先调价/限售再评估退市");
            impacts.add(impact);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("mode", safeMode);
        body.put("patchesApplied", applied);
        body.put("before", Map.of(
                "total", before.get("total"),
                "highCount", before.get("highCount"),
                "mediumCount", before.get("mediumCount"),
                "suggestDelistCount", before.get("suggestDelistCount"),
                "items", before.get("items")
        ));
        body.put("after", Map.of(
                "total", after.get("total"),
                "highCount", after.get("highCount"),
                "mediumCount", after.get("mediumCount"),
                "suggestDelistCount", after.get("suggestDelistCount"),
                "scannedCount", after.get("scannedCount"),
                "items", after.get("items")
        ));
        body.put("impacts", impacts);
        body.put("summary", buildHypotheticalSummary(safeMode, before, after, impacts));
        body.put("evaluatedAt", Instant.now().toString());
        return withModeMeta(body);
    }

    private String buildHypotheticalSummary(String mode, Map<String, Object> before,
                                            Map<String, Object> after, List<Map<String, Object>> impacts) {
        long beforeHigh = ((Number) before.getOrDefault("highCount", 0)).longValue();
        long afterHigh = ((Number) after.getOrDefault("highCount", 0)).longValue();
        double revenueDrop = impacts.stream()
                .mapToDouble(i -> Math.abs(num(i.get("revenueImpact30d"), 0)))
                .sum();
        StringBuilder sb = new StringBuilder();
        if ("delist".equals(mode) || mode.contains("退市")) {
            sb.append("退市推演：高风险项 ").append(beforeHigh).append(" → ").append(afterHigh);
            sb.append("；预估月营收影响约 ").append(String.format("%.0f", revenueDrop)).append(" 元。");
        } else if ("price".equals(mode) || mode.contains("改价")) {
            sb.append("改价推演：风险命中 ").append(before.get("total"))
                    .append(" → ").append(after.get("total")).append("。");
        } else {
            sb.append("假设评估完成：风险项 ").append(before.get("total"))
                    .append(" → ").append(after.get("total")).append("。");
        }
        if (!impacts.isEmpty()) {
            sb.append(impacts.get(0).get("conclusion"));
        }
        return sb.toString();
    }

    private Map<String, Object> auditRisksOn(Map<String, Object> graph, List<String> offeringIds) {
        Map<String, Object> rules = riskRules();
        List<Map<String, Object>> allOfferings = castListOfMaps(graph.get("shelfOfferings"));
        // 仅稽核在架（上架）商品；已下架不计入
        List<Map<String, Object>> onShelf = allOfferings.stream()
                .filter(o -> {
                    String st = str(o.get("state"));
                    return st.isBlank() || "上架".equals(st) || "在售".equals(st);
                })
                .collect(Collectors.toList());
        int scannedCount = onShelf.size();
        List<Map<String, Object>> offerings = onShelf;
        if (offeringIds != null && !offeringIds.isEmpty()) {
            Set<String> idSet = new LinkedHashSet<>(offeringIds);
            offerings = onShelf.stream()
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

        List<Double> allRevenues = onShelf.stream()
                .map(o -> num(o.get("revenue30d"), 0))
                .sorted()
                .collect(Collectors.toList());
        int cutoffIdx = allRevenues.isEmpty() ? 0 : Math.max(0, (int) (allRevenues.size() * lowPct) - 1);
        double lowThreshold = allRevenues.isEmpty() ? 0 : allRevenues.get(cutoffIdx);

        List<Map<String, Object>> results = new ArrayList<>();
        boolean tryRiskSwrl = opsRules.preferSwrlAny("R-B01", "R-B02", "R-B03", "R-B04", "R-B05");
        Map<String, Object> b01 = opsRules.riskRule("R-B01");
        Map<String, Object> b02 = opsRules.riskRule("R-B02");
        Map<String, Object> b03 = opsRules.riskRule("R-B03");
        Map<String, Object> b04 = opsRules.riskRule("R-B04");
        Map<String, Object> b05 = opsRules.riskRule("R-B05");
        Set<String> lowCats = opsRules.riskCategories("R-B04", Set.of("low_eff"));
        String riskEngine = tryRiskSwrl ? "openllet-swrl" : "java-rules";
        int swrlOk = 0;
        int swrlFail = 0;

        for (Map<String, Object> o : offerings) {
            List<Map<String, Object>> risks = new ArrayList<>();
            String riskLevel = "LOW";
            List<String> actions = new ArrayList<>();
            List<Map<String, Object>> evidenceTriples = new ArrayList<>();
            boolean suggestDelist = false;
            boolean urgent = false;
            boolean usedSwrl = false;

            double monthly = num(o.get("monthlyFee"), -1);
            double oneTime = num(o.get("oneTimeFee"), 0);
            String wlTag = str(o.get("whitelistTag"));
            String name = str(o.get("offeringName"));
            boolean inWhitelist = whitelist.contains(wlTag)
                    || whitelist.stream().anyMatch(name::contains);
            String state = str(o.get("state"));
            boolean offeringOnShelf = state.isBlank() || "上架".equals(state) || "在售".equals(state);
            String category = str(o.get("category"));
            boolean lowEffCategory = lowCats.contains(category);
            boolean lowRevenue = num(o.get("revenue30d"), 0) <= lowThreshold;

            if (tryRiskSwrl) {
                Map<String, Object> flags = new LinkedHashMap<>();
                flags.put("inWhitelist", inWhitelist);
                flags.put("onShelf", offeringOnShelf);
                flags.put("lowEffCategoryFlag", lowEffCategory);
                flags.put("lowRevenueFlag", lowRevenue);
                OpsSwrlReasoner.SwrlRiskResult rr = opsSwrlReasoner.reasonRiskOffering(
                        o, flags, b01, b02, b03, b04, b05, rules);
                if (rr.success() && "openllet-swrl".equals(rr.engine())) {
                    usedSwrl = true;
                    swrlOk++;
                    risks.addAll(rr.risks());
                    riskLevel = rr.riskLevel();
                    suggestDelist = rr.suggestDelist();
                    urgent = rr.urgent();
                    for (Map<String, Object> risk : risks) {
                        String feature = str(risk.get("feature"));
                        // 与 Java 路径一致：零元资费处置话术挂在 B02（无合约在架）上
                        if ("零元资费".equals(feature)
                                && risks.stream().noneMatch(r -> "R-B02".equals(str(r.get("ruleId"))))) {
                            continue;
                        }
                        Map<String, Object> act = castMap(riskActions.get(feature));
                        if (!act.isEmpty()) {
                            actions.add(str(act.getOrDefault("defaultAction", feature)));
                        } else if ("预警升级".equals(feature)) {
                            actions.add("紧急复核");
                        }
                    }
                    if (risks.stream().anyMatch(r -> "R-B01".equals(str(r.get("ruleId")))
                            && "零元资费".equals(str(r.get("feature"))))) {
                        evidenceTriples.add(triple(o.get("offeringId"), "hasPricePlan", "PP-" + o.get("offeringId")));
                        evidenceTriples.add(triple("PP-" + o.get("offeringId"), "monthlyFee", 0));
                        evidenceTriples.add(triple("PP-" + o.get("offeringId"), "oneTimeFee", 0));
                    }
                    if (risks.stream().anyMatch(r -> "R-B02".equals(str(r.get("ruleId"))))) {
                        evidenceTriples.add(triple(o.get("offeringId"), "hasContract", false));
                    }
                    if (risks.stream().anyMatch(r -> "R-B03".equals(str(r.get("ruleId"))))) {
                        evidenceTriples.add(triple(o.get("offeringId"), "salesCnt30d", 0));
                        evidenceTriples.add(triple(o.get("offeringId"), "shelfDays", o.get("shelfDays")));
                    }
                } else {
                    swrlFail++;
                }
            }

            if (!usedSwrl) {
                if (opsRules.isRuleEnabled(b01)
                        && monthly == 0 && oneTime == 0 && !inWhitelist) {
                    risks.add(riskFeature("R-B01", "零元资费", "月费与一次性费均为0且非权益赠送白名单"));
                    evidenceTriples.add(triple(o.get("offeringId"), "hasPricePlan", "PP-" + o.get("offeringId")));
                    evidenceTriples.add(triple("PP-" + o.get("offeringId"), "monthlyFee", 0));
                    evidenceTriples.add(triple("PP-" + o.get("offeringId"), "oneTimeFee", 0));
                    if (opsRules.isRuleEnabled(b02)
                            && offeringOnShelf
                            && !truthy(o.get("hasContract"))) {
                        risks.add(riskFeature("R-B02", "零元无合约在架", "零元资费已上架且无合约约束"));
                        evidenceTriples.add(triple(o.get("offeringId"), "hasContract", false));
                        riskLevel = "HIGH";
                        Map<String, Object> act = castMap(riskActions.get("零元资费"));
                        actions.add(str(act.getOrDefault("defaultAction", "建议立即下架或转验证渠道")));
                    }
                }

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

                if (opsRules.isRuleEnabled(b03)
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

                if (opsRules.isRuleEnabled(b04)
                        && lowEffCategory
                        && lowRevenue
                        && !truthy(o.get("strategicTag"))) {
                    risks.add(riskFeature("R-B04", "低效产商品", "近90日收入贡献排名后5%且无战略标签"));
                    if ("LOW".equals(riskLevel)) {
                        riskLevel = "MEDIUM";
                    }
                    Map<String, Object> act = castMap(riskActions.get("低效产商品"));
                    actions.add(str(act.getOrDefault("defaultAction", "纳入优胜劣汰池")));
                    suggestDelist = true;
                }

                if (opsRules.isRuleEnabled(b05)
                        && "HIGH".equals(riskLevel) && num(o.get("shelfDays"), 0) > reviewDays) {
                    risks.add(riskFeature("R-B05", "预警升级", "高风险且上架超过" + reviewDays + "天未复核"));
                    actions.add("紧急复核");
                    urgent = true;
                }
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
                row.put("reasonEngine", usedSwrl ? "openllet-swrl" : "java-rules");
                Map<String, Object> disposition = new LinkedHashMap<>();
                disposition.put("defaultAction", uniqueActions.isEmpty() ? "关注" : uniqueActions.get(0));
                disposition.put("needConfirm", "HIGH".equals(riskLevel));
                row.put("disposition", disposition);
                results.add(row);
            }
        }

        if (tryRiskSwrl && swrlFail > 0 && swrlOk == 0) {
            riskEngine = "fallback-java";
        } else if (tryRiskSwrl && swrlFail > 0) {
            riskEngine = "openllet-swrl+java-fallback";
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
        body.put("reasonEngine", riskEngine);
        body.put("swrlOkCount", swrlOk);
        body.put("swrlFailCount", swrlFail);
        body.put("auditedAt", Instant.now().toString());
        return withModeMeta(body);
    }

    private Map<String, Object> enrichPromoCandidate(String oid, Map<String, Object> c) {
        Map<String, Object> out = new LinkedHashMap<>(c);
        out.putIfAbsent("path", List.of(
                oid + "-participatesIn->" + c.get("id"),
                c.get("id") + "-daysToExpire->" + c.get("daysToExpire")
        ));
        return out;
    }

    private Map<String, Object> enrichCompetitorCandidate(String oid, Map<String, Object> c) {
        Map<String, Object> out = new LinkedHashMap<>(c);
        out.putIfAbsent("path", List.of(
                oid + "-competesWith->" + c.get("id"),
                c.get("id") + "-priceGapRatio->" + c.get("priceGapRatio")
        ));
        return out;
    }

    private Map<String, Object> enrichBehaviorCandidate(String oid, Map<String, Object> c) {
        Map<String, Object> out = new LinkedHashMap<>(c);
        out.putIfAbsent("path", List.of(oid + "-influencedBy->" + c.get("id")));
        return out;
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
        return riskAudit.effective(fromGraph, fromFile);
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

    /**
     * 按品类选择配置模板：家庭附加用 TPL-FAMILY-ADD-20，避免套用家庭基础 128 主套餐模板。
     */
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

    /**
     * 按当前草稿生成打印/短信话术，避免沿用校园/个人样例消息。
     */
    private void enrichSceneNotices(Map<String, Object> draft, Map<String, String> fillSources) {
        if (draft == null) {
            return;
        }
        String name = str(firstNonEmpty(draft.get("offerName"), draft.get("offeringName"), "本套餐"));
        Object feeObj = firstNonEmpty(draft.get("fixedFeeAmount"), draft.get("monthlyFee"));
        String feeText = feeObj == null || String.valueOf(feeObj).isBlank()
                ? "按资费标准"
                : String.valueOf(feeObj).replaceAll("\\.0$", "") + "元";
        String resources = java.util.stream.Stream.of(
                        str(draft.get("includeData")),
                        str(draft.get("includeVoice")),
                        str(draft.get("includeBroadband")))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("+"));
        if (resources.isBlank()) {
            resources = "套餐约定资源";
        }
        String scenario = str(draft.get("bizScenario"));
        String category = str(firstNonEmpty(draft.get("categoryName"), draft.get("messageRootKey"), "配置方案"));
        String audience = str(firstNonEmpty(draft.get("targetUser"), "客户"));

        putNoticeIfBlank(draft, fillSources, "printMonthlyFeeText", feeText + "/月");
        putNoticeIfBlank(draft, fillSources, "printResourceText", resources);
        putNoticeIfBlank(draft, fillSources, "printLimitText",
                audience + "专属；场景「" + (scenario.isBlank() ? category : scenario) + "」按销售政策执行");
        putNoticeIfBlank(draft, fillSources, "successSmsImmediate",
                "恭喜您成功办理" + name + "，月费" + feeText + "，含" + resources + "，感谢您的支持！");
        putNoticeIfBlank(draft, fillSources, "successSmsReserved",
                "您的" + name + "已预约生效，月费" + feeText + "，生效后可享受约定权益。");
        putNoticeIfBlank(draft, fillSources, "cancelSms",
                "您好，您的" + name + "已退订，如有疑问请致电10086。");
        putNoticeIfBlank(draft, fillSources, "confirmSms",
                "尊敬的客户，您正在办理" + name + "，月费" + feeText + "，是否确认办理？");

        Map<String, Object> print = castMap(draft.get("printNotice"));
        if (print.isEmpty()) {
            print = new LinkedHashMap<>();
        }
        print.putIfAbsent("prcMonthFee", draft.get("printMonthlyFeeText"));
        print.putIfAbsent("containResource", draft.get("printResourceText"));
        print.putIfAbsent("limitCondition", draft.get("printLimitText"));
        draft.put("printNotice", print);

        Map<String, Object> sms = castMap(draft.get("smsNotice"));
        if (sms.isEmpty()) {
            sms = new LinkedHashMap<>();
        }
        sms.putIfAbsent("sysNoteNow", draft.get("successSmsImmediate"));
        sms.putIfAbsent("sysNoteNext", draft.get("successSmsReserved"));
        sms.putIfAbsent("sysNoteCancle", draft.get("cancelSms"));
        sms.putIfAbsent("sysNoteErke", draft.get("confirmSms"));
        draft.put("smsNotice", sms);
    }

    private void putNoticeIfBlank(Map<String, Object> draft, Map<String, String> fillSources,
                                  String key, String value) {
        if (empty(draft.get(key))) {
            draft.put(key, value);
            if (fillSources != null) {
                fillSources.put(key, "scenario_default");
            }
        }
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

    private double resolveFixedFee(Map<String, Object> draft) {
        Object fee = firstNonEmpty(
                draft.get("fixedFeeAmount"),
                draft.get("monthlyFee"),
                castMap(draft.get("chargePlan")).get("fixedFeeAmount"));
        return num(fee, -1);
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
        return values.length > 0 ? values[values.length - 1] : null;
    }
}
