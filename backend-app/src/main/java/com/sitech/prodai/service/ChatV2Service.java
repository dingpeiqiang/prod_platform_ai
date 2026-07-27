package com.sitech.prodai.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Commercial chat v2 in-memory store aligned with /api/v2/chat.
 * Replaceable by DB/external session service without changing API contract.
 */
@Service
public class ChatV2Service {

    private final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> messagesBySession = new ConcurrentHashMap<>();
    private final Path uploadDir;

    public ChatV2Service() {
        this.uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create upload dir: " + uploadDir, e);
        }
    }

    public Map<String, Object> createSession(String userId, String title,
                                             List<String> contextTags,
                                             Map<String, Object> metadata) {
        String sessionId = "sess-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String now = Instant.now().toString();
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("session_id", sessionId);
        session.put("user_id", userId);
        session.put("title", empty(title) ? "新对话" : title);
        session.put("context_tags", contextTags == null ? List.of() : new ArrayList<>(contextTags));
        session.put("metadata", metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        session.put("status", "active");
        session.put("created_at", now);
        session.put("updated_at", now);
        sessions.put(sessionId, session);
        messagesBySession.put(sessionId, new ArrayList<>());
        return copy(session);
    }

    public List<Map<String, Object>> listSessions(String userId, String status, int limit) {
        String safeStatus = empty(status) ? "active" : status;
        return sessions.values().stream()
                .filter(s -> empty(userId) || Objects.equals(userId, s.get("user_id")))
                .filter(s -> "all".equalsIgnoreCase(safeStatus) || safeStatus.equals(str(s.get("status"))))
                .sorted(Comparator.comparing((Map<String, Object> s) -> str(s.get("updated_at"))).reversed())
                .limit(Math.max(1, Math.min(limit, 200)))
                .map(this::copy)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSession(String sessionId) {
        Map<String, Object> session = sessions.get(sessionId);
        return session == null ? null : copy(session);
    }

    public Map<String, Object> updateSession(String sessionId, String title,
                                             List<String> contextTags,
                                             Map<String, Object> metadata,
                                             String status) {
        Map<String, Object> session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        if (!empty(title)) {
            session.put("title", title);
        }
        if (contextTags != null) {
            session.put("context_tags", new ArrayList<>(contextTags));
        }
        if (metadata != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) session.get("metadata");
            Map<String, Object> merged = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
            merged.putAll(metadata);
            session.put("metadata", merged);
        }
        if (!empty(status)) {
            session.put("status", status);
        }
        session.put("updated_at", Instant.now().toString());
        return copy(session);
    }

    public boolean deleteSession(String sessionId) {
        messagesBySession.remove(sessionId);
        return sessions.remove(sessionId) != null;
    }

    public Map<String, Object> saveMessage(String sessionId, String role, String content,
                                           String contentType, Map<String, Object> metadata,
                                           String parentId, String stepType) {
        if (content == null || content.isBlank()) {
            return Map.of("success", false, "skipped", true, "reason", "empty_content");
        }
        ensureSession(sessionId);
        String messageId = "msg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String now = Instant.now().toString();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message_id", messageId);
        message.put("session_id", sessionId);
        message.put("role", role);
        message.put("content", content);
        message.put("content_type", empty(contentType) ? "text" : contentType);
        message.put("parent_id", parentId);
        message.put("step_type", stepType);
        message.put("created_at", now);
        message.put("metadata", metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        messagesBySession.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
        touchSession(sessionId);
        Map<String, Object> result = copy(message);
        result.put("success", true);
        return result;
    }

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
        List<Map<String, Object>> all = messagesBySession.getOrDefault(sessionId, List.of());
        List<Map<String, Object>> filtered = all.stream()
                .filter(m -> beforeTs == null || beforeTs.isBlank() || str(m.get("created_at")).compareTo(beforeTs) < 0)
                .filter(m -> afterTs == null || afterTs.isBlank() || str(m.get("created_at")).compareTo(afterTs) > 0)
                .sorted(Comparator.comparing(m -> str(m.get("created_at"))))
                .collect(Collectors.toList());

        int safeLimit = Math.max(1, Math.min(limit, 500));
        boolean hasMoreBefore = filtered.size() > safeLimit;
        List<Map<String, Object>> page = filtered;
        if (hasMoreBefore) {
            page = filtered.subList(Math.max(0, filtered.size() - safeLimit), filtered.size());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> m : page) {
            Map<String, Object> row = copy(m);
            if (!includeMetadata) {
                row.put("metadata", Map.of());
            }
            rows.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", rows);
        body.put("total", all.size());
        body.put("has_more_before", hasMoreBefore);
        body.put("has_more_after", false);
        return body;
    }

    public Map<String, Object> getMessage(String messageId) {
        for (List<Map<String, Object>> list : messagesBySession.values()) {
            for (Map<String, Object> m : list) {
                if (messageId.equals(m.get("message_id"))) {
                    return copy(m);
                }
            }
        }
        return null;
    }

    public boolean updateMessage(String messageId, String content, Map<String, Object> metadata) {
        for (List<Map<String, Object>> list : messagesBySession.values()) {
            for (Map<String, Object> m : list) {
                if (!messageId.equals(m.get("message_id"))) {
                    continue;
                }
                if (content != null) {
                    m.put("content", content);
                }
                if (metadata != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existing = (Map<String, Object>) m.get("metadata");
                    Map<String, Object> merged = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
                    merged.putAll(metadata);
                    m.put("metadata", merged);
                }
                touchSession(str(m.get("session_id")));
                return true;
            }
        }
        return false;
    }

    public boolean deleteMessage(String messageId) {
        for (Map.Entry<String, List<Map<String, Object>>> entry : messagesBySession.entrySet()) {
            List<Map<String, Object>> list = entry.getValue();
            boolean removed = list.removeIf(m -> messageId.equals(m.get("message_id")));
            if (removed) {
                touchSession(entry.getKey());
                return true;
            }
        }
        return false;
    }

    public List<Map<String, Object>> searchMessages(String query, String userId, String sessionId, int limit) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : messagesBySession.entrySet()) {
            String sid = entry.getKey();
            if (!empty(sessionId) && !sessionId.equals(sid)) {
                continue;
            }
            Map<String, Object> session = sessions.get(sid);
            if (!empty(userId) && session != null && !Objects.equals(userId, session.get("user_id"))) {
                continue;
            }
            for (Map<String, Object> m : entry.getValue()) {
                if (str(m.get("content")).toLowerCase(Locale.ROOT).contains(q)) {
                    results.add(copy(m));
                }
            }
        }
        return results.stream()
                .sorted(Comparator.comparing((Map<String, Object> m) -> str(m.get("created_at"))).reversed())
                .limit(Math.max(1, Math.min(limit, 100)))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSessionStats(String sessionId) {
        List<Map<String, Object>> list = messagesBySession.getOrDefault(sessionId, List.of());
        long userCount = list.stream().filter(m -> "user".equals(m.get("role"))).count();
        long assistantCount = list.stream().filter(m -> "assistant".equals(m.get("role"))).count();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", sessionId);
        body.put("message_count", list.size());
        body.put("user_message_count", userCount);
        body.put("assistant_message_count", assistantCount);
        body.put("exists", sessions.containsKey(sessionId));
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

    private void ensureSession(String sessionId) {
        if (sessions.containsKey(sessionId)) {
            return;
        }
        Map<String, Object> session = new LinkedHashMap<>();
        String now = Instant.now().toString();
        session.put("session_id", sessionId);
        session.put("user_id", null);
        session.put("title", "新对话");
        session.put("context_tags", List.of());
        session.put("metadata", Map.of());
        session.put("status", "active");
        session.put("created_at", now);
        session.put("updated_at", now);
        sessions.put(sessionId, session);
        messagesBySession.putIfAbsent(sessionId, new ArrayList<>());
    }

    private void touchSession(String sessionId) {
        Map<String, Object> session = sessions.get(sessionId);
        if (session != null) {
            session.put("updated_at", Instant.now().toString());
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

    private Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }

    private boolean empty(String value) {
        return value == null || value.isBlank();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
