package com.sitech.prodai.service.flow.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 引擎事件发布器（智聊重设计 W1-1）。
 * <p>
 * 职责：将引擎节点事件异步投递给全部监听器。
 * <ul>
 *   <li>异步：daemon 单线程顺序投递，不阻塞状态机主线程；</li>
 *   <li>隔离：任一监听器异常仅 log.warn，不影响其他监听器与引擎推进；</li>
 *   <li>可选：无监听器时零开销（空发布）。</li>
 * </ul>
 */
public class FlowEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FlowEventPublisher.class);

    private final List<FlowEventListener> listeners;
    private final ExecutorService dispatcher;

    public FlowEventPublisher(List<FlowEventListener> listeners) {
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.dispatcher = this.listeners.isEmpty()
                ? null
                : Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "flow-event-dispatcher");
                    t.setDaemon(true);
                    return t;
                });
    }

    /** 引擎内唯一发布入口：无监听器直接返回；有则异步顺序投递。 */
    public void publish(FlowNodeEvent event) {
        if (dispatcher == null || event == null) {
            return;
        }
        dispatcher.execute(() -> {
            for (FlowEventListener listener : listeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    log.warn("[FlowEngine] 事件监听器异常（不影响执行）: type={} nodeId={} error={}",
                            event.getType(), event.getNodeId(), e.getMessage());
                }
            }
        });
    }
}
