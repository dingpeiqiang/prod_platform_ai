package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SPARQL 查询工具：自然语言 → SPARQL → 查询 RDF 知识库。
 */
@Component
public class SparqlQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SparqlQueryTool.class);

    private final OntologyService ontologyService;

    public SparqlQueryTool(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getName() {
        return "sparql_query";
    }

    @Override
    public String getDescription() {
        return "将自然语言转化为 SPARQL 查询，检索 RDF 知识库中的事实数据";
    }

    @Override
    public String getLabel() {
        return "数据查询";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("ops");
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("查询摘要").type("string")
                        .description("自然语言查询结果摘要").build(),
                ToolOutputField.builder("entity_ids", ToolOutputField.Role.COUNT)
                        .label("实体数").type("list")
                        .description("命中的实体 ID 列表").build(),
                ToolOutputField.builder("raw_results", ToolOutputField.Role.OTHER)
                        .label("原始结果").type("list")
                        .description("RDF 查询原始记录").build(),
                ToolOutputField.builder("sparql", ToolOutputField.Role.OTHER)
                        .label("SPARQL").type("string")
                        .description("生成的 SPARQL 查询语句").build(),
                ToolOutputField.builder("discovery_method", ToolOutputField.Role.OTHER)
                        .label("发现方式").type("string")
                        .description("实体发现方式：llm / keyword_fallback").build()
        );
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("question")
                        .label("查询语句")
                        .description("自然语言查询语句（NL→SPARQL 入口）")
                        .required()
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("maxEntities")
                        .label("返回上限")
                        .description("返回实体数上限")
                        .type("number")
                        .defaultValue("20")
                        .build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String question = params != null ? String.valueOf(params.getOrDefault("question", "")) : "";
        int maxEntities = 20;
        if (params != null && params.get("maxEntities") instanceof Number n) {
            maxEntities = n.intValue();
        }

        log.info("[AgentTool] sparql_query 执行: question={}", question);

        try {
            Map<String, Object> result = ontologyService.nlDiscoverAndRetrieve(question, maxEntities);
            return ExecutionResult.ok(getName(), result);
        } catch (Exception e) {
            log.error("[AgentTool] sparql_query 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "SPARQL 查询失败: " + e.getMessage());
        }
    }
}