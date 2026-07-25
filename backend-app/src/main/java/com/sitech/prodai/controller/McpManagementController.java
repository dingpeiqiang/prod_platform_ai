package com.sitech.prodai.controller;

import com.sitech.prodai.service.McpManagementService;
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
@RestController
@RequestMapping("/api/v1/mcp-management")
public class McpManagementController {

    private final McpManagementService mcpManagementService;

    public McpManagementController(McpManagementService mcpManagementService) {
        this.mcpManagementService = mcpManagementService;
    }

    @GetMapping("/tools")
    public Map<String, Object> tools(@RequestParam(required = false) String category) {
        return mcpManagementService.listTools(category);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return mcpManagementService.stats();
    }

    @GetMapping("/categories")
    public Map<String, Object> categories() {
        return mcpManagementService.categories();
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(required = false) String tool_name,
                                    @RequestParam(defaultValue = "100") int limit) {
        return mcpManagementService.logs(tool_name, limit);
    }

    @PostMapping("/tools/{toolName}/test")
    public Map<String, Object> test(@PathVariable String toolName,
                                    @RequestBody(required = false) Map<String, Object> args) {
        return mcpManagementService.testTool(toolName, args);
    }

    @GetMapping("/external-tools")
    public Map<String, Object> externalTools() {
        return mcpManagementService.externalTools();
    }

    @GetMapping("/external-tools/{toolName}")
    public Map<String, Object> getExternalTool(@PathVariable String toolName) {
        return mcpManagementService.getExternalTool(toolName);
    }

    @PostMapping("/external-tools")
    public Map<String, Object> createExternalTool(@RequestBody Map<String, Object> body) {
        return mcpManagementService.createExternalTool(body == null ? Map.of() : body);
    }

    @PutMapping("/external-tools/{toolName}")
    public Map<String, Object> updateExternalTool(@PathVariable String toolName,
                                                  @RequestBody Map<String, Object> body) {
        return mcpManagementService.updateExternalTool(toolName, body == null ? Map.of() : body);
    }

    @DeleteMapping("/external-tools/{toolName}")
    public Map<String, Object> deleteExternalTool(@PathVariable String toolName) {
        return mcpManagementService.deleteExternalTool(toolName);
    }

    @PostMapping("/external-tools/{toolName}/toggle")
    public Map<String, Object> toggleExternalTool(@PathVariable String toolName,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        boolean enabled = body == null || !Boolean.FALSE.equals(body.get("enabled"));
        return mcpManagementService.toggleExternalTool(toolName, enabled);
    }

    /** OpenAPI 解析/导入：占位，避免前端 404；完整解析可后续接专用服务。 */
    @PostMapping("/external-tools/parse")
    public Map<String, Object> parseOpenApi(@RequestBody Map<String, Object> body) {
        return Map.of("success", false, "message", "OpenAPI parse not implemented yet", "tools", java.util.List.of());
    }

    @PostMapping("/external-tools/import")
    public Map<String, Object> importOpenApi(@RequestBody Map<String, Object> body) {
        return Map.of("success", false, "message", "OpenAPI import not implemented yet", "tools", java.util.List.of());
    }
}
