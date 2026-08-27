package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.service.agent.Executor;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.ExecStep;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认执行层实现。
 * <p>
 * 根据查询计划，按顺序调用已注册的工具。
 * <p>
 * 依赖编排（设计文档 3.5 节）：支持 ExecStep 声明参数来源
 * （direct / result / evidence / default），前序工具输出注入后续工具入参。
 * <p>
 * 降级策略（设计文档 3.6 节）：某工具失败 → 依赖它的下游步骤中止（依赖链语义），
 * 其他独立工具照常执行；失败信息经 errorMessage 透传给表达层。
 */
@Component
public class DefaultExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(DefaultExecutor.class);

    private final Map<String, AgentTool> toolMap;

    public DefaultExecutor(List<AgentTool> tools) {
        this.toolMap = new ConcurrentHashMap<>();
        for (AgentTool tool : tools) {
            this.toolMap.put(tool.getName(), tool);
            log.info("[DefaultExecutor] 注册工具: {} - {}", tool.getName(), tool.getDescription());
        }
    }

    @Override
    public List<ExecutionResult> execute(QueryPlan plan) {
        return execute(plan, null);
    }

    @Override
    public List<ExecutionResult> execute(QueryPlan plan, SessionContext context) {
        return execute(plan, context, null);
    }

    @Override
    public List<ExecutionResult> execute(QueryPlan plan, SessionContext context, StepListener listener) {
        List<ExecutionResult> results = new ArrayList<>();

        List<ExecStep> steps = resolveSteps(plan);
        if (steps.isEmpty()) {
            log.info("[DefaultExecutor] 查询计划无工具调用，跳过执行");
            return results;
        }

        // 步骤结果索引：tool 名 → 结果（供 result:<X>.<key> 来源解析）
        Map<String, ExecutionResult> stepResults = new LinkedHashMap<>();
        // 中止的工具集合：因上游失败而跳过的下游步骤
        for (ExecStep step : steps) {
            AgentTool tool = toolMap.get(step.getTool());
            if (tool == null) {
                log.warn("[DefaultExecutor] 未找到工具: {}", step.getTool());
                results.add(ExecutionResult.fail(step.getTool(), "未找到工具: " + step.getTool()));
                continue;
            }

            // 解析本步骤入参
            Map<String, Object> stepParams;
            try {
                stepParams = resolveStepParams(step, plan.getParams(), stepResults, context);
            } catch (DependencyFailedException e) {
                // 上游依赖失败 → 中止当前依赖链
                log.warn("[DefaultExecutor] 步骤 {} 因上游失败被中止: {}", step.getTool(), e.getMessage());
                results.add(ExecutionResult.fail(step.getTool(), "上游工具失败，已中止: " + e.getMessage()));
                continue;
            }

            log.info("[DefaultExecutor] 执行工具: {} params={}", step.getTool(), stepParams.keySet());
            // 流式进度：工具开始前即时回调（置于 try 外，监听器异常向上传播而非记为工具失败）
            if (listener != null) {
                listener.onStepStart(step.getTool());
            }
            long toolStart = System.currentTimeMillis();
            try {
                ExecutionResult result = tool.execute(stepParams);
                result.setExecutionTimeMs(System.currentTimeMillis() - toolStart);
                results.add(result);
                stepResults.put(step.getTool(), result);
                log.info("[DefaultExecutor] 工具执行完成: {} success={}", step.getTool(), result.isSuccess());
            } catch (Exception e) {
                log.error("[DefaultExecutor] 工具执行异常: {}", step.getTool(), e);
                ExecutionResult fail = ExecutionResult.fail(step.getTool(), "工具执行异常: " + e.getMessage());
                fail.setExecutionTimeMs(System.currentTimeMillis() - toolStart);
                results.add(fail);
                stepResults.put(step.getTool(), fail);
            }
            // 流式进度：该工具结束即回调（无论成败），调用方据此实时推送 done/error
            if (listener != null) {
                listener.onStepComplete(results.get(results.size() - 1));
            }
        }

        return results;
    }

    /**
     * 将计划的 tools 扁平视图归一为步骤列表（steps 为空时降级为 direct 全参传递）。
     */
    private List<ExecStep> resolveSteps(QueryPlan plan) {
        if (plan.getSteps() != null && !plan.getSteps().isEmpty()) {
            return plan.getSteps();
        }
        if (plan.getTools() == null) {
            return List.of();
        }
        // 兼容旧计划：每个工具一个步骤，参数全部 direct 传入
        return plan.getTools().stream().map(ExecStep::new).toList();
    }

    /**
     * 解析单个步骤的入参：literalParams + paramMappings 各来源取值。
     *
     * @throws DependencyFailedException 依赖的 result 来源对应的前序步骤失败时
     */
    private Map<String, Object> resolveStepParams(ExecStep step,
                                                  Map<String, Object> planParams,
                                                  Map<String, ExecutionResult> stepResults,
                                                  SessionContext context) {
        Map<String, Object> params = new LinkedHashMap<>(step.getLiteralParams());
        for (Map.Entry<String, String> entry : step.getParamMappings().entrySet()) {
            String paramName = entry.getKey();
            String source = entry.getValue();
            Object value = resolveSource(source, planParams, stepResults, context);
            if (value != null) {
                params.put(paramName, value);
            }
        }
        // direct 兜底：无映射的参数从 plan.params 直接透传（保持旧行为）
        if (planParams != null) {
            for (Map.Entry<String, Object> entry : planParams.entrySet()) {
                params.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return params;
    }

    private Object resolveSource(String source,
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
            ExecutionResult prior = stepResults.get(toolName);
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
        log.warn("[DefaultExecutor] 未知参数来源格式: {}", source);
        return null;
    }

    /** 上游依赖失败导致的步骤中止（内部控制流信号） */
    private static class DependencyFailedException extends RuntimeException {
        DependencyFailedException(String message) {
            super(message);
        }
    }

    /**
     * 获取已注册的工具列表（供外部查看）。
     */
    public Map<String, AgentTool> getToolMap() {
        return toolMap;
    }
}
