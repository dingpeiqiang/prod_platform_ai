package com.sitech.prodai.service.agent.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户数据行权限上下文（方案 §4.4，阶段 A2；C2 扩展客户归属维度）。
 * <p>
 * 由后端从登录态/请求头解析，随 {@link SessionContext} 流转；注入点在数据访问出口
 * （SparqlConfigDiscoverer / MetricService / B2B 目录工具），LLM 与前端均不可触达、不可覆盖。
 * <p>
 * 权限模型（最小可用集）：
 * <ul>
 *   <li>{@code visibleChannels}：可见渠道范围（对齐本体 channelScope 字典：全量 / 电渠+厅店 / 内部验证）。
 *       通配符 {@link #ALL_CHANNELS} 表示不限（系统管理员）；</li>
 *   <li>{@code maxSensitivity}：可查最高数据敏感度（0=公开 1=内部 2=敏感）；</li>
 *   <li>{@code visibleCustomers}：可见客户归属范围（C2 政企 B2B 目录：客户经理→名下客户）。
 *       通配符 {@link #ALL_CUSTOMERS} 表示不限；</li>
 *   <li>{@code userId}：审计归因用，仅落审计，不参与过滤。</li>
 * </ul>
 * <p>
 * 安全约束：本对象只能由服务端代码构造（构造器私有包装），任何来自请求体的
 * scope/user 字段一律在 Controller 层丢弃，不存在"从前端透传权限"的通路。
 */
public final class UserScope {

    /** 渠道通配：不限渠道（系统管理员/内部全量账号）。 */
    public static final String ALL_CHANNELS = "*";

    /** 客户归属通配：不限客户（系统管理员/非客户经理角色无 B2B 归属约束）。 */
    public static final String ALL_CUSTOMERS = "*";

    /** 敏感度：公开数据（商品目录、资费）。 */
    public static final int SENSITIVITY_PUBLIC = 0;
    /** 敏感度：内部数据（订购量、经营指标）。 */
    public static final int SENSITIVITY_INTERNAL = 1;
    /** 敏感度：敏感数据（用户套包、投诉明细）。 */
    public static final int SENSITIVITY_SENSITIVE = 2;

    private final String userId;
    private final Set<String> visibleChannels;
    private final Set<String> visibleCustomers;
    private final int maxSensitivity;

    private UserScope(String userId, Set<String> visibleChannels, Set<String> visibleCustomers,
                      int maxSensitivity) {
        this.userId = userId == null ? "" : userId;
        this.visibleChannels = visibleChannels == null || visibleChannels.isEmpty()
                ? Set.of(ALL_CHANNELS) : Set.copyOf(visibleChannels);
        // 空客户列表 = 显式"名下无客户"（受限），仅 null 视为不限（向后兼容三参工厂语义由调用方保证）
        this.visibleCustomers = visibleCustomers == null
                ? Set.of(ALL_CUSTOMERS) : Set.copyOf(visibleCustomers);
        this.maxSensitivity = Math.max(SENSITIVITY_PUBLIC,
                Math.min(SENSITIVITY_SENSITIVE, maxSensitivity));
    }

    /** 全量权限（系统管理员/未启用行权限的部署形态）：行为与改造前一致。 */
    public static UserScope unrestricted() {
        return new UserScope("", Set.of(ALL_CHANNELS), Set.of(ALL_CUSTOMERS), SENSITIVITY_SENSITIVE);
    }

    /** 服务端构造入口：登录态解析结果 → 权限上下文（客户归属不限，向后兼容既有调用）。 */
    public static UserScope of(String userId, java.util.Collection<String> visibleChannels, int maxSensitivity) {
        return new UserScope(userId, new LinkedHashSet<>(visibleChannels), Set.of(ALL_CUSTOMERS), maxSensitivity);
    }

    /** 服务端构造入口（C2）：含客户归属维度（政企 B2B 目录按名下客户过滤）。 */
    public static UserScope of(String userId, java.util.Collection<String> visibleChannels,
                               java.util.Collection<String> visibleCustomers, int maxSensitivity) {
        return new UserScope(userId, new LinkedHashSet<>(visibleChannels),
                new LinkedHashSet<>(visibleCustomers), maxSensitivity);
    }

    /** 是否不限渠道。 */
    public boolean isAllChannels() {
        return visibleChannels.contains(ALL_CHANNELS);
    }

    /** 是否不限客户归属（C2）。 */
    public boolean isAllCustomers() {
        return visibleCustomers.contains(ALL_CUSTOMERS);
    }

    /** 是否可查指定敏感度数据。 */
    public boolean canAccess(int sensitivity) {
        return maxSensitivity >= sensitivity;
    }

    /** 是否可见指定客户（C2 政企 B2B 目录行权限：归属过滤在工具层强制注入）。 */
    public boolean canSeeCustomer(String customerId) {
        return isAllCustomers() || (customerId != null && visibleCustomers.contains(customerId));
    }

    public String getUserId() {
        return userId;
    }

    public Set<String> getVisibleChannels() {
        return visibleChannels;
    }

    public Set<String> getVisibleCustomers() {
        return visibleCustomers;
    }

    public int getMaxSensitivity() {
        return maxSensitivity;
    }

    /** 审计摘要（不含敏感明细，仅权限范围画像）。 */
    public String auditSummary() {
        return "user=" + (userId.isBlank() ? "anonymous" : userId)
                + ", channels=" + visibleChannels
                + ", customers=" + (isAllCustomers() ? "ALL" : visibleCustomers)
                + ", maxSensitivity=" + maxSensitivity;
    }
}
