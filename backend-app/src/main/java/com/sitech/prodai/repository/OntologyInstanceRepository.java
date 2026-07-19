package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.OntologyInstance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OntologyInstanceRepository extends JpaRepository<OntologyInstance, Integer> {

    List<OntologyInstance> findByOntologyCode(String ontologyCode);

    List<OntologyInstance> findByOntologyCodeAndUserId(String ontologyCode, String userId);

    List<OntologyInstance> findBySessionId(String sessionId);

    List<OntologyInstance> findByOntologyCodeAndStatus(String ontologyCode, String status);

    List<OntologyInstance> findByOntologyCodeAndStatusAndUserId(String ontologyCode, String status, String userId);

    List<OntologyInstance> findByOntologyCodeAndStatusOrderBySubmittedAtDesc(String ontologyCode, String status, Pageable pageable);

    List<OntologyInstance> findByOntologyCodeAndStatusAndUserIdOrderBySubmittedAtDesc(String ontologyCode, String status, String userId, Pageable pageable);

    List<OntologyInstance> findByOntologyCodeAndStatusAndSubmittedAtAfterOrderBySubmittedAtDesc(String ontologyCode, String status, LocalDateTime after, Pageable pageable);
}
