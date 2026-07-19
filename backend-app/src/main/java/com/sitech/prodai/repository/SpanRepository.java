package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.Span;
import com.sitech.prodai.domain.entity.SpanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpanRepository extends JpaRepository<Span, String> {

    List<Span> findByTraceIdOrderByStartTimeAsc(String traceId);

    List<Span> findByTraceId(String traceId);

    List<Span> findByComponentOrderByCreatedAtDesc(String component);

    List<Span> findByStatusOrderByCreatedAtDesc(SpanStatus status);

    void deleteByTraceId(String traceId);
}