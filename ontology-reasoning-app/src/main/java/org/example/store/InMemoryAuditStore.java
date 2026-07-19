package org.example.store;

import org.example.model.Models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAuditStore implements AuditStore {
    private final Map<String, List<Models.AuditEntry>> audits = new ConcurrentHashMap<>();

    @Override
    public void append(String traceId, Models.AuditEntry entry) {
        audits.computeIfAbsent(traceId, key -> new ArrayList<>()).add(entry);
    }

    @Override
    public List<Models.AuditEntry> get(String traceId) {
        return audits.getOrDefault(traceId, List.of());
    }
}
