package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
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
 * <p>IRI 口径与灌图一致：类型/谓词用绝对 IRI（{@code <baseIri>Offering}），不用未声明前缀的缩写名。
 */
@Service
public class SparqlConfigDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(SparqlConfigDiscoverer.class);

    /** query_type → 本体语义标签（与 FactGraphSyncService 灌入的属性值对应）。 */
    private static final Map<String, List<String>> TYPE_LABELS = Map.of(
            "campus", List.of("校园", "学生", "青春", "大学"),
            "family", List.of("家庭", "融合"),
            "broadband", List.of("宽带", "提速"),
            "5g", List.of("5G", "畅享"),
            "device", List.of("终端", "手机", "宽带电视", "IPTV", "机顶盒"),
            "sim_card", List.of("号卡", "副卡", "物联卡", "流量卡")
    );

    /** 通用话术词：无过滤价值，不叠加为关键词 FILTER。 */
    private static final List<String> GENERIC_WORDS =
            List.of("套餐", "模板", "资费", "方案", "配置", "在售", "在架", "上线");

    private final Rdf4jOntologyStore rdf4jStore;
    private final ProdAiProperties properties;

    public SparqlConfigDiscoverer(Rdf4jOntologyStore rdf4jStore, ProdAiProperties properties) {
        this.rdf4jStore = rdf4jStore;
        this.properties = properties;
    }

    /** 与 FactGraphSyncService/Rdf4jOntologyStore.iri 灌图口径一致：baseIri + 本地名。 */
    private String typeIri(String type) {
        return "<" + properties.getOntology().normalizedBaseIri() + type + ">";
    }

    private String propIri(String prop) {
        return "<" + properties.getOntology().normalizedBaseIri() + prop + ">";
    }

    /**
     * 执行语义检索，返回打分排序后的商品卡片（与旧 discoverConfigs 输出结构对齐）。
     * <p>行权限（方案 §4.4）：{@code scope} 非空且非全量时，权限子句强制注入——
     * 优先级最高、后拼接（任何业务 FILTER 都改变不了权限过滤结果）；scope 为空回落
     * unrestricted 行为（未启用行权限的部署形态零漂移）。
     */
    public List<Map<String, Object>> discover(LlmIntentExtractor.DiscoverIntent intent,
                                              com.sitech.prodai.service.agent.model.UserScope scope) {
        String sparql = buildSparql(intent, scope);
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

    /** 兼容入口：未带权限上下文的调用按 unrestricted 处理（全量可见）。 */
    public List<Map<String, Object>> discover(LlmIntentExtractor.DiscoverIntent intent) {
        return discover(intent, com.sitech.prodai.service.agent.model.UserScope.unrestricted());
    }

    private String buildSparql(LlmIntentExtractor.DiscoverIntent intent,
                               com.sitech.prodai.service.agent.model.UserScope scope) {
        String offering = typeIri("Offering");
        String pName = propIri("offeringName");
        String pFee = propIri("monthlyFee");
        String pState = propIri("state");
        String pCategory = propIri("categoryName");
        String pShelfDays = propIri("shelfDays");
        String pChannel = propIri("channelScope");
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ?offering ?offeringName ?monthlyFee ?state ?categoryName ?shelfDays ")
                .append("WHERE { ?offering a ").append(offering).append(" ; ")
                .append(pName).append(" ?offeringName . ")
                .append("OPTIONAL { ?offering ").append(pFee).append(" ?monthlyFee } ")
                .append("OPTIONAL { ?offering ").append(pState).append(" ?state } ")
                .append("OPTIONAL { ?offering ").append(pCategory).append(" ?categoryName } ")
                .append("OPTIONAL { ?offering ").append(pShelfDays).append(" ?shelfDays } ");
        // 行权限过滤需要 channelScope 变量：受限 scope 时才绑定（unrestricted 不增加查询面）
        boolean channelRestricted = scope != null && !scope.isAllChannels();
        if (channelRestricted) {
            sb.append("OPTIONAL { ?offering ").append(pChannel).append(" ?channelScope } ");
        }
        List<String> filters = new ArrayList<>();
        // ── 行权限子句（方案 §4.4）：最先判定，业务 FILTER 之前拼接；
        //    生成自服务端 UserScope（非 LLM 产物），无注入面（值经 quote 转义）
        if (scope != null && !scope.isAllChannels()) {
            List<String> channelOrs = new ArrayList<>();
            for (String ch : scope.getVisibleChannels()) {
                // 精确词项等值 + CONTAINS 兜底（channelScope 存复合值如「电渠+厅店」）
                channelOrs.add("SAMETERM(STR(?channelScope), " + quote(ch) + ")");
                channelOrs.add("CONTAINS(STR(?channelScope), " + quote(ch) + ")");
            }
            filters.add("(BOUND(?channelScope) && (" + String.join(" || ", channelOrs) + "))");
        }
        if (intent.state() != null && !"null".equalsIgnoreCase(intent.state())) {
            // 用 SAMETERM 做枚举等值：本库中 '=' 对 xsd:string 字面量在部分比较路径下不成立
            // （CONTAINS/SAMETERM 均可命中，'=' 恒假——RDF4J 4.3.4 实测），且枚举本就是词项等值
            filters.add("SAMETERM(?state, " + quote(intent.state()) + ")");
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
        if (intent.timeWindowDays() != null) {
            filters.add("(BOUND(?shelfDays) && ?shelfDays <= " + trim(intent.timeWindowDays()) + ")");
        }
        for (String kw : intent.keywords()) {
            // 品类语义已由 query_type 标签 FILTER 覆盖（TYPE_LABELS），同义关键词不再叠加过滤
            if (labels.contains(kw)) {
                continue;
            }
            if (GENERIC_WORDS.contains(kw)) {
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
        Object shelfDays = row.get("shelfDays");
        card.put("shelf_days", shelfDays == null ? null : (int) num(shelfDays));
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
