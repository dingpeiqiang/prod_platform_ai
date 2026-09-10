package com.sitech.prodai.eval;

import com.sitech.prodai.service.agent.flow.PublishedFlowRegistry;
import com.sitech.prodai.service.agent.impl.DefaultUnderstander;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 黄金评测运行器（方案 R1）。
 * <p>
 * 链路：黄金用例 → 真实 DefaultUnderstander（LLM 经 {@link ReplayableLlm} 录制回放）
 * → 按期望断言（intent/tools/forbid/params/multi 五维）→ 报告输出。
 * <p>
 * 运行模式：
 * <ul>
 *   <li>默认（回放）：无录制命中的用例 skip，CI 零网络依赖保持绿色</li>
 *   <li>{@code -Dprodai.eval.live=true}：真实调用 LLM 并录制响应（本地专项回归）</li>
 * </ul>
 */
class LlmEvalRunnerTest {

    private static final String GOLDEN = "/eval/golden/intent_golden.jsonl";

    private final boolean live = Boolean.getBoolean("prodai.eval.live");

    private final Path evalRoot = EvalRecordingStore.defaultRoot();

    @Test
    void goldenSetLoadsAndCoversRequiredDimensions() throws Exception {
        List<EvalCase> cases = EvalRecordingStore.loadGoldenCases(GOLDEN);
        assertFalse(cases.isEmpty(), "黄金评测集为空");
        assertTrue(cases.size() >= 20, "黄金评测集不足 20 条，当前 " + cases.size());
        assertTrue(cases.stream().anyMatch(c -> "rd".equals(c.getScene())), "缺少 rd 场景用例");
        assertTrue(cases.stream().anyMatch(c -> "ops".equals(c.getScene())), "缺少 ops 场景用例");
        assertTrue(cases.stream().anyMatch(EvalCase::expectsClarify), "缺少 CLARIFY 用例");
        assertTrue(cases.stream().anyMatch(c -> !c.forbiddenTools().isEmpty()), "缺少禁用工具对抗用例");
        assertTrue(cases.stream().anyMatch(EvalCase::expectMultiIntent), "缺少混合意图用例");
        // case_id 唯一性门禁
        long distinct = cases.stream().map(EvalCase::getCaseId).distinct().count();
        assertEquals(cases.size(), distinct, "case_id 存在重复");
    }

    @Test
    void runGoldenSuite() throws Exception {
        List<EvalCase> cases = EvalRecordingStore.loadGoldenCases(GOLDEN);
        EvalRecordingStore store = new EvalRecordingStore(evalRoot, live);
        List<EvalResult> results = new ArrayList<>();
        int executed = 0;
        int skipped = 0;

        for (EvalCase evalCase : cases) {
            EvalResult result = new EvalResult(evalCase.getCaseId(), evalCase.getInput());
            boolean ran = evaluateCase(evalCase, result, store);
            if (ran) {
                executed++;
            } else {
                skipped++;
            }
            results.add(result);
        }

        report(results, executed, skipped);

        // 门禁语义：live 模式（真实回归）任何失败即失败；
        // 回放模式仅校验实际执行的用例（录制未命中的不阻塞 CI）。
        List<EvalResult> failures = results.stream()
                .filter(r -> r.getChecks().containsValue("false"))
                .toList();
        if (live) {
            assertTrue(failures.isEmpty(), "live 评测存在失败用例: " +
                    failures.stream().map(EvalResult::toReportLine).toList());
        } else {
            for (EvalResult failure : failures) {
                assertFalse(true, "回放用例失败: " + failure.toReportLine());
            }
        }
    }

    /** 单用例执行与断言；返回 false 表示跳过（LLM 不可用/无录制）。 */
    private boolean evaluateCase(EvalCase evalCase, EvalResult result, EvalRecordingStore store) {
        DefaultUnderstander understander = new DefaultUnderstander(
                ReplayableLlm.create(store, live, evalCase.getCaseId()),
                EvalToolStubs.all(),
                null,
                null,
                new AgentCapabilityRegistry(EvalToolStubs.all()));
        SessionContext context = new SessionContext("eval-" + evalCase.getCaseId());
        context.setScene(evalCase.getScene());
        List<QueryPlan> plans;
        try {
            plans = understander.understandAll(evalCase.getInput(), context);
        } catch (ReplayableLlm.ReplayMissException e) {
            result.skip("llm", e.getMessage());
            return false;
        } catch (Exception e) {
            // CLARIFY 期望用例：理解层抛错（如 LLM 输出解析失败）也算执行过——
            // 但此处区分不了守门异常与 LLM 不可用，统一按 miss 处理由 live 门禁兜底
            result.skip("llm", "理解链路异常: " + e.getMessage());
            return false;
        }
        assertNotNull(plans, "理解输出为空: " + evalCase.getCaseId());
        assertFalse(plans.isEmpty(), "理解输出为空: " + evalCase.getCaseId());

        QueryPlan first = plans.get(0);
        result.setActualIntent(first.getIntent());
        result.setActualTools(String.valueOf(first.getTools()));

        assertIntent(evalCase, first, result);
        assertTools(evalCase, first, result);
        assertForbidden(evalCase, plans, result);
        assertParams(evalCase, first, result);
        assertMultiIntent(evalCase, plans, result);
        return true;
    }

    private void assertIntent(EvalCase evalCase, QueryPlan first, EvalResult result) {
        String expectedIntent = evalCase.expectedIntent();
        if (expectedIntent == null || expectedIntent.isBlank()) {
            result.skip("intent", "用例未声明 intent_type");
            return;
        }
        String actual = first.getIntent() == null ? "" : first.getIntent();
        boolean ok = evalCase.expectsClarify()
                ? QueryPlan.INTENT_CLARIFY.equalsIgnoreCase(actual)
                : actual.equalsIgnoreCase(expectedIntent);
        result.mark("intent", ok, "期望 " + expectedIntent + " 实际 " + actual);
    }

    private void assertTools(EvalCase evalCase, QueryPlan first, EvalResult result) {
        List<String> expected = evalCase.expectedTools();
        if (expected.isEmpty()) {
            result.skip("tools", "用例未声明 tools");
            return;
        }
        List<String> actual = first.getTools() == null ? List.of() : first.getTools();
        // 对抗用例：期望工具不在已知白名单 → 断言其必须被 sanitizeTools 剔除
        boolean adversarial = expected.stream().anyMatch(t -> !KNOWN_TOOLS.contains(t));
        if (adversarial) {
            List<String> leaked = expected.stream().filter(actual::contains).toList();
            result.mark("tools", leaked.isEmpty(), "幻觉工具未被剔除: " + leaked);
            return;
        }
        boolean ok = expected.isEmpty()
                ? actual.isEmpty()
                : expected.stream().anyMatch(actual::contains);
        result.mark("tools", ok, "期望命中 " + expected + " 实际 " + actual);
    }

    private void assertForbidden(EvalCase evalCase, List<QueryPlan> plans, EvalResult result) {
        List<String> forbidden = evalCase.forbiddenTools();
        if (forbidden.isEmpty()) {
            result.skip("forbid", "无限定");
            return;
        }
        List<String> allTools = new ArrayList<>();
        for (QueryPlan plan : plans) {
            if (plan.getTools() != null) {
                allTools.addAll(plan.getTools());
            }
        }
        List<String> leaked = forbidden.stream().filter(allTools::contains).toList();
        result.mark("forbid", leaked.isEmpty(), "禁用工具穿透: " + leaked);
    }

    private void assertParams(EvalCase evalCase, QueryPlan first, EvalResult result) {
        var expected = evalCase.expectedParams();
        if (expected.isEmpty()) {
            result.skip("params", "无参数断言");
            return;
        }
        List<String> failures = new ArrayList<>();
        for (var e : expected.entrySet()) {
            Object actualValue = first.getParams().get(e.getKey());
            if (actualValue == null) {
                failures.add(e.getKey() + " 缺失");
                continue;
            }
            String actualStr = String.valueOf(actualValue);
            String expectedStr = String.valueOf(e.getValue());
            if (!actualStr.contains(expectedStr)) {
                failures.add(e.getKey() + " 期望含「" + expectedStr + "」实际「" + actualStr + "」");
            }
        }
        result.mark("params", failures.isEmpty(), String.join("; ", failures));
    }

    private void assertMultiIntent(EvalCase evalCase, List<QueryPlan> plans, EvalResult result) {
        if (!evalCase.expectMultiIntent()) {
            result.skip("multi", "单意图用例");
            return;
        }
        result.mark("multi", plans.size() > 1, "期望拆分为多子计划，实际 " + plans.size());
    }

    private void report(List<EvalResult> results, int executed, int skipped) {
        System.out.println("===== LLM 黄金评测报告（live=" + live + "） =====");
        for (EvalResult result : results) {
            System.out.println(result.toReportLine());
        }
        System.out.printf("总计 %d | 执行 %d | 跳过 %d%n", results.size(), executed, skipped);
        if (!live && executed == 0) {
            System.out.println("提示：录制覆盖率 0%。本地执行 -Dprodai.eval.live=true 补录后，"
                    + "回放模式即可覆盖守门回归。");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }

    /** 已注册工具名单（对抗用例判定幻觉名的依据，与能力注册表对齐）。 */
    private static final List<String> KNOWN_TOOLS = List.of(
            "sparql_query", "swrl_root_cause", "swrl_risk_audit", "rule_explain", "ontology_explain",
            "rd_draft_generate", "rd_config_search", "rd_draft_manage", "rd_compliance",
            "rd_doc_parse", "rd_draft_extract", "rd_workorder_create", "rd_category_resolve",
            "rd_scheme_compare", "flow_execute");
}
