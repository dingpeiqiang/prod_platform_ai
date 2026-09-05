package com.sitech.prodai.service;

import com.sitech.prodai.service.ProductOntologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ABox 生产同步调度器（R5）：定时把业务系统 JDBC 事实源刷新进事实图缓存。
 * <p>复用 {@link ProductOntologyService#reloadGraph()} 的 last-known-good 守卫
 * （LOAD→VALIDATE→SMOKE→COMMIT）：同步失败自动回退现行图谱，不影响在跑服务。
 * <p>启用条件：{@code prodai.ontology.abox-source=jdbc}（dev/demo 用 mock 不装配本类）。
 * 首次全量：应用启动后 initialDelay 1 分钟执行第一轮；此后按 fixedDelay 周期刷新。
 */
@Component
@ConditionalOnProperty(prefix = "prodai.ontology", name = "abox-source", havingValue = "jdbc")
public class ABoxSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ABoxSyncScheduler.class);

    private final ProductOntologyService productOntologyService;
    private final long intervalMinutes;

    /** 最近一次同步结果（可观测：getGraphSummary → abox_last_synced_at / abox_last_sync_ok）。 */
    private volatile Instant lastSyncedAt;
    private volatile boolean lastSyncOk;
    private volatile String lastSyncMessage = "not run yet";

    public ABoxSyncScheduler(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
        this.intervalMinutes = 30;
    }

    /** 装配后回接：把同步状态视图挂到图谱摘要（getGraphSummary → abox_* 字段）。 */
    @jakarta.annotation.PostConstruct
    public void wireStatus() {
        productOntologyService.setAboxSyncStatusSupplier(this::syncStatus);
    }

    /** 首次全量：启动后 1 分钟触发（等待 Spring 容器与本体初始化完成）。 */
    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE)
    public void initialSync() {
        log.info("[ABoxSync] 首次全量同步触发");
        syncOnce();
    }

    /** 定时刷新：按 abox-sync-interval-minutes 周期执行（默认 30 分钟）。 */
    @Scheduled(initialDelayString = "#{T(java.time.Duration).ofMinutes(${prodai.ontology.abox-sync-interval-minutes:30}).toMillis()}",
            fixedDelayString = "#{T(java.time.Duration).ofMinutes(${prodai.ontology.abox-sync-interval-minutes:30}).toMillis()}")
    public void scheduledSync() {
        log.info("[ABoxSync] 定时刷新触发（间隔 {} 分钟）", intervalMinutes);
        syncOnce();
    }

    /** 执行一轮同步并记录可观测状态；异常吞掉（守卫已保证 last-known-good 不被破坏）。 */
    void syncOnce() {
        try {
            Map<String, Object> report = productOntologyService.reloadGraph();
            boolean ok = Boolean.TRUE.equals(report.get("success"));
            lastSyncedAt = Instant.now();
            lastSyncOk = ok;
            lastSyncMessage = String.valueOf(report.getOrDefault("message", ok ? "ok" : "rejected"));
            if (ok) {
                log.info("[ABoxSync] 同步成功: {}", lastSyncMessage);
            } else {
                log.warn("[ABoxSync] 同步被守卫拒绝（保留 last-known-good）: step={}, errors={}",
                        report.get("step"), report.get("errors"));
            }
        } catch (Exception e) {
            lastSyncedAt = Instant.now();
            lastSyncOk = false;
            lastSyncMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("[ABoxSync] 同步异常（last-known-good 保留）: {}", lastSyncMessage, e);
        }
    }

    /** 同步状态视图（OntologyGraphManager.getGraphSummary 聚合展示）。 */
    public Map<String, Object> syncStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("abox_last_synced_at", lastSyncedAt == null ? null : lastSyncedAt.toString());
        status.put("abox_last_sync_ok", lastSyncOk);
        status.put("abox_last_sync_message", lastSyncMessage);
        return status;
    }
}
