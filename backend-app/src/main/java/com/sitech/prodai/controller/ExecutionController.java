package com.sitech.prodai.controller;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.WorkflowExecutionService;
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

@RestController
@RequestMapping("/api/execution")
public class ExecutionController {

    private final WorkflowExecutionService executionService;

    public ExecutionController(WorkflowExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/{executionId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String executionId) {
        return executionService.getExecution(executionId);
    }

    @GetMapping("")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String workflowCode,
            @RequestParam(required = false) Integer limit) {
        return executionService.listExecutions(workflowCode, limit);
    }

    @GetMapping("/status/{status}")
    public ApiResponse<Map<String, Object>> listByStatus(@PathVariable String status) {
        return executionService.listExecutionsByStatus(status);
    }

    @PostMapping("/{executionId}/resume")
    public ApiResponse<Map<String, Object>> resume(@PathVariable String executionId) {
        return executionService.resumeExecution(executionId, null);
    }

    @PostMapping("/{executionId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String executionId) {
        return executionService.cancelExecution(executionId);
    }

    @PutMapping("/{executionId}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable String executionId, @RequestBody Map<String, Object> body) {
        return executionService.updateExecutionStatus(executionId, body);
    }

    @PostMapping("/{executionId}/logs")
    public ApiResponse<Map<String, Object>> addLogs(@PathVariable String executionId, @RequestBody Map<String, Object> body) {
        Map<String, Object> statusData = Map.of("executionLogs", body.get("logs"));
        return executionService.updateExecutionStatus(executionId, statusData);
    }

    @PostMapping("/execute")
    public ApiResponse<List<Map<String, Object>>> execute(@RequestBody Map<String, Object> body) {
        String workflowCode = String.valueOf(body.get("workflowCode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputData = (Map<String, Object>) body.get("inputData");
        return executionService.executeWorkflow(workflowCode, inputData, null);
    }
}