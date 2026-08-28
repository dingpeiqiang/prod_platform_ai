package com.sitech.prodai.service.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具执行结果。
 */
public class ExecutionResult {

    private boolean success;
    private String toolName;
    private Map<String, Object> data;
    private Map<String, Object> params;
    private String errorMessage;
    private long executionTimeMs;

    public ExecutionResult() {
        this.data = new LinkedHashMap<>();
    }

    public ExecutionResult(boolean success, String toolName, Map<String, Object> data, String errorMessage) {
        this.success = success;
        this.toolName = toolName;
        this.data = data != null ? new LinkedHashMap<>(data) : new LinkedHashMap<>();
        this.errorMessage = errorMessage;
    }

    public static ExecutionResult ok(String toolName, Map<String, Object> data) {
        return new ExecutionResult(true, toolName, data, null);
    }

    public static ExecutionResult fail(String toolName, String errorMessage) {
        return new ExecutionResult(false, toolName, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data != null ? new LinkedHashMap<>(data) : new LinkedHashMap<>();
    }

    /** 该步骤实际解析并透传给工具执行的入参（供流式 tool 事件展示输入）。 */
    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params != null ? new LinkedHashMap<>(params) : null;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    @Override
    public String toString() {
        return "ExecutionResult{success=" + success + ", toolName='" + toolName + "'}";
    }
}