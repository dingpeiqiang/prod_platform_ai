package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.common.MapOps;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 运营稽核引擎（R2 Phase6 从 {@link ProductOntologyService} 拆出）。
 * <p>职责单一：运营大屏（getOpsDashboard / getOpsRevenueOverview）、
 * 异动告警（listOpsAlerts / buildAnomalyAlerts）、批量风险稽核（runBatchRiskAudit / getLastBatchAudit）、
 * 单品归因（analyzeRootCause R-A01~A05，SWRL 优先 Java 回退）、
 * 风险稽核（auditRisks R-B01~B05）与假设评估（evaluateHypothetical）。
 * <p>事实图读取、工单查询经函数式回调由宿主提供；风险规则经 {@link RiskRulesSupplier} 回调读取，
 * 保持图缓存与规则所有权不外泄。
 */
public class OpsRiskAuditEngine {

    private static final Logger log = LoggerFactory.getLogger(OpsRiskAuditEngine.class);

    private final ObjectMapper objectMapper;
    private final OpsSwrlReasoner opsSwrlReasoner;
    private final OpsRulesService opsRules;
    private final RiskAuditService riskAudit;
    private final OntologyVersionService versionService;

    /** 事实图读取回调（宿主 graphCache 单源）。 */
    @FunctionalInterface
    public interface GraphSupplier {
        Map<String, Object> loadGraph();
    }

    /** 风险规则回调（graph 默认 + 文件默认 + overrides 合并，宿主单源）。 */
    @FunctionalInterface
    public interface RiskRulesSupplier {
        Map<String, Object> get();
    }

    /** 工单总数回调（大屏展示用）。 */
    @FunctionalInterface
    public interface WorkOrderCounter {
        long count();
    }

    /** 响应模式元信息回调（demoMode/dataSource 注入）。 */
    @FunctionalInterface
    public interface ModeMetaApplier {
        Map<String, Object> apply(Map<String, Object> body);
    }

    private final GraphSupplier graphSupplier;
    private final RiskRulesSupplier riskRulesSupplier;
    private final WorkOrderCounter workOrderCounter;
    private final ModeMetaApplier modeMetaApplier;

    /** 最近一次批量稽核快照（内存态；表 B 落盘后重启可回读）。 */
    private final AtomicReference<Map<String, Object>> lastBatchAudit = new AtomicReference<>(new LinkedHashMap<>());

    public OpsRiskAuditEngine(ObjectMapper objectMapper,
                              OpsSwrlReasoner opsSwrlReasoner,
                              OpsRulesService opsRules,
                              RiskAuditService riskAudit,
                              OntologyVersionService versionService,
                              GraphSupplier graphSupplier,
                              RiskRulesSupplier riskRulesSupplier,
                              WorkOrderCounter workOrderCounter,
                              ModeMetaApplier modeMetaApplier) {
        this.objectMapper = objectMapper;
        this.opsSwrlReasoner = opsSwrlReasoner;
        this.opsRules = opsRules;
        this.riskAudit = riskAudit;
        this.versionService = versionService;
        this.graphSupplier = graphSupplier;
        this.riskRulesSupplier = riskRulesSupplier;
        this.workOrderCounter = workOrderCounter;
        this.modeMetaApplier = modeMetaApplier;
    }

    private Map<String, Object> loadGraph() {
        return graphSupplier.loadGraph();
    }

    private Map<String, Object> riskRules() {
        return riskRulesSupplier.get();
    }

    private Map<String, Object> withModeMeta(Map<String, Object> body) {
        return modeMetaApplier == null ? body : modeMetaApplier.apply(body);
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        return MapOps.deepCopy(objectMapper, source);
    }

    /**
     * 运营大屏：风险稽核 + 异动告警 + 最近批量稽核摘要聚合视图。
     */
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
        body.put("workOrderCount", workOrderCounter.count());
        body.put("lastBatchAudit", lastBatchAuditSummary());
        return withModeMeta(body);
    }

    /**
     * 运营大屏·收入与规模总览：从事实图 shelfOfferings 聚合 30 天真实指标。
     * <p>
     * 返回结构：
     * <ul>
     *   <li>totals：revenue30d（元）/ sales30d（单）/ offeringCount / activeOfferingCount</li>
     *   <li>items[]：每个在架商品的 {offeringId, offeringName, monthlyFee, revenue30d, sales30d, shelfDays, state}</li>
     *   <li>anomalyAlertCount：异动告警数（供大屏预警角标）</li>
     * </ul>
     * 注：同比/累计值需外部数仓口径，本期不返回；前端按"仅展示有值指标"降级。
     */
    public Map<String, Object> getOpsRevenueOverview() {
        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> offerings = MapOps.castListOfMaps(graph.get("shelfOfferings"));

        long revenue30d = 0L;
        long sales30d = 0L;
        int activeCount = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> o : offerings) {
            long rev = MapOps.toLong(o.get("revenue30d"));
            long sales = MapOps.toLong(o.get("salesCnt30d"));
            revenue30d += rev;
            sales30d += sales;
            if (sales > 0) {
                activeCount++;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("offeringId", MapOps.str(o.get("offeringId")));
            item.put("offeringName", MapOps.str(o.get("offeringName")));
            item.put("state", MapOps.str(o.get("state")));
            item.put("monthlyFee", o.get("monthlyFee"));
            item.put("revenue30d", rev);
            item.put("sales30d", sales);
            item.put("shelfDays", MapOps.toLong(o.get("shelfDays")));
            items.add(item);
        }
        items.sort((a, b) -> Long.compare(MapOps.toLong(b.get("revenue30d")), MapOps.toLong(a.get("revenue30d"))));

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("revenue30d", revenue30d);
        totals.put("sales30d", sales30d);
        totals.put("offeringCount", items.size());
        totals.put("activeOfferingCount", activeCount);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("totals", totals);
        body.put("items", items);
        body.put("anomalyAlertCount", buildAnomalyAlerts().size());
        body.put("generatedAt", Instant.now().toString());
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
                    .filter(a -> oid.equals(MapOps.str(a.get("offeringId"))))
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
        lastBatchAudit.set(snapshot);
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
        Map<String, Object> memSnapshot = lastBatchAudit.get();
        boolean available = (dbSnapshot != null && !dbSnapshot.isEmpty())
                || (memSnapshot != null && !memSnapshot.isEmpty());
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
                body.put("items", memSnapshot.get("items"));
            }
        }
        return withModeMeta(body);
    }

    private Map<String, Object> lastBatchAuditSummary() {
        Map<String, Object> snapshot = lastBatchAudit.get();
        if (snapshot == null || snapshot.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>(snapshot);
        summary.remove("items");
        return summary;
    }

    /** 从 opsGraph 指标事实生成异动告警，无事实则不造假 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildAnomalyAlerts() {
        Map<String, Object> graph = loadGraph();
        Map<String, Object> opsGraph = MapOps.castMap(graph.get("opsGraph"));
        List<Map<String, Object>> shelf = MapOps.castListOfMaps(graph.get("shelfOfferings"));
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Map.Entry<String, Object> e : opsGraph.entrySet()) {
            String oid = e.getKey();
            Map<String, Object> node = MapOps.castMap(e.getValue());
            String name = shelf.stream()
                    .filter(o -> oid.equals(MapOps.str(o.get("offeringId"))))
                    .map(o -> MapOps.str(o.get("offeringName")))
                    .filter(s -> !s.isBlank())
                    .findFirst()
                    .orElse(oid);
            for (Map<String, Object> m : MapOps.castListOfMaps(node.get("metrics"))) {
                if (!isMetricAnomaly(m)) continue;
                String metric = MapOps.str(m.get("metricCode"));
                double delta = MapOps.num(m.get("metricDelta"), Double.NaN);
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

    public Map<String, Object> analyzeRootCause(String offeringId) {
        return analyzeRootCause(offeringId, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeRootCause(String offeringId, String text) {
        String oid = resolveOfferingId(offeringId, text);
        if (oid == null) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", MapOps.empty(offeringId) && MapOps.empty(text)
                    ? "请提供产商品编码或名称"
                    : "无法从请求解析产商品，请使用在架编码或完整商品名称");
            fail.put("offeringId", offeringId);
            fail.put("query", text);
            return fail;
        }

        Map<String, Object> graph = loadGraph();
        Map<String, Object> node = MapOps.castMap(MapOps.castMap(graph.get("opsGraph")).get(oid));
        Map<String, Object> offering = MapOps.castListOfMaps(graph.get("shelfOfferings")).stream()
                .filter(o -> oid.equals(MapOps.str(o.get("offeringId"))))
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
                ? opsSwrlReasoner.reasonRootCause(oid, MapOps.str(offering.get("offeringName")), node, a01, a02, a03, a04, a05)
                : OpsSwrlReasoner.SwrlFireResult.skipJava("规则配置为 java，跳过 SWRL");
        if (swrl.success() && "openllet-swrl".equals(swrl.engine())) {
            reasonEngine = "openllet-swrl";
            swrlFired.addAll(swrl.firedRules());
            anomalies.addAll(swrl.anomalies());
            for (Map<String, Object> chCand : swrl.channelCandidates()) {
                Map<String, Object> c = new LinkedHashMap<>(chCand);
                double orderDelta = MapOps.num(c.get("orderDelta"), 0);
                double contrib = MapOps.num(c.get("contribRatio"), 0);
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
                triples.add(MapOps.triple(oid, "soldOn", c.get("id")));
                triples.add(MapOps.triple(c.get("id"), "orderDelta", orderDelta));
                triples.add(MapOps.triple(c.get("id"), "contribRatio", contrib));
            }
            for (Map<String, Object> prCand : swrl.promotionCandidates()) {
                Map<String, Object> c = enrichPromoCandidate(oid, prCand);
                candidates.add(c);
                triples.add(MapOps.triple(oid, "participatesIn", c.get("id")));
                triples.add(MapOps.triple(c.get("id"), "daysToExpire", c.get("daysToExpire")));
                triples.add(MapOps.triple(c.get("id"), "drivenOrderRatio", c.get("drivenOrderRatio")));
            }
            for (Map<String, Object> cpCand : swrl.competitorCandidates()) {
                Map<String, Object> c = enrichCompetitorCandidate(oid, cpCand);
                candidates.add(c);
                triples.add(MapOps.triple(oid, "competesWith", c.get("id")));
                triples.add(MapOps.triple(c.get("id"), "priceGap", c.get("priceGap")));
                triples.add(MapOps.triple(c.get("id"), "penetrationDeltaPp", c.get("penetrationDeltaPp")));
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
                for (Map<String, Object> m : MapOps.castListOfMaps(node.get("metrics"))) {
                    if (!isMetricAnomaly(m)) continue;
                    Object deltaObj = m.get("metricDelta");
                    if (deltaObj != null) {
                        double delta = MapOps.num(deltaObj, 0);
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
                for (Map<String, Object> ch : MapOps.castListOfMaps(node.get("channels"))) {
                    double orderDelta = MapOps.num(ch.get("orderDelta"), 0);
                    double contrib = MapOps.num(ch.get("contribRatio"), 0);
                    if (orderDelta <= orderDeltaLte && contrib >= contribGte) {
                        double weight = MapOps.num(ch.get("weightHint"), contrib);
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
                        triples.add(MapOps.triple(oid, "soldOn", ch.get("channelId")));
                        triples.add(MapOps.triple(ch.get("channelId"), "orderDelta", orderDelta));
                        triples.add(MapOps.triple(ch.get("channelId"), "contribRatio", contrib));
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
            for (Map<String, Object> pr : MapOps.castListOfMaps(node.get("promotions"))) {
                int days = (int) MapOps.num(pr.get("daysToExpire"), 999);
                double driven = MapOps.num(pr.get("drivenOrderRatio"), 0);
                if (days <= daysLte && driven >= drivenGte) {
                    double weight = MapOps.num(pr.get("weightHint"), driven);
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
                    triples.add(MapOps.triple(oid, "participatesIn", pr.get("promoId")));
                    triples.add(MapOps.triple(pr.get("promoId"), "daysToExpire", days));
                    triples.add(MapOps.triple(pr.get("promoId"), "drivenOrderRatio", driven));
                }
            }
        }

        if (!swrlRootOk && opsRules.isRuleEnabled(a04)) {
            double gapGte = opsRules.ruleNum(a04, "priceGapRatioGte", 0.15);
            double penetGt = opsRules.ruleNum(a04, "penetrationDeltaPpGt", 0);
            for (Map<String, Object> cp : MapOps.castListOfMaps(node.get("competitors"))) {
                double gapRatio = MapOps.num(cp.get("priceGapRatio"), 0);
                double penet = MapOps.num(cp.get("penetrationDeltaPp"), 0);
                if (gapRatio >= gapGte && penet > penetGt) {
                    double weight = MapOps.num(cp.get("weightHint"), Math.round(gapRatio * 100.0) / 100.0);
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
                    triples.add(MapOps.triple(oid, "competesWith", cp.get("competitorId")));
                    triples.add(MapOps.triple(cp.get("competitorId"), "priceGap", cp.get("priceGap")));
                    triples.add(MapOps.triple(cp.get("competitorId"), "penetrationDeltaPp", cp.get("penetrationDeltaPp")));
                }
            }
        }

        if (!swrlRootOk && opsRules.isRuleEnabled(a05)) {
            double minWeight = opsRules.ruleNum(a05, "minWeightHint", 0.08);
            for (Map<String, Object> ub : MapOps.castListOfMaps(node.get("behaviors"))) {
                double weight = MapOps.num(ub.get("weightHint"), minWeight);
                if (weight < minWeight) continue;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("type", "UserBehavior");
                c.put("id", ub.get("behaviorId"));
                c.put("name", ub.get("name"));
                c.put("score", weight);
                c.put("weight", weight);
                c.put("ruleId", "R-A05");
                c.put("engine", "java-rules");
                c.put("evidence", List.of(MapOps.str(ub.get("name")), "行为佐证"));
                candidates.add(enrichBehaviorCandidate(oid, c));
            }
        }

        int topN = opsRules.rootCauseTopN();
        candidates.sort((a, b) -> Double.compare(MapOps.num(b.get("score"), 0), MapOps.num(a.get("score"), 0)));
        List<Map<String, Object>> top3 = candidates.stream().limit(Math.max(1, topN)).collect(Collectors.toList());

        Map<String, Object> suggestionsMap = MapOps.castMap(graph.get("actionSuggestions"));
        List<String> actionList = new ArrayList<>();
        for (Map<String, Object> c : top3) {
            for (Object a : MapOps.castList(suggestionsMap.get(MapOps.str(c.get("type"))))) {
                String action = MapOps.str(a);
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
        anomalies.forEach(a -> applied.add(MapOps.str(a.get("ruleId"))));
        top3.forEach(c -> applied.add(MapOps.str(c.get("ruleId"))));
        if (Boolean.TRUE.equals(a05.get("includeWhenRanked"))
                && top3.stream().anyMatch(c -> "R-A05".equals(MapOps.str(c.get("ruleId"))))) {
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
        body.put("entityNames", buildEntityNameMap(node, offering));
        body.put("reportEvidence", reportEvidence);
        body.put("market", MapOps.castMap(node.get("market")));
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
        List<Map<String, Object>> shelf = MapOps.castListOfMaps(mutated.get("shelfOfferings"));
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> o : shelf) {
            byId.put(MapOps.str(o.get("offeringId")), o);
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
            List<Map<String, Object>> items = MapOps.castListOfMaps(beforeScan.get("items"));
            for (Map<String, Object> item : items) {
                if (Boolean.TRUE.equals(item.get("suggestDelist"))
                        || "HIGH".equals(MapOps.str(item.get("riskLevel")))) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("offeringId", MapOps.str(item.get("offeringId")));
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
            String oid = MapOps.str(patch.getOrDefault("offeringId",
                    patch.getOrDefault("entity_id", patch.get("entityId"))));
            if (oid.isBlank()) {
                continue;
            }
            Map<String, Object> offering = byId.get(oid);
            if (offering == null) {
                continue;
            }
            Map<String, Object> beforeRow = new LinkedHashMap<>(offering);
            Map<String, Object> changes = MapOps.castMap(patch.get("changes"));
            if (changes.isEmpty() && ("delist".equals(safeMode) || "退市".equals(safeMode))) {
                changes = Map.of("state", "下架");
            }
            if (changes.isEmpty() && "price".equals(safeMode)) {
                double fee = MapOps.num(offering.get("monthlyFee"), 0);
                changes = Map.of("monthlyFee", fee <= 0 ? 19 : fee);
            }
            offering.putAll(changes);
            // 下架后不再计入在架稽核：标记 state
            if ("下架".equals(MapOps.str(offering.get("state"))) || "停售".equals(MapOps.str(offering.get("state")))) {
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
        List<Map<String, Object>> afterShelf = MapOps.castListOfMaps(afterGraph.get("shelfOfferings"));
        afterShelf.removeIf(o -> {
            String st = MapOps.str(o.get("state"));
            return "下架".equals(st) || "停售".equals(st) || "已退市".equals(st);
        });
        afterGraph.put("shelfOfferings", afterShelf);
        Map<String, Object> after = auditRisksOn(afterGraph, null);

        List<Map<String, Object>> impacts = new ArrayList<>();
        for (Map<String, Object> row : applied) {
            Map<String, Object> beforeSnap = MapOps.castMap(row.get("before"));
            double revenue = MapOps.num(beforeSnap.get("revenue30d"), 0);
            double sales = MapOps.num(beforeSnap.get("salesCnt30d"), 0);
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
                .mapToDouble(i -> Math.abs(MapOps.num(i.get("revenueImpact30d"), 0)))
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
        List<Map<String, Object>> allOfferings = MapOps.castListOfMaps(graph.get("shelfOfferings"));
        // 仅稽核在架（上架）商品；已下架不计入
        List<Map<String, Object>> onShelf = allOfferings.stream()
                .filter(o -> {
                    String st = MapOps.str(o.get("state"));
                    return st.isBlank() || "上架".equals(st) || "在售".equals(st);
                })
                .collect(Collectors.toList());
        int scannedCount = onShelf.size();
        List<Map<String, Object>> offerings = onShelf;
        if (offeringIds != null && !offeringIds.isEmpty()) {
            Set<String> idSet = new LinkedHashSet<>(offeringIds);
            offerings = onShelf.stream()
                    .filter(o -> idSet.contains(MapOps.str(o.get("offeringId"))))
                    .collect(Collectors.toList());
        }

        Set<String> whitelist = MapOps.castList(graph.get("equityGiftWhitelist")).stream()
                .map(MapOps::str)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Object> riskActions = MapOps.castMap(graph.get("riskActions"));
        int zeroShelfDays = (int) MapOps.num(rules.get("zeroSalesShelfDays"), 180);
        int reviewDays = (int) MapOps.num(rules.get("highRiskReviewDays"), 30);
        double lowPct = MapOps.num(rules.get("lowRevenuePercentile"), 0.05);

        List<Double> allRevenues = onShelf.stream()
                .map(o -> MapOps.num(o.get("revenue30d"), 0))
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

            double monthly = MapOps.num(o.get("monthlyFee"), -1);
            double oneTime = MapOps.num(o.get("oneTimeFee"), 0);
            String wlTag = MapOps.str(o.get("whitelistTag"));
            String name = MapOps.str(o.get("offeringName"));
            boolean inWhitelist = whitelist.contains(wlTag)
                    || whitelist.stream().anyMatch(name::contains);
            String state = MapOps.str(o.get("state"));
            boolean offeringOnShelf = state.isBlank() || "上架".equals(state) || "在售".equals(state);
            String category = MapOps.str(o.get("category"));
            boolean lowEffCategory = lowCats.contains(category);
            boolean lowRevenue = MapOps.num(o.get("revenue30d"), 0) <= lowThreshold;

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
                        String feature = MapOps.str(risk.get("feature"));
                        // 与 Java 路径一致：零元资费处置话术挂在 B02（无合约在架）上
                        if ("零元资费".equals(feature)
                                && risks.stream().noneMatch(r -> "R-B02".equals(MapOps.str(r.get("ruleId"))))) {
                            continue;
                        }
                        Map<String, Object> act = MapOps.castMap(riskActions.get(feature));
                        if (!act.isEmpty()) {
                            actions.add(MapOps.str(act.getOrDefault("defaultAction", feature)));
                        } else if ("预警升级".equals(feature)) {
                            actions.add("紧急复核");
                        }
                    }
                    if (risks.stream().anyMatch(r -> "R-B01".equals(MapOps.str(r.get("ruleId")))
                            && "零元资费".equals(MapOps.str(r.get("feature"))))) {
                        evidenceTriples.add(MapOps.triple(o.get("offeringId"), "hasPricePlan", "PP-" + o.get("offeringId")));
                        evidenceTriples.add(MapOps.triple("PP-" + o.get("offeringId"), "monthlyFee", 0));
                        evidenceTriples.add(MapOps.triple("PP-" + o.get("offeringId"), "oneTimeFee", 0));
                    }
                    if (risks.stream().anyMatch(r -> "R-B02".equals(MapOps.str(r.get("ruleId"))))) {
                        evidenceTriples.add(MapOps.triple(o.get("offeringId"), "hasContract", false));
                    }
                    if (risks.stream().anyMatch(r -> "R-B03".equals(MapOps.str(r.get("ruleId"))))) {
                        evidenceTriples.add(MapOps.triple(o.get("offeringId"), "salesCnt30d", 0));
                        evidenceTriples.add(MapOps.triple(o.get("offeringId"), "shelfDays", o.get("shelfDays")));
                    }
                } else {
                    swrlFail++;
                }
            }

            if (!usedSwrl) {
                if (opsRules.isRuleEnabled(b01)
                        && monthly == 0 && oneTime == 0 && !inWhitelist) {
                    risks.add(riskFeature("R-B01", "零元资费", "月费与一次性费均为0且非权益赠送白名单"));
                    evidenceTriples.add(MapOps.triple(o.get("offeringId"), "hasPricePlan", "PP-" + o.get("offeringId")));
                    evidenceTriples.add(MapOps.triple("PP-" + o.get("offeringId"), "monthlyFee", 0));
                    evidenceTriples.add(MapOps.triple("PP-" + o.get("offeringId"), "oneTimeFee", 0));
                    if (opsRules.isRuleEnabled(b02)
                            && offeringOnShelf
                            && !MapOps.truthy(o.get("hasContract"))) {
                        risks.add(riskFeature("R-B02", "零元无合约在架", "零元资费已上架且无合约约束"));
                        evidenceTriples.add(MapOps.triple(o.get("offeringId"), "hasContract", false));
                        riskLevel = "HIGH";
                        Map<String, Object> act = MapOps.castMap(riskActions.get("零元资费"));
                        actions.add(MapOps.str(act.getOrDefault("defaultAction", "建议立即下架或转验证渠道")));
                    }
                }

                double fullDiscGte = opsRules.ruleNum(b01, "fullDiscountPercentGte", 100);
                if (opsRules.isRuleEnabled(b01)
                        && MapOps.num(o.get("discountPercent"), -1) >= fullDiscGte
                        && MapOps.truthy(o.get("repeatable"))
                        && MapOps.empty(o.get("targetCustomerGroup"))) {
                    risks.add(riskFeature("R-B01", "异常全额赠送", "折扣100% + 可重复订购 + 无目标客户群"));
                    riskLevel = "HIGH";
                    Map<String, Object> act = MapOps.castMap(riskActions.get("异常全额赠送"));
                    actions.add(MapOps.str(act.getOrDefault("defaultAction", "限售 + 复核优惠规则")));
                }

                if (opsRules.isRuleEnabled(b03)
                        && MapOps.num(o.get("salesCnt30d"), -1) == 0 && MapOps.num(o.get("shelfDays"), 0) > zeroShelfDays) {
                    risks.add(riskFeature("R-B03", "长期零销",
                            "近30日销量0且在架" + o.get("shelfDays") + "天（阈值>" + zeroShelfDays + "）"));
                    if (!"HIGH".equals(riskLevel)) {
                        riskLevel = "MEDIUM";
                    }
                    Map<String, Object> act = MapOps.castMap(riskActions.get("长期零销"));
                    actions.add(MapOps.str(act.getOrDefault("defaultAction", "建议下架/归档")));
                    suggestDelist = true;
                    evidenceTriples.add(MapOps.triple(o.get("offeringId"), "salesCnt30d", 0));
                    evidenceTriples.add(MapOps.triple(o.get("offeringId"), "shelfDays", o.get("shelfDays")));
                }

                if (opsRules.isRuleEnabled(b04)
                        && lowEffCategory
                        && lowRevenue
                        && !MapOps.truthy(o.get("strategicTag"))) {
                    risks.add(riskFeature("R-B04", "低效产商品", "近90日收入贡献排名后5%且无战略标签"));
                    if ("LOW".equals(riskLevel)) {
                        riskLevel = "MEDIUM";
                    }
                    Map<String, Object> act = MapOps.castMap(riskActions.get("低效产商品"));
                    actions.add(MapOps.str(act.getOrDefault("defaultAction", "纳入优胜劣汰池")));
                    suggestDelist = true;
                }

                if (opsRules.isRuleEnabled(b05)
                        && "HIGH".equals(riskLevel) && MapOps.num(o.get("shelfDays"), 0) > reviewDays) {
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

    /**
     * 归因响应实体 ID → 中文名映射（来源事实图节点，供前端替代静态词典翻译）。
     */
    private Map<String, Object> buildEntityNameMap(Map<String, Object> node, Map<String, Object> offering) {
        Map<String, Object> names = new LinkedHashMap<>();
        if (offering != null) {
            names.put(MapOps.str(offering.get("offeringId")), MapOps.str(offering.get("offeringName")));
        }
        for (Map<String, Object> ch : MapOps.castListOfMaps(node.get("channels"))) {
            if (ch.get("channelId") != null && ch.get("name") != null) {
                names.put(MapOps.str(ch.get("channelId")), MapOps.str(ch.get("name")));
            }
        }
        for (Map<String, Object> pr : MapOps.castListOfMaps(node.get("promotions"))) {
            if (pr.get("promoId") != null && pr.get("name") != null) {
                names.put(MapOps.str(pr.get("promoId")), MapOps.str(pr.get("name")));
            }
        }
        for (Map<String, Object> cp : MapOps.castListOfMaps(node.get("competitors"))) {
            if (cp.get("competitorId") != null && cp.get("name") != null) {
                names.put(MapOps.str(cp.get("competitorId")), MapOps.str(cp.get("name")));
            }
        }
        for (Map<String, Object> ub : MapOps.castListOfMaps(node.get("behaviors"))) {
            if (ub.get("behaviorId") != null && ub.get("name") != null) {
                names.put(MapOps.str(ub.get("behaviorId")), MapOps.str(ub.get("name")));
            }
        }
        Map<String, Object> market = MapOps.castMap(node.get("market"));
        if (market.get("scopeId") != null && market.get("name") != null) {
            names.put(MapOps.str(market.get("scopeId")), MapOps.str(market.get("name")));
        }
        return names;
    }

    private Map<String, Object> riskFeature(String ruleId, String feature, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("feature", feature);
        row.put("message", message);
        return row;
    }

    private boolean isMetricAnomaly(Map<String, Object> metric) {
        Map<String, Object> a01 = opsRules.rootCauseRule("R-A01");
        if (!opsRules.isRuleEnabled(a01)) {
            return false;
        }
        Object deltaObj = metric.get("metricDelta");
        double deltaLte = opsRules.ruleNum(a01, "metricDeltaLte", -0.10);
        if (deltaObj != null && MapOps.num(deltaObj, 0) <= deltaLte) {
            return true;
        }
        boolean honorFlag = a01.get("honorAnomalyFlag") == null || MapOps.truthy(a01.get("honorAnomalyFlag"));
        return honorFlag && MapOps.truthy(metric.get("anomaly"));
    }

    /**
     * 按编码或自然语言解析产商品（与 {@link OntologyGraphManager#resolveOfferingId} 同源的宿主回调版本）。
     */
    private String resolveOfferingId(String offeringId, String text) {
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
}
