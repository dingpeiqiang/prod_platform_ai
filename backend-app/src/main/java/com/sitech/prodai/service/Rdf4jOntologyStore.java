package com.sitech.prodai.service;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Rdf4jOntologyStore implements OntologyStore {

    private final Map<String, String> classRegistry = new ConcurrentHashMap<>();
    private final Map<String, String> propertyRegistry = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> instances = new ConcurrentHashMap<>();

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
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : instances.values()) {
            rows.add(new LinkedHashMap<>(item));
        }
        return rows;
    }

    @Override
    public void addClass(String className) {
        if (className != null && !className.isBlank()) {
            classRegistry.put(className, className);
        }
    }

    @Override
    public void addProperty(String propertyName) {
        if (propertyName != null && !propertyName.isBlank()) {
            propertyRegistry.put(propertyName, propertyName);
        }
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
    }

    @Override
    public void updateInstance(String uri, Map<String, Object> facts) {
        Map<String, Object> row = instances.getOrDefault(uri, new LinkedHashMap<>());
        if (facts != null) {
            row.putAll(facts);
        }
        row.put("uri", uri);
        instances.put(uri, row);
    }

    @Override
    public void deleteInstance(String uri) {
        instances.remove(uri);
    }

    @Override
    public Map<String, Object> stats() {
        return Map.of(
                "classCount", classRegistry.size(),
                "propertyCount", propertyRegistry.size(),
                "instanceCount", instances.size(),
                "classes", classes(),
                "properties", properties()
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
            node.put("type", propertyName.contains(":") ? "object_property" : "datatype_property");
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
            node.put("label", uri);
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

            // 1) 类 / 属性
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

            // 2) 实例（rdf:type 指向已登记类，或非 schema 类型）
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

            // 3) 实例属性三元组
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
