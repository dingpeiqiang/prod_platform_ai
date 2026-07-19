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
}
