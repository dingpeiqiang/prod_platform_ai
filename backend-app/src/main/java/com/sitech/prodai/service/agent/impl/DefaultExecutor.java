package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.service.agent.Executor;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认执行层实现。
 * <p>
 * 根据查询计划，按顺序调用已注册的工具。
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
        List<ExecutionResult> results = new ArrayList<>();

        if (plan.getTools() == null || plan.getTools().isEmpty()) {
            log.info("[DefaultExecutor] 查询计划无工具调用，跳过执行");
            return results;
        }

        for (String toolName : plan.getTools()) {
            AgentTool tool = toolMap.get(toolName);
            if (tool == null) {
                log.warn("[DefaultExecutor] 未找到工具: {}", toolName);
                results.add(ExecutionResult.fail(toolName, "未找到工具: " + toolName));
                continue;
            }

            log.info("[DefaultExecutor] 执行工具: {}", toolName);
            try {
                ExecutionResult result = tool.execute(plan.getParams());
                results.add(result);
                log.info("[DefaultExecutor] 工具执行完成: {} success={}", toolName, result.isSuccess());
            } catch (Exception e) {
                log.error("[DefaultExecutor] 工具执行异常: {}", toolName, e);
                results.add(ExecutionResult.fail(toolName, "工具执行异常: " + e.getMessage()));
            }
        }

        return results;
    }

    /**
     * 获取已注册的工具列表（供外部查看）。
     */
    public Map<String, AgentTool> getToolMap() {
        return toolMap;
    }
}