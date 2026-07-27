package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.RecommendationEngine;
import com.sitech.prodai.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单首次生成意图处理器 —— 对齐 Python {@code app/intent/handlers/form_handler.py::FormHandler}。
 *
 * <p>处理流程：
 * <ol>
 *   <li>Phase 1：识别表单类型</li>
 *   <li>Phase 2：AI 字段推断（LLM 调用） + 推荐引擎批量推荐</li>
 *   <li>Phase 3：合并推荐 → 发送 intent_event → 统计 → done</li>
 * </ol>
 *
 * <p>Python 端调用 {@code ai_inference_service.infer_fields()} 做 AI 推断，
 * Java 端使用 {@link LlmService} 做等价调用（基于本体 prompt + LLM 推断字段值）。
 */
@Component
@ConditionalOnProperty(name = "prodai.llm.enabled", havingValue = "true", matchIfMissing = false)
public class FormHandler implements BaseIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(FormHandler.class);

    private final LlmService llmService;
    private final RecommendationEngine recommendationEngine;
    private final ConfigLoader configLoader;

    public FormHandler(LlmService llmService,
                       RecommendationEngine recommendationEngine,
                       ConfigLoader configLoader) {
        this.llmService = llmService;
        this.recommendationEngine = recommendationEngine;
        this.configLoader = configLoader;
    }

    @Override
    public String getIntentType() {
        return "form";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        if (IntentRecognitionSupport.isMetaGuideRequest(ctx.getLastUserMessage())) {
            return Flux.fromIterable(IntentRecognitionSupport.metaGuideSkipEvents("表单/配置生成"));
        }
        Map<String, Object> intentData = ctx.getIntentData();
        String formCode = str(intentData.get("formCode"));
        String formName = "";
        if (formCode != null && ctx.getOntologies().containsKey(formCode)) {
            formName = str(ctx.getOntologies().get(formCode).get("formName"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> extracted = (Map<String, Object>) intentData.getOrDefault("extractedFields", new LinkedHashMap<>());
        double confidence = toDouble(intentData.get("confidence"));

        final String code = formCode != null ? formCode : "";
        final String name = formName.isEmpty() ? code : formName;

        List<Map<String, Object>> initialEvents = new ArrayList<>();

        // ═══ Phase 1：识别 ══════════════════════════════════════════
        Map<String, Object> identifyResult = new LinkedHashMap<>();
        identifyResult.put("formCode", code);
        identifyResult.put("formName", name);
        identifyResult.put("confidence", confidence);
        identifyResult.put("confidenceLevel", confidence >= 0.8 ? "high" : confidence >= 0.5 ? "medium" : "low");
        initialEvents.add(SseUtils.thinking("📋 识别到表单「" + name + "」", identifyResult));

        // ═══ Phase 2：执行 ══════════════════════════════════════════
        // Step 1：AI 字段推断
        initialEvents.add(SseUtils.thinking("🧠 正在分析字段推断..."));

        return Flux.concat(
                Flux.fromIterable(initialEvents),

                // AI 字段推断（异步）
                reactor.core.publisher.Mono.fromCallable(() -> inferFieldsWithLlm(ctx, code, extracted, name))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(inferResult -> {
                            List<Map<String, Object>> events = new ArrayList<>();
                            Map<String, Object> inferredFields = inferResult.inferredFields;
                            String reasoning = inferResult.reasoning;

                            // 合并 LLM 意图识别结果和 AI 推断结果
                            extracted.putAll(inferredFields);
                            ctx.getIntentData().put("extractedFields", extracted);

                            events.add(SseUtils.thinking(
                                    "✅ AI 推断完成，共 " + inferredFields.size() + " 个字段",
                                    Map.of(
                                            "inferredFields", inferredFields.keySet(),
                                            "inferredCount", inferredFields.size(),
                                            "sample", inferredFields.entrySet().stream()
                                                    .limit(5)
                                                    .collect(java.util.stream.Collectors.toMap(
                                                            Map.Entry::getKey, Map.Entry::getValue,
                                                            (a, b) -> a, LinkedHashMap::new))
                                    )
                            ));

                            if (reasoning != null && !reasoning.isEmpty()) {
                                events.add(SseUtils.reasoning(reasoning));
                            }

                            // Step 2：历史推荐
                            events.addAll(doRecommendation(ctx, code, extracted));

                            // ═══ Phase 3：输出 ══════════════════════════════════════════
                            events.add(SseUtils.intentEvent("form", "generate", ctx.getIntentData(), true));

                            StreamStats stats = ctx.getStreamStats();
                            if (stats != null) {
                                stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
                                stats.setForm(true);
                                events.add(SseUtils.stats(stats));
                            }

                            events.add(SseUtils.thinking("✅ 准备生成 " + name + " 表单..."));
                            events.add(SseUtils.doneEvent("form", true, ctx.getIntentData()));

                            return Flux.fromIterable(events);
                        })
                        .onErrorResume(e -> {
                            log.error("[FormHandler] 处理失败", e);
                            List<Map<String, Object>> events = new ArrayList<>();
                            events.add(SseUtils.thinking("⚠\uFE0F 处理异常: " + e.getMessage()));
                            events.add(SseUtils.intentEvent("form", "generate", ctx.getIntentData(), true));
                            StreamStats stats = ctx.getStreamStats();
                            if (stats != null) {
                                stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
                                stats.setForm(true);
                                events.add(SseUtils.stats(stats));
                            }
                            events.add(SseUtils.doneEvent("form", true, ctx.getIntentData()));
                            return Flux.fromIterable(events);
                        })
        );
    }

    /** AI 字段推断（对齐 Python ai_inference_service.infer_fields） */
    private InferResult inferFieldsWithLlm(IntentContext ctx, String formCode,
                                           Map<String, Object> extracted, String formName) {
        if (formCode == null || formCode.isEmpty()) {
            return new InferResult(new LinkedHashMap<>(), "");
        }
        try {
            // 构建推断 prompt
            Map<String, Object> ontology = configLoader.getOntology(formCode);
            if (ontology == null) {
                return new InferResult(new LinkedHashMap<>(), "");
            }

            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一个表单字段推断助手。根据用户输入推断表单字段值。\n\n");
            prompt.append("表单类型：").append(formName).append("（").append(formCode).append("）\n");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) ontology.getOrDefault("entities", List.of());
            prompt.append("可填字段：\n");
            for (Map<String, Object> entity : entities) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.getOrDefault("fields", List.of());
                for (Map<String, Object> field : fields) {
                    prompt.append("- ").append(field.get("fieldCode"))
                          .append(" (").append(field.getOrDefault("fieldName", "")).append(")");
                    if (Boolean.TRUE.equals(field.get("required"))) {
                        prompt.append(" [必填]");
                    }
                    prompt.append("\n");
                }
            }
            prompt.append("\n用户输入：").append(ctx.getLastUserMessage()).append("\n");
            if (!extracted.isEmpty()) {
                prompt.append("\n已提取字段：").append(extracted).append("\n");
            }
            prompt.append("\n请输出 JSON 格式的字段推断结果，如 {\"fieldCode\": \"value\"}。仅输出 JSON，不要其他内容。");

            String llmResponse = llmService.completePrompt(prompt.toString());
            // 尝试解析 LLM 返回的 JSON
            Map<String, Object> inferred = parseJsonToMap(llmResponse);
            String reasoning = "";  // Java 端暂不提取 reasoning

            return new InferResult(inferred, reasoning);
        } catch (Exception e) {
            log.warn("[FormHandler] AI 推断失败: {}，使用已有数据", e.getMessage());
            return new InferResult(new LinkedHashMap<>(), "");
        }
    }

    /** 历史推荐（对齐 Python recommendation_engine.batch_recommend） */
    private List<Map<String, Object>> doRecommendation(IntentContext ctx, String formCode,
                                                       Map<String, Object> extracted) {
        List<Map<String, Object>> events = new ArrayList<>();
        try {
            // 获取字段列表
            Map<String, Object> ontologyDef = ctx.getOntologies().getOrDefault(formCode, new LinkedHashMap<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) ontologyDef.getOrDefault("entities", List.of());
            List<String> allFieldCodes = new ArrayList<>();
            for (Map<String, Object> entity : entities) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.getOrDefault("fields", List.of());
                for (Map<String, Object> field : fields) {
                    allFieldCodes.add(str(field.get("fieldCode")));
                }
            }

            events.add(SseUtils.thinking("📚 正在查询历史推荐数据...", Map.of(
                    "step", "recommendation_engine_init",
                    "formCode", formCode,
                    "fieldCount", allFieldCodes.size()
            )));

            events.add(SseUtils.thinking("🔧 推荐引擎执行中...", Map.of(
                    "step", "recommendation_engine_execute",
                    "strategies", List.of("ai_recommend", "frequency", "user_personalized", "time_decay", "static")
            )));

            // 调用推荐引擎批量推荐
            // batchRecommend 签名：(formCode, extractedFields(Map<String,String>), userInput, userId, conversationContext, maxPerField, fieldCodes)
            Map<String, String> extractedStr = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : extracted.entrySet()) {
                extractedStr.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
            Map<String, com.sitech.prodai.service.RecommendationEngine.RecommendationResult> recommendationsResult =
                    recommendationEngine.batchRecommend(
                            formCode,
                            extractedStr,
                            ctx.getLastUserMessage(),
                            ctx.resolveUserId(),
                            null,   // conversationContext
                            3,      // maxPerField
                            allFieldCodes.isEmpty() ? null : allFieldCodes
                    );

            // 转换为 allRecommendations 格式 {fieldCode: {items: [...], strategyUsed: [...], ...}}
            Map<String, Object> allRecommendations = new LinkedHashMap<>();
            int totalRecs = 0;
            for (Map.Entry<String, com.sitech.prodai.service.RecommendationEngine.RecommendationResult> entry : recommendationsResult.entrySet()) {
                com.sitech.prodai.service.RecommendationEngine.RecommendationResult rec = entry.getValue();
                if (rec.success && !rec.recommendations.isEmpty()) {
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (com.sitech.prodai.service.RecommendationEngine.RecommendationItem item : rec.recommendations) {
                        items.add(item.toDict());
                    }
                    Map<String, Object> fieldRec = new LinkedHashMap<>();
                    fieldRec.put("items", items);
                    fieldRec.put("strategyUsed", rec.strategyUsed);
                    fieldRec.put("totalCandidates", rec.totalCandidates);
                    fieldRec.put("processingTimeMs", Math.round(rec.processingTimeMs * 100.0) / 100.0);
                    allRecommendations.put(entry.getKey(), fieldRec);
                    totalRecs += items.size();
                }
            }

            events.add(SseUtils.thinking(
                    "📚 为 " + allRecommendations.size() + " 个字段生成推荐，共 " + totalRecs + " 条",
                    Map.of(
                            "step", "recommendation_complete",
                            "fieldCount", allRecommendations.size(),
                            "totalRecommendations", totalRecs
                    )
            ));

            // 合并 LLM 推荐和引擎推荐
            @SuppressWarnings("unchecked")
            Map<String, Object> llmRecs = (Map<String, Object>) ctx.getIntentData().getOrDefault("fieldRecommendations", new LinkedHashMap<>());
            Map<String, Object> merged = SseUtils.mergeFieldRecommendations(llmRecs, allRecommendations);
            ctx.getIntentData().put("fieldRecommendations", merged);

        } catch (Exception e) {
            log.error("[FormHandler] 推荐引擎异常", e);
            events.add(SseUtils.thinking("⚠\uFE0F 推荐引擎异常: " + e.getMessage()));
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.debug("[FormHandler] JSON 解析失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
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

    private record InferResult(Map<String, Object> inferredFields, String reasoning) {
    }
}
