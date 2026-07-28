package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.service.OpsRulesService;
import com.sitech.prodai.service.ProductOntologyService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 校验异动归因 Handler 回填的场景化 thinking 步骤含 id + 非空 result。
 */
class ProductOpsReasonHandlerThinkingTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildAfterEventsEmitsRcaThinkingStepsWithResults() throws Exception {
        ProductOntologyService ontology = mock(ProductOntologyService.class);
        OpsRulesService opsRules = mock(OpsRulesService.class);
        org.mockito.Mockito.when(opsRules.formatRuleName(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.when(opsRules.formatRuleLabel(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        ProductOpsReasonHandler handler = new ProductOpsReasonHandler(ontology, opsRules);

        Map<String, Object> root = sampleRoot();
        com.sitech.prodai.intent.IntentContext ctx = new com.sitech.prodai.intent.IntentContext();
        ctx.setStreamStats(new com.sitech.prodai.intent.StreamStats());

        Method m = ProductOpsReasonHandler.class.getDeclaredMethod(
                "buildAfterEvents",
                com.sitech.prodai.intent.IntentContext.class,
                String.class,
                String.class,
                Map.class,
                long.class
        );
        m.setAccessible(true);
        List<Map<String, Object>> events = (List<Map<String, Object>>) m.invoke(
                handler, ctx, "家庭融合下滑原因", "sess_test", root, 42L);

        List<Map<String, Object>> thinking = events.stream()
                .filter(e -> "thinking".equals(e.get("type")))
                .toList();

        assertEquals(5, thinking.size());
        assertEquals(List.of("locate", "confirm", "drill", "reason", "conclude"),
                thinking.stream().map(e -> String.valueOf(e.get("id"))).toList());

        for (Map<String, Object> step : thinking) {
            assertNotNull(step.get("title"), "title required for " + step.get("id"));
            assertNotNull(step.get("result"), "result required for " + step.get("id"));
            assertFalse(String.valueOf(step.get("result")).isBlank(), "result blank for " + step.get("id"));
        }

        Map<String, Object> reason = thinking.stream()
                .filter(e -> "reason".equals(e.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("ontology", reason.get("stepType"));
    }

    private Map<String, Object> sampleRoot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", true);
        root.put("offeringId", "OF-HF-128");
        root.put("offeringName", "家庭融合畅享128套餐");
        root.put("reasonEngine", "openllet");
        root.put("appliedRules", List.of("R-A01", "R-A02"));
        root.put("swrlFiredRules", List.of("R-A01", "R-A02"));
        root.put("market", Map.of("name", "本地家庭融合", "scopeId", "MKT-HF"));
        root.put("actionList", List.of("营业厅驻点促销", "核查竞品资费"));

        Map<String, Object> anomaly = new LinkedHashMap<>();
        anomaly.put("metricCode", "累计收入");
        anomaly.put("message", "累计收入环比 -18%");
        anomaly.put("ruleId", "R-A01");
        root.put("anomalies", List.of(anomaly));

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("rank", 1);
        path.put("name", "营业厅订购下滑");
        path.put("weight", 0.42);
        path.put("rootCauseType", "Channel");
        path.put("ruleId", "R-A02");
        path.put("evidence", List.of("订购量变化 -35%", "渠道贡献占比 42%"));
        root.put("paths", List.of(path));
        return root;
    }
}
