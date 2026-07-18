package com.sitech.prodai.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowMockController {

    private final Map<String, Map<String, Object>> workflows = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1);

    public WorkflowMockController() {
        putDemo("offering_config_workflow", "产商品配置工作流", "product");
        putDemo("tariff_filing_workflow", "资费备案工作流", "tariff");
    }

    private void putDemo(String code, String name, String category) {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("id", seq.getAndIncrement());
        w.put("workflowCode", code);
        w.put("workflowName", name);
        w.put("description", name + "（Mock）");
        w.put("category", category);
        w.put("tags", List.of("mock"));
        w.put("priority", 10);
        w.put("isActive", true);
        w.put("isInLibrary", true);
        w.put("workflowData", Map.of("nodes", List.of(), "edges", List.of()));
        w.put("createdAt", Instant.now().toString());
        w.put("updatedAt", Instant.now().toString());
        w.put("executionCount", 0);
        workflows.put(code, w);
    }

    @GetMapping("/categories")
    public Map<String, Object> categories() {
        return ok(List.of(
                cat("general", "通用"),
                cat("product", "产商品"),
                cat("tariff", "资费"),
                cat("ops", "运营")
        ));
    }

    @GetMapping("")
    public Map<String, Object> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String keyword) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> w : workflows.values()) {
            if (category != null && !category.isBlank() && !Objects.equals(category, w.get("category"))) continue;
            if (isActive != null && !Objects.equals(isActive, w.get("isActive"))) continue;
            if (keyword != null && !keyword.isBlank()) {
                String hay = String.valueOf(w.get("workflowCode")) + String.valueOf(w.get("workflowName"));
                if (!hay.contains(keyword)) continue;
            }
            data.add(new LinkedHashMap<>(w));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        body.put("total", data.size());
        return body;
    }

    @GetMapping("/{workflowCode}")
    public Map<String, Object> get(@PathVariable String workflowCode) {
        Map<String, Object> w = workflows.get(workflowCode);
        if (w == null) return fail("workflow not found");
        return ok(new LinkedHashMap<>(w));
    }

    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("workflowCode", "wf_" + seq.getAndIncrement()));
        if (workflows.containsKey(code)) return fail("already exists");
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("id", seq.getAndIncrement());
        w.put("workflowCode", code);
        w.put("workflowName", body.getOrDefault("workflowName", code));
        w.put("description", body.getOrDefault("description", ""));
        w.put("category", body.getOrDefault("category", "general"));
        w.put("tags", body.getOrDefault("tags", List.of()));
        w.put("priority", body.getOrDefault("priority", 10));
        w.put("isActive", body.getOrDefault("isActive", true));
        w.put("isInLibrary", body.getOrDefault("isInLibrary", false));
        w.put("workflowData", body.getOrDefault("workflowData", Map.of()));
        w.put("createdAt", Instant.now().toString());
        w.put("updatedAt", Instant.now().toString());
        w.put("executionCount", 0);
        workflows.put(code, w);
        return ok(new LinkedHashMap<>(w));
    }

    @PutMapping("/{workflowCode}")
    public Map<String, Object> update(@PathVariable String workflowCode, @RequestBody Map<String, Object> body) {
        Map<String, Object> w = workflows.get(workflowCode);
        if (w == null) return fail("workflow not found");
        body.forEach((k, v) -> {
            if (!"workflowCode".equals(k) && !"id".equals(k)) w.put(k, v);
        });
        w.put("updatedAt", Instant.now().toString());
        return ok(new LinkedHashMap<>(w));
    }

    @DeleteMapping("/{workflowCode}")
    public Map<String, Object> delete(@PathVariable String workflowCode) {
        if (!workflows.containsKey(workflowCode)) return fail("workflow not found");
        workflows.remove(workflowCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "deleted");
        return body;
    }

    @PostMapping("/{workflowCode}/toggle")
    public Map<String, Object> toggle(@PathVariable String workflowCode) {
        Map<String, Object> w = workflows.get(workflowCode);
        if (w == null) return fail("workflow not found");
        boolean active = Boolean.TRUE.equals(w.get("isActive"));
        w.put("isActive", !active);
        w.put("updatedAt", Instant.now().toString());
        return ok(new LinkedHashMap<>(w));
    }

    @GetMapping("/{workflowCode}/history")
    public Map<String, Object> history(@PathVariable String workflowCode) {
        if (!workflows.containsKey(workflowCode)) return fail("workflow not found");
        return ok(List.of());
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }

    private Map<String, Object> cat(String code, String name) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("code", code);
        c.put("name", name);
        return c;
    }
}