package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从配置的 RDF 种子文件灌入内存本体库。
 * <p>
 * 演示 / 生产同一套加载逻辑：{@code prodai.ontology.rdf-seed-path} 有值则加载，
 * 为空则跳过（生产默认不灌数）。演示数据写在 {@code ontology/rdf_seed.json}。
 */
@Component
@Order(10)
public class OntologySeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OntologySeedLoader.class);

    private final Rdf4jOntologyStore rdf4jStore;
    private final ProdAiProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public OntologySeedLoader(Rdf4jOntologyStore rdf4jStore,
                              ProdAiProperties properties,
                              ResourceLoader resourceLoader,
                              ObjectMapper objectMapper) {
        this.rdf4jStore = rdf4jStore;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        String path = properties.getOntology().getRdfSeedPath();
        if (path == null || path.isBlank()) {
            log.info("[OntologySeedLoader] rdf-seed-path 未配置，跳过 RDF 灌数");
            return;
        }
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException("RDF seed not found: " + path);
            }
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> seed = objectMapper.readValue(in, new TypeReference<>() {});
                applySeed(seed);
            }
            log.info("[OntologySeedLoader] loaded RDF seed from {}", path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RDF seed: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void applySeed(Map<String, Object> seed) {
        for (String className : stringList(seed.get("classes"))) {
            rdf4jStore.addClass(className);
        }
        for (String property : stringList(seed.get("properties"))) {
            rdf4jStore.addProperty(property);
        }
        Object rawInstances = seed.get("instances");
        if (!(rawInstances instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            m.forEach((k, v) -> row.put(String.valueOf(k), v));
            String id = str(row.get("id"));
            String type = str(row.get("type"));
            if (id.isBlank() || type.isBlank()) {
                continue;
            }
            Map<String, Object> facts = new LinkedHashMap<>();
            Object factsObj = row.get("facts");
            if (factsObj instanceof Map<?, ?> fm) {
                fm.forEach((k, v) -> facts.put(String.valueOf(k), v));
            }
            facts.put("type", type);
            rdf4jStore.addInstance(toUri(id), type, facts);
        }
    }

    private String toUri(String id) {
        if (id.startsWith("http://") || id.startsWith("https://")) {
            return id;
        }
        return properties.getOntology().normalizedBaseIri() + id;
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(this::str).filter(s -> !s.isBlank()).toList();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
