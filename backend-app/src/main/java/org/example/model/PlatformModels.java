package org.example.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PlatformModels {
    private PlatformModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SwrlRuleRef(@JsonProperty("rule_id") String ruleId, @JsonProperty("module") String module) {
        @JsonCreator
        public SwrlRuleRef {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvaluateSwrlRequest(
            @JsonProperty("facts") Models.FactSet facts,
            @JsonProperty("rule_refs") List<SwrlRuleRef> ruleRefs,
            @JsonProperty("rule_module") String ruleModule,
            @JsonProperty("trace_context") Models.TraceContext traceContext
    ) {
        @JsonCreator
        public EvaluateSwrlRequest {
            facts = facts == null ? new Models.FactSet(Map.of()) : facts;
            ruleRefs = ruleRefs == null ? List.of() : List.copyOf(ruleRefs);
            traceContext = traceContext == null ? new Models.TraceContext(null, null, null) : traceContext;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SwrlResult(
            @JsonProperty("rule_id") String ruleId,
            @JsonProperty("fired") boolean fired,
            @JsonProperty("conclusions") List<Map<String, Object>> conclusions,
            @JsonProperty("bindings") Map<String, Object> bindings
    ) {
        @JsonCreator
        public SwrlResult {
            conclusions = conclusions == null ? List.of() : List.copyOf(conclusions);
            bindings = bindings == null ? Map.of() : Map.copyOf(bindings);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvaluateSwrlResponse(
            @JsonProperty("results") List<SwrlResult> results,
            @JsonProperty("fired_rule_ids") List<String> firedRuleIds,
            @JsonProperty("rules") List<Map<String, Object>> rules
    ) {
        @JsonCreator
        public EvaluateSwrlResponse {
            results = results == null ? List.of() : List.copyOf(results);
            firedRuleIds = firedRuleIds == null ? List.of() : List.copyOf(firedRuleIds);
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShaclValidationRequest(
            @JsonProperty("data") Map<String, Object> data,
            @JsonProperty("shapes") String shapes,
            @JsonProperty("tenant_id") String tenantId
    ) {
        @JsonCreator
        public ShaclValidationRequest {
            data = data == null ? Map.of() : Map.copyOf(data);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShaclViolation(
            @JsonProperty("severity") String severity,
            @JsonProperty("path") String path,
            @JsonProperty("message") String message,
            @JsonProperty("source_shape") String sourceShape
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShaclValidationResponse(
            @JsonProperty("conforms") boolean conforms,
            @JsonProperty("results") List<ShaclViolation> results
    ) {
        @JsonCreator
        public ShaclValidationResponse {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HypotheticalTriple(
            @JsonProperty("subject") String subject,
            @JsonProperty("predicate") String predicate,
            @JsonProperty("object") String object,
            @JsonProperty("literal") Boolean literal
    ) {
        @JsonCreator
        public HypotheticalTriple {
            if (literal == null) {
                literal = Boolean.TRUE;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HypotheticalEvaluateResponse(
            @JsonProperty("facts") Map<String, Map<String, Object>> facts,
            @JsonProperty("decision") Models.DecisionResult decision
    ) {
        @JsonCreator
        public HypotheticalEvaluateResponse {
            facts = facts == null ? Map.of() : Map.copyOf(facts);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExplainRequest(
            @JsonProperty("trace_id") String traceId,
            @JsonProperty("audience") String audience,
            @JsonProperty("tenant_id") String tenantId
    ) {
        @JsonCreator
        public ExplainRequest {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExplainResponse(
            @JsonProperty("natural_language") String naturalLanguage,
            @JsonProperty("referenced_rules") List<String> referencedRules
    ) {
        @JsonCreator
        public ExplainResponse {
            referencedRules = referencedRules == null ? List.of() : List.copyOf(referencedRules);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SchemaResponse(@JsonProperty("classes") List<String> classes,
                                 @JsonProperty("properties") List<String> properties,
                                 @JsonProperty("timestamp") Instant timestamp) {
        @JsonCreator
        public SchemaResponse {
            classes = classes == null ? List.of() : List.copyOf(classes);
            properties = properties == null ? List.of() : List.copyOf(properties);
            timestamp = timestamp == null ? Instant.now() : timestamp;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogResponse(@JsonProperty("classes") List<String> classes,
                                  @JsonProperty("properties") List<String> properties) {
        @JsonCreator
        public CatalogResponse {
            classes = classes == null ? List.of() : List.copyOf(classes);
            properties = properties == null ? List.of() : List.copyOf(properties);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SchemaDetailRequest(@JsonProperty("class_name") String className) {
        @JsonCreator
        public SchemaDetailRequest {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SchemaDetailResponse(@JsonProperty("class_name") String className,
                                       @JsonProperty("samples") List<Map<String, Object>> samples) {
        @JsonCreator
        public SchemaDetailResponse {
            samples = samples == null ? List.of() : List.copyOf(samples);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SparqlQueryRequest(@JsonProperty("query") String query) {
        @JsonCreator
        public SparqlQueryRequest {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SparqlQueryResponse(@JsonProperty("results") List<Map<String, Object>> results) {
        @JsonCreator
        public SparqlQueryResponse {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NlQueryRequest(@JsonProperty("question") String question) {
        @JsonCreator
        public NlQueryRequest {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NlQueryResponse(@JsonProperty("answer") String answer,
                                  @JsonProperty("sparql") String sparql,
                                  @JsonProperty("results") List<Map<String, Object>> results) {
        @JsonCreator
        public NlQueryResponse {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }
}
