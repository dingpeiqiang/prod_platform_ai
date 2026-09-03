package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.config.ProdAiProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 流程路由注册器（S1 业务场景接入）：启动时读取 {@code prodai.flow-router} 配置，
 * 把关键词规则批量注册进 {@link FlowIntentRouter}。
 * <p>
 * 配置形态（application.yml）：
 * <pre>
 * prodai:
 *   flow-router:
 *     enabled: true
 *     routes:
 *       - workflow-code: demo_linear_flow
 *         display-name: 演示线性流程
 *         keywords: [跑一下演示流程, 演示流程]
 * </pre>
 * enabled=false 时跳过注册（灰度开关，未命中路由自然走原 LLM 链路，双轨并行）。
 */
@Component
public class FlowRouteRegistrar {

    private static final Logger log = LoggerFactory.getLogger(FlowRouteRegistrar.class);

    private final FlowIntentRouter flowIntentRouter;
    private final ProdAiProperties properties;

    public FlowRouteRegistrar(FlowIntentRouter flowIntentRouter, ProdAiProperties properties) {
        this.flowIntentRouter = flowIntentRouter;
        this.properties = properties;
    }

    @PostConstruct
    public void registerFromConfig() {
        ProdAiProperties.FlowRouter cfg = properties.getFlowRouter();
        if (cfg == null || !cfg.isEnabled()) {
            log.info("[FlowRouteRegistrar] 流程意图路由未启用（prodai.flow-router.enabled=false），对话走原 LLM 链路");
            return;
        }
        List<Map<String, Object>> routes = cfg.getRoutes();
        if (routes == null || routes.isEmpty()) {
            log.warn("[FlowRouteRegistrar] 流程意图路由已启用但未配置 routes，注册表为空");
            return;
        }
        int registered = 0;
        for (Map<String, Object> route : routes) {
            // 注意：绑定目标为 Map<String,Object> 时 yml key 保留 kebab 原样（宽松绑定不作用于 Map key），
            // 因此同时兼容 camelCase 与 kebab-case 两种 key
            String workflowCode = firstNonBlank(route, "workflowCode", "workflow-code");
            if (workflowCode.isBlank()) {
                log.warn("[FlowRouteRegistrar] 跳过缺少 workflow-code 的路由规则: {}", route);
                continue;
            }
            String displayName = firstNonBlank(route, "displayName", "display-name");
            List<String> keywords = castStrings(route.get("keywords"));
            if (keywords.isEmpty()) {
                log.warn("[FlowRouteRegistrar] 跳过缺少 keywords 的路由规则: {}", workflowCode);
                continue;
            }
            flowIntentRouter.register(workflowCode, displayName, keywords);
            registered++;
        }
        log.info("[FlowRouteRegistrar] 流程意图路由注册完成：{} 条规则（共 {} 条配置）", registered, routes.size());
    }

    /** 取首个非空 key 的字符串值。 */
    private static String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return "";
    }

    /**
     * 归一化 keywords 为 List。
     * 注意：绑定目标为 Map&lt;String,Object&gt; 时 YAML 数组表现为 {0=v1, 1=v2} 的索引 Map 而非 List
     * （宽松绑定不作用于 Map 内层），因此需同时兼容两种形态。
     */
    @SuppressWarnings("unchecked")
    private static List<String> castStrings(Object v) {
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (v instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey((a, b) -> Integer.compare(
                            parseIntSafe(String.valueOf(a)), parseIntSafe(String.valueOf(b)))))
                    .map(e -> String.valueOf(e.getValue()))
                    .toList();
        }
        return List.of();
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
