package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.McpToolDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface McpToolDefinitionRepository extends JpaRepository<McpToolDefinition, Integer> {

    Optional<McpToolDefinition> findByToolName(String toolName);

    Optional<McpToolDefinition> findByToolCode(String toolCode);

    List<McpToolDefinition> findByCategory(String category);

    List<McpToolDefinition> findByIsEnabledTrue();

    List<McpToolDefinition> findByIsEnabledTrueAndIsPublicTrue();
}