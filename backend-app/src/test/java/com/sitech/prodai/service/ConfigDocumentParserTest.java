package com.sitech.prodai.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void rejectsLegacyDoc() {
        ConfigDocumentParser.ParseResult result =
                parser.parse("x".getBytes(StandardCharsets.UTF_8), "legacy.doc");
        assertFalse(result.success());
        assertTrue(result.message().contains("docx"));
    }

    @Test
    void failsOnEmptyBytes() {
        ConfigDocumentParser.ParseResult result = parser.parse(new byte[0], "a.md");
        assertFalse(result.success());
    }
}
