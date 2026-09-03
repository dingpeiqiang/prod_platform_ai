package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SWRL 归因工具：触发 SWRL 归因推理。
 */
@Component
public class SwrlRootCauseTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SwrlRootCauseTool.class);

    private final ProductOntologyService productOntologyService;

    public SwrlRootCauseTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "swrl_root_cause";
    }

    @Override
    public String getDescription() {
        return "触发 SWRL 归因推理，分析业务指标异动的根因";
    }

    @Override
    public String getLabel() {
        return "异动归因";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("ops");
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("offeringId", ToolOutputField.Role.BUSINESS_ENTITY_ID)
                        .label("商品编码").type("string")
                        .description("分析对象商品/套餐编码").build(),
                ToolOutputField.builder("offeringName", ToolOutputField.Role.BUSINESS_ENTITY_NAME)
                        .label("商品/套餐").type("string")
                        .description("分析对象商品/套餐名称").build(),
                ToolOutputField.builder("paths", ToolOutputField.Role.COUNT)
                        .outputKey("pathCount")
                        .label("归因路径数").type("list")
                        .description("命中的归因路径列表").build(),
                ToolOutputField.builder("reasonEngine", ToolOutputField.Role.OTHER)
                        .label("推理引擎").type("string")
                        .description("使用的推理引擎：openllet-swrl / java-rules").build(),
                ToolOutputField.builder("message", ToolOutputField.Role.OTHER)
                        .outputKey("remark")
                        .label("备注").type("string")
                        .description("执行备注信息").build()
        );
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("offering")
                        .label("商品/套餐")
                        .description("分析对象商品/套餐；缺省时从 question 语义解析")
                        .type("string")
                        .source("context")
                        .build(),
                ToolParam.builder("question")
                        .label("归因问题")
                        .description("原始问题（归因文本）")
                        .type("string")
                        .source("question")
                        .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionResult execute(Map<String, Object> params) {
        String offeringId = params != null ? String.valueOf(params.getOrDefault("offering", "")) : "";
        String text = params != null ? String.valueOf(params.getOrDefault("question", "")) : "";

        log.info("[AgentTool] swrl_root_cause 执行: offeringId={}", offeringId);

        try {
            Map<String, Object> result;
            if (offeringId != null && !offeringId.isBlank() && !"null".equals(offeringId)) {
                result = productOntologyService.analyzeRootCause(offeringId, text);
            } else {
                result = productOntologyService.analyzeRootCause(text);
            }
            return ExecutionResult.ok(getName(), result);
        } catch (Exception e) {
            log.error("[AgentTool] swrl_root_cause 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "归因分析失败: " + e.getMessage());
        }
    }
}