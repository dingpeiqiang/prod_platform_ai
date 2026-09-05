package com.sitech.prodai.service.ontologygen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 轻量 JSON Schema 校验器（R4 单源契约执行器）。
 * <p><b>架构定位</b>：与 {@code ShaclValidationDelegate}（R7）同构——声明式契约文件
 * （template.schema.json draft-07）+ Java 求值器，不引入第三方 JSON Schema 依赖（构建零新增包）。
 * <p>支持的约束子集（覆盖模板 schema 全部用法）：
 * {@code type}（含联合类型）、{@code properties}、{@code required}、{@code additionalProperties:false}、
 * {@code items}、{@code $ref}（#/definitions/...）、{@code enum}、{@code pattern}、
 * {@code minLength/maxLength}、{@code minItems/maxItems}、{@code minimum/maximum}、{@code minProperties}。
 * <p>只读无状态；schema 文件为唯一契约源，校验规则随 schema 文件演进。
 */
@Component
public class JsonSchemaLiteValidator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaLiteValidator.class);

    /** 模板 Schema 契约文件（R4 单源）。 */
    public static final String TEMPLATE_SCHEMA_PATH = "classpath:ontologies/templates/schema/template.schema.json";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private volatile Map<String, Object> templateSchema;

    public JsonSchemaLiteValidator(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    /** 加载模板 Schema（进程内缓存）。 */
    public Map<String, Object> templateSchema() {
        if (templateSchema == null) {
            synchronized (this) {
                if (templateSchema == null) {
                    templateSchema = loadSchema(TEMPLATE_SCHEMA_PATH);
                }
            }
        }
        return templateSchema;
    }

    /**
     * 按模板 Schema 校验单个模板。
     *
     * @return 错误列表（空 = 通过）；每条形如 {@code fields[3].type: 值 "xxx" 不在枚举内}
     */
    public List<String> validateTemplate(Map<String, Object> template) {
        return validate(template, templateSchema(), "#", "");
    }

    // ------------------------------------------------------------------
    // 核心求值
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<String> validate(Object value, Map<String, Object> schema, String pointer, String path) {
        List<String> errors = new ArrayList<>();
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = resolveRef(String.valueOf(schema.get("$ref")));
            if (resolved == null) {
                errors.add(path + ": 无法解析 $ref " + schema.get("$ref"));
                return errors;
            }
            return validate(value, resolved, pointer, path);
        }

        Object type = schema.get("type");
        if (type != null && !typeMatches(value, type)) {
            errors.add(path + ": 类型应为 " + type + "，实际 " + typeName(value));
            return errors;
        }

        if (schema.get("enum") instanceof List<?> allowed && !allowed.contains(value)) {
            errors.add(path + ": 值 " + json(value) + " 不在枚举内 " + json(allowed));
        }

        if (schema.get("pattern") instanceof String pattern && value instanceof String text
                && !Pattern.compile(pattern).matcher(text).find()) {
            errors.add(path + ": 值 " + json(text) + " 不匹配正则 " + pattern);
        }
        if (value instanceof String text) {
            if (schema.get("minLength") instanceof Number min && text.length() < min.intValue()) {
                errors.add(path + ": 长度 " + text.length() + " < minLength " + min);
            }
            if (schema.get("maxLength") instanceof Number max && text.length() > max.intValue()) {
                errors.add(path + ": 长度 " + text.length() + " > maxLength " + max);
            }
        }
        if (value instanceof Number num) {
            if (schema.get("minimum") instanceof Number min && num.doubleValue() < min.doubleValue()) {
                errors.add(path + ": 值 " + num + " < minimum " + min);
            }
            if (schema.get("maximum") instanceof Number max && num.doubleValue() > max.doubleValue()) {
                errors.add(path + ": 值 " + num + " > maximum " + max);
            }
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> props = schema.get("properties") instanceof Map<?, ?> p
                    ? (Map<String, Object>) p : Map.of();
            if (schema.get("required") instanceof List<?> required) {
                for (Object r : required) {
                    if (!map.containsKey(String.valueOf(r))) {
                        errors.add(path + ": 缺少必填属性 " + r);
                    }
                }
            }
            if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
                for (Object keyObj : map.keySet()) {
                    String key = String.valueOf(keyObj);
                    if (!props.containsKey(key)) {
                        errors.add(path + "." + key + ": 不允许的附加属性");
                    }
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object childSchema = props.get(String.valueOf(entry.getKey()));
                if (childSchema instanceof Map<?, ?> cs) {
                    errors.addAll(validate(entry.getValue(), (Map<String, Object>) cs,
                            pointer, child(path, String.valueOf(entry.getKey()))));
                }
            }
            if (schema.get("minProperties") instanceof Number minProps && map.size() < minProps.intValue()) {
                errors.add(path + ": 属性数 " + map.size() + " < minProperties " + minProps);
            }
        }

        if (value instanceof List<?> list) {
            if (schema.get("items") instanceof Map<?, ?> itemSchema) {
                for (int i = 0; i < list.size(); i++) {
                    errors.addAll(validate(list.get(i), (Map<String, Object>) itemSchema,
                            pointer, path + "[" + i + "]"));
                }
            }
            if (schema.get("minItems") instanceof Number minItems && list.size() < minItems.intValue()) {
                errors.add(path + ": 元素数 " + list.size() + " < minItems " + minItems);
            }
            if (schema.get("maxItems") instanceof Number maxItems && list.size() > maxItems.intValue()) {
                errors.add(path + ": 元素数 " + list.size() + " > maxItems " + maxItems);
            }
        }
        return errors;
    }

    /** 解析本 schema 文件内引用（#/definitions/...）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveRef(String ref) {
        if (!ref.startsWith("#/")) {
            return null;
        }
        Object cur = templateSchema();
        for (String part : ref.substring(2).split("/")) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(part);
        }
        return cur instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private boolean typeMatches(Object value, Object type) {
        if (type instanceof List<?> types) {
            return types.stream().anyMatch(t -> typeMatches(value, t));
        }
        return switch (String.valueOf(type)) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "null" -> value == null;
            default -> false;
        };
    }

    private String typeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return value.getClass().getSimpleName();
    }

    private String child(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> loadSchema(String path) {
        try {
            var resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException("schema not found: " + path);
            }
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, new TypeReference<>() { });
            }
        } catch (Exception e) {
            throw new IllegalStateException("schema 加载失败: " + path + " - " + e.getMessage(), e);
        }
    }
}
