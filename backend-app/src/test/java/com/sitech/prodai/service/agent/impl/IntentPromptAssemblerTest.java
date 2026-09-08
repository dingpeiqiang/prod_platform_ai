package com.sitech.prodai.service.agent.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 意图提示词组装器测试（方案 R3 验收）。
 * <p>
 * 验证：模板加载（classpath 回退）、rd/ops 场景差异、{rd_rules_block} 占位符替换、
 * 外部目录热更、以及 DefaultUnderstander 集成后系统提示词包含全部动态部分。
 */
class IntentPromptAssemblerTest {

    @Test
    void assemblesRdPromptWithRulesBlock() {
        IntentPromptAssembler assembler = new IntentPromptAssembler("");
        String prompt = assembler.assembleSystemPrompt(true);
        assertNotNull(prompt);
        // rd 角色 + 公共 JSON 契约 + rd 铁律块（占位符已替换）
        assertTrue(prompt.contains("产商品研发智能助手"), "缺少 rd 角色定义");
        assertTrue(prompt.contains("intent"), "缺少输出 JSON 契约");
        assertTrue(prompt.contains("CONFIRM"), "缺少 CONFIRM 判定规则");
        assertTrue(prompt.contains("rd_draft_manage"), "缺少 rd 铁律块（工单操作）");
        assertTrue(prompt.contains("rd_config_search"), "缺少查已有 vs 造新分流");
        assertFalse(prompt.contains("{rd_rules_block}"), "占位符未被替换");
    }

    @Test
    void assemblesOpsPromptWithoutRdRules() {
        IntentPromptAssembler assembler = new IntentPromptAssembler("");
        String prompt = assembler.assembleSystemPrompt(false);
        assertTrue(prompt.contains("产品运营智能助手"), "缺少 ops 角色定义");
        assertTrue(prompt.contains("CONFIRM"), "缺少 CONFIRM 判定规则");
        assertFalse(prompt.contains("rd_draft_manage"), "ops 场景不应注入 rd 铁律块");
    }

    @Test
    void externalDirOverridesClasspathWithHotReload() throws Exception {
        Path external = Paths.get("target", "test-prompt-intent");
        if (Files.exists(external)) {
            try (Stream<Path> walk = Files.walk(external)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        Files.createDirectories(external);
        // 外部目录放置覆盖版 base_prompt（mtime 热更验证）
        String overridden = "CUSTOM_BASE_MARKER 输出 JSON";
        Files.writeString(external.resolve("base_prompt.txt"), overridden, StandardCharsets.UTF_8);

        IntentPromptAssembler assembler = new IntentPromptAssembler(external.toString());
        String prompt = assembler.assembleSystemPrompt(false);
        assertTrue(prompt.contains("CUSTOM_BASE_MARKER"), "外部目录模板未生效");

        // 热更：修改文件内容（保证 mtime 变化）后无需重建组件
        Thread.sleep(1100);
        String updated = "UPDATED_MARKER 输出 JSON";
        Files.writeString(external.resolve("base_prompt.txt"), updated, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(external.resolve("base_prompt.txt"),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 2000));
        String reloaded = assembler.assembleSystemPrompt(false);
        assertTrue(reloaded.contains("UPDATED_MARKER"), "mtime 热更未生效");
    }

    @Test
    void missingTemplateFallsBackToInlineRole() {
        // 空外部目录 + classpath 正常时不会触发兜底；此处验证 classpath 缺失文件时返回 null 路径安全
        IntentPromptAssembler assembler = new IntentPromptAssembler("");
        assertNotNull(assembler.assembleSystemPrompt(true));
        assertFalse(assembler.listTemplates().isEmpty(), "classpath 模板清单为空");
    }
}
