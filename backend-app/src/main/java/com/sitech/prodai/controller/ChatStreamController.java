package com.sitech.prodai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentHandlerRegistry;
import com.sitech.prodai.intent.SseUtils;
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
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatStreamController(IntentHandlerRegistry intentRegistry,
                                Optional<LlmService> llmService,
                                ConfigLoader configLoader,
                                ObjectMapper objectMapper) {
        this.intentRegistry = intentRegistry;
        this.llmService = llmService;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentStream(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        executor.execute(() -> {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> messages = (List<Map<String, Object>>) request.getOrDefault("messages", List.of());
                String userId = str(request.get("userId"), str(request.get("user_id")));
                String sessionId = str(request.get("sessionId"), str(request.get("session_id")));
                String scene = str(request.get("scene"));

                String lastUserMessage = "";
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Map<String, Object> msg = messages.get(i);
                    if ("user".equals(str(msg.get("role")))) {
                        lastUserMessage = str(msg.get("content"));
                        break;
                    }
                }

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

                sendEvent(emitter, SseUtils.thinking("... 正在分析用户意图...", Map.of(
                        "messagesCount", messages.size(),
                        "scene", scene,
                        "lastUserMessage", lastUserMessage.length() > 100 ? lastUserMessage.substring(0, 100) : lastUserMessage
                )));

                String intentPrompt = buildIntentPrompt(messagesText.toString(), lastUserMessage, ontologiesInfo.toString(), scene);
                sendEvent(emitter, SseUtils.thinking("... 调用 LLM 进行意图识别...", Map.of(
                        "promptLength", intentPrompt.length(),
                        "scene", scene
                )));

                String intentResult = "";
                try {
                    intentResult = llmService.map(s -> s.completePrompt(intentPrompt)).orElse("");
                } catch (Exception e) {
                    log.warn("[chat/agent/stream] LLM 意图识别失败: {}，降级到 chat", e.getMessage());
                }

                Map<String, Object> intentData = parseIntentResult(intentResult);
                String intentType = normalizeIntentType(str(intentData.get("intentType"), str(intentData.get("intent_type"))));
                if (intentType.isEmpty()) {
                    intentType = resolveDefaultIntentByScene(scene);
                }
                if (intentType.isEmpty()) {
                    intentType = "chat";
                }

                double confidence = toDouble(intentData.get("confidence"));
                log.info("[chat/agent/stream] 意图识别结果: type={}, confidence={}, scene={}", intentType, confidence, scene);

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
                eventFlux.toStream().forEach(event -> {
                    try {
                        sendEvent(emitter, event);
                    } catch (Exception e) {
                        log.error("[chat/agent/stream] SSE 发送失败", e);
                    }
                });

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

    private String buildIntentPrompt(String messagesText, String lastUserMessage, String ontologiesInfo, String scene) {
        String sceneHint = scene == null || scene.isBlank() ? "" : "（当前前端场景：" + scene + "）";
        String historyBlock = "";
        if (!messagesText.isBlank()) {
            historyBlock = "\n\n对话上下文（按时间顺序，最新在最后）：\n" + messagesText;
        }
        return "你是一个 AI 原生意图识别助手。请根据用户输入判断意图类型，并优先识别产商品运营场景" + sceneHint + "。\n\n"
                + "可用意图类型：\n"
                + "- chat: 纯聊天/问答\n"
                + "- form: 生成表单\n"
                + "- product_ops_query: 产商品市场洞察、在售查询、竞品对比、指标查询\n"
                + "- product_ops_policy: 产商品立项研判、规则评估、风险稽核\n"
                + "- product_ops_reason: 产商品异动归因、证据链解释、审计追溯\n"
                + historyBlock + "\n\n"
                + "用户最新消息：" + lastUserMessage + "\n\n"
                + "请输出 JSON 格式的意图识别结果：\n"
                + "{\"intentType\": \"意图类型\", \"action\": \"子操作\", \"confidence\": 0.0-1.0, \"extractedFields\": {}}\n"
                + "仅输出 JSON，不要其他内容。";
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

    private String normalizeIntentType(String intentType) {
        if (intentType == null || intentType.isBlank()) return "";
        String normalized = intentType.trim().toLowerCase();
        return switch (normalized) {
            case "query", "nl_query", "product_ops_query", "market_insight" -> "product_ops_query";
            case "policy", "evaluate", "product_ops_policy", "risk_audit", "online_check" -> "product_ops_policy";
            case "reason", "explain", "product_ops_reason", "root_cause" -> "product_ops_reason";
            default -> normalized;
        };
    }

    private String resolveDefaultIntentByScene(String scene) {
        if (scene == null || scene.isBlank()) return "";
        String normalized = scene.trim().toLowerCase();
        return switch (normalized) {
            case "query", "market", "market_insight", "rd" -> "product_ops_query";
            case "online", "policy", "risk", "audit", "ops" -> "product_ops_policy";
            case "reason", "root_cause", "explain" -> "product_ops_reason";
            default -> "";
        };
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
