package com.sitech.prodai.service.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询收敛策略（阶段 B1）单测：规模判定三档边界、收敛轮次封顶、
 * 维度推导优先级与已答去重、澄清契约结构、答案合并子句、降维提示口径。
 */
class QueryRefinePolicyTest {

    // ── 规模判定（judge） ──

    @Test
    void judgeReturnsOkWithinComfortThreshold() {
        assertEquals(QueryRefinePolicy.Verdict.OK, QueryRefinePolicy.judge(0, 0));
        assertEquals(QueryRefinePolicy.Verdict.OK, QueryRefinePolicy.judge(5, 0), "边界值 5：直接呈现");
    }

    @Test
    void judgeReturnsRefineForModerateHits() {
        assertEquals(QueryRefinePolicy.Verdict.REFINE, QueryRefinePolicy.judge(6, 0), "边界值 6：呈现 + 建议追问");
        assertEquals(QueryRefinePolicy.Verdict.REFINE, QueryRefinePolicy.judge(20, 0), "边界值 20：呈现 + 建议追问");
    }

    @Test
    void judgeReturnsForceSummaryBeyondThreshold() {
        assertEquals(QueryRefinePolicy.Verdict.FORCE_SUMMARY, QueryRefinePolicy.judge(21, 0), "边界值 21：先澄清收敛");
        assertEquals(QueryRefinePolicy.Verdict.FORCE_SUMMARY, QueryRefinePolicy.judge(92, 0));
    }

    @Test
    void judgeCapsRefineRoundsAndFallsBackToOk() {
        // 已问满 2 轮仍未收敛：第 3 轮强制降维呈现（OK），不再追问
        assertEquals(QueryRefinePolicy.Verdict.OK, QueryRefinePolicy.judge(92, 2));
        assertEquals(QueryRefinePolicy.Verdict.OK, QueryRefinePolicy.judge(21, 2));
    }

    @Test
    void unknownHitCountDoesNotBlock() {
        assertEquals(QueryRefinePolicy.Verdict.OK, QueryRefinePolicy.judge(-1, 0), "命中数未知（-1）按 OK 不阻塞主链路");
    }

    // ── 追问维度推导（nextDimensions） ──

    @Test
    void firstRoundAsksTopTwoDimensions() {
        List<String> dims = QueryRefinePolicy.nextDimensions(Map.of());
        assertEquals(List.of("city", "monthly_fee"), dims, "首轮问地市 + 资费档位（区分度最高）");
    }

    @Test
    void answeredDimensionsAreSkipped() {
        List<String> dims = QueryRefinePolicy.nextDimensions(Map.of("city", "南京", "monthly_fee", 39));
        assertEquals(List.of("time_window", "focus"), dims, "第二轮只问尚未补齐的维度");
    }

    @Test
    void allAnsweredYieldsEmpty() {
        Map<String, Object> all = Map.of("city", "南京", "monthly_fee", 39, "time_window", 30, "focus", "资费");
        assertTrue(QueryRefinePolicy.nextDimensions(all).isEmpty(), "四维全已答 → 无可追问，走降维呈现");
    }

    // ── 澄清契约（clarifyContracts） ──

    @Test
    void contractsCarryLabelAndReason() {
        Map<String, Map<String, Object>> contracts =
                QueryRefinePolicy.clarifyContracts(List.of("city", "monthly_fee"));
        assertEquals(2, contracts.size());
        assertEquals("地市", contracts.get("city").get("label"));
        assertNotNull(contracts.get("city").get("description"), "追问理由（为什么问）随契约下发");
        assertEquals("资费档位（如 39 元/59 元）", contracts.get("monthly_fee").get("label"));
    }

    // ── 答案合并（mergeAnswer） ──

    @Test
    void mergeAnswerAppendsRefineClauses() {
        String refined = QueryRefinePolicy.mergeAnswer("有哪些在售套餐",
                Map.of("city", "南京", "monthly_fee", "39 元"));
        assertTrue(refined.contains("有哪些在售套餐"), "原问题保留在前");
        assertTrue(refined.contains("地市：南京"), "地市答案追加为收敛子句");
        assertTrue(refined.contains("资费档位（如 39 元/59 元）：39 元"), "资费答案按维度标签拼接");
    }

    @Test
    void mergeAnswerIgnoresUnknownAndBlankValues() {
        String refined = QueryRefinePolicy.mergeAnswer("有哪些在售套餐",
                Map.of("focus", "资费", "other_key", "无关值", "city", "  "));
        assertFalse(refined.contains("other_key"), "非收敛维度键不拼入");
        assertFalse(refined.contains("地市"), "空白答案不拼入");
        assertTrue(refined.contains("关注维度（资费/流量/权益/办理限制）：资费"));
    }

    @Test
    void mergeAnswerNoopWithoutAnswers() {
        assertEquals("有哪些在售套餐", QueryRefinePolicy.mergeAnswer("有哪些在售套餐", null));
        assertEquals("有哪些在售套餐", QueryRefinePolicy.mergeAnswer("有哪些在售套餐", Map.of()));
        assertEquals(null, QueryRefinePolicy.mergeAnswer(null, Map.of("city", "南京")));
    }

    // ── 降维呈现提示（summaryPrompt） ──

    @Test
    void summaryPromptCarriesTopNAndTotal() {
        String prompt = QueryRefinePolicy.summaryPrompt(92);
        assertTrue(prompt.contains("92"), "总量声明如实");
        assertTrue(prompt.contains(String.valueOf(QueryRefinePolicy.REFINED_TOP_N)), "TOP N 口径");
        assertTrue(prompt.contains("排序"), "排序依据要求");
    }

    @Test
    void summaryPromptEmptyForUnknownHits() {
        assertEquals("", QueryRefinePolicy.summaryPrompt(-1));
    }

    // ── 常量语义 ──

    @Test
    void maxRefineRoundsIsTwo() {
        assertEquals(2, QueryRefinePolicy.MAX_REFINE_ROUNDS, "澄清至多 2 轮，第 3 轮强制降维（方案 §4.5）");
    }

    @Test
    void dimensionPriorityIsCityFeeWindowFocus() {
        assertEquals(List.of("city", "monthly_fee", "time_window", "focus"), QueryRefinePolicy.REFINE_DIMENSIONS);
    }
}
