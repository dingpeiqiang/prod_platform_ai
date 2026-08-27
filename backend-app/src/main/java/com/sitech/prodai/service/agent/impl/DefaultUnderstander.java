package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.Understander;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认理解层实现。
 * <p>
 * 理解过程：
 * 1. 意图识别 → 用户想做什么
 * 2. 实体抽取 → 涉及哪些实体
 * 3. 查询计划生成 → 需要调用哪些工具
 */
@Component
public class DefaultUnderstander implements Understander {

    private static final Logger log = LoggerFactory.getLogger(DefaultUnderstander.class);

    private final LlmService llmService;

    public DefaultUnderstander(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public QueryPlan understand(String question, SessionContext context) {
        if (question == null || question.isBlank()) {
            return chatPlan(question);
        }

        // Step 1: 尝试关键词降级（无 LLM 时的兜底）
        Map<String, Object> keywordResult = IntentRecognitionSupport.tryKeywordFallback(question, null);
        if (keywordResult != null) {
            String intentType = String.valueOf(keywordResult.getOrDefault("intentType", ""));
            String action = String.valueOf(keywordResult.getOrDefault("action", ""));
            return mapKeywordToPlan(intentType, action, question);
        }

        // Step 2: 尝试 LLM 意图识别
        try {
            List<Map<String, String>> history = toHistory(context);
            String llmResult = llmService.completeMessages(
                    buildSystemPrompt(),
                    history,
                    question
            );
            return parseLlmResult(llmResult, question);
        } catch (Exception e) {
            log.warn("[DefaultUnderstander] LLM 意图识别失败，降级到关键词: {}", e.getMessage());
        }

        // Step 3: 降级到通用对话
        return chatPlan(question);
    }

    /**
     * 将关键词降级结果映射为查询计划。
     */
    private QueryPlan mapKeywordToPlan(String intentType, String action, String question) {
        String normalized = IntentRecognitionSupport.normalizeIntentType(intentType);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("question", question);

        return switch (normalized) {
            case "product_ops_query" -> new QueryPlan("SPARQL_QUERY",
                    List.of("sparql_query"), params, question);
            case "product_ops_reason" -> new QueryPlan("SWRL_INFER",
                    List.of("sparql_query", "swrl_root_cause"), params, question);
            case "product_ops_policy" -> {
                if ("risk_audit".equals(action) || "product_ops_policy".equals(normalized)) {
                    yield new QueryPlan("SWRL_INFER",
                            List.of("swrl_risk_audit"), params, question);
                }
                yield new QueryPlan("SPARQL_QUERY",
                        List.of("sparql_query"), params, question);
            }
            default -> chatPlan(question);
        };
    }

    /**
     * 解析 LLM 返回的结构化结果。
     */
    private QueryPlan parseLlmResult(String llmResult, String question) {
        if (llmResult == null || llmResult.isBlank()) {
            return chatPlan(question);
        }

        // 尝试从 LLM 输出中提取 JSON
        int start = llmResult.indexOf('{');
        int end = llmResult.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                String json = llmResult.substring(start, end + 1);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> parsed = mapper.readValue(json,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

                String intent = String.valueOf(parsed.getOrDefault("intent", "CHAT"));
                @SuppressWarnings("unchecked")
                List<String> tools = (List<String>) parsed.getOrDefault("tools", List.of());
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) parsed.getOrDefault("params", new LinkedHashMap<>());
                params.putIfAbsent("question", question);

                if (tools.isEmpty()) {
                    return chatPlan(question);
                }
                return new QueryPlan(intent, tools, params, question);
            } catch (Exception e) {
                log.warn("[DefaultUnderstander] LLM 输出解析失败: {}", e.getMessage());
            }
        }

        return chatPlan(question);
    }

    /**
     * 通用对话：不调用工具，直接 LLM 回复。
     */
    private QueryPlan chatPlan(String question) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("question", question);
        return new QueryPlan("CHAT", List.of(), params, question);
    }

    private String buildSystemPrompt() {
        return "你是一个智能助手，负责理解用户问题并生成查询计划。\n\n"
                + "请分析用户意图，输出 JSON 格式（仅输出 JSON，不要其他内容）：\n"
                + "{\n"
                + "  \"intent\": \"意图类型 (SPARQL_QUERY | SWRL_INFER | RULE_EXPLAIN | CHAT)\",\n"
                + "  \"tools\": [\"工具名称列表\"],\n"
                + "  \"params\": { \"参数名\": \"参数值\" }\n"
                + "}\n\n"
                + "可用工具：\n"
                + "- sparql_query: 查询 RDF 知识库（适用于查询数据、商品列表、指标等）\n"
                + "- swrl_root_cause: 归因分析（适用于分析原因、根因定位）\n"
                + "- swrl_risk_audit: 风险稽核（适用于风险筛查、合规检查）\n"
                + "- rule_explain: 规则解释（适用于询问规则含义）\n"
                + "- ontology_explain: 本体概念解释（适用于询问业务概念）\n\n"
                + "如果用户只是打招呼或闲聊，intent 设为 CHAT，tools 为空列表。";
    }

    private List<Map<String, String>> toHistory(SessionContext context) {
        if (context == null || context.getHistory() == null) {
            return null;
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> entry : context.getHistory()) {
            Map<String, String> converted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : entry.entrySet()) {
                converted.put(e.getKey(), String.valueOf(e.getValue()));
            }
            result.add(converted);
        }
        return result;
    }
}