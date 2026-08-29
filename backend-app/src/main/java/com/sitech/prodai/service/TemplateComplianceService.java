package com.sitech.prodai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 合规裁剪服务（P2-4 务实档，§11.4，独立类禁止并入 ProductOntologyService）。
 * <p>职责：
 * <ul>
 *   <li><b>bindings 裁剪</b>：合规执行后按模板 {@code compliance_bindings.rule_ids} 过滤
 *       R-C* issues——该品类适用的规则保留，未声明（裁剪面外）的规则剔除；
 *       存量 {@code checkCompliance} Java R-C* 主体逻辑<b>不动</b>（不动项）；</li>
 *   <li><b>轻量字段约束</b>：P2-1 {@link TemplateConstraintCompiler} 供给的约束元数据
 *       对草稿<b>已携带</b>的值做枚举值域/精度/互斥组合校验（§4.4 display 契约），
 *       产出 {@code templateIssues} 增量视图——并存窗口不并入 issues/compliancePass
 *       （§13.7 先验证后阻断，家庭/校园行为不漂移）；</li>
 *   <li><b>等价性报告</b>：kept issues 与存量 issues 逐条对比（ruleId/field/level/message
 *       结构不变）， {@code equivalence.issuesEquivalent} 为 P2-6 门禁与 P2-7 清理前置依据。</li>
 * </ul>
 * <p>输出与存量 body 同构（success/issues/compliancePass/appliedRules/canSubmit/messageRootKey），
 * 增量键：{@code bindingsApplied}/{@code cutIssues}/{@code templateIssues}/{@code equivalence}。
 */
@Service
public class TemplateComplianceService {

    private static final Logger log = LoggerFactory.getLogger(TemplateComplianceService.class);

    private final ProductOntologyService ontologyService;
    private final ProductTemplateRegistry templateRegistry;
    private final TemplateConstraintCompiler constraintCompiler;
    private final ConfigMessageProjector messageProjector;
    private final OpsRulesService opsRules;

    public TemplateComplianceService(ProductOntologyService ontologyService,
                                     ProductTemplateRegistry templateRegistry,
                                     TemplateConstraintCompiler constraintCompiler,
                                     ConfigMessageProjector messageProjector,
                                     OpsRulesService opsRules) {
        this.ontologyService = ontologyService;
        this.templateRegistry = templateRegistry;
        this.constraintCompiler = constraintCompiler;
        this.messageProjector = messageProjector;
        this.opsRules = opsRules;
    }

    /** 按品类模板裁剪面执行合规校验。graphOverride 透传存量（P1-7 SMOKE 参数化通道）。 */
    public Map<String, Object> checkComplianceByTemplate(Map<String, Object> draftInput,
                                                         Map<String, Object> graphOverride) {
        Map<String, Object> legacy = ontologyService.checkCompliance(draftInput, graphOverride);
        Map<String, Object> draft = messageProjector.applyCategoryDefaults(
                draftInput == null ? Map.of() : draftInput);
        Set<String> bindings = resolveBindings(draft);
        Map<String, Object> body = applyCut(legacy, bindings);
        body.put("templateIssues", templateFieldChecks(draft));
        return body;
    }

    /** 品类适用的合规规则面（compliance_bindings.rule_ids）；品类未识别返回空集=不裁剪。 */
    public Set<String> resolveBindings(Map<String, Object> draft) {
        String category = firstNonBlank(draft == null ? null : draft.get("categoryCode"),
                draft == null ? null : draft.get("messageRootKey"));
        if (category == null || category.isBlank()) {
            return Set.of();
        }
        return templateRegistry.findByCategory(category)
                .map(t -> t.get("compliance_bindings"))
                .filter(b -> b instanceof Map<?, ?>)
                .map(b -> ((Map<?, ?>) b).get("rule_ids"))
                .filter(ids -> ids instanceof List<?>)
                .map(ids -> ((List<?>) ids).stream().map(String::valueOf).collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    /**
     * 裁剪执行：issues 按 bindings 过滤（bindings 空=不裁剪），重算 compliancePass/appliedRules
     * （语义与存量一致：无 HIGH 且无 R-C06 → pass；pass 时 appliedRules=R-C08 开启则 ["R-C08"]），
     * 并附等价性对比报告。包级可见供单测直测。
     */
    Map<String, Object> applyCut(Map<String, Object> legacyBody, Set<String> bindings) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> legacyIssues = legacyBody.get("issues") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        List<Map<String, Object>> kept = bindings.isEmpty()
                ? legacyIssues : filterIssues(legacyIssues, bindings);
        List<Map<String, Object>> cut = legacyIssues.stream()
                .filter(i -> !kept.contains(i))
                .collect(Collectors.toList());

        boolean hasHigh = kept.stream().anyMatch(i -> "HIGH".equals(i.get("issueLevel")));
        boolean requiredOk = kept.stream().noneMatch(i -> "R-C06".equals(i.get("ruleId")));
        boolean compliancePass = !hasHigh && requiredOk;
        List<String> appliedRules = compliancePass
                ? (opsRules.isConfigEnabled("R-C08") ? List.of("R-C08") : List.of())
                : kept.stream().map(i -> String.valueOf(i.get("ruleId"))).distinct().sorted()
                        .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", Boolean.TRUE.equals(legacyBody.get("success")));
        body.put("issues", kept);
        body.put("compliancePass", compliancePass);
        body.put("appliedRules", appliedRules);
        body.put("canSubmit", compliancePass);
        body.put("messageRootKey", legacyBody.get("messageRootKey"));
        body.put("bindingsApplied", new ArrayList<>(bindings.stream().sorted().toList()));
        body.put("cutIssues", cut);
        body.put("equivalence", equivalence(legacyBody, body));
        return body;
    }

    /** 按品类适用面过滤 issues（裁剪=规则未在 bindings 声明）。 */
    static List<Map<String, Object>> filterIssues(List<Map<String, Object>> issues, Set<String> bindings) {
        return issues.stream()
                .filter(i -> bindings.contains(String.valueOf(i.get("ruleId"))))
                .collect(Collectors.toList());
    }

    /** 等价性对比（§12.5）：裁剪后报告 vs 存量报告，issue 结构（ruleId/field/level/message）逐条比对。 */
    private Map<String, Object> equivalence(Map<String, Object> legacyBody, Map<String, Object> cutBody) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> legacyIssues = legacyBody.get("issues") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keptIssues = cutBody.get("issues") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        List<String> mismatches = new ArrayList<>();
        if (legacyIssues.size() != keptIssues.size()) {
            mismatches.add("issues 数量不一致: legacy=" + legacyIssues.size() + " cut=" + keptIssues.size());
        } else {
            for (int i = 0; i < legacyIssues.size(); i++) {
                Map<String, Object> l = legacyIssues.get(i);
                Map<String, Object> k = keptIssues.get(i);
                for (String key : List.of("ruleId", "issueType", "issueLevel", "field", "message")) {
                    if (!java.util.Objects.equals(l.get(key), k.get(key))) {
                        mismatches.add("issues[" + i + "]."
                                + key + ": " + l.get(key) + " -> " + k.get(key));
                    }
                }
            }
        }
        if (!java.util.Objects.equals(legacyBody.get("compliancePass"), cutBody.get("compliancePass"))) {
            mismatches.add("compliancePass: " + legacyBody.get("compliancePass")
                    + " -> " + cutBody.get("compliancePass"));
        }
        if (!java.util.Objects.equals(legacyBody.get("appliedRules"), cutBody.get("appliedRules"))) {
            mismatches.add("appliedRules: " + legacyBody.get("appliedRules")
                    + " -> " + cutBody.get("appliedRules"));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("issuesEquivalent", mismatches.isEmpty());
        report.put("legacyIssueCount", legacyIssues.size());
        report.put("cutIssueCount", keptIssues.size());
        report.put("removedByCut", cutBody.get("cutIssues"));
        report.put("mismatches", mismatches);
        return report;
    }

    // ------------------------------------------------------------------
    // 轻量字段约束（P2-1 编译器供给；仅校验草稿已携带的值，缺不做必填阻断）
    // ------------------------------------------------------------------

    /**
     * 模板字段约束校验（§4.4 display 契约）：枚举值域 / 精度 / 互斥组合。
     * 产出独立 {@code templateIssues} 视图（ruleId=TEMPLATE_FIELD，level=LOW），
     * 并存窗口不计入 compliancePass——先验证后阻断（§13.7）。
     */
    private List<Map<String, Object>> templateFieldChecks(Map<String, Object> normDraft) {
        List<Map<String, Object>> issues = new ArrayList<>();
        String category = firstNonBlank(normDraft.get("categoryCode"), normDraft.get("messageRootKey"));
        if (category == null) {
            return issues;
        }
        Map<String, Object> compiled = constraintCompiler.compile(category);
        if (compiled == null || !(compiled.get("constraints") instanceof Map<?, ?> constraints)) {
            return issues;
        }
        for (Map.Entry<?, ?> entry : constraints.entrySet()) {
            String fieldCode = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> c) || !hasValue(normDraft.get(fieldCode))) {
                continue;
            }
            Object value = normDraft.get(fieldCode);
            checkEnum(fieldCode, c, value, issues);
            checkPrecision(fieldCode, c, value, issues);
            checkMutex(fieldCode, c, value, issues);
        }
        return issues;
    }

    /** 枚举值域（display 值域；multiselect 支持列表/逗号串）。 */
    private void checkEnum(String fieldCode, Map<?, ?> c, Object value,
                           List<Map<String, Object>> issues) {
        if (!(c.get("enum_values") instanceof List<?> allowed) || allowed.isEmpty()) {
            return;
        }
        for (String item : splitValues(value, Boolean.TRUE.equals(c.get("multi")))) {
            if (allowed.stream().noneMatch(v -> String.valueOf(v).equals(item))) {
                issues.add(templateIssue(fieldCode, "枚举值域", "值「" + item + "」不在模板值域内",
                        List.of("field=" + fieldCode, "value=" + item, "domain=" + allowed)));
            }
        }
    }

    /** 精度约束：input 限长（precision=最大字符数），number 限小数位（decimal_places）。 */
    private void checkPrecision(String fieldCode, Map<?, ?> c, Object value,
                                List<Map<String, Object>> issues) {
        String type = String.valueOf(c.get("type"));
        if ("number".equals(type)) {
            if (c.get("decimal_places") instanceof Number places) {
                String text = String.valueOf(value).trim();
                int dot = text.indexOf('.');
                int scale = dot < 0 ? 0 : text.length() - dot - 1;
                if (scale > places.intValue()) {
                    issues.add(templateIssue(fieldCode, "精度超限",
                            "小数位 " + scale + " 超过模板约束 " + places.intValue(),
                            List.of("field=" + fieldCode, "value=" + value)));
                }
            }
            return;
        }
        if (c.get("precision") instanceof Number precision && value != null
                && String.valueOf(value).length() > precision.intValue()) {
            issues.add(templateIssue(fieldCode, "长度超限",
                    "长度 " + String.valueOf(value).length() + " 超过模板约束 " + precision.intValue(),
                    List.of("field=" + fieldCode, "value=" + value)));
        }
    }

    /** 互斥组合约束（值域内互斥，如 chnClassLimit：营业前台 与 电子渠道/大掌柜）。 */
    private void checkMutex(String fieldCode, Map<?, ?> c, Object value,
                            List<Map<String, Object>> issues) {
        if (!(c.get("mutex_value_groups") instanceof List<?> groups) || groups.isEmpty()) {
            return;
        }
        Set<Integer> hitGroups = new LinkedHashSet<>();
        for (String item : splitValues(value, true)) {
            for (int g = 0; g < groups.size(); g++) {
                if (groups.get(g) instanceof List<?> members
                        && members.stream().anyMatch(m -> String.valueOf(m).equals(item))) {
                    hitGroups.add(g);
                }
            }
        }
        if (hitGroups.size() > 1) {
            issues.add(templateIssue(fieldCode, "互斥组合",
                    "取值跨互斥组 " + hitGroups + "，不可同时配置",
                    List.of("field=" + fieldCode, "value=" + value, "groups=" + groups)));
        }
    }

    private Map<String, Object> templateIssue(String fieldCode, String issueType, String message,
                                              List<String> evidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", "TEMPLATE_FIELD");
        row.put("issueType", issueType);
        row.put("issueLevel", "LOW");
        row.put("field", fieldCode);
        row.put("message", message);
        row.put("evidence", evidence);
        return row;
    }

    /** 单选仅拆列表/逗号串为单项；多选拆分全部成员。 */
    private List<String> splitValues(Object value, boolean multi) {
        if (value instanceof List<?> list) {
            return list.stream().map(v -> String.valueOf(v).trim()).filter(s -> !s.isBlank()).toList();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) {
            return List.of();
        }
        return multi && text.contains(",")
                ? Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList()
                : List.of(text);
    }

    private boolean hasValue(Object value) {
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return value != null && !String.valueOf(value).isBlank();
    }

    private String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v != null && !String.valueOf(v).isBlank() && !"null".equals(String.valueOf(v))) {
                return String.valueOf(v);
            }
        }
        return null;
    }
}
