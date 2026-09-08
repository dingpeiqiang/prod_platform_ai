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
import java.util.List;
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
    void parsesDocxTableIntoTableBlock() throws Exception {
        byte[] bytes = buildDocxWithTable();
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "plans.docx");
        assertTrue(result.success(), result.message());
        assertEquals("docx", result.engine());
        // IR：1 个表格块（表头 + 2 数据行）+ 1 段落
        assertTrue(result.document() != null);
        assertEquals(1, result.document().tables().size(), "docx 表格应独立成 table 块");
        DocumentIR.Block table = result.document().tables().get(0);
        assertEquals(List.of("套餐名称", "月费", "流量"), table.headers());
        assertEquals(2, table.rows().size());
        assertEquals("家庭融合畅享158", table.rows().get(0).get(0));
        assertEquals("198", table.rows().get(0).get(1));
        // 纯文本投影保留表格内容（Markdown 管道表），段落不混表
        assertTrue(result.text().contains("家庭融合畅享158"));
        assertTrue(result.text().contains("198"));
    }

    @Test
    void parsesDocxGridSpanMergedCells() throws Exception {
        byte[] bytes = buildDocxWithGridSpanTable();
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "merged.docx");
        assertTrue(result.success(), result.message());
        DocumentIR.Block table = result.document().tables().get(0);
        // gridSpan=2 的合并单元格取首格值并补空列对齐
        assertEquals(List.of("套餐名称", "要素", ""), table.headers());
        assertEquals(3, table.rows().get(0).size());
        assertEquals("158", table.rows().get(0).get(1));
    }

    @Test
    void parsesDocxHeadingBlocks() throws Exception {
        byte[] bytes = buildDocxWithHeading();
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "heading.docx");
        assertTrue(result.success(), result.message());
        assertEquals(1, result.document().getBlocks().stream()
                .filter(b -> b.type() == DocumentIR.BlockType.HEADING).count());
        assertEquals("一、融合套餐概述", result.document().getBlocks().get(0).text());
    }

    @Test
    void parsesXlsxSheetsWithBusinessNames() throws Exception {
        byte[] bytes = buildXlsxTwoSheets();
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "plans.xlsx");
        assertTrue(result.success(), result.message());
        assertEquals("xlsx", result.engine());
        // IR：每 sheet 一个 table 块，sheet 业务名保留
        assertEquals(2, result.document().tables().size(), "两个 sheet 应各成一个表格块");
        assertEquals("资费清单", result.document().tables().get(0).sheetName());
        assertEquals("流量包清单", result.document().tables().get(1).sheetName());
        assertTrue(result.text().contains("【工作表：资费清单】"), "纯文本投影应保留 sheet 业务名");
        assertTrue(result.text().contains("家庭体验0元流量包"));
    }

    @Test
    void parsesCsvIntoSingleTableBlockWithHeaders() {
        String csv = """
                套餐名称,月费,流量,客群
                "家庭融合畅享158",158,40GB,家庭
                """;
        ConfigDocumentParser.ParseResult result =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8), "plans.csv");
        assertTrue(result.success(), result.message());
        assertEquals(1, result.document().tables().size());
        DocumentIR.Block table = result.document().tables().get(0);
        assertEquals(List.of("套餐名称", "月费", "流量", "客群"), table.headers());
        assertEquals(1, table.rows().size());
        assertEquals("158", table.rows().get(0).get(1));
    }

    @Test
    void irToChunksKeepsTableWholeAndProjectsHeaders() {
        DocumentIR ir = new DocumentIR("csv", List.of(DocumentIR.Block.table(null, null,
                List.of("套餐名称", "月费"),
                List.of(List.of("A", "1"), List.of("B", "2"), List.of("C", "3")))));
        List<String> chunks = ir.toChunks(60, 5);
        assertFalse(chunks.isEmpty());
        // 每片都带表头（表格不拦腰后丢失列语义）
        for (String chunk : chunks) {
            assertTrue(chunk.contains("套餐名称"), "分片应重挂表头: " + chunk);
        }
        String plain = ir.toPlainText();
        assertTrue(plain.contains("| A | 1 |"));
        assertTrue(plain.contains("| C | 3 |"));
    }

    @Test
    void parsesXlsxSharedStringsAndNumericCells() throws Exception {
        byte[] bytes = buildMinimalXlsx();
        ConfigDocumentParser.ParseResult result = parser.parse(bytes, "plans.xlsx");
        assertTrue(result.success(), result.message());
        assertEquals("xlsx", result.engine());
        assertTrue(result.text().contains("家庭融合畅享158"));
        assertTrue(result.text().contains("158"));
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
        return zipXmlEntries(Map.ofEntries(Map.entry("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """), Map.entry("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """), Map.entry("word/document.xml", documentXml)));
    }

    /** 带表格 + 前置段落的 docx（表头 3 列 + 2 数据行）。 */
    private static byte[] buildDocxWithTable() throws Exception {
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>套餐资费清单如下：</w:t></w:r></w:p>
                    <w:tbl>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>套餐名称</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>月费</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>流量</w:t></w:r></w:p></w:tc>
                      </w:tr>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>家庭融合畅享158</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>198</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>40GB</w:t></w:r></w:p></w:tc>
                      </w:tr>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>校园体验19元</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>19</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>5GB</w:t></w:r></w:p></w:tc>
                      </w:tr>
                    </w:tbl>
                  </w:body>
                </w:document>
                """;
        return zipXmlEntries(Map.ofEntries(Map.entry("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """), Map.entry("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """), Map.entry("word/document.xml", documentXml)));
    }

    /** 带横向合并（gridSpan=2）表头的 docx 表格。 */
    private static byte[] buildDocxWithGridSpanTable() throws Exception {
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:tbl>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>套餐名称</w:t></w:r></w:p></w:tc>
                        <w:tc><w:pPr><w:gridSpan w:val="2"/></w:pPr><w:p><w:r><w:t>要素</w:t></w:r></w:p></w:tc>
                      </w:tr>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>家庭融合畅享158</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>158</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>40GB</w:t></w:r></w:p></w:tc>
                      </w:tr>
                    </w:tbl>
                  </w:body>
                </w:document>
                """;
        return zipXmlEntries(Map.ofEntries(Map.entry("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """), Map.entry("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """), Map.entry("word/document.xml", documentXml)));
    }

    /** 带 Heading1 样式标题 + 段落的 docx。 */
    private static byte[] buildDocxWithHeading() throws Exception {
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>一、融合套餐概述</w:t></w:r></w:p>
                    <w:p><w:r><w:t>面向家庭用户</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """;
        return zipXmlEntries(Map.ofEntries(Map.entry("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """), Map.entry("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """), Map.entry("word/document.xml", documentXml)));
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

    private static byte[] buildMinimalXlsx() throws Exception {
        return buildXlsxTwoSheets();
    }

    /** 双 sheet xlsx（业务名：资费清单/流量包清单），每 sheet 首行表头 + 数据行。 */
    private static byte[] buildXlsxTwoSheets() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
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
                  <sheets><sheet name="资费清单" sheetId="1" r:id="rId1"/><sheet name="流量包清单" sheetId="2" r:id="rId2"/></sheets>
                </workbook>
                """);
        entries.put("xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
                </Relationships>
                """);
        entries.put("xl/sharedStrings.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="6" uniqueCount="6">
                  <si><t>套餐名称</t></si>
                  <si><t>月费</t></si>
                  <si><t>家庭融合畅享158</t></si>
                  <si><t>家庭体验0元流量包</t></si>
                  <si><t>流量</t></si>
                  <si><t>客群</t></si>
                </sst>
                """);
        entries.put("xl/worksheets/sheet1.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>2</v></c>
                      <c r="B2"><v>158</v></c>
                    </row>
                  </sheetData>
                </worksheet>
                """);
        entries.put("xl/worksheets/sheet2.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>3</v></c>
                      <c r="B1" t="s"><v>4</v></c>
                      <c r="C1" t="inlineStr"><is><t>5</t></is></c>
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

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
