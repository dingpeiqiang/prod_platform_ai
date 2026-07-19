package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.McpToolStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface McpToolStatsRepository extends JpaRepository<McpToolStats, Integer> {

    Optional<McpToolStats> findByToolNameAndStatDateAndStatHour(String toolName, String statDate, Integer statHour);

    List<McpToolStats> findByToolNameOrderByStatDateDesc(String toolName);

    List<McpToolStats> findByStatDateOrderByToolName(String statDate);

    void deleteByToolName(String toolName);
}