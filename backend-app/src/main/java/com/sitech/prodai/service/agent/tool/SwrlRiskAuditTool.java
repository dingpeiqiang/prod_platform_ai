package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SWRL 风险稽核工具：触发 SWRL 风险稽核。
 */
@Component
public class SwrlRiskAuditTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SwrlRiskAuditTool.class);

    private final ProductOntologyService productOntologyService;

    public SwrlRiskAuditTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "swrl_risk_audit";
    }

    @Override
    public String getDescription() {
        return "触发 SWRL 风险稽核，筛查高风险商品";
    }

    @Override
    public String getLabel() {
        return "风险稽核";
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("total", ToolOutputField.Role.COUNT)
                        .label("风险总数").type("number")
                        .description("命中风险的商品总数").build(),
                ToolOutputField.builder("scannedCount", ToolOutputField.Role.COUNT)
                        .label("筛查数").type("number")
                        .description("本次筛查的商品数").build(),
                ToolOutputField.builder("highCount", ToolOutputField.Role.COUNT)
                        .label("高风险").type("number")
                        .description("高风险商品数").build(),
                ToolOutputField.builder("mediumCount", ToolOutputField.Role.COUNT)
                        .label("中风险").type("number")
                        .description("中风险商品数").build(),
                ToolOutputField.builder("suggestDelistCount", ToolOutputField.Role.COUNT)
                        .label("建议下架").type("number")
                        .description("建议下架的商品数").build(),
                ToolOutputField.builder("reasonEngine", ToolOutputField.Role.OTHER)
                        .label("推理引擎").type("string")
                        .description("使用的推理引擎").build(),
                ToolOutputField.builder("items", ToolOutputField.Role.ITEMS)
                        .label("风险商品明细").type("list")
                        .description("命中风险的商品明细（名称/等级/分数/处置建议）").build()
        );
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("offeringIds")
                        .label("商品范围")
                        .description("限定风险筛查范围；缺省全量筛查")
                        .type("list")
                        .source("context")
                        .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionResult execute(Map<String, Object> params) {
        log.info("[AgentTool] swrl_risk_audit 执行");

        try {
            Object offeringIdsRaw = params != null ? params.get("offeringIds") : null;
            List<String> offeringIds = null;
            if (offeringIdsRaw instanceof List<?> list) {
                offeringIds = list.stream().map(String::valueOf).toList();
            }
            Map<String, Object> result = productOntologyService.auditRisks(offeringIds);
            return ExecutionResult.ok(getName(), result);
        } catch (Exception e) {
            log.error("[AgentTool] swrl_risk_audit 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "风险稽核失败: " + e.getMessage());
        }
    }
}