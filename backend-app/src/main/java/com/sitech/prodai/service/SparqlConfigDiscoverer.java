package com.sitech.prodai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本体 SPARQL 配置发现器：基于 LLM 解析的 {@link LlmIntentExtractor.DiscoverIntent} 构建参数化 SPARQL，
 * 在 RDF4J 本体图的 Offering 实例上执行语义检索。
 * <p>查询按意图分层：query_type 映射本体语义标签（关键词 FILTER + 月费容差 FILTER），
 * 全部参数经字符串常量拼接（枚举/数值），无注入面；自由关键词走 CONTAINS 且对引号转义。
 */
@Service
public class SparqlConfigDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(SparqlConfigDiscoverer.class);

    /** query_type → 本体语义标签（与 FactGraphSyncService 灌入的属性值对应）。 */
    private static final Map<String, List<String>> TYPE_LABELS = Map.of(
            "campus", List.of("校园", "学生", "青春", "大学"),
            "family", List.of("家庭", "融合"),
            "broadband", List.of("宽带", "提速"),
            "5g", List.of("5G", "畅享")
    );

    private final Rdf4jOntologyStore rdf4jStore;

    public SparqlConfigDiscoverer(Rdf4jOntologyStore rdf4jStore) {
        this.rdf4jStore = rdf4jStore;
    }

    /**
     * 执行语义检索，返回打分排序后的商品卡片（与旧 discoverConfigs 输出结构对齐）。
     */
    public List<Map<String, Object>> discover(LlmIntentExtractor.DiscoverIntent intent) {
        String sparql = buildSparql(intent);
        log.info("[SparqlConfigDiscoverer] engine={} SPARQL: {}", intent.engine(), sparql.replaceAll("\\s+", " "));
        List<Map<String, Object>> rows = rdf4jStore.sparqlSelect(sparql);
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            cards.add(toCard(row, intent));
        }
        cards.sort((a, b) -> Integer.compare((int) num(b.get("score")), (int) num(a.get("score"))));
        int lim = Math.max(1, Math.min(intent.limit(), 50));
        return cards.size() > lim ? new ArrayList<>(cards.subList(0, lim)) : cards;
    }

    private String buildSparql(LlmIntentExtractor.DiscoverIntent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ?offering ?offeringName ?monthlyFee ?state ?categoryName ")
                .append("WHERE { ?offering a :Offering ; :offeringName ?offeringName . ")
                .append("OPTIONAL { ?offering :monthlyFee ?monthlyFee } ")
                .append("OPTIONAL { ?offering :state ?state } ")
                .append("OPTIONAL { ?offering :categoryName ?categoryName } ");
        List<String> filters = new ArrayList<>();
        if (intent.state() != null && !"null".equalsIgnoreCase(intent.state())) {
            filters.add("STR(?state) = " + quote(intent.state()));
        }
        List<String> labels = TYPE_LABELS.getOrDefault(intent.queryType(), List.of());
        if (!labels.isEmpty()) {
            List<String> ors = new ArrayList<>();
            for (String label : labels) {
                ors.add("CONTAINS(STR(?offeringName), " + quote(label) + ")");
            }
            filters.add("(" + String.join(" || ", ors) + ")");
        }
        if (intent.monthlyFee() != null) {
            double tol = intent.feeTolerance() == null ? 5 : intent.feeTolerance();
            double lo = Math.max(0, intent.monthlyFee() - tol);
            double hi = intent.monthlyFee() + tol;
            filters.add("(BOUND(?monthlyFee) && ?monthlyFee >= " + trim(lo) + " && ?monthlyFee <= " + trim(hi) + ")");
        }
        for (String kw : intent.keywords()) {
            if (TYPE_LABELS.values().stream().anyMatch(labels::contains)) {
                continue;
            }
            if (List.of("套餐", "模板", "资费", "方案", "配置", "在售", "在架", "上线").contains(kw)) {
                continue;
            }
            filters.add("(CONTAINS(STR(?offeringName), " + quote(kw) + ") "
                    + "|| CONTAINS(STR(?categoryName), " + quote(kw) + "))");
        }
        for (String f : filters) {
            sb.append("FILTER ").append(f).append(" . ");
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, Object> toCard(Map<String, Object> row, LlmIntentExtractor.DiscoverIntent intent) {
        Map<String, Object> card = new LinkedHashMap<>();
        String uri = str(row.get("offering"));
        String offeringId = uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1) : uri;
        String name = str(row.get("offeringName"));
        card.put("offering_id", offeringId);
        card.put("offering_name", name);
        // 双命名对齐：与词典回退 toQueryCard 的 id/code/name 字段结构一致，
        // 前端/复制链路（offering 参数）无需按检索引擎区分字段名
        card.put("id", offeringId);
        card.put("code", offeringId);
        card.put("name", name);
        Object fee = row.get("monthlyFee");
        card.put("monthly_fee", fee == null ? null : num(fee));
        card.put("state", str(row.get("state")));
        card.put("category_name", str(row.get("categoryName")));
        int score = 60;
        if (intent.monthlyFee() != null && fee != null) {
            double diff = Math.abs(num(fee) - intent.monthlyFee());
            double tol = intent.feeTolerance() == null ? 5 : intent.feeTolerance();
            score += diff <= tol / 2 ? 30 : (diff <= tol ? 15 : 0);
        }
        if (!"all".equals(intent.queryType())) {
            score += 10;
        }
        card.put("score", score);
        card.put("engine", "sparql");
        return card;
    }

    private String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private double num(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
