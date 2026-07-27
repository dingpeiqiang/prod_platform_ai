package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentContextBuilder;
import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseStreamSupport;
import com.sitech.prodai.intent.SseUtils;
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
        String expectationType = BaseIntentContextBuilder.resolveExpectationType(ctx);
        if ("risk_audit".equals(expectationType)) {
            return handleRiskAudit(ctx);
        }
        return handlePolicyEvaluate(ctx, expectationType);
    }

    private Flux<Map<String, Object>> handleRiskAudit(IntentContext ctx) {
        List<Map<String, Object>> prelude = List.of(
                SseUtils.thinkingRich(
                        "正在按风险规则全量稽核在架商品...",
                        Map.of(
                                "step", 5,
                                "totalSteps", 6,
                                "phase", "running",
                                "expectationType", "risk_audit",
                                "policySetId", "PS_PRODUCT_RISK_V1"
                        ),
                        -1
                )
        );

        return SseStreamSupport.deferWork(
                prelude,
                () -> productOntologyService.auditRisks(null),
                result -> buildRiskAuditEvents(ctx, result)
        );
    }

    private Flux<Map<String, Object>> handlePolicyEvaluate(IntentContext ctx, String expectationType) {
        Map<String, Object> facts = BaseIntentContextBuilder.extractPolicyFacts(ctx);
        String policySetId = BaseIntentContextBuilder.resolvePolicySetId(ctx);

        List<Map<String, Object>> prelude = List.of(
                SseUtils.thinkingRich(
                        "正在调用规则引擎进行政策评估...",
                        Map.of(
                                "step", 5,
                                "totalSteps", 6,
                                "phase", "running",
                                "policySetId", policySetId,
                                "expectationType", expectationType
                        ),
                        -1
                )
        );

        return SseStreamSupport.deferWork(
                prelude,
                () -> ontologyService.evaluate(facts, policySetId, expectationType,
                        ctx.resolveSessionId(), ctx.resolveUserId()),
                result -> buildPolicyEvents(ctx, facts, policySetId, expectationType, result)
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildRiskAuditEvents(IntentContext ctx, Map<String, Object> result) {
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

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(SseUtils.thinkingRich(
                ok ? "风险稽核完成，正在组织答复..." : "风险稽核未通过",
                Map.of(
                        "step", 6,
                        "totalSteps", 6,
                        "highCount", high,
                        "mediumCount", medium,
                        "itemCount", items.size(),
                        "success", ok
                ),
                0,
                triggeredRules.isEmpty() ? null : "命中规则: " + String.join("、",
                        triggeredRules.stream().map(opsRules::formatRuleLabel).limit(6).toList())
        ));
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
            Map<String, Object> result
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

        String answerText = formatPolicyAnswer(verdict, reason, policySetId, triggeredRules);
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
        events.add(SseUtils.thinkingRich(
                "政策评估完成：" + verdictLabel,
                Map.of(
                        "step", 6,
                        "totalSteps", 6,
                        "policySetId", policySetId,
                        "verdict", verdictLabel,
                        "triggeredRules", triggeredRules.size()
                ),
                0,
                reason != null && !reason.isBlank() ? "原因: " + truncatePolicyStr(reason, 200) : null
        ));
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

    private String formatPolicyAnswer(String verdict, String reason, String policySetId, List<String> rules) {
        String verdictLabel = switch (verdict) {
            case "allow" -> "通过";
            case "deny" -> "拒绝";
            default -> "待审";
        };
        StringBuilder sb = new StringBuilder();
        sb.append("策略集：").append(policySetId);
        sb.append("\n评估结论：").append(verdictLabel);
        if (reason != null && !reason.isBlank()) {
            sb.append("\n原因：").append(reason);
        }
        if (!rules.isEmpty()) {
            sb.append("\n命中规则：").append(String.join(", ", rules));
        }
        return sb.toString();
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
