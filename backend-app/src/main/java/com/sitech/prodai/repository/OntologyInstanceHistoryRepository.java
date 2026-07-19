package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.OntologyInstanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntologyInstanceHistoryRepository extends JpaRepository<OntologyInstanceHistory, Integer> {

    List<OntologyInstanceHistory> findByFormInstanceId(Integer formInstanceId);

    List<OntologyInstanceHistory> findByFormInstanceIdInAndFieldCode(List<Integer> formInstanceIds, String fieldCode);

    List<OntologyInstanceHistory> findByFieldCodeAndUserId(String fieldCode, String userId);

    List<OntologyInstanceHistory> findByUserId(String userId);

    List<OntologyInstanceHistory> findByFieldCode(String fieldCode);

    long countByFieldCodeAndUserId(String fieldCode, String userId);
}
