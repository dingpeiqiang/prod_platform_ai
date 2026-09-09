package com.sitech.prodai.service.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询收敛策略（方案 §4.5，阶段 B1）：多轮收敛查询 SOP 的确定性规模判定单一实现。
 * <p>
 * 手册链路（query-ask）与动态编排共用：粗查命中规模超过直接呈现的舒适阈值时，
 * 先澄清收敛、再精查；至多 {@link #MAX_REFINE_ROUNDS} 轮，第 {@code MAX_REFINE_ROUNDS + 1}
 * 轮强制降维呈现（TOP N + 排序依据 + 总量声明），不无限追问。
 * <p>
 * 纯确定性代码：无 LLM、无 Spring 依赖——规模判断口径（多少条算多、追问哪几个维度）
 * 收拢在本类，规则变化只改一处（DRY）；澄清话术生成仍归表达层 LLM（听懂与说清是 LLM 本场）。
 */
public final class QueryRefinePolicy {

    /** 粗查命中 ≤ 该值：规模舒适，直接呈现 */
    public static final int OK_THRESHOLD = 5;

    /** 粗查命中 ≤ 该值：可呈现但建议追问收敛；> 该值：触顶走澄清 */
    public static final int REFINE_THRESHOLD = 20;

    /** 收敛澄清至多轮数：第 3 轮强制降维呈现兜底（独立于参数补全门的 MAX_CLARIFY_ROUNDS） */
    public static final int MAX_REFINE_ROUNDS = 2;

    /** 精查呈现上限（降维 TOP N） */
    public static final int REFINED_TOP_N = 10;

    /** 规模判定结论 */
    public enum Verdict {
        /** 命中规模舒适：直接呈现 */
        OK,
        /** 命中偏多：呈现 + 建议追问收敛 */
        REFINE,
        /** 命中过多：先澄清收敛，不直接全量呈现 */
        FORCE_SUMMARY
    }

    /** 收敛澄清维度（按优先级）：地市 > 资费档位 > 时间窗 > 关注维度 */
    public static final List<String> REFINE_DIMENSIONS = List.of("city", "monthly_fee", "time_window", "focus");

    /** 澄清参数名 → 业务展示名（追问文案与澄清契约共用） */
    private static final Map<String, String> DIMENSION_LABELS = Map.of(
            "city", "地市",
            "monthly_fee", "资费档位（如 39 元/59 元）",
            "time_window", "时间范围（如近 30 天上架）",
            "focus", "关注维度（资费/流量/权益/办理限制）");

    private QueryRefinePolicy() {
    }

    /**
     * 规模判定（确定性）：按命中条数三档分流。
     *
     * @param hits   粗查命中条数（负数视为未知，按 OK 处理不阻塞主链路）
     * @param rounds 已进行的收敛澄清轮数（0 = 首轮粗查）
     * @return 判定结论
     */
    public static Verdict judge(int hits, int rounds) {
        if (rounds >= MAX_REFINE_ROUNDS) {
            // 已问 2 轮仍未收敛：强制降维呈现，不再追问（第 3 轮兜底）
            return Verdict.OK;
        }
        if (hits < 0) {
            return Verdict.OK;
        }
        if (hits <= OK_THRESHOLD) {
            return Verdict.OK;
        }
        if (hits <= REFINE_THRESHOLD) {
            return Verdict.REFINE;
        }
        return Verdict.FORCE_SUMMARY;
    }

    /**
     * 判定本轮应追问的收敛维度：首轮问地市 + 资费档位（区分度最高），
     * 第二轮只问尚未补齐的维度（不重复追问已回答项）。
     *
     * @param answered 已回答的维度（用户澄清回传键，null 安全）
     * @return 待追问维度（最多 2 个；空 = 无可追问，走降维呈现）
     */
    public static List<String> nextDimensions(Map<String, Object> answered) {
        List<String> out = new ArrayList<>();
        for (String dim : REFINE_DIMENSIONS) {
            if (answered != null && answered.containsKey(dim)) {
                continue;
            }
            out.add(dim);
            if (out.size() >= 2) {
                break;
            }
        }
        return out;
    }

    /**
     * 构建收敛澄清契约（与 ParamCompletionGate.paramContract 同构：label/description/options），
     * 前端据此渲染选择题补参。
     *
     * @param dimensions 待追问维度
     * @return 参数名 → 契约视图（snake_case）
     */
    public static Map<String, Map<String, Object>> clarifyContracts(List<String> dimensions) {
        Map<String, Map<String, Object>> contracts = new LinkedHashMap<>();
        if (dimensions == null) {
            return contracts;
        }
        for (String dim : dimensions) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("label", DIMENSION_LABELS.getOrDefault(dim, dim));
            c.put("description", refineReason(dim));
            contracts.put(dim, c);
        }
        return contracts;
    }

    /**
     * 用户澄清答案 → 追加到查询问题的收敛子句（精查输入）：
     * 答案不进 SPARQL 参数面（NL→SPARQL 提取由本体服务既有链路承担），
     * 只拼接成自然语言追加句，如「，地市南京，月费 39 元以内」。
     *
     * @param baseQuestion 原始问题
     * @param answers      用户澄清回传（维度 → 值；仅取收敛维度键，其余不拼）
     * @return 追加后的精查问题（无有效答案时原样返回）
     */
    public static String mergeAnswer(String baseQuestion, Map<String, Object> answers) {
        if (baseQuestion == null || answers == null || answers.isEmpty()) {
            return baseQuestion;
        }
        StringBuilder sb = new StringBuilder(baseQuestion);
        for (String dim : REFINE_DIMENSIONS) {
            Object val = answers.get(dim);
            if (val == null || String.valueOf(val).isBlank()) {
                continue;
            }
            sb.append("，").append(DIMENSION_LABELS.getOrDefault(dim, dim))
                    .append("：").append(val);
        }
        return sb.toString();
    }

    /**
     * 降维呈现提示句（FORCE_SUMMARY / 精查结果注入表达层 prompt）：
     * TOP N + 排序依据 + 总量声明，不再逐条罗列全量。
     *
     * @param hits 总命中条数
     * @return 提示句（空串 = 无需降维）
     */
    public static String summaryPrompt(int hits) {
        if (hits < 0) {
            return "";
        }
        return "命中共 " + hits + " 条，只呈现前 " + REFINED_TOP_N
                + " 条（按相关度排序），并明确告知用户总量与筛选建议。";
    }

    private static String refineReason(String dim) {
        return switch (dim) {
            case "city" -> "按地市收窄可显著减少无关条目";
            case "monthly_fee" -> "按资费档位收窄可聚焦目标价位商品";
            case "time_window" -> "按上架时间收窄可聚焦近期商品";
            case "focus" -> "按关注维度呈现可精简输出要素";
            default -> "补充该信息可更精准命中";
        };
    }
}
