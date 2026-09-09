package com.sitech.prodai.controller;

import com.sitech.prodai.service.metric.MetricEtlService;
import com.sitech.prodai.service.metric.MetricThresholdCalibrator;
import com.sitech.prodai.service.ops.OpsGraphPublishService;
import com.sitech.prodai.service.ops.OpsGraphSchemaValidator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 产商品中心事实图契约：{@code GET .../ops-graph}。
 * <p>
 * 外部系统或本项目 {@code HttpOpsProductDataSource}（data-source=http）可消费。
 * 推荐 {@code PRODUCT_CENTER_BASE_URL=http://host:port/api/v1/product-center}。
 * 契约版本见 {@link OpsGraphSchemaValidator#CONTRACT_VERSION}。
 */
@RestController
public class ProductCenterController {

    private final OpsGraphPublishService publishService;
    private final MetricEtlService metricEtlService;
    private final MetricThresholdCalibrator thresholdCalibrator;

    public ProductCenterController(OpsGraphPublishService publishService,
                                   MetricEtlService metricEtlService,
                                   MetricThresholdCalibrator thresholdCalibrator) {
        this.publishService = publishService;
        this.metricEtlService = metricEtlService;
        this.thresholdCalibrator = thresholdCalibrator;
    }

    /** 与 HttpOpsProductDataSource 约定：{baseUrl}/ops-graph */
    @GetMapping({"/ops-graph", "/api/v1/product-center/ops-graph"})
    public Map<String, Object> opsGraph() {
        return publishService.loadPublishedGraph();
    }

    /** 契约说明：字段与版本，供主数据对接方联调。 */
    @GetMapping({"/api/v1/product-center/ops-graph/contract", "/ops-graph/contract"})
    public Map<String, Object> opsGraphContract() {
        Map<String, Object> body = new LinkedHashMap<>(OpsGraphSchemaValidator.contractDescriptor());
        body.put("sourcePath", publishService.resolvedSourcePath());
        return body;
    }

    /** 指标宽表契约说明（MetricsContract-v1）：列清单 / 粒度 / ETL 约定，供 T+1 ETL 对接方联调。 */
    @GetMapping("/api/v1/product-center/metrics/contract")
    public Map<String, Object> metricsContract() {
        return com.sitech.prodai.service.metric.MetricsContractValidator.contractDescriptor();
    }

    /**
     * 手动触发 T+1 ETL 灌数（本地替身）：宽表无数据或需刷新时调用；
     * 生产环境由真实数仓 ETL 替代，本端点仅联调/演示用。
     */
    @PostMapping("/api/v1/product-center/metrics/etl")
    public Map<String, Object> runMetricEtl() {
        return metricEtlService.run("manual");
    }

    /** 最近一次 ETL 执行摘要（可观测）。 */
    @GetMapping("/api/v1/product-center/metrics/etl/last-run")
    public Map<String, Object> metricEtlLastRun() {
        return metricEtlService.lastRun();
    }

    /**
     * 触发阈值分位数校准：基于宽表真实环比分布计算 P5/P10，
     * 与 R-A01 当前阈值对照输出建议（应用建议需人工确认后改单源）。
     */
    @PostMapping("/api/v1/product-center/metrics/threshold-calibration")
    public Map<String, Object> calibrateThresholds() {
        return thresholdCalibrator.calibrate();
    }

    /** 最近一次阈值校准报告（可观测）。 */
    @GetMapping("/api/v1/product-center/metrics/threshold-calibration/last")
    public Map<String, Object> lastCalibration() {
        return thresholdCalibrator.lastCalibration();
    }
}
