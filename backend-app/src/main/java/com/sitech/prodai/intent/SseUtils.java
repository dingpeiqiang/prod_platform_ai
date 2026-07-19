package com.sitech.prodai.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 工具函数 —— 对齐 Python {@code app/intent/utils.py}。
 *
 * <p>提供 SSE 事件构造方法，返回 {@code Map<String, Object>}（事件数据体），
 * 由 Controller 层序列化为 JSON 并通过 SseEmitter 发送。
 */
@Component
public final class SseUtils {

    private SseUtils() {
    }

    // ==================== 基础事件 ====================

    /** 系统步骤日志（type=thinking），对齐 Python thinking() */
    public static Map<String, Object> thinking(String content) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "thinking");
        event.put("content", content);
        return event;
    }

    /** 系统步骤日志（带 result），对齐 Python thinking(content, result) */
    public static Map<String, Object> thinking(String content, Object result) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "thinking");
        event.put("content", content);
        if (result != null) {
            event.put("result", result);
        }
        return event;
    }

    /** 直接回复用户的消息（type=text），对齐 Python ask_user() */
    public static Map<String, Object> askUser(String content) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "text");
        event.put("content", content);
        return event;
    }

    /** 大模型推理过程（type=reasoning），对齐 Python reasoning() */
    public static Map<String, Object> reasoning(String content) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "reasoning");
        event.put("content", content);
        return event;
    }

    /** 推理过程（带 step 标签） */
    public static Map<String, Object> reasoning(String content, String step) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "reasoning");
        event.put("content", content);
        if (step != null) {
            event.put("step", step);
        }
        return event;
    }

    /** 消息事件（type=message） */
    public static Map<String, Object> message(String content) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "message");
        event.put("content", content);
        return event;
    }

    /** 错误事件（type=error） */
    public static Map<String, Object> error(String content) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "error");
        event.put("content", content);
        return event;
    }

    /** text_start 事件 */
    public static Map<String, Object> textStart() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "text_start");
        return event;
    }

    /** text 事件 */
    public static Map<String, Object> text(String content) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "text");
        event.put("content", content);
        return event;
    }

    /** text_end 事件 */
    public static Map<String, Object> textEnd() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "text_end");
        return event;
    }

    /** 统计事件（type=stats），对齐 Python sse({"type": "stats", "content": stats.to_dict()}) */
    public static Map<String, Object> stats(StreamStats stats) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "stats");
        event.put("content", stats.toMap());
        return event;
    }

    // ==================== 统一意图事件 ====================

    /**
     * 统一的意图事件格式（版本 2.0）—— 对齐 Python intent_event()。
     *
     * @param intentType 意图类型
     * @param action     子操作（generate/update/delete/import/export 等）
     * @param data       意图数据
     * @param isForm     是否表单意图
     */
    public static Map<String, Object> intentEvent(String intentType, String action,
                                                    Object data, boolean isForm) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "intent");
        event.put("version", "2.0");
        event.put("intentType", intentType);
        event.put("action", action != null ? action : "");
        event.put("data", data != null ? data : Map.of());
        event.put("isForm", isForm);
        return event;
    }

    /**
     * 统一的 done 事件格式 —— 对齐 Python done_event()。
     */
    public static Map<String, Object> doneEvent(String intentType, boolean isForm,
                                                 Map<String, Object> intentData) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "done");
        event.put("isForm", isForm);
        if (intentType != null && !intentType.isEmpty()) {
            event.put("intentType", intentType);
        }
        if (intentData != null) {
            event.put("intentData", intentData);
        }
        return event;
    }

    /** done 事件（不带 intentData） */
    public static Map<String, Object> doneEvent(String intentType, boolean isForm) {
        return doneEvent(intentType, isForm, null);
    }

    // ==================== 推荐合并工具 ====================

    /**
     * 合并两路 fieldRecommendations —— 对齐 Python merge_field_recommendations()。
     *
     * <p>合并策略：LLM 推荐优先保留（source=llm_rule），引擎推荐作为补充追加。
     *
     * @param llmRecs    LLM 意图识别输出的推荐
     * @param engineRecs 推荐引擎输出的推荐
     * @param maxPerField 每个字段最大推荐数
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeFieldRecommendations(
            Map<String, Object> llmRecs,
            Map<String, Object> engineRecs,
            int maxPerField) {

        Map<String, Object> merged = new LinkedHashMap<>();

        // 先注入 LLM 推荐
        if (llmRecs != null) {
            for (Map.Entry<String, Object> entry : llmRecs.entrySet()) {
                String fieldCode = entry.getKey();
                Object recData = entry.getValue();
                if (recData instanceof List<?> list) {
                    Map<String, Object> fieldRec = new LinkedHashMap<>();
                    fieldRec.put("items", list.stream()
                            .filter(item -> item instanceof Map<?, ?>)
                            .map(item -> {
                                Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) item);
                                m.putIfAbsent("source", "llm_rule");
                                return m;
                            })
                            .toList());
                    fieldRec.put("strategyUsed", List.of("llm_rule_inference"));
                    fieldRec.put("_has_llm", true);
                    merged.put(fieldCode, fieldRec);
                } else if (recData instanceof Map<?, ?> recMap) {
                    Map<String, Object> fieldRec = new LinkedHashMap<>();
                    Object items = recMap.get("items");
                    if (items instanceof List<?> itemList) {
                        List<Map<String, Object>> copiedItems = new java.util.ArrayList<>();
                        for (Object item : itemList) {
                            if (item instanceof Map<?, ?> map) {
                                copiedItems.add(new LinkedHashMap<>((Map<String, Object>) map));
                            }
                        }
                        fieldRec.put("items", copiedItems);
                    } else {
                        fieldRec.put("items", List.of());
                    }
                    Object strategy = recMap.get("strategyUsed");
                    fieldRec.put("strategyUsed", strategy != null ? strategy : List.of("llm_rule_inference"));
                    fieldRec.put("_has_llm", true);
                    merged.put(fieldCode, fieldRec);
                }
            }
        }

        // 追加引擎推荐
        if (engineRecs != null) {
            for (Map.Entry<String, Object> entry : engineRecs.entrySet()) {
                String fieldCode = entry.getKey();
                Object engineData = entry.getValue();

                List<?> engineItems = null;
                if (engineData instanceof Map<?, ?> engineMap) {
                    Object items = engineMap.get("items");
                    if (items instanceof List<?> list) {
                        engineItems = list;
                    }
                } else if (engineData instanceof List<?> list) {
                    engineItems = list;
                }

                if (engineItems == null || engineItems.isEmpty()) {
                    continue;
                }

                if (merged.containsKey(fieldCode)) {
                    // 去重追加
                    Map<String, Object> existing = (Map<String, Object>) merged.get(fieldCode);
                    Object existingItemsObj = existing.get("items");
                    List<Object> existingItems = existingItemsObj instanceof List<?> list ? new java.util.ArrayList<>(list.size()) : new java.util.ArrayList<>();
                    if (existingItemsObj instanceof List<?> list) {
                        for (Object item : list) {
                            existingItems.add(item);
                        }
                    }
                    java.util.Set<Object> existingValues = new java.util.HashSet<>();
                    for (Object item : existingItems) {
                        if (item instanceof Map<?, ?> m) {
                            existingValues.add(m.get("value"));
                        }
                    }
                    for (Object item : engineItems) {
                        if (item instanceof Map<?, ?> m) {
                            Object value = m.get("value");
                            if (!existingValues.contains(value)) {
                                existingItems.add(item);
                                existingValues.add(value);
                            }
                        }
                    }
                } else {
                    Map<String, Object> fieldRec = new LinkedHashMap<>();
                    List<Object> copiedItems = new java.util.ArrayList<>();
                    for (Object item : engineItems) {
                        if (item instanceof Map<?, ?> map) {
                            copiedItems.add(new LinkedHashMap<>((Map<String, Object>) map));
                        }
                    }
                    fieldRec.put("items", copiedItems);
                    if (engineData instanceof Map<?, ?> engineMap) {
                        Object strategy = engineMap.get("strategyUsed");
                        fieldRec.put("strategyUsed", strategy != null ? strategy : List.of());
                    } else {
                        fieldRec.put("strategyUsed", List.of());
                    }
                    merged.put(fieldCode, fieldRec);
                }
            }
        }

        // 按 maxPerField 截断
        for (Map.Entry<String, Object> entry : merged.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> fieldRec) {
                Object items = fieldRec.get("items");
                if (items instanceof List<?> list && list.size() > maxPerField) {
                    List<Map<String, Object>> llmItems = new java.util.ArrayList<>();
                    List<Map<String, Object>> otherItems = new java.util.ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m && "llm_rule".equals(m.get("source"))) {
                            llmItems.add(new LinkedHashMap<>((Map<String, Object>) m));
                        } else if (item instanceof Map<?, ?> m) {
                            otherItems.add(new LinkedHashMap<>((Map<String, Object>) m));
                        }
                    }
                    otherItems.sort((a, b) -> {
                        double ca = 0;
                        if (a instanceof Map<?, ?>) {
                            Map<String, Object> ma = (Map<String, Object>) a;
                            ca = ((Number) (ma.containsKey("confidence") ? ma.get("confidence") : 0)).doubleValue();
                        }
                        double cb = 0;
                        if (b instanceof Map<?, ?>) {
                            Map<String, Object> mb = (Map<String, Object>) b;
                            cb = ((Number) (mb.containsKey("confidence") ? mb.get("confidence") : 0)).doubleValue();
                        }
                        return Double.compare(cb, ca);
                    });
                    List<Map<String, Object>> truncated = new java.util.ArrayList<>(llmItems);
                    truncated.addAll(otherItems);
                    while (truncated.size() > maxPerField) {
                        truncated.remove(truncated.size() - 1);
                    }
                    ((Map<String, Object>) entry.getValue()).put("items", truncated);
                }
            }
        }

        return merged;
    }

    /** 合并推荐（默认每字段最大 5 条） */
    public static Map<String, Object> mergeFieldRecommendations(
            Map<String, Object> llmRecs,
            Map<String, Object> engineRecs) {
        return mergeFieldRecommendations(llmRecs, engineRecs, 5);
    }
}
