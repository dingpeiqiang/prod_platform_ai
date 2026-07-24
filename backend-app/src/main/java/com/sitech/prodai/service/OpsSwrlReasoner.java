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
 * 产商品运营归因 SWRL 推理（Openllet）——正式 OWL SWRL 引擎。
 * 覆盖 R-A01 / R-A02；阈值与启用开关来自 {@link OpsRulesService}（ops_rules.json）。
 * 勿与已废弃的伪 DSL {@link SwrlRuleEngine} 混淆。
 */
@Service
public class OpsSwrlReasoner {

    private static final Logger log = LoggerFactory.getLogger(OpsSwrlReasoner.class);
    private static final String NS = "http://prodai.sitech.com/ontology/product-ops#";
    private static final IRI SWRLB_LTE = IRI.create("http://www.w3.org/2003/11/swrlb#lessThanOrEqual");
    private static final IRI SWRLB_GTE = IRI.create("http://www.w3.org/2003/11/swrlb#greaterThanOrEqual");

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
            List<String> firedRules,
            String message
    ) {}

    /**
     * 对单个产商品 opsGraph 节点执行 R-A01 / R-A02 SWRL 推理。
     *
     * @param offeringId  产商品编码
     * @param offeringName 产商品名称
     * @param node        opsGraph 节点（metrics/channels）
     * @param a01         R-A01 规则配置
     * @param a02         R-A02 规则配置
     */
    public SwrlFireResult reasonRootCauseA01A02(
            String offeringId,
            String offeringName,
            Map<String, Object> node,
            Map<String, Object> a01,
            Map<String, Object> a02
    ) {
        if (!properties.getOntology().isSwrlEnabled()) {
            return new SwrlFireResult(false, "disabled", false, List.of(), List.of(), List.of(), "SWRL 已关闭");
        }
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLDataFactory df = manager.getOWLDataFactory();
            OWLOntology ontology = loadBaseOntology(manager);

            OWLNamedIndividual offeringInd = df.getOWLNamedIndividual(IRI.create(NS + "offering/" + offeringId));
            manager.applyChange(new AddAxiom(ontology, df.getOWLClassAssertionAxiom(cls(df, "Offering"), offeringInd)));
            manager.applyChange(new AddAxiom(ontology, dataAssert(df, offeringInd, "offeringId", offeringId)));
            if (offeringName != null && !offeringName.isBlank()) {
                manager.applyChange(new AddAxiom(ontology, dataAssert(df, offeringInd, "offeringName", offeringName)));
            }

            List<Map<String, Object>> metricFacts = castListOfMaps(node.get("metrics"));
            int metricIdx = 0;
            for (Map<String, Object> m : metricFacts) {
                String mid = "metric-" + offeringId + "-" + (metricIdx++);
                OWLNamedIndividual metricInd = df.getOWLNamedIndividual(IRI.create(NS + "metric/" + mid));
                manager.applyChange(new AddAxiom(ontology, df.getOWLClassAssertionAxiom(cls(df, "Metric"), metricInd)));
                manager.applyChange(new AddAxiom(ontology, objectAssert(df, offeringInd, "hasMetric", metricInd)));
                manager.applyChange(new AddAxiom(ontology, dataAssert(df, metricInd, "metricCode", str(m.get("metricCode")))));
                if (m.get("metricDelta") != null) {
                    manager.applyChange(new AddAxiom(ontology,
                            dataAssertDecimal(df, metricInd, "metricDelta", num(m.get("metricDelta")))));
                }
                if (m.get("metricDeltaPp") != null) {
                    manager.applyChange(new AddAxiom(ontology,
                            dataAssertDecimal(df, metricInd, "metricDeltaPp", num(m.get("metricDeltaPp")))));
                }
                if (truthy(m.get("anomaly"))) {
                    manager.applyChange(new AddAxiom(ontology,
                            dataAssertBoolean(df, metricInd, "anomalyFlag", true)));
                }
            }

            List<Map<String, Object>> channelFacts = castListOfMaps(node.get("channels"));
            Map<String, Map<String, Object>> channelById = new LinkedHashMap<>();
            for (Map<String, Object> ch : channelFacts) {
                String cid = str(ch.get("channelId"));
                if (cid.isBlank()) continue;
                channelById.put(cid, ch);
                OWLNamedIndividual chInd = df.getOWLNamedIndividual(IRI.create(NS + "channel/" + cid));
                manager.applyChange(new AddAxiom(ontology, df.getOWLClassAssertionAxiom(cls(df, "Channel"), chInd)));
                manager.applyChange(new AddAxiom(ontology, objectAssert(df, offeringInd, "soldOn", chInd)));
                manager.applyChange(new AddAxiom(ontology, dataAssert(df, chInd, "channelId", cid)));
                manager.applyChange(new AddAxiom(ontology, dataAssert(df, chInd, "channelName", str(ch.get("name")))));
                manager.applyChange(new AddAxiom(ontology,
                        dataAssertDecimal(df, chInd, "orderDelta", num(ch.get("orderDelta")))));
                manager.applyChange(new AddAxiom(ontology,
                        dataAssertDecimal(df, chInd, "contribRatio", num(ch.get("contribRatio")))));
                if (ch.get("weightHint") != null) {
                    manager.applyChange(new AddAxiom(ontology,
                            dataAssertDecimal(df, chInd, "weightHint", num(ch.get("weightHint")))));
                }
            }

            boolean a01Enabled = a01 == null || a01.get("enabled") == null || truthy(a01.get("enabled"));
            boolean a02Enabled = a02 == null || a02.get("enabled") == null || truthy(a02.get("enabled"));
            double metricDeltaLte = ruleNum(a01, "metricDeltaLte", -0.10);
            boolean honorFlag = a01 == null || a01.get("honorAnomalyFlag") == null || truthy(a01.get("honorAnomalyFlag"));
            double orderDeltaLte = ruleNum(a02, "orderDeltaLte", -0.20);
            double contribGte = ruleNum(a02, "contribRatioGte", 0.30);

            if (a01Enabled) {
                manager.applyChange(new AddAxiom(ontology, buildRa01DeltaRule(df, metricDeltaLte)));
                if (honorFlag) {
                    manager.applyChange(new AddAxiom(ontology, buildRa01FlagRule(df)));
                }
            }
            if (a02Enabled) {
                manager.applyChange(new AddAxiom(ontology, buildRa02Rule(df, orderDeltaLte, contribGte)));
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

            List<Map<String, Object>> channelCandidates = new ArrayList<>();
            if (anomalyFired && a02Enabled) {
                NodeSet<OWLNamedIndividual> channelRoots = reasoner.getInstances(cls(df, "ChannelRootCause"), true);
                Set<String> hitIds = new LinkedHashSet<>();
                for (OWLNamedIndividual ind : channelRoots.getFlattened()) {
                    String iri = ind.getIRI().toString();
                    String cid = iri.contains("/") ? iri.substring(iri.lastIndexOf('/') + 1) : iri;
                    hitIds.add(cid);
                }
                if (!hitIds.isEmpty()) {
                    fired.add("R-A02");
                }
                for (String cid : hitIds) {
                    Map<String, Object> ch = channelById.get(cid);
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
                    channelCandidates.add(c);
                }
            }

            reasoner.dispose();
            return new SwrlFireResult(
                    true,
                    "openllet-swrl",
                    anomalyFired,
                    anomalies,
                    channelCandidates,
                    fired,
                    anomalyFired ? "SWRL 推理完成" : "SWRL 未点燃异动（R-A01）"
            );
        } catch (Exception e) {
            log.warn("[OpsSwrlReasoner] SWRL 推理失败，将回退 Java 规则: {}", e.getMessage());
            return new SwrlFireResult(false, "fallback-java", false, List.of(), List.of(), List.of(),
                    "SWRL 失败: " + e.getMessage());
        }
    }

    private OWLOntology loadBaseOntology(OWLOntologyManager manager) throws Exception {
        Resource resource = resourceLoader.getResource(properties.getOntology().getProductOpsOwlPath());
        try (InputStream in = resource.getInputStream()) {
            return manager.loadOntologyFromOntologyDocument(in);
        }
    }

    private SWRLRule buildRa01DeltaRule(OWLDataFactory df, double metricDeltaLte) {
        SWRLVariable o = df.getSWRLVariable(IRI.create(NS + "var/o"));
        SWRLVariable m = df.getSWRLVariable(IRI.create(NS + "var/m"));
        SWRLVariable d = df.getSWRLVariable(IRI.create(NS + "var/d"));
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "Offering"), o));
        body.add(df.getSWRLObjectPropertyAtom(objProp(df, "hasMetric"), o, m));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "metricDelta"), m, d));
        body.add(df.getSWRLBuiltInAtom(SWRLB_LTE, List.<SWRLDArgument>of(d, df.getSWRLLiteralArgument(decimalLiteral(df, metricDeltaLte)))));
        Set<SWRLAtom> head = Set.of(df.getSWRLClassAtom(cls(df, "AnomalyOffering"), o));
        return df.getSWRLRule(body, head);
    }

    private SWRLRule buildRa01FlagRule(OWLDataFactory df) {
        SWRLVariable o = df.getSWRLVariable(IRI.create(NS + "var/o2"));
        SWRLVariable m = df.getSWRLVariable(IRI.create(NS + "var/m2"));
        SWRLVariable f = df.getSWRLVariable(IRI.create(NS + "var/f2"));
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "Offering"), o));
        body.add(df.getSWRLObjectPropertyAtom(objProp(df, "hasMetric"), o, m));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "anomalyFlag"), m, f));
        // anomalyFlag == true
        body.add(df.getSWRLBuiltInAtom(
                IRI.create("http://www.w3.org/2003/11/swrlb#equal"),
                List.of(f, df.getSWRLLiteralArgument(df.getOWLLiteral(true)))
        ));
        Set<SWRLAtom> head = Set.of(df.getSWRLClassAtom(cls(df, "AnomalyOffering"), o));
        return df.getSWRLRule(body, head);
    }

    private SWRLRule buildRa02Rule(OWLDataFactory df, double orderDeltaLte, double contribGte) {
        SWRLVariable o = df.getSWRLVariable(IRI.create(NS + "var/o3"));
        SWRLVariable c = df.getSWRLVariable(IRI.create(NS + "var/c3"));
        SWRLVariable od = df.getSWRLVariable(IRI.create(NS + "var/od3"));
        SWRLVariable cr = df.getSWRLVariable(IRI.create(NS + "var/cr3"));
        Set<SWRLAtom> body = new LinkedHashSet<>();
        body.add(df.getSWRLClassAtom(cls(df, "AnomalyOffering"), o));
        body.add(df.getSWRLObjectPropertyAtom(objProp(df, "soldOn"), o, c));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "orderDelta"), c, od));
        body.add(df.getSWRLDataPropertyAtom(dataProp(df, "contribRatio"), c, cr));
        body.add(df.getSWRLBuiltInAtom(SWRLB_LTE, List.<SWRLDArgument>of(od, df.getSWRLLiteralArgument(decimalLiteral(df, orderDeltaLte)))));
        body.add(df.getSWRLBuiltInAtom(SWRLB_GTE, List.<SWRLDArgument>of(cr, df.getSWRLLiteralArgument(decimalLiteral(df, contribGte)))));
        Set<SWRLAtom> head = Set.of(df.getSWRLClassAtom(cls(df, "ChannelRootCause"), c));
        return df.getSWRLRule(body, head);
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
        return df.getOWLDataPropertyAssertionAxiom(dataProp(df, prop), s, df.getOWLLiteral(value));
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
        return df.getOWLLiteral(value);
    }

    private double ruleNum(Map<String, Object> rule, String key, double defaultValue) {
        if (rule == null || !rule.containsKey(key) || rule.get(key) == null) return defaultValue;
        return num(rule.get(key));
    }

    private double num(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).replace("%", "").trim());
        } catch (Exception e) {
            return 0;
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
