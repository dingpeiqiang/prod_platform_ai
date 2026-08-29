package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.ops.OpsExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-2 抽取层模板化回归：① 动态槽位集（基础键 ∪ 模板 draft 字段）；② prompt 动态拼装；
 * ③ 可配置 slotPatterns 补充抽取；④ 存量正则行为零漂移（P1-7 用例话术前后对比）。
 */
class OpsExtractionServiceTemplateTest {

    private ProdAiProperties properties;
    private OpsRulesService opsRules;
    private ProductTemplateRegistry registry;
    private ProductExtractionTemplateSupport templateSupport;
    private OpsExtractionService extractionService;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        properties = new ProdAiProperties();
        properties.getOntology().setDemoEnabled(true);
        properties.getOntology().setDataSource("classpath");
        properties.getOntology().setGraphPath("classpath:ontology/mock_graph.json");
        properties.getOntology().setRulesPath("classpath:ontology/ops_rules.json");
        properties.getOntology().setLlmExtractEnabled(false);

        opsRules = new OpsRulesService(mapper, resourceLoader, properties);
        opsRules.load();
        registry = new ProductTemplateRegistry(mapper);
        registry.init();
        templateSupport = new ProductExtractionTemplateSupport(registry);
        extractionService = new OpsExtractionService(mapper, properties, opsRules, templateSupport, Optional.empty());
    }

    @Test
    void extractableSlotKeysShouldUnionBaseAndTemplateDraftFields() {
        var keys = templateSupport.extractableSlotKeys();
        // 基础集（inferFields 硬编码依赖）
        assertTrue(keys.containsAll(ProductExtractionTemplateSupport.BASE_SLOT_KEYS));
        assertTrue(keys.contains("bizScenario") && keys.contains("monthlyFee"));
        // 模板 draft 字段（familyBasePrc/broadBandMainPrc 等）
        assertTrue(keys.contains("teamType"), "familyBasePrc.draft 字段 teamType 应入白名单");
        assertTrue(keys.contains("roleMax"), "familyBasePrc.draft 字段 roleMax 应入白名单");
        assertTrue(keys.contains("phoneMbrMax"), "模板 draft 字段 phoneMbrMax 应入白名单");
        // projection 字段不进抽取白名单
        assertFalse(keys.contains("payFlag"), "projection 字段 payFlag 不应入抽取白名单");
    }

    @Test
    void promptSectionShouldListDraftFieldsAndDefaultHints() {
        String section = templateSupport.buildPromptSection("familyBasePrc");
        assertTrue(section.contains("激活模板品类 familyBasePrc"), "应含品类头");
        assertTrue(section.contains("- teamType(家庭套餐类型,select)"), "draft 字段约束行");
        assertTrue(section.contains("全家福|F套餐"), "枚举 display 值域");
        assertTrue(section.contains("别名=套餐类型/家庭类型/全家福/F套餐"), "slot_aliases 拼装");
        assertTrue(section.contains("默认值（系统补全项，勿在抽取输出编造）："), "projection 默认值提示");
        assertTrue(section.contains("payFlag=是"), "默认值键值对");
        // 品类未识别：空段，prompt 保持存量形态
        assertEquals("", templateSupport.buildPromptSection(null));
        assertEquals("", templateSupport.buildPromptSection("notExistPrc"));
    }

    @Test
    void matchCategoryShouldFallbackByMatchers() {
        assertEquals("familyBasePrc", templateSupport.matchCategory("办理家庭融合套餐"));
        assertEquals("personMainPrc", templateSupport.matchCategory("开一个5G套餐"));
    }

    @Test
    void regexExtractionShouldKeepLegacyBehaviorUnchanged() {
        // P1-7 用例话术：存量行为零漂移（家庭融合/校园/5G/宽带）
        Map<String, Object> family = extractionService.parseSlotsByRegex("家庭融合套餐月费128元，2张卡，500M宽带");
        assertEquals("家庭融合", family.get("bizScenario"));
        assertEquals("家庭", family.get("targetUser"));
        assertEquals("fusion", family.get("offeringType"));
        assertEquals(128.0, family.get("monthlyFee"));
        assertEquals("500M", family.get("includeBroadband"));

        Map<String, Object> clearBind = extractionService.parseSlotsByRegex("家庭融合不加128，单独上");
        assertEquals(Boolean.TRUE, clearBind.get("clearBindExisting"));
        assertEquals("", clearBind.get("bindExistingMainPkg"));

        Map<String, Object> fiveG = extractionService.parseSlotsByRegex("5G套餐99元月费");
        assertEquals("5G个人主套餐", fiveG.get("bizScenario"));
        assertEquals(99.0, fiveG.get("monthlyFee"));
    }

    @Test
    void configuredSlotPatternsShouldSupplementNewVariants() {
        // 内置 DATA_PATTERN 不覆盖"流量10G"语序；slotPatterns 配置补充（ops_rules.extraction.slotPatterns）
        Map<String, Object> slots = extractionService.parseSlotsByRegex("套餐含流量10G 不限速");
        assertEquals("10GB", slots.get("includeData"), "可配置模式应补充抽取'流量10G'变体");
        // 内置正则优先：已含"GB流量"的话术走内置，不重复/覆盖
        Map<String, Object> builtin = extractionService.parseSlotsByRegex("含5GB流量 月费19元");
        assertEquals("5GB", builtin.get("includeData"));
    }

    @Test
    void llmMergeShouldAllowTemplateFieldsButRejectUnknownKeys() {
        // llmExtractEnabled=false 时不走 LLM；此用例直接验证白名单逻辑经 extractSlots 的 regex 引擎不破坏
        // LLM 路径白名单由单元级验证：allowedKeys 接受 teamType、拒绝任意键
        var keys = templateSupport.extractableSlotKeys();
        assertTrue(keys.contains("teamType"));
        assertFalse(keys.contains("__attacker__"));
        assertFalse(keys.contains("systemPrompt"));
    }
}
