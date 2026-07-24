package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.service.OntologyService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductOpsReasonHandler implements BaseIntentHandler {

    private final OntologyService ontologyService;

    public ProductOpsReasonHandler(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getIntentType() {
        return "product_ops_reason";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        String target = ctx.getExtractedFields().containsKey("target")
                ? String.valueOf(ctx.getExtractedFields().get("target"))
                : ctx.getLastUserMessage();
        String traceId = ctx.resolveSessionId();
        String tenantId = ctx.resolveUserId();

        Map<String, Object> nlResult = ontologyService.nlQuery(target);
        String answer = String.valueOf(nlResult.getOrDefault("answer", ""));

        Map<String, Object> explainResult = ontologyService.explain(traceId, "business", tenantId);
        String explanation = String.valueOf(explainResult.getOrDefault("natural_language", answer));
        List<String> referencedRules = List.of();
        if (explainResult.get("referenced_rules") instanceof List<?> list) {
            referencedRules = list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }

        List<Map<String, Object>> results = List.of();
        if (nlResult.get("results") instanceof List<?> list) {
            results = list.stream().filter(Map.class::isInstance).map(e -> (Map<String, Object>) e).toList();
        }

        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("target", target);
        intentData.put("explanation", explanation);
        intentData.put("referencedRules", referencedRules);
        intentData.put("sparql", nlResult.get("sparql"));
        intentData.put("results", results);
        intentData.put("traceId", traceId);

        String answerText = formatReasonAnswer(explanation, referencedRules, results);

        Map<String, Object> statsPayload = Map.of(
                "traceId", traceId,
                "referenced_rules", referencedRules,
                "target", target,
                "evidenceCount", results.size()
        );

        return Flux.just(
                SseUtils.thinkingRich(
                        "正在追溯产商品异动根因并构建证据链...",
                        Map.of(
                                "step", 5,
                                "totalSteps", 6,
                                "traceId", traceId.length() > 12 ? traceId.substring(0, 12) + "..." : traceId,
                                "target", target.length() > 50 ? target.substring(0, 50) + "..." : target,
                                "ruleCount", referencedRules.size(),
                                "evidenceCount", results.size()
                        ),
                        -1,
                        referencedRules.isEmpty() ? null : "引用规则: " + referencedRules.stream()
                                .map(this::formatRuleLabel)
                                .reduce((a, b) -> a + "、" + b)
                                .orElse("")
                ),
                SseUtils.intentEvent(getIntentType(), "root_cause", intentData, false),
                SseUtils.textStart(),
                SseUtils.text(answerText),
                SseUtils.textEnd(),
                SseUtils.stats(ctx.getStreamStats()),
                SseUtils.doneEvent(getIntentType(), false, Map.of(
                        "intentType", getIntentType(),
                        "action", "root_cause",
                        "stats", statsPayload,
                        "explanation", explanation,
                        "referencedRules", referencedRules,
                        "sparql", nlResult.get("sparql"),
                        "results", results,
                        "traceId", traceId,
                        "target", target,
                        "evidenceCount", results.size()
                ))
        );
    }

    private String formatReasonAnswer(String explanation, List<String> rules, List<Map<String, Object>> evidence) {
        StringBuilder sb = new StringBuilder();
        if (explanation != null && !explanation.isBlank()) {
            sb.append(explanation);
        } else {
            sb.append("根因分析完成");
        }
        if (!rules.isEmpty()) {
            sb.append("\n\n引用规则：");
            for (int i = 0; i < rules.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(formatRuleLabel(rules.get(i)));
            }
        }
        if (!evidence.isEmpty()) {
            sb.append("\n\n支撑证据：");
            int limit = Math.min(5, evidence.size());
            for (int i = 0; i < limit; i++) {
                Map<String, Object> row = evidence.get(i);
                sb.append("\n").append(i + 1).append(". ").append(summarizeEvidenceRow(row));
            }
            if (evidence.size() > limit) {
                sb.append("\n…其余 ").append(evidence.size() - limit).append(" 条见结果卡片");
            }
        }
        return sb.toString();
    }

    private String summarizeEvidenceRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return "—";
        }
        Object message = row.get("message");
        if (message != null && !String.valueOf(message).isBlank()) {
            return String.valueOf(message);
        }
        Object name = row.get("name");
        if (name == null) name = row.get("productName");
        if (name == null) name = row.get("offeringName");
        StringBuilder line = new StringBuilder();
        if (name != null) {
            line.append(name);
        }
        String[] prefer = {"status", "revenueGrowth", "growth", "users", "newUserMonth", "isZeroFee", "_bucket"};
        for (String key : prefer) {
            Object val = row.get(key);
            if (val == null || String.valueOf(val).isBlank()) continue;
            if (!line.isEmpty()) line.append(" · ");
            line.append(fieldLabel(key)).append("：").append(val);
            if (line.length() > 80) break;
        }
        if (line.isEmpty()) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getValue() == null) continue;
                if (!line.isEmpty()) line.append(" · ");
                line.append(fieldLabel(e.getKey())).append("：").append(e.getValue());
                if (line.length() > 80) break;
            }
        }
        return line.isEmpty() ? "—" : line.toString();
    }

    private String fieldLabel(String key) {
        return switch (key) {
            case "name", "productName" -> "名称";
            case "offeringName" -> "产商品";
            case "status" -> "状态";
            case "revenueGrowth", "growth" -> "收入增长";
            case "users", "newUserMonth" -> "用户数";
            case "isZeroFee" -> "零资费";
            case "_bucket" -> "分类";
            default -> key;
        };
    }

    private String formatRuleLabel(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) return "";
        String cn = switch (ruleId) {
            case "R-A01" -> "异动确认";
            case "R-A02" -> "渠道归因";
            case "R-A03" -> "促销归因";
            case "R-A04" -> "竞品冲击";
            case "R-A05" -> "行为变化";
            case "R-B01" -> "高风险命中";
            case "R-B02", "R-B03" -> "中风险命中";
            case "R-B04" -> "优胜劣汰";
            case "R-B05" -> "风险复核";
            default -> null;
        };
        return cn == null ? ruleId : cn + "（" + ruleId + "）";
    }
}