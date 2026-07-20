package org.example.store;

import org.example.model.Models;

import java.util.List;
import java.util.Map;

public interface OntologyStore {
    Map<String, Models.FactSet> retrieve(List<Models.EntityRef> refs, String namespace);

    List<String> classes();

    List<String> properties();

    List<Map<String, Object>> sparqlSelect(String query);

    List<Map<String, Object>> samplesFor(String className);

    void addClass(String className);

    void addProperty(String propertyName);

    List<Map<String, Object>> allInstances();

    void addInstance(String uri, String type, Map<String, Object> facts);

    void updateInstance(String uri, Map<String, Object> facts);

    void deleteInstance(String uri);

    Map<String, Object> stats();
}