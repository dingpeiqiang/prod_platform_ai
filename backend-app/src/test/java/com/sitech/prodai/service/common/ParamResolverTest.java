package com.sitech.prodai.service.common;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.SessionContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 参数来源解析公共组件测试（P3-2 验收）：四来源协议 + 依赖失败信号。
 */
class ParamResolverTest {

    private SessionContext contextWithEvidence(String key, String value) {
        SessionContext ctx = new SessionContext();
        ctx.getCachedEvidence().put(key, value);
        return ctx;
    }

    @Test
    void directSourceReadsPlanParams() {
        Map<String, Object> planParams = Map.of("metric", "收入");
        assertEquals("收入", ParamResolver.resolve("direct:metric", planParams, Map.of(), null));
        assertNull(ParamResolver.resolve("direct:missing", planParams, Map.of(), null));
    }

    @Test
    void resultSourceReadsPriorStepData() {
        Map<String, ExecutionResult> stepResults = new LinkedHashMap<>();
        stepResults.put("sparql_query", ExecutionResult.ok("sparql_query", Map.of("rows", 5)));
        assertEquals(5, ParamResolver.resolve("result:sparql_query.rows", Map.of(), stepResults, null));
        // 无 key → 返回整个 data
        Map<String, Object> data = (Map<String, Object>) ParamResolver.resolve(
                "result:sparql_query", Map.of(), stepResults, null);
        assertEquals(5, data.get("rows"));
    }

    @Test
    void resultSourceThrowsWhenDependencyMissing() {
        assertThrows(DependencyFailedException.class,
                () -> ParamResolver.resolve("result:ghost.key", Map.of(), Map.of(), null));
    }

    @Test
    void resultSourceThrowsWhenDependencyFailed() {
        Map<String, ExecutionResult> stepResults = Map.of(
                "bad_tool", ExecutionResult.fail("bad_tool", "本体库不可用"));
        assertThrows(DependencyFailedException.class,
                () -> ParamResolver.resolve("result:bad_tool.key", Map.of(), stepResults, null));
    }

    @Test
    void evidenceSourceReadsContext() {
        SessionContext ctx = contextWithEvidence("entity", "OFF_001");
        assertEquals("OFF_001", ParamResolver.resolve("evidence:entity", Map.of(), Map.of(), ctx));
        assertNull(ParamResolver.resolve("evidence:missing", Map.of(), Map.of(), ctx));
        assertNull(ParamResolver.resolve("evidence:any", Map.of(), Map.of(), null));
    }

    @Test
    void defaultSourceReturnsLiteral() {
        assertEquals("HIGH", ParamResolver.resolve("default:HIGH", Map.of(), Map.of(), null));
    }

    @Test
    void blankAndUnknownSourcesReturnNull() {
        assertNull(ParamResolver.resolve(null, Map.of(), Map.of(), null));
        assertNull(ParamResolver.resolve("  ", Map.of(), Map.of(), null));
        assertNull(ParamResolver.resolve("unknown:foo", Map.of(), Map.of(), null));
    }
}
