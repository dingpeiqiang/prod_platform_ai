package com.sitech.prodai.controller;

import com.sitech.prodai.service.ChatV2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 *
 * <p>Session / Message CRUD 统一委托给 {@link ChatV2Service}（数据库持久化，
 * H2/MySQL 同构表），文件上传由 ChatV2Service 处理并落盘 uploads 目录。
 */
@Tag(name = "对话 v2", description = "会话/消息 CRUD（数据库持久化）与文件上传下载，对齐前端 chatApi.js（/api/v2/chat/*）")
@RestController
@RequestMapping("/api/v2/chat")
public class ChatV2Controller {

    private final ChatV2Service chatV2Service;

    public ChatV2Controller(ChatV2Service chatV2Service) {
        this.chatV2Service = chatV2Service;
    }

    // ── Session CRUD ──────────────────────────────────

    @Operation(summary = "会话列表", description = "按用户/状态查询会话列表（默认最多 50 条）")
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

    @Operation(summary = "创建会话", description = "新建会话（user_id 缺省为 default，可携带上下文标签与元数据）")
    @PostMapping("/sessions")
    public Map<String, Object> createSession(@RequestBody Map<String, Object> request) {
        String userId = str(request.get("user_id"));
        String title = str(request.get("title"));
        if (userId == null || userId.isBlank()) userId = "default";
        return chatV2Service.createSession(userId, title, castStringList(request.get("context_tags")),
                castMap(request.get("metadata")));
    }

    @Operation(summary = "会话详情", description = "查询单个会话信息，不存在返回 404")
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        Map<String, Object> session = chatV2Service.getSession(sessionId);
        return session == null
                ? ResponseEntity.status(404).body(Map.of("error", "会话不存在"))
                : ResponseEntity.ok(session);
    }

    @Operation(summary = "更新会话", description = "修改会话标题、上下文标签、元数据或状态")
    @PatchMapping("/sessions/{sessionId}")
    public ResponseEntity<?> updateSession(@PathVariable String sessionId,
                                           @RequestBody Map<String, Object> request) {
        Map<String, Object> session = chatV2Service.updateSession(sessionId,
                str(request.get("title")),
                castStringList(request.get("context_tags")),
                castMap(request.get("metadata")),
                str(request.get("status")));
        return session == null
                ? ResponseEntity.status(404).body(Map.of("error", "更新失败"))
                : ResponseEntity.ok(session);
    }

    @Operation(summary = "删除会话", description = "删除指定会话及其消息")
    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        return Map.of("success", chatV2Service.deleteSession(sessionId));
    }

    // ── Message CRUD ──────────────────────────────────

    @Operation(summary = "消息列表", description = "查询会话内消息（支持时间范围过滤与是否含元数据）")
    @GetMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> listMessages(
            @PathVariable String sessionId,
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @RequestParam(value = "before_ts", required = false) String beforeTs,
            @RequestParam(value = "after_ts", required = false) String afterTs,
            @RequestParam(value = "include_metadata", defaultValue = "true") boolean includeMetadata) {
        return chatV2Service.listMessages(sessionId, limit, beforeTs, afterTs, includeMetadata);
    }

    @Operation(summary = "保存消息", description = "向会话写入一条消息（content 为空时跳过）")
    @PostMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> createMessage(@PathVariable String sessionId,
                                             @RequestBody Map<String, Object> request) {
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

    @Operation(summary = "批量保存消息", description = "向会话批量写入消息列表")
    @PostMapping("/sessions/{sessionId}/messages/batch")
    public Map<String, Object> createMessagesBatch(@PathVariable String sessionId,
                                                   @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        return chatV2Service.saveMessagesBatch(sessionId, messages);
    }

    @Operation(summary = "消息详情", description = "查询单条消息，不存在或不属于该会话返回 404")
    @GetMapping("/sessions/{sessionId}/messages/{messageId}")
    public ResponseEntity<?> getMessage(@PathVariable String sessionId,
                                        @PathVariable String messageId) {
        Map<String, Object> message = chatV2Service.getMessage(messageId);
        if (message == null || !sessionId.equals(message.get("session_id"))) {
            return ResponseEntity.status(404).body(Map.of("error", "消息不存在"));
        }
        return ResponseEntity.ok(message);
    }

    @Operation(summary = "更新消息", description = "修改消息内容或元数据")
    @PatchMapping("/sessions/{sessionId}/messages/{messageId}")
    public Map<String, Object> updateMessage(@PathVariable String sessionId,
                                             @PathVariable String messageId,
                                             @RequestBody Map<String, Object> request) {
        boolean ok = chatV2Service.updateMessage(messageId,
                request.containsKey("content") ? str(request.get("content")) : null,
                castMap(request.get("metadata")));
        return Map.of("success", ok);
    }

    @Operation(summary = "删除消息", description = "删除指定消息")
    @DeleteMapping("/sessions/{sessionId}/messages/{messageId}")
    public Map<String, Object> deleteMessage(@PathVariable String sessionId,
                                             @PathVariable String messageId) {
        return Map.of("success", chatV2Service.deleteMessage(messageId));
    }

    // ── 搜索 / 统计 ──────────────────────────────────

    @Operation(summary = "消息检索", description = "全文关键字检索消息，可按用户/会话过滤")
    @GetMapping("/messages/search")
    public Map<String, Object> searchMessages(
            @RequestParam("q") String query,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<Map<String, Object>> results = chatV2Service.searchMessages(query, userId, sessionId, limit);
        return Map.of("results", results, "total", results.size());
    }

    @Operation(summary = "会话统计", description = "返回会话消息数、参与角色等聚合统计")
    @GetMapping("/sessions/{sessionId}/stats")
    public Map<String, Object> sessionStats(@PathVariable String sessionId) {
        return chatV2Service.getSessionStats(sessionId);
    }

    // ── 文件上传 ──────────────────────────────────────

    @Operation(summary = "上传文件", description = "multipart 上传文件，落盘 uploads 目录并返回文件标识")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        return chatV2Service.uploadFile(file);
    }

    @Operation(summary = "下载/预览文件", description = "按文件名读取 uploads 目录中的文件（inline 输出）")
    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Path path = chatV2Service.resolveUpload(filename);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
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
