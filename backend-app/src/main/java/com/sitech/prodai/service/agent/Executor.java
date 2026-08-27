package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;

import java.util.List;

/**
 * 执行层 - 说对话。
 * 执行查询计划，调用底层工具。
 */
public interface Executor {

    /**
     * 执行查询计划，调用所需的工具。
     *
     * @param plan 查询计划
     * @return 各工具的执行结果列表
     */
    List<ExecutionResult> execute(QueryPlan plan);
}