package org.example.store;

import org.example.model.Models;

public interface SnapshotStore {
    void save(Models.Snapshot snapshot);

    Models.Snapshot get(String snapshotId);
}