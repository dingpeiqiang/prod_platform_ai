package com.sitech.prodai.controller;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.flow.FlowEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 对话侧执行统一经 LLM 理解层 → flow_execute 工具（AgentTool）→ 引擎，无关键词旁路。
 */
@Tag(name = "流程引擎", description = "固定流程引擎：流程实例执行、人工介入恢复、取消与执行日志查询")
@RestController
@RequestMapping("/api/v1/flow-engine")
public class FlowEngineController {

    private final FlowEngineService flowEngineService;

    public FlowEngineController(FlowEngineService flowEngineService) {
        this.flowEngineService = flowEngineService;
    }

    /** 启动执行：{workflow_code, version?, input_data}；未显式给 version 时锁定已发布最新版。 */
    @Operation(summary = "发起流程执行", description = "body: flow_code/initial_context，创建执行实例并开始运行")
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
    @Operation(summary = "恢复执行", description = "实例暂停后自动恢复执行")
@PostMapping("/executions/{executionId}/resume")
    public ApiResponse<Map<String, Object>> resume(@PathVariable String executionId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String user = body != null && body.get("triggered_by") != null
                ? String.valueOf(body.get("triggered_by")) : null;
        return flowEngineService.resumeExecution(executionId, user);
    }

    /** 人工节点恢复：{resume_token, form_data}；令牌一次有效，表单数据写入节点输出后续推状态机。 */
    @Operation(summary = "人工介入恢复", description = "body: 人工填写的节点输出/确认数据，从挂起节点继续")
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
    @Operation(summary = "执行详情", description = "返回执行实例状态/上下文/当前节点")
@GetMapping("/executions/{executionId}")
    public ApiResponse<Map<String, Object>> getExecution(@PathVariable String executionId) {
        return flowEngineService.getExecution(executionId);
    }

    /** 节点执行记录（执行时序）：编辑器逐节点点亮的数据源。 */
    @Operation(summary = "节点日志", description = "返回该实例各节点的执行日志（入参/出参/耗时/状态）")
@GetMapping("/executions/{executionId}/node-logs")
    public ApiResponse<Map<String, Object>> getNodeLogs(@PathVariable String executionId) {
        return flowEngineService.getNodeLogs(executionId);
    }

    /** 执行实例列表（P4-2）：?workflow_code=&page=&page_size=，start_time 倒序；执行历史可视化数据源。 */
    @Operation(summary = "执行列表", description = "分页返回执行实例（按状态/流程/时间筛选）")
@GetMapping("/executions")
    public ApiResponse<Map<String, Object>> listExecutions(
            @RequestParam(required = false) String workflow_code,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        return flowEngineService.listExecutions(workflow_code, page, page_size);
    }

    /** 取消执行（P4-1）：{reason?}；仅 running/waiting_human/pending 可取消，终态拒绝。 */
    @Operation(summary = "取消执行", description = "终止运行中的执行实例（不可恢复）")
@PostMapping("/executions/{executionId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String executionId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
        String user = body != null && body.get("triggered_by") != null
                ? String.valueOf(body.get("triggered_by")) : null;
        return flowEngineService.cancelExecution(executionId, reason, user);
    }
}
