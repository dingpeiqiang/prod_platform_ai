package com.sitech.prodai.service.changesub;

/**
 * 商品变更监听器（方案 §6-C3）：{@link ProductChangePublisher} 的观察者契约。
 * <p>
 * 可靠性约定（对齐 FlowEventListener 先例）：检测可靠性 &gt; 通知可靠性——
 * 实现方内部自行降级，异常由发布器隔离吞掉，不影响图谱重载主流程。
 */
@FunctionalInterface
public interface ProductChangeListener {

    /** 收到一批商品变更（字段级 diff 条目清单，见 {@link ProductChangeEvent}）。 */
    void onProductChange(ProductChangeEvent event);
}
