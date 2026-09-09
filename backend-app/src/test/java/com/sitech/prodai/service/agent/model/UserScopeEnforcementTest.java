package com.sitech.prodai.service.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.FactGraphSyncService;
import com.sitech.prodai.service.LlmIntentExtractor;
import com.sitech.prodai.service.Rdf4jOntologyStore;
import com.sitech.prodai.service.SparqlConfigDiscoverer;
import com.sitech.prodai.service.ops.ClasspathOpsProductDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行权限注入回归（方案 §4.4，阶段 A2）：真实 RDF4J 内存库 + mock_graph 灌图。
 * <p>守护三个安全性质：
 * <ul>
 *   <li>受限 scope 下 SPARQL 强制注入渠道过滤子句（可见范围外商品不可达）；</li>
 *   <li>unrestricted 行为零漂移（全量命中，与启用行权限前一致）；</li>
 *   <li>ThreadLocal 上下文 clear 后不残留（线程池复用不串号）。</li>
 * </ul>
 */
class UserScopeEnforcementTest {

    private Rdf4jOntologyStore store;
    private SparqlConfigDiscoverer discoverer;
    private LlmIntentExtractor extractor;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setDemoEnabled(true);
        properties.getOntology().setDataSource("classpath");
        properties.getOntology().setGraphPath("classpath:ontology/mock_graph.json");
        properties.getOntology().setBaseIri("http://example.org/");

        store = new Rdf4jOntologyStore(properties);
        store.initRepository();
        new FactGraphSyncService(store)
                .syncShelfOfferings(new ClasspathOpsProductDataSource(mapper, resourceLoader, properties).loadRawGraph());
        discoverer = new SparqlConfigDiscoverer(store, properties);
        extractor = new LlmIntentExtractor(java.util.Optional.empty(), mapper);
    }

    @AfterEach
    void tearDown() {
        UserScopeContext.clear();
    }

    @Test
    void unrestrictedScopeKeepsFullResults() {
        LlmIntentExtractor.DiscoverIntent intent = extractor.fallbackExtract("查一下大学生套餐配置");
        List<Map<String, Object>> hits = discoverer.discover(intent, UserScope.unrestricted());
        assertTrue(hits.size() >= 5, "unrestricted 应命中全量（≥5），实际 " + hits.size());
    }

    @Test
    void restrictedScopeFiltersByChannel() {
        LlmIntentExtractor.DiscoverIntent intent = extractor.fallbackExtract("查一下大学生套餐配置");
        List<Map<String, Object>> full = discoverer.discover(intent, UserScope.unrestricted());

        // 受限用户仅可见「电渠+厅店」渠道商品（mock 中 A7/内部验证 渠道商品应被过滤）
        UserScope restricted = UserScope.of("agent01", List.of("电渠+厅店"), UserScope.SENSITIVITY_INTERNAL);
        List<Map<String, Object>> hits = discoverer.discover(intent, restricted);

        assertTrue(hits.size() <= full.size(), "受限结果不应多于全量");
        assertNotEquals(0, hits.size(), "受限用户应仍有可见渠道内命中（电渠+厅店 商品存在）");
        assertTrue(hits.size() < full.size(), "受限结果应严格少于全量（A7/内部验证 商品被过滤）");
    }

    @Test
    void restrictedScopeWithImpossibleChannelYieldsEmpty() {
        LlmIntentExtractor.DiscoverIntent intent = extractor.fallbackExtract("查一下大学生套餐配置");
        UserScope restricted = UserScope.of("agent01", List.of("不存在的渠道XYZ"), UserScope.SENSITIVITY_INTERNAL);
        List<Map<String, Object>> hits = discoverer.discover(intent, restricted);
        assertTrue(hits.isEmpty(), "可见范围无任何商品时应返回空（权限过滤在数据层生效）");
    }

    @Test
    void threadLocalContextSurvivesSameThreadAndClearsProperly() {
        UserScope a = UserScope.of("userA", List.of("电渠+厅店"), 1);
        UserScope b = UserScope.unrestricted();

        UserScopeContext.bind(a);
        assertEquals("userA", UserScopeContext.current().getUserId());
        UserScopeContext.clear();
        // clear 后回落 unrestricted：线程池复用不会把 userA 权限泄漏给下一位
        assertTrue(UserScopeContext.current().isAllChannels(), "clear 后应回落 unrestricted");

        UserScopeContext.bind(b);
        assertTrue(UserScopeContext.current().isAllChannels());
        UserScopeContext.clear();
    }

    @Test
    void runWithWrapsAndAlwaysClears() {
        String result = UserScopeContext.runWith(
                UserScope.of("userB", List.of("A7"), 1), () -> "ok");
        assertEquals("ok", result);
        assertTrue(UserScopeContext.current().isAllChannels(), "runWith 结束（含异常路径）必须清理");
    }

    @Test
    void resolverMapsRolesToScopes() {
        UserScopeResolver resolver = new UserScopeResolver();

        UserScope admin = resolver.resolve("boss", "admin");
        assertTrue(admin.isAllChannels(), "admin 应全量");
        assertEquals(UserScope.SENSITIVITY_SENSITIVE, admin.getMaxSensitivity());

        UserScope user = resolver.resolve("zhangsan", "user");
        assertTrue(!user.isAllChannels(), "普通角色应受限");
        assertTrue(user.getVisibleChannels().contains("电渠+厅店"));
        assertEquals(UserScope.SENSITIVITY_INTERNAL, user.getMaxSensitivity());

        // 未登录（鉴权关闭）→ unrestricted，行为零漂移
        UserScope anonymous = resolver.resolve(null, null);
        assertTrue(anonymous.isAllChannels());

        // 客户经理：客户归属维度受限（名下客户），渠道/敏感度沿用政企条线口径
        UserScope am = resolver.resolve("am01", "account_manager");
        assertFalse(am.isAllChannels(), "客户经理渠道受限");
        assertTrue(am.canSeeCustomer("GE-CUST-001"), "名下客户可见");
        assertFalse(am.isAllCustomers(), "客户经理非全量客户归属");
        assertFalse(resolver.resolve("am99", "account_manager").canSeeCustomer("GE-CUST-001"),
                "字典外客户经理无名下客户（仍受限，不冒充全量）");
    }
}
