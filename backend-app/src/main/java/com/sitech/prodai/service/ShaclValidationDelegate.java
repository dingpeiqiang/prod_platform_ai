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
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.ShaclSail;
import org.eclipse.rdf4j.sail.shacl.ShaclSailValidationException;
import org.eclipse.rdf4j.sail.shacl.results.ValidationReport;
import org.eclipse.rdf4j.sail.shacl.results.ValidationResult;
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
 * SHACL 校验委托（R7 试点，真引擎版）。
 * <p><b>引擎策略（引擎优先 + Lite 兜底）</b>：rdf4j-shacl 4.3.4 已引入 pom，
 * {@link #runShacl} 优先走 RDF4J {@link ShaclSail} 事务校验（shapes 经
 * {@code RDF4J.SHACL_SHAPE_GRAPH} 装载，进程内缓存一个 SailRepository，逐草稿事务校验）；
 * 引擎异常时降级为「TTL 解析 + 纯 Java 求值」的 {@link #runShaclLite}（sh:minCount/maxCount/not 语义），
 * 保证合规链路永不因引擎问题中断。
 * <ul>
 *   <li>违规结果经 violation report 映射回 R-C 编号（sh:sourceShape → shapes 中 sh:name，
 *       sh:resultMessage 前缀兜底），对外 issue 契约与 Java 引擎同构；</li>
 *   <li>白名单豁免（R-C05）在投影阶段过滤，保持单点语义。</li>
 * </ul>
 * <p>演进口径：并跑比对（ComplianceParityTest）一致率达标后，试点 3 条规则以 SHACL 为准，
 * Java 实现标记 {@code @Deprecated}（实施方案 §6.3 第 5 步）。
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

    /** shapes 预加载缓存（进程内一次解析，多次校验复用；数据仓库逐草稿一次性新建，不复用）。 */
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
     * 图校验核心：优先 RDF4J ShaclSail 真引擎（R7-完成），引擎异常降级 Lite 求值。
     */
    List<Map<String, Object>> runShacl(Model data) throws Exception {
        try {
            return runShaclSail(data);
        } catch (Exception e) {
            log.warn("[ShaclValidationDelegate] ShaclSail 引擎校验失败，降级 Lite 求值: {}", e.getMessage());
            return runShaclLite(data);
        }
    }

    /**
     * 真引擎路径：shapes 装载缓存复用（{@link #shapesRepository}），数据仓库逐草稿一次性
     * 新建（用完即 shutDown，数据不落库）——复用同一仓库会累积先前草稿数据，
     * 导致「先合规后违规」场景漏报（违规数据与存量数据互相干扰），故每次校验独立建仓。
     * commit 抛出的违规报告经 {@link #violationsToIssues} 映射为 issue 契约行；
     * 非违规类异常（引擎/环境问题）原样上抛，由 {@link #runShacl} 降级。
     */
    private List<Map<String, Object>> runShaclSail(Model data) throws Exception {
        ShaclSail sail = new ShaclSail(new MemoryStore());
        sail.setLogValidationPlans(false);
        sail.setLogValidationViolations(false);
        SailRepository dataRepo = new SailRepository(sail);
        dataRepo.init();
        try (RepositoryConnection conn = dataRepo.getConnection()) {
            conn.begin(ShaclSail.TransactionSettings.ValidationApproach.Bulk);
            try {
                conn.add(loadShapes(), RDF4J.SHACL_SHAPE_GRAPH);
                conn.add(data);
                conn.commit();
            } catch (RepositoryException e) {
                ShaclSailValidationException validation = findValidationCause(e);
                if (validation == null) {
                    throw e;
                }
                return violationsToIssues(validation.getValidationReport());
            }
        } finally {
            dataRepo.shutDown();
        }
        return List.of();
    }

    /** 沿 cause 链定位 SHACL 违规异常（违规语义才降级映射，其他异常上抛）。 */
    private ShaclSailValidationException findValidationCause(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ShaclSailValidationException validation) {
                return validation;
            }
        }
        return null;
    }

    /**
     * violation report → issue 契约行：sh:sourceShape → shapes 中 sh:name（R-C 编号）+ sh:path（字段）；
     * NodeShape 结果（如 R-C05）无 path 时按规则族回填默认字段。messageText 携带 shapes 语义文案。
     */
    private List<Map<String, Object>> violationsToIssues(ValidationReport report) throws Exception {
        List<Map<String, Object>> issues = new ArrayList<>();
        if (report == null || report.conforms()) {
            return issues;
        }
        Model shapes = loadShapes();
        for (ValidationResult vr : report.getValidationResult()) {
            Model resultModel = vr.asModel(new LinkedHashModel());
            Value sourceShape = first(resultModel, (org.eclipse.rdf4j.model.Resource) null, SH_NS + "sourceShape");
            String ruleId = null;
            String field = null;
            String messageText = null;
            if (sourceShape instanceof org.eclipse.rdf4j.model.Resource shapeRes) {
                Value name = ruleAnnotation(shapes, shapeRes);
                if (name != null && RC_ID.matcher(name.stringValue()).matches()) {
                    ruleId = name.stringValue();
                }
                Value path = first(shapes, shapeRes, SH_NS + "path");
                if (path != null) {
                    field = localName(path.stringValue());
                }
                Value message = first(shapes, shapeRes, SH_NS + "message");
                if (message != null) {
                    messageText = message.stringValue();
                }
            }
            if (ruleId == null) {
                Value resultMessage = first(resultModel,
                        (org.eclipse.rdf4j.model.Resource) null, SH_NS + "resultMessage");
                String text = resultMessage == null ? "" : resultMessage.stringValue();
                var m = RC_ID.matcher(text);
                ruleId = m.find() ? m.group() : "SHACL";
            }
            if (field == null && "R-C05".equals(ruleId)) {
                field = "fixedFeeAmount";
            }
            issues.add(issue(ruleId, field == null ? "offering" : field,
                    messageText == null ? vr.getSourceConstraintComponent().toString() : messageText));
        }
        return issues;
    }

    /**
     * Lite 兜底路径：逐条解析 shapes 中的 PropertyShape/NodeShape 约束并求值
     * （原降级实现，sh:minCount / sh:maxCount / sh:not 语义）。
     */
    List<Map<String, Object>> runShaclLite(Model data) throws Exception {
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
     * 求值 sh:NodeShape（R-C05 零固费语义，与真引擎同构）。
     * <p>SHACL 违规 = 节点不满足 shape。R-C05 的 shapes 以 sh:or 列「合规出路」
     * （fee>0 ∨ fee<0 ∨ oneTime>0 ∨ hasContract ∨ fee未声明，见 compliance-shacl.ttl），
     * 全部出路不满足即违规。求值支持 sh:or / sh:property / sh:not 嵌套。
     */
    private List<Map<String, Object>> evalNodeShape(Model shapes, Model data,
                                                    org.eclipse.rdf4j.model.Resource shape) {
        List<Map<String, Object>> issues = new ArrayList<>();
        String ruleId = ruleIdOf(shapes, shape);
        if (!isPilotRule(ruleId)) {
            return issues;
        }
        if (!conformsTo(shapes, data, shape)) {
            issues.add(issue(ruleId, "fixedFeeAmount", "no conforming escape branch (zero fee, no contract)"));
        }
        return issues;
    }

    /** 节点是否满足指定 shape：自身带 sh:path 按值约束求值；sh:or 任一成员满足 / sh:not 内层不满足 / 全部 sh:property 满足。 */
    private boolean conformsTo(Model shapes, Model data, org.eclipse.rdf4j.model.Resource node) {
        Value path = first(shapes, node, SH_NS + "path");
        if (path != null) {
            return propertyShapeConforms(shapes, data, node);
        }
        Value orHead = first(shapes, node, SH_NS + "or");
        if (orHead != null) {
            return rdfListItems(shapes, orHead).stream()
                    .filter(org.eclipse.rdf4j.model.Resource.class::isInstance)
                    .map(org.eclipse.rdf4j.model.Resource.class::cast)
                    .anyMatch(member -> conformsTo(shapes, data, member));
        }
        Value notObj = first(shapes, node, SH_NS + "not");
        if (notObj != null) {
            return !(notObj instanceof org.eclipse.rdf4j.model.Resource inner)
                    || !conformsTo(shapes, data, inner);
        }
        boolean hasProperty = false;
        for (Statement st : shapes.filter(node, Values.iri(SH_NS + "property"), null)) {
            hasProperty = true;
            if (st.getObject() instanceof org.eclipse.rdf4j.model.Resource ps
                    && !propertyShapeConforms(shapes, data, ps)) {
                return false;
            }
        }
        return hasProperty;
    }

    /** 单条 PropertyShape 是否满足（minCount / hasValue / minExclusive / maxExclusive）。 */
    private boolean propertyShapeConforms(Model shapes, Model data,
                                          org.eclipse.rdf4j.model.Resource ps) {
        Value path = first(shapes, ps, SH_NS + "path");
        if (path == null) {
            return true;
        }
        List<Value> values = data.filter(null, Values.iri(baseIri() + localName(path.stringValue())), null)
                .stream().map(Statement::getObject).toList();
        Value minCount = first(shapes, ps, SH_NS + "minCount");
        if (minCount != null && values.size() < Integer.parseInt(minCount.stringValue())) {
            return false;
        }
        Value hasValue = first(shapes, ps, SH_NS + "hasValue");
        if (hasValue != null) {
            return values.stream().anyMatch(v -> v.stringValue().equalsIgnoreCase(hasValue.stringValue()));
        }
        Value minExclusive = first(shapes, ps, SH_NS + "minExclusive");
        if (minExclusive != null) {
            double bound = Double.parseDouble(minExclusive.stringValue());
            return values.stream().allMatch(v -> doubleOf(v) > bound);
        }
        Value maxExclusive = first(shapes, ps, SH_NS + "maxExclusive");
        if (maxExclusive != null) {
            double bound = Double.parseDouble(maxExclusive.stringValue());
            return values.stream().allMatch(v -> doubleOf(v) < bound);
        }
        return true;
    }

    private double doubleOf(Value v) {
        try {
            return Double.parseDouble(v.stringValue());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** 展开 RDF List（sh:or / sh:and 成员）。 */
    private List<Value> rdfListItems(Model shapes, Value head) {
        List<Value> items = new ArrayList<>();
        Value current = head;
        while (current instanceof org.eclipse.rdf4j.model.Resource node) {
            Value firstItem = first(shapes, node, RDF.FIRST.stringValue());
            if (firstItem != null) {
                items.add(firstItem);
            }
            Value rest = first(shapes, node, RDF.REST.stringValue());
            if (rest == null || rest.equals(RDF.NIL)) {
                break;
            }
            current = rest;
        }
        return items;
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
        return isPilotRuleId(ruleId);
    }

    /** 试点规则判定（R-C06/R-C03/R-C05）；SHACL 转正覆盖与并跑比对共用单点口径。 */
    static boolean isPilotRuleId(String ruleId) {
        return "R-C06".equals(ruleId) || "R-C03".equals(ruleId) || "R-C05".equals(ruleId);
    }

    private int countValues(Model data, String path) {
        return (int) data.filter(null, Values.iri(baseIri() + path), null).stream().count();
    }

    private String ruleIdOf(Model shapes, org.eclipse.rdf4j.model.Resource shape) {
        Value name = ruleAnnotation(shapes, shape);
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

    /**
     * 规则编号注解：优先 sh:name（PropertyShape），回退 rdfs:label（NodeShape 专用，
     * sh:name 按 shacl.ttl 本体域属 PropertyShape，NodeShape 使用会触发 RDF4J 双类型推断）。
     */
    private Value ruleAnnotation(Model shapes, org.eclipse.rdf4j.model.Resource shape) {
        Value name = first(shapes, shape, SH_NS + "name");
        if (name != null) {
            return name;
        }
        return first(shapes, shape, "http://www.w3.org/2000/01/rdf-schema#label");
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
