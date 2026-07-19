package com.sitech.prodai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentHandlerRegistry;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.StreamStats;
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

/**
 * 意图驱动的聊天流式 API —— 对齐 Python {@code app/api/chat.py::chat_stream}。
 *
 * <p>处理流程：
 * <ol>
 *   <li>接收 ChatRequest（messages + modelConfig）</li>
 *   <li>提取最后一条用户消息 + 构建消息文本</li>
 *   <li>调用 LLM 进行意图识别</li>
 *   <li>解析意图结果（intentType / formCode / extractedFields / fieldRecommendations）</li>
 *   <li>构建 {@link IntentContext}</li>
 *   <li>通过 {@link IntentHandlerRegistry#dispatch} 分发到对应处理器</li>
 *   <li>将 {@code Flux<Map<String, Object>>} 转换为 SSE 事件流</li>
 * </ol>
 */
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

    /**
     * 意图驱动的流式聊天 —— 对齐 Python POST /api/v1/chat/stream。
     *
     * <p>请求体格式：
     * <pre>{@code
     * {
     *   "messages": [{"role": "user", "content": "用户输入"}],
     *   "modelConfig": {...},
     *   "userId": "user123",
     *   "sessionId": "session456"
     * }
     * }</pre>
     */
    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentStream(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        executor.execute(() -> {
            try {
                // 1. 提取请求参数
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> messages = (List<Map<String, Object>>) request.getOrDefault("messages", List.of());
                String userId = str(request.get("userId"), str(request.get("user_id")));
                String sessionId = str(request.get("sessionId"), str(request.get("session_id")));

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

                // 2. 加载本体
                Map<String, Map<String, Object>> ontologies = configLoader.getAllOntologies();
                StringBuilder ontologiesInfo = new StringBuilder();
                for (Map.Entry<String, Map<String, Object>> e : ontologies.entrySet()) {
                    ontologiesInfo.append("- ").append(e.getKey()).append(": ")
                            .append(str(e.getValue().get("formName"))).append("\n");
                }

                // 3. 发送 thinking 事件
                sendEvent(emitter, SseUtils.thinking("🔍 正在分析用户意图...", Map.of(
                        "messagesCount", messages.size(),
                        "lastUserMessage", lastUserMessage.length() > 100 ? lastUserMessage.substring(0, 100) : lastUserMessage
                )));

                // 4. 调用 LLM 进行意图识别
                String intentPrompt = buildIntentPrompt(messagesText.toString(), lastUserMessage, ontologiesInfo.toString());
                sendEvent(emitter, SseUtils.thinking("🧠 调用 LLM 进行意图识别...", Map.of(
                        "promptLength", intentPrompt.length()
                )));

                String intentResult = "";
                try {
                    intentResult = llmService.map(s -> s.completePrompt(intentPrompt)).orElse("");
                } catch (Exception e) {
                    log.warn("[chat/agent/stream] LLM 意图识别失败: {}，降级到 chat", e.getMessage());
                }

                // 5. 解析意图结果
                Map<String, Object> intentData = parseIntentResult(intentResult);
                String intentType = str(intentData.get("intentType"));
                if (intentType.isEmpty()) {
                    intentType = str(intentData.get("intent_type"));
                }
                if (intentType.isEmpty()) {
                    intentType = "chat";  // 默认降级到聊天
                }

                double confidence = toDouble(intentData.get("confidence"));
                log.info("[chat/agent/stream] 意图识别结果: type={}, confidence={}, formCode={}",
                        intentType, confidence, intentData.get("formCode"));

                // 6. 构建 IntentContext
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

                // 7. 分发到意图处理器
                Flux<Map<String, Object>> eventFlux = intentRegistry.dispatch(intentType, ctx);

                // 8. 流式输出 SSE 事件
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

    /** 构建意图识别 prompt（对齐 Python build_intent_prompt） */
    private String buildIntentPrompt(String messagesText, String lastUserMessage, String ontologiesInfo) {
        return """
                你是一个意图识别助手。请根据用户输入判断意图类型。

                可用意图类型：
                - chat: 纯聊天/问答
                - form: 生成表单（用户想填写或创建表单）
                - form_update: 更新表单字段
                - validate: 校验表单数据
                - delete_form: 删除表单
                - configure: 配置新业务（创建新表单类型）
                - manage_history: 管理历史数据（分析/导入/查询/导出）

                可用业务场景：
                """ + ontologiesInfo + """

                用户最后一条消息：""" + lastUserMessage + """

                对话历史：
                """ + messagesText + """

                请输出 JSON 格式的意图识别结果：
                {
                  "intentType": "意图类型",
                  "formCode": "表单编码（如果涉及表单）",
                  "formName": "表单名称",
                  "confidence": 0.0-1.0,
                  "extractedFields": {"fieldCode": "value"},
                  "action": "子操作（如 analyze/import/query/export）"
                }

                仅输出 JSON，不要其他内容。""";
    }

    /** 解析 LLM 返回的意图识别结果 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseIntentResult(String llmResult) {
        if (llmResult == null || llmResult.isEmpty()) {
            return new LinkedHashMap<>();
        }
        // 尝试从文本中提取 JSON
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

    /** 发送 SSE 事件 */
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
}
