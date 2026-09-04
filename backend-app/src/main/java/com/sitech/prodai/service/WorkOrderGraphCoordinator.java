package com.sitech.prodai.service;

/**
 * 工单 → 事实图回写协调接口（R2 Phase1 拆分）。
 * <p>工单域与内存事实图（graphCache）强耦合：开单/状态流转需回写货架 dispositionStatus 与图内工单列表。
 * 通过该接口由 {@link ProductOntologyService} 提供图写入实现，保持同步锁与缓存所有权不外泄。
 */
public interface WorkOrderGraphCoordinator {

    /** 开单回写：货架 dispositionStatus=work_order_open + lastWorkOrderId，工单置顶入图。 */
    void onWorkOrderCreated(String offeringId, String workOrderId, java.util.Map<String, Object> workOrder);

    /** 状态流转回写：货架 dispositionStatus 按 status 映射，图内工单更新或置顶插入。 */
    void syncWorkOrderToGraph(java.util.Map<String, Object> workOrder, String status);

    /** 货架商品名称反查（开单时兜底展示名）。 */
    java.util.Map<String, Object> findShelfOffering(String offeringId);

    /** 演示模式与数据来源标注（demoMode/dataSource/dataSourceMode）。 */
    java.util.Map<String, Object> modeMeta();
}
