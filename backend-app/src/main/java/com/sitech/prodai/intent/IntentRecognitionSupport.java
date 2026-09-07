package com.sitech.prodai.intent;

import java.util.Locale;

/**
 * 意图类型归一化（单源）。
 * <p>
 * 去旧留新：旧 intent 处理链路（meta/窄白名单/关键词降级/scene 默认）已随翻译层重构
 * 清理，仅保留理解层（DefaultUnderstander）仍在使用的 LLM 输出意图归一化。
 */
public final class IntentRecognitionSupport {

    private IntentRecognitionSupport() {}

    /** LLM 输出意图（自由文本/旧枚举）→ 规范意图类型；未知值原样小写返回。 */
    public static String normalizeIntentType(String intentType) {
        if (intentType == null || intentType.isBlank()) return "";
        String normalized = intentType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "query", "nl_query", "product_ops_query",
                 "market_insight", "analyze", "analysis" -> "product_ops_query";
            case "policy", "evaluate", "product_ops_policy",
                 "risk_audit", "online_check", "offering_ops_risk_audit" -> "product_ops_policy";
            case "reason", "explain", "product_ops_reason",
                 "root_cause", "offering_ops_root_cause" -> "product_ops_reason";
        case "monitor", "ops_monitor", "product_ops_monitor" -> "product_ops_monitor";
        // W5 意图收敛（方案 §7.2）：compare 意图并入 query——对比是查询的子形态，
        // scene='compare' 空白问题随扶正消解（统一走 query 场景的 query_reuse_v2 工作流）
        case "compare", "compare_state", "product_ops_compare",
             "what_if", "hypothesis" -> "product_ops_query";
            case "guide" -> "chat";
            default -> normalized;
        };
    }
}
