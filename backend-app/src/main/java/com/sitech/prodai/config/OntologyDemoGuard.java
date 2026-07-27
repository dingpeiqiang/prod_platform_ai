package com.sitech.prodai.config;

import com.sitech.prodai.service.ops.OpsGraphSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * 数据护栏：prod profile 禁止 demo-enabled；非演示禁止加载 mock_graph 路径；
 * data-source=http 时必须配置 product-center-base-url；生产禁止指向本机 product-center。
 */
@Component
public class OntologyDemoGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OntologyDemoGuard.class);

    private final ProdAiProperties properties;
    private final Environment environment;

    public OntologyDemoGuard(ProdAiProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean demo = properties.getOntology().isDemoEnabled();
        boolean prodProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
        String graphPath = properties.getOntology().getGraphPath() == null
                ? ""
                : properties.getOntology().getGraphPath().toLowerCase(Locale.ROOT);
        String opsGraphPath = properties.getOntology().getOpsGraphPath() == null
                ? ""
                : properties.getOntology().getOpsGraphPath().toLowerCase(Locale.ROOT);
        String dataSource = properties.getOntology().getDataSource() == null
                ? "classpath"
                : properties.getOntology().getDataSource().trim().toLowerCase(Locale.ROOT);
        String baseUrl = properties.getOntology().getProductCenterBaseUrl();

        if (prodProfile && demo) {
            throw new IllegalStateException(
                    "prodai.ontology.demo-enabled must be false when spring.profiles.active includes prod/production");
        }
        if (!demo && graphPath.contains("mock_graph")) {
            throw new IllegalStateException(
                    "Refuse mock_graph when prodai.ontology.demo-enabled=false. "
                            + "Use a real graph-path or enable demo (dev/demo profile).");
        }
        if (!demo && opsGraphPath.contains("mock_graph")) {
            throw new IllegalStateException(
                    "Refuse ops-graph-path mock_graph when prodai.ontology.demo-enabled=false. "
                            + "Publish a real export for GET /api/v1/product-center/ops-graph.");
        }
        if ("http".equals(dataSource) && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalStateException(
                    "prodai.ontology.data-source=http requires prodai.ontology.product-center-base-url "
                            + "(GET {base}/ops-graph returns " + OpsGraphSchemaValidator.CONTRACT_VERSION + " JSON).");
        }
        if ("http".equals(dataSource) && OpsGraphSchemaValidator.looksLikeLocalProductCenter(baseUrl)) {
            if (prodProfile) {
                throw new IllegalStateException(
                        "prodai.ontology.product-center-base-url must not point to local product-center in prod. "
                                + "Use external BOSS/CRM ops-graph URL (contract "
                                + OpsGraphSchemaValidator.CONTRACT_VERSION + ").");
            }
            log.warn("[OntologyDemoGuard] http data-source points to local product-center ({}) — "
                            + "allowed for integration test only; production must use external URL",
                    baseUrl);
        }
        if (demo) {
            log.warn("[OntologyDemoGuard] demo-enabled=true — mock/fixture paths are active (profiles={}, dataSource={})",
                    Arrays.toString(environment.getActiveProfiles()), dataSource);
        } else {
            log.info("[OntologyDemoGuard] production-safe ontology config: dataSource={}, graphPath={}, contract={}",
                    dataSource, properties.getOntology().getGraphPath(), OpsGraphSchemaValidator.CONTRACT_VERSION);
        }
    }
}
