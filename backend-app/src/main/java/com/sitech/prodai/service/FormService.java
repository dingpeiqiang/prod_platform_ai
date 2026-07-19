package com.sitech.prodai.service;

import com.sitech.prodai.domain.entity.OntologyInstance;
import com.sitech.prodai.repository.OntologyInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FormService {

    private static final Logger log = LoggerFactory.getLogger(FormService.class);

    private final OntologyService ontologyService;
    private final OntologyInstanceRepository instanceRepository;
    private final Map<String, Map<String, Object>> formStates = new ConcurrentHashMap<>();
    private final AtomicLong instanceIdSeq = new AtomicLong(1000);

    public FormService(OntologyService ontologyService, OntologyInstanceRepository instanceRepository) {
        this.ontologyService = ontologyService;
        this.instanceRepository = instanceRepository;
    }

    public Map<String, Object> generateForm(String userInput, String formCode, String userId,
                                            Map<String, Object> extractedFields,
                                            Map<String, Object> fieldRecommendations) {
        Map<String, Object> ontologyResult = ontologyService.getFormConstraint(formCode);
        if (!Boolean.TRUE.equals(ontologyResult.get("success"))) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "未找到表单 " + formCode + " 的本体定义");
            return fail;
        }
        Map<String, Object> constraints = castMap(ontologyResult.get("constraints"));
        List<Object> entities = asList(constraints.get("entities"));

        List<Map<String, Object>> fields = new ArrayList<>();
        for (Object entityObj : entities) {
            if (!(entityObj instanceof Map<?, ?> entity)) continue;
            for (Object fieldDefObj : asList(entity.get("fields"))) {
                if (!(fieldDefObj instanceof Map<?, ?> fieldDef)) continue;
                String fieldCode = str(fieldDef.get("fieldCode"));
                Map<String, Object> fieldInfo = new LinkedHashMap<>();
                fieldInfo.put("fieldCode", fieldCode);
                fieldInfo.put("fieldName", fieldDef.get("fieldName"));
                fieldInfo.put("fieldType", str(fieldDef.get("fieldType")) != null && !str(fieldDef.get("fieldType")).isEmpty() ? str(fieldDef.get("fieldType")) : "input");
                fieldInfo.put("required", Boolean.TRUE.equals(fieldDef.get("required")));
                fieldInfo.put("disabled", false);
                fieldInfo.put("hidden", false);
                fieldInfo.put("rules", List.of());
                fieldInfo.put("recommend", List.of());
                fieldInfo.put("defaultValue", null);
                fieldInfo.put("options", asList(fieldDef.get("options")));
                if (fieldDef.get("enumConfig") instanceof Map<?, ?> enumConfig) {
                    fieldInfo.put("enumConfig", enumConfig);
                }
                if (extractedFields != null && fieldCode != null && extractedFields.containsKey(fieldCode)) {
                    fieldInfo.put("defaultValue", extractedFields.get(fieldCode));
                }
                if (fieldRecommendations != null && fieldCode != null && fieldRecommendations.containsKey(fieldCode)) {
                    Object rec = fieldRecommendations.get(fieldCode);
                    if (rec instanceof Map<?, ?> recMap && recMap.get("items") instanceof List<?> items) {
                        fieldInfo.put("recommend", items);
                    } else if (rec instanceof List<?> list) {
                        fieldInfo.put("recommend", list);
                    }
                }
                fields.add(fieldInfo);
            }
        }

        Map<String, Object> formSchema = new LinkedHashMap<>();
        formSchema.put("formCode", formCode);
        formSchema.put("formName", constraints.getOrDefault("formName", formCode));
        formSchema.put("version", 1);
        formSchema.put("globalControl", Map.of());
        formSchema.put("fields", fields);

        String formId = "form_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("version", 1);
        state.put("schema", formSchema);
        state.put("formCode", formCode);
        state.put("userId", userId);
        formStates.put(formId, state);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("formSchema", formSchema);
        body.put("formId", formId);
        body.put("message", "表单生成成功");
        return body;
    }

    public Map<String, Object> generateForm(Map<String, Object> request) {
        String formCode = firstNonBlank(request == null ? null : str(request.get("formCode")), "offering_config");
        Map<String, Object> extracted = request != null && request.get("extractedFields") instanceof Map<?, ?> ? castMap(request.get("extractedFields")) : Map.of();
        Map<String, Object> recommendations = request != null && request.get("fieldRecommendations") instanceof Map<?, ?> ? castMap(request.get("fieldRecommendations")) : Map.of();
        String userId = request == null ? null : str(request.get("userId"));
        String userInput = request == null ? null : str(request.get("userInput"));
        return generateForm(userInput, formCode, userId, extracted, recommendations);
    }

    public Map<String, Object> listOntologies() {
        return ontologyService.getAllOntologies();
    }

    public Map<String, Object> getFormSchema(String formCode) {
        Map<String, Object> ontology = ontologyService.getOntology(formCode);
        if (!Boolean.TRUE.equals(ontology.get("success"))) {
            return Map.of("success", false, "data", null, "message", "表单不存在");
        }
        Map<String, Object> data = castMap(ontology.get("data"));
        List<Map<String, Object>> fields = flattenFields(data);
        return Map.of("success", true, "formCode", data.get("ontologyCode"), "formName", data.get("ontologyName"), "version", 1, "fields", fields, "entities", data.get("entities"), "data", data, "message", "ok");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> submitForm(Map<String, Object> request) {
        if (request == null) return Map.of("success", false, "message", "request is required");
        String formId = str(request.get("formId"));
        String formCode = str(request.get("formCode"));
        String userId = request.get("userId") == null ? "anonymous" : str(request.get("userId"));
        String sessionId = str(request.get("sessionId"));
        Map<String, Object> state = formId == null ? null : formStates.get(formId);
        Object clientVersion = request.get("version");
        if (state != null && clientVersion != null) {
            Object serverVersion = state.get("version");
            if (serverVersion != null && !String.valueOf(serverVersion).equals(String.valueOf(clientVersion))) {
                return Map.of("success", false, "message", "表单版本不匹配，请刷新后重试");
            }
        }
        if (formCode == null || formCode.isEmpty()) formCode = state == null ? null : str(state.get("formCode"));
        OntologyInstance instance = new OntologyInstance();
        instance.setOntologyCode(formCode == null ? "unknown" : formCode);
        instance.setUserId(userId);
        instance.setSessionId(sessionId == null || sessionId.isEmpty() ? null : sessionId);
        Object dataObj = request.getOrDefault("data", Map.of());
        instance.setData(dataObj instanceof Map ? castMap(dataObj) : new LinkedHashMap<>());
        instance.setStatus("submitted");
        instance.setSubmittedAt(LocalDateTime.now());
        OntologyInstance saved = instanceRepository.save(instance);
        if (state != null) state.put("version", ((Number) state.getOrDefault("version", 1)).intValue() + 1);
        return Map.of("success", true, "message", "表单提交成功", "formInstanceId", saved.getId());
    }

    public Map<String, Object> validateWithLlm(Map<String, Object> request) {
        String formCode = firstNonBlank(request == null ? null : str(request.get("form_code")), request == null ? null : str(request.get("formCode")));
        Map<String, Object> data = request != null && request.get("data") instanceof Map<?, ?> ? castMap(request.get("data")) : Map.of();
        List<Map<String, Object>> errors = new ArrayList<>();
        if (formCode != null && !formCode.isEmpty()) {
            Map<String, Object> schema = getFormSchema(formCode);
            Object fieldsObj = schema.get("fields");
            if (fieldsObj instanceof List<?> fields) {
                for (Object fieldObj : fields) {
                    if (!(fieldObj instanceof Map<?, ?> field)) continue;
                    String code = str(field.get("fieldCode"));
                    String name = firstNonBlank(str(field.get("fieldName")), code, "未知字段");
                    boolean required = Boolean.TRUE.equals(field.get("required"));
                    Object value = code == null ? null : data.get(code);
                    boolean empty = value == null || (value instanceof String s && s.isBlank()) || (value instanceof List<?> list && list.isEmpty());
                    if (required && empty) {
                        errors.add(Map.of("field_name", name, "field_code", code == null ? "" : code, "reason", name + " 为必填项", "level", "error"));
                    }
                }
            }
        }
        return Map.of("success", true, "valid", errors.isEmpty(), "errors", errors, "warnings", List.of(), "reasoning", List.of("基于本体 required 规则的本地校验"));
    }

    public Map<String, Object> adaptForChatWindow(Map<String, Object> formSchema) {
        return formSchema;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> flattenFields(Map<String, Object> ontology) {
        List<Map<String, Object>> fields = new ArrayList<>();
        Object entitiesObj = ontology.get("entities");
        if (!(entitiesObj instanceof List<?> entities)) return fields;
        for (Object entityObj : entities) {
            if (!(entityObj instanceof Map<?, ?> entity)) continue;
            Object fieldsObj = entity.get("fields");
            if (!(fieldsObj instanceof List<?> entityFields)) continue;
            for (Object fieldObj : entityFields) {
                if (!(fieldObj instanceof Map<?, ?> field)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                field.forEach((k, v) -> row.put(String.valueOf(k), v));
                fields.add(row);
            }
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>(); }
    private List<Object> asList(Object value) { return value instanceof List<?> list ? new ArrayList<>(list) : List.of(); }
    private String str(Object value) { return value == null ? "" : String.valueOf(value); }
    private String firstNonBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v; return null; }
}
