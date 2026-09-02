package com.sitech.prodai.controller;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.ToolExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工具直接执行端点：供可视化工作流编辑器（及未来的流程引擎）调用真实 AgentTool。
 * <p>
 * 设计要点（见《固定流程引擎设计文档》§2）：
 * - 工具注册表复用 Agent 体系（AgentTool 注入），不重复建设；
 * - 入参为结构化 Map，不接收自由文本（自由文本属于 Agent 对话入口，方案 §12.2 边界红线）；
 * - MCP 外部工具当前后端无真实连通，返回明确失败而非模拟成功。
 */
@RestController
@RequestMapping("/api/v1/agent-tools")
public class ToolExecutionController {

    private final ToolExecutionService toolExecutionService;

    public ToolExecutionController(ToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    /** 已注册工具清单（名称/描述/参数契约/输出契约），供编辑器工具节点选择与参数表单渲染。 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return toolExecutionService.listTools();
    }

    /** 执行单个工具：params 为结构化入参，返回 ExecutionResult 同构 JSON。 */
    @PostMapping("/{toolName}/execute")
    public ApiResponse<Map<String, Object>> execute(@PathVariable String toolName,
                                                    @RequestBody(required = false) Map<String, Object> params) {
        ExecutionResult result = toolExecutionService.execute(toolName, params == null ? Map.of() : params);
        if (result == null) {
            return ApiResponse.fail("工具不存在: " + toolName);
        }
        return ApiResponse.ok(toolExecutionService.toMap(result));
    }
}
