package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.WorkflowHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowHistoryRepository extends JpaRepository<WorkflowHistory, Integer> {

    List<WorkflowHistory> findByWorkflowIdOrderByVersionDesc(Integer workflowId);

    List<WorkflowHistory> findByWorkflowCodeOrderByVersionDesc(String workflowCode);

    void deleteByWorkflowId(Integer workflowId);
}