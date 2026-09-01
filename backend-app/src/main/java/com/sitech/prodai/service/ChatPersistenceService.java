package com.sitech.prodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.domain.entity.ChatMessage;
import com.sitech.prodai.domain.entity.ChatMessageMetadata;
import com.sitech.prodai.domain.entity.ChatSession;
import com.sitech.prodai.mapper.ChatMessageMapper;
import com.sitech.prodai.mapper.ChatMessageMetadataMapper;
import com.sitech.prodai.mapper.ChatSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 对话持久化服务 —— 管理 ChatSession / ChatMessage / Metadata 的 CRUD。
 */
@Service
public class ChatPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatPersistenceService.class);

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatMessageMetadataMapper metadataMapper;
    private final ObjectMapper objectMapper;

    public ChatPersistenceService(ChatSessionMapper sessionMapper,
                                  ChatMessageMapper messageMapper,
                                  ChatMessageMetadataMapper metadataMapper,
                                  ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.metadataMapper = metadataMapper;
        this.objectMapper = objectMapper;
    }

    // ── Session ──────────────────────────────────────

    /**
     * 获取或创建会话。若 sessionId 不存在则新建。
     */
    @Transactional
    public ChatSession getOrCreateSession(String sessionId, String userId, String title) {
        ChatSession existing = sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
        if (existing != null) {
            return existing;
        }
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新对话");
        session.setStatus("active");
        sessionMapper.insert(session);
        log.info("[ChatPersistence] 创建会话: sessionId={}, userId={}", sessionId, userId);
        return session;
    }

    /**
     * 更新会话标题。
     */
    @Transactional
    public void updateSessionTitle(String sessionId, String title) {
        ChatSession s = sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
        if (s != null) {
            s.setTitle(title);
            sessionMapper.updateById(s);
        }
    }

    /**
     * 归档会话。
     */
    @Transactional
    public void archiveSession(String sessionId) {
        ChatSession s = sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
        if (s != null) {
            s.setStatus("archived");
            sessionMapper.updateById(s);
        }
    }

    /**
     * 获取用户最近的会话列表（仅返回至少有一条消息的会话）。
     */
    public List<ChatSession> getRecentSessions(String userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<ChatSession> sessions = sessionMapper.selectPage(
                        new Page<>(1, safeLimit * 3L),
                        new LambdaQueryWrapper<ChatSession>()
                                .eq(ChatSession::getUserId, userId)
                                .orderByDesc(ChatSession::getCreatedAt))
                .getRecords();
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<String> sessionIds = sessions.stream().map(ChatSession::getSessionId).toList();
        Set<String> nonEmpty = messageMapper.countGroupBySessionId(sessionIds).stream()
                .map(row -> String.valueOf(row.get("sessionId")))
                .collect(java.util.stream.Collectors.toSet());
        return sessions.stream()
                .filter(s -> nonEmpty.contains(s.getSessionId()))
                .limit(safeLimit)
                .toList();
    }

    // ── Message ──────────────────────────────────────

    /**
     * 保存一条消息（无 metadata）。
     */
    @Transactional
    public ChatMessage saveMessage(String sessionId, String role, String content, String contentType) {
        return saveMessage(sessionId, role, content, contentType, null);
    }

    /**
     * 保存一条消息。内容为空时不落库，返回 null。
     * metadata 写入 KV 扩展表，供历史还原 intentData / streamText 等。
     */
    @Transactional
    public ChatMessage saveMessage(String sessionId, String role, String content, String contentType,
                                   Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            log.debug("[ChatPersistence] 跳过空内容消息: sessionId={}, role={}", sessionId, role);
            return null;
        }
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Integer maxOrder = messageMapper.findMaxSortOrderBySessionId(sessionId);
        int sortOrder = (maxOrder == null ? 0 : maxOrder) + 1;

        ChatMessage msg = new ChatMessage();
        msg.setMessageId(messageId);
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType(contentType != null ? contentType : "text");
        msg.setSortOrder(sortOrder);
        messageMapper.insert(msg);

        if (metadata != null && !metadata.isEmpty()) {
            List<ChatMessageMetadata> metaList = new ArrayList<>();
            for (Map.Entry<String, Object> e : metadata.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                ChatMessageMetadata meta = new ChatMessageMetadata();
                meta.setMessageId(messageId);
                meta.setMetaKey(e.getKey());
                meta.setValue(serializeValue(e.getValue()));
                metaList.add(meta);
            }
            if (!metaList.isEmpty()) {
                metaList.forEach(metadataMapper::insert);
            }
        }
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
            String contentType = String.valueOf(m.getOrDefault("content_type", "text"));
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = m.get("metadata") instanceof Map<?, ?>
                    ? new LinkedHashMap<>((Map<String, Object>) m.get("metadata"))
                    : null;
            if (!content.isBlank()) {
                saveMessage(sessionId, role, content, contentType, metadata);
                count++;
            }
        }
        return count;
    }

    /**
     * 获取会话的所有消息（按排序顺序）。
     */
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getSortOrder));
    }

    /**
     * 获取会话消息及 metadata（供 API 返回 / 前端历史还原）。
     */
    public List<Map<String, Object>> getSessionMessageMaps(String sessionId) {
        List<ChatMessage> messages = getSessionMessages(sessionId);
        if (messages.isEmpty()) {
            return List.of();
        }
        List<String> ids = messages.stream().map(ChatMessage::getMessageId).toList();
        List<ChatMessageMetadata> metas = metadataMapper.selectList(
                new LambdaQueryWrapper<ChatMessageMetadata>().in(ChatMessageMetadata::getMessageId, ids));
        Map<String, Map<String, Object>> metaByMsg = new HashMap<>();
        for (ChatMessageMetadata meta : metas) {
            metaByMsg
                    .computeIfAbsent(meta.getMessageId(), k -> new LinkedHashMap<>())
                    .put(meta.getMetaKey(), deserializeValue(meta.getValue()));
        }

        List<Map<String, Object>> result = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("message_id", m.getMessageId());
            row.put("session_id", m.getSessionId());
            row.put("role", m.getRole());
            row.put("content", m.getContent());
            row.put("content_type", m.getContentType());
            row.put("parent_id", m.getParentId());
            row.put("sort_order", m.getSortOrder());
            row.put("created_at", m.getCreatedAt());
            row.put("metadata", metaByMsg.getOrDefault(m.getMessageId(), Map.of()));
            result.add(row);
        }
        return result;
    }

    /**
     * 获取单条消息的 metadata。
     */
    public Map<String, Object> getMessageMetadata(String messageId) {
        List<ChatMessageMetadata> metas = metadataMapper.selectList(
                new LambdaQueryWrapper<ChatMessageMetadata>().eq(ChatMessageMetadata::getMessageId, messageId));
        Map<String, Object> out = new LinkedHashMap<>();
        for (ChatMessageMetadata meta : metas) {
            out.put(meta.getMetaKey(), deserializeValue(meta.getValue()));
        }
        return out;
    }

    /**
     * 获取会话最近 N 条消息（用于构建 LLM 上下文）。
     */
    public List<Map<String, String>> getRecentMessages(String sessionId, int limit) {
        List<ChatMessage> all = getSessionMessages(sessionId);
        int start = Math.max(0, all.size() - limit);
        return all.subList(start, all.size()).stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent() == null ? "" : m.getContent()))
                .toList();
    }

    /**
     * 获取会话消息数量。
     */
    public long getMessageCount(String sessionId) {
        return messageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
    }

    /**
     * 按关键词搜索消息。
     */
    public List<ChatMessage> searchMessages(String sessionId, String keyword, int limit) {
        if (sessionId != null && !sessionId.isBlank()) {
            return messageMapper.selectPage(
                            new Page<>(1, limit),
                            new LambdaQueryWrapper<ChatMessage>()
                                    .eq(ChatMessage::getSessionId, sessionId)
                                    .like(ChatMessage::getContent, keyword)
                                    .orderByDesc(ChatMessage::getCreatedAt))
                    .getRecords();
        }
        return messageMapper.selectPage(
                        new Page<>(1, limit),
                        new LambdaQueryWrapper<ChatMessage>()
                                .like(ChatMessage::getContent, keyword)
                                .orderByDesc(ChatMessage::getCreatedAt))
                .getRecords();
    }

    private String serializeValue(Object value) {
        if (value == null) return null;
        if (value instanceof String s) {
            // 纯字符串直接存，避免二次 JSON 引号包裹导致前端还原异常
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[ChatPersistence] metadata 序列化失败: {}", e.getMessage());
            return String.valueOf(value);
        }
    }

    private Object deserializeValue(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return value;
        if (trimmed.startsWith("{") || trimmed.startsWith("[")
                || "true".equals(trimmed) || "false".equals(trimmed)
                || trimmed.chars().allMatch(c -> Character.isDigit(c) || c == '-' || c == '.')) {
            try {
                return objectMapper.readValue(value, new TypeReference<>() {});
            } catch (JsonProcessingException ignored) {
                return value;
            }
        }
        return value;
    }
}
