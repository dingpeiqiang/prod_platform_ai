package com.sitech.prodai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.domain.entity.Workflow;
import com.sitech.prodai.domain.entity.WorkflowHistory;
import com.sitech.prodai.mapper.WorkflowHistoryMapper;
import com.sitech.prodai.mapper.WorkflowMapper;
import com.sitech.prodai.service.agent.flow.FlowIntentRouter;
import com.sitech.prodai.service.flow.EditorDefinitionNormalizer;
import com.sitech.prodai.service.flow.FlowDefinitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowService 发布守门测试（改造方案 §12.3 铁律三：发布即绿灯）：
 * 非法定义拒绝发布、编辑器形态先归一化再守门、合法定义正常发布。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private WorkflowMapper workflowMapper;
    @Mock
    private WorkflowHistoryMapper workflowHistoryMapper;
    @Mock
    private FlowIntentRouter flowIntentRouter;

    private WorkflowService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowService(workflowMapper, workflowHistoryMapper,
                new FlowDefinitionValidator(), flowIntentRouter);
    }

    @Test
    void publishRejectedWhenDefinitionInvalid() {
        Workflow wf = workflow("wf_1", Map.of("nodes", List.of()));
        when(workflowMapper.selectOne(any())).thenReturn(wf);

        ApiResponse<Map<String, Object>> resp = service.publishWorkflow("wf_1", "alice");

        assertFalse(resp.isSuccess());
        verify(workflowHistoryMapper, never()).insert(any(WorkflowHistory.class));
        verify(workflowMapper, never()).updateById(any(Workflow.class));
    }

    @Test
    void publishRejectsEditorTypeWithoutAction() {
        // 未映射的编辑器节点类型（loop）归一化后无 action → 守门拒绝
        Map<String, Object> definition = Map.of(
                "nodes", List.of(editorNode("s", "start"), editorNode("l", "loop")),
                "edges", List.of());
        Workflow wf = workflow("wf_2", definition);
        when(workflowMapper.selectOne(any())).thenReturn(wf);

        ApiResponse<Map<String, Object>> resp = service.publishWorkflow("wf_2", "alice");

        assertFalse(resp.isSuccess());
        verify(workflowHistoryMapper, never()).insert(any(WorkflowHistory.class));
    }

    @Test
    void publishNormalizesEditorDefinitionThenPasses() {
        // 编辑器原始形态：start → tool → end（含 edges），归一化后应通过守门
        Map<String, Object> toolData = new LinkedHashMap<>();
        toolData.put("label", "查套餐");
        toolData.put("tool_type", "query_product");
        Map<String, Object> definition = Map.of(
                "nodes", List.of(editorNode("s", "start"),
                        Map.of("id", "t", "type", "tool", "data", toolData),
                        editorNode("e", "end")),
                "edges", List.of(Map.of("source", "s", "target", "t"),
                        Map.of("source", "t", "target", "e")));
        Workflow wf = workflow("wf_3", definition);
        when(workflowMapper.selectOne(any())).thenReturn(wf);

        ApiResponse<Map<String, Object>> resp = service.publishWorkflow("wf_3", "alice");

        assertTrue(resp.isSuccess());
        assertEquals(4, wf.getVersion());
        verify(workflowHistoryMapper).insert(any(WorkflowHistory.class));
        verify(workflowMapper).updateById(wf);
    }

    @Test
    void publishAcceptsEngineDefinitionDirectly() {
        // 已是引擎形态（含 action）的定义不归一化，直接守门
        Map<String, Object> definition = engineDefinition();
        Workflow wf = workflow("wf_4", definition);
        when(workflowMapper.selectOne(any())).thenReturn(wf);

        ApiResponse<Map<String, Object>> resp = service.publishWorkflow("wf_4", "alice");

        assertTrue(resp.isSuccess());
        verify(workflowHistoryMapper).insert(any(WorkflowHistory.class));
    }

    @Test
    void publishFailsWhenWorkflowMissing() {
        when(workflowMapper.selectOne(any())).thenReturn(null);

        ApiResponse<Map<String, Object>> resp = service.publishWorkflow("ghost", "alice");

        assertFalse(resp.isSuccess());
        verify(workflowHistoryMapper, never()).insert(any(WorkflowHistory.class));
    }

    @Test
    void historySnapshotBeforeVersionBump() {
        Workflow wf = workflow("wf_5", engineDefinition());
        wf.setVersion(7);
        when(workflowMapper.selectOne(any())).thenReturn(wf);

        service.publishWorkflow("wf_5", "alice");

        ArgumentCaptor<WorkflowHistory> captor = ArgumentCaptor.forClass(WorkflowHistory.class);
        verify(workflowHistoryMapper).insert(captor.capture());
        WorkflowHistory history = captor.getValue();
        assertEquals(7, history.getVersion());
        assertEquals(8, wf.getVersion());
        assertTrue(Boolean.TRUE.equals(wf.getIsActive()));
    }

    @Test
    void publishRegistersTriggerKeywordsToRouter() {
        // S1：tags 里 kw: 前缀 + 定义 trigger_keywords 双来源合并注册
        Workflow wf = workflow("wf_6", Map.of(
                "trigger_keywords", List.of("跑一下演示流程", "demo流程"),
                "nodes", engineDefinition().get("nodes"),
                "connections", engineDefinition().get("connections")));
        wf.setTags(List.of("kw:演示流程", "普通标签"));
        when(workflowMapper.selectOne(any())).thenReturn(wf);

        service.publishWorkflow("wf_6", "alice");

        verify(flowIntentRouter).register(eq("wf_6"), eq("wf_6"),
                eq(List.of("演示流程", "跑一下演示流程", "demo流程")));
    }

    @Test
    void publishWithoutKeywordsSkipsRouterRegistration() {
        // 未配置任何触发词 → 不注册（灰度接管：不配不接管）
        Workflow wf = workflow("wf_7", engineDefinition());
        when(workflowMapper.selectOne(any())).thenReturn(wf);

        service.publishWorkflow("wf_7", "alice");

        verify(flowIntentRouter, never()).register(any(), any(), any());
    }

    @Test
    void unpublishUnregistersRouter() {
        Workflow wf = workflow("wf_8", engineDefinition());
        when(workflowMapper.selectOne(any())).thenReturn(wf);

        service.unpublishWorkflow("wf_8", "alice");

        verify(flowIntentRouter).unregister("wf_8");
    }

    /** 打桩 selectPage：返回单页单条（page 参数与 service 内部构造无关，直接回放记录） */
    private void mockSelectPage(Workflow wf) {
        Page<Workflow> page = new Page<>(1, 20);
        page.setRecords(List.of(wf));
        page.setTotal(1);
        when(workflowMapper.selectPage(any(), any())).thenReturn(page);
    }

    @Test
    void listMapExposesPublishedAndTriggerKeywords() {
        // S3-A 工作流库：toMap 透出 published 语义字段 + 触发词（与注册路由同源提取）
        Workflow wf = workflow("wf_9", Map.of(
                "trigger_keywords", "跑一下演示流程,demo流程",
                "nodes", engineDefinition().get("nodes"),
                "connections", engineDefinition().get("connections")));
        wf.setTags(List.of("kw:演示流程", "普通标签"));
        wf.setIsActive(true);
        mockSelectPage(wf);

        ApiResponse<Map<String, Object>> resp = service.listWorkflows(null, null, null,
                "wf_9", null, null, null, null, null, "desc", 1, 20);

        assertTrue(resp.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.getData().get("data");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals(Boolean.TRUE, row.get("published"));
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) row.get("trigger_keywords");
        assertEquals(List.of("演示流程", "跑一下演示流程", "demo流程"), keywords);
    }

    @Test
    void listMapUnpublishedWorkflowHasNoKeywords() {
        // 未发布 + 无触发词配置 → published=false、trigger_keywords 空列表
        Workflow wf = workflow("wf_10", engineDefinition());
        wf.setIsActive(false);
        mockSelectPage(wf);

        ApiResponse<Map<String, Object>> resp = service.listWorkflows(null, null, null,
                "wf_10", null, null, null, null, null, "desc", 1, 20);

        assertTrue(resp.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.getData().get("data");
        assertEquals(Boolean.FALSE, rows.get(0).get("published"));
        assertTrue(((List<?>) rows.get(0).get("trigger_keywords")).isEmpty());
    }

    private Workflow workflow(String code, Map<String, Object> definition) {
        Workflow wf = new Workflow();
        wf.setId(1);
        wf.setWorkflowCode(code);
        wf.setWorkflowName(code);
        wf.setVersion(3);
        wf.setIsActive(false);
        wf.setWorkflowData(definition);
        return wf;
    }

    private Map<String, Object> editorNode(String id, String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("data", new LinkedHashMap<>());
        return node;
    }

    private Map<String, Object> engineDefinition() {
        return Map.of(
                "nodes", List.of(
                        Map.of("id", "s", "action", "flow.start", "action_params", Map.of()),
                        Map.of("id", "e", "action", "flow.end", "action_params", Map.of())),
                "connections", List.of(Map.of("source", "s", "target", "e")));
    }
}
