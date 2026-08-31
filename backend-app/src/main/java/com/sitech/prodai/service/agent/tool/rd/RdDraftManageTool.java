package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.repository.OpsWorkOrderRepository;
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
import java.util.Locale;
import java.util.Map;

/**
 * 产商品研发 - 配置草稿管理工具（删除 / 复制 / 提交备案）。
 * <p>
 * 工单卡上的「删除」「复制」「提交」操作统一走会话消息 → 翻译层路由到本工具执行，
 * 全量记录用户操作到会话历史。
 * <p>
 * 定位契约：草稿是工单的关联状态，统一以 work_order_id 定位——
 * 工单 payload.draftId 在开单时写入（rd_config_chat 生成即开单 / rd_draft_manage 复制即开单）。
 * 不再接受 draft_id/client_id 入参，避免 LLM 把工单号误填进 draft_id。
 */
@Component
public class RdDraftManageTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdDraftManageTool.class);

    private final ProductOntologyService productOntologyService;
    private final OpsWorkOrderRepository workOrderRepository;

    public RdDraftManageTool(ProductOntologyService productOntologyService,
                             OpsWorkOrderRepository workOrderRepository) {
        this.productOntologyService = productOntologyService;
        this.workOrderRepository = workOrderRepository;
    }

    @Override
    public String getName() {
        return "rd_draft_manage";
    }

    @Override
    public String getDescription() {
        return "管理配置草稿：删除、复制或提交备案配置工单关联的草稿（按工单号 work_order_id 定位，草稿随工单查询）";
    }

    @Override
    public String getLabel() {
        return "草稿管理";
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("action")
                        .label("操作类型")
                        .description("delete=删除草稿，copy=复制草稿生成副本，submit=提交草稿走合规备案闭环")
                        .required()
                        .type("string")
                        .build(),
                ToolParam.builder("work_order_id")
                        .label("工单ID")
                        .description("配置工单号（WO 开头），草稿按此工单号关联定位；删除/复制/提交必须携带")
                        .required()
                        .type("string")
                        .build(),
                ToolParam.builder("draft")
                        .label("草稿内容")
                        .description("copy 时可携带的草稿内容（通常无需提供，从工单关联草稿读取）")
                        .type("object")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("操作回执").type("string")
                        .description("草稿删除/复制操作结果摘要").build(),
                ToolOutputField.builder("draft", ToolOutputField.Role.OTHER)
                        .label("草稿内容").type("object")
                        .description("操作涉及的草稿内容（copy 时为副本草稿）").build(),
                ToolOutputField.builder("action", ToolOutputField.Role.OTHER)
                        .label("操作类型").type("string").build(),
                ToolOutputField.builder("success", ToolOutputField.Role.OTHER)
                        .label("是否成功").type("boolean").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String action = params != null ? String.valueOf(params.getOrDefault("action", "")).trim().toLowerCase(Locale.ROOT) : "";
        List<String> workOrderIds = workOrderIds(params);

        log.info("[AgentTool] rd_draft_manage 执行: action={}, workOrderIds={}", action, workOrderIds);
        if (!"delete".equals(action) && !"copy".equals(action) && !"submit".equals(action)) {
            return ExecutionResult.fail(getName(), "不支持的操作类型: " + action + "（允许 delete / copy / submit）");
        }
        if (workOrderIds.isEmpty()) {
            return ExecutionResult.fail(getName(), "缺少工单号 work_order_id，无法定位配置草稿");
        }
        // 批量提交：work_order_id 支持逗号/分号分隔多值（单值时与原行为一致）
        if ("submit".equals(action) && workOrderIds.size() > 1) {
            return doBatchSubmit(workOrderIds, params);
        }
        String workOrderId = workOrderIds.get(0);
        try {
            // 统一凭工单号反查关联草稿（payload.draftId），定位不到时按会话内名称兜底
            DraftRef ref = locateByWorkOrder(workOrderId);
            if (ref == null) {
                return ExecutionResult.fail(getName(), "未找到工单 " + workOrderId + " 关联的配置草稿");
            }
            return switch (action) {
                case "delete" -> doDelete(ref, workOrderId);
                case "copy" -> doCopy(ref, workOrderId, params);
                case "submit" -> doSubmit(ref, workOrderId, params);
                default -> ExecutionResult.fail(getName(), "不支持的操作类型: " + action);
            };
        } catch (Exception e) {
            log.error("[AgentTool] rd_draft_manage 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "草稿管理失败: " + e.getMessage());
        }
    }

    /**
     * 工单号参数解析：work_order_id / workOrderId / wo_id，
     * 逗号/分号/顿号分隔的多值拆分为列表（批量提交场景），单值返回单元素列表。
     */
    private List<String> workOrderIds(Map<String, Object> params) {
        String raw = strParam(params, "work_order_id", "workOrderId", "wo_id");
        List<String> out = new ArrayList<>();
        if (raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("[,，;；、\\s]+")) {
            String id = part.trim();
            if (!id.isEmpty() && !"null".equalsIgnoreCase(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 工单号 → 关联草稿引用（draftId + 名称 + 状态），来源于工单 payload.draftId。 */
    private record DraftRef(Long draftId, String clientId, String offeringName, String status) {
    }

    private DraftRef locateByWorkOrder(String workOrderId) {
        Map<String, Object> wo = workOrderRepository.findByWorkOrderId(workOrderId)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("draftId", e.getPayload() == null ? null : e.getPayload().get("draftId"));
                    m.put("offeringName", e.getOfferingName());
                    m.put("status", e.getStatus());
                    return m;
                })
                .orElse(null);
        if (wo == null) {
            return null;
        }
        Long draftId = parseLong(String.valueOf(wo.get("draftId")));
        if (draftId == null) {
            log.warn("[AgentTool] rd_draft_manage 工单 {} 无关联草稿（payload.draftId 缺失）", workOrderId);
            return null;
        }
        String name = String.valueOf(firstNonEmpty(wo.get("offeringName"), ""));
        String status = String.valueOf(firstNonEmpty(wo.get("status"), "open"));
        return new DraftRef(draftId, "", name, status);
    }

    /** 删除草稿：删除后同步取消关联配置工单。 */
    private ExecutionResult doDelete(DraftRef ref, String workOrderId) {
        Map<String, Object> resp = productOntologyService.deleteConfigDraft(ref.draftId());
        boolean ok = Boolean.TRUE.equals(resp.get("success"));
        String name = ref.offeringName();
        if (ok) {
            cancelLinkedWorkOrder(workOrderId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nl_answer", ok ? "已删除配置草稿「" + name + "」（draftId=" + ref.draftId() + "），工单 " + workOrderId + " 已同步取消"
                : "删除失败：" + resp.getOrDefault("message", "未知错误"));
        out.put("action", "delete");
        out.put("success", ok);
        out.put("draft_id", ref.draftId());
        out.put("work_order_id", workOrderId);
        return ok ? ExecutionResult.ok(getName(), out) : ExecutionResult.fail(getName(), String.valueOf(out.get("nl_answer")));
    }

    /** 草稿删除后联动关闭配置工单（open/in_progress → cancelled）；失败不影响删除结果。 */
    private void cancelLinkedWorkOrder(String workOrderId) {
        try {
            Map<String, Object> resp = productOntologyService.updateWorkOrderStatus(workOrderId, "cancelled", "关联配置草稿已删除");
            if (!Boolean.TRUE.equals(resp.get("success"))) {
                log.info("[AgentTool] rd_draft_manage 工单 {} 未流转到 cancelled: {}", workOrderId,
                        resp.getOrDefault("message", ""));
            }
        } catch (Exception e) {
            log.warn("[AgentTool] rd_draft_manage 关闭工单失败（不影响删除结果）: {}", e.getMessage());
        }
    }

    /**
     * 提交草稿：合规 → 沉淀本体 → 草稿流转 filing（submitConfigDraft 闭环），
     * 成功后把当前配置工单流转 done（提交即完成备案登记，不再生成备案工单）。
     */
    private ExecutionResult doSubmit(DraftRef ref, String workOrderId, Map<String, Object> params) {
        String sessionId = strParam(params, "session_id", "sessionId");
        // 重复提交拦截：已完成的工单（草稿已备案流转 filing）不再重复提交
        if ("done".equalsIgnoreCase(ref.status()) || "cancelled".equalsIgnoreCase(ref.status())) {
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.put("nl_answer", "工单 " + workOrderId + "（" + ref.offeringName() + "）已于此前提交完成备案，无需重复提交");
            skip.put("action", "submit");
            skip.put("success", false);
            skip.put("skipped", true);
            skip.put("draft_id", ref.draftId());
            skip.put("work_order_id", workOrderId);
            return ExecutionResult.ok(getName(), skip);
        }
        Map<String, Object> full = productOntologyService.getConfigDraft(ref.draftId());
        if (!Boolean.TRUE.equals(full.get("success"))) {
            return ExecutionResult.fail(getName(), "读取工单 " + workOrderId + " 关联草稿失败: " + full.getOrDefault("message", ""));
        }
        Map<String, Object> submitReq = new LinkedHashMap<>();
        submitReq.put("draftId", ref.draftId());
        if (!sessionId.isBlank()) {
            submitReq.put("sessionId", sessionId);
        }
        Map<String, Object> resp = productOntologyService.submitConfigDraft(submitReq);
        boolean ok = Boolean.TRUE.equals(resp.get("success"));
        String offeringName = ref.offeringName();
        Map<String, Object> out = new LinkedHashMap<>();
        if (ok) {
            // 配置工单流转 done（提交完成）；失败不阻断提交回执
            try {
                productOntologyService.updateWorkOrderStatus(workOrderId, "done", "草稿已提交备案，合规通过");
            } catch (Exception e) {
                log.warn("[AgentTool] rd_draft_manage 提交后流转工单失败（不影响提交结果）: {}", e.getMessage());
            }
            out.put("nl_answer", "已提交「" + offeringName + "」完成备案：商品编码=" + resp.get("offeringId")
                    + "，关联草稿已流转 filing");
            out.put("offering_id", resp.get("offeringId"));
        } else {
            String issues = resp.get("issues") instanceof List<?> list && !list.isEmpty()
                    ? "（问题项：" + list.stream()
                            .map(i -> i instanceof Map<?, ?> m ? String.valueOf(m.get("ruleId")) : String.valueOf(i))
                            .distinct().collect(java.util.stream.Collectors.joining("、")) + "）"
                    : "";
            out.put("nl_answer", "提交失败：" + resp.getOrDefault("message", "合规未通过或服务异常") + issues);
        }
        out.put("action", "submit");
        out.put("success", ok);
        out.put("draft_id", ref.draftId());
        out.put("work_order_id", workOrderId);
        return ok ? ExecutionResult.ok(getName(), out) : ExecutionResult.fail(getName(), String.valueOf(out.get("nl_answer")));
    }

    /**
     * 批量提交（问题1）：work_order_id 逗号分隔多工单时逐单走 submitConfigDraft 备案闭环。
     * 单单失败不阻断其余工单，回执逐单汇报结果（失败含合规问题规则号），
     * 并把最后一个成功命中的工单号写入 work_order_id 供前端高亮定位。
     */
    private ExecutionResult doBatchSubmit(List<String> workOrderIds, Map<String, Object> params) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<String> okNames = new ArrayList<>();
        List<String> failNames = new ArrayList<>();
        String lastOkWoId = "";
        for (String woId : workOrderIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("work_order_id", woId);
            DraftRef ref = locateByWorkOrder(woId);
            if (ref == null) {
                item.put("success", false);
                item.put("message", "未找到工单关联的配置草稿");
                failNames.add(woId);
                results.add(item);
                continue;
            }
            ExecutionResult single = doSubmit(ref, woId, params);
            Map<String, Object> data = single.getData() == null ? Map.of() : single.getData();
            item.put("success", single.isSuccess());
            item.put("message", single.isSuccess()
                    ? String.valueOf(data.getOrDefault("nl_answer", "已提交"))
                    : single.getErrorMessage());
            if (single.isSuccess()) {
                okNames.add(String.valueOf(firstNonEmpty(ref.offeringName(), woId)));
                lastOkWoId = woId;
            } else {
                failNames.add(String.valueOf(firstNonEmpty(ref.offeringName(), woId)));
            }
            results.add(item);
        }
        boolean allOk = failNames.isEmpty();
        Map<String, Object> out = new LinkedHashMap<>();
        StringBuilder nl = new StringBuilder();
        nl.append(allOk ? "已批量提交 " : "批量提交完成：成功 ").append(okNames.size()).append(" 单");
        if (!allOk) {
            nl.append("，失败 ").append(failNames.size()).append(" 单（")
                    .append(String.join("、", failNames)).append("）");
        }
        nl.append("。明细：");
        for (Map<String, Object> item : results) {
            nl.append('\n').append("- ").append(item.get("work_order_id")).append("：")
                    .append(item.get("message"));
        }
        out.put("nl_answer", nl.toString());
        out.put("action", "submit");
        out.put("success", allOk);
        out.put("batch", true);
        out.put("results", results);
        out.put("work_order_id", lastOkWoId);
        return ExecutionResult.ok(getName(), out);
    }

    /** 复制草稿：读取工单关联草稿 → 另存副本 → 副本开单（新工单同样关联副本 draftId）。 */
    private ExecutionResult doCopy(DraftRef ref, String workOrderId, Map<String, Object> params) {
        String sessionId = strParam(params, "session_id", "sessionId");
        Long sourceId = ref.draftId();
        Map<String, Object> full = productOntologyService.getConfigDraft(sourceId);
        if (!Boolean.TRUE.equals(full.get("success"))) {
            return ExecutionResult.fail(getName(), "读取工单 " + workOrderId + " 关联草稿失败: " + full.getOrDefault("message", ""));
        }
        Map<String, Object> draft = full.get("draft") instanceof Map<?, ?> d ? new LinkedHashMap<>((Map<String, Object>) d) : Map.of();
        String sourceName = String.valueOf(firstNonEmpty(draft.get("offeringName"), draft.get("offerName"),
                ref.offeringName(), "配置草稿"));

        String copyName = sourceName + "(副本)";
        draft.remove("offeringId");
        draft.remove("offerId");
        draft.remove("draftId");
        draft.remove("draft_id");
        draft.put("offeringName", copyName);
        if (draft.containsKey("offerName")) {
            draft.put("offerName", copyName);
        }

        Map<String, Object> saveReq = new LinkedHashMap<>();
        saveReq.put("draft", draft);
        saveReq.put("clientId", "P" + System.currentTimeMillis());
        if (!sessionId.isBlank()) {
            saveReq.put("sessionId", sessionId);
        }
        Map<String, Object> saved = productOntologyService.saveConfigDraft(saveReq);
        boolean ok = Boolean.TRUE.equals(saved.get("success"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nl_answer", ok ? "已复制配置草稿「" + sourceName + "」为「" + copyName + "」（draftId=" + saved.get("draftId") + "）"
                : "复制失败：" + saved.getOrDefault("message", "未知错误"));
        out.put("action", "copy");
        out.put("success", ok);
        out.put("work_order_id", workOrderId);
        if (ok) {
            out.put("draft", saved.get("draft"));
            out.put("draft_id", saved.get("draftId"));
            out.put("client_id", saved.get("clientId"));
            attachCopyWorkOrder(out, saved, draft, copyName, sessionId);
        }
        return ok ? ExecutionResult.ok(getName(), out) : ExecutionResult.fail(getName(), String.valueOf(out.get("nl_answer")));
    }

    /** 副本草稿保存后同步创建配置工单（新工单 payload 关联副本 draftId）；开单失败不影响复制结果。 */
    @SuppressWarnings("unchecked")
    private void attachCopyWorkOrder(Map<String, Object> out, Map<String, Object> saved,
                                     Map<String, Object> draft, String copyName, String sessionId) {
        try {
            String monthlyFee = String.valueOf(firstNonEmpty(draft.get("monthlyFee"), draft.get("fixedFeeAmount"), ""));
            String scenario = String.valueOf(firstNonEmpty(draft.get("bizScenario"), draft.get("scenario"), ""));
            Map<String, Object> woReq = new LinkedHashMap<>();
            woReq.put("offeringId", firstNonEmpty(draft.get("offeringId"), draft.get("offerId"), saved.get("draftId")));
            woReq.put("offeringName", copyName);
            woReq.put("source", "rd_config_draft");
            woReq.put("draftId", saved.get("draftId"));
            if (sessionId != null && !sessionId.isBlank()) {
                woReq.put("sessionId", sessionId);
            }
            woReq.put("title", copyName + "配置工单");
            woReq.put("summary", "配置草稿副本已生成：月费=" + (monthlyFee.isBlank() ? "-" : monthlyFee)
                    + "，场景=" + (scenario.isBlank() ? "-" : scenario));
            woReq.put("actions", List.of(
                    "核对配置草稿字段完整性",
                    "合规校验后提交资费备案",
                    "备案通过后发布上架"
            ));
            // 稽核结果随单：副本内容相对源草稿有变化（改名/清 offeringId），对副本重跑稽核取最新结论
            Map<String, Object> audit = productOntologyService.checkCompliance(draft);
            woReq.put("compliancePass", audit.get("compliancePass"));
            woReq.put("complianceIssues", audit.get("issues"));
            Map<String, Object> woBody = productOntologyService.createWorkOrder(woReq);
            if (woBody != null && woBody.get("workOrder") instanceof Map<?, ?> wo) {
                out.put("workOrder", wo);
                out.put("work_order_id", ((Map<String, Object>) wo).get("workOrderId"));
            }
        } catch (Exception e) {
            log.warn("[AgentTool] rd_draft_manage 副本开单失败（不影响复制结果）: {}", e.getMessage());
        }
    }

    private String strParam(Map<String, Object> params, String... keys) {
        if (params == null) {
            return "";
        }
        for (String key : keys) {
            Object v = params.get(key);
            if (v != null && !String.valueOf(v).isBlank() && !"null".equals(String.valueOf(v))) {
                return String.valueOf(v).trim();
            }
        }
        return "";
    }

    private Long parseLong(String value) {
        try {
            return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)
                    ? Long.parseLong(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Object firstNonEmpty(Object... values) {
        for (Object v : values) {
            if (v != null && !String.valueOf(v).isBlank() && !"null".equals(String.valueOf(v))) {
                return v;
            }
        }
        return "";
    }
}
