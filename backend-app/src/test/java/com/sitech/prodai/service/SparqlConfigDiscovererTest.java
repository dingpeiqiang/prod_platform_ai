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
        assertEquals(92, synced, "灌图条数应与 mock_graph 在架商品一致");

        LlmIntentExtractor.DiscoverIntent intent =
                extractor.fallbackExtract("查一下近30天大学生套餐配置");
        List<Map<String, Object>> hits = discoverer.discover(intent);

        assertTrue(hits.size() >= 5, "近30天大学生套餐应至少命中 5 条，实际 " + hits.size()
                + "（guard：SPARQL 前缀未声明或 state 等值失效时归零）");
        for (Map<String, Object> h : hits) {
            assertEquals("上架", String.valueOf(h.get("state")), "命中条目应为上架态");
        }
    }
}
