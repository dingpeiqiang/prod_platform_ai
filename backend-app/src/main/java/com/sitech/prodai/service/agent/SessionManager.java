package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.model.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器，维护多轮对话上下文。
 * <p>
 * 管理 SessionContext 的创建、获取、更新、过期。
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    /** 会话过期时间：30 分钟 */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    /**
     * 获取或创建会话。
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
        }

        // 创建新会话
        String newId = generateSessionId();
        SessionContext context = new SessionContext(newId);
        sessions.put(newId, new SessionEntry(context));
        log.info("[SessionManager] 创建新会话: {}", newId);
        return context;
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