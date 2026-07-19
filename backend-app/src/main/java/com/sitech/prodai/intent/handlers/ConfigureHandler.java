package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.dto.ChatCompletionRequest;
import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.OntologyService;
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
 * 新业务配置意图处理器 —— 对齐 Python {@code app/intent/handlers/configure_handler.py::ConfigureHandler}。
 *
 * <p>AI 对话生成新表单配置。Python 端调用 {@code AdminService.chat(ai_messages)}，
 * Java 端使用 {@link LlmService} 做等价调用（LLM 生成配置 JSON）。
 */
@Component
@ConditionalOnProperty(name = "prodai.llm.enabled", havingValue = "true", matchIfMissing = false)
public class ConfigureHandler implements BaseIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfigureHandler.class);

    private final LlmService llmService;
    private final OntologyService ontologyService;

    public ConfigureHandler(LlmService llmService, OntologyService ontologyService) {
        this.llmService = llmService;
        this.ontologyService = ontologyService;
    }

    @Override
    public String getIntentType() {
        return "configure";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        Map<String, Object> intentData = ctx.getIntentData();
        String suggestedCode = str(intentData.get("formCode"));
        String suggestedName = str(intentData.get("formName"));

        // 构建 AI 对话请求
        ChatCompletionRequest aiRequest = new ChatCompletionRequest();
        aiRequest.setSystemPrompt(buildConfigureSystemPrompt());
        List<ChatCompletionRequest.ChatMessage> aiMessages = new ArrayList<>();
        if (ctx.getMessages() != null) {
            for (Map<String, Object> msg : ctx.getMessages()) {
                ChatCompletionRequest.ChatMessage cm = new ChatCompletionRequest.ChatMessage();
                cm.setRole(str(msg.get("role")));
                cm.setContent(str(msg.get("content")));
                aiMessages.add(cm);
            }
        } else {
            ChatCompletionRequest.ChatMessage cm = new ChatCompletionRequest.ChatMessage();
            cm.setRole("user");
            cm.setContent(ctx.getLastUserMessage());
            aiMessages.add(cm);
        }
        aiRequest.setMessages(aiMessages);

        return reactor.core.publisher.Mono.fromCallable(() -> llmService.complete(aiRequest))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(aiResult -> {
                    List<Map<String, Object>> events = new ArrayList<>();
                    StreamStats stats = ctx.getStreamStats();
                    if (stats != null) {
                        stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
                    }

                    String replyText = str(aiResult.get("content"));
                    // 尝试从 LLM 回复中解析配置 JSON
                    Map<String, Object> configData = parseConfigJson(replyText);
                    boolean hasConfig = !configData.isEmpty();

                    if (hasConfig) {
                        int fieldCount = 0;
                        Object entities = configData.get("entities");
                        if (entities instanceof List<?> entityList) {
                            for (Object entity : entityList) {
                                if (entity instanceof Map<?, ?> entityMap) {
                                    Object fields = entityMap.get("fields");
                                    if (fields instanceof List<?> fieldList) {
                                        fieldCount += fieldList.size();
                                    }
                                }
                            }
                        }

                        // Phase 1：识别
                        events.add(SseUtils.thinking(
                                "🛠\uFE0F 识别到新业务配置请求: " + suggestedName,
                                Map.of("suggestedCode", suggestedCode, "suggestedName", suggestedName)
                        ));

                        // Phase 2：执行
                        events.add(SseUtils.thinking(
                                "✅ 配置生成完成: " + str(configData.get("formName")) + " (" + fieldCount + " 个字段)",
                                Map.of(
                                        "formName", str(configData.get("formName")),
                                        "formCode", str(configData.get("formCode")),
                                        "fieldCount", fieldCount,
                                        "entityCount", configData.getOrDefault("entities", List.of()) instanceof List ? ((List<?>) configData.get("entities")).size() : 0
                                )
                        ));

                        // Phase 3：输出
                        String desc = "已为您生成 **" + str(configData.get("formName")) + "** 表单配置，包含 " + fieldCount + " 个字段。";
                        events.add(SseUtils.textStart());
                        for (int i = 0; i < desc.length(); i += 3) {
                            events.add(SseUtils.text(desc.substring(i, Math.min(i + 3, desc.length()))));
                        }
                        if (!replyText.isEmpty()) {
                            for (int i = 0; i < replyText.length(); i += 3) {
                                events.add(SseUtils.text(replyText.substring(i, Math.min(i + 3, replyText.length()))));
                            }
                        }
                        events.add(SseUtils.textEnd());

                        if (stats != null) {
                            events.add(SseUtils.stats(stats));
                        }
                        events.add(SseUtils.intentEvent("configure", "generate",
                                Map.of("config", configData, "reply", replyText), false));
                        events.add(SseUtils.doneEvent("configure", false, ctx.getIntentData()));
                    } else {
                        // AI 引导用户补充需求
                        String guideReply = replyText.isEmpty() ? "请描述你想创建的表单类型。" : replyText;

                        events.add(SseUtils.thinking("💬 正在引导您描述需求...", Map.of("mode", "guide", "reply", guideReply)));

                        if (stats != null) {
                            events.add(SseUtils.stats(stats));
                        }
                        events.add(SseUtils.textStart());
                        for (int i = 0; i < guideReply.length(); i += 3) {
                            events.add(SseUtils.text(guideReply.substring(i, Math.min(i + 3, guideReply.length()))));
                        }
                        events.add(SseUtils.textEnd());
                        events.add(SseUtils.doneEvent("configure", false, ctx.getIntentData()));
                    }
                    return Flux.fromIterable(events);
                })
                .onErrorResume(e -> {
                    log.error("[ConfigureHandler] 配置生成失败", e);
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "配置生成失败";
                    List<Map<String, Object>> events = new ArrayList<>();
                    StreamStats stats = ctx.getStreamStats();
                    if (stats != null) {
                        stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
                        stats.setError(errorMsg);
                    }
                    events.add(SseUtils.thinking("❌ 配置生成失败: " + errorMsg, Map.of("success", false, "error", errorMsg)));
                    if (stats != null) {
                        events.add(SseUtils.stats(stats));
                    }
                    events.add(SseUtils.error(errorMsg));
                    events.add(SseUtils.doneEvent("configure", false, ctx.getIntentData()));
                    return Flux.fromIterable(events);
                });
    }

    private String buildConfigureSystemPrompt() {
        return """
                你是一个表单配置生成助手。根据用户的描述，生成表单配置 JSON。
                配置格式：
                {
                  "formCode": "表单编码",
                  "formName": "表单名称",
                  "description": "表单描述",
                  "entities": [
                    {
                      "entityCode": "实体编码",
                      "entityName": "实体名称",
                      "fields": [
                        {"fieldCode": "字段编码", "fieldName": "字段名称", "fieldType": "input", "required": true}
                      ]
                    }
                  ]
                }
                如果用户描述的信息不足以生成完整配置，请引导用户补充更多信息。""";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigJson(String text) {
        if (text == null || text.isEmpty()) {
            return new LinkedHashMap<>();
        }
        // 尝试从文本中提取 JSON 块
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String json = text.substring(start, end + 1);
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception e) {
                log.debug("[ConfigureHandler] JSON 解析失败: {}", e.getMessage());
            }
        }
        return new LinkedHashMap<>();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
