package com.sitech.prodai.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "pd_ai_ontology_instance")
public class OntologyInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ontologyCode;
    private String userId;
    private String sessionId;
    private String status;
    private LocalDateTime submittedAt;

    @ElementCollection
    @CollectionTable(
            name = "pd_ai_ontology_instance_data",
            joinColumns = @JoinColumn(name = "ontology_instance_id")
    )
    @MapKeyColumn(name = "data_key")
    @Column(name = "data", columnDefinition = "TEXT")
    private Map<String, String> data = new LinkedHashMap<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOntologyCode() { return ontologyCode; }
    public void setOntologyCode(String ontologyCode) { this.ontologyCode = ontologyCode; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Map<String, String> getData() { return data; }
    public void setData(Map<String, Object> input) {
        this.data = new LinkedHashMap<>();
        if (input != null) {
            input.forEach((k, v) -> this.data.put(k, v == null ? null : String.valueOf(v)));
        }
    }
}
