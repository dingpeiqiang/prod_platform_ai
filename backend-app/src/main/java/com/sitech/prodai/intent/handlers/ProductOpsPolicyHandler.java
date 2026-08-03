package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentContextBuilder;
import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.intent.SseStreamSupport;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.ThinkingStepBuilder;
import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.OpsRulesService;
import com.sitech.prodai.service.ProductOntologyService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 立项研判 / 风险稽核意图。
 * risk_audit 走 ProductOntologyService.auditRisks（与 REST /ops/risk-audit 同源）；
 * 其余 expectation 仍走 OntologyService.evaluate 策略集。
 */
@Component
public class ProductOpsPolicyHandler implements BaseIntentHandler {

    private final OntologyService ontologyService;
    private final ProductOntologyService productOntologyService;
    private final OpsRulesService opsRules;

    public ProductOpsPolicyHandler(
            OntologyService ontologyService,
            ProductOntologyService productOntologyService,
            OpsRulesService opsRules
    ) {
        this.ontologyService = ontologyService;
        this.productOntologyService = productOntologyService;
        this.opsRules = opsRules;
    }

    @Override
    public String getIntentType() {
        return "product_ops_policy";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        // 门闩：使用说明/勿执行类请求禁止跑规则引擎
        if (IntentRecognitionSupport.isMetaGuideRequest(ctx.getLastUserMessage())) {
            return Flux.fromIterable(IntentRecognitionSupport.metaGuideSkipEvents("立项研判/风险稽核"));
        }
        String expectationType = BaseIntentContextBuilder.resolveExpectationType(ctx);
        if ("risk_audit".equals(expectationType)) {
            return handleRiskAudit(ctx);
        }
        return handlePolicyEvaluate(ctx, expectationType);
    }

    private Flux<Map<String, Object>> handleRiskAudit(IntentContext ctx) {
        List<Map<String, Object>> prelude = List.of(
                ThinkingStepBuilder.running(
                        "load", "加载在架清单", "正在加载在架商品清单...",
                        2, 5, Map.of("expectationType", "risk_audit", "policySetId", "PS_PRODUCT_RISK_V1"))
        );

        return SseStreamSupport.deferWork(
                prelude,
                () -> productOntologyService.auditRisks(null),
                (result, elapsedMs) -> buildRiskAuditEvents(ctx, result, elapsedMs)
        );
    }

    private Flux<Map<String, Object>> handlePolicyEvaluate(IntentContext ctx, String expectationType) {
        Map<String, Object> facts = BaseIntentContextBuilder.extractPolicyFacts(ctx);
        String policySetId = BaseIntentContextBuilder.resolvePolicySetId(ctx);

        List<Map<String, Object>> prelude = List.of(
                ThinkingStepBuilder.running(
                        "facts", "抽取立项要素", "正在抽取立项要素与政策事实...",
                        2, 5, Map.of("policySetId", policySetId, "expectationType", expectationType))
        );

        return SseStreamSupport.deferWork(
                prelude,
                () -> ontologyService.evaluate(facts, policySetId, expectationType,
                        ctx.resolveSessionId(), ctx.resolveUserId()),
                (result, elapsedMs) -> buildPolicyEvents(ctx, facts, policySetId, expectationType, result, elapsedMs)
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildRiskAuditEvents(IntentContext ctx, Map<String, Object> result, long elapsedMs) {
        boolean ok = !Boolean.FALSE.equals(result.get("success"));
        List<Map<String, Object>> items = toMapList(result.get("items"));
        List<String> triggeredRules = collectTriggeredRules(items);
        int high = toInt(result.get("highCount"));
        int medium = toInt(result.get("mediumCount"));
        int delist = toInt(result.get("suggestDelistCount"));
        int scanned = toInt(result.get("scannedCount"));
        String ruleVersion = String.valueOf(result.getOrDefault("ruleVersion", "RiskRules-v1.2"));

        String verdict = high > 0 ? "deny" : (medium > 0 ? "review" : "allow");
        String reason = ok
                ? String.format("扫描 %d 条在架商品：高风险 %d / 中风险 %d / 建议下架 %d（%s）",
                scanned, high, medium, delist, ruleVersion)
                : String.valueOf(result.getOrDefault("message", "风险稽核失败"));

        Map<String, Object> factsSummary = new LinkedHashMap<>();
        factsSummary.put("scannedCount", scanned);
        factsSummary.put("highCount", high);
        factsSummary.put("mediumCount", medium);
        factsSummary.put("suggestDelistCount", delist);
        factsSummary.put("ruleVersion", ruleVersion);

        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("expectationType", "risk_audit");
        intentData.put("policySetId", "PS_PRODUCT_RISK_V1");
        intentData.put("verdict", verdict);
        intentData.put("reason", reason);
        intentData.put("triggeredRules", triggeredRules);
        intentData.put("factsSummary", factsSummary);
        intentData.put("riskAudit", result);
        intentData.put("items", items);
        intentData.put("highCount", high);
        intentData.put("mediumCount", medium);
        intentData.put("suggestDelistCount", delist);
        intentData.put("scannedCount", scanned);
        intentData.put("ruleVersion", ruleVersion);
        intentData.put("success", ok);

        String answerText = formatRiskAuditAnswer(result, items);
        String ruleDetails = triggeredRules.isEmpty() ? null : "命中规则：" + String.join("、",
                triggeredRules.stream().map(opsRules::formatRuleName).limit(6).toList());

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(ThinkingStepBuilder.done(
                "load", "加载在架清单", "加载在架商品清单",
                "扫描范围 " + scanned + " 条", 2, 5, 0, null,
                Map.of("scannedCount", scanned)));
        events.add(ThinkingStepBuilder.done(
                "match", "匹配风险规则集", "匹配适用风险规则集",
                ruleVersion, 3, 5, 0, null, Map.of("ruleVersion", ruleVersion)));
        events.add(ThinkingStepBuilder.done(
                "scan", "全量扫描打分", "按规则全量扫描打分",
                "高风险 " + high + " / 中风险 " + medium, 4, 5, 0, ruleDetails,
                Map.of("highCount", high, "mediumCount", medium)));
        events.add(ThinkingStepBuilder.done(
                "conclude", "风险与处置建议", "汇总风险与处置建议",
                ok ? "建议下架 " + delist + " · 结论 " + verdict : "风险排查未完成",
                5, 5, elapsedMs, reason,
                Map.of("highCount", high, "mediumCount", medium, "suggestDelistCount", delist,
                        "verdict", verdict, "success", ok)));
        events.add(SseUtils.intentEvent(getIntentType(), "risk_audit", intentData, false));
        events.addAll(SseStreamSupport.chunkedTextEvents(answerText));
        events.add(SseUtils.stats(ctx.getStreamStats()));
        Map<String, Object> donePayload = new LinkedHashMap<>();
        donePayload.put("intentType", getIntentType());
        donePayload.put("action", "risk_audit");
        donePayload.put("stats", Map.of(
                "policySetId", "PS_PRODUCT_RISK_V1",
                "expectationType", "risk_audit",
                "verdict", verdict,
                "highCount", high,
                "mediumCount", medium,
                "scannedCount", scanned
        ));
        donePayload.put("verdict", verdict);
        donePayload.put("reason", reason);
        donePayload.put("triggeredRules", triggeredRules);
        donePayload.put("factsSummary", factsSummary);
        donePayload.put("policySetId", "PS_PRODUCT_RISK_V1");
        donePayload.put("expectationType", "risk_audit");
        donePayload.put("riskAudit", result);
        donePayload.put("items", items);
        events.add(SseUtils.doneEvent(getIntentType(), false, donePayload));
        return events;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildPolicyEvents(
            IntentContext ctx,
            Map<String, Object> facts,
            String policySetId,
            String expectationType,
            Map<String, Object> result,
            long elapsedMs
    ) {
        Map<String, Object> decision = (Map<String, Object>) result.get("decision");
        if (decision == null) {
            decision = Map.of();
        }
        String verdict = String.valueOf(decision.getOrDefault("verdict", "review"));
        String reason = String.valueOf(decision.getOrDefault("reason", ""));
        List<String> triggeredRules = List.of();
        if (decision.get("triggered_rules") instanceof List<?> list) {
            triggeredRules = list.stream().map(String.class::cast).toList();
        }

        Map<String, Object> factsSummary = new LinkedHashMap<>();
        factsSummary.put("productType", facts.getOrDefault("productType", "-"));
        factsSummary.put("targetMarketSize", facts.getOrDefault("targetMarketSize", "-"));
        factsSummary.put("isZeroFee", facts.getOrDefault("isZeroFee", false));
        factsSummary.put("onlineMonths", facts.getOrDefault("onlineMonths", "-"));
        factsSummary.put("newUserMonth", facts.getOrDefault("newUserMonth", "-"));
        factsSummary.put("annualSpend", facts.getOrDefault("annualSpend", "-"));
        factsSummary.put("vipLevel", facts.getOrDefault("vipLevel", "-"));

        String answerText = formatPolicyAnswer(
                verdict, reason, policySetId, expectationType, triggeredRules, factsSummary);
        String verdictLabel = switch (verdict) {
            case "allow" -> "通过";
            case "deny" -> "拒绝";
            default -> "待审";
        };

        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("facts", facts);
        intentData.put("factsSummary", factsSummary);
        intentData.put("decision", decision);
        intentData.put("verdict", verdict);
        intentData.put("reason", reason);
        intentData.put("triggeredRules", triggeredRules);
        intentData.put("policySetId", policySetId);
        intentData.put("expectationType", expectationType);

        Map<String, Object> statsPayload = Map.of(
                "policySetId", policySetId,
                "expectationType", expectationType,
                "verdict", verdict,
                "reason", reason,
                "triggered_rules", triggeredRules
        );

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(ThinkingStepBuilder.done(
                "facts", "抽取立项要素", "抽取立项要素与政策事实",
                String.valueOf(factsSummary.getOrDefault("productType", "-"))
                        + " · 规模 " + factsSummary.getOrDefault("targetMarketSize", "-"),
                2, 5, 0, null, Map.of("factsSummary", factsSummary)));
        events.add(ThinkingStepBuilder.done(
                "policy", "选择策略集", "选择适用政策策略集",
                policySetId, 3, 5, 0, null, Map.of("policySetId", policySetId)));
        events.add(ThinkingStepBuilder.done(
                "decide", "规则引擎判定", "执行规则引擎判定",
                triggeredRules.isEmpty() ? "未触发阻断规则" : "命中 " + triggeredRules.size() + " 条规则",
                4, 5, 0,
                triggeredRules.isEmpty() ? null : "命中规则：" + String.join("、", triggeredRules),
                Map.of("triggeredRules", triggeredRules.size())));
        events.add(ThinkingStepBuilder.done(
                "conclude", "政策结论", "汇总政策评估结论",
                "评估结论：" + verdictLabel,
                5, 5, elapsedMs,
                reason != null && !reason.isBlank() ? "原因: " + truncatePolicyStr(reason, 200) : null,
                Map.of("policySetId", policySetId, "verdict", verdictLabel)));
        events.add(SseUtils.intentEvent(getIntentType(), expectationType, intentData, false));
        events.addAll(SseStreamSupport.chunkedTextEvents(answerText));
        events.add(SseUtils.stats(ctx.getStreamStats()));
        events.add(SseUtils.doneEvent(getIntentType(), false, Map.of(
                "intentType", getIntentType(),
                "action", expectationType,
                "stats", statsPayload,
                "verdict", verdict,
                "reason", reason,
                "triggeredRules", triggeredRules,
                "factsSummary", factsSummary,
                "policySetId", policySetId,
                "expectationType", expectationType
        )));
        return events;
    }

    private String formatRiskAuditAnswer(Map<String, Object> result, List<Map<String, Object>> items) {
        if (Boolean.FALSE.equals(result.get("success"))) {
            return "风险稽核失败：" + result.getOrDefault("message", "未知错误");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### 全量智能稽核结果\n\n");
        sb.append("扫描 **").append(result.getOrDefault("scannedCount", items.size())).append("** 条 · 规则 **")
                .append(result.getOrDefault("ruleVersion", "RiskRules-v1.2")).append("**\n\n");
        sb.append("| 高风险 | 中风险 | 建议下架 |\n| --- | --- | --- |\n| **")
                .append(result.getOrDefault("highCount", 0)).append("** | **")
                .append(result.getOrDefault("mediumCount", 0)).append("** | **")
                .append(result.getOrDefault("suggestDelistCount", 0)).append("** |\n\n");
        int limit = Math.min(8, items.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> it = items.get(i);
            sb.append("- **").append(it.getOrDefault("offeringName", it.get("offeringId"))).append("** [")
                    .append(it.getOrDefault("riskLevel", "-")).append("] 分值")
                    .append(it.getOrDefault("riskScore", "-"));
            if (Boolean.TRUE.equals(it.get("urgent"))) {
                sb.append(" ·紧急");
            }
            sb.append("\n");
            Object risks = it.get("risks");
            if (risks instanceof List<?> list && !list.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Object r : list) {
                    if (r instanceof Map<?, ?> m) {
                        parts.add(m.get("ruleId") + ":" + m.get("feature"));
                    }
                }
                if (!parts.isEmpty()) {
                    sb.append("  ").append(String.join("；", parts)).append("\n");
                }
            }
        }
        if (items.size() > 8) {
            sb.append("\n…共 ").append(result.getOrDefault("total", items.size())).append(" 项，详见右侧清单\n");
        }
        sb.append("\n右侧可筛选「建议下架」、调整零销阈值并重新推理。");
        return sb.toString();
    }

    private String formatPolicyAnswer(
            String verdict,
            String reason,
            String policySetId,
            String expectationType,
            List<String> rules,
            Map<String, Object> factsSummary
    ) {
        String verdictLabel = switch (verdict) {
            case "allow" -> "通过";
            case "deny" -> "拒绝";
            default -> "待审";
        };
        String modeLabel = switch (expectationType == null ? "" : expectationType) {
            case "online_check", "candidate_check" -> "立项校验";
            case "risk_audit" -> "风险稽核";
            default -> expectationType == null || expectationType.isBlank() ? "策略评估" : expectationType;
        };
        StringBuilder sb = new StringBuilder();
        sb.append("### 立项研判结论\n\n");
        sb.append("- **策略集**：`").append(policySetId).append("`\n");
        sb.append("- **评估模式**：").append(modeLabel).append("\n");
        sb.append("- **评估结论**：").append(verdictLabel).append("\n");
        if (reason != null && !reason.isBlank()) {
            sb.append("- **原因**：").append(reason).append("\n");
        }
        if (rules != null && !rules.isEmpty()) {
            sb.append("- **命中规则**：").append(String.join("、", rules)).append("\n");
        }
        if (factsSummary != null && !factsSummary.isEmpty()) {
            sb.append("\n**评估事实**\n\n");
            appendFactLine(sb, "产品类型", factsSummary.get("productType"));
            appendFactLine(sb, "目标市场规模", factsSummary.get("targetMarketSize"));
            appendFactLine(sb, "零资费", factsSummary.get("isZeroFee"));
            appendFactLine(sb, "在售月数", factsSummary.get("onlineMonths"));
            appendFactLine(sb, "月新增用户", factsSummary.get("newUserMonth"));
            appendFactLine(sb, "年消费", factsSummary.get("annualSpend"));
            appendFactLine(sb, "会员等级", factsSummary.get("vipLevel"));
        }
        return sb.toString();
    }

    private void appendFactLine(StringBuilder sb, String label, Object value) {
        String display = value == null || String.valueOf(value).isBlank() ? "-" : String.valueOf(value);
        sb.append("- ").append(label).append("：").append(display).append("\n");
    }

    private List<String> collectTriggeredRules(List<Map<String, Object>> items) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Object risks = item.get("risks");
            if (!(risks instanceof List<?> list)) continue;
            for (Object r : list) {
                if (r instanceof Map<?, ?> m && m.get("ruleId") != null) {
                    String id = String.valueOf(m.get("ruleId"));
                    if (!out.contains(id)) out.add(id);
                }
            }
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

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String truncatePolicyStr(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
