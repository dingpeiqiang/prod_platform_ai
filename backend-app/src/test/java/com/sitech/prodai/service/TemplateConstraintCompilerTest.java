package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * P2-1 模板约束编译器测试：required/枚举值域/数值精度/显隐组合/互斥/component 容器六类约束生成。
 */
class TemplateConstraintCompilerTest {

    private ProductTemplateRegistry registry;
    private TemplateConstraintCompiler compiler;

    @BeforeEach
    void setUp() {
        registry = new ProductTemplateRegistry(new ObjectMapper());
        registry.init();
        compiler = new TemplateConstraintCompiler(registry);
    }

    @Test
    void compileShouldRequireAndConstrainEnumOnFamilyBase() {
        Map<String, Object> meta = compiler.compile("familyBasePrc");
        assertNotNull(meta, "familyBasePrc 应可编译");
        assertEquals("familyBasePrc", meta.get("category_code"));

        @SuppressWarnings("unchecked")
        Map<String, Object> constraints = (Map<String, Object>) meta.get("constraints");
        // 必填约束：模板显式 required 字段
        for (String required : List.of("teamType", "ifFloorsPrc", "payFlag", "roleMax", "phoneMbrMax", "kdMbrMax")) {
            Object c = constraints.get(required);
            assertTrue(c instanceof Map<?, ?> && Boolean.TRUE.equals(((Map<?, ?>) c).get("required")),
                    required + " 应为必填约束");
        }
        // 枚举值域按 display 契约（§4.4）
        @SuppressWarnings("unchecked")
        Map<String, Object> teamType = (Map<String, Object>) constraints.get("teamType");
        assertEquals(List.of("全家福", "F套餐"), teamType.get("enum_values"));
        assertEquals("1", ((Map<?, ?>) teamType.get("enum_value_map")).get("全家福"));
        // multiselect 标注 multi
        @SuppressWarnings("unchecked")
        Map<String, Object> chnClassLimit = (Map<String, Object>) constraints.get("chnClassLimit");
        assertEquals(Boolean.TRUE, chnClassLimit.get("multi"));
    }

    @Test
    void compileShouldConstrainNumericPrecisionAndComponentSection() {
        @SuppressWarnings("unchecked")
        Map<String, Object> constraints =
                ((Map<String, Object>) compiler.compile("familyBasePrc").get("constraints"));
        // 数值精度：roleMax precision=1（整数位宽）
        @SuppressWarnings("unchecked")
        Map<String, Object> roleMax = (Map<String, Object>) constraints.get("roleMax");
        assertEquals(1, roleMax.get("precision"));
        // 小数字段：phoneMbrFee decimal_places=2
        @SuppressWarnings("unchecked")
        Map<String, Object> phoneMbrFee = (Map<String, Object>) constraints.get("phoneMbrFee");
        assertEquals(2, phoneMbrFee.get("decimal_places"));
        // component section 成员字段标注 section_component + 容器字段清单
        assertFalse(roleMax.containsKey("section_component"), "familyInfo 非 component 容器，不应标注");
        @SuppressWarnings("unchecked")
        Map<String, Object> phoneMbrMax = (Map<String, Object>) constraints.get("phoneMbrMax");
        assertEquals(Boolean.TRUE, phoneMbrMax.get("section_component"));
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = compiler.compile("familyBasePrc");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> componentSections = (Map<String, List<String>>) meta.get("component_sections");
        assertTrue(componentSections.get("phoneMbrInfo").containsAll(List.of("phoneMbrMax", "phoneMbrMin", "freeMbrCnt")),
                "phoneMbrInfo 容器应聚合成员字段");
    }

    @Test
    void compileShouldBuildVisibleAndHiddenCompositeConstraints() {
        @SuppressWarnings("unchecked")
        Map<String, Object> constraints =
                ((Map<String, Object>) compiler.compile("familyBasePrc").get("constraints"));
        // 显式 visible_when 保留
        @SuppressWarnings("unchecked")
        Map<String, Object> fav1Method = (Map<String, Object>) constraints.get("fav1Method");
        assertEquals(Map.of("ifFloorsPrc", "是"), fav1Method.get("visible_when"));
        // derive_rules.hidden 编译为 hidden_when 条件数组（OR：任一满足即隐藏；顺序随 derive_rules 声明）
        @SuppressWarnings("unchecked")
        Map<String, Object> fav1ValidityValue = (Map<String, Object>) constraints.get("fav1ValidityVAlue");
        assertEquals(List.of(Map.of("ifFloorsPrc", "否"), Map.of("fav1Validity", "长期有效")),
                fav1ValidityValue.get("hidden_when"));
        // derive_rules.visible 不覆盖字段显式 visible_when
        assertEquals(Map.of("ifFloorsPrc", "是"), fav1Method.get("visible_when"));
    }

    @Test
    void compileShouldEmitMutexBindingForChannelLimit() {
        Map<String, Object> meta = compiler.compile("broadBandMainPrc");
        assertNotNull(meta, "broadBandMainPrc 应可编译（继承 commonBasePrc 互斥键）");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mutexBindings = (List<Map<String, Object>>) meta.get("mutex_bindings");
        assertEquals(1, mutexBindings.size(), "仅 chnClassLimit 声明值域互斥");
        assertEquals("chnClassLimit", mutexBindings.get(0).get("field"));
        assertEquals(List.of(List.of("营业前台"), List.of("电子渠道", "大掌柜")), mutexBindings.get(0).get("groups"));
    }

    @Test
    void compileShouldMergeParentFieldsAndMarkReadonly() {
        Map<String, Object> meta = compiler.compile("familyBasePrc");
        @SuppressWarnings("unchecked")
        Map<String, Object> constraints = (Map<String, Object>) meta.get("constraints");
        // 继承合并：父模板 commonBasePrc 字段进入约束集
        assertNotNull(constraints.get("channelScope"), "父模板字段 channelScope 应合并编译");
        assertNotNull(constraints.get("chnClassLimit"), "父模板字段 chnClassLimit 应合并编译");
        // readonly 字段标注（pricingId field_class=readonly）
        @SuppressWarnings("unchecked")
        Map<String, Object> pricingId = (Map<String, Object>) constraints.get("pricingId");
        assertEquals(Boolean.TRUE, pricingId.get("readonly"));
    }

    @Test
    void compileShouldReturnNullForUnknownCategory() {
        assertNull(compiler.compile("notExistPrc"));
    }

    @Test
    void compiledMetaShouldBeJsonSerializable() {
        Map<String, Object> meta = compiler.compile("familyBasePrc");
        assertNotNull(meta);
        String json = new ObjectMapper().valueToTree(meta).toString();
        assertFalse(json.isBlank());
        assertTrue(json.contains("\"enum_values\""));
        assertTrue(json.contains("\"mutex_value_groups\"") || json.contains("mutex_bindings"));
    }
}
