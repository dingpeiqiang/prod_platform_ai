package com.sitech.prodai.intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 意图处理器注册中心 —— 对齐 Python {@code app/intent/registry.py::IntentHandlerRegistry}。
 *
 * <p>Spring 自动注入所有 {@link BaseIntentHandler} 实现，按 intentType 注册。
 * 未注册的意图类型降级到 ChatHandler。
 */
@Component
public class IntentHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(IntentHandlerRegistry.class);

    private final Map<String, BaseIntentHandler> handlers = new ConcurrentHashMap<>();
    private final Optional<ChatHandlerFallback> fallback;

    public IntentHandlerRegistry(List<BaseIntentHandler> handlerBeans, Optional<ChatHandlerFallback> fallback) {
        this.fallback = fallback;
        for (BaseIntentHandler handler : handlerBeans) {
            String intentType = handler.getIntentType();
            if (intentType == null || intentType.isBlank()) {
                log.warn("[IntentHandlerRegistry] 处理器 {} 未设置 intentType，跳过", handler.getClass().getSimpleName());
                continue;
            }
            handlers.put(intentType, handler);
            log.info("[IntentHandlerRegistry] 注册处理器: {} -> {}", intentType, handler.getClass().getSimpleName());
        }
        log.info("[IntentHandlerRegistry] 初始化完成，共注册 {} 个处理器", handlers.size());
    }

    /**
     * 根据意图类型分发到对应处理器。
     *
     * @param intentType 意图类型字符串
     * @param ctx        意图上下文数据袋
     * @return SSE 事件流
     */
    public Flux<Map<String, Object>> dispatch(String intentType, IntentContext ctx) {
        BaseIntentHandler handler = handlers.get(intentType);
        if (handler != null) {
            log.debug("[IntentHandlerRegistry] 分发意图: {} -> {}", intentType, handler.getClass().getSimpleName());
            return handler.handle(ctx);
        }
        log.warn("[IntentHandlerRegistry] 未注册的意图类型: {}，降级到 ChatHandler", intentType);
        return fallback
                .map(f -> f.handle(ctx))
                .orElseGet(() -> Flux.just(SseUtils.text("LLM service is not enabled")));
    }

    /** 获取指定类型的处理器实例 */
    public BaseIntentHandler getHandler(String intentType) {
        return handlers.get(intentType);
    }

    /** 列出所有已注册的处理器 {type: name} */
    public Map<String, String> listHandlers() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, BaseIntentHandler> e : handlers.entrySet()) {
            result.put(e.getKey(), e.getValue().getClass().getSimpleName());
        }
        return result;
    }

    /** 检查是否已注册指定意图类型 */
    public boolean has(String intentType) {
        return handlers.containsKey(intentType);
    }

    /**
     * ChatHandler 降级接口 —— 当意图类型未注册时使用。
     *
     * <p>由 {@link handlers.ChatHandler} 实现，避免循环依赖。
     */
    public interface ChatHandlerFallback extends BaseIntentHandler {
    }
}
