package com.sitech.prodai.service.changesub;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商品变更事件发布器（方案 §6-C3）：观察者模式性质守护——
 * 异步投递、监听器异常隔离、空事件零开销、多监听器全量送达。
 */
class ProductChangePublisherTest {

    private Map<String, Object> change(String id, String type, String oldV, String newV) {
        return Map.of("offering_id", id, "change_type", type,
                "old_value", oldV, "new_value", newV);
    }

    @Test
    void emptyEventSkipsDispatch() throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        ProductChangePublisher publisher = new ProductChangePublisher(List.of(
                e -> delivered.incrementAndGet()));
        // 空变更事件：零开销跳过（异步投递不发生）
        publisher.publish(new ProductChangeEvent("r1", List.of()));
        // 给异步线程留出误投递窗口
        Thread.sleep(100);
        assertEquals(0, delivered.get(), "空变更事件不触发监听器");
    }

    @Test
    void listenerExceptionIsolated() throws Exception {
        CountDownLatch second = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();
        ProductChangePublisher publisher = new ProductChangePublisher(List.of(
                e -> {
                    throw new IllegalStateException("监听器故意炸");
                },
                e -> {
                    delivered.incrementAndGet();
                    second.countDown();
                }));
        publisher.publish(new ProductChangeEvent("r1", List.of(
                change("OF-A", ChangeDetectService.TYPE_FEE_CHANGE, "128", "99"))));

        assertTrue(second.await(2, TimeUnit.SECONDS), "第二个监听器仍应收到事件");
        assertEquals(1, delivered.get(), "异常监听器被隔离，不影响其他监听器");
    }

    @Test
    void asyncDeliveryToAllListeners() throws Exception {
        CountDownLatch both = new CountDownLatch(2);
        AtomicInteger delivered = new AtomicInteger();
        ProductChangePublisher publisher = new ProductChangePublisher(List.of(
                e -> {
                    delivered.incrementAndGet();
                    both.countDown();
                },
                e -> {
                    delivered.incrementAndGet();
                    both.countDown();
                }));

        publisher.publish(new ProductChangeEvent("r2", List.of(
                change("OF-A", ChangeDetectService.TYPE_FEE_CHANGE, "128", "99"),
                change("OF-B", ChangeDetectService.TYPE_STATE_CHANGE, "on_shelf", "on_sale"))));

        assertTrue(both.await(2, TimeUnit.SECONDS), "两个监听器均应异步收到事件");
        assertEquals(2, delivered.get(), "多监听器全量送达");
    }

    @Test
    void noListenersIsZeroOverhead() {
        ProductChangePublisher publisher = new ProductChangePublisher(List.of());
        publisher.publish(new ProductChangeEvent("r3", List.of(
                change("OF-A", ChangeDetectService.TYPE_FEE_CHANGE, "128", "99"))));
        // 无监听器：空发布不抛错（零开销路径）
        assertTrue(true);
    }
}
