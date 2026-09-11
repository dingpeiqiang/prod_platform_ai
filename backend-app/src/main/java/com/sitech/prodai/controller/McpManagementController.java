package com.sitech.prodai.controller;

import com.sitech.prodai.service.McpManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MCP 工具管理 API。内置工具来自 ToolRegistry，外部工具来自 DB（可配置种子）。
 */
@Tag(name = "MCP 工具管理", description = "MCP 工具注册表查询、调用统计/日志、外部 HTTP 工具 CRUD 与启停、OpenAPI 规范导入")
@RestController
@RequestMapping("/api/v1/mcp-management")
public class McpManagementController {

    private final McpManagementService mcpManagementService;

    public McpManagementController(McpManagementService mcpManagementService) {
        this.mcpManagementService = mcpManagementService;
    }

    @Operation(summary = "工具清单", description = "返回已注册的全部 MCP 工具（内置 AgentTool + 外部工具，含参数契约）")
@GetMapping("/tools")
    public Map<String, Object> tools(@RequestParam(required = false) String category) {
        return mcpManagementService.listTools(category);
    }

    @Operation(summary = "调用统计", description = "返回各工具调用次数/成功率/平均耗时聚合统计")
@GetMapping("/stats")
    public Map<String, Object> stats() {
        return mcpManagementService.stats();
    }

    @Operation(summary = "工具分类", description = "返回工具分类清单及其计数")
@GetMapping("/categories")
    public Map<String, Object> categories() {
        return mcpManagementService.categories();
    }

    @Operation(summary = "调用日志", description = "分页返回工具调用明细日志（时间/入参/出参/耗时/状态）")
@GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(required = false) String tool_name,
                                    @RequestParam(defaultValue = "100") int limit) {
        return mcpManagementService.logs(tool_name, limit);
    }

    @Operation(summary = "工具测试", description = "按给定入参真实执行一次工具调用，返回执行结果")
@PostMapping("/tools/{toolName}/test")
    public Map<String, Object> test(@PathVariable String toolName,
                                    @RequestBody(required = false) Map<String, Object> args) {
        return mcpManagementService.testTool(toolName, args);
    }

    @Operation(summary = "外部工具列表", description = "返回 DB 中登记的外部 HTTP 工具清单")
@GetMapping("/external-tools")
    public Map<String, Object> externalTools() {
        return mcpManagementService.externalTools();
    }

    @Operation(summary = "外部工具详情", description = "按 toolName 返回外部工具定义（URL/方法/鉴权/Schema）")
@GetMapping("/external-tools/{toolName}")
    public Map<String, Object> getExternalTool(@PathVariable String toolName) {
        return mcpManagementService.getExternalTool(toolName);
    }

    @Operation(summary = "创建外部工具", description = "body: tool_name/url/request_method/auth_type/input_schema 等")
@PostMapping("/external-tools")
    public Map<String, Object> createExternalTool(@RequestBody Map<String, Object> body) {
        return mcpManagementService.createExternalTool(body == null ? Map.of() : body);
    }

    @Operation(summary = "更新外部工具", description = "按 toolName 更新外部工具定义")
@PutMapping("/external-tools/{toolName}")
    public Map<String, Object> updateExternalTool(@PathVariable String toolName,
                                                  @RequestBody Map<String, Object> body) {
        return mcpManagementService.updateExternalTool(toolName, body == null ? Map.of() : body);
    }

    @Operation(summary = "删除外部工具", description = "按 toolName 删除外部工具登记")
@DeleteMapping("/external-tools/{toolName}")
    public Map<String, Object> deleteExternalTool(@PathVariable String toolName) {
        return mcpManagementService.deleteExternalTool(toolName);
    }

    @Operation(summary = "启停外部工具", description = "切换外部工具启用/禁用状态")
@PostMapping("/external-tools/{toolName}/toggle")
    public Map<String, Object> toggleExternalTool(@PathVariable String toolName,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        boolean enabled = body == null || !Boolean.FALSE.equals(body.get("enabled"));
        return mcpManagementService.toggleExternalTool(toolName, enabled);
    }

    /** OpenAPI 解析/导入：占位，避免前端 404；完整解析可后续接专用服务。 */
    @Operation(summary = "OpenAPI 规范解析", description = "提交 OpenAPI/Swagger 规范文本，解析为工具定义预览（导入前校验）")
@PostMapping("/external-tools/parse")
    public Map<String, Object> parseOpenApi(@RequestBody Map<String, Object> body) {
        return Map.of("success", false, "message", "OpenAPI parse not implemented yet", "tools", java.util.List.of());
    }

    @Operation(summary = "外部工具批量导入", description = "将解析后的工具定义批量落库登记")
@PostMapping("/external-tools/import")
    public Map<String, Object> importOpenApi(@RequestBody Map<String, Object> body) {
        return Map.of("success", false, "message", "OpenAPI import not implemented yet", "tools", java.util.List.of());
    }
}
