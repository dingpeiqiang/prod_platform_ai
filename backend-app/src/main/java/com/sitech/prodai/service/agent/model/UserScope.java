package com.sitech.prodai.service.agent.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户数据行权限上下文（方案 §4.4，阶段 A2）。
 * <p>
 * 由后端从登录态/请求头解析，随 {@link SessionContext} 流转；注入点在数据访问出口
 * （SparqlConfigDiscoverer / MetricService），LLM 与前端均不可触达、不可覆盖。
 * <p>
 * 权限模型（最小可用集）：
 * <ul>
 *   <li>{@code visibleChannels}：可见渠道范围（对齐本体 channelScope 字典：全量 / 电渠+厅店 / 内部验证）。
 *       通配符 {@link #ALL_CHANNELS} 表示不限（系统管理员）；</li>
 *   <li>{@code maxSensitivity}：可查最高数据敏感度（0=公开 1=内部 2=敏感）；</li>
 *   <li>{@code userId}：审计归因用，仅落审计，不参与过滤。</li>
 * </ul>
 * <p>
 * 安全约束：本对象只能由服务端代码构造（构造器私有包装），任何来自请求体的
 * scope/user 字段一律在 Controller 层丢弃，不存在"从前端透传权限"的通路。
 */
public final class UserScope {

    /** 渠道通配：不限渠道（系统管理员/内部全量账号）。 */
    public static final String ALL_CHANNELS = "*";

    /** 敏感度：公开数据（商品目录、资费）。 */
    public static final int SENSITIVITY_PUBLIC = 0;
    /** 敏感度：内部数据（订购量、经营指标）。 */
    public static final int SENSITIVITY_INTERNAL = 1;
    /** 敏感度：敏感数据（用户套包、投诉明细）。 */
    public static final int SENSITIVITY_SENSITIVE = 2;

    private final String userId;
    private final Set<String> visibleChannels;
    private final int maxSensitivity;

    private UserScope(String userId, Set<String> visibleChannels, int maxSensitivity) {
        this.userId = userId == null ? "" : userId;
        this.visibleChannels = visibleChannels == null || visibleChannels.isEmpty()
                ? Set.of(ALL_CHANNELS) : Set.copyOf(visibleChannels);
        this.maxSensitivity = Math.max(SENSITIVITY_PUBLIC,
                Math.min(SENSITIVITY_SENSITIVE, maxSensitivity));
    }

    /** 全量权限（系统管理员/未启用行权限的部署形态）：行为与改造前一致。 */
    public static UserScope unrestricted() {
        return new UserScope("", Set.of(ALL_CHANNELS), SENSITIVITY_SENSITIVE);
    }

    /** 服务端构造入口：登录态解析结果 → 权限上下文。 */
    public static UserScope of(String userId, java.util.Collection<String> visibleChannels, int maxSensitivity) {
        return new UserScope(userId, new LinkedHashSet<>(visibleChannels), maxSensitivity);
    }

    /** 是否不限渠道。 */
    public boolean isAllChannels() {
        return visibleChannels.contains(ALL_CHANNELS);
    }

    /** 是否可查指定敏感度数据。 */
    public boolean canAccess(int sensitivity) {
        return maxSensitivity >= sensitivity;
    }

    public String getUserId() {
        return userId;
    }

    public Set<String> getVisibleChannels() {
        return visibleChannels;
    }

    public int getMaxSensitivity() {
        return maxSensitivity;
    }

    /** 审计摘要（不含敏感明细，仅权限范围画像）。 */
    public String auditSummary() {
        return "user=" + (userId.isBlank() ? "anonymous" : userId)
                + ", channels=" + visibleChannels
                + ", maxSensitivity=" + maxSensitivity;
    }
}
