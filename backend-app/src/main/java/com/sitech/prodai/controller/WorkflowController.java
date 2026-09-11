package com.sitech.prodai.controller;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "工作流管理", description = "LangChain 工作流全生命周期：创建/编辑/删除、启停、发布/下线、版本历史/回滚/对比/复制")
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Operation(summary = "工作流分类列表", description = "返回全部工作流分类及其计数，供筛选器渲染")
    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return workflowService.getCategories();
    }

    @Operation(summary = "工作流列表（分页）", description = "多条件筛选：分类/启停/关键字/标签/创建人/执行次数区间，支持排序与分页")
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

    @Operation(summary = "工作流详情", description = "按 workflowCode 返回工作流完整定义（含节点/连线/执行参数）")
    @GetMapping("/{workflowCode}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String workflowCode) {
        return workflowService.getWorkflow(workflowCode);
    }

    @Operation(summary = "创建工作流", description = "body: workflow_code(唯一)/workflow_name/workflow_data(画布JSON)/category/tags 等")
    @PostMapping("")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return workflowService.createWorkflow(body, null);
    }

    @Operation(summary = "更新工作流", description = "按 workflowCode 更新定义与元信息，自动落版本历史")
    @PutMapping("/{workflowCode}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String workflowCode, @RequestBody Map<String, Object> body) {
        return workflowService.updateWorkflow(workflowCode, body, null);
    }

    @Operation(summary = "删除工作流", description = "按 workflowCode 删除工作流及其版本历史")
    @DeleteMapping("/{workflowCode}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String workflowCode) {
        return workflowService.deleteWorkflow(workflowCode);
    }

    @Operation(summary = "启停工作流", description = "切换 is_active 状态（停用后不可被场景命中执行）")
    @PostMapping("/{workflowCode}/toggle")
    public ApiResponse<Map<String, Object>> toggle(@PathVariable String workflowCode) {
        return workflowService.toggleWorkflow(workflowCode);
    }

    @Operation(summary = "版本历史", description = "返回该工作流的全部历史版本（含变更说明）")
    @GetMapping("/{workflowCode}/history")
    public ApiResponse<Map<String, Object>> history(@PathVariable String workflowCode) {
        return workflowService.getWorkflowHistory(workflowCode);
    }

    @Operation(summary = "发布工作流", description = "将当前版本发布为正式版本（可被场景编排引用）")
    @PostMapping("/{workflowCode}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String workflowCode) {
        return workflowService.publishWorkflow(workflowCode, null);
    }

    @Operation(summary = "下线工作流", description = "将已发布工作流下线，场景不再命中")
    @PostMapping("/{workflowCode}/unpublish")
    public ApiResponse<Map<String, Object>> unpublish(@PathVariable String workflowCode) {
        return workflowService.unpublishWorkflow(workflowCode, null);
    }

    @Operation(summary = "批量发布", description = "body: workflowCodes 数组，批量发布多个工作流")
    @PostMapping("/batch-publish")
    public ApiResponse<Map<String, Object>> batchPublish(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> workflowCodes = (List<String>) body.get("workflowCodes");
        return workflowService.batchPublish(workflowCodes, null);
    }

    @Operation(summary = "版本回滚", description = "body: targetVersion 目标版本号，回滚后自动生成新版本记录")
    @PostMapping("/{workflowCode}/rollback")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable String workflowCode, @RequestBody Map<String, Object> body) {
        Integer targetVersion = (Integer) body.get("targetVersion");
        return workflowService.rollbackVersion(workflowCode, targetVersion, null);
    }

    @Operation(summary = "版本对比", description = "query: version1/version2，返回两版定义的结构化差异")
    @GetMapping("/{workflowCode}/compare")
    public ApiResponse<Map<String, Object>> compare(@PathVariable String workflowCode,
                                                     @RequestParam Integer version1,
                                                     @RequestParam Integer version2) {
        return workflowService.compareVersions(workflowCode, version1, version2);
    }

    @Operation(summary = "复制工作流", description = "body: newWorkflowCode 新编码，深拷贝定义为新工作流")
    @PostMapping("/{workflowCode}/copy")
    public ApiResponse<Map<String, Object>> copy(@PathVariable String workflowCode, @RequestBody Map<String, Object> body) {
        String newWorkflowCode = String.valueOf(body.get("newWorkflowCode"));
        return workflowService.copyWorkflow(workflowCode, newWorkflowCode, null);
    }
}