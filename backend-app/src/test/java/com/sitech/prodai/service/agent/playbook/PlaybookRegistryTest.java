package com.sitech.prodai.service.agent.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手册注册表（Playbook 层）单测：装载门禁校验 + 双消费视图（路由判定 / SOP 渲染）。
 * <p>
 * 手册 = 完成一类事情的指导手册（步骤 + 每步操作方法 + 使用工具），
 * 用户定义的工作流形态；doc-batch-import 为第一本手册（智读·文件配置固化）。
 */
class PlaybookRegistryTest {

    private PlaybookRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PlaybookRegistry();
        registry.init();
    }

    // ── 装载 ──

    @Test
    void loadsDocBatchImportPlaybookFromClasspath() {
        assertNotNull(registry.get("doc-batch-import"), "第一本手册应从 classpath:playbooks 装载");
        assertTrue(registry.problems().isEmpty(), () -> "装载应零问题: " + registry.problems());
    }

    @Test
    void loadedPlaybookHasStepsToolsGuardrails() {
        Map<String, Object> book = registry.get("doc-batch-import");
        assertEquals("文档批量导入配置", book.get("title"));
        assertTrue(book.get("steps") instanceof List<?> steps && steps.size() >= 3, "应有完整步骤链");
        assertTrue(book.get("tools") instanceof List<?> tools && !tools.isEmpty(), "应声明工具引用");
        assertTrue(book.get("guardrails") instanceof Map<?, ?>, "应声明护栏");
    }

    @Test
    void registersKnownToolsAndResolvesReferences() {
        // 工具名单注入后重载：rd_file_parse / rd_compliance 已注册 → 引用可解析、零问题
        registry.registerKnownTools(java.util.Set.of("rd_file_parse", "rd_compliance", "rd_config_chat"));
        registry.reload();
        assertTrue(registry.problems().isEmpty(), () -> "引用的工具均已注册，装载应零问题: " + registry.problems());
    }

    @Test
    void unknownToolReferenceIsRejected() {
        // 模拟工具名单不含手册引用的工具 → 装载门禁应报"引用不可解析"（MCP 约束）
        registry.registerKnownTools(java.util.Set.of("some_other_tool"));
        registry.reload();
        List<String> problems = registry.problems().get("doc-batch-import");
        assertNotNull(problems, "引用不存在的工具应被门禁拦截");
        assertTrue(problems.stream().anyMatch(p -> p.contains("引用不可解析")),
                () -> "应报引用不可解析: " + problems);
    }

    // ── 路由消费（双消费①） ──

    @Test
    void routesByIntentDeclaration() {
        assertEquals("doc-batch-import", registry.route("rd", "RD_FILE_PARSE", List.of()),
                "意图命中 applies_to.intents → 返回手册 code");
    }

    @Test
    void routesByToolDeclaration() {
        assertEquals("doc-batch-import", registry.route("rd", "SOME_OTHER_INTENT", List.of("rd_file_parse")),
                "工具命中 applies_to.tools 亦应命中");
    }

    @Test
    void sceneMismatchDoesNotRoute() {
        assertNull(registry.route("ops", "RD_FILE_PARSE", List.of()),
                "场景不一致（ops ≠ rd）→ 不路由");
    }

    @Test
    void unrelatedIntentAndToolDoesNotRoute() {
        assertNull(registry.route("rd", "RD_CONFIG_CHAT", List.of("rd_config_chat")),
                "意图与工具均不在适用域 → 不路由");
    }

    // ── 触发词快筛（入口三级瀑布第一级） ──

    @Test
    void triggerHitRoutesDirectly() {
        assertEquals("doc-batch-import", registry.matchTrigger("rd", "帮我导入文档：方案.docx"),
                "话术含触发词「导入文档」→ 直达手册");
    }

    @Test
    void longestTriggerWinsWhenMultipleMatch() {
        // doc-batch-import 的触发词里「批量导入」比「导入」长 → 命中后者
        assertEquals("doc-batch-import", registry.matchTrigger("rd", "批量导入这批文档"));
    }

    @Test
    void triggerMissAndSceneMissReturnNull() {
        assertNull(registry.matchTrigger("rd", "配一个39元套餐"), "未含触发词 → 不快筛");
        assertNull(registry.matchTrigger("ops", "导入文档"), "场景不符 → 不快筛");
        assertNull(registry.matchTrigger("rd", "  "), "空话术 → 不快筛");
    }

    // ── 编排消费（双消费②） ──

    @Test
    void renderSopContainsStepsHowAndTools() {
        String sop = registry.renderSop("doc-batch-import");
        assertNotNull(sop);
        assertTrue(sop.contains("【标准作业程序：文档批量导入配置】"));
        assertTrue(sop.contains("第1步 解析文档提取文本"), () -> "应含步骤: " + sop);
        assertTrue(sop.contains("每条草稿一单"), () -> "应含操作方法/约束: " + sop);
        assertTrue(sop.contains("（工具：rd_file_parse）"), () -> "应含工具标注: " + sop);
        assertTrue(sop.contains("护栏："), () -> "应含护栏段: " + sop);
    }

    @Test
    void renderSopReturnsNullForUnknownCode() {
        assertNull(registry.renderSop("no-such-playbook"));
    }

    @Test
    void codesForSceneFiltersByScene() {
        List<String> rdCodes = registry.codesForScene("rd");
        assertTrue(rdCodes.contains("doc-batch-import"));
        List<String> opsCodes = registry.codesForScene("ops");
        assertTrue(opsCodes.isEmpty(), () -> "ops 场景无手册: " + opsCodes);
    }
}
