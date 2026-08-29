package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 产品配置模板注册中心（P1-2，独立类，禁止并入 ProductOntologyService）。
 * <p>职责：加载 {@code classpath:ontologies/templates/*.json}；执行 §4.7 校验
 * （template_id 唯一 / field_code 唯一 / derive_rules 引用字段存在 / compliance_bindings.rule_ids 已注册 / extends 无环）；
 * {@code extends} 父模板按 field_code 合并（子覆盖同名项）；版本缓存 + 热重载。
 * <p>enum_map 值域契约（设计方案 §4.4）：抽取与草稿统一存 display 值；
 * 报文投影时翻译为 value；无 enum_map 视为 display=value 透传；合规校验一律按显示值域执行。
 */
@Service
public class ProductTemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProductTemplateRegistry.class);
    private static final String TEMPLATE_LOCATION = "classpath*:ontologies/templates/*.json";
    private static final Pattern RULE_ID_PATTERN = Pattern.compile("R-(C|D|CONF)\\d{2,3}");

    /** P1 阶段合规规则注册面：Java R-C01~C09 / R-D01~D05 / R-CONF-001~002（与 ops_rules.json 对齐）。 */
    private static final Set<String> REGISTERED_RULE_IDS = Set.of(
            "R-C01", "R-C02", "R-C03", "R-C04", "R-C05", "R-C06", "R-C07", "R-C08", "R-C09",
            "R-D01", "R-D02", "R-D03", "R-D04", "R-D05",
            "R-CONF-001", "R-CONF-002");

    private final ObjectMapper objectMapper;

    /** 原始模板（template_id -> 原始 JSON）。 */
    private volatile Map<String, Map<String, Object>> rawTemplates = new ConcurrentHashMap<>();
    /** 合并继承后的生效视图（template_id -> resolved JSON）。 */
    private volatile Map<String, Map<String, Object>> resolvedTemplates = new ConcurrentHashMap<>();
    /** 校验报告（最近一次 load）。 */
    private volatile Map<String, Object> lastValidationReport = new LinkedHashMap<>();

    public ProductTemplateRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        load();
    }

    /** 全量加载 + 校验 + 继承合并；失败保留上一可用版本（last-known-good 语义）。 */
    public synchronized Map<String, Object> load() {
        Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(TEMPLATE_LOCATION);
            for (Resource resource : resources) {
                try {
                    Map<String, Object> template = objectMapper.readValue(
                            resource.getInputStream(), new TypeReference<Map<String, Object>>() { });
                    String templateId = String.valueOf(template.get("template_id"));
                    if (templateId == null || templateId.isBlank() || "null".equals(templateId)) {
                        errors.add("模板缺少 template_id: " + resource.getFilename());
                        continue;
                    }
                    if (raw.containsKey(templateId)) {
                        errors.add("template_id 重复: " + templateId);
                        continue;
                    }
                    raw.put(templateId, template);
                } catch (IOException e) {
                    errors.add("模板解析失败: " + resource.getFilename() + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            errors.add("模板目录扫描失败: " + e.getMessage());
        }

        Map<String, Map<String, Object>> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : raw.entrySet()) {
            String templateId = entry.getKey();
            List<String> templateErrors = validate(templateId, raw);
            if (!templateErrors.isEmpty()) {
                templateErrors.forEach(err -> errors.add("[" + templateId + "] " + err));
                continue;
            }
            resolved.put(templateId, mergeExtends(templateId, raw, new LinkedHashSet<>()));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalRaw", raw.size());
        report.put("totalResolved", resolved.size());
        report.put("errors", errors);
        if (resolved.isEmpty() && !raw.isEmpty()) {
            // 全部模板校验失败：保留 last-known-good，不置空
            log.error("[模板注册] 全部模板校验失败，保留上一可用版本；errors={}", errors);
            report.put("keptLastKnownGood", true);
        } else {
            this.rawTemplates = new ConcurrentHashMap<>(raw);
            this.resolvedTemplates = new ConcurrentHashMap<>(resolved);
            report.put("keptLastKnownGood", false);
        }
        this.lastValidationReport = report;
        log.info("[模板注册] 加载完成 raw={} resolved={} errors={}", raw.size(), resolved.size(), errors);
        return report;
    }

    /** 热重载（增量注册：新增产品只落地模板文件）。 */
    public synchronized Map<String, Object> reload() {
        return load();
    }

    /** 按品类码取合并后的生效模板。 */
    public Optional<Map<String, Object>> findByCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return Optional.empty();
        }
        Map<String, Map<String, Object>> resolved = resolvedTemplates;
        if (resolved.containsKey(categoryCode)) {
            return Optional.of(resolved.get(categoryCode));
        }
        return resolved.values().stream()
                .filter(t -> categoryCode.equals(String.valueOf(t.get("category_code"))))
                .findFirst();
    }

    /** 按类型取模板（别名 findByCategory，对齐 §6.1 API 契约）。 */
    public Optional<Map<String, Object>> findByType(String type) {
        return findByCategory(type);
    }

    /** 模板清单（摘要视图）。 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map<String, Object> template : resolvedTemplates.values()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("template_id", template.get("template_id"));
            summary.put("template_name", template.get("template_name"));
            summary.put("version", template.get("version"));
            summary.put("status", template.get("status"));
            summary.put("category_code", template.get("category_code"));
            summary.put("message_root_key", template.get("message_root_key"));
            summary.put("extends", template.get("extends"));
            summary.put("field_count", template.get("fields") instanceof List<?> l ? l.size() : 0);
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * matchers 兜底匹配（§7 智聊多模板命中裁决）：关键词命中计数 × priority 裁决，最高分胜出。
     * 返回命中的品类码；无命中返回 null。
     */
    public String matchCategory(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String best = null;
        long bestScore = -1;
        for (Map<String, Object> template : resolvedTemplates.values()) {
            Map<String, Object> matchers = castMap(template.get("matchers"));
            if (matchers == null || Boolean.TRUE.equals(matchers.get("abstract"))) {
                continue;
            }
            Object kwObj = matchers.get("keywords");
            if (!(kwObj instanceof List<?> keywords)) {
                continue;
            }
            long hits = keywords.stream()
                    .filter(k -> text.contains(String.valueOf(k)))
                    .count();
            if (hits <= 0) {
                continue;
            }
            long priority = matchers.get("priority") instanceof Number n ? n.longValue() : 0;
            long score = hits * 100 + priority;
            if (score > bestScore) {
                bestScore = score;
                best = String.valueOf(template.get("category_code"));
            }
        }
        return best;
    }

    public int count() {
        return resolvedTemplates.size();
    }

    public Map<String, Object> lastValidationReport() {
        return lastValidationReport;
    }

    // ------------------------------------------------------------------
    // 渲染视图（P1-3 下发）：DynamicForm 可直接消费的 schema
    // ------------------------------------------------------------------

    /**
     * 生成 §9.1 模板渲染 schema：sections + fields（含 enum/显隐/必填/单位/精度）+ derive_rules 渲染视图。
     * <p>渲染约定：field_class=readonly（查询返回）与 source=hidden（隐藏元素）不出现在表单；
     * enum_map 仅下发 display 值（草稿统一存显示值，投影时才翻译传输值，§4.4 契约）。
     */
    public Map<String, Object> buildFormSchema(String categoryCode) {
        Optional<Map<String, Object>> found = findByCategory(categoryCode);
        if (found.isEmpty()) {
            return null;
        }
        Map<String, Object> template = found.get();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("formName", template.get("template_name") + " 配置表单（模板 v" + template.get("version") + "）");
        schema.put("formCode", "offering_config");
        schema.put("categoryCode", template.get("category_code"));
        schema.put("messageRootKey", template.get("message_root_key"));
        schema.put("templateVersion", template.get("version"));
        schema.put("sections", template.get("sections"));
        schema.put("deriveRules", template.get("derive_rules"));
        schema.put("complianceBindings", template.get("compliance_bindings"));

        List<Map<String, Object>> formFields = new ArrayList<>();
        if (template.get("fields") instanceof List<?> fields) {
            for (Object f : fields) {
                if (!(f instanceof Map<?, ?> field)) {
                    continue;
                }
                String fieldClass = str(field.get("field_class"));
                String source = str(field.get("source"));
                if ("readonly".equals(fieldClass) || "hidden".equals(source)) {
                    continue;
                }
                formFields.add(toFormField(field));
            }
        }
        schema.put("fields", formFields);
        return schema;
    }

    private Map<String, Object> toFormField(Map<?, ?> field) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fieldCode", field.get("field_code"));
        out.put("fieldName", field.get("label"));
        out.put("fieldType", mapFieldType(str(field.get("type"))));
        out.put("required", Boolean.TRUE.equals(field.get("required")));
        Object defaultValue = field.get("default_value");
        if (defaultValue != null) {
            out.put("value", defaultValue);
        }
        if (field.get("unit") != null) {
            out.put("unit", field.get("unit"));
        }
        if (field.get("precision") instanceof Number precision) {
            out.put("precision", precision.intValue());
        }
        if (field.get("extract_hint") != null) {
            out.put("placeholder", field.get("extract_hint"));
        }
        if (field.get("visible_when") != null) {
            out.put("visibleWhen", field.get("visible_when"));
        }
        if (field.get("slot_aliases") != null) {
            out.put("slotAliases", field.get("slot_aliases"));
        }
        if (field.get("rule_description") != null) {
            out.put("ruleDescription", field.get("rule_description"));
        }
        out.put("section", field.get("section"));
        out.put("fieldClass", field.get("field_class"));
        // enum_map：草稿值域取 display（契约：无 enum_map 视为 display=value 透传）
        Map<String, Object> enumConfig = castMap(field.get("enum_config"));
        if (enumConfig != null && enumConfig.get("enum_map") instanceof List<?> enumMap) {
            List<Map<String, Object>> options = new ArrayList<>();
            for (Object item : enumMap) {
                if (item instanceof Map<?, ?> entry) {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("label", entry.get("display"));
                    option.put("value", entry.get("display"));
                    options.add(option);
                }
            }
            out.put("options", options);
        }
        return out;
    }

    private String mapFieldType(String type) {
        // DynamicForm 支持input/select/number/date/textarea；multiselect 映射为 select（multiple 语义由前端增强）
        return switch (type == null ? "input" : type) {
            case "select", "multiselect" -> "select";
            case "number" -> "number";
            case "date" -> "date";
            default -> "input";
        };
    }

    // ------------------------------------------------------------------
    // 校验（§4.7）
    // ------------------------------------------------------------------

    private List<String> validate(String templateId, Map<String, Map<String, Object>> raw) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> template = raw.get(templateId);

        // extends 父模板存在 + 无环
        String parent = str(template.get("extends"));
        if (parent != null && !parent.isBlank()) {
            if ("common".equals(str(template.get("category_code"))) && parent.isBlank()) {
                errors.add("公共模板 extends 不能为空");
            }
            if (!raw.containsKey(parent)) {
                errors.add("extends 父模板不存在: " + parent);
            }
            if (hasExtendsCycle(templateId, raw, new LinkedHashSet<>())) {
                errors.add("extends 存在环");
            }
        }

        // field_code 唯一（含合并视图：子覆盖父，但同模板内不得重复）
        Set<String> seen = new HashSet<>();
        Object fieldsObj = template.get("fields");
        if (fieldsObj instanceof List<?> fields) {
            for (Object f : fields) {
                if (!(f instanceof Map<?, ?> field)) {
                    errors.add("fields 含非法项（非对象）");
                    continue;
                }
                String code = str(field.get("field_code"));
                if (code == null || code.isBlank()) {
                    errors.add("字段缺少 field_code");
                    continue;
                }
                if (!seen.add(code)) {
                    errors.add("field_code 重复: " + code);
                }
            }
        } else {
            errors.add("fields 缺失或非法");
        }

        // derive_rules 引用字段存在（when 键 / visible / hidden / set_default 键）
        // 环 / 缺父已在上方报错，合并视图不可用时直接返回已收集错误，避免异常中断校验
        Set<String> knownFields;
        try {
            knownFields = mergedFieldCodes(templateId, raw);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return errors;
        }
        Object rulesObj = template.get("derive_rules");
        if (rulesObj instanceof List<?> rules) {
            for (Object r : rules) {
                if (!(r instanceof Map<?, ?> rule)) {
                    errors.add("derive_rules 含非法项（非对象）");
                    continue;
                }
                Object when = rule.get("when");
                if (when instanceof Map<?, ?> whenMap) {
                    for (Object key : whenMap.keySet()) {
                        if (!knownFields.contains(String.valueOf(key))) {
                            errors.add("derive_rules.when 引用未定义字段: " + key);
                        }
                    }
                }
                for (String listKey : List.of("visible", "hidden")) {
                    if (rule.get(listKey) instanceof List<?> list) {
                        for (Object target : list) {
                            if (!knownFields.contains(String.valueOf(target))) {
                                errors.add("derive_rules." + listKey + " 引用未定义字段: " + target);
                            }
                        }
                    }
                }
                if (rule.get("set_default") instanceof Map<?, ?> defaults) {
                    for (Object key : defaults.keySet()) {
                        if (!knownFields.contains(String.valueOf(key))) {
                            errors.add("derive_rules.set_default 引用未定义字段: " + key);
                        }
                    }
                }
            }
        }

        // compliance_bindings.rule_ids 已注册
        Map<String, Object> bindings = castMap(template.get("compliance_bindings"));
        if (bindings != null && bindings.get("rule_ids") instanceof List<?> ruleIds) {
            for (Object ruleId : ruleIds) {
                String id = String.valueOf(ruleId);
                if (!REGISTERED_RULE_IDS.contains(id) || !RULE_ID_PATTERN.matcher(id).matches()) {
                    errors.add("compliance_bindings 引用未注册规则: " + id);
                }
            }
        }

        // few_shot 键存在（模板 fields 的 slot_aliases 抽取提示必须可索引；few_shot 为 P2-2 抽取模板化预留键）
        if (template.get("few_shot") instanceof Map<?, ?> fewShot
                && fewShot.containsKey("examples")
                && !(fewShot.get("examples") instanceof List<?>)) {
            errors.add("few_shot.examples 必须为数组");
        }
        return errors;
    }

    private boolean hasExtendsCycle(String templateId, Map<String, Map<String, Object>> raw, Set<String> visiting) {
        if (!visiting.add(templateId)) {
            return true;
        }
        String parent = str(raw.get(templateId).get("extends"));
        if (parent == null || parent.isBlank()) {
            return false;
        }
        if (!raw.containsKey(parent)) {
            return false;
        }
        return hasExtendsCycle(parent, raw, visiting);
    }

    private Set<String> mergedFieldCodes(String templateId, Map<String, Map<String, Object>> raw) {
        Map<String, Object> merged = mergeExtends(templateId, raw, new LinkedHashSet<>());
        Set<String> codes = new LinkedHashSet<>();
        if (merged.get("fields") instanceof List<?> fields) {
            for (Object f : fields) {
                if (f instanceof Map<?, ?> field && field.get("field_code") != null) {
                    codes.add(String.valueOf(field.get("field_code")));
                }
            }
        }
        return codes;
    }

    // ------------------------------------------------------------------
    // 继承合并：extends 父模板字段按 field_code 合并，子模板覆盖同名项
    // ------------------------------------------------------------------

    private Map<String, Object> mergeExtends(String templateId, Map<String, Map<String, Object>> raw, Set<String> visiting) {
        Map<String, Object> template = raw.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        if (!visiting.add(templateId)) {
            throw new IllegalStateException("extends 环: " + visiting);
        }
        String parent = str(template.get("extends"));
        Map<String, Object> merged = new LinkedHashMap<>(template);
        if (parent != null && !parent.isBlank() && raw.containsKey(parent)) {
            Map<String, Object> parentMerged = mergeExtends(parent, raw, visiting);
            // sections 按 code 合并（子覆盖）
            merged.put("sections", mergeByCode(parentMerged.get("sections"), template.get("sections")));
            // fields 按 field_code 合并（子覆盖）
            merged.put("fields", mergeByCode(parentMerged.get("fields"), template.get("fields")));
            // derive_rules：父子串联（子规则优先执行）
            List<Object> rules = new ArrayList<>();
            if (template.get("derive_rules") instanceof List<?> childRules) {
                rules.addAll(childRules);
            }
            if (parentMerged.get("derive_rules") instanceof List<?> parentRules) {
                rules.addAll(parentRules);
            }
            merged.put("derive_rules", rules);
            // compliance_bindings / message_projection：map 浅合并（子覆盖）
            Map<String, Object> bindings = new LinkedHashMap<>(castOrEmpty(parentMerged.get("compliance_bindings")));
            bindings.putAll(castOrEmpty(template.get("compliance_bindings")));
            merged.put("compliance_bindings", bindings);
            Map<String, Object> projection = new LinkedHashMap<>(castOrEmpty(parentMerged.get("message_projection")));
            projection.putAll(castOrEmpty(template.get("message_projection")));
            merged.put("message_projection", projection);
        }
        return deepCopy(merged);
    }

    /** 按 code/field_code 键合并两个列表：child 项覆盖 parent 同名项，parent 其余保留在前。 */
    private List<Object> mergeByCode(Object parentObj, Object childObj) {
        Map<String, Object> byCode = new LinkedHashMap<>();
        List<String> parentOrder = new ArrayList<>();
        collectByCode(parentObj, byCode, parentOrder);
        List<String> childOrder = new ArrayList<>();
        collectByCode(childObj, byCode, childOrder);
        List<Object> merged = new ArrayList<>();
        for (String code : parentOrder) {
            merged.add(byCode.get(code));
        }
        for (String code : childOrder) {
            if (!parentOrder.contains(code)) {
                merged.add(byCode.get(code));
            }
        }
        return merged;
    }

    private void collectByCode(Object listObj, Map<String, Object> byCode, List<String> order) {
        if (!(listObj instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object code = map.containsKey("field_code") ? map.get("field_code") : map.get("code");
                if (code != null) {
                    byCode.put(String.valueOf(code), map);
                    order.add(String.valueOf(code));
                }
            }
        }
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(source),
                    new TypeReference<Map<String, Object>>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private Map<String, Object> castOrEmpty(Object value) {
        Map<String, Object> map = castMap(value);
        return map == null ? new LinkedHashMap<>() : map;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
