package com.sitech.prodai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档结构化中间层（Document IR）：解析产物不再是纯字符串，而是一份轻量结构化文档，
 * 文本只是它的一个投影（toPlainText）。三层分工——
 * <pre>
 *   第1层 解析（ConfigDocumentParser）      → 产出本 IR（blocks 语义块序列）
 *   第2层 投影（toPlainText/toChunks）      → 按消费者降维（LLM prompt/正则/分片/展示）
 *   第3层 抽取消费（OpsExtractionService）   → 表格块表头映射直通，段落块走既有 LLM/正则
 * </pre>
 * 块类型：heading（标题，含级别）、paragraph（段落）、table（表格，含表头/行/sheet 名）。
 * 设计约束：不引入 POI，docx/xlsx 仍零依赖手解 XML；IR 只承载「表格结构 + 标题层级 +
 * 段落边界」三种最低限语义，复杂排版（图片/脚注/嵌套表）不建模（YAGNI）。
 */
public final class DocumentIR {

    /** 块类型 */
    public enum BlockType {
        /** 标题（level 1..n） */
        HEADING,
        /** 段落（普通叙述文本） */
        PARAGRAPH,
        /** 表格（headers + rows；xlsx 每 sheet 一表，csv 整文件一表） */
        TABLE
    }

    /**
     * 语义块：type + 各类型字段（联合体式，按 type 取用）。
     * heading: text, level；paragraph: text；table: title?, sheetName?, headers?, rows。
     */
    public record Block(BlockType type, String text, int level,
                        String title, String sheetName,
                        List<String> headers, List<List<String>> rows) {

        public static Block heading(String text, int level) {
            return new Block(BlockType.HEADING, text, level, null, null, null, null);
        }

        public static Block paragraph(String text) {
            return new Block(BlockType.PARAGRAPH, text, 0, null, null, null, null);
        }

        public static Block table(String title, String sheetName, List<String> headers, List<List<String>> rows) {
            return new Block(BlockType.TABLE, null, 0, title, sheetName,
                    headers == null ? List.of() : headers,
                    rows == null ? List.of() : rows);
        }

        public boolean isTable() {
            return type == BlockType.TABLE;
        }
    }

    /** 解析引擎（docx/xlsx/csv/pdf/markdown/text），与 ParseResult.engine 同源。 */
    private final String engine;
    /** 语义块序列（保序）。 */
    private final List<Block> blocks;

    public DocumentIR(String engine, List<Block> blocks) {
        this.engine = engine == null ? "" : engine;
        this.blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public String getEngine() {
        return engine;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    public List<Block> tables() {
        return blocks.stream().filter(Block::isTable).toList();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /**
     * 投影①：纯文本（兼容旧链路——LLM prompt/正则/展示）。
     * 表格投影为 Markdown 管道表（| A | 198 |），sheet 名/标题保留为标题行；
     * 标题块投影为独立行；段落保持原文本。
     */
    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            switch (block.type()) {
                case HEADING -> {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(block.text());
                }
                case PARAGRAPH -> {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(block.text());
                }
                case TABLE -> {
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(tableMarkdown(block));
                }
            }
        }
        return sb.toString().trim();
    }

    /** 表格 → Markdown 文本（首行表头 + 分隔行 + 数据行；无表头时仅数据行）。 */
    private static String tableMarkdown(Block table) {
        StringBuilder sb = new StringBuilder();
        if (table.sheetName() != null && !table.sheetName().isBlank()) {
            sb.append("【工作表：").append(table.sheetName()).append("】\n");
        } else if (table.title() != null && !table.title().isBlank()) {
            sb.append("【").append(table.title()).append("】\n");
        }
        List<String> headers = table.headers();
        List<List<String>> rows = table.rows();
        if (headers != null && !headers.isEmpty()) {
            sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
            sb.append("|").append(" --- |".repeat(headers.size())).append("\n");
        }
        for (List<String> row : rows) {
            sb.append("| ").append(String.join(" | ", row)).append(" |");
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /**
     * 投影②：语义分片（按块切，表格块整体进片不拦腰、段落不混表）。
     * 以 chunkSize 为片长上限逐块攒片：单块超限时表格硬按行切（保表头）、段落按行回退切分；
     * 超过 maxChunks 截断尾部（与既有 6000 字/5 片策略一致，防打爆 LLM）。
     */
    public List<String> toChunks(int chunkSize, int maxChunks) {
        List<String> chunks = new ArrayList<>();
        if (chunkSize <= 0 || maxChunks <= 0 || blocks.isEmpty()) {
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (Block block : blocks) {
            if (chunks.size() >= maxChunks) {
                return chunks;
            }
            String blockText = switch (block.type()) {
                case HEADING, PARAGRAPH -> block.text();
                case TABLE -> tableMarkdown(block);
            };
            if (blockText.isBlank()) {
                continue;
            }
            // 单块即超限：先落当前片，再对该块独立切分
            if (blockText.length() > chunkSize) {
                if (current.length() > 0 && chunks.size() < maxChunks) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                appendOversizedBlock(chunks, block, blockText, chunkSize, maxChunks);
                continue;
            }
            if (current.length() + blockText.length() + 2 > chunkSize) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(blockText);
        }
        if (current.length() > 0 && chunks.size() < maxChunks) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    /** 超限块切分：表格按行切且每片重挂表头；标题/段落按行边界回退切分。 */
    private void appendOversizedBlock(List<String> chunks, Block block, String blockText,
                                      int chunkSize, int maxChunks) {
        if (block.isTable()) {
            List<String> headers = block.headers();
            List<List<String>> rows = block.rows();
            StringBuilder piece = new StringBuilder();
            String headerLine = headers == null || headers.isEmpty() ? ""
                    : "| " + String.join(" | ", headers) + " |";
            for (List<String> row : rows) {
                String rowLine = "| " + String.join(" | ", row) + " |";
                if (piece.length() + rowLine.length() + 1 > chunkSize && piece.length() > 0) {
                    chunks.add(piece.toString().trim());
                    piece.setLength(0);
                    if (chunks.size() >= maxChunks) {
                        return;
                    }
                    if (!headerLine.isEmpty()) {
                        piece.append(headerLine).append('\n');
                    }
                }
                piece.append(rowLine).append('\n');
            }
            if (piece.length() > 0 && chunks.size() < maxChunks) {
                chunks.add(piece.toString().trim());
            }
            return;
        }
        // 标题/段落：按行边界回退切分（避免句子拦腰）
        int start = 0;
        while (start < blockText.length() && chunks.size() < maxChunks) {
            int end = Math.min(start + chunkSize, blockText.length());
            if (end < blockText.length()) {
                int boundary = blockText.lastIndexOf('\n', end);
                if (boundary > start) {
                    end = boundary + 1;
                }
            }
            chunks.add(blockText.substring(start, end).trim());
            start = end;
        }
    }

    /** 序列化为 Map（供工具输出/前端契约透传），snake_case 键。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine", engine);
        List<Map<String, Object>> blockList = new ArrayList<>();
        for (Block block : blocks) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("type", block.type().name().toLowerCase(java.util.Locale.ROOT));
            if (block.text() != null && !block.text().isBlank()) {
                b.put("text", block.text());
            }
            if (block.level() > 0) {
                b.put("level", block.level());
            }
            if (block.title() != null && !block.title().isBlank()) {
                b.put("title", block.title());
            }
            if (block.sheetName() != null && !block.sheetName().isBlank()) {
                b.put("sheet_name", block.sheetName());
            }
            if (block.isTable()) {
                b.put("headers", block.headers());
                b.put("rows", block.rows());
            }
            blockList.add(b);
        }
        m.put("blocks", blockList);
        return m;
    }
}
