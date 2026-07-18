package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Form / ontology schema mock loaded from classpath:ontologies/*.json.
 * Replaceable by DB / config center without changing API contract.
 */
@Service
public class FormMockService {

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Object>> ontologies = new ConcurrentHashMap<>();

    public FormMockService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadAll();
    }

    private void loadAll() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:ontologies/*.json");
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    Map<String, Object> raw = objectMapper.readValue(in, new TypeReference<>() {});
                    String code = firstNonBlank(
                            str(raw.get("formCode")),
                            str(raw.get("ontologyCode")),
                            resource.getFilename() == null ? null : resource.getFilename().replace(".json", "")
                    );
                    if (code == null || code.isBlank()) {
                        continue;
                    }
                    Map<String, Object> normalized = normalizeOntology(code, raw);
                    ontologies.put(code, normalized);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load ontologies mocks: " + e.getMessage(), e);
        }
        if (ontologies.isEmpty()) {
            ontologies.put("offering_config", defaultOfferingConfig());
        }
    }

    public Map<String, Object> listOntologies() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> o : ontologies.values()) {
            list.add(copy(o));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("ontologies", list);
        return body;
    }

    public Map<String, Object> getFormSchema(String formCode) {
        Map<String, Object> ontology = ontologies.get(formCode);
        if (ontology == null) {
            // case-insensitive fallback
            for (Map.Entry<String, Map<String, Object>> e : ontologies.entrySet()) {
                if (e.getKey().equalsIgnoreCase(formCode)) {
                    ontology = e.getValue();
                    break;
                }
            }
        }
        if (ontology == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("data", null);
            body.put("message", "表单不存在");
            return body;
        }

        List<Map<String, Object>> fields = flattenFields(ontology);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("formCode", ontology.get("ontologyCode"));
        data.put("formName", ontology.get("ontologyName"));
        data.put("version", ontology.getOrDefault("version", 1));
        data.put("description", ontology.getOrDefault("description", ""));
        data.put("category", ontology.getOrDefault("category", "general"));
        data.put("fields", fields);
        data.put("entities", ontology.get("entities"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("formCode", data.get("formCode"));
        body.put("formName", data.get("formName"));
        body.put("version", data.get("version"));
        body.put("fields", fields);
        body.put("entities", ontology.get("entities"));
        body.put("data", data);
        body.put("message", "ok");
        return body;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> flattenFields(Map<String, Object> ontology) {
        List<Map<String, Object>> fields = new ArrayList<>();
        Object entitiesObj = ontology.get("entities");
        if (!(entitiesObj instanceof List<?> entities)) {
            return fields;
        }
        for (Object entityObj : entities) {
            if (!(entityObj instanceof Map<?, ?> entity)) {
                continue;
            }
            Object fieldsObj = entity.get("fields");
            if (!(fieldsObj instanceof List<?> entityFields)) {
                continue;
            }
            for (Object fieldObj : entityFields) {
                if (!(fieldObj instanceof Map<?, ?> field)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                field.forEach((k, v) -> row.put(String.valueOf(k), v));
                // normalize options for frontend select
                if (!row.containsKey("options") && row.get("enumConfig") instanceof Map<?, ?> enumConfig) {
                    Object options = enumConfig.get("options");
                    if (options != null) {
                        row.put("options", options);
                    }
                }
                fields.add(row);
            }
        }
        return fields;
    }

    private Map<String, Object> normalizeOntology(String code, Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ontologyCode", code);
        out.put("ontologyName", firstNonBlank(str(raw.get("formName")), str(raw.get("ontologyName")), code));
        out.put("description", raw.getOrDefault("description", ""));
        out.put("category", raw.getOrDefault("category", "general"));
        out.put("isActive", raw.getOrDefault("isActive", true));
        out.put("version", raw.getOrDefault("version", 1));
        out.put("entities", raw.getOrDefault("entities", List.of()));
        out.put("formCode", code);
        out.put("formName", out.get("ontologyName"));
        return out;
    }

    private Map<String, Object> defaultOfferingConfig() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldCode", "offeringName");
        field.put("fieldName", "产商品名称");
        field.put("fieldType", "input");
        field.put("required", true);

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityCode", "offering_config");
        entity.put("entityName", "产商品配置草稿");
        entity.put("fields", List.of(field));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("formCode", "offering_config");
        raw.put("formName", "产商品配置草稿");
        raw.put("category", "product");
        raw.put("entities", List.of(entity));
        return normalizeOntology("offering_config", raw);
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
