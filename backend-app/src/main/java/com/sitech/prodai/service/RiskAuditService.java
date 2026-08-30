package com.sitech.prodai.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风险阈值覆盖·审计子服务（P2-7，兑现 §14.4-2 复杂度承诺，独立类禁止并入 ProductOntologyService）。
 * <p>从 {@code ProductOntologyService} 拆出并持有：运行时覆盖态（{@code overrides}）
 * 与最近 N 条操作审计链（{@code auditLog}，last-50 重启即失，与 P3-5 审计落盘顺路）。
 * <p>生效规则 = 图默认 ∪ 文件默认 ∪ 运行时覆盖；图默认归属 {@code ProductOntologyService#loadGraph()}
 * （本服务不反向依赖存量服务，图默认由调用方注入），文件默认由 {@link OpsRulesService} 供给。
 * <p>边界：live 管理视图沿用内存链展示；完整表 B 落盘由 P3-5 监控/审计落盘任务收口。
 */
@Service
public class RiskAuditService {

    private static final int AUDIT_LIMIT = 50;

    private final Map<String, Object> overrides = new ConcurrentHashMap<>();

    /** 风险阈值覆盖审计（内存，最近 AUDIT_LIMIT 条）。 */
    private final List<Map<String, Object>> auditLog = new ArrayList<>();

    /**
     * 生效阈值 = 图默认 ∪ 文件默认 ∪ 运行时覆盖（文件优先于图，覆盖最高）。
     *
     * @param graphDefaults 图谱 riskRuleDefaults（由 ProductOntologyService 注入）
     * @param fileDefaults  外置 ops_rules 阈值（{@link OpsRulesService#riskDefaults()}）
     */
    public Map<String, Object> effective(Map<String, Object> graphDefaults, Map<String, Object> fileDefaults) {
        Map<String, Object> base = new LinkedHashMap<>();
        if (graphDefaults != null) {
            base.putAll(graphDefaults);
        }
        if (fileDefaults != null) {
            base.putAll(fileDefaults);
        }
        base.putAll(overrides);
        return base;
    }

    /** 仅运行时覆盖态副本（幂等只读快照）。 */
    public Map<String, Object> overrides() {
        return new LinkedHashMap<>(overrides);
    }

    /** 审计链快照（深拷贝，防外部改动）。 */
    public synchronized List<Map<String, Object>> snapshotAudit() {
        return auditLog.stream().map(LinkedHashMap::new).collect(java.util.stream.Collectors.toList());
    }

    /** 追加审计行（{@code effective} 为调用方计算的生效阈值快照，须先于写入覆盖）。 */
    public synchronized void append(String action, Map<String, Object> detail, Map<String, Object> effective) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("at", Instant.now().toString());
        row.put("action", action);
        row.put("detail", detail == null ? Map.of() : new LinkedHashMap<>(detail));
        row.put("overrides", new LinkedHashMap<>(overrides));
        row.put("effective", effective == null ? Map.of() : new LinkedHashMap<>(effective));
        auditLog.add(0, row);
        while (auditLog.size() > AUDIT_LIMIT) {
            auditLog.remove(auditLog.size() - 1);
        }
    }

    /** 按白名单落覆盖值，返回实际写入集（空 = 无可写项）。 */
    public Map<String, Object> apply(Map<String, Object> incoming, Set<String> allowed) {
        Map<String, Object> applied = new LinkedHashMap<>();
        if (incoming != null) {
            for (Map.Entry<String, Object> e : incoming.entrySet()) {
                if (allowed.contains(e.getKey()) && e.getValue() != null) {
                    overrides.put(e.getKey(), e.getValue());
                    applied.put(e.getKey(), e.getValue());
                }
            }
        }
        return applied;
    }

    /** 清空全部覆盖，返回清空前的覆盖副本（供审计记录）。 */
    public synchronized Map<String, Object> clear() {
        Map<String, Object> before = new LinkedHashMap<>(overrides);
        overrides.clear();
        return before;
    }
}