package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.service.OntologyService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @SuppressWarnings("unchecked")
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        String question = ctx.getExtractedFields().containsKey("question")
                ? String.valueOf(ctx.getExtractedFields().get("question"))
                : ctx.getLastUserMessage();

        // 使用 LLM 增强的实体发现 + 本体检索
        Map<String, Object> result = ontologyService.nlDiscoverAndRetrieve(question, 20);
        List<Map<String, Object>> results = List.of();
        if (result.get("raw_results") instanceof List<?> list) {
            results = list.stream().filter(Map.class::isInstance).map(e -> (Map<String, Object>) e).toList();
        }
        String answerText = formatAnswer(String.valueOf(result.getOrDefault("nl_answer", "")), results);
        String discoveryMethod = String.valueOf(result.getOrDefault("discovery_method", "unknown"));

        List<String> columns = new ArrayList<>();
        if (!results.isEmpty()) {
            for (String key : results.get(0).keySet()) {
                if (!key.startsWith("_")) columns.add(key);
            }
        }

        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("question", question);
        intentData.put("sparql", result.get("sparql"));
        intentData.put("results", results);
        intentData.put("count", results.size());
        intentData.put("columns", columns);
        intentData.put("discoveryMethod", discoveryMethod);
        intentData.put("graphData", buildGraphData(results));

        Map<String, Object> donePayload = new LinkedHashMap<>();
        donePayload.put("intentType", getIntentType());
        donePayload.put("action", "query");
        donePayload.put("stats", Map.of("count", results.size(), "question", question, "discoveryMethod", discoveryMethod));
        donePayload.put("results", results);
        donePayload.put("question", question);
        donePayload.put("sparql", result.get("sparql"));
        donePayload.put("columns", columns);
        donePayload.put("discoveryMethod", discoveryMethod);
        donePayload.put("graphData", buildGraphData(results));

        return Flux.just(
                SseUtils.thinkingRich(
                        "正在通过 " + ("llm".equals(discoveryMethod) ? "LLM 实体发现" : "关键词匹配") + " 检索本体事实...",
                        Map.of(
                                "step", 5,
                                "totalSteps", 6,
                                "discoveryMethod", discoveryMethod,
                                "resultCount", results.size(),
                                "question", question.length() > 60 ? question.substring(0, 60) + "..." : question
                        ),
                        -1,
                        result.get("sparql") != null ? "SPARQL: " + truncateStr(String.valueOf(result.get("sparql")), 150) : null
                ),
                SseUtils.intentEvent(getIntentType(), "query", intentData, false),
                SseUtils.textStart(),
                SseUtils.text(answerText),
                SseUtils.textEnd(),
                SseUtils.stats(ctx.getStreamStats()),
                SseUtils.doneEvent(getIntentType(), false, donePayload)
        );
    }

    private String formatAnswer(String summary, List<Map<String, Object>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append(summary == null || summary.isBlank() ? "查询完成" : summary);
        sb.append("\n共 ").append(results.size()).append(" 条结果。");
        if (results.isEmpty()) {
            sb.append("\n（本体库暂无匹配数据，可先导入 TTL 或换个问法）");
            return sb.toString();
        }
        int limit = Math.min(results.size(), 10);
        for (int i = 0; i < limit; i++) {
            sb.append("\n").append(i + 1).append(". ").append(summarizeRow(results.get(i)));
        }
        if (results.size() > limit) {
            sb.append("\n… 其余 ").append(results.size() - limit).append(" 条见下方结果面板");
        }
        return sb.toString();
    }

    private String summarizeRow(Map<String, Object> row) {
        List<String> parts = new ArrayList<>();
        for (String key : List.of("name", "productName", "status", "growth", "revenueGrowth",
                "users", "newUserMonth", "isZeroFee", "product", "entity", "_bucket")) {
            Object val = row.get(key);
            if (val != null && !String.valueOf(val).isBlank()) {
                parts.add(key + "=" + val);
            }
        }
        if (parts.isEmpty()) {
            return row.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .limit(4)
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
        }
        return String.join(", ", parts);
    }

    /**
     * 将查询结果转换为图谱数据（用于 SparqlResultGraph 组件）
     */
    private Map<String, Object> buildGraphData(List<Map<String, Object>> results) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (Map<String, Object> row : results) {
            // 提取实体 URI 作为节点
            String entityId = String.valueOf(row.getOrDefault("entity", row.getOrDefault("uri", "")));
            if (entityId != null && !entityId.isBlank() && !entityId.equals("null")) {
                String label = String.valueOf(row.getOrDefault("name", row.getOrDefault("productName", entityId)));
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", entityId);
                node.put("label", label.length() > 30 ? label.substring(0, 30) + "..." : label);
                node.put("type", "entity");
                node.put("properties", row);
                nodes.add(node);
            }

            // 提取属性作为边
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value != null && !key.startsWith("_") && !key.equals("entity") && !key.equals("uri")) {
                    String valueStr = String.valueOf(value);
                    // 如果值是 URI，创建边
                    if (valueStr.startsWith("http")) {
                        Map<String, Object> edge = new LinkedHashMap<>();
                        edge.put("id", entityId + "-" + key + "-" + valueStr);
                        edge.put("source", entityId);
                        edge.put("target", valueStr);
                        edge.put("label", key);
                        edges.add(edge);
                    }
                }
            }
        }

        Map<String, Object> graphData = new LinkedHashMap<>();
        graphData.put("nodes", nodes);
        graphData.put("edges", edges);
        return graphData;
    }

    private String truncateStr(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}