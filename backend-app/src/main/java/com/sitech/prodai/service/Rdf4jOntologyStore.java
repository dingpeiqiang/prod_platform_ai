package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RDF4J 本体存储：Map 视图（兼容既有 API）+ Sail MemoryStore 真三元组，支持真实 SPARQL SELECT。
 */
@Service
public class Rdf4jOntologyStore implements OntologyStore {

    private static final Logger log = LoggerFactory.getLogger(Rdf4jOntologyStore.class);

    private final ProdAiProperties properties;
    private final Map<String, String> classRegistry = new ConcurrentHashMap<>();
    private final Map<String, String> propertyRegistry = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> instances = new ConcurrentHashMap<>();
    private Repository repository;

    public Rdf4jOntologyStore(ProdAiProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initRepository() {
        repository = new SailRepository(new MemoryStore());
        repository.init();
        log.info("[Rdf4jOntologyStore] Sail MemoryStore 已初始化，baseIri={}", baseIri());
    }

    @PreDestroy
    public void shutdownRepository() {
        if (repository != null) {
            repository.shutDown();
        }
    }

    private String baseIri() {
        return properties.getOntology().normalizedBaseIri();
    }

    private ValueFactory vf() {
        return repository.getValueFactory();
    }

    private IRI iri(String absoluteOrLocal) {
        if (absoluteOrLocal == null || absoluteOrLocal.isBlank()) {
            return vf().createIRI(baseIri() + "Thing");
        }
        if (absoluteOrLocal.startsWith("http://") || absoluteOrLocal.startsWith("https://")) {
            return vf().createIRI(absoluteOrLocal);
        }
        return vf().createIRI(baseIri() + absoluteOrLocal);
    }

    @Override
    public Map<String, Object> retrieve(List<com.sitech.prodai.domain.EntityRef> entities, String namespace) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (entities == null) {
            return facts;
        }
        for (com.sitech.prodai.domain.EntityRef entity : entities) {
            String uri = entity.normalizedUri(namespace);
            Map<String, Object> fact = new LinkedHashMap<>(instances.getOrDefault(uri, Map.of()));
            if (fact.isEmpty()) {
                fact.put("uri", uri);
                fact.put("type", entity.type());
                fact.put("source", entity.source());
            }
            facts.put(uri, fact);
        }
        return facts;
    }

    @Override
    public List<String> classes() {
        return new ArrayList<>(classRegistry.keySet());
    }

    @Override
    public List<String> properties() {
        return new ArrayList<>(propertyRegistry.keySet());
    }

    @Override
    public List<Map<String, Object>> samplesFor(String className) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : instances.values()) {
            if (className == null || className.equals(String.valueOf(item.get("type")))) {
                rows.add(new LinkedHashMap<>(item));
            }
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> sparqlSelect(String query) {
        if (query == null || query.isBlank()) {
            return allInstances();
        }
        if (repository == null) {
            log.warn("[Rdf4jOntologyStore] Repository 未就绪，返回空结果（调用方自行回退）");
            return new ArrayList<>();
        }
        try (RepositoryConnection conn = repository.getConnection()) {
            TupleQuery tq = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
            try (TupleQueryResult rs = tq.evaluate()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.hasNext()) {
                    BindingSet bs = rs.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String name : bs.getBindingNames()) {
                        Value v = bs.getValue(name);
                        if (v != null) {
                            row.put(name, valueToJava(v));
                        }
                    }
                    // 兼容旧面板：若绑定含主体 URI，附带 type
                    Object subject = firstUri(row);
                    if (subject != null) {
                        Map<String, Object> entity = instances.get(String.valueOf(subject));
                        if (entity != null) {
                            row.putIfAbsent("uri", subject);
                            row.putIfAbsent("type", entity.get("type"));
                            row.putIfAbsent("product", subject);
                            row.putIfAbsent("entity", subject);
                        }
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (Exception e) {
            // 异常时返回空列表而非全量实例：掩盖零命中会让上层（如 discoverConfigs）
            // 误判 SPARQL 命中，跳过词典回退并输出无序脏数据
            log.warn("[Rdf4jOntologyStore] SPARQL 执行失败，返回空结果: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Object firstUri(Map<String, Object> row) {
        for (String key : List.of("product", "entity", "s", "uri", "offering")) {
            Object v = row.get(key);
            if (v != null && String.valueOf(v).startsWith("http")) {
                return v;
            }
        }
        for (Object v : row.values()) {
            if (v != null && String.valueOf(v).startsWith("http")) {
                return v;
            }
        }
        return null;
    }

    @Override
    public void addClass(String className) {
        if (className != null && !className.isBlank()) {
            classRegistry.put(className, className);
            if (repository != null) {
                try (RepositoryConnection conn = repository.getConnection()) {
                    conn.add(iri(className), RDF.TYPE, OWL.CLASS);
                }
            }
        }
    }

    @Override
    public void addProperty(String propertyName) {
        if (propertyName != null && !propertyName.isBlank()) {
            propertyRegistry.put(propertyName, propertyName);
            if (repository != null) {
                try (RepositoryConnection conn = repository.getConnection()) {
                    boolean objectProp = isObjectPropertyName(propertyName);
                    conn.add(iri(propertyName), RDF.TYPE, objectProp ? OWL.OBJECTPROPERTY : OWL.DATATYPEPROPERTY);
                }
            }
        }
    }

    private boolean isObjectPropertyName(String name) {
        return Set.of(
                "hasIndicator", "soldThrough", "targets", "competesWith",
                "compliesWith", "promotedBy", "hasMetric", "soldOn",
                "configuresProduct", "belongsToCategory", "hasSalesPolicy", "hasReleaseScope",
                "hasNetworkCapability", "hasFamilyOfferPolicy", "hasChargePlan", "hasPreferentialPlan",
                "hasResourceEntitlement", "hasPrintNotice", "hasSmsNotice", "hasValueAddedEquity",
                "hasOfferCompatibility", "hasConfigChange", "governedBy", "appliesScene", "similarTo",
                "basedOnTemplate", "appliesConstraint", "constrainsScheme"
        ).contains(name);
    }

    @Override
    public List<Map<String, Object>> allInstances() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : instances.values()) {
            rows.add(new LinkedHashMap<>(item));
        }
        return rows;
    }

    @Override
    public void addInstance(String uri, String type, Map<String, Object> facts) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (facts != null) {
            row.putAll(facts);
        }
        row.put("uri", uri);
        row.put("type", type);
        instances.put(uri, row);
        if (type != null && !type.isBlank()) {
            classRegistry.putIfAbsent(type, type);
        }
        syncInstanceTriples(uri, type, row);
    }

    @Override
    public void updateInstance(String uri, Map<String, Object> facts) {
        Map<String, Object> row = instances.getOrDefault(uri, new LinkedHashMap<>());
        if (facts != null) {
            row.putAll(facts);
        }
        row.put("uri", uri);
        instances.put(uri, row);
        String type = String.valueOf(row.getOrDefault("type", "Thing"));
        syncInstanceTriples(uri, type, row);
    }

    @Override
    public void deleteInstance(String uri) {
        instances.remove(uri);
        if (repository != null && uri != null) {
            try (RepositoryConnection conn = repository.getConnection()) {
                IRI subject = iri(uri);
                conn.remove(subject, null, null);
            }
        }
    }

    /**
     * 将实例 Map 同步为 RDF 三元组（先清主体再写入）。
     */
    private void syncInstanceTriples(String uri, String type, Map<String, Object> row) {
        if (repository == null || uri == null || uri.isBlank()) {
            return;
        }
        try (RepositoryConnection conn = repository.getConnection()) {
            IRI subject = iri(uri);
            conn.remove(subject, null, null);
            if (type != null && !type.isBlank()) {
                conn.add(subject, RDF.TYPE, iri(type));
            }
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String key = e.getKey();
                if ("uri".equals(key) || "type".equals(key) || e.getValue() == null) {
                    continue;
                }
                propertyRegistry.putIfAbsent(key, key);
                writeFact(conn, subject, key, e.getValue());
            }
        } catch (Exception ex) {
            log.warn("[Rdf4jOntologyStore] 同步三元组失败 uri={}: {}", uri, ex.getMessage());
        }
    }

    private void writeFact(RepositoryConnection conn, IRI subject, String prop, Object value) {
        IRI predicate = iri(prop);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                writeSingleValue(conn, subject, predicate, prop, item);
            }
            return;
        }
        writeSingleValue(conn, subject, predicate, prop, value);
    }

    private void writeSingleValue(RepositoryConnection conn, IRI subject, IRI predicate, String prop, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean b) {
            conn.add(subject, predicate, vf().createLiteral(b));
            return;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            conn.add(subject, predicate, vf().createLiteral(((Number) value).longValue()));
            return;
        }
        if (value instanceof Number n) {
            conn.add(subject, predicate, vf().createLiteral(n.doubleValue()));
            return;
        }
        String text = String.valueOf(value);
        if (text.startsWith("http://") || text.startsWith("https://") || isObjectPropertyName(prop)) {
            conn.add(subject, predicate, iri(text));
            return;
        }
        // 尝试数值字面量
        try {
            if (text.matches("-?\\d+")) {
                conn.add(subject, predicate, vf().createLiteral(Long.parseLong(text)));
                return;
            }
            if (text.matches("-?\\d+(\\.\\d+)?")) {
                conn.add(subject, predicate, vf().createLiteral(Double.parseDouble(text)));
                return;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            conn.add(subject, predicate, vf().createLiteral(Boolean.parseBoolean(text)));
            return;
        }
        conn.add(subject, predicate, vf().createLiteral(text, XSD.STRING));
    }

    @Override
    public Map<String, Object> stats() {
        return Map.of(
                "classCount", classRegistry.size(),
                "propertyCount", propertyRegistry.size(),
                "instanceCount", instances.size(),
                "classes", classes(),
                "properties", properties(),
                "sparqlEngine", "rdf4j-sail-memory"
        );
    }

    public Map<String, Object> getEntity(String uri) {
        return new LinkedHashMap<>(instances.getOrDefault(uri, Map.of()));
    }

    public List<String> getClasses() {
        return classes();
    }

    public List<String> getProperties() {
        return properties();
    }

    public List<Map<String, Object>> getInstances(String className) {
        return samplesFor(className);
    }

    public void clear() {
        classRegistry.clear();
        propertyRegistry.clear();
        instances.clear();
        if (repository != null) {
            try (RepositoryConnection conn = repository.getConnection()) {
                conn.clear();
            }
        }
    }

    public Map<String, Object> getGraphData() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (String className : classRegistry.keySet()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "class_" + className);
            node.put("name", className);
            node.put("label", className);
            node.put("type", "class");
            nodes.add(node);
        }

        for (String propertyName : propertyRegistry.keySet()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "prop_" + propertyName);
            node.put("name", propertyName);
            node.put("label", propertyName);
            node.put("type", isObjectPropertyName(propertyName) ? "object_property" : "datatype_property");
            nodes.add(node);

            String className = propertyName.contains(":") ? propertyName.split(":")[0] : "Thing";
            if (classRegistry.containsKey(className)) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", "edge_" + className + "_" + propertyName);
                edge.put("source", "class_" + className);
                edge.put("target", "prop_" + propertyName);
                edge.put("label", "hasProperty");
                edge.put("type", "domain");
                edges.add(edge);
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : instances.entrySet()) {
            String uri = entry.getKey();
            Map<String, Object> facts = entry.getValue();
            String type = String.valueOf(facts.getOrDefault("type", "Unknown"));

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "inst_" + uri);
            node.put("name", uri);
            node.put("label", String.valueOf(facts.getOrDefault("productName",
                    facts.getOrDefault("channelName",
                            facts.getOrDefault("competitorName",
                                    facts.getOrDefault("indicatorName", uri))))));
            node.put("type", "instance");
            node.put("classId", "class_" + type);
            nodes.add(node);

            if (classRegistry.containsKey(type)) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", "edge_inst_" + uri + "_" + type);
                edge.put("source", "class_" + type);
                edge.put("target", "inst_" + uri);
                edge.put("label", "instanceOf");
                edge.put("type", "relation");
                edges.add(edge);
            }

            // 对象属性边
            for (String rel : List.of("hasIndicator", "soldThrough", "targets", "competesWith",
                    "compliesWith", "promotedBy")) {
                Object target = facts.get(rel);
                if (target == null) {
                    continue;
                }
                List<?> targets = target instanceof List<?> list ? list : List.of(target);
                for (Object t : targets) {
                    String targetUri = String.valueOf(t);
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("id", "edge_rel_" + uri + "_" + rel + "_" + targetUri);
                    edge.put("source", "inst_" + uri);
                    edge.put("target", "inst_" + targetUri);
                    edge.put("label", rel);
                    edge.put("type", "object_property");
                    edges.add(edge);
                }
            }
        }

        return Map.of(
                "nodes", nodes,
                "edges", edges,
                "classCount", classRegistry.size(),
                "propertyCount", propertyRegistry.size(),
                "instanceCount", instances.size(),
                "edgeCount", edges.size()
        );
    }

    /**
     * 用 RDF4J Rio 解析 Turtle，写入本存储的 class/property/instance 视图。
     */
    @Override
    public Map<String, Object> importTtl(String ttlContent, boolean replace) {
        if (ttlContent == null || ttlContent.isBlank()) {
            return Map.of("success", false, "message", "TTL 内容为空");
        }
        if (replace) {
            clear();
        }
        try {
            Model model = Rio.parse(new StringReader(ttlContent), "", RDFFormat.TURTLE);
            int classCount = 0;
            int propCount = 0;
            int instanceCount = 0;

            Set<String> schemaTypes = Set.of(
                    OWL.CLASS.stringValue(),
                    RDFS.CLASS.stringValue(),
                    OWL.OBJECTPROPERTY.stringValue(),
                    OWL.DATATYPEPROPERTY.stringValue(),
                    RDF.PROPERTY.stringValue(),
                    OWL.ONTOLOGY.stringValue()
            );

            for (Statement st : model.filter(null, RDF.TYPE, null)) {
                Resource subject = st.getSubject();
                Value object = st.getObject();
                if (!(subject instanceof IRI subIri) || !(object instanceof IRI typeIri)) {
                    continue;
                }
                String type = typeIri.stringValue();
                String local = localName(subIri);
                if (OWL.CLASS.stringValue().equals(type) || RDFS.CLASS.stringValue().equals(type)) {
                    addClass(local);
                    classCount++;
                } else if (OWL.OBJECTPROPERTY.stringValue().equals(type)
                        || OWL.DATATYPEPROPERTY.stringValue().equals(type)
                        || RDF.PROPERTY.stringValue().equals(type)) {
                    addProperty(local);
                    propCount++;
                }
            }

            Set<String> knownClasses = new LinkedHashSet<>(classRegistry.keySet());
            Map<String, Map<String, Object>> pending = new LinkedHashMap<>();
            for (Statement st : model.filter(null, RDF.TYPE, null)) {
                Resource subject = st.getSubject();
                Value object = st.getObject();
                if (!(subject instanceof IRI subIri) || !(object instanceof IRI typeIri)) {
                    continue;
                }
                String typeLocal = localName(typeIri);
                String typeUri = typeIri.stringValue();
                if (schemaTypes.contains(typeUri)) {
                    continue;
                }
                if (!knownClasses.contains(typeLocal)) {
                    addClass(typeLocal);
                    knownClasses.add(typeLocal);
                }
                String uri = subIri.stringValue();
                Map<String, Object> row = pending.computeIfAbsent(uri, k -> new LinkedHashMap<>());
                row.put("type", typeLocal);
                row.put("uri", uri);
            }

            for (Statement st : model) {
                if (RDF.TYPE.equals(st.getPredicate())) {
                    continue;
                }
                Resource subject = st.getSubject();
                if (!(subject instanceof IRI subIri)) {
                    continue;
                }
                String uri = subIri.stringValue();
                if (!pending.containsKey(uri) && !instances.containsKey(uri)) {
                    continue;
                }
                Map<String, Object> row = pending.computeIfAbsent(uri, k -> {
                    Map<String, Object> existing = instances.get(k);
                    return existing != null ? new LinkedHashMap<>(existing) : new LinkedHashMap<>();
                });
                String prop = localName(st.getPredicate());
                addProperty(prop);
                Object converted = valueToJava(st.getObject());
                Object prev = row.get(prop);
                if (prev == null) {
                    row.put(prop, converted);
                } else if (prev instanceof List<?> list) {
                    List<Object> copy = new ArrayList<>(list);
                    copy.add(converted);
                    row.put(prop, copy);
                } else {
                    row.put(prop, List.of(prev, converted));
                }
            }

            for (Map.Entry<String, Map<String, Object>> e : pending.entrySet()) {
                String type = String.valueOf(e.getValue().getOrDefault("type", "Thing"));
                addInstance(e.getKey(), type, e.getValue());
                instanceCount++;
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "TTL 导入成功");
            body.put("replace", replace);
            body.put("classesAdded", classCount);
            body.put("propertiesAdded", propCount);
            body.put("instancesAdded", instanceCount);
            body.put("stats", stats());
            return body;
        } catch (Exception e) {
            return Map.of("success", false, "message", "TTL 解析失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> sparqlQuery(String query) {
        return sparqlSelect(query);
    }

    private static String localName(Value v) {
        if (v instanceof IRI iri) {
            String local = iri.getLocalName();
            if (local != null && !local.isBlank()) {
                return local;
            }
            String s = iri.stringValue();
            int hash = s.lastIndexOf('#');
            int slash = s.lastIndexOf('/');
            int idx = Math.max(hash, slash);
            return idx >= 0 && idx < s.length() - 1 ? s.substring(idx + 1) : s;
        }
        return String.valueOf(v);
    }

    private static Object valueToJava(Value v) {
        if (v instanceof Literal lit) {
            try {
                if (lit.getDatatype() != null) {
                    String dt = lit.getDatatype().stringValue();
                    if (dt.endsWith("#integer") || dt.endsWith("#int") || dt.endsWith("#long")) {
                        return lit.longValue();
                    }
                    if (dt.endsWith("#decimal") || dt.endsWith("#double") || dt.endsWith("#float")) {
                        return lit.doubleValue();
                    }
                    if (dt.endsWith("#boolean")) {
                        return lit.booleanValue();
                    }
                }
            } catch (Exception ignored) {
                // fall through to label
            }
            return lit.getLabel();
        }
        if (v instanceof IRI iri) {
            return iri.stringValue();
        }
        return String.valueOf(v);
    }
}
