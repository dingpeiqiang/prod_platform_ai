package com.sitech.prodai.service.agent.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用户行权限上下文持有者（方案 §4.4，阶段 A2）：请求级 ThreadLocal 单源。
 * <p>
 * 注入时序：编排层入口（AgentOrchestrator.process / processStream）从 SessionContext
 * 取 UserScope 写入 → 数据访问出口（SparqlConfigDiscoverer / MetricService）读取 →
 * finally 清理（线程池复用防串号）。
 * <p>
 * 安全约束：
 * <ul>
 *   <li>唯一写入源是编排层（服务端登录态解析产物），工具入参/LLM 输出无通路；</li>
 *   <li>读取侧 try-finally 严格清理，线程池复用不会把上一位用户的权限泄漏给下一位；</li>
 *   <li>未设置时回落 unrestricted（未启用行权限的部署形态行为不变）。</li>
 * </ul>
 */
public final class UserScopeContext {

    private static final Logger log = LoggerFactory.getLogger(UserScopeContext.class);

    private static final ThreadLocal<UserScope> HOLDER = new ThreadLocal<>();

    private UserScopeContext() {
    }

    /** 编排层入口调用：绑定本次请求的权限上下文。 */
    public static void bind(UserScope scope) {
        HOLDER.set(scope != null ? scope : UserScope.unrestricted());
    }

    /** 数据访问出口调用：取当前权限上下文（未绑定时 unrestricted）。 */
    public static UserScope current() {
        UserScope scope = HOLDER.get();
        return scope != null ? scope : UserScope.unrestricted();
    }

    /** 请求结束必须清理（编排层 finally），防线程池复用串号。 */
    public static void clear() {
        HOLDER.remove();
    }

    /** 带清理的执行包装：编排层可选使用，确保异常路径也不泄漏。 */
    public static <T> T runWith(UserScope scope, java.util.function.Supplier<T> action) {
        bind(scope);
        try {
            return action.get();
        } finally {
            clear();
        }
    }
}
