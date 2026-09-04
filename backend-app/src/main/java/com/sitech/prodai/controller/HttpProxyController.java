package com.sitech.prodai.controller;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 通用 HTTP 代理端点：供可视化工作流编辑器 http 节点发起服务端外呼请求。
 * <p>
 * 设计要点：
 * - 浏览器直接外呼受 CORS 限制，统一走后端代理（RestTemplate，connect 5s / read 30s）；
 * - 支持 GET/POST/PUT/DELETE，响应体以字符串返回，状态码原样透传；
 * - SSRF 防护：仅允许 http/https 协议，拒绝回环/内网网段目标。
 */
@RestController
@RequestMapping("/api/v1/http-proxy")
public class HttpProxyController {

    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "DELETE");

    private final RestTemplate restTemplate;

    public HttpProxyController() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restTemplate = new RestTemplate(factory);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> proxy(@RequestBody(required = false) Map<String, Object> body) {
        String url = body == null ? null : String.valueOf(body.getOrDefault("url", ""));
        String method = body == null ? "GET" : String.valueOf(body.getOrDefault("method", "GET"));
        Object payload = body == null ? null : body.get("body");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (body != null && body.get("headers") instanceof Map<?, ?> h)
                ? (Map<String, Object>) h : Map.of();

        if (url == null || url.isBlank() || "null".equals(url)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "url 未配置"));
        }
        if (!isSafeUrl(url)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "目标地址不允许访问"));
        }
        method = method.toUpperCase();
        if (!ALLOWED_METHODS.contains(method)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "不支持的请求方法: " + method));
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach((k, v) -> {
            if (k != null && v != null && !"content-length".equalsIgnoreCase(k)) {
                httpHeaders.set(k, String.valueOf(v));
            }
        });
        if (httpHeaders.getContentType() == null && payload != null) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }

        HttpEntity<Object> entity = new HttpEntity<>(payload, httpHeaders);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", resp.getStatusCode().value(),
                    "data", resp.getBody() == null ? "" : resp.getBody()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "status", 502,
                    "message", "HTTP 请求失败: " + e.getMessage()
            ));
        }
    }

    private boolean isSafeUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            java.net.InetAddress address = java.net.InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
