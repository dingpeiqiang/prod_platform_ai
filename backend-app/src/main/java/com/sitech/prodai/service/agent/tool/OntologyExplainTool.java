package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TBox 概念解释工具（方案 §6-B5，修正 §5.2 语义错位）。
 * <p>
 * 概念问 TBox：按概念名（本地名/中文标签模糊匹配）检索本体类/属性定义，
 * 返回 rdfs:label / rdfs:comment / 子类层级 / 关联属性（domain/range），
 * 让"什么是融合套餐"这类概念问得到本体建模口径的解释，而非评估审计流水。
 * <p>
 * 去旧留新：原 explain 的归因评估审计记录（评估流水）拆分至
 * {@link AttributionQueryTool}（attribution_query），本工具不再兜底审计语义；
 * 手册 online-check/root-cause 的 explain 收尾步骤语义不变（概念解释收尾）。
 * <p>
 * TBox 访问经 OntologyService.sparqlQuery（RDF4J 只读），数据源为
 * product-config.ttl（v2.2，rdfs:label/rdfs:comment 已中文建模）。
 */
@Component
public class OntologyExplainTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(OntologyExplainTool.class);

    /** 本体命名空间（product-config.ttl @base，本地名匹配前缀）。 */
    private static final String NS = "http://www.telecom-ontology.org/prod-config#";

    private final OntologyService ontologyService;

    public OntologyExplainTool(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getName() {
        return "ontology_explain";
    }

    @Override
    public String getDescription() {
        return "解释本体（TBox）中的业务概念：类定义（中文标签与含义说明）、类层级（父类/子类）、关联属性（含义与适用范围）。"
                + "用户问「什么是XX」「XX是什么意思」「XX和YY什么关系」等概念/名词解释时使用；"
                + "查归因评估流水请用 attribution_query，解释规则编号语义请用 rule_explain，查商品数据请用 sparql_query";
    }

    @Override
    public String getLabel() {
        return "概念解释";
    }

    @Override
    public java.util.Set<String> getScenes() {
        // query 场景（产商品查询助手）：概念/名词问询（建模口径解释）
        return java.util.Set.of("ops", "query");
    }

    /** 概念解读后的典型业务链：基于概念查数据/解释规则。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("sparql_query", "rule_explain", "attribution_query");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("concept")
                        .label("概念名")
                        .description("本体概念名（英文本地名如 ConfigScheme，或中文关键词如 方案/资费）")
                        .required()
                        .type("string")
                        .source("question")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("natural_language", ToolOutputField.Role.SUMMARY)
                        .label("概念解释").type("string")
                        .description("概念的自然语言解释（建模口径）").build(),
                ToolOutputField.builder("concept_uri", ToolOutputField.Role.OTHER)
                        .label("概念 URI").type("string")
                        .description("命中的本体概念 URI").build(),
                ToolOutputField.builder("label", ToolOutputField.Role.OTHER)
                        .label("中文标签").type("string")
                        .description("rdfs:label 中文标签").build(),
                ToolOutputField.builder("comment", ToolOutputField.Role.OTHER)
                        .label("含义说明").type("string")
                        .description("rdfs:comment 含义说明").build(),
                ToolOutputField.builder("super_classes", ToolOutputField.Role.ITEMS)
                        .label("父类").type("list")
                        .description("rdfs:subClassOf 父类清单").build(),
                ToolOutputField.builder("sub_classes", ToolOutputField.Role.ITEMS)
                        .label("子类").type("list")
                        .description("子类清单").build(),
                ToolOutputField.builder("related_properties", ToolOutputField.Role.ITEMS)
                        .label("关联属性").type("list")
                        .description("以该概念为 domain/range 的属性（含说明）").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String concept = params != null ? str(params.get("concept")) : "";

        log.info("[AgentTool] ontology_explain 执行: concept={}", concept);

        if (concept.isBlank()) {
            return ExecutionResult.fail(getName(), "缺少概念名：请提供要解释的本体概念（如 方案/ConfigScheme）");
        }

        try {
            Map<String, Object> hit = resolveConcept(concept);
            if (hit == null) {
                return ExecutionResult.fail(getName(),
                        "本体中未找到概念「" + concept + "」（可尝试英文本地名或中文关键词，如 ConfigScheme/方案/资费）");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            String uri = str(hit.get("uri"));
            out.put("concept_uri", uri);
            out.put("label", str(hit.get("label")));
            out.put("comment", str(hit.get("comment")));
            out.put("super_classes", superClassesOf(uri));
            out.put("sub_classes", subClassesOf(uri));
            out.put("related_properties", relatedPropertiesOf(uri));
            out.put("natural_language", buildSummary(concept, out));
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] ontology_explain 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "概念解释失败: " + e.getMessage());
        }
    }

    // ===== TBox 检索（RDF4J 只读 SPARQL） =====

    /** 概念解析：本地名精确 → 本地名前缀 → 中文 label/comment 包含（确定性优先级）。 */
    private Map<String, Object> resolveConcept(String concept) {
        String local = concept;
        int hashIdx = concept.indexOf('#');
        if (hashIdx >= 0 && hashIdx < concept.length() - 1) {
            local = concept.substring(hashIdx + 1);
        }

        // 1) 本地名精确（类或属性）
        Map<String, Object> hit = queryConcept("LOWER(STR(?name)) = LOWER(\"" + escape(local) + "\")");
        if (hit != null) {
            return hit;
        }
        // 2) 本地名前缀
        hit = queryConcept("STRSTARTS(LOWER(STR(?name)), LOWER(\"" + escape(local) + "\"))");
        if (hit != null) {
            return hit;
        }
        // 3) 中文 label/comment 包含
        return queryConcept("CONTAINS(LOWER(STR(?lbl)), LOWER(\"" + escape(concept) + "\"))"
                + " || CONTAINS(LOWER(STR(?cmt)), LOWER(\"" + escape(concept) + "\"))");
    }

    /** 单条概念检索（类/属性统一，LIMIT 1），未命中返回 null。 */
    private Map<String, Object> queryConcept(String filter) {
        String query =
                "SELECT ?uri ?name ?lbl ?cmt WHERE { "
                        + "{ ?uri a owl:Class . } UNION { ?uri a owl:ObjectProperty . } "
                        + "BIND(REPLACE(STR(?uri), \"^.*[#/]\", \"\") AS ?name) "
                        + "OPTIONAL { ?uri rdfs:label ?lbl } "
                        + "OPTIONAL { ?uri rdfs:comment ?cmt } "
                        + "FILTER (" + filter + ") "
                        + "} LIMIT 1";
        List<Map<String, Object>> rows = sparqlRows(query);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("uri", str(row.get("uri")));
        hit.put("label", zhLabel(row.get("lbl")));
        hit.put("comment", zhLabel(row.get("cmt")));
        return hit;
    }

    /** 父类清单（rdfs:subClassOf）。 */
    private List<Map<String, Object>> superClassesOf(String uri) {
        return hierarchyOf(uri, "?uri rdfs:subClassOf ?rel", "super");
    }

    /** 子类清单（反向 subClassOf）。 */
    private List<Map<String, Object>> subClassesOf(String uri) {
        return hierarchyOf(uri, "?rel rdfs:subClassOf ?uri", "sub");
    }

    private List<Map<String, Object>> hierarchyOf(String uri, String pattern, String direction) {
        List<Map<String, Object>> out = new ArrayList<>();
        String query =
                "SELECT ?rel ?lbl ?cmt WHERE { "
                        + pattern.replace("?uri", "<" + uri + ">").replace("?rel", "?rel")
                        + " OPTIONAL { ?rel rdfs:label ?lbl } "
                        + "OPTIONAL { ?rel rdfs:comment ?cmt } "
                        + "} LIMIT 20";
        List<Map<String, Object>> rows = sparqlRows(query);
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("uri", str(row.get("rel")));
            item.put("label", zhLabel(row.get("lbl")));
            item.put("comment", zhLabel(row.get("cmt")));
            out.add(item);
        }
        return out;
    }

    /** 关联属性：以该概念为 domain/range 的属性（含中文说明）。 */
    private List<Map<String, Object>> relatedPropertiesOf(String uri) {
        List<Map<String, Object>> out = new ArrayList<>();
        String query =
                "SELECT ?prop ?role ?lbl ?cmt WHERE { "
                        + "{ ?prop rdfs:domain <" + uri + "> . BIND(\"domain\" AS ?role) } "
                        + "UNION { ?prop rdfs:range <" + uri + "> . BIND(\"range\" AS ?role) } "
                        + "OPTIONAL { ?prop rdfs:label ?lbl } "
                        + "OPTIONAL { ?prop rdfs:comment ?cmt } "
                        + "} LIMIT 30";
        List<Map<String, Object>> rows = sparqlRows(query);
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("uri", str(row.get("prop")));
            item.put("role", str(row.get("role")));
            item.put("label", zhLabel(row.get("lbl")));
            item.put("comment", zhLabel(row.get("cmt")));
            out.add(item);
        }
        return out;
    }

    /** SPARQL 行集取数（OntologyService 返回 Map 包裹 results，此处解包为行列表）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sparqlRows(String query) {
        Map<String, Object> resp = ontologyService.sparqlQuery(query);
        Object results = resp == null ? null : resp.get("results");
        List<Map<String, Object>> out = new ArrayList<>();
        if (results instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    out.add(row);
                }
            }
        }
        return out;
    }

    /** 摘要：概念 + 中文含义 + 层级 + 关联属性数，一段业务可读解释。 */
    private String buildSummary(String concept, Map<String, Object> out) {
        StringBuilder sb = new StringBuilder();
        String label = str(out.get("label"));
        String comment = str(out.get("comment"));
        sb.append("「").append(label.isBlank() ? concept : label).append("」");
        if (!comment.isBlank()) {
            sb.append("：").append(comment);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> supers = (List<Map<String, Object>>) out.get("super_classes");
        if (supers != null && !supers.isEmpty()) {
            sb.append("。属于").append(joinLabels(supers)).append("的下位概念");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subs = (List<Map<String, Object>>) out.get("sub_classes");
        if (subs != null && !subs.isEmpty()) {
            sb.append("，下含").append(joinLabels(subs)).append("等子类");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> props = (List<Map<String, Object>>) out.get("related_properties");
        if (props != null && !props.isEmpty()) {
            sb.append("，关联属性 ").append(props.size()).append(" 个");
        }
        sb.append("（建模口径见 product-config 本体）。");
        return sb.toString();
    }

    private String joinLabels(List<Map<String, Object>> items) {
        List<String> labels = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String label = str(item.get("label"));
            String uri = str(item.get("uri"));
            labels.add(label.isBlank() ? localNameOf(uri) : label);
        }
        return String.join("、", labels);
    }

    private String localNameOf(String uri) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        int idx = uri.lastIndexOf('#');
        if (idx < 0) {
            idx = uri.lastIndexOf('/');
        }
        return idx >= 0 && idx < uri.length() - 1 ? uri.substring(idx + 1) : uri;
    }

    /** label/comment 多语言字面量取中文（形如 "xxx"@zh；无语言标记原样返回）。 */
    private String zhLabel(Object v) {
        if (v == null) {
            return "";
        }
        String s = str(v);
        // RDF4J valueToJava 对多语言字面量通常返回纯文本；防御性剥离 @zh 尾缀
        int at = s.lastIndexOf("\"@");
        if (s.startsWith("\"") && at > 0) {
            return s.substring(1, at);
        }
        return s;
    }

    private String escape(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
