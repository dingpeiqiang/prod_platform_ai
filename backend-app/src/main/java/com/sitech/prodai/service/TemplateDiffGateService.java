package com.sitech.prodai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P2-6 回归门禁·双引擎 diff 运行器（设计方案 §13.4，独立类禁止并入 ProductOntologyService）。
 * <p>对 P1-7 回归用例集逐条执行 双引擎对比，产出字段级 diff 报告
 * （{@link DeriveDiffUtil}），并按判据裁决：
 * <ul>
 *   <li>字段级 diff 全空（{@code parityPassed}：legacyOnly/valueMismatches/fillSources/
 *       appliedRules/recommendedTemplates/messageRootKey 均无漂移）；</li>
 *   <li>期望 R-C* 命中一致（含于 parity 的 appliedRulesDiff）；</li>
 *   <li>期望报文节点一致（用例期望 messageNodes/messageNodePaths 在引擎侧报文逐一断言；
 *       存量侧由 P1-7 回归运行器保证）。</li>
 * </ul>
 * 三项全过为"通过"；该判据是 P2-7 删除 Java 分支的唯一前置条件。
 * <p>启用策略（§13.7 先验证后阻断）：阻断开关 {@code ops_rules.config.rules.R-GATE.enabled}；
 * 关闭时 diff 漂移仅记录报告（shadow），不产出门禁失败断言。
 */
@Service
public class TemplateDiffGateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateDiffGateService.class);

    private final ProductConfigRegressionService regressionService;
    private final ProductOntologyService productOntologyService;
    private final TemplateDeriveEngine deriveEngine;
    private final ConfigMessageProjector messageProjector;
    private final OpsRulesService opsRules;

    public TemplateDiffGateService(ProductConfigRegressionService regressionService,
                                   ProductOntologyService productOntologyService,
                                   TemplateDeriveEngine deriveEngine,
                                   ConfigMessageProjector messageProjector,
                                   OpsRulesService opsRules) {
        this.regressionService = regressionService;
        this.productOntologyService = productOntologyService;
        this.deriveEngine = deriveEngine;
        this.messageProjector = messageProjector;
        this.opsRules = opsRules;
    }

    /** 阻断开关：ops_rules.config.rules.R-GATE.enabled（关闭 = shadow 仅报告）。 */
    public boolean blocking() {
        return opsRules.isConfigEnabled("R-GATE");
    }

    /**
     * 全量双引擎 diff 报告（评审入口）。
     *
     * @param graphOverride pending 图谱（SMOKE 模式）；null 表示现行运行态
     */
    public Map<String, Object> runAll(Map<String, Object> graphOverride) {
        boolean blocking = blocking();
        List<Map<String, Object>> cases = regressionService.cases();
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Map<String, Object> c : cases) {
            Map<String, Object> result = runCase(c, graphOverride, blocking);
            results.add(result);
            failures.addAll(castListOfMaps(result.get("failures")));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gate", "p2_6_derive_diff_gate");
        body.put("blocking", blocking);
        body.put("total", results.size());
        body.put("parityPassedCount", results.stream().filter(r -> Boolean.TRUE.equals(r.get("parityPassed"))).count());
        body.put("failedCount", results.stream().filter(r -> !Boolean.TRUE.equals(r.get("passed"))).count());
        body.put("success", failures.isEmpty());
        body.put("failures", failures);
        body.put("cases", results);
        body.put("durationMs", System.currentTimeMillis() - start);
        log.info("[diff门禁] 双引擎对比完成: total={}, parityPassed={}, blocking={}",
                results.size(), body.get("parityPassedCount"), blocking);
        return body;
    }

    /**
     * SMOKE 断言聚合（§13.4 守卫回接）：P1-7 回归断言 + 双引擎 diff 门禁失败（阻断时）。
     */
    public List<Map<String, Object>> smokeFailures(Map<String, Object> graphOverride) {
        List<Map<String, Object>> failures = new ArrayList<>(
                regressionService.smokeAgainstGraph(graphOverride));
        Map<String, Object> diffReport = runAll(graphOverride);
        failures.addAll(castListOfMaps(diffReport.get("failures")));
        return failures;
    }

    // ------------------------------------------------------------------
    // 单用例：双引擎执行 + diff 裁决
    // ------------------------------------------------------------------

    private Map<String, Object> runCase(Map<String, Object> c, Map<String, Object> graphOverride,
                                        boolean blocking) {
        String caseId = str(c.get("case_id"));
        Map<String, Object> draftInput = castMap(c.get("draft"));
        Map<String, Object> slots = castMap(c.get("slots"));
        Map<String, Object> expected = castMap(c.get("expected"));
        List<Map<String, Object>> failures = new ArrayList<>();

        Map<String, Object> legacyBody = productOntologyService.inferFields(slots, draftInput, graphOverride);
        Map<String, Object> engineBody = deriveEngine.derive(slots, draftInput,
                graphOverride != null ? graphOverride : productOntologyService.loadGraph());

        // 判据一/二：字段级 diff 全空（含 R-C* 命中一致性）
        Map<String, Object> diff = DeriveDiffUtil.diff(legacyBody, engineBody);
        boolean parityPassed = Boolean.TRUE.equals(diff.get("parityPassed"));

        // 判据三：期望报文节点在引擎侧报文逐一成立（存量侧由 P1-7 回归运行器保证）
        Map<String, Object> engineMessage = messageProjector.toMessage(castMap(engineBody.get("draft")));
        for (Map.Entry<String, Object> e : castMap(expected.get("messageNodes")).entrySet()) {
            Object actual = messagePath(engineMessage, e.getKey());
            if (isEmpty(actual) || !normalizedEquals(actual, e.getValue())) {
                failures.add(failure(caseId, "engine_message_nodes", e.getKey(),
                        str(e.getValue()), isEmpty(actual) ? "<missing>" : str(actual)));
            }
        }
        for (String path : castList(expected.get("messageNodePaths"))) {
            if (isEmpty(messagePath(engineMessage, path))) {
                failures.add(failure(caseId, "engine_message_nodes", path, "<present>", "<missing>"));
            }
        }

        if (!parityPassed) {
            if (blocking) {
                failures.add(failure(caseId, "derive_diff", "parity", "全空",
                        "漂移: legacyOnly=" + countOf(diff.get("legacyOnly"))
                                + " valueMismatches=" + countOf(diff.get("valueMismatches"))
                                + " fillSourcesDiff=" + countOf(diff.get("fillSourcesDiff"))
                                + " appliedRulesDiff=" + diff.get("appliedRulesDiff")
                                + " messageRootKeyDiff=" + diff.get("messageRootKeyDiff")));
            } else {
                log.info("[diff门禁] shadow 漂移记录（不阻断）: {} -> {}", caseId, DeriveDiffUtil.summarize(caseId, diff));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", caseId);
        result.put("title", str(c.get("title")));
        result.put("parityPassed", parityPassed);
        result.put("strictlyIdentical", diff.get("strictlyIdentical"));
        result.put("passed", failures.isEmpty());
        result.put("failures", failures);
        result.put("engineAdditions", countOf(diff.get("engineAdditions")));
        return result;
    }

    private Object messagePath(Map<String, Object> message, String path) {
        if (message == null || path == null || path.isBlank()) {
            return null;
        }
        Map<String, Object> body = message;
        if (body.size() == 1) {
            Object inner = body.values().iterator().next();
            if (inner instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                body = cast;
            }
        }
        Object current = body;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = m.get(segment);
        }
        return current;
    }

    private boolean normalizedEquals(Object actual, Object expected) {
        String a = String.valueOf(actual).trim();
        String b = String.valueOf(expected).trim();
        if (a.endsWith(".0")) {
            a = a.substring(0, a.length() - 2);
        }
        if (b.endsWith(".0")) {
            b = b.substring(0, b.length() - 2);
        }
        return a.equals(b);
    }

    private Map<String, Object> failure(String caseId, String check, String target,
                                        String expected, String actual) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caseId", caseId);
        row.put("check", check);
        row.put("target", target);
        row.put("expected", expected);
        row.put("actual", actual);
        return row;
    }

    private int countOf(Object list) {
        return list instanceof List<?> l ? l.size() : 0;
    }

    private boolean isEmpty(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object raw) {
        return raw instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListOfMaps(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    private List<String> castList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(this::str).toList();
        }
        return List.of();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
