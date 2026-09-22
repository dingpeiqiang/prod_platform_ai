package com.sitech.prodai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.appstore.EopInterfaceAdapter;
import com.sitech.prodai.service.appstore.InterfaceFacade;
import com.sitech.prodai.service.appstore.MockInterfaceAdapter;
import com.sitech.prodai.service.appstore.OfferSimV16Service;
import com.sitech.prodai.service.appstore.RoutingInterfaceFacade;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 产销品场景接口适配层装配（统一方案 §4.5.4，策略模式 + 工厂）。
 * <p>
 * 始终装配 {@link MockInterfaceAdapter}（mock 基座）与 {@link EopInterfaceAdapter}（real），
 * 由 {@link RoutingInterfaceFacade} 按 {@code prodai.appstore.interface.mode} 与 {@code real-list}
 * 逐接口路由，注入 {@code AppStoreV16Controller}（对外签名与出参不变）。
 * 真实连接仅在 real 模式被调用时发生，mock 模式无网络依赖；real 调用失败不回退 mock，直接返回错误出参。
 */
@Configuration
public class AppStoreInterfaceConfig {

    @Bean
    public MockInterfaceAdapter mockInterfaceAdapter(OfferSimV16Service sim) {
        return new MockInterfaceAdapter(sim);
    }

    @Bean
    public EopInterfaceAdapter eopInterfaceAdapter(AppStoreInterfaceProperties props,
                                                   ObjectMapper objectMapper) {
        return new EopInterfaceAdapter(props, objectMapper);
    }

    @Bean
    public InterfaceFacade interfaceFacade(AppStoreInterfaceProperties props,
                                           MockInterfaceAdapter mockInterfaceAdapter,
                                           EopInterfaceAdapter eopInterfaceAdapter) {
        return new RoutingInterfaceFacade(props, mockInterfaceAdapter, eopInterfaceAdapter);
    }
}
