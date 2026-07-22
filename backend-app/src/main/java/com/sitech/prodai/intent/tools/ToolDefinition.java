package com.sitech.prodai.intent.tools;

import java.util.Map;

/**
 * Function Calling 工具定义 —— 描述一个可被 LLM 调用的工具。
 *
 * <p>对齐 OpenAI Function Calling 格式：
 * <pre>
 * {
 *   "type": "function",
 *   "function": {
 *     "name": "ontology_query",
 *     "description": "查询本体数据",
 *     "parameters": {
 *       "type": "object",
 *       "properties": { ... },
 *       "required": [...]
 *     }
 *   }
 * }
 * </pre>
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters,
        ToolExecutor executor
) {

    /**
     * 将工具定义转换为 OpenAI Function Calling 格式。
     */
    public Map<String, Object> toOpenAiFunction() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }

    /**
     * 工具执行器函数式接口。
     */
    @FunctionalInterface
    public interface ToolExecutor {
        /**
         * 执行工具。
         *
         * @param args LLM 传入的参数
         * @return 工具执行结果（JSON 字符串）
         */
        String execute(Map<String, Object> args);
    }
}
