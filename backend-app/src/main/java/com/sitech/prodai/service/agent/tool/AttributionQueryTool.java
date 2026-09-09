package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 归因评估审计查询工具（方案 §6-B5，自 ontology_explain 拆分）。
 * <p>
 * 承接原 OntologyService.explain 的审计流水语义：按评估轨迹 ID（traceId）
 * 查询归因评估记录（评估步骤 + 引用规则），供"这次评估为什么得出这个结论"
 * 的追溯问询使用。与概念解释（ontology_explain → TBox）职责分离，
 * 修正 §5.2 语义错位：概念问不再返回评估记录。
 * <p>
 * 数据源：OntologyService 内存审计账（appendAudit 写入、explain 读取，同源同口径）。
 */
@Component
public class AttributionQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(AttributionQueryTool.class);

    private final OntologyService ontologyService;

    public AttributionQueryTool(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getName() {
        return "attribution_query";
    }

    @Override
    public String getDescription() {
        return "按评估轨迹 ID 查询归因评估审计记录：评估步骤、命中规则编号、评估结论说明。"
                + "追溯「这次评估/归因为什么得出这个结论」（如「查一下这次归因的评估流水」）时使用；"
                + "解释业务概念请用 ontology_explain，解释规则编号语义请用 rule_explain";
    }

    @Override
    public String getLabel() {
        return "评估流水查询";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("ops", "query");
    }

    /** 拿到评估流水后的典型业务链：解释命中规则语义 / 回到数据查询复核。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("rule_explain", "sparql_query");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("trace_id")
                        .label("评估轨迹ID")
                        .description("归因评估轨迹标识（评估执行时生成的 traceId；缺省查最近一次 agent-trace）")
                        .type("string")
                        .defaultValue("agent-trace")
                        .source("context")
                        .build(),
                ToolParam.builder("audience")
                        .label("解释视角")
                        .description("解释文案视角（business=业务 / audit=明细流水，默认 business）")
                        .type("string")
                        .defaultValue("business")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("natural_language", ToolOutputField.Role.SUMMARY)
                        .label("评估说明").type("string")
                        .description("评估结论说明（按视角生成）").build(),
                ToolOutputField.builder("trace_id", ToolOutputField.Role.OTHER)
                        .label("轨迹ID").type("string")
                        .description("本次查询的评估轨迹标识").build(),
                ToolOutputField.builder("referenced_rules", ToolOutputField.Role.ITEMS)
                        .label("引用规则").type("list")
                        .description("评估过程中引用的规则编号清单").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String traceId = params != null && !str(params.get("trace_id")).isBlank()
                ? str(params.get("trace_id")) : "agent-trace";
        String audience = params != null && !str(params.get("audience")).isBlank()
                ? str(params.get("audience")) : "business";
        log.info("[AgentTool] attribution_query 执行: traceId={}, audience={}", traceId, audience);

        try {
            Map<String, Object> result = ontologyService.explain(traceId, audience, "agent");
            if (result == null) {
                return ExecutionResult.fail(getName(), "评估流水查询失败（服务未返回结果）");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("trace_id", traceId);
            out.put("natural_language", result.get("natural_language"));
            Object rules = result.get("referenced_rules");
            out.put("referenced_rules", rules instanceof List<?> list ? list : new ArrayList<>());
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] attribution_query 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "评估流水查询失败: " + e.getMessage());
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
