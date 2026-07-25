package com.sitech.prodai.intent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.intent.SseStreamSupport;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Function Calling 编排服务 —— 协调 LLM 与工具调用。
 *
 * <p>工具调用格式（LLM 输出）：
 * <pre>
 * [TOOL_CALL] ontology_query {"question": "查询所有5G套餐"}
 * </pre>
 */
@Service
@ConditionalOnProperty(name = "prodai.llm.enabled", havingValue = "true", matchIfMissing = false)
public class FunctionCallingService {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_TOOL_ROUNDS = 5;

    /** 匹配 [TOOL_CALL] name {json...}，JSON 可跨行。 */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "\\[TOOL_CALL]\\s+(\\w+)\\s+(\\{[\\s\\S]*?})(?=\\s*(?:\\[TOOL_CALL]|\\[TOOL_RESULT]|$))",
            Pattern.MULTILINE
    );

    private final LlmService llmService;
    private final ToolRegistry toolRegistry;

    public FunctionCallingService(LlmService llmService, ToolRegistry toolRegistry) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 带 Function Calling 的同步调用。
     */
    public String completeWithTools(String systemPrompt, String userMessage) {
        ToolLoopResult result = runToolLoop(systemPrompt, userMessage);
        return result.finalReply();
    }

    /**
     * 带 Function Calling 的流式调用：thinking（含工具步骤）→ text 分片。
     */
    public Flux<Map<String, Object>> streamWithTools(String systemPrompt, String userMessage) {
        return Mono.fromCallable(() -> runToolLoop(systemPrompt, userMessage))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(result -> {
                    List<Map<String, Object>> events = new ArrayList<>(result.events());
                    events.addAll(SseStreamSupport.chunkedTextEvents(result.finalReply()));
                    return Flux.fromIterable(events);
                })
                .onErrorResume(error -> {
                    log.error("[FunctionCallingService] 流式工具调用失败", error);
                    return Flux.fromIterable(List.of(
                            SseUtils.thinking("工具调用失败，尝试直接回复..."),
                            SseUtils.textStart(),
                            SseUtils.text("抱歉，处理时遇到问题：" + error.getMessage()),
                            SseUtils.textEnd()
                    ));
                });
    }

    private ToolLoopResult runToolLoop(String systemPrompt, String userMessage) {
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(SseUtils.thinking("正在分析是否需要调用工具..."));

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

            StringBuilder toolResults = new StringBuilder();
            for (ToolCall toolCall : toolCalls) {
                events.add(SseUtils.thinkingRich(
                        "调用工具: " + toolCall.name(),
                        Map.of(
                                "phase", "tool_call",
                                "tool", toolCall.name(),
                                "round", round + 1
                        ),
                        -1,
                        summarizeArgs(toolCall.args())
                ));

                Optional<String> result = toolRegistry.execute(toolCall.name(), toolCall.args());
                String resultStr = result.orElse("{\"error\": \"tool_not_found\"}");
                toolResults.append("[TOOL_RESULT] ")
                        .append(toolCall.name())
                        .append(" ")
                        .append(resultStr)
                        .append("\n");

                events.add(SseUtils.thinkingRich(
                        "工具 " + toolCall.name() + " 执行完成",
                        Map.of(
                                "phase", "tool_result",
                                "tool", toolCall.name(),
                                "round", round + 1
                        ),
                        0,
                        truncate(resultStr, 200)
                ));
            }

            conversation.add(Map.of("role", "assistant", "content", currentReply));
            conversation.add(Map.of("role", "user", "content", toolResults.toString()));
            currentReply = llmService.completeMessages(enhancedSystemPrompt, conversation, "请基于工具结果给出最终回答，不要再输出 [TOOL_CALL]。");
            round++;
        }

        String finalReply = stripToolCallArtifacts(currentReply);
        if (finalReply.isBlank()) {
            finalReply = "已完成处理，但未能生成有效回复。";
        }
        return new ToolLoopResult(events, finalReply);
    }

    private String buildSystemPromptWithTools(String basePrompt) {
        List<Map<String, Object>> toolDefs = toolRegistry.getAllToolDefinitions();
        if (toolDefs.isEmpty()) {
            return basePrompt != null ? basePrompt : "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(basePrompt != null ? basePrompt : "");
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
        sb.append("1. 需要数据时输出 [TOOL_CALL]，不要编造业务数据\n");
        sb.append("2. 工具结果会反馈给你，请基于结果回答用户\n");
        sb.append("3. 若不需要工具，请直接回答，不要输出工具调用格式\n");
        sb.append("4. 最终回答中不要包含 [TOOL_CALL] 或 [TOOL_RESULT]\n");

        return sb.toString();
    }

    List<ToolCall> parseToolCalls(String response) {
        List<ToolCall> toolCalls = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return toolCalls;
        }

        String normalized = response.replace("```", "");
        Matcher matcher = TOOL_CALL_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String toolName = matcher.group(1).trim();
            String argsJson = matcher.group(2).trim();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> args = mapper.readValue(argsJson, Map.class);
                toolCalls.add(new ToolCall(toolName, args));
                log.debug("[FunctionCallingService] 解析到工具调用: {} args={}", toolName, args);
            } catch (Exception e) {
                log.warn("[FunctionCallingService] 工具调用参数解析失败: {} error={}", argsJson, e.getMessage());
            }
        }

        // 回退：逐行解析单行 JSON
        if (toolCalls.isEmpty()) {
            for (String line : normalized.split("\n")) {
                line = line.trim();
                if (!line.startsWith("[TOOL_CALL]")) {
                    continue;
                }
                String remainder = line.substring("[TOOL_CALL]".length()).trim();
                int spaceIdx = remainder.indexOf(' ');
                if (spaceIdx <= 0) {
                    continue;
                }
                String toolName = remainder.substring(0, spaceIdx).trim();
                String argsJson = remainder.substring(spaceIdx + 1).trim();
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = mapper.readValue(argsJson, Map.class);
                    toolCalls.add(new ToolCall(toolName, args));
                } catch (Exception e) {
                    log.warn("[FunctionCallingService] 单行工具调用解析失败: {} error={}", argsJson, e.getMessage());
                }
            }
        }

        return toolCalls;
    }

    static String stripToolCallArtifacts(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[TOOL_CALL]") || trimmed.startsWith("[TOOL_RESULT]")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private static String summarizeArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "";
        }
        try {
            return truncate(mapper.writeValueAsString(args), 160);
        } catch (Exception e) {
            return args.toString();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private record ToolLoopResult(List<Map<String, Object>> events, String finalReply) {
    }

    record ToolCall(String name, Map<String, Object> args) {
    }
}
