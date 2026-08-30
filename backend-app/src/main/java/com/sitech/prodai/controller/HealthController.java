package com.sitech.prodai.controller;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import com.sitech.prodai.service.OntologyVersionService;
import com.sitech.prodai.service.ProductTemplateRegistry;
import com.sitech.prodai.service.ops.HttpOpsProductDataSource;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class HealthController {

    private final ProdAiProperties properties;
    private final Environment environment;
    private final HttpOpsProductDataSource httpOpsProductDataSource;
    private final Optional<OntologyVersionService> versionService;
    private final Optional<ProductTemplateRegistry> templateRegistry;

    public HealthController(
            ProdAiProperties properties,
            Environment environment,
            HttpOpsProductDataSource httpOpsProductDataSource,
            org.springframework.beans.factory.ObjectProvider<OntologyVersionService> versionProvider,
            org.springframework.beans.factory.ObjectProvider<ProductTemplateRegistry> templateProvider
    ) {
        this.properties = properties;
        this.environment = environment;
        this.httpOpsProductDataSource = httpOpsProductDataSource;
        this.versionService = Optional.ofNullable(versionProvider.getIfAvailable());
        this.templateRegistry = Optional.ofNullable(templateProvider.getIfAvailable());
    }

    @RequestMapping(value = {"/health", "/api/v1/health"}, method = {RequestMethod.GET, RequestMethod.HEAD})
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "prod-platform-ai");
        body.put("runtime", "spring-boot");
        body.put("profiles", Arrays.asList(environment.getActiveProfiles()));

        Map<String, Object> ontology = new LinkedHashMap<>();
        ontology.put("demoMode", properties.getOntology().isDemoEnabled());
        ontology.put("dataSourceMode", properties.getOntology().getDataSource());
        ontology.put("graphPath", properties.getOntology().getGraphPath());
        ontology.put("opsGraphPath", properties.getOntology().getOpsGraphPath());
        ontology.put("contractVersion", com.sitech.prodai.service.ops.OpsGraphSchemaValidator.CONTRACT_VERSION);
        // P3-5 ②：变更面指标（表 A 聚合 + 模板注册中心；版本库不可用时优雅降级）
        ontology.putAll(deployMetrics());
        String mode = properties.getOntology().getDataSource() == null
                ? "classpath"
                : properties.getOntology().getDataSource().trim().toLowerCase();
        if ("http".equals(mode)) {
            ontology.put("http", httpOpsProductDataSource.probe());
        }
        body.put("ontology", ontology);
        return body;
    }

    /**
     * P3-5 ② 变更面指标：graphVersion/rulesVersion = 表 A ttl/ops_rules 最新 published；
     * activeTemplateCount = 模板注册中心当前有效模板数；publishedTemplateCount = 表 A 已发布模板行数；
     * lastDeployAt = 跨资产最新 published_at；lastGoodSnapshotAt = abox_snapshot 最新 published_at。
     */
    private Map<String, Object> deployMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        int activeTemplateCount = templateRegistry.map(ProductTemplateRegistry::count).orElse(0);
        metrics.put("activeTemplateCount", activeTemplateCount);
        if (versionService.isEmpty()) {
            metrics.put("graphVersion", null);
            metrics.put("rulesVersion", null);
            metrics.put("publishedTemplateCount", 0);
            metrics.put("lastDeployAt", null);
            metrics.put("lastGoodSnapshotAt", null);
            return metrics;
        }
        try {
            OntologyVersionService vs = versionService.get();
            metrics.put("graphVersion", vs.latestPublishedByType(OntologyVersionService.TYPE_TTL)
                    .map(OntologyAssetVersion::getVersion).orElse(null));
            metrics.put("rulesVersion", vs.latestPublishedByType(OntologyVersionService.TYPE_OPS_RULES)
                    .map(OntologyAssetVersion::getVersion).orElse(null));
            metrics.put("publishedTemplateCount", vs.countPublished(OntologyVersionService.TYPE_TEMPLATE));
            metrics.put("lastDeployAt", vs.latestPublishedByType(OntologyVersionService.TYPE_TTL)
                    .map(OntologyAssetVersion::getPublishedAt).map(Object::toString).orElse(null));
            metrics.put("lastGoodSnapshotAt", vs.latestPublishedByType(OntologyVersionService.TYPE_ABOX_SNAPSHOT)
                    .map(OntologyAssetVersion::getPublishedAt).map(Object::toString).orElse(null));
        } catch (Exception e) {
            // 版本表未初始化/不可用：不阻断健康检查
        }
        return metrics;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "prod-platform-ai");
        body.put("runtime", "spring-boot");
        body.put("docs", "See backend-app/README.md");
        return body;
    }
}
