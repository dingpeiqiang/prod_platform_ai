package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.LlmUserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LlmUserConfigRepository extends JpaRepository<LlmUserConfig, Integer> {

    Optional<LlmUserConfig> findByUserIdentifierAndIsActiveTrue(String userIdentifier);

    List<LlmUserConfig> findByUserIdentifier(String userIdentifier);
}
