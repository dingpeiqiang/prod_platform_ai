package com.sitech.prodai.service;

import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.domain.entity.OntologyInstance;
import com.sitech.prodai.repository.OntologyInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能推荐引擎 —— 对齐 Python {@code app/services/recommendation_engine.py::RecommendationEngine}
 * 及其策略模块 {@code recommendations/strategies.py}。
 *
 * <p>多维度推荐策略（按优先级降序）：
 * <ul>
 *   <li>优先级 1 — AI 推荐（ContextAwareStrategy，从 LLM extractedFields 提取）</li>
 *   <li>优先级 2 — 近期常用（TimeDecayStrategy，时间衰减打分）</li>
 *   <li>优先级 3 — 用户高频（UserPersonalizedStrategy + FrequencyStrategy）</li>
 *   <li>优先级 4 — 静态配置（兜底）</li>
 * </ul>
 *
 * <p>每个策略独立打分，结果按优先级 + 置信度 + 分数合并、去重、截断。
 */
@Service
public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);

    private final ConfigLoader configLoader;
    private final OntologyInstanceRepository instanceRepository;
    private final OntologyService ontologyService;

    private final int maxRecommendations;
    private final int historyQueryLimit;
    private final double confidenceThreshold;
    private final double countScoreWeight;
    private final double userScoreWeight;
    private final double timeScoreWeight;
    private final double countScorePerUnit;
    private final double userScorePerUnit;
    private final double timeDecayDays;
    private final int recentDaysThreshold;

    public RecommendationEngine(ConfigLoader configLoader,
                                OntologyInstanceRepository instanceRepository,
                                OntologyService ontologyService) {
        this.configLoader = configLoader;
        this.instanceRepository = instanceRepository;
        this.ontologyService = ontologyService;
        Map<String, Object> cfg = configLoader.getRecommendationConfig();
        this.maxRecommendations = toInt(cfg.get("recommendationLimit"), 3);
        this.historyQueryLimit = toInt(cfg.get("historyQueryLimit"), 1000);
        this.confidenceThreshold = toDouble(cfg.get("confidenceThreshold"), 0.4);
        this.countScoreWeight = toDouble(cfg.get("countScoreWeight"), 0.4);
        this.userScoreWeight = toDouble(cfg.get("userScoreWeight"), 0.4);
        this.timeScoreWeight = toDouble(cfg.get("timeScoreWeight"), 0.2);
        this.countScorePerUnit = toDouble(cfg.get("countScorePerUnit"), 0.1);
        this.userScorePerUnit = toDouble(cfg.get("userScorePerUnit"), 0.2);
        this.timeDecayDays = toDouble(cfg.get("timeDecayDays"), 30);
        this.recentDaysThreshold = toInt(cfg.get("recentDaysThreshold"), 90);
    }

    /**
     * 推荐主入口 —— 对齐 Python recommend。
     *
     * @param formCode             表单编码
     * @param fieldCode            字段编码
     * @param userInput            用户输入
     * @param userId               用户ID
     * @param conversationContext  对话上下文（含 extractedFields）
     * @param maxRecommendations   单字段最大推荐数
     * @param strategies           使用的策略列表（null 时用默认）
     * @return 推荐结果
     */
    public RecommendationResult recommend(String formCode, String fieldCode, String userInput,
                                          String userId, Map<String, Object> conversationContext,
                                          int maxRecommendations, List<String> strategies) {
        long startMs = System.currentTimeMillis();
        if (strategies == null || strategies.isEmpty()) {
            strategies = List.of("frequency", "user_personalized", "time_decay", "static");
        }

        try {
            List<String> strategiesUsed = new ArrayList<>();
            Map<Integer, List<RecommendationItem>> byPriority = new HashMap<>();
            byPriority.put(1, new ArrayList<>()); // AI 推荐
            byPriority.put(2, new ArrayList<>()); // 近期常用
            byPriority.put(3, new ArrayList<>()); // 用户高频 / 历史频率
            byPriority.put(4, new ArrayList<>()); // 静态兜底
            int totalCandidates = 0;

            // 1. AI 推荐（上下文感知）
            if (userInput != null && !userInput.isEmpty() && conversationContext != null) {
                strategiesUsed.add("ai_recommend");
                List<RecommendationItem> aiItems = contextAwareRecommend(userInput, formCode, fieldCode, conversationContext);
                for (RecommendationItem item : aiItems) {
                    item.priority = 1;
                    item.reason = "AI智能推荐";
                    byPriority.get(1).add(item);
                    totalCandidates++;
                }
            }

            // 2. 用户个性化
            if (strategies.contains("user_personalized") && userId != null && !userId.isEmpty()) {
                strategiesUsed.add("user_personalized");
                List<RecommendationItem> userItems = userPersonalizedRecommend(formCode, fieldCode, userId);
                for (RecommendationItem item : userItems) {
                    item.priority = 3;
                    byPriority.get(3).add(item);
                    totalCandidates++;
                }
            }

            // 3. 历史频率
            if (strategies.contains("frequency")) {
                strategiesUsed.add("frequency");
                List<RecommendationItem> freqItems = frequencyRecommend(formCode, fieldCode, userId, conversationContext);
                for (RecommendationItem item : freqItems) {
                    if (item.priority == null || item.priority > 3) {
                        item.priority = 3;
                    }
                    byPriority.get(3).add(item);
                    totalCandidates++;
                }
            }

            // 4. 时间衰减
            if (strategies.contains("time_decay")) {
                strategiesUsed.add("time_decay");
                List<RecommendationItem> timeItems = timeDecayRecommend(formCode, fieldCode, userId);
                for (RecommendationItem item : timeItems) {
                    if (item.priority == null || item.priority > 2) {
                        item.priority = 2;
                    }
                    byPriority.get(2).add(item);
                    totalCandidates++;
                }
            }

            // 5. 静态兜底
            if (strategies.contains("static")) {
                strategiesUsed.add("static");
                List<RecommendationItem> staticItems = getStaticRecommendations(formCode, fieldCode);
                for (RecommendationItem item : staticItems) {
                    if (item.priority == null || item.priority > 4) {
                        item.priority = 4;
                    }
                    byPriority.get(4).add(item);
                    totalCandidates++;
                }
            }

            // 合并去重
            Set<String> seenValues = new HashSet<>();
            List<RecommendationItem> finalRecs = new ArrayList<>();
            int effectiveMax = Math.min(maxRecommendations, this.maxRecommendations);

            for (int priority = 1; priority <= 4; priority++) {
                List<RecommendationItem> candidates = byPriority.getOrDefault(priority, new ArrayList<>());
                candidates.sort(Comparator.comparingDouble((RecommendationItem i) -> i.confidence).reversed()
                        .thenComparingDouble((RecommendationItem i) -> i.score).reversed());
                for (RecommendationItem item : candidates) {
                    if (item.value == null || item.value.isEmpty()) {
                        continue;
                    }
                    if (item.label == null || item.label.isEmpty()) {
                        item.label = getEnumLabel(formCode, fieldCode, item.value);
                    }
                    if (seenValues.add(item.value)) {
                        item.reason = simplifyReason(item.reason, item.confidence);
                        if (item.reason != null && !item.reason.trim().isEmpty()) {
                            finalRecs.add(item);
                        }
                    }
                    if (finalRecs.size() >= effectiveMax) {
                        break;
                    }
                }
                if (finalRecs.size() >= effectiveMax) {
                    break;
                }
            }

            long elapsed = System.currentTimeMillis() - startMs;
            log.info("[RecommendationEngine] 推荐完成 field={} candidates={} returned={} strategies={} time={}ms",
                    fieldCode, totalCandidates, finalRecs.size(), strategiesUsed, elapsed);

            return new RecommendationResult(true, fieldCode, finalRecs, totalCandidates,
                    strategiesUsed, elapsed, null);
        } catch (Exception e) {
            log.error("[RecommendationEngine] 推荐失败 field={}", fieldCode, e);
            return new RecommendationResult(false, fieldCode, List.of(), 0, List.of(),
                    System.currentTimeMillis() - startMs, str(e));
        }
    }

    /** 对齐 Python batch_recommend */
    public Map<String, RecommendationResult> batchRecommend(String formCode,
                                                             Map<String, String> extractedFields,
                                                             String userInput,
                                                             String userId,
                                                             Map<String, Object> conversationContext,
                                                             int maxPerField,
                                                             List<String> fieldCodes) {
        Map<String, RecommendationResult> results = new LinkedHashMap<>();
        List<String> targetFields = fieldCodes != null ? fieldCodes : new ArrayList<>(extractedFields.keySet());
        for (String fieldCode : targetFields) {
            Map<String, Object> ctx = conversationContext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(conversationContext);
            ctx.put("extractedFields", extractedFields);
            RecommendationResult r = recommend(formCode, fieldCode, userInput, userId, ctx, maxPerField, null);
            results.put(fieldCode, r);
        }
        return results;
    }

    // ==================== 策略实现 ====================

    /** 对齐 Python ContextAwareStrategy.recommend */
    @SuppressWarnings("unchecked")
    private List<RecommendationItem> contextAwareRecommend(String userInput, String formCode,
                                                            String fieldCode, Map<String, Object> context) {
        List<RecommendationItem> recs = new ArrayList<>();
        Object extractedObj = context.get("extractedFields");
        if (!(extractedObj instanceof Map<?, ?> extracted)) {
            return recs;
        }
        Object value = extracted.get(fieldCode);
        if (value == null) {
            return recs;
        }
        String strValue = String.valueOf(value).trim();
        if (strValue.isEmpty()) {
            return recs;
        }
        RecommendationItem item = new RecommendationItem();
        item.value = strValue;
        item.fieldCode = fieldCode;
        item.score = 0.95;
        item.source = "llm_extraction";
        item.confidence = 0.95;
        item.matchType = "extracted";
        item.reason = "🔴 AI从您的输入中提取";
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("extractedBy", "llm");
        meta.put("source", "user_input");
        meta.put("isEmpty", false);
        meta.put("priority", 1);
        item.metadata = meta;
        recs.add(item);
        return recs;
    }

    /** 对齐 Python FrequencyRecommendationStrategy.recommend */
    @SuppressWarnings("unchecked")
    private List<RecommendationItem> frequencyRecommend(String formCode, String fieldCode,
                                                         String userId, Map<String, Object> context) {
        List<RecommendationItem> recs = new ArrayList<>();
        List<OntologyInstance> instances;
        if (userId != null && !userId.isEmpty()) {
            instances = instanceRepository.findByOntologyCodeAndStatusAndUserIdOrderBySubmittedAtDesc(
                    formCode, "submitted", userId, PageRequest.of(0, historyQueryLimit));
        } else {
            instances = instanceRepository.findByOntologyCodeAndStatusOrderBySubmittedAtDesc(
                    formCode, "submitted", PageRequest.of(0, historyQueryLimit));
        }
        if (instances.isEmpty()) {
            return recs;
        }

        // 上下文过滤
        Map<String, String> extractedFields = new HashMap<>();
        if (context != null && context.get("extractedFields") instanceof Map<?, ?> ef) {
            for (Map.Entry<?, ?> e : ef.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    extractedFields.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }
        List<OntologyInstance> filtered = new ArrayList<>(instances);
        int filterFieldCount = 0;
        if (!extractedFields.isEmpty()) {
            List<OntologyInstance> matched = new ArrayList<>();
            for (OntologyInstance inst : instances) {
                Map<String, Object> data = inst.getData() == null ? Map.of() : inst.getData();
                int totalCheckable = 0;
                double matchScore = 0;
                for (Map.Entry<String, String> e : extractedFields.entrySet()) {
                    String efVal = e.getValue() == null ? "" : e.getValue().trim().toLowerCase();
                    if (efVal.isEmpty()) {
                        continue;
                    }
                    totalCheckable++;
                    Object histValObj = data.get(e.getKey());
                    String histVal = histValObj == null ? "" : String.valueOf(histValObj).trim().toLowerCase();
                    if (histVal.equals(efVal)) {
                        matchScore += 1.0;
                    } else if (!histVal.isEmpty() && (efVal.contains(histVal) || histVal.contains(efVal))) {
                        matchScore += 0.5;
                    }
                }
                if (totalCheckable == 0 || matchScore > 0) {
                    matched.add(inst);
                }
            }
            if (!matched.isEmpty()) {
                filtered = matched;
                filterFieldCount = extractedFields.size();
            }
        }

        Map<String, FreqStats> statsMap = new HashMap<>();
        for (OntologyInstance inst : filtered) {
            Map<String, Object> data = inst.getData() == null ? Map.of() : inst.getData();
            Object valueObj = data.get(fieldCode);
            if (valueObj == null) {
                continue;
            }
            String value = String.valueOf(valueObj).trim();
            if (value.isEmpty()) {
                continue;
            }
            FreqStats stats = statsMap.computeIfAbsent(value, k -> new FreqStats());
            stats.count++;
            if (inst.getSubmittedAt() != null) {
                if (stats.lastUsed == null || inst.getSubmittedAt().isAfter(stats.lastUsed)) {
                    stats.lastUsed = inst.getSubmittedAt();
                }
            }
            if (userId != null && userId.equals(inst.getUserId())) {
                stats.userCount++;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        boolean hasFiltered = filterFieldCount > 0 && filtered.size() != instances.size();
        for (Map.Entry<String, FreqStats> e : statsMap.entrySet()) {
            FreqStats s = e.getValue();
            if (s.count < 1) {
                continue;
            }
            double score = calculateFreqScore(s, now);
            String reason;
            String source;
            double confidence;
            if (hasFiltered) {
                reason = "🟢 基于" + filtered.size() + "条相似记录推断";
                source = "inference";
                confidence = Math.min((double) s.count / Math.max(filtered.size(), 1), 1.0);
            } else {
                reason = "🟢 历史填写" + s.count + "次";
                source = "history";
                confidence = Math.min(s.count / 10.0, 1.0);
            }
            RecommendationItem item = new RecommendationItem();
            item.value = e.getKey();
            item.fieldCode = fieldCode;
            item.score = score;
            item.source = source;
            item.confidence = confidence;
            item.matchType = "exact";
            item.reason = reason;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("count", s.count);
            meta.put("userCount", s.userCount);
            meta.put("lastUsed", s.lastUsed);
            meta.put("filteredCount", hasFiltered ? filtered.size() : null);
            meta.put("totalCount", instances.size());
            meta.put("filterFieldCount", filterFieldCount);
            item.metadata = meta;
            recs.add(item);
        }
        recs.sort(Comparator.comparingDouble((RecommendationItem i) -> i.score).reversed());
        return recs;
    }

    /** 对齐 Python UserPersonalizedStrategy.recommend */
    private List<RecommendationItem> userPersonalizedRecommend(String formCode, String fieldCode, String userId) {
        List<RecommendationItem> recs = new ArrayList<>();
        if (userId == null || userId.isEmpty()) {
            return recs;
        }
        List<OntologyInstance> instances = instanceRepository.findByOntologyCodeAndStatusAndUserIdOrderBySubmittedAtDesc(
                formCode, "submitted", userId, PageRequest.of(0, 100));
        Map<String, FreqStats> statsMap = new HashMap<>();
        for (OntologyInstance inst : instances) {
            Map<String, Object> data = inst.getData() == null ? Map.of() : inst.getData();
            Object valueObj = data.get(fieldCode);
            if (valueObj == null) {
                continue;
            }
            String value = String.valueOf(valueObj).trim();
            if (value.isEmpty()) {
                continue;
            }
            FreqStats stats = statsMap.computeIfAbsent(value, k -> new FreqStats());
            stats.count++;
            if (inst.getSubmittedAt() != null) {
                if (stats.lastUsed == null || inst.getSubmittedAt().isAfter(stats.lastUsed)) {
                    stats.lastUsed = inst.getSubmittedAt();
                }
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, FreqStats> e : statsMap.entrySet()) {
            FreqStats s = e.getValue();
            if (s.count < 1) {
                continue;
            }
            double baseScore = calculateUserBaseScore(s, now);
            double userBoost = Math.min(s.count * userScorePerUnit, 1.0);
            double boostedScore = baseScore + (userBoost * 0.5);
            RecommendationItem item = new RecommendationItem();
            item.value = e.getKey();
            item.fieldCode = fieldCode;
            item.score = boostedScore;
            item.source = "history";
            item.confidence = Math.min(s.count / 5.0, 1.0);
            item.matchType = "exact";
            item.reason = "🟢 您历史填写" + s.count + "次";
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("count", s.count);
            meta.put("personalized", true);
            item.metadata = meta;
            recs.add(item);
        }
        recs.sort(Comparator.comparingDouble((RecommendationItem i) -> i.score).reversed());
        return recs;
    }

    /** 对齐 Python TimeDecayStrategy.recommend */
    private List<RecommendationItem> timeDecayRecommend(String formCode, String fieldCode, String userId) {
        List<RecommendationItem> recs = new ArrayList<>();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(recentDaysThreshold);
        List<OntologyInstance> instances = instanceRepository.findByOntologyCodeAndStatusAndSubmittedAtAfterOrderBySubmittedAtDesc(
                formCode, "submitted", cutoff, PageRequest.of(0, 500));
        Map<String, TimeStats> statsMap = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (OntologyInstance inst : instances) {
            Map<String, Object> data = inst.getData() == null ? Map.of() : inst.getData();
            Object valueObj = data.get(fieldCode);
            if (valueObj == null) {
                continue;
            }
            String value = String.valueOf(valueObj).trim();
            if (value.isEmpty()) {
                continue;
            }
            TimeStats stats = statsMap.computeIfAbsent(value, k -> new TimeStats());
            stats.count++;
            if (inst.getSubmittedAt() != null) {
                long daysAgo = ChronoUnit.DAYS.between(inst.getSubmittedAt(), now);
                double recency = Math.max(0, 1.0 - (daysAgo / timeDecayDays));
                stats.recencyScore += recency;
            }
        }
        for (Map.Entry<String, TimeStats> e : statsMap.entrySet()) {
            TimeStats s = e.getValue();
            if (s.count < 1) {
                continue;
            }
            double avgRecency = s.recencyScore / s.count;
            double score = avgRecency * 1.5;
            RecommendationItem item = new RecommendationItem();
            item.value = e.getKey();
            item.fieldCode = fieldCode;
            item.score = score;
            item.source = "time_decay";
            item.confidence = avgRecency;
            item.matchType = "exact";
            item.reason = "🟡 近期常用（" + s.count + "次）";
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("recencyScore", avgRecency);
            meta.put("count", s.count);
            item.metadata = meta;
            recs.add(item);
        }
        recs.sort(Comparator.comparingDouble((RecommendationItem i) -> i.score).reversed());
        return recs;
    }

    /** 对齐 Python _get_static_recommendations */
    private List<RecommendationItem> getStaticRecommendations(String formCode, String fieldCode) {
        List<RecommendationItem> recs = new ArrayList<>();
        List<String> staticValues = configLoader.getRecommendations(formCode, fieldCode);
        if (staticValues == null || staticValues.isEmpty()) {
            return recs;
        }
        for (int i = 0; i < staticValues.size(); i++) {
            String value = staticValues.get(i);
            RecommendationItem item = new RecommendationItem();
            item.value = value;
            item.fieldCode = fieldCode;
            item.score = 0.3 - (i * 0.05);
            item.source = "static";
            item.confidence = 0.5;
            item.matchType = "exact";
            item.reason = "⚪ 常用选项（#" + (i + 1) + "）";
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("staticRank", i + 1);
            item.metadata = meta;
            recs.add(item);
        }
        return recs;
    }

    // ==================== 打分计算 ====================

    private double calculateFreqScore(FreqStats stats, LocalDateTime now) {
        double countScore = Math.min(stats.count * countScorePerUnit, 1.0);
        double timeScore = 1.0;
        if (stats.lastUsed != null) {
            long daysSince = ChronoUnit.DAYS.between(stats.lastUsed, now);
            if (daysSince > 0) {
                timeScore = Math.max(0.0, 1.0 - (daysSince / timeDecayDays));
            }
        }
        return countScore * countScoreWeight + timeScore * timeScoreWeight;
    }

    private double calculateUserBaseScore(FreqStats stats, LocalDateTime now) {
        double countScore = Math.min(stats.count * 0.1, 1.0);
        double timeScore = 1.0;
        if (stats.lastUsed != null) {
            long daysSince = ChronoUnit.DAYS.between(stats.lastUsed, now);
            if (daysSince > 0) {
                timeScore = Math.max(0.0, 1.0 - (daysSince / 30));
            }
        }
        return countScore * 0.5 + timeScore * 0.5;
    }

    /** 对齐 Python _simplify_reason */
    private String simplifyReason(String reason, double confidence) {
        if (reason == null) {
            return "";
        }
        if (reason.contains("🔴") || reason.contains("🟡") || reason.contains("🟢") || reason.contains("⚪")) {
            return reason;
        }
        if (confidence >= 0.8) {
            return reason;
        } else if (confidence >= 0.6) {
            if (reason.contains("历史填写")) {
                return "🟢 高频填写";
            } else if (reason.contains("相似记录")) {
                return "🟢 智能推断";
            } else if (reason.contains("近期")) {
                return "🟡 近期常用";
            } else if (reason.contains("您历史")) {
                return "🟢 您常填写";
            }
            return "🟢 推荐选项";
        }
        return reason.trim().isEmpty() ? "⚪ 常用选项" : reason;
    }

    /** 对齐 Python _get_enum_label */
    @SuppressWarnings("unchecked")
    private String getEnumLabel(String formCode, String fieldCode, String value) {
        try {
            Map<String, Object> result = ontologyService.getFormConstraint(formCode);
            if (!Boolean.TRUE.equals(result.get("success"))) {
                return value;
            }
            Object constraintsObj = result.get("constraints");
            if (!(constraintsObj instanceof Map<?, ?> constraints)) {
                return value;
            }
            Object entitiesObj = constraints.get("entities");
            if (!(entitiesObj instanceof List<?> entities)) {
                return value;
            }
            for (Object entityObj : entities) {
                if (!(entityObj instanceof Map<?, ?> entity)) {
                    continue;
                }
                Object fieldsObj = entity.get("fields");
                if (!(fieldsObj instanceof List<?> fields)) {
                    continue;
                }
                for (Object fieldObj : fields) {
                    if (!(fieldObj instanceof Map<?, ?> field)) {
                        continue;
                    }
                    if (!fieldCode.equals(field.get("fieldCode"))) {
                        continue;
                    }
                    List<?> options = List.of();
                    Object enumConfigObj = field.get("enumConfig");
                    if (enumConfigObj instanceof Map<?, ?> enumConfig) {
                        Object opts = enumConfig.get("options");
                        if (opts instanceof List<?> list) {
                            options = list;
                        }
                    }
                    if (options.isEmpty()) {
                        Object opts = field.get("options");
                        if (opts instanceof List<?> list) {
                            options = list;
                        }
                    }
                    for (Object opt : options) {
                        if (opt instanceof String s) {
                            if (s.equals(value)) {
                                return s;
                            }
                        } else if (opt instanceof Map<?, ?> optMap) {
                            if (value.equals(optMap.get("value"))) {
                                Object label = optMap.get("label");
                                return label == null ? value : String.valueOf(label);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[RecommendationEngine] 获取标签失败: {}", e.getMessage());
        }
        return value;
    }

    // ==================== 工具方法 ====================

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String str(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    // ==================== 数据类 ====================

    /** 对齐 Python RecommendationItem */
    public static class RecommendationItem {
        public String value;
        public String fieldCode;
        public double score;
        public String source;
        public double confidence;
        public String matchType;
        public String reason;
        public Map<String, Object> metadata;
        public Integer priority;
        public String label;

        public Map<String, Object> toDict() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", value);
            m.put("fieldCode", fieldCode);
            m.put("score", Math.round(score * 1000) / 1000.0);
            m.put("source", source);
            m.put("confidence", Math.round(confidence * 1000) / 1000.0);
            m.put("matchType", matchType);
            m.put("reason", reason);
            m.put("metadata", metadata == null ? Map.of() : metadata);
            if (label != null && !label.isEmpty()) {
                m.put("label", label);
            }
            return m;
        }
    }

    /** 对齐 Python RecommendationResult */
    public static class RecommendationResult {
        public final boolean success;
        public final String fieldCode;
        public final List<RecommendationItem> recommendations;
        public final int totalCandidates;
        public final List<String> strategyUsed;
        public final double processingTimeMs;
        public final String error;

        public RecommendationResult(boolean success, String fieldCode, List<RecommendationItem> recommendations,
                                     int totalCandidates, List<String> strategyUsed,
                                     double processingTimeMs, String error) {
            this.success = success;
            this.fieldCode = fieldCode;
            this.recommendations = recommendations == null ? List.of() : recommendations;
            this.totalCandidates = totalCandidates;
            this.strategyUsed = strategyUsed == null ? List.of() : strategyUsed;
            this.processingTimeMs = processingTimeMs;
            this.error = error;
        }

        public Map<String, Object> toDict() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", success);
            m.put("fieldCode", fieldCode);
            List<Map<String, Object>> recList = new ArrayList<>();
            for (RecommendationItem r : recommendations) {
                recList.add(r.toDict());
            }
            m.put("recommendations", recList);
            m.put("totalCandidates", totalCandidates);
            m.put("strategyUsed", strategyUsed);
            m.put("processingTimeMs", Math.round(processingTimeMs * 1000) / 1000.0);
            m.put("error", error);
            return m;
        }
    }

    private static class FreqStats {
        int count;
        int userCount;
        LocalDateTime lastUsed;
    }

    private static class TimeStats {
        int count;
        double recencyScore;
    }
}
