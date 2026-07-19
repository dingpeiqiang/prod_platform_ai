package com.sitech.prodai.service;

import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.domain.entity.OntologyInstance;
import com.sitech.prodai.domain.entity.OntologyInstanceHistory;
import com.sitech.prodai.repository.OntologyInstanceHistoryRepository;
import com.sitech.prodai.repository.OntologyInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 历史数据服务 —— 对齐 Python {@code app/services/history_service.py::HistoryService}。
 *
 * <p>提供基于历史数据的字段推荐（频次 + 时间衰减 + 用户个性化打分），
 * 同时承担 ontology_instance_history 表的写入。
 *
 * <p>核心算法：
 * <ul>
 *   <li>count_score = min(count * countScorePerUnit, 1.0) * countScoreWeight</li>
 *   <li>user_score = min(user_count * userScorePerUnit, 1.0) * userScoreWeight</li>
 *   <li>time_score = max(0, 1 - days_since / timeDecayDays) * timeScoreWeight</li>
 *   <li>final = count_score + user_score + time_score</li>
 * </ul>
 */
@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

    private final ConfigLoader configLoader;
    private final OntologyService ontologyService;
    private final OntologyInstanceRepository instanceRepository;
    private final OntologyInstanceHistoryRepository historyRepository;

    public HistoryService(ConfigLoader configLoader,
                          OntologyService ontologyService,
                          OntologyInstanceRepository instanceRepository,
                          OntologyInstanceHistoryRepository historyRepository) {
        this.configLoader = configLoader;
        this.ontologyService = ontologyService;
        this.instanceRepository = instanceRepository;
        this.historyRepository = historyRepository;
    }

    // ==================== 推荐查询 ====================

    /** 对齐 Python get_recommend_values */
    public Map<String, Object> getRecommendValues(String formCode, String fieldCode,
                                                   String userId, Integer limit) {
        try {
            List<Map<String, Object>> recommendations = new ArrayList<>();
            Map<String, Object> config = configLoader.getRecommendationConfig();
            int limitValue = limit == null ? toInt(config.get("recommendationLimit"), 10) : limit;

            // 1. 数据库历史推荐
            List<Map<String, Object>> dbRecs = getDbRecommendations(formCode, fieldCode, userId, config);
            recommendations.addAll(dbRecs);

            // 2. 静态推荐兜底
            List<String> staticValues = configLoader.getRecommendations(formCode, fieldCode);
            Set<String> existingValues = new HashSet<>();
            for (Map<String, Object> r : recommendations) {
                existingValues.add(str(r.get("value")));
            }
            for (String rec : staticValues) {
                if (!existingValues.contains(rec)) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("value", rec);
                    r.put("score", 0.5);
                    r.put("source", "static");
                    recommendations.add(r);
                    existingValues.add(rec);
                }
            }

            // 3. 排序
            recommendations.sort(Comparator.comparingDouble((Map<String, Object> r) ->
                    toDouble(r.get("score"))).reversed());

            // 4. 取 top N 并补充 label
            List<Map<String, Object>> top = new ArrayList<>();
            for (int i = 0; i < Math.min(limitValue, recommendations.size()); i++) {
                Map<String, Object> r = recommendations.get(i);
                String code = str(r.get("value"));
                String label = getFieldLabel(formCode, fieldCode, code);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("value", code);
                row.put("label", label);
                row.put("score", r.get("score"));
                row.put("source", r.getOrDefault("source", "db"));
                top.add(row);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("recommendations", top);
            body.put("source", "hybrid");
            return body;
        } catch (Exception e) {
            log.error("[HistoryService] get_recommend_values 失败 form={} field={}", formCode, fieldCode, e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", str(e));
            return body;
        }
    }

    // ==================== 写入 ====================

    /** 对齐 Python save_history */
    @Transactional
    public void saveHistory(Integer formInstanceId, String fieldCode, String fieldValue, String userId) {
        if (formInstanceId == null) {
            return;
        }
        try {
            OntologyInstanceHistory h = new OntologyInstanceHistory();
            h.setFormInstanceId(formInstanceId);
            h.setFieldCode(fieldCode);
            h.setFieldValue(fieldValue == null ? "" : fieldValue);
            h.setUserId(userId);
            historyRepository.save(h);
            log.debug("[HistoryService] 保存历史记录 instance_id={} field={}", formInstanceId, fieldCode);
        } catch (Exception e) {
            log.error("[HistoryService] 保存历史记录失败 instance_id={} field={}",
                    formInstanceId, fieldCode, e);
        }
    }

    // ==================== 内部方法 ====================

    /** 对齐 Python _get_db_recommendations */
    private List<Map<String, Object>> getDbRecommendations(String formCode, String fieldCode,
                                                            String userId, Map<String, Object> config) {
        if (config == null) {
            config = configLoader.getRecommendationConfig();
        }
        int queryLimit = toInt(config.get("historyQueryLimit"), 1000);
        int recLimit = toInt(config.get("recommendationLimit"), 10);

        // 查询 submitted 实例
        List<OntologyInstance> instances;
        if (userId != null && !userId.isEmpty()) {
            instances = instanceRepository.findByOntologyCodeAndStatusAndUserIdOrderBySubmittedAtDesc(
                    formCode, "submitted", userId, PageRequest.of(0, queryLimit));
        } else {
            instances = instanceRepository.findByOntologyCodeAndStatusOrderBySubmittedAtDesc(
                    formCode, "submitted", PageRequest.of(0, queryLimit));
        }

        // 统计 value → score_info
        Map<String, ScoreInfo> valueScores = new HashMap<>();
        for (OntologyInstance inst : instances) {
            Map<String, Object> data = inst.getData();
            if (data == null || !data.containsKey(fieldCode)) {
                continue;
            }
            Object valueObj = data.get(fieldCode);
            String value = valueObj == null ? "" : String.valueOf(valueObj).trim();
            if (value.isEmpty()) {
                continue;
            }
            ScoreInfo info = valueScores.computeIfAbsent(value, k -> new ScoreInfo());
            info.count++;
            LocalDateTime submitted = inst.getSubmittedAt();
            if (submitted != null) {
                if (info.lastUsed == null || submitted.isAfter(info.lastUsed)) {
                    info.lastUsed = submitted;
                }
            }
            if (userId != null && userId.equals(inst.getUserId())) {
                info.userCount++;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (Map.Entry<String, ScoreInfo> e : valueScores.entrySet()) {
            double score = calculateScore(e.getValue(), now, config);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("value", e.getKey());
            r.put("score", score);
            r.put("source", "database");
            r.put("count", e.getValue().count);
            r.put("user_count", e.getValue().userCount);
            recommendations.add(r);
        }
        recommendations.sort(Comparator.comparingDouble((Map<String, Object> r) ->
                toDouble(r.get("score"))).reversed());
        return recommendations.size() > recLimit ? recommendations.subList(0, recLimit) : recommendations;
    }

    /** 对齐 Python _calculate_score */
    private double calculateScore(ScoreInfo info, LocalDateTime now, Map<String, Object> config) {
        double countScoreWeight = toDouble(config.get("countScoreWeight"), 0.4);
        double userScoreWeight = toDouble(config.get("userScoreWeight"), 0.4);
        double timeScoreWeight = toDouble(config.get("timeScoreWeight"), 0.2);
        double countScorePerUnit = toDouble(config.get("countScorePerUnit"), 0.1);
        double userScorePerUnit = toDouble(config.get("userScorePerUnit"), 0.2);
        double timeDecayDays = toDouble(config.get("timeDecayDays"), 30);

        double countScore = Math.min(info.count * countScorePerUnit, 1.0);
        double userScore = Math.min(info.userCount * userScorePerUnit, 1.0);
        double timeScore = 1.0;
        if (info.lastUsed != null) {
            long daysSince = ChronoUnit.DAYS.between(info.lastUsed, now);
            if (daysSince > 0) {
                timeScore = Math.max(0.0, 1.0 - (daysSince / timeDecayDays));
            }
        }
        return countScore * countScoreWeight + userScore * userScoreWeight + timeScore * timeScoreWeight;
    }

    /** 对齐 Python _get_field_label —— 从本体定义查中文标签 */
    @SuppressWarnings("unchecked")
    private String getFieldLabel(String formCode, String fieldCode, String code) {
        try {
            Map<String, Object> ontologyResult = ontologyService.getFormConstraint(formCode);
            if (!Boolean.TRUE.equals(ontologyResult.get("success"))) {
                return code;
            }
            Object constraintsObj = ontologyResult.get("constraints");
            if (!(constraintsObj instanceof Map<?, ?> constraints)) {
                return code;
            }
            Object entitiesObj = constraints.get("entities");
            if (!(entitiesObj instanceof List<?> entities)) {
                return code;
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
                    // 优先从 enumConfig.options 查找
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
                            if (s.equals(code)) {
                                return s;
                            }
                        } else if (opt instanceof Map<?, ?> optMap) {
                            if (code.equals(optMap.get("value"))) {
                                Object label = optMap.get("label");
                                return label == null ? code : String.valueOf(label);
                            }
                        }
                    }
                    return code;
                }
            }
            return code;
        } catch (Exception e) {
            log.warn("[HistoryService] 获取字段标签失败: {}", e.getMessage());
            return code;
        }
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

    private double toDouble(Object value) {
        return toDouble(value, 0.0);
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

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String str(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** 内部打分信息 */
    private static class ScoreInfo {
        int count;
        int userCount;
        LocalDateTime lastUsed;
    }
}
