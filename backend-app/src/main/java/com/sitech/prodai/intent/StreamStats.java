package com.sitech.prodai.intent;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流式输出统计信息 —— 对齐 Python {@code app/services/llm/base.py::StreamStats}
 * 与 chat_stream 中的扩展字段（total_elapsed / is_form / error / llm_*）。
 */
public class StreamStats {

    private Instant startTime;
    private Instant endTime;
    private int tokenCount;
    private int charCount;
    private int chunkCount;
    private int thinkingChars;
    private int errorCount;

    // 扩展字段（chat_stream 使用）
    private double totalElapsed;
    private boolean form;
    private String error;
    private double llmElapsed;
    private int llmTokens;
    private int llmChars;
    private double llmTps;

    public StreamStats() {
        this.startTime = Instant.now();
    }

    public StreamStats(long startEpochMillis) {
        this.startTime = Instant.ofEpochMilli(startEpochMillis);
    }

    public double getElapsed() {
        if (endTime != null) {
            return Duration.between(startTime, endTime).toMillis() / 1000.0;
        }
        return Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
    }

    public double getTokensPerSecond() {
        double elapsed = getElapsed();
        return elapsed > 0 ? tokenCount / elapsed : 0.0;
    }

    public double getCharsPerSecond() {
        double elapsed = getElapsed();
        return elapsed > 0 ? charCount / elapsed : 0.0;
    }

    /** 对齐 Python to_dict() */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("elapsed", Math.round(getElapsed() * 1000.0) / 1000.0);
        m.put("tokenCount", tokenCount);
        m.put("charCount", charCount);
        m.put("chunkCount", chunkCount);
        m.put("thinkingChars", thinkingChars);
        m.put("errorCount", errorCount);
        m.put("totalElapsed", Math.round(totalElapsed * 1000.0) / 1000.0);
        m.put("isForm", form);
        if (error != null) {
            m.put("error", error);
        }
        m.put("llmElapsed", Math.round(llmElapsed * 1000.0) / 1000.0);
        m.put("llmTokens", llmTokens);
        m.put("llmChars", llmChars);
        m.put("llmTps", Math.round(llmTps * 10.0) / 10.0);
        return m;
    }

    public void finish() {
        this.endTime = Instant.now();
    }

    public void setTotalElapsed(double totalElapsed) {
        this.totalElapsed = totalElapsed;
    }

    public void setForm(boolean form) {
        this.form = form;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setLlmElapsed(double llmElapsed) {
        this.llmElapsed = llmElapsed;
    }

    public void setLlmTokens(int llmTokens) {
        this.llmTokens = llmTokens;
    }

    public void setLlmChars(int llmChars) {
        this.llmChars = llmChars;
    }

    public void setLlmTps(double llmTps) {
        this.llmTps = llmTps;
    }

    public long getStartTimeMillis() {
        return startTime.toEpochMilli();
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }
}
