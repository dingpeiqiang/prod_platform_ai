package com.sitech.prodai.service;

import com.sitech.prodai.domain.entity.ChatMessage;
import com.sitech.prodai.domain.entity.ChatSession;
import com.sitech.prodai.repository.ChatMessageRepository;
import com.sitech.prodai.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 对话持久化服务 —— 管理 ChatSession / ChatMessage 的 CRUD。
 */
@Service
public class ChatPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatPersistenceService.class);

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;

    public ChatPersistenceService(ChatSessionRepository sessionRepo,
                                  ChatMessageRepository messageRepo) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
    }

    // ── Session ──────────────────────────────────────

    /**
     * 获取或创建会话。若 sessionId 不存在则新建。
     */
    @Transactional
    public ChatSession getOrCreateSession(String sessionId, String userId, String title) {
        Optional<ChatSession> existing = sessionRepo.findBySessionId(sessionId);
        if (existing.isPresent()) {
            return existing.get();
        }
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新对话");
        session.setStatus("active");
        sessionRepo.save(session);
        log.info("[ChatPersistence] 创建会话: sessionId={}, userId={}", sessionId, userId);
        return session;
    }

    /**
     * 更新会话标题。
     */
    @Transactional
    public void updateSessionTitle(String sessionId, String title) {
        sessionRepo.findBySessionId(sessionId).ifPresent(s -> {
            s.setTitle(title);
            sessionRepo.save(s);
        });
    }

    /**
     * 归档会话。
     */
    @Transactional
    public void archiveSession(String sessionId) {
        sessionRepo.findBySessionId(sessionId).ifPresent(s -> {
            s.setStatus("archived");
            sessionRepo.save(s);
        });
    }

    /**
     * 获取用户最近的会话列表。
     */
    public List<ChatSession> getRecentSessions(String userId, int limit) {
        return sessionRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit)).getContent();
    }

    // ── Message ──────────────────────────────────────

    /**
     * 保存一条消息。自动生成 messageId 和 sortOrder。
     */
    @Transactional
    public ChatMessage saveMessage(String sessionId, String role, String content, String contentType) {
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Integer maxOrder = messageRepo.findMaxSortOrderBySessionId(sessionId);

        ChatMessage msg = new ChatMessage();
        msg.setMessageId(messageId);
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType(contentType != null ? contentType : "text");
        msg.setSortOrder(maxOrder + 1);
        messageRepo.save(msg);
        return msg;
    }

    /**
     * 从 request.messages 列表批量保存消息。
     */
    @Transactional
    public int saveMessages(String sessionId, List<Map<String, Object>> messages) {
        int count = 0;
        for (Map<String, Object> m : messages) {
            String role = String.valueOf(m.getOrDefault("role", "user"));
            String content = String.valueOf(m.getOrDefault("content", ""));
            if (!content.isBlank()) {
                saveMessage(sessionId, role, content, "text");
                count++;
            }
        }
        return count;
    }

    /**
     * 获取会话的所有消息（按排序顺序）。
     */
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return messageRepo.findBySessionIdOrderBySortOrderAsc(sessionId);
    }

    /**
     * 获取会话最近 N 条消息（用于构建 LLM 上下文）。
     */
    public List<Map<String, String>> getRecentMessages(String sessionId, int limit) {
        List<ChatMessage> all = messageRepo.findBySessionIdOrderBySortOrderAsc(sessionId);
        int start = Math.max(0, all.size() - limit);
        return all.subList(start, all.size()).stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .toList();
    }

    /**
     * 获取会话消息数量。
     */
    public long getMessageCount(String sessionId) {
        return messageRepo.countBySessionId(sessionId);
    }

    /**
     * 按关键词搜索消息。
     */
    public List<ChatMessage> searchMessages(String sessionId, String keyword, int limit) {
        if (sessionId != null && !sessionId.isBlank()) {
            return messageRepo.findBySessionIdAndContentContainingOrderByCreatedAtDesc(
                    sessionId, keyword, PageRequest.of(0, limit));
        }
        return messageRepo.findByContentContainingOrderByCreatedAtDesc(
                keyword, PageRequest.of(0, limit));
    }
}
