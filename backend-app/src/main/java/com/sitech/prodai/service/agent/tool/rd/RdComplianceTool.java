package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.ProductTemplateRegistry;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolOutputField;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产商品研发 - 智检合规校验工具。
 * <p>
 * 对产商品配置草稿执行合规检查（资费/政策/规则），返回校验结果与建议。
 * 包装 product-ontology/config/compliance 后端能力。
 */
@Component
public class RdComplianceTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdComplianceTool.class);

    private final ProductOntologyService productOntologyService;
    private final ProductTemplateRegistry templateRegistry;

    public RdComplianceTool(ProductOntologyService productOntologyService,
                            ProductTemplateRegistry templateRegistry) {
        this.productOntologyService = productOntologyService;
        this.templateRegistry = templateRegistry;
    }

    @Override
    public String getName() {
        return "rd_compliance";
    }

    @Override
    public String getDescription() {
        return "对产商品配置草稿进行合规校验（资费、政策、规则），输出通过/风险/整改建议";
    }

    @Override
    public String getLabel() {
        return "合规校验";
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("draft")
                        .label("配置草稿")
                        .description("待校验的产商品配置草稿")
                        .type("object")
                        .build(),
                ToolParam.builder("offering_id")
                        .label("商品编码")
                        .description("商品/套餐编码（可选）")
                        .type("string")
                        .build(),
                ToolParam.builder("text")
                        .label("校验描述")
                        .description("对需校验配置的自然语言描述（可选）")
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("product_type")
                        .label("产品品类")
                        .description("可选，产品品类码（category_code，如 familyBasePrc）；未提供时由模板 matchers 兜底识别")
                        .type("string")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("校验摘要").type("string")
                        .description("合规校验结果摘要").build(),
                ToolOutputField.builder("draft", ToolOutputField.Role.OTHER)
                        .label("配置草稿").type("object")
                        .description("被校验的完整配置草稿").build(),
                ToolOutputField.builder("compliance_pass", ToolOutputField.Role.OTHER)
                        .label("是否通过").type("boolean")
                        .description("合规校验是否通过").build(),
                ToolOutputField.builder("issues", ToolOutputField.Role.ITEMS)
                        .label("风险明细").type("list").build(),
                ToolOutputField.builder("config", ToolOutputField.Role.OTHER)
                        .label("校验结果").type("object").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = params != null && params.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) params.get("draft") : null;
        String offeringId = params != null ? String.valueOf(params.getOrDefault("offering_id", "")).trim() : "";
        String text = params != null ? String.valueOf(params.getOrDefault("text", "")) : "";
        String productType = params != null ? String.valueOf(params.getOrDefault("product_type", "")).trim() : "";

        boolean hasDraft = draft != null && !draft.isEmpty();
        boolean hasText = !text.isBlank() && !"null".equalsIgnoreCase(text);
        log.info("[AgentTool] rd_compliance 执行: hasDraft={}, offeringId={}, hasText={}, productType={}",
                hasDraft, offeringId, hasText, productType);
        if (!hasDraft && !hasText) {
            return ExecutionResult.fail(getName(), "缺少待校验的配置草稿");
        }
        try {
            String category = RdProductTypeSupport.resolve(templateRegistry, productType, text);
            Map<String, Object> resp = productOntologyService.checkComplianceSmart(
                    offeringId.isEmpty() ? null : offeringId,
                    hasText ? text : null,
                    RdProductTypeSupport.applyToDraft(draft, category));
            return ExecutionResult.ok(getName(), normalize(resp, draft));
        } catch (Exception e) {
            log.error("[AgentTool] rd_compliance 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "合规校验失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> resp, Map<String, Object> draft) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resp == null) {
            out.put("nl_answer", "未返回校验结果");
            return out;
        }
        Object passed = resp.get("compliancePass") != null ? resp.get("compliancePass") : resp.get("passed");
        Object issues = resp.get("issues");
        Object summary = resp.get("summary");
        if (summary == null) summary = resp.get("message");
        Object nl = resp.get("nl_answer");
        String verdict = Boolean.TRUE.equals(passed) ? "合规通过" : "存在风险项";
        String answer;
        if (summary != null && !String.valueOf(summary).isBlank()) {
            answer = String.valueOf(summary);
        } else if (nl != null) {
            answer = String.valueOf(nl);
        } else {
            int issueCount = issues instanceof List<?> l ? l.size() : 0;
            answer = verdict + (issueCount > 0 ? "（" + issueCount + " 项待整改）" : "");
        }
        out.put("nl_answer", answer);
        out.put("compliance_pass", passed != null ? passed : false);
        if (issues instanceof List<?>) out.put("issues", issues);
        if (draft != null) out.put("draft", draft);
        out.put("config", resp);
        return out;
    }
}
