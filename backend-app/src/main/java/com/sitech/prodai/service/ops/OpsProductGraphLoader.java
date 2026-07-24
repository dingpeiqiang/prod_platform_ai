package com.sitech.prodai.service.ops;

import com.sitech.prodai.config.ProdAiProperties;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按配置选择 classpath / http / empty 数据源。
 */
@Service
public class OpsProductGraphLoader {

    private final ProdAiProperties properties;
    private final ClasspathOpsProductDataSource classpathSource;
    private final HttpOpsProductDataSource httpSource;

    public OpsProductGraphLoader(ProdAiProperties properties,
                                 ClasspathOpsProductDataSource classpathSource,
                                 HttpOpsProductDataSource httpSource) {
        this.properties = properties;
        this.classpathSource = classpathSource;
        this.httpSource = httpSource;
    }

    public record LoadedGraph(Map<String, Object> graph, String sourceId) {}

    public LoadedGraph load() {
        String mode = normalizeMode(properties.getOntology().getDataSource());
        return switch (mode) {
            case "http" -> new LoadedGraph(httpSource.loadRawGraph(), httpSource.sourceId());
            case "empty" -> new LoadedGraph(emptyGraph(), "empty");
            default -> new LoadedGraph(classpathSource.loadRawGraph(), classpathSource.sourceId());
        };
    }

    public static Map<String, Object> emptyGraph() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("shelfOfferings", List.of());
        raw.put("opsGraph", Map.of());
        raw.put("bizScenarios", Map.of());
        raw.put("templates", Map.of());
        raw.put("equityGiftWhitelist", List.of());
        raw.put("riskRuleDefaults", Map.of());
        return raw;
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "classpath";
        }
        return mode.trim().toLowerCase();
    }
}
