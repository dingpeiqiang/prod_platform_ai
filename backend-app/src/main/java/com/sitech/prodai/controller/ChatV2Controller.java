package com.sitech.prodai.controller;

import com.sitech.prodai.domain.entity.ChatMessage;
import com.sitech.prodai.domain.entity.ChatSession;
import com.sitech.prodai.service.ChatPersistenceService;
import com.sitech.prodai.service.ChatV2Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chat v2 API aligned with frontend chatApi.js (/api/v2/chat/*).
 *
 * <p>Session / Message CRUD 委托给 {@link ChatPersistenceService}（JPA），
 * 文件上传仍由 {@link ChatV2Service}（内存）处理。
 */
@RestController
@RequestMapping("/api/v2/chat")
public class ChatV2Controller {

    private static final Logger log = LoggerFactory.getLogger(ChatV2Controller.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ChatV2Service chatV2Service;
    private final Optional<ChatPersistenceService> persistenceService;

    public ChatV2Controller(ChatV2Service chatV2Service,
                            Optional<ChatPersistenceService> persistenceService) {
        this.chatV2Service = chatV2Service;
        this.persistenceService = persistenceService;
        log.info("[ChatV2Controller] initialized, persistence={}", persistenceService.isPresent() ? "JPA" : "in-memory");
    }

    // ── Session CRUD (JPA) ────────────────────────────

    @GetMapping("/sessions")
    public Map<String, Object> listSessions(
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "status", defaultValue = "active") String status,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (persistenceService.isEmpty()) {
            return Map.of("sessions", List.of(), "total", 0);
        }
        String safeUserId = (userId == null || userId.isBlank()) ? "default" : userId;
        List<ChatSession> sessions = persistenceService.get().getRecentSessions(safeUserId, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessions", sessions.stream().map(this::sessionToMap).toList());
        body.put("total", sessions.size());
        return body;
    }

    @PostMapping("/sessions")
    public Map<String, Object> createSession(@RequestBody Map<String, Object> request) {
        String userId = str(request.get("user_id"));
        String title = str(request.get("title"));
        if (userId == null || userId.isBlank()) userId = "default";

        if (persistenceService.isPresent()) {
            String sessionId = "sess-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            ChatSession session = persistenceService.get().getOrCreateSession(sessionId, userId,
                    title != null && !title.isBlank() ? title : "新对话");
            return sessionToMap(session);
        }

        // 降级到内存
        return chatV2Service.createSession(userId, title, null, null);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        if (persistenceService.isPresent()) {
            return persistenceService.get().getSessionMessages(sessionId).isEmpty()
                    ? ResponseEntity.status(404).body(Map.of("error", "会话不存在"))
                    : ResponseEntity.ok(Map.of("session_id", sessionId, "exists", true));
        }
        Map<String, Object> session = chatV2Service.getSession(sessionId);
        return session == null
                ? ResponseEntity.status(404).body(Map.of("error", "会话不存在"))
                : ResponseEntity.ok(session);
    }

    @PatchMapping("/sessions/{sessionId}")
    public ResponseEntity<?> updateSession(@PathVariable String sessionId,
                                           @RequestBody Map<String, Object> request) {
        if (persistenceService.isPresent()) {
            String title = str(request.get("title"));
            if (title != null) {
                persistenceService.get().updateSessionTitle(sessionId, title);
            }
            String status = str(request.get("status"));
            if ("archived".equals(status)) {
                persistenceService.get().archiveSession(sessionId);
            }
            return ResponseEntity.ok(Map.of("success", true, "session_id", sessionId));
        }
        Map<String, Object> session = chatV2Service.updateSession(sessionId,
                str(request.get("title")), null, null, str(request.get("status")));
        return session == null
                ? ResponseEntity.status(404).body(Map.of("error", "更新失败"))
                : ResponseEntity.ok(session);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        if (persistenceService.isPresent()) {
            persistenceService.get().archiveSession(sessionId);
            return Map.of("success", true);
        }
        return Map.of("success", chatV2Service.deleteSession(sessionId));
    }

    // ── Message CRUD (JPA) ────────────────────────────

    @GetMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> listMessages(
            @PathVariable String sessionId,
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @RequestParam(value = "before_ts", required = false) String beforeTs,
            @RequestParam(value = "after_ts", required = false) String afterTs,
            @RequestParam(value = "include_metadata", defaultValue = "true") boolean includeMetadata) {
        if (persistenceService.isPresent()) {
            List<Map<String, Object>> messages = persistenceService.get().getSessionMessageMaps(sessionId);
            if (messages.size() > limit) {
                messages = messages.subList(Math.max(0, messages.size() - limit), messages.size());
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messages", messages);
            body.put("total", messages.size());
            body.put("has_more_before", false);
            body.put("has_more_after", false);
            return body;
        }
        return chatV2Service.listMessages(sessionId, limit, beforeTs, afterTs, includeMetadata);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> createMessage(@PathVariable String sessionId,
                                             @RequestBody Map<String, Object> request) {
        if (persistenceService.isPresent()) {
            String role = str(request.get("role"));
            String content = str(request.get("content"));
            String contentType = str(request.getOrDefault("content_type", "text"));
            if (content == null || content.isBlank()) {
                return Map.of("success", false, "skipped", true, "reason", "empty_content");
            }
            // 首条有效消息时再确保会话存在，避免空会话进入历史
            persistenceService.get().getOrCreateSession(sessionId, "default",
                    content.length() > 50 ? content.substring(0, 50) : content);
            ChatMessage msg = persistenceService.get().saveMessage(
                    sessionId, role, content, contentType, castMap(request.get("metadata")));
            if (msg == null) {
                return Map.of("success", false, "skipped", true, "reason", "empty_content");
            }
            Map<String, Object> result = messageToMap(msg);
            Map<String, Object> meta = persistenceService.get().getMessageMetadata(msg.getMessageId());
            result.put("metadata", meta);
            result.put("success", true);
            return result;
        }
        String content = str(request.get("content"));
        if (content == null || content.isBlank()) {
            return Map.of("success", false, "skipped", true, "reason", "empty_content");
        }
        return chatV2Service.saveMessage(sessionId,
                str(request.get("role")),
                content,
                str(request.getOrDefault("content_type", "text")),
                castMap(request.get("metadata")),
                request.get("parent_id") == null ? null : str(request.get("parent_id")),
                request.get("step_type") == null ? null : str(request.get("step_type")));
    }

    @PostMapping("/sessions/{sessionId}/messages/batch")
    public Map<String, Object> createMessagesBatch(@PathVariable String sessionId,
                                                   @RequestBody Map<String, Object> request) {
        if (persistenceService.isPresent()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
            int count = persistenceService.get().saveMessages(sessionId, messages);
            return Map.of("success", true, "count", count);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        return chatV2Service.saveMessagesBatch(sessionId, messages);
    }

    @GetMapping("/sessions/{sessionId}/messages/{messageId}")
    public ResponseEntity<?> getMessage(@PathVariable String sessionId,
                                        @PathVariable String messageId) {
        if (persistenceService.isPresent()) {
            List<ChatMessage> all = persistenceService.get().getSessionMessages(sessionId);
            return all.stream()
                    .filter(m -> m.getMessageId().equals(messageId))
                    .findFirst()
                    .map(m -> ResponseEntity.ok((Object) messageToMap(m)))
                    .orElse(ResponseEntity.status(404).body(Map.of("error", "消息不存在")));
        }
        Map<String, Object> message = chatV2Service.getMessage(messageId);
        return message == null
                ? ResponseEntity.status(404).body(Map.of("error", "消息不存在"))
                : ResponseEntity.ok(message);
    }

    @PatchMapping("/sessions/{sessionId}/messages/{messageId}")
    public Map<String, Object> updateMessage(@PathVariable String sessionId,
                                             @PathVariable String messageId,
                                             @RequestBody Map<String, Object> request) {
        if (persistenceService.isPresent()) {
            return Map.of("success", false, "message", "JPA 模式暂不支持消息更新");
        }
        boolean ok = chatV2Service.updateMessage(messageId,
                request.containsKey("content") ? str(request.get("content")) : null,
                castMap(request.get("metadata")));
        return Map.of("success", ok);
    }

    @DeleteMapping("/sessions/{sessionId}/messages/{messageId}")
    public Map<String, Object> deleteMessage(@PathVariable String sessionId,
                                             @PathVariable String messageId) {
        if (persistenceService.isPresent()) {
            return Map.of("success", false, "message", "JPA 模式暂不支持消息删除");
        }
        return Map.of("success", chatV2Service.deleteMessage(messageId));
    }

    // ── 搜索 / 统计 ──────────────────────────────────

    @GetMapping("/messages/search")
    public Map<String, Object> searchMessages(
            @RequestParam("q") String query,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (persistenceService.isPresent()) {
            List<ChatMessage> results = persistenceService.get().searchMessages(sessionId, query, limit);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("results", results.stream().map(this::messageToMap).toList());
            body.put("total", results.size());
            return body;
        }
        List<Map<String, Object>> results = chatV2Service.searchMessages(query, userId, sessionId, limit);
        return Map.of("results", results, "total", results.size());
    }

    @GetMapping("/sessions/{sessionId}/stats")
    public Map<String, Object> sessionStats(@PathVariable String sessionId) {
        if (persistenceService.isPresent()) {
            long count = persistenceService.get().getMessageCount(sessionId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("session_id", sessionId);
            body.put("message_count", count);
            body.put("exists", count > 0);
            return body;
        }
        return chatV2Service.getSessionStats(sessionId);
    }

    // ── 文件上传（仍使用内存服务）──────────────────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        return chatV2Service.uploadFile(file);
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Path path = chatV2Service.resolveUpload(filename);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    // ── 实体 → Map 转换 ──────────────────────────────

    private Map<String, Object> sessionToMap(ChatSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("session_id", s.getSessionId());
        m.put("user_id", s.getUserId());
        m.put("title", s.getTitle());
        m.put("status", s.getStatus());
        m.put("created_at", s.getCreatedAt() != null ? s.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString() : null);
        m.put("updated_at", s.getUpdatedAt() != null ? s.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toString() : null);
        return m;
    }

    private Map<String, Object> messageToMap(ChatMessage m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message_id", m.getMessageId());
        out.put("session_id", m.getSessionId());
        out.put("role", m.getRole());
        out.put("content", m.getContent());
        out.put("content_type", m.getContentType());
        out.put("parent_id", m.getParentId());
        out.put("step_type", null);
        out.put("created_at", m.getCreatedAt() != null ? m.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString() : null);
        Map<String, Object> meta = Map.of();
        if (persistenceService.isPresent()) {
            meta = persistenceService.get().getMessageMetadata(m.getMessageId());
        }
        out.put("metadata", meta);
        return out;
    }

    // ── 工具方法 ──────────────────────────────────────

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return null;
    }
}
