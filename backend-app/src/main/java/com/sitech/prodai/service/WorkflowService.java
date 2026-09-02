package com.sitech.prodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.domain.entity.Workflow;
import com.sitech.prodai.domain.entity.WorkflowHistory;
import com.sitech.prodai.mapper.WorkflowHistoryMapper;
import com.sitech.prodai.mapper.WorkflowMapper;
import com.sitech.prodai.service.flow.EditorDefinitionNormalizer;
import com.sitech.prodai.service.flow.FlowDefinitionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowMapper workflowMapper;
    private final WorkflowHistoryMapper workflowHistoryMapper;
    private final FlowDefinitionValidator flowDefinitionValidator;

    public WorkflowService(WorkflowMapper workflowMapper,
                           WorkflowHistoryMapper workflowHistoryMapper,
                           FlowDefinitionValidator flowDefinitionValidator) {
        this.workflowMapper = workflowMapper;
        this.workflowHistoryMapper = workflowHistoryMapper;
        this.flowDefinitionValidator = flowDefinitionValidator;
    }

    @Transactional
    public ApiResponse<Map<String, Object>> createWorkflow(Map<String, Object> workflowData, String user) {
        try {
            String workflowCode = String.valueOf(workflowData.get("workflowCode"));
            if (workflowCode == null || workflowCode.isBlank()) {
                return ApiResponse.fail("workflowCode is required");
            }

            if (existsByWorkflowCode(workflowCode)) {
                return ApiResponse.fail("Workflow " + workflowCode + " already exists");
            }

            Workflow workflow = new Workflow();
            workflow.setWorkflowCode(workflowCode);
            workflow.setWorkflowName(String.valueOf(workflowData.getOrDefault("workflowName", workflowCode)));
            workflow.setDescription(String.valueOf(workflowData.get("description")));
            workflow.setCategory(String.valueOf(workflowData.getOrDefault("category", "general")));
            workflow.setTags((List<Object>) workflowData.getOrDefault("tags", new ArrayList<>()));
            workflow.setPriority((Integer) workflowData.getOrDefault("priority", 10));
            workflow.setIsActive((Boolean) workflowData.getOrDefault("isActive", true));
            workflow.setIsInLibrary((Boolean) workflowData.getOrDefault("isInLibrary", false));
            workflow.setWorkflowData((Map<String, Object>) workflowData.getOrDefault("workflowData", new HashMap<>()));
            workflow.setVersion(1);
            workflow.setCreatedBy(user);
            workflow.setUpdatedBy(user);

            workflowMapper.insert(workflow);

            WorkflowHistory history = new WorkflowHistory();
            history.setWorkflowId(workflow.getId());
            history.setWorkflowCode(workflow.getWorkflowCode());
            history.setVersion(1);
            history.setWorkflowName(workflow.getWorkflowName());
            history.setDescription(workflow.getDescription());
            history.setWorkflowData(workflow.getWorkflowData());
            history.setCategory(workflow.getCategory());
            history.setTags(workflow.getTags());
            history.setPriority(workflow.getPriority());
            history.setIsActive(workflow.getIsActive());
            history.setChangeNote("Initial version");
            history.setCreatedBy(user);
            workflowHistoryMapper.insert(history);

            logger.info("Created workflow: {}", workflowCode);
            return ApiResponse.ok(toMap(workflow));
        } catch (Exception e) {
            logger.error("Failed to create workflow", e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> getWorkflow(String workflowCode) {
        try {
            Optional<Workflow> workflowOpt = findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                return ApiResponse.fail("Workflow " + workflowCode + " not found");
            }

            Workflow workflow = workflowOpt.get();
            Map<String, Object> workflowMap = toMap(workflow);
            Map<String, Object> workflowData = (Map<String, Object>) workflowMap.getOrDefault("workflowData", new HashMap<>());
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowData.getOrDefault("nodes", new ArrayList<>());
            for (Map<String, Object> node : nodes) {
                Map<String, Object> nodeData = (Map<String, Object>) node.getOrDefault("data", new HashMap<>());
                nodeData.remove("outputs");
            }

            logger.info("Loaded workflow {}", workflowCode);
            return ApiResponse.ok(workflowMap);
        } catch (Exception e) {
            logger.error("Failed to get workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> updateWorkflow(String workflowCode, Map<String, Object> workflowData, String user) {
        try {
            Optional<Workflow> workflowOpt = findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                return ApiResponse.fail("Workflow " + workflowCode + " not found");
            }

            Workflow workflow = workflowOpt.get();

            WorkflowHistory history = new WorkflowHistory();
            history.setWorkflowId(workflow.getId());
            history.setWorkflowCode(workflow.getWorkflowCode());
            history.setVersion(workflow.getVersion());
            history.setWorkflowName(workflow.getWorkflowName());
            history.setDescription(workflow.getDescription());
            history.setWorkflowData(workflow.getWorkflowData());
            history.setCategory(workflow.getCategory());
            history.setTags(workflow.getTags());
            history.setPriority(workflow.getPriority());
            history.setIsActive(workflow.getIsActive());
            history.setChangeNote(String.valueOf(workflowData.getOrDefault("changeNote", "Updated to version " + (workflow.getVersion() + 1))));
            history.setCreatedBy(user);
            workflowHistoryMapper.insert(history);

            if (workflowData.containsKey("workflowName")) {
                workflow.setWorkflowName(String.valueOf(workflowData.get("workflowName")));
            }
            if (workflowData.containsKey("description")) {
                workflow.setDescription(String.valueOf(workflowData.get("description")));
            }
            if (workflowData.containsKey("category")) {
                workflow.setCategory(String.valueOf(workflowData.get("category")));
            }
            if (workflowData.containsKey("tags")) {
                workflow.setTags((List<Object>) workflowData.get("tags"));
            }
            if (workflowData.containsKey("priority")) {
                workflow.setPriority((Integer) workflowData.get("priority"));
            }
            if (workflowData.containsKey("isActive")) {
                workflow.setIsActive((Boolean) workflowData.get("isActive"));
            }
            if (workflowData.containsKey("isInLibrary")) {
                workflow.setIsInLibrary((Boolean) workflowData.get("isInLibrary"));
            }
            if (workflowData.containsKey("workflowData")) {
                workflow.setWorkflowData((Map<String, Object>) workflowData.get("workflowData"));
            }

            workflow.setVersion(workflow.getVersion() + 1);
            workflow.setUpdatedBy(user);

            workflowMapper.updateById(workflow);

            logger.info("Updated workflow: {} to version {}", workflowCode, workflow.getVersion());
            return ApiResponse.ok(toMap(workflow));
        } catch (Exception e) {
            logger.error("Failed to update workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> deleteWorkflow(String workflowCode) {
        try {
            Optional<Workflow> workflowOpt = findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                return ApiResponse.fail("Workflow " + workflowCode + " not found");
            }

            workflowMapper.deleteById(workflowOpt.get().getId());

            logger.info("Deleted workflow: {}", workflowCode);
            return ApiResponse.ok(null, "Workflow " + workflowCode + " deleted successfully");
        } catch (Exception e) {
            logger.error("Failed to delete workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> listWorkflows(String category, Boolean isActive, String keyword,
                                                          String workflowCode, List<String> tags, String createdBy,
                                                          Integer minExecutionCount, Integer maxExecutionCount,
                                                          String sortBy, String sortOrder, Integer page, Integer pageSize) {
        try {
            LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<>();

            wrapper.eq(category != null && !category.isBlank(), Workflow::getCategory, category)
                    .eq(isActive != null, Workflow::getIsActive, isActive)
                    .eq(workflowCode != null && !workflowCode.isBlank(), Workflow::getWorkflowCode, workflowCode)
                    .eq(createdBy != null && !createdBy.isBlank(), Workflow::getCreatedBy, createdBy)
                    .ge(minExecutionCount != null, Workflow::getExecutionCount, minExecutionCount)
                    .le(maxExecutionCount != null, Workflow::getExecutionCount, maxExecutionCount);

            if (keyword != null && !keyword.isBlank()) {
                wrapper.and(w -> w.like(Workflow::getWorkflowCode, keyword)
                        .or().like(Workflow::getWorkflowName, keyword)
                        .or().like(Workflow::getDescription, keyword));
            }

            String sortColumn = switch (sortBy == null ? "createdAt" : sortBy) {
                case "updatedAt" -> "updated_at";
                case "priority" -> "priority";
                case "executionCount" -> "execution_count";
                default -> "created_at";
            };
            boolean asc = !"desc".equalsIgnoreCase(sortOrder);
            wrapper.last("ORDER BY " + sortColumn + (asc ? " ASC" : " DESC"));

            int p = page != null ? page : 1;
            int ps = pageSize != null ? pageSize : 20;

            Page<Workflow> result = workflowMapper.selectPage(new Page<>(p, ps), wrapper);
            long total = result.getTotal();

            List<Map<String, Object>> data = new ArrayList<>();
            for (Workflow workflow : result.getRecords()) {
                data.add(toMap(workflow));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("total", total);
            response.put("page", p);
            response.put("pageSize", ps);
            response.put("totalPages", (int) Math.ceil((double) total / ps));
            response.put("data", data);

            logger.info("Loaded {} workflows from database (total: {})", data.size(), total);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            logger.error("Failed to list workflows", e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> publishWorkflow(String workflowCode, String user) {
        try {
            Optional<Workflow> workflowOpt = findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                return ApiResponse.fail("工作流 " + workflowCode + " 不存在");
            }

            Workflow workflow = workflowOpt.get();

            // 发布即绿灯（改造方案 §12.3 铁律三）：发布前先归一化 + 守门校验，非法定义拒绝发布
            Map<String, Object> definition = workflow.getWorkflowData();
            if (EditorDefinitionNormalizer.needsNormalize(definition)) {
                definition = EditorDefinitionNormalizer.normalize(definition);
            }
            FlowDefinitionValidator.ValidationResult check = flowDefinitionValidator.validate(definition);
            if (!check.valid()) {
                logger.warn("Publish rejected, invalid definition: {} problems={}", workflowCode, check.problems());
                return ApiResponse.fail("流程定义校验未通过，拒绝发布", check.problems());
            }

            WorkflowHistory history = new WorkflowHistory();
            history.setWorkflowId(workflow.getId());
            history.setWorkflowCode(workflow.getWorkflowCode());
            history.setVersion(workflow.getVersion());
            history.setWorkflowName(workflow.getWorkflowName());
            history.setDescription(workflow.getDescription());
            history.setWorkflowData(workflow.getWorkflowData());
            history.setCategory(workflow.getCategory());
            history.setTags(workflow.getTags());
            history.setPriority(workflow.getPriority());
            history.setIsActive(workflow.getIsActive());
            history.setChangeNote("发布前版本 " + workflow.getVersion());
            history.setCreatedBy(user);
            workflowHistoryMapper.insert(history);

            workflow.setIsActive(true);
            workflow.setVersion(workflow.getVersion() + 1);
            workflow.setUpdatedBy(user);

            workflowMapper.updateById(workflow);

            logger.info("Published workflow: {} (version {})", workflowCode, workflow.getVersion());
            return ApiResponse.ok(toMap(workflow), "工作流 " + workflowCode + " 已成功发布");
        } catch (Exception e) {
            logger.error("Failed to publish workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> unpublishWorkflow(String workflowCode, String user) {
        try {
            Optional<Workflow> workflowOpt = findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                return ApiResponse.fail("工作流 " + workflowCode + " 不存在");
            }

            Workflow workflow = workflowOpt.get();

            WorkflowHistory history = new WorkflowHistory();
            history.setWorkflowId(workflow.getId());
            history.setWorkflowCode(workflow.getWorkflowCode());
            history.setVersion(workflow.getVersion());
            history.setWorkflowName(workflow.getWorkflowName());
            history.setDescription(workflow.getDescription());
            history.setWorkflowData(workflow.getWorkflowData());
            history.setCategory(workflow.getCategory());
            history.setTags(workflow.getTags());
            history.setPriority(workflow.getPriority());
            history.setIsActive(workflow.getIsActive());
            history.setChangeNote("下线前版本 " + workflow.getVersion());
            history.setCreatedBy(user);
            workflowHistoryMapper.insert(history);

            workflow.setIsActive(false);
            workflow.setVersion(workflow.getVersion() + 1);
            workflow.setUpdatedBy(user);

            workflowMapper.updateById(workflow);

            logger.info("Unpublished workflow: {}", workflowCode);
            return ApiResponse.ok(toMap(workflow), "工作流 " + workflowCode + " 已成功下线");
        } catch (Exception e) {
            logger.error("Failed to unpublish workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> batchPublish(List<String> workflowCodes, String user) {
        Map<String, Object> successList = new HashMap<>();
        Map<String, Object> failedList = new HashMap<>();

        for (String workflowCode : workflowCodes) {
            ApiResponse<Map<String, Object>> result = publishWorkflow(workflowCode, user);
            Map<String, Object> item = new HashMap<>();
            item.put("workflowCode", workflowCode);
            item.put("message", result.getMessage());
            if (result.isSuccess()) {
                successList.put(workflowCode, item);
            } else {
                failedList.put(workflowCode, item);
            }
        }

        Map<String, Object> results = new HashMap<>();
        results.put("success", successList);
        results.put("failed", failedList);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("total", workflowCodes.size());
        response.put("successCount", successList.size());
        response.put("failedCount", failedList.size());
        response.put("results", results);

        return ApiResponse.ok(response);
    }

    @Transactional
    public ApiResponse<Map<String, Object>> rollbackVersion(String workflowCode, Integer targetVersion, String user) {
        try {
            Optional<Workflow> workflowOpt = findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                return ApiResponse.fail("工作流 " + workflowCode + " 不存在");
            }

            Workflow workflow = workflowOpt.get();

            List<WorkflowHistory> histories = workflowHistoryMapper.selectList(
                    new LambdaQueryWrapper<WorkflowHistory>()
                            .eq(WorkflowHistory::getWorkflowCode, workflowCode)
                            .orderByDesc(WorkflowHistory::getVersion));
            Optional<WorkflowHistory> historyOpt = histories.stream()
                    .filter(h -> h.getVersion().equals(targetVersion))
                    .findFirst();

            if (historyOpt.isEmpty()) {
                return ApiResponse.fail("版本 " + targetVersion + " 不存在");
            }

            WorkflowHistory targetHistory = historyOpt.get();

            WorkflowHistory currentHistory = new WorkflowHistory();
            currentHistory.setWorkflowId(workflow.getId());
            currentHistory.setWorkflowCode(workflow.getWorkflowCode());
            currentHistory.setVersion(workflow.getVersion());
            currentHistory.setWorkflowName(workflow.getWorkflowName());
            currentHistory.setDescription(workflow.getDescription());
            currentHistory.setWorkflowData(workflow.getWorkflowData());
            currentHistory.setCategory(workflow.getCategory());
            currentHistory.setTags(workflow.getTags());
            currentHistory.setPriority(workflow.getPriority());
            currentHistory.setIsActive(workflow.getIsActive());
            currentHistory.setChangeNote("回滚前版本 " + workflow.getVersion());
            currentHistory.setCreatedBy(user);
            workflowHistoryMapper.insert(currentHistory);

            workflow.setWorkflowName(targetHistory.getWorkflowName());
            workflow.setDescription(targetHistory.getDescription());
            workflow.setWorkflowData(targetHistory.getWorkflowData());
            workflow.setCategory(targetHistory.getCategory());
            workflow.setTags(targetHistory.getTags());
            workflow.setPriority(targetHistory.getPriority());
            workflow.setIsActive(targetHistory.getIsActive());
            workflow.setVersion(workflow.getVersion() + 1);
            workflow.setUpdatedBy(user);

            workflowMapper.updateById(workflow);

            logger.info("Rolled back workflow {} to version {}", workflowCode, targetVersion);
            return ApiResponse.ok(toMap(workflow), "工作流 " + workflowCode + " 已回滚到版本 " + targetVersion);
        } catch (Exception e) {
            logger.error("Failed to rollback workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> compareVersions(String workflowCode, Integer version1, Integer version2) {
        try {
            List<WorkflowHistory> histories = workflowHistoryMapper.selectList(
                    new LambdaQueryWrapper<WorkflowHistory>()
                            .eq(WorkflowHistory::getWorkflowCode, workflowCode)
                            .orderByDesc(WorkflowHistory::getVersion));

            Optional<WorkflowHistory> history1Opt = histories.stream()
                    .filter(h -> h.getVersion().equals(version1))
                    .findFirst();
            Optional<WorkflowHistory> history2Opt = histories.stream()
                    .filter(h -> h.getVersion().equals(version2))
                    .findFirst();

            if (history1Opt.isEmpty()) {
                return ApiResponse.fail("版本 " + version1 + " 不存在");
            }
            if (history2Opt.isEmpty()) {
                return ApiResponse.fail("版本 " + version2 + " 不存在");
            }

            WorkflowHistory h1 = history1Opt.get();
            WorkflowHistory h2 = history2Opt.get();

            Map<String, Object> diff = new HashMap<>();
            diff.put("workflowCode", workflowCode);
            diff.put("version1", version1);
            diff.put("version2", version2);
            List<Map<String, Object>> changes = new ArrayList<>();

            if (!equals(h1.getWorkflowName(), h2.getWorkflowName())) {
                changes.add(createChange("workflowName", "工作流名称", h1.getWorkflowName(), h2.getWorkflowName()));
            }
            if (!equals(h1.getDescription(), h2.getDescription())) {
                changes.add(createChange("description", "描述", h1.getDescription(), h2.getDescription()));
            }
            if (!equals(h1.getCategory(), h2.getCategory())) {
                changes.add(createChange("category", "分类", h1.getCategory(), h2.getCategory()));
            }
            if (!equals(h1.getPriority(), h2.getPriority())) {
                changes.add(createChange("priority", "优先级", h1.getPriority(), h2.getPriority()));
            }
            if (!equals(h1.getIsActive(), h2.getIsActive())) {
                changes.add(createChange("isActive", "启用状态",
                        h1.getIsActive() ? "启用" : "禁用", h2.getIsActive() ? "启用" : "禁用"));
            }
            if (!equals(h1.getTags(), h2.getTags())) {
                changes.add(createChange("tags", "标签", h1.getTags(), h2.getTags()));
            }
            if (!equals(h1.getWorkflowData(), h2.getWorkflowData())) {
                Map<String, Object> change = createChange("workflowData", "工作流配置", h1.getWorkflowData(), h2.getWorkflowData());
                change.put("type", "complex");
                changes.add(change);
            }

            diff.put("changes", changes);
            return ApiResponse.ok(diff);
        } catch (Exception e) {
            logger.error("Failed to compare versions for workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> toggleWorkflow(String workflowCode) {
        try {
            Optional<Workflow> workflowOpt = findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                return ApiResponse.fail("Workflow " + workflowCode + " not found");
            }

            Workflow workflow = workflowOpt.get();
            workflow.setIsActive(!workflow.getIsActive());

            workflowMapper.updateById(workflow);

            return ApiResponse.ok(toMap(workflow));
        } catch (Exception e) {
            logger.error("Failed to toggle workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> getWorkflowHistory(String workflowCode) {
        try {
            Optional<Workflow> workflowOpt = findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                return ApiResponse.fail("Workflow " + workflowCode + " not found");
            }

            List<WorkflowHistory> histories = workflowHistoryMapper.selectList(
                    new LambdaQueryWrapper<WorkflowHistory>()
                            .eq(WorkflowHistory::getWorkflowCode, workflowCode)
                            .orderByDesc(WorkflowHistory::getVersion));
            List<Map<String, Object>> data = new ArrayList<>();
            for (WorkflowHistory history : histories) {
                data.add(toHistoryMap(history));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("data", data);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            logger.error("Failed to get workflow history {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> copyWorkflow(String sourceWorkflowCode, String newWorkflowCode, String user) {
        try {
            Optional<Workflow> sourceOpt = findByWorkflowCode(sourceWorkflowCode);
            if (sourceOpt.isEmpty()) {
                return ApiResponse.fail("源工作流 " + sourceWorkflowCode + " 不存在");
            }

            if (existsByWorkflowCode(newWorkflowCode)) {
                return ApiResponse.fail("工作流 " + newWorkflowCode + " 已存在");
            }

            Workflow source = sourceOpt.get();

            Workflow newWorkflow = new Workflow();
            newWorkflow.setWorkflowCode(newWorkflowCode);
            newWorkflow.setWorkflowName(source.getWorkflowName() + " (副本)");
            newWorkflow.setDescription(source.getDescription());
            newWorkflow.setCategory(source.getCategory());
            newWorkflow.setTags(source.getTags());
            newWorkflow.setPriority(source.getPriority());
            newWorkflow.setIsActive(true);
            newWorkflow.setIsInLibrary(false);
            newWorkflow.setWorkflowData(source.getWorkflowData());
            newWorkflow.setVersion(1);
            newWorkflow.setCreatedBy(user);
            newWorkflow.setUpdatedBy(user);

            workflowMapper.insert(newWorkflow);

            WorkflowHistory history = new WorkflowHistory();
            history.setWorkflowId(newWorkflow.getId());
            history.setWorkflowCode(newWorkflow.getWorkflowCode());
            history.setVersion(1);
            history.setWorkflowName(newWorkflow.getWorkflowName());
            history.setDescription(newWorkflow.getDescription());
            history.setWorkflowData(newWorkflow.getWorkflowData());
            history.setCategory(newWorkflow.getCategory());
            history.setTags(newWorkflow.getTags());
            history.setPriority(newWorkflow.getPriority());
            history.setIsActive(newWorkflow.getIsActive());
            history.setChangeNote("从 " + sourceWorkflowCode + " 复制");
            history.setCreatedBy(user);
            workflowHistoryMapper.insert(history);

            logger.info("Copied workflow: {} -> {}", sourceWorkflowCode, newWorkflowCode);
            return ApiResponse.ok(toMap(newWorkflow));
        } catch (Exception e) {
            logger.error("Failed to copy workflow {} -> {}", sourceWorkflowCode, newWorkflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    public ApiResponse<List<Map<String, Object>>> getCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();
        categories.add(createCategory("general", "通用"));
        categories.add(createCategory("ai", "AI应用"));
        categories.add(createCategory("data", "数据处理"));
        categories.add(createCategory("integration", "系统集成"));
        categories.add(createCategory("automation", "自动化"));
        return ApiResponse.ok(categories);
    }

    private Optional<Workflow> findByWorkflowCode(String workflowCode) {
        return Optional.ofNullable(workflowMapper.selectOne(
                new LambdaQueryWrapper<Workflow>().eq(Workflow::getWorkflowCode, workflowCode)));
    }

    private boolean existsByWorkflowCode(String workflowCode) {
        return workflowMapper.selectCount(
                new LambdaQueryWrapper<Workflow>().eq(Workflow::getWorkflowCode, workflowCode)) > 0;
    }

    private Map<String, Object> toMap(Workflow workflow) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", workflow.getId());
        map.put("workflowCode", workflow.getWorkflowCode());
        map.put("workflowName", workflow.getWorkflowName());
        map.put("description", workflow.getDescription());
        map.put("category", workflow.getCategory());
        map.put("tags", workflow.getTags());
        map.put("priority", workflow.getPriority());
        map.put("isActive", workflow.getIsActive());
        map.put("isInLibrary", workflow.getIsInLibrary());
        map.put("workflowData", workflow.getWorkflowData());
        map.put("version", workflow.getVersion());
        map.put("executionCount", workflow.getExecutionCount());
        map.put("lastExecutionAt", workflow.getLastExecutionAt());
        map.put("lastExecutionStatus", workflow.getLastExecutionStatus());
        map.put("createdBy", workflow.getCreatedBy());
        map.put("updatedBy", workflow.getUpdatedBy());
        map.put("createdAt", workflow.getCreatedAt());
        map.put("updatedAt", workflow.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toHistoryMap(WorkflowHistory history) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", history.getId());
        map.put("workflowId", history.getWorkflowId());
        map.put("workflowCode", history.getWorkflowCode());
        map.put("version", history.getVersion());
        map.put("workflowName", history.getWorkflowName());
        map.put("description", history.getDescription());
        map.put("workflowData", history.getWorkflowData());
        map.put("category", history.getCategory());
        map.put("tags", history.getTags());
        map.put("priority", history.getPriority());
        map.put("isActive", history.getIsActive());
        map.put("changeNote", history.getChangeNote());
        map.put("createdBy", history.getCreatedBy());
        map.put("createdAt", history.getCreatedAt());
        return map;
    }

    private Map<String, Object> createCategory(String code, String name) {
        Map<String, Object> cat = new HashMap<>();
        cat.put("code", code);
        cat.put("name", name);
        return cat;
    }

    private Map<String, Object> createChange(String field, String label, Object value1, Object value2) {
        Map<String, Object> change = new HashMap<>();
        change.put("field", field);
        change.put("label", label);
        change.put("value1", value1);
        change.put("value2", value2);
        return change;
    }

    private boolean equals(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }
}
