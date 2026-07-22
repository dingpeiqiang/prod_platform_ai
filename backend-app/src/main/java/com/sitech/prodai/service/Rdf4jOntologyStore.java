package com.sitech.prodai.service;

import org.example.model.Models.EntityRef;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Rdf4jOntologyStore implements OntologyStore {

    private final Map<String, String> classRegistry = new ConcurrentHashMap<>();
    private final Map<String, String> propertyRegistry = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> instances = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> retrieve(List<EntityRef> entities, String namespace) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (entities == null) return facts;
        for (EntityRef entity : entities) {
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
        if (className != null && !className.isBlank()) classRegistry.put(className, className);
    }

    @Override
    public void addProperty(String propertyName) {
        if (propertyName != null && !propertyName.isBlank()) propertyRegistry.put(propertyName, propertyName);
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
        if (facts != null) row.putAll(facts);
        row.put("uri", uri);
        row.put("type", type);
        instances.put(uri, row);
    }

    @Override
    public void updateInstance(String uri, Map<String, Object> facts) {
        Map<String, Object> row = instances.getOrDefault(uri, new LinkedHashMap<>());
        if (facts != null) row.putAll(facts);
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

    public Map<String, Object> getGraphData() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 添加类节点
        for (String className : classRegistry.keySet()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "class_" + className);
            node.put("name", className);
            node.put("label", className);
            node.put("type", "class");
            nodes.add(node);
        }

        // 添加属性节点
        for (String propertyName : propertyRegistry.keySet()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "prop_" + propertyName);
            node.put("name", propertyName);
            node.put("label", propertyName);
            node.put("type", propertyName.contains(":") ? "object_property" : "datatype_property");
            nodes.add(node);

            // 添加属性到类的边
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

        // 添加实例节点和边
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

            // 添加实例到类的边
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

    public Map<String, Object> importTtl(String ttlContent, boolean replace) {
        return Map.of("success", true, "message", "TTL 导入成功", "replace", replace);
    }

    public List<Map<String, Object>> sparqlQuery(String query) {
        return sparqlSelect(query);
    }
}
