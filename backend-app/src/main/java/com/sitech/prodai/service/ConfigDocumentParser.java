package com.sitech.prodai.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 配置文档结构化解析：txt/md、csv、docx（OOXML）、xlsx（工作表单元格）、pdf（PDFBox）。
 * <p>
 * 产出 {@link DocumentIR}（语义块序列：heading/paragraph/table），文本为 IR 的
 * toPlainText 投影（表格→Markdown 管道表，sheet 名/标题保留）。
 * docx 表格按 XML 级解析（w:tbl/w:tr/w:tc，gridSpan 合并取首格值），xlsx 读 sheet 业务名，
 * 均零依赖手解 XML（不引入 POI）。
 */
@Service
public class ConfigDocumentParser {

    private static final Pattern DOCX_PARAGRAPH = Pattern.compile("<w:p\\b[^>]*>(.*?)</w:p>", Pattern.DOTALL);
    private static final Pattern DOCX_TABLE = Pattern.compile("<w:tbl>(.*?)</w:tbl>", Pattern.DOTALL);
    private static final Pattern DOCX_ROW = Pattern.compile("<w:tr\\b[^>]*>(.*?)</w:tr>", Pattern.DOTALL);
    private static final Pattern DOCX_CELL = Pattern.compile("<w:tc>(.*?)</w:tc>", Pattern.DOTALL);
    private static final Pattern DOCX_TEXT = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>");
    private static final Pattern DOCX_STYLE = Pattern.compile("<w:pStyle\\b[^>]*w:val=\"([^\"]*)\"");
    private static final Pattern DOCX_BREAKS = Pattern.compile("<w:(br|tab)\\b[^/>]*/>");
    private static final Pattern XLSX_SI = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL);
    private static final Pattern XLSX_T = Pattern.compile("<t[^>]*>([^<]*)</t>");
    private static final Pattern XLSX_ROW = Pattern.compile("<row\\b[^>]*>(.*?)</row>", Pattern.DOTALL);
    private static final Pattern XLSX_CELL = Pattern.compile("<c\\b([^>]*)>(.*?)</c>", Pattern.DOTALL);
    private static final Pattern XLSX_ATTR = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final Pattern XLSX_V = Pattern.compile("<v[^>]*>([^<]*)</v>");
    private static final Pattern XLSX_IS_T = Pattern.compile("<is>.*?<t[^>]*>([^<]*)</t>.*?</is>", Pattern.DOTALL);
    private static final Pattern XLSX_SHEET_DECL = Pattern.compile("<sheet\\b[^>]*>");
    private static final Pattern SHEET_ENTRY = Pattern.compile("^xl/worksheets/sheet\\d+\\.xml$");
    private static final Pattern CELL_REF = Pattern.compile("^([A-Z]+)(\\d+)$");
    private static final Pattern HEADING_TEXT = Pattern.compile("^#{1,4}\\s*(.+)$");
    private static final Pattern MD_TABLE_ROW = Pattern.compile("^\\|.+\\|$");
    private static final Pattern MD_TABLE_SEP = Pattern.compile("^\\|?(\\s*:?-{2,}:?\\s*\\|)+\\s*:?-{2,}:?\\s*\\|?$");

    public ParseResult parse(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            return ParseResult.fail("empty document");
        }
        String name = fileName == null ? "document.txt" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".doc") && !name.endsWith(".docx")) {
                return ParseResult.fail("legacy .doc 暂不支持，请另存为 .docx / .pdf / .md / .txt / .csv / .xlsx");
            }
            if (name.endsWith(".docx")) {
                return ParseResult.ok(extractDocx(bytes), "docx");
            }
            if (name.endsWith(".xlsx") || name.endsWith(".xlsm")) {
                return ParseResult.ok(extractXlsx(bytes), "xlsx");
            }
            if (name.endsWith(".xls") && !name.endsWith(".xlsx") && !name.endsWith(".xlsm")) {
                return ParseResult.fail("legacy .xls 暂不支持，请另存为 .xlsx / .csv");
            }
            if (name.endsWith(".pdf")) {
                return ParseResult.ok(extractPdf(bytes), "pdf");
            }
            if (name.endsWith(".csv")) {
                return ParseResult.ok(extractCsv(bytes), "csv");
            }
            Charset cs = detectCharset(bytes);
            String text = new String(bytes, cs).trim();
            if (text.isEmpty()) {
                return ParseResult.fail("document text is empty");
            }
            text = stripBom(text);
            String engine = name.endsWith(".md") ? "markdown" : "text";
            return ParseResult.ok(new DocumentIR(engine, blocksFromText(text, engine)), engine);
        } catch (Exception e) {
            return ParseResult.fail("parse failed: " + e.getMessage());
        }
    }

    // ===== docx：段落/标题/表格 XML 级解析（表格结构保留，合并单元格取首格值） =====

    private DocumentIR extractDocx(byte[] bytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    List<DocumentIR.Block> blocks = parseDocxBody(xml);
                    if (blocks.isEmpty()) {
                        throw new IOException("docx has no extractable text");
                    }
                    return new DocumentIR("docx", blocks);
                }
            }
        }
        throw new IOException("word/document.xml not found in docx");
    }

    /** docx body → 块序列：表格成 table 块，其余段落成 heading/paragraph 块（表格不再混入文本流）。 */
    private List<DocumentIR.Block> parseDocxBody(String xml) {
        List<DocumentIR.Block> blocks = new ArrayList<>();
        int pos = 0;
        Matcher tbl = DOCX_TABLE.matcher(xml);
        while (tbl.find()) {
            appendDocxSegments(blocks, xml.substring(pos, tbl.start()));
            blocks.add(parseDocxTable(tbl.group(1)));
            pos = tbl.end();
        }
        appendDocxSegments(blocks, xml.substring(pos));
        return blocks;
    }

    /** 表格外的 docx 片段按段落切分，识别 Heading 样式为标题块。 */
    private void appendDocxSegments(List<DocumentIR.Block> blocks, String segment) {
        if (segment == null || segment.isBlank()) {
            return;
        }
        Matcher p = DOCX_PARAGRAPH.matcher(segment);
        StringBuilder text = new StringBuilder();
        while (p.find()) {
            String paraText = docxRunsText(p.group(1));
            if (paraText.isBlank()) {
                continue;
            }
            int level = docxHeadingLevel(p.group(1));
            if (level > 0) {
                flushParagraphText(blocks, text);
                blocks.add(DocumentIR.Block.heading(paraText, level));
            } else {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(paraText);
            }
        }
        flushParagraphText(blocks, text);
    }

    private void flushParagraphText(List<DocumentIR.Block> blocks, StringBuilder text) {
        String merged = normalizeExtractedText(text.toString());
        text.setLength(0);
        if (!merged.isBlank()) {
            blocks.add(DocumentIR.Block.paragraph(merged));
        }
    }

    /** 段落内文本：保留 w:br/w:tab 为换行/Tab，拼接 w:t 内容。 */
    private String docxRunsText(String paragraphXml) {
        String withBreaks = DOCX_BREAKS.matcher(paragraphXml).replaceAll("\n");
        Matcher m = DOCX_TEXT.matcher(withBreaks);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            sb.append(unescapeXml(m.group(1)));
        }
        return sb.toString().trim();
    }

    /** Heading 样式级别：Heading1..9 / 标题 1..9 → 1..9；非标题返回 0。 */
    private int docxHeadingLevel(String paragraphXml) {
        Matcher style = DOCX_STYLE.matcher(paragraphXml);
        if (!style.find()) {
            return 0;
        }
        String val = style.group(1).toLowerCase(Locale.ROOT);
        Matcher num = Pattern.compile("(?:heading|标题)\\s*(\\d)").matcher(val);
        return num.find() ? Integer.parseInt(num.group(1)) : 0;
    }

    /** docx 表格：行×单元格 → headers/rows（gridSpan 合并单元格取首格值，空列补位对齐）。 */
    private DocumentIR.Block parseDocxTable(String tblXml) {
        List<List<String>> rawRows = new ArrayList<>();
        Matcher tr = DOCX_ROW.matcher(tblXml);
        while (tr.find()) {
            List<String> cells = new ArrayList<>();
            Matcher tc = DOCX_CELL.matcher(tr.group(1));
            while (tc.find()) {
                int span = docxGridSpan(tc.group(1));
                String value = docxRunsText(tc.group(1));
                cells.add(value);
                for (int i = 1; i < span; i++) {
                    cells.add("");
                }
            }
            if (!cells.stream().allMatch(String::isBlank)) {
                rawRows.add(cells);
            }
        }
        int cols = rawRows.stream().mapToInt(List::size).max().orElse(0);
        List<List<String>> rows = new ArrayList<>();
        for (List<String> raw : rawRows) {
            List<String> row = new ArrayList<>(raw);
            while (row.size() < cols) {
                row.add("");
            }
            rows.add(row);
        }
        // 首行含 2 个及以上非空单元格且后续有数据行时视为表头
        List<String> headers = List.of();
        List<List<String>> data = rows;
        if (rows.size() >= 2 && firstRowLooksLikeHeader(rows.get(0))) {
            headers = rows.get(0);
            data = rows.subList(1, rows.size());
        }
        return DocumentIR.Block.table(null, null, headers, data);
    }

    private int docxGridSpan(String cellXml) {
        Matcher m = Pattern.compile("<w:gridSpan\\b[^>]*w:val=\"(\\d+)\"").matcher(cellXml);
        return m.find() ? Math.max(1, Integer.parseInt(m.group(1))) : 1;
    }

    /** docx 首行表头启发：≥2 个非空单元格（表体行可能仅 1-2 列有值）。 */
    private boolean firstRowLooksLikeHeader(List<String> firstRow) {
        int filled = 0;
        for (String c : firstRow) {
            if (c != null && !c.isBlank()) {
                filled++;
            }
        }
        return filled >= 2;
    }

    // ===== xlsx：共享字符串 + sheet 业务名（workbook.xml），每 sheet 一个 table 块 =====

    private DocumentIR extractXlsx(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = readZipEntries(bytes);
        List<String> shared = parseSharedStrings(entries.get("xl/sharedStrings.xml"));

        List<String> sheetNames = entries.keySet().stream()
                .filter(n -> SHEET_ENTRY.matcher(n).matches())
                .sorted(Comparator.comparingInt(ConfigDocumentParser::sheetIndex))
                .toList();
        if (sheetNames.isEmpty()) {
            throw new IOException("no worksheet found in xlsx");
        }
        Map<String, String> sheetDisplayNames = parseSheetDisplayNames(entries.get("xl/workbook.xml"));

        List<DocumentIR.Block> blocks = new ArrayList<>();
        for (String sheetPath : sheetNames) {
            String sheetXml = new String(entries.get(sheetPath), StandardCharsets.UTF_8);
            List<List<String>> rows = extractSheetRows(sheetXml, shared);
            if (rows.isEmpty()) {
                continue;
            }
            String sheetName = sheetDisplayNames.getOrDefault(sheetPath,
                    "工作表" + (sheetIndex(sheetPath)));
            appendTableBlock(blocks, rows, sheetName, null);
        }
        // 兼容仅有共享字符串、无 sheet 单元格的异常包
        if (blocks.isEmpty() && !shared.isEmpty()) {
            blocks.add(DocumentIR.Block.paragraph(String.join("\n", shared)));
        }
        if (blocks.isEmpty()) {
            throw new IOException("xlsx has no extractable cell values");
        }
        return new DocumentIR("xlsx", blocks);
    }

    /** workbook.xml 声明顺序（sheet path → 业务名）：r:id ↔ rels 映射，缺失回退文件序号名。 */
    private Map<String, String> parseSheetDisplayNames(byte[] workbookBytes) {
        Map<String, String> names = new LinkedHashMap<>();
        if (workbookBytes == null || workbookBytes.length == 0) {
            return names;
        }
        String xml = new String(workbookBytes, StandardCharsets.UTF_8);
        Matcher decl = XLSX_SHEET_DECL.matcher(xml);
        int index = 0;
        while (decl.find()) {
            index++;
            Map<String, String> attrs = parseAttrs(decl.group());
            String name = attrs.get("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            names.put("xl/worksheets/sheet" + index + ".xml", unescapeXml(name));
        }
        return names;
    }

    private static int sheetIndex(String path) {
        Matcher m = Pattern.compile("sheet(\\d+)\\.xml$").matcher(path);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private List<String> parseSharedStrings(byte[] sharedBytes) {
        List<String> shared = new ArrayList<>();
        if (sharedBytes == null || sharedBytes.length == 0) {
            return shared;
        }
        String xml = new String(sharedBytes, StandardCharsets.UTF_8);
        Matcher si = XLSX_SI.matcher(xml);
        while (si.find()) {
            Matcher t = XLSX_T.matcher(si.group(1));
            StringBuilder cell = new StringBuilder();
            while (t.find()) {
                cell.append(unescapeXml(t.group(1)));
            }
            shared.add(cell.toString());
        }
        return shared;
    }

    /** sheet XML → 二维表（含表头行；纯文本投影时逐行 TSV 由 tableMarkdown 兜底）。 */
    private List<List<String>> extractSheetRows(String sheetXml, List<String> shared) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = XLSX_ROW.matcher(sheetXml);
        while (rowMatcher.find()) {
            Map<Integer, String> cells = new LinkedHashMap<>();
            int maxCol = -1;
            Matcher cellMatcher = XLSX_CELL.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                Map<String, String> attrs = parseAttrs(cellMatcher.group(1));
                String ref = attrs.getOrDefault("r", "");
                int col = columnIndex(ref);
                if (col < 0) {
                    col = maxCol + 1;
                }
                maxCol = Math.max(maxCol, col);
                cells.put(col, resolveCellValue(attrs.get("t"), cellMatcher.group(2), shared));
            }
            if (maxCol < 0) {
                continue;
            }
            List<String> row = new ArrayList<>();
            for (int c = 0; c <= maxCol; c++) {
                row.add(cells.getOrDefault(c, ""));
            }
            if (row.stream().anyMatch(v -> v != null && !v.isBlank())) {
                rows.add(row);
            }
        }
        return rows;
    }

    // ===== csv：整文件一个 table 块（首行表头） =====

    private DocumentIR extractCsv(byte[] bytes) {
        Charset cs = detectCharset(bytes);
        String raw = stripBom(new String(bytes, cs));
        if (raw.isBlank()) {
            throw new IllegalArgumentException("csv is empty");
        }
        char delimiter = detectCsvDelimiter(raw);
        List<List<String>> rows = parseCsvRows(raw, delimiter);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("csv has no rows");
        }
        List<List<String>> data = rows.stream()
                .filter(row -> !row.stream().allMatch(String::isBlank))
                .toList();
        if (data.isEmpty()) {
            throw new IllegalArgumentException("csv has no extractable text");
        }
        int cols = data.stream().mapToInt(List::size).max().orElse(0);
        List<List<String>> normalized = new ArrayList<>();
        for (List<String> row : data) {
            List<String> aligned = new ArrayList<>(row.stream().map(String::trim).toList());
            while (aligned.size() < cols) {
                aligned.add("");
            }
            normalized.add(aligned);
        }
        List<String> headers = List.of();
        List<List<String>> tableRows = normalized;
        if (normalized.size() >= 2) {
            headers = normalized.get(0);
            tableRows = normalized.subList(1, normalized.size());
        }
        return new DocumentIR("csv", List.of(
                DocumentIR.Block.table(null, null, headers, tableRows)));
    }

    /** CSV 分隔符探测（逗号/分号/Tab）：前 5 行无引号计数最多者。 */
    private char detectCsvDelimiter(String raw) {
        String sample = raw.lines().limit(5).reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        int commas = countUnquoted(sample, ',');
        int semis = countUnquoted(sample, ';');
        int tabs = countUnquoted(sample, '\t');
        if (tabs >= commas && tabs >= semis && tabs > 0) {
            return '\t';
        }
        if (semis > commas) {
            return ';';
        }
        return ',';
    }

    private int countUnquoted(String text, char ch) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ch && !inQuotes) {
                count++;
            }
        }
        return count;
    }

    /** CSV 行切分（引号字段处理：双写引号转义；\r 经 \n 统一换行）。 */
    private List<List<String>> parseCsvRows(String raw, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < raw.length() && raw.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                current.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                rows.add(current);
                current = new ArrayList<>();
            } else if (c == '\r') {
                // ignore; handle \r\n via \n
            } else {
                field.append(c);
            }
        }
        current.add(field.toString());
        if (!(current.size() == 1 && current.get(0).isBlank())) {
            rows.add(current);
        }
        return rows;
    }

    // ===== pdf / 纯文本：段落块（Markdown 识别标题与管道表） =====

    private DocumentIR extractPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                throw new IOException("pdf has no extractable text（扫描件需先 OCR）");
            }
            return new DocumentIR("pdf", List.of(
                    DocumentIR.Block.paragraph(normalizeExtractedText(text))));
        }
    }

    /** 纯文本/Markdown → 块序列：# 标题块、Markdown 管道表块、段落块。 */
    private List<DocumentIR.Block> blocksFromText(String text, String engine) {
        if ("markdown".equals(engine)) {
            return blocksFromMarkdown(text);
        }
        List<DocumentIR.Block> blocks = new ArrayList<>();
        for (String para : normalizeExtractedText(text).split("\n")) {
            if (!para.isBlank()) {
                blocks.add(DocumentIR.Block.paragraph(para));
            }
        }
        return blocks;
    }

    /** Markdown 块切分：# 标题、| 管道表（含分隔行）、段落。 */
    private List<DocumentIR.Block> blocksFromMarkdown(String text) {
        List<DocumentIR.Block> blocks = new ArrayList<>();
        List<List<String>> tableRows = new ArrayList<>();
        StringBuilder para = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            String trimmed = line.trim();
            if (MD_TABLE_ROW.matcher(trimmed).matches()) {
                flushParagraphText(blocks, para);
                if (MD_TABLE_SEP.matcher(trimmed).matches()) {
                    continue; // 分隔行不进 rows
                }
                List<String> cells = mdTableRow(trimmed);
                if (!cells.stream().allMatch(String::isBlank)) {
                    tableRows.add(cells);
                }
                continue;
            }
            if (!tableRows.isEmpty()) {
                appendTableBlock(blocks, tableRows, null, null);
                tableRows = new ArrayList<>();
            }
            Matcher heading = HEADING_TEXT.matcher(trimmed);
            if (heading.matches()) {
                flushParagraphText(blocks, para);
                blocks.add(DocumentIR.Block.heading(heading.group(1).trim(), 1));
                continue;
            }
            if (trimmed.isBlank()) {
                flushParagraphText(blocks, para);
            } else {
                if (para.length() > 0) {
                    para.append('\n');
                }
                para.append(trimmed);
            }
        }
        if (!tableRows.isEmpty()) {
            appendTableBlock(blocks, tableRows, null, null);
        }
        flushParagraphText(blocks, para);
        return blocks;
    }

    /** "| a | b |" → [a, b]（去首尾空管道）。 */
    private List<String> mdTableRow(String line) {
        String body = line.startsWith("|") ? line.substring(1) : line;
        if (body.endsWith("|")) {
            body = body.substring(0, body.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : body.split("\\|", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }

    /** 二维表 → table 块：首行做表头（≥3 列且首行非空列 ≥2 时），其余为数据行。 */
    private void appendTableBlock(List<DocumentIR.Block> blocks, List<List<String>> rows,
                                  String sheetName, String title) {
        if (rows.isEmpty()) {
            return;
        }
        List<String> headers = List.of();
        List<List<String>> data = rows;
        boolean headerLike = rows.size() >= 2 && firstRowLooksLikeHeader(rows.get(0));
        if (headerLike || rows.size() == 1) {
            // 单行表（无数据行）也按表头保留列语义，避免表格内容被当段落吞掉
            headers = rows.get(0);
            data = rows.subList(1, rows.size());
        }
        blocks.add(DocumentIR.Block.table(title, sheetName, headers, data));
    }

    /** 折叠多余空白，保留段落换行。 */
    private String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String resolveCellValue(String type, String cellInner, List<String> shared) {
        if (cellInner == null) {
            return "";
        }
        if ("inlineStr".equals(type)) {
            Matcher m = XLSX_IS_T.matcher(cellInner);
            return m.find() ? unescapeXml(m.group(1)) : "";
        }
        Matcher v = XLSX_V.matcher(cellInner);
        if (!v.find()) {
            return "";
        }
        String raw = unescapeXml(v.group(1).trim());
        if ("s".equals(type)) {
            try {
                int idx = Integer.parseInt(raw);
                return idx >= 0 && idx < shared.size() ? shared.get(idx) : raw;
            } catch (NumberFormatException e) {
                return raw;
            }
        }
        if ("b".equals(type)) {
            return "1".equals(raw) || "true".equalsIgnoreCase(raw) ? "TRUE" : "FALSE";
        }
        if ("e".equals(type)) {
            return raw;
        }
        // 数字 / 公式缓存值：去掉无意义的 .0
        if (raw.matches("-?\\d+\\.0+")) {
            return raw.substring(0, raw.indexOf('.'));
        }
        return raw;
    }

    private Map<String, String> parseAttrs(String attrXml) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (attrXml == null) {
            return attrs;
        }
        Matcher m = XLSX_ATTR.matcher(attrXml);
        while (m.find()) {
            attrs.put(m.group(1), m.group(2));
        }
        return attrs;
    }

    /** A1 -> 0, B1 -> 1, AA1 -> 26 */
    private int columnIndex(String cellRef) {
        if (cellRef == null || cellRef.isBlank()) {
            return -1;
        }
        Matcher m = CELL_REF.matcher(cellRef.toUpperCase(Locale.ROOT));
        if (!m.matches()) {
            return -1;
        }
        String letters = m.group(1);
        int col = 0;
        for (int i = 0; i < letters.length(); i++) {
            col = col * 26 + (letters.charAt(i) - 'A' + 1);
        }
        return col - 1;
    }

    private Map<String, byte[]> readZipEntries(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zis.readAllBytes());
                }
            }
        }
        return entries;
    }

    private String stripBom(String text) {
        if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1).trim();
        }
        return text == null ? "" : text;
    }

    private String unescapeXml(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /** UTF-8（含 BOM）优先；若替换字符过多则回退 GB18030（常见中文导出）。 */
    private Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        long replacement = utf8.chars().filter(ch -> ch == '\uFFFD').count();
        if (replacement == 0) {
            return StandardCharsets.UTF_8;
        }
        try {
            Charset gbk = Charset.forName("GB18030");
            String gbkText = new String(bytes, gbk);
            long gbkReplacement = gbkText.chars().filter(ch -> ch == '\uFFFD').count();
            if (gbkReplacement < replacement) {
                return gbk;
            }
        } catch (Exception ignored) {
            // keep UTF-8
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * 解析结果：success + IR（text 为其 toPlainText 投影，兼容旧消费方）+ engine + message。
     */
    public record ParseResult(boolean success, DocumentIR document, String text, String engine, String message) {
        static ParseResult ok(DocumentIR document, String engine) {
            return new ParseResult(true, document, document.toPlainText(), engine, null);
        }

        static ParseResult fail(String message) {
            return new ParseResult(false, null, "", null, message);
        }
    }
}
