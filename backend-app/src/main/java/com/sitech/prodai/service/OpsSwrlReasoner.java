package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.common.MapOps;
import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLClassAtom;
import org.semanticweb.owlapi.model.SWRLRule;
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
import java.util.function.Predicate;

/**
 * 产商品运营 OWL/SWRL 推理（Openllet）。
 * <ul>
 *   <li>异动归因 R-A01~A05</li>
 *   <li>风险稽核 R-B01~B05（白名单/分位等预计算布尔由调用方断言）</li>
 *   <li>运行告警分级 R-E00~E04</li>
 * </ul>
 * 规则骨架内置于 product.ttl（swrl:Imp + :thresholdValue 命名阈值个体），
 * 运行时按 ops_rules.json / ops_rules.alertGrading 覆写阈值个体并关闭未启用规则，
 * 规则本体无需重建；阈值与启用开关来自 {@link OpsRulesService}。
 */
@Service
public class OpsSwrlReasoner {

    private static final Logger log = LoggerFactory.getLogger(OpsSwrlReasoner.class);
    private static final String NS = "http://prodai.sitech.com/ontology/product#";
    private static final String ALERT_NS = NS;

    /**
     * 规则 label 判定：兼容 product.ttl 的英文规则号与 scenarios TTL 的中文规则名。
     * 空白实体在「结论类已合并」后是唯一能区分具体规则的锚点。
     */
    private static final Predicate<String> RULE_B01_ZERO_FEE =
            labelMatches("R-B01.zeroFee", "零元资费风险");
    private static final Predicate<String> RULE_B01_DISCOUNT =
            labelMatches("R-B01.discount", "异常折扣定价");
    private static final Predicate<String> RULE_B02 =
            labelMatches("R-B02", "零元在架无合约");

    private static final Predicate<String> RULE_C03_MUTEX =
            labelMatches("R-C03.mutex", "互斥组冲突");
    private static final Predicate<String> RULE_C03_DISCOUNT =
            labelMatches("R-C03.discount", "R-C03.discountGift", "折扣赠费冲突");
    private static final Predicate<String> RULE_C04 =
            labelMatches("R-C04", "附加包缺主资费");
    private static final Predicate<String> RULE_C05 =
            labelMatches("R-C05", "零元高资费风险");
    private static final Predicate<String> RULE_C06_NAME =
            labelMatches("R-C06.name", "必填项-名称缺失");
    private static final Predicate<String> RULE_C06_CATEGORY =
            labelMatches("R-C06.category", "必填项-类别缺失");
    private static final Predicate<String> RULE_C06_FEE =
            labelMatches("R-C06.fee", "必填项-资费缺失");
    private static final Predicate<String> RULE_C06_CHANNEL =
            labelMatches("R-C06.channel", "必填项-渠道缺失");
    private static final Predicate<String> RULE_C07 =
            labelMatches("R-C07", "全额折扣可重复");
    private static final Predicate<String> RULE_C09_BELOW =
            labelMatches("R-C09.below", "固费低于下限");
    private static final Predicate<String> RULE_C09_ABOVE =
            labelMatches("R-C09.above", "固费高于上限");

    private static Predicate<String> labelMatches(String... candidates) {
        return label -> {
            for (String c : candidates) {
                if (c.equals(label)) return true;
            }
            return false;
        };
    }
    private static final String ALERT_OWL_PATH = "classpath:ontology/product.ttl";

    private final ResourceLoader resourceLoader;
    private final ProdAiProperties properties;
    private final OpsRulesService opsRules;
    /** 实例事实（ABox）实时读取：仅 {@link #reasonStoredConfigCompliance} 使用，可缺省。 */
    private final OntologyInstanceFactsService instanceFacts;

    public OpsSwrlReasoner(ResourceLoader resourceLoader, ProdAiProperties properties, OpsRulesService opsRules,
                           OntologyInstanceFactsService instanceFacts) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.opsRules = opsRules;
        this.instanceFacts = instanceFacts;
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

            Map<String, Double> thresholds = new LinkedHashMap<>();
            if (a01Enabled) {
                thresholds.put("th_RA01_metricDeltaLte", metricDeltaLte);
            }
            if (a02Enabled) {
                thresholds.put("th_RA02_orderDeltaLte", orderDeltaLte);
                thresholds.put("th_RA02_contribRatioGte", contribGte);
            }
            if (a03Enabled) {
                thresholds.put("th_RA03_daysToExpireLte", daysLte);
                thresholds.put("th_RA03_drivenOrderRatioGte", drivenGte);
            }
            if (a04Enabled) {
                thresholds.put("th_RA04_priceGapRatioGte", gapGte);
                thresholds.put("th_RA04_penetrationDeltaPpGt", penetGt);
            }
            if (a05Enabled) {
                thresholds.put("th_RA05_minWeightHint", minWeight);
            }
            applyThresholds(manager, ontology, df, thresholds);

            Set<String> disabledRules = new LinkedHashSet<>();
            if (!a01Enabled) {
                disabledRules.add("R-A01");
            } else if (!honorFlag) {
                disabledRules.add("R-A01.flag");
            }
            if (!a02Enabled) disabledRules.add("R-A02");
            if (!a03Enabled) disabledRules.add("R-A03");
            if (!a04Enabled) disabledRules.add("R-A04");
            if (!a05Enabled) disabledRules.add("R-A05");
            disableRules(manager, ontology, disabledRules);

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
     * @param b01~b05  R-B01~B05 配置 + defaults（zeroSalesShelfDays / highRiskReviewDays）
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

            Map<String, Double> thresholds = new LinkedHashMap<>();
            if (enabled(b01)) {
                thresholds.put("th_RB01_fullDiscountPercentGte", fullDiscGte);
            }
            if (enabled(b03)) {
                thresholds.put("th_RB03_zeroSalesShelfDays", zeroShelfDays);
            }
            if (enabled(b05)) {
                thresholds.put("th_RB05_highRiskReviewDays", reviewDays);
            }
            applyThresholds(manager, ontology, df, thresholds);

            Set<String> disabledRules = new LinkedHashSet<>();
            if (!enabled(b01)) {
                disabledRules.add("R-B01.zeroFee");
                disabledRules.add("R-B01.discount");
            }
            if (!enabled(b02)) disabledRules.add("R-B02");
            if (!enabled(b03)) disabledRules.add("R-B03");
            if (!enabled(b04)) disabledRules.add("R-B04");
            if (!enabled(b05)) disabledRules.add("R-B05");
            disableRules(manager, ontology, disabledRules);

            OpenlletReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
            reasoner.prepareReasoner();

            List<Map<String, Object>> risks = new ArrayList<>();
            List<String> fired = new ArrayList<>();
            String riskLevel = "LOW";
            boolean suggestDelist = false;
            boolean urgent = false;

            if (ruleFired(reasoner, offeringInd, df, ontology, RULE_B01_ZERO_FEE, "ZeroFeeRiskOffering")) {
                fired.add("R-B01");
                risks.add(riskFeature("R-B01", "零元资费", "月费与一次性费均为0且非权益赠送白名单"));
            }
            if (ruleFired(reasoner, offeringInd, df, ontology, RULE_B01_DISCOUNT, "AbnormalDiscountOffering")) {
                if (!fired.contains("R-B01")) fired.add("R-B01");
                risks.add(riskFeature("R-B01", "异常全额赠送", "折扣100% + 可重复订购 + 无目标客户群"));
                riskLevel = "HIGH";
            }
            if (ruleFired(reasoner, offeringInd, df, ontology, RULE_B02, "ZeroFeeNoContractOffering")) {
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

    // ---------- 配置合规（R-C03~C09） ----------

    /**
     * 配置合规 SWRL 结论。
     *
     * @param violations 逐条违规：{ruleId, conclusionClass, field, evidence}
     * @param reasoning  推理链：{ruleId, condition, conclusion} 逐规则可读说明
     */
    public record ConfigComplianceResult(
            boolean success,
            String engine,
            List<String> firedRules,
            List<Map<String, Object>> violations,
            List<Map<String, Object>> reasoning,
            boolean compliant,
            String message
    ) {
        public static ConfigComplianceResult fail(String engine, String message) {
            return new ConfigComplianceResult(false, engine, List.of(), List.of(), List.of(), false, message);
        }
    }

    /**
     * 配置草稿合规推理 —— 实例事实由存储实时装载。
     * <p>
     * 与 {@link #reasonConfigCompliance} 的唯一差别是事实来源：
     * 先经 {@link OntologyInstanceFactsService} 从 H2/MySQL 的
     * {@code pd_ai_ontology_instance} 实时读取该草稿的原始事实，再交给同一套 SWRL 流程。
     * 读取到的字段即「表字段」，仍由 {@code ops_rules.config.propertyMapping} 声明式映射到本体属性。
     *
     * @param draftId 草稿主键（pd_ai_ontology_instance.id）
     * @param graph   事实图（取 equityGiftWhitelist 等）
     * @return 实例不存在时返回 fallback 结果，不回退 Java 规则
     */
    public ConfigComplianceResult reasonStoredConfigCompliance(
            Long draftId,
            Map<String, Object> graph,
            Map<String, Object> c03,
            Map<String, Object> c04,
            Map<String, Object> c05,
            Map<String, Object> c06,
            Map<String, Object> c07,
            Map<String, Object> c08,
            Map<String, Object> c09,
            double feeMin,
            double feeMax
    ) {
        Map<String, Object> facts = instanceFacts == null ? Map.of() : instanceFacts.loadConfigFacts(draftId);
        if (facts.isEmpty()) {
            log.warn("[OpsSwrlReasoner] 实例事实为空，跳过推理 draftId={}", draftId);
            return ConfigComplianceResult.fail("no-instance",
                    "未找到配置实例事实：draftId=" + draftId);
        }
        return reasonConfigCompliance(facts, graph, c03, c04, c05, c06, c07, c08, c09, feeMin, feeMax);
    }

    /**
     * 配置草稿合规推理（R-C03~C09），基于 product.ttl 本体 SWRL 规则。
     * <p>
     * 分工：原表字段 → 本体数据属性的映射由 {@code ops_rules.config.propertyMapping} 声明式给出
     * （见 {@link #assertMappedFacts}），Java 侧不含逐字段硬编码；
     * 阈值比较与多条件合取由 SWRL 承担，违规归入 ConfigViolation 子类。
     * 合规由违规族查询结果给出（开放世界下补集无法证明「不存在其它违规」）。
     *
     * @param draft  配置草稿（已 applyCategoryDefaults）
     * @param graph  事实图（取 equityGiftWhitelist 等）
     * @param c03~c09 ops_rules.config.rules 对应规则（enabled / 阈值）
     */
    public ConfigComplianceResult reasonConfigCompliance(
            Map<String, Object> draft,
            Map<String, Object> graph,
            Map<String, Object> c03,
            Map<String, Object> c04,
            Map<String, Object> c05,
            Map<String, Object> c06,
            Map<String, Object> c07,
            Map<String, Object> c08,
            Map<String, Object> c09,
            double feeMin,
            double feeMax
    ) {
        if (!properties.getOntology().isSwrlEnabled()) {
            return ConfigComplianceResult.fail("disabled", "SWRL 已关闭");
        }
        String offeringId = str(MapOps.str(firstNonEmpty(draft.get("offeringId"), "draft")));
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLDataFactory df = manager.getOWLDataFactory();
            OWLOntology ontology = loadBaseOntology(manager);

            OWLNamedIndividual ind = ind(df, "config/" + offeringId);
            add(manager, ontology, df.getOWLClassAssertionAxiom(cls(df, "Offering"), ind));
            // 原始事实 → 本体属性：完全由 ops_rules.config.propertyMapping 声明，Java 不再逐字段硬编码
            assertMappedFacts(manager, ontology, df, ind, draft, graph,
                    castListOfMaps(opsRules.configPropertyMapping().get("properties")));

            double discount = normalizeDiscount(MapOps.num(
                    firstNonEmpty(draft.get("discountPercent"), draft.get("prefDiscount")), -1));

            // 阈值覆写（R-C07 / R-C09）
            Map<String, Double> thresholds = new LinkedHashMap<>();
            if (enabled(c07)) thresholds.put("th_RC07_discountFull", ruleNum(c07, "discountPercentGte", 100));
            if (enabled(c09)) {
                thresholds.put("th_RC09_feeMin", feeMin);
                thresholds.put("th_RC09_feeMax", feeMax);
            }
            applyThresholds(manager, ontology, df, thresholds);

            Set<String> disabledRules = new LinkedHashSet<>();
            if (!enabled(c03)) {
                disabledRules.add("R-C03.mutex");
                disabledRules.add("R-C03.discountGift");
            }
            if (!enabled(c04)) disabledRules.add("R-C04");
            if (!enabled(c05)) disabledRules.add("R-C05");
            if (!enabled(c06)) {
                disabledRules.add("R-C06.name");
                disabledRules.add("R-C06.category");
                disabledRules.add("R-C06.fee");
                disabledRules.add("R-C06.channel");
            }
            if (!enabled(c07)) disabledRules.add("R-C07");
            if (!enabled(c09)) {
                disabledRules.add("R-C09.below");
                disabledRules.add("R-C09.above");
            }
            disableRules(manager, ontology, disabledRules);

            // 单轮推理：违规类由 SWRL 推出，合规由 OWL 补集（C08Compliant ≡ ¬ConfigViolation）直接得出
            OpenlletReasoner reasoner = reasonerFor(ontology);
            reasoner.prepareReasoner();

            List<Map<String, Object>> violations = new ArrayList<>();
            List<Map<String, Object>> reasoning = new ArrayList<>();
            List<String> fired = new ArrayList<>();
            collectViolation(reasoner, df, ind, ontology, "R-C06", new String[]{"C06MissingRequired"},
                    "必填缺失",
                    c06, violations, reasoning, fired, fieldForC06(draft), RULE_C06_NAME, RULE_C06_CATEGORY,
                    RULE_C06_FEE, RULE_C06_CHANNEL);
            collectViolation(reasoner, df, ind, ontology, "R-C03",
                    new String[]{"C03MutualExclusiveConflict"}, "互斥/优惠冲突",
                    c03, violations, reasoning, fired,
                    !empty(draft.get("bindExistingMainPkg")) ? "mutexGroup" : "prefDiscount",
                    RULE_C03_MUTEX, RULE_C03_DISCOUNT);
            collectViolation(reasoner, df, ind, ontology, "R-C04", new String[]{"C04OrphanAddon"},
                    "附加缺依赖",
                    c04, violations, reasoning, fired, "dependOn", RULE_C04);
            collectViolation(reasoner, df, ind, ontology, "R-C05", new String[]{"C05HighRiskZeroFee"},
                    "高风险零固费",
                    c05, violations, reasoning, fired, "fixedFeeAmount", RULE_C05);
            collectViolation(reasoner, df, ind, ontology, "R-C07", new String[]{"C07AbnormalDiscount"},
                    "异常优惠漏洞",
                    c07, violations, reasoning, fired, "prefDiscount", RULE_C07);
            collectViolation(reasoner, df, ind, ontology, "R-C09", new String[]{"C09FeeRangeViolation"},
                    "资费区间违规",
                    c09, violations, reasoning, fired, "fixedFeeAmount", RULE_C09_BELOW, RULE_C09_ABOVE);

            // 合规判定：回读实例是否落入违规族 ConfigViolation（各违规规则的 SWRL head 显式断言该父类）。
            // 开放世界假设下 OWL 无法以补集证明「不存在其它违规」，故合规只能由违规族查询结果给出，
            // 而该查询结果完全来自本轮 SWRL 推理——外部不再注入任何判定布尔。
            boolean inViolationFamily = isType(reasoner, ind, df, "ConfigViolation");
            reasoner.dispose();

            boolean compliant = enabled(c08) && !inViolationFamily && violations.isEmpty();
            if (compliant) {
                fired.add("R-C08");
                reasoning.add(reasonRow("R-C08", "不属于 ConfigViolation 违规族", "C08Compliant 合规通过"));
            }
            return new ConfigComplianceResult(true, "openllet-swrl", fired, violations, reasoning, compliant,
                    compliant ? "SWRL 合规推理：通过" : "SWRL 合规推理：命中 " + violations.size() + " 项");
        } catch (Exception e) {
            log.warn("[OpsSwrlReasoner] 配置合规 SWRL 失败 offering={}: {}", offeringId, e.getMessage());
            return ConfigComplianceResult.fail("fallback-java", "SWRL 失败: " + e.getMessage());
        }
    }

    private OpenlletReasoner reasonerFor(OWLOntology ontology) {
        return OpenlletReasonerFactory.getInstance().createReasoner(ontology);
    }

    private void collectViolation(OpenlletReasoner reasoner, OWLDataFactory df, OWLNamedIndividual ind,
                                  OWLOntology ontology, String ruleId, String[] conclusionClasses, String label,
                                  Map<String, Object> rule,
                                  List<Map<String, Object>> violations, List<Map<String, Object>> reasoning,
                                  List<String> fired, String field, Predicate<String>... labelPredicates) {
        if (!enabled(rule)) return;
        boolean hit = false;
        for (Predicate<String> p : labelPredicates) {
            if (ruleFired(reasoner, ind, df, ontology, p, conclusionClasses)) {
                hit = true;
                break;
            }
        }
        if (!hit) return;
        fired.add(ruleId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("conclusionClass", conclusionClasses[0]);
        row.put("field", field);
        row.put("label", label);
        row.put("evidence", List.of(ruleId + " → " + conclusionClasses[0]));
        violations.add(row);
        reasoning.add(reasonRow(ruleId, str(rule.get("name")), conclusionClasses[0] + "（命中 " + label + "）"));
    }

    private Map<String, Object> reasonRow(String ruleId, String condition, String conclusion) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("condition", condition);
        row.put("conclusion", conclusion);
        return row;
    }

    private String ruleLevel(Map<String, Object> violation) {
        return "R-C06".equals(violation.get("ruleId")) ? "MEDIUM" : "HIGH";
    }

    private String fieldForC06(Map<String, Object> draft) {
        if (empty(firstNonEmpty(draft.get("offeringName"), draft.get("offerName")))) return "offeringName";
        if (empty(draft.get("messageRootKey"))) return "messageRootKey";
        if (empty(firstNonEmpty(draft.get("fixedFeeAmount"), draft.get("monthlyFee")))) return "fixedFeeAmount";
        return "channelScope";
    }

    private double normalizeDiscount(double discount) {
        if (discount > 0 && discount <= 1) return discount * 100;
        return discount;
    }

    private Object firstNonEmpty(Object... values) {
        for (Object v : values) {
            if (v != null && !String.valueOf(v).isBlank()) return v;
        }
        return null;
    }

    private boolean empty(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    /**
     * 单产商品告警分级结果（R-E00~E04）。
     *
     * @param level          P0/P1/P2/NONE
     * @param needWorkOrder  是否需建单（P0/P1 强制，P2 不建单）
     * @param slaMinutes     SLA 处置时限（分钟）
     */
    public record SwrlAlertResult(
            boolean success,
            String engine,
            boolean alerted,
            String level,
            boolean needWorkOrder,
            int slaMinutes,
            List<String> firedRules,
            List<String> reasons,
            String message
    ) {
    }

    /**
     * 单产商品优化建议推理结果（R-O01~R-O05）。
     *
     * @param applicable  是否存在可给出优化建议的指标异常
     * @param firedRules  命中的建议规则（如 R-O01/R-O02）
     */
    public record SwrlSuggestionResult(
            boolean success,
            String engine,
            boolean applicable,
            List<String> firedRules,
            String message
    ) {
    }

    /**
     * 单产商品运行告警分级（R-E00~E04），基于 product.ttl 本体。
     * <p>
     * 断言监控事实（error_count / fee_error_rate / 趋势布尔），按 ops_rules.alertGrading 阈值
     * 覆写本体阈值个体，推出 P0/P1/P2 等级与是否强制建单；无告警返回 level=NONE。
     * 阈值全部外置；SWRL 失败回退 Java-rules（同阈值）。
     *
     * @param offeringId    产商品ID
     * @param facts         监控事实：order_count/error_count/fee_error_rate/order_trend/error_trend/fee_trend
     * @param e01~e04       ops_rules.alertGrading.rules 对应规则（阈值 + enabled）
     * @param alertDefaults ops_rules.alertGrading.defaults（SLA 分钟）
     */
    public SwrlAlertResult gradeAlert(
            String offeringId,
            Map<String, Object> facts,
            Map<String, Object> e01,
            Map<String, Object> e02,
            Map<String, Object> e03,
            Map<String, Object> e04,
            Map<String, Object> alertDefaults
    ) {
        double feeGte = ruleNum(e01, "feeErrorRateGte", 0.05);
        double errGte = ruleNum(e01, "errorCountGte", 10);
        double p1ErrGte = ruleNum(e02, "errorCountGte", 1);
        double p1FeeGte = ruleNum(e02, "feeErrorRateGte", 0.01);
        boolean requireTrend = e03 == null || e03.get("requireTrendWorse") == null
                || truthy(e03.get("requireTrendWorse"));
        int slaP0 = (int) ruleNum(alertDefaults, "slaP0Minutes", 30);
        int slaP1 = (int) ruleNum(alertDefaults, "slaP1Minutes", 240);
        int slaP2 = (int) ruleNum(alertDefaults, "slaP2Minutes", 1440);

        if (!properties.getOntology().isSwrlEnabled()) {
            return gradeByJava(offeringId, facts, e01, e02, e03, e04,
                    feeGte, errGte, p1ErrGte, p1FeeGte, requireTrend, slaP0, slaP1, slaP2, "fallback-java", "SWRL 已关闭");
        }
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLDataFactory df = manager.getOWLDataFactory();
            OWLOntology ontology = loadAlertOntology(manager);

            OWLNamedIndividual o = alertInd(df, offeringId);
            add(manager, ontology, df.getOWLClassAssertionAxiom(alertCls(df, "Offering"), o));
            add(manager, ontology, alertDataAssert(df, o, "offeringId", offeringId));
            add(manager, ontology, alertDataAssertDecimal(df, o, "orderCount", num(facts.get("order_count"))));
            add(manager, ontology, alertDataAssertDecimal(df, o, "errorCount", num(facts.get("error_count"))));
            add(manager, ontology, alertDataAssertDecimal(df, o, "feeErrorRate", num(facts.get("fee_error_rate"))));
            add(manager, ontology, alertDataAssertBoolean(df, o, "orderTrendDown",
                    "下降".equals(str(facts.get("order_trend")))));
            add(manager, ontology, alertDataAssertBoolean(df, o, "errorTrendUp",
                    "上升".equals(str(facts.get("error_trend")))));
            add(manager, ontology, alertDataAssertBoolean(df, o, "feeTrendUp",
                    "上升".equals(str(facts.get("fee_trend")))));

            Map<String, Double> thresholds = new LinkedHashMap<>();
            if (enabled(e01)) {
                thresholds.put("th_RE01_feeErrorRateGte", feeGte);
                thresholds.put("th_RE01_errorCountGte", errGte);
            }
            if (enabled(e02)) {
                thresholds.put("th_RE02_errorCountGte", p1ErrGte);
                thresholds.put("th_RE02_feeErrorRateGte", p1FeeGte);
            }
            applyThresholds(manager, ontology, df, thresholds);

            Set<String> disabledRules = new LinkedHashSet<>();
            if (!enabled(e01)) {
                disabledRules.add("R-E01.fee");
                disabledRules.add("R-E01.err");
            }
            if (!enabled(e02)) {
                disabledRules.add("R-E02.err");
                disabledRules.add("R-E02.fee");
            }
            if (!enabled(e03) || !requireTrend) {
                disabledRules.add("R-E03.errTrend");
                disabledRules.add("R-E03.feeTrend");
                disabledRules.add("R-E03.orderTrend");
            }
            if (!enabled(e04)) {
                disabledRules.add("R-E04.p0");
                disabledRules.add("R-E04.p1");
            }
            disableRules(manager, ontology, disabledRules);

            OpenlletReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
            reasoner.prepareReasoner();

            boolean p0 = isAlertType(reasoner, o, df, "P0AlertOffering");
            boolean p1 = isAlertType(reasoner, o, df, "P1AlertOffering");
            boolean p2 = isAlertType(reasoner, o, df, "P2AlertOffering");
            boolean needWo = isAlertType(reasoner, o, df, "NeedWorkOrderOffering");
            reasoner.dispose();

            List<String> fired = new ArrayList<>();
            List<String> reasons = new ArrayList<>();
            String level = "NONE";
            if (p0) {
                level = "P0";
                fired.add("R-E01");
                reasons.add("计费差错率≥" + feeGte + " 或异常量≥" + errGte);
            } else if (p1) {
                level = "P1";
                fired.add("R-E02");
                reasons.add("异常量≥" + p1ErrGte + " 或计费差错率≥" + p1FeeGte);
            } else if (p2) {
                level = "P2";
                fired.add("R-E03");
                reasons.add("指标趋势劣化（无实量异常）");
            }
            if (!"NONE".equals(level)) {
                fired.add(0, "R-E00");
            }
            if (needWo) {
                fired.add("R-E04");
            }
            int sla = "P0".equals(level) ? slaP0 : "P1".equals(level) ? slaP1 : "P2".equals(level) ? slaP2 : 0;
            return new SwrlAlertResult(true, "openllet-swrl", !"NONE".equals(level), level, needWo, sla, fired, reasons,
                    "SWRL 告警分级完成: " + level);
        } catch (Exception e) {
            log.warn("[OpsSwrlReasoner] 告警分级 SWRL 失败 offering={}: {}", offeringId, e.getMessage());
            return gradeByJava(offeringId, facts, e01, e02, e03, e04,
                    feeGte, errGte, p1ErrGte, p1FeeGte, requireTrend, slaP0, slaP1, slaP2,
                    "fallback-java", "SWRL 失败: " + e.getMessage());
        }
    }

    /** Java 回退分级（阈值与 SWRL 同源，保证引擎不可用时结果一致）。 */
    private SwrlAlertResult gradeByJava(
            String offeringId, Map<String, Object> facts,
            Map<String, Object> e01, Map<String, Object> e02, Map<String, Object> e03, Map<String, Object> e04,
            double feeGte, double errGte, double p1ErrGte, double p1FeeGte, boolean requireTrend,
            int slaP0, int slaP1, int slaP2, String engine, String message
    ) {
        double err = num(facts.get("error_count"));
        double fee = num(facts.get("fee_error_rate"));
        boolean trendWorse = "下降".equals(str(facts.get("order_trend")))
                || "上升".equals(str(facts.get("error_trend")))
                || "上升".equals(str(facts.get("fee_trend")));
        List<String> fired = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        String level = "NONE";
        if (enabled(e01) && (fee >= feeGte || err >= errGte)) {
            level = "P0";
            fired.add("R-E01");
            reasons.add("计费差错率≥" + feeGte + " 或异常量≥" + errGte);
        } else if (enabled(e02) && (err >= p1ErrGte || fee >= p1FeeGte)) {
            level = "P1";
            fired.add("R-E02");
            reasons.add("异常量≥" + p1ErrGte + " 或计费差错率≥" + p1FeeGte);
        } else if (enabled(e03) && requireTrend && trendWorse) {
            level = "P2";
            fired.add("R-E03");
            reasons.add("指标趋势劣化（无实量异常）");
        }
        if (!"NONE".equals(level)) {
            fired.add(0, "R-E00");
        }
        boolean needWo = enabled(e04) && ("P0".equals(level) || "P1".equals(level));
        if (needWo) {
            fired.add("R-E04");
        }
        int sla = "P0".equals(level) ? slaP0 : "P1".equals(level) ? slaP1 : "P2".equals(level) ? slaP2 : 0;
        return new SwrlAlertResult(true, engine, !"NONE".equals(level), level, needWo, sla, fired, reasons, message);
    }

    /**
     * 单产商品优化建议推理（R-O01~R-O05），基于 product.ttl 本体。
     * <p>
     * 断言逐指标异常布尔（由调用方按 metricAnomaly 阈值预计算），SWRL 推出建议分类个体
     * （:ChannelPromotionOffering / :RetentionOffering / :BundleUpsellOffering /
     * :PriceOptimizeOffering / :EngagementOffering），映射回 R-O 规则 ID；无异常返回 applicable=false。
     * SWRL 不可用时回退 Java 判定（条件=异常布尔，与规则同源）。
     *
     * @param offeringId 产商品ID
     * @param flags      指标异常布尔：revenueDrop/newUsersDrop/orderDrop/churnRise/activeDrop/arpuDrop/attachDrop
     * @param rulesById  建议规则（R-O01~R-O05，含 enabled），控制开关
     */
    public SwrlSuggestionResult reasonSuggestions(
            String offeringId,
            Map<String, Boolean> flags,
            Map<String, Map<String, Object>> rulesById
    ) {
        List<String> javaFired = suggestionFiredByJava(flags, rulesById);
        if (!properties.getOntology().isSwrlEnabled()) {
            return new SwrlSuggestionResult(true, "fallback-java", !javaFired.isEmpty(), javaFired, "SWRL 已关闭");
        }
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLDataFactory df = manager.getOWLDataFactory();
            OWLOntology ontology = loadAlertOntology(manager);

            OWLNamedIndividual o = alertInd(df, offeringId);
            add(manager, ontology, df.getOWLClassAssertionAxiom(alertCls(df, "Offering"), o));
            add(manager, ontology, alertDataAssert(df, o, "offeringId", offeringId));
            add(manager, ontology, alertDataAssertBoolean(df, o, "revenueDropFlag", flag(flags, "revenueDrop")));
            add(manager, ontology, alertDataAssertBoolean(df, o, "newUsersDropFlag", flag(flags, "newUsersDrop")));
            add(manager, ontology, alertDataAssertBoolean(df, o, "orderDropFlag", flag(flags, "orderDrop")));
            add(manager, ontology, alertDataAssertBoolean(df, o, "churnUsersRiseFlag", flag(flags, "churnRise")));
            add(manager, ontology, alertDataAssertBoolean(df, o, "activeUsersDropFlag", flag(flags, "activeDrop")));
            add(manager, ontology, alertDataAssertBoolean(df, o, "arpuDropFlag", flag(flags, "arpuDrop")));
            add(manager, ontology, alertDataAssertBoolean(df, o, "attachRateDropFlag", flag(flags, "attachDrop")));

            Set<String> disabled = new LinkedHashSet<>();
            if (!enabled(rule(rulesById, "R-O01"))) {
                disabled.add("R-O01.revenue");
                disabled.add("R-O01.order");
            }
            if (!enabled(rule(rulesById, "R-O02"))) disabled.add("R-O02.churn");
            if (!enabled(rule(rulesById, "R-O03"))) disabled.add("R-O03.attach");
            if (!enabled(rule(rulesById, "R-O04"))) disabled.add("R-O04.arpu");
            if (!enabled(rule(rulesById, "R-O05"))) disabled.add("R-O05.active");
            disableRules(manager, ontology, disabled);

            OpenlletReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
            reasoner.prepareReasoner();
            List<String> fired = new ArrayList<>();
            if (enabled(rule(rulesById, "R-O01"))
                    && (isAlertType(reasoner, o, df, "ChannelPromotionOffering"))) {
                fired.add("R-O01");
            }
            if (enabled(rule(rulesById, "R-O02"))
                    && isAlertType(reasoner, o, df, "RetentionOffering")) {
                fired.add("R-O02");
            }
            if (enabled(rule(rulesById, "R-O03"))
                    && isAlertType(reasoner, o, df, "BundleUpsellOffering")) {
                fired.add("R-O03");
            }
            if (enabled(rule(rulesById, "R-O04"))
                    && isAlertType(reasoner, o, df, "PriceOptimizeOffering")) {
                fired.add("R-O04");
            }
            if (enabled(rule(rulesById, "R-O05"))
                    && isAlertType(reasoner, o, df, "EngagementOffering")) {
                fired.add("R-O05");
            }
            reasoner.dispose();
            return new SwrlSuggestionResult(true, "openllet-swrl", !fired.isEmpty(), fired,
                    "SWRL 优化建议推理完成: " + String.join(",", fired));
        } catch (Exception e) {
            log.warn("[OpsSwrlReasoner] 优化建议 SWRL 失败 offering={}: {}", offeringId, e.getMessage());
            return new SwrlSuggestionResult(true, "fallback-java", !javaFired.isEmpty(), javaFired,
                    "SWRL 失败: " + e.getMessage());
        }
    }

    /** Java 回退建议判定（条件=异常布尔，与 R-O 规则同源）。 */
    private List<String> suggestionFiredByJava(
            Map<String, Boolean> flags, Map<String, Map<String, Object>> rulesById) {
        List<String> fired = new ArrayList<>();
        if (enabled(rule(rulesById, "R-O01"))
                && (flag(flags, "revenueDrop") || flag(flags, "orderDrop") || flag(flags, "newUsersDrop"))) {
            fired.add("R-O01");
        }
        if (enabled(rule(rulesById, "R-O02")) && flag(flags, "churnRise")) {
            fired.add("R-O02");
        }
        if (enabled(rule(rulesById, "R-O03")) && flag(flags, "attachDrop")) {
            fired.add("R-O03");
        }
        if (enabled(rule(rulesById, "R-O04")) && flag(flags, "arpuDrop")) {
            fired.add("R-O04");
        }
        if (enabled(rule(rulesById, "R-O05")) && flag(flags, "activeDrop")) {
            fired.add("R-O05");
        }
        return fired;
    }

    private Map<String, Object> rule(Map<String, Map<String, Object>> rulesById, String ruleId) {
        return rulesById == null ? null : rulesById.get(ruleId);
    }

    private boolean flag(Map<String, Boolean> flags, String key) {
        return flags != null && Boolean.TRUE.equals(flags.get(key));
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

    // ---------- SWRL 规则阈值/开关（规则骨架内置于 product.ttl，运行时覆写） ----------

    /**
     * 将运行时阈值覆写到命名阈值个体（:th_*）的 :thresholdValue 断言：
     * 先移除本体中既有断言，再按 ops_rules.json 注入新值，规则体经 ?t 绑定自动生效。
     */
    private void applyThresholds(
            OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory df, Map<String, Double> thresholds) {
        if (thresholds.isEmpty()) return;
        for (Map.Entry<String, Double> e : thresholds.entrySet()) {
            OWLNamedIndividual th = ind(df, e.getKey());
            for (OWLDataPropertyAssertionAxiom ax : ontology.dataPropertyAssertionAxioms(th).toList()) {
                if (dataProp(df, "thresholdValue").equals(ax.getProperty().asOWLDataProperty())) {
                    manager.applyChange(new RemoveAxiom(ontology, ax));
                }
            }
            add(manager, ontology, dataAssertDecimal(df, th, "thresholdValue", e.getValue()));
        }
    }

    /**
     * 按规则 ID（对应 SWRL 规则的 rdfs:label，如 "R-A01.flag"）关闭未启用规则：
     * 从本体移除对应 SWRLRule 公理，剩余规则交由 Openllet 推理。
     */
    private void disableRules(OWLOntologyManager manager, OWLOntology ontology, Set<String> ruleIds) {
        if (ruleIds.isEmpty()) return;
        for (OWLAxiom axiom : ontology.axioms(AxiomType.SWRL_RULE).toList()) {
            if (axiom instanceof SWRLRule rule) {
                String label = ruleLabel(rule);
                if (label != null && ruleIds.contains(label)) {
                    manager.applyChange(new RemoveAxiom(ontology, axiom));
                }
            }
        }
    }

    private String ruleLabel(SWRLRule rule) {
        for (OWLAnnotation ann : rule.getAnnotations()) {
            if (ann.getProperty().isLabel()) {
                return ann.getValue().asLiteral().map(OWLLiteral::getLiteral).orElse(null);
            }
        }
        return null;
    }

    /**
     * 按规则 ID 判定该规则是否对实例推出指定结论类。
     * <p>
     * 结论类已合并（一场景只有一个父类，不再一规则一子类），因此不能再靠
     * {@code getTypes()} 是否含某个子类来区分命中的是哪条规则。改为两步：
     * <ol>
     *   <li>实例确实被归入该结论类（父类 {@code conclusionClass}）；</li>
     *   <li>本体里存在 label 命中 {@code ruleIdPredicate} 的规则，且其 head 指向该类。</li>
     * </ol>
     * 两条同时成立，才认为「是这条规则推出的」。
     * <p>
     * label 取值兼容两套写法：{@code product.ttl} 用英文规则号（如 {@code R-B01.zeroFee}），
     * scenarios TTL 用中文规则名（如「零元资费风险」）。
     */
    private boolean ruleFired(OpenlletReasoner reasoner, OWLNamedIndividual ind, OWLDataFactory df,
                              OWLOntology ontology, Predicate<String> ruleIdPredicate, String... conclusionClasses) {
        boolean typed = false;
        for (String conclusionClass : conclusionClasses) {
            if (isType(reasoner, ind, df, conclusionClass)) {
                typed = true;
                break;
            }
        }
        if (!typed) {
            return false;
        }
        for (OWLAxiom axiom : ontology.axioms(AxiomType.SWRL_RULE).toList()) {
            if (!(axiom instanceof SWRLRule rule)) {
                continue;
            }
            String label = ruleLabel(rule);
            if (label == null || !ruleIdPredicate.test(label)) {
                continue;
            }
            if (headTargetsClass(rule, conclusionClasses)) {
                return true;
            }
        }
        return false;
    }

    private boolean headTargetsClass(SWRLRule rule, String... classLocals) {
        for (SWRLAtom atom : rule.getHead()) {
            if (atom instanceof SWRLClassAtom classAtom) {
                OWLClassExpression ce = classAtom.getPredicate();
                if (!ce.isNamed()) {
                    continue;
                }
                String local = localName(ce.asOWLClass().getIRI());
                for (String classLocal : classLocals) {
                    if (classLocal.equals(local)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String localName(IRI iri) {
        String s = iri.toString();
        int i = Math.max(s.lastIndexOf('#'), s.lastIndexOf('/'));
        return i >= 0 && i < s.length() - 1 ? s.substring(i + 1) : s;
    }

    // ---------- OWL helpers ----------

    private OWLOntology loadBaseOntology(OWLOntologyManager manager) throws Exception {
        Resource resource = resourceLoader.getResource(properties.getOntology().getProductOpsOwlPath());
        try (InputStream in = resource.getInputStream()) {
            return manager.loadOntologyFromOntologyDocument(in);
        }
    }

    private void add(OWLOntologyManager manager, OWLOntology ontology, OWLAxiom axiom) {
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

    private boolean isAlertType(OpenlletReasoner reasoner, OWLNamedIndividual ind, OWLDataFactory df, String classLocal) {
        return reasoner.getTypes(ind, true).containsEntity(alertCls(df, classLocal));
    }

    private OWLOntology loadAlertOntology(OWLOntologyManager manager) throws Exception {
        Resource resource = resourceLoader.getResource(ALERT_OWL_PATH);
        try (InputStream in = resource.getInputStream()) {
            return manager.loadOntologyFromOntologyDocument(in);
        }
    }

    private OWLNamedIndividual ind(OWLDataFactory df, String local) {
        return df.getOWLNamedIndividual(IRI.create(NS + local));
    }

    private OWLNamedIndividual alertInd(OWLDataFactory df, String local) {
        return df.getOWLNamedIndividual(IRI.create(ALERT_NS + local));
    }

    private OWLClass cls(OWLDataFactory df, String local) {
        return df.getOWLClass(IRI.create(NS + local));
    }

    private OWLClass alertCls(OWLDataFactory df, String local) {
        return df.getOWLClass(IRI.create(ALERT_NS + local));
    }

    private OWLObjectProperty objProp(OWLDataFactory df, String local) {
        return df.getOWLObjectProperty(IRI.create(NS + local));
    }

    private OWLDataProperty dataProp(OWLDataFactory df, String local) {
        return df.getOWLDataProperty(IRI.create(NS + local));
    }

    private OWLDataProperty alertDataProp(OWLDataFactory df, String local) {
        return df.getOWLDataProperty(IRI.create(ALERT_NS + local));
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

    private OWLDataPropertyAssertionAxiom dataAssertFloat(
            OWLDataFactory df, OWLNamedIndividual s, String prop, double value) {
        return df.getOWLDataPropertyAssertionAxiom(dataProp(df, prop), s,
                df.getOWLLiteral(java.math.BigDecimal.valueOf(value).toPlainString(), OWL2Datatype.XSD_FLOAT));
    }

    private OWLDataPropertyAssertionAxiom dataAssertBoolean(
            OWLDataFactory df, OWLNamedIndividual s, String prop, boolean value) {
        return df.getOWLDataPropertyAssertionAxiom(dataProp(df, prop), s, df.getOWLLiteral(value));
    }

    private OWLDataPropertyAssertionAxiom alertDataAssert(
            OWLDataFactory df, OWLNamedIndividual s, String prop, String value) {
        return df.getOWLDataPropertyAssertionAxiom(
                alertDataProp(df, prop), s, df.getOWLLiteral(value == null ? "" : value, OWL2Datatype.XSD_STRING));
    }

    private OWLDataPropertyAssertionAxiom alertDataAssertDecimal(
            OWLDataFactory df, OWLNamedIndividual s, String prop, double value) {
        return df.getOWLDataPropertyAssertionAxiom(alertDataProp(df, prop), s, decimalLiteral(df, value));
    }

    private OWLDataPropertyAssertionAxiom alertDataAssertBoolean(
            OWLDataFactory df, OWLNamedIndividual s, String prop, boolean value) {
        return df.getOWLDataPropertyAssertionAxiom(alertDataProp(df, prop), s, df.getOWLLiteral(value));
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

    // ---------- 声明式映射（ops_rules.config.propertyMapping） ----------

    /**
     * 按声明式映射把配置草稿/graph 字段翻译为本体数据属性断言。
     * <p>没有任何 Java 分支逻辑：属性名、取值来源、类型、转换算子全部来自配置；
     * 映射缺失时仅断言 offeringId 标识，规则自然因缺少事实而不触发。
     */
    private void assertMappedFacts(OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory df,
                                   OWLNamedIndividual ind, Map<String, Object> draft, Map<String, Object> graph,
                                   List<Map<String, Object>> mapping) {
        add(manager, ontology, dataAssert(df, ind, "offeringId",
                str(firstNonEmpty(draft.get("offeringId"), "draft"))));
        // 同一属性出现多条 mapping 时按 OR 合并（声明式表达「多分支任一成立」）
        Map<String, Boolean> booleanFacts = new LinkedHashMap<>();
        for (Map<String, Object> spec : mapping) {
            String prop = str(spec.get("property"));
            if (prop.isBlank()) continue;
            List<Object> values = resolveSources(draft, graph, spec);
            Object raw = values.isEmpty() ? null : values.get(0);
            String kind = str(spec.get("kind"));
            switch (kind) {
                case "decimal" -> add(manager, ontology,
                        dataAssertDecimal(df, ind, prop, toDecimal(raw, spec)));
                case "boolean" -> booleanFacts.merge(prop, toBoolean(raw, values, spec, graph),
                        Boolean::logicalOr);
                default -> {
                    if (raw != null) add(manager, ontology, dataAssert(df, ind, prop, str(raw)));
                }
            }
        }
        booleanFacts.forEach((prop, value) ->
                add(manager, ontology, dataAssertBoolean(df, ind, prop, value)));
    }

    /** 按 spec.sources 逐条解析取值（支持 draft./graph. 前缀、点号嵌套与 | 兜底），丢弃空值。 */
    private List<Object> resolveSources(Map<String, Object> draft, Map<String, Object> graph,
                                        Map<String, Object> spec) {
        List<Object> values = new ArrayList<>();
        for (Object src : MapOps.castList(spec.get("sources"))) {
            for (String candidate : str(src).split("\\|")) {
                Object v = lookup(candidate.trim(), draft, graph);
                if (!empty(v)) values.add(v);
            }
        }
        return values;
    }

    /** 读取点号路径：graph.x.y 取事实图，draft.x.y 或裸路径取草稿。 */
    private Object lookup(String path, Map<String, Object> draft, Map<String, Object> graph) {
        if (path.isBlank()) return null;
        Object current = path.startsWith("graph.") ? graph : draft;
        String trimmed = path.startsWith("graph.") ? path.substring("graph.".length())
                : path.startsWith("draft.") ? path.substring("draft.".length()) : path;
        for (String segment : trimmed.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(segment);
        }
        return current;
    }

    private double toDecimal(Object raw, Map<String, Object> spec) {
        double value = num(raw, num(spec.get("default"), 0));
        return switch (str(spec.get("transform"))) {
            case "percent-normalize" -> value > 0 && value <= 1 ? value * 100 : value;
            default -> value;
        };
    }

    private boolean toBoolean(Object raw, List<Object> values, Map<String, Object> spec,
                              Map<String, Object> graph) {
        return switch (str(spec.get("transform"))) {
            case "all-empty" -> values.isEmpty();
            case "all-nonempty" -> !values.isEmpty();
            case "positive" -> num(raw, num(spec.get("default"), -1)) > 0;
            case "equals-any" -> values.stream().anyMatch(v -> containsValue(spec.get("equals"), v));
            case "graph-contains" -> {
                Object list = graph.get(str(spec.get("graphKey")));
                yield values.stream().anyMatch(v -> MapOps.castList(list).stream()
                        .anyMatch(e -> str(e).equals(str(v))));
            }
            default -> spec.containsKey("equals")
                    ? values.stream().anyMatch(v -> containsValue(spec.get("equals"), v))
                    : values.stream().anyMatch(this::truthy);
        };
    }

    private boolean containsValue(Object candidates, Object value) {
        String text = str(value);
        return MapOps.castList(candidates).stream().anyMatch(c -> str(c).equals(text));
    }
}

