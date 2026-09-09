package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.ops.ClasspathOpsProductDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智查本体检索链路端到端回归：真实 RDF4J 内存库 + mock_graph 灌图 + SPARQL 发现。
 * <p>守护两个历史缺陷：SPARQL 缩写前缀未声明（解析报错→0 命中）、
 * '=' 对 xsd:string 字面量比较失效（state 过滤恒假→0 命中）。二者曾被词典兜底掩盖。
 */
class SparqlConfigDiscovererTest {

    @Test
    void campusNear30QueryShouldHitStudentOfferings() {
        ObjectMapper mapper = new ObjectMapper();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setDemoEnabled(true);
        properties.getOntology().setDataSource("classpath");
        properties.getOntology().setGraphPath("classpath:ontology/mock_graph.json");
        properties.getOntology().setBaseIri("http://example.org/");

        Rdf4jOntologyStore store = new Rdf4jOntologyStore(properties);
        store.initRepository();
        FactGraphSyncService sync = new FactGraphSyncService(store);
        SparqlConfigDiscoverer discoverer = new SparqlConfigDiscoverer(store, properties);
        LlmIntentExtractor extractor = new LlmIntentExtractor(java.util.Optional.empty(), mapper);

        ClasspathOpsProductDataSource source =
                new ClasspathOpsProductDataSource(mapper, resourceLoader, properties);
        int synced = sync.syncShelfOfferings(source.loadRawGraph());
        assertEquals(100, synced, "灌图条数应与 mock_graph 在架商品一致");

        LlmIntentExtractor.DiscoverIntent intent =
                extractor.fallbackExtract("查一下近30天大学生套餐配置");
        List<Map<String, Object>> hits = discoverer.discover(intent);

        assertTrue(hits.size() >= 5, "近30天大学生套餐应至少命中 5 条，实际 " + hits.size()
                + "（guard：SPARQL 前缀未声明或 state 等值失效时归零）");
        for (Map<String, Object> h : hits) {
            assertEquals("上架", String.valueOf(h.get("state")), "命中条目应为上架态");
        }
    }

    @Test
    void deviceIntentShouldHitTerminalOfferings() {
        SparqlFixture fixture = newFixture();

        LlmIntentExtractor.DiscoverIntent intent =
                fixture.extractor.fallbackExtract("有哪些终端手机可以买");
        List<Map<String, Object>> hits = fixture.discoverer.discover(intent);

        assertTrue(hits.size() >= 3, "终端意图应至少命中 3 条终端商品，实际 " + hits.size());
        for (Map<String, Object> h : hits) {
            assertEquals("终端销售", String.valueOf(h.get("category_name")), "device 意图命中条目应为终端域");
        }
    }

    @Test
    void simCardIntentShouldHitSimOfferings() {
        SparqlFixture fixture = newFixture();

        LlmIntentExtractor.DiscoverIntent intent =
                fixture.extractor.fallbackExtract("想办一张号卡副卡");
        List<Map<String, Object>> hits = fixture.discoverer.discover(intent);

        assertTrue(hits.size() >= 3, "号卡意图应至少命中 3 条号卡商品，实际 " + hits.size());
        for (Map<String, Object> h : hits) {
            assertEquals("号卡资费", String.valueOf(h.get("category_name")), "sim_card 意图命中条目应为号卡域");
        }
    }

    /** 隔离构建：真实 RDF4J 内存库 + mock_graph 全量灌图 + SPARQL 发现器。 */
    private SparqlFixture newFixture() {
        ObjectMapper mapper = new ObjectMapper();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setDemoEnabled(true);
        properties.getOntology().setDataSource("classpath");
        properties.getOntology().setGraphPath("classpath:ontology/mock_graph.json");
        properties.getOntology().setBaseIri("http://example.org/");

        Rdf4jOntologyStore store = new Rdf4jOntologyStore(properties);
        store.initRepository();
        FactGraphSyncService sync = new FactGraphSyncService(store);
        SparqlConfigDiscoverer discoverer = new SparqlConfigDiscoverer(store, properties);
        LlmIntentExtractor extractor = new LlmIntentExtractor(java.util.Optional.empty(), mapper);

        ClasspathOpsProductDataSource source =
                new ClasspathOpsProductDataSource(mapper, resourceLoader, properties);
        int synced = sync.syncShelfOfferings(source.loadRawGraph());
        assertEquals(100, synced, "灌图条数应与 mock_graph 在架商品一致");
        return new SparqlFixture(discoverer, extractor);
    }

    /** 检索依赖项聚合（Java 无 record 时用轻量载体）。 */
    private record SparqlFixture(SparqlConfigDiscoverer discoverer, LlmIntentExtractor extractor) {
    }
}
