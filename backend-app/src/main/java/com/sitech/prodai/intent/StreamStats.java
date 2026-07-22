package com.sitech.prodai.intent;

import com.sitech.prodai.util.TokenCounter;

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

    // LLM 成本统计
    private int inputTokens;
    private int outputTokens;
    private double estimatedCost;

    // Token 精确计数器（可选注入）
    private TokenCounter tokenCounter;
    private String model = "gpt-4o-mini";

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
        m.put("inputTokens", inputTokens);
        m.put("outputTokens", outputTokens);
        m.put("totalTokens", inputTokens + outputTokens);
        m.put("estimatedCost", estimatedCost);
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

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public double getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(double estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    /**
     * 设置 Token 精确计数器。
     */
    public void setTokenCounter(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    /**
     * 设置模型名称（用于选择合适的编码）。
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * 记录输出 token。
     * 优先使用 jtokkit 精确计数，降级到字符估算。
     */
    public void recordOutputText(String text) {
        if (text == null || text.isBlank()) return;
        int tokens;
        if (tokenCounter != null) {
            tokens = tokenCounter.countForModel(text, model);
        } else {
            tokens = estimateTokens(text);
        }
        this.outputTokens += tokens;
        this.llmTokens += tokens;
        this.llmChars += text.length();
        updateEstimatedCost();
    }

    /**
     * 记录输入 token。
     * 优先使用 jtokkit 精确计数，降级到字符估算。
     */
    public void recordInputTokens(String prompt) {
        if (prompt == null || prompt.isBlank()) return;
        int tokens;
        if (tokenCounter != null) {
            tokens = tokenCounter.countForModel(prompt, model);
        } else {
            tokens = estimateTokens(prompt);
        }
        this.inputTokens += tokens;
        updateEstimatedCost();
    }

    private int estimateTokens(String text) {
        int chinese = 0, ascii = 0;
        for (char c : text.toCharArray()) {
            if (c > 0x4e00 && c < 0x9fff) {
                chinese++;
            } else if (c > 32) {
                ascii++;
            }
        }
        return (int) Math.ceil(chinese * 2.0 + ascii * 0.35);
    }

    /**
     * 基于 token 数估算成本（以 gpt-4o-mini 为例：$0.15/1M input, $0.60/1M output）。
     */
    private void updateEstimatedCost() {
        this.estimatedCost = Math.round(
                (inputTokens * 0.15 + outputTokens * 0.60) / 1_000_000.0 * 1_000_000.0
        ) / 1_000_000.0;
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

    public void recordToken() {
        this.tokenCount++;
    }

    public int getTokensUsed() {
        return tokenCount;
    }
}
