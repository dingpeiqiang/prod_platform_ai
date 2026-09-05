package com.sitech.prodai.service.ontologygen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板 TTL 生成器（R4-Step3）：从模板 {@code category_meta}（品类元数据 SSOT）
 * 生成 product-config.ttl 的品类个体块。
 * <p>生成范围：{@code # BEGIN GENERATED: category-individuals} 标记区
 * （品类个体 owl:NamedIndividual/ProductCategory 断言）。
 * TTL 其余部分（TBox 类/属性骨架、示例 ABox）为手维区：属性 IRI 存在业务重命名与
 * 多字段共用（parity 分析结论），不纳入生成范围。
 * <p>CI 门禁：TemplateSingleSourceConsistencyTest 重新生成并与仓库文件比对。
 */
public final class TemplateTtlGenerator {

    public static final String BEGIN_MARKER = "# BEGIN GENERATED: category-individuals (FROM templates/*.json DO NOT EDIT)";
    public static final String END_MARKER = "# END GENERATED: category-individuals";

    private TemplateTtlGenerator() {
    }

    /**
     * 生成品类个体块（含标记行）。
     *
     * @param templates template_id → 原始模板 JSON（保持文件序）
     */
    public static String generateCategoryBlock(Map<String, Map<String, Object>> templates) {
        StringBuilder sb = new StringBuilder();
        sb.append(BEGIN_MARKER).append('\n');
        sb.append("# 由 TemplateTtlGenerator 生成；品类元数据单源 = category_meta。\n");
        sb.append("# Individuals - categories\n");
        for (Map.Entry<String, Map<String, Object>> entry : templates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            Map<String, Object> template = entry.getValue();
            if (!(template.get("category_meta") instanceof Map<?, ?> meta)) {
                continue;
            }
            String templateId = entry.getKey();
            String name = String.valueOf(template.get("template_name"));
            sb.append(':').append(templateId).append(" rdf:type owl:NamedIndividual , :ProductCategory ;\n");
            sb.append("  :categoryCode \"").append(templateId).append("\" ; :categoryName \"").append(name).append("\" ;\n");
            sb.append("  :productLine \"").append(meta.get("product_line")).append("\" ;\n");
            sb.append("  :isMainOffer \"").append(Boolean.TRUE.equals(meta.get("is_main_offer")))
                    .append("\"^^xsd:boolean ;\n");
            sb.append("  :messageRootKey \"").append(templateId).append("\" ;\n");
            sb.append("  rdfs:comment \"必备要素:").append(joinElements(meta)).append("\"@zh ;\n");
            sb.append("  rdfs:label \"").append(name).append("\"@zh .\n\n");
        }
        sb.append(END_MARKER);
        return sb.toString();
    }

    private static String joinElements(Map<?, ?> meta) {
        Object elements = meta.get("required_elements");
        if (elements instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return "";
    }

    /**
     * 将生成块写回 TTL 全文（替换标记区内容；无标记区则追加在品类声明位置之后由人工处理）。
     */
    public static String applyToTtl(String ttlText, String generatedBlock) {
        int begin = ttlText.indexOf(BEGIN_MARKER);
        int end = ttlText.indexOf(END_MARKER);
        if (begin >= 0 && end > begin) {
            return ttlText.substring(0, begin) + generatedBlock + ttlText.substring(end + END_MARKER.length());
        }
        throw new IllegalArgumentException("TTL 缺少 GENERATED 标记区（" + BEGIN_MARKER + "），"
                + "请先由 scripts/inject_projection_source.py 初始化标记区");
    }

    /** CLI：从 backend-app 运行，再生 TTL 品类块并回写。 */
    public static void main(String[] args) throws IOException {
        Path templateDir = Paths.get("src", "main", "resources", "ontologies", "templates");
        Path ttlPath = Paths.get("src", "main", "resources", "ontology", "product-config.ttl");
        Map<String, Map<String, Object>> templates = new LinkedHashMap<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
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
        String block = generateCategoryBlock(templates);
        String updated = applyToTtl(Files.readString(ttlPath), block);
        Files.writeString(ttlPath, updated);
        System.out.println("已再生 TTL 品类块: " + ttlPath.toAbsolutePath());
    }
}
