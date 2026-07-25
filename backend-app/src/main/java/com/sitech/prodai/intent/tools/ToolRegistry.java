package com.sitech.prodai.intent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Function Calling 工具注册中心 —— 管理所有可被 LLM 调用的工具。
 *
 * <p>Spring 启动时注入所有 {@link ToolDefinition} Bean 并自动注册。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public ToolRegistry(@Autowired(required = false) List<ToolDefinition> toolDefinitions) {
        if (toolDefinitions != null) {
            for (ToolDefinition tool : toolDefinitions) {
                register(tool);
            }
        }
        log.info("[ToolRegistry] 初始化完成，共注册 {} 个工具", tools.size());
    }

    /**
     * 注册一个工具定义。
     */
    public void register(ToolDefinition tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            log.warn("[ToolRegistry] 跳过无效的工具定义: {}", tool);
            return;
        }
        tools.put(tool.name(), tool);
        log.info("[ToolRegistry] 注册工具: {} - {}", tool.name(), tool.description());
    }

    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<Map<String, Object>> getAllToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (ToolDefinition tool : tools.values()) {
            definitions.add(tool.toOpenAiFunction());
        }
        return definitions;
    }

    public List<String> getAllToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    public Optional<String> execute(String name, Map<String, Object> args) {
        ToolDefinition tool = tools.get(name);
        if (tool == null) {
            log.warn("[ToolRegistry] 工具不存在: {}", name);
            return Optional.empty();
        }

        try {
            log.info("[ToolRegistry] 执行工具: {} args={}", name, args);
            String result = tool.executor().execute(args);
            log.debug("[ToolRegistry] 工具执行完成: {} result_length={}", name, result != null ? result.length() : 0);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.error("[ToolRegistry] 工具执行失败: {} error={}", name, e.getMessage(), e);
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("tool", name);
            try {
                return Optional.of(MAPPER.writeValueAsString(errorResult));
            } catch (Exception jsonEx) {
                return Optional.of("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public int getToolCount() {
        return tools.size();
    }

    public List<Map<String, String>> listTools() {
        List<Map<String, String>> list = new ArrayList<>();
        for (ToolDefinition tool : tools.values()) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("name", tool.name());
            info.put("description", tool.description());
            list.add(info);
        }
        return list;
    }
}
