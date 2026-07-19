package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Integer> {

    Optional<WorkflowExecution> findByExecutionId(String executionId);

    List<WorkflowExecution> findByWorkflowIdOrderByCreatedAtDesc(Integer workflowId);

    List<WorkflowExecution> findByWorkflowCodeOrderByCreatedAtDesc(String workflowCode);

    List<WorkflowExecution> findByStatusOrderByCreatedAtDesc(String status);

    void deleteByWorkflowId(Integer workflowId);
}