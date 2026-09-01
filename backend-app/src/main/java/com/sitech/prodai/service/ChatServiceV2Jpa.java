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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通用聊天服务（DB 持久化版） —— 对齐 Python {@code app/services/chat_service_v2.py::ChatServiceV2}。
 *
 * <p>与业务完全解耦：所有业务特定字段（intent_type / form_code / extracted_fields）
 * 均通过 {@link ChatMessageMetadata} KV 扩展表存储，不污染核心消息表结构。
 *
 * <p>此服务与现有内存版 {@link ChatV2Service} 并存：
 * <ul>
 *   <li>本服务面向需要持久化的场景（按 sessionId 跨重启恢复对话）</li>
 *   <li>{@link ChatV2Service} 面向临时会话或测试场景</li>
 * </ul>
 */
@Service
public class ChatServiceV2Jpa {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceV2Jpa.class);
    private static final DateTimeFormatter TITLE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatMessageMetadataMapper metadataMapper;
    private final ObjectMapper objectMapper;

    public ChatServiceV2Jpa(ChatSessionMapper sessionMapper,
                            ChatMessageMapper messageMapper,
                            ChatMessageMetadataMapper metadataMapper,
                            ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.metadataMapper = metadataMapper;
        this.objectMapper = objectMapper;
    }

    // ==================== 会话操作 ====================

    /** 对齐 Python create_session */
    @Transactional
    public Map<String, Object> createSession(String userId, String title,
                                              List<String> contextTags,
                                              Map<String, Object> metadata) {
        try {
            String sessionId = UUID.randomUUID().toString();
            ChatSession session = new ChatSession();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setTitle(title == null || title.isBlank()
                    ? "新会话 " + LocalDateTime.now().format(TITLE_FMT)
                    : title);
            session.setContextTags(contextTags == null ? List.of() : new ArrayList<>(contextTags));
            session.setSessionMetadata(metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
            session.setStatus("active");
            sessionMapper.insert(session);
            log.info("[ChatServiceV2Jpa] 创建会话 session_id={} user_id={}", sessionId, userId);
            return sessionToDict(session);
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 创建会话失败 user_id={}", userId, e);
            return null;
        }
    }

    /** 对齐 Python get_sessions */
    public List<Map<String, Object>> getSessions(String userId, String status, int limit) {
        try {
            int safeLimit = Math.max(1, Math.min(limit, 200));
            List<ChatSession> sessions;
            if (userId != null && !userId.isEmpty() && status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
                sessions = sessionMapper.selectPage(
                                new Page<>(1, safeLimit),
                                new LambdaQueryWrapper<ChatSession>()
                                        .eq(ChatSession::getUserId, userId)
                                        .eq(ChatSession::getStatus, status)
                                        .orderByDesc(ChatSession::getUpdatedAt))
                        .getRecords();
            } else if (userId != null && !userId.isEmpty()) {
                sessions = sessionMapper.selectPage(
                                new Page<>(1, safeLimit),
                                new LambdaQueryWrapper<ChatSession>()
                                        .eq(ChatSession::getUserId, userId)
                                        .orderByDesc(ChatSession::getCreatedAt))
                        .getRecords();
            } else if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
                sessions = sessionMapper.selectPage(
                                new Page<>(1, safeLimit),
                                new LambdaQueryWrapper<ChatSession>()
                                        .eq(ChatSession::getStatus, status)
                                        .orderByDesc(ChatSession::getUpdatedAt))
                        .getRecords();
            } else {
                sessions = sessionMapper.selectPage(
                                new Page<>(1, safeLimit),
                                new LambdaQueryWrapper<ChatSession>().orderByDesc(ChatSession::getCreatedAt))
                        .getRecords();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (ChatSession s : sessions) {
                result.add(sessionToDict(s));
            }
            return result;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 查询会话列表失败", e);
            return List.of();
        }
    }

    /** 对齐 Python get_session */
    public Map<String, Object> getSession(String sessionId) {
        try {
            ChatSession session = sessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
            return session == null ? null : sessionToDict(session);
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 查询会话失败", e);
            return null;
        }
    }

    /** 对齐 Python update_session */
    @Transactional
    public boolean updateSession(String sessionId, String title,
                                  List<String> contextTags,
                                  Map<String, Object> metadata,
                                  String status) {
        try {
            ChatSession session = sessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
            if (session == null) {
                return false;
            }
            if (title != null) {
                session.setTitle(title);
            }
            if (contextTags != null) {
                session.setContextTags(new ArrayList<>(contextTags));
            }
            if (metadata != null) {
                session.setSessionMetadata(new LinkedHashMap<>(metadata));
            }
            if (status != null) {
                session.setStatus(status);
            }
            sessionMapper.updateById(session);
            return true;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 更新会话失败 session_id={}", sessionId, e);
            return false;
        }
    }

    /** 对齐 Python delete_session */
    @Transactional
    public boolean deleteSession(String sessionId) {
        try {
            ChatSession session = sessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
            if (session == null) {
                return false;
            }
            // 先删除消息和 metadata（不会自动级联到独立的 metadata 表）
            List<ChatMessage> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, sessionId)
                            .orderByAsc(ChatMessage::getSortOrder));
            for (ChatMessage m : messages) {
                metadataMapper.deleteByMessageId(m.getMessageId());
            }
            messages.forEach(messageMapper::deleteById);
            sessionMapper.deleteById(session.getId());
            log.info("[ChatServiceV2Jpa] 删除会话 session_id={}", sessionId);
            return true;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 删除会话失败 session_id={}", sessionId, e);
            return false;
        }
    }

    // ==================== 消息操作 ====================

    /** 对齐 Python save_message */
    @Transactional
    public Map<String, Object> saveMessage(String sessionId, String role, String content,
                                            String contentType, Map<String, Object> metadata,
                                            String parentId, String userId, String stepType) {
        try {
            if (content == null || content.isBlank()) {
                log.debug("[ChatServiceV2Jpa] 跳过空内容消息 session_id={}, role={}", sessionId, role);
                return Map.of("success", false, "skipped", true, "reason", "empty_content");
            }

            // 确保会话存在（不存在则自动创建）
            ChatSession session = sessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
            if (session == null) {
                session = new ChatSession();
                session.setSessionId(sessionId);
                session.setUserId(userId);
                String title = content.length() > 50 ? content.substring(0, 50) : content;
                session.setTitle(title.isBlank() ? ("会话 " + LocalDateTime.now().format(TITLE_FMT)) : title);
                session.setStatus("active");
                sessionMapper.insert(session);
                log.debug("[ChatServiceV2Jpa] 自动创建会话 session_id={}", sessionId);
            }

            Integer maxSort = messageMapper.findMaxSortOrderBySessionId(sessionId);
            int sortOrder = (maxSort == null ? 0 : maxSort) + 1;

            String messageId = UUID.randomUUID().toString();
            ChatMessage message = new ChatMessage();
            message.setMessageId(messageId);
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content);
            message.setContentType(contentType == null || contentType.isEmpty() ? "text" : contentType);
            message.setParentId(parentId);
            message.setSortOrder(sortOrder);
            messageMapper.insert(message);

            // 写入 metadata
            if (metadata != null && !metadata.isEmpty()) {
                List<ChatMessageMetadata> metaList = new ArrayList<>();
                for (Map.Entry<String, Object> e : metadata.entrySet()) {
                    ChatMessageMetadata meta = new ChatMessageMetadata();
                    meta.setMessageId(messageId);
                    meta.setMetaKey(e.getKey());
                    meta.setValue(serializeValue(e.getValue()));
                    metaList.add(meta);
                }
                metaList.forEach(metadataMapper::insert);
            }
            if (stepType != null && !stepType.isEmpty()) {
                ChatMessageMetadata stepMeta = new ChatMessageMetadata();
                stepMeta.setMessageId(messageId);
                stepMeta.setMetaKey("step_type");
                stepMeta.setValue(stepType);
                metadataMapper.insert(stepMeta);
            }

            // 更新会话 updated_at
            sessionMapper.updateById(session);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message_id", messageId);
            result.put("session_id", sessionId);
            return result;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 保存消息失败", e);
            return null;
        }
    }

    /** 对齐 Python save_step_message */
    @Transactional
    public Map<String, Object> saveStepMessage(String sessionId, String stepType, String content,
                                                String parentId, String userId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("step_type", stepType);
        return saveMessage(sessionId, "system", content, "thinking", metadata, parentId, userId, stepType);
    }

    /** 对齐 Python get_messages */
    public Map<String, Object> getMessages(String sessionId, int limit,
                                            LocalDateTime beforeTs, LocalDateTime afterTs,
                                            boolean includeMetadata) {
        try {
            long total = messageMapper.selectCount(
                    new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
            int safeLimit = Math.max(1, Math.min(limit, 500));
            List<ChatMessage> messages;
            if (beforeTs != null) {
                messages = messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .lt(ChatMessage::getCreatedAt, beforeTs)
                                .orderByAsc(ChatMessage::getSortOrder)
                                .last("LIMIT " + (safeLimit + 1)));
            } else if (afterTs != null) {
                messages = messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .gt(ChatMessage::getCreatedAt, afterTs)
                                .orderByAsc(ChatMessage::getSortOrder)
                                .last("LIMIT " + (safeLimit + 1)));
            } else {
                messages = messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .orderByAsc(ChatMessage::getSortOrder)
                                .last("LIMIT " + (safeLimit + 1)));
            }

            boolean hasMore = messages.size() > safeLimit;
            if (hasMore) {
                messages = messages.subList(0, safeLimit);
            }
            boolean hasMoreBefore = beforeTs != null && hasMore;
            boolean hasMoreAfter;
            if (afterTs != null) {
                hasMoreAfter = hasMore;
            } else if (beforeTs != null) {
                hasMoreAfter = false;
            } else {
                hasMoreAfter = total > messages.size();
            }

            List<Map<String, Object>> resultMessages = new ArrayList<>();
            if (includeMetadata && !messages.isEmpty()) {
                List<String> msgIds = new ArrayList<>();
                for (ChatMessage m : messages) {
                    msgIds.add(m.getMessageId());
                }
                List<ChatMessageMetadata> metas = metadataMapper.selectList(
                        new LambdaQueryWrapper<ChatMessageMetadata>().in(ChatMessageMetadata::getMessageId, msgIds));
                Map<String, List<ChatMessageMetadata>> metaMap = new HashMap<>();
                for (ChatMessageMetadata meta : metas) {
                    metaMap.computeIfAbsent(meta.getMessageId(), k -> new ArrayList<>()).add(meta);
                }
                for (ChatMessage m : messages) {
                    Map<String, Object> d = messageToDict(m, false);
                    Map<String, Object> metadataDict = new LinkedHashMap<>();
                    for (ChatMessageMetadata meta : metaMap.getOrDefault(m.getMessageId(), List.of())) {
                        metadataDict.put(meta.getMetaKey(), deserializeValue(meta.getValue()));
                    }
                    d.put("metadata", metadataDict);
                    resultMessages.add(d);
                }
            } else {
                for (ChatMessage m : messages) {
                    resultMessages.add(messageToDict(m, false));
                }
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messages", resultMessages);
            body.put("total", total);
            body.put("has_more_before", hasMoreBefore);
            body.put("has_more_after", hasMoreAfter);
            return body;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 获取消息失败 session_id={}", sessionId, e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messages", List.of());
            body.put("total", 0);
            body.put("has_more_before", false);
            body.put("has_more_after", false);
            return body;
        }
    }

    /** 对齐 Python get_message */
    public Map<String, Object> getMessage(String messageId) {
        try {
            ChatMessage msg = messageMapper.selectOne(
                    new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getMessageId, messageId));
            if (msg == null) {
                return null;
            }
            Map<String, Object> d = messageToDict(msg, false);
            List<ChatMessageMetadata> metas = metadataMapper.selectList(
                    new LambdaQueryWrapper<ChatMessageMetadata>().eq(ChatMessageMetadata::getMessageId, messageId));
            Map<String, Object> metadataDict = new LinkedHashMap<>();
            for (ChatMessageMetadata meta : metas) {
                metadataDict.put(meta.getMetaKey(), deserializeValue(meta.getValue()));
            }
            d.put("metadata", metadataDict);
            return d;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 获取消息详情失败 msg_id={}", messageId, e);
            return null;
        }
    }

    /** 对齐 Python delete_message */
    @Transactional
    public boolean deleteMessage(String messageId) {
        try {
            ChatMessage msg = messageMapper.selectOne(
                    new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getMessageId, messageId));
            if (msg == null) {
                return false;
            }
            metadataMapper.deleteByMessageId(messageId);
            messageMapper.deleteById(msg.getId());
            return true;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 删除消息失败 msg_id={}", messageId, e);
            return false;
        }
    }

    /** 对齐 Python update_message */
    @Transactional
    public boolean updateMessage(String messageId, String content, Map<String, Object> metadata) {
        try {
            ChatMessage msg = messageMapper.selectOne(
                    new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getMessageId, messageId));
            if (msg == null) {
                log.warn("[ChatServiceV2Jpa] 更新消息失败：消息不存在 msg_id={}", messageId);
                return false;
            }
            if (content != null) {
                msg.setContent(content);
                messageMapper.updateById(msg);
            }
            if (metadata != null) {
                metadataMapper.deleteByMessageId(messageId);
                List<ChatMessageMetadata> metaList = new ArrayList<>();
                for (Map.Entry<String, Object> e : metadata.entrySet()) {
                    ChatMessageMetadata meta = new ChatMessageMetadata();
                    meta.setMessageId(messageId);
                    meta.setMetaKey(e.getKey());
                    meta.setValue(serializeValue(e.getValue()));
                    metaList.add(meta);
                }
                metaList.forEach(metadataMapper::insert);
            }
            return true;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 更新消息失败 msg_id={}", messageId, e);
            return false;
        }
    }

    /** 对齐 Python save_messages_batch */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveMessagesBatch(String sessionId, List<Map<String, Object>> messages,
                                                   String userId) {
        if (messages == null || messages.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("success", false);
            empty.put("count", 0);
            empty.put("message_ids", List.of());
            return empty;
        }
        try {
            // 确保会话存在
            ChatSession session = sessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
            if (session == null) {
                session = new ChatSession();
                session.setSessionId(sessionId);
                session.setUserId(userId);
                session.setTitle("会话 " + LocalDateTime.now().format(TITLE_FMT));
                session.setStatus("active");
                sessionMapper.insert(session);
            }

            Integer maxSort = messageMapper.findMaxSortOrderBySessionId(sessionId);
            int currentSort = maxSort == null ? 0 : maxSort;

            List<ChatMessage> messageObjects = new ArrayList<>();
            List<ChatMessageMetadata> metadataObjects = new ArrayList<>();
            List<String> messageIds = new ArrayList<>();

            for (Map<String, Object> msgData : messages) {
                String messageId = UUID.randomUUID().toString();
                messageIds.add(messageId);
                currentSort++;

                ChatMessage message = new ChatMessage();
                message.setMessageId(messageId);
                message.setSessionId(sessionId);
                message.setRole(str(msgData.getOrDefault("role", "user"), "user"));
                message.setContent(str(msgData.getOrDefault("content", ""), ""));
                message.setContentType(str(msgData.getOrDefault("content_type", "text"), "text"));
                Object parentId = msgData.get("parent_id");
                message.setParentId(parentId == null ? null : String.valueOf(parentId));
                message.setSortOrder(currentSort);
                messageObjects.add(message);

                Object metaObj = msgData.get("metadata");
                if (metaObj instanceof Map<?, ?> metaMap) {
                    for (Map.Entry<?, ?> e : metaMap.entrySet()) {
                        ChatMessageMetadata meta = new ChatMessageMetadata();
                        meta.setMessageId(messageId);
                        meta.setMetaKey(String.valueOf(e.getKey()));
                        meta.setValue(serializeValue(e.getValue()));
                        metadataObjects.add(meta);
                    }
                }
            }

            messageObjects.forEach(messageMapper::insert);
            metadataObjects.forEach(metadataMapper::insert);
            sessionMapper.updateById(session);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("count", messageObjects.size());
            body.put("message_ids", messageIds);
            return body;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 批量保存消息失败 session_id={}", sessionId, e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("count", 0);
            body.put("message_ids", List.of());
            return body;
        }
    }

    // ==================== 搜索 ====================

    /** 对齐 Python search_messages */
    public List<Map<String, Object>> searchMessages(String queryText, String userId,
                                                     String sessionId, int limit) {
        try {
            int safeLimit = Math.max(1, Math.min(limit, 100));
            List<ChatMessage> messages;
            if (sessionId != null && !sessionId.isEmpty()) {
                messages = messageMapper.selectPage(
                                new Page<>(1, safeLimit),
                                new LambdaQueryWrapper<ChatMessage>()
                                        .eq(ChatMessage::getSessionId, sessionId)
                                        .like(ChatMessage::getContent, queryText)
                                        .orderByDesc(ChatMessage::getCreatedAt))
                        .getRecords();
            } else {
                messages = messageMapper.selectPage(
                                new Page<>(1, safeLimit),
                                new LambdaQueryWrapper<ChatMessage>()
                                        .like(ChatMessage::getContent, queryText)
                                        .orderByDesc(ChatMessage::getCreatedAt))
                        .getRecords();
                // 按 userId 过滤
                if (userId != null && !userId.isEmpty()) {
                    Set<String> userSessionIds = new HashSet<>();
                    for (ChatSession s : sessionMapper.selectList(
                            new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getUserId, userId))) {
                        userSessionIds.add(s.getSessionId());
                    }
                    messages = messages.stream()
                            .filter(m -> userSessionIds.contains(m.getSessionId()))
                            .toList();
                }
            }
            List<String> msgIds = new ArrayList<>();
            for (ChatMessage m : messages) {
                msgIds.add(m.getMessageId());
            }
            List<ChatMessageMetadata> metas = msgIds.isEmpty() ? List.of()
                    : metadataMapper.selectList(
                            new LambdaQueryWrapper<ChatMessageMetadata>().in(ChatMessageMetadata::getMessageId, msgIds));
            Map<String, List<ChatMessageMetadata>> metaMap = new HashMap<>();
            for (ChatMessageMetadata meta : metas) {
                metaMap.computeIfAbsent(meta.getMessageId(), k -> new ArrayList<>()).add(meta);
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (ChatMessage m : messages) {
                Map<String, Object> d = messageToDict(m, false);
                Map<String, Object> metadataDict = new LinkedHashMap<>();
                for (ChatMessageMetadata meta : metaMap.getOrDefault(m.getMessageId(), List.of())) {
                    metadataDict.put(meta.getMetaKey(), deserializeValue(meta.getValue()));
                }
                d.put("metadata", metadataDict);
                result.add(d);
            }
            return result;
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 搜索消息失败", e);
            return List.of();
        }
    }

    // ==================== 统计 ====================

    /** 对齐 Python get_session_stats */
    public Map<String, Object> getSessionStats(String sessionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            long total = messageMapper.selectCount(
                    new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
            long userMsgs = messageMapper.selectCount(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, sessionId)
                            .eq(ChatMessage::getRole, "user"));
            long aiMsgs = messageMapper.selectCount(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, sessionId)
                            .eq(ChatMessage::getRole, "assistant"));
            body.put("total", total);
            body.put("user_messages", userMsgs);
            body.put("assistant_messages", aiMsgs);
        } catch (Exception e) {
            log.error("[ChatServiceV2Jpa] 获取统计失败 session_id={}", sessionId, e);
            body.put("total", 0);
            body.put("user_messages", 0);
            body.put("assistant_messages", 0);
        }
        return body;
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> sessionToDict(ChatSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("session_id", s.getSessionId());
        m.put("user_id", s.getUserId());
        m.put("title", s.getTitle());
        m.put("context_tags", s.getContextTags() == null ? List.of() : s.getContextTags());
        m.put("metadata", s.getSessionMetadata() == null ? Map.of() : s.getSessionMetadata());
        m.put("status", s.getStatus());
        m.put("created_at", s.getCreatedAt());
        m.put("updated_at", s.getUpdatedAt());
        return m;
    }

    private Map<String, Object> messageToDict(ChatMessage m, boolean includeMetadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message_id", m.getMessageId());
        result.put("session_id", m.getSessionId());
        result.put("role", m.getRole());
        result.put("content", m.getContent());
        result.put("content_type", m.getContentType());
        result.put("parent_id", m.getParentId());
        result.put("sort_order", m.getSortOrder());
        result.put("created_at", m.getCreatedAt());
        if (includeMetadata) {
            result.put("metadata", Map.of());
        }
        return result;
    }

    private String serializeValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[ChatServiceV2Jpa] 序列化 metadata value 失败: {}", e.getMessage());
            return String.valueOf(value);
        }
    }

    private Object deserializeValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Object>() {
            });
        } catch (JsonProcessingException e) {
            return value;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String str(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value);
        return s.isEmpty() ? defaultValue : s;
    }
}
