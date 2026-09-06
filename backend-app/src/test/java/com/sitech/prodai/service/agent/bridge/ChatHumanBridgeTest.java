package com.sitech.prodai.service.agent.bridge;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatHumanBridge 单元测试（智聊重设计 W2-5）：
 * 挂起翻译（buildBinding）、非挂起拒绝、form_spec → clarify_contracts 翻译、
 * 恢复侧轻量文本意图映射与表单值合并、binding 为 null 的零行为兜底。
 */
@ExtendWith(MockitoExtension.class)
class ChatHumanBridgeTest {

    @Mock
    private FlowEngineService flowEngineService;

    private ChatHumanBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new ChatHumanBridge(flowEngineService);
    }

    /** 挂起执行实例视图（executionToMap + form_spec 注入后的引擎形态）。 */
    private Map<String, Object> suspendedExecution() {
        Map<String, Object> formSpec = new LinkedHashMap<>();
        formSpec.put("form_code", "approval_form");
        formSpec.put("fields", List.of(
                Map.of("field_code", "approved", "field_name", "是否同意", "field_type", "switch", "required", true),
                Map.of("field_code", "comment", "field_name", "审批意见", "field_type", "textarea",
                        "description", "请填写审批意见", "required", false)));

        Map<String, Object> exec = new LinkedHashMap<>();
        exec.put("execution_id", "EX-100");
        exec.put("resume_token", "tok-abc");
        exec.put("status", "waiting_human");
        exec.put("current_node_id", "h1");
        exec.put("workflow_code", "chat_configure");
        exec.put("form_spec", formSpec);
        return exec;
    }

    // ── 挂起翻译 buildBinding ──

    @Test
    void buildBindingTranslatesSuspendedExecutionWithFormSpec() {
        Map<String, Object> binding = bridge.buildBinding(suspendedExecution());

        assertNotNull(binding, "挂起态应产出绑定信息");
        assertEquals("EX-100", binding.get("execution_id"));
        assertEquals("tok-abc", binding.get("resume_token"));
        assertEquals("h1", binding.get("node_id"));
        assertEquals("chat_configure", binding.get("workflow_code"));
        assertEquals("approval_form", binding.get("form_code"));
        assertNotNull(binding.get("form_spec"), "form_spec 应原样保留（done 透传给前端渲染）");
        assertNotNull(binding.get("suspended_at"));
    }

    @Test
    void buildBindingReturnsNullForCompletedExecution() {
        Map<String, Object> exec = suspendedExecution();
        exec.put("status", "completed");

        assertNull(bridge.buildBinding(exec), "非挂起态不应绑定会话");
    }

    @Test
    void buildBindingReturnsNullWhenTokenMissing() {
        Map<String, Object> exec = suspendedExecution();
        exec.remove("resume_token");

        assertNull(bridge.buildBinding(exec), "缺 resume_token 无法恢复，不应绑定");
    }

    @Test
    void buildBindingReturnsNullForNullInput() {
        assertNull(bridge.buildBinding(null));
    }

    // ── form_spec → clarify_contracts 翻译 ──

    @Test
    void toClarifyContractsTranslatesFieldsWithLabelDescriptionOptions() {
        Map<String, Object> formSpec = suspendedExecution().get("form_spec") instanceof Map<?, ?> s
                ? (Map<String, Object>) s : null;

        Map<String, Map<String, Object>> contracts = bridge.toClarifyContracts(formSpec);

        assertEquals(2, contracts.size(), "两个字段应翻译为两条契约");
        Map<String, Object> approved = contracts.get("approved");
        assertNotNull(approved);
        assertEquals("是否同意", approved.get("label"));
        assertTrue((Boolean) approved.get("required"), "required 应正确映射");

        Map<String, Object> comment = contracts.get("comment");
        assertNotNull(comment);
        assertEquals("审批意见", comment.get("label"));
        assertEquals("请填写审批意见", comment.get("description"), "description 应从字段定义提取");
        assertFalse((Boolean) comment.get("required"));
    }

    @Test
    void toClarifyContractsPassesOptionsThrough() {
        Map<String, Object> formSpec = Map.of("form_code", "f", "fields", List.of(
                Map.of("field_code", "risk", "field_name", "风险等级",
                        "options", List.of("HIGH", "MEDIUM"))));

        Map<String, Map<String, Object>> contracts = bridge.toClarifyContracts(formSpec);

        assertEquals(List.of("HIGH", "MEDIUM"), contracts.get("risk").get("options"),
                "options 应透传（前端渲染选择题）");
    }

    @Test
    void toClarifyContractsReturnsEmptyMapForNullSpec() {
        Map<String, Map<String, Object>> contracts = bridge.toClarifyContracts(null);

        assertNotNull(contracts);
        assertTrue(contracts.isEmpty(), "无 form_spec（通用确认门）应返回空契约");
    }

    @Test
    void toClarifyContractsIgnoresFieldsWithoutCode() {
        Map<String, Object> formSpec = Map.of("fields", List.of(
                Map.of("field_name", "缺 code 的字段")));

        assertTrue(bridge.toClarifyContracts(formSpec).isEmpty());
    }

    // ── 恢复侧 resume ──

    @Test
    void resumeReturnsNullForNullBinding() {
        assertNull(bridge.resume(null, "确认", null, "u1"), "binding 为 null 时零行为");

        verify(flowEngineService, never()).resumeFromHuman(any(), any(), any(), any());
    }

    @Test
    void resumeConfirmTextMapsToConfirmedTrue() {
        Map<String, Object> binding = bridge.buildBinding(suspendedExecution());
        when(flowEngineService.resumeFromHuman(eq("EX-100"), eq("tok-abc"), any(), eq("user-a")))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed")));

        ApiResponse<Map<String, Object>> resp = bridge.resume(binding, "确认，同意这个方案", null, "user-a");

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertEquals("completed", resp.getData().get("status"));

        verify(flowEngineService).resumeFromHuman(eq("EX-100"), eq("tok-abc"), any(), eq("user-a"));
    }

    @Test
    void resumeCancelTextMapsToConfirmedFalse() {
        Map<String, Object> binding = bridge.buildBinding(suspendedExecution());
        when(flowEngineService.resumeFromHuman(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed")));

        bridge.resume(binding, "取消吧，不要了", null, "user-a");

        verify(flowEngineService).resumeFromHuman(eq("EX-100"), eq("tok-abc"), any(), eq("user-a"));
    }

    @Test
    void resumeMergesStructuredFormDataOverTextIntent() {
        Map<String, Object> binding = bridge.buildBinding(suspendedExecution());
        when(flowEngineService.resumeFromHuman(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "completed")));

        bridge.resume(binding, "确认", Map.of("comment", "结构化意见"), "user-a");

        verify(flowEngineService).resumeFromHuman(eq("EX-100"), eq("tok-abc"), any(), eq("user-a"));
    }

    @Test
    void resumeWithPlainTextOnlyStillCallsEngine() {
        Map<String, Object> binding = bridge.buildBinding(suspendedExecution());
        when(flowEngineService.resumeFromHuman(any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("status", "waiting_human")));

        ApiResponse<Map<String, Object>> resp = bridge.resume(binding, "改成 49 元", null, "user-a");

        assertNotNull(resp);
        assertEquals("waiting_human", resp.getData().get("status"), "再次挂起（下一道阶段门）应透传");
    }

    @Test
    void resumePropagatesEngineRejection() {
        Map<String, Object> binding = bridge.buildBinding(suspendedExecution());
        when(flowEngineService.resumeFromHuman(any(), any(), any(), any()))
                .thenReturn(ApiResponse.fail("恢复令牌无效或已被使用"));

        ApiResponse<Map<String, Object>> resp = bridge.resume(binding, "确认", null, "user-a");

        assertNotNull(resp);
        assertFalse(resp.isSuccess(), "引擎拒绝应原样透传（编排层负责清空绑定）");
        assertTrue(resp.getMessage().contains("令牌"));
    }
}
