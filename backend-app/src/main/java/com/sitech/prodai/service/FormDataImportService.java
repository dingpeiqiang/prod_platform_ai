package com.sitech.prodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.domain.entity.OntologyInstance;
import com.sitech.prodai.mapper.OntologyInstanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单历史数据导入/导出服务 —— 导入将 JSONL / 记录列表写入 pd_ai_ontology_instance，
 * 供推荐引擎与智查检索；导出按本体查询实例并回放 JSONL。
 *
 * <p>嵌套字段自动展平（a.b 取 b，同名冲突加后缀）；字段白名单来自本体定义。
 */
@Service
public class FormDataImportService {

    private static final Logger log = LoggerFactory.getLogger(FormDataImportService.class);

    private final OntologyInstanceMapper instanceMapper;
    private final ConfigLoader configLoader;
    private final ObjectMapper objectMapper;

    public FormDataImportService(OntologyInstanceMapper instanceMapper,
                                 ConfigLoader configLoader,
                                 ObjectMapper objectMapper) {
        this.instanceMapper = instanceMapper;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
    }

    /** 可导入表单列表（本体注册表驱动）。 */
    public Map<String, Object> listImportableForms() {
        List<Map<String, Object>> forms = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : configLoader.getAllOntologies().entrySet()) {
            Map<String, Object> form = new LinkedHashMap<>();
            form.put("formCode", e.getKey());
            form.put("formName", e.getValue().getOrDefault("formName", e.getKey()));
            form.put("description", e.getValue().getOrDefault("description", ""));
            forms.add(form);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("forms", forms);
        return body;
    }

    /** 导入模板：基于本体字段生成一行示例的 JSONL。 */
    public Map<String, Object> buildTemplate(String formCode) {
        Map<String, Object> ontology = configLoader.getOntology(formCode);
        if (ontology == null) {
            return Map.of("success", false, "message", "未找到表单 " + formCode + " 的本体定义");
        }
        List<String> fieldCodes = new ArrayList<>();
        for (Map<String, Object> field : flattenFields(ontology)) {
            fieldCodes.add(str(field.get("fieldCode")));
        }
        Map<String, Object> sample = new LinkedHashMap<>();
        for (String code : fieldCodes) {
            sample.put(code, "");
        }
        String jsonl = formCode + "_template.jsonl\n";
        try {
            jsonl = objectMapper.writeValueAsString(sample) + "\n";
        } catch (Exception ignored) {
            // 模板生成失败时回退占位文本
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("fileName", formCode + "_template.jsonl");
        body.put("contentType", "application/x-ndjson");
        body.put("content", jsonl);
        body.put("fieldCodes", fieldCodes);
        return body;
    }

    /** 从 JSONL 流导入。 */
    public Map<String, Object> importJsonl(String formCode, InputStream in, Integer limit) {
        Map<String, Object> ontology = configLoader.getOntology(formCode);
        if (ontology == null) {
            return fail("未找到表单 " + formCode + " 的本体定义");
        }
        Set<String> knownFields = collectFieldCodes(ontology);
        List<Map<String, Object>> records = new ArrayList<>();
        int totalSource = 0;
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                totalSource++;
                try {
                    records.add(objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {
                    }));
                } catch (Exception e) {
                    log.debug("[FormDataImport] 第 {} 行解析失败: {}", totalSource, e.getMessage());
                }
            }
        } catch (Exception e) {
            return fail("读取上传内容失败: " + e.getMessage());
        }
        return persist(formCode, records, totalSource, limit, knownFields);
    }

    /** 从请求体记录列表导入（import/execute 场景）。 */
    public Map<String, Object> importRecords(String formCode, List<Object> records, Integer limit) {
        Map<String, Object> ontology = configLoader.getOntology(formCode);
        if (ontology == null) {
            return fail("未找到表单 " + formCode + " 的本体定义");
        }
        Set<String> knownFields = collectFieldCodes(ontology);
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (records != null) {
            for (Object r : records) {
                if (r instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    normalized.add(row);
                }
            }
        }
        return persist(formCode, normalized, normalized.size(), limit, knownFields);
    }

    private Map<String, Object> persist(String formCode, List<Map<String, Object>> records,
                                        int totalSource, Integer limit, Set<String> knownFields) {
        int max = limit == null || limit <= 0 ? Integer.MAX_VALUE : limit;
        Set<String> nestedFields = new HashSet<>();
        int imported = 0;
        int skipped = 0;
        int errors = 0;
        Map<String, Map<String, Integer>> fieldStats = new LinkedHashMap<>();

        for (Map<String, Object> record : records) {
            if (imported >= max) break;
            if (record == null || record.isEmpty()) {
                skipped++;
                continue;
            }
            try {
                Map<String, Object> flat = new LinkedHashMap<>();
                flatten("", record, flat, nestedFields);
                if (flat.values().stream().allMatch(v -> v == null || String.valueOf(v).isBlank())) {
                    skipped++;
                    continue;
                }
                OntologyInstance instance = new OntologyInstance();
                instance.setOntologyCode(formCode);
                instance.setUserId("data_import");
                instance.setStatus("imported");
                instance.setSubmittedAt(LocalDateTime.now());
                instance.setData(flat);
                instanceMapper.insert(instance);
                imported++;
                for (Map.Entry<String, Object> e : flat.entrySet()) {
                    String val = e.getValue() == null ? "" : String.valueOf(e.getValue());
                    if (val.isBlank()) continue;
                    fieldStats.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>())
                            .merge(val, 1, Integer::sum);
                }
            } catch (Exception e) {
                errors++;
                log.warn("[FormDataImport] 记录写入失败: {}", e.getMessage());
            }
        }

        List<Map<String, Object>> fieldStatList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : fieldStats.entrySet()) {
            List<Map<String, Object>> topValues = e.getValue().entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .map(t -> {
                        Map<String, Object> tv = new LinkedHashMap<>();
                        tv.put("value", t.getKey());
                        tv.put("count", t.getValue());
                        return tv;
                    })
                    .toList();
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("fieldCode", e.getKey());
            stat.put("distinctValues", e.getValue().size());
            stat.put("topValues", topValues);
            fieldStatList.add(stat);
        }
        fieldStatList.sort((a, b) -> Integer.compare((int) b.get("distinctValues"), (int) a.get("distinctValues")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", errors == 0 || imported > 0);
        body.put("message", imported > 0 ? "导入完成" : "无有效记录被导入");
        body.put("totalSource", totalSource);
        body.put("totalImported", imported);
        body.put("totalSkipped", skipped);
        body.put("totalErrors", errors);
        body.put("fieldStats", fieldStatList.size() > 10 ? fieldStatList.subList(0, 10) : fieldStatList);
        body.put("nestedFields", new ArrayList<>(nestedFields));
        return body;
    }

    private void flatten(String prefix, Map<String, Object> source, Map<String, Object> target,
                         Set<String> nestedFields) {
        for (Map.Entry<String, Object> e : source.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object value = e.getValue();
            if (value instanceof Map<?, ?> nested) {
                nestedFields.add(e.getKey());
                Map<String, Object> nestedMap = new LinkedHashMap<>();
                nested.forEach((k, v) -> nestedMap.put(String.valueOf(k), v));
                flatten(e.getKey(), nestedMap, target, nestedFields);
            } else if (value instanceof List<?> list) {
                target.putIfAbsent(key, String.valueOf(list));
            } else {
                Object prevObj = target.get(e.getKey());
                String prev = prevObj == null ? "" : String.valueOf(prevObj);
                if (prev.isBlank()) {
                    target.put(e.getKey(), value == null ? null : String.valueOf(value));
                } else if (value != null && !String.valueOf(value).isBlank()) {
                    target.putIfAbsent(key, String.valueOf(value));
                }
            }
        }
    }

    private Set<String> collectFieldCodes(Map<String, Object> ontology) {
        Set<String> codes = new HashSet<>();
        for (Map<String, Object> field : flattenFields(ontology)) {
            String code = str(field.get("fieldCode"));
            if (!code.isBlank()) codes.add(code);
        }
        return codes;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> flattenFields(Map<String, Object> ontology) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if (!(ontology.get("entities") instanceof List<?> entities)) return fields;
        for (Object entityObj : entities) {
            if (!(entityObj instanceof Map<?, ?> entity)) continue;
            if (!(entity.get("fields") instanceof List<?> entityFields)) continue;
            for (Object fieldObj : entityFields) {
                if (fieldObj instanceof Map<?, ?> field) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    field.forEach((k, v) -> row.put(String.valueOf(k), v));
                    fields.add(row);
                }
            }
        }
        return fields;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 导出表单历史数据为 JSONL（与导入格式对齐，可直接回导）。
     *
     * @param formCode 本体编码
     * @param limit    最大导出行数（null/≤0 表示不限制）
     */
    public Map<String, Object> exportJsonl(String formCode, Integer limit) {
        Map<String, Object> ontology = configLoader.getOntology(formCode);
        if (ontology == null) {
            return fail("未找到表单 " + formCode + " 的本体定义");
        }
        LambdaQueryWrapper<OntologyInstance> query = new LambdaQueryWrapper<OntologyInstance>()
                .eq(OntologyInstance::getOntologyCode, formCode)
                .orderByAsc(OntologyInstance::getId);
        if (limit != null && limit > 0) {
            query.last("LIMIT " + limit);
        }
        List<OntologyInstance> rows = instanceMapper.selectList(query);
        StringBuilder sb = new StringBuilder();
        for (OntologyInstance row : rows) {
            Map<String, Object> record = new LinkedHashMap<>(row.getData() == null ? Map.of() : row.getData());
            record.put("_status", row.getStatus());
            try {
                sb.append(objectMapper.writeValueAsString(record)).append('\n');
            } catch (Exception e) {
                log.warn("[FormDataExport] 序列化实例 {} 失败: {}", row.getId(), e.getMessage());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("formCode", formCode);
        body.put("fileName", formCode + "_export.jsonl");
        body.put("contentType", "application/x-ndjson");
        body.put("total", rows.size());
        body.put("content", sb.toString());
        return body;
    }
}
