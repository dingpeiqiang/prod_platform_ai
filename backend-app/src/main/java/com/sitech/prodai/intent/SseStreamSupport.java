package com.sitech.prodai.intent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * SSE 流式体感辅助：先发 thinking，重活放到弹性线程，正文按块吐出。
 */
public final class SseStreamSupport {

    private SseStreamSupport() {
    }

    /**
     * @param prelude     立刻发出的前置事件（如 thinking）
     * @param work        阻塞重活
     * @param afterWork   重活结果 + 耗时毫秒 → 后续事件（intent / text / done）
     */
    public static <T> Flux<Map<String, Object>> deferWork(
            List<Map<String, Object>> prelude,
            Supplier<T> work,
            BiFunction<T, Long, List<Map<String, Object>>> afterWork
    ) {
        Flux<Map<String, Object>> head = prelude == null || prelude.isEmpty()
                ? Flux.empty()
                : Flux.fromIterable(prelude);
        Flux<Map<String, Object>> body = Mono.fromCallable(() -> {
                    long start = System.currentTimeMillis();
                    T result = work.get();
                    long elapsedMs = Math.max(0L, System.currentTimeMillis() - start);
                    return afterWork.apply(result, elapsedMs);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
        return Flux.concat(head, body);
    }

    /** 将长文本切成多段 text 事件，前端可逐段渲染。 */
    public static List<Map<String, Object>> chunkedTextEvents(String text) {
        return chunkedTextEvents(text, 48);
    }

    public static List<Map<String, Object>> chunkedTextEvents(String text, int chunkSize) {
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(SseUtils.textStart());
        if (text == null || text.isEmpty()) {
            events.add(SseUtils.textEnd());
            return events;
        }
        int size = Math.max(16, chunkSize);
        for (int i = 0; i < text.length(); i += size) {
            int end = Math.min(text.length(), i + size);
            events.add(SseUtils.text(text.substring(i, end)));
        }
        events.add(SseUtils.textEnd());
        return events;
    }

    /**
     * 带轻微间隔的分片流（打字机体感）；间隔很短，总延迟可控。
     */
    public static Flux<Map<String, Object>> chunkedTextFlux(String text) {
        List<Map<String, Object>> chunks = chunkedTextEvents(text, 40);
        if (chunks.size() <= 2) {
            return Flux.fromIterable(chunks);
        }
        Map<String, Object> start = chunks.get(0);
        Map<String, Object> end = chunks.get(chunks.size() - 1);
        List<Map<String, Object>> middles = chunks.subList(1, chunks.size() - 1);
        return Flux.concat(
                Flux.just(start),
                Flux.fromIterable(middles).delayElements(Duration.ofMillis(8)),
                Flux.just(end)
        );
    }
}
