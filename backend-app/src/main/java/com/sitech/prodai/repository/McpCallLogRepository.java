package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.McpCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface McpCallLogRepository extends JpaRepository<McpCallLog, Integer> {

    List<McpCallLog> findByToolNameOrderByTimestampDesc(String toolName);

    List<McpCallLog> findByToolCategoryOrderByTimestampDesc(String toolCategory);

    List<McpCallLog> findBySuccessOrderByTimestampDesc(Boolean success);

    List<McpCallLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);

    List<McpCallLog> findByToolNameAndTimestampBetweenOrderByTimestampDesc(String toolName, LocalDateTime start, LocalDateTime end);

    long countByToolName(String toolName);

    long countByToolNameAndSuccess(String toolName, Boolean success);

    void deleteByTimestampBefore(LocalDateTime before);
}