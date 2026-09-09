package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.queryheat.QueryHeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询热度分析工具（方案 §6-C4，对应缺口 C8"查询热度反哺商品运营"）。
 * <p>
 * 从会话审计数据聚合高频查询词/关键词与按天业务量趋势，给运营"用户在关注什么商品"
 * 的洞察入口——查询行为本身成为商品运营的信号源（可与 market-insight 手册联动：
 * 高热度商品 → 经营指标趋势核查）。
 * <p>
 * 口径约束（方案 §6-C4）：
 * <ul>
 *   <li>只聚合不落明细：输出仅含计数/关键词/日期，不透出 session/user 个人级数据
 *       （隐私护栏与 QueryHeatService 一致）；</li>
 *   <li>只读：无写操作、无落库；</li>
 *   <li>空态如实：无会话数据时明示"暂无查询记录"，不冒充零热度结论。</li>
 * </ul>
 * <p>
 * 零编排改动接入：实现 {@code getScenes()} 含 ops 即进入场景能力集（SPI 自声明）；
 * query 场景不同步开放（热度分析是运营视角，查询用户无需看到自己贡献的热度）。
 */
@Component
public class QueryHeatTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(QueryHeatTool.class);

    /** top N 默认值。 */
    private static final int DEFAULT_LIMIT = 10;

    private final QueryHeatService queryHeatService;

    public QueryHeatTool(QueryHeatService queryHeatService) {
        this.queryHeatService = queryHeatService;
    }

    @Override
    public String getName() {
        return "query_heat";
    }

    @Override
    public String getDescription() {
        return "查询热度分析：从历史会话聚合高频查询问题、高频关键词与近14天业务量趋势，"
                + "回答「用户最近都在查什么」「哪些商品被问得最多」「查询量趋势如何」等运营洞察问题。"
                + "运营复盘选品/营销定位时使用；查单个商品档案请用 product_360，查经营指标请用 metric_query";
    }

    @Override
    public String getLabel() {
        return "查询热度分析";
    }

    @Override
    public java.util.Set<String> getScenes() {
        // 方案 §6-C4：查询热度反哺商品运营——运营视角专属（query 场景不开放）
        return java.util.Set.of("ops");
    }

    /** 看完热度后的典型业务链：对高热商品查经营指标 / 看商品全景 / 走市场洞察手册。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("metric_query", "product_360", "sparql_query");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("limit")
                        .label("条数")
                        .description("高频问题/关键词各返回的条数（默认 10，上限 50）")
                        .type("number")
                        .defaultValue("10")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("热度摘要").type("string")
                        .description("高频问题与关键词概览摘要").build(),
                ToolOutputField.builder("total_questions", ToolOutputField.Role.COUNT)
                        .label("问题总量").type("number")
                        .description("聚合窗口内的用户问题总数").build(),
                ToolOutputField.builder("top_questions", ToolOutputField.Role.ITEMS)
                        .label("高频问题").type("list")
                        .description("高频查询问题条目（question/count，截断至 100 字）").build(),
                ToolOutputField.builder("top_keywords", ToolOutputField.Role.OTHER)
                        .label("高频关键词").type("list")
                        .description("高频关键词条目（keyword/count）").build(),
                ToolOutputField.builder("daily_counts", ToolOutputField.Role.OTHER)
                        .label("按天趋势").type("list")
                        .description("近14天每日问题条数（date/count，补零对齐）").build(),
                ToolOutputField.builder("sources", ToolOutputField.Role.OTHER)
                        .label("数据来源").type("object")
                        .description("数据来源与隐私口径").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        Integer limit = castInt(params != null ? params.get("limit") : null);
        log.info("[AgentTool] query_heat 执行: limit={}", limit);
        try {
            Map<String, Object> heat = queryHeatService.queryHeat(limit, null);
            if (!Boolean.TRUE.equals(heat.get("success"))) {
                return ExecutionResult.fail(getName(),
                        String.valueOf(heat.getOrDefault("message", "查询热度聚合失败")));
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> topQuestions = (List<Map<String, Object>>) heat.get("top_questions");
            if (topQuestions == null || topQuestions.isEmpty()) {
                return ExecutionResult.fail(getName(),
                        "暂无查询记录：用户开始使用查询助手后，这里会出现高频查询分析（当前无历史会话数据）");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total_questions", heat.get("total_questions"));
            out.put("top_questions", topQuestions);
            out.put("top_keywords", heat.get("top_keywords"));
            out.put("daily_counts", heat.get("daily_counts"));
            out.put("sources", heat.get("sources"));
            out.put("nl_answer", buildSummary(heat, topQuestions));
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] query_heat 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "查询热度分析失败: " + e.getMessage());
        }
    }

    /** 摘要：总量 + 高频问题 TOP3 串述 + 高频关键词提示。 */
    private String buildSummary(Map<String, Object> heat, List<Map<String, Object>> topQuestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("共聚合 ").append(heat.get("total_questions"))
                .append(" 个用户问题。高频查询：");
        for (int i = 0; i < Math.min(3, topQuestions.size()); i++) {
            if (i > 0) {
                sb.append("；");
            }
            Map<String, Object> q = topQuestions.get(i);
            sb.append("「").append(q.get("question")).append("」（")
                    .append(q.get("count")).append(" 次）");
        }
        if (topQuestions.size() > 3) {
            sb.append(" 等（其余 ").append(topQuestions.size() - 3).append(" 条见高频问题列表）");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keywords = (List<Map<String, Object>>) heat.get("top_keywords");
        if (keywords != null && !keywords.isEmpty()) {
            sb.append("。高频关键词：");
            for (int i = 0; i < Math.min(5, keywords.size()); i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append(keywords.get(i).get("keyword"));
            }
        }
        sb.append("。可作为选品与营销洞察参考（数据只聚合不落明细，不含个人级信息）");
        return sb.toString();
    }

    private Integer castInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
