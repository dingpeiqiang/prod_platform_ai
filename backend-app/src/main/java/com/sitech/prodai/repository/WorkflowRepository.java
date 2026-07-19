package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.Workflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Integer> {

    Optional<Workflow> findByWorkflowCode(String workflowCode);

    boolean existsByWorkflowCode(String workflowCode);

    List<Workflow> findByCategory(String category);

    List<Workflow> findByIsActive(Boolean isActive);

    List<Workflow> findByCreatedBy(String createdBy);

    @Query("SELECT w FROM Workflow w WHERE w.workflowCode LIKE %:keyword% OR w.workflowName LIKE %:keyword% OR w.description LIKE %:keyword%")
    List<Workflow> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT DISTINCT w.category FROM Workflow w")
    List<String> findAllCategories();

    Page<Workflow> findByCategory(String category, Pageable pageable);

    Page<Workflow> findByIsActive(Boolean isActive, Pageable pageable);

    List<Workflow> findByIsActiveTrue();
}