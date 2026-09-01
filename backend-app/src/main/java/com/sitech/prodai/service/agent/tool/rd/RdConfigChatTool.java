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
 * 产商品研发 - 智聊对话配置工具。
 * <p>
 * 将用户自然语言需求翻译为产商品配置草稿（包装 product-ontology/config/chat 后端能力）。
 * 支持 §9.4 product_type 入参：显式品类优先，未识别由模板 matchers 兜底。
 */
@Component
public class RdConfigChatTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdConfigChatTool.class);

    private final ProductOntologyService productOntologyService;
    private final ProductTemplateRegistry templateRegistry;

    public RdConfigChatTool(ProductOntologyService productOntologyService,
                            ProductTemplateRegistry templateRegistry) {
        this.productOntologyService = productOntologyService;
        this.templateRegistry = templateRegistry;
    }

    @Override
    public String getName() {
        return "rd_config_chat";
    }

    @Override
    public String getDescription() {
        return "根据用户自然语言，生成产商品对话配置草稿（业务场景、套餐、资费等字段）";
    }

    @Override
    public String getLabel() {
        return "对话配置生成";
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("text")
                        .label("配置需求")
                        .description("用户对产商品配置的自然语言描述（对话配置入口）")
                        .required()
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("draft")
                        .label("已有草稿")
                        .description("待补充/润色的已有配置草稿（可为空）")
                        .type("object")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("配置结果摘要").type("string")
                        .description("本次生成的配置结果摘要").build(),
                ToolOutputField.builder("draft", ToolOutputField.Role.OTHER)
                        .label("配置草稿").type("object")
                        .description("本次生成的完整配置草稿").build(),
                ToolOutputField.builder("config", ToolOutputField.Role.OTHER)
                        .label("配置内容").type("object").build(),
                ToolOutputField.builder("workOrder", ToolOutputField.Role.OTHER)
                        .label("配置工单").type("object")
                        .description("草稿生成即创建的配置工单（绑定当前会话）").build(),
                ToolOutputField.builder("workOrderId", ToolOutputField.Role.BUSINESS_ENTITY_ID)
                        .label("配置工单号").type("string")
                        .description("本次生成的配置工单号（WO 开头），会话内后续提交/复制/删除凭此定位").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String text = params != null ? String.valueOf(params.getOrDefault("text", "")) : "";
        String productType = params != null ? String.valueOf(params.getOrDefault("product_type", "")).trim() : "";
        String sessionId = params != null ? String.valueOf(params.getOrDefault("session_id", "")).trim() : "";
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = params != null && params.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) params.get("draft") : null;

        log.info("[AgentTool] rd_config_chat 执行: text={}, productType={}, sessionId={}",
                text, productType, sessionId);
        if (text == null || text.isBlank()) {
            return ExecutionResult.fail(getName(), "缺少配置需求描述");
        }
        try {
            String category = RdProductTypeSupport.resolve(templateRegistry, productType, text);
            Map<String, Object> resp = productOntologyService.chatConfigure(
                    text, RdProductTypeSupport.applyToDraft(draft, category));
            ensureDraftName(resp, text);
            // 补名可能修复 R-C06（资费名称缺失）：对补名后的草稿重跑稽核，刷新结论再开单，
            // 避免工单展示过期稽核结果、复制后重跑稽核出现结论不一致
            if (resp.get("draft") instanceof Map<?, ?> rawNamed) {
                @SuppressWarnings("unchecked")
                Map<String, Object> namedDraft = (Map<String, Object>) rawNamed;
                Map<String, Object> refreshed = productOntologyService.checkCompliance(namedDraft);
                resp.put("compliancePass", refreshed.get("compliancePass"));
                resp.put("issues", refreshed.get("issues"));
                resp.put("canSubmit", refreshed.get("canSubmit"));
            }
            Map<String, Object> out = normalize(resp);

            // 草稿生成即落库 + 即开单：工单 payload 关联 draftId，后续删除/复制仅凭工单号反查草稿
            if (!sessionId.isBlank()) {
                persistDraft(out, resp, sessionId);
                attachDraftWorkOrder(out, resp, sessionId);
            }
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] rd_config_chat 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "配置生成失败: " + e.getMessage());
        }
    }

    /** 草稿落库（pd_ai_ontology_instance）：draftId/clientId 写回 output.draft，供工单卡删除/复制操作使用。 */
    @SuppressWarnings("unchecked")
    private void persistDraft(Map<String, Object> out, Map<String, Object> resp, String sessionId) {
        try {
            if (!(out.get("draft") instanceof Map<?, ?> rawDraft) || ((Map<?, ?>) rawDraft).isEmpty()) {
                return;
            }
            Map<String, Object> draft = new LinkedHashMap<>((Map<String, Object>) rawDraft);
            Map<String, Object> saveReq = new LinkedHashMap<>();
            saveReq.put("draft", draft);
            saveReq.put("sessionId", sessionId);
            Map<String, Object> saved = productOntologyService.saveConfigDraft(saveReq);
            if (Boolean.TRUE.equals(saved.get("success"))) {
                draft.put("draftId", saved.get("draftId"));
                draft.put("clientId", saved.get("clientId"));
                out.put("draft", draft);
                out.put("draft_id", saved.get("draftId"));
                out.put("client_id", saved.get("clientId"));
            }
        } catch (Exception e) {
            log.warn("[AgentTool] rd_config_chat 草稿落库失败（不影响配置结果）: {}", e.getMessage());
        }
    }

    /**
     * 草稿命名兜底：derive 引擎不产出 offeringName，缺名时按「模板名+资费特征」拼名
     * （如「家庭基础套餐·158元·500M」），避免后续复制副本退化为「配置草稿(副本)」。
     */
    private void ensureDraftName(Map<String, Object> resp, String text) {
        if (resp == null || !(resp.get("draft") instanceof Map<?, ?> rawDraft)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = (Map<String, Object>) rawDraft;
        String name = str(firstNonEmpty(draft.get("offeringName"), draft.get("offerName")));
        if (!name.isBlank()) {
            return;
        }
        String templateName = templateRegistry.findByCategory(str(draft.get("categoryCode")))
                .map(t -> str(t.get("template_name")))
                .filter(s -> !s.isBlank())
                .orElse("");
        if (templateName.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder(templateName);
        String fee = str(firstNonEmpty(draft.get("monthlyFee"), draft.get("fixedFeeAmount")));
        if (!fee.isEmpty() && !"null".equals(fee)) {
            sb.append('·').append(fee).append("元");
        }
        String bandwidth = str(firstNonEmpty(draft.get("bandwidth"), draft.get("speed")));
        if (!bandwidth.isEmpty() && !"null".equals(bandwidth)) {
            sb.append('·').append(bandwidth);
        }
        String resolvedName = sb.toString();
        draft.put("offeringName", resolvedName);
        if (draft.containsKey("offerName")) {
            draft.put("offerName", resolvedName);
        }
        log.info("[AgentTool] rd_config_chat 草稿补名: {} (text={})", resolvedName, text);
    }

    /** 草稿产出后创建配置工单（source=rd_config_draft）；开单失败不阻断配置结果返回。 */
    @SuppressWarnings("unchecked")
    private void attachDraftWorkOrder(Map<String, Object> out, Map<String, Object> resp, String sessionId) {
        try {
            Map<String, Object> draft = resp != null && resp.get("draft") instanceof Map<?, ?> d
                    ? (Map<String, Object>) d : Map.of();
            String offeringName = str(firstNonEmpty(draft.get("offeringName"), draft.get("offerName")));
            String monthlyFee = str(firstNonEmpty(draft.get("monthlyFee"), draft.get("fixedFeeAmount")));
            String scenario = str(firstNonEmpty(draft.get("bizScenario"), draft.get("scenario")));

            Map<String, Object> woReq = new LinkedHashMap<>();
            woReq.put("offeringId", str(firstNonEmpty(draft.get("offeringId"), draft.get("offerId"))));
            woReq.put("offeringName", offeringName);
            woReq.put("source", "rd_config_draft");
            woReq.put("sessionId", sessionId);
            // 工单与草稿强关联：删除/复制操作仅凭 work_order_id 反查
            woReq.put("draftId", str(firstNonEmpty(out.get("draft_id"), draft.get("draftId"), draft.get("draft_id"))));
            // 稽核结果随单展示：工单卡直接透出草稿合规结论与问题项
            woReq.put("compliancePass", resp.get("compliancePass"));
            woReq.put("complianceIssues", resp.get("issues"));
            woReq.put("title", offeringName.isEmpty() ? "产商品配置工单" : offeringName + "配置工单");
            woReq.put("summary", "对话配置草稿已生成：月费=" + (monthlyFee.isEmpty() ? "-" : monthlyFee)
                    + "，场景=" + (scenario.isEmpty() ? "-" : scenario));
            woReq.put("actions", List.of(
                    "核对配置草稿字段完整性",
                    "合规校验后提交",
                    "提交通过后发布上架"
            ));
            Map<String, Object> woBody = productOntologyService.createWorkOrder(woReq);
            if (woBody != null && woBody.get("workOrder") instanceof Map<?, ?> wo) {
                out.put("workOrder", wo);
                out.put("workOrderId", ((Map<String, Object>) wo).get("workOrderId"));
            }
        } catch (Exception e) {
            log.warn("[AgentTool] rd_config_chat 草稿开单失败（不影响配置结果）: {}", e.getMessage());
        }
    }

    /** 取首个非空字符串。 */
    private Object firstNonEmpty(Object... values) {
        for (Object v : values) {
            if (v != null && !String.valueOf(v).isBlank() && !"null".equals(String.valueOf(v))) {
                return v;
            }
        }
        return "";
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** 将 service 响应规范化为工具 output 契约（抽取摘要 + 保留明细）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resp == null) {
            out.put("nl_answer", "未返回配置结果");
            return out;
        }
        Object draft = resp.get("draft");
        Object summary = resp.get("summary");
        if (summary == null) summary = resp.get("message");
        if (summary == null && draft instanceof Map<?, ?> d) {
            Object name = d.get("offeringName");
            if (name == null) name = d.get("name");
            summary = name != null ? "已生成配置草稿：" + name : "已生成配置草稿";
        }
        if (summary == null) summary = resp.get("nl_answer");
        out.put("nl_answer", summary != null ? String.valueOf(summary) : "已生成配置草稿");
        if (draft != null) out.put("draft", draft);
        out.put("config", resp);
        return out;
    }
}
