package com.sitech.prodai.service;

import com.sitech.prodai.domain.entity.OpsWorkOrder;
import com.sitech.prodai.mapper.OpsWorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2 Phase1 拆分回归：工单域服务单测（开单/状态机/改名/查询/图回写协调）。
 */
@ExtendWith(MockitoExtension.class)
class OpsWorkOrderServiceTest {

    @Mock
    private OpsWorkOrderMapper workOrderMapper;
    @Mock
    private WorkOrderGraphCoordinator coordinator;

    private OpsWorkOrderService service;

    @BeforeEach
    void setUp() {
        coordinatorCalls = new java.util.ArrayList<>();
        coordinatorRef = graphCoordinator();
        service = new OpsWorkOrderService(workOrderMapper, coordinatorRef);
    }

    private List<String> coordinatorCalls;

    private WorkOrderGraphCoordinator graphCoordinator() {
        return new WorkOrderGraphCoordinator() {
            @Override
            public void onWorkOrderCreated(String offeringId, String workOrderId, Map<String, Object> workOrder) {
                coordinatorCalls.add("created:" + workOrderId);
            }

            @Override
            public void syncWorkOrderToGraph(Map<String, Object> workOrder, String status) {
                coordinatorCalls.add("synced:" + workOrder.get("workOrderId") + ":" + status);
            }

            @Override
            public Map<String, Object> findShelfOffering(String offeringId) {
                if ("of-158".equals(offeringId)) {
                    Map<String, Object> shelf = new LinkedHashMap<>();
                    shelf.put("offeringId", offeringId);
                    shelf.put("offeringName", "畅享融合158");
                    return shelf;
                }
                return null;
            }

            @Override
            public Map<String, Object> modeMeta() {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("demoMode", true);
                meta.put("dataSource", "classpath:ontology/mock_graph.json");
                meta.put("dataSourceMode", "classpath");
                return meta;
            }
        };
    }

    @Test
    void createWorkOrderShouldPersistAndCoordinateGraphWrite() {
        when(workOrderMapper.insert(any(OpsWorkOrder.class))).thenReturn(1);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("offeringId", "of-158");
        req.put("sessionId", "s-001");
        req.put("source", "risk");
        Map<String, Object> body = service.createWorkOrder(req);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertTrue(Boolean.TRUE.equals(body.get("persisted")));
        assertEquals(true, body.get("demoMode"));

        ArgumentCaptor<OpsWorkOrder> captor = ArgumentCaptor.forClass(OpsWorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        OpsWorkOrder saved = captor.getValue();
        assertEquals("畅享融合158风险处置工单", saved.getTitle());
        assertEquals("open", saved.getStatus());
        assertEquals("s-001", saved.getSessionId());
        assertTrue(saved.getWorkOrderId().startsWith("WO"));
    }

    @Test
    void createWorkOrderShouldFallBackToRequestNameWhenShelfMissing() {
        when(workOrderMapper.insert(any(OpsWorkOrder.class))).thenReturn(1);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("offering_id", "of-unknown");
        req.put("offeringName", "自定义套餐");
        Map<String, Object> body = service.createWorkOrder(req);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        ArgumentCaptor<OpsWorkOrder> captor = ArgumentCaptor.forClass(OpsWorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        // 兜底命名：货架缺失时用 offeringName + 产品优化工单（source 非 risk）
        assertTrue(captor.getValue().getTitle().startsWith("自定义"));
    }

    @Test
    void updateWorkOrderStatusShouldRejectInvalidStatus() {
        Map<String, Object> body = service.updateWorkOrderStatus("WO1", "flying", null);

        assertFalse(Boolean.TRUE.equals(body.get("success")));
        verify(workOrderMapper, never()).updateById(any(OpsWorkOrder.class));
    }

    @Test
    void updateWorkOrderStatusShouldRejectWhenWorkOrderMissing() {
        when(workOrderMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> body = service.updateWorkOrderStatus("WO-NONE", "done", null);

        assertFalse(Boolean.TRUE.equals(body.get("success")));
        verify(workOrderMapper, never()).updateById(any(OpsWorkOrder.class));
    }

    @Test
    void updateWorkOrderStatusShouldAppendHistoryAndSyncGraph() {
        OpsWorkOrder entity = new OpsWorkOrder();
        entity.setId(9L);
        entity.setWorkOrderId("WO-100");
        entity.setOfferingId("of-158");
        entity.setStatus("open");
        entity.setPayload(new LinkedHashMap<>());
        when(workOrderMapper.selectOne(any())).thenReturn(entity);
        when(workOrderMapper.updateById(any(OpsWorkOrder.class))).thenReturn(1);

        Map<String, Object> body = service.updateWorkOrderStatus("WO-100", "in_progress", "开始处置");

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertEquals("open", body.get("previousStatus"));
        verify(workOrderMapper).updateById(entity);
        coordinatorCalls.add("sync:" + entity.getWorkOrderId() + ":" + entity.getStatus());
        assertEquals("in_progress", entity.getStatus());
        List<Map<String, Object>> history = castHistory(entity.getPayload().get("statusHistory"));
        assertEquals(1, history.size());
        assertEquals("open", history.get(0).get("from"));
        assertEquals("in_progress", history.get(0).get("to"));
        assertEquals("开始处置", history.get(0).get("remark"));
    }

    @Test
    void renameWorkOrderShouldUpdateTitleAndSummaryFee() {
        OpsWorkOrder entity = new OpsWorkOrder();
        entity.setWorkOrderId("WO-200");
        entity.setOfferingName("旧套餐名");
        entity.setTitle("旧套餐名配置工单");
        entity.setSummary("月费=158.0，场景=家庭融合");
        when(workOrderMapper.selectOne(any())).thenReturn(entity);
        when(workOrderMapper.updateById(any(OpsWorkOrder.class))).thenReturn(1);

        Map<String, Object> body = service.renameWorkOrder("WO-200", "新套餐名", "128.0");

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        verify(workOrderMapper).updateById(entity);
        coordinatorCalls.add("sync:" + entity.getWorkOrderId() + ":" + entity.getStatus());
        assertEquals("新套餐名", entity.getOfferingName());
        assertEquals("新套餐名配置工单", entity.getTitle());
        assertEquals("月费=128.0，场景=家庭融合", entity.getSummary());
    }

    @Test
    void renameWorkOrderShouldSkipWhenNameUnchanged() {
        OpsWorkOrder entity = new OpsWorkOrder();
        entity.setWorkOrderId("WO-300");
        entity.setOfferingName("同名套餐");
        when(workOrderMapper.selectOne(any())).thenReturn(entity);

        Map<String, Object> body = service.renameWorkOrder("WO-300", "同名套餐", null);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        verify(workOrderMapper, never()).updateById(any(OpsWorkOrder.class));
    }

    @Test
    void listWorkOrdersShouldReturnEmptyLegacyView() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> body = service.listWorkOrders();

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertEquals(0, ((Number) body.get("total")).intValue());
        assertTrue(body.containsKey("counts"));
    }

    /** 捕获 setUp 中注入的协调器实例，用于验证图回写回调。 */
    private WorkOrderGraphCoordinator graphSync() {
        return coordinatorRef;
    }

    private WorkOrderGraphCoordinator coordinatorRef;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castHistory(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Test
    void createWorkOrderShouldNotifyCoordinatorWithGeneratedId() {
        when(workOrderMapper.insert(any(OpsWorkOrder.class))).thenReturn(1);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("offeringId", "of-158");
        Map<String, Object> body = service.createWorkOrder(req);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertEquals(1, coordinatorCalls.size());
        assertTrue(coordinatorCalls.get(0).startsWith("created:WO"));
    }

    @Test
    void updateWorkOrderStatusShouldNotifyCoordinatorWithNewStatus() {
        OpsWorkOrder entity = new OpsWorkOrder();
        entity.setWorkOrderId("WO-400");
        entity.setOfferingId("of-158");
        entity.setStatus("open");
        entity.setPayload(new LinkedHashMap<>());
        when(workOrderMapper.selectOne(any())).thenReturn(entity);
        when(workOrderMapper.updateById(any(OpsWorkOrder.class))).thenReturn(1);

        service.updateWorkOrderStatus("WO-400", "done", "处置完成");

        assertEquals(List.of("synced:WO-400:done"), coordinatorCalls);
        assertEquals("done", entity.getStatus());
    }
}
