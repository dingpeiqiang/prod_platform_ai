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
     * 场景可见性自声明（能力注册表单源）：本工具在哪些对话场景（scene）下对 LLM 可见。
     * <p>
     * 取值与 {@code SessionContext#getScene()} 对齐：{@code "rd"}（产商品研发）、
     * {@code "ops"}（运营分析）等；跨场景工具返回多个场景。
     * <p>
     * 白名单守门语义（防 LLM 编造能力）不变，仅从四处硬编码 Set 收敛为
     * 「工具自声明 + {@link AgentCapabilityRegistry} 统一读取」：
     * 新增工具只需实现本方法，无需再同步修改理解层/表达层白名单。
     * <p>
     * 默认空集（选择性可见）：未声明场景的工具不出现在任何场景能力清单中。
     */
    default java.util.Set<String> getScenes() {
        return Collections.emptySet();
    }

    /**
     * 执行工具。
     *
     * @param params 工具参数
     * @return 执行结果
     */
    ExecutionResult execute(Map<String, Object> params);
}