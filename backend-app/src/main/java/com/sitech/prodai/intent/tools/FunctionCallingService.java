package com.sitech.prodai.intent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Function Calling 编排服务 —— 协调 LLM 与工具调用。
 *
 * <p>处理流程：
 * <ol>
 *   <li>构建包含工具定义的系统提示词</li>
 *   <li>调用 LLM 获取响应</li>
 *   <li>解析响应中的工具调用请求</li>
 *   <li>执行工具并获取结果</li>
 *   <li>将工具结果反馈给 LLM 获取最终回复</li>
 *   <li>支持多轮工具调用（最多 5 轮）</li>
 * </ol>
 *
 * <p>工具调用格式（LLM 输出）：
 * <pre>
 * [TOOL_CALL] ontology_query {"question": "查询所有5G套餐"}
 * </pre>
 *
 * <p>工具调用格式（执行结果反馈）：
 * <pre>
 * [TOOL_RESULT] ontology_query {"success": true, "results": [...]}
 * </pre>
 */
@Service
@ConditionalOnProperty(name = "prodai.llm.enabled", havingValue = "true", matchIfMissing = false)
public class FunctionCallingService {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_TOOL_ROUNDS = 5;

    private final LlmService llmService;
    private final ToolRegistry toolRegistry;

    public FunctionCallingService(LlmService llmService, ToolRegistry toolRegistry) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 带 Function Calling 的同步调用。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return 最终回复文本
     */
    public String completeWithTools(String systemPrompt, String userMessage) {
        List<Map<String, String>> conversation = new ArrayList<>();
        String enhancedSystemPrompt = buildSystemPromptWithTools(systemPrompt);
        String currentReply = llmService.completeMessages(enhancedSystemPrompt, conversation, userMessage);

        int round = 0;
        while (round < MAX_TOOL_ROUNDS) {
            List<ToolCall> toolCalls = parseToolCalls(currentReply);
            if (toolCalls.isEmpty()) {
                break;
            }

            log.info("[FunctionCallingService] 第 {} 轮工具调用，共 {} 个工具", round + 1, toolCalls.size());

            // 执行工具并构建结果消息
            StringBuilder toolResults = new StringBuilder();
            for (ToolCall toolCall : toolCalls) {
                Optional<String> result = toolRegistry.execute(toolCall.name(), toolCall.args());
                String resultStr = result.orElse("{\"error\": \"tool_not_found\"}");
                toolResults.append("[TOOL_RESULT] ").append(toolCall.name()).append(" ").append(resultStr).append("\n");
            }

            // 将 LLM 回复和工具结果添加到对话历史
            conversation.add(Map.of("role", "assistant", "content", currentReply));
            conversation.add(Map.of("role", "user", "content", toolResults.toString()));

            // 继续对话
            currentReply = llmService.completeMessages(enhancedSystemPrompt, conversation, "");
            round++;
        }

        return currentReply;
    }

    /**
     * 带 Function Calling 的流式调用。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return SSE 事件流
     */
    public Flux<Map<String, Object>> streamWithTools(String systemPrompt, String userMessage) {
        List<Map<String, String>> conversation = new ArrayList<>();
        String enhancedSystemPrompt = buildSystemPromptWithTools(systemPrompt);

        return Flux.defer(() -> {
            // 第一次调用
            Flux<String> stream = llmService.streamWithMessages(enhancedSystemPrompt, conversation, userMessage);

            return stream
                    .doOnNext(chunk -> {
                        // 检查是否包含工具调用
                        // 注意：流式输出中工具调用可能不完整，需要在 done 事件后处理
                    })
                    .map(chunk -> {
                        Map<String, Object> event = new LinkedHashMap<>();
                        event.put("type", "text");
                        event.put("content", chunk);
                        return event;
                    });
        });
    }

    /**
     * 构建包含工具定义的系统提示词。
     */
    private String buildSystemPromptWithTools(String basePrompt) {
        List<Map<String, Object>> toolDefs = toolRegistry.getAllToolDefinitions();
        if (toolDefs.isEmpty()) {
            return basePrompt;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(basePrompt);
        sb.append("\n\n## 可用工具\n\n");
        sb.append("你可以调用以下工具来获取数据。当需要查询数据或执行操作时，请使用工具调用格式：\n\n");
        sb.append("```\n[TOOL_CALL] <tool_name> <json_args>\n```\n\n");
        sb.append("可用工具列表：\n\n");

        for (Map<String, Object> toolDef : toolDefs) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) toolDef.get("function");
            if (function != null) {
                sb.append("- **").append(function.get("name")).append("**: ");
                sb.append(function.get("description")).append("\n");

                @SuppressWarnings("unchecked")
                Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
                if (parameters != null && parameters.containsKey("properties")) {
                    sb.append("  参数：\n");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
                    for (Map.Entry<String, Object> prop : properties.entrySet()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> propDef = (Map<String, Object>) prop.getValue();
                        sb.append("    - ").append(prop.getKey()).append(": ");
                        sb.append(propDef.getOrDefault("description", "")).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        sb.append("\n注意：\n");
        sb.append("1. 每次只能调用一个工具\n");
        sb.append("2. 工具调用结果会自动反馈给你，请基于结果继续回答\n");
        sb.append("3. 如果不需要工具，请直接回答，不要输出工具调用格式\n");

        return sb.toString();
    }

    /**
     * 解析 LLM 响应中的工具调用。
     *
     * <p>格式：[TOOL_CALL] tool_name {"arg1": "value1", ...}
     */
    private List<ToolCall> parseToolCalls(String response) {
        List<ToolCall> toolCalls = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return toolCalls;
        }

        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("[TOOL_CALL]")) {
                String remainder = line.substring("[TOOL_CALL]".length()).trim();
                int spaceIdx = remainder.indexOf(' ');
                if (spaceIdx > 0) {
                    String toolName = remainder.substring(0, spaceIdx).trim();
                    String argsJson = remainder.substring(spaceIdx + 1).trim();

                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = mapper.readValue(argsJson, Map.class);
                        toolCalls.add(new ToolCall(toolName, args));
                        log.debug("[FunctionCallingService] 解析到工具调用: {} args={}", toolName, args);
                    } catch (Exception e) {
                        log.warn("[FunctionCallingService] 工具调用参数解析失败: {} error={}", argsJson, e.getMessage());
                    }
                }
            }
        }

        return toolCalls;
    }

    /**
     * 工具调用记录。
     */
    private record ToolCall(String name, Map<String, Object> args) {
    }
}
