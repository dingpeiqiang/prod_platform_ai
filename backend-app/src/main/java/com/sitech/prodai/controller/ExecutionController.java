package com.sitech.prodai.controller;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.WorkflowExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "执行实例", description = "流程执行实例管理：查询、恢复、取消、状态更新与日志追加")
@RestController
@RequestMapping("/api/execution")
public class ExecutionController {

    private final WorkflowExecutionService executionService;

    public ExecutionController(WorkflowExecutionService executionService) {
        this.executionService = executionService;
    }

    @Operation(summary = "执行实例详情", description = "按 ID 查询执行实例")
    @GetMapping("/{executionId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String executionId) {
        return executionService.getExecution(executionId);
    }

    @Operation(summary = "执行实例列表", description = "分页查询执行实例")
    @GetMapping("")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String workflowCode,
            @RequestParam(required = false) Integer limit) {
        return executionService.listExecutions(workflowCode, limit);
    }

    @Operation(summary = "按状态查询实例", description = "按执行状态筛选实例列表")
    @GetMapping("/status/{status}")
    public ApiResponse<Map<String, Object>> listByStatus(@PathVariable String status) {
        return executionService.listExecutionsByStatus(status);
    }

    @Operation(summary = "恢复执行", description = "人工介入后恢复执行")
    @PostMapping("/{executionId}/resume")
    public ApiResponse<Map<String, Object>> resume(@PathVariable String executionId) {
        return executionService.resumeExecution(executionId, null);
    }

    @Operation(summary = "取消执行", description = "取消指定执行实例")
    @PostMapping("/{executionId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String executionId) {
        return executionService.cancelExecution(executionId);
    }

    @Operation(summary = "更新执行状态", description = "更新执行实例状态")
    @PutMapping("/{executionId}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable String executionId, @RequestBody Map<String, Object> body) {
        return executionService.updateExecutionStatus(executionId, body);
    }

    @Operation(summary = "追加执行日志", description = "向执行实例追加日志记录")
    @PostMapping("/{executionId}/logs")
    public ApiResponse<Map<String, Object>> addLogs(@PathVariable String executionId, @RequestBody Map<String, Object> body) {
        Map<String, Object> statusData = Map.of("executionLogs", body.get("logs"));
        return executionService.updateExecutionStatus(executionId, statusData);
    }

    @Operation(summary = "执行流程", description = "触发流程引擎执行（批量节点）")
    @PostMapping("/execute")
    public ApiResponse<List<Map<String, Object>>> execute(@RequestBody Map<String, Object> body) {
        String workflowCode = String.valueOf(body.get("workflowCode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputData = (Map<String, Object>) body.get("inputData");
        return executionService.executeWorkflow(workflowCode, inputData, null);
    }
}