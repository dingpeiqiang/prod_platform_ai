package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.intent.SseStreamSupport;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.ThinkingStepBuilder;
import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.ProductOntologyService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 市场洞察：优先 ops 事实图（与归因/稽核同源），RDF 空库时不再误报「暂无匹配」。
 */
@Component
public class ProductOpsQueryHandler implements BaseIntentHandler {

    private final ProductOntologyService productOntologyService;
    private final OntologyService ontologyService;

    public ProductOpsQueryHandler(ProductOntologyService productOntologyService, OntologyService ontologyService) {
        this.productOntologyService = productOntologyService;
        this.ontologyService = ontologyService;
    }

    @Override
    public String getIntentType() {
        return "product_ops_query";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        if (IntentRecognitionSupport.isMetaGuideRequest(ctx.getLastUserMessage())) {
            return Flux.fromIterable(IntentRecognitionSupport.metaGuideSkipEvents("市场洞察"));
        }
        String question = ctx.getExtractedFields().containsKey("question")
                ? String.valueOf(ctx.getExtractedFields().get("question"))
                : ctx.getLastUserMessage();

        List<Map<String, Object>> prelude = List.of(
                ThinkingStepBuilder.running(
                        "scope", "锁定洞察范围", "正在解析洞察问题与检索范围...",
                        2, 5, Map.of("question", question.length() > 60 ? question.substring(0, 60) + "..." : question))
        );

        return SseStreamSupport.deferWork(
                prelude,
                () -> retrieveMarketInsight(question),
                (result, elapsedMs) -> buildAfterEvents(ctx, question, result, elapsedMs)
        );
    }

    /**
     * 主路径：ops 图谱；若无命中再尝试 RDF NL（兼容仍灌有 rdf_seed 的环境）。
     */
    private Map<String, Object> retrieveMarketInsight(String question) {
        Map<String, Object> ops = productOntologyService.marketInsight(question, 20);
        List<Map<String, Object>> opsRows = toMapList(ops.get("raw_results"));
        if (!opsRows.isEmpty()) {
            return ops;
        }

        Map<String, Object> rdf = ontologyService.nlDiscoverAndRetrieve(question, 20);
        List<Map<String, Object>> rdfRows = toMapList(rdf.get("raw_results"));
        if (!rdfRows.isEmpty()) {
            return rdf;
        }

        // 两边皆空：保留 ops 侧话术与 discovery_method，便于前端提示同源数据源
        if (ops.get("nl_answer") != null) {
            return ops;
        }
        return rdf;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildAfterEvents(
            IntentContext ctx, String question, Map<String, Object> result, long elapsedMs
    ) {
        List<Map<String, Object>> results = List.of();
        if (result.get("raw_results") instanceof List<?> list) {
            results = list.stream().filter(Map.class::isInstance).map(e -> (Map<String, Object>) e).toList();
        }
        Map<String, Object> trendSummary = buildTrendSummary(results);
        String answerText = formatAnswer(String.valueOf(result.getOrDefault("nl_answer", "")), results, trendSummary);
        String discoveryMethod = String.valueOf(result.getOrDefault("discovery_method", "unknown"));

        // 面板展示列：业务字段优先，避免 product/entity/uri 等内部 ID 占满前几列
        List<String> columns = buildDisplayColumns(results);

        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("question", question);
        intentData.put("sparql", result.get("sparql"));
        intentData.put("results", results);
        intentData.put("count", results.size());
        intentData.put("columns", columns);
        intentData.put("discoveryMethod", discoveryMethod);
        intentData.put("graphData", buildGraphData(results));
        intentData.put("trendSummary", trendSummary);

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
        donePayload.put("trendSummary", trendSummary);

        String methodLabel = switch (discoveryMethod) {
            case "ops_graph" -> "经营数据";
            case "llm" -> "智能检索";
            case "keyword_fallback" -> "关键词匹配";
            default -> "业务检索";
        };

        String scopeResult = question == null || question.isBlank() ? "通用市场洞察" : shortText(question, 40);
        String retrieveResult = "命中 " + results.size() + " 条（" + methodLabel + "）";
        String aggregateResult = formatTrendResult(trendSummary);
        String concludeResult = results.isEmpty()
                ? "暂无匹配商品，可换关键词或同步事实图"
                : "共 " + results.size() + " 条洞察结果已就绪";

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(ThinkingStepBuilder.done(
                "scope", "锁定洞察范围", "解析洞察问题与检索范围",
                scopeResult, 2, 5, 0, null, Map.of("question", scopeResult)));
        events.add(ThinkingStepBuilder.done(
                "retrieve", "检索经营事实", "检索经营事实图与在售商品",
                retrieveResult, 3, 5, 0, null,
                Map.of("discoveryMethod", discoveryMethod, "resultCount", results.size())));
        events.add(ThinkingStepBuilder.done(
                "aggregate", "聚合经营指标", "汇总增长、负增与零资费等指标",
                aggregateResult, 4, 5, 0, null, Map.of("trendSummary", trendSummary)));
        events.add(ThinkingStepBuilder.done(
                "conclude", "生成洞察摘要", "汇总洞察结论",
                concludeResult, 5, 5, elapsedMs, null,
                Map.of("resultCount", results.size())));
        events.add(SseUtils.intentEvent(getIntentType(), "query", intentData, false));
        events.addAll(SseStreamSupport.chunkedTextEvents(answerText));
        events.add(SseUtils.stats(ctx.getStreamStats()));
        events.add(SseUtils.doneEvent(getIntentType(), false, donePayload));
        return events;
    }

    private String formatTrendResult(Map<String, Object> trend) {
        Object avg = trend.get("avgGrowth");
        Object neg = trend.get("negativeCount");
        Object zero = trend.get("zeroFeeCount");
        if (!(avg instanceof Number n)) {
            return "样本不足，暂无聚合指标";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("平均增长 ").append(String.format("%.1f%%", n.doubleValue() * 100));
        if (neg instanceof Number negN && negN.intValue() > 0) {
            sb.append(" · 负增长 ").append(negN.intValue()).append(" 个");
        }
        if (zero instanceof Number z && z.intValue() > 0) {
            sb.append(" · 零资费 ").append(z.intValue()).append(" 个");
        }
        return sb.toString();
    }

    private String shortText(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /**
     * 正文只给短摘要；明细交给「市场洞察结果」面板，避免 key=value 列表与表格重复。
     */
    private String formatAnswer(String summary, List<Map<String, Object>> results, Map<String, Object> trend) {
        StringBuilder sb = new StringBuilder();
        sb.append(summary == null || summary.isBlank() ? "查询完成" : summary.trim());
        if (results.isEmpty()) {
            sb.append("\n当前事实图暂无匹配在售/风险商品，可换个品类关键词或先同步 ops-graph。");
            return sb.toString();
        }
        sb.append("\n共 ").append(results.size()).append(" 条，详见下方市场洞察结果。");
        Object avg = trend.get("avgGrowth");
        Object neg = trend.get("negativeCount");
        Object zero = trend.get("zeroFeeCount");
        if (avg instanceof Number n) {
            sb.append("\n平均增长 ").append(String.format("%.1f%%", n.doubleValue() * 100));
            if (neg instanceof Number negN && negN.intValue() > 0) {
                sb.append("，负增长 ").append(negN.intValue()).append(" 个");
            }
            if (zero instanceof Number z && z.intValue() > 0) {
                sb.append("，零资费 ").append(z.intValue()).append(" 个");
            }
            sb.append("。");
        }
        return sb.toString();
    }

    private List<String> buildDisplayColumns(List<Map<String, Object>> results) {
        if (results.isEmpty()) return List.of();
        Map<String, Object> sample = results.get(0);
        List<String> preferred = List.of("name", "status", "growth", "users", "isZeroFee", "_bucket");
        List<String> columns = new ArrayList<>();
        for (String key : preferred) {
            if (hasDisplayValue(sample, key) || results.stream().anyMatch(r -> hasDisplayValue(r, key))) {
                columns.add(key);
            }
        }
        if (columns.isEmpty()) {
            for (String key : sample.keySet()) {
                if (!key.startsWith("_") && !List.of("product", "entity", "uri", "productName",
                        "revenueGrowth", "newUserMonth").contains(key)) {
                    columns.add(key);
                }
            }
        }
        return columns;
    }

    private boolean hasDisplayValue(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return false;
        String s = String.valueOf(val).trim();
        return !s.isEmpty() && !"null".equalsIgnoreCase(s);
    }

    private Map<String, Object> buildTrendSummary(List<Map<String, Object>> results) {
        List<Double> growths = new ArrayList<>();
        int zeroFee = 0;
        for (Map<String, Object> row : results) {
            Double g = parseGrowth(row.get("growth"));
            if (g == null) g = parseGrowth(row.get("revenueGrowth"));
            if (g != null) growths.add(g);
            Object z = row.get("isZeroFee");
            if (Boolean.TRUE.equals(z) || "true".equalsIgnoreCase(String.valueOf(z))
                    || "1".equals(String.valueOf(z)) || "是".equals(String.valueOf(z))) {
                zeroFee++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sampleCount", growths.size());
        summary.put("zeroFeeCount", zeroFee);
        if (growths.isEmpty()) {
            summary.put("avgGrowth", null);
            summary.put("negativeCount", 0);
            return summary;
        }
        double sum = 0;
        int neg = 0;
        for (Double g : growths) {
            sum += g;
            if (g < 0) neg++;
        }
        summary.put("avgGrowth", sum / growths.size());
        summary.put("negativeCount", neg);
        return summary;
    }

    private Double parseGrowth(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) {
            double v = n.doubleValue();
            return Math.abs(v) > 1 ? v / 100.0 : v;
        }
        try {
            String s = String.valueOf(raw).replace("%", "").trim();
            if (s.isEmpty()) return null;
            double v = Double.parseDouble(s);
            return Math.abs(v) > 1 ? v / 100.0 : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> buildGraphData(List<Map<String, Object>> results) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (Map<String, Object> row : results) {
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

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value != null && !key.startsWith("_") && !key.equals("entity") && !key.equals("uri")) {
                    String valueStr = String.valueOf(value);
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

    private List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }

    private String truncateStr(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
