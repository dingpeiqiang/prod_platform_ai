package com.sitech.prodai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 模板约束编译器（P2-1 务实档，§11.4）。
 * <p>职责：将合并继承后的模板编译为<b>轻量 Java 可消费的约束元数据</b>，供 P2-4 合规裁剪与 P2-3 derive_rules 引擎消费。
 * <b>不引入 SHACL 引擎依赖</b>（pom 无 shacl，全库零命中）；SHACL NodeShape 产出留 P3-3 演进档。
 * <p>生成规则（§P2-1）：
 * <ul>
 *   <li>{@code required} → 必填约束；</li>
 *   <li>{@code enum_config.static} → 枚举值域（契约 §4.4：校验按 display 值域，enum_value_map 保留投影翻译通道）；</li>
 *   <li>数值约束 → {@code precision}/{@code decimal_places}/{@code min}/{@code max}/{@code pattern}（模板提供即编译，预留通道）；</li>
 *   <li>{@code visible_when} + derive_rules 显隐 → 组合依赖约束（visible_when 单条件 / hidden_when 条件数组=任一满足即隐藏）；</li>
 *   <li>{@code mutex_value_groups}（值域内互斥，如 chnClassLimit 营业前台 vs 电子/大掌柜）→ 互斥组合约束；</li>
 *   <li>{@code section.component=true} → 子对象/列表容器约束（成员字段标注 {@code section_component}）。</li>
 * </ul>
 * <p>输出统一 snake_case；只读编译（无状态），模板变更由 Registry 热重载后即时生效。
 */
@Service
public class TemplateConstraintCompiler {

    private final ProductTemplateRegistry registry;

    public TemplateConstraintCompiler(ProductTemplateRegistry registry) {
        this.registry = registry;
    }

    /**
     * 按品类码编译约束元数据。
     *
     * @return 约束元数据；品类未识别返回 null（调用方自行降级）
     */
    public Map<String, Object> compile(String categoryCode) {
        Optional<Map<String, Object>> found = registry.findByCategory(categoryCode);
        return found.map(this::compileTemplate).orElse(null);
    }

    /**
     * 编译单个合并后的模板。产物结构：
     * <pre>{@code
     * {
     *   "template_id": "...", "template_name": "...", "version": "...", "category_code": "...",
     *   "constraints": { "<field_code>": { required/type/enum_values/enum_value_map/precision/decimal_places/
     *                                     min/max/pattern/multi/readonly/visible_when/hidden_when/
     *                                     mutex_value_groups/section/section_component/message_path } },
     *   "mutex_bindings": [ { "field": "...", "groups": [[..],[..]] } ],
     *   "component_sections": { "<section_code>": { "message_path": "...", "fields": [...] } }
     * }
     * }</pre>
     */
    public Map<String, Object> compileTemplate(Map<String, Object> template) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("template_id", template.get("template_id"));
        out.put("template_name", template.get("template_name"));
        out.put("version", template.get("version"));
        out.put("category_code", template.get("category_code"));

        Map<String, Boolean> componentSections = collectComponentSections(template);
        Map<String, List<String>> componentFields = new LinkedHashMap<>();
        Map<String, Object> constraints = new LinkedHashMap<>();
        if (template.get("fields") instanceof List<?> fields) {
            for (Object f : fields) {
                if (f instanceof Map<?, ?> field) {
                    constraints.put(String.valueOf(field.get("field_code")),
                            compileField(field, componentSections, componentFields));
                }
            }
        }
        compileHiddenWhen(template, constraints);

        out.put("constraints", constraints);
        out.put("mutex_bindings", collectMutexBindings(constraints));
        out.put("component_sections", componentFields);
        return out;
    }

    /** 单字段编译：基础 + 枚举值域 + 数值约束 + 组合约束。 */
    private Map<String, Object> compileField(Map<?, ?> field, Map<String, Boolean> componentSections,
                                             Map<String, List<String>> componentFields) {
        String code = String.valueOf(field.get("field_code"));
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("type", field.get("type"));
        c.put("required", Boolean.TRUE.equals(field.get("required")));
        c.put("field_class", field.get("field_class"));
        if (field.get("message_path") != null) {
            c.put("message_path", field.get("message_path"));
        }
        String section = str(field.get("section"));
        if (section != null) {
            c.put("section", section);
            if (Boolean.TRUE.equals(componentSections.get(section))) {
                c.put("section_component", true);
                componentFields.computeIfAbsent(section, k -> new ArrayList<>()).add(code);
            }
        }
        compileEnum(field, c);
        compileNumeric(field, c);
        compileComposite(field, c);
        return c;
    }

    /** 枚举值域（display 契约）+ display→value 投影翻译通道。 */
    private void compileEnum(Map<?, ?> field, Map<String, Object> c) {
        if (!(field.get("enum_config") instanceof Map<?, ?> enumConfig)) {
            return;
        }
        if (!(enumConfig.get("enum_map") instanceof List<?> enumMap) || enumMap.isEmpty()) {
            return;
        }
        List<Object> values = new ArrayList<>();
        Map<String, Object> valueMap = new LinkedHashMap<>();
        for (Object item : enumMap) {
            if (item instanceof Map<?, ?> entry && entry.get("display") != null) {
                values.add(entry.get("display"));
                if (entry.get("value") != null) {
                    valueMap.put(String.valueOf(entry.get("display")), entry.get("value"));
                }
            }
        }
        c.put("enum_values", values);
        if (!valueMap.isEmpty()) {
            c.put("enum_value_map", valueMap);
        }
        if ("multiselect".equals(str(field.get("type")))) {
            c.put("multi", true);
        }
    }

    /** 数值约束：precision/decimal_places 必编；min/max/pattern 预留通道（模板提供即透传）。 */
    private void compileNumeric(Map<?, ?> field, Map<String, Object> c) {
        if (field.get("precision") instanceof Number precision) {
            c.put("precision", precision.intValue());
        }
        if (field.get("decimal_places") instanceof Number decimalPlaces) {
            c.put("decimal_places", decimalPlaces.intValue());
        }
        Object min = field.get("min_value");
        Object max = field.get("max_value");
        if (min instanceof Number || max instanceof Number) {
            if (min instanceof Number) {
                c.put("min", min);
            }
            if (max instanceof Number) {
                c.put("max", max);
            }
        }
        if (field.get("pattern") != null) {
            c.put("pattern", field.get("pattern"));
        }
    }

    /** 组合约束：显式 visible_when / mutex_value_groups / readonly 标注。 */
    private void compileComposite(Map<?, ?> field, Map<String, Object> c) {
        if (field.get("visible_when") instanceof Map<?, ?> visibleWhen) {
            c.put("visible_when", visibleWhen);
        }
        if (field.get("mutex_value_groups") instanceof List<?> mutexGroups && !mutexGroups.isEmpty()) {
            c.put("mutex_value_groups", mutexGroups);
        }
        if ("readonly".equals(str(field.get("field_class")))) {
            c.put("readonly", true);
        }
    }

    /**
     * derive_rules 显隐编译：{@code {when, visible:[..]}} → 目标字段 visible_when（字段未显式声明时）；
     * {@code {when, hidden:[..]}} → 目标字段 hidden_when 数组（多条件=OR 任一满足即隐藏）。
     */
    private void compileHiddenWhen(Map<String, Object> template, Map<String, Object> constraints) {
        if (!(template.get("derive_rules") instanceof List<?> rules)) {
            return;
        }
        for (Object r : rules) {
            if (!(r instanceof Map<?, ?> rule) || !(rule.get("when") instanceof Map<?, ?> when)) {
                continue;
            }
            if (rule.get("hidden") instanceof List<?> hiddenTargets) {
                for (Object target : hiddenTargets) {
                    appendCondition(constraints, String.valueOf(target), "hidden_when", when);
                }
            }
            if (rule.get("visible") instanceof List<?> visibleTargets) {
                for (Object target : visibleTargets) {
                    Map<String, Object> c = constraintOf(constraints, String.valueOf(target));
                    if (c != null && !c.containsKey("visible_when")) {
                        c.put("visible_when", when);
                    }
                }
            }
        }
    }

    private void appendCondition(Map<String, Object> constraints, String target, String key, Map<?, ?> when) {
        Map<String, Object> c = constraintOf(constraints, target);
        if (c == null) {
            return;
        }
        if (!(c.get(key) instanceof List<?> existing)) {
            List<Object> conditions = new ArrayList<>();
            conditions.add(when);
            c.put(key, conditions);
            return;
        }
        if (!containsCondition(existing, when)) {
            @SuppressWarnings("unchecked")
            List<Object> conditions = (List<Object>) c.get(key);
            conditions.add(when);
        }
    }

    private boolean containsCondition(List<?> existing, Map<?, ?> when) {
        return existing.stream().anyMatch(cond -> cond instanceof Map<?, ?> m && m.equals(when));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> constraintOf(Map<String, Object> constraints, String fieldCode) {
        Object c = constraints.get(fieldCode);
        return c instanceof Map ? (Map<String, Object>) c : null;
    }

    /** 收集 component section（子对象/列表容器）。 */
    private Map<String, Boolean> collectComponentSections(Map<String, Object> template) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (template.get("sections") instanceof List<?> sections) {
            for (Object s : sections) {
                if (s instanceof Map<?, ?> section && Boolean.TRUE.equals(section.get("component"))) {
                    result.put(String.valueOf(section.get("code")), true);
                }
            }
        }
        return result;
    }

    /** 汇总互斥组合约束（值域内互斥 → 供 P2-4 合规校验执行）。 */
    private List<Map<String, Object>> collectMutexBindings(Map<String, Object> constraints) {
        List<Map<String, Object>> bindings = new ArrayList<>();
        for (Map.Entry<String, Object> entry : constraints.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> c && c.get("mutex_value_groups") instanceof List<?> groups) {
                Map<String, Object> binding = new LinkedHashMap<>();
                binding.put("field", entry.getKey());
                binding.put("groups", groups);
                bindings.add(binding);
            }
        }
        return bindings;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
