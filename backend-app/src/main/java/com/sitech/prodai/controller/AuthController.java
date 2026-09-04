package com.sitech.prodai.controller;

import com.sitech.prodai.config.JwtService;
import com.sitech.prodai.service.AuthService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证 API：登录 / 注册 / 当前用户信息。
 * <p>
 * 通过 {@code prodai.auth.enabled=false} 可整体关闭（本地开发场景），
 * 关闭时前端回落到原有本地模式。
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "prodai.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> request) {
        return authService.login(
                request.get("username") == null ? null : String.valueOf(request.get("username")),
                request.get("password") == null ? null : String.valueOf(request.get("password")));
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> request) {
        return authService.register(
                request.get("username") == null ? null : String.valueOf(request.get("username")),
                request.get("password") == null ? null : String.valueOf(request.get("password")),
                request.get("display_name") == null ? null : String.valueOf(request.get("display_name")));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = authService.resolveUser(authorization);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "未登录或登录已过期"));
        }
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    /** 前端登出入口（JWT 无状态，服务端仅做 token 有效性确认）。 */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = jwtService.extractToken(authorization);
        if (token != null && jwtService.parseToken(token) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "登录状态已失效"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
