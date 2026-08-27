package com.sitech.prodai.service.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文，维护多轮对话的中间状态。
 */
public class SessionContext {

    private String sessionId;
    private List<Map<String, Object>> history;
    private Map<String, Object> cachedEvidence;
    private String lastIntent;
    private List<String> lastTools;
    private Map<String, Object> lastParams;

    public SessionContext() {
        this.history = new ArrayList<>();
        this.cachedEvidence = new LinkedHashMap<>();
        this.lastParams = new LinkedHashMap<>();
    }

    public SessionContext(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<Map<String, Object>> getHistory() {
        return history;
    }

    public void setHistory(List<Map<String, Object>> history) {
        this.history = history != null ? history : new ArrayList<>();
    }

    public Map<String, Object> getCachedEvidence() {
        return cachedEvidence;
    }

    public void setCachedEvidence(Map<String, Object> cachedEvidence) {
        this.cachedEvidence = cachedEvidence != null ? cachedEvidence : new LinkedHashMap<>();
    }

    public String getLastIntent() {
        return lastIntent;
    }

    public void setLastIntent(String lastIntent) {
        this.lastIntent = lastIntent;
    }

    public List<String> getLastTools() {
        return lastTools;
    }

    public void setLastTools(List<String> lastTools) {
        this.lastTools = lastTools;
    }

    public Map<String, Object> getLastParams() {
        return lastParams;
    }

    public void setLastParams(Map<String, Object> lastParams) {
        this.lastParams = lastParams != null ? lastParams : new LinkedHashMap<>();
    }

    public void addHistoryEntry(String role, String content) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("role", role);
        entry.put("content", content);
        this.history.add(entry);
    }

    public void cacheEvidence(String key, Object value) {
        this.cachedEvidence.put(key, value);
    }
}