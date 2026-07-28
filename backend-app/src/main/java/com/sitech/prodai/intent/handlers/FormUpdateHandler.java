package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.intent.ThinkingStepBuilder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 表单更新意图处理器 —— 对齐 Python FormUpdateHandler。
 */
@Component
public class FormUpdateHandler implements BaseIntentHandler {

    @Override
    public String getIntentType() {
        return "form_update";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        Map<String, Object> intentData = ctx.getIntentData();
        String detectedFormCode = str(intentData.get("detectedFormCode"));
        String formName = "";
        if (detectedFormCode != null && ctx.getOntologies().containsKey(detectedFormCode)) {
            formName = str(ctx.getOntologies().get(detectedFormCode).get("formName"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> extracted = (Map<String, Object>) intentData.getOrDefault("extractedFields", Map.of());
        double confidence = toDouble(intentData.get("confidence"));
        String displayName = formName.isEmpty() ? detectedFormCode : formName;
        String fieldList = extracted.isEmpty()
                ? null
                : extracted.keySet().stream().map(String::valueOf).collect(Collectors.joining("、"));

        StreamStats stats = ctx.getStreamStats();
        if (stats != null) {
            stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
            stats.setForm(true);
        }

        return Flux.fromIterable(List.of(
                ThinkingStepBuilder.done(
                        "identify", "识别更新意图", "识别表单更新目标",
                        displayName, 2, 4, 0, null,
                        Map.of("formCode", detectedFormCode, "formName", displayName)),
                ThinkingStepBuilder.done(
                        "extract", "提取待改字段", "提取待更新字段",
                        extracted.isEmpty() ? "未提取到字段" : "提取到 " + extracted.size() + " 个待更新字段",
                        3, 4, 0, fieldList,
                        Map.of("extractedCount", extracted.size(), "confidence", confidence)),
                ThinkingStepBuilder.done(
                        "apply", "应用更新", "应用字段更新",
                        extracted.isEmpty() ? "无字段可更新" : "已准备更新 " + extracted.size() + " 个字段",
                        4, 4, 0, null, Map.of("extractedCount", extracted.size())),
                stats != null ? SseUtils.stats(stats) : SseUtils.stats(new StreamStats()),
                SseUtils.intentEvent("form", "update", ctx.getIntentResult(), true),
                SseUtils.doneEvent("form", true, ctx.getIntentData())
        ));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
