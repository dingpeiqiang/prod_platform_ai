package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.OntologyInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OntologyInstanceRepository extends JpaRepository<OntologyInstance, Long> {

    List<OntologyInstance> findTop50ByOntologyCodeOrderByIdDesc(String ontologyCode);

    List<OntologyInstance> findTop50ByOntologyCodeAndSessionIdOrderByIdDesc(String ontologyCode, String sessionId);

    List<OntologyInstance> findTop50ByOntologyCodeAndUserIdOrderByIdDesc(String ontologyCode, String userId);

    List<OntologyInstance> findTop50ByOntologyCodeAndStatusOrderByIdDesc(String ontologyCode, String status);

    Optional<OntologyInstance> findByIdAndOntologyCode(Long id, String ontologyCode);
}
