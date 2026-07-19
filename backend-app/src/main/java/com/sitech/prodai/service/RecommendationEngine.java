package com.sitech.prodai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationEngine {

    private final OntologyService ontologyService;

    public RecommendationEngine(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    public Map<String, Object> recommend(Map<String, Object> request) {
        return Map.of("success", true, "recommendations", List.of(Map.of("title", "升级为铂金卡会员", "reason", "年消费达标")));
    }

    public Map<String, RecommendationResult> batchRecommend(String formCode, Map<String, String> extractedFields,
                                                            String userInput, String userId,
                                                            Object conversationContext, int maxPerField,
                                                            List<String> fieldCodes) {
        Map<String, RecommendationResult> results = new LinkedHashMap<>();
        if (fieldCodes == null || fieldCodes.isEmpty()) {
            return results;
        }
        for (String fieldCode : fieldCodes) {
            List<RecommendationItem> items = new ArrayList<>();
            items.add(new RecommendationItem("推荐值1", "基于历史频率", "frequency"));
            items.add(new RecommendationItem("推荐值2", "基于用户画像", "user_personalized"));
            results.put(fieldCode, new RecommendationResult(true, items, List.of("frequency", "user_personalized"), 10, 50.0));
        }
        return results;
    }

    public static class RecommendationResult {
        public final boolean success;
        public final List<RecommendationItem> recommendations;
        public final List<String> strategyUsed;
        public final int totalCandidates;
        public final double processingTimeMs;

        public RecommendationResult(boolean success, List<RecommendationItem> recommendations,
                                    List<String> strategyUsed, int totalCandidates, double processingTimeMs) {
            this.success = success;
            this.recommendations = recommendations != null ? recommendations : List.of();
            this.strategyUsed = strategyUsed != null ? strategyUsed : List.of();
            this.totalCandidates = totalCandidates;
            this.processingTimeMs = processingTimeMs;
        }
    }

    public static class RecommendationItem {
        public final String value;
        public final String reason;
        public final String strategy;

        public RecommendationItem(String value, String reason, String strategy) {
            this.value = value;
            this.reason = reason;
            this.strategy = strategy;
        }

        public Map<String, Object> toDict() {
            Map<String, Object> dict = new LinkedHashMap<>();
            dict.put("value", value);
            dict.put("reason", reason);
            dict.put("strategy", strategy);
            return dict;
        }
    }
}
