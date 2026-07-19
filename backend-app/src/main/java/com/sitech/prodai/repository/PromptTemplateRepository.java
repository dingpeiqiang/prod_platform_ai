package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Integer> {

    Optional<PromptTemplate> findByCode(String code);

    List<PromptTemplate> findByIsActiveTrue();

    List<PromptTemplate> findByCategoryAndIsActiveTrue(String category);
}
