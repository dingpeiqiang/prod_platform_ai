package com.sitech.prodai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * 数据护栏：prod profile 禁止 demo-enabled；非演示禁止加载 mock_graph 路径。
 * 业务逻辑与演示/生产无关——仅 graph-path / data-source 不同。
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

        if (prodProfile && demo) {
            throw new IllegalStateException(
                    "prodai.ontology.demo-enabled must be false when spring.profiles.active includes prod/production");
        }
        if (!demo && graphPath.contains("mock_graph")) {
            throw new IllegalStateException(
                    "Refuse mock_graph when prodai.ontology.demo-enabled=false. "
                            + "Use a real graph-path or enable demo (dev/demo profile).");
        }
        if (demo) {
            log.warn("[OntologyDemoGuard] demo-enabled=true — mock/fixture paths are active (profiles={})",
                    Arrays.toString(environment.getActiveProfiles()));
        }
    }
}
