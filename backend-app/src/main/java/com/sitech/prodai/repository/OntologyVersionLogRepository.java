package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.OntologyVersionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 本体资产版本库·动作日志仓库（P1-5 表 B）。
 */
public interface OntologyVersionLogRepository extends JpaRepository<OntologyVersionLog, Long> {

    List<OntologyVersionLog> findByVersionIdOrderByCreatedAtDesc(Long versionId);

    List<OntologyVersionLog> findTop50ByActionOrderByCreatedAtDesc(String action);
}
