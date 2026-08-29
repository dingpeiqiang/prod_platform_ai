package com.sitech.prodai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * derive_rules 引擎 vs 存量 Java inferFields 字段级 diff 工具（P2-3，§12.5 灰度评审件）。
 * <p>比较维度：draft 深比较（legacyOnly / engineAdditions / valueMismatches）、
 * appliedRules、recommendedTemplates、messageRootKey。
 * <ul>
 *   <li>{@code parityPassed}：核心推导面零漂移（legacyOnly/valueMismatches 为空且规则/模板/根键一致）
 *       —— P2-6 发布门禁判据；</li>
 *   <li>{@code engineAdditions}：引擎增量字段（fillSource=derive_rule 的模板 set_default 接管项），
 *       单独列示供评审归档，不计入 parity 判据；</li>
 *   <li>{@code strictlyIdentical}：parityPassed 且无任何增量（并存窗口终态）。</li>
 * </ul>
 * <p>纯函数无状态，供单测/SMOKE/发布评审复用。
 */
public final class DeriveDiffUtil {

    private DeriveDiffUtil() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> diff(Map<String, Object> legacyBody, Map<String, Object> engineBody) {
        Map<String, Object> legacyDraft = mapOrEmpty(legacyBody == null ? null : legacyBody.get("draft"));
        Map<String, Object> engineDraft = mapOrEmpty(engineBody == null ? null : engineBody.get("draft"));

        List<Map<String, Object>> legacyOnly = new ArrayList<>();
        List<Map<String, Object>> engineAdditions = new ArrayList<>();
        List<Map<String, Object>> valueMismatches = new ArrayList<>();

        Map<String, String> legacyFill = mapOrEmpty(legacyDraft.get("fillSources")).entrySet().stream()
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), String.valueOf(e.getValue())), Map::putAll);
        Map<String, String> engineFill = mapOrEmpty(engineDraft.get("fillSources")).entrySet().stream()
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), String.valueOf(e.getValue())), Map::putAll);

        TreeSet<String> allKeys = new TreeSet<>();
        for (String k : legacyDraft.keySet()) {
            allKeys.add(k);
        }
        for (String k : engineDraft.keySet()) {
            allKeys.add(k);
        }
        for (String key : allKeys) {
            if ("fillSources".equals(key)) {
                // 推导记账单：结构化比较（公共键漂移才计入 parity；引擎侧新增键与 engineAdditions 一致）
                continue;
            }
            boolean inLegacy = legacyDraft.containsKey(key);
            boolean inEngine = engineDraft.containsKey(key);
            if (inLegacy && !inEngine) {
                legacyOnly.add(row(key, legacyDraft.get(key), null, legacyFill.get(key)));
            } else if (!inLegacy && inEngine) {
                engineAdditions.add(row(key, null, engineDraft.get(key), engineFill.get(key)));
            } else if (!Objects.equals(legacyDraft.get(key), engineDraft.get(key))) {
                valueMismatches.add(row(key, legacyDraft.get(key), engineDraft.get(key),
                        legacyFill.get(key) + " -> " + engineFill.get(key)));
            }
        }

        List<String> fillSourcesDiff = new ArrayList<>();
        for (Map.Entry<String, String> e : legacyFill.entrySet()) {
            String engineSrc = engineFill.get(e.getKey());
            if (engineSrc == null) {
                fillSourcesDiff.add("legacyOnly: " + e.getKey() + "=" + e.getValue());
            } else if (!e.getValue().equals(engineSrc)) {
                fillSourcesDiff.add(e.getKey() + ": " + e.getValue() + " -> " + engineSrc);
            }
        }

        List<String> appliedRulesDiff = listDiff(legacyBody, engineBody, "appliedRules");
        List<String> recommendedTemplatesDiff = listDiff(legacyBody, engineBody, "recommendedTemplates");
        boolean messageRootKeyDiff = !Objects.equals(
                legacyBody == null ? null : legacyBody.get("messageRootKey"),
                engineBody == null ? null : engineBody.get("messageRootKey"));

        boolean parityPassed = legacyOnly.isEmpty() && valueMismatches.isEmpty() && fillSourcesDiff.isEmpty()
                && appliedRulesDiff.isEmpty() && recommendedTemplatesDiff.isEmpty() && !messageRootKeyDiff;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("parityPassed", parityPassed);
        report.put("strictlyIdentical", parityPassed && engineAdditions.isEmpty());
        report.put("legacyOnly", legacyOnly);
        report.put("engineAdditions", engineAdditions);
        report.put("valueMismatches", valueMismatches);
        report.put("fillSourcesDiff", fillSourcesDiff);
        report.put("appliedRulesDiff", appliedRulesDiff);
        report.put("recommendedTemplatesDiff", recommendedTemplatesDiff);
        report.put("messageRootKeyDiff", messageRootKeyDiff);
        return report;
    }

    /** 单用例 diff 报告摘要（供 SMOKE/评审日志聚合）。 */
    public static String summarize(String caseId, Map<String, Object> report) {
        return "case=" + caseId
                + " parityPassed=" + report.get("parityPassed")
                + " engineAdditions=" + ((List<?>) report.get("engineAdditions")).size()
                + " legacyOnly=" + ((List<?>) report.get("legacyOnly")).size()
                + " valueMismatches=" + ((List<?>) report.get("valueMismatches")).size();
    }

    private static Map<String, Object> row(String key, Object legacyValue, Object engineValue, String trace) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("field", key);
        row.put("legacyValue", legacyValue);
        row.put("engineValue", engineValue);
        row.put("trace", trace);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static List<String> listDiff(Map<String, Object> legacyBody, Map<String, Object> engineBody,
                                         String key) {
        List<?> legacy = legacyBody == null ? List.of() : asList(legacyBody.get(key));
        List<?> engine = engineBody == null ? List.of() : asList(engineBody.get(key));
        List<String> diffs = new ArrayList<>();
        for (Object v : legacy) {
            if (!engine.contains(v)) {
                diffs.add("legacyOnly: " + v);
            }
        }
        for (Object v : engine) {
            if (!legacy.contains(v)) {
                diffs.add("engineOnly: " + v);
            }
        }
        return diffs;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOrEmpty(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }
}
