package com.sitech.prodai.service;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具直接执行服务：可视化工作流编辑器 / 流程引擎 tool 节点的统一工具入口。
 * <p>
 * 与 {@code DefaultExecutor} 共享同一份 AgentTool 注册逻辑（按 Spring 注入的 List 构建工具表），
 * 但执行语义独立：单工具直接执行，无 plan/steps 依赖编排（那是 Agent 对话入口的职责）。
 */
@Service
public class ToolExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionService.class);

    private final Map<String, AgentTool> toolMap = new ConcurrentHashMap<>();

    public ToolExecutionService(List<AgentTool> tools) {
        if (tools != null) {
            for (AgentTool tool : tools) {
                this.toolMap.put(tool.getName(), tool);
            }
            log.info("[ToolExecutionService] 注册工具 {} 个: {}", toolMap.size(), toolMap.keySet());
        }
    }

    /** 工具清单（含参数/输出契约），供编辑器渲染工具选择器与参数表单。 */
    public ApiResponse<List<Map<String, Object>>> listTools() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AgentTool tool : toolMap.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.getName());
            item.put("label", tool.getLabel());
            item.put("description", tool.getDescription());
            item.put("params", tool.getParams());
            item.put("output_fields", tool.getOutputFields());
            items.add(item);
        }
        return ApiResponse.ok(items);
    }

    /** 工具是否已注册（定义期守门：发布流程前校验 tool 节点引用的工具存在）。 */
    public boolean containsTool(String toolName) {
        return toolName != null && toolMap.containsKey(toolName);
    }

    /** 按名取工具（定义期守门：读取工具参数/输出契约做静态校验）；未注册返回 null。 */
    public AgentTool getTool(String toolName) {
        return toolName == null ? null : toolMap.get(toolName);
    }

    /** 执行工具；工具不存在返回 null（由控制器转为显式失败）。 */
    public ExecutionResult execute(String toolName, Map<String, Object> params) {
        AgentTool tool = toolMap.get(toolName);
        if (tool == null) {
            log.warn("[ToolExecutionService] 未找到工具: {}", toolName);
            return null;
        }
        long start = System.currentTimeMillis();
        try {
            ExecutionResult result = tool.execute(params);
            result.setParams(params);
            result.setExecutionTimeMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("[ToolExecutionService] 工具执行异常: {}", toolName, e);
            ExecutionResult fail = ExecutionResult.fail(toolName, "工具执行异常: " + e.getMessage());
            fail.setParams(params);
            fail.setExecutionTimeMs(System.currentTimeMillis() - start);
            return fail;
        }
    }

    /** ExecutionResult → JSON Map（snake_case 契约）。 */
    public Map<String, Object> toMap(ExecutionResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", result.isSuccess());
        map.put("tool_name", result.getToolName());
        map.put("data", result.getData());
        map.put("error_message", result.getErrorMessage());
        map.put("execution_time_ms", result.getExecutionTimeMs());
        if (result.getParams() != null) {
            map.put("params", result.getParams());
        }
        return map;
    }
}
