package com.sitech.prodai.service.common;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.SessionContext;

import java.util.Map;

/**
 * 参数来源解析公共组件（P3-2，设计文档 §10 组件共享边界）。
 * <p>
 * 从 {@code DefaultExecutor.resolveSource} 抽取的纯函数式来源解析逻辑
 * （无状态、无 LLM 依赖、无编排语义），四来源协议：
 * <pre>
 *   direct:&lt;name&gt;        → 计划入参 planParams 取值
 *   result:&lt;tool&gt;.&lt;key&gt;  → 前序步骤结果取值（依赖未执行/失败抛 {@link DependencyFailedException}）
 *   evidence:&lt;key&gt;       → 会话上下文 cachedEvidence 取值
 *   default:&lt;literal&gt;    → 字面值原样返回
 * </pre>
 * 固定流程引擎的 flow 节点（{{nodeId.output.field}} 语义）如需对齐四来源协议可复用本类；
 * 不强制迁移引擎侧已有的 resolveValue（两者语义不同源，抽取原则：只抽纯函数）。
 */
public final class ParamResolver {

    private ParamResolver() {
    }

    /**
     * 按来源声明解析单值。
     *
     * @param source      来源声明（direct:/result:/evidence:/default: 前缀，空返回 null）
     * @param planParams  计划入参（direct: 来源）
     * @param stepResults 前序步骤结果（result: 来源；依赖未执行或失败抛异常）
     * @param context     会话上下文（evidence: 来源，可为 null）
     * @return 解析值（来源无值返回 null）
     * @throws DependencyFailedException result: 来源对应的前序步骤未执行或执行失败
     */
    public static Object resolve(String source,
                                 Map<String, Object> planParams,
                                 Map<String, ExecutionResult> stepResults,
                                 SessionContext context) {
        if (source == null || source.isBlank()) {
            return null;
        }
        if (source.startsWith("direct:")) {
            String name = source.substring("direct:".length());
            return planParams != null ? planParams.get(name) : null;
        }
        if (source.startsWith("result:")) {
            // result:<tool>.<key>
            String body = source.substring("result:".length());
            int dot = body.indexOf('.');
            String toolName = dot > 0 ? body.substring(0, dot) : body;
            String key = dot > 0 ? body.substring(dot + 1) : null;
            ExecutionResult prior = stepResults != null ? stepResults.get(toolName) : null;
            if (prior == null) {
                throw new DependencyFailedException("前序工具 " + toolName + " 尚未执行");
            }
            if (!prior.isSuccess()) {
                throw new DependencyFailedException("前序工具 " + toolName + " 执行失败");
            }
            if (key == null || key.isBlank()) {
                return prior.getData();
            }
            return prior.getData() != null ? prior.getData().get(key) : null;
        }
        if (source.startsWith("evidence:")) {
            String key = source.substring("evidence:".length());
            return context != null && context.getCachedEvidence() != null
                    ? context.getCachedEvidence().get(key) : null;
        }
        if (source.startsWith("default:")) {
            return source.substring("default:".length());
        }
        return null;
    }
}
