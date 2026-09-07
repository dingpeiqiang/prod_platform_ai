package com.sitech.prodai.service.agent.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void loadsChatConfigureAndDiscoverHistoryPlaybooks() {
        // 场景手册齐全：智读（doc-batch-import）/ 智聊（chat-configure）/ 智查（discover-history）
        assertNotNull(registry.get("chat-configure"), "智聊手册应从 classpath:playbooks 装载");
        assertNotNull(registry.get("discover-history"), "智查手册应从 classpath:playbooks 装载");
        assertTrue(registry.problems().isEmpty(), () -> "装载应零问题: " + registry.problems());
    }

    @Test
    void loadsOpsAnalysisPlaybook() {
        // 运营问诊手册（ops-analysis）：ops 场景固化工作流退役后由手册接管
        Map<String, Object> ops = registry.get("ops-analysis");
        assertNotNull(ops, "运营问诊手册应从 classpath:playbooks 装载");
        assertEquals("运营问诊分析", ops.get("title"));
        assertTrue(ops.get("steps") instanceof List<?> s && s.size() == 4,
                "运营问诊手册应 4 步（事实查询/归因/稽核/解释）");
        assertEquals("ops", ((Map<?, ?>) ops.get("applies_to")).get("scene"), "运营问诊手册适用 ops 场景");
        assertTrue(registry.problems().isEmpty(), () -> "装载应零问题: " + registry.problems());
    }

    @Test
    void chatConfigureAndDiscoverHaveStepsToolsTriggers() {
        Map<String, Object> chat = registry.get("chat-configure");
        assertEquals("对话式配置草稿生成", chat.get("title"));
        assertTrue(chat.get("steps") instanceof List<?> s && s.size() == 4, "智聊手册应 4 步（品类/生成/合规/开单）");
        assertEquals("rd", ((Map<?, ?>) chat.get("applies_to")).get("scene"), "智聊手册适用 rd 场景");

        Map<String, Object> discover = registry.get("discover-history");
        assertEquals("历史配置检索复用", discover.get("title"));
        assertTrue(discover.get("steps") instanceof List<?> s && s.size() == 3, "智查手册应 3 步（需求/检索/整理）");
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
        // 工具名单注入后重载：四本手册引用的工具均已注册 → 引用可解析、零问题
        registry.registerKnownTools(java.util.Set.of(
                "rd_file_parse", "rd_compliance", "rd_config_chat", "rd_config_discover",
                "sparql_query", "swrl_root_cause", "swrl_risk_audit", "ontology_explain"));
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
        // 四本手册全部被拦截（引用的工具一个都不可解析）
        assertNotNull(registry.problems().get("chat-configure"), "智聊手册引用不可解析时也应被拦截");
        assertNotNull(registry.problems().get("discover-history"), "智查手册引用不可解析时也应被拦截");
        assertNotNull(registry.problems().get("ops-analysis"), "运营问诊手册引用不可解析时也应被拦截");
    }

    // ── 路由消费（双消费①） ──

    @Test
    void routesByIntentDeclaration() {
        assertEquals("doc-batch-import", registry.route("rd", "RD_FILE_PARSE", List.of()),
                "意图命中 applies_to.intents → 返回手册 code");
        assertEquals("chat-configure", registry.route("rd", "RD_CONFIG_CHAT", List.of()),
                "智聊意图命中 → 返回智聊手册 code");
        assertEquals("discover-history", registry.route("rd", "RD_CONFIG_DISCOVER", List.of()),
                "智查意图命中 → 返回智查手册 code");
    }

    @Test
    void routesByToolDeclaration() {
        assertEquals("doc-batch-import", registry.route("rd", "SOME_OTHER_INTENT", List.of("rd_file_parse")),
                "工具命中 applies_to.tools 亦应命中");
        assertEquals("chat-configure", registry.route("rd", "SOME_OTHER_INTENT", List.of("rd_config_chat")),
                "rd_config_chat 命中智聊手册");
        assertEquals("discover-history", registry.route("rd", "SOME_OTHER_INTENT", List.of("rd_config_discover")),
                "rd_config_discover 命中智查手册");
    }

    @Test
    void routesOpsIntentsToOpsAnalysis() {
        // 运营问诊手册：ops 意图/工具命中 → 返回手册 code（固化工作流退役，路由回落动态编排）
        assertEquals("ops-analysis", registry.route("ops", "PRODUCT_OPS_REASON", List.of()),
                "归因意图命中运营问诊手册");
        assertEquals("ops-analysis", registry.route("ops", "PRODUCT_OPS_POLICY", List.of()),
                "稽核意图命中运营问诊手册");
        assertEquals("ops-analysis", registry.route("ops", "SOME_OTHER_INTENT", List.of("swrl_root_cause")),
                "ops 工具命中运营问诊手册");
    }

    @Test
    void sceneMismatchDoesNotRoute() {
        assertNull(registry.route("query", "RD_FILE_PARSE", List.of()),
                "场景不一致（query ≠ rd）→ 不路由");
        assertNull(registry.route("rd", "PRODUCT_OPS_REASON", List.of()),
                "运营问诊手册对 rd 场景不路由");
    }

    @Test
    void unrelatedIntentAndToolDoesNotRoute() {
        assertNull(registry.route("rd", "RD_DRAFT_MANAGE", List.of("rd_draft_manage")),
                "意图与工具均不在适用域 → 不路由");
    }

    // ── 触发词快筛（入口三级瀑布第一级） ──

    @Test
    void triggerHitRoutesDirectly() {
        assertEquals("doc-batch-import", registry.matchTrigger("rd", "帮我导入文档：方案.docx"),
                "话术含触发词「导入文档」→ 直达手册");
        assertEquals("chat-configure", registry.matchTrigger("rd", "帮我配置一个月费128的家庭套餐"),
                "智聊天话术「配置一个」→ 直达智聊手册");
        assertEquals("discover-history", registry.matchTrigger("rd", "找一下月费39的校园套餐"),
                "智查话术「找一下」→ 直达智查手册");
        assertEquals("ops-analysis", registry.matchTrigger("ops", "分析一下哪些商品有下架风险"),
                "运营问诊话术「下架风险」→ 直达运营问诊手册");
    }

    @Test
    void longestTriggerWinsWhenMultipleMatch() {
        // doc-batch-import 的触发词里「批量导入」比「导入」长 → 命中后者
        assertEquals("doc-batch-import", registry.matchTrigger("rd", "批量导入这批文档"));
    }

    @Test
    void triggerMissAndSceneMissReturnNull() {
        assertNull(registry.matchTrigger("rd", "上一轮的套餐叫什么"), "未含触发词 → 不快筛");
        assertNull(registry.matchTrigger("ops", "导入文档"), "rd 触发词对 ops 场景不快筛");
        assertNull(registry.matchTrigger("rd", "  "), "空话术 → 不快筛");
    }

    @Test
    void sceneMismatchBlocksTrigger() {
        // 智查触发词对 query 场景不快筛（手册声明 applies_to.scene=rd，档案调阅页走常规链路）
        assertNull(registry.matchTrigger("query", "找一下月费39的校园套餐"),
                "智查手册 scene=rd，query 场景话术不触发");
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
        assertTrue(rdCodes.contains("chat-configure"), "rd 场景应含智聊手册");
        assertTrue(rdCodes.contains("discover-history"), "rd 场景应含智查手册");
        List<String> opsCodes = registry.codesForScene("ops");
        assertTrue(opsCodes.contains("ops-analysis"), () -> "ops 场景应含运营问诊手册: " + opsCodes);
        assertFalse(opsCodes.contains("chat-configure"), "rd 手册对 ops 场景不可见");
    }
}
