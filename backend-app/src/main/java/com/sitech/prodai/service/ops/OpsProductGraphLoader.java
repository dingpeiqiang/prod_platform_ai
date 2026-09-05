package com.sitech.prodai.service.ops;

import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按配置选择 classpath / http / empty / jdbc 数据源。
 * <p>jdbc（R5）：业务系统只读库直连（prodai.ontology.abox-source=jdbc），
 * 经 OpsGraphSchemaValidator 校验后走 last-known-good 守卫，失败回退现行图谱。
 */
@Service
public class OpsProductGraphLoader {

    private static final Logger log = LoggerFactory.getLogger(OpsProductGraphLoader.class);

    private final ProdAiProperties properties;
    private final ClasspathOpsProductDataSource classpathSource;
    private final HttpOpsProductDataSource httpSource;
    private final ObjectProvider<JdbcOpsProductDataSource> jdbcSourceProvider;

    public OpsProductGraphLoader(ProdAiProperties properties,
                                 ClasspathOpsProductDataSource classpathSource,
                                 HttpOpsProductDataSource httpSource) {
        this(properties, classpathSource, httpSource, null);
    }

    @Autowired
    public OpsProductGraphLoader(ProdAiProperties properties,
                                 ClasspathOpsProductDataSource classpathSource,
                                 HttpOpsProductDataSource httpSource,
                                 ObjectProvider<JdbcOpsProductDataSource> jdbcSourceProvider) {
        this.properties = properties;
        this.classpathSource = classpathSource;
        this.httpSource = httpSource;
        this.jdbcSourceProvider = jdbcSourceProvider;
    }

    public record LoadedGraph(Map<String, Object> graph, String sourceId) {}

    public LoadedGraph load() {
        String mode = normalizeMode(properties.getOntology().getDataSource());
        return switch (mode) {
            case "http" -> {
                Map<String, Object> raw = httpSource.loadRawGraph();
                yield new LoadedGraph(raw, httpSource.sourceId());
            }
            case "jdbc" -> {
                JdbcOpsProductDataSource jdbcSource = jdbcSourceProvider == null
                        ? null : jdbcSourceProvider.getIfAvailable();
                if (jdbcSource == null) {
                    throw new IllegalStateException(
                            "prodai.ontology.data-source=jdbc but JdbcOpsProductDataSource bean is absent "
                                    + "(check prodai.ontology.abox-source=jdbc).");
                }
                Map<String, Object> raw = jdbcSource.loadRawGraph();
                OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
                if (!vr.warnings().isEmpty()) {
                    log.warn("[OpsProductGraphLoader] jdbc ops-graph schema warnings: {}", vr.warnings());
                }
                if (!vr.ok()) {
                    throw new IllegalStateException(
                            "jdbc ops-graph schema invalid: " + String.join("; ", vr.errors()));
                }
                yield new LoadedGraph(vr.normalized(), jdbcSource.sourceId());
            }
            case "empty" -> new LoadedGraph(emptyGraph(), "empty");
            default -> {
                Map<String, Object> raw = classpathSource.loadRawGraph();
                OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
                if (!vr.warnings().isEmpty()) {
                    // classpath 缺键时软补齐
                }
                if (!vr.ok()) {
                    throw new IllegalStateException(
                            "classpath ops-graph schema invalid: " + String.join("; ", vr.errors()));
                }
                yield new LoadedGraph(vr.normalized(), classpathSource.sourceId());
            }
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
