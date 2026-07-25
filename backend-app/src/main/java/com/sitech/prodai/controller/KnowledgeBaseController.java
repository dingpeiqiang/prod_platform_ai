package com.sitech.prodai.controller;

import com.sitech.prodai.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 知识库 API。数据来自 {@link KnowledgeBaseService}（种子配置 / 运行时写入）。
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return knowledgeBaseService.stats();
    }

    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        return knowledgeBaseService.add(body == null ? Map.of() : body);
    }

    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, Object> body) {
        return knowledgeBaseService.search(body == null ? Map.of() : body);
    }

    @PostMapping("/qa")
    public Map<String, Object> qa(@RequestBody Map<String, Object> body) {
        return knowledgeBaseService.qa(body == null ? Map.of() : body);
    }

    @GetMapping("/document/{entryId}")
    public Map<String, Object> get(@PathVariable String entryId) {
        return knowledgeBaseService.get(entryId);
    }

    @DeleteMapping("/document/{entryId}")
    public Map<String, Object> delete(@PathVariable String entryId) {
        return knowledgeBaseService.delete(entryId);
    }
}
