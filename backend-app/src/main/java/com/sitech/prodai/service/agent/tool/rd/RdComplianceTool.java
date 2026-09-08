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

import java.util.ArrayList;
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
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    /** 合规校验后的典型业务链：通过→开单 / 未通过→修改草稿（智聊链路下游环节）。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("rd_workorder_create", "rd_draft_manage");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("draft")
                        .label("配置草稿")
                        .description("待校验的产商品配置草稿：单个草稿 Map（智聊链路）或草稿条目清单（智读链路批量，每条含 draft/issues/compliancePass）")
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
                ToolOutputField.builder("items", ToolOutputField.Role.ITEMS)
                        .label("校验清单").type("list")
                        .description("批量校验时的逐条结果清单（每条含 draft/issues/compliancePass/status），承接上游草稿清单形态").build(),
                ToolOutputField.builder("draft", ToolOutputField.Role.OTHER)
                        .label("配置草稿").type("object")
                        .description("被校验的完整配置草稿（单草稿形态；批量时为首条投影）").build(),
                ToolOutputField.builder("compliance_pass", ToolOutputField.Role.OTHER)
                        .label("是否通过").type("boolean")
                        .description("合规校验是否通过").build(),
                ToolOutputField.builder("issues", ToolOutputField.Role.OTHER)
                        .label("风险明细").type("list").build(),
                ToolOutputField.builder("config", ToolOutputField.Role.OTHER)
                        .label("校验结果").type("object").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        // 入参形态自适应：items 清单（智读批量链路，rd_draft_extract 产出）走逐条校验；
        // 单个草稿 Map（智聊链路）保持既有单草稿校验
        List<Map<String, Object>> batchItems = extractItems(params);
        if (batchItemsPresent(batchItems)) {
            return executeBatch(batchItems);
        }
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

    /** 批量形态：草稿清单逐条重跑合规（规则引擎 R-C），回填结论与问题明细。 */
    private ExecutionResult executeBatch(List<Map<String, Object>> items) {
        log.info("[AgentTool] rd_compliance 批量执行: itemCount={}", items.size());
        try {
            List<Map<String, Object>> outItems = new ArrayList<>();
            int passed = 0;
            for (int idx = 0; idx < items.size(); idx++) {
                Map<String, Object> entry = new LinkedHashMap<>(items.get(idx));
                @SuppressWarnings("unchecked")
                Map<String, Object> draft = entry.get("draft") instanceof Map<?, ?> d
                        ? (Map<String, Object>) d : new LinkedHashMap<>();
                Map<String, Object> resp = productOntologyService.checkComplianceSmart(null, null, draft);
                boolean itemPass = Boolean.TRUE.equals(resp.get("compliancePass"))
                        || Boolean.TRUE.equals(resp.get("passed"));
                entry.put("draft", draft);
                entry.put("issues", resp.get("issues") instanceof List<?> l ? l : List.of());
                entry.put("compliancePass", itemPass);
                entry.put("status", itemPass ? "通过" : "待修正");
                entry.put("index", idx + 1);
                if (itemPass) {
                    passed++;
                }
                outItems.add(entry);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("nl_answer", "已校验 " + outItems.size() + " 条草稿（通过 " + passed
                    + "，待修正 " + (outItems.size() - passed) + "）");
            out.put("items", outItems);
            out.put("total", outItems.size());
            out.put("passedCount", passed);
            out.put("pendingCount", outItems.size() - passed);
            // 首条投影：保持单草稿消费方（旧链路输出键）兼容
            if (!outItems.isEmpty()) {
                out.put("draft", outItems.get(0).get("draft"));
                out.put("compliance_pass", outItems.get(0).get("compliancePass"));
                out.put("issues", outItems.get(0).get("issues"));
            }
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] rd_compliance 批量失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "合规校验失败: " + e.getMessage());
        }
    }

    /** 提取清单形态入参：draft 为 List 且非空时按批量处理（元素为条目映射）。 */
    private static List<Map<String, Object>> extractItems(Map<String, Object> params) {
        if (params == null || !(params.get("draft") instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) m;
                items.add(item);
            }
        }
        return items;
    }

    private static boolean batchItemsPresent(List<Map<String, Object>> items) {
        return items != null && !items.isEmpty();
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
