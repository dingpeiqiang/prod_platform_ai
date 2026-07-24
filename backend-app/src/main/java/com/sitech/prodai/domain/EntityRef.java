package com.sitech.prodai.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** RDF/图谱实体引用（自 org.example.Models.EntityRef 迁入正式包）。 */
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
