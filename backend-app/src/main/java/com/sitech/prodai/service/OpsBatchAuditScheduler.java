package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时批量风险稽核（对齐方案「每日全量在售产品风险筛查」）。
 * 默认关闭；demo/dev 可通过 {@code prodai.ontology.batch-audit-enabled=true} 开启。
 */
@Component
@ConditionalOnProperty(prefix = "prodai.ontology", name = "batch-audit-enabled", havingValue = "true")
public class OpsBatchAuditScheduler {

    private static final Logger log = LoggerFactory.getLogger(OpsBatchAuditScheduler.class);

    private final ProductOntologyService productOntologyService;
    private final ProdAiProperties properties;

    public OpsBatchAuditScheduler(ProductOntologyService productOntologyService, ProdAiProperties properties) {
        this.productOntologyService = productOntologyService;
        this.properties = properties;
    }

    @Scheduled(cron = "${prodai.ontology.batch-audit-cron:0 0 2 * * ?}")
    public void runScheduledBatchAudit() {
        log.info("[OpsBatchAudit] 定时批量稽核触发 cron={}", properties.getOntology().getBatchAuditCron());
        productOntologyService.runBatchRiskAudit("scheduler");
    }
}
