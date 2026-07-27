package com.sitech.prodai.service.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.Map;

/**
 * 产商品中心 HTTP 事实图。
 * <p>契约：{@code GET {productCenterBaseUrl}/ops-graph} 返回与 classpath mock_graph 同构 JSON，
 * 顶层必含 shelfOfferings / opsGraph / bizScenarios / templates / equityGiftWhitelist / riskRuleDefaults。
 * 勿将 baseUrl 指向本服务自身（同进程自指）；本进程发布请走 OpsGraphPublishService。
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
            throw new IllegalStateException(
                    "prodai.ontology.data-source=http requires prodai.ontology.product-center-base-url "
                            + "(GET {base}/ops-graph). Refusing empty graph.");
        }
        if (OpsGraphSchemaValidator.looksLikeLocalProductCenter(base)) {
            log.warn("[HttpOpsProductDataSource] baseUrl looks like local product-center ({}). "
                    + "Prefer external BOSS/CRM ops-graph in production; same-process publish uses OpsGraphPublishService.",
                    base);
        }
        String url = resolveOpsGraphUrl(base);
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            int timeout = Math.max(1000, properties.getOntology().getProductCenterTimeoutMs());
            factory.setConnectTimeout(timeout);
            factory.setReadTimeout(timeout);
            RestClient client = RestClient.builder().requestFactory(factory).build();
            String body = client.get().uri(url).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("Empty response from ops-graph: " + url);
            }
            Map<String, Object> raw = objectMapper.readValue(body, new TypeReference<>() {});
            OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
            if (!vr.warnings().isEmpty()) {
                log.warn("[HttpOpsProductDataSource] schema warnings from {}: {}", url, vr.warnings());
            }
            if (!vr.ok()) {
                throw new IllegalStateException(
                        "ops-graph schema invalid from " + url + ": " + String.join("; ", vr.errors()));
            }
            log.info("[HttpOpsProductDataSource] 已加载: {} (shelf={}, contract={})",
                    url,
                    vr.normalized().get("shelfOfferings") instanceof java.util.List<?> list ? list.size() : 0,
                    OpsGraphSchemaValidator.CONTRACT_VERSION);
            return vr.normalized();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load ops graph from product center: " + e.getMessage(), e);
        }
    }

    /** 探测 ops-graph 是否可达（不抛异常，供 health/meta）。 */
    public Map<String, Object> probe() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        String base = properties.getOntology().getProductCenterBaseUrl();
        out.put("configured", base != null && !base.isBlank());
        out.put("baseUrl", base == null ? "" : base);
        out.put("contractVersion", OpsGraphSchemaValidator.CONTRACT_VERSION);
        out.put("localProductCenter", OpsGraphSchemaValidator.looksLikeLocalProductCenter(base));
        if (base == null || base.isBlank()) {
            out.put("reachable", false);
            out.put("message", "product-center-base-url not set");
            return out;
        }
        String url = resolveOpsGraphUrl(base);
        out.put("opsGraphUrl", url);
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            int timeout = Math.min(3000, Math.max(500, properties.getOntology().getProductCenterTimeoutMs()));
            factory.setConnectTimeout(timeout);
            factory.setReadTimeout(timeout);
            RestClient client = RestClient.builder().requestFactory(factory).build();
            String body = client.get().uri(url).retrieve().body(String.class);
            boolean ok = body != null && !body.isBlank();
            out.put("reachable", ok);
            if (ok) {
                try {
                    Map<String, Object> raw = objectMapper.readValue(body, new TypeReference<>() {});
                    OpsGraphSchemaValidator.ValidationResult vr = OpsGraphSchemaValidator.validateAndNormalize(raw);
                    out.put("schemaOk", vr.ok());
                    out.put("schemaErrors", vr.errors());
                    out.put("schemaWarnings", vr.warnings());
                    out.put("message", vr.ok() ? "ok" : "schema invalid");
                } catch (Exception parseEx) {
                    out.put("schemaOk", false);
                    out.put("message", "reachable but not JSON: " + parseEx.getMessage());
                }
            } else {
                out.put("message", "empty body");
            }
        } catch (Exception e) {
            out.put("reachable", false);
            out.put("message", e.getMessage());
        }
        return out;
    }

    static String resolveOpsGraphUrl(String base) {
        String trimmed = base == null ? "" : base.trim();
        if (trimmed.isEmpty()) return "";
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/ops-graph")) {
            return trimmed;
        }
        return trimmed.endsWith("/") ? trimmed + "ops-graph" : trimmed + "/ops-graph";
    }
}
