package com.sitech.prodai.controller;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.WorkflowService;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return workflowService.getCategories();
    }

    @GetMapping("")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String workflowCode,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) Integer minExecutionCount,
            @RequestParam(required = false) Integer maxExecutionCount,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return workflowService.listWorkflows(category, isActive, keyword, workflowCode, tags,
                createdBy, minExecutionCount, maxExecutionCount, sortBy, sortOrder, page, pageSize);
    }

    @GetMapping("/{workflowCode}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String workflowCode) {
        return workflowService.getWorkflow(workflowCode);
    }

    @PostMapping("")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return workflowService.createWorkflow(body, null);
    }

    @PutMapping("/{workflowCode}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String workflowCode, @RequestBody Map<String, Object> body) {
        return workflowService.updateWorkflow(workflowCode, body, null);
    }

    @DeleteMapping("/{workflowCode}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String workflowCode) {
        return workflowService.deleteWorkflow(workflowCode);
    }

    @PostMapping("/{workflowCode}/toggle")
    public ApiResponse<Map<String, Object>> toggle(@PathVariable String workflowCode) {
        return workflowService.toggleWorkflow(workflowCode);
    }

    @GetMapping("/{workflowCode}/history")
    public ApiResponse<Map<String, Object>> history(@PathVariable String workflowCode) {
        return workflowService.getWorkflowHistory(workflowCode);
    }

    @PostMapping("/{workflowCode}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String workflowCode) {
        return workflowService.publishWorkflow(workflowCode, null);
    }

    @PostMapping("/{workflowCode}/unpublish")
    public ApiResponse<Map<String, Object>> unpublish(@PathVariable String workflowCode) {
        return workflowService.unpublishWorkflow(workflowCode, null);
    }

    @PostMapping("/batch-publish")
    public ApiResponse<Map<String, Object>> batchPublish(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> workflowCodes = (List<String>) body.get("workflowCodes");
        return workflowService.batchPublish(workflowCodes, null);
    }

    @PostMapping("/{workflowCode}/rollback")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable String workflowCode, @RequestBody Map<String, Object> body) {
        Integer targetVersion = (Integer) body.get("targetVersion");
        return workflowService.rollbackVersion(workflowCode, targetVersion, null);
    }

    @GetMapping("/{workflowCode}/compare")
    public ApiResponse<Map<String, Object>> compare(@PathVariable String workflowCode,
                                                     @RequestParam Integer version1,
                                                     @RequestParam Integer version2) {
        return workflowService.compareVersions(workflowCode, version1, version2);
    }

    @PostMapping("/{workflowCode}/copy")
    public ApiResponse<Map<String, Object>> copy(@PathVariable String workflowCode, @RequestBody Map<String, Object> body) {
        String newWorkflowCode = String.valueOf(body.get("newWorkflowCode"));
        return workflowService.copyWorkflow(workflowCode, newWorkflowCode, null);
    }
}