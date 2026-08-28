package com.sitech.prodai.service.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文，维护多轮对话的中间状态。
 */
public class SessionContext {

    /** 连续澄清上限：超过后降级为按缺省值继续（防死循环，设计文档 4.4 节） */
    private static final int MAX_CLARIFY_ROUNDS = 3;

    private String sessionId;
    private List<Map<String, Object>> history;
    private Map<String, Object> cachedEvidence;
    private String lastIntent;
    private List<String> lastTools;
    private Map<String, Object> lastParams;

    /** 已澄清参数缓存：被用户补齐的必填参数（offering 等），跨轮复用 */
    private Map<String, Object> resolvedParams;

    /** 会话级附加元数据（分析对象、对比周期等，前端上下文标签展示） */
    private Map<String, Object> meta;

    /** 当前请求的助手场景（null 或空 = 默认运营场景；"rd" = 产商品研发场景）。驱动理解层分支选择，不落库。 */
    private String scene;

    /** 连续澄清轮次计数 */
    private int clarifyRounds;

    /** 最近一次澄清计划中待补充的参数名列表（供表达层生成追问文案） */
    private List<String> lastClarifyParams;

    public SessionContext() {
        this.history = new ArrayList<>();
        this.cachedEvidence = new LinkedHashMap<>();
        this.lastParams = new LinkedHashMap<>();
        this.resolvedParams = new LinkedHashMap<>();
        this.meta = new LinkedHashMap<>();
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

    public Map<String, Object> getResolvedParams() {
        return resolvedParams;
    }

    public void setResolvedParams(Map<String, Object> resolvedParams) {
        this.resolvedParams = resolvedParams != null ? resolvedParams : new LinkedHashMap<>();
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? meta : new LinkedHashMap<>();
    }

    public int getClarifyRounds() {
        return clarifyRounds;
    }

    public void incrementClarifyRounds() {
        this.clarifyRounds++;
    }

    public void resetClarifyRounds() {
        this.clarifyRounds = 0;
    }

    /**
     * 是否已达到澄清上限（超过后应按缺省值继续，防死循环）。
     */
    public boolean exceedClarifyLimit() {
        return clarifyRounds >= MAX_CLARIFY_ROUNDS;
    }

    /**
     * 记录用户补齐的澄清参数，跨轮复用。
     */
    public void resolveParam(String name, Object value) {
        if (name != null && value != null) {
            this.resolvedParams.put(name, value);
            this.meta.put("last_" + name, value);
        }
    }

    public List<String> getLastClarifyParams() {
        return lastClarifyParams;
    }

    public void setLastClarifyParams(List<String> lastClarifyParams) {
        this.lastClarifyParams = lastClarifyParams;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }
}