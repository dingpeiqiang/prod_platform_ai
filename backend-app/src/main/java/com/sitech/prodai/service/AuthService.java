package com.sitech.prodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.config.JwtService;
import com.sitech.prodai.domain.entity.User;
import com.sitech.prodai.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 认证服务：用户名密码登录 → JWT 签发；token 校验 → 用户信息。
 * <p>
 * 密码存储格式：{@code salt hex + ":" + SHA-256(salt + password) hex}（每用户独立 salt）。
 */
@Service
@ConditionalOnProperty(name = "prodai.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{2,32}$");

    private final UserMapper userMapper;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, JwtService jwtService) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
    }

    /** 登录：成功返回 token + 用户信息；失败返回 error message。 */
    public Map<String, Object> login(String username, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            body.put("success", false);
            body.put("message", "用户名和密码不能为空");
            return body;
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username.trim()));
        if (user == null || !verifyPassword(password, user.getPasswordHash())) {
            body.put("success", false);
            body.put("message", "用户名或密码错误");
            return body;
        }
        if (user.getIsEnabled() == null || user.getIsEnabled() != 1) {
            body.put("success", false);
            body.put("message", "账号已被禁用，请联系管理员");
            return body;
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtService.issueToken(user.getUsername(), user.getRole());
        body.put("success", true);
        body.put("token", token);
        body.put("user", toUserMap(user));
        return body;
    }

    /** 校验 token 并返回用户信息；无效返回 null。 */
    public Map<String, Object> resolveUser(String authorizationHeader) {
        String token = jwtService.extractToken(authorizationHeader);
        if (token == null) {
            return null;
        }
        var claims = jwtService.parseToken(token);
        if (claims == null) {
            return null;
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, claims.getSubject()));
        if (user == null || user.getIsEnabled() == null || user.getIsEnabled() != 1) {
            return null;
        }
        return toUserMap(user);
    }

    /** 注册新用户（初始部署自助建号；admin 角色只能由 DB 侧赋予）。 */
    public Map<String, Object> register(String username, String password, String displayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            body.put("success", false);
            body.put("message", "用户名需为 2-32 位字母、数字、下划线或中文");
            return body;
        }
        if (password == null || password.length() < 6) {
            body.put("success", false);
            body.put("message", "密码长度至少 6 位");
            return body;
        }
        String trimmed = username.trim();
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, trimmed)) > 0) {
            body.put("success", false);
            body.put("message", "用户名已存在");
            return body;
        }
        User user = new User();
        user.setUsername(trimmed);
        user.setPasswordHash(hashPassword(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? trimmed : displayName.trim());
        user.setRole("user");
        user.setIsEnabled(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtService.issueToken(user.getUsername(), user.getRole());
        body.put("success", true);
        body.put("token", token);
        body.put("user", toUserMap(user));
        return body;
    }

    private boolean verifyPassword(String password, String storedHash) {
        if (storedHash == null || !storedHash.contains(":")) {
            return false;
        }
        int idx = storedHash.indexOf(':');
        String salt = storedHash.substring(0, idx);
        String expected = storedHash.substring(idx + 1);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sha256Hex(salt + password).getBytes(StandardCharsets.UTF_8));
    }

    private String hashPassword(String password) {
        String salt = HexFormat.of().formatHex(
                new java.security.SecureRandom().generateSeed(16));
        return salt + ":" + sha256Hex(salt + password);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.getUsername());
        map.put("display_name", user.getDisplayName());
        map.put("role", user.getRole());
        map.put("last_login_at", user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString());
        return map;
    }
}
