package com.sitech.prodai.service.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

/**
 * 对外发布产商品事实图（ops-graph 契约）。
 * 只读本地/classpath 文件，不走 HTTP，避免本进程 data-source=http 自调用死循环。
 */
@Service
public class OpsGraphPublishService {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ProdAiProperties properties;

    public OpsGraphPublishService(ObjectMapper objectMapper,
                                  ResourceLoader resourceLoader,
                                  ProdAiProperties properties) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    /**
     * 返回与 {@code mock_graph} / {@code ops_graph_export} 同构的原始 JSON Map。
     */
    public Map<String, Object> loadPublishedGraph() {
        String path = resolvePath();
        if (path == null || path.isBlank()) {
            return OpsProductGraphLoader.emptyGraph();
        }
        boolean isMock = path.contains("mock_graph");
        if (isMock && !properties.getOntology().isDemoEnabled()) {
            throw new IllegalStateException(
                    "Refuse to publish mock_graph when prodai.ontology.demo-enabled=false. "
                            + "Set prodai.ontology.ops-graph-path to a real export.");
        }
        try {
            Resource resource = resourceLoader.getResource(path);
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> raw = objectMapper.readValue(in, new TypeReference<>() {});
                OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
                if (!vr.ok()) {
                    throw new IllegalStateException(
                            "Published ops-graph schema invalid from " + path + ": "
                                    + String.join("; ", vr.errors()));
                }
                return vr.normalized();
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish ops-graph from " + path + ": " + e.getMessage(), e);
        }
    }

    public String resolvedSourcePath() {
        String path = resolvePath();
        return path == null || path.isBlank() ? "empty" : path;
    }

    private String resolvePath() {
        String ops = properties.getOntology().getOpsGraphPath();
        if (ops != null && !ops.isBlank()) {
            return ops.trim();
        }
        String graph = properties.getOntology().getGraphPath();
        if (graph != null && !graph.isBlank()) {
            return graph.trim();
        }
        return "";
    }
}
