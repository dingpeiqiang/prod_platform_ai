package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.service.ProductOntologyService;
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
 * 产商品研发 - 批量落库开单原子工具（智读链路环节④ / 智聊链路环节④）。
 * <p>
 * 入参 items 统一承载待开单草稿（形态自适应）：草稿清单（智读文档批量导入链路，
 * rd_draft_extract 产出）逐条开单；单个草稿 Map（智聊对话配置链路，rd_compliance 承接
 * rd_draft_generate 产出）自动包装为单条清单走同一落库开单流程（source 按清单长度区分：
 * 批量=rd_file_parse、单条=rd_config_chat，绑定当前会话）。
 * <p>
 * 合规通过草稿开「待处理」单；合规未通过（待修正）草稿同样开单，
 * 稽核结论与问题规则随单展示。单条失败不阻断其余开单。
 */
@Component
public class RdWorkorderCreateTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdWorkorderCreateTool.class);

    private final ProductOntologyService productOntologyService;

    public RdWorkorderCreateTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "rd_workorder_create";
    }

    @Override
    public String getDescription() {
        return "把配置草稿落库并创建配置工单（合规通过开待处理单，未通过开带问题标签的待修正单，绑定当前会话）；入参为草稿清单或单个草稿（自动归一为清单）";
    }

    @Override
    public String getLabel() {
        return "批量落库开单";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    /** 开单后的典型业务链：管理工单关联草稿（修改/提交），链路终点不再原地重复开单。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("rd_draft_manage");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("items")
                        .label("待开单草稿")
                        .description("待落库开单的配置草稿：清单（智读链路批量）或单个草稿 Map（智聊链路，内部自动包装为单条清单）；每条结构 {draft, compliancePass, issues, sourceExcerpt}")
                        .type("list")
                        .build(),
                ToolParam.builder("compliance_pass")
                        .label("合规结论")
                        .description("上一步合规校验结论（可选，随工单展示；智聊链路从合规环节承接，作为条目未自带结论时的兜底）")
                        .type("boolean")
                        .build(),
                ToolParam.builder("issues")
                        .label("风险明细")
                        .description("上一步合规校验的问题规则清单（可选，随待修正工单展示；作为条目未自带问题时的兜底）")
                        .type("list")
                        .build(),
                ToolParam.builder("session_id")
                        .label("会话标识")
                        .description("当前会话 ID（工单绑定会话）")
                        .type("string")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("开单摘要").type("string")
                        .description("本次批量落库开单的结果摘要").build(),
                ToolOutputField.builder("workOrders", ToolOutputField.Role.ITEMS)
                        .label("配置工单").type("list")
                        .description("创建的配置工单清单").build(),
                ToolOutputField.builder("workOrderCount", ToolOutputField.Role.COUNT)
                        .label("工单数量").type("number").build(),
                ToolOutputField.builder("workOrderFailures", ToolOutputField.Role.OTHER)
                        .label("失败明细").type("list")
                        .description("落库/开单失败的条目及原因").build(),
                ToolOutputField.builder("items", ToolOutputField.Role.OTHER)
                        .label("草稿清单").type("list")
                        .description("回填 draftId/workOrderId 后的草稿清单").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String sessionId = params != null ? String.valueOf(params.getOrDefault("session_id", "")).trim() : "";
        Object itemsObj = params != null ? params.get("items") : null;
        boolean compliancePass = Boolean.TRUE.equals(params != null ? params.get("compliance_pass") : null);
        Object issuesObj = params != null ? params.get("issues") : null;
        log.info("[AgentTool] rd_workorder_create 执行: itemsType={}, sessionId={}",
                itemsObj == null ? "null" : itemsObj.getClass().getSimpleName(), sessionId);
        if (sessionId.isBlank()) {
            return ExecutionResult.fail(getName(), "缺少会话标识（工单需绑定会话）");
        }
        // 入参归一：items 形态自适应——单个草稿 Map（智聊链路）包装为单条清单，
        // 与草稿清单（智读链路）同走批量流程；条目未自带合规结论时以顶层入参兜底
        List<?> rawItems;
        if (itemsObj instanceof List<?> list && !list.isEmpty()) {
            rawItems = list;
        } else if (itemsObj instanceof Map<?, ?> draft && !draft.isEmpty()) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("index", 1);
            wrapped.put("draft", draft);
            wrapped.put("compliancePass", params.get("compliance_pass"));
            wrapped.put("issues", issuesObj);
            rawItems = List.of(wrapped);
        } else {
            return ExecutionResult.fail(getName(), "缺少待开单草稿（需上一步套餐抽取或草稿生成产出）");
        }
        boolean single = rawItems.size() == 1;
        try {
            List<Map<String, Object>> workOrders = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            List<Map<String, Object>> items = new ArrayList<>();
            int idx = 0;
            for (Object o : rawItems) {
                idx++;
                if (!(o instanceof Map<?, ?> rawItem)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) rawItem);
                if (!(item.get("draft") instanceof Map<?, ?> rawDraft) || ((Map<?, ?>) rawDraft).isEmpty()) {
                    // 静默跳过 → 显式失败：用户能在摘要中看到该条未开单及原因
                    failures.add("#" + idx + ": 草稿为空（未抽到有效配置字段）");
                    continue;
                }
                boolean pass = item.get("compliancePass") != null
                        ? Boolean.TRUE.equals(item.get("compliancePass"))
                        : compliancePass;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> draft = new LinkedHashMap<>((Map<String, Object>) rawDraft);
                    Map<String, Object> saveReq = new LinkedHashMap<>();
                    saveReq.put("draft", draft);
                    saveReq.put("sessionId", sessionId);
                    saveReq.put("compliancePass", pass);
                    Map<String, Object> saved = productOntologyService.saveConfigDraft(saveReq);
                    if (!Boolean.TRUE.equals(saved.get("success"))) {
                        failures.add("#" + idx + ": " + saved.getOrDefault("message", "草稿落库失败"));
                        continue;
                    }
                    String draftId = String.valueOf(saved.get("draftId"));

                    Map<String, Object> wo = createItemWorkOrder(item, draft, draftId, sessionId, single);
                    if (wo != null) {
                        workOrders.add(wo);
                        // 草稿落库标识随 item 回传：前端批次卡/草稿表单可凭 draftId 关联工单
                        item.put("draftId", saved.get("draftId"));
                        item.put("clientId", saved.get("clientId"));
                        item.put("workOrderId", wo.get("workOrderId"));
                    } else {
                        failures.add("#" + idx + ": 工单创建失败");
                    }
                } catch (Exception e) {
                    log.warn("[AgentTool] rd_workorder_create #{} 落库/开单失败（不阻断其余条目）: {}", idx, e.getMessage());
                    failures.add("#" + idx + ": " + e.getMessage());
                }
                items.add(item);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            String summary = single
                    ? "已创建 " + workOrders.size() + " 个配置工单"
                    : "已批量创建 " + workOrders.size() + " 个配置工单";
            if (!failures.isEmpty()) {
                summary += "（失败 " + failures.size() + " 条）";
            }
            out.put("nl_answer", summary);
            out.put("workOrders", workOrders);
            out.put("workOrderCount", workOrders.size());
            out.put("items", items);
            if (!failures.isEmpty()) {
                out.put("workOrderFailures", failures);
            }
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] rd_workorder_create 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "批量开单失败: " + e.getMessage());
        }
    }

    /**
     * 单条草稿开配置工单：payload 关联 draftId + 稽核结论随单展示。
     * 合规未通过的待修正草稿：标题带「待修正」标记，摘要给出问题规则与修正指引，动作改为修正闭环。
     * 来源标识按清单长度区分：批量清单（智读）=rd_file_parse；单条（智聊）=rd_config_chat。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createItemWorkOrder(Map<String, Object> item, Map<String, Object> draft,
                                                    String draftId, String sessionId, boolean single) {
        boolean pass = Boolean.TRUE.equals(item.get("compliancePass"));
        String offeringName = str(firstNonEmpty(draft.get("offeringName"), draft.get("offerName")));
        String offeringId = str(firstNonEmpty(draft.get("offeringId"), draft.get("offerId")));
        String monthlyFee = str(firstNonEmpty(draft.get("monthlyFee"), draft.get("fixedFeeAmount")));
        String scenario = str(firstNonEmpty(draft.get("bizScenario"), draft.get("scenario")));
        String excerpt = str(item.get("sourceExcerpt"));
        String sourcePrefix = single ? "对话配置" : "文档解析";

        Map<String, Object> woReq = new LinkedHashMap<>();
        woReq.put("offeringId", offeringId);
        woReq.put("offeringName", offeringName);
        woReq.put("source", single ? "rd_config_chat" : "rd_file_parse");
        woReq.put("sessionId", sessionId);
        woReq.put("draftId", draftId);
        woReq.put("compliancePass", pass);
        woReq.put("complianceIssues", item.get("issues"));
        woReq.put("title", offeringName.isEmpty()
                ? (pass ? sourcePrefix + "配置工单" : sourcePrefix + "待修正工单")
                : offeringName + (pass ? "配置工单" : "配置工单（待修正）"));
        woReq.put("summary", buildWorkOrderSummary(item, pass, monthlyFee, scenario, excerpt, sourcePrefix));
        woReq.put("actions", pass
                ? List.of(
                        "核对配置草稿字段完整性",
                        "合规校验后提交",
                        "提交通过后发布上架")
                : List.of(
                        "按稽核问题修正草稿字段",
                        "修正后重跑合规校验",
                        "合规通过后提交入库"));
        try {
            Map<String, Object> woBody = productOntologyService.createWorkOrder(woReq);
            return woBody != null && woBody.get("workOrder") instanceof Map<?, ?> wo
                    ? (Map<String, Object>) wo : null;
        } catch (Exception e) {
            log.warn("[AgentTool] rd_workorder_create 开单失败（draftId={}）: {}", draftId, e.getMessage());
            return null;
        }
    }

    /** 工单摘要：合规通过列要素；未通过列问题规则并附修正指引。来源前缀区分智读/智聊链路。 */
    private String buildWorkOrderSummary(Map<String, Object> item, boolean pass,
                                         String monthlyFee, String scenario, String excerpt, String sourcePrefix) {
        StringBuilder sb = new StringBuilder();
        if (pass) {
            sb.append(sourcePrefix).append("草稿已生成：");
        } else {
            sb.append(sourcePrefix).append("草稿待修正（合规未通过）：");
        }
        sb.append("月费=").append(monthlyFee.isEmpty() ? "-" : monthlyFee)
                .append("，场景=").append(scenario.isEmpty() ? "-" : scenario);
        if (excerpt != null && !excerpt.isBlank()) {
            sb.append("，原文摘录=").append(truncate(excerpt, 60));
        }
        if (!pass) {
            List<String> ruleIds = new ArrayList<>();
            if (item.get("issues") instanceof List<?> issues) {
                for (Object o : issues) {
                    if (o instanceof Map<?, ?> issue) {
                        String ruleId = str(firstNonEmpty(issue.get("ruleId"), issue.get("ruleCode")));
                        if (!ruleId.isEmpty() && !ruleIds.contains(ruleId)) {
                            ruleIds.add(ruleId);
                        }
                    } else if (o != null) {
                        String ruleId = str(o);
                        if (!ruleId.isEmpty() && !ruleIds.contains(ruleId)) {
                            ruleIds.add(ruleId);
                        }
                    }
                }
            }
            if (!ruleIds.isEmpty()) {
                sb.append("，问题规则=").append(String.join("、", ruleIds));
            }
            sb.append("，请修正后重跑合规");
        }
        return sb.toString();
    }

    /** 截断超长文本（原文摘录随单摘要展示，避免工单 summary 过长）。 */
    private String truncate(String text, int max) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
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
}
