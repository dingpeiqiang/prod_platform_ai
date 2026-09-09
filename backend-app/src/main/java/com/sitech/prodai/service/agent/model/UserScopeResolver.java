package com.sitech.prodai.service.agent.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户行权限解析器（方案 §4.4，阶段 A2）：登录态 → {@link UserScope}。
 * <p>
 * 当前权限字典（v1 最小可用集，与本体 channelScope 字典对齐）：
 * <ul>
 *   <li>{@code admin} 角色 → 全量（ALL_CHANNELS + 全敏感度）；</li>
 *   <li>其余角色 → 默认受限：电渠+厅店渠道、内部敏感度；</li>
 *   <li>未登录（auth.enabled=false 本地形态）→ unrestricted，行为与改造前一致。</li>
 * </ul>
 * <p>
 * 扩展路径（不改变注入机制）：角色→范围映射后续落库（pd_ai_user_scope 表）或接
 * 统一权限中心；本类只改字典读取，注入链路（Context → Discoverer/MetricService）零改动。
 * <p>
 * 安全约束：请求体中的任何 scope/user/channel 字段在 Controller 层丢弃——
 * 本类是 UserScope 的唯一生产者，输入仅来自 JWT 解析结果。
 */
@Service
public class UserScopeResolver {

    private static final Logger log = LoggerFactory.getLogger(UserScopeResolver.class);

    /** 角色受限默认渠道范围（对齐 mock channelScope 字典：电渠+厅店为正式销售渠道）。 */
    private static final List<String> DEFAULT_ROLE_CHANNELS = List.of("电渠+厅店");

    /** 非 admin 角色默认敏感度上限：内部数据可见、敏感数据（用户套包等）不可见。 */
    private static final int DEFAULT_ROLE_MAX_SENSITIVITY = UserScope.SENSITIVITY_INTERNAL;

    /**
     * 从登录态解析权限上下文。
     *
     * @param username 登录用户名（null = 未登录，本地开发形态）
     * @param role     登录角色（admin/user/...）
     * @return 权限上下文（非 null）
     */
    public UserScope resolve(String username, String role) {
        if (username == null || username.isBlank()) {
            // 未登录：鉴权关闭的本地形态 → unrestricted（行为与启用行权限前一致）
            return UserScope.unrestricted();
        }
        if ("admin".equalsIgnoreCase(role)) {
            return UserScope.of(username, List.of(UserScope.ALL_CHANNELS), UserScope.SENSITIVITY_SENSITIVE);
        }
        UserScope scope = UserScope.of(username, DEFAULT_ROLE_CHANNELS, DEFAULT_ROLE_MAX_SENSITIVITY);
        log.debug("[UserScopeResolver] resolved: {}", scope.auditSummary());
        return scope;
    }

    /** 审计视图：解析结果摘要（供 Controller 日志与后续 A3 审计落库复用）。 */
    public Map<String, Object> auditOf(UserScope scope) {
        Map<String, Object> audit = new LinkedHashMap<>();
        if (scope == null) {
            return audit;
        }
        audit.put("scope_user", scope.getUserId());
        audit.put("scope_channels", scope.getVisibleChannels());
        audit.put("scope_max_sensitivity", scope.getMaxSensitivity());
        return audit;
    }
}
