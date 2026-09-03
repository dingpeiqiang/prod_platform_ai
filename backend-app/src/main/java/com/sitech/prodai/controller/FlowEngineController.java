package com.sitech.prodai.controller;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.agent.flow.FlowIntentRouter;
import com.sitech.prodai.service.agent.flow.FlowRouteRegistrar;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 固定流程引擎执行端点（P2-2）—— 见《固定流程引擎设计文档》§7。
 * <p>
 * 边界（方案 §12.2）：引擎入口只收结构化入参（表单/上游系统传参），不接收自由文本。
 */
@RestController
@RequestMapping("/api/v1/flow-engine")
public class FlowEngineController {

    private final FlowEngineService flowEngineService;
    private final FlowIntentRouter flowIntentRouter;
    private final FlowRouteRegistrar flowRouteRegistrar;

    public FlowEngineController(FlowEngineService flowEngineService,
                                FlowIntentRouter flowIntentRouter,
                                FlowRouteRegistrar flowRouteRegistrar) {
        this.flowEngineService = flowEngineService;
        this.flowIntentRouter = flowIntentRouter;
        this.flowRouteRegistrar = flowRouteRegistrar;
    }

    /** S1 诊断：查看流程意图路由注册表（注册器是否生效、关键词归一化结果）。 */
    @GetMapping("/router-routes")
    public Map<String, Object> routerRoutes() {
        return Map.of("routes", flowIntentRouter.listRoutes());
    }

    /** S1 诊断：手动触发一次注册器（区分"启动时未执行"与"执行了但配置空"）。 */
    @PostMapping("/router-routes/reload")
    public Map<String, Object> reloadRoutes() {
        flowRouteRegistrar.registerFromConfig();
        return Map.of("routes", flowIntentRouter.listRoutes());
    }

    /** 启动执行：{workflow_code, version?, input_data}；未显式给 version 时锁定已发布最新版。 */
    @PostMapping("/executions")
    public ApiResponse<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        String workflowCode = String.valueOf(body.get("workflow_code"));
        Integer version = body.get("version") instanceof Number n ? n.intValue() : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> inputData = body.get("input_data") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String user = body.get("triggered_by") != null ? String.valueOf(body.get("triggered_by")) : null;
        return flowEngineService.startExecution(workflowCode, version, inputData, user);
    }

    /** 失败续跑：从最近落库节点继续，复用启动时锁定的定义版本。 */
    @PostMapping("/executions/{executionId}/resume")
    public ApiResponse<Map<String, Object>> resume(@PathVariable String executionId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String user = body != null && body.get("triggered_by") != null
                ? String.valueOf(body.get("triggered_by")) : null;
        return flowEngineService.resumeExecution(executionId, user);
    }

    /** 人工节点恢复：{resume_token, form_data}；令牌一次有效，表单数据写入节点输出后续推状态机。 */
    @PostMapping("/executions/{executionId}/human-resume")
    public ApiResponse<Map<String, Object>> humanResume(@PathVariable String executionId,
                                                        @RequestBody Map<String, Object> body) {
        String resumeToken = body.get("resume_token") != null ? String.valueOf(body.get("resume_token")) : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = body.get("form_data") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String user = body.get("triggered_by") != null ? String.valueOf(body.get("triggered_by")) : null;
        return flowEngineService.resumeFromHuman(executionId, resumeToken, formData, user);
    }

    /** 执行详情（状态+上下文）：编辑器执行面板轮询数据源。 */
    @GetMapping("/executions/{executionId}")
    public ApiResponse<Map<String, Object>> getExecution(@PathVariable String executionId) {
        return flowEngineService.getExecution(executionId);
    }

    /** 节点执行记录（执行时序）：编辑器逐节点点亮的数据源。 */
    @GetMapping("/executions/{executionId}/node-logs")
    public ApiResponse<Map<String, Object>> getNodeLogs(@PathVariable String executionId) {
        return flowEngineService.getNodeLogs(executionId);
    }

    /** 执行实例列表（P4-2）：?workflow_code=&page=&page_size=，start_time 倒序；执行历史可视化数据源。 */
    @GetMapping("/executions")
    public ApiResponse<Map<String, Object>> listExecutions(
            @RequestParam(required = false) String workflow_code,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        return flowEngineService.listExecutions(workflow_code, page, page_size);
    }

    /** 取消执行（P4-1）：{reason?}；仅 running/waiting_human/pending 可取消，终态拒绝。 */
    @PostMapping("/executions/{executionId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String executionId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
        String user = body != null && body.get("triggered_by") != null
                ? String.valueOf(body.get("triggered_by")) : null;
        return flowEngineService.cancelExecution(executionId, reason, user);
    }
}
