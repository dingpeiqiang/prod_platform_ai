package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.SwrlRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SwrlRuleRepository extends JpaRepository<SwrlRule, Integer> {

    Optional<SwrlRule> findByRuleId(String ruleId);

    List<SwrlRule> findByEnabledTrue();

    List<SwrlRule> findByModule(String module);

    List<SwrlRule> findByEnabledTrueAndModule(String module);
}