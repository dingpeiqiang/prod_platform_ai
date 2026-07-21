package com.sitech.prodai.intent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图处理器上下文数据袋 —— 对齐 Python {@code app/intent/base.py::IntentContext}。
 *
 * <p>替代 chat_stream() 内部十余个局部变量，在意图识别 → 分发 → 处理器之间传递上下文。
 */
public class IntentContext {

    // ── 意图识别结果 ──────────────────────────────────
    private Map<String, Object> intentData = new HashMap<>();
    private String intentResult = "";  // 原始 LLM JSON 字符串
    private String intentType = "";
    private double confidence;
    private String action = "";
    private String formCode = "";
    private String formName = "";
    private Map<String, Object> extractedFields = new HashMap<>();

    // ── 本体与场景数据 ────────────────────────────────
    private Map<String, Map<String, Object>> ontologies = new HashMap<>();
    private String ontologiesInfo = "";
    private String sceneKeywords = "";

    // ── 原始请求相关 ──────────────────────────────────
    private Map<String, Object> request;  // ChatRequest 透传
    private String lastUserMessage = "";
    private String messagesText = "";

    // ── Prompt 相关 ──────────────────────────────────
    private String intentPrompt = "";

    // ── 统计与时间 ────────────────────────────────────
    private long startTime;
    private StreamStats streamStats;

    // ── 错误信息 ──────────────────────────────────────
    private String errorInfo;

    // ── 用户/会话信息 ────────────────────────────────
    private String userId;
    private String sessionId;

    // ── 消息列表（从 request.messages 转换） ─────────
    private List<Map<String, Object>> messages;

    public IntentContext() {
        this.startTime = System.currentTimeMillis();
        this.streamStats = new StreamStats(this.startTime);
    }

    // ==================== Getters / Setters ====================

    public Map<String, Object> getIntentData() {
        return intentData;
    }

    public void setIntentData(Map<String, Object> intentData) {
        this.intentData = intentData != null ? intentData : new HashMap<>();
    }

    public String getIntentResult() {
        return intentResult;
    }

    public void setIntentResult(String intentResult) {
        this.intentResult = intentResult != null ? intentResult : "";
    }

    public String getIntentType() {
        return intentType;
    }

    public void setIntentType(String intentType) {
        this.intentType = intentType != null ? intentType : "";
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action != null ? action : "";
    }

    public String getFormCode() {
        return formCode;
    }

    public void setFormCode(String formCode) {
        this.formCode = formCode != null ? formCode : "";
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName != null ? formName : "";
    }

    public Map<String, Object> getExtractedFields() {
        return extractedFields;
    }

    public void setExtractedFields(Map<String, Object> extractedFields) {
        this.extractedFields = extractedFields != null ? extractedFields : new HashMap<>();
    }

    public Map<String, Map<String, Object>> getOntologies() {
        return ontologies;
    }

    public void setOntologies(Map<String, Map<String, Object>> ontologies) {
        this.ontologies = ontologies != null ? ontologies : new HashMap<>();
    }

    public String getOntologiesInfo() {
        return ontologiesInfo;
    }

    public void setOntologiesInfo(String ontologiesInfo) {
        this.ontologiesInfo = ontologiesInfo != null ? ontologiesInfo : "";
    }

    public String getSceneKeywords() {
        return sceneKeywords;
    }

    public void setSceneKeywords(String sceneKeywords) {
        this.sceneKeywords = sceneKeywords != null ? sceneKeywords : "";
    }

    public Map<String, Object> getRequest() {
        return request;
    }

    public void setRequest(Map<String, Object> request) {
        this.request = request;
    }

    public String getLastUserMessage() {
        return lastUserMessage;
    }

    public void setLastUserMessage(String lastUserMessage) {
        this.lastUserMessage = lastUserMessage != null ? lastUserMessage : "";
    }

    public String getMessagesText() {
        return messagesText;
    }

    public void setMessagesText(String messagesText) {
        this.messagesText = messagesText != null ? messagesText : "";
    }

    public String getIntentPrompt() {
        return intentPrompt;
    }

    public void setIntentPrompt(String intentPrompt) {
        this.intentPrompt = intentPrompt != null ? intentPrompt : "";
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public StreamStats getStreamStats() {
        return streamStats;
    }

    public void setStreamStats(StreamStats streamStats) {
        this.streamStats = streamStats;
    }

    public String getErrorInfo() {
        return errorInfo;
    }

    public void setErrorInfo(String errorInfo) {
        this.errorInfo = errorInfo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<Map<String, Object>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, Object>> messages) {
        this.messages = messages;
    }

    /** 从 request map 中提取 userId（兼容多种 key） */
    public String resolveUserId() {
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        if (request != null) {
            Object val = request.get("userId");
            if (val == null) {
                val = request.get("user_id");
            }
            if (val != null) {
                return String.valueOf(val);
            }
        }
        return "user-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /** 从 request map 中提取 sessionId */
    public String resolveSessionId() {
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionId;
        }
        if (request != null) {
            Object val = request.get("sessionId");
            if (val == null) {
                val = request.get("session_id");
            }
            if (val != null) {
                return String.valueOf(val);
            }
        }
        return "session-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
