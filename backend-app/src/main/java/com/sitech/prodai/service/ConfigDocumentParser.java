package com.sitech.prodai.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 配置文档文本抽取：txt/md、docx（OOXML）、xlsx（共享字符串）、pdf（PDFBox）。
 */
@Service
public class ConfigDocumentParser {

    private static final Pattern DOCX_TEXT = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>");
    private static final Pattern XLSX_SI = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL);
    private static final Pattern XLSX_T = Pattern.compile("<t[^>]*>([^<]*)</t>");

    public ParseResult parse(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            return ParseResult.fail("empty document");
        }
        String name = fileName == null ? "document.txt" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".doc") && !name.endsWith(".docx")) {
                return ParseResult.fail("legacy .doc 暂不支持，请另存为 .docx / .pdf / .md / .txt");
            }
            if (name.endsWith(".docx")) {
                return ParseResult.ok(extractDocx(bytes), "docx");
            }
            if (name.endsWith(".xlsx") || name.endsWith(".xlsm")) {
                return ParseResult.ok(extractXlsx(bytes), "xlsx");
            }
            if (name.endsWith(".pdf")) {
                return ParseResult.ok(extractPdf(bytes), "pdf");
            }
            Charset cs = detectCharset(bytes);
            String text = new String(bytes, cs).trim();
            if (text.isEmpty()) {
                return ParseResult.fail("document text is empty");
            }
            // 去掉 UTF-8 BOM 残留
            if (text.charAt(0) == '\uFEFF') {
                text = text.substring(1).trim();
            }
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
                    // 段落边界保留换行，便于套餐分段抽取
                    String withBreaks = xml
                            .replaceAll("</w:p>", "\n")
                            .replaceAll("<w:br[^/]*/>", "\n")
                            .replaceAll("<w:tab[^/]*/>", "\t");
                    Matcher m = DOCX_TEXT.matcher(withBreaks);
                    StringBuilder sb = new StringBuilder();
                    while (m.find()) {
                        sb.append(m.group(1));
                    }
                    return normalizeExtractedText(sb.toString());
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
        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalized;
    }

    private String extractXlsx(byte[] bytes) throws IOException {
        String shared = "";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("xl/sharedStrings.xml".equals(entry.getName())) {
                    shared = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        if (shared.isBlank()) {
            throw new IOException("xl/sharedStrings.xml not found or empty");
        }
        StringBuilder sb = new StringBuilder();
        Matcher si = XLSX_SI.matcher(shared);
        while (si.find()) {
            Matcher t = XLSX_T.matcher(si.group(1));
            while (t.find()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(t.group(1));
            }
        }
        return sb.toString().trim();
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);
            if (text == null || text.isBlank()) {
                throw new IOException("pdf has no extractable text");
            }
            return text.trim();
        }
    }

    private Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
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
