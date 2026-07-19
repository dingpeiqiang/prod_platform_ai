package com.sitech.prodai.service;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.domain.entity.Workflow;
import com.sitech.prodai.domain.entity.WorkflowExecution;
import com.sitech.prodai.repository.WorkflowExecutionRepository;
import com.sitech.prodai.repository.WorkflowRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowRepository workflowRepository;

    public WorkflowExecutionService(WorkflowExecutionRepository executionRepository,
                                    WorkflowRepository workflowRepository) {
        this.executionRepository = executionRepository;
        this.workflowRepository = workflowRepository;
    }

    @Transactional
    public ApiResponse<Map<String, Object>> createExecution(String workflowCode, Map<String, Object> executionData, String user) {
        try {
            Optional<Workflow> workflowOpt = workflowRepository.findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                return ApiResponse.fail("Workflow " + workflowCode + " not found");
            }

            Workflow workflow = workflowOpt.get();

            String executionId = UUID.randomUUID().toString().substring(0, 8);

            WorkflowExecution execution = new WorkflowExecution();
            execution.setWorkflowId(workflow.getId());
            execution.setWorkflowCode(workflowCode);
            execution.setExecutionId(executionId);
            execution.setStatus("pending");
            execution.setInputData((Map<String, Object>) executionData.getOrDefault("inputData", new HashMap<>()));
            execution.setTriggeredBy(user);
            execution.setTriggerType(String.valueOf(executionData.getOrDefault("triggerType", "manual")));
            execution.setNotes(String.valueOf(executionData.get("notes")));
            execution.setExecutionLogs(new ArrayList<>());

            execution = executionRepository.save(execution);

            workflow.setExecutionCount(workflow.getExecutionCount() + 1);
            workflowRepository.save(workflow);

            logger.info("Created execution for workflow {}: {}", workflowCode, executionId);
            return ApiResponse.ok(toMap(execution));
        } catch (Exception e) {
            logger.error("Failed to create execution for workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> updateExecutionStatus(String executionId, Map<String, Object> statusData) {
        try {
            Optional<WorkflowExecution> executionOpt = executionRepository.findByExecutionId(executionId);
            if (executionOpt.isEmpty()) {
                return ApiResponse.fail("Execution " + executionId + " not found");
            }

            WorkflowExecution execution = executionOpt.get();

            if (statusData.containsKey("status")) {
                execution.setStatus(String.valueOf(statusData.get("status")));
            }
            if (statusData.containsKey("startTime")) {
                execution.setStartTime(LocalDateTime.parse(String.valueOf(statusData.get("startTime")).replace('Z', '+')));
            }
            if (statusData.containsKey("endTime")) {
                execution.setEndTime(LocalDateTime.parse(String.valueOf(statusData.get("endTime")).replace('Z', '+')));
            }
            if (statusData.containsKey("durationSeconds")) {
                execution.setDurationSeconds((Integer) statusData.get("durationSeconds"));
            }
            if (statusData.containsKey("outputData")) {
                execution.setOutputData((Map<String, Object>) statusData.get("outputData"));
            }
            if (statusData.containsKey("errorMessage")) {
                execution.setErrorMessage(String.valueOf(statusData.get("errorMessage")));
            }
            if (statusData.containsKey("executionLogs")) {
                execution.setExecutionLogs((List<Object>) statusData.get("executionLogs"));
            }

            execution = executionRepository.save(execution);

            Optional<Workflow> workflowOpt = workflowRepository.findById(execution.getWorkflowId());
            if (workflowOpt.isPresent()) {
                Workflow workflow = workflowOpt.get();
                workflow.setLastExecutionAt(LocalDateTime.now());
                workflow.setLastExecutionStatus(execution.getStatus());
                workflowRepository.save(workflow);
            }

            return ApiResponse.ok(toMap(execution));
        } catch (Exception e) {
            logger.error("Failed to update execution {}", executionId, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> getExecution(String executionId) {
        try {
            Optional<WorkflowExecution> executionOpt = executionRepository.findByExecutionId(executionId);
            if (executionOpt.isEmpty()) {
                return ApiResponse.fail("Execution " + executionId + " not found");
            }

            return ApiResponse.ok(toMap(executionOpt.get()));
        } catch (Exception e) {
            logger.error("Failed to get execution {}", executionId, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> listExecutions(String workflowCode, Integer limit) {
        try {
            limit = limit != null ? limit : 50;
            List<WorkflowExecution> executions;

            if (workflowCode != null && !workflowCode.isBlank()) {
                executions = executionRepository.findByWorkflowCodeOrderByCreatedAtDesc(workflowCode);
            } else {
                executions = executionRepository.findAll();
            }

            if (executions.size() > limit) {
                executions = executions.subList(0, limit);
            }

            List<Map<String, Object>> data = new ArrayList<>();
            for (WorkflowExecution execution : executions) {
                data.add(toMap(execution));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("data", data);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            logger.error("Failed to list executions", e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> listExecutionsByStatus(String status) {
        try {
            List<WorkflowExecution> executions = executionRepository.findByStatusOrderByCreatedAtDesc(status);

            List<Map<String, Object>> data = new ArrayList<>();
            for (WorkflowExecution execution : executions) {
                data.add(toMap(execution));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("data", data);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            logger.error("Failed to list executions by status {}", status, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> resumeExecution(String executionId, String user) {
        try {
            Optional<WorkflowExecution> executionOpt = executionRepository.findByExecutionId(executionId);
            if (executionOpt.isEmpty()) {
                return ApiResponse.fail("Execution " + executionId + " not found");
            }

            WorkflowExecution execution = executionOpt.get();

            if (!"failed".equals(execution.getStatus()) && !"pending".equals(execution.getStatus())) {
                return ApiResponse.fail("Execution can only be resumed if it is failed or pending");
            }

            execution.setStatus("pending");
            execution.setErrorMessage(null);
            execution.setTriggeredBy(user);

            execution = executionRepository.save(execution);

            logger.info("Resumed execution: {}", executionId);
            return ApiResponse.ok(toMap(execution));
        } catch (Exception e) {
            logger.error("Failed to resume execution {}", executionId, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> cancelExecution(String executionId) {
        try {
            Optional<WorkflowExecution> executionOpt = executionRepository.findByExecutionId(executionId);
            if (executionOpt.isEmpty()) {
                return ApiResponse.fail("Execution " + executionId + " not found");
            }

            WorkflowExecution execution = executionOpt.get();

            if (!"pending".equals(execution.getStatus())) {
                return ApiResponse.fail("Only pending executions can be cancelled");
            }

            execution.setStatus("cancelled");
            execution.setEndTime(LocalDateTime.now());

            execution = executionRepository.save(execution);

            logger.info("Cancelled execution: {}", executionId);
            return ApiResponse.ok(toMap(execution));
        } catch (Exception e) {
            logger.error("Failed to cancel execution {}", executionId, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<List<Map<String, Object>>> executeWorkflow(String workflowCode, Map<String, Object> inputData, String user) {
        try {
            Optional<Workflow> workflowOpt = workflowRepository.findByWorkflowCode(workflowCode);
            if (workflowOpt.isEmpty()) {
                throw new RuntimeException("Workflow " + workflowCode + " not found");
            }

            Workflow workflow = workflowOpt.get();

            List<Map<String, Object>> results = new ArrayList<>();

            String executionId = UUID.randomUUID().toString().substring(0, 8);

            WorkflowExecution execution = new WorkflowExecution();
            execution.setWorkflowId(workflow.getId());
            execution.setWorkflowCode(workflowCode);
            execution.setExecutionId(executionId);
            execution.setStatus("running");
            execution.setInputData(inputData != null ? inputData : new HashMap<>());
            execution.setStartTime(LocalDateTime.now());
            execution.setTriggeredBy(user);
            execution.setTriggerType("manual");
            execution.setExecutionLogs(new ArrayList<>());

            execution = executionRepository.save(execution);

            Map<String, Object> executionData = toMap(execution);
            results.add(executionData);

            workflow.setExecutionCount(workflow.getExecutionCount() + 1);
            workflow.setLastExecutionAt(LocalDateTime.now());
            workflow.setLastExecutionStatus("running");
            workflowRepository.save(workflow);

            logger.info("Started execution for workflow {}: {}", workflowCode, executionId);
            return ApiResponse.ok(results);
        } catch (Exception e) {
            logger.error("Failed to execute workflow {}", workflowCode, e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    private Map<String, Object> toMap(WorkflowExecution execution) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", execution.getId());
        map.put("workflowId", execution.getWorkflowId());
        map.put("workflowCode", execution.getWorkflowCode());
        map.put("executionId", execution.getExecutionId());
        map.put("status", execution.getStatus());
        map.put("startTime", execution.getStartTime());
        map.put("endTime", execution.getEndTime());
        map.put("durationSeconds", execution.getDurationSeconds());
        map.put("inputData", execution.getInputData());
        map.put("outputData", execution.getOutputData());
        map.put("errorMessage", execution.getErrorMessage());
        map.put("executionLogs", execution.getExecutionLogs());
        map.put("triggeredBy", execution.getTriggeredBy());
        map.put("triggerType", execution.getTriggerType());
        map.put("notes", execution.getNotes());
        map.put("createdAt", execution.getCreatedAt());
        map.put("updatedAt", execution.getUpdatedAt());
        return map;
    }
}