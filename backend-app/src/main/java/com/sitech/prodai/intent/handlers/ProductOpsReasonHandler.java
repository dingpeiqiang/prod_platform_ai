package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.intent.SseStreamSupport;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.ThinkingStepBuilder;
import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.OpsRulesService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 异动归因意图：场景化思考步骤 + 图谱/SWRL 异步执行，正文分片输出。
 */
@Component
public class ProductOpsReasonHandler implements BaseIntentHandler {

    private static final int TOTAL = 6;

    private final ProductOntologyService productOntologyService;
    private final OpsRulesService opsRules;

    public ProductOpsReasonHandler(ProductOntologyService productOntologyService, OpsRulesService opsRules) {
        this.productOntologyService = productOntologyService;
        this.opsRules = opsRules;
    }

    @Override
    public String getIntentType() {
        return "product_ops_reason";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        if (IntentRecognitionSupport.isMetaGuideRequest(ctx.getLastUserMessage())) {
            return Flux.fromIterable(IntentRecognitionSupport.metaGuideSkipEvents("异动归因"));
        }
        String target = ctx.getExtractedFields().containsKey("target")
                ? String.valueOf(ctx.getExtractedFields().get("target"))
                : ctx.getLastUserMessage();
        String offeringHint = ctx.getExtractedFields().containsKey("offeringId")
                ? String.valueOf(ctx.getExtractedFields().get("offeringId"))
                : null;
        String traceId = ctx.resolveSessionId();

        List<Map<String, Object>> prelude = List.of(
                ThinkingStepBuilder.running(
                        "locate", "锁定分析对象", "正在定位分析对象与指标快照...",
                        2, TOTAL, Map.of("target", shortText(target, 50), "traceId", shortId(traceId)))
        );

        return SseStreamSupport.deferWork(
                prelude,
                () -> productOntologyService.analyzeRootCause(offeringHint, target),
                (root, elapsedMs) -> buildAfterEvents(ctx, target, traceId, root, elapsedMs)
        );
    }

    private List<Map<String, Object>> buildAfterEvents(
            IntentContext ctx, String target, String traceId, Map<String, Object> root, long elapsedMs
    ) {
        boolean ok = Boolean.TRUE.equals(root.get("success"));
        List<String> referencedRules = toStringList(root.get("appliedRules"));
        List<Map<String, Object>> paths = toMapList(root.get("paths"));
        List<Map<String, Object>> anomalies = toMapList(root.get("anomalies"));
        List<String> swrlFired = toStringList(root.get("swrlFiredRules"));
        String offeringName = String.valueOf(root.getOrDefault("offeringName", root.getOrDefault("offeringId", "目标商品")));
        String offeringId = String.valueOf(root.getOrDefault("offeringId", ""));
        String reasonEngine = String.valueOf(root.getOrDefault("reasonEngine", "rules"));
        Object marketObj = root.get("market");
        String marketLabel = "";
        if (marketObj instanceof Map<?, ?> m) {
            Object name = m.get("name");
            Object scopeId = m.get("scopeId");
            marketLabel = String.valueOf(name != null ? name : (scopeId != null ? scopeId : ""));
        }

        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("target", target);
        intentData.put("offeringId", root.get("offeringId"));
        intentData.put("offeringName", root.get("offeringName"));
        intentData.put("success", ok);
        intentData.put("message", root.get("message"));
        intentData.put("explanation", formatReasonSummary(root));
        intentData.put("referencedRules", referencedRules);
        intentData.put("anomalies", anomalies);
        intentData.put("paths", paths);
        intentData.put("results", paths);
        intentData.put("rootCause", root);
        intentData.put("traceId", traceId);

        String answerText = formatReasonAnswer(root);
        Map<String, Object> statsPayload = new LinkedHashMap<>();
        statsPayload.put("traceId", traceId);
        statsPayload.put("referenced_rules", referencedRules);
        statsPayload.put("target", target);
        statsPayload.put("offeringId", root.get("offeringId"));
        statsPayload.put("evidenceCount", paths.size());
        statsPayload.put("success", ok);

        String locateResult = offeringName
                + (offeringId.isBlank() || "null".equals(offeringId) ? "" : "（" + offeringId + "）")
                + (marketLabel.isBlank() || "null".equals(marketLabel) ? "" : " · " + marketLabel);

        String confirmResult;
        String confirmDetails = null;
        if (anomalies.isEmpty()) {
            confirmResult = ok ? "未检出异动指标" : "异动确认失败";
        } else {
            Map<String, Object> a0 = anomalies.get(0);
            confirmResult = String.valueOf(a0.getOrDefault("message", "指标异动已确认"));
            confirmDetails = anomalies.stream()
                    .map(a -> String.valueOf(a.getOrDefault("message", "")))
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining("；"));
        }

        String drillResult = summarizeDrill(paths);
        String reasonResult = (swrlFired.isEmpty() ? referencedRules : swrlFired).stream()
                .map(opsRules::formatRuleName)
                .collect(Collectors.joining("、"));
        if (reasonResult.isBlank()) {
            reasonResult = "引擎：" + reasonEngine;
        } else {
            reasonResult = reasonEngine + " 命中 " + reasonResult;
        }

        String concludeResult;
        String concludeDetails = null;
        if (!ok) {
            concludeResult = "未能完成异动原因分析";
        } else if (paths.isEmpty()) {
            concludeResult = String.valueOf(root.getOrDefault("message", "暂无命中归因路径"));
        } else {
            Map<String, Object> primary = paths.get(0);
            concludeResult = "主因：" + primary.getOrDefault("name", "—")
                    + "（权重 " + primary.getOrDefault("weight", "—") + "）· 证据 "
                    + paths.size() + " 条";
            Object evidence = primary.get("evidence");
            if (evidence instanceof List<?> list && !list.isEmpty()) {
                concludeDetails = "主因证据：" + list.stream().map(String::valueOf).collect(Collectors.joining("；"));
            }
            Object actions = root.get("actionList");
            if (actions instanceof List<?> list && !list.isEmpty()) {
                String act = "策略建议：" + list.stream().map(String::valueOf).limit(3).collect(Collectors.joining("；"));
                concludeDetails = concludeDetails == null ? act : concludeDetails + "\n" + act;
            }
        }

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(ThinkingStepBuilder.done(
                "locate", "锁定分析对象", "定位分析对象与指标快照",
                locateResult, 2, TOTAL, 0, null,
                Map.of("offeringId", offeringId, "offeringName", offeringName)));
        events.add(ThinkingStepBuilder.done(
                "confirm", "异动确认", "对照阈值确认指标异动",
                confirmResult, 3, TOTAL, 0, confirmDetails,
                Map.of("anomalyCount", anomalies.size())));
        events.add(ThinkingStepBuilder.done(
                "drill", "多维下钻", "按渠道 / 促销 / 竞品 / 行为扫描异动集中维度",
                drillResult, 4, TOTAL, 0, null,
                Map.of("pathCount", paths.size())));
        events.add(ThinkingStepBuilder.doneOntology(
                "reason", "规则推理", "执行图谱与 SWRL 归因规则",
                reasonResult, 5, TOTAL, elapsedMs,
                referencedRules.isEmpty() ? null : "参考规则：" + referencedRules.stream()
                        .map(opsRules::formatRuleName).collect(Collectors.joining("、")),
                Map.of("ruleCount", referencedRules.size(), "reasonEngine", reasonEngine,
                        "evidenceCount", paths.size())));
        events.add(ThinkingStepBuilder.done(
                "conclude", "归因结论", "汇总主因路径与处置建议",
                concludeResult, 6, TOTAL, 0, concludeDetails,
                Map.of("success", ok, "evidenceCount", paths.size())));

        events.add(SseUtils.intentEvent(getIntentType(), "root_cause", intentData, false));
        events.addAll(SseStreamSupport.chunkedTextEvents(answerText));
        events.add(SseUtils.stats(ctx.getStreamStats()));
        events.add(SseUtils.doneEvent(getIntentType(), false, Map.of(
                "intentType", getIntentType(),
                "action", "root_cause",
                "stats", statsPayload,
                "rootCause", root,
                "referencedRules", referencedRules,
                "results", paths,
                "traceId", traceId,
                "target", target,
                "evidenceCount", paths.size()
        )));
        return events;
    }

    private String summarizeDrill(List<Map<String, Object>> paths) {
        if (paths.isEmpty()) {
            return "4 维扫描完成，暂无命中维度";
        }
        List<String> types = new ArrayList<>();
        for (Map<String, Object> p : paths) {
            String t = String.valueOf(p.getOrDefault("rootCauseType", p.getOrDefault("type", "")));
            String label = switch (t) {
                case "Channel" -> "渠道";
                case "Promotion" -> "促销";
                case "Competitor" -> "竞品";
                case "UserBehavior" -> "行为";
                default -> t.isBlank() || "null".equals(t) ? null : t;
            };
            if (label != null && !types.contains(label)) {
                types.add(label);
            }
        }
        if (types.isEmpty()) {
            return "多维扫描完成，命中 " + paths.size() + " 条路径";
        }
        return "扫描渠道/促销/竞品/行为 · 命中 " + String.join("+", types);
    }

    private String formatReasonSummary(Map<String, Object> root) {
        if (!Boolean.TRUE.equals(root.get("success"))) {
            return "根因分析失败：" + root.getOrDefault("message", "未知错误");
        }
        List<Map<String, Object>> anomalies = toMapList(root.get("anomalies"));
        List<Map<String, Object>> paths = toMapList(root.get("paths"));
        if (anomalies.isEmpty()) {
            return String.valueOf(root.getOrDefault("message", "未检出异动指标"));
        }
        Map<String, Object> anomaly = anomalies.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(anomaly.getOrDefault("message", "指标异动"));
        if (anomaly.get("ruleId") != null) {
            sb.append("（").append(formatRuleLabel(String.valueOf(anomaly.get("ruleId")))).append("）");
        }
        if (paths.isEmpty()) {
            sb.append("。").append(root.getOrDefault("message", "暂无命中归因路径"));
            return sb.toString();
        }
        Map<String, Object> primary = paths.get(0);
        sb.append("。主因：").append(primary.getOrDefault("name", "—"))
                .append("（权重 ").append(primary.getOrDefault("weight", "—")).append("）");
        if (paths.size() > 1) {
            sb.append("；另有 ").append(paths.size() - 1).append(" 条次因路径，详见上方报告与支撑证据");
        }
        sb.append("。");
        return sb.toString();
    }

    private String formatReasonAnswer(Map<String, Object> root) {
        if (!Boolean.TRUE.equals(root.get("success"))) {
            return "根因分析失败：" + root.getOrDefault("message", "未知错误");
        }
        String name = String.valueOf(root.getOrDefault("offeringName", root.getOrDefault("offeringId", "目标商品")));
        List<Map<String, Object>> anomalies = toMapList(root.get("anomalies"));
        List<Map<String, Object>> paths = toMapList(root.get("paths"));
        List<String> rules = toStringList(root.get("appliedRules"));

        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(name).append(" 异动根因分析\n\n");
        if (anomalies.isEmpty()) {
            sb.append(root.getOrDefault("message", "未检出异动指标"));
            return sb.toString();
        }
        Map<String, Object> anomaly = anomalies.get(0);
        sb.append("**异动结论**：").append(anomaly.getOrDefault("message", "—"));
        if (anomaly.get("ruleId") != null) {
            sb.append("（").append(formatRuleLabel(String.valueOf(anomaly.get("ruleId")))).append("）");
        }
        sb.append("\n\n");
        if (paths.isEmpty()) {
            sb.append(root.getOrDefault("message", "暂无命中归因路径"));
            return sb.toString();
        }
        sb.append("**根因路径**\n");
        for (Map<String, Object> p : paths) {
            sb.append(p.getOrDefault("rank", "-")).append(". **")
                    .append(p.getOrDefault("name", "—")).append("**")
                    .append(" 权重 ").append(p.getOrDefault("weight", "—"))
                    .append(" ← ").append(formatRuleLabel(String.valueOf(p.getOrDefault("ruleId", ""))))
                    .append("\n");
            Object evidence = p.get("evidence");
            if (evidence instanceof List<?> list && !list.isEmpty()) {
                sb.append("   证据：").append(String.join("；", list.stream().map(String::valueOf).toList())).append("\n");
            }
        }
        Object actions = root.get("actionList");
        if (actions instanceof List<?> list && !list.isEmpty()) {
            sb.append("\n**策略建议**\n");
            for (Object a : list) {
                sb.append("- ").append(a).append("\n");
            }
        }
        if (!rules.isEmpty()) {
            sb.append("\n引用规则：");
            for (int i = 0; i < rules.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(formatRuleLabel(rules.get(i)));
            }
        }
        return sb.toString();
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) out.add(String.valueOf(item));
        }
        return out;
    }

    private List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }

    private String shortId(String id) {
        if (id == null) return "";
        return id.length() > 12 ? id.substring(0, 12) + "..." : id;
    }

    private String shortText(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private String formatRuleLabel(String ruleId) {
        return opsRules.formatRuleLabel(ruleId);
    }
}
