package com.sitech.prodai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.OntologyStore;
import com.sitech.prodai.service.Rdf4jOntologyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-1a 实例 CRUD + 裸 SPARQL REST 端点测试：真实 RDF4J 内存库直连 Controller 方法。
 * <p>守护点：创建幂等（重复 URI 拒绝）、更新 merge 语义、删除幂等、SPARQL 可查询刚写入的实例。
 */
class ProductOntologyInstanceEndpointTest {

    private ProductOntologyController controller;

    @BeforeEach
    void setUp() {
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setBaseIri("http://example.org/");
        Rdf4jOntologyStore store = new Rdf4jOntologyStore(properties);
        store.initRepository();
        OntologyStore ontologyStore = store;
        // 其余依赖在实例/SPARQL 端点路径上不被触达，传 null（构造器仅做字段赋值）
        controller = new ProductOntologyController(
                null, null, ontologyStore, null, null, null, null, null, null);
    }

    @Test
    void createListGetUpdateDeleteInstanceShouldRoundTrip() {
        // 创建
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("uri", "http://example.org/ConfigSchemescheme_ut_1");
        createReq.put("type", "ConfigScheme");
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("schemeId", "scheme_ut_1");
        facts.put("monthlyFee", 39);
        createReq.put("facts", facts);
        Map<String, Object> created = controller.createInstance(createReq);
        assertTrue(Boolean.TRUE.equals(created.get("success")), "创建应成功: " + created);

        // 重复创建被拒（幂等创建）
        Map<String, Object> duplicated = controller.createInstance(createReq);
        assertFalse(Boolean.TRUE.equals(duplicated.get("success")), "重复 URI 创建应失败");

        // 列表可见
        Map<String, Object> list = controller.listInstances("ConfigScheme");
        assertEquals(1, ((Number) list.get("total")).intValue(), "ConfigScheme 列表应恰有 1 条");

        // 详情可查
        Map<String, Object> got = controller.getInstance("http://example.org/ConfigSchemescheme_ut_1");
        assertTrue(Boolean.TRUE.equals(got.get("success")), "详情应可查");
        @SuppressWarnings("unchecked")
        Map<String, Object> instance = (Map<String, Object>) got.get("instance");
        assertEquals("scheme_ut_1", String.valueOf(instance.get("schemeId")));

        // 更新（merge 语义：新增字段 + 覆盖旧字段）
        Map<String, Object> updateReq = new LinkedHashMap<>();
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("monthlyFee", 59);
        patch.put("channelScope", "A7");
        updateReq.put("facts", patch);
        Map<String, Object> updated = controller.updateInstance("http://example.org/ConfigSchemescheme_ut_1", updateReq);
        assertTrue(Boolean.TRUE.equals(updated.get("success")), "更新应成功");
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) updated.get("instance");
        assertEquals(59, ((Number) merged.get("monthlyFee")).intValue(), "monthlyFee 应被覆盖");
        assertEquals("A7", String.valueOf(merged.get("channelScope")), "channelScope 应新增");
        assertEquals("scheme_ut_1", String.valueOf(merged.get("schemeId")), "未触及字段应保留");

        // 删除（幂等）
        Map<String, Object> deleted = controller.deleteInstance("http://example.org/ConfigSchemescheme_ut_1");
        assertTrue(Boolean.TRUE.equals(deleted.get("success")));
        Map<String, Object> deletedAgain = controller.deleteInstance("http://example.org/ConfigSchemescheme_ut_1");
        assertTrue(Boolean.TRUE.equals(deletedAgain.get("success")), "删除应幂等");
        Map<String, Object> afterDelete = controller.getInstance("http://example.org/ConfigSchemescheme_ut_1");
        assertFalse(Boolean.TRUE.equals(afterDelete.get("success")), "删除后详情应查不到");
    }

    @Test
    void sparqlShouldQueryJustCreatedInstance() {
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("uri", "http://example.org/ConfigSchemescheme_sparql_ut");
        createReq.put("type", "ConfigScheme");
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("schemeId", "scheme_sparql_ut");
        facts.put("monthlyFee", 19);
        createReq.put("facts", facts);
        controller.createInstance(createReq);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("query", "PREFIX ex: <http://example.org/>\n"
                + "SELECT ?s ?fee WHERE { ?s a ex:ConfigScheme ; ex:schemeId ?id ; ex:monthlyFee ?fee ."
                + " FILTER(?id = 'scheme_sparql_ut') }");
        Map<String, Object> body = controller.sparql(req);
        assertTrue(Boolean.TRUE.equals(body.get("success")), "SPARQL 查询应成功");
        assertEquals(1, ((Number) body.get("total")).intValue(),
                "SPARQL 应命中刚创建的实例（guard：写入未同步三元组则归零）");
    }

    @Test
    void createWithoutUriOrTypeShouldFail() {
        Map<String, Object> body = controller.createInstance(Map.of("type", "Thing"));
        assertFalse(Boolean.TRUE.equals(body.get("success")), "缺 uri 应失败");
        body = controller.createInstance(Map.of("uri", "http://example.org/x"));
        assertFalse(Boolean.TRUE.equals(body.get("success")), "缺 type 应失败");
    }

    @Test
    void updateAndGetMissingInstanceShouldFail() {
        Map<String, Object> updated = controller.updateInstance(
                "http://example.org/NotExists", Map.of("facts", Map.of("a", 1)));
        assertFalse(Boolean.TRUE.equals(updated.get("success")), "更新不存在实例应失败");

        Map<String, Object> got = controller.getInstance("http://example.org/NotExists");
        assertFalse(Boolean.TRUE.equals(got.get("success")), "查询不存在实例应失败");
        assertTrue(got.containsKey("message"), "应返回提示消息");
    }

    /** Jackson 序列化冒烟：响应对齐 JSON snake_case 传输口径（facts 键透传）。 */
    @Test
    void responseShouldBeJacksonSerializable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("uri", "http://example.org/ConfigSchemescheme_json_ut");
        createReq.put("type", "ConfigScheme");
        createReq.put("facts", Map.of("schemeId", "scheme_json_ut"));
        String json = mapper.writeValueAsString(controller.createInstance(createReq));
        assertTrue(json.contains("\"success\":true"), "响应应可序列化且 success=true");
    }
}
