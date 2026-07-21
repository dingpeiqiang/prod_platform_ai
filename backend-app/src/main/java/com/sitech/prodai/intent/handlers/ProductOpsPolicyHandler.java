package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentContextBuilder;
import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.service.OntologyService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

@Component
public class ProductOpsPolicyHandler implements BaseIntentHandler {

    private final OntologyService ontologyService;

    public ProductOpsPolicyHandler(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getIntentType() {
        return "product_ops_policy";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        Map<String, Object> facts = BaseIntentContextBuilder.extractPolicyFacts(ctx);
        String policySetId = BaseIntentContextBuilder.resolvePolicySetId(ctx);
        String expectationType = BaseIntentContextBuilder.resolveExpectationType(ctx);
        Map<String, Object> result = ontologyService.evaluate(facts, policySetId, expectationType, ctx.resolveSessionId(), ctx.resolveUserId());
        Map<String, Object> decision = (Map<String, Object>) result.get("decision");
        Map<String, Object> statsPayload = Map.of(
                "policySetId", policySetId,
                "expectationType", expectationType,
                "verdict", decision.getOrDefault("verdict", "review"),
                "reason", decision.getOrDefault("reason", ""),
                "triggered_rules", decision.getOrDefault("triggered_rules", java.util.List.of())
        );
        return Flux.just(
                SseUtils.thinking("正在调用规则引擎进行政策评估..."),
                SseUtils.intentEvent(getIntentType(), expectationType, Map.of("facts", facts, "decision", decision), false),
                SseUtils.textStart(),
                SseUtils.text("policySetId=" + policySetId),
                SseUtils.text("评估结果：" + decision.get("verdict")),
                SseUtils.text("原因：" + decision.get("reason")),
                SseUtils.textEnd(),
                SseUtils.stats(ctx.getStreamStats()),
                SseUtils.doneEvent(getIntentType(), false, Map.of("intentType", getIntentType(), "action", expectationType, "stats", statsPayload))
        );
    }
}