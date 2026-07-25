package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 启动时按 {@code prodai.ontology.ttl-path} 加载 Turtle。
 * 与 {@link OntologySeedLoader} 共用同一套存储；路径为空则跳过。
 */
@Component
@Order(20)
public class OntologyTtlLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OntologyTtlLoader.class);

    private final Rdf4jOntologyStore rdf4jStore;
    private final ProdAiProperties properties;
    private final ResourceLoader resourceLoader;

    public OntologyTtlLoader(Rdf4jOntologyStore rdf4jStore,
                             ProdAiProperties properties,
                             ResourceLoader resourceLoader) {
        this.rdf4jStore = rdf4jStore;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String path = properties.getOntology().getTtlPath();
        if (path == null || path.isBlank()) {
            log.info("[OntologyTtlLoader] ttl-path 未配置，跳过 TTL 导入");
            return;
        }
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("TTL not found: " + path);
        }
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        MapResult result = MapResult.of(rdf4jStore.importTtl(content, false));
        if (!result.success()) {
            throw new IllegalStateException("TTL import failed: " + result.message());
        }
        log.info("[OntologyTtlLoader] loaded TTL from {} — {}", path, result.message());
    }

    private record MapResult(boolean success, String message) {
        static MapResult of(java.util.Map<String, Object> m) {
            boolean ok = Boolean.TRUE.equals(m.get("success"));
            return new MapResult(ok, String.valueOf(m.getOrDefault("message", "")));
        }
    }
}
