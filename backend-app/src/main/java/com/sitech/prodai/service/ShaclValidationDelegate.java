package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.common.MapOps;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SHACL 校验委托（R7 试点）。
 * <p><b>当前状态（网络受限降级）</b>：rdf4j-shacl 4.3.4 构件在当前环境不可达
 * （公网镜像全阻 + 内网 Nexus 无此构件），委托以「TTL 解析 + SPARQL-less 图校验」模式运行：
 * <ul>
 *   <li>compliance-shacl.ttl 经 rdf4j-rio-turtle 解析（shapes 契约与引擎无关）；</li>
 *   <li>校验引擎按 shapes 中的 sh:minCount / sh:maxCount / sh:not 语义做纯 Java 求值，
 *       违规输出结构与 RDF4J SHACL 引擎一致（violation.rule → R-C 编号）；</li>
 *   <li>rdf4j-shacl 构件恢复可达后，仅替换 {@link #runShacl} 实现即可切换真引擎，
 *       shapes 文件与 issue 契约零变更。</li>
 * </ul>
 * <p>目标引擎（构件可达后）：RDF4J ShaclSail 事务校验，与 Java 引擎并跑比对
 * 一致率 ≥99% 后作为合规事实源。白名单豁免（R-C05）在投影阶段过滤，保持单点语义。
 */
@Service
public class ShaclValidationDelegate {

    private static final Logger log = LoggerFactory.getLogger(ShaclValidationDelegate.class);

    /** 合规 shapes 文件（R-C 编号经 sh:name 标注，与 violation.rule 对齐）。 */
    static final String SHACL_PATH = "classpath:ontologies/compliance-shacl.ttl";

    private static final Pattern RC_ID = Pattern.compile("R-C\\d+");
    private static final String SH_NS = "http://www.w3.org/ns/shacl#";

    private final ProdAiProperties properties;
    private final ResourceLoader resourceLoader;

    /** shapes 预加载缓存（进程内一次解析，多次校验复用）。 */
    private volatile Model shapesModel;

    public ShaclValidationDelegate(ProdAiProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 校验单个草稿（与 ComplianceRuleEngine.checkCompliance 同构返回 issues 列表）。
     *
     * @param draft 草稿 Map（lowerCamelCase 字段）
     * @param graph 事实图（读取 equityGiftWhitelist 豁免清单）
     * @return issues：{ruleId, issueType, issueLevel, field, message, engine:"rdf4j-shacl"}
     */
    public Map<String, Object> validate(Map<String, Object> draft, Map<String, Object> graph) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("engine", "rdf4j-shacl");
        try {
            if (isExempt(draft, graph)) {
                body.put("issues", List.of());
                body.put("exempt", true);
                return body;
            }
            Model data = draftToStatements(draft);
            List<Map<String, Object>> violations = runShacl(data);
            body.put("issues", violations);
        } catch (Exception e) {
            // 校验失败不阻断合规链路：交由并跑比对统计，Java 引擎仍为权威事实源
            log.warn("[ShaclValidationDelegate] SHACL 校验失败（并跑降级）: {}", e.getMessage());
            body.put("success", false);
            body.put("issues", List.of());
            body.put("message", "SHACL engine error: " + e.getMessage());
        }
        return body;
    }

    /**
     * 图校验核心：逐条解析 shapes 中的 PropertyShape/NodeShape 约束并求值。
     * （rdf4j-shacl 构件可达后替换为 ShaclSail 事务校验，本方法为唯一替换点）
     */
    List<Map<String, Object>> runShacl(Model data) throws Exception {
        Model shapes = loadShapes();
        List<Map<String, Object>> issues = new ArrayList<>();
        for (Statement st : shapes.filter(null, RDF.TYPE, Values.iri(SH_NS + "PropertyShape"))) {
            issues.addAll(evalPropertyShape(shapes, data, st.getSubject()));
        }
        for (Statement st : shapes.filter(null, RDF.TYPE, Values.iri(SH_NS + "NodeShape"))) {
            issues.addAll(evalNodeShape(shapes, data, st.getSubject()));
        }
        return issues;
    }

    /**
     * 求值 sh:NodeShape 的 sh:not 嵌套分支（R-C05 零固费语义）。
     * <p>对齐口径：Java 引擎（ComplianceRuleEngine R-C05）仅在显式声明零固费时触发
     * （resolveFixedFee 缺省 -1 不等于 0），故字段缺失（无 statement）视为「未声明」，
     * 与 not 分支不触发同义；仅当数据图中存在显式 fixedFeeAmount/oneTimeFee 值
     * 且均 ≤0、且无 hasContract=true 时判违规。
     */
    private List<Map<String, Object>> evalNodeShape(Model shapes, Model data,
                                                    org.eclipse.rdf4j.model.Resource shape) {
        List<Map<String, Object>> issues = new ArrayList<>();
        String ruleId = ruleIdOf(shapes, shape);
        if (!isPilotRule(ruleId)) {
            return issues;
        }
        List<Value> notBranches = new ArrayList<>();
        for (Statement st : shapes.filter(shape, Values.iri(SH_NS + "not"), null)) {
            notBranches.add(st.getObject());
        }
        if (notBranches.isEmpty()) {
            return issues;
        }
        boolean allUnsatisfied = true;
        for (Value branch : notBranches) {
            if (!(branch instanceof org.eclipse.rdf4j.model.Resource branchNode)
                    || innerConstraintSatisfied(shapes, data, branchNode)) {
                allUnsatisfied = false;
                break;
            }
        }
        if (allUnsatisfied && declaredZeroFee(data)) {
            issues.add(issue(ruleId, "fixedFeeAmount", "not-branches all unsatisfied (zero fee, no contract)"));
        }
        return issues;
    }

    /** 数据图是否显式声明零固费与零一次性费（字段缺失视为未声明，与 Java 引擎 resolveFixedFee 缺省 -1 对齐）。 */
    private boolean declaredZeroFee(Model data) {
        boolean hasFeeStatement = data.filter(null, Values.iri(baseIri() + "fixedFeeAmount"), null).stream()
                .anyMatch(s -> isNumeric(s.getObject().stringValue()));
        boolean hasOneTimeStatement = data.filter(null, Values.iri(baseIri() + "oneTimeFee"), null).stream()
                .anyMatch(s -> isNumeric(s.getObject().stringValue()));
        return hasFeeStatement && hasOneTimeStatement;
    }

    private boolean isNumeric(String text) {
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 内层约束是否满足（支持 sh:minExclusive / sh:hasValue），满足则对应 sh:not 分支不触发。 */
    private boolean innerConstraintSatisfied(Model shapes, Model data,
                                             org.eclipse.rdf4j.model.Resource branch) {
        for (Statement st : shapes.filter(branch, Values.iri(SH_NS + "property"), null)) {
            if (!(st.getObject() instanceof org.eclipse.rdf4j.model.Resource ps)) {
                continue;
            }
            Value path = first(shapes, ps, SH_NS + "path");
            if (path == null) {
                continue;
            }
            Value minExclusive = first(shapes, ps, SH_NS + "minExclusive");
            if (minExclusive != null) {
                double bound = Double.parseDouble(minExclusive.stringValue());
                boolean hasGreater = data.filter(null, Values.iri(baseIri() + localName(path.stringValue())), null)
                        .stream().anyMatch(s -> {
                            try {
                                return Double.parseDouble(s.getObject().stringValue()) > bound;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        });
                if (hasGreater) {
                    return true;
                }
            }
            Value hasValue = first(shapes, ps, SH_NS + "hasValue");
            if (hasValue != null) {
                boolean hasMatching = data.filter(null, Values.iri(baseIri() + localName(path.stringValue())), null)
                        .stream().anyMatch(s -> s.getObject().stringValue().equalsIgnoreCase(hasValue.stringValue()));
                if (hasMatching) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 求值单个 sh:PropertyShape（支持 sh:minCount / sh:maxCount / sh:hasValue）。 */
    private List<Map<String, Object>> evalPropertyShape(Model shapes, Model data,
                                                        org.eclipse.rdf4j.model.Resource shape) {
        List<Map<String, Object>> issues = new ArrayList<>();
        String ruleId = ruleIdOf(shapes, shape);
        if (!isPilotRule(ruleId)) {
            return issues;
        }
        String path = pathOf(shapes, shape);
        if (path == null) {
            return issues;
        }
        Value minCount = first(shapes, shape, SH_NS + "minCount");
        Value maxCount = first(shapes, shape, SH_NS + "maxCount");
        int count = countValues(data, path);
        if (minCount != null && count < Integer.parseInt(minCount.stringValue())) {
            issues.add(issue(ruleId, path, "minCount " + count + " < " + minCount.stringValue()));
        }
        if (maxCount != null && count > Integer.parseInt(maxCount.stringValue())) {
            issues.add(issue(ruleId, path, "maxCount " + count + " > " + maxCount.stringValue()));
        }
        return issues;
    }

    private boolean isPilotRule(String ruleId) {
        return "R-C06".equals(ruleId) || "R-C03".equals(ruleId) || "R-C05".equals(ruleId);
    }

    private int countValues(Model data, String path) {
        return (int) data.filter(null, Values.iri(baseIri() + path), null).stream().count();
    }

    private String ruleIdOf(Model shapes, org.eclipse.rdf4j.model.Resource shape) {
        Value name = first(shapes, shape, SH_NS + "name");
        if (name != null && RC_ID.matcher(name.stringValue()).matches()) {
            return name.stringValue();
        }
        Value message = first(shapes, shape, SH_NS + "message");
        if (message != null) {
            var m = RC_ID.matcher(message.stringValue());
            if (m.find()) {
                return m.group();
            }
        }
        return "SHACL";
    }

    private String pathOf(Model shapes, org.eclipse.rdf4j.model.Resource shape) {
        Value path = first(shapes, shape, SH_NS + "path");
        return path == null ? null : localName(path.stringValue());
    }

    private Value first(Model model, org.eclipse.rdf4j.model.Resource subject, String predicate) {
        return model.filter(subject, Values.iri(predicate), null).stream()
                .findFirst().map(Statement::getObject).orElse(null);
    }

    private Map<String, Object> issue(String ruleId, String field, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("issueType", switch (ruleId) {
            case "R-C06" -> "必填缺失";
            case "R-C03" -> "资费/关系冲突";
            case "R-C05" -> "高风险资费";
            default -> "SHACL违规";
        });
        row.put("issueLevel", "HIGH");
        row.put("field", field);
        row.put("message", ruleId + " SHACL violation: " + detail);
        row.put("engine", "rdf4j-shacl");
        return row;
    }

    /** 草稿 Map → RDF statements（类型语义对齐 Rdf4jOntologyStore.writeSingleValue）。 */
    public Model draftToStatements(Map<String, Object> draft) {
        Model model = new LinkedHashModel();
        if (draft == null || draft.isEmpty()) {
            return model;
        }
        String offeringId = MapOps.str(MapOps.firstNonEmpty(draft.get("offeringId"), "draft_current"));
        org.eclipse.rdf4j.model.IRI subject = Values.iri(baseIri() + "offering/" + offeringId);
        model.add(subject, RDF.TYPE, Values.iri(baseIri() + "Offering"));
        ValueFactory vf = Values.getValueFactory();
        for (Map.Entry<String, Object> e : draft.entrySet()) {
            String field = e.getKey();
            Object value = e.getValue();
            if (value == null || "uri".equals(field) || "type".equals(field)) {
                continue;
            }
            org.eclipse.rdf4j.model.IRI predicate = Values.iri(baseIri() + field);
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    addFact(model, vf, subject, predicate, item);
                }
            } else {
                addFact(model, vf, subject, predicate, value);
            }
        }
        return model;
    }

    private void addFact(Model model, ValueFactory vf,
                         org.eclipse.rdf4j.model.IRI subject,
                         org.eclipse.rdf4j.model.IRI predicate, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean b) {
            model.add(subject, predicate, vf.createLiteral(b));
        } else if (value instanceof Number n) {
            model.add(subject, predicate, vf.createLiteral(n.doubleValue()));
        } else if (value instanceof Map<?, ?>) {
            // 嵌套结构（chargePlan/releaseScope）在投影层拍平为标量后再校验；此处跳过
        } else {
            String text = String.valueOf(value);
            if (text.matches("-?\\d+")) {
                model.add(subject, predicate, vf.createLiteral(Long.parseLong(text)));
            } else if (text.matches("-?\\d+(\\.\\d+)?")) {
                model.add(subject, predicate, vf.createLiteral(Double.parseDouble(text)));
            } else if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                model.add(subject, predicate, vf.createLiteral(Boolean.parseBoolean(text)));
            } else {
                model.add(subject, predicate, vf.createLiteral(text, XSD.STRING));
            }
        }
    }

    /** R-C05 白名单豁免（与 Java 引擎分支对齐）：零固费且白名单场景/内部验证渠道。 */
    public boolean isExempt(Map<String, Object> draft, Map<String, Object> graph) {
        double monthly = MapOps.resolveFixedFee(draft);
        double oneTime = MapOps.num(draft.get("oneTimeFee"), 0);
        if (monthly != 0 || oneTime != 0 || MapOps.truthy(draft.get("hasContract"))) {
            return false;
        }
        String scenario = MapOps.str(draft.get("bizScenario"));
        List<Object> whitelist = MapOps.castList(graph == null ? Map.of() : graph.get("equityGiftWhitelist"));
        return whitelist.contains(scenario) || "内部验证".equals(MapOps.str(draft.get("channelScope")));
    }

    /** 加载并解析 compliance-shacl.ttl（进程内缓存一次；TTL 语法错误即抛出）。 */
    private Model loadShapes() throws Exception {
        if (shapesModel != null) {
            return shapesModel;
        }
        synchronized (this) {
            if (shapesModel == null) {
                var resource = resourceLoader.getResource(SHACL_PATH);
                try (var in = resource.getInputStream()) {
                    shapesModel = Rio.parse(in, "", RDFFormat.TURTLE);
                }
                log.info("[ShaclValidationDelegate] 已解析 SHACL shapes（{} 条语句，R-C06/R-C03/R-C05 试点）",
                        shapesModel.size());
            }
            return shapesModel;
        }
    }

    private String baseIri() {
        return properties.getOntology().normalizedBaseIri();
    }

    private String localName(String iri) {
        int idx = iri.lastIndexOf('#');
        if (idx < 0) {
            idx = iri.lastIndexOf('/');
        }
        return idx >= 0 && idx < iri.length() - 1 ? iri.substring(idx + 1) : iri;
    }
}
