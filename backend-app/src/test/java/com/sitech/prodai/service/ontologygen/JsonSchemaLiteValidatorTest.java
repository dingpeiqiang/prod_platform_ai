package com.sitech.prodai.service.ontologygen;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4 单源契约校验器单测：模板 Schema（template.schema.json）求值正确性。
 * 覆盖：全量真实模板通过、缺必填/枚举外值/pattern 不匹配/附加属性/联合类型。
 */
class JsonSchemaLiteValidatorTest {

    private static JsonSchemaLiteValidator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        mapper = new ObjectMapper();
        validator = new JsonSchemaLiteValidator(mapper, new DefaultResourceLoader());
    }

    @Test
    void classpathTemplatesShouldAllPassSchema() throws Exception {
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath*:ontologies/templates/*.json");
        assertTrue(resources.length > 0, "应加载到模板文件");
        for (var resource : resources) {
            Map<String, Object> template = mapper.readValue(resource.getInputStream(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            List<String> errors = validator.validateTemplate(template);
            assertTrue(errors.isEmpty(),
                    resource.getFilename() + " 应通过 schema 校验: " + errors);
        }
    }

    @Test
    void shouldRejectMissingRequiredProperty() {
        Map<String, Object> template = Map.of("template_id", "demoPrc");
        List<String> errors = validator.validateTemplate(template);
        assertTrue(errors.stream().anyMatch(e -> e.contains("缺少必填属性")),
                "缺 template_name/version/category_code/fields 应报错: " + errors);
    }

    @Test
    void shouldRejectEnumViolationAndPatternMismatch() {
        Map<String, Object> template = new java.util.LinkedHashMap<>(Map.of(
                "template_id", "DemoPrc",
                "template_name", "演示",
                "version", "1.0",
                "category_code", "demo",
                "fields", List.of()));
        List<String> errors = validator.validateTemplate(template);
        assertTrue(errors.stream().anyMatch(e -> e.contains("template_id") && e.contains("正则")),
                "template_id 命名不符应报错: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("version") && e.contains("正则")),
                "version 非三段式应报错: " + errors);
    }

    @Test
    void shouldRejectAdditionalProperties() {
        Map<String, Object> template = new java.util.LinkedHashMap<>(Map.of(
                "template_id", "demoPrc",
                "template_name", "演示",
                "version", "1.0.0",
                "category_code", "demo",
                "fields", List.of(),
                "unknownKey", "x"));
        List<String> errors = validator.validateTemplate(template);
        assertTrue(errors.stream().anyMatch(e -> e.contains("不允许的附加属性")),
                "附加属性应报错: " + errors);
    }

    @Test
    void shouldSupportUnionTypeAndRefResolution() {
        // extends 允许 string|null；enumConfig 经 $ref 求值
        Map<String, Object> field = Map.of(
                "field_code", "demoField",
                "label", "演示",
                "type", "checkbox",
                "required", true,
                "section", "base");
        Map<String, Object> template = Map.of(
                "template_id", "demoPrc",
                "template_name", "演示",
                "version", "1.0.0",
                "category_code", "demo",
                "fields", List.of(field));
        List<String> errors = validator.validateTemplate(template);
        assertTrue(errors.stream().anyMatch(e -> e.contains("type") && e.contains("枚举")),
                "field.type 非法枚举应报错: " + errors);
        assertFalse(errors.stream().anyMatch(e -> e.contains("无法解析")),
                "$ref 应可解析: " + errors);
        assertEquals(1, errors.stream().filter(e -> e.contains("枚举")).count());
    }
}
