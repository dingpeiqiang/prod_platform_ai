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
     * 业务可读工具名（显示标签）。
     * <p>
     * 旧工具不实现时默认使用工具内部名 {@link #getName()}。
     */
    default String getLabel() {
        return getName();
    }

    /**
     * 输出字段契约（自描述化）：声明工具执行结果 {@code data} 中的关键字段及其语义。
     * <p>
     * 编排层据此做通用渲染（摘要 / 结论 / 证据 / 业务实体缓存 / 计数指标），
     * 替代原先散落在编排层对具体输出键的字符串硬编码。
     * <p>
     * 旧工具可不实现，默认返回空列表（编排层回落至旧行为）。
     */
    default List<ToolOutputField> getOutputFields() {
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