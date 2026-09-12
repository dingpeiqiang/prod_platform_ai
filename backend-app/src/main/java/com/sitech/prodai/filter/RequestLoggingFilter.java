package com.sitech.prodai.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final int MAX_BODY_LOG_LENGTH = 2048;
    private static final String LOG_REQUEST_BODY_PROPERTY = "log.request-body-enabled";
    private static final String CONTENT_TYPE_MULTIPART = "multipart/";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);

        MDC.put("requestId", requestId);

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        log.info("[REQUEST] {} {}?{} | client={} | requestId={}",
                method, uri, queryString == null ? "" : queryString, clientIp, requestId);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (Exception e) {
            log.error("[REQUEST-ERROR] {} {} | request={} | exception={} | message={} | requestId={}",
                    method, uri, summarizeRequestBody(wrappedRequest),
                    e.getClass().getSimpleName(), e.getMessage(), requestId, e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = wrappedResponse.getStatus();
            log.info("[REQUEST-BODY] {} {} | body={} | requestId={}",
                    method, uri, summarizeRequestBody(wrappedRequest), requestId);
            log.info("[RESPONSE-BODY] {} {} | status={} | duration={}ms | body={} | requestId={}",
                    method, uri, status, duration, summarizeResponseBody(wrappedResponse), requestId);
            wrappedResponse.copyBodyToResponse();
            MDC.remove("requestId");
        }
    }

    private String summarizeRequestBody(ContentCachingRequestWrapper request) {
        try {
            if (!isLoggableBody(request.getContentType(), request.getContentLength())) {
                return "<skipped>";
            }
            byte[] content = request.getContentAsByteArray();
            if (content.length == 0) {
                return "";
            }
            return truncate(new String(content, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "<read-error: " + e.getMessage() + ">";
        }
    }

    private String summarizeResponseBody(ContentCachingResponseWrapper response) {
        try {
            String contentType = response.getContentType();
            if (contentType != null && (contentType.contains(CONTENT_TYPE_EVENT_STREAM)
                    || contentType.contains(CONTENT_TYPE_OCTET_STREAM))) {
                return "<skipped>";
            }
            byte[] content = response.getContentAsByteArray();
            if (content.length == 0) {
                return "";
            }
            return truncate(new String(content, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "<read-error: " + e.getMessage() + ">";
        }
    }

    private boolean isLoggableBody(String contentType, int contentLength) {
        if (contentLength == 0) {
            return false;
        }
        if (contentType == null) {
            return true;
        }
        String lower = contentType.toLowerCase();
        return !lower.startsWith(CONTENT_TYPE_MULTIPART)
                && !lower.contains(CONTENT_TYPE_EVENT_STREAM)
                && !lower.contains(CONTENT_TYPE_OCTET_STREAM);
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        String stripped = body.replaceAll("\\s+", " ").trim();
        if (stripped.length() <= MAX_BODY_LOG_LENGTH) {
            return stripped;
        }
        return stripped.substring(0, MAX_BODY_LOG_LENGTH) + "...(truncated, total=" + stripped.length() + " chars)";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/") || uri.startsWith("/health") || uri.startsWith("/error")
                || uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
