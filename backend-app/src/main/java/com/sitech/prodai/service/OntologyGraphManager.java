package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.common.MapOps;
import com.sitech.prodai.service.ops.OpsGraphSchemaValidator;
import com.sitech.prodai.service.ops.OpsProductGraphLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本体图谱管理器（R2 Phase6 从 {@link ProductOntologyService} 拆出）。
 * <p>职责单一：事实图缓存单源（loadGraph/reloadGraph last-known-good 守卫）、
 * 图摘要/元模型视图（getGraphSummary/getOntologyMeta）、商品解析（resolveOfferingId）、
 * 工单 → 事实图回写协调器（WorkOrderGraphCoordinator 实现，graphCache 写入与同步锁在此内聚）。
 * <p>风险阈值/规则目录等跨域数据经函数式回调由宿主提供，保持图缓存所有权不外泄。
 */
public class OntologyGraphManager {

    private static final Logger log = LoggerFactory.getLogger(OntologyGraphManager.class);

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
    private final OpsProductGraphLoader graphLoader;
    private final LastKnownGoodGuard lastKnownGoodGuard;
    private final FactGraphSyncService factGraphSync;
    private final ConfigMessageProjector messageProjector;
    /** 延迟解析回归运行器（P1-7 SMOKE 回接）：规避与 ProductConfigRegressionService 的构造循环依赖。 */
    private final ObjectProvider<ProductConfigRegressionService> regressionServiceProvider;
    /** 跨域回调：风险阈值生效态（图默认 ∪ 文件默认 ∪ 覆盖）。 */
    private RiskRulesSupplier riskRulesSupplier;
    /** 跨域回调：外置规则全集目录视图。 */
    private OpsRulesCatalogSupplier opsRulesCatalogSupplier;
    /** R5：ABox 同步状态回调（ABoxSyncScheduler.syncStatus()，jdbc 源才注入）。 */
    private java.util.function.Supplier<Map<String, Object>> aboxSyncStatusSupplier;

    private Map<String, Object> graphCache;
    private String graphSourceId = "empty";

    /** 风险阈值生效态回调（宿主注入：riskRules()）。 */
    @FunctionalInterface
    public interface RiskRulesSupplier {
        Map<String, Object> get();
    }

    /** 外置规则目录回调（宿主注入：getOpsRulesCatalog()）。 */
    @FunctionalInterface
    public interface OpsRulesCatalogSupplier {
        Map<String, Object> get();
    }

    /** 跨域回调注入点（宿主装配完成后调用）。 */
    public void setCallbacks(RiskRulesSupplier riskRulesSupplier,
                             OpsRulesCatalogSupplier opsRulesCatalogSupplier) {
        this.riskRulesSupplier = riskRulesSupplier;
        this.opsRulesCatalogSupplier = opsRulesCatalogSupplier;
    }

    public OntologyGraphManager(ObjectMapper objectMapper,
                                ProdAiProperties properties,
                                OpsProductGraphLoader graphLoader,
                                LastKnownGoodGuard lastKnownGoodGuard,
                                FactGraphSyncService factGraphSync,
                                ConfigMessageProjector messageProjector,
                                ObjectProvider<ProductConfigRegressionService> regressionServiceProvider) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.graphLoader = graphLoader;
        this.lastKnownGoodGuard = lastKnownGoodGuard;
        this.factGraphSync = factGraphSync;
        this.messageProjector = messageProjector;
        this.regressionServiceProvider = regressionServiceProvider;
    }

    private Map<String, Object> riskRules() {
        return riskRulesSupplier == null ? Map.of() : riskRulesSupplier.get();
    }

    private Map<String, Object> opsRulesCatalog() {
        return opsRulesCatalogSupplier == null ? Map.of() : opsRulesCatalogSupplier.get();
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
                            raw.put("shelfOfferings", MapOps.castListOfMaps(raw.get("shelfOfferings")));
                            Map<String, Object> pending = new LinkedHashMap<>();
                            pending.put("graph", raw);
                            pending.put("sourceId", loaded.sourceId());
                            return pending;
                        },
                        commit -> {
                            graphCache = MapOps.castMap(commit.get("graph"));
                            graphSourceId = String.valueOf(commit.get("sourceId"));
                            syncFactGraphToRdf();
                        })
                .validator(pending -> {
                    OpsGraphSchemaValidator.ValidationResult vr =
                            OpsGraphSchemaValidator.validateAndNormalize(MapOps.castMap(pending.get("graph")));
                    return vr.ok() ? List.of() : vr.errors();
                })
                // P1-7 回接：SMOKE 用 pending 图谱跑回归用例集，任一断言失败阻断切换
                .smoke(pending -> regressionServiceProvider.getObject()
                        .smokeAgainstGraph(MapOps.castMap(pending.get("graph"))))
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

    public synchronized Map<String, Object> loadGraph() {
        if (graphCache != null) {
            return graphCache;
        }
        OpsProductGraphLoader.LoadedGraph loaded = graphLoader.load();
        Map<String, Object> raw = new LinkedHashMap<>(loaded.graph());
        graphSourceId = loaded.sourceId();
        // 事实图原样使用；演示扩容写在 mock_graph.json，不在代码造数
        raw.put("shelfOfferings", MapOps.castListOfMaps(raw.get("shelfOfferings")));
        graphCache = raw;
        return graphCache;
    }

    /** 事实图 → 本体图同步：使 SPARQL 可对在架商品做语义检索（灌图失败不阻断启动）。 */
    public void syncFactGraphToRdf() {
        try {
            factGraphSync.syncShelfOfferings(loadGraph());
        } catch (Exception e) {
            log.warn("[OntologyGraphManager] 事实图灌本体图失败（SPARQL 检索将走回退）: {}", e.getMessage());
        }
    }

    /** 配置发布 → graphCache 原子切换（ComplianceRuleEngine 知识自迭代提交回调）。 */
    public synchronized void publishGraph(Map<String, Object> graph) {
        graphCache = graph;
    }

    /** 工单域 → 事实图回写协调器（graphCache 写入与同步锁内聚在本类）。 */
    public WorkOrderGraphCoordinator buildWorkOrderGraphCoordinator() {
        return new WorkOrderGraphCoordinator() {
            @Override
            public void onWorkOrderCreated(String offeringId, String workOrderId, Map<String, Object> workOrder) {
                synchronized (OntologyGraphManager.this) {
                    Map<String, Object> graph = loadGraph();
                    List<Map<String, Object>> shelf = MapOps.castListOfMaps(graph.get("shelfOfferings"));
                    for (Map<String, Object> o : shelf) {
                        if (offeringId.equals(MapOps.str(o.get("offeringId")))) {
                            o.put("dispositionStatus", "work_order_open");
                            o.put("lastWorkOrderId", workOrderId);
                            break;
                        }
                    }
                    graph.put("shelfOfferings", shelf);
                    List<Map<String, Object>> graphOrders = MapOps.castListOfMaps(graph.get("workOrders"));
                    graphOrders.add(0, new LinkedHashMap<>(workOrder));
                    graph.put("workOrders", graphOrders);
                    graphCache = graph;
                }
            }

            @Override
            public void syncWorkOrderToGraph(Map<String, Object> saved, String status) {
                String offeringId = MapOps.str(saved.get("offeringId"));
                String woId = MapOps.str(saved.get("workOrderId"));
                String disposition = switch (status) {
                    case "in_progress" -> "work_order_in_progress";
                    case "done" -> "work_order_done";
                    case "cancelled" -> "work_order_cancelled";
                    default -> "work_order_open";
                };
                synchronized (OntologyGraphManager.this) {
                    Map<String, Object> graph = loadGraph();
                    List<Map<String, Object>> shelf = MapOps.castListOfMaps(graph.get("shelfOfferings"));
                    for (Map<String, Object> o : shelf) {
                        if (offeringId.equals(MapOps.str(o.get("offeringId")))) {
                            o.put("dispositionStatus", disposition);
                            o.put("lastWorkOrderId", woId);
                            o.put("lastWorkOrderStatus", status);
                            break;
                        }
                    }
                    graph.put("shelfOfferings", shelf);
                    List<Map<String, Object>> graphOrders = MapOps.castListOfMaps(graph.get("workOrders"));
                    boolean updated = false;
                    for (int i = 0; i < graphOrders.size(); i++) {
                        if (woId.equals(MapOps.str(graphOrders.get(i).get("workOrderId")))) {
                            graphOrders.set(i, new LinkedHashMap<>(saved));
                            updated = true;
                            break;
                        }
                    }
                    if (!updated) {
                        graphOrders.add(0, new LinkedHashMap<>(saved));
                    }
                    graph.put("workOrders", graphOrders);
                    graphCache = graph;
                }
            }

            @Override
            public Map<String, Object> findShelfOffering(String offeringId) {
                return OntologyGraphManager.this.findShelfOffering(offeringId);
            }

            @Override
            public Map<String, Object> modeMeta() {
                return OntologyGraphManager.this.modeMeta();
            }
        };
    }

    /** 货架商品反查：按 offeringId 精确匹配，未命中返回 null。 */
    public Map<String, Object> findShelfOffering(String offeringId) {
        if (MapOps.empty(offeringId)) {
            return null;
        }
        return MapOps.castListOfMaps(loadGraph().get("shelfOfferings")).stream()
                .filter(o -> offeringId.equals(MapOps.str(o.get("offeringId"))))
                .findFirst()
                .orElse(null);
    }

    /** 演示模式与数据来源标注（demoMode/dataSource/dataSourceMode）。 */
    public Map<String, Object> modeMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("demoMode", properties.getOntology().isDemoEnabled());
        meta.put("dataSource", graphSourceId);
        meta.put("dataSourceMode", properties.getOntology().getDataSource());
        return meta;
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

    public Map<String, Object> getGraphSummary() {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> rules = riskRules();
        List<Map<String, Object>> offerings = MapOps.castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> opsGraph = MapOps.castMap(graph.get("opsGraph"));
        int anomalyCount = opsGraph == null ? 0 : opsGraph.size();

        Set<String> previewCats = Set.of("zero_fee", "low_eff", "abnormal_discount", "whitelist", "threshold_demo");
        Set<String> previewIds = new LinkedHashSet<>();
        int previewLimit = 20;
        Map<String, Object> catalog = opsRulesCatalog();
        if (!catalog.isEmpty()) {
            List<String> ids = MapOps.castList(catalog.get("previewOfferingIds")).stream()
                    .map(MapOps::str).collect(Collectors.toList());
            previewIds.addAll(ids);
        }
        List<Map<String, Object>> shelfPreview = offerings.stream()
                .filter(o -> {
                    String category = MapOps.str(o.get("category"));
                    if (previewCats.contains(category)) {
                        return true;
                    }
                    return previewIds.contains(MapOps.str(o.get("offeringId")));
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
        body.put("scenarios", new ArrayList<>(MapOps.castMap(graph.get("bizScenarios")).keySet()));
        body.put("templates", new ArrayList<>(MapOps.castMap(graph.get("templates")).keySet()));
        body.put("shelfCount", offerings.size());
        body.put("anomalyOfferingCount", anomalyCount);
        body.put("ruleVersion", rules.getOrDefault("ruleVersion", "RiskRules-v1.2"));
        body.put("riskRules", rules);
        body.put("shelfOfferings", shelfPreview);
        body.put("classes", ONTOLOGY_CLASSES);
        body.put("configSchemes", MapOps.castListOfMaps(graph.get("configSchemes")));
        body.put("productCategories", messageProjector.categories());
        body.put("relations", List.of(
                "configuresProduct", "belongsToCategory", "hasSalesPolicy", "hasReleaseScope",
                "hasNetworkCapability", "hasFamilyOfferPolicy", "hasChargePlan", "hasPreferentialPlan",
                "hasResourceEntitlement", "hasPrintNotice", "hasSmsNotice", "hasValueAddedEquity",
                "hasOfferCompatibility", "hasConfigChange", "governedBy", "appliesScene", "similarTo"
        ));
        body.put("ruleSets", catalog.getOrDefault("ruleSets", Map.of()));
        body.put("engines", catalog.getOrDefault("engines", Map.of()));
        // R5 可观测：ABox 同步状态（abox-source=jdbc 时由 ABoxSyncScheduler 提供；mock 源为 null）
        body.putAll(aboxSyncStatus());
        return withModeMeta(body);
    }

    /** ABox 同步可观测字段（abox_last_synced_at / abox_row_count / abox_last_sync_ok）。 */
    private Map<String, Object> aboxSyncStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        if (aboxSyncStatusSupplier != null) {
            status.putAll(aboxSyncStatusSupplier.get());
        } else {
            status.put("abox_last_synced_at", null);
            status.put("abox_last_sync_ok", null);
            status.put("abox_row_count", 0);
        }
        return status;
    }

    /** ABox 同步状态回调（宿主注入：ABoxSyncScheduler.syncStatus()；mock 源不注入）。 */
    public void setAboxSyncStatusSupplier(java.util.function.Supplier<Map<String, Object>> supplier) {
        this.aboxSyncStatusSupplier = supplier;
    }

    /** 最近一次成功加载的货架行数（loadGraph 时记录）。 */
    public long aboxRowCount() {
        Map<String, Object> graph = graphCache;
        return graph == null ? 0 : MapOps.castListOfMaps(graph.get("shelfOfferings")).size();
    }

    public Map<String, Object> getOntologyMeta() {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("classes", ONTOLOGY_CLASSES);
        body.put("ontologyVersion", "2.2");
        body.put("productCategories", messageProjector.categories());
        body.put("bizScenarios", MapOps.castMap(graph.get("bizScenarios")));
        body.put("templates", MapOps.castMap(graph.get("templates")));
        body.put("configSchemes", MapOps.castListOfMaps(graph.get("configSchemes")));
        body.put("equityGiftWhitelist", MapOps.castList(graph.get("equityGiftWhitelist")));
        body.put("riskRuleDefaults", riskRules());
        body.put("opsRules", opsRulesCatalog());
        return withModeMeta(body);
    }

    /**
     * 按编码或自然语言解析产商品。优先精确编码，再按在架名称/别名匹配。
     * @return offeringId，无法解析时返回 null
     */
    public String resolveOfferingId(String offeringId, String text,
                                    com.sitech.prodai.service.OpsRulesService opsRules) {
        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> shelf = MapOps.castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> opsGraph = MapOps.castMap(graph.get("opsGraph"));

        if (!MapOps.empty(offeringId)) {
            String oid = offeringId.trim();
            boolean onShelf = shelf.stream().anyMatch(o -> oid.equals(MapOps.str(o.get("offeringId"))));
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
            String oid = MapOps.str(o.get("offeringId"));
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
            String name = MapOps.str(o.get("offeringName"));
            if (name.length() >= 2 && q.contains(name) && name.length() > bestLen) {
                bestId = MapOps.str(o.get("offeringId"));
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

    private static Map<String, String> metaClass(String code, String name) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("classCode", code);
        row.put("className", name);
        return row;
    }
}
