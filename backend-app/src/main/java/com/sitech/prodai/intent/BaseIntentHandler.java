package com.sitech.prodai.intent;

import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 意图处理器抽象接口 —— 对齐 Python {@code app/intent/base.py::BaseIntentHandler}。
 *
 * <p>每个处理器负责一种意图类型，接收 {@link IntentContext} 并产出 SSE 事件流。
 * 事件为 {@code Map<String, Object>}，由 Controller 层序列化为 JSON 并通过 SseEmitter 发送。
 */
public interface BaseIntentHandler {

    /**
     * 获取处理器对应的意图类型（用于注册器查找）。
     *
     * @return 意图类型字符串，如 "form" / "chat" / "validate"
     */
    String getIntentType();

    /**
     * 处理意图并产出 SSE 事件流。
     *
     * <p>对齐 Python {@code async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]}，
     * Java 端使用 {@code Flux<Map<String, Object>>} 替代异步生成器。
     *
     * @param ctx 意图处理器上下文
     * @return SSE 事件流（每个 Map 是一个事件的数据体）
     */
    Flux<Map<String, Object>> handle(IntentContext ctx);

    /**
     * 处理器描述（默认返回 intentType）。
     */
    default String getDescription() {
        return getIntentType();
    }
}
