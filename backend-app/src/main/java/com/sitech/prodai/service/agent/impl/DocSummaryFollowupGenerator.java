package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 附件-only 场景的「解析摘要 + 开放式追问」生成器（豆包式文件处理体验）。
 * <p>
 * 用户只上传附件未输入文本时：先解析文件，再由 LLM 基于解析内容生成一段
 * 自然语言总结 + 开放式追问（不弹结构化选项表单，用户下一轮自由回答）。
 * <p>
 * 输入来自 rd_doc_parse 的解析产物（document IR / document_text），
 * 长文本经分片降维（复用 DocumentIR.toChunks 的 6000 字/5 片策略，防打爆 LLM）。
 */
@Component
public class DocSummaryFollowupGenerator {

    private static final Logger log = LoggerFactory.getLogger(DocSummaryFollowupGenerator.class);

    /** LLM 摘要分片上限：与 OpsExtractionService 的既有策略一致（6000 字/片，最多 5 片）。 */
    private static final int LLM_DOC_CHUNK_SIZE = 6000;
    private static final int LLM_DOC_MAX_CHUNKS = 5;

    private final LlmService llmService;

    public DocSummaryFollowupGenerator(LlmService llmService) {
        this.llmService = llmService;
    }

    /**
     * 基于解析产物生成「总结 + 开放式追问」自然语言文案。
     *
     * @param parseData rd_doc_parse 成功结果（含 document/document_text/extractedChars 等键）
     * @param fileNames 文件名列表（用于话术称呼）
     * @return 自然语言文案；LLM 不可用/失败时回退模板（确定性守门，不阻断对话）
     */
    public String generate(Map<String, Object> parseData, List<String> fileNames) {
        String excerpt = buildExcerpt(parseData);
        String generated = llmGenerate(excerpt, fileNames);
        if (generated != null && !generated.isBlank()) {
            return generated.trim();
        }
        return fallback(fileNames, excerpt.length());
    }

    /**
     * 从解析产物构建 LLM 摘要输入文本：优先用结构化 IR 分片（保表格结构），
     * 降级用 document_text 纯文本截断。
     */
    private String buildExcerpt(Map<String, Object> parseData) {
        if (parseData == null) {
            return "";
        }
        if (parseData.get("document") instanceof Map<?, ?> irMap) {
            List<String> chunks = irToChunks(irMap);
            if (!chunks.isEmpty()) {
                return String.join("\n\n", chunks);
            }
        }
        Object text = parseData.get("document_text");
        if (text instanceof String s && !s.isBlank()) {
            return s.length() > LLM_DOC_CHUNK_SIZE * LLM_DOC_MAX_CHUNKS
                    ? s.substring(0, LLM_DOC_CHUNK_SIZE * LLM_DOC_MAX_CHUNKS) : s;
        }
        return "";
    }

    /** 把工具输出中的 document IR Map 重建为分片序列（snake_case 键，与 DocumentIR.toMap 同构）。 */
    private List<String> irToChunks(Map<?, ?> irMap) {
        try {
            if (!(irMap.get("blocks") instanceof List<?> blockList)) {
                return List.of();
            }
            StringBuilder plain = new StringBuilder();
            for (Object o : blockList) {
                if (!(o instanceof Map<?, ?> b)) {
                    continue;
                }
                String type = strValue(b.get("type"));
                switch (type) {
                    case "heading", "paragraph" -> {
                        String t = strValue(b.get("text"));
                        if (!t.isBlank()) {
                            plain.append(t).append("\n\n");
                        }
                    }
                    case "table" -> plain.append(tableMarkdown(b)).append("\n\n");
                    default -> { }
                }
                if (plain.length() >= LLM_DOC_CHUNK_SIZE * LLM_DOC_MAX_CHUNKS) {
                    break;
                }
            }
            String full = plain.toString().trim();
            if (full.isEmpty()) {
                return List.of();
            }
            List<String> chunks = new java.util.ArrayList<>();
            for (int i = 0; i < full.length() && chunks.size() < LLM_DOC_MAX_CHUNKS; i += LLM_DOC_CHUNK_SIZE) {
                chunks.add(full.substring(i, Math.min(i + LLM_DOC_CHUNK_SIZE, full.length())));
            }
            return chunks;
        } catch (Exception e) {
            log.warn("[DocSummaryFollowupGenerator] IR 分片失败，降级纯文本: {}", e.getMessage());
            return List.of();
        }
    }

    /** 表格块 → Markdown 管道表（与 DocumentIR.tableMarkdown 同构）。 */
    private String tableMarkdown(Map<?, ?> table) {
        StringBuilder sb = new StringBuilder();
        Object sheetName = table.get("sheet_name");
        Object title = table.get("title");
        if (sheetName != null && !String.valueOf(sheetName).isBlank()) {
            sb.append("【工作表：").append(sheetName).append("】\n");
        } else if (title != null && !String.valueOf(title).isBlank()) {
            sb.append("【").append(title).append("】\n");
        }
        if (table.get("headers") instanceof List<?> headers && !headers.isEmpty()) {
            sb.append("| ").append(joinCells(headers)).append(" |\n");
            sb.append("|").append(" --- |".repeat(headers.size())).append("\n");
        }
        if (table.get("rows") instanceof List<?> rows) {
            for (Object row : rows) {
                if (row instanceof List<?> cells) {
                    sb.append("| ").append(joinCells(cells)).append(" |\n");
                }
            }
        }
        return sb.toString().stripTrailing();
    }

    /** 单元格序列 → Markdown 行片段（通配符列表无法直接 reduce，先映射为字符串流）。 */
    private static String joinCells(List<?> cells) {
        return cells.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(" | "));
    }

    /** null 安全取字符串（Map<?,?> 通配符场景）。 */
    private static String strValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** LLM 生成总结 + 开放式追问；失败返回 null 由调用方回退模板。 */
    private String llmGenerate(String excerpt, List<String> fileNames) {
        if (excerpt.isBlank()) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("用户上传了文件「").append(String.join("、", fileNames)).append("」但未输入任何文字。\n")
                    .append("以下是文件解析出的内容：\n\n")
                    .append(excerpt).append("\n\n")
                    .append("请你扮演业务助手，做两件事：\n")
                    .append("1. 用 2-4 句话概括这份文件的内容与结构（涉及什么业务、包含哪些关键信息/数据，如有数字请用具体数字）；\n")
                    .append("2. 用一句自然、开放的话询问用户接下来想基于这份文件做什么（例如继续分析、生成方案或配置等方向可点到为止，不要列选项清单，不要输出 Markdown 标题）。\n")
                    .append("只输出这段对话文本本身。");
            return llmService.completePrompt(sb.toString());
        } catch (Exception e) {
            log.warn("[DocSummaryFollowupGenerator] 摘要追问 LLM 生成失败，回退模板: {}", e.getMessage());
            return null;
        }
    }

    /** 确定性回退模板：LLM 不可用时保证对话可继续。 */
    private String fallback(List<String> fileNames, int excerptChars) {
        String name = fileNames.isEmpty() ? "文档" : String.join("、", fileNames);
        return "已解析文件「" + name + "」（提取 " + excerptChars + " 字）。"
                + "请告诉我接下来想基于这份文件做什么，例如继续分析内容、生成方案草稿或批量导入配置。";
    }
}
