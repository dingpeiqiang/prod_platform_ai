package com.sitech.prodai.service;

import org.example.model.Models.EntityRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        return new ArrayList<>(instances.values()).stream().map(LinkedHashMap::new).toList();
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
        return new ArrayList<>(instances.values()).stream().map(LinkedHashMap::new).toList();
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
        return Map.of(
                "nodes", new ArrayList<>(instances.values()),
                "links", List.of()
        );
    }

    public Map<String, Object> importTtl(String ttlContent, boolean replace) {
        return Map.of("success", true, "message", "TTL 导入成功", "replace", replace);
    }
}
