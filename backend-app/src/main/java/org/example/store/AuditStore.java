package org.example.store;

import org.example.model.Models;

import java.util.List;

public interface AuditStore {
    void append(String traceId, Models.AuditEntry entry);
    List<Models.AuditEntry> get(String traceId);
}
