package com.sitech.prodai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentHandlerRegistry;
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

                // ── 步骤 3：意图识别（关键词快路径 / LLM + 心跳） ──
                long step3Start = System.currentTimeMillis();
                StreamStats streamStats = new StreamStats();
                streamStats.recordInputTokens(intentPrompt);

                Map<String, Object> fastIntent = tryFastIntent(lastUserMessage, scene);
                String intentResult = "";
                boolean usedFastPath = false;

                if (fastIntent != null && !str(fastIntent.get("intentType")).isBlank()) {
                    usedFastPath = true;
                    intentData = new LinkedHashMap<>(fastIntent);
                    intentResult = "";
                    sendEvent(emitter, SseUtils.thinkingRich(
                            "已按关键词快速识别意图，跳过整轮 LLM 等待...",
                            Map.of(
                                    "step", 3,
                                    "totalSteps", 4,
                                    "fastPath", true,
                                    "intentType", str(fastIntent.get("intentType"))
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
                }
                long step3Elapsed = System.currentTimeMillis() - step3Start;

                com.sitech.prodai.intent.StreamStats intentStats = new com.sitech.prodai.intent.StreamStats();
                intentStats.recordInputTokens(intentPrompt);
                intentStats.recordOutputText(intentResult);

                intentType = normalizeIntentType(str(intentData.get("intentType"), str(intentData.get("intent_type"))));
                if (intentType.isEmpty()) {
                    intentType = resolveDefaultIntentByScene(scene);
                }
                if (intentType.isEmpty()) {
                    intentType = "chat";
                }

                double confidence = usedFastPath
                        ? toDouble(intentData.getOrDefault("confidence", 0.92))
                        : toDouble(intentData.get("confidence"));
                if (usedFastPath && confidence <= 0) {
                    confidence = 0.92;
                }
                log.info("[chat/agent/stream] 意图识别结果: type={}, confidence={}, scene={}, fastPath={}",
                        intentType, confidence, scene, usedFastPath);

                String intentLabel = switch (intentType) {
                    case "product_ops_query" -> "数据查询";
                    case "product_ops_policy" -> "政策评估";
                    case "product_ops_reason" -> "原因分析";
                    case "product_ops_compare" -> "对比分析";
                    case "form" -> "表单操作";
                    case "validate" -> "校验";
                    case "configure" -> "配置管理";
                    default -> "通用对话";
                };

                if (!usedFastPath) {
                    sendEvent(emitter, SseUtils.thinkingRich(
                            "意图识别完成：" + intentLabel + "（置信度 " + String.format("%.0f", confidence * 100) + "%）",
                            Map.of(
                                    "step", 3,
                                    "totalSteps", 4,
                                    "intentType", intentType,
                                    "intentLabel", intentLabel,
                                    "confidence", Math.round(confidence * 100) / 100.0,
                                    "inputTokens", intentStats.getInputTokens(),
                                    "outputTokens", intentStats.getOutputTokens(),
                                    "elapsed", Math.round(step3Elapsed / 1000.0 * 1000.0) / 1000.0
                            ),
                            step3Elapsed,
                            intentResult.isEmpty() ? null : truncateForLog(intentResult, 200)
                    ));
                } else {
                    sendEvent(emitter, SseUtils.thinkingRich(
                            "快速意图：" + intentLabel,
                            Map.of(
                                    "step", 3,
                                    "totalSteps", 4,
                                    "intentType", intentType,
                                    "intentLabel", intentLabel,
                                    "confidence", confidence,
                                    "fastPath", true,
                                    "elapsed", Math.round(step3Elapsed / 1000.0 * 1000.0) / 1000.0
                            ),
                            step3Elapsed
                    ));
                }

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
                eventFlux.toStream().forEach(event -> {
                    try {
                        sendEvent(emitter, event);
                    } catch (Exception e) {
                        log.error("[chat/agent/stream] SSE 发送失败", e);
                    }
                });

                // 对话持久化（JPA 可选）
                final String pSessionId = sessionId;
                final String pUserId = userId;
                final String pLastMsg = lastUserMessage;
                final String finalIntentType = intentType;
                final Map<String, Object> finalIntentData = intentData;
                persistenceService.ifPresent(svc -> {
                    try {
                        svc.getOrCreateSession(pSessionId, pUserId,
                                pLastMsg.length() > 50 ? pLastMsg.substring(0, 50) : pLastMsg);
                        svc.saveMessage(pSessionId, "user", pLastMsg, "text");
                        String assistantContent = "意图: " + finalIntentType;
                        if (finalIntentData.containsKey("verdict")) {
                            assistantContent += " | 结论: " + finalIntentData.get("verdict");
                        }
                        svc.saveMessage(pSessionId, "assistant", assistantContent, "json");
                    } catch (Exception e) {
                        log.warn("[chat/agent/stream] 对话持久化失败: {}", e.getMessage());
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
            case "query", "nl_query", "product_ops_query",
                 "market_insight" -> "product_ops_query";
            case "policy", "evaluate", "product_ops_policy",
                 "risk_audit", "online_check", "offering_ops_risk_audit" -> "product_ops_policy";
            case "reason", "explain", "product_ops_reason",
                 "root_cause", "offering_ops_root_cause" -> "product_ops_reason";
            case "compare", "compare_state", "product_ops_compare",
                 "what_if", "hypothesis" -> "product_ops_compare";
            default -> normalized;
        };
    }

    /**
     * 高置信关键词快路径：跳过整轮意图 LLM，显著缩短首包等待。
     */
    private Map<String, Object> tryFastIntent(String text, String scene) {
        if (text == null || text.isBlank()) {
            String byScene = resolveDefaultIntentByScene(scene);
            if (byScene.isEmpty()) return null;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("intentType", byScene);
            data.put("confidence", 0.85);
            data.put("action", byScene.contains("reason") ? "root_cause"
                    : byScene.contains("policy") ? "risk_audit" : "query");
            return data;
        }
        String t = text.trim();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("confidence", 0.93);
        Map<String, Object> fields = new LinkedHashMap<>();

        if (containsAny(t, "根因", "异动", "下滑", "下降", "环比", "归因", "为何下降", "为什么跌", "收入下滑")) {
            data.put("intentType", "product_ops_reason");
            data.put("action", "root_cause");
            fields.put("target", t);
            data.put("extractedFields", fields);
            return data;
        }
        if (containsAny(t, "零元", "0元", "零资费", "风险稽核", "优胜劣汰", "建议下架", "长期零销", "筛查风险")) {
            data.put("intentType", "product_ops_policy");
            data.put("action", "risk_audit");
            fields.put("question", t);
            data.put("extractedFields", fields);
            return data;
        }
        if (containsAny(t, "查一下", "查询", "有哪些", "在售", "列出", "检索", "SPARQL", "图谱里")) {
            data.put("intentType", "product_ops_query");
            data.put("action", "query");
            fields.put("question", t);
            data.put("extractedFields", fields);
            return data;
        }
        // 研发助手配置话术：家庭融合 / 校园 / 月费 / 上一个套餐 → form，跳过意图 LLM
        if (containsAny(t, "家庭融合", "校园", "上一个", "配置一个", "帮我配", "月费", "宽带", "融合套餐", "批量导入", "一文多包")) {
            String byScene = resolveDefaultIntentByScene(scene);
            data.put("intentType", byScene.isEmpty() || "chat".equals(byScene) ? "form" : byScene);
            if ("form".equals(data.get("intentType"))) {
                data.put("formCode", "offering_config");
                data.put("form_code", "offering_config");
            }
            data.put("action", "generate");
            data.put("confidence", 0.9);
            fields.put("question", t);
            data.put("extractedFields", fields);
            return data;
        }

        String byScene = resolveDefaultIntentByScene(scene);
        if (!byScene.isEmpty() && scene != null && (scene.contains("ops") || scene.contains("offering_ops")
                || scene.contains("rd") || scene.contains("offering_config"))) {
            data.put("intentType", byScene);
            data.put("confidence", 0.88);
            data.put("action", byScene.contains("reason") ? "root_cause"
                    : byScene.contains("policy") ? "risk_audit" : "query");
            fields.put(byScene.contains("reason") ? "target" : "question", t);
            data.put("extractedFields", fields);
            return data;
        }
        return null;
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) return true;
        }
        return false;
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
                log.warn("[chat/agent/stream] LLM 意图识别失败: {}，降级到 chat", e.getMessage());
                return "";
            }
        });
        int tick = 0;
        while (!future.isDone()) {
            try {
                return future.get(2, java.util.concurrent.TimeUnit.SECONDS);
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

    private String resolveDefaultIntentByScene(String scene) {
        if (scene == null || scene.isBlank()) return "";
        String normalized = scene.trim().toLowerCase();
        return switch (normalized) {
            // ── 配置类场景 → 表单/配置意图 ───────────────────
            case "rd", "rd_center", "rd_offering_config",
                 "rd_tariff_filing", "rd.chat", "rd.import",
                 "offering_config", "offering_config_chat",
                 "offering_config_batch",
                 "tariff_filing_apply", "tariff_filing_apply_v2" -> "form";
            // ── 分析查询类场景 ────────────────────────────────
            case "query", "market", "market_insight",
                 "offering_ops_center", "offering_ops_analysis",
                 "offering_ops_query",
                 "tariff_center", "tariff_filing" -> "product_ops_query";
            // ── 风险稽核 / 政策评估类场景 ────────────────────
            case "online", "online_check", "policy", "risk", "audit",
                 "risk_audit",
                 "ops", "ops_center", "ops_insight",
                 "offering_ops_risk_audit" -> "product_ops_policy";
            // ── 异动归因类场景 ────────────────────────────────
            case "reason", "root_cause", "explain",
                 "offering_ops_root_cause" -> "product_ops_reason";
            // ── 对比分析类场景 ────────────────────────────────
            case "compare", "compare_state", "what_if", "hypothesis" -> "product_ops_compare";
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

    /** 截断文本用于日志/思考步骤的 details 字段，避免过长 */
    private String truncateForLog(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
