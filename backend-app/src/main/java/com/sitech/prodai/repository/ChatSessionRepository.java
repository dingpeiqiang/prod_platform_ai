package com.sitech.prodai.repository;

import com.sitech.prodai.domain.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Integer> {

    Optional<ChatSession> findBySessionId(String sessionId);

    Page<ChatSession> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<ChatSession> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<ChatSession> findByUserIdAndStatusOrderByUpdatedAtDesc(String userId, String status, Pageable pageable);

    Page<ChatSession> findByStatusOrderByUpdatedAtDesc(String status, Pageable pageable);

    List<ChatSession> findBySessionIdIn(List<String> sessionIds);
}
