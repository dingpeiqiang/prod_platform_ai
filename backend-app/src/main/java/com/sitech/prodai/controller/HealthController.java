package com.sitech.prodai.controller;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.ops.HttpOpsProductDataSource;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final ProdAiProperties properties;
    private final Environment environment;
    private final HttpOpsProductDataSource httpOpsProductDataSource;

    public HealthController(
            ProdAiProperties properties,
            Environment environment,
            HttpOpsProductDataSource httpOpsProductDataSource
    ) {
        this.properties = properties;
        this.environment = environment;
        this.httpOpsProductDataSource = httpOpsProductDataSource;
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
        String mode = properties.getOntology().getDataSource() == null
                ? "classpath"
                : properties.getOntology().getDataSource().trim().toLowerCase();
        if ("http".equals(mode)) {
            ontology.put("http", httpOpsProductDataSource.probe());
        }
        body.put("ontology", ontology);
        return body;
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
