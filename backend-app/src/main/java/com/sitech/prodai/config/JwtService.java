package com.sitech.prodai.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

/**
 * JWT 签发与校验。
 * <p>
 * 密钥通过 {@code prodai.auth.jwt-secret}（环境变量 AUTH_JWT_SECRET）注入；
 * 未配置时自动派生实例级密钥（单机可用，多实例部署必须显式配置统一密钥）。
 * 通过 {@code prodai.auth.enabled=false} 可整体关闭认证（本地开发）。
 */
@Component
@ConditionalOnProperty(name = "prodai.auth.enabled", havingValue = "true", matchIfMissing = true)
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final SecretKey key;

    public JwtService(@Value("${prodai.auth.jwt-secret:}") String jwtSecret) {
        byte[] keyBytes;
        if (jwtSecret == null || jwtSecret.isBlank()) {
            keyBytes = deriveDefaultSecret();
            log.warn("[JwtService] prodai.auth.jwt-secret 未配置，使用实例级派生密钥（多实例部署请统一配置 AUTH_JWT_SECRET）");
        } else {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issueToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_TTL)))
                .signWith(key)
                .compact();
    }

    /** 校验并解析 token；无效/过期返回 null。 */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring(7).trim();
    }

    private byte[] deriveDefaultSecret() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(
                    ("prod-platform-ai-default-jwt|" + Instant.now().toEpochMilli() / (3600_000L * 24))
                            .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return HexFormat.of().parseHex(
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        }
    }
}
