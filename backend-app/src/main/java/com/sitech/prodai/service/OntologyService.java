package com.sitech.prodai.service;

import com.sitech.prodai.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本体只读查询服务 —— 对齐 Python {@code app/services/ontology_service.py::OntologyService}。
 *
 * <p>从 {@link ConfigLoader} 读取本体定义（文件数据源），不支持增删改（对齐 Python 行为）。
 * 返回结构严格对齐 Python dict，包含 camelCase 与少量 snake_case key（如 default_values）。
 */
@Service
public class OntologyService {

    private static final Logger log = LoggerFactory.getLogger(OntologyService.class);

    private final ConfigLoader configLoader;

    public OntologyService(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    /** 对齐 Python get_categories */
    public List<Map<String, String>> getCategories() {
        return List.of(
                cat("general", "通用本体"),
                cat("tariff", "资费备案"),
                cat("product", "产商品配置"),
                cat("customer", "客户信息"),
                cat("business", "业务流程")
        );
    }

    private Map<String, String> cat(String code, String name) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }

    /** 对齐 Python list_ontologies */
    public Map<String, Object> listOntologies(String category, Boolean isActive) {
        try {
            Map<String, Map<String, Object>> ontologies = configLoader.getAllOntologies();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> e : ontologies.entrySet()) {
                String code = e.getKey();
                Map<String, Object> data = e.getValue();
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("ontologyCode", code);
                info.put("ontologyName", str(data.get("formName"), code));
                info.put("category", str(data.get("category"), "general"));
                info.put("description", str(data.get("description"), ""));
                info.put("entities", data.getOrDefault("entities", List.of()));
                info.put("isActive", true);
                if (category != null && !category.isEmpty() && !category.equals(info.get("category"))) {
                    continue;
                }
                if (isActive != null && !isActive) {
                    continue;
                }
                result.add(info);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", result);
            return body;
        } catch (Exception e) {
            log.error("[OntologyService] list_ontologies 失败", e);
            return fail(str(e));
        }
    }

    /** 对齐 Python get_ontology */
    public Map<String, Object> getOntology(String ontologyCode) {
        try {
            Map<String, Object> ontology = configLoader.getOntology(ontologyCode);
            if (ontology == null) {
                return fail("本体 " + ontologyCode + " 不存在");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ontologyCode", ontologyCode);
            data.put("ontologyName", str(ontology.get("formName"), ontologyCode));
            data.put("category", str(ontology.get("category"), "general"));
            data.put("description", str(ontology.get("description"), ""));
            data.put("entities", ontology.getOrDefault("entities", List.of()));
            data.put("isActive", true);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return body;
        } catch (Exception e) {
            log.error("[OntologyService] get_ontology 失败", e);
            return fail(str(e));
        }
    }

    /** 对齐 Python get_business_rules */
    public Map<String, Object> getBusinessRules(String ontologyCode) {
        try {
            Map<String, Object> ontology = configLoader.getOntology(ontologyCode);
            if (ontology == null) {
                return fail("本体 " + ontologyCode + " 不存在");
            }
            Map<String, Object> defaultValues = new LinkedHashMap<>();
            Map<String, Object> validationRules = new LinkedHashMap<>();
            Map<String, Object> fieldMappings = new LinkedHashMap<>();
            List<Object> businessRules = new ArrayList<>();

            for (Object entityObj : asList(ontology.get("entities"))) {
                if (!(entityObj instanceof Map<?, ?> entity)) {
                    continue;
                }
                for (Object fieldObj : asList(entity.get("fields"))) {
                    if (!(fieldObj instanceof Map<?, ?> field)) {
                        continue;
                    }
                    String fieldCode = str(field.get("fieldCode"));
                    if (fieldCode == null || fieldCode.isEmpty()) {
                        continue;
                    }
                    if (field.containsKey("defaultValue")) {
                        defaultValues.put(fieldCode, field.get("defaultValue"));
                    }
                    Map<String, Object> rules = new LinkedHashMap<>();
                    if (Boolean.TRUE.equals(field.get("required"))) {
                        rules.put("required", true);
                    }
                    putIfPresent(rules, field, "minLength");
                    putIfPresent(rules, field, "maxLength");
                    putIfPresent(rules, field, "pattern");
                    putIfPresent(rules, field, "min");
                    putIfPresent(rules, field, "max");
                    if (!rules.isEmpty()) {
                        validationRules.put(fieldCode, rules);
                    }
                    if (field.containsKey("label")) {
                        fieldMappings.put(fieldCode, field.get("label"));
                    }
                }
            }
            businessRules.addAll(asList(ontology.get("businessRules")));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("default_values", defaultValues);
            data.put("validation_rules", validationRules);
            data.put("field_mappings", fieldMappings);
            data.put("business_rules", businessRules);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return body;
        } catch (Exception e) {
            log.error("[OntologyService] get_business_rules 失败", e);
            return fail(str(e));
        }
    }

    /** 对齐 Python get_form_constraint */
    public Map<String, Object> getFormConstraint(String formCode) {
        Map<String, Object> ontology = configLoader.getOntology(formCode);
        if (ontology != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("constraints", ontology);
            return body;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("constraints", new LinkedHashMap<>());
        body.put("message", "未找到表单代码 " + formCode + " 的本体约束");
        return body;
    }

    /** 对齐 Python get_all_ontologies */
    public Map<String, Object> getAllOntologies() {
        Map<String, Map<String, Object>> ontologies = configLoader.getAllOntologies();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : ontologies.entrySet()) {
            String code = e.getKey();
            Map<String, Object> ont = e.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("formCode", code);
            item.put("formName", str(ont.get("formName"), code));
            item.put("description", str(ont.get("description"), ""));
            list.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("ontologies", list);
        return body;
    }

    /** 对齐 Python validate_schema */
    public Map<String, Object> validateSchema(String formCode, Map<String, Object> schema) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> ontology = configLoader.getOntology(formCode);
        if (ontology == null) {
            errors.add("表单代码 " + formCode + " 不存在于本体中");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("valid", false);
            body.put("errors", errors);
            return body;
        }
        java.util.Set<String> ontologyFieldCodes = new java.util.LinkedHashSet<>();
        for (Object entityObj : asList(ontology.get("entities"))) {
            if (!(entityObj instanceof Map<?, ?> entity)) {
                continue;
            }
            for (Object fieldObj : asList(entity.get("fields"))) {
                if (fieldObj instanceof Map<?, ?> field && field.get("fieldCode") != null) {
                    ontologyFieldCodes.add(str(field.get("fieldCode")));
                }
            }
        }
        java.util.Set<String> schemaFieldCodes = new java.util.LinkedHashSet<>();
        for (Object fieldObj : asList(schema.get("fields"))) {
            if (fieldObj instanceof Map<?, ?> field && field.get("fieldCode") != null) {
                schemaFieldCodes.add(str(field.get("fieldCode")));
            }
        }
        for (String code : schemaFieldCodes) {
            if (!ontologyFieldCodes.contains(code)) {
                errors.add("字段 " + code + " 不在本体定义中");
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("valid", errors.isEmpty());
        body.put("errors", errors);
        return body;
    }

    // ==================== 不支持的操作（文件数据源，对齐 Python） ====================

    public Map<String, Object> createOntology(Map<String, Object> data, String user) {
        return fail("本体管理功能已切换为文件数据源，不支持创建本体");
    }

    public Map<String, Object> updateOntology(String code, Map<String, Object> data, String user) {
        return fail("本体管理功能已切换为文件数据源，不支持更新本体");
    }

    public Map<String, Object> deleteOntology(String code) {
        return fail("本体管理功能已切换为文件数据源，不支持删除本体");
    }

    public Map<String, Object> toggleActive(String code) {
        return fail("本体管理功能已切换为文件数据源，不支持切换本体状态");
    }

    // ==================== 工具方法 ====================

    private void putIfPresent(Map<String, Object> rules, Map<?, ?> field, String key) {
        if (field.containsKey(key)) {
            rules.put(key, field.get(key));
        }
    }

    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String str(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value);
        return s.isEmpty() ? defaultValue : s;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
