package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.agent.model.ExecutionResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 工具接口 - 所有 Agent 工具都实现此接口。
 */
public interface AgentTool {

    /**
     * 工具名称（唯一标识）。
     */
    String getName();

    /**
     * 工具描述（供 LLM 理解工具用途）。
     */
    String getDescription();

    /**
     * 入参规范声明（供理解层校验必填参数、生成 CLARIFY 澄清）。
     * <p>
     * 旧工具可不实现，默认返回空列表。
     */
    default List<ToolParam> getParams() {
        return Collections.emptyList();
    }

    /**
     * 执行工具。
     *
     * @param params 工具参数
     * @return 执行结果
     */
    ExecutionResult execute(Map<String, Object> params);
}