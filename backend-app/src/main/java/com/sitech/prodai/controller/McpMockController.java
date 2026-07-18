package com.sitech.prodai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp-management")
public class McpMockController {

    @GetMapping("/tools")
    public Map<String, Object> tools(@RequestParam(required = false) String category) {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("ontology_infer", "本体字段推理", "ontology"));
        tools.add(tool("compliance_check", "合规校验", "compliance"));
        tools.add(tool("form_schema", "表单 Schema", "form"));
        if (category != null && !category.isBlank()) {
            tools.removeIf(t -> !category.equals(t.get("category")));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("tools", tools);
        body.put("total", tools.size());
        return body;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_tools", 3);
        data.put("categories", List.of("ontology", "compliance", "form"));
        data.put("total_calls", 0);
        data.put("success_calls", 0);
        data.put("failed_calls", 0);
        data.put("success_rate", 0.0);
        data.put("recent_logs_count", 0);
        return Map.of("success", true, "data", data);
    }

    @GetMapping("/categories")
    public Map<String, Object> categories() {
        return Map.of("success", true, "categories", List.of(
                Map.of("code", "ontology", "name", "本体", "count", 1),
                Map.of("code", "compliance", "name", "合规", "count", 1),
                Map.of("code", "form", "name", "表单", "count", 1)
        ));
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(required = false) String tool_name, @RequestParam(defaultValue = "100") int limit) {
        return Map.of("success", true, "data", List.of(), "total", 0);
    }

    @PostMapping("/tools/{toolName}/test")
    public Map<String, Object> test(@PathVariable String toolName, @RequestBody(required = false) Map<String, Object> args) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", toolName);
        data.put("ok", true);
        data.put("result", Map.of("message", "Mock 执行成功", "args", args == null ? Map.of() : args));
        data.put("timestamp", Instant.now().toString());
        return Map.of("success", true, "data", data);
    }

    @GetMapping("/external-tools")
    public Map<String, Object> externalTools() {
        return Map.of("success", true, "data", List.of(), "total", 0);
    }

    private Map<String, Object> tool(String name, String label, String category) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("label", label);
        t.put("category", category);
        t.put("description", label + "（Mock）");
        t.put("enabled", true);
        return t;
    }
}