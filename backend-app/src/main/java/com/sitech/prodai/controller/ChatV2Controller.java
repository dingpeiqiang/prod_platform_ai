package com.sitech.prodai.controller;

import com.sitech.prodai.service.ChatV2Service;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat v2 API aligned with frontend chatApi.js (/api/v2/chat/*).
 * Backed by in-memory ChatV2Service — replaceable with DB later.
 */
@RestController
@RequestMapping("/api/v2/chat")
public class ChatV2Controller {

    private final ChatV2Service chatV2Service;

    public ChatV2Controller(ChatV2Service chatV2Service) {
        this.chatV2Service = chatV2Service;
    }

    @GetMapping("/sessions")
    public Map<String, Object> listSessions(
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "status", defaultValue = "active") String status,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<Map<String, Object>> sessions = chatV2Service.listSessions(userId, status, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessions", sessions);
        body.put("total", sessions.size());
        return body;
    }

    @PostMapping("/sessions")
    public Map<String, Object> createSession(@RequestBody Map<String, Object> request) {
        return chatV2Service.createSession(
                str(request.get("user_id")),
                str(request.get("title")),
                castStringList(request.get("context_tags")),
                castMap(request.get("metadata"))
        );
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        Map<String, Object> session = chatV2Service.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(404).body(Map.of("error", "会话不存在"));
        }
        return ResponseEntity.ok(session);
    }

    @PatchMapping("/sessions/{sessionId}")
    public ResponseEntity<?> updateSession(@PathVariable String sessionId,
                                           @RequestBody Map<String, Object> request) {
        Map<String, Object> session = chatV2Service.updateSession(
                sessionId,
                request.get("title") == null ? null : str(request.get("title")),
                castStringList(request.get("context_tags")),
                castMap(request.get("metadata")),
                request.get("status") == null ? null : str(request.get("status"))
        );
        if (session == null) {
            return ResponseEntity.status(404).body(Map.of("error", "更新失败"));
        }
        return ResponseEntity.ok(session);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        return Map.of("success", chatV2Service.deleteSession(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> listMessages(
            @PathVariable String sessionId,
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @RequestParam(value = "before_ts", required = false) String beforeTs,
            @RequestParam(value = "after_ts", required = false) String afterTs,
            @RequestParam(value = "include_metadata", defaultValue = "true") boolean includeMetadata) {
        return chatV2Service.listMessages(sessionId, limit, beforeTs, afterTs, includeMetadata);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> createMessage(@PathVariable String sessionId,
                                             @RequestBody Map<String, Object> request) {
        return chatV2Service.saveMessage(
                sessionId,
                str(request.get("role")),
                str(request.get("content")),
                str(request.getOrDefault("content_type", "text")),
                castMap(request.get("metadata")),
                request.get("parent_id") == null ? null : str(request.get("parent_id")),
                request.get("step_type") == null ? null : str(request.get("step_type"))
        );
    }

    @PostMapping("/sessions/{sessionId}/messages/batch")
    public Map<String, Object> createMessagesBatch(@PathVariable String sessionId,
                                                   @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        return chatV2Service.saveMessagesBatch(sessionId, messages);
    }

    @GetMapping("/sessions/{sessionId}/messages/{messageId}")
    public ResponseEntity<?> getMessage(@PathVariable String sessionId,
                                        @PathVariable String messageId) {
        Map<String, Object> message = chatV2Service.getMessage(messageId);
        if (message == null) {
            return ResponseEntity.status(404).body(Map.of("error", "消息不存在"));
        }
        return ResponseEntity.ok(message);
    }

    @PatchMapping("/sessions/{sessionId}/messages/{messageId}")
    public Map<String, Object> updateMessage(@PathVariable String sessionId,
                                             @PathVariable String messageId,
                                             @RequestBody Map<String, Object> request) {
        boolean ok = chatV2Service.updateMessage(
                messageId,
                request.containsKey("content") ? str(request.get("content")) : null,
                castMap(request.get("metadata"))
        );
        return Map.of("success", ok);
    }

    @DeleteMapping("/sessions/{sessionId}/messages/{messageId}")
    public Map<String, Object> deleteMessage(@PathVariable String sessionId,
                                             @PathVariable String messageId) {
        return Map.of("success", chatV2Service.deleteMessage(messageId));
    }

    @GetMapping("/messages/search")
    public Map<String, Object> searchMessages(
            @RequestParam("q") String query,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<Map<String, Object>> results = chatV2Service.searchMessages(query, userId, sessionId, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("results", results);
        body.put("total", results.size());
        return body;
    }

    @GetMapping("/sessions/{sessionId}/stats")
    public Map<String, Object> sessionStats(@PathVariable String sessionId) {
        return chatV2Service.getSessionStats(sessionId);
    }

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
