package com.sitech.prodai.service;

import com.sitech.prodai.domain.EntityRef;

import java.util.List;
import java.util.Map;

public interface OntologyStore {
    Map<String, Object> retrieve(List<EntityRef> entities, String namespace);
    List<String> classes();
    List<String> properties();
    List<Map<String, Object>> samplesFor(String className);
    List<Map<String, Object>> sparqlSelect(String query);
    void addClass(String className);
    void addProperty(String propertyName);
    List<Map<String, Object>> allInstances();
    void addInstance(String uri, String type, Map<String, Object> facts);
    void updateInstance(String uri, Map<String, Object> facts);
    void deleteInstance(String uri);
    Map<String, Object> stats();
    Map<String, Object> getGraphData();
    Map<String, Object> importTtl(String ttlContent, boolean replace);
    Map<String, Object> getEntity(String uri);
}
