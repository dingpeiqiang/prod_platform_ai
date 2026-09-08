package com.sitech.prodai.service.agent.tool.rd;

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
 * 产商品研发 - 品类识别原子工具（智聊链路环节①）。
 * <p>
 * 从话术/显式入参识别产品品类码（显式 product_type 优先，模板 matchers 兜底）。
 * 只做品类识别环节，不做草稿生成（职责拆分：一个工具一个环节）。
 */
@Component
public class RdCategoryResolveTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdCategoryResolveTool.class);

    private final ProductTemplateRegistry templateRegistry;

    public RdCategoryResolveTool(ProductTemplateRegistry templateRegistry) {
        this.templateRegistry = templateRegistry;
    }

    @Override
    public String getName() {
        return "rd_category_resolve";
    }

    @Override
    public String getDescription() {
        return "从用户话术或显式入参识别产品品类（家庭融合/校园/5G 等），供后续草稿生成使用";
    }

    @Override
    public String getLabel() {
        return "品类识别";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    /** 品类识别后的典型业务链：参数抽取（按本品类模板 required_slots 判定缺要素）→ 草稿生成。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("rd_slot_extract", "rd_draft_generate");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("text")
                        .label("配置需求")
                        .description("用户对产商品配置的自然语言描述")
                        .required()
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
                        .label("识别结果").type("string")
                        .description("品类识别结果说明").build(),
                ToolOutputField.builder("category_code", ToolOutputField.Role.BUSINESS_ENTITY_ID)
                        .label("品类编码").type("string")
                        .description("识别出的产品品类码（category_code）").build(),
                ToolOutputField.builder("category_name", ToolOutputField.Role.BUSINESS_ENTITY_NAME)
                        .label("品类名称").type("string")
                        .description("识别出的品类模板名称").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String text = params != null ? String.valueOf(params.getOrDefault("text", "")) : "";
        String productType = params != null ? String.valueOf(params.getOrDefault("product_type", "")).trim() : "";
        log.info("[AgentTool] rd_category_resolve 执行: text={}, productType={}", text, productType);
        if (text == null || text.isBlank() || "null".equals(text)) {
            return ExecutionResult.fail(getName(), "缺少配置需求描述");
        }
        try {
            String category = RdProductTypeSupport.resolve(templateRegistry, productType, text);
            Map<String, Object> out = new LinkedHashMap<>();
            if (category == null || category.isBlank()) {
                out.put("nl_answer", "未能识别产品品类（可显式指定 product_type，或在话术中说明家庭融合/校园/5G 等场景）");
                out.put("category_code", "");
                return ExecutionResult.ok(getName(), out);
            }
            String templateName = templateRegistry.findByCategory(category)
                    .map(t -> String.valueOf(t.getOrDefault("template_name", "")))
                    .orElse("");
            out.put("nl_answer", "已识别产品品类：" + (templateName.isBlank() ? category : templateName + "（" + category + "）"));
            out.put("category_code", category);
            if (!templateName.isBlank()) {
                out.put("category_name", templateName);
            }
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] rd_category_resolve 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "品类识别失败: " + e.getMessage());
        }
    }
}
