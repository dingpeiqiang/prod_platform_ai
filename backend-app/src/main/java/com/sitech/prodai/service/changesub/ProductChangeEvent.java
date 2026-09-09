package com.sitech.prodai.service.changesub;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 商品变更事件（方案 §6-C3，对应缺口 C5）：一次货架快照对比检出的全部字段级变更。
 * <p>不可变 POJO；{@code changes} 条目结构：
 * {@code offering_id / offering_name / change_type(fee_change|state_change) /
 * old_value / new_value}；{@code version} 为本次检测对应的快照版本号
 * （pd_ai_ontology_version.version，如 r1715...）。
 */
public final class ProductChangeEvent {

    private final String version;
    private final Instant occurredAt;
    private final List<Map<String, Object>> changes;

    public ProductChangeEvent(String version, List<Map<String, Object>> changes) {
        this.version = version == null ? "" : version;
        this.occurredAt = Instant.now();
        this.changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public String getVersion() {
        return version;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /** 字段级变更清单（不可变视图；空列表=本次无变更，发布器零开销跳过）。 */
    public List<Map<String, Object>> getChanges() {
        return changes;
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }
}
