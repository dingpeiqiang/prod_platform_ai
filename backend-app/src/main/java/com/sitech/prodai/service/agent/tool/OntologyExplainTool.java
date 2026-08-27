package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 本体解释工具：解释本体概念。
 */
@Component
public class OntologyExplainTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(OntologyExplainTool.class);

    private final OntologyService ontologyService;

    public OntologyExplainTool(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getName() {
        return "ontology_explain";
    }

    @Override
    public String getDescription() {
        return "解释本体（Ontology）中的概念、类、属性及其关系";
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String concept = params != null ? String.valueOf(params.getOrDefault("concept", "")) : "";

        log.info("[AgentTool] ontology_explain 执行: concept={}", concept);

        try {
            Map<String, Object> result = ontologyService.explain("agent-trace", "business", "agent");
            return ExecutionResult.ok(getName(), result);
        } catch (Exception e) {
            log.error("[AgentTool] ontology_explain 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "本体解释失败: " + e.getMessage());
        }
    }
}