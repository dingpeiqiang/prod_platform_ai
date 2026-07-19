package org.example.store;

import org.example.model.Models;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySnapshotStore implements SnapshotStore {
    private final Map<String, Models.Snapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public void save(Models.Snapshot snapshot) {
        snapshots.put(snapshot.snapshotId(), snapshot);
    }

    @Override
    public Models.Snapshot get(String snapshotId) {
        return snapshots.get(snapshotId);
    }
}
