package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.StreamStats;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单更新意图处理器 —— 对齐 Python {@code app/intent/handlers/form_update_handler.py::FormUpdateHandler}。
 *
 * <p>增量更新字段：提取已更新字段 → 发送 intent_event → 完成。
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

        // Phase 1：识别
        Map<String, Object> identifyResult = new LinkedHashMap<>();
        identifyResult.put("formCode", detectedFormCode);
        identifyResult.put("formName", !formName.isEmpty() ? formName : detectedFormCode);

        // Phase 2：执行
        Map<String, Object> execResult = new LinkedHashMap<>();
        execResult.put("extractedFields", extracted.keySet());
        execResult.put("extractedCount", extracted.size());
        execResult.put("confidence", confidence);
        execResult.put("confidenceLevel", confidence >= 0.8 ? "high" : confidence >= 0.5 ? "medium" : "low");

        // Phase 3：输出
        StreamStats stats = ctx.getStreamStats();
        if (stats != null) {
            stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
            stats.setForm(true);
        }

        return Flux.fromIterable(List.of(
                SseUtils.thinking("\uD83D\uDCCB 识别到表单更新「" + (formName.isEmpty() ? detectedFormCode : formName) + "」", identifyResult),
                SseUtils.thinking(
                        extracted.isEmpty() ? "⚠\uFE0F 未提取到任何字段值"
                                : "📝 提取到 " + extracted.size() + " 个待更新字段",
                        execResult),
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
