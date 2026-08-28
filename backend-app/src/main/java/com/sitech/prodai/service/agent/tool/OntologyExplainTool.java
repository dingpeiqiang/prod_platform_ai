package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
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
    public String getLabel() {
        return "概念解释";
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("concept")
                        .label("本体概念")
                        .description("本体概念名")
                        .required()
                        .type("string")
                        .source("question")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("natural_language", ToolOutputField.Role.SUMMARY)
                        .label("解释文案").type("string")
                        .description("本体概念的自然语言解释").build(),
                ToolOutputField.builder("referenced_rules", ToolOutputField.Role.OTHER)
                        .label("引用规则").type("list")
                        .description("解释过程中引用的规则编号").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String concept = params != null ? String.valueOf(params.getOrDefault("concept", "")) : "";

        log.info("[AgentTool] ontology_explain 执行: concept={}", concept);

        try {
            // 以 concept 作为评估轨迹标识，对应本次解释的上下文
            Map<String, Object> result = ontologyService.explain(
                    concept.isBlank() ? "agent-trace" : concept, "business", "agent");
            if (result != null && !concept.isBlank()) {
                result.put("concept", concept);
            }
            return ExecutionResult.ok(getName(), result);
        } catch (Exception e) {
            log.error("[AgentTool] ontology_explain 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "本体解释失败: " + e.getMessage());
        }
    }
}