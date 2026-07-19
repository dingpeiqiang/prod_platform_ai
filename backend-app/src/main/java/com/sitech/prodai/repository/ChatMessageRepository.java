package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {

    Optional<ChatMessage> findByMessageId(String messageId);

    List<ChatMessage> findBySessionIdOrderBySortOrderAsc(String sessionId);

    Page<ChatMessage> findBySessionIdOrderBySortOrderAsc(String sessionId, Pageable pageable);

    List<ChatMessage> findBySessionIdAndCreatedAtBeforeOrderBySortOrderAsc(String sessionId, LocalDateTime before, Pageable pageable);

    List<ChatMessage> findBySessionIdAndCreatedAtAfterOrderBySortOrderAsc(String sessionId, LocalDateTime after, Pageable pageable);

    long countBySessionId(String sessionId);

    long countBySessionIdAndRole(String sessionId, String role);

    List<ChatMessage> findByContentContainingOrderByCreatedAtDesc(String keyword, Pageable pageable);

    List<ChatMessage> findBySessionIdAndContentContainingOrderByCreatedAtDesc(String sessionId, String keyword, Pageable pageable);

    @Query("SELECT COALESCE(MAX(m.sortOrder), 0) FROM ChatMessage m WHERE m.sessionId = :sessionId")
    Integer findMaxSortOrderBySessionId(@Param("sessionId") String sessionId);
}
