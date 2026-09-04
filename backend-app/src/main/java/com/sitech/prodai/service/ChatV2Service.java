package com.sitech.prodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会话 v2 服务 —— 数据库持久化实现（H2/MySQL 同构表 pd_ai_chat_sessions / pd_ai_chat_messages /
 * pd_ai_chat_message_metadata），API 契约与原内存版保持一致。
 * <p>
 * 原内存 ConcurrentHashMap 实现重启丢数据且不支持多实例部署，生产环境必须落库。
 */
@Service
public class ChatV2Service {

    private static final Logger log = LoggerFactory.getLogger(ChatV2Service.class);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_INSTANT;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatMessageMetadataMapper metadataMapper;
    private final ObjectMapper objectMapper;
    private final Path uploadDir;

    public ChatV2Service(ChatSessionMapper sessionMapper,
                         ChatMessageMapper messageMapper,
                         ChatMessageMetadataMapper metadataMapper,
                         ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.metadataMapper = metadataMapper;
        this.objectMapper = objectMapper;
        this.uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create upload dir: " + uploadDir, e);
        }
    }

    @Transactional
    public Map<String, Object> createSession(String userId, String title,
                                             List<String> contextTags,
                                             Map<String, Object> metadata) {
        String sessionId = "sess-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(empty(title) ? "新对话" : title);
        session.setContextTags(contextTags == null ? new ArrayList<>() : new ArrayList<>(contextTags));
        session.setSessionMetadata(metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        session.setStatus("active");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return toSessionMap(session);
    }

    public List<Map<String, Object>> listSessions(String userId, String status, int limit) {
        String safeStatus = empty(status) ? "active" : status;
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(!empty(userId), ChatSession::getUserId, userId)
                .ne(!"all".equalsIgnoreCase(safeStatus), ChatSession::getStatus, "__none__")
                .eq(!"all".equalsIgnoreCase(safeStatus), ChatSession::getStatus, safeStatus)
                .orderByDesc(ChatSession::getUpdatedAt);
        return sessionMapper.selectList(wrapper).stream()
                .limit(Math.max(1, Math.min(limit, 200)))
                .map(this::toSessionMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSession(String sessionId) {
        ChatSession session = findSession(sessionId);
        return session == null ? null : toSessionMap(session);
    }

    @Transactional
    public Map<String, Object> updateSession(String sessionId, String title,
                                             List<String> contextTags,
                                             Map<String, Object> metadata,
                                             String status) {
        ChatSession session = findSession(sessionId);
        if (session == null) {
            return null;
        }
        if (!empty(title)) {
            session.setTitle(title);
        }
        if (contextTags != null) {
            session.setContextTags(new ArrayList<>(contextTags));
        }
        if (metadata != null) {
            Map<String, Object> merged = session.getSessionMetadata() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(session.getSessionMetadata());
            merged.putAll(metadata);
            session.setSessionMetadata(merged);
        }
        if (!empty(status)) {
            session.setStatus(status);
        }
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        return toSessionMap(session);
    }

    @Transactional
    public boolean deleteSession(String sessionId) {
        ChatSession session = findSession(sessionId);
        if (session == null) {
            return false;
        }
        List<ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        for (ChatMessage message : messages) {
            metadataMapper.delete(new LambdaQueryWrapper<ChatMessageMetadata>()
                    .eq(ChatMessageMetadata::getMessageId, message.getMessageId()));
        }
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(session.getId());
        return true;
    }

    @Transactional
    public Map<String, Object> saveMessage(String sessionId, String role, String content,
                                           String contentType, Map<String, Object> metadata,
                                           String parentId, String stepType) {
        if (content == null || content.isBlank()) {
            return Map.of("success", false, "skipped", true, "reason", "empty_content");
        }
        ensureSession(sessionId);
        String messageId = "msg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setContentType(empty(contentType) ? "text" : contentType);
        message.setParentId(parentId);
        message.setSortOrder(0);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        saveMetadata(messageId, "step_type", stepType);
        saveMetadataMap(messageId, metadata);
        touchSession(sessionId);

        Map<String, Object> result = toMessageMap(message);
        result.put("metadata", metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        if (!empty(stepType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) result.get("metadata");
            Map<String, Object> merged = new LinkedHashMap<>(meta);
            merged.putIfAbsent("step_type", stepType);
            result.put("metadata", merged);
        }
        result.put("success", true);
        return result;
    }

    @Transactional
    public Map<String, Object> saveMessagesBatch(String sessionId, List<Map<String, Object>> messages) {
        ensureSession(sessionId);
        List<String> ids = new ArrayList<>();
        if (messages != null) {
            for (Map<String, Object> msg : messages) {
                String content = str(msg.get("content"));
                if (content == null || content.isBlank()) {
                    continue;
                }
                Map<String, Object> saved = saveMessage(
                        sessionId,
                        str(msg.get("role")),
                        content,
                        str(msg.getOrDefault("content_type", "text")),
                        castMap(msg.get("metadata")),
                        msg.get("parent_id") == null ? null : str(msg.get("parent_id")),
                        msg.get("step_type") == null ? null : str(msg.get("step_type"))
                );
                if (saved != null && saved.get("message_id") != null) {
                    ids.add(str(saved.get("message_id")));
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("count", ids.size());
        body.put("message_ids", ids);
        return body;
    }

    public Map<String, Object> listMessages(String sessionId, int limit,
                                            String beforeTs, String afterTs,
                                            boolean includeMetadata) {
        List<ChatMessage> all = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt));

        List<Map<String, Object>> filtered = all.stream()
                .map(m -> Map.entry(m, (includeMetadata ? loadMetadataMap(m.getMessageId()) : Map.<String, Object>of())))
                .filter(e -> beforeTs == null || beforeTs.isBlank()
                        || toInstantStr(e.getKey().getCreatedAt()).compareTo(beforeTs) < 0)
                .filter(e -> afterTs == null || afterTs.isBlank()
                        || toInstantStr(e.getKey().getCreatedAt()).compareTo(afterTs) > 0)
                .map(e -> toMessageMap(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        int safeLimit = Math.max(1, Math.min(limit, 500));
        boolean hasMoreBefore = filtered.size() > safeLimit;
        List<Map<String, Object>> page = filtered;
        if (hasMoreBefore) {
            page = filtered.subList(Math.max(0, filtered.size() - safeLimit), filtered.size());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", page);
        body.put("total", all.size());
        body.put("has_more_before", hasMoreBefore);
        body.put("has_more_after", false);
        return body;
    }

    public Map<String, Object> getMessage(String messageId) {
        ChatMessage message = messageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getMessageId, messageId));
        if (message == null) {
            return null;
        }
        return toMessageMap(message, loadMetadataMap(messageId));
    }

    @Transactional
    public boolean updateMessage(String messageId, String content, Map<String, Object> metadata) {
        ChatMessage message = messageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getMessageId, messageId));
        if (message == null) {
            return false;
        }
        if (content != null) {
            message.setContent(content);
            messageMapper.updateById(message);
        }
        if (metadata != null) {
            Map<String, Object> merged = loadMetadataMap(messageId);
            merged.putAll(metadata);
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                saveMetadata(messageId, entry.getKey(), entry.getValue() == null ? null : strOrJson(entry.getValue()));
            }
        }
        touchSession(message.getSessionId());
        return true;
    }

    @Transactional
    public boolean deleteMessage(String messageId) {
        ChatMessage message = messageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getMessageId, messageId));
        if (message == null) {
            return false;
        }
        metadataMapper.delete(new LambdaQueryWrapper<ChatMessageMetadata>()
                .eq(ChatMessageMetadata::getMessageId, messageId));
        messageMapper.deleteById(message.getId());
        touchSession(message.getSessionId());
        return true;
    }

    public List<Map<String, Object>> searchMessages(String query, String userId, String sessionId, int limit) {
        String q = query == null ? "" : query;
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .like(!q.isBlank(), ChatMessage::getContent, q)
                .eq(!empty(sessionId), ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getCreatedAt);
        List<ChatMessage> candidates = messageMapper.selectList(wrapper);

        List<Map<String, Object>> results = new ArrayList<>();
        for (ChatMessage m : candidates) {
            if (!empty(userId)) {
                ChatSession session = findSession(m.getSessionId());
                if (session == null || !Objects.equals(userId, session.getUserId())) {
                    continue;
                }
            }
            if (!q.isBlank() && !str(m.getContent()).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) {
                continue;
            }
            results.add(toMessageMap(m, loadMetadataMap(m.getMessageId())));
            if (results.size() >= Math.max(1, Math.min(limit, 100))) {
                break;
            }
        }
        return results;
    }

    public Map<String, Object> getSessionStats(String sessionId) {
        List<ChatMessage> list = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        long userCount = list.stream().filter(m -> "user".equals(m.getRole())).count();
        long assistantCount = list.stream().filter(m -> "assistant".equals(m.getRole())).count();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", sessionId);
        body.put("message_count", list.size());
        body.put("user_message_count", userCount);
        body.put("assistant_message_count", assistantCount);
        body.put("exists", findSession(sessionId) != null);
        return body;
    }

    public Map<String, Object> uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }
        String newName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = uploadDir.resolve(newName);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("upload failed: " + e.getMessage(), e);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("filename", original);
        body.put("url", "/api/v2/chat/files/" + newName);
        body.put("size", file.getSize());
        return body;
    }

    public Path resolveUpload(String filename) {
        if (empty(filename) || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("invalid filename");
        }
        Path path = uploadDir.resolve(filename).normalize();
        if (!path.startsWith(uploadDir) || !Files.exists(path)) {
            throw new IllegalArgumentException("file not found");
        }
        return path;
    }

    // ==================== 内部方法 ====================

    private ChatSession findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
    }

    private void ensureSession(String sessionId) {
        if (findSession(sessionId) != null) {
            return;
        }
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(null);
        session.setTitle("新对话");
        session.setContextTags(new ArrayList<>());
        session.setSessionMetadata(Map.of());
        session.setStatus("active");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
    }

    private void touchSession(String sessionId) {
        ChatSession session = findSession(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    private void saveMetadata(String messageId, String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        ChatMessageMetadata meta = new ChatMessageMetadata();
        meta.setMessageId(messageId);
        meta.setMetaKey(key);
        meta.setValue(value);
        meta.setCreatedAt(LocalDateTime.now());
        metadataMapper.insert(meta);
    }

    private void saveMetadataMap(String messageId, Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            saveMetadata(messageId, entry.getKey(), entry.getValue() == null ? null : strOrJson(entry.getValue()));
        }
    }

    private Map<String, Object> loadMetadataMap(String messageId) {
        List<ChatMessageMetadata> rows = metadataMapper.selectList(
                new LambdaQueryWrapper<ChatMessageMetadata>().eq(ChatMessageMetadata::getMessageId, messageId));
        Map<String, Object> map = new LinkedHashMap<>();
        for (ChatMessageMetadata row : rows) {
            map.put(row.getMetaKey(), row.getValue());
        }
        return map;
    }

    private Map<String, Object> toSessionMap(ChatSession session) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("session_id", session.getSessionId());
        map.put("user_id", session.getUserId());
        map.put("title", session.getTitle());
        map.put("context_tags", session.getContextTags() == null ? List.of() : session.getContextTags());
        map.put("metadata", session.getSessionMetadata() == null ? Map.of() : session.getSessionMetadata());
        map.put("status", session.getStatus());
        map.put("created_at", toInstantStr(session.getCreatedAt()));
        map.put("updated_at", toInstantStr(session.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> toMessageMap(ChatMessage message) {
        return toMessageMap(message, Map.of());
    }

    private Map<String, Object> toMessageMap(ChatMessage message, Map<String, Object> metadata) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message_id", message.getMessageId());
        map.put("session_id", message.getSessionId());
        map.put("role", message.getRole());
        map.put("content", message.getContent());
        map.put("content_type", message.getContentType());
        map.put("parent_id", message.getParentId());
        map.put("step_type", metadata.get("step_type"));
        map.put("created_at", toInstantStr(message.getCreatedAt()));
        map.put("metadata", metadata == null ? Map.of() : metadata);
        return map;
    }

    private String toInstantStr(LocalDateTime time) {
        return time == null ? null
                : ISO_FMT.format(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String strOrJson(Object value) {
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return null;
    }

    private boolean empty(String value) {
        return value == null || value.isBlank();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
