package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.Ontology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OntologyRepository extends JpaRepository<Ontology, Integer> {

    Optional<Ontology> findByOntologyCode(String ontologyCode);
}
