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
 * 产商品研发 - 文档解析原子工具（智读链路环节①）。
 * <p>
 * 解析方案文档（已上传 fileId 或原始文本）为文本，透出解析引擎/抽取字符数/trace_id。
 * 只做解析环节，不做抽取/合规/开单（职责拆分：一个工具一个环节）。
 * 包装 product-ontology/config/batch-by-file 与 config/batch 的解析部分。
 */
@Component
public class RdDocParseTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdDocParseTool.class);

    private final ProductOntologyService productOntologyService;

    public RdDocParseTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "rd_doc_parse";
    }

    @Override
    public String getDescription() {
        return "解析产商品方案文档（已上传文件或粘贴文本），抽出文本内容与解析引擎信息（不做套餐抽取与开单）";
    }

    @Override
    public String getLabel() {
        return "文档解析";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    /** 文档解析后的典型业务链：套餐抽取 → 开单（智读链路下游环节）。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("rd_draft_extract", "rd_workorder_create");
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
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("解析摘要").type("string")
                        .description("本次文档解析的结果摘要").build(),
                ToolOutputField.builder("document_text", ToolOutputField.Role.OTHER)
                        .label("文档文本").type("string")
                        .description("解析出的文档全文（供下一步套餐抽取）").build(),
                ToolOutputField.builder("items", ToolOutputField.Role.ITEMS)
                        .label("解析明细").type("list")
                        .description("逐文件解析明细（含各文件解析出的草稿原文）").build(),
                ToolOutputField.builder("parseEngine", ToolOutputField.Role.OTHER)
                        .label("解析引擎").type("string").build(),
                ToolOutputField.builder("extractedChars", ToolOutputField.Role.COUNT)
                        .label("抽取字符数").type("number").build(),
                ToolOutputField.builder("trace_id", ToolOutputField.Role.OTHER)
                        .label("解析留痕标识").type("string").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String fileId = params != null ? String.valueOf(params.getOrDefault("file_id", "")).trim() : "";
        String fileIds = params != null ? String.valueOf(params.getOrDefault("file_ids", "")).trim() : "";
        String docText = params != null ? String.valueOf(params.getOrDefault("document_text", "")) : "";
        String fileName = params != null ? String.valueOf(params.getOrDefault("file_name", "")).trim() : "";

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

        log.info("[AgentTool] rd_doc_parse 执行: fileIds={}, hasDocText={}", ids, !docText.isBlank());

        try {
            Map<String, Object> out;
            if (!ids.isEmpty()) {
                out = ids.size() == 1
                        ? parseSingle(ids.get(0), fileName.isEmpty() ? null : fileName)
                        : parseMultiFiles(ids);
            } else if (!docText.isBlank()) {
                out = parseText(docText);
            } else {
                return ExecutionResult.fail(getName(), "缺少文档（未提供已上传 file_id/file_ids 或文档文本）");
            }
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] rd_doc_parse 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "文档解析失败: " + e.getMessage());
        }
    }

    /** 单文件解析：batchFromUploadedFile 返回含解析与抽取全量，此处只透出解析环节产出（文本/引擎/trace）。 */
    private Map<String, Object> parseSingle(String id, String fileName) {
        Map<String, Object> resp = productOntologyService.batchFromUploadedFile(id, fileName);
        if (Boolean.FALSE.equals(resp.get("success"))) {
            return failBody(resp);
        }
        Map<String, Object> out = parseOutBody(resp);
        out.put("fileNames", List.of(str(resp.getOrDefault("fileName", id))));
        return out;
    }

    /** 多文件解析：逐份解析后合并解析明细。 */
    private Map<String, Object> parseMultiFiles(List<String> ids) {
        List<Map<String, Object>> fileDetails = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();
        List<String> traceIds = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String id : ids) {
            try {
                Map<String, Object> resp = productOntologyService.batchFromUploadedFile(id, null);
                if (Boolean.FALSE.equals(resp.get("success"))) {
                    failures.add(id + ": " + resp.getOrDefault("message", "解析失败"));
                    continue;
                }
                Object fn = resp.get("fileName");
                String name = fn != null && !String.valueOf(fn).isBlank() ? String.valueOf(fn) : id;
                fileNames.add(name);
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("file_name", name);
                if (resp.get("parseEngine") != null) {
                    detail.put("parse_engine", resp.get("parseEngine"));
                }
                if (resp.get("extractedChars") instanceof Number n) {
                    detail.put("extracted_chars", n.intValue());
                }
                fileDetails.add(detail);
                Object trace = resp.get("trace_id");
                if (trace != null && !String.valueOf(trace).isBlank()) {
                    traceIds.add(String.valueOf(trace));
                }
                if (resp.get("items") instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            items.add((Map<String, Object>) m);
                        }
                    }
                }
            } catch (Exception e) {
                failures.add(id + ": " + e.getMessage());
            }
        }
        if (fileDetails.isEmpty()) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "全部文档解析失败：" + String.join("；", failures));
            return fail;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nl_answer", "已解析 " + fileNames.size() + " 份文档"
                + (failures.isEmpty() ? "" : "；失败：" + String.join("；", failures)));
        out.put("items", items);
        out.put("fileNames", fileNames);
        out.put("fileCount", ids.size());
        out.put("fileDetails", fileDetails);
        if (!traceIds.isEmpty()) {
            out.put("trace_id", String.join(",", traceIds));
        }
        if (!failures.isEmpty()) {
            out.put("failures", failures);
        }
        return out;
    }

    /** 纯文本解析（无文件）：batchFromDocument 返回含抽取全量，此处只透出文本与 trace。 */
    private Map<String, Object> parseText(String docText) {
        Map<String, Object> resp = productOntologyService.batchFromDocument(docText, null);
        if (Boolean.FALSE.equals(resp.get("success"))) {
            return failBody(resp);
        }
        Map<String, Object> out = parseOutBody(resp);
        out.put("document_text", docText);
        out.put("extractedChars", docText.length());
        return out;
    }

    /** 从批次响应提取解析环节键（引擎/字符数/trace_id/明细），丢弃抽取环节键。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseOutBody(Map<String, Object> resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object summary = resp.get("summary");
        if (summary == null) summary = resp.get("message");
        if (summary == null) summary = "文档解析完成";
        out.put("nl_answer", String.valueOf(summary));
        if (resp.get("items") instanceof List<?> items) {
            out.put("items", items);
        }
        for (String key : new String[]{"trace_id", "parseEngine", "extractedChars", "fileName"}) {
            if (resp.get(key) != null) {
                out.put(key, resp.get(key));
            }
        }
        return out;
    }

    private Map<String, Object> failBody(Map<String, Object> resp) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("success", false);
        fail.put("message", resp.getOrDefault("message", "解析失败"));
        if (resp.get("parseEngine") != null) {
            fail.put("parseEngine", resp.get("parseEngine"));
        }
        return fail;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
