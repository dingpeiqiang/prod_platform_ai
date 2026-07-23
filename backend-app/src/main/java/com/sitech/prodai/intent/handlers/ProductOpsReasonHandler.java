package com.sitech.prodai.intent.handlers;

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
public class ProductOpsReasonHandler implements BaseIntentHandler {

    private final OntologyService ontologyService;

    public ProductOpsReasonHandler(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getIntentType() {
        return "product_ops_reason";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        String target = ctx.getExtractedFields().containsKey("target")
                ? String.valueOf(ctx.getExtractedFields().get("target"))
                : ctx.getLastUserMessage();
        String traceId = ctx.resolveSessionId();
        String tenantId = ctx.resolveUserId();

        Map<String, Object> nlResult = ontologyService.nlQuery(target);
        String answer = String.valueOf(nlResult.getOrDefault("answer", ""));

        Map<String, Object> explainResult = ontologyService.explain(traceId, "business", tenantId);
        String explanation = String.valueOf(explainResult.getOrDefault("natural_language", answer));
        List<String> referencedRules = List.of();
        if (explainResult.get("referenced_rules") instanceof List<?> list) {
            referencedRules = list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }

        List<Map<String, Object>> results = List.of();
        if (nlResult.get("results") instanceof List<?> list) {
            results = list.stream().filter(Map.class::isInstance).map(e -> (Map<String, Object>) e).toList();
        }

        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("target", target);
        intentData.put("explanation", explanation);
        intentData.put("referencedRules", referencedRules);
        intentData.put("sparql", nlResult.get("sparql"));
        intentData.put("results", results);
        intentData.put("traceId", traceId);

        String answerText = formatReasonAnswer(explanation, referencedRules, results);

        Map<String, Object> statsPayload = Map.of(
                "traceId", traceId,
                "referenced_rules", referencedRules,
                "target", target,
                "evidenceCount", results.size()
        );

        return Flux.just(
                SseUtils.thinkingRich(
                        "正在追溯产商品异动根因并构建证据链...",
                        Map.of(
                                "step", 5,
                                "totalSteps", 6,
                                "traceId", traceId.length() > 12 ? traceId.substring(0, 12) + "..." : traceId,
                                "target", target.length() > 50 ? target.substring(0, 50) + "..." : target,
                                "ruleCount", referencedRules.size(),
                                "evidenceCount", results.size()
                        ),
                        -1,
                        referencedRules.isEmpty() ? null : "引用规则: " + String.join(", ", referencedRules)
                ),
                SseUtils.intentEvent(getIntentType(), "root_cause", intentData, false),
                SseUtils.textStart(),
                SseUtils.text(answerText),
                SseUtils.textEnd(),
                SseUtils.stats(ctx.getStreamStats()),
                SseUtils.doneEvent(getIntentType(), false, Map.of(
                        "intentType", getIntentType(),
                        "action", "root_cause",
                        "stats", statsPayload,
                        "explanation", explanation,
                        "referencedRules", referencedRules,
                        "sparql", nlResult.get("sparql"),
                        "results", results,
                        "traceId", traceId,
                        "target", target,
                        "evidenceCount", results.size()
                ))
        );
    }

    private String formatReasonAnswer(String explanation, List<String> rules, List<Map<String, Object>> evidence) {
        StringBuilder sb = new StringBuilder();
        if (explanation != null && !explanation.isBlank()) {
            sb.append(explanation);
        } else {
            sb.append("根因分析完成");
        }
        if (!rules.isEmpty()) {
            sb.append("\n\n引用规则：").append(String.join(", ", rules));
        }
        if (!evidence.isEmpty()) {
            sb.append("\n关联证据：").append(evidence.size()).append(" 条");
        }
        return sb.toString();
    }
}