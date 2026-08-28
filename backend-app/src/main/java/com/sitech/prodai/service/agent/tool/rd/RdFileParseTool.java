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
 * 产商品研发 - 智读文件解析工具。
 * <p>
 * 解析方案文档（已上传 fileId 或原始文本）并批量映射为产商品配置草稿。
 * 包装 product-ontology/config/batch-by-file 与 config/batch 后端能力。
 */
@Component
public class RdFileParseTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdFileParseTool.class);

    private final ProductOntologyService productOntologyService;

    public RdFileParseTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
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
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("file_id")
                        .label("文档标识")
                        .description("已上传文档的 file_id（有值时优先按文件解析）")
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
                        .description("本次文档解析/映射的结果摘要").build(),
                ToolOutputField.builder("items", ToolOutputField.Role.ITEMS)
                        .label("配置草稿").type("list")
                        .description("解析出的配置草稿清单").build(),
                ToolOutputField.builder("batch", ToolOutputField.Role.OTHER)
                        .label("批次").type("object").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String fileId = params != null ? String.valueOf(params.getOrDefault("file_id", "")).trim() : "";
        String docText = params != null ? String.valueOf(params.getOrDefault("document_text", "")) : "";
        String fileName = params != null ? String.valueOf(params.getOrDefault("file_name", "")).trim() : "";

        log.info("[AgentTool] rd_file_parse 执行: fileId={}, hasDocText={}", fileId, !docText.isBlank());

        try {
            Map<String, Object> resp;
            if (!fileId.isEmpty() && !"null".equalsIgnoreCase(fileId)) {
                resp = productOntologyService.batchFromUploadedFile(fileId, fileName.isEmpty() ? null : fileName);
            } else if (!docText.isBlank()) {
                resp = productOntologyService.batchFromDocument(docText, null);
            } else {
                return ExecutionResult.fail(getName(), "缺少文档（未提供已上传 file_id 或文档文本）");
            }
            return ExecutionResult.ok(getName(), normalize(resp));
        } catch (Exception e) {
            log.error("[AgentTool] rd_file_parse 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "文档解析失败: " + e.getMessage());
        }
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
}
