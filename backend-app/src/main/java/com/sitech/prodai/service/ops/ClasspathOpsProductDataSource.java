package com.sitech.prodai.service.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/** classpath / 本地文件事实图（含 demo mock_graph）。 */
@Component
public class ClasspathOpsProductDataSource implements OpsProductDataSource {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ProdAiProperties properties;

    public ClasspathOpsProductDataSource(ObjectMapper objectMapper,
                                         ResourceLoader resourceLoader,
                                         ProdAiProperties properties) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    @Override
    public String sourceId() {
        String path = properties.getOntology().getGraphPath();
        if (path == null || path.isBlank()) {
            return "empty";
        }
        return path.contains("mock_graph") ? "mock_graph" : "classpath";
    }

    @Override
    public Map<String, Object> loadRawGraph() {
        String path = properties.getOntology().getGraphPath();
        if (path == null || path.isBlank()) {
            return OpsProductGraphLoader.emptyGraph();
        }
        boolean isMock = path.contains("mock_graph");
        if (isMock && !properties.getOntology().isDemoEnabled()) {
            throw new IllegalStateException(
                    "Refuse to load mock_graph when prodai.ontology.demo-enabled=false. "
                            + "Enable demo (dev/demo profile) or set a real graph path / data-source=http.");
        }
        try {
            Resource resource = resourceLoader.getResource(path);
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, new TypeReference<>() {});
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load ontology graph from " + path + ": " + e.getMessage(), e);
        }
    }
}
