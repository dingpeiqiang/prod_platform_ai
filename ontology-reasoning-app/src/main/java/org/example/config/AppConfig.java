package org.example.config;

import org.example.engine.IntegrativeReasonEngine;
import org.example.client.OntologyReasoningClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public IntegrativeReasonEngine integrativeReasonEngine(@Value("${onto.namespace:http://example.org/}") String namespace) {
        return new IntegrativeReasonEngine(namespace);
    }

    @Bean
    public OntologyReasoningClient ontologyReasoningClient(@Value("${onto.java-service-url:http://localhost:8088/api/v1}") String baseUrl) {
        return new OntologyReasoningClient(baseUrl);
    }
}
