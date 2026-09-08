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
 * 产商品研发 - 草稿生成原子工具（智聊链路环节②）。
 * <p>
 * 将用户自然语言需求翻译为产商品配置草稿（chatConfigure：槽位抽取→模板 derive→报文投影），
 * 补名兜底 + 合规结论刷新。只做草稿生成环节，不做落库开单（职责拆分：一个工具一个环节）。
 */
@Component
public class RdDraftGenerateTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdDraftGenerateTool.class);

    private final ProductOntologyService productOntologyService;
    private final ProductTemplateRegistry templateRegistry;

    public RdDraftGenerateTool(ProductOntologyService productOntologyService,
                               ProductTemplateRegistry templateRegistry) {
        this.productOntologyService = productOntologyService;
        this.templateRegistry = templateRegistry;
    }

    @Override
    public String getName() {
        return "rd_draft_generate";
    }

    @Override
    public String getDescription() {
        return "根据用户自然语言，生成产商品对话配置草稿（业务场景、套餐、资费等字段），不做落库开单";
    }

    @Override
    public String getLabel() {
        return "草稿生成";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    /** 草稿生成后的典型业务链：合规校验 → 开单（智聊链路下游环节）。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("rd_compliance", "rd_workorder_create");
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
                ToolParam.builder("category_code")
                        .label("品类编码")
                        .description("上一步品类识别的产出（可选，草稿生成内部同样会兜底识别）")
                        .type("string")
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
                ToolOutputField.builder("draftOfferingName", ToolOutputField.Role.OTHER)
                        .label("草稿名称").outputKey("offeringName").type("string")
                        .description("生成的配置草稿名称").build(),
                ToolOutputField.builder("draftMonthlyFee", ToolOutputField.Role.OTHER)
                        .label("月费").outputKey("monthlyFee").type("string")
                        .description("草稿月费金额（元）").build(),
                ToolOutputField.builder("draftIncludeBroadband", ToolOutputField.Role.OTHER)
                        .label("宽带").outputKey("includeBroadband").type("string")
                        .description("草稿包含的宽带速率").build(),
                ToolOutputField.builder("draftTargetUser", ToolOutputField.Role.OTHER)
                        .label("目标客群").outputKey("targetUser").type("string")
                        .description("草稿面向的目标客群").build(),
                ToolOutputField.builder("draftChannelScope", ToolOutputField.Role.OTHER)
                        .label("销售渠道").outputKey("channelScope").type("string")
                        .description("草稿配置的销售渠道范围").build(),
                ToolOutputField.builder("draftBizScenario", ToolOutputField.Role.OTHER)
                        .label("业务场景").outputKey("bizScenario").type("string")
                        .description("草稿归属的业务场景").build(),
                ToolOutputField.builder("draft", ToolOutputField.Role.OTHER)
                        .label("配置草稿").type("object")
                        .description("本次生成的完整配置草稿").build(),
                ToolOutputField.builder("compliancePass", ToolOutputField.Role.OTHER)
                        .label("合规结论").type("boolean")
                        .description("生成后合规校验结论（补名后重跑刷新）").build(),
                ToolOutputField.builder("issues", ToolOutputField.Role.ITEMS)
                        .label("风险明细").type("list").build(),
                ToolOutputField.builder("config", ToolOutputField.Role.OTHER)
                        .label("配置内容").type("object").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String text = params != null ? String.valueOf(params.getOrDefault("text", "")) : "";
        String categoryCode = params != null ? String.valueOf(params.getOrDefault("category_code", "")).trim() : "";
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = params != null && params.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) params.get("draft") : null;

        log.info("[AgentTool] rd_draft_generate 执行: text={}, categoryCode={}", text, categoryCode);
        if (text == null || text.isBlank() || "null".equals(text)) {
            return ExecutionResult.fail(getName(), "缺少配置需求描述");
        }
        try {
            Map<String, Object> resp = productOntologyService.chatConfigure(text, draft);
            ensureDraftName(resp, text);
            // 补名可能修复 R-C06（资费名称缺失）：对补名后的草稿重跑稽核，刷新结论
            // （落库开单环节在 rd_workorder_create，工单将展示刷新后的稽核结果）
            if (resp.get("draft") instanceof Map<?, ?> rawNamed) {
                @SuppressWarnings("unchecked")
                Map<String, Object> namedDraft = (Map<String, Object>) rawNamed;
                Map<String, Object> refreshed = productOntologyService.checkCompliance(namedDraft);
                resp.put("compliancePass", refreshed.get("compliancePass"));
                resp.put("issues", refreshed.get("issues"));
                resp.put("canSubmit", refreshed.get("canSubmit"));
            }
            Map<String, Object> out = normalize(resp);
            if (!categoryCode.isBlank() && !"null".equals(categoryCode)) {
                out.put("category_code", categoryCode);
            }
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] rd_draft_generate 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "配置生成失败: " + e.getMessage());
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
        log.info("[AgentTool] rd_draft_generate 草稿补名: {} (text={})", resolvedName, text);
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

    /** 将 service 响应规范化为工具 output 契约（抽取摘要 + 草稿要素 + 保留明细）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resp == null) {
            out.put("nl_answer", "未返回配置结果");
            return out;
        }
        Object draft = resp.get("draft");
        Map<String, Object> d = draft instanceof Map<?, ?> dm ? (Map<String, Object>) dm : Map.of();
        String name = str(firstNonEmpty(d.get("offeringName"), d.get("offerName")));
        String fee = str(firstNonEmpty(d.get("monthlyFee"), d.get("fixedFeeAmount")));
        String broadband = str(firstNonEmpty(d.get("includeBroadband"), d.get("downstreamBandwidth")));
        String targetUser = str(d.get("targetUser"));
        String channel = str(firstNonEmpty(d.get("channelScope"),
                ((Map<String, Object>) (d.get("releaseScope") instanceof Map<?, ?> rs ? rs : Map.of()))
                        .get("channelScope")));
        String scenario = str(firstNonEmpty(d.get("bizScenario"), d.get("scenario")));

        // 输出摘要 = 草稿要素明细（名称/月费/宽带/客群/渠道），业务人员据此核对数据流
        StringBuilder summary = new StringBuilder();
        if (!name.isBlank()) {
            summary.append("已生成配置草稿「").append(name).append("」");
        } else {
            summary.append("已生成配置草稿");
        }
        List<String> elements = new ArrayList<>();
        if (!fee.isBlank() && !"null".equals(fee)) {
            elements.add("月费 " + fee.replaceAll("\\.0$", "") + " 元");
        }
        if (!broadband.isBlank() && !"null".equals(broadband)) {
            elements.add("含 " + broadband + " 宽带");
        }
        if (!targetUser.isBlank() && !"null".equals(targetUser)) {
            elements.add("面向" + targetUser + "客户");
        }
        if (!channel.isBlank() && !"null".equals(channel)) {
            elements.add(channel + "销售");
        }
        if (!elements.isEmpty()) {
            summary.append("：").append(String.join("，", elements));
        }
        out.put("nl_answer", summary.toString());

        // 草稿要素平铺下发：前端思考时间线「输出」行逐项展示（数据流具体化）
        putIfPresent(out, "draftOfferingName", name);
        putIfPresent(out, "draftMonthlyFee", fee);
        putIfPresent(out, "draftIncludeBroadband", broadband);
        putIfPresent(out, "draftTargetUser", targetUser);
        putIfPresent(out, "draftChannelScope", channel);
        putIfPresent(out, "draftBizScenario", scenario);

        if (draft != null) out.put("draft", draft);
        // 合规结论平铺下发：下游开单环节（rd_workorder_create）经 result: 引用承接挂单展示
        if (resp.get("compliancePass") != null) {
            out.put("compliancePass", resp.get("compliancePass"));
        }
        if (resp.get("issues") instanceof List<?> issueList && !issueList.isEmpty()) {
            out.put("issues", issueList);
        }
        out.put("config", resp);
        return out;
    }

    /** 值非空且非 "null" 时放入 out（key 固定为草稿要素契约键）。 */
    private void putIfPresent(Map<String, Object> out, String key, String value) {
        if (value != null && !value.isBlank() && !"null".equals(value)) {
            out.put(key, value);
        }
    }
}
