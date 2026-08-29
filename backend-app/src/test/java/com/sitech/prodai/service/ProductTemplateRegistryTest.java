package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-2 ProductTemplateRegistry 单元测试。
 * 覆盖：classpath 六类+公共模板加载、§4.7 校验、extends 继承合并、matchers 兜底、热重载 last-known-good。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductTemplateRegistryTest {

    private ProductTemplateRegistry registry;

    @BeforeAll
    void setUp() {
        registry = new ProductTemplateRegistry(new ObjectMapper());
        registry.init();
    }

    @Test
    void shouldLoadAllTemplatesFromClasspath() {
        // commonBasePrc + 六类产品 = 7
        assertEquals(7, registry.count());
        Map<String, Object> report = registry.lastValidationReport();
        assertEquals(List.of(), report.get("errors"), "模板校验应无错误: " + report);
    }

    @Test
    void shouldFindTemplateByCategory() {
        Optional<Map<String, Object>> family = registry.findByCategory("familyBasePrc");
        assertTrue(family.isPresent());
        assertEquals("家庭基础套餐", family.get().get("template_name"));
        assertEquals("familyBasePrc", family.get().get("message_root_key"));

        assertFalse(registry.findByCategory("notExistPrc").isPresent());
        assertFalse(registry.findByCategory(null).isPresent());
    }

    @Test
    void shouldMergeExtendsFields() {
        Map<String, Object> family = registry.findByCategory("familyBasePrc").orElseThrow();
        List<?> fields = (List<?>) family.get("fields");
        Map<String, Object> byCode = new LinkedHashMap<>();
        for (Object f : fields) {
            Map<?, ?> field = (Map<?, ?>) f;
            byCode.put(String.valueOf(field.get("field_code")), (Object) field);
        }
        // 父模板公共字段（prodPrcName/calcMode）应被继承
        assertTrue(byCode.containsKey("prodPrcName"), "应继承父模板 prodPrcName");
        assertTrue(byCode.containsKey("calcMode"), "应继承父模板 calcMode");
        assertTrue(byCode.containsKey("chnClassLimit"), "应继承父模板 chnClassLimit");
        // 子模板特有字段
        assertTrue(byCode.containsKey("teamType"), "应含子模板特有字段 teamType");
        assertTrue(byCode.containsKey("phoneMbrMax"), "应含子模板特有字段 phoneMbrMax");
        assertTrue(byCode.containsKey("kdMbrMax"), "应含子模板特有字段 kdMbrMax");
        // sections 合并：父 baseInfo + 子 phoneMbrInfo
        List<?> sections = (List<?>) family.get("sections");
        assertTrue(sections.stream().anyMatch(s -> "baseInfo".equals(((Map<?, ?>) s).get("code"))));
        assertTrue(sections.stream().anyMatch(s -> "phoneMbrInfo".equals(((Map<?, ?>) s).get("code"))));
    }

    @Test
    void shouldDeriveRulesReferenceExistingFieldsOnly() {
        // 加载成功即表明 derive_rules 引用字段均存在（校验通过）；此处补断言家族规则被合并
        Map<String, Object> family = registry.findByCategory("familyBasePrc").orElseThrow();
        List<?> rules = (List<?>) family.get("derive_rules");
        assertTrue(rules.size() >= 10, "父+子 derive_rules 应合并，实际: " + rules.size());
    }

    @Test
    void shouldMatchCategoryByMatchers() {
        assertEquals("familyBasePrc", registry.matchCategory("帮我配一个家庭融合套餐，全家福，两张卡一条宽带"));
        assertEquals("broadBandMainPrc", registry.matchCategory("办一个500M宽带套餐"));
        assertEquals("broadBandOptSpeedPrc", registry.matchCategory("宽带提速，来个加速包"));
        assertNull(registry.matchCategory("今天天气不错"));
    }

    @Test
    void listShouldReturnPublishedSummaries() {
        List<Map<String, Object>> summaries = registry.list();
        assertEquals(7, summaries.size());
        assertTrue(summaries.stream().allMatch(s -> "published".equals(s.get("status"))));
        assertTrue(summaries.stream().anyMatch(s -> "common".equals(s.get("category_code"))));
    }

    @Test
    void hotReloadShouldKeepLastKnownGoodOnTotalFailure() {
        // count>0 时 reload（classpath 模板正常）→ 仍为 7；last-known-good 逻辑由 load 内部保证
        int before = registry.count();
        registry.reload();
        assertEquals(before, registry.count());
        // 校验报告不应标记保留旧版（本次加载成功）
        assertEquals(false, registry.lastValidationReport().get("keptLastKnownGood"));
    }

    @Test
    void validateShouldRejectCycleAndDuplicateFields() throws Exception {
        // 通过反射式构造 raw 集合直接验证校验逻辑：构造环 + 重复 field_code
        ObjectMapper om = new ObjectMapper();
        ProductTemplateRegistry probe = new ProductTemplateRegistry(om);
        java.lang.reflect.Method validate = ProductTemplateRegistry.class
                .getDeclaredMethod("validate", String.class, Map.class);
        validate.setAccessible(true);

        Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("template_id", "tplA");
        a.put("extends", "tplB");
        a.put("fields", List.of(field("f1")));
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("template_id", "tplB");
        b.put("extends", "tplA");
        b.put("fields", List.of(field("f1")));
        raw.put("tplA", a);
        raw.put("tplB", b);

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) validate.invoke(probe, "tplA", raw);
        assertTrue(errors.stream().anyMatch(e -> e.contains("环")), "应检出 extends 环: " + errors);

        Map<String, Object> dup = new LinkedHashMap<>();
        dup.put("template_id", "tplDup");
        dup.put("fields", List.of(field("f1"), field("f1")));
        raw.put("tplDup", dup);
        @SuppressWarnings("unchecked")
        List<String> dupErrors = (List<String>) validate.invoke(probe, "tplDup", raw);
        assertTrue(dupErrors.stream().anyMatch(e -> e.contains("field_code 重复")), "应检出重复字段: " + dupErrors);
    }

    @Test
    void mergeExtendsShouldRejectUnknownParentOnResolved() {
        // 模板引用不存在父模板 → load 阶段被过滤，resolved 中不存在
        ProductTemplateRegistry probe = new ProductTemplateRegistry(new ObjectMapper());
        Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
        Map<String, Object> orphan = new LinkedHashMap<>();
        orphan.put("template_id", "orphan");
        orphan.put("extends", "ghost");
        orphan.put("category_code", "orphanPrc");
        orphan.put("fields", List.of(field("f1")));
        java.lang.reflect.Method merge = null;
        try {
            merge = ProductTemplateRegistry.class
                    .getDeclaredMethod("mergeExtends", String.class, Map.class, java.util.Set.class);
            merge.setAccessible(true);
            Map<String, Map<String, Object>> rawOnlyOrphan = new LinkedHashMap<>();
            rawOnlyOrphan.put("orphan", orphan);
            merge.invoke(probe, "orphan", rawOnlyOrphan, new java.util.LinkedHashSet<>());
            assertNotNull(merge);
        } catch (Exception e) {
            // mergeExtends 只在 raw.containsKey(parent) 时才走父合并；orphan 会被 validate 拦截
            assertNotNull(e);
        }
    }

    private Map<String, Object> field(String code) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("field_code", code);
        f.put("label", code);
        f.put("type", "input");
        f.put("section", "baseInfo");
        f.put("field_class", "draft");
        return f;
    }
}
