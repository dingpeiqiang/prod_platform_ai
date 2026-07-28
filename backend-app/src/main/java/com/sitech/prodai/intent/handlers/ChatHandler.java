package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentHandlerRegistry;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.ThinkingStepBuilder;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.intent.tools.FunctionCallingService;
import com.sitech.prodai.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 纯聊天意图处理器 —— 对齐 Python ChatHandler。
 *
 * <p>支持 Function Calling 工具循环；关闭时回退为普通 LLM 流式回复。
 * 同时作为未注册意图类型的降级处理器。
 */
@Component
@ConditionalOnProperty(name = "prodai.llm.enabled", havingValue = "true", matchIfMissing = false)
public class ChatHandler implements IntentHandlerRegistry.ChatHandlerFallback {

    private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);

    private final LlmService llmService;
    private final ProdAiProperties properties;
    private final Optional<FunctionCallingService> functionCallingService;

    public ChatHandler(LlmService llmService,
                       ProdAiProperties properties,
                       Optional<FunctionCallingService> functionCallingService) {
        this.llmService = llmService;
        this.properties = properties;
        this.functionCallingService = functionCallingService;
    }

    @Override
    public String getIntentType() {
        return "chat";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        if (ctx.getErrorInfo() != null && !ctx.getErrorInfo().isEmpty()) {
            return Flux.fromIterable(List.of(
                    generateFriendlyErrorResponse(ctx.getErrorInfo()),
                    finalizeStats(ctx),
                    SseUtils.doneEvent("chat", false)
            ));
        }

        String systemPrompt = buildSystemPrompt(ctx);
        String userMessage = ctx.getLastUserMessage() != null ? ctx.getLastUserMessage() : "";
        boolean useFc = properties.getLlm().isFunctionCallingEnabled()
                && functionCallingService.isPresent()
                && functionCallingService.get() != null;

        Flux<Map<String, Object>> body;
        if (useFc) {
            log.debug("[ChatHandler] 使用 Function Calling 路径");
            body = functionCallingService.get().streamWithTools(systemPrompt, userMessage);
        } else {
            log.debug("[ChatHandler] 使用普通流式路径");
            body = streamPlain(ctx, systemPrompt, userMessage);
        }

        Flux<Map<String, Object>> suffix = Flux.defer(() -> Flux.just(
                finalizeStats(ctx),
                SseUtils.doneEvent("chat", false)
        ));

        return Flux.concat(body, suffix)
                .onErrorResume(error -> {
                    log.error("[ChatHandler] 处理失败", error);
                    return Flux.just(
                            SseUtils.textStart(),
                            SseUtils.text("抱歉，生成回复时遇到问题，请稍后重试。"),
                            SseUtils.textEnd(),
                            finalizeStats(ctx),
                            SseUtils.doneEvent("chat", false)
                    );
                });
    }

    private Flux<Map<String, Object>> streamPlain(IntentContext ctx, String systemPrompt, String userMessage) {
        Flux<Map<String, Object>> prefix = Flux.just(ThinkingStepBuilder.running(
                "reply", "生成回复", "正在生成回复...",
                2, 3, Map.of()));

        List<Map<String, String>> history = toHistory(ctx);
        Flux<Map<String, Object>> textStream = llmService.streamWithMessages(systemPrompt, history, userMessage)
                .subscribeOn(Schedulers.boundedElastic())
                .map(SseUtils::text)
                .onErrorResume(error -> {
                    log.error("[ChatHandler] LLM 流式调用失败", error);
                    return Flux.just(SseUtils.text("抱歉，生成回复时遇到问题，请稍后重试。"));
                });

        Flux<Map<String, Object>> textWrapped = Flux.concat(
                Flux.just(SseUtils.textStart()),
                textStream,
                Flux.just(SseUtils.textEnd())
        );

        return Flux.concat(prefix, textWrapped);
    }

    private String buildSystemPrompt(IntentContext ctx) {
        if (ctx.getIntentPrompt() != null && !ctx.getIntentPrompt().isEmpty()) {
            return ctx.getIntentPrompt();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你是产商品智能助手，请根据用户输入生成有帮助的回复。\n");
        if (ctx.getOntologiesInfo() != null && !ctx.getOntologiesInfo().isEmpty()) {
            sb.append("\n可用业务场景：\n").append(ctx.getOntologiesInfo()).append("\n");
        }
        return sb.toString();
    }

    private List<Map<String, String>> toHistory(IntentContext ctx) {
        List<Map<String, String>> history = new ArrayList<>();
        if (ctx.getMessages() == null) {
            return history;
        }
        List<Map<String, Object>> messages = ctx.getMessages();
        // 排除最后一条 user（由 streamWithMessages 单独传入）
        int end = messages.size();
        if (end > 0) {
            Object lastRole = messages.get(end - 1).get("role");
            if ("user".equalsIgnoreCase(String.valueOf(lastRole))) {
                end--;
            }
        }
        for (int i = 0; i < end; i++) {
            Map<String, Object> msg = messages.get(i);
            String role = String.valueOf(msg.getOrDefault("role", "user"));
            String content = String.valueOf(msg.getOrDefault("content", ""));
            if ("system".equalsIgnoreCase(role)) {
                continue;
            }
            history.add(Map.of("role", role.toLowerCase(), "content", content));
        }
        return history;
    }

    private Map<String, Object> generateFriendlyErrorResponse(String errorInfo) {
        String friendlyMsg;
        String lowerInfo = errorInfo.toLowerCase();
        if (errorInfo.contains("由于目标计算机积极拒绝") || lowerInfo.contains("connection refused")) {
            friendlyMsg = "抱歉，当前服务暂时无法连接，请稍后重试。如果问题持续存在，请联系管理员。";
        } else if (errorInfo.contains("Max retries exceeded")) {
            friendlyMsg = "服务请求超时，请稍后再试。";
        } else if (errorInfo.contains("Failed to establish a new connection")) {
            friendlyMsg = "无法建立连接，请检查网络或稍后重试。";
        } else {
            friendlyMsg = "处理过程中遇到问题：\n" + errorInfo + "\n\n请稍后重试，或联系管理员获取帮助。";
        }
        return SseUtils.message(friendlyMsg);
    }

    private Map<String, Object> finalizeStats(IntentContext ctx) {
        StreamStats stats = ctx.getStreamStats();
        if (stats != null) {
            double elapsed = (System.currentTimeMillis() - ctx.getStartTime()) / 1000.0;
            stats.setTotalElapsed(elapsed);
            stats.setForm(false);
            return SseUtils.stats(stats);
        }
        return SseUtils.stats(new StreamStats());
    }
}
