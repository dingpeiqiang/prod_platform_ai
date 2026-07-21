package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.service.OntologyService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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

        Map<String, Object> donePayload = Map.of(
                "intentType", getIntentType(),
                "action", "root_cause",
                "stats", Map.of(
                        "traceId", traceId,
                        "referenced_rules", referencedRules,
                        "target", target
                )
        );
        return Flux.just(
                SseUtils.thinking("... 正在追溯产商品异动根因并构建证据链..."),
                SseUtils.intentEvent(getIntentType(), "root_cause", Map.of("target", target, "explanation", explanation, "referenced_rules", referencedRules, "sparql", nlResult.get("sparql"), "results", nlResult.get("results")), false),
                SseUtils.textStart(),
                SseUtils.text(explanation),
                SseUtils.textEnd(),
                SseUtils.stats(ctx.getStreamStats()),
                SseUtils.doneEvent(getIntentType(), false, donePayload)
        );
    }
}
