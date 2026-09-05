package com.sitech.prodai.service.ontologygen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4-Step4 单源一致性门禁：重新生成投影配置 / TTL 品类块，与仓库文件比对。
 * 手工修改生成物（config_message_projection.json、product-config.ttl 品类区）
 * 而不改模板 → 本测试失败，防止 xlsx→JSON→TTL/投影 同构漂移复发。
 */
class TemplateSingleSourceConsistencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Map<String, Map<String, Object>> templates;

    @BeforeAll
    static void loadTemplates() throws Exception {
        templates = new LinkedHashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:ontologies/templates/*.json");
        assertTrue(resources.length > 0, "应加载到模板文件");
        for (Resource resource : resources) {
            Map<String, Object> template = MAPPER.readValue(resource.getInputStream(),
                    new TypeReference<Map<String, Object>>() { });
            templates.put(String.valueOf(template.get("template_id")), template);
        }
    }

    @Test
    void templatesShouldPassSchemaGate() {
        JsonSchemaLiteValidator validator = new JsonSchemaLiteValidator(MAPPER,
                new org.springframework.core.io.DefaultResourceLoader());
        templates.values().forEach(template -> assertTrue(
                validator.validateTemplate(template).isEmpty(),
                "模板应过 schema 门禁: " + template.get("template_id")));
    }

    @Test
    void projectionConfigShouldMatchTemplateGeneration() throws Exception {
        Map<String, Object> generated = ProjectionConfigGenerator.generate(templates);
        Map<String, Object> committed = MAPPER.readValue(getClass().getResourceAsStream(
                "/ontology/config_message_projection.json"), new TypeReference<Map<String, Object>>() { });

        // 比对单源派生的四段（version/generated_from/description 为生成器元信息，不参与比对）
        for (String section : new String[]{"categories", "sharedMappings", "categoryMappings", "defaultsByCategory"}) {
            String gen = ProjectionConfigGenerator.canonicalJson(
                    Map.of(section, generated.get(section)), MAPPER);
            String real = ProjectionConfigGenerator.canonicalJson(
                    Map.of(section, committed.get(section)), MAPPER);
            assertEquals(gen, real, "投影配置段 " + section + " 与模板再生结果不一致："
                    + "请勿手改 config_message_projection.json，应修改模板后由 ProjectionConfigGenerator 再生");
        }
    }

    @Test
    void ttlCategoryBlockShouldMatchTemplateGeneration() throws Exception {
        String ttl = new String(getClass().getResourceAsStream("/ontology/product-config.ttl")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int begin = ttl.indexOf(TemplateTtlGenerator.BEGIN_MARKER);
        int end = ttl.indexOf(TemplateTtlGenerator.END_MARKER);
        assertTrue(begin >= 0 && end > begin, "product-config.ttl 缺少 GENERATED 标记区");
        String committedBlock = ttl.substring(begin, end + TemplateTtlGenerator.END_MARKER.length());
        String generatedBlock = TemplateTtlGenerator.generateCategoryBlock(templates);
        assertEquals(generatedBlock, committedBlock,
                "TTL 品类个体块与模板 category_meta 再生结果不一致："
                        + "请修改模板 category_meta 后由 TemplateTtlGenerator 再生，勿手改 TTL");
    }
}
