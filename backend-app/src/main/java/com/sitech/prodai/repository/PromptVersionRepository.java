package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersion, Integer> {

    List<PromptVersion> findByPromptIdOrderByVersionDesc(Integer promptId);
}
