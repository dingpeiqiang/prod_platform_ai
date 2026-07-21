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
public class ProductOpsQueryHandler implements BaseIntentHandler {

    private final OntologyService ontologyService;

    public ProductOpsQueryHandler(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getIntentType() {
        return "product_ops_query";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        String question = ctx.getExtractedFields().containsKey("question")
                ? String.valueOf(ctx.getExtractedFields().get("question"))
                : ctx.getLastUserMessage();
        Map<String, Object> result = ontologyService.nlQuery(question);
        List<Map<String, Object>> results = List.of();
        if (result.get("results") instanceof List<?> list) {
            results = list.stream().filter(Map.class::isInstance).map(e -> (Map<String, Object>) e).toList();
        }
        Map<String, Object> donePayload = Map.of(
                "intentType", getIntentType(),
                "action", "query",
                "stats", Map.of("count", results.size(), "question", question)
        );
        return Flux.just(
                SseUtils.thinking("正在基于本体检索产商品事实与指标..."),
                SseUtils.intentEvent(getIntentType(), "query", Map.of("question", question, "sparql", result.get("sparql"), "results", results), false),
                SseUtils.textStart(),
                SseUtils.text(String.valueOf(result.get("answer"))),
                SseUtils.textEnd(),
                SseUtils.stats(ctx.getStreamStats()),
                SseUtils.doneEvent(getIntentType(), false, donePayload)
        );
    }
}