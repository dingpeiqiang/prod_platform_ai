package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.OntologyInstance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyInstanceRepository extends JpaRepository<OntologyInstance, Long> {
}
