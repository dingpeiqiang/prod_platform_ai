package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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