package com.sitech.prodai.config;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.changesub.ChangeDetectService;
import com.sitech.prodai.service.changesub.ProductChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * C3 变更订阅装配（方案 §6-C3）：观察者模式装配点。
 * <p>
 * 复刻 FlowEngineGatewayConfig 的 FlowEventPublisher 装配先例：
 * <ul>
 *   <li>发布器 @Bean：构造注入全部 {@code ProductChangeListener}（daemon 异步、异常隔离）；</li>
 *   <li>回注：{@code ProductOntologyService.setChangePublisher}（setter 回注避免与
 *       本体门面构造环，对齐 ABoxSyncScheduler.wireStatus 先例）；</li>
 *   <li>开关：{@code prodai.change-sub.detect-enabled}（默认关）——关闭时不装配发布器，
 *       reloadGraph 零开销；检测服务 detect() 自身亦受开关守卫（双保险）。</li>
 * </ul>
 */
@Configuration
public class ChangeSubConfig {

    private static final Logger log = LoggerFactory.getLogger(ChangeSubConfig.class);

    private final ProdAiProperties properties;

    public ChangeSubConfig(ProdAiProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ProductChangePublisher productChangePublisher(
            ObjectProvider<com.sitech.prodai.service.changesub.ProductChangeListener> listenerProvider,
            ProductOntologyService productOntologyService,
            ObjectProvider<ChangeDetectService> changeDetectProvider) {
        List<com.sitech.prodai.service.changesub.ProductChangeListener> listeners =
                listenerProvider.orderedStream().toList();
        ProductChangePublisher publisher = new ProductChangePublisher(listeners);
        if (properties.getChangeSub().isDetectEnabled()) {
            // 开关开：回注发布器与检测服务引用（reloadGraph 后自动检测并发布）
            productOntologyService.setChangePublisher(publisher);
            productOntologyService.setChangeDetectProvider(changeDetectProvider);
            log.info("[变更订阅] 变更检测已启用（随图谱重载自动运行，监听器 {} 个）", listeners.size());
        } else {
            log.info("[变更订阅] 检测开关关闭（prodai.change-sub.detect-enabled=false），发布器空装配");
        }
        return publisher;
    }
}
