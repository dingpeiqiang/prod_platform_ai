package com.sitech.prodai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentHandlerRegistry;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.ThinkingStepBuilder;
import com.sitech.prodai.service.ChatPersistenceService;
import com.sitech.prodai.service.IntentPromptManager;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.Understander;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
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
    private final Optional<Understander> understander;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatStreamController(IntentHandlerRegistry intentRegistry,
                                Optional<LlmService> llmService,
                                ConfigLoader configLoader,
                                ObjectMapper objectMapper,
                                IntentPromptManager intentPromptManager,
                                Optional<ChatPersistenceService> persistenceService,
                                Optional<Understander> understander) {
        this.intentRegistry = intentRegistry;
        this.llmService = llmService;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
        this.intentPromptManager = intentPromptManager;
        this.persistenceService = persistenceService;
        this.understander = understander;
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

                // ── 确认业务意图（唯一共享前缀步骤）────────────────
                String intentPrompt = intentPromptManager.renderIntentPrompt(
                        messagesText.toString(), lastUserMessage, ontologiesInfo.toString(), scene);

                long intentStart = System.currentTimeMillis();
                StreamStats streamStats = new StreamStats();
                streamStats.recordInputTokens(intentPrompt);

                String intentResult = "";
                String intentSource = IntentRecognitionSupport.SOURCE_LLM;

                if (IntentRecognitionSupport.isMetaGuideRequest(lastUserMessage)) {
                    intentData = IntentRecognitionSupport.chatMetaResult();
                    intentSource = IntentRecognitionSupport.SOURCE_META;
                    sendEvent(emitter, ThinkingStepBuilder.running(
                            "intent", "确认业务意图", "识别为使用说明类问题，改为一般对话...",
                            1, 1, Map.of("source", intentSource, "intentType", "chat")));
                } else if (lastUserMessage == null || lastUserMessage.isBlank()) {
                    Map<String, Object> byScene = IntentRecognitionSupport.resolveBlankInputByScene(scene);
                    if (byScene != null) {
                        intentData = byScene;
                        intentSource = IntentRecognitionSupport.SOURCE_SCENE_DEFAULT;
                        sendEvent(emitter, ThinkingStepBuilder.running(
                                "intent", "确认业务意图", "按当前业务场景继续处理...",
                                1, 1, Map.of("source", intentSource, "intentType", str(byScene.get("intentType")))));
                    }
                } else {
                    // 翻译层理解优先：LLM 完整理解（意图 + 实体抽取 + 查询计划生成）
                    QueryPlan plan = tryUnderstand(emitter, sessionId, messages, lastUserMessage);
                    if (plan != null) {
                        intentResult = plan.getIntent();
                        intentData = planToIntentData(plan);
                        intentSource = IntentRecognitionSupport.SOURCE_LLM;
                    } else {
                        // 旧链路兜底：窄白名单 → LLM 意图补全 → 关键词 → 场景默认
                        Map<String, Object> whitelist = IntentRecognitionSupport.tryNarrowWhitelist(lastUserMessage);
                        if (whitelist != null && !str(whitelist.get("intentType")).isBlank()) {
                            intentData = whitelist;
                            intentSource = IntentRecognitionSupport.SOURCE_WHITELIST;
                            sendEvent(emitter, ThinkingStepBuilder.running(
                                    "intent", "确认业务意图", "已识别为常用业务指令...",
                                    1, 1, Map.of("source", intentSource, "intentType", str(whitelist.get("intentType")))));
                        } else {
                            sendEvent(emitter, ThinkingStepBuilder.running(
                                    "intent", "确认业务意图", "正在识别业务意图...",
                                    1, 1, Map.of("promptLength", intentPrompt.length())));
                            intentResult = completePromptWithHeartbeat(emitter, intentPrompt, intentStart);
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
                }
                long intentElapsed = System.currentTimeMillis() - intentStart;

                intentType = IntentRecognitionSupport.normalizeIntentType(
                        str(intentData.get("intentType"), str(intentData.get("intent_type"))));
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
                int sceneSteps = sceneStepCount(intentType, action);
                int totalSteps = 1 + sceneSteps;

                Map<String, Object> intentExtra = new LinkedHashMap<>();
                intentExtra.put("intentType", intentType);
                intentExtra.put("intentLabel", intentLabel);
                intentExtra.put("confidence", Math.round(confidence * 100) / 100.0);
                intentExtra.put("source", intentSource);
                sendEvent(emitter, ThinkingStepBuilder.done(
                        "intent", "确认业务意图", "确认业务意图",
                        intentLabel + (confidence > 0 ? " · 把握度 " + Math.round(confidence * 100) + "%" : ""),
                        1, totalSteps, intentElapsed, null, intentExtra));

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
                    // 先收集再发送：避免客户端断连导致历史正文/思考步骤缺失
                    try {
                        collectForPersistence(event, assistantText, collectedIntentData,
                                reasoningSteps, collectedIntentType, collectedAction);
                    } catch (Exception collectEx) {
                        log.warn("[chat/agent/stream] 持久化收集失败: {}", collectEx.getMessage());
                    }
                    try {
                        sendEvent(emitter, event);
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
                                Object explanation = collectedIntentData.get("explanation");
                                if (explanation == null) {
                                    explanation = collectedIntentData.get("nl_answer");
                                }
                                if (explanation == null) {
                                    explanation = collectedIntentData.get("message");
                                }
                                if (explanation != null && !String.valueOf(explanation).isBlank()) {
                                    replyText = String.valueOf(explanation).trim();
                                } else {
                                    String label = IntentRecognitionSupport.resolveIntentLabel(
                                            collectedIntentType[0], collectedAction[0]);
                                    replyText = "已完成「" + (label.isBlank() ? collectedIntentType[0] : label) + "」处理。";
                                    if (collectedIntentData.containsKey("verdict")) {
                                        replyText += "\n结论：" + collectedIntentData.get("verdict");
                                    }
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

    /**
     * 翻译层理解优先：调用 Understander 完整理解（意图 + 实体抽取 + 查询计划生成）。
     * 成功后推送 query_plan 事件；失败/闲聊时返回 null，交由旧链路兜底。
     */
    private QueryPlan tryUnderstand(SseEmitter emitter, String sessionId,
                                    List<Map<String, Object>> messages, String lastUserMessage) {
        if (understander.isEmpty() || lastUserMessage == null || lastUserMessage.isBlank()) {
            return null;
        }
        try {
            QueryPlan plan = understander.get().understand(
                    lastUserMessage, buildSessionContext(sessionId, messages));
            if (plan == null || "CHAT".equals(plan.getIntent())) {
                return null;
            }
            // 推送查询计划（翻译层"中间语言"），前端 QueryPlanCard 展示
            if (plan.getTools() != null && !plan.getTools().isEmpty()) {
                Map<String, Object> qp = new LinkedHashMap<>();
                qp.put("intent", plan.getIntent());
                qp.put("tools", plan.getTools());
                qp.put("params", plan.getParams());
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("type", "query_plan");
                evt.put("queryPlan", qp);
                sendEvent(emitter, evt);
            }
            return plan;
        } catch (Exception e) {
            log.warn("[chat/agent/stream] 翻译层理解失败，走旧链路: {}", e.getMessage());
            return null;
        }
    }

    /** 构建多轮会话上下文（最近 10 条 user/assistant 消息，供 Understander 使用） */
    private SessionContext buildSessionContext(String sessionId, List<Map<String, Object>> messages) {
        SessionContext ctx = new SessionContext(sessionId);
        if (messages != null) {
            int count = 0;
            for (int i = messages.size() - 1; i >= 0 && count < 10; i--) {
                Map<String, Object> m = messages.get(i);
                String role = str(m.get("role"));
                String content = str(m.get("content"));
                if (("user".equals(role) || "assistant".equals(role)) && !content.isBlank()) {
                    ctx.getHistory().add(0, Map.of("role", role, "content", content));
                    count++;
                }
            }
        }
        return ctx;
    }

    /** 查询计划 → 旧链路 intentData（intentType/action/tools/extractedFields） */
    private Map<String, Object> planToIntentData(QueryPlan plan) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (plan == null) return data;
        Map<String, Object> params = plan.getParams();
        String intentType = IntentRecognitionSupport.normalizeIntentType(
                str(params.get("intent_type"), str(params.get("intentType"))));
        if (intentType.isBlank()) {
            intentType = deriveBizIntent(plan.getIntent(), plan.getTools());
        }
        if (intentType.isEmpty()) return data;
        data.put("intentType", intentType);
        String action = str(params.get("action"));
        if (action.isBlank()) action = deriveBizAction(intentType);
        if (!action.isBlank()) data.put("action", action);
        if (plan.getTools() != null && !plan.getTools().isEmpty()) {
            data.put("tools", plan.getTools());
        }
        Map<String, Object> extracted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String key = e.getKey();
            if ("intent_type".equals(key) || "intentType".equals(key)
                    || "action".equals(key) || e.getValue() == null) {
                continue;
            }
            extracted.put(key, e.getValue());
        }
        if (!extracted.isEmpty()) data.put("extractedFields", extracted);
        return data;
    }

    /** 由计划的意图类型反推业务意图（兼容关键词兜底路径） */
    private String deriveBizIntent(String planIntent, List<String> tools) {
        if (tools == null) tools = List.of();
        return switch (planIntent == null ? "" : planIntent) {
            case "SPARQL_QUERY" -> "product_ops_query";
            case "SWRL_INFER" -> tools.contains("swrl_risk_audit") ? "product_ops_policy" : "product_ops_reason";
            default -> "";
        };
    }

    private String deriveBizAction(String intentType) {
        return switch (intentType) {
            case "product_ops_reason" -> "root_cause";
            case "product_ops_policy" -> "risk_audit";
            case "product_ops_monitor" -> "ops_monitor";
            case "product_ops_compare" -> "compare";
            case "product_ops_query" -> "query";
            default -> "";
        };
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
                Object metaObj = event.get("metadata");
                String id = str(event.get("id"));
                String title = str(event.get("title"));
                String stepType = str(event.get("stepType"));
                if (metaObj instanceof Map<?, ?> m) {
                    if (id.isBlank() && m.get("scheduleId") != null) {
                        id = str(m.get("scheduleId"));
                    }
                }
                if (title.isBlank()) {
                    title = !id.isBlank() ? id : "处理步骤";
                }
                if (stepType.isBlank()) {
                    stepType = "ontology".equals(id) || "reason".equals(id) ? "ontology" : "llm";
                }
                step.put("type", stepType);
                if (!id.isBlank()) step.put("id", id);
                step.put("title", title);
                step.put("content", str(event.get("content")));
                step.put("status", "done");
                if (metaObj instanceof Map<?, ?> m) {
                    step.put("metadata", new LinkedHashMap<>((Map<String, Object>) m));
                }
                if (event.get("elapsed") != null) step.put("elapsed", event.get("elapsed"));
                if (event.get("details") != null) step.put("details", event.get("details"));
                if (event.get("result") != null) {
                    step.put("result", event.get("result"));
                } else {
                    String c = str(event.get("content"));
                    if (c.contains("已确认业务意图：")) {
                        step.put("result", c.substring(c.indexOf('：') + 1).trim());
                        step.put("content", "确认业务意图");
                    }
                }
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
    private String completePromptWithHeartbeat(SseEmitter emitter, String intentPrompt, long intentStart) {
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
                long elapsed = System.currentTimeMillis() - intentStart;
                try {
                    Map<String, Object> extra = new LinkedHashMap<>();
                    extra.put("phase", "waiting_llm");
                    extra.put("waitSeconds", elapsed / 1000);
                    extra.put("tick", tick);
                    sendEvent(emitter, ThinkingStepBuilder.running(
                            "intent", "确认业务意图",
                            "业务意图识别进行中（已等待 " + (elapsed / 1000) + "s）...",
                            1, 1, extra));
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

    /** 各意图 Handler 场景步骤数（不含共享 intent 前缀） */
    private int sceneStepCount(String intentType, String action) {
        return switch (intentType == null ? "" : intentType) {
            case "product_ops_reason" -> 5;
            case "product_ops_query", "product_ops_monitor", "product_ops_compare" -> 4;
            case "product_ops_policy" -> 4;
            case "configure" -> 3;
            case "form" -> 4;
            case "form_update" -> 3;
            case "validate" -> 4;
            case "delete_form" -> 2;
            case "manage_history" -> 2;
            case "chat" -> 2;
            default -> 2;
        };
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
