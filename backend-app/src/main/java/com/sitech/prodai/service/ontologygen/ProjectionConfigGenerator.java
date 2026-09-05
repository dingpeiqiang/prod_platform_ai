package com.sitech.prodai.service.ontologygen;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 投影配置生成器（R4-Step3）：从模板 JSON（SSOT）生成 config_message_projection.json。
 * <p>映射规则（与 scripts/inject_projection_source.py 迁移语义一致）：
 * <ul>
 *   <li>{@code categories} ← 品类模板 {@code category_meta} + {@code template_name}</li>
 *   <li>{@code sharedMappings} ← commonBasePrc 字段 {@code business_key}/{@code message_path}/{@code draft_aliases}</li>
 *   <li>{@code categoryMappings[tid]} ← 品类模板 raw 字段（不含继承）</li>
 *   <li>{@code defaultsByCategory} ← 模板 {@code message_projection.defaults}</li>
 * </ul>
 * 生成物标记 {@code generated_from}；CI 门禁：TemplateSingleSourceConsistencyTest 重新生成并与仓库文件比对。
 */
public final class ProjectionConfigGenerator {

    /** 投影映射的公共基类模板（sharedMappings 单源）。 */
    public static final String COMMON_TEMPLATE_ID = "commonBasePrc";

    private ProjectionConfigGenerator() {
    }

    /**
     * 从模板集合生成投影配置结构。
     *
     * @param templates template_id → 原始模板 JSON（保持文件序）
     */
    public static Map<String, Object> generate(Map<String, Map<String, Object>> templates) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("version", "Generated-v3.0");
        config.put("generated_from", "ontologies/templates/*.json (R4 SSOT) DO NOT EDIT");
        config.put("description", "产商品报文投影配置（GENERATED）。单源为 ontologies/templates/*.json，"
                + "由 ProjectionConfigGenerator 生成；手工修改将被 CI 门禁拒绝。");

        Map<String, Object> categories = new LinkedHashMap<>();
        Map<String, Object> defaultsByCategory = new LinkedHashMap<>();
        Map<String, Object> categoryMappings = new LinkedHashMap<>();
        List<Map<String, Object>> sharedMappings = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : templates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            String templateId = entry.getKey();
            Map<String, Object> template = entry.getValue();
            Object metaObj = template.get("category_meta");
            if (!(metaObj instanceof Map<?, ?> meta)) {
                continue; // common 基类模板无品类元数据
            }
            Map<String, Object> category = new LinkedHashMap<>();
            category.put("label", template.get("template_name"));
            category.put("isMainOffer", Boolean.TRUE.equals(meta.get("is_main_offer")));
            category.put("productLine", meta.get("product_line"));
            category.put("requiredElements", meta.get("required_elements"));
            categories.put(templateId, category);

            Object defaults = castMap(template.get("message_projection")).get("defaults");
            defaultsByCategory.put(templateId, defaults != null ? defaults : new LinkedHashMap<>());

            List<Map<String, Object>> mappings = new ArrayList<>();
            if (template.get("fields") instanceof List<?> fields) {
                for (Object f : fields) {
                    if (f instanceof Map<?, ?> field) {
                        Map<String, Object> mapping = toMapping((Map<String, Object>) field);
                        if (mapping != null) {
                            mappings.add(mapping);
                        }
                    }
                }
            }
            categoryMappings.put(templateId, mappings);
        }

        Map<String, Object> common = templates.get(COMMON_TEMPLATE_ID);
        if (common != null && common.get("fields") instanceof List<?> fields) {
            for (Object f : fields) {
                if (f instanceof Map<?, ?> field) {
                    Map<String, Object> mapping = toMapping((Map<String, Object>) field);
                    if (mapping != null) {
                        sharedMappings.add(mapping);
                    }
                }
            }
        }

        config.put("categories", categories);
        config.put("sharedMappings", sharedMappings);
        config.put("categoryMappings", categoryMappings);
        config.put("defaultsByCategory", defaultsByCategory);
        return config;
    }

    private static Map<String, Object> toMapping(Map<String, Object> field) {
        Object businessKey = field.get("business_key");
        Object messagePath = field.get("message_path");
        if (businessKey == null || messagePath == null) {
            return null; // 缺 business_key 的字段不参与投影映射生成（schema 契约）
        }
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("business", businessKey);
        mapping.put("path", messagePath);
        if (field.get("draft_aliases") instanceof List<?> aliases && !aliases.isEmpty()) {
            mapping.put("aliases", aliases);
        }
        return mapping;
    }

    /** 规范化 JSON（键排序 + 映射数组按 business/path 排序），用于生成物比对。 */
    public static String canonicalJson(Map<String, Object> config, ObjectMapper mapper) {
        Map<String, Object> canonical = new TreeMap<>();
        config.forEach((k, v) -> canonical.put(k, canonicalize(v)));
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(canonical);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            boolean mappingList = !list.isEmpty() && list.get(0) instanceof Map<?, ?>;
            List<Object> items = list.stream().map(ProjectionConfigGenerator::canonicalize)
                    .collect(Collectors.toList());
            if (mappingList) {
                items.sort(Comparator.comparing(Object::toString));
            }
            return items;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    /** CLI：从 backend-app 运行 {@code java ... ProjectionConfigGenerator} 再生配置文件。 */
    public static void main(String[] args) throws IOException {
        Path templateDir = Paths.get("src", "main", "resources", "ontologies", "templates");
        Path target = Paths.get("src", "main", "resources", "ontology", "config_message_projection.json");
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Map<String, Object>> templates = new LinkedHashMap<>();
        try (var files = Files.list(templateDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(p -> {
                try {
                    Map<String, Object> t = mapper.readValue(Files.readAllBytes(p),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
                    templates.put(String.valueOf(t.get("template_id")), t);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        Map<String, Object> config = generate(templates);
        Files.writeString(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config) + "\n");
        System.out.println("已再生: " + target.toAbsolutePath());
    }
}
