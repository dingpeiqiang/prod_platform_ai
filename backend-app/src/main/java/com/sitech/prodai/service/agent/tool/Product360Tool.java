package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.metric.MetricService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品 360 视图工具（方案 §6-A4，对应缺口 C2）：单商品全景一屏呈现。
 * <p>
 * 聚合三个只读数据面（各面失败降级缺面，不冒充无数据）：
 * <ul>
 *   <li>基础档案：事实图 shelfOfferings（品类/资费/状态/上架天数/渠道范围/合约标记）；</li>
 *   <li>经营趋势：metric_query 同源指标链路（近30天订购量/收入，经行权限过滤）；</li>
 *   <li>风险信号：合约到期/互斥组/战略标记等档案内风险维度（投诉明细涉敏，未接数据源前缺省）。</li>
 * </ul>
 * <p>
 * 零编排改动接入：实现 {@code getScenes()} 含 query 即进入场景能力集（SPI 自声明）。
 * 数据行权限（§4.4）：指标面经 MetricService 行级过滤；档案面来自事实图货架
 * （目录级数据，SPARQL 链路权限子句同源口径）。
 */
@Component
public class Product360Tool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(Product360Tool.class);

    /** 指标趋势窗口（近30天，方案 outputFields 订阅量走势口径）。 */
    private static final String TREND_WINDOW = "recent_30d";

    private final ProductOntologyService productOntologyService;
    private final MetricService metricService;

    public Product360Tool(ProductOntologyService productOntologyService, MetricService metricService) {
        this.productOntologyService = productOntologyService;
        this.metricService = metricService;
    }

    @Override
    public String getName() {
        return "product_360";
    }

    @Override
    public String getDescription() {
        return "按商品 ID 或名称聚合呈现单商品全景：基础档案（品类/资费/状态/上架时间/渠道范围）、"
                + "近30天订购量与收入趋势、合约与互斥风险信号、数据来源。"
                + "用户指定/指名单个商品要看全貌（如「看一下XX套餐的360视图」「XX商品全景」）时使用；"
                + "多商品对比请用 rd_scheme_compare，宽泛找商品请用 sparql_query";
    }

    @Override
    public String getLabel() {
        return "商品全景";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("ops", "query");
    }

    /** 商品全景后的典型业务链：对比候选方案 / 查指标趋势 / 转研发检索相似配置。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("rd_scheme_compare", "metric_query", "rd_config_search");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("offering_id")
                        .label("商品编码")
                        .description("商品编码（有编码走精查）；与 offering_name 至少提供一个")
                        .type("string")
                        .source("context")
                        .build(),
                ToolParam.builder("offering_name")
                        .label("商品名称")
                        .description("商品名称或含商品名的话术（无编码时按名称最长匹配解析）")
                        .type("string")
                        .source("question")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("全景摘要").type("string")
                        .description("商品全景摘要").build(),
                ToolOutputField.builder("profile", ToolOutputField.Role.OTHER)
                        .label("基础档案").type("object")
                        .description("基础档案：品类/资费/状态/上架天数/渠道范围/客群").build(),
                ToolOutputField.builder("subscriptions", ToolOutputField.Role.COUNT)
                        .label("近30天订购").type("object")
                        .description("近30天订购量与收入趋势（窗口总量+环比）").build(),
                ToolOutputField.builder("risk_signals", ToolOutputField.Role.OTHER)
                        .label("风险信号").type("list")
                        .description("合约/互斥/战略标记等风险信号清单").build(),
                ToolOutputField.builder("sources", ToolOutputField.Role.OTHER)
                        .label("数据来源").type("object")
                        .description("各数据面来源与口径（数据新旧可感知）").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String offeringId = params != null ? str(params.get("offering_id")) : "";
        String offeringName = params != null ? str(params.get("offering_name")) : "";
        log.info("[AgentTool] product_360 执行: offeringId={}, offeringName={}", offeringId, offeringName);

        try {
            // 商品解析：有 id 走精查，无 id 按话术名称最长匹配（与指标链路同一解析器）
            String resolvedId = productOntologyService.resolveOfferingId(offeringId, offeringName);
            if (resolvedId == null || resolvedId.isBlank()) {
                return ExecutionResult.fail(getName(),
                        "无法定位商品：请提供商品编码或确切的商品名称（当前入参 id=" + offeringId + ", name=" + offeringName + "）");
            }
            Map<String, Object> offering = findShelfOffering(resolvedId);
            if (offering == null) {
                return ExecutionResult.fail(getName(), "货架中未找到商品 " + resolvedId + "（可能已下架或编码有误）");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("offering_id", resolvedId);
            out.put("profile", buildProfile(offering));
            out.put("subscriptions", buildSubscriptions(resolvedId));
            out.put("risk_signals", buildRiskSignals(offering));
            out.put("sources", buildSources(offering, resolvedId));
            out.put("nl_answer", buildSummary(resolvedId, offering));
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] product_360 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "商品全景查询失败: " + e.getMessage());
        }
    }

    /** 基础档案：事实图货架条目的目录级字段投影。 */
    private Map<String, Object> buildProfile(Map<String, Object> offering) {
        Map<String, Object> profile = new LinkedHashMap<>();
        putIfPresent(profile, "offering_name", offering.get("offeringName"));
        putIfPresent(profile, "category_name", offering.get("categoryName"));
        putIfPresent(profile, "product_line", offering.get("productLine"));
        putIfPresent(profile, "offering_type", offering.get("offeringType"));
        putIfPresent(profile, "state", offering.get("state"));
        putIfPresent(profile, "monthly_fee", offering.get("monthlyFee"));
        putIfPresent(profile, "shelf_days", offering.get("shelfDays"));
        putIfPresent(profile, "channel_scope", offering.get("channelScope"));
        putIfPresent(profile, "target_user", offering.get("targetUser"));
        putIfPresent(profile, "biz_scenario", offering.get("bizScenario"));
        return profile;
    }

    /** 经营趋势面：指标链路（近30天订购量），失败降级缺面（不冒充无数据）。 */
    private Map<String, Object> buildSubscriptions(String offeringId) {
        try {
            Map<String, Object> series = metricService.series("order_cnt", offeringId, TREND_WINDOW);
            if (Boolean.TRUE.equals(series.get("success"))) {
                Map<String, Object> sub = new LinkedHashMap<>();
                sub.put("metric", series.get("metric"));
                sub.put("metric_name", series.get("metric_name"));
                sub.put("window", series.get("window"));
                sub.put("total", series.get("total"));
                sub.put("delta_pct", series.get("delta_pct"));
                sub.put("source", series.get("source"));
                return sub;
            }
        } catch (Exception e) {
            log.warn("[AgentTool] product_360 指标面降级: {}", e.getMessage());
        }
        Map<String, Object> degraded = new LinkedHashMap<>();
        degraded.put("unavailable", true);
        return degraded;
    }

    /** 风险信号面：档案内风险维度（合约/互斥/战略/依赖），条目化输出。 */
    private List<Map<String, Object>> buildRiskSignals(Map<String, Object> offering) {
        List<Map<String, Object>> signals = new ArrayList<>();
        if (Boolean.TRUE.equals(offering.get("hasContract"))) {
            signals.add(Map.of("type", "contract", "message", "该商品含合约约束（合约期与到期日需在合约库补充）"));
        }
        Object mutex = offering.get("mutexGroup");
        if (mutex != null && !String.valueOf(mutex).isBlank()) {
            signals.add(Map.of("type", "mutex", "message", "互斥组 " + mutex + "：同组商品不可并存办理"));
        }
        if (Boolean.TRUE.equals(offering.get("strategicTag"))) {
            signals.add(Map.of("type", "strategic", "message", "战略标记商品：调整需走专项审批"));
        }
        Object dependOn = offering.get("dependOn");
        if (dependOn != null && !String.valueOf(dependOn).isBlank()) {
            signals.add(Map.of("type", "dependency", "message", "依赖主商品 " + dependOn + "（附加类商品须主套包在订）"));
        }
        // 投诉明细涉敏（敏感度=2）且数据源未接：不出造数信号，缺面如实呈现
        return signals;
    }

    /** 数据来源面：各数据面口径与新旧可感知信息（方案 §6-A1 数据时间戳透出的挂点）。 */
    private Map<String, Object> buildSources(Map<String, Object> offering, String offeringId) {
        Map<String, Object> sources = new LinkedHashMap<>();
        Map<String, Object> meta = productOntologyService.withModeMeta(new LinkedHashMap<>());
        sources.put("profile_source", String.valueOf(meta.getOrDefault("dataSource", "unknown")));
        sources.put("profile_mode", String.valueOf(meta.getOrDefault("dataSourceMode", "unknown")));
        sources.put("metric_source", metricSourceId(offeringId));
        return sources;
    }

    private String metricSourceId(String offeringId) {
        try {
            Map<String, Object> series = metricService.series("order_cnt", offeringId, TREND_WINDOW);
            return String.valueOf(series.getOrDefault("source", "unknown"));
        } catch (Exception e) {
            return "unavailable";
        }
    }

    /** 全景摘要：一句话串联商品名 + 状态 + 资费 + 近30天订购。 */
    private String buildSummary(String offeringId, Map<String, Object> offering) {
        StringBuilder sb = new StringBuilder();
        sb.append("商品 ").append(str(offering.getOrDefault("offeringName", offeringId)));
        Object state = offering.get("state");
        if (state != null && !String.valueOf(state).isBlank()) {
            sb.append("（").append(state).append("）");
        }
        Object fee = offering.get("monthlyFee");
        if (fee != null) {
            sb.append("，月费 ").append(fee).append(" 元");
        }
        Map<String, Object> sub = buildSubscriptions(offeringId);
        if (!Boolean.TRUE.equals(sub.get("unavailable")) && sub.get("total") != null) {
            sb.append("，近30天订购 ").append(sub.get("total"));
            if (sub.get("delta_pct") != null) {
                sb.append("（环比 ").append(sub.get("delta_pct")).append("%）");
            }
        }
        return sb.toString();
    }

    /** 货架商品反查：经事实图货架精确匹配（未命中返回 null）。 */
    private Map<String, Object> findShelfOffering(String offeringId) {
        List<Map<String, Object>> shelf = castListOfMaps(productOntologyService.loadGraph().get("shelfOfferings"));
        return shelf.stream()
                .filter(o -> offeringId.equals(str(o.get("offeringId"))))
                .findFirst()
                .orElse(null);
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            map.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListOfMaps(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
