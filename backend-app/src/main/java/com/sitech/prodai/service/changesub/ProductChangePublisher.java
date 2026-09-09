package com.sitech.prodai.service.changesub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 商品变更事件发布器（方案 §6-C3）：观察者模式（复刻 FlowEventPublisher 先例）。
 * <p>
 * 职责：将变更检测产生的事件异步投递给全部监听器（落提醒行的持久化监听者、
 * 生产可扩展站内信/短信通道监听者）。
 * <ul>
 *   <li>异步：daemon 单线程顺序投递，不阻塞图谱重载主线程；</li>
 *   <li>隔离：任一监听器异常仅 log.warn，不影响其他监听器与检测流程；</li>
 *   <li>零开销：无监听器或空变更事件直接返回（空发布）。</li>
 * </ul>
 */
public class ProductChangePublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductChangePublisher.class);

    private final List<ProductChangeListener> listeners;
    private final ExecutorService dispatcher;

    public ProductChangePublisher(List<ProductChangeListener> listeners) {
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.dispatcher = this.listeners.isEmpty()
                ? null
                : Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "product-change-dispatcher");
                    t.setDaemon(true);
                    return t;
                });
    }

    /** 变更检测内唯一发布入口：无监听器/空事件直接返回；有则异步顺序投递。 */
    public void publish(ProductChangeEvent event) {
        if (dispatcher == null || event == null || !event.hasChanges()) {
            return;
        }
        dispatcher.execute(() -> {
            for (ProductChangeListener listener : listeners) {
                try {
                    listener.onProductChange(event);
                } catch (Exception e) {
                    log.warn("[变更订阅] 变更监听器异常（不影响检测流程）: version={} error={}",
                            event.getVersion(), e.getMessage());
                }
            }
        });
    }
}
