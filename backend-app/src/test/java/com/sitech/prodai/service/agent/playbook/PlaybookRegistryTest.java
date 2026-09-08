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
    void loadsOpsEntryPlaybooks() {
        // ops 四入口手册（market-insight/root-cause/risk-audit/online-check）：
        // 原单本 ops-analysis 按前端四场景入口拆分——每个入口各自的工具链收拢到各自手册
        Map<String, Object> market = registry.get("market-insight");
        assertNotNull(market, "市场洞察手册应从 classpath:playbooks 装载");
        assertEquals("市场洞察", market.get("title"));
        assertTrue(market.get("steps") instanceof List<?> ms && ms.size() == 2,
                "市场洞察手册应 2 步（事实查询/风险速览）");

        Map<String, Object> rootCause = registry.get("root-cause");
        assertNotNull(rootCause, "异动归因手册应装载");
        assertTrue(rootCause.get("steps") instanceof List<?> rs && rs.size() == 3,
                "异动归因手册应 3 步（查询/归因/解释）");

        Map<String, Object> riskAudit = registry.get("risk-audit");
        assertNotNull(riskAudit, "风险稽核手册应装载");
        assertTrue(riskAudit.get("steps") instanceof List<?> ks && ks.size() == 3,
                "风险稽核手册应 3 步（圈定/稽核/规则解释）");

        Map<String, Object> onlineCheck = registry.get("online-check");
        assertNotNull(onlineCheck, "立项研判手册应装载");
        assertTrue(onlineCheck.get("steps") instanceof List<?> os && os.size() == 3,
                "立项研判手册应 3 步（查询/门槛规则解释/概念解释）");
        for (String code : List.of("market-insight", "root-cause", "risk-audit", "online-check")) {
            assertEquals("ops", ((Map<?, ?>) registry.get(code).get("applies_to")).get("scene"),
                    code + " 应适用 ops 场景");
        }
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
        assertTrue(discover.get("steps") instanceof List<?> s && s.size() == 2, "智查手册应 2 步（检索/整理，需求解析内联检索）");
    }

    @Test
    void loadedPlaybookHasStepsToolsGuardrails() {
        Map<String, Object> book = registry.get("doc-batch-import");
        assertEquals("文档批量导入配置", book.get("title"));
        assertTrue(book.get("steps") instanceof List<?> steps && steps.size() >= 3, "应有完整步骤链");
        // 顶层 tools 已删除（工具配置独一份在 AgentTool 注册表）——工具调用在步骤 tool 字段
        assertFalse(book.containsKey("tools"), "顶层 tools 声明已退役（步骤 tool 是调用语句）");
        assertTrue(((List<?>) book.get("steps")).stream()
                        .allMatch(s -> s instanceof Map<?, ?> m && m.get("tool") != null
                                && !String.valueOf(m.get("tool")).isBlank()),
                "每步都应声明工具调用");
        assertTrue(book.get("guardrails") instanceof Map<?, ?>, "应声明护栏");
    }

    @Test
    void registersKnownToolsAndResolvesReferences() {
        // 工具名单注入后重载：七本手册引用的工具均已注册 → 引用可解析、零问题
        registry.registerKnownTools(java.util.Set.of(
                "rd_doc_parse", "rd_draft_extract", "rd_compliance", "rd_workorder_create",
                "rd_category_resolve", "rd_draft_generate", "rd_config_search",
                "sparql_query", "swrl_root_cause", "swrl_risk_audit", "ontology_explain", "rule_explain"));
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
        // rd 四本手册全部被拦截（引用的工具一个都不可解析）；ops 四本手册同样被拦截
        assertNotNull(registry.problems().get("chat-configure"), "智聊手册引用不可解析时也应被拦截");
        assertNotNull(registry.problems().get("discover-history"), "智查手册引用不可解析时也应被拦截");
        for (String code : List.of("market-insight", "root-cause", "risk-audit", "online-check")) {
            assertNotNull(registry.problems().get(code), code + " 手册引用不可解析时也应被拦截");
        }
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
    void routesOpsIntentsToOpsEntryPlaybooks() {
        // ops 四入口手册：ops 意图命中 → 返回手册 code（按入口拆分后各归其位）
        assertEquals("root-cause", registry.route("ops", "PRODUCT_OPS_REASON", List.of()),
                "归因意图命中异动归因手册");
        assertEquals("market-insight", registry.route("ops", "PRODUCT_OPS_QUERY", List.of()),
                "运营查询意图（analyze 归一化产物）命中市场洞察手册");
        assertEquals("market-insight", registry.route("ops", "PRODUCT_OPS_MONITOR", List.of()),
                "盯盘意图命中市场洞察手册（含风险速览双工具链）");
    }

    @Test
    void routeDisambiguatesSameIntentByUtterance() {
        // 立项/稽核同归 PRODUCT_OPS_POLICY——歧义不在注册序，而在 intent_guide 归口话术：
        // 携带用户话术后按各手册归口特征词面交集二跳判定（手册即意图定义源的落点），
        // 无话术（空串）才退化为注册序兜底
        assertEquals("risk-audit", registry.route("ops", "PRODUCT_OPS_POLICY", List.of(), ""),
                "无话术 → 注册序兜底（risk-audit 先注册）");
        assertEquals("risk-audit",
                registry.route("ops", "PRODUCT_OPS_POLICY", List.of(), "帮我对在架商品做一次风险稽核"),
                "稽核话术命中 risk-audit 归口特征「风险稽核」");
        assertEquals("risk-audit",
                registry.route("ops", "PRODUCT_OPS_POLICY", List.of(), "这些商品有没有下架风险"),
                "下架话术命中 risk-audit 归口特征「该不该下架/下架」——partial：下架风险特征词计分");
        assertEquals("online-check",
                registry.route("ops", "PRODUCT_OPS_POLICY", List.of(), "评估一下这个商品值不值得立项"),
                "立项话术命中 online-check 归口特征「立项」");
        assertEquals("online-check",
                registry.route("ops", "PRODUCT_OPS_POLICY", List.of(), "新品上线前做个准入评估"),
                "准入话术命中 online-check 归口特征「准入评估」");
    }

    @Test
    void routeMatchesIntentCaseInsensitively() {
        // 理解层归一化产物是小写（product_ops_query），手册声明是大写（PRODUCT_OPS_QUERY）——
        // 严格 equals 永不命中，导致手册意图升级机制失效（实测截图走动态编排的根因）
        assertEquals("market-insight", registry.route("ops", "product_ops_query", List.of()),
                "小写归一化意图应命中大写声明的手册 intents");
        assertEquals("root-cause", registry.route("ops", "Product_Ops_Reason", List.of()),
                "混合大小写同样命中（匹配忽略大小写）");
        assertEquals("doc-batch-import", registry.route("rd", "rd_file_parse", List.of()),
                "rd 场景小写意图同样命中（手册级意图码匹配忽略大小写）");    }

    @Test
    void sceneMismatchDoesNotRoute() {
        assertNull(registry.route("query", "RD_FILE_PARSE", List.of()),
                "场景不一致（query ≠ rd）→ 不路由");
        assertNull(registry.route("rd", "PRODUCT_OPS_REASON", List.of()),
                "ops 手册对 rd 场景不路由");
    }

    @Test
    void unrelatedIntentAndToolDoesNotRoute() {
        // 路由只认业务意图：工具名不再参与匹配（applies_to.tools 与 route toolHit 已退役），
        // 工具兜底（LLM 自选工具 ≠ 认领整本手册）不升级走手册链路
        assertNull(registry.route("rd", "RD_DRAFT_MANAGE", List.of("rd_draft_manage")),
                "意图与工具均不在适用域 → 不路由");
        assertNull(registry.route("rd", "SOME_OTHER_INTENT", List.of("rd_doc_parse")),
                "仅工具命中、意图未命中 → 不路由（工具名不具备业务分流权）");
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
        assertEquals("risk-audit", registry.matchTrigger("ops", "分析一下哪些商品有下架风险"),
                "稽核话术「下架风险」→ 直达风险稽核手册");
        assertEquals("market-insight", registry.matchTrigger("ops", "查一下上月经营数据"),
                "洞察话术「经营数据」→ 直达市场洞察手册");
        assertEquals("root-cause", registry.matchTrigger("ops", "做一次根因分析"),
                "归因话术「根因分析」→ 直达异动归因手册");
        assertEquals("online-check", registry.matchTrigger("ops", "评估一下这个商品的立项研判"),
                "立项话术「立项研判」→ 直达立项研判手册");
    }

    @Test
    void broadUtterancesMissTriggerByDesign() {
        // 宽泛话术不做触发词快筛（substring 误触发风险）——交给理解层 LLM 识别，
        // 意图归一化命中 applies_to.intents 后由编排层升级走手册直达链路
        assertNull(registry.matchTrigger("ops", "查一下上月哪些数据涨了"),
                "宽泛综合话术不快筛，留给 LLM 识别（意图升级路由）");
        assertNull(registry.matchTrigger("ops", "风险商品有哪些"), "宽泛词「风险商品」已从触发词移除");
        assertNull(registry.matchTrigger("ops", "帮我分析一下"), "宽泛词「分析一下」已从触发词移除");
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
        assertTrue(sop.contains("（工具：rd_doc_parse）"), () -> "应含工具标注: " + sop);
        assertTrue(sop.contains("护栏："), () -> "应含护栏段: " + sop);
    }

    @Test
    void renderSopIncludesIntentGuideWhenDeclared() {
        // 意图归口（手册 = 意图定义源）：随 SOP 下发，LLM 依据归口声明选意图码，
        // 不再自由发挥（实测曾输出 ANALYZE 自由意图，全靠归一化映射表修补）
        String sop = registry.renderSop("market-insight");
        assertNotNull(sop);
        assertTrue(sop.contains("意图归口"), () -> "声明了 intent_guide 应输出归口段: " + sop);
        assertTrue(sop.contains("PRODUCT_OPS_QUERY："), () -> "归口段应含意图码: " + sop);
        assertTrue(sop.contains("增长趋势"), () -> "归口段应含话术特征: " + sop);
        // 未声明 intent_guide 的手册不输出归口段
        String rdSop = registry.renderSop("doc-batch-import");
        assertFalse(rdSop.contains("意图归口"), "未声明归口的手册不应输出归口段");
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
        assertTrue(opsCodes.contains("market-insight"), () -> "ops 场景应含市场洞察手册: " + opsCodes);
        assertTrue(opsCodes.contains("root-cause"), () -> "ops 场景应含异动归因手册: " + opsCodes);
        assertTrue(opsCodes.contains("risk-audit"), () -> "ops 场景应含风险稽核手册: " + opsCodes);
        assertTrue(opsCodes.contains("online-check"), () -> "ops 场景应含立项研判手册: " + opsCodes);
        assertFalse(opsCodes.contains("chat-configure"), "rd 手册对 ops 场景不可见");
    }
}
