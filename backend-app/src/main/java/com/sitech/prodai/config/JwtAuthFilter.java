package com.sitech.prodai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 鉴权过滤器：拦截所有 /api/** 请求，强制校验 JWT（除白名单）。
 * <p>
 * 白名单：健康检查、认证端点（login/register）、H2 控制台、静态资源。
 * 通过 {@code prodai.auth.enabled=false} 整体关闭（本地开发）。
 */
@Component
@ConditionalOnProperty(name = "prodai.auth.enabled", havingValue = "true", matchIfMissing = true)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** 免鉴权前缀（健康检查、登录注册、H2 控制台、非 API 静态资源） */
    private static final List<String> WHITELIST_PREFIXES = List.of(
            "/health",
            "/api/v1/health",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/h2-console",
            "/index.html",
            "/assets",
            "/favicon",
            "/uploads"
    );

    private final JwtService jwtService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    /** 请求级用户上下文（username/role），供后续接入 @AuthUser 注入或日志审计 */
    private static final String ATTR_USERNAME = "AUTH_USERNAME";
    private static final String ATTR_ROLE = "AUTH_ROLE";

    public JwtAuthFilter(JwtService jwtService, AuthService authService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        for (String prefix : WHITELIST_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = jwtService.extractToken(authorization);
        Claims claims = token == null ? null : jwtService.parseToken(token);

        if (claims == null) {
            writeUnauthorized(response, "未登录或登录已过期，请重新登录");
            return;
        }

        // token 合法但用户已被禁用/删除
        Map<String, Object> user = authService.resolveUser(authorization);
        if (user == null) {
            writeUnauthorized(response, "账号不可用，请联系管理员");
            return;
        }

        request.setAttribute(ATTR_USERNAME, claims.getSubject());
        request.setAttribute(ATTR_ROLE, String.valueOf(claims.getOrDefault("role", "user")));
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    public static String currentUsername(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_USERNAME);
    }

    public static String currentRole(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_ROLE);
    }
}
