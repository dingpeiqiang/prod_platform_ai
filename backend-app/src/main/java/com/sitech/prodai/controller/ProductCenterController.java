package com.sitech.prodai.controller;

import com.sitech.prodai.service.ops.OpsGraphPublishService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 产商品中心事实图契约：{@code GET .../ops-graph}。
 * <p>
 * 外部系统或本项目 {@code HttpOpsProductDataSource}（data-source=http）可消费。
 * 推荐 {@code PRODUCT_CENTER_BASE_URL=http://host:port/api/v1/product-center}。
 */
@RestController
public class ProductCenterController {

    private final OpsGraphPublishService publishService;

    public ProductCenterController(OpsGraphPublishService publishService) {
        this.publishService = publishService;
    }

    /** 与 HttpOpsProductDataSource 约定：{baseUrl}/ops-graph */
    @GetMapping({"/ops-graph", "/api/v1/product-center/ops-graph"})
    public Map<String, Object> opsGraph() {
        return publishService.loadPublishedGraph();
    }
}
