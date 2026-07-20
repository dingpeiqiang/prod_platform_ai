package org.example.store;

import org.example.model.Models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOntologyStore implements OntologyStore {

    private final Map<String, Map<String, Object>> entities = new ConcurrentHashMap<>();
    private final Set<String> classes = ConcurrentHashMap.newKeySet();
    private final Set<String> properties = ConcurrentHashMap.newKeySet();

    public InMemoryOntologyStore() {
        seed();
    }

    @Override
    public Map<String, Models.FactSet> retrieve(List<Models.EntityRef> refs, String namespace) {
        Map<String, Models.FactSet> out = new LinkedHashMap<>();
        for (Models.EntityRef ref : refs) {
            String uri = ref.normalizedUri(namespace);
            Map<String, Object> fact = entities.getOrDefault(uri, defaultFact(ref));
            out.put(uri, new Models.FactSet(fact));
        }
        return out;
    }

    @Override
    public List<String> classes() {
        return new ArrayList<>(classes);
    }

    @Override
    public List<String> properties() {
        return new ArrayList<>(properties);
    }

    @Override
    public List<Map<String, Object>> sparqlSelect(String query) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : entities.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entity", entry.getKey());
            row.putAll(entry.getValue());
            rows.add(row);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> samplesFor(String className) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : entities.entrySet()) {
            Map<String, Object> sample = new LinkedHashMap<>(entry.getValue());
            sample.put("class_name", className);
            sample.put("entity", entry.getKey());
            samples.add(sample);
        }
        if (samples.isEmpty()) {
            samples.add(Map.of("class_name", className, "entity", className + "_001", "vipLevel", "Gold", "annualSpend", 80000));
        }
        return samples;
    }

    private Map<String, Object> defaultFact(Models.EntityRef ref) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("entityId", ref.id());
        fact.put("entityType", ref.type());
        fact.put("source", ref.source());
        fact.put("vipLevel", "Gold");
        fact.put("annualSpend", 80000);
        fact.put("memberYears", 3);
        return fact;
    }

    @Override
    public void addClass(String className) {
        classes.add(className);
    }

    @Override
    public void addProperty(String propertyName) {
        properties.add(propertyName);
    }

    @Override
    public List<Map<String, Object>> allInstances() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : entities.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>(entry.getValue());
            item.put("uri", entry.getKey());
            list.add(item);
        }
        return list;
    }

    @Override
    public void addInstance(String uri, String type, Map<String, Object> facts) {
        Map<String, Object> fact = new LinkedHashMap<>(facts);
        fact.putIfAbsent("entityId", uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1) : uri);
        fact.putIfAbsent("entityType", type);
        fact.putIfAbsent("source", "ontology");
        entities.put(uri, fact);
        classes.add(type);
    }

    @Override
    public void updateInstance(String uri, Map<String, Object> facts) {
        entities.computeIfPresent(uri, (k, v) -> {
            Map<String, Object> merged = new LinkedHashMap<>(v);
            merged.putAll(facts);
            return merged;
        });
    }

    @Override
    public void deleteInstance(String uri) {
        entities.remove(uri);
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("classCount", classes.size());
        s.put("propertyCount", properties.size());
        s.put("instanceCount", entities.size());
        s.put("classes", List.copyOf(classes));
        s.put("properties", List.copyOf(properties));
        return s;
    }

    private void seed() {
        classes.addAll(List.of("Customer", "Account", "Invoice", "Payment"));
        properties.addAll(List.of("vipLevel", "annualSpend", "memberYears", "creditLimit", "accountStatus", "outstandingBalance"));
        entities.put("http://example.org/Customer_Li", defaultFact(new Models.EntityRef("Customer_Li", "Customer", "ontology")));
    }
}
