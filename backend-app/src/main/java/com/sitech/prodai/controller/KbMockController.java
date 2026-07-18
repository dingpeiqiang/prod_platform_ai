package com.sitech.prodai.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/kb")
public class KbMockController {

    private final Map<String, Map<String, Object>> docs = new ConcurrentHashMap<>();

    public KbMockController() {
        String id = "doc-demo-1";
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("title", "产商品配置知识（Mock）");
        d.put("content", "家庭融合套餐配置需校验月费、带宽、互斥组与合约约束。");
        d.put("source", "mock");
        d.put("importance", 1.0);
        d.put("createdAt", Instant.now().toString());
        docs.put(id, d);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_entries", docs.size());
        data.put("total_sessions", 0);
        data.put("utilization", docs.isEmpty() ? 0.0 : Math.min(1.0, docs.size() / 100.0));
        return Map.of("success", true, "data", data);
    }

    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        String id = "doc-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("title", body.getOrDefault("title", "未命名"));
        d.put("content", body.getOrDefault("content", ""));
        d.put("source", body.getOrDefault("source", ""));
        d.put("importance", body.getOrDefault("importance", 1.0));
        d.put("createdAt", Instant.now().toString());
        docs.put(id, d);
        return Map.of("success", true, "data", d);
    }

    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", ""));
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> d : docs.values()) {
            String hay = String.valueOf(d.get("title")) + String.valueOf(d.get("content"));
            if (query.isBlank() || hay.contains(query)) {
                Map<String, Object> row = new LinkedHashMap<>(d);
                row.put("similarity", 0.85);
                results.add(row);
            }
        }
        return Map.of("success", true, "data", results);
    }

    @PostMapping("/qa")
    public Map<String, Object> qa(@RequestBody Map<String, Object> body) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", "（Mock）知识库问答尚未接通检索增强，请后续替换实现。");
        data.put("refs", List.of());
        return Map.of("success", true, "data", data);
    }

    @GetMapping("/document/{entryId}")
    public Map<String, Object> get(@PathVariable String entryId) {
        Map<String, Object> d = docs.get(entryId);
        if (d == null) return Map.of("success", false, "message", "not found");
        return Map.of("success", true, "data", d);
    }

    @DeleteMapping("/document/{entryId}")
    public Map<String, Object> delete(@PathVariable String entryId) {
        docs.remove(entryId);
        return Map.of("success", true, "message", "deleted");
    }
}