package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentHandlerRegistry;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 纯聊天意图处理器 —— 对齐 Python {@code app/intent/handlers/chat_handler.py::ChatHandler}。
 *
 * <p>LLM 流式输出回复；同时作为未注册意图类型的降级处理器。
 */
@Component
@ConditionalOnProperty(name = "prodai.llm.enabled", havingValue = "true", matchIfMissing = false)
public class ChatHandler implements IntentHandlerRegistry.ChatHandlerFallback {

    private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);

    private final LlmService llmService;

    public ChatHandler(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getIntentType() {
        return "chat";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        // 如果有错误信息，生成友好的错误回复
        if (ctx.getErrorInfo() != null && !ctx.getErrorInfo().isEmpty()) {
            return Flux.fromIterable(List.of(
                    generateFriendlyErrorResponse(ctx.getErrorInfo()),
                    finalizeStats(ctx, false),
                    SseUtils.doneEvent("chat", false)
            ));
        }

        String prompt = buildChatPrompt(ctx);

        // 前置 thinking 事件
        Flux<Map<String, Object>> prefix = Flux.just(
                SseUtils.thinking("\uD83D\uDCAC 正在生成回复...")
        );

        // LLM 流式输出（text_start → text chunks → text_end）
        Flux<Map<String, Object>> textStream = llmService.streamChatText(prompt)
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

        // 后置 stats + done 事件
        Flux<Map<String, Object>> suffix = Flux.just(
                finalizeStats(ctx, false),
                SseUtils.doneEvent("chat", false)
        );

        return Flux.concat(prefix, textWrapped, suffix);
    }

    /** 构建聊天 prompt（对齐 Python stream_chat_reply 的 prompt 构建） */
    private String buildChatPrompt(IntentContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (ctx.getIntentPrompt() != null && !ctx.getIntentPrompt().isEmpty()) {
            sb.append(ctx.getIntentPrompt());
        } else {
            sb.append("你是一个智能助手，请根据用户输入生成回复。\n\n");
            if (ctx.getOntologiesInfo() != null && !ctx.getOntologiesInfo().isEmpty()) {
                sb.append("可用业务场景：\n").append(ctx.getOntologiesInfo()).append("\n\n");
            }
            sb.append("对话历史：\n").append(ctx.getMessagesText() != null ? ctx.getMessagesText() : "");
        }
        return sb.toString();
    }

    /** 根据错误信息生成友好的用户回复 */
    private Map<String, Object> generateFriendlyErrorResponse(String errorInfo) {
        String friendlyMsg;
        String lowerInfo = errorInfo.toLowerCase();
        if (errorInfo.contains("由于目标计算机积极拒绝") || lowerInfo.contains("connection refused")) {
            friendlyMsg = "\uD83D\uDE14 抱歉，当前服务暂时无法连接，请稍后重试。如果问题持续存在，请联系管理员。";
        } else if (errorInfo.contains("Max retries exceeded")) {
            friendlyMsg = "\uD83D\uDE14 服务请求超时，请稍后再试。";
        } else if (errorInfo.contains("Failed to establish a new connection")) {
            friendlyMsg = "\uD83D\uDE14 无法建立连接，请检查网络或稍后重试。";
        } else {
            friendlyMsg = "\uD83D\uDE14 处理过程中遇到问题：\n" + errorInfo + "\n\n请稍后重试，或联系管理员获取帮助。";
        }
        return SseUtils.message(friendlyMsg);
    }

    /** 最终化统计信息并返回 stats 事件 */
    private Map<String, Object> finalizeStats(IntentContext ctx, boolean isForm) {
        StreamStats stats = ctx.getStreamStats();
        if (stats != null) {
            double elapsed = (System.currentTimeMillis() - ctx.getStartTime()) / 1000.0;
            stats.setTotalElapsed(elapsed);
            stats.setForm(isForm);
            return SseUtils.stats(stats);
        }
        return SseUtils.stats(new StreamStats());
    }
}
