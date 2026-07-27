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
 * 配置文档文本抽取：txt/md、csv、docx（OOXML）、xlsx（工作表单元格）、pdf（PDFBox）。
 */
@Service
public class ConfigDocumentParser {

    private static final Pattern DOCX_TEXT = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>");
    private static final Pattern XLSX_SI = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL);
    private static final Pattern XLSX_T = Pattern.compile("<t[^>]*>([^<]*)</t>");
    private static final Pattern XLSX_ROW = Pattern.compile("<row\\b[^>]*>(.*?)</row>", Pattern.DOTALL);
    private static final Pattern XLSX_CELL = Pattern.compile("<c\\b([^>]*)>(.*?)</c>", Pattern.DOTALL);
    private static final Pattern XLSX_ATTR = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final Pattern XLSX_V = Pattern.compile("<v[^>]*>([^<]*)</v>");
    private static final Pattern XLSX_IS_T = Pattern.compile("<is>.*?<t[^>]*>([^<]*)</t>.*?</is>", Pattern.DOTALL);
    private static final Pattern SHEET_ENTRY = Pattern.compile("^xl/worksheets/sheet\\d+\\.xml$");
    private static final Pattern CELL_REF = Pattern.compile("^([A-Z]+)(\\d+)$");

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
            return ParseResult.ok(normalizeExtractedText(text), engine);
        } catch (Exception e) {
            return ParseResult.fail("parse failed: " + e.getMessage());
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    // 段落/换行保留；表格单元格之间用 Tab，便于后续按行理解
                    String withBreaks = xml
                            .replaceAll("</w:p>", "\n")
                            .replaceAll("<w:br[^/]*/>", "\n")
                            .replaceAll("<w:tab[^/]*/>", "\t")
                            .replaceAll("</w:tc>", "\t");
                    Matcher m = DOCX_TEXT.matcher(withBreaks);
                    StringBuilder sb = new StringBuilder();
                    while (m.find()) {
                        sb.append(unescapeXml(m.group(1)));
                    }
                    String text = normalizeExtractedText(sb.toString());
                    if (text.isBlank()) {
                        throw new IOException("docx has no extractable text");
                    }
                    return text;
                }
            }
        }
        throw new IOException("word/document.xml not found in docx");
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

    /**
     * 解析 xlsx：共享字符串 + 各 sheet 单元格（含数字/布尔/内联字符串），按行列输出 TSV。
     */
    private String extractXlsx(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = readZipEntries(bytes);
        List<String> shared = parseSharedStrings(entries.get("xl/sharedStrings.xml"));

        List<String> sheetNames = entries.keySet().stream()
                .filter(n -> SHEET_ENTRY.matcher(n).matches())
                .sorted(Comparator.comparingInt(ConfigDocumentParser::sheetIndex))
                .toList();
        if (sheetNames.isEmpty()) {
            throw new IOException("no worksheet found in xlsx");
        }

        StringBuilder out = new StringBuilder();
        int sheetNo = 0;
        for (String sheetPath : sheetNames) {
            sheetNo++;
            String sheetXml = new String(entries.get(sheetPath), StandardCharsets.UTF_8);
            String sheetText = extractSheetTsv(sheetXml, shared);
            if (sheetText.isBlank()) {
                continue;
            }
            if (sheetNames.size() > 1) {
                if (out.length() > 0) {
                    out.append("\n\n");
                }
                out.append("【工作表").append(sheetNo).append("】\n");
            } else if (out.length() > 0) {
                out.append('\n');
            }
            out.append(sheetText);
        }
        String text = out.toString().trim();
        if (text.isBlank()) {
            // 兼容仅有共享字符串、无 sheet 单元格的异常包
            if (!shared.isEmpty()) {
                return String.join("\n", shared).trim();
            }
            throw new IOException("xlsx has no extractable cell values");
        }
        return text;
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

    private String extractSheetTsv(String sheetXml, List<String> shared) {
        List<String> lines = new ArrayList<>();
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
            StringBuilder line = new StringBuilder();
            for (int c = 0; c <= maxCol; c++) {
                if (c > 0) {
                    line.append('\t');
                }
                line.append(cells.getOrDefault(c, ""));
            }
            String rowText = line.toString().replaceAll("\\t+$", "");
            if (!rowText.isBlank()) {
                lines.add(rowText);
            }
        }
        return String.join("\n", lines);
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

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                throw new IOException("pdf has no extractable text（扫描件需先 OCR）");
            }
            return normalizeExtractedText(text);
        }
    }

    /**
     * CSV：自动识别分隔符（逗号/分号/Tab），处理引号字段，输出为 Tab 分隔文本。
     */
    private String extractCsv(byte[] bytes) {
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
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append('\t');
                }
                sb.append(row.get(i).trim());
            }
        }
        String text = sb.toString().trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("csv has no extractable text");
        }
        return text;
    }

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

    public record ParseResult(boolean success, String text, String engine, String message) {
        static ParseResult ok(String text, String engine) {
            return new ParseResult(true, text, engine, null);
        }

        static ParseResult fail(String message) {
            return new ParseResult(false, "", null, message);
        }
    }
}
