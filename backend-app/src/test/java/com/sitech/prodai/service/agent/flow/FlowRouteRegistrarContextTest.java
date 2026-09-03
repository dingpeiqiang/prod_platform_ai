package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 上下文级验证（S1 集成回归）：
 * 真实绑定链路（@ConfigurationProperties + PostConstruct）下注册器生效。
 * 此测试曾抓住关键 bug——Map 形态绑定下 YAML 数组 keywords 变为索引 Map 而非 List。
 */
class FlowRouteRegistrarContextTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProdAiProperties.class)
    static class Cfg {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Cfg.class)
            .withBean(FlowEngineService.class, () -> org.mockito.Mockito.mock(FlowEngineService.class))
            .withBean(FlowIntentRouter.class)
            .withBean(FlowRouteRegistrar.class)
            .withPropertyValues(
                    "prodai.flow-router.enabled=true",
                    "prodai.flow-router.routes[0].workflow-code=demo_flow",
                    "prodai.flow-router.routes[0].display-name=演示",
                    "prodai.flow-router.routes[0].keywords[0]=演示流程",
                    "prodai.flow-router.routes[0].keywords[1]=跑一下");

    @Test
    void registrarRegistersRoutesThroughRealBinding() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(FlowRouteRegistrar.class);
            FlowIntentRouter router = ctx.getBean(FlowIntentRouter.class);
            assertThat(router.listRoutes()).containsKey("demo_flow");
            assertThat(router.listRoutes().get("demo_flow").keywords())
                    .isEqualTo(List.of("演示流程", "跑一下"));
        });
    }
}