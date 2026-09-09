package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.metric.MetricRegistryService;
import com.sitech.prodai.service.metric.MetricService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 指标查询工具（指标域 P0）：自然语言 → 字典口径解析 → 指标服务时序/下钻/异动查询。
 * <p>
 * 与 {@code SparqlQueryTool} 分工：SPARQL 答关系问题（本体），本工具答数值与时间问题
 * （指标仓）；理解层按场景白名单可见（ops + query），手册 market-insight 前置调用。
 * <p>
 * LLM 不算数：指标/维度/窗口解析优先参数显式传入，缺省由
 * {@link MetricRegistryService#resolveMetricByAlias} 口语别名映射（字典 aliases 单源），
 * 窗口与下钻维度缺省时由话术语义解析兜底（{@link #resolveWindowFromQuestion}/
 * {@link #resolveDimensionFromQuestion}，B7 参数化增强）；全部聚合与派生由
 * {@link MetricService} 引擎计算。
 */
@Component
public class MetricQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(MetricQueryTool.class);

    /** 话术窗口口语 → 窗口 ID（长短语优先匹配，防「近7天」误吞「近7天环比」）。 */
    private static final List<Map.Entry<String, String>> WINDOW_PHRASES = List.of(
            Map.entry("月环比", "mom"),
            Map.entry("周环比", "wow"),
            Map.entry("同比", "yoy"),
            Map.entry("环比", "mom"),
            Map.entry("本月", "mtd"),
            Map.entry("本月至今", "mtd"),
            Map.entry("近7天", "recent_7d"),
            Map.entry("近30天", "recent_30d"),
            Map.entry("近90天", "recent_90d"),
            Map.entry("近一周", "recent_7d"),
            Map.entry("近一月", "recent_30d"),
            Map.entry("近三月", "recent_90d"),
            Map.entry("上周", "wow"),
            Map.entry("上月", "mom"));

    /** 话术下钻口语 → 维度 ID（长短语优先，防「客户群」误吞）。 */
    private static final List<Map.Entry<String, String>> DIMENSION_PHRASES = List.of(
            Map.entry("分地市", "region"),
            Map.entry("各地市", "region"),
            Map.entry("按地市", "region"),
            Map.entry("地市", "region"),
            Map.entry("地区", "region"),
            Map.entry("分渠道", "channel"),
            Map.entry("各渠道", "channel"),
            Map.entry("按渠道", "channel"),
            Map.entry("渠道", "channel"),
            Map.entry("客户群", "customer"),
            Map.entry("分客群", "customer"),
            Map.entry("按客群", "customer"),
            Map.entry("客群", "customer"));

    private final MetricService metricService;
    private final MetricRegistryService metricRegistry;

    public MetricQueryTool(MetricService metricService, MetricRegistryService metricRegistry) {
        this.metricService = metricService;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public String getName() {
        return "metric_query";
    }

    @Override
    public String getDescription() {
        return "查询运营指标时序/下钻/异动（收入、订购量、新增、流失、ARPU 等指标的趋势、构成与波动检测）";
    }

    @Override
    public String getLabel() {
        return "指标查询";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("ops", "query");
    }

    /** 指标查询后的典型业务链：异动 → 归因/稽核；趋势 → 事实关系补充（SPARQL）。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("swrl_root_cause", "swrl_risk_audit", "sparql_query");
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("metric", ToolOutputField.Role.OTHER)
                        .label("指标").type("string")
                        .description("指标编码（如 revenue/order_cnt/arpu）").build(),
                ToolOutputField.builder("metric_name", ToolOutputField.Role.OTHER)
                        .label("指标名称").type("string")
                        .description("指标业务名称").build(),
                ToolOutputField.builder("total", ToolOutputField.Role.COUNT)
                        .label("窗口总量").type("number")
                        .description("查询窗口内指标合计").build(),
                ToolOutputField.builder("delta_pct", ToolOutputField.Role.OTHER)
                        .label("环比").type("number")
                        .description("环比/同比变化率（有对比窗口时）").build(),
                ToolOutputField.builder("series", ToolOutputField.Role.ITEMS)
                        .label("时序数据").type("list")
                        .description("日粒度时间序列 [{date, value}]").build(),
                ToolOutputField.builder("items", ToolOutputField.Role.ITEMS)
                        .label("下钻明细").type("list")
                        .description("维度下钻结果 [{key, value, ratio}]").build(),
                ToolOutputField.builder("anomaly", ToolOutputField.Role.OTHER)
                        .label("异动标记").type("boolean")
                        .description("是否检出异动（突降/连降）").build(),
                ToolOutputField.builder("message", ToolOutputField.Role.SUMMARY)
                        .label("结果说明").type("string")
                        .description("查询结果或失败说明").build()
        );
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("question")
                        .label("查询语句")
                        .description("自然语言指标问题（如「近30天收入趋势」）；未显式给 metric 时按字典别名解析")
                        .required()
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("metric")
                        .label("指标")
                        .description("指标编码（revenue/order_cnt/new_users/churn_users/active_users/arpu/attach_rate/net_growth）；缺省按话术别名解析")
                        .type("string")
                        .build(),
                ToolParam.builder("offeringId")
                        .label("商品范围")
                        .description("限定商品编码；缺省全量商品")
                        .type("string")
                        .source("context")
                        .build(),
                ToolParam.builder("window")
                        .label("时间窗口")
                        .description("recent_7d/recent_30d/recent_90d/mtd/wow/mom/yoy；缺省按话术解析（如「近7天」→recent_7d、「月环比」→mom），再缺省按字典默认")
                        .type("string")
                        .build(),
                ToolParam.builder("dimension")
                        .label("下钻维度")
                        .description("region/channel/customer；传入时执行下钻查询（返回 items）；缺省按话术解析（如「分地市」→region）")
                        .type("string")
                        .build(),
                ToolParam.builder("mode")
                        .label("查询模式")
                        .description("series=时序（默认）/ breakdown=下钻 / anomaly=异动检测")
                        .type("string")
                        .defaultValue("series")
                        .build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String question = params != null ? str(params.get("question")) : "";
        String mode = params != null && !str(params.get("mode")).isBlank()
                ? str(params.get("mode")).toLowerCase() : "series";
        String rawMetric = params != null ? str(params.get("metric")) : "";
        // metric 既可为指标 ID 也可为口语别名（如「收入」）；非注册 ID 时回落别名解析
        String metricId = !rawMetric.isBlank() && metricRegistry.metric(rawMetric).isEmpty()
                ? metricRegistry.resolveMetricByAlias(rawMetric)
                : (!rawMetric.isBlank() ? rawMetric : metricRegistry.resolveMetricByAlias(question));
        String offeringId = params != null ? str(params.getOrDefault("offeringId",
                str(params.get("offering_id")))) : "";
        // 窗口/维度缺省时由话术语义解析兜底（LLM 忘传参数也能命中意图；显式参数优先）
        String window = params != null && !str(params.get("window")).isBlank()
                ? str(params.get("window")) : resolveWindowFromQuestion(question);
        String dimension = params != null && !str(params.get("dimension")).isBlank()
                ? str(params.get("dimension")) : resolveDimensionFromQuestion(question);

        log.info("[AgentTool] metric_query 执行: mode={} metric={} offering={} window={} dimension={}",
                mode, metricId, offeringId, window, dimension);

        try {
            if (metricId == null || metricId.isBlank()) {
                return ExecutionResult.fail(getName(), "无法从请求解析指标，请指明指标（如收入/订购量/新增用户）"
                        + "或使用 metric 参数；可用指标: " + metricRegistry.metricIds());
            }
            // 下钻意图（话术含维度或显式 dimension）但 mode 未声明时自动升级 breakdown
            if (!dimension.isBlank() && "series".equals(mode)) {
                mode = "breakdown";
            }
            Map<String, Object> result;
            switch (mode) {
                case "breakdown" -> result = metricService.breakdown(metricId, offeringId, window,
                        dimension.isBlank() ? "region" : dimension);
                case "anomaly" -> result = metricService.detectAnomaly(metricId, offeringId);
                default -> result = metricService.series(metricId, offeringId, window);
            }
            // 防御性拷贝：指标服务可返回不可变 Map，message 注入不得污染数据源返回值
            Map<String, Object> body = new java.util.LinkedHashMap<>(result);
            if (Boolean.TRUE.equals(body.get("success"))) {
                body.putIfAbsent("message", "已查询指标「" + body.get("metric_name") + "」");
                return ExecutionResult.ok(getName(), body);
            }
            // 指标服务业务失败（无数据/口径不支持）：如实透出，不冒充成功
            return ExecutionResult.fail(getName(), str(body.getOrDefault("message", "指标查询失败")));
        } catch (Exception e) {
            log.error("[AgentTool] metric_query 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "指标查询失败: " + e.getMessage());
        }
    }

    // ===== 话术语义解析（B7 参数化增强；LLM 不算数，引擎翻译） =====

    /**
     * 话术窗口解析：口语（近7天/本月/月环比/同比…）→ 窗口 ID，长短语优先；
     * 未命中返回空串（回落字典默认窗口，与既有行为零漂移）。
     */
    private String resolveWindowFromQuestion(String question) {
        return matchPhrase(WINDOW_PHRASES, question);
    }

    /**
     * 话术下钻维度解析：口语（分地市/各渠道/客户群…）→ 维度 ID；
     * 未命中返回空串（保持 series 模式，不擅自下钻）。
     */
    private String resolveDimensionFromQuestion(String question) {
        return matchPhrase(DIMENSION_PHRASES, question);
    }

    /** 长短语优先匹配：防止「近7天环比」先命中「近7天」而丢失对比语义。 */
    private String matchPhrase(List<Map.Entry<String, String>> phrases, String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String best = "";
        int bestLen = 0;
        for (Map.Entry<String, String> e : phrases) {
            if (text.contains(e.getKey()) && e.getKey().length() > bestLen) {
                best = e.getValue();
                bestLen = e.getKey().length();
            }
        }
        return best;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
