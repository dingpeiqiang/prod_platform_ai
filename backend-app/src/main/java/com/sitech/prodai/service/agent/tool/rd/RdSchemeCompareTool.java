package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.service.ProductOntologyService;
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
 * 产商品研发 - 多方案对比工具。
 * <p>
 * 对基础产商品配置草稿应用多组资费/字段补丁，逐案合规 + 粗算收益，输出可解释推荐。
 * 包装 product-ontology/config/compare 后端能力。
 */
@Component
public class RdSchemeCompareTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdSchemeCompareTool.class);

    private final ProductOntologyService productOntologyService;

    public RdSchemeCompareTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "rd_scheme_compare";
    }

    @Override
    public String getDescription() {
        return "对产商品配置的多个候选方案进行对比（资费/字段变更），输出合规与收益推荐";
    }

    @Override
    public String getLabel() {
        return "多方案对比";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("text")
                        .label("对比需求")
                        .description("用户对多方案对比的自然语言描述（可含多个资费档位）")
                        .required()
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("draft")
                        .label("基础草稿")
                        .description("作为对比基准的产商品配置草稿（可为空）")
                        .type("object")
                        .build(),
                ToolParam.builder("patches")
                        .label("候选方案")
                        .description("各候选方案的变更补丁（description + changes）")
                        .type("list")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("对比摘要").type("string")
                        .description("多方案对比结果摘要").build(),
                ToolOutputField.builder("comparisons", ToolOutputField.Role.ITEMS)
                        .label("候选方案").type("list")
                        .description("各候选方案的合规与收益对比明细").build(),
                ToolOutputField.builder("recommended", ToolOutputField.Role.CONCLUSION)
                        .label("推荐方案").type("object")
                        .description("综合合规与收益后的推荐方案").build(),
                ToolOutputField.builder("config", ToolOutputField.Role.OTHER)
                        .label("对比结果").type("object").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String text = params != null ? String.valueOf(params.getOrDefault("text", "")) : "";
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = params != null && params.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) params.get("draft") : null;
        @SuppressWarnings("unchecked")
        List<Object> patches = params != null && params.get("patches") instanceof List<?>
                ? (List<Object>) params.get("patches") : null;

        log.info("[AgentTool] rd_scheme_compare 执行: hasText={}, hasDraft={}, patches={}",
                !text.isBlank(), draft != null, patches == null ? 0 : patches.size());

        Map<String, Object> request = new LinkedHashMap<>();
        if (!text.isBlank()) {
            request.put("text", text);
        }
        if (draft != null) {
            request.put("draft", draft);
        }
        if (patches != null) {
            request.put("patches", patches);
        }
        try {
            Map<String, Object> resp = productOntologyService.compareConfigSchemes(request);
            return ExecutionResult.ok(getName(), normalize(resp));
        } catch (Exception e) {
            log.error("[AgentTool] rd_scheme_compare 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "多方案对比失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resp == null) {
            out.put("nl_answer", "未返回对比结果");
            return out;
        }
        Object comparisons = resp.get("comparisons");
        Object recommended = resp.get("recommended");
        Object explanation = resp.get("explanation");
        int count = comparisons instanceof List<?> l ? l.size() : 0;
        String answer;
        if (explanation != null && !String.valueOf(explanation).isBlank()) {
            answer = String.valueOf(explanation);
        } else if (recommended instanceof Map<?, ?> r && r.get("label") != null) {
            answer = "推荐方案：" + r.get("label") + "（共对比 " + count + " 个候选方案）";
        } else {
            answer = "已对比 " + count + " 个候选方案";
        }
        out.put("nl_answer", answer);
        if (comparisons instanceof List<?>) out.put("comparisons", comparisons);
        if (recommended != null) out.put("recommended", recommended);
        out.put("config", resp);
        return out;
    }
}
