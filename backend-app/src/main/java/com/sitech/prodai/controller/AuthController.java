package com.sitech.prodai.controller;

import com.sitech.prodai.config.JwtService;
import com.sitech.prodai.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "认证管理", description = "用户登录 / 注册 / 当前用户信息 / 登出。除 login、register 外的接口均需携带 Authorization: Bearer <token>")
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

    @Operation(summary = "用户登录", description = "校验用户名密码，成功后返回 JWT token 与用户信息；后续请求需携带 Authorization: Bearer <token>")
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> request) {
        return authService.login(
                request.get("username") == null ? null : String.valueOf(request.get("username")),
                request.get("password") == null ? null : String.valueOf(request.get("password")));
    }

    @Operation(summary = "用户注册", description = "创建新账号，用户名唯一；注册成功直接返回 token（免二次登录）")
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> request) {
        return authService.register(
                request.get("username") == null ? null : String.valueOf(request.get("username")),
                request.get("password") == null ? null : String.valueOf(request.get("password")),
                request.get("display_name") == null ? null : String.valueOf(request.get("display_name")));
    }

    @Operation(summary = "当前用户信息", description = "根据 Authorization 头解析当前登录用户（用户名/角色/显示名），token 失效返回 401")
    @GetMapping("/me")
    public ResponseEntity<?> me(@Parameter(description = "JWT 令牌，格式：Bearer <token>") @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = authService.resolveUser(authorization);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "未登录或登录已过期"));
        }
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    /** 前端登出入口（JWT 无状态，服务端仅做 token 有效性确认）。 */
    @Operation(summary = "退出登录", description = "JWT 无状态登出：仅校验 token 有效性，前端负责清除本地登录态")
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
