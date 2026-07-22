package com.sitech.prodai.intent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Spring 启动时自动扫描并注册所有 {@link ToolDefinition} Bean。
 * 支持按名称查找工具、获取所有工具定义、执行工具调用。
 *
 * <p>使用方式：
 * <pre>
 * // 注册工具（通过 @Bean 或直接调用）
 * toolRegistry.register(toolDefinition);
 *
 * // 获取所有工具定义（用于 LLM 请求）
 * List&lt;Map&lt;String, Object&gt;&gt; tools = toolRegistry.getAllToolDefinitions();
 *
 * // 执行工具调用
 * Optional&lt;String&gt; result = toolRegistry.execute("ontology_query", args);
 * </pre>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final ConcurrentHashMap<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * 注册一个工具定义。
     *
     * @param tool 工具定义
     */
    public void register(ToolDefinition tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            log.warn("[ToolRegistry] 跳过无效的工具定义: {}", tool);
            return;
        }
        tools.put(tool.name(), tool);
        log.info("[ToolRegistry] 注册工具: {} - {}", tool.name(), tool.description());
    }

    /**
     * 根据名称获取工具定义。
     *
     * @param name 工具名称
     * @return 工具定义（Optional）
     */
    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 获取所有工具的 OpenAI Function Calling 格式定义。
     *
     * @return 工具定义列表
     */
    public List<Map<String, Object>> getAllToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (ToolDefinition tool : tools.values()) {
            definitions.add(tool.toOpenAiFunction());
        }
        return definitions;
    }

    /**
     * 获取所有工具名称。
     *
     * @return 工具名称列表
     */
    public List<String> getAllToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * 执行工具调用。
     *
     * @param name 工具名称
     * @param args 工具参数
     * @return 工具执行结果（JSON 字符串），工具不存在时返回 Optional.empty()
     */
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
            return Optional.of(errorResult.toString());
        }
    }

    /**
     * 检查工具是否已注册。
     *
     * @param name 工具名称
     * @return 是否已注册
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 获取已注册的工具数量。
     *
     * @return 工具数量
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * 列出所有已注册的工具信息。
     *
     * @return 工具信息列表
     */
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
