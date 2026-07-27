package com.sitech.prodai.controller;

import com.sitech.prodai.domain.entity.ChatMessage;
import com.sitech.prodai.domain.entity.ChatSession;
import com.sitech.prodai.service.ChatPersistenceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话历史 API —— 前端 Sidebar 加载/管理对话历史。
 */
@RestController
@RequestMapping("/api/v1/chat/history")
public class ChatHistoryController {

    private final Optional<ChatPersistenceService> persistenceService;

    public ChatHistoryController(Optional<ChatPersistenceService> persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * 获取用户最近的会话列表。
     */
    @GetMapping("/sessions")
    public Map<String, Object> listSessions(
            @RequestParam(defaultValue = "default") String userId,
            @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (persistenceService.isEmpty()) {
            body.put("success", false);
            body.put("message", "对话持久化未启用");
            body.put("sessions", List.of());
            return body;
        }
        List<ChatSession> sessions = persistenceService.get().getRecentSessions(userId, limit);
        body.put("success", true);
        body.put("sessions", sessions.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getSessionId());
            m.put("title", s.getTitle());
            m.put("status", s.getStatus());
            m.put("createdAt", s.getCreatedAt());
            m.put("updatedAt", s.getUpdatedAt());
            return m;
        }).toList());
        return body;
    }

    /**
     * 获取会话的所有消息。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> getSessionMessages(@PathVariable String sessionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (persistenceService.isEmpty()) {
            body.put("success", false);
            body.put("messages", List.of());
            return body;
        }
        List<Map<String, Object>> messages = persistenceService.get().getSessionMessageMaps(sessionId);
        body.put("success", true);
        body.put("messages", messages.stream().map(m -> {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("messageId", m.get("message_id"));
            msg.put("role", m.get("role"));
            msg.put("content", m.get("content"));
            msg.put("contentType", m.get("content_type"));
            msg.put("createdAt", m.get("created_at"));
            msg.put("metadata", m.get("metadata"));
            return msg;
        }).toList());
        return body;
    }

    /**
     * 更新会话标题。
     */
    @PatchMapping("/sessions/{sessionId}/title")
    public Map<String, Object> updateTitle(@PathVariable String sessionId,
                                           @RequestBody Map<String, Object> body) {
        String title = String.valueOf(body.getOrDefault("title", "新对话"));
        persistenceService.ifPresent(svc -> svc.updateSessionTitle(sessionId, title));
        return Map.of("success", true, "message", "标题已更新");
    }

    /**
     * 删除会话（归档）。
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        persistenceService.ifPresent(svc -> svc.archiveSession(sessionId));
        return Map.of("success", true, "message", "会话已删除");
    }

    /**
     * 搜索消息。
     */
    @GetMapping("/search")
    public Map<String, Object> searchMessages(
            @RequestParam(required = false) String sessionId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (persistenceService.isEmpty()) {
            body.put("success", false);
            body.put("messages", List.of());
            return body;
        }
        List<ChatMessage> messages = persistenceService.get().searchMessages(sessionId, keyword, limit);
        body.put("success", true);
        body.put("messages", messages.stream().map(m -> {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("messageId", m.getMessageId());
            msg.put("sessionId", m.getSessionId());
            msg.put("role", m.getRole());
            msg.put("content", m.getContent());
            msg.put("createdAt", m.getCreatedAt());
            return msg;
        }).toList());
        return body;
    }
}
