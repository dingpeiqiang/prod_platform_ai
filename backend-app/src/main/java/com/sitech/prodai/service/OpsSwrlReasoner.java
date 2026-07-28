package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLDArgument;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 产商品运营 OWL/SWRL 推理（Openllet）。
 * <ul>
 *   <li>异动归因 R-A01~A05</li>
 *   <li>风险稽核 R-B01~B05（白名单/分位等预计算布尔由调用方断言）</li>
 * </ul>
 * 阈值与启用开关来自 {@link OpsRulesService}（ops_rules.json）。
 */
@Service
public class OpsSwrlReasoner {

    private static final Logger log = LoggerFactory.getLogger(OpsSwrlReasoner.class);
    private static final String NS = "http://prodai.sitech.com/ontology/product-ops#";
    private static final IRI SWRLB_LTE = IRI.create("http://www.w3.org/2003/11/swrlb#lessThanOrEqual");
    private static final IRI SWRLB_GTE = IRI.create("http://www.w3.org/2003/11/swrlb#greaterThanOrEqual");
    private static final IRI SWRLB_GT = IRI.create("http://www.w3.org/2003/11/swrlb#greaterThan");
    private static final IRI SWRLB_EQ = IRI.create("http://www.w3.org/2003/11/swrlb#equal");

    private final ResourceLoader resourceLoader;
    private final ProdAiProperties properties;

    public OpsSwrlReasoner(ResourceLoader resourceLoader, ProdAiProperties properties) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    public record SwrlFireResult(
            boolean success,
            String engine,
            boolean anomalyFired,
            List<Map<String, Object>> anomalies,
            List<Map<String, Object>> channelCandidates,
            List<Map<String, Object>> promotionCandidates,
            List<Map<String, Object>> competitorCandidates,
            List<Map<String, Object>> behaviorCandidates,
            List<String> firedRules,
            String message
    ) {
        public static SwrlFireResult disabled(String message) {
            return new SwrlFireResult(false, "disabled", false,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), message);
        }

        public static SwrlFireResult skipJava(String message) {
            return new SwrlFireResult(false, "java-rules", false,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), message);
        }
    }

    public record SwrlRiskResult(
            boolean success,
            String engine,
            List<Map<String, Object>> risks,
            List<String> firedRules,
            String riskLevel,
            boolean suggestDelist,
            boolean urgent,
            String message
    ) {
        public static SwrlRiskResult fail(String engine, String message) {
            return new SwrlRiskResult(false, engine, List.of(), List.of(), "LOW", false, false, message);
        }
    }

    /**
     * 对单个产商品 opsGraph 节点执行 R-A01~A05 SWRL 推理。
     */
    public SwrlFireResult reasonRootCause(
            String offeringId,
            String offeringName,
            Map<String, Object> node,
            Map<String, Object> a01,
            Map<String, Object> a02,
            Map<String, Object> a03,
            Map<String, Object> a04,
            Map<String, Object> a05
    ) {
        if (!properties.getOntology().isSwrlEnabled()) {
            return SwrlFireResult.disabled("SWRL 已关闭");
        }
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLDataFactory df = manager.getOWLDataFactory();
            OWLOntology ontology = loadBaseOntology(manager);

            OWLNamedIndividual offeringInd = ind(df, "offering/" + offeringId);
            add(manager, ontology, df.getOWLClassAssertionAxiom(cls(df, "Offering"), offeringInd));
            add(manager, ontology, dataAssert(df, offeringInd, "offeringId", offeringId));
            if (offeringName != null && !offeringName.isBlank()) {
                add(manager, ontology, dataAssert(df, offeringInd, "offeringName", offeringName));
            }

            List<Map<String, Object>> metricFacts = castListOfMaps(node.get("metrics"));
            int metricIdx = 0;
            for (Map<String, Object> m : metricFacts) {
                String mid = "metric-" + offeringId + "-" + (metricIdx++);
                OWLNamedIndividual metricInd = ind(df, "metric/" + mid);
                add(manager, ontology, df.getOWLClassAssertionAxiom(cls(df, "Metric"), metricInd));
                add(manager, ontology, objectAssert(df, offeringInd, "hasMetric", metricInd));
                add(manager, ontology, dataAssert(df, metricInd, "metricCode", str(m.get("metricCode"))));
                if (m.get("metricDelta") != null) {
                    add(manager, ontology, dataAssertDecimal(df, metricInd, "metricDelta", num(m.get("metricDelta"))));
                }
                if (m.get("metricDeltaPp") != null) {
                    add(manager, ontology, dataAssertDecimal(df, metricInd, "metricDeltaPp", num(m.get("metricDeltaPp"))));
                }
                if (truthy(m.get("anomaly"))) {
                    add(manager, ontology, dataAssertBoolean(df, metricInd, "anomalyFlag", true));
                }
            }

            Map<String, Map<String, Object>> channelById = assertChannels(manager, ontology, df, offeringInd, node);
            Map<String, Map<String, Object>> promoById = assertPromotions(manager, ontology, df, offeringInd, node);
            Map<String, Map<String, Object>> competitorById = assertCompetitors(manager, ontology, df, offeringInd, node);
            Map<String, Map<String, Object>> behaviorById = assertBehaviors(manager, ontology, df, offeringInd, node);

            boolean a01Enabled = enabled(a01);
            boolean a02Enabled = enabled(a02);
            boolean a03Enabled = enabled(a03);
            boolean a04Enabled = enabled(a04);
            boolean a05Enabled = enabled(a05);

            double metricDeltaLte = ruleNum(a01, "metricDeltaLte", -0.10);
            boolean honorFlag = a01 == null || a01.get("honorAnomalyFlag") == null || truthy(a01.get("honorAnomalyFlag"));
            double orderDeltaLte = ruleNum(a02, "orderDeltaLte", -0.20);
            double contribGte = ruleNum(a02, "contribRatioGte", 0.30);
            double daysLte = ruleNum(a03, "daysToExpireLte", 7);
            double drivenGte = ruleNum(a03, "drivenOrderRatioGte", 0.25);
            double gapGte = ruleNum(a04, "priceGapRatioGte", 0.15);
            double penetGt = ruleNum(a04, "penetrationDeltaPpGt", 0);
            double minWeight = ruleNum(a05, "minWeightHint", 0.08);

            if (a01Enabled) {
                add(manager, ontology, buildRa01DeltaRule(df, metricDeltaLte));
                if (honorFlag) {
                    add(manager, ontology, buildRa01FlagRule(df));
                }
            }
            if (a02Enabled) {
                add(manager, ontology, buildRa02Rule(df, orderDeltaLte, contribGte));
            }
            if (a03Enabled) {
                add(manager, ontology, buildRa03Rule(df, daysLte, drivenGte));
            }
            if (a04Enabled) {
                add(manager, ontology, buildRa04Rule(df, gapGte, penetGt));
            }
            if (a05Enabled) {
                add(manager, ontology, buildRa05Rule(df, minWeight));
            }

            OpenlletReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
            reasoner.prepareReasoner();

            boolean anomalyFired = reasoner.getTypes(offeringInd, true).containsEntity(cls(df, "AnomalyOffering"));
            List<String> fired = new ArrayList<>();
            List<Map<String, Object>> anomalies = new ArrayList<>();
            if (anomalyFired && a01Enabled) {
                fired.add("R-A01");
                for (Map<String, Object> m : metricFacts) {
                    boolean hit = false;
                    Object deltaObj = m.get("metricDelta");
                    if (deltaObj != null && num(deltaObj) <= metricDeltaLte) {
                        hit = true;
                    } else if (honorFlag && truthy(m.get("anomaly"))) {
                        hit = true;
                    }
                    if (!hit) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("metricCode", m.get("metricCode"));
                    row.put("metricValue", m.get("metricValue"));
                    if (deltaObj != null) {
                        double delta = num(deltaObj);
                        row.put("metricDelta", delta);
                        row.put("message", m.get("metricCode") + "环比 " + Math.round(delta * 100) + "%");
                    } else {
                        row.put("metricDeltaPp", m.get("metricDeltaPp"));
                        row.put("message", m.get("metricCode") + "异动 " + m.get("metricDeltaPp") + "pp");
                    }
                    row.put("ruleId", "R-A01");
                    row.put("anomalyFlag", true);
                    row.put("engine", "openllet-swrl");
                    anomalies.add(row);
                }
            }

            List<Map<String, Object>> channelCandidates = List.of();
            List<Map<String, Object>> promotionCandidates = List.of();
            List<Map<String, Object>> competitorCandidates = List.of();
            List<Map<String, Object>> behaviorCandidates = List.of();

            if (anomalyFired) {
                if (a02Enabled) {
                    Set<String> hitIds = instanceLocalIds(reasoner, df, "ChannelRootCause");
                    if (!hitIds.isEmpty()) fired.add("R-A02");
                    channelCandidates = buildChannelCandidates(hitIds, channelById);
                }
                if (a03Enabled) {
                    Set<String> hitIds = instanceLocalIds(reasoner, df, "PromotionRootCause");
                    if (!hitIds.isEmpty()) fired.add("R-A03");
                    promotionCandidates = buildPromoCandidates(hitIds, promoById, daysLte);
                }
                if (a04Enabled) {
                    Set<String> hitIds = instanceLocalIds(reasoner, df, "CompetitorRootCause");
                    if (!hitIds.isEmpty()) fired.add("R-A04");
                    competitorCandidates = buildCompetitorCandidates(hitIds, competitorById);
                }
                if (a05Enabled) {
                    Set<String> hitIds = instanceLocalIds(reasoner, df, "BehaviorRootCause");
                    if (!hitIds.isEmpty()) fired.add("R-A05");
                    behaviorCandidates = buildBehaviorCandidates(hitIds, behaviorById);
                }
            }

            reasoner.dispose();
            return new SwrlFireResult(
                    true,
                    "openllet-swrl",
                    anomalyFired,
                    anomalies,
                    channelCandidates,
                    promotionCandidates,
                    competitorCandidates,
                    behaviorCandidates,
                    fired,
                    anomalyFired ? "SWRL 归因推理完成" : "SWRL 未点燃异动（R-A01）"
            );
        } catch (Exception e) {
            log.warn("[OpsSwrlReasoner] 归因 SWRL 失败，将回退 Java: {}", e.getMessage());
            return new SwrlFireResult(false, "fallback-java", false,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    "SWRL 失败: " + e.getMessage());
        }
    }

    /** 兼容旧调用：仅 A01/A02。 */
    public SwrlFireResult reasonRootCauseA01A02(
            String offeringId,
            String offeringName,
            Map<String, Object> node,
            Map<String, Object> a01,
            Map<String, Object> a02
    ) {
        return reasonRootCause(offeringId, offeringName, node, a01, a02, Map.of("enabled", false),
                Map.of("enabled", false), Map.of("enabled", false));
    }

    /**
     * 单商品风险 SWRL。调用方需传入预计算布尔：inWhitelist / lowEffCategory / lowRevenue 等。
     *
     * @param offering 货架行
     * @param flags    预计算：inWhitelist, onShelf, lowEffCategoryFlag, lowRevenueFlag
     * @param rules    R-B01~B05 配置 + defaults（zeroSalesShelfDays / highRiskReviewDays）
     */
    public SwrlRiskResult reasonRiskOffering(
            Map<String, Object> offering,
            Map<String, Object> flags,
            Map<String, Object> b01,
            Map<String, Object> b02,
            Map<String, Object> b03,
            Map<String, Object> b04,
            Map<String, Object> b05,
            Map<String, Object> riskDefaults
    ) {
        if (!properties.getOntology().isSwrlEnabled()) {
            return SwrlRiskResult.fail("disabled", "SWRL 已关闭");
        }
        String offeringId = str(offering.get("offeringId"));
        if (offeringId.isBlank()) {
            return SwrlRiskResult.fail("invalid", "offeringId 为空");
        }
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLDataFactory df = manager.getOWLDataFactory();
            OWLOntology ontology = loadBaseOntology(manager);

            OWLNamedIndividual offeringInd = ind(df, "offering/" + offeringId);
            add(manager, ontology, df.getOWLClassAssertionAxiom(cls(df, "Offering"), offeringInd));
            add(manager, ontology, dataAssert(df, offeringInd, "offeringId", offeringId));
            add(manager, ontology, dataAssertDecimal(df, offeringInd, "monthlyFee", num(offering.get("monthlyFee"))));
            add(manager, ontology, dataAssertDecimal(df, offeringInd, "oneTimeFee", num(offering.get("oneTimeFee"))));
            add(manager, ontology, dataAssertDecimal(df, offeringInd, "salesCnt30d", num(offering.get("salesCnt30d"))));
            add(manager, ontology, dataAssertDecimal(df, offeringInd, "shelfDays", num(offering.get("shelfDays"))));
            add(manager, ontology, dataAssertDecimal(df, offeringInd, "revenue30d", num(offering.get("revenue30d"))));
            add(manager, ontology, dataAssertDecimal(df, offeringInd, "discountPercent",
                    offering.get("discountPercent") == null ? -1 : num(offering.get("discountPercent"))));
            add(manager, ontology, dataAssertBoolean(df, offeringInd, "hasContract", truthy(offering.get("hasContract"))));
            add(manager, ontology, dataAssertBoolean(df, offeringInd, "onShelf", truthy(flags.get("onShelf"))));
            add(manager, ontology, dataAssertBoolean(df, offeringInd, "inWhitelist", truthy(flags.get("inWhitelist"))));
            add(manager, ontology, dataAssertBoolean(df, offeringInd, "repeatableFlag", truthy(offering.get("repeatable"))));
            add(manager, ontology, dataAssertBoolean(df, offeringInd, "emptyTargetGroup",
                    offering.get("targetCustomerGroup") == null
                            || str(offering.get("targetCustomerGroup")).isBlank()));
            add(manager, ontology, dataAssertBoolean(df, offeringInd, "strategicTagFlag",
                    truthy(offering.get("strategicTag"))));
            add(manager, ontology, dataAssertBoolean(df, offeringInd, "lowEffCategoryFlag",
                    truthy(flags.get("lowEffCategoryFlag"))));
            add(manager, ontology, dataAssertBoolean(df, offeringInd, "lowRevenueFlag",
                    truthy(flags.get("lowRevenueFlag"))));

            double fullDiscGte = ruleNum(b01, "fullDiscountPercentGte", 100);
            double zeroShelfDays = ruleNum(riskDefaults, "zeroSalesShelfDays", 180);
            double reviewDays = ruleNum(riskDefaults, "highRiskReviewDays", 30);

            if (enabled(b01)) {
                add(manager, ontology, buildRb01ZeroFeeRule(df));
                add(manager, ontology, buildRb01DiscountRule(df, fullDiscGte));
            }
            if (enabled(b02)) {
                add(manager, ontology, buildRb02Rule(df));
            }
            if (enabled(b03)) {
                add(manager, ontology, buildRb03Rule(df, zeroShelfDays));
            }
            if (enabled(b04)) {
                add(manager, ontology, buildRb04Rule(df));
            }
            // HighRisk 归类：零元无合约 / 异常折扣
            add(manager, ontology, buildHighRiskFromB02(df));
            add(manager, ontology, buildHighRiskFromDiscount(df));
            if (enabled(b05)) {
                add(manager, ontology, buildRb05Rule(df, reviewDays));
            }

            OpenlletReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
            reasoner.prepareReasoner();

            List<Map<String, Object>> risks = new ArrayList<>();
            List<String> fired = new ArrayList<>();
            String riskLevel = "LOW";
            boolean suggestDelist = false;
            boolean urgent = false;

            if (isType(reasoner, offeringInd, df, "ZeroFeeRiskOffering")) {
                fired.add("R-B01");
                risks.add(riskFeature("R-B01", "零元资费", "月费与一次性费均为0且非权益赠送白名单"));
            }
            if (isType(reasoner, offeringInd, df, "AbnormalDiscountOffering")) {
                if (!fired.contains("R-B01")) fired.add("R-B01");
                risks.add(riskFeature("R-B01", "异常全额赠送", "折扣100% + 可重复订购 + 无目标客户群"));
                riskLevel = "HIGH";
            }
            if (isType(reasoner, offeringInd, df, "ZeroFeeNoContractOffering")) {
                fired.add("R-B02");
                risks.add(riskFeature("R-B02", "零元无合约在架", "零元资费已上架且无合约约束"));
                riskLevel = "HIGH";
            }
            if (isType(reasoner, offeringInd, df, "ZeroSalesOffering")) {
                fired.add("R-B03");
                risks.add(riskFeature("R-B03", "长期零销",
                        "近30日销量0且在架" + offering.get("shelfDays") + "天（阈值>" + (int) zeroShelfDays + "）"));
                if (!"HIGH".equals(riskLevel)) riskLevel = "MEDIUM";
                suggestDelist = true;
            }
            if (isType(reasoner, offeringInd, df, "LowEffOffering")) {
                fired.add("R-B04");
                risks.add(riskFeature("R-B04", "低效产商品", "近90日收入贡献排名后5%且无战略标签"));
                if ("LOW".equals(riskLevel)) riskLevel = "MEDIUM";
                suggestDelist = true;
            }
            if (isType(reasoner, offeringInd, df, "UrgentReviewOffering")) {
                fired.add("R-B05");
                risks.add(riskFeature("R-B05", "预警升级", "高风险且上架超过" + (int) reviewDays + "天未复核"));
                urgent = true;
                riskLevel = "HIGH";
            }

            reasoner.dispose();
            return new SwrlRiskResult(true, "openllet-swrl", risks, fired, riskLevel, suggestDelist, urgent,
                    risks.isEmpty() ? "SWRL 未命中风险" : "SWRL 风险推理完成");
        } catch (Exception e) {
            log.warn("[OpsSwrlReasoner] 风险 SWRL 失败 offering={}: {}", offeringId, e.getMessage());
            return SwrlRiskResult.fail("fallback-java", "SWRL 失败: " + e.getMessage());
        }
    }

    // ---------- ABox helpers ----------

    private Map<String, Map<String, Object>> assertChannels(
            OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory df,
            OWLNamedIndividual offeringInd, Map<String, Object> node) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> ch : castListOfMaps(node.get("channels"))) {
            String cid = str(ch.get("channelId"));
            if (cid.isBlank()) continue;
            byId.put(cid, ch);
            OWLNamedIndividual chInd = ind(df, "channel/" + cid);
            add(manager, ontology, df.getOWLClassAssertionAxiom(cls(df, "Channel"), chInd));
            add(manager, ontology, objectAssert(df, offeringInd, "soldOn", chInd));
            add(manager, ontology, dataAssert(df, chInd, "channelId", cid));
            add(manager, ontology, dataAssert(df, chInd, "channelName", str(ch.get("name"))));
            add(manager, ontology, dataAssertDecimal(df, chInd, "orderDelta", num(ch.get("orderDelta"))));
            add(manager, ontology, dataAssertDecimal(df, chInd, "contribRatio", num(ch.get("contribRatio"))));
            if (ch.get("weightHint") != null) {
                add(manager, ontology, dataAssertDecimal(df, chInd, "weightHint", num(ch.get("weightHint"))));
            }
        }
        return byId;
    }

    private Map<String, Map<String, Object>> assertPromotions(
            OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory df,
            OWLNamedIndividual offeringInd, Map<String, Object> node) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> pr : castListOfMaps(node.get("promotions"))) {
            String pid = str(pr.get("promoId"));
            if (pid.isBlank()) continue;
            byId.put(pid, pr);
            OWLNamedIndividual pInd = ind(df, "promo/" + pid);
            add(manager, ontology, df.getOWLClassAssertionAxiom(cls(df, "Promotion"), pInd));
            add(manager, ontology, objectAssert(df, offeringInd, "participatesIn", pInd));
            add(manager, ontology, dataAssert(df, pInd, "promoId", pid));
            add(manager, ontology, dataAssert(df, pInd, "promoName", str(pr.get("name"))));
            add(manager, ontology, dataAssertDecimal(df, pInd, "daysToExpire", num(pr.get("daysToExpire"), 999)));
            add(manager, ontology, dataAssertDecimal(df, pInd, "drivenOrderRatio", num(pr.get("drivenOrderRatio"))));
            if (pr.get("weightHint") != null) {
                add(manager, ontology, dataAssertDecimal(df, pInd, "weightHint", num(pr.get("weightHint"))));
            }
        }
        return byId;
    }

    private Map<String, Map<String, Object>> assertCompetitors(
            OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory df,
            OWLNamedIndividual offeringInd, Map<String, Object> node) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> cp : castListOfMaps(node.get("competitors"))) {
            String cid = str(cp.get("competitorId"));
            if (cid.isBlank()) continue;
            byId.put(cid, cp);
            OWLNamedIndividual cInd = ind(df, "competitor/" + cid);
            add(manager, ontology, df.getOWLClassAssertionAxiom(cls(df, "Competitor"), cInd));
            add(manager, ontology, objectAssert(df, offeringInd, "competesWith", cInd));
            add(manager, ontology, dataAssert(df, cInd, "competitorId", cid));
            add(manager, ontology, dataAssert(df, cInd, "competitorName", str(cp.get("name"))));
            add(manager, ontology, dataAssertDecimal(df, cInd, "priceGap", num(cp.get("priceGap"))));
            add(manager, ontology, dataAssertDecimal(df, cInd, "priceGapRatio", num(cp.get("priceGapRatio"))));
            add(manager, ontology, dataAssertDecimal(df, cInd, "penetrationDeltaPp", num(cp.get("penetrationDeltaPp"))));
            if (cp.get("weightHint") != null) {
                add(manager, ontology, dataAssertDecimal(df, cInd, "weightHint", num(cp.get("weightHint"))));
            }
        }
        return byId;
    }

    private Map<String, Map<String, Object>> assertBehaviors(
            OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory df,
            OWLNamedIndividual offeringInd, Map<String, Object> node) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> ub : castListOfMaps(node.get("behaviors"))) {
            String bid = str(ub.get("behaviorId"));
            if (bid.isBlank()) continue;
            byId.put(bid, ub);
            OWLNamedIndividual bInd = ind(df, "behavior/" + bid);
            add(manager, ontology, df.getOWLClassAssertionAxiom(cls(df, "UserBehavior"), bInd));
            add(manager, ontology, objectAssert(df, offeringInd, "influencedBy", bInd));
            add(manager, ontology, dataAssert(df, bInd, "behaviorId", bid));
            add(manager, ontology, dataAssert(df, bInd, "behaviorName", str(ub.get("name"))));
            add(manager, ontology, dataAssertDecimal(df, bInd, "weightHint", num(ub.get("weightHint"))));
        }
        return byId;
    }

    // ---------- Candidate builders ----------

    private List<Map<String, Object>> buildChannelCandidates(Set<String> hitIds, Map<String, Map<String, Object>> byId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String cid : hitIds) {
            Map<String, Object> ch = byId.get(cid);
            if (ch == null) continue;
            double orderDelta = num(ch.get("orderDelta"));
            double contrib = num(ch.get("contribRatio"));
            double weight = ch.get("weightHint") != null ? num(ch.get("weightHint")) : contrib;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("type", "Channel");
            c.put("id", cid);
            c.put("name", ch.get("name"));
            c.put("score", weight);
            c.put("weight", weight);
            c.put("ruleId", "R-A02");
            c.put("engine", "openllet-swrl");
            c.put("evidence", List.of(
                    "订购量变化 " + Math.round(orderDelta * 100) + "%",
                    "渠道贡献占比 " + Math.round(contrib * 100) + "%"
            ));
            c.put("orderDelta", orderDelta);
            c.put("contribRatio", contrib);
            c.put("trend", ch.get("trend"));
            out.add(c);
        }
        return out;
    }

    private List<Map<String, Object>> buildPromoCandidates(
            Set<String> hitIds, Map<String, Map<String, Object>> byId, double daysLte) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String pid : hitIds) {
            Map<String, Object> pr = byId.get(pid);
            if (pr == null) continue;
            int days = (int) num(pr.get("daysToExpire"), 999);
            double driven = num(pr.get("drivenOrderRatio"));
            double weight = pr.get("weightHint") != null ? num(pr.get("weightHint")) : driven;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("type", "Promotion");
            c.put("id", pid);
            c.put("name", pr.get("name"));
            c.put("score", weight);
            c.put("weight", weight);
            c.put("ruleId", "R-A03");
            c.put("engine", "openllet-swrl");
            c.put("evidence", List.of(days + " 日后到期", "历史带动订购占比 " + Math.round(driven * 100) + "%"));
            c.put("daysToExpire", days);
            c.put("drivenOrderRatio", driven);
            out.add(c);
        }
        return out;
    }

    private List<Map<String, Object>> buildCompetitorCandidates(Set<String> hitIds, Map<String, Map<String, Object>> byId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String cid : hitIds) {
            Map<String, Object> cp = byId.get(cid);
            if (cp == null) continue;
            double gapRatio = num(cp.get("priceGapRatio"));
            double weight = cp.get("weightHint") != null
                    ? num(cp.get("weightHint"))
                    : Math.round(gapRatio * 100.0) / 100.0;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("type", "Competitor");
            c.put("id", cid);
            c.put("name", cp.get("name"));
            c.put("score", weight);
            c.put("weight", weight);
            c.put("ruleId", "R-A04");
            c.put("engine", "openllet-swrl");
            c.put("evidence", List.of(
                    "月费低 " + cp.get("priceGap") + " 元（约 " + String.format("%.1f", gapRatio * 100) + "%）",
                    "本地渗透率 +" + cp.get("penetrationDeltaPp") + "pp"
            ));
            c.put("priceGap", cp.get("priceGap"));
            c.put("priceGapRatio", gapRatio);
            c.put("penetrationDeltaPp", cp.get("penetrationDeltaPp"));
            out.add(c);
        }
        return out;
    }

    private List<Map<String, Object>> buildBehaviorCandidates(Set<String> hitIds, Map<String, Map<String, Object>> byId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String bid : hitIds) {
            Map<String, Object> ub = byId.get(bid);
            if (ub == null) continue;
            double weight = num(ub.get("weightHint"));
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("type", "UserBehavior");
            c.put("id", bid);
            c.put("name", ub.get("name"));
            c.put("score", weight);
            c.put("weight", weight);
            c.put("ruleId", "R-A05");
            c.put("engine", "openllet-swrl");
            c.put("evidence", List.of(str(ub.get("name")), "行为佐证"));
            out.add(c);
        }
        return out;
    }

    private Map<String, Object> riskFeature(String ruleId, String feature, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("feature", feature);
        row.put("message", message);
        row.put("engine", "openllet-swrl");
        return row;
    }

    // ---------- SWRL rules: root cause ----------

    private SWRLRule buildRa01DeltaRule(OWLDataFactory df, double metricDeltaLte) {
        SWRLVariable o = var(df, "o");
        SWRLVariable m = var(df, "m");
        SWRLVariable d = var(df, "d");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "Offering"), o));
        body.add(df.getSWRLObjectPropertyAtom(objProp(df, "hasMetric"), o, m));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "metricDelta"), m, d));
        body.add(df.getSWRLBuiltInAtom(SWRLB_LTE, List.of(d, litArg(df, metricDeltaLte))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "AnomalyOffering"), o)));
    }

    private SWRLRule buildRa01FlagRule(OWLDataFactory df) {
        SWRLVariable o = var(df, "o2");
        SWRLVariable m = var(df, "m2");
        SWRLVariable f = var(df, "f2");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "Offering"), o));
        body.add(df.getSWRLObjectPropertyAtom(objProp(df, "hasMetric"), o, m));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "anomalyFlag"), m, f));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(f, df.getSWRLLiteralArgument(df.getOWLLiteral(true)))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "AnomalyOffering"), o)));
    }

    private SWRLRule buildRa02Rule(OWLDataFactory df, double orderDeltaLte, double contribGte) {
        SWRLVariable o = var(df, "o3");
        SWRLVariable c = var(df, "c3");
        SWRLVariable od = var(df, "od3");
        SWRLVariable cr = var(df, "cr3");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "AnomalyOffering"), o));
        body.add(df.getSWRLObjectPropertyAtom(objProp(df, "soldOn"), o, c));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "orderDelta"), c, od));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "contribRatio"), c, cr));
        body.add(df.getSWRLBuiltInAtom(SWRLB_LTE, List.of(od, litArg(df, orderDeltaLte))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_GTE, List.of(cr, litArg(df, contribGte))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "ChannelRootCause"), c)));
    }

    private SWRLRule buildRa03Rule(OWLDataFactory df, double daysLte, double drivenGte) {
        SWRLVariable o = var(df, "o4");
        SWRLVariable p = var(df, "p4");
        SWRLVariable d = var(df, "d4");
        SWRLVariable r = var(df, "r4");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "AnomalyOffering"), o));
        body.add(df.getSWRLObjectPropertyAtom(objProp(df, "participatesIn"), o, p));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "daysToExpire"), p, d));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "drivenOrderRatio"), p, r));
        body.add(df.getSWRLBuiltInAtom(SWRLB_LTE, List.of(d, litArg(df, daysLte))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_GTE, List.of(r, litArg(df, drivenGte))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "PromotionRootCause"), p)));
    }

    private SWRLRule buildRa04Rule(OWLDataFactory df, double gapGte, double penetGt) {
        SWRLVariable o = var(df, "o5");
        SWRLVariable c = var(df, "c5");
        SWRLVariable g = var(df, "g5");
        SWRLVariable p = var(df, "p5");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "AnomalyOffering"), o));
        body.add(df.getSWRLObjectPropertyAtom(objProp(df, "competesWith"), o, c));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "priceGapRatio"), c, g));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "penetrationDeltaPp"), c, p));
        body.add(df.getSWRLBuiltInAtom(SWRLB_GTE, List.of(g, litArg(df, gapGte))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_GT, List.of(p, litArg(df, penetGt))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "CompetitorRootCause"), c)));
    }

    private SWRLRule buildRa05Rule(OWLDataFactory df, double minWeight) {
        SWRLVariable o = var(df, "o6");
        SWRLVariable b = var(df, "b6");
        SWRLVariable w = var(df, "w6");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "AnomalyOffering"), o));
        body.add(df.getSWRLObjectPropertyAtom(objProp(df, "influencedBy"), o, b));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "weightHint"), b, w));
        body.add(df.getSWRLBuiltInAtom(SWRLB_GTE, List.of(w, litArg(df, minWeight))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "BehaviorRootCause"), b)));
    }

    // ---------- SWRL rules: risk ----------

    private SWRLRule buildRb01ZeroFeeRule(OWLDataFactory df) {
        SWRLVariable o = var(df, "rb1o");
        SWRLVariable mf = var(df, "rb1mf");
        SWRLVariable ot = var(df, "rb1ot");
        SWRLVariable wl = var(df, "rb1wl");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "Offering"), o));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "monthlyFee"), o, mf));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "oneTimeFee"), o, ot));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "inWhitelist"), o, wl));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(mf, litArg(df, 0))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(ot, litArg(df, 0))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(wl, df.getSWRLLiteralArgument(df.getOWLLiteral(false)))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "ZeroFeeRiskOffering"), o)));
    }

    private SWRLRule buildRb01DiscountRule(OWLDataFactory df, double fullDiscGte) {
        SWRLVariable o = var(df, "rb1do");
        SWRLVariable d = var(df, "rb1dd");
        SWRLVariable r = var(df, "rb1dr");
        SWRLVariable e = var(df, "rb1de");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "Offering"), o));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "discountPercent"), o, d));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "repeatableFlag"), o, r));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "emptyTargetGroup"), o, e));
        body.add(df.getSWRLBuiltInAtom(SWRLB_GTE, List.of(d, litArg(df, fullDiscGte))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(r, df.getSWRLLiteralArgument(df.getOWLLiteral(true)))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(e, df.getSWRLLiteralArgument(df.getOWLLiteral(true)))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "AbnormalDiscountOffering"), o)));
    }

    private SWRLRule buildRb02Rule(OWLDataFactory df) {
        SWRLVariable o = var(df, "rb2o");
        SWRLVariable s = var(df, "rb2s");
        SWRLVariable c = var(df, "rb2c");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "ZeroFeeRiskOffering"), o));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "onShelf"), o, s));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "hasContract"), o, c));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(s, df.getSWRLLiteralArgument(df.getOWLLiteral(true)))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(c, df.getSWRLLiteralArgument(df.getOWLLiteral(false)))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "ZeroFeeNoContractOffering"), o)));
    }

    private SWRLRule buildRb03Rule(OWLDataFactory df, double zeroShelfDays) {
        SWRLVariable o = var(df, "rb3o");
        SWRLVariable sales = var(df, "rb3s");
        SWRLVariable days = var(df, "rb3d");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "Offering"), o));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "salesCnt30d"), o, sales));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "shelfDays"), o, days));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(sales, litArg(df, 0))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_GT, List.of(days, litArg(df, zeroShelfDays))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "ZeroSalesOffering"), o)));
    }

    private SWRLRule buildRb04Rule(OWLDataFactory df) {
        SWRLVariable o = var(df, "rb4o");
        SWRLVariable lowCat = var(df, "rb4c");
        SWRLVariable lowRev = var(df, "rb4r");
        SWRLVariable strat = var(df, "rb4s");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "Offering"), o));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "lowEffCategoryFlag"), o, lowCat));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "lowRevenueFlag"), o, lowRev));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "strategicTagFlag"), o, strat));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(lowCat, df.getSWRLLiteralArgument(df.getOWLLiteral(true)))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(lowRev, df.getSWRLLiteralArgument(df.getOWLLiteral(true)))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_EQ, List.of(strat, df.getSWRLLiteralArgument(df.getOWLLiteral(false)))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "LowEffOffering"), o)));
    }

    private SWRLRule buildHighRiskFromB02(OWLDataFactory df) {
        SWRLVariable o = var(df, "hr1");
        return df.getSWRLRule(
                Set.of(df.getSWRLClassAtom(cls(df, "ZeroFeeNoContractOffering"), o)),
                Set.of(df.getSWRLClassAtom(cls(df, "HighRiskOffering"), o)));
    }

    private SWRLRule buildHighRiskFromDiscount(OWLDataFactory df) {
        SWRLVariable o = var(df, "hr2");
        return df.getSWRLRule(
                Set.of(df.getSWRLClassAtom(cls(df, "AbnormalDiscountOffering"), o)),
                Set.of(df.getSWRLClassAtom(cls(df, "HighRiskOffering"), o)));
    }

    private SWRLRule buildRb05Rule(OWLDataFactory df, double reviewDays) {
        SWRLVariable o = var(df, "rb5o");
        SWRLVariable days = var(df, "rb5d");
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "HighRiskOffering"), o));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "shelfDays"), o, days));
        body.add(df.getSWRLBuiltInAtom(SWRLB_GT, List.of(days, litArg(df, reviewDays))));
        return df.getSWRLRule(body, Set.of(df.getSWRLClassAtom(cls(df, "UrgentReviewOffering"), o)));
    }

    // ---------- OWL helpers ----------

    private OWLOntology loadBaseOntology(OWLOntologyManager manager) throws Exception {
        Resource resource = resourceLoader.getResource(properties.getOntology().getProductOpsOwlPath());
        try (InputStream in = resource.getInputStream()) {
            return manager.loadOntologyFromOntologyDocument(in);
        }
    }

    private void add(OWLOntologyManager manager, OWLOntology ontology, org.semanticweb.owlapi.model.OWLAxiom axiom) {
        manager.applyChange(new AddAxiom(ontology, axiom));
    }

    private Set<String> instanceLocalIds(OpenlletReasoner reasoner, OWLDataFactory df, String classLocal) {
        NodeSet<OWLNamedIndividual> nodes = reasoner.getInstances(cls(df, classLocal), true);
        Set<String> ids = new LinkedHashSet<>();
        for (OWLNamedIndividual ind : nodes.getFlattened()) {
            String iri = ind.getIRI().toString();
            ids.add(iri.contains("/") ? iri.substring(iri.lastIndexOf('/') + 1) : iri);
        }
        return ids;
    }

    private boolean isType(OpenlletReasoner reasoner, OWLNamedIndividual ind, OWLDataFactory df, String classLocal) {
        return reasoner.getTypes(ind, true).containsEntity(cls(df, classLocal));
    }

    private OWLNamedIndividual ind(OWLDataFactory df, String local) {
        return df.getOWLNamedIndividual(IRI.create(NS + local));
    }

    private SWRLVariable var(OWLDataFactory df, String name) {
        return df.getSWRLVariable(IRI.create(NS + "var/" + name));
    }

    private SWRLDArgument litArg(OWLDataFactory df, double value) {
        return df.getSWRLLiteralArgument(decimalLiteral(df, value));
    }

    private OWLClass cls(OWLDataFactory df, String local) {
        return df.getOWLClass(IRI.create(NS + local));
    }

    private OWLObjectProperty objProp(OWLDataFactory df, String local) {
        return df.getOWLObjectProperty(IRI.create(NS + local));
    }

    private OWLDataProperty dataProp(OWLDataFactory df, String local) {
        return df.getOWLDataProperty(IRI.create(NS + local));
    }

    private OWLObjectPropertyAssertionAxiom objectAssert(
            OWLDataFactory df, OWLNamedIndividual s, String prop, OWLNamedIndividual o) {
        return df.getOWLObjectPropertyAssertionAxiom(objProp(df, prop), s, o);
    }

    private OWLDataPropertyAssertionAxiom dataAssert(OWLDataFactory df, OWLNamedIndividual s, String prop, String value) {
        return df.getOWLDataPropertyAssertionAxiom(
                dataProp(df, prop), s, df.getOWLLiteral(value == null ? "" : value, OWL2Datatype.XSD_STRING));
    }

    private OWLDataPropertyAssertionAxiom dataAssertDecimal(
            OWLDataFactory df, OWLNamedIndividual s, String prop, double value) {
        return df.getOWLDataPropertyAssertionAxiom(dataProp(df, prop), s, decimalLiteral(df, value));
    }

    private OWLDataPropertyAssertionAxiom dataAssertBoolean(
            OWLDataFactory df, OWLNamedIndividual s, String prop, boolean value) {
        return df.getOWLDataPropertyAssertionAxiom(dataProp(df, prop), s, df.getOWLLiteral(value));
    }

    private OWLLiteral decimalLiteral(OWLDataFactory df, double value) {
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value);
        return df.getOWLLiteral(bd.toPlainString(), OWL2Datatype.XSD_DECIMAL);
    }

    private boolean enabled(Map<String, Object> rule) {
        if (rule == null || rule.isEmpty()) return true;
        Object enabled = rule.get("enabled");
        return enabled == null || truthy(enabled);
    }

    private double ruleNum(Map<String, Object> rule, String key, double defaultValue) {
        if (rule == null || !rule.containsKey(key) || rule.get(key) == null) return defaultValue;
        return num(rule.get(key));
    }

    private double num(Object value) {
        return num(value, 0);
    }

    private double num(Object value, double defaultValue) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(String.valueOf(value).replace("%", "").trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value == null) return false;
        String s = String.valueOf(value).trim().toLowerCase();
        return Set.of("1", "true", "yes", "y", "是").contains(s);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }
}
