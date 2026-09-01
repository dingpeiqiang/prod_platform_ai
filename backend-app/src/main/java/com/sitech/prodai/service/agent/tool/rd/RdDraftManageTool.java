package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.repository.OpsWorkOrderRepository;
import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.ops.OpsExtractionService;
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
 * 产商品研发 - 配置草稿管理工具（修改 / 删除 / 复制 / 提交）。
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
    private final OpsExtractionService extractionService;

    public RdDraftManageTool(ProductOntologyService productOntologyService,
                             OpsWorkOrderRepository workOrderRepository,
                             OpsExtractionService extractionService) {
        this.productOntologyService = productOntologyService;
        this.workOrderRepository = workOrderRepository;
        this.extractionService = extractionService;
    }

    @Override
    public String getName() {
        return "rd_draft_manage";
    }

    @Override
    public String getDescription() {
        return "管理配置草稿：修改、删除、复制或提交配置工单关联的草稿（按工单号 work_order_id 定位，草稿随工单查询）";
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
                        .description("update=修改草稿字段（改资费名称等），delete=删除草稿，copy=复制草稿生成副本，submit=提交草稿走合规闭环")
                        .required()
                        .type("string")
                        .build(),
                ToolParam.builder("work_order_id")
                        .label("工单ID")
                        .description("配置工单号（WO 开头），草稿按此工单号关联定位；修改/删除/复制/提交必须携带")
                        .required()
                        .type("string")
                        .build(),
                ToolParam.builder("offering_name")
                        .label("资费名称")
                        .description("update 修改后的资费名称（如：家庭套餐198元/月），仅 update 且需改名时提供")
                        .type("string")
                        .build(),
                ToolParam.builder("monthly_fee")
                        .label("月费")
                        .description("update 修改后的月费金额（如：198），仅 update 且需改价时提供")
                        .type("string")
                        .build(),
                ToolParam.builder("question")
                        .label("用户话术")
                        .description("update 时的原始用户话术（多轮增量修改兜底解析用，如「改成家庭版」「198元/月」），建议透传")
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
        if (!"delete".equals(action) && !"copy".equals(action) && !"submit".equals(action) && !"update".equals(action)) {
            return ExecutionResult.fail(getName(), "不支持的操作类型: " + action + "（允许 delete / copy / update / submit）");
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
                case "update" -> doUpdate(ref, workOrderId, params);
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

    /**
     * 修改草稿字段：读取工单关联草稿 → LLM 识别修改意图 → 覆盖字段 → 回写保存。
     * 已提交（done）的工单同样允许修改，修改后工单重开为 open，可再次提交。
     * <p>
     * 修改意图识别链路（模板驱动，非正则猜测）：
     * ① LLM 已给结构化字段（offering_name/monthly_fee）直接用；
     * ② 否则把用户话术 + 当前草稿值交给 {@link OpsExtractionService#extractUpdateIntent}，
     *    先识别品类 → 定位模板 → 模板字段白名单内约束抽取。
     */
    private ExecutionResult doUpdate(DraftRef ref, String workOrderId, Map<String, Object> params) {
        Map<String, Object> full = productOntologyService.getConfigDraft(ref.draftId());
        if (!Boolean.TRUE.equals(full.get("success"))) {
            return ExecutionResult.fail(getName(), "读取工单 " + workOrderId + " 关联草稿失败: " + full.getOrDefault("message", ""));
        }
        Map<String, Object> draft = full.get("draft") instanceof Map<?, ?> d ? new LinkedHashMap<>((Map<String, Object>) d) : Map.of();

        Map<String, Object> changes = new LinkedHashMap<>();
        Map<String, Object> explicit = explicitChanges(draft, params);
        changes.putAll(explicit);
        if (changes.isEmpty()) {
            // LLM 约束抽取：品类 → 模板 → 模板字段白名单，未提及不编造
            String text = strParam(params, "question", "text", "nl_answer");
            Map<String, Object> intent = extractionService.extractUpdateIntent(text, draft, draftValueSnapshot(draft));
            for (Map.Entry<String, Object> e : intent.entrySet()) {
                applyDraftChange(draft, changes, e.getKey(), e.getValue());
            }
        }
        if (changes.isEmpty()) {
            return ExecutionResult.fail(getName(), "未识别到需要修改的草稿字段，请明确修改内容（如：把资费名称改成XX、月费改成99）");
        }

        Map<String, Object> saveReq = new LinkedHashMap<>();
        saveReq.put("draft", draft);
        saveReq.put("draftId", ref.draftId());
        String sessionId = strParam(params, "session_id", "sessionId");
        if (!sessionId.isBlank()) {
            saveReq.put("sessionId", sessionId);
        }
        Map<String, Object> saved = productOntologyService.saveConfigDraft(saveReq);
        boolean ok = Boolean.TRUE.equals(saved.get("success"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", "update");
        out.put("success", ok);
        out.put("draft_id", ref.draftId());
        out.put("work_order_id", workOrderId);
        out.put("changed_fields", changes);
        if (!ok) {
            out.put("nl_answer", "修改失败：" + saved.getOrDefault("message", "未知错误"));
            return ExecutionResult.fail(getName(), String.valueOf(out.get("nl_answer")));
        }

        // 已提交的工单修改后重开为 open，允许再次提交
        if ("done".equalsIgnoreCase(ref.status()) || "cancelled".equalsIgnoreCase(ref.status())) {
            try {
                productOntologyService.updateWorkOrderStatus(workOrderId, "open", "草稿字段修改，工单重开待重新提交");
                out.put("work_order_status", "open");
                out.put("reopened", true);
            } catch (Exception e) {
                log.warn("[AgentTool] rd_draft_manage 修改后重开工单失败（不影响修改结果）: {}", e.getMessage());
            }
        }

        // 资费名称变更时同步更新工单列（offering_name/title/summary），保证工单卡片展示与草稿一致
        if (changes.containsKey("offeringName")) {
            try {
                String newFee = String.valueOf(changes.getOrDefault("monthlyFee", ""));
                productOntologyService.renameWorkOrder(workOrderId, String.valueOf(changes.get("offeringName")), newFee);
            } catch (Exception e) {
                log.warn("[AgentTool] rd_draft_manage 同步工单名称失败（不影响修改结果）: {}", e.getMessage());
            }
        }

        StringBuilder changeDesc = new StringBuilder();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            if (changeDesc.length() > 0) {
                changeDesc.append("、");
            }
            changeDesc.append(fieldLabel(entry.getKey())).append("→").append(entry.getValue());
        }
        out.put("nl_answer", "已修改工单 " + workOrderId + " 关联草稿（draftId=" + ref.draftId() + "）：" + changeDesc
                + (out.containsKey("reopened") ? "，工单已重开为待处理，可重新提交" : ""));
        return ExecutionResult.ok(getName(), out);
    }

    /** LLM 显式结构化参数（offering_name/monthly_fee）→ 草稿变更；无变化返回空。 */
    private Map<String, Object> explicitChanges(Map<String, Object> draft, Map<String, Object> params) {
        Map<String, Object> changes = new LinkedHashMap<>();
        String newName = strParam(params, "offering_name", "offeringName", "tariff_name", "tariffName", "name");
        applyDraftChange(draft, changes, "offeringName", newName);
        String monthlyFee = strParam(params, "monthly_fee", "monthlyFee", "fixed_fee_amount", "fixedFeeAmount");
        applyDraftChange(draft, changes, "monthlyFee", monthlyFee);
        return changes;
    }

    /**
     * 单字段落草稿：值有效且与现值不同才写入 changes。
     * monthlyFee 变更时联动派生字段（固费/chargePlan/打印与短信话术）；
     * offeringName 变更时同步 offerName。
     */
    private void applyDraftChange(Map<String, Object> draft, Map<String, Object> changes, String field, Object value) {
        if (value == null) {
            return;
        }
        String val = String.valueOf(value).trim();
        if (val.isBlank() || "null".equalsIgnoreCase(val)) {
            return;
        }
        String current = String.valueOf(firstNonEmpty(draft.get(field), ""));
        if (val.equals(current)) {
            return;
        }
        draft.put(field, val);
        if ("monthlyFee".equals(field)) {
            changes.put("monthlyFee", val);
            applyFeeCascade(draft, val);
        } else {
            changes.put(field, val);
            if ("offeringName".equals(field) && draft.containsKey("offerName")) {
                draft.put("offerName", val);
            }
        }
    }

    /** 注入 LLM prompt 的当前草稿值快照（关键字段，供对比理解要改什么）。 */
    private Map<String, Object> draftValueSnapshot(Map<String, Object> draft) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String key : List.of("offeringName", "monthlyFee", "fixedFeeAmount", "bizScenario",
                "targetUser", "includeBroadband", "includeData", "includeVoice", "channelScope")) {
            if (draft.get(key) != null) {
                snapshot.put(key, draft.get(key));
            }
        }
        return snapshot;
    }

    /** 修改回执用的字段中文名。 */
    private String fieldLabel(String field) {
        return switch (field) {
            case "offeringName" -> "资费名称";
            case "monthlyFee" -> "月费";
            default -> field;
        };
    }

    /**
     * 月费变更后的派生字段联动：固费对齐月费 → chargePlan.fixedFeeAmount 同步
     * → 打印月费文案 / 成功短信 / 确认短信等含旧金额的话术全部按新值重算。
     * 仅更新与月费相关字段，资源/场景等其余内容保持不动。
     */
    private void applyFeeCascade(Map<String, Object> draft, String newFee) {
        draft.put("fixedFeeAmount", newFee);
        if (draft.get("chargePlan") instanceof Map<?, ?> cp) {
            @SuppressWarnings("unchecked")
            Map<String, Object> charge = new LinkedHashMap<>((Map<String, Object>) cp);
            if (charge.containsKey("fixedFeeAmount")) {
                charge.put("fixedFeeAmount", newFee);
            }
            draft.put("chargePlan", charge);
        }
        String feeText = newFee.replaceAll("\\.0$", "") + "元";
        String name = String.valueOf(firstNonEmpty(draft.get("offeringName"), draft.get("offerName"), "本套餐"));
        String resources = java.util.stream.Stream.of(
                        draft.get("includeData"), draft.get("includeVoice"), draft.get("includeBroadband"))
                .map(v -> v == null ? "" : String.valueOf(v))
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.joining("+"));
        if (resources.isBlank()) {
            resources = "套餐约定资源";
        }
        draft.put("printMonthlyFeeText", feeText + "/月");
        draft.put("successSmsImmediate",
                "恭喜您成功办理" + name + "，月费" + feeText + "，含" + resources + "，感谢您的支持！");
        draft.put("successSmsReserved",
                "您的" + name + "已预约生效，月费" + feeText + "，生效后可享受约定权益。");
        draft.put("confirmSms",
                "尊敬的客户，您正在办理" + name + "，月费" + feeText + "，是否确认办理？");
        if (draft.get("printNotice") instanceof Map<?, ?> pn) {
            @SuppressWarnings("unchecked")
            Map<String, Object> print = new LinkedHashMap<>((Map<String, Object>) pn);
            print.put("prcMonthFee", feeText + "/月");
            draft.put("printNotice", print);
        }
        if (draft.get("smsNotice") instanceof Map<?, ?> sn) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sms = new LinkedHashMap<>((Map<String, Object>) sn);
            sms.put("sysNoteNow", draft.get("successSmsImmediate"));
            sms.put("sysNoteNext", draft.get("successSmsReserved"));
            sms.put("sysNoteErke", draft.get("confirmSms"));
            draft.put("smsNotice", sms);
        }
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
     * 提交草稿：合规 → 沉淀本体（submitConfigDraft 闭环），
     * 成功后把当前配置工单流转 done（提交即完成）。
     */
    private ExecutionResult doSubmit(DraftRef ref, String workOrderId, Map<String, Object> params) {
        String sessionId = strParam(params, "session_id", "sessionId");
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
                productOntologyService.updateWorkOrderStatus(workOrderId, "done", "草稿已提交，合规通过");
            } catch (Exception e) {
                log.warn("[AgentTool] rd_draft_manage 提交后流转工单失败（不影响提交结果）: {}", e.getMessage());
            }
            out.put("nl_answer", "已提交「" + offeringName + "」：商品编码=" + resp.get("offeringId"));
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
     * 批量提交（问题1）：work_order_id 逗号分隔多工单时逐单走 submitConfigDraft 闭环。
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

        // 工单卡复制弹窗的补充需求（question 参数透传，如「改名为校园青春版，月费 29 元」）：
        // 复制后按需求修正字段（LLM 约束抽取，模板字段白名单，未提及不编造）
        String requirement = strParam(params, "question", "text", "nl_answer");
        Map<String, Object> appliedChanges = new LinkedHashMap<>();
        if (!requirement.isBlank() && !requirement.startsWith("复制")) {
            Map<String, Object> intent = extractionService.extractUpdateIntent(requirement, draft, draftValueSnapshot(draft));
            for (Map.Entry<String, Object> e : intent.entrySet()) {
                applyDraftChange(draft, appliedChanges, e.getKey(), e.getValue());
            }
            if (appliedChanges.containsKey("offeringName")) {
                String newName = String.valueOf(appliedChanges.get("offeringName"));
                draft.put("offeringName", newName);
                if (draft.containsKey("offerName")) {
                    draft.put("offerName", newName);
                }
                copyName = newName;
            }
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
                + (appliedChanges.isEmpty() ? "" : "，并按补充需求调整字段：" + appliedChanges.keySet())
                : "复制失败：" + saved.getOrDefault("message", "未知错误"));
        out.put("action", "copy");
        out.put("success", ok);
        out.put("work_order_id", workOrderId);
        if (ok) {
            out.put("draft", saved.get("draft"));
            out.put("draft_id", saved.get("draftId"));
            out.put("client_id", saved.get("clientId"));
            if (!appliedChanges.isEmpty()) {
                out.put("applied_requirements", appliedChanges);
            }
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
                    "合规校验后提交",
                    "提交通过后发布上架"
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
