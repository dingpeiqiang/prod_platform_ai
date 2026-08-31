package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.ChatPersistenceService;
import com.sitech.prodai.service.agent.model.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器，维护多轮对话上下文。
 * <p>
 * 管理 SessionContext 的创建、获取、更新、过期。
 * <p>
 * 历史会话快照恢复：内存上下文过期/重启丢失后，若调用方指定了 sessionId
 * （如前端切换历史会话），从持久化消息快照重建上下文，保证后续轮次
 * 仍能续接该会话的真实历史（历史/证据缓存/已澄清参数），而非另起新会话。
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    /** 会话过期时间：30 分钟 */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    /** 快照恢复时最多回放的历史消息条数（防止超长会话撑爆 Prompt） */
    private static final int RESTORE_HISTORY_LIMIT = 20;

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final Optional<ChatPersistenceService> persistenceService;

    public SessionManager(Optional<ChatPersistenceService> persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * 获取或创建会话。
     * <p>
     * 指定 sessionId 且内存中不存在（过期/重启）时：优先从持久化快照恢复该会话，
     * 保证「历史会话 = 实际会话快照」；仅在无任何快照时才生成新 ID。
     *
     * @param sessionId 会话 ID，null 时创建新会话
     * @return 会话上下文
     */
    public SessionContext getOrCreate(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            SessionEntry entry = sessions.get(sessionId);
            if (entry != null && !isExpired(entry)) {
                entry.lastAccessTime = System.currentTimeMillis();
                return entry.context;
            }
            // 内存无该会话（首次访问历史会话/过期/重启）：从持久化快照恢复，保持会话 ID 不变
            SessionContext restored = restoreFromPersistence(sessionId);
            if (restored != null) {
                sessions.put(sessionId, new SessionEntry(restored));
                log.info("[SessionManager] 从持久化快照恢复会话: {}", sessionId);
                return restored;
            }
        }

        // 创建新会话
        String newId = sessionId != null && !sessionId.isBlank() ? sessionId : generateSessionId();
        SessionContext context = new SessionContext(newId);
        sessions.put(newId, new SessionEntry(context));
        log.info("[SessionManager] 创建新会话: {}", newId);
        return context;
    }

    /**
     * 从持久化消息快照重建会话上下文（历史 + 最近用户/助手轮次 + 上下文状态）。
     * 无持久化或该会话无消息时返回 null（交由调用方新建）。
     * <p>
     * 除 history 外，还从最近一条助手消息的 metadata 反推会话状态
     * （lastIntent / lastTools / lastParams / resolvedParams / cachedEvidence），
     * 保证历史会话续接时「继续提问」仍能拿到上一轮意图/参数/证据（如 evidence:offering），
     * 而不是退化为无上下文的新会话。
     */
    private SessionContext restoreFromPersistence(String sessionId) {
        try {
            if (persistenceService.isEmpty()) {
                return null;
            }
            ChatPersistenceService svc = persistenceService.get();
            var recent = svc.getRecentMessages(sessionId, RESTORE_HISTORY_LIMIT);
            if (recent == null || recent.isEmpty()) {
                return null;
            }
            SessionContext context = new SessionContext(sessionId);
            for (Map<String, String> entry : recent) {
                String role = entry.getOrDefault("role", "user");
                String content = entry.getOrDefault("content", "");
                if (!content.isBlank()) {
                    context.addHistoryEntry(role, content);
                }
            }
            restoreContextFromMetadata(svc, sessionId, context);
            return context;
        } catch (Exception e) {
            log.warn("[SessionManager] 会话快照恢复失败（按新会话处理）: sessionId={}, error={}",
                    sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * 从最近一条助手消息的 metadata 恢复会话上下文状态。
     * <p>
     * persistTurn 落库的 query_plan（intent/tools/params）与 tool_results（工具输出）
     * 是编排层上下文的持久化投影：恢复后续接提问时，理解层可复用 resolvedParams
     * （已澄清参数）、执行层可复用 cachedEvidence（evidence: 来源解析）。
     * 恢复失败不影响 history（仅退化为无上下文续接）。
     */
    private void restoreContextFromMetadata(ChatPersistenceService svc, String sessionId, SessionContext context) {
        try {
            var maps = svc.getSessionMessageMaps(sessionId);
            if (maps == null || maps.isEmpty()) {
                return;
            }
            // 从最近一条带 query_plan metadata 的助手消息反推上下文（倒序找）
            for (int i = maps.size() - 1; i >= 0; i--) {
                Map<String, Object> row = maps.get(i);
                if (!"assistant".equals(row.get("role"))) {
                    continue;
                }
                Object metaObj = row.get("metadata");
                if (!(metaObj instanceof Map<?, ?> meta)) {
                    continue;
                }
                Object planObj = meta.get("query_plan");
                if (planObj instanceof Map<?, ?> plan) {
                    Object intentVal = plan.get("intent");
                    context.setLastIntent(intentVal != null ? String.valueOf(intentVal) : "");
                    if (plan.get("tools") instanceof List<?> tools) {
                        context.setLastTools(tools.stream().map(String::valueOf).toList());
                    }
                    if (plan.get("params") instanceof Map<?, ?> params) {
                        Map<String, Object> copied = new java.util.LinkedHashMap<>();
                        params.forEach((k, v) -> copied.put(String.valueOf(k), v));
                        context.setLastParams(copied);
                        // 已澄清参数同步恢复：resolvedParams 与 lastParams 同源（persistTurn 时 params
                        // 已含 applySuppliedParams 合并结果），续接提问时理解层从 resolvedParams 补齐
                        context.getResolvedParams().putAll(copied);
                    }
                }
                // 工具输出恢复为证据缓存：key=工具名（与编排层 cacheEvidence 一致），
                // 续接追问时执行层 evidence:<tool> 来源解析可直接命中，避免重复查询
                Object resultsObj = meta.get("tool_results");
                if (resultsObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> t
                                && t.get("name") != null
                                && "done".equals(String.valueOf(t.get("status")))
                                && t.get("output") != null) {
                            context.cacheEvidence(String.valueOf(t.get("name")), t.get("output"));
                        }
                    }
                }
                break;
            }
        } catch (Exception e) {
            log.warn("[SessionManager] 会话上下文 metadata 恢复失败（仅恢复历史）: sessionId={}, error={}",
                    sessionId, e.getMessage());
        }
    }

    /**
     * 获取会话上下文，不存在时返回 null。
     */
    public SessionContext get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        SessionEntry entry = sessions.get(sessionId);
        if (entry == null || isExpired(entry)) {
            if (entry != null) {
                sessions.remove(sessionId);
                log.info("[SessionManager] 会话过期已移除: {}", sessionId);
            }
            return null;
        }
        entry.lastAccessTime = System.currentTimeMillis();
        return entry.context;
    }

    /**
     * 保存会话上下文。
     */
    public void save(SessionContext context) {
        if (context == null || context.getSessionId() == null) {
            return;
        }
        SessionEntry entry = sessions.get(context.getSessionId());
        if (entry == null) {
            entry = new SessionEntry(context);
            sessions.put(context.getSessionId(), entry);
        } else {
            entry.context = context;
            entry.lastAccessTime = System.currentTimeMillis();
        }
    }

    /**
     * 清理过期会话。
     */
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry ->
                now - entry.getValue().lastAccessTime > SESSION_TTL_MS);
    }

    private boolean isExpired(SessionEntry entry) {
        return System.currentTimeMillis() - entry.lastAccessTime > SESSION_TTL_MS;
    }

    private String generateSessionId() {
        return "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static class SessionEntry {
        SessionContext context;
        long lastAccessTime;

        SessionEntry(SessionContext context) {
            this.context = context;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}