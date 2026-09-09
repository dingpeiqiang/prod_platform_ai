package com.sitech.prodai.service.agent.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户行权限解析器（方案 §4.4，阶段 A2；C2 扩展客户经理角色）：登录态 → {@link UserScope}。
 * <p>
 * 当前权限字典（v1 最小可用集，与本体 channelScope 字典对齐）：
 * <ul>
 *   <li>{@code admin} 角色 → 全量（ALL_CHANNELS + 全敏感度 + 不限客户归属）；</li>
 *   <li>{@code account_manager} 角色（C2 政企客户经理）→ 渠道受限 + 名下客户归属列表
 *       （v1 mock：演示名下客户；生产接统一权限中心/客户归属表替换字典读取）；</li>
 *   <li>其余角色 → 默认受限：电渠+厅店渠道、内部敏感度（客户归属不限）；</li>
 *   <li>未登录（auth.enabled=false 本地形态）→ unrestricted，行为与改造前一致。</li>
 * </ul>
 * <p>
 * 扩展路径（不改变注入机制）：角色→范围映射后续落库（pd_ai_user_scope 表）或接
 * 统一权限中心；本类只改字典读取，注入链路（Context → 工具/Discoverer/MetricService）零改动。
 * <p>
 * 安全约束：请求体中的任何 scope/user/channel/customer 字段在 Controller 层丢弃——
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
     * 客户经理名下客户归属（C2 v1 mock 演示字典：生产接客户归属表/权限中心替换）。
     * key = 客户经理登录名，value = 名下政企客户编号列表（确定性，演示口径）。
     */
    private static final Map<String, List<String>> ACCOUNT_MANAGER_CUSTOMERS = Map.of(
            "am01", List.of("GE-CUST-001", "GE-CUST-002", "GE-CUST-003"),
            "am02", List.of("GE-CUST-004", "GE-CUST-005")
    );

    /**
     * 从登录态解析权限上下文。
     *
     * @param username 登录用户名（null = 未登录，本地开发形态）
     * @param role     登录角色（admin/user/account_manager/...）
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
        if ("account_manager".equalsIgnoreCase(role)) {
            // C2 政企客户经理：客户归属维度 = 名下客户列表（服务端字典，LLM/前端不可触达）
            List<String> customers = ACCOUNT_MANAGER_CUSTOMERS.getOrDefault(username.toLowerCase(), List.of());
            UserScope scope = UserScope.of(username, DEFAULT_ROLE_CHANNELS, customers, DEFAULT_ROLE_MAX_SENSITIVITY);
            log.debug("[UserScopeResolver] resolved account_manager: {}", scope.auditSummary());
            return scope;
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
        audit.put("scope_customers", scope.isAllCustomers() ? "ALL" : scope.getVisibleCustomers());
        audit.put("scope_max_sensitivity", scope.getMaxSensitivity());
        return audit;
    }
}
