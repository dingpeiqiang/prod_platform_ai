package org.example.config;

import org.example.engine.IntegrativeReasonEngine;
import org.example.client.OntologyReasoningClient;
import com.sitech.prodai.service.OntologyStore;
import org.example.store.InMemorySnapshotStore;
import org.example.store.InMemoryAuditStore;
import com.sitech.prodai.service.Rdf4jOntologyStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AppConfig {

    @Bean
    @Primary
    public OntologyStore ontologyStore(Rdf4jOntologyStore rdf4jOntologyStore) {
        return rdf4jOntologyStore;
    }

    @Bean
    public IntegrativeReasonEngine integrativeReasonEngine(
            @Value("${onto.namespace:http://example.org/}") String namespace,
            OntologyStore ontologyStore) {
        return new IntegrativeReasonEngine(namespace, new InMemorySnapshotStore(), new InMemoryAuditStore());
    }

    @Bean
    public OntologyReasoningClient ontologyReasoningClient(@Value("${onto.java-service-url:http://localhost:8088/api/v1}") String baseUrl) {
        return new OntologyReasoningClient(baseUrl);
    }
}