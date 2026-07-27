package com.sitech.prodai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentHandlerRegistry;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.service.ChatPersistenceService;
import com.sitech.prodai.service.IntentPromptManager;
import com.sitech.prodai.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);

    private final IntentHandlerRegistry intentRegistry;
    private final Optional<LlmService> llmService;
    private final ConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final IntentPromptManager intentPromptManager;
    private final Optional<ChatPersistenceService> persistenceService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatStreamController(IntentHandlerRegistry intentRegistry,
                                Optional<LlmService> llmService,
                                ConfigLoader configLoader,
                                ObjectMapper objectMapper,
                                IntentPromptManager intentPromptManager,
                                Optional<ChatPersistenceService> persistenceService) {
        this.intentRegistry = intentRegistry;
        this.llmService = llmService;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
        this.intentPromptManager = intentPromptManager;
        this.persistenceService = persistenceService;
    }

    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentStream(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        // SSE 异步异常回调 —— 防止 ControllerAdvice 无法捕获 SSE 线程内的异常
        emitter.onTimeout(() -> {
            log.warn("[chat/agent/stream] SSE 超时，客户端 sessionId={}", request.get("sessionId"));
            emitter.complete();
        });
        emitter.onError(e -> {
            log.warn("[chat/agent/stream] SSE 错误: {}", e.getMessage());
        });
        emitter.onCompletion(() -> {
            log.debug("[chat/agent/stream] SSE 连接完成");
        });

        executor.execute(() -> {
            String sessionId = str(request.get("sessionId"), str(request.get("session_id")));
            String userId = str(request.get("userId"), str(request.get("user_id")));
            if (userId.isBlank()) userId = "default";

            // 自动分配 sessionId
            if (sessionId.isBlank()) {
                sessionId = "sess_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }

            String intentType = "chat";
            Map<String, Object> intentData = new LinkedHashMap<>();

            // 提前提取 lastUserMessage（effectively final，供 lambda 捕获）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allMessages = (List<Map<String, Object>>) request.getOrDefault("messages", List.of());
            String lastUserMessage = "";
            for (int i = allMessages.size() - 1; i >= 0; i--) {
                Map<String, Object> msg = allMessages.get(i);
                if ("user".equals(str(msg.get("role")))) {
                    lastUserMessage = str(msg.get("content"));
                    break;
                }
            }

            try {
                // 告知前端最终 sessionId（新建对话时由首条消息绑定）
                Map<String, Object> sessionEvent = new LinkedHashMap<>();
                sessionEvent.put("type", "session");
                sessionEvent.put("sessionId", sessionId);
                sendEvent(emitter, sessionEvent);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> messages = (List<Map<String, Object>>) request.getOrDefault("messages", List.of());

                // 若前端未传消息体，尝试从 DB 加载历史上下文
                if (messages.isEmpty() && persistenceService.isPresent()) {
                    try {
                        List<Map<String, String>> dbHistory = persistenceService.get().getRecentMessages(sessionId, 20);
                        List<Map<String, Object>> converted = new ArrayList<>();
                        for (Map<String, String> m : dbHistory) {
                            converted.add(new LinkedHashMap<>(m));
                        }
                        messages = converted;
                    } catch (Exception e) {
                        log.debug("[chat/agent/stream] 从 DB 加载历史消息失败: {}", e.getMessage());
                    }
                }

                String scene = str(request.get("scene"));

                StringBuilder messagesText = new StringBuilder();
                for (Map<String, Object> msg : messages) {
                    messagesText.append(str(msg.get("role"))).append(": ")
                            .append(str(msg.get("content"))).append("\n");
                }

                Map<String, Map<String, Object>> ontologies = configLoader.getAllOntologies();
                StringBuilder ontologiesInfo = new StringBuilder();
                for (Map.Entry<String, Map<String, Object>> e : ontologies.entrySet()) {
                    ontologiesInfo.append("- ").append(e.getKey()).append(": ")
                            .append(str(e.getValue().get("formName"))).append("\n");
                }

                // ── 步骤 1：分析输入 ──────────────────────────────
                long stepStart = System.currentTimeMillis();
                sendEvent(emitter, SseUtils.thinkingRich(
                        "正在分析用户输入...",
                        Map.of(
                                "step", 1,
                                "totalSteps", 4,
                                "messagesCount", messages.size(),
                                "scene", scene.isEmpty() ? "未指定" : scene,
                                "inputPreview", lastUserMessage.length() > 80
                                        ? lastUserMessage.substring(0, 80) + "..." : lastUserMessage
                        ),
                        0));

                // ── 步骤 2：构建意图识别 Prompt ─────────────────
                long step2Start = System.currentTimeMillis();
                String intentPrompt = intentPromptManager.renderIntentPrompt(
                        messagesText.toString(), lastUserMessage, ontologiesInfo.toString(), scene);
                long step2Elapsed = System.currentTimeMillis() - step2Start;

                // 统计本体加载情况
                int ontologyCount = ontologies.size();
                StringBuilder ontologyNames = new StringBuilder();
                for (String key : ontologies.keySet()) {
                    if (!ontologyNames.isEmpty()) ontologyNames.append(", ");
                    ontologyNames.append(key);
                }

                sendEvent(emitter, SseUtils.thinkingRich(
                        "已加载 " + ontologyCount + " 个本体，构建意图识别 Prompt...",
                        Map.of(
                                "step", 2,
                                "totalSteps", 4,
                                "promptLength", intentPrompt.length(),
                                "ontologyCount", ontologyCount,
                                "ontologyNames", ontologyNames.toString(),
                                "scene", scene.isEmpty() ? "通用" : scene,
                                "conversationDepth", messages.size()
                        ),
                        step2Elapsed));

                // ── 步骤 3：意图识别（meta → 窄白名单 → LLM → 关键词 fallback） ──
                long step3Start = System.currentTimeMillis();
                StreamStats streamStats = new StreamStats();
                streamStats.recordInputTokens(intentPrompt);

                String intentResult = "";
                String intentSource = IntentRecognitionSupport.SOURCE_LLM;
                boolean skippedLlm = false;

                if (IntentRecognitionSupport.isMetaGuideRequest(lastUserMessage)) {
                    // meta：只要说明/勿执行 → 强制 chat，禁止业务 Handler
                    intentData = IntentRecognitionSupport.chatMetaResult();
                    intentSource = IntentRecognitionSupport.SOURCE_META;
                    skippedLlm = true;
                    sendEvent(emitter, SseUtils.thinkingRich(
                            "识别为使用说明/勿执行请求，走通用对话，跳过业务意图...",
                            Map.of(
                                    "step", 3,
                                    "totalSteps", 4,
                                    "source", intentSource,
                                    "intentType", "chat"
                            ),
                            0
                    ));
                } else if (lastUserMessage == null || lastUserMessage.isBlank()) {
                    Map<String, Object> byScene = IntentRecognitionSupport.resolveBlankInputByScene(scene);
                    if (byScene != null) {
                        intentData = byScene;
                        intentSource = IntentRecognitionSupport.SOURCE_SCENE_DEFAULT;
                        skippedLlm = true;
                        sendEvent(emitter, SseUtils.thinkingRich(
                                "空输入，按当前场景默认意图...",
                                Map.of(
                                        "step", 3,
                                        "totalSteps", 4,
                                        "source", intentSource,
                                        "intentType", str(byScene.get("intentType"))
                                ),
                                0
                        ));
                    }
                } else {
                    Map<String, Object> whitelist = IntentRecognitionSupport.tryNarrowWhitelist(lastUserMessage);
                    if (whitelist != null && !str(whitelist.get("intentType")).isBlank()) {
                        intentData = whitelist;
                        intentSource = IntentRecognitionSupport.SOURCE_WHITELIST;
                        skippedLlm = true;
                        sendEvent(emitter, SseUtils.thinkingRich(
                                "命中短指令白名单，跳过意图 LLM...",
                                Map.of(
                                        "step", 3,
                                        "totalSteps", 4,
                                        "source", intentSource,
                                        "intentType", str(whitelist.get("intentType"))
                                ),
                                0
                        ));
                    } else {
                        sendEvent(emitter, SseUtils.thinkingRich(
                                "正在调用大模型识别意图...",
                                Map.of(
                                        "step", 3,
                                        "totalSteps", 4,
                                        "phase", "running",
                                        "promptLength", intentPrompt.length()
                                ),
                                -1
                        ));
                        intentResult = completePromptWithHeartbeat(emitter, intentPrompt, step3Start);
                        streamStats.recordOutputText(intentResult);
                        intentData = parseIntentResult(intentResult);
                        intentSource = IntentRecognitionSupport.SOURCE_LLM;

                        String parsedType = IntentRecognitionSupport.normalizeIntentType(
                                str(intentData.get("intentType"), str(intentData.get("intent_type"))));
                        if (parsedType.isEmpty()) {
                            Map<String, Object> fallback = IntentRecognitionSupport.tryKeywordFallback(
                                    lastUserMessage, scene);
                            if (fallback != null && !str(fallback.get("intentType")).isBlank()) {
                                intentData = fallback;
                                intentSource = IntentRecognitionSupport.SOURCE_FALLBACK;
                                sendEvent(emitter, SseUtils.thinkingRich(
                                        "意图 LLM 无有效结果，已关键词降级...",
                                        Map.of(
                                                "step", 3,
                                                "totalSteps", 4,
                                                "source", intentSource,
                                                "intentType", str(fallback.get("intentType"))
                                        ),
                                        0
                                ));
                            } else {
                                String sceneDefault = IntentRecognitionSupport.resolveDefaultIntentByScene(scene);
                                if (!sceneDefault.isEmpty()) {
                                    intentData = new LinkedHashMap<>();
                                    intentData.put("intentType", sceneDefault);
                                    intentData.put("action", IntentRecognitionSupport.defaultActionForScene(
                                            scene, sceneDefault));
                                    intentData.put("confidence", 0.5);
                                    intentData.put("source", IntentRecognitionSupport.SOURCE_SCENE_DEFAULT);
                                    intentSource = IntentRecognitionSupport.SOURCE_SCENE_DEFAULT;
                                }
                            }
                        }
                    }
                }
                long step3Elapsed = System.currentTimeMillis() - step3Start;

                com.sitech.prodai.intent.StreamStats intentStats = new com.sitech.prodai.intent.StreamStats();
                intentStats.recordInputTokens(intentPrompt);
                intentStats.recordOutputText(intentResult);

                intentType = IntentRecognitionSupport.normalizeIntentType(
                        str(intentData.get("intentType"), str(intentData.get("intent_type"))));
                // meta 再次兜底：防止 LLM/fallback 误判为业务意图
                if (IntentRecognitionSupport.isMetaGuideRequest(lastUserMessage)) {
                    intentType = "chat";
                    intentData.put("intentType", "chat");
                    intentData.put("action", "guide");
                    intentSource = IntentRecognitionSupport.SOURCE_META;
                }
                if (intentType.isEmpty()) {
                    intentType = "chat";
                }

                double confidence = toDouble(intentData.get("confidence"));
                if (confidence <= 0) {
                    confidence = IntentRecognitionSupport.SOURCE_LLM.equals(intentSource) ? 0.0 : 0.5;
                }
                String action = str(intentData.get("action"));
                log.info("[chat/agent/stream] 意图识别结果: type={}, action={}, confidence={}, scene={}, source={}",
                        intentType, action, confidence, scene, intentSource);

                String intentLabel = IntentRecognitionSupport.resolveIntentLabel(intentType, action);

                Map<String, Object> step3Meta = new LinkedHashMap<>();
                step3Meta.put("step", 3);
                step3Meta.put("totalSteps", 4);
                step3Meta.put("intentType", intentType);
                step3Meta.put("intentLabel", intentLabel);
                step3Meta.put("confidence", Math.round(confidence * 100) / 100.0);
                step3Meta.put("source", intentSource);
                step3Meta.put("elapsed", Math.round(step3Elapsed / 1000.0 * 1000.0) / 1000.0);
                if (!skippedLlm) {
                    step3Meta.put("inputTokens", intentStats.getInputTokens());
                    step3Meta.put("outputTokens", intentStats.getOutputTokens());
                }
                sendEvent(emitter, SseUtils.thinkingRich(
                        "意图识别完成：" + intentLabel
                                + "（来源 " + intentSource
                                + (confidence > 0
                                ? "，置信度 " + String.format("%.0f", confidence * 100) + "%"
                                : "")
                                + "）",
                        step3Meta,
                        step3Elapsed,
                        intentResult.isEmpty() ? null : truncateForLog(intentResult, 200)
                ));

                // ── 步骤 4：分发到处理器 ────────────────────────
                long step4Start = System.currentTimeMillis();
                sendEvent(emitter, SseUtils.thinkingRich(
                        "正在分发到「" + intentLabel + "」处理器执行...",
                        Map.of(
                                "step", 4,
                                "totalSteps", 4,
                                "handler", intentType,
                                "action", str(intentData.get("action"))
                        ),
                        0));

                IntentContext ctx = new IntentContext();
                ctx.setIntentData(intentData);
                ctx.setIntentResult(intentResult);
                ctx.setIntentType(intentType);
                ctx.setConfidence(confidence);
                ctx.setOntologies(ontologies);
                ctx.setOntologiesInfo(ontologiesInfo.toString());
                ctx.setRequest(request);
                ctx.setLastUserMessage(lastUserMessage);
                ctx.setMessagesText(messagesText.toString());
                ctx.setIntentPrompt(intentPrompt);
                ctx.setUserId(userId);
                ctx.setSessionId(sessionId);
                ctx.setMessages(messages);
                ctx.setAction(str(intentData.get("action")));
                ctx.setFormCode(str(firstNonNull(intentData.get("formCode"), intentData.get("form_code"))));
                ctx.setFormName(str(firstNonNull(intentData.get("formName"), intentData.get("form_name"))));
                Object extracted = firstNonNull(intentData.get("extractedFields"), intentData.get("extracted_fields"));
                if (extracted instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    ctx.setExtractedFields(normalized);
                }

                Flux<Map<String, Object>> eventFlux = intentRegistry.dispatch(intentType, ctx);

                // 收集流式输出，供历史完整还原（正文 + intentData + 思考步骤）
                StringBuilder assistantText = new StringBuilder();
                Map<String, Object> collectedIntentData = new LinkedHashMap<>();
                List<Map<String, Object>> reasoningSteps = new ArrayList<>();
                final String[] collectedIntentType = {intentType};
                final String[] collectedAction = {str(intentData.get("action"))};

                eventFlux.toStream().forEach(event -> {
                    try {
                        sendEvent(emitter, event);
                        collectForPersistence(event, assistantText, collectedIntentData,
                                reasoningSteps, collectedIntentType, collectedAction);
                    } catch (Exception e) {
                        log.error("[chat/agent/stream] SSE 发送失败", e);
                    }
                });

                // 对话持久化：仅有用户内容时落库；助手保存真实回复正文 + metadata
                final String pSessionId = sessionId;
                final String pUserId = userId;
                final String pLastMsg = lastUserMessage == null ? "" : lastUserMessage.trim();
                if (!pLastMsg.isBlank()) {
                    persistenceService.ifPresent(svc -> {
                        try {
                            String title = pLastMsg.length() > 50 ? pLastMsg.substring(0, 50) : pLastMsg;
                            svc.getOrCreateSession(pSessionId, pUserId, title);
                            svc.saveMessage(pSessionId, "user", pLastMsg, "text");

                            String replyText = assistantText.toString().trim();
                            if (replyText.isBlank() && !collectedIntentData.isEmpty()) {
                                replyText = "意图: " + collectedIntentType[0];
                                if (collectedIntentData.containsKey("verdict")) {
                                    replyText += " | 结论: " + collectedIntentData.get("verdict");
                                }
                            }
                            if (!replyText.isBlank()) {
                                Map<String, Object> meta = new LinkedHashMap<>();
                                meta.put("intent_type", collectedIntentType[0]);
                                if (collectedAction[0] != null && !collectedAction[0].isBlank()) {
                                    meta.put("action", collectedAction[0]);
                                }
                                meta.put("stream_text", replyText);
                                meta.put("done", true);
                                meta.put("content_type", "chat");
                                if (!collectedIntentData.isEmpty()) {
                                    meta.put("intent_data", collectedIntentData);
                                }
                                if (!reasoningSteps.isEmpty()) {
                                    meta.put("reasoning_full", reasoningSteps);
                                }
                                svc.saveMessage(pSessionId, "assistant", replyText, "text", meta);
                            }
                        } catch (Exception e) {
                            log.warn("[chat/agent/stream] 对话持久化失败: {}", e.getMessage());
                        }
                    });
                } else {
                    log.debug("[chat/agent/stream] 用户消息为空，跳过历史持久化 sessionId={}", sessionId);
                }

                emitter.complete();
            } catch (Exception e) {
                log.error("[chat/agent/stream] 处理失败", e);
                try {
                    sendEvent(emitter, SseUtils.error("处理失败: " + e.getMessage()));
                    sendEvent(emitter, SseUtils.doneEvent("chat", false));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseIntentResult(String llmResult) {
        if (llmResult == null || llmResult.isEmpty()) {
            return new LinkedHashMap<>();
        }
        int start = llmResult.indexOf('{');
        int end = llmResult.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String json = llmResult.substring(start, end + 1);
            try {
                return objectMapper.readValue(json, Map.class);
            } catch (Exception e) {
                log.warn("[chat/agent/stream] 意图结果 JSON 解析失败: {}", e.getMessage());
            }
        }
        return new LinkedHashMap<>();
    }

    private void sendEvent(SseEmitter emitter, Map<String, Object> event) throws Exception {
        String json = objectMapper.writeValueAsString(event);
        emitter.send(SseEmitter.event().data(json));
    }

    /**
     * 从 SSE 事件中收集历史持久化所需字段。
     */
    @SuppressWarnings("unchecked")
    private void collectForPersistence(Map<String, Object> event,
                                       StringBuilder assistantText,
                                       Map<String, Object> intentData,
                                       List<Map<String, Object>> reasoningSteps,
                                       String[] intentTypeHolder,
                                       String[] actionHolder) {
        if (event == null) return;
        String type = str(event.get("type"));
        switch (type) {
            case "text" -> {
                String chunk = str(event.get("content"));
                if (!chunk.isEmpty()) {
                    assistantText.append(chunk);
                }
            }
            case "thinking" -> {
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("type", "thinking");
                step.put("content", str(event.get("content")));
                if (event.get("metadata") instanceof Map<?, ?> m) {
                    step.put("metadata", new LinkedHashMap<>((Map<String, Object>) m));
                }
                if (event.get("elapsed") != null) step.put("elapsed", event.get("elapsed"));
                if (event.get("details") != null) step.put("details", event.get("details"));
                if (event.get("result") != null) step.put("result", event.get("result"));
                step.put("_index", reasoningSteps.size());
                reasoningSteps.add(step);
            }
            case "intent" -> {
                String it = str(event.get("intentType"));
                if (!it.isBlank()) intentTypeHolder[0] = it;
                String action = str(event.get("action"));
                if (!action.isBlank()) actionHolder[0] = action;
                if (event.get("data") instanceof Map<?, ?> data) {
                    intentData.putAll((Map<String, Object>) data);
                }
            }
            case "done" -> {
                String it = str(event.get("intentType"));
                if (!it.isBlank()) intentTypeHolder[0] = it;
                if (event.get("intentData") instanceof Map<?, ?> data) {
                    Map<String, Object> doneData = (Map<String, Object>) data;
                    intentData.putAll(doneData);
                    if (doneData.get("action") != null) {
                        actionHolder[0] = str(doneData.get("action"));
                    }
                    if (doneData.get("intentType") != null) {
                        intentTypeHolder[0] = str(doneData.get("intentType"));
                    }
                }
            }
            case "product_ops_query", "product_ops_policy", "product_ops_reason",
                 "product_ops_compare", "product_ops_monitor" -> {
                intentTypeHolder[0] = type;
                if (event.get("data") instanceof Map<?, ?> data) {
                    intentData.putAll((Map<String, Object>) data);
                } else {
                    // 部分 handler 直接把字段放在事件根上
                    Map<String, Object> copy = new LinkedHashMap<>(event);
                    copy.remove("type");
                    intentData.putAll(copy);
                }
            }
            default -> {
                // ignore
            }
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String str(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isEmpty() ? defaultValue : String.valueOf(value);
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }

    /** LLM 意图识别期间定时推 thinking，避免长时间无反馈。 */
    private String completePromptWithHeartbeat(SseEmitter emitter, String intentPrompt, long step3Start) {
        if (llmService.isEmpty()) {
            return "";
        }
        java.util.concurrent.Future<String> future = executor.submit(() -> {
            try {
                return llmService.get().completePrompt(intentPrompt);
            } catch (Exception e) {
                log.warn("[chat/agent/stream] LLM 意图识别失败: {}，降级到关键词/chat", e.getMessage());
                return "";
            }
        });
        int tick = 0;
        while (!future.isDone()) {
            try {
                return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException te) {
                tick++;
                long elapsed = System.currentTimeMillis() - step3Start;
                try {
                    sendEvent(emitter, SseUtils.thinkingRich(
                            "意图识别仍在进行（已等待 " + (elapsed / 1000) + "s）...",
                            Map.of(
                                    "step", 3,
                                    "totalSteps", 4,
                                    "phase", "waiting_llm",
                                    "waitSeconds", elapsed / 1000,
                                    "tick", tick
                            ),
                            -1
                    ));
                } catch (Exception sendEx) {
                    future.cancel(true);
                    return "";
                }
            } catch (Exception e) {
                log.warn("[chat/agent/stream] 等待意图识别异常: {}", e.getMessage());
                return "";
            }
        }
        try {
            return future.get();
        } catch (Exception e) {
            return "";
        }
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object v : values) {
            if (v != null) return v;
        }
        return null;
    }

    /** 截断文本用于日志/思考步骤的 details 字段，避免过长 */
    private String truncateForLog(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
