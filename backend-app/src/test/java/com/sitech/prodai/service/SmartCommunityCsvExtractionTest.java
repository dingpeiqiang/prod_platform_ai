package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.ontologygen.JsonSchemaLiteValidator;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.ops.OpsExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智慧社区 CSV 导入回归：GB18030 编码 CSV → 解析 → 逐套餐抽取（LLM 禁用走正则兜底）。
 */
class SmartCommunityCsvExtractionTest {

    private ConfigDocumentParser parser;
    private OpsExtractionService extractionService;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setDemoEnabled(true);
        properties.getOntology().setDataSource("classpath");
        properties.getOntology().setGraphPath("classpath:ontology/mock_graph.json");
        properties.getOntology().setRulesPath("classpath:ontology/ops_rules.json");
        properties.getOntology().setLlmExtractEnabled(false);

        OpsRulesService opsRules = new OpsRulesService(mapper, resourceLoader, properties);
        opsRules.load();
        ProductTemplateRegistry registry = new ProductTemplateRegistry(mapper,
                new JsonSchemaLiteValidator(mapper, resourceLoader));
        registry.init();
        ProductExtractionTemplateSupport templateSupport = new ProductExtractionTemplateSupport(registry);
        parser = new ConfigDocumentParser();
        extractionService = new OpsExtractionService(mapper, properties, opsRules, templateSupport, Optional.empty());
    }

    @Test
    void parsesSmartCommunityCsvAndExtractsThreePackages() throws Exception {
        Path csv = Path.of("../test_files/智慧社区融合方案.csv");
        if (!Files.exists(csv)) {
            csv = Path.of("test_files/智慧社区融合方案.csv");
        }
        byte[] bytes = Files.readAllBytes(csv);
        ConfigDocumentParser.ParseResult parsed = parser.parse(bytes, "智慧社区融合方案.csv");
        assertTrue(parsed.success(), "GB18030 CSV 解析应成功: " + parsed.message());

        // Document IR 路径：CSV → 表格块 → 表头映射直通（零 LLM）
        assertTrue(parsed.document() != null && parsed.document().tables().size() == 1,
                "CSV 应解析为 1 个表格块");
        OpsExtractionService.PackageExtractResult extracted = extractionService.extractPackagesFromDocument(
                parsed.text(), parsed.document().toMap(), List.of());
        assertEquals("table-direct", extracted.engine(), "LLM 禁用时表格直通引擎");
        assertEquals(3, extracted.packages().size(), "应抽出 A/B/C 三条套餐，实际: " + extracted.packages());

        Map<String, Object> pkgA = extracted.packages().get(0);
        assertEquals(198.0, ((Number) pkgA.get("monthlyFee")).doubleValue(), 0.01);
        assertEquals("40GB", pkgA.get("includeData"));
        assertEquals("500M", pkgA.get("includeBroadband"));

        Map<String, Object> pkgB = extracted.packages().get(1);
        assertEquals(0.0, ((Number) pkgB.get("monthlyFee")).doubleValue(), 0.01);
        assertEquals("5GB", pkgB.get("includeData"));
    }
}
