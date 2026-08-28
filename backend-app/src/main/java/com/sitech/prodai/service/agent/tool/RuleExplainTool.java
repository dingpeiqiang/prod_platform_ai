package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.OpsRulesService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则解释工具：解释规则含义。
 */
@Component
public class RuleExplainTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RuleExplainTool.class);

    private final OpsRulesService opsRulesService;

    public RuleExplainTool(OpsRulesService opsRulesService) {
        this.opsRulesService = opsRulesService;
    }

    @Override
    public String getName() {
        return "rule_explain";
    }

    @Override
    public String getDescription() {
        return "解释业务规则的含义和用途";
    }

    @Override
    public String getLabel() {
        return "规则解释";
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("ruleId", ToolOutputField.Role.OTHER)
                        .label("规则编号").type("string")
                        .description("规则编号，如 R-A01").build(),
                ToolOutputField.builder("label", ToolOutputField.Role.SUMMARY)
                        .label("规则说明").type("string")
                        .description("规则的业务解释文案").build()
        );
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("ruleId")
                        .label("规则编号")
                        .description("规则编号，如 R-A01")
                        .required()
                        .type("string")
                        .format("R-[A-Z]+-?\\d*")
                        .source("question")
                        .build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String ruleId = params != null ? String.valueOf(params.getOrDefault("ruleId", "")) : "";

        log.info("[AgentTool] rule_explain 执行: ruleId={}", ruleId);

        try {
            String label = opsRulesService.formatRuleLabel(ruleId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ruleId", ruleId);
            data.put("label", label);
            return ExecutionResult.ok(getName(), data);
        } catch (Exception e) {
            log.error("[AgentTool] rule_explain 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "规则解释失败: " + e.getMessage());
        }
    }
}