package com.sitech.prodai.intent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 意图识别辅助：meta 优先、窄白名单加速、关键词仅作 LLM 失败降级。
 * <p>
 * 流水线约定：meta →（可选）窄白名单 → LLM → 关键词 fallback → scene 默认（仅空输入/空结果）。
 * 禁止「任意文本 + ops scene」强制业务意图。
 */
public final class IntentRecognitionSupport {

    public static final String SOURCE_META = "meta";
    public static final String SOURCE_WHITELIST = "whitelist";
    public static final String SOURCE_LLM = "llm";
    public static final String SOURCE_FALLBACK = "fallback";
    public static final String SOURCE_SCENE_DEFAULT = "scene_default";

    /** 极窄短指令：全文精确匹配才可跳过意图 LLM。 */
    private static final Set<String> MONITOR_SHORT_COMMANDS = Set.of(
            "打开运营监控",
            "打开运营监控告警列表",
            "查看告警列表",
            "打开告警列表",
            "打开监控看板",
            "查看监控看板"
    );

    private IntentRecognitionSupport() {}

    /**
     * 元意图：只要使用说明 / 不要执行业务。命中后必须走 chat，禁止业务 Handler。
     */
    public static boolean isMetaGuideRequest(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.trim();
        return containsAny(t,
                "使用指导", "使用说明", "操作步骤", "怎么用", "如何使用", "使用手册",
                "只输出使用说明", "仅输出使用说明", "只给使用说明", "只要使用说明",
                "不要直接执行", "不要执行", "勿执行", "不要生成配置结果",
                "不要执行该场景", "不要直接执行该场景", "仅说明", "只要说明");
    }

    /**
     * 窄白名单加速：仅精确短指令。
     */
    public static Map<String, Object> tryNarrowWhitelist(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty() || t.length() > 24) return null;
        if (MONITOR_SHORT_COMMANDS.contains(t)) {
            return intentResult("product_ops_monitor", "ops_monitor", 0.95, SOURCE_WHITELIST, t);
        }
        return null;
    }

    /**
     * LLM 不可用 / 解析失败时的关键词降级。不含宽 scene 强制。
     */
    public static Map<String, Object> tryKeywordFallback(String text, String scene) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (isMetaGuideRequest(text)) {
            return chatMetaResult();
        }
        String t = text.trim();
        Map<String, Object> fields = new LinkedHashMap<>();

        if (containsAny(t, "运营监控", "告警列表", "查看告警", "监控看板", "异动告警", "打开运营监控")) {
            fields.put("question", t);
            return intentResult("product_ops_monitor", "ops_monitor", 0.55, SOURCE_FALLBACK, fields);
        }
        if (containsAny(t, "根因", "异动", "下滑", "下降", "环比", "归因", "为何下降", "为什么跌", "收入下滑")
                && !containsAny(t, "假设", "如果", "方案A", "方案B")) {
            fields.put("target", t);
            return intentResult("product_ops_reason", "root_cause", 0.55, SOURCE_FALLBACK, fields);
        }
        if (containsAny(t, "零元", "0元", "零资费", "风险稽核", "优胜劣汰", "建议下架", "长期零销", "筛查风险")
                && !containsAny(t, "假设", "如果", "对比", "方案A", "方案B", "推演", "改价后")) {
            fields.put("question", t);
            return intentResult("product_ops_policy", "risk_audit", 0.55, SOURCE_FALLBACK, fields);
        }
        if (containsAny(t, "假设", "如果", "多方案", "方案对比", "方案A", "方案B", "对比一下",
                "改价", "下调后", "what if", "推演", "市场规模改为", "放宽门槛")) {
            fields.put("question", t);
            return intentResult("product_ops_compare", "compare", 0.55, SOURCE_FALLBACK, fields);
        }
        if (containsAny(t, "立项", "上线门槛", "能否通过审核", "新品研判", "立项研判")
                && !containsAny(t, "假设", "如果", "方案A", "方案B", "对比")) {
            fields.put("question", t);
            return intentResult("product_ops_policy", "online_check", 0.55, SOURCE_FALLBACK, fields);
        }
        if (containsAny(t, "查一下", "查询", "有哪些", "在售", "列出", "检索", "SPARQL", "图谱里",
                "增长趋势", "市场洞察", "竞品")) {
            fields.put("question", t);
            return intentResult("product_ops_query", "query", 0.55, SOURCE_FALLBACK, fields);
        }
        if (containsAny(t, "家庭融合", "校园", "配置一个", "帮我配", "融合套餐", "批量导入", "一文多包")) {
            String byScene = resolveDefaultIntentByScene(scene);
            String intentType = byScene.isEmpty() || "chat".equals(byScene) ? "form" : byScene;
            Map<String, Object> data = intentResult(intentType, "generate", 0.5, SOURCE_FALLBACK, Map.of("question", t));
            if ("form".equals(intentType)) {
                data.put("formCode", "offering_config");
                data.put("form_code", "offering_config");
            }
            return data;
        }
        return null;
    }

    /** 空输入时按 UI scene 给默认意图（不作伪装高置信关键词）。 */
    public static Map<String, Object> resolveBlankInputByScene(String scene) {
        String byScene = resolveDefaultIntentByScene(scene);
        if (byScene.isEmpty()) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("intentType", byScene);
        data.put("action", defaultActionForScene(scene, byScene));
        data.put("confidence", 0.6);
        data.put("source", SOURCE_SCENE_DEFAULT);
        return data;
    }

    public static Map<String, Object> chatMetaResult() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("intentType", "chat");
        data.put("action", "guide");
        data.put("confidence", 0.99);
        data.put("source", SOURCE_META);
        return data;
    }

    public static String resolveDefaultIntentByScene(String scene) {
        if (scene == null || scene.isBlank()) return "";
        String normalized = scene.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "rd", "rd_center", "rd_offering_config",
                 "rd_tariff_filing", "rd.chat", "rd.import",
                 "offering_config", "offering_config_chat",
                 "offering_config_batch",
                 "tariff_filing_apply", "tariff_filing_apply_v2" -> "form";
            case "query", "market", "market_insight",
                 "offering_ops_center", "offering_ops_analysis",
                 "offering_ops_query",
                 "tariff_center", "tariff_filing" -> "product_ops_query";
            case "policy", "risk", "audit",
                 "risk_audit",
                 "ops", "ops_center", "ops_insight",
                 "offering_ops_risk_audit" -> "product_ops_policy";
            case "online", "online_check" -> "product_ops_policy";
            case "ops_monitor", "ops.monitor", "monitor" -> "product_ops_monitor";
            case "reason", "root_cause", "explain",
                 "offering_ops_root_cause" -> "product_ops_reason";
            case "compare", "compare_state", "what_if", "hypothesis" -> "product_ops_compare";
            default -> "";
        };
    }

    public static String defaultActionForScene(String scene, String intentType) {
        String sceneNorm = scene == null ? "" : scene.trim().toLowerCase(Locale.ROOT);
        String byScene = intentType == null ? "" : intentType;
        if (byScene.contains("monitor")) return "ops_monitor";
        if (byScene.contains("reason")) return "root_cause";
        if (sceneNorm.contains("online")) return "online_check";
        if (byScene.contains("compare")) return "compare";
        if (byScene.contains("policy") && sceneNorm.contains("risk")) return "risk_audit";
        if (byScene.contains("policy")) return sceneNorm.contains("online") ? "online_check" : "risk_audit";
        if ("form".equals(byScene)) return "generate";
        return "query";
    }

    public static String resolveIntentLabel(String intentType, String action) {
        String a = action == null ? "" : action.toLowerCase(Locale.ROOT);
        return switch (intentType == null ? "" : intentType) {
            case "product_ops_query" -> "市场洞察";
            case "product_ops_policy" -> {
                if (a.contains("online")) yield "立项研判";
                if (a.contains("risk") || a.contains("audit")) yield "风险稽核";
                yield "政策评估";
            }
            case "product_ops_reason" -> "异动归因";
            case "product_ops_monitor" -> "运营监控";
            case "product_ops_compare" -> "对比分析";
            case "form" -> "表单操作";
            case "validate" -> "校验";
            case "configure" -> "配置管理";
            default -> "通用对话";
        };
    }

    public static String normalizeIntentType(String intentType) {
        if (intentType == null || intentType.isBlank()) return "";
        String normalized = intentType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "query", "nl_query", "product_ops_query",
                 "market_insight" -> "product_ops_query";
            case "policy", "evaluate", "product_ops_policy",
                 "risk_audit", "online_check", "offering_ops_risk_audit" -> "product_ops_policy";
            case "reason", "explain", "product_ops_reason",
                 "root_cause", "offering_ops_root_cause" -> "product_ops_reason";
            case "monitor", "ops_monitor", "product_ops_monitor" -> "product_ops_monitor";
            case "compare", "compare_state", "product_ops_compare",
                 "what_if", "hypothesis" -> "product_ops_compare";
            case "guide" -> "chat";
            default -> normalized;
        };
    }

    public static boolean containsAny(String text, String... keys) {
        if (text == null || keys == null) return false;
        for (String key : keys) {
            if (key != null && text.contains(key)) return true;
        }
        return false;
    }

    /**
     * Handler 侧门闩：误路由到业务意图时跳过副作用。
     */
    public static List<Map<String, Object>> metaGuideSkipEvents(String businessLabel) {
        String label = businessLabel == null || businessLabel.isBlank() ? "业务操作" : businessLabel;
        return List.of(
                SseUtils.thinkingRich(
                        "检测到仅说明/勿执行请求，已跳过「" + label + "」业务执行",
                        Map.of(
                                "step", 5,
                                "totalSteps", 5,
                                "source", SOURCE_META
                        ),
                        0
                ),
                SseUtils.text(
                        "当前请求只要使用说明，未执行「" + label + "」。"
                                + "如需正式办理，请直接描述业务需求，并去掉「不要执行」类约束。"
                ),
                SseUtils.doneEvent("chat", true)
        );
    }

    private static Map<String, Object> intentResult(String intentType, String action,
                                                    double confidence, String source, String question) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("question", question);
        return intentResult(intentType, action, confidence, source, fields);
    }

    private static Map<String, Object> intentResult(String intentType, String action,
                                                    double confidence, String source,
                                                    Map<String, Object> fields) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("intentType", intentType);
        data.put("action", action);
        data.put("confidence", confidence);
        data.put("source", source);
        data.put("extractedFields", fields == null ? Map.of() : new LinkedHashMap<>(fields));
        return data;
    }
}
