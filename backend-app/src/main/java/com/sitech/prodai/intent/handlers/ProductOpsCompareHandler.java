package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentContextBuilder;
import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.intent.SseStreamSupport;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.service.OntologyService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 产商品运营：多方案假设对比（compare_state）。
 */
@Component
public class ProductOpsCompareHandler implements BaseIntentHandler {

    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元");
    private static final Pattern MARKET_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*万");

    private final OntologyService ontologyService;

    public ProductOpsCompareHandler(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getIntentType() {
        return "product_ops_compare";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        if (IntentRecognitionSupport.isMetaGuideRequest(ctx.getLastUserMessage())) {
            return Flux.fromIterable(IntentRecognitionSupport.metaGuideSkipEvents("方案对比"));
        }
        String question = ctx.getExtractedFields().containsKey("question")
                ? String.valueOf(ctx.getExtractedFields().get("question"))
                : ctx.getLastUserMessage();
        String policySetId = BaseIntentContextBuilder.resolvePolicySetId(ctx);
        if (!"PS_PRODUCT_ONLINE_V1".equals(policySetId) && !"PS_PRODUCT_RISK_V1".equals(policySetId)) {
            // 对比场景默认立项策略集
            if (question != null && (question.contains("立项") || question.contains("方案") || question.contains("资费"))) {
                policySetId = "PS_PRODUCT_ONLINE_V1";
            }
        }

        List<Map<String, Object>> prelude = List.of(
                SseUtils.thinkingRich(
                        "正在构建方案对比快照并评估规则...",
                        Map.of(
                                "step", 5,
                                "totalSteps", 6,
                                "phase", "running",
                                "policySetId", policySetId,
                                "question", question == null ? "" : (question.length() > 60
                                        ? question.substring(0, 60) + "..." : question)
                        ),
                        -1
                )
        );

        String finalPolicy = policySetId;
        return SseStreamSupport.deferWork(
                prelude,
                () -> doCompare(ctx, question, finalPolicy),
                result -> buildAfterEvents(ctx, question, finalPolicy, result)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doCompare(IntentContext ctx, String question, String policySetId) {
        Map<String, Object> baseFacts = BaseIntentContextBuilder.extractPolicyFacts(ctx);
        enrichFactsFromQuestion(baseFacts, question);

        // 优先从本体发现相关产品事实
        Map<String, Object> discovered = ontologyService.nlDiscoverAndRetrieve(
                question == null || question.isBlank() ? "在售5G套餐" : question, 5);
        String snapshotId = null;
        if (discovered.get("snapshot") instanceof Map<?, ?> snap) {
            snapshotId = String.valueOf(snap.get("snapshot_id"));
        }
        if (snapshotId == null || snapshotId.isBlank() || "null".equals(snapshotId)) {
            if (discovered.get("raw_results") instanceof List<?> list && !list.isEmpty()
                    && list.get(0) instanceof Map<?, ?>) {
                Map<String, Object> first = new LinkedHashMap<>((Map<String, Object>) list.get(0));
                mergeMissing(baseFacts, first);
                snapshotId = ontologyService.createSnapshotFromFacts(
                        first, ctx.resolveSessionId(), ctx.resolveUserId());
            } else {
                snapshotId = ontologyService.createSnapshotFromFacts(
                        baseFacts, ctx.resolveSessionId(), ctx.resolveUserId());
            }
        }

        List<Map<String, Object>> patches = extractPatches(ctx, question, baseFacts);
        return ontologyService.compareState(
                snapshotId, patches, policySetId,
                ctx.resolveSessionId(), ctx.resolveUserId(), baseFacts
        );
    }

    private void enrichFactsFromQuestion(Map<String, Object> facts, String question) {
        if (question == null || question.isBlank()) {
            return;
        }
        String q = question.toLowerCase();
        if (!facts.containsKey("productType") || "融合套餐".equals(facts.get("productType"))) {
            if (q.contains("5g") || question.contains("套餐")) {
                facts.put("productType", "5G套餐");
            }
        }
        Matcher market = MARKET_PATTERN.matcher(question);
        if (market.find()) {
            try {
                double wan = Double.parseDouble(market.group(1));
                facts.put("targetMarketSize", (long) (wan * 10000));
            } catch (NumberFormatException ignored) {
                // keep existing
            }
        }
        if (question.contains("零资费") || question.contains("0元") || question.contains("零元")) {
            facts.put("isZeroFee", true);
            facts.putIfAbsent("status", "在售");
            facts.putIfAbsent("onlineMonths", 4);
            facts.putIfAbsent("newUserMonth", 30);
        }
        if (!facts.containsKey("targetMarketSize")) {
            // 演示默认：未写规模时用 8 万触发 R-ONLINE-001
            if (question.contains("立项") || question.contains("方案")) {
                facts.put("targetMarketSize", 80000);
            }
        }
        facts.putIfAbsent("status", "在售");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractPatches(IntentContext ctx, String question,
                                                     Map<String, Object> baseFacts) {
        Object raw = ctx.getExtractedFields().get("patches");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> fromFields = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    fromFields.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
            if (!fromFields.isEmpty()) {
                return fromFields;
            }
        }

        List<Map<String, Object>> patches = new ArrayList<>();
        List<Double> prices = new ArrayList<>();
        if (question != null) {
            Matcher pm = PRICE_PATTERN.matcher(question);
            while (pm.find()) {
                try {
                    prices.add(Double.parseDouble(pm.group(1)));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }

        if (prices.size() >= 2) {
            char label = 'A';
            for (Double price : prices) {
                Map<String, Object> changes = new LinkedHashMap<>();
                changes.put("price", price);
                changes.put("productType", baseFacts.getOrDefault("productType", "5G套餐"));
                Object market = baseFacts.get("targetMarketSize");
                if (market != null) {
                    changes.put("targetMarketSize", market);
                } else {
                    changes.put("targetMarketSize", 150000);
                }
                Map<String, Object> patch = new LinkedHashMap<>();
                patch.put("description", "方案" + label + "：" + price + "元/月");
                patch.put("entity_id", baseFacts.getOrDefault("uri", "product/draft"));
                patch.put("changes", changes);
                patches.add(patch);
                label++;
                if (patches.size() >= 4) {
                    break;
                }
            }
        } else if (question != null && (question.contains("对比") || question.contains("方案"))) {
            // 默认演示：39 vs 59，市场规模取对话或 15 万
            Object market = baseFacts.getOrDefault("targetMarketSize", 150000);
            patches.add(schemePatch("方案A：39元/月", 39, market, baseFacts));
            patches.add(schemePatch("方案B：59元/月", 59, market, baseFacts));
        } else if (question != null && (question.contains("假设") || question.contains("如果")
                || question.contains("改价") || question.contains("下调"))) {
            Map<String, Object> changes = new LinkedHashMap<>();
            if (!prices.isEmpty()) {
                changes.put("price", prices.get(0));
                changes.put("isZeroFee", prices.get(0) <= 0);
            } else {
                changes.put("price", 19);
                changes.put("isZeroFee", false);
            }
            Object market = baseFacts.get("targetMarketSize");
            if (market != null) {
                changes.put("targetMarketSize", market);
            }
            changes.put("productType", baseFacts.getOrDefault("productType", "5G套餐"));
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("description", "假设变更：" + changes);
            patch.put("entity_id", baseFacts.getOrDefault("uri", "product/draft"));
            patch.put("changes", changes);
            patches.add(patch);
        } else {
            // 至少返回基准 + 放宽市场规模对比
            Object market = baseFacts.getOrDefault("targetMarketSize", 80000);
            patches.add(schemePatch("当前方案（市场规模=" + market + "）",
                    numberOr(baseFacts.get("price"), 39), market, baseFacts));
            patches.add(schemePatch("放宽市场规模至15万户",
                    numberOr(baseFacts.get("price"), 39), 150000, baseFacts));
        }
        return patches;
    }

    private Map<String, Object> schemePatch(String desc, Object price, Object market,
                                            Map<String, Object> baseFacts) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("price", price);
        changes.put("targetMarketSize", market);
        changes.put("productType", baseFacts.getOrDefault("productType", "5G套餐"));
        changes.put("status", baseFacts.getOrDefault("status", "在售"));
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("description", desc);
        patch.put("entity_id", baseFacts.getOrDefault("uri", "product/draft"));
        patch.put("changes", changes);
        return patch;
    }

    private double numberOr(Object v, double def) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private void mergeMissing(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> e : source.entrySet()) {
            if (!target.containsKey(e.getKey()) || target.get(e.getKey()) == null) {
                target.put(e.getKey(), e.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildAfterEvents(
            IntentContext ctx, String question, String policySetId, Map<String, Object> result
    ) {
        List<Map<String, Object>> comparisons = List.of();
        if (result.get("comparisons") instanceof List<?> list) {
            comparisons = list.stream()
                    .filter(Map.class::isInstance)
                    .map(e -> (Map<String, Object>) e)
                    .toList();
        }
        List<Map<String, Object>> patches = new ArrayList<>();
        for (Map<String, Object> comp : comparisons) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("description", comp.get("patch_description"));
            p.put("evaluation", comp.get("evaluation"));
            patches.add(p);
        }

        String answerText = formatAnswer(policySetId, comparisons);
        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("question", question);
        intentData.put("policySetId", policySetId);
        intentData.put("snapshotId", result.get("snapshot_id"));
        intentData.put("patches", patches);
        intentData.put("comparisons", comparisons);

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(SseUtils.thinkingRich(
                "方案对比完成，共 " + comparisons.size() + " 套方案",
                Map.of(
                        "step", 6,
                        "totalSteps", 6,
                        "policySetId", policySetId,
                        "compareCount", comparisons.size()
                ),
                0,
                null
        ));
        events.add(SseUtils.intentEvent(getIntentType(), "compare", intentData, false));
        events.addAll(SseStreamSupport.chunkedTextEvents(answerText));
        events.add(SseUtils.stats(ctx.getStreamStats()));
        events.add(SseUtils.doneEvent(getIntentType(), false, Map.of(
                "intentType", getIntentType(),
                "action", "compare",
                "stats", Map.of("compareCount", comparisons.size(), "policySetId", policySetId),
                "question", question == null ? "" : question,
                "patches", patches,
                "comparisons", comparisons,
                "policySetId", policySetId,
                "snapshotId", result.getOrDefault("snapshot_id", "")
        )));
        return events;
    }

    @SuppressWarnings("unchecked")
    private String formatAnswer(String policySetId, List<Map<String, Object>> comparisons) {
        StringBuilder sb = new StringBuilder();
        sb.append("策略集：").append(policySetId).append("\n");
        sb.append("共对比 ").append(comparisons.size()).append(" 套方案：\n");
        String recommend = null;
        for (int i = 0; i < comparisons.size(); i++) {
            Map<String, Object> c = comparisons.get(i);
            String desc = String.valueOf(c.getOrDefault("patch_description", "方案" + (i + 1)));
            Map<String, Object> eval = c.get("evaluation") instanceof Map<?, ?>
                    ? (Map<String, Object>) c.get("evaluation") : Map.of();
            String verdict = String.valueOf(eval.getOrDefault("verdict", "review"));
            String reason = String.valueOf(eval.getOrDefault("reason", ""));
            String label = switch (verdict) {
                case "allow" -> "✅ 合规通过";
                case "deny" -> "❌ 不通过";
                default -> "⚠️ 待审";
            };
            sb.append(i + 1).append(". ").append(desc).append(" → ").append(label);
            if (!reason.isBlank() && !"null".equals(reason)) {
                sb.append("\n   依据：").append(truncate(reason, 120));
            }
            sb.append("\n");
            if ("allow".equals(verdict) && recommend == null) {
                recommend = desc;
            }
        }
        if (recommend != null) {
            sb.append("推荐结论：优先选择「").append(recommend).append("」。");
        } else {
            sb.append("推荐结论：当前方案均未完全通过，建议调整市场规模或资费后重试。");
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
