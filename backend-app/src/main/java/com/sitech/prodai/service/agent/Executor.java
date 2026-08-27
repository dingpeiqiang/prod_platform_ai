package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;

import java.util.List;

/**
 * 执行层 - 说对话。
 * 执行查询计划，调用底层工具。
 */
public interface Executor {

    /**
     * 步骤监听器：供流式编排感知执行进度（每工具开始/完成即时回调）。
     * <p>
     * 回调在工具执行边界触发，异常会向上传播中止整个执行（由调用方决定降级行为）。
     */
    interface StepListener {

        /** 单个工具开始执行前回调 */
        default void onStepStart(String toolName) {
        }

        /** 单个工具执行结束后回调（含成功与失败结果） */
        default void onStepComplete(ExecutionResult result) {
        }
    }

    /**
     * 执行查询计划，调用所需的工具。
     *
     * @param plan 查询计划
     * @return 各工具的执行结果列表
     */
    List<ExecutionResult> execute(QueryPlan plan);

    /**
     * 执行查询计划（带会话上下文，支持 evidence 参数来源与依赖编排）。
     *
     * @param plan    查询计划
     * @param context 会话上下文（可为 null）
     * @return 各工具的执行结果列表（失败步骤的下游被中止）
     */
    default List<ExecutionResult> execute(QueryPlan plan, SessionContext context) {
        return execute(plan);
    }

    /**
     * 执行查询计划（带步骤监听，供流式场景逐工具推送进度）。
     *
     * @param plan     查询计划
     * @param context  会话上下文（可为 null）
     * @param listener 步骤监听器（可为 null）
     * @return 各工具的执行结果列表（失败步骤的下游被中止）
     */
    default List<ExecutionResult> execute(QueryPlan plan, SessionContext context, StepListener listener) {
        return execute(plan, context);
    }
}
