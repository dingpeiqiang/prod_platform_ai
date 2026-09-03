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
 * 产商品研发 - 智读文件解析工具。
 * <p>
 * 解析方案文档（已上传 fileId 或原始文本）并批量映射为产商品配置草稿。
 * 包装 product-ontology/config/batch-by-file 与 config/batch 后端能力。
 */
@Component
public class RdFileParseTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdFileParseTool.class);

    private final ProductOntologyService productOntologyService;
    private final ProductTemplateRegistry templateRegistry;

    public RdFileParseTool(ProductOntologyService productOntologyService,
                           ProductTemplateRegistry templateRegistry) {
        this.productOntologyService = productOntologyService;
        this.templateRegistry = templateRegistry;
    }

    @Override
    public String getName() {
        return "rd_file_parse";
    }

    @Override
    public String getDescription() {
        return "解析产商品方案文档（已上传文件或粘贴文本），批量映射为配置草稿清单";
    }

    @Override
    public String getLabel() {
        return "方案文档解析";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("file_id")
                        .label("文档标识")
                        .description("已上传文档的 file_id（单文档时有值则优先按文件解析）")
                        .type("string")
                        .build(),
                ToolParam.builder("file_ids")
                        .label("多文档标识")
                        .description("多个已上传文档的 file_id，逗号分隔（多文档批量解析）")
                        .type("string")
                        .build(),
                ToolParam.builder("document_text")
                        .label("文档内容")
                        .description("方案文档的文本内容（无 file_id 时使用）")
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("file_name")
                        .label("文档名")
                        .description("文档名称（用于展示）")
                        .type("string")
                        .build(),
                ToolParam.builder("product_type")
                        .label("产品品类")
                        .description("可选，产品品类码（category_code，如 familyBasePrc）；未提供时由模板 matchers 兜底识别")
                        .type("string")
                        .build(),
                ToolParam.builder("session_id")
                        .label("会话标识")
                        .description("当前会话 ID（批量落库草稿与创建配置工单时绑定会话）")
                        .type("string")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("解析摘要").type("string")
                        .description("本次文档解析/映射的结果摘要").build(),
                ToolOutputField.builder("items", ToolOutputField.Role.ITEMS)
                        .label("配置草稿").type("list")
                        .description("解析出的配置草稿清单").build(),
                ToolOutputField.builder("batch", ToolOutputField.Role.OTHER)
                        .label("批次").type("object").build(),
                ToolOutputField.builder("workOrders", ToolOutputField.Role.OTHER)
                        .label("配置工单").type("list")
                        .description("解析即创建的配置工单清单（每条草稿一单，合规未通过单据带待修正标记，绑定当前会话）").build(),
                ToolOutputField.builder("workOrderCount", ToolOutputField.Role.OTHER)
                        .label("工单数量").type("number").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String fileId = params != null ? String.valueOf(params.getOrDefault("file_id", "")).trim() : "";
        String fileIds = params != null ? String.valueOf(params.getOrDefault("file_ids", "")).trim() : "";
        String docText = params != null ? String.valueOf(params.getOrDefault("document_text", "")) : "";
        String fileName = params != null ? String.valueOf(params.getOrDefault("file_name", "")).trim() : "";
        String productType = params != null ? String.valueOf(params.getOrDefault("product_type", "")).trim() : "";
        String sessionId = params != null ? String.valueOf(params.getOrDefault("session_id", "")).trim() : "";
        String matchHint = RdProductTypeSupport.resolve(templateRegistry, productType, docText);

        List<String> ids = new ArrayList<>();
        if (fileIds != null && !fileIds.isEmpty() && !"null".equalsIgnoreCase(fileIds)) {
            for (String raw : fileIds.split("[,，;；]")) {
                String id = raw.trim();
                if (!id.isEmpty() && !"null".equalsIgnoreCase(id) && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        if (fileId != null && !fileId.isEmpty() && !"null".equalsIgnoreCase(fileId) && !ids.contains(fileId)) {
            ids.add(0, fileId);
        }

        log.info("[AgentTool] rd_file_parse 执行: fileIds={}, hasDocText={}, sessionId={}", ids, !docText.isBlank(), sessionId);

        try {
            Map<String, Object> resp;
            if (!ids.isEmpty()) {
                resp = ids.size() == 1
                        ? productOntologyService.batchFromUploadedFile(ids.get(0), fileName.isEmpty() ? null : fileName)
                        : batchMultiFiles(ids);
            } else if (!docText.isBlank()) {
                resp = productOntologyService.batchFromDocument(docText, null);
            } else {
                return ExecutionResult.fail(getName(), "缺少文档（未提供已上传 file_id/file_ids 或文档文本）");
            }
            Map<String, Object> normalized = normalize(resp);
            // §9.4 product_type 观察：matchers 兜底解析结果随输出返回（批量逐项注入由 P2-2 抽取模板化接管）
            if (matchHint != null) {
                normalized.put("product_type", matchHint);
            }
            // 解析即开单：合规通过的草稿逐条落库并创建配置工单（source=rd_file_parse，绑定当前会话）
            if (!sessionId.isBlank()) {
                attachBatchWorkOrders(normalized, sessionId);
            }
            return ExecutionResult.ok(getName(), normalized);
        } catch (Exception e) {
            log.error("[AgentTool] rd_file_parse 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "文档解析失败: " + e.getMessage());
        }
    }

    /** 多文档批量解析：逐份解析后合并草稿清单与批次统计。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> batchMultiFiles(List<String> ids) {
        List<Map<String, Object>> mergedItems = new ArrayList<>();
        List<Map<String, Object>> mergedConfirmable = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        List<String> traceIds = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int passed = 0;
        for (String id : ids) {
            try {
                Map<String, Object> resp = productOntologyService.batchFromUploadedFile(id, null);
                if (Boolean.FALSE.equals(resp.get("success"))) {
                    failures.add(id + ": " + resp.getOrDefault("message", "解析失败"));
                    continue;
                }
                Object itemsObj = resp.get("items");
                if (itemsObj instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            mergedItems.add((Map<String, Object>) m);
                        }
                    }
                }
                Object confirmableObj = resp.get("confirmableDrafts");
                if (confirmableObj instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            mergedConfirmable.add((Map<String, Object>) m);
                        }
                    }
                }
                passed += resp.get("passedCount") instanceof Number n ? n.intValue() : 0;
                Object fn = resp.get("fileName");
                fileNames.add(fn != null && !String.valueOf(fn).isBlank() ? String.valueOf(fn) : id);
                Object trace = resp.get("trace_id");
                if (trace != null && !String.valueOf(trace).isBlank()) {
                    traceIds.add(String.valueOf(trace));
                }
            } catch (Exception e) {
                failures.add(id + ": " + e.getMessage());
            }
        }
        if (mergedItems.isEmpty()) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "全部文档解析失败：" + String.join("；", failures));
            return fail;
        }
        for (int i = 0; i < mergedItems.size(); i++) {
            mergedItems.get(i).put("index", i + 1);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", mergedItems.size());
        body.put("passedCount", passed);
        body.put("pendingCount", mergedItems.size() - passed);
        body.put("items", mergedItems);
        body.put("confirmableDrafts", mergedConfirmable);
        body.put("fileNames", fileNames);
        body.put("fileCount", ids.size());
        body.put("trace_id", String.join(",", traceIds));
        String summary = "已解析 " + fileNames.size() + " 份文档，共 " + mergedItems.size() + " 条配置草稿"
                + "（通过 " + passed + "，待修正 " + (mergedItems.size() - passed) + "）";
        if (!failures.isEmpty()) {
            summary += "；失败：" + String.join("；", failures);
        }
        body.put("summary", summary);
        body.put("message", summary);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resp == null) {
            out.put("nl_answer", "未返回解析结果");
            return out;
        }
        Object items = resp.get("items");
        Object countObj = resp.get("count");
        int count = countObj instanceof Number n ? n.intValue() : (items instanceof List<?> l ? l.size() : 0);
        Object summary = resp.get("summary");
        if (summary == null) summary = resp.get("message");
        if (summary == null) summary = "解析完成，生成 " + count + " 条配置草稿";
        out.put("nl_answer", String.valueOf(summary));
        if (items instanceof List<?>) out.put("items", items);
        out.put("batch", resp.get("batch") != null ? resp.get("batch") : resp);
        return out;
    }

    /**
     * 批量落库 + 即开单：解析出的全部草稿逐条 saveConfigDraft + createWorkOrder（source=rd_file_parse）。
     * 合规通过草稿开「待处理」单；合规未通过（待修正）草稿同样开单，稽核结论与问题规则随单展示，
     * 便于业务人员在工单卡中直接看到未通过原因并修正后重跑合规。
     * 工单 payload 关联 draftId（与 rd_config_chat 同构，后续提交/复制/删除凭工单号反查草稿）。
     * 单条失败不阻断其余开单，汇总结果写入 output（workOrders/workOrderCount/workOrderFailures）。
     */
    @SuppressWarnings("unchecked")
    private void attachBatchWorkOrders(Map<String, Object> out, String sessionId) {
        if (!(out.get("items") instanceof List<?> rawItems) || rawItems.isEmpty()) {
            return;
        }
        List<Map<String, Object>> workOrders = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int idx = 0;
        for (Object o : rawItems) {
            idx++;
            if (!(o instanceof Map<?, ?> rawItem)) {
                continue;
            }
            Map<String, Object> item = (Map<String, Object>) rawItem;
            if (!(item.get("draft") instanceof Map<?, ?> rawDraft) || ((Map<?, ?>) rawDraft).isEmpty()) {
                continue;
            }
            boolean pass = Boolean.TRUE.equals(item.get("compliancePass"));
            try {
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

                Map<String, Object> wo = createItemWorkOrder(item, draft, draftId, sessionId);
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
                log.warn("[AgentTool] rd_file_parse #{} 落库/开单失败（不阻断其余条目）: {}", idx, e.getMessage());
                failures.add("#" + idx + ": " + e.getMessage());
            }
        }

        out.put("workOrders", workOrders);
        out.put("workOrderCount", workOrders.size());
        if (!failures.isEmpty()) {
            out.put("workOrderFailures", failures);
        }
        // 摘要追加开单结果：用户在消息流直接看到「解析 N 条 → 开 M 单」
        Object summaryObj = out.get("nl_answer");
        String summary = summaryObj != null ? String.valueOf(summaryObj) : "";
        String woSummary = "已批量创建 " + workOrders.size() + " 个配置工单";
        if (!failures.isEmpty()) {
            woSummary += "（失败 " + failures.size() + " 条）";
        }
        out.put("nl_answer", summary.isBlank() ? woSummary : summary + "；" + woSummary);
    }

    /**
     * 单条草稿开配置工单：payload 关联 draftId + 稽核结论随单展示。
     * 合规未通过的待修正草稿：标题带「待修正」标记，摘要给出问题规则与修正指引，动作改为修正闭环。
     */
    private Map<String, Object> createItemWorkOrder(Map<String, Object> item, Map<String, Object> draft,
                                                    String draftId, String sessionId) {
        boolean pass = Boolean.TRUE.equals(item.get("compliancePass"));
        String offeringName = str(firstNonEmpty(draft.get("offeringName"), draft.get("offerName")));
        String offeringId = str(firstNonEmpty(draft.get("offeringId"), draft.get("offerId")));
        String monthlyFee = str(firstNonEmpty(draft.get("monthlyFee"), draft.get("fixedFeeAmount")));
        String scenario = str(firstNonEmpty(draft.get("bizScenario"), draft.get("scenario")));
        String excerpt = str(item.get("sourceExcerpt"));

        Map<String, Object> woReq = new LinkedHashMap<>();
        woReq.put("offeringId", offeringId);
        woReq.put("offeringName", offeringName);
        woReq.put("source", "rd_file_parse");
        woReq.put("sessionId", sessionId);
        woReq.put("draftId", draftId);
        woReq.put("compliancePass", pass);
        woReq.put("complianceIssues", item.get("issues"));
        woReq.put("title", offeringName.isEmpty()
                ? (pass ? "文档解析配置工单" : "文档解析待修正工单")
                : offeringName + (pass ? "配置工单" : "配置工单（待修正）"));
        woReq.put("summary", buildWorkOrderSummary(item, pass, monthlyFee, scenario, excerpt));
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
            log.warn("[AgentTool] rd_file_parse 开单失败（draftId={}）: {}", draftId, e.getMessage());
            return null;
        }
    }

    /** 工单摘要：合规通过列要素；未通过列问题规则并附修正指引。 */
    private String buildWorkOrderSummary(Map<String, Object> item, boolean pass,
                                         String monthlyFee, String scenario, String excerpt) {
        StringBuilder sb = new StringBuilder();
        if (pass) {
            sb.append("文档解析草稿已生成：");
        } else {
            sb.append("文档解析草稿待修正（合规未通过）：");
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
