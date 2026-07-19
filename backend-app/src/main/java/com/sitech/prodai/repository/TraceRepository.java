package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.Trace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TraceRepository extends JpaRepository<Trace, String> {

    List<Trace> findByServiceNameOrderByCreatedAtDesc(String serviceName);

    List<Trace> findByCreatedAtBetweenOrderByCreatedAtDesc(java.time.LocalDateTime start, java.time.LocalDateTime end);
}