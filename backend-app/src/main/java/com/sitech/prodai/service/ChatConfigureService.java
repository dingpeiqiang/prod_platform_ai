package com.sitech.prodai.service;

import com.sitech.prodai.service.common.MapOps;
import com.sitech.prodai.service.ops.OpsExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
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
 * 对话式配置服务（R2 Phase6 从 {@link ProductOntologyService} 拆出）。
 * <p>职责单一：智查检索（discoverConfigs LLM+SPARQL+词典打分）、市场洞察（marketInsight）、
 * 一键复制为新草稿（copyAsDraft 家族 + 工单联动）、多方案对比（compareConfigSchemes）、
 * 聊天配置（chatConfigure）、配置审计链（configTraces 内存 + 表 B 落盘）与溯源解释。
 * <p>事实图读取、合规校验、商品解析经函数式回调由宿主提供，保持图缓存与规则所有权不外泄。
 */
public class ChatConfigureService {

    private static final Logger log = LoggerFactory.getLogger(ChatConfigureService.class);

    private final ObjectMapperHolder objectMapperHolder;
    private final OpsExtractionService extractionService;
    private final TemplateDeriveEngine deriveEngine;
    private final LlmIntentExtractor intentExtractor;
    private final SparqlConfigDiscoverer sparqlDiscoverer;
    private final ConfigMessageProjector messageProjector;
    private final ConfigDraftService configDraftService;
    private final ConfigDocImportService configDocImportService;
    private final OpsWorkOrderService opsWorkOrderService;
    private final OntologyVersionService versionService;

    /** 配置场景审计链路（内存）；对齐方案 get_trace / explain。 */
    private final Map<String, List<Map<String, Object>>> configTraces = new ConcurrentHashMap<>();

    /** 事实图读取回调（宿主 graphCache 单源）。 */
    @FunctionalInterface
    public interface GraphSupplier {
        Map<String, Object> loadGraph();
    }

    /** 合规回调：draft → {compliancePass, issues, appliedRules}。 */
    @FunctionalInterface
    public interface ComplianceChecker {
        Map<String, Object> checkCompliance(Map<String, Object> draft);
    }

    /** 商品解析回调（offeringId, text → offeringId 或 null）。 */
    @FunctionalInterface
    public interface OfferingIdResolver {
        String resolve(String offeringId, String text);
    }

    /** 货架商品反查回调。 */
    @FunctionalInterface
    public interface ShelfOfferingFinder {
        Map<String, Object> find(String offeringId);
    }

    /** 开单回调（工单联动）。 */
    @FunctionalInterface
    public interface WorkOrderCreator {
        Map<String, Object> create(Map<String, Object> request);
    }

    /** ObjectMapper 访问回调（deepCopy 需要）。 */
    @FunctionalInterface
    public interface ObjectMapperHolder {
        com.fasterxml.jackson.databind.ObjectMapper get();
    }

    private final GraphSupplier graphSupplier;
    private final ComplianceChecker complianceChecker;
    private final OfferingIdResolver offeringIdResolver;
    private final ShelfOfferingFinder shelfOfferingFinder;
    private final WorkOrderCreator workOrderCreator;
    private final ComplianceRuleEngine complianceRuleEngine;

    public ChatConfigureService(ObjectMapperHolder objectMapperHolder,
                                OpsExtractionService extractionService,
                                TemplateDeriveEngine deriveEngine,
                                LlmIntentExtractor intentExtractor,
                                SparqlConfigDiscoverer sparqlDiscoverer,
                                ConfigMessageProjector messageProjector,
                                ConfigDraftService configDraftService,
                                ConfigDocImportService configDocImportService,
                                OpsWorkOrderService opsWorkOrderService,
                                OntologyVersionService versionService,
                                GraphSupplier graphSupplier,
                                ComplianceChecker complianceChecker,
                                OfferingIdResolver offeringIdResolver,
                                ShelfOfferingFinder shelfOfferingFinder,
                                WorkOrderCreator workOrderCreator,
                                ComplianceRuleEngine complianceRuleEngine) {
        this.objectMapperHolder = objectMapperHolder;
        this.extractionService = extractionService;
        this.deriveEngine = deriveEngine;
        this.intentExtractor = intentExtractor;
        this.sparqlDiscoverer = sparqlDiscoverer;
        this.messageProjector = messageProjector;
        this.configDraftService = configDraftService;
        this.configDocImportService = configDocImportService;
        this.opsWorkOrderService = opsWorkOrderService;
        this.versionService = versionService;
        this.graphSupplier = graphSupplier;
        this.complianceChecker = complianceChecker;
        this.offeringIdResolver = offeringIdResolver;
        this.shelfOfferingFinder = shelfOfferingFinder;
        this.workOrderCreator = workOrderCreator;
        this.complianceRuleEngine = complianceRuleEngine;
    }

    private Map<String, Object> loadGraph() {
        return graphSupplier.loadGraph();
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        return MapOps.deepCopy(objectMapperHolder.get(), source);
    }

    /**
     * 智查：LLM 意图结构化 + 本体 SPARQL 语义检索优先，词典打分（matchScore）回退。
     * <p>三层链路：LlmIntentExtractor（NL→结构化意图）→ FactGraphSyncService（事实图→Offering 实例）
     * → SparqlConfigDiscoverer（参数化 SPARQL）。SPARQL 无命中或异常时回退 matchScore，保证可用性。
     */
    public Map<String, Object> discoverConfigs(String query, int limit) {
        String q = query == null ? "" : query.trim();
        int lim = limit <= 0 ? 20 : Math.min(limit, 50);
        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> offerings = MapOps.castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> templates = MapOps.castMap(graph.get("templates"));
        List<Map<String, Object>> schemes = MapOps.castListOfMaps(graph.get("configSchemes"));

        LlmIntentExtractor.DiscoverIntent intent = intentExtractor.extract(q);
        List<Map<String, Object>> items = new ArrayList<>();
        String retrieveEngine = "dict-score";
        if (!q.isBlank()) {
            try {
                List<Map<String, Object>> sparqlHits = sparqlDiscoverer.discover(intent);
                if (!sparqlHits.isEmpty()) {
                    items.addAll(sparqlHits);
                    retrieveEngine = intent.engine() + "+sparql";
                }
            } catch (Exception e) {
                log.warn("[ChatConfigureService] SPARQL 语义检索失败，回退词典打分: {}", e.getMessage());
            }
        }
        if (items.isEmpty()) {
            Integer timeWindowDays = intent.timeWindowDays();
            for (Map<String, Object> o : offerings) {
                int score = matchScore(q, o);
                if (score <= 0 && !q.isBlank()) {
                    continue;
                }
                if (q.isBlank()) {
                    score = 1;
                }
                if (timeWindowDays != null && MapOps.num(o.get("shelfDays"), -1) > timeWindowDays) {
                    continue;
                }
                Map<String, Object> row = toQueryCard(o, score);
                items.add(row);
            }
            items.sort((a, b) -> Integer.compare((int) MapOps.num(b.get("score"), 0), (int) MapOps.num(a.get("score"), 0)));
        }
        if (items.size() > lim) {
            items = new ArrayList<>(items.subList(0, lim));
        }

        List<Map<String, Object>> tplHits = new ArrayList<>();
        for (Map.Entry<String, Object> e : templates.entrySet()) {
            Map<String, Object> t = MapOps.castMap(e.getValue());
            String blob = (MapOps.str(t.get("templateId")) + " " + MapOps.str(t.get("name"))
                    + " " + MapOps.str(t.get("messageRootKey")) + " " + MapOps.str(t.get("categoryCode")))
                    .toLowerCase(Locale.ROOT);
            if (q.isBlank() || blob.contains(q.toLowerCase(Locale.ROOT))
                    || q.contains("模板") || q.contains("套餐") || q.contains("资费")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("template_id", t.get("templateId"));
                row.put("name", t.get("name"));
                row.put("monthly_fee", MapOps.firstNonEmpty(t.get("fixedFeeAmount"), t.get("monthlyFee")));
                row.put("message_root_key", t.get("messageRootKey"));
                row.put("category_code", t.get("categoryCode"));
                tplHits.add(row);
            }
        }

        List<Map<String, Object>> schemeHits = new ArrayList<>();
        for (Map<String, Object> s : schemes) {
            String blob = (MapOps.str(s.get("schemeId")) + " " + MapOps.str(s.get("schemeName"))
                    + " " + MapOps.str(s.get("messageRootKey")) + " " + MapOps.str(s.get("categoryName"))
                    + " " + MapOps.str(s.get("productLine"))).toLowerCase(Locale.ROOT);
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
        body.put("retrieve_engine", retrieveEngine);
        // 无匹配时如实返回：LLM 表达层会向用户说明"未找到"，不硬凑低分项
        body.put("no_match", q != null && !q.isBlank() && items.isEmpty());
        body.put("intent", Map.of(
                "query_type", intent.queryType(),
                "keywords", intent.keywords(),
                "monthly_fee", intent.monthlyFee() == null ? "null" : intent.monthlyFee(),
                "fee_tolerance", intent.feeTolerance() == null ? "null" : intent.feeTolerance()
        ));
        body.put("total", items.size());
        body.put("items", items);
        body.put("templates", tplHits.stream().limit(10).collect(Collectors.toList()));
        body.put("configSchemes", schemeHits);
        body.put("productCategories", messageProjector.categories());
        String traceId = "cfg-discover-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "nl_discover_and_retrieve",
                "query", q,
                "engine", retrieveEngine,
                "hit_count", items.size(),
                "timestamp", Instant.now().toString()
        ));
        body.put("trace_id", traceId);
        return withModeMeta(body);
    }

    private Map<String, Object> withModeMeta(Map<String, Object> body) {
        return body;
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
        List<Map<String, Object>> offerings = MapOps.castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> opsGraph = MapOps.castMap(graph.get("opsGraph"));
        Map<String, Object> templates = MapOps.castMap(graph.get("templates"));

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
            String id = MapOps.str(o.get("offeringId"));
            if (!id.isBlank()) {
                seen.add(id);
            }
            String bucket = riskish ? "风险/零资费" : "在售/增长";
            rows.add(toMarketInsightRow(o, opsGraph.get(id), score, bucket));
        }

        // 货架无 5G 实体时，用模板补一条增长样本
        if (qLower.contains("5g") && rows.stream().noneMatch(r -> MapOps.str(r.get("name")).toLowerCase(Locale.ROOT).contains("5g"))) {
            for (Object raw : templates.values()) {
                Map<String, Object> t = MapOps.castMap(raw);
                String name = MapOps.str(t.get("name"));
                if (!name.toLowerCase(Locale.ROOT).contains("5g")) {
                    continue;
                }
                String tid = MapOps.str(t.get("templateId"));
                if (seen.contains(tid)) {
                    continue;
                }
                Map<String, Object> synthetic = new LinkedHashMap<>();
                synthetic.put("offeringId", tid);
                synthetic.put("offeringName", name);
                synthetic.put("state", "上架");
                synthetic.put("monthlyFee", MapOps.firstNonEmpty(t.get("fixedFeeAmount"), t.get("monthlyFee")));
                synthetic.put("category", "normal");
                synthetic.put("offeringType", "main_pkg");
                rows.add(toMarketInsightRow(synthetic, null, 35, "在售/增长"));
                seen.add(tid);
            }
        }

        rows.sort((a, b) -> {
            int sc = Integer.compare((int) MapOps.num(b.get("_score"), 0), (int) MapOps.num(a.get("_score"), 0));
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
        body.put("entity_ids", rows.stream().map(r -> MapOps.str(r.get("product"))).filter(s -> !s.isBlank()).toList());
        body.put("sparql", "ops-graph shelfOfferings + opsGraph.metrics");
        body.put("discovery_method", "ops_graph");
        body.put("count", rows.size());
        body.put("question", q);
        return body;
    }

    private boolean isOnShelf(Map<String, Object> o) {
        String st = MapOps.str(o.get("state"));
        return st.isBlank() || "上架".equals(st) || "在售".equals(st);
    }

    private boolean isRiskishOffering(Map<String, Object> o) {
        String cat = MapOps.str(o.get("category"));
        if ("zero_fee".equals(cat) || "low_eff".equals(cat) || "abnormal_discount".equals(cat)) {
            return true;
        }
        return MapOps.num(o.get("monthlyFee"), -1) == 0 && !"whitelist".equals(cat);
    }

    private Map<String, Object> toMarketInsightRow(Map<String, Object> offering, Object opsNodeRaw, int score, String bucket) {
        Map<String, Object> opsNode = MapOps.castMap(opsNodeRaw);
        String id = MapOps.str(offering.get("offeringId"));
        String name = MapOps.str(MapOps.firstNonEmpty(offering.get("offeringName"), offering.get("name"), id));
        Double growth = null;
        Double users = null;
        for (Map<String, Object> m : MapOps.castListOfMaps(opsNode.get("metrics"))) {
            Object delta = m.get("metricDelta");
            if (delta != null && growth == null) {
                growth = MapOps.num(delta, 0);
            }
            String code = MapOps.str(m.get("metricCode"));
            if (("累计收入".equals(code) || "收入".equals(code) || code.contains("收入")) && delta != null) {
                growth = MapOps.num(delta, 0);
            }
            if (users == null && (code.contains("用户") || code.contains("新增") || "订购量".equals(code))) {
                users = MapOps.num(m.get("metricValue"), Double.NaN);
                if (users.isNaN()) {
                    users = null;
                }
            }
        }
        if (growth == null && offering.get("revenue30d") != null && offering.get("salesCnt30d") != null) {
            double sales = MapOps.num(offering.get("salesCnt30d"), 0);
            // 无环比时用销量相对占位，避免面板空白
            growth = sales <= 0 ? -0.05 : Math.min(0.2, sales / 5000.0);
        }
        if (users == null) {
            double sales = MapOps.num(offering.get("salesCnt30d"), Double.NaN);
            if (!Double.isNaN(sales)) {
                users = sales;
            }
        }

        boolean zeroFee = Boolean.TRUE.equals(offering.get("isZeroFee"))
                || "zero_fee".equals(MapOps.str(offering.get("category")))
                || MapOps.num(offering.get("monthlyFee"), -1) == 0;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("product", id);
        row.put("entity", id);
        row.put("uri", id);
        row.put("name", name);
        row.put("productName", name);
        row.put("status", MapOps.firstNonEmpty(offering.get("state"), "在售"));
        row.put("price", MapOps.firstNonEmpty(offering.get("fixedFeeAmount"), offering.get("monthlyFee")));
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
     * <p>sessionId 非空时复制即开配置工单（对齐 rd_draft_manage doCopy 的「复制即开单」闭环），
     * 开单失败不影响复制结果。
     */
    public Map<String, Object> copyAsDraft(String offeringId, String text) {
        return copyAsDraft(offeringId, text, null, null);
    }

    public Map<String, Object> copyAsDraft(String offeringId, String text, String sessionId) {
        return copyAsDraft(offeringId, text, sessionId, null);
    }

    /**
     * 带补充需求的复制：requirement 非空时（如「改名为校园青春版，月费 29 元」），
     * 复制后经 {@link OpsExtractionService#extractUpdateIntent} 按需求修正副本字段，
     * 与工单卡复制弹窗（rd_draft_manage doCopy）同构。
     */
    public Map<String, Object> copyAsDraft(String offeringId, String text, String sessionId, String requirement) {
        String oid = offeringIdResolver.resolve(offeringId, text);
        Map<String, Object> shelf = shelfOfferingFinder.find(oid);
        if (shelf == null) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "未找到可复制的历史方案：" + MapOps.firstNonEmpty(offeringId, text));
            return fail;
        }

        Map<String, Object> sourceDraft = complianceRuleEngine.shelfOfferingToDraft(shelf);
        Map<String, Object> draft = deepCopy(sourceDraft);
        draft.remove("state");
        draft.put("status", "draft");
        draft.put("copiedFrom", shelf.get("offeringId"));
        String baseName = MapOps.str(MapOps.firstNonEmpty(draft.get("offeringName"), "配置方案"));
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
            if (!MapOps.str(draft.get("offeringName")).contains("副本")) {
                draft.put("offeringName", baseName + " (副本)");
            }
        }

        // 复制弹窗补充需求：LLM 约束抽取（模板字段白名单，未提及不编造），与 doCopy 同构
        Map<String, Object> appliedRequirements = new LinkedHashMap<>();
        String req = requirement == null ? "" : requirement.trim();
        if (!req.isBlank() && !"null".equalsIgnoreCase(req)) {
            Map<String, Object> intent = extractionService.extractUpdateIntent(
                    req, draft, draftValueSnapshot(draft));
            for (Map.Entry<String, Object> e : intent.entrySet()) {
                applyCopyDraftChange(draft, appliedRequirements, e.getKey(), e.getValue());
            }
        }

        Map<String, Object> compliance = complianceChecker.checkCompliance(draft);
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
        if (!appliedRequirements.isEmpty()) {
            body.put("applied_requirements", appliedRequirements);
        }
        // 草稿先落库再开单（开单原子性，对齐 RdConfigChatTool.persistDraft）：
        // 草稿落库失败时不开单，避免产生 payload.draftId 缺失的孤儿工单
        // （孤儿工单上的复制/删除/提交操作都会因反查不到草稿而失败）。
        if (sessionId != null && !sessionId.isBlank()) {
            body.put("sessionId", sessionId);
            if (!persistCopyDraft(body, draft, compliance)) {
                log.warn("[ChatConfigureService] 智查复制草稿落库未成功，跳过开单（避免孤儿工单）: sessionId={}", sessionId);
                return body;
            }
        }
        attachCopyWorkOrder(body, draft, sessionId);
        return body;
    }

    /**
     * 智查复制草稿落库（pd_ai_ontology_instance）：draftId 写回 body/draft，
     * 供 attachCopyWorkOrder 关联工单 payload，后续凭工单号反查草稿。
     *
     * @return 落库是否成功（draftId 已写回）；失败时调用方跳过开单，保证工单与草稿强关联
     */
    private boolean persistCopyDraft(Map<String, Object> body, Map<String, Object> draft, Map<String, Object> compliance) {
        try {
            if (draft == null || draft.isEmpty()) {
                return false;
            }
            Map<String, Object> saveReq = new LinkedHashMap<>();
            saveReq.put("draft", draft);
            saveReq.put("sessionId", body.get("sessionId"));
            saveReq.put("compliancePass", compliance.get("compliancePass"));
            Map<String, Object> saved = configDraftService.saveConfigDraft(saveReq);
            if (Boolean.TRUE.equals(saved.get("success")) && saved.get("draftId") != null) {
                draft.put("draftId", saved.get("draftId"));
                draft.put("clientId", saved.get("clientId"));
                body.put("draft", draft);
                body.put("draft_id", saved.get("draftId"));
                body.put("client_id", saved.get("clientId"));
                return true;
            }
            log.warn("[ChatConfigureService] 智查复制草稿落库失败: {}", saved.getOrDefault("message", "未知错误"));
            return false;
        } catch (Exception e) {
            log.warn("[ChatConfigureService] 智查复制草稿落库失败（不影响复制结果）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 复制补充需求的单字段落草稿：值有效且与现值不同才写入。
     * monthlyFee 变更联动固费/chargePlan；offeringName 变更同步 offerName（与 doCopy.applyDraftChange 同构）。
     */
    private void applyCopyDraftChange(Map<String, Object> draft, Map<String, Object> changes, String field, Object value) {
        if (field == null || value == null) {
            return;
        }
        String val = String.valueOf(value).trim();
        if (val.isBlank() || "null".equalsIgnoreCase(val)) {
            return;
        }
        String current = MapOps.str(MapOps.firstNonEmpty(draft.get(field), ""));
        if (val.equals(current)) {
            return;
        }
        draft.put(field, val);
        changes.put(field, val);
        if ("monthlyFee".equals(field)) {
            draft.put("fixedFeeAmount", val);
            if (draft.get("chargePlan") instanceof Map<?, ?> cp) {
                Map<String, Object> charge = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : cp.entrySet()) {
                    charge.put(String.valueOf(e.getKey()), e.getValue());
                }
                if (charge.containsKey("fixedFeeAmount")) {
                    charge.put("fixedFeeAmount", val);
                }
                draft.put("chargePlan", charge);
            }
        } else if ("offeringName".equals(field) && draft.containsKey("offerName")) {
            draft.put("offerName", val);
        }
    }

    /** 注入修改意图抽取 prompt 的当前草稿值快照（与 RdDraftManageTool.draftValueSnapshot 同构）。 */
    private Map<String, Object> draftValueSnapshot(Map<String, Object> draft) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String key : List.of("offeringName", "monthlyFee", "fixedFeeAmount", "bizScenario",
                "targetUser", "includeBroadband", "includeData", "includeVoice", "channelScope")) {
            if (draft.get(key) != null) {
                snapshot.put(key, draft.get(key));
            }
        }
        return snapshot;
    }

    /**
     * 复制成功后同步创建配置工单（payload 关联草稿 draftId，绑定 sessionId）。
     * 前端工单卡（attachWorkOrdersToMsg → SessionWorkOrderCard）据此展示「复制即开单」闭环，
     * 后续删除/复制/提交仅凭工单号反查 payload.draftId 定位草稿。
     */
    @SuppressWarnings("unchecked")
    private void attachCopyWorkOrder(Map<String, Object> body, Map<String, Object> draft, String sessionId) {
        try {
            String copyName = MapOps.str(MapOps.firstNonEmpty(draft.get("offeringName"), "配置草稿副本"));
            String monthlyFee = String.valueOf(MapOps.firstNonEmpty(draft.get("monthlyFee"), draft.get("fixedFeeAmount"), ""));
            String scenario = String.valueOf(MapOps.firstNonEmpty(draft.get("bizScenario"), draft.get("scenario"), ""));
            Map<String, Object> woReq = new LinkedHashMap<>();
            woReq.put("offeringName", copyName);
            woReq.put("source", "rd_config_draft");
            // 工单与草稿强关联：删除/复制操作仅凭 work_order_id 反查
            woReq.put("draftId", MapOps.str(MapOps.firstNonEmpty(body.get("draft_id"), draft.get("draftId"), draft.get("draft_id"))));
            if (sessionId != null && !sessionId.isBlank()) {
                woReq.put("sessionId", sessionId);
            }
            woReq.put("title", copyName + "配置工单");
            woReq.put("summary", "智查结果复制为新草稿：月费=" + (monthlyFee.isBlank() ? "-" : monthlyFee)
                    + "，场景=" + (scenario.isBlank() ? "-" : scenario));
            woReq.put("actions", List.of(
                    "核对配置草稿字段完整性",
                    "合规校验后提交",
                    "提交通过后发布上架"
            ));
            woReq.put("compliancePass", body.get("compliancePass"));
            woReq.put("complianceIssues", body.get("issues"));
            Map<String, Object> woBody = workOrderCreator.create(woReq);
            if (woBody != null && woBody.get("workOrder") instanceof Map<?, ?> wo) {
                body.put("workOrder", wo);
                body.put("work_order_id", ((Map<String, Object>) wo).get("workOrderId"));
            }
        } catch (Exception e) {
            log.warn("[ChatConfigureService] 智查复制开单失败（不影响复制结果）: {}", e.getMessage());
        }
    }

    /** 智读：先解析文档再批量映射（薄委托 → {@link ConfigDocImportService}）。 */
    public Map<String, Object> batchFromDocumentBytes(byte[] bytes, String fileName,
                                                      ConfigDocImportService.GraphSupplier importGraphSupplier,
                                                      ConfigDocImportService.ComplianceChecker importComplianceChecker,
                                                      ConfigDocImportService.AuditAppender auditAppender) {
        return configDocImportService.batchFromDocumentBytes(bytes, fileName,
                importGraphSupplier, extractionService, deriveEngine, importComplianceChecker,
                messageProjector, null, auditAppender);
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

    /** P3-6 溯源链回放：字段默认值来源逐层解析（PROV-O derivedFrom，"为什么默认500M"）。 */
    public Map<String, Object> explainFieldDefault(String field) {
        return deriveEngine.explainFieldDefault(field, loadGraph());
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
        String text = MapOps.str(req.getOrDefault("text", req.get("question")));
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

        double marketScale = MapOps.num(req.get("marketScale"), 150000);
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
            if (MapOps.empty(variant.get("offeringName"))) {
                variant.put("offeringName", "候选方案" + rank);
            } else if (patches.size() > 1) {
                variant.put("offeringName",
                        MapOps.str(baseDraft.getOrDefault("offeringName", "候选方案")) + "-方案" + (char) ('A' + rank - 1));
            }
            Map<String, Object> compliance = complianceChecker.checkCompliance(variant);
            double fee = MapOps.resolveFixedFee(variant);
            if (fee < 0) {
                fee = MapOps.num(variant.get("monthlyFee"), 0);
            }
            // 粗算：转化率随价格下降，年营收 = 市场规模 * 转化率 * 月费 * 12
            double conv = Math.max(0.02, Math.min(0.12, 0.10 - fee / 1000.0));
            double annualRevenue = marketScale * conv * Math.max(fee, 0) * 12;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", MapOps.firstNonEmpty(patch.get("description"), "方案" + rank));
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
            return Double.compare(MapOps.num(b.get("estimatedAnnualRevenue"), 0), MapOps.num(a.get("estimatedAnnualRevenue"), 0));
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
                "recommended", MapOps.str(recommended.get("label")),
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

    private List<Map<String, Object>> compareDraftFields(Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, Object>> diffs = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String k : keys) {
            if ("fillSources".equals(k)) {
                continue;
            }
            String a = MapOps.str(before.get(k));
            String b = MapOps.str(after.get(k));
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

    /** 配置审计链追加（内存 + 表 B config 域落盘）。 */
    public void appendConfigAudit(String traceId, Map<String, Object> step) {
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
        return chatConfigure(text, draft, null);
    }

    /**
     * 聊天配置（支持上游槽位透传）：{@code preSlots} 非空时跳过内部抽取
     * （智聊手册化后 rd_slot_extract 环节已抽，省一次 LLM 调用），否则内部兜底抽取
     * （动态编排入口不受影响）。合并策略：preSlots 为基准，内部仍以原话补抽缺失键
     * （上游仅透传已确认槽位，未携带键不视为「话术未提及」）。
     */
    public Map<String, Object> chatConfigure(String text, Map<String, Object> draft, Map<String, Object> preSlots) {
        boolean hasPreSlots = preSlots != null && !preSlots.isEmpty();
        OpsExtractionService.SlotExtractResult extracted = hasPreSlots
                ? new OpsExtractionService.SlotExtractResult(new LinkedHashMap<>(preSlots), "upstream")
                : extractionService.extractSlots(text == null ? "" : text);
        Map<String, Object> slots = extracted.slots();
        Map<String, Object> infer = deriveEngine.derive(slots, draft, loadGraph());
        @SuppressWarnings("unchecked")
        Map<String, Object> inferredDraft = (Map<String, Object>) infer.get("draft");
        Map<String, Object> compliance = complianceChecker.checkCompliance(inferredDraft);

        Set<String> applied = new LinkedHashSet<>();
        MapOps.castList(infer.get("appliedRules")).forEach(r -> applied.add(MapOps.str(r)));
        MapOps.castList(compliance.get("appliedRules")).forEach(r -> applied.add(MapOps.str(r)));

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

    public Map<String, Object> batchFromDocument(String documentText, List<Map<String, Object>> packages,
                                                 ConfigDocImportService.GraphSupplier importGraphSupplier,
                                                 ConfigDocImportService.ComplianceChecker importComplianceChecker) {
        return configDocImportService.batchFromDocument(documentText, packages,
                importGraphSupplier, extractionService, deriveEngine, importComplianceChecker,
                messageProjector, null);
    }

    /**
     * 中文整句分词局限修复：从查询中提取候选关键词。
     * <p>中文无分隔符时按空白分词会得到整句，导致"39"等数字 token 无法被拆出、费用匹配失效。
     */
    private List<String> extractQueryTokens(String q) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : q.split("[\\s,，、。?？!！]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        Matcher num = Pattern.compile("\\d+").matcher(q);
        while (num.find()) {
            tokens.add(num.group());
        }
        String[] dict = {"校园", "学生", "大学", "青春", "风险", "零资费", "低效", "5g", "套餐",
                "家庭", "融合", "宽带", "提速", "在售", "在架", "上线", "模板", "资费", "方案", "配置"};
        for (String word : dict) {
            if (q.contains(word)) {
                tokens.add(word);
            }
        }
        return new ArrayList<>(tokens);
    }

    /**
     * 月费容差匹配："39左右/上下/附近"等语义提取目标资费，与商品月费比较。
     * <p>返回值：-1 表示查询无费用意图；否则返回月费差值（绝对值），用于按接近度排序与阈值过滤。
     */
    private double feeIntentDiff(String q, Map<String, Object> offering) {
        Matcher num = Pattern.compile("(?:月费|月租|资费)?(\\d{1,4})\\s*(?:元)?(?:左右|上下|附近|上下浮动|之间)?").matcher(q);
        double target = -1;
        while (num.find()) {
            String g = num.group(1);
            if (!g.isBlank()) {
                int v = Integer.parseInt(g);
                if (v >= 5 && v <= 999) {
                    target = v;
                    break;
                }
            }
        }
        if (target < 0) {
            return -1;
        }
        double fee = MapOps.num(offering.get("monthlyFee"), MapOps.num(offering.get("fixedFeeAmount"), -1));
        if (fee < 0) {
            return -1;
        }
        return Math.abs(fee - target);
    }

    private int matchScore(String query, Map<String, Object> offering) {
        if (query == null || query.isBlank()) {
            return 1;
        }
        String q = query.toLowerCase(Locale.ROOT);
        String id = MapOps.str(offering.get("offeringId")).toLowerCase(Locale.ROOT);
        String name = MapOps.str(offering.get("offeringName")).toLowerCase(Locale.ROOT);
        String cat = MapOps.str(offering.get("category")).toLowerCase(Locale.ROOT);
        String type = MapOps.str(offering.get("offeringType")).toLowerCase(Locale.ROOT);
        String rootKey = MapOps.str(offering.get("messageRootKey")).toLowerCase(Locale.ROOT);
        String catCode = MapOps.str(offering.get("categoryCode")).toLowerCase(Locale.ROOT);
        String catName = MapOps.str(offering.get("categoryName")).toLowerCase(Locale.ROOT);
        String productLine = MapOps.str(offering.get("productLine")).toLowerCase(Locale.ROOT);
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
        double feeDiff = feeIntentDiff(q, offering);
        for (String token : extractQueryTokens(q)) {
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
            if (token.matches("\\d+") && (MapOps.str(offering.get("monthlyFee")).contains(token)
                    || MapOps.str(offering.get("fixedFeeAmount")).contains(token))) {
                score += 30;
            }
        }
        if (feeDiff >= 0) {
            if (feeDiff <= 5) {
                score += 45;
            } else if (feeDiff <= 10) {
                score += 25;
            } else if (feeDiff <= 20) {
                score += 10;
            } else {
                // 费用意图与商品资费严重偏离（>20 元）：强过滤。
                return -1;
            }
        }
        if (q.contains("在售") || q.contains("在架") || q.contains("上线")) {
            if ("上架".equals(MapOps.str(offering.get("state")))) {
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
        String id = MapOps.str(o.get("offeringId"));
        Object fee = MapOps.firstNonEmpty(o.get("fixedFeeAmount"), o.get("monthlyFee"));
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
                + " | " + MapOps.firstNonEmpty(o.get("categoryName"), o.get("messageRootKey"), o.get("offeringType")));
        row.put("template", o.get("basedOnTemplate"));
        row.put("source", "shelf");
        return row;
    }
}
