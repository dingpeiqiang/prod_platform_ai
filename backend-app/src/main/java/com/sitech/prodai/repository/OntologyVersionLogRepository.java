package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.OntologyVersionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 本体资产版本库·动作日志仓库（P1-5 表 B；P3-5 ① 泛化审计一张表）。
 */
public interface OntologyVersionLogRepository extends JpaRepository<OntologyVersionLog, Long> {

    List<OntologyVersionLog> findByVersionIdOrderByCreatedAtDesc(Long versionId);

    List<OntologyVersionLog> findTop50ByActionOrderByCreatedAtDesc(String action);

    /** config 域链路明细（按时间升序 = 步骤前后序）。 */
    List<OntologyVersionLog> findByDomainAndTraceIdOrderByCreatedAtAsc(String domain, String traceId);

    /** 指定域最近 N 条审计（risk 展示 / batch 最近一次）。 */
    List<OntologyVersionLog> findTop50ByDomainOrderByCreatedAtDesc(String domain);

    Optional<OntologyVersionLog> findFirstByDomainOrderByCreatedAtDesc(String domain);
}
