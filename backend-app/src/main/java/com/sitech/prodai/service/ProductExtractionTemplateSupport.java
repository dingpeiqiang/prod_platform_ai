package com.sitech.prodai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 模板驱动抽取支撑（P2-2，§11.2）：为 {@code OpsExtractionService} 提供模板化能力，
 * 自身保持无状态——模板数据一律经 {@link ProductTemplateRegistry} 合并继承后的生效视图。
 * <ul>
 *   <li>① SLOT_KEYS 动态化：{@link #extractableSlotKeys()} = 存量基础槽位集 ∪ 全部激活模板
 *       {@code field_class=draft} 字段（新增品类字段零代码扩槽）；</li>
 *   <li>② prompt 动态拼装：{@link #buildPromptSection(String)} 按 field_class 分组——draft 为抽取目标
 *       （含 slot_aliases/枚举值域/extract_hint），projection 仅提示默认值不进抽取输出；</li>
 *   <li>③ 品类感知：{@link #matchCategory(String)} 走模板 matchers 兜底（§9.4）。</li>
 * </ul>
 * <p>边界：存量基础槽位键为 {@code inferFields} 硬编码依赖，P2-3 derive_rules 引擎接管前保持不变。
 */
@Service
public class ProductExtractionTemplateSupport {

    /** 存量基础槽位键（P1 边界：inferFields 依赖，17 键冻结；模板字段为扩展集）。 */
    public static final Set<String> BASE_SLOT_KEYS = Set.of(
            "bizScenario", "targetUser", "offeringType", "monthlyFee", "oneTimeFee",
            "includeBroadband", "includeData", "includeVoice", "channelScope",
            "offeringName", "bindExistingMainPkg", "clearBindExisting",
            "hasContract", "contractMonths", "repeatable", "dependOn", "discountPercent");

    private final ProductTemplateRegistry registry;

    public ProductExtractionTemplateSupport(ProductTemplateRegistry registry) {
        this.registry = registry;
    }

    /** 品类感知（§9.4）：matchers 关键词命中计数 × priority 裁决；无命中返回 null。 */
    public String matchCategory(String text) {
        return registry.matchCategory(text);
    }

    /** 动态可抽取槽位键：基础集 ∪ 模板 draft 字段 field_code 并集（品类无关，白名单安全）。 */
    public Set<String> extractableSlotKeys() {
        Set<String> keys = new LinkedHashSet<>(BASE_SLOT_KEYS);
        for (Map<String, Object> template : registry.allResolved()) {
            if (template.get("fields") instanceof List<?> fields) {
                for (Object f : fields) {
                    if (f instanceof Map<?, ?> field && "draft".equals(str(field.get("field_class")))
                            && field.get("field_code") != null) {
                        keys.add(str(field.get("field_code")));
                    }
                }
            }
        }
        return keys;
    }

    /**
     * 模板抽取约束段（注入 LLM prompt）：draft 字段逐条列出抽取约束；
     * projection 默认值聚合提示"勿在抽取输出编造"。品类未识别返回空串（prompt 保持存量形态）。
     */
    public String buildPromptSection(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return "";
        }
        Optional<Map<String, Object>> found = registry.findByCategory(categoryCode);
        if (found.isEmpty()) {
            return "";
        }
        Map<String, Object> template = found.get();
        List<String> draftLines = new ArrayList<>();
        List<String> defaultHints = new ArrayList<>();
        collectFieldHints(template, draftLines, defaultHints);
        if (draftLines.isEmpty() && defaultHints.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("激活模板品类 ").append(categoryCode)
                .append("（").append(template.get("template_name"))
                .append(" v").append(template.get("version")).append("）\n");
        if (!draftLines.isEmpty()) {
            sb.append("模板扩展字段（field_code=含义）：\n").append(String.join("\n", draftLines)).append("\n");
        }
        if (!defaultHints.isEmpty()) {
            sb.append("默认值（系统补全项，勿在抽取输出编造）：").append(String.join("; ", defaultHints)).append("\n");
        }
        return sb.toString();
    }

    private void collectFieldHints(Map<String, Object> template, List<String> draftLines, List<String> defaultHints) {
        if (!(template.get("fields") instanceof List<?> fields)) {
            return;
        }
        for (Object f : fields) {
            if (!(f instanceof Map<?, ?> field)) {
                continue;
            }
            String code = str(field.get("field_code"));
            if ("draft".equals(str(field.get("field_class")))) {
                draftLines.add(fieldLine(field, code));
            } else if (field.get("default_value") != null && !"hidden".equals(str(field.get("source")))) {
                defaultHints.add(code + "=" + field.get("default_value"));
            }
        }
    }

    /** 单条 draft 字段约束：field_code(label,type): 别名=..；取值=..；提示=.. */
    private String fieldLine(Map<?, ?> field, String code) {
        StringBuilder line = new StringBuilder("- ").append(code)
                .append("(").append(field.get("label")).append(",")
                .append(field.get("type")).append(")");
        StringJoiner parts = new StringJoiner("；");
        if (field.get("slot_aliases") instanceof List<?> aliases && !aliases.isEmpty()) {
            StringJoiner aliasJoiner = new StringJoiner("/");
            aliases.forEach(a -> aliasJoiner.add(String.valueOf(a)));
            parts.add("别名=" + aliasJoiner);
        }
        String enumText = enumValueText(field);
        if (!enumText.isEmpty()) {
            parts.add("取值=" + enumText);
        }
        if (field.get("extract_hint") != null) {
            parts.add("提示=" + field.get("extract_hint"));
        }
        String detail = parts.toString();
        return detail.isEmpty() ? line.toString() : line.append(": ").append(detail).toString();
    }

    /** 枚举值域按 display 契约拼接（§4.4：抽取与草稿统一存 display 值）。 */
    private String enumValueText(Map<?, ?> field) {
        if (!(field.get("enum_config") instanceof Map<?, ?> enumConfig)
                || !(enumConfig.get("enum_map") instanceof List<?> enumMap)
                || enumMap.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("|");
        for (Object item : enumMap) {
            if (item instanceof Map<?, ?> entry && entry.get("display") != null) {
                joiner.add(String.valueOf(entry.get("display")));
            }
        }
        return joiner.toString();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
