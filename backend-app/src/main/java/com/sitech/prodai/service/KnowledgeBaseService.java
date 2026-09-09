package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.kb.InMemoryVectorSearch;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库内存服务。演示/生产同一实现，种子由 {@code prodai.kb.seed-path} 决定。
 * <p>
 * 检索供给（方案 §6-B6）：内嵌向量检索（{@link InMemoryVectorSearch}，字符 bigram
 * TF-IDF + 余弦相似度），替换 v0 词面 contains 伪相似度（固定 0.85）；文档增删时
 * 同步维护向量索引。生产文档规模化时以同 API 实现替换为外部向量库，本服务零改动。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /** 检索 Top-K 上限（qa 引用聚合口径）。 */
    private static final int TOP_K = 3;

    private final Map<String, Map<String, Object>> docs = new ConcurrentHashMap<>();
    private final ProdAiProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final InMemoryVectorSearch vectorSearch;

    public KnowledgeBaseService(ProdAiProperties properties,
                                ResourceLoader resourceLoader,
                                ObjectMapper objectMapper,
                                InMemoryVectorSearch vectorSearch) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.vectorSearch = vectorSearch;
    }

    @PostConstruct
    public void loadSeed() {
        String path = properties.getKb().getSeedPath();
        if (path == null || path.isBlank()) {
            log.info("[KnowledgeBaseService] kb.seed-path 未配置，空库启动");
            return;
        }
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException("KB seed not found: " + path);
            }
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> seed = objectMapper.readValue(in, new TypeReference<>() {});
                Object raw = seed.get("documents");
                if (raw instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Map<String, Object> doc = new LinkedHashMap<>();
                            m.forEach((k, v) -> doc.put(String.valueOf(k), v));
                            String id = String.valueOf(doc.getOrDefault("id", "doc-" + UUID.randomUUID().toString().substring(0, 8)));
                            doc.put("id", id);
                            doc.putIfAbsent("createdAt", Instant.now().toString());
                            docs.put(id, doc);
                            vectorSearch.upsert(id, String.valueOf(doc.get("title")), String.valueOf(doc.get("content")));
                        }
                    }
                }
            }
            log.info("[KnowledgeBaseService] loaded {} docs from {} (vector index built)", docs.size(), path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load KB seed: " + path, e);
        }
    }

    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_entries", docs.size());
        data.put("total_sessions", 0);
        data.put("utilization", docs.isEmpty() ? 0.0 : Math.min(1.0, docs.size() / 100.0));
        return Map.of("success", true, "data", data);
    }

    public Map<String, Object> add(Map<String, Object> body) {
        String id = "doc-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("title", body.getOrDefault("title", "未命名"));
        d.put("content", body.getOrDefault("content", ""));
        d.put("source", body.getOrDefault("source", ""));
        d.put("importance", body.getOrDefault("importance", 1.0));
        d.put("createdAt", Instant.now().toString());
        docs.put(id, d);
        vectorSearch.upsert(id, String.valueOf(d.get("title")), String.valueOf(d.get("content")));
        return Map.of("success", true, "data", d);
    }

    public Map<String, Object> search(Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", ""));
        // 向量检索（§6-B6）：余弦相似度降序 Top-K；空查询返回空集（不冒充全量）
        List<Map<String, Object>> ranked = vectorSearch.topK(query, TOP_K);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> hit : ranked) {
            Map<String, Object> doc = docs.get(String.valueOf(hit.get("id")));
            if (doc != null) {
                Map<String, Object> row = new LinkedHashMap<>(doc);
                row.put("similarity", hit.get("similarity"));
                results.add(row);
            }
        }
        return Map.of("success", true, "data", results);
    }

    public Map<String, Object> qa(Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", body.getOrDefault("question", "")));
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map<String, Object> hit : vectorSearch.topK(query, TOP_K)) {
            Map<String, Object> doc = docs.get(String.valueOf(hit.get("id")));
            if (doc != null) {
                hits.add(doc);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (hits.isEmpty()) {
            data.put("answer", docs.isEmpty()
                    ? "知识库为空，请配置 prodai.kb.seed-path 或通过 /api/kb/add 写入文档。"
                    : "未命中相关文档，请换关键词检索。");
            data.put("refs", List.of());
        } else {
            Map<String, Object> top = hits.get(0);
            data.put("answer", "基于文档《" + top.get("title") + "》：" + top.get("content"));
            data.put("refs", hits.stream().limit(3).map(d -> Map.of(
                    "id", d.get("id"),
                    "title", d.get("title")
            )).toList());
        }
        return Map.of("success", true, "data", data);
    }

    public Map<String, Object> get(String entryId) {
        Map<String, Object> d = docs.get(entryId);
        if (d == null) {
            return Map.of("success", false, "message", "not found");
        }
        return Map.of("success", true, "data", d);
    }

    public Map<String, Object> delete(String entryId) {
        docs.remove(entryId);
        vectorSearch.remove(entryId);
        return Map.of("success", true, "message", "deleted");
    }
}
