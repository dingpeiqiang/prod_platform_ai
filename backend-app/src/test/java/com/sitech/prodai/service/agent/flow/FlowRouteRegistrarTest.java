package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.config.ProdAiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程路由注册器测试：enabled 开关、规则解析与无效规则跳过。
 */
class FlowRouteRegistrarTest {

    private static ProdAiProperties.FlowRouter cfg(boolean enabled, List<Map<String, Object>> routes) {
        ProdAiProperties.FlowRouter f = new ProdAiProperties.FlowRouter();
        f.setEnabled(enabled);
        f.setRoutes(routes);
        return f;
    }

    private static ProdAiProperties props(ProdAiProperties.FlowRouter f) {
        ProdAiProperties p = new ProdAiProperties();
        // ProdAiProperties 内嵌 FlowRouter 为 final 字段，通过 getter 覆盖不可行——
        // 改为用反射外的简单做法：直接构造独立 FlowIntentRouter 校验注册结果
        return null;
    }

    @Test
    void registersValidRoutesFromConfig() {
        FlowIntentRouter router = new FlowIntentRouter(null);
        // 用 kebab-case key 模拟 yml 绑定 Map<String,Object> 的真实形态（宽松绑定不作用于 Map key）
        ProdAiProperties.FlowRouter f = cfg(true, List.of(
                Map.of("workflow-code", "demo_linear_flow",
                        "display-name", "演示线性流程",
                        "keywords", List.of("演示流程", "跑一下"))));

        registrarOf(router, f).registerFromConfig();

        assertEquals(1, router.listRoutes().size());
        FlowIntentRouter.FlowRoute route = router.listRoutes().get("demo_linear_flow");
        assertEquals("演示线性流程", route.displayName());
        assertEquals(List.of("演示流程", "跑一下"), route.keywords());
    }

    @Test
    void registersCamelCaseKeysToo() {
        FlowIntentRouter router = new FlowIntentRouter(null);
        ProdAiProperties.FlowRouter f = cfg(true, List.of(
                Map.of("workflowCode", "code_camel",
                        "displayName", "驼峰键流程",
                        "keywords", List.of("驼峰"))));

        registrarOf(router, f).registerFromConfig();

        assertEquals(1, router.listRoutes().size(), "camelCase key 也应兼容");
        assertEquals("驼峰键流程", router.listRoutes().get("code_camel").displayName());
    }

    @Test
    void disabledOrEmptyConfigRegistersNothing() {
        FlowIntentRouter router = new FlowIntentRouter(null);

        registrarOf(router, cfg(false, List.of(Map.of("workflowCode", "x", "keywords", List.of("k")))))
                .registerFromConfig();
        assertEquals(0, router.listRoutes().size(), "enabled=false 不注册");

        registrarOf(router, cfg(true, List.of())).registerFromConfig();
        assertEquals(0, router.listRoutes().size(), "空 routes 不注册");

        registrarOf(router, cfg(true, List.of(
                Map.of("workflowCode", "", "keywords", List.of("k")),
                Map.of("workflowCode", "y"))))
                .registerFromConfig();
        assertEquals(0, router.listRoutes().size(), "缺 code/keywords 的规则跳过");
    }

    @Test
    void keywordMatchIsCaseInsensitiveAfterRegister() {
        FlowIntentRouter router = new FlowIntentRouter(null);
        ProdAiProperties.FlowRouter f = cfg(true, List.of(
                Map.of("workflowCode", "code_x", "keywords", List.of("DemoFlow"))));

        registrarOf(router, f).registerFromConfig();

        assertEquals(List.of("demoflow"), router.listRoutes().get("code_x").keywords(),
                "关键词应归一化为小写");
    }

    /** 构造挂接指定配置的注册器（用真实 FlowIntentRouter 而非 mock，直接校验注册结果）。 */
    private static FlowRouteRegistrar registrarOf(FlowIntentRouter router, ProdAiProperties.FlowRouter f) {
        ProdAiProperties p = new ProdAiProperties();
        try {
            java.lang.reflect.Field field = ProdAiProperties.class.getDeclaredField("flowRouter");
            field.setAccessible(true);
            field.set(p, f);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return new FlowRouteRegistrar(router, p);
    }
}
