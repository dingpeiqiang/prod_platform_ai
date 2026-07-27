package com.sitech.prodai.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDocumentParserTest {

    private final ConfigDocumentParser parser = new ConfigDocumentParser();

    @Test
    void parsesMarkdownFixture() throws Exception {
        byte[] bytes = Files.readAllBytes(
                Path.of("src/test/resources/testdata/zhidu_family_fusion.md"));
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "zhidu_family_fusion.md");
        assertTrue(result.success());
        assertEquals("markdown", result.engine());
        assertTrue(result.text().contains("家庭融合畅享158"));
        assertTrue(result.text().contains("家庭体验0元流量包"));
        assertTrue(result.text().contains("家庭融合加装包"));
    }

    @Test
    void parsesPlainTextUtf8() {
        String text = "套餐A：校园体验19元；月费19元；目标校园；全渠道\n";
        ConfigDocumentParser.ParseResult result =
                parser.parse(text.getBytes(StandardCharsets.UTF_8), "plan.txt");
        assertTrue(result.success());
        assertEquals("text", result.engine());
        assertTrue(result.text().contains("校园体验19元"));
    }

    @Test
    void parsesCsvWithQuotedFieldsAndCommaDelimiter() {
        String csv = """
                套餐名称,月费,流量,客群
                "家庭融合畅享158",158,40GB,家庭
                "含逗号,套餐",19,"5GB,体验",校园
                """;
        ConfigDocumentParser.ParseResult result =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8), "plans.csv");
        assertTrue(result.success(), result.message());
        assertEquals("csv", result.engine());
        assertTrue(result.text().contains("家庭融合畅享158"));
        assertTrue(result.text().contains("158"));
        assertTrue(result.text().contains("含逗号,套餐"));
        assertTrue(result.text().contains("5GB,体验"));
    }

    @Test
    void parsesCsvGbkEncoding() {
        String csv = "套餐名称,月费\n家庭融合畅享158,158\n";
        byte[] bytes = csv.getBytes(Charset.forName("GB18030"));
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "gbk.csv");
        assertTrue(result.success(), result.message());
        assertTrue(result.text().contains("家庭融合畅享158"));
    }

    @Test
    void parsesDocxParagraphText() throws Exception {
        byte[] bytes = buildMinimalDocx("套餐A：家庭融合畅享158；月费158元；目标家庭");
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "plan.docx");
        assertTrue(result.success(), result.message());
        assertEquals("docx", result.engine());
        assertTrue(result.text().contains("家庭融合畅享158"));
        assertTrue(result.text().contains("月费158元"));
    }

    @Test
    void parsesXlsxSharedStringsAndNumericCells() throws Exception {
        byte[] bytes = buildMinimalXlsx();
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "plans.xlsx");
        assertTrue(result.success(), result.message());
        assertEquals("xlsx", result.engine());
        assertTrue(result.text().contains("家庭融合畅享158"));
        assertTrue(result.text().contains("158"));
        assertTrue(result.text().contains("40"));
        assertTrue(result.text().contains("家庭体验0元流量包"));
    }

    @Test
    void parsesPdfExtractableText() throws Exception {
        byte[] bytes = buildMinimalPdf("Family Fusion 158 yuan plan");
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "plan.pdf");
        assertTrue(result.success(), result.message());
        assertEquals("pdf", result.engine());
        assertTrue(result.text().contains("Family Fusion 158"));
    }

    @Test
    void rejectsLegacyDoc() {
        ConfigDocumentParser.ParseResult result =
                parser.parse("x".getBytes(StandardCharsets.UTF_8), "legacy.doc");
        assertFalse(result.success());
        assertTrue(result.message().contains("docx"));
    }

    @Test
    void rejectsLegacyXls() {
        ConfigDocumentParser.ParseResult result =
                parser.parse("x".getBytes(StandardCharsets.UTF_8), "legacy.xls");
        assertFalse(result.success());
        assertTrue(result.message().contains("xlsx"));
    }

    @Test
    void failsOnEmptyBytes() {
        ConfigDocumentParser.ParseResult result = parser.parse(new byte[0], "a.md");
        assertFalse(result.success());
    }

    private static byte[] buildMinimalDocx(String paragraph) throws Exception {
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """.formatted(escapeXml(paragraph));
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """);
        entries.put("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """);
        entries.put("word/document.xml", documentXml);
        return zipXmlEntries(entries);
    }

    private static byte[] buildMinimalXlsx() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
                </Types>
                """);
        entries.put("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """);
        entries.put("xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """);
        entries.put("xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
                </Relationships>
                """);
        entries.put("xl/sharedStrings.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
                  <si><t>套餐名称</t></si>
                  <si><t>月费</t></si>
                  <si><t>家庭融合畅享158</t></si>
                  <si><t>家庭体验0元流量包</t></si>
                </sst>
                """);
        entries.put("xl/worksheets/sheet1.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="inlineStr"><is><t>流量GB</t></is></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>2</v></c>
                      <c r="B2"><v>158</v></c>
                      <c r="C2"><v>40</v></c>
                    </row>
                    <row r="3">
                      <c r="A3" t="s"><v>3</v></c>
                      <c r="B3"><v>0</v></c>
                      <c r="C3"><v>5</v></c>
                    </row>
                  </sheetData>
                </worksheet>
                """);
        return zipXmlEntries(entries);
    }

    private static byte[] buildMinimalPdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    private static byte[] zipXmlEntries(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
