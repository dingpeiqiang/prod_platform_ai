package org.example.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Models {
    private Models() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntityRef(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("source") String source
    ) {
        @JsonCreator
        public EntityRef {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            if (source == null || source.isBlank()) {
                source = "ontology";
            }
        }

        public String normalizedUri(String namespace) {
            if (id.startsWith("http://") || id.startsWith("https://")) {
                return id;
            }
            return namespace + id;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraceContext(
            @JsonProperty("trace_id") String traceId,
            @JsonProperty("tenant_id") String tenantId,
            @JsonProperty("timestamp") Instant timestamp
    ) {
        @JsonCreator
        public TraceContext {
            if (traceId == null || traceId.isBlank()) {
                traceId = java.util.UUID.randomUUID().toString();
            }
            if (tenantId == null || tenantId.isBlank()) {
                tenantId = "marketing_tenant";
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FactSet(@JsonProperty("root") Map<String, Object> root) {
        @JsonCreator
        public FactSet {
            root = root == null ? new LinkedHashMap<>() : new LinkedHashMap<>(root);
        }

        public FactSet with(String key, Object value) {
            Map<String, Object> copy = new LinkedHashMap<>(root);
            copy.put(key, value);
            return new FactSet(copy);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DecisionResult(
            @JsonProperty("verdict") String verdict,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("triggered_rules") List<String> triggeredRules,
            @JsonProperty("reason") String reason,
            @JsonProperty("metrics") List<EvaluationMetric> metrics,
            @JsonProperty("candidate_index") Integer candidateIndex
    ) {
        @JsonCreator
        public DecisionResult {
            if (confidence < 0.0) confidence = 0.0;
            if (confidence > 1.0) confidence = 1.0;
            triggeredRules = triggeredRules == null ? List.of() : List.copyOf(triggeredRules);
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvaluationMetric(
            @JsonProperty("name") String name,
            @JsonProperty("value") Object value,
            @JsonProperty("level") String level
    ) {
    }

    public record Snapshot(String snapshotId, TraceContext traceContext, Map<String, FactSet> facts, long ttlSeconds) {
        public Snapshot {
            facts = facts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(facts);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetrieveFactsRequest(
            @JsonProperty("entities") List<EntityRef> entities,
            @JsonProperty("intent") Map<String, Object> intent,
            @JsonProperty("trace_context") TraceContext traceContext
    ) {
        @JsonCreator
        public RetrieveFactsRequest {
            entities = entities == null ? List.of() : List.copyOf(entities);
            intent = intent == null ? Map.of() : Map.copyOf(intent);
            traceContext = traceContext == null ? new TraceContext(null, null, null) : traceContext;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetrieveFactsResponse(
            @JsonProperty("snapshot_id") String snapshotId,
            @JsonProperty("facts_map") Map<String, FactSet> factsMap
    ) {
        @JsonCreator
        public RetrieveFactsResponse {
            factsMap = factsMap == null ? Map.of() : Map.copyOf(factsMap);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvaluatePolicyRequest(
            @JsonProperty("facts") FactSet facts,
            @JsonProperty("context") Map<String, Object> context,
            @JsonProperty("trace_context") TraceContext traceContext
    ) {
        @JsonCreator
        public EvaluatePolicyRequest {
            facts = facts == null ? new FactSet(Map.of()) : facts;
            context = context == null ? Map.of() : Map.copyOf(context);
            traceContext = traceContext == null ? new TraceContext(null, null, null) : traceContext;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvaluatePolicyResponse(@JsonProperty("decision") DecisionResult decision) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StateChangePatch(
            @JsonProperty("target_entity") EntityRef targetEntity,
            @JsonProperty("changes") FactSet changes,
            @JsonProperty("description") String description
    ) {
        @JsonCreator
        public StateChangePatch {
            Objects.requireNonNull(targetEntity, "targetEntity");
            changes = changes == null ? new FactSet(Map.of()) : changes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompareStateRequest(
            @JsonProperty("base_snapshot_id") String baseSnapshotId,
            @JsonProperty("patches") List<StateChangePatch> patches,
            @JsonProperty("evaluation_metrics") List<String> evaluationMetrics,
            @JsonProperty("policy_set_id") String policySetId,
            @JsonProperty("trace_context") TraceContext traceContext
    ) {
        @JsonCreator
        public CompareStateRequest {
            patches = patches == null ? List.of() : List.copyOf(patches);
            evaluationMetrics = evaluationMetrics == null ? List.of() : List.copyOf(evaluationMetrics);
            traceContext = traceContext == null ? new TraceContext(null, null, null) : traceContext;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompareStateResponse(@JsonProperty("comparisons") List<Map<String, Object>> comparisons) {
        @JsonCreator
        public CompareStateResponse {
            comparisons = comparisons == null ? List.of() : List.copyOf(comparisons);
        }
    }

    public record AuditEntry(String step, Instant timestamp, Map<String, Object> details) {
        public AuditEntry {
            details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
            timestamp = timestamp == null ? Instant.now() : timestamp;
        }
    }

    public static Map<String, Object> snapshotToMap(Snapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("trace_id", snapshot.traceContext().traceId());
        meta.put("tenant_id", snapshot.traceContext().tenantId());
        meta.put("created_at", snapshot.traceContext().timestamp().toString());
        meta.put("ttl", snapshot.ttlSeconds());
        map.put("_meta", meta);
        Map<String, Object> facts = new LinkedHashMap<>();
        snapshot.facts().forEach((k, v) -> facts.put(k, v.root()));
        map.put("facts", facts);
        return map;
    }

    public static List<Map<String, Object>> auditEntriesToMaps(List<AuditEntry> entries) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AuditEntry entry : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("step", entry.step());
            row.put("timestamp", entry.timestamp().toString());
            row.put("details", entry.details());
            out.add(row);
        }
        return out;
    }
}
