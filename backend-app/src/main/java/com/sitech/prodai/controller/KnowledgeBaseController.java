package com.sitech.prodai.controller;

import com.sitech.prodai.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "知识库", description = "文件知识库：知识条目添加、检索问答、文档明细管理")
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Operation(summary = "知识库统计", description = "返回条目总数/切片总数/最近更新时间")
@GetMapping("/stats")
    public Map<String, Object> stats() {
        return knowledgeBaseService.stats();
    }

    @Operation(summary = "新增知识条目", description = "body: title/content/metadata，文本切片后入库")
@PostMapping("/add")
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        return knowledgeBaseService.add(body == null ? Map.of() : body);
    }

    @Operation(summary = "知识检索", description = "按关键词检索知识切片，返回命中片段与得分")
@PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, Object> body) {
        return knowledgeBaseService.search(body == null ? Map.of() : body);
    }

    @Operation(summary = "知识问答", description = "基于知识库检索增强的问答接口")
@PostMapping("/qa")
    public Map<String, Object> qa(@RequestBody Map<String, Object> body) {
        return knowledgeBaseService.qa(body == null ? Map.of() : body);
    }

    @Operation(summary = "文档详情", description = "返回知识条目原文与切片明细")
@GetMapping("/document/{entryId}")
    public Map<String, Object> get(@PathVariable String entryId) {
        return knowledgeBaseService.get(entryId);
    }

    @Operation(summary = "删除知识条目", description = "按 entryId 删除条目及其切片")
@DeleteMapping("/document/{entryId}")
    public Map<String, Object> delete(@PathVariable String entryId) {
        return knowledgeBaseService.delete(entryId);
    }
}
