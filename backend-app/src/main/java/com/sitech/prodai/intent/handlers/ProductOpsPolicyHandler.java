package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentContextBuilder;
import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.service.OntologyService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
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
    @SuppressWarnings("unchecked")
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        Map<String, Object> facts = BaseIntentContextBuilder.extractPolicyFacts(ctx);
        String policySetId = BaseIntentContextBuilder.resolvePolicySetId(ctx);
        String expectationType = BaseIntentContextBuilder.resolveExpectationType(ctx);
        Map<String, Object> result = ontologyService.evaluate(facts, policySetId, expectationType,
                ctx.resolveSessionId(), ctx.resolveUserId());
        Map<String, Object> decision = (Map<String, Object>) result.get("decision");
        String verdict = String.valueOf(decision.getOrDefault("verdict", "review"));
        String reason = String.valueOf(decision.getOrDefault("reason", ""));
        List<String> triggeredRules = List.of();
        if (decision.get("triggered_rules") instanceof List<?> list) {
            triggeredRules = list.stream().map(String.class::cast).toList();
        }

        Map<String, Object> factsSummary = new LinkedHashMap<>();
        factsSummary.put("productType", facts.getOrDefault("productType", "-"));
        factsSummary.put("targetMarketSize", facts.getOrDefault("targetMarketSize", "-"));
        factsSummary.put("isZeroFee", facts.getOrDefault("isZeroFee", false));
        factsSummary.put("onlineMonths", facts.getOrDefault("onlineMonths", "-"));
        factsSummary.put("newUserMonth", facts.getOrDefault("newUserMonth", "-"));
        factsSummary.put("annualSpend", facts.getOrDefault("annualSpend", "-"));
        factsSummary.put("vipLevel", facts.getOrDefault("vipLevel", "-"));

        String answerText = formatPolicyAnswer(verdict, reason, policySetId, triggeredRules);

        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("facts", facts);
        intentData.put("factsSummary", factsSummary);
        intentData.put("decision", decision);
        intentData.put("verdict", verdict);
        intentData.put("reason", reason);
        intentData.put("triggeredRules", triggeredRules);
        intentData.put("policySetId", policySetId);
        intentData.put("expectationType", expectationType);

        Map<String, Object> statsPayload = Map.of(
                "policySetId", policySetId,
                "expectationType", expectationType,
                "verdict", verdict,
                "reason", reason,
                "triggered_rules", triggeredRules
        );

        return Flux.just(
                SseUtils.thinking("正在调用规则引擎进行政策评估..."),
                SseUtils.intentEvent(getIntentType(), expectationType, intentData, false),
                SseUtils.textStart(),
                SseUtils.text(answerText),
                SseUtils.textEnd(),
                SseUtils.stats(ctx.getStreamStats()),
                SseUtils.doneEvent(getIntentType(), false, Map.of(
                        "intentType", getIntentType(),
                        "action", expectationType,
                        "stats", statsPayload,
                        "verdict", verdict,
                        "reason", reason,
                        "triggeredRules", triggeredRules,
                        "factsSummary", factsSummary,
                        "policySetId", policySetId,
                        "expectationType", expectationType
                ))
        );
    }

    private String formatPolicyAnswer(String verdict, String reason, String policySetId, List<String> rules) {
        String verdictLabel = switch (verdict) {
            case "allow" -> "通过";
            case "deny" -> "拒绝";
            default -> "待审";
        };
        StringBuilder sb = new StringBuilder();
        sb.append("策略集：").append(policySetId);
        sb.append("\n评估结论：").append(verdictLabel);
        if (reason != null && !reason.isBlank()) {
            sb.append("\n原因：").append(reason);
        }
        if (!rules.isEmpty()) {
            sb.append("\n命中规则：").append(String.join(", ", rules));
        }
        return sb.toString();
    }
}