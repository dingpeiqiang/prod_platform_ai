package com.sitech.prodai.service.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 产商品中心 HTTP 事实图。约定 {@code GET {baseUrl}/ops-graph} 返回与 mock_graph 同构 JSON。
 */
@Component
public class HttpOpsProductDataSource implements OpsProductDataSource {

    private static final Logger log = LoggerFactory.getLogger(HttpOpsProductDataSource.class);

    private final ObjectMapper objectMapper;
    private final ProdAiProperties properties;

    public HttpOpsProductDataSource(ObjectMapper objectMapper, ProdAiProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String sourceId() {
        return "http";
    }

    @Override
    public Map<String, Object> loadRawGraph() {
        String base = properties.getOntology().getProductCenterBaseUrl();
        if (base == null || base.isBlank()) {
            log.warn("[HttpOpsProductDataSource] 未配置 product-center-base-url，返回空图");
            return OpsProductGraphLoader.emptyGraph();
        }
        String url = base.endsWith("/") ? base + "ops-graph" : base + "/ops-graph";
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            int timeout = Math.max(1000, properties.getOntology().getProductCenterTimeoutMs());
            factory.setConnectTimeout(timeout);
            factory.setReadTimeout(timeout);
            RestClient client = RestClient.builder().requestFactory(factory).build();
            String body = client.get().uri(url).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return OpsProductGraphLoader.emptyGraph();
            }
            Map<String, Object> raw = objectMapper.readValue(body, new TypeReference<>() {});
            raw.putIfAbsent("shelfOfferings", List.of());
            raw.putIfAbsent("opsGraph", Map.of());
            raw.putIfAbsent("bizScenarios", Map.of());
            raw.putIfAbsent("templates", Map.of());
            raw.putIfAbsent("equityGiftWhitelist", List.of());
            raw.putIfAbsent("riskRuleDefaults", Map.of());
            log.info("[HttpOpsProductDataSource] 已加载: {}", url);
            return raw;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load ops graph from product center: " + e.getMessage(), e);
        }
    }
}
