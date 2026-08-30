package com.sitech.prodai.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-7 审计子服务拆分验证：覆盖态、生效规则合成、白名单覆盖、审计链快照行为与拆分前等价。
 * <p>（有效规则 = 图默认 ∪ 文件默认 ∪ 运行时覆盖，文件优先于图，覆盖最高）
 */
class RiskAuditServiceTest {

    private RiskAuditService newAudit() {
        return new RiskAuditService();
    }

    @Test
    void effectiveMergesGraphThenFileThenOverrides() {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("zeroSalesShelfDays", 30);
        graph.put("ruleVersion", "g1");
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("ruleVersion", "f1");
        file.put("highRiskReviewDays", 10);

        Map<String, Object> eff = newAudit().effective(graph, file);

        assertEquals(30, eff.get("zeroSalesShelfDays"));
        // 文件优先于图
        assertEquals("f1", eff.get("ruleVersion"));
        assertEquals(10, eff.get("highRiskReviewDays"));
    }

    @Test
    void applyHonorsWhitelistAndOverrideOutranksFile() {
        RiskAuditService audit = newAudit();
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("ruleVersion", "v9");
        incoming.put("notAllowed", "x");

        Set<String> allowed = Set.of("zeroSalesShelfDays", "zeroSalesDaysWindow",
                "highRiskReviewDays", "lowRevenuePercentile", "ruleVersion");
        Map<String, Object> applied = audit.apply(incoming, allowed);

        assertEquals(Map.of("ruleVersion", "v9"), applied);
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("ruleVersion", "f1");
        Map<String, Object> eff = audit.effective(Map.of(), file);
        // 覆盖最高
        assertEquals("v9", eff.get("ruleVersion"));
    }

    @Test
    void clearReturnsPriorOverridesAndEmptiesEffective() {
        RiskAuditService audit = newAudit();
        audit.apply(Map.of("ruleVersion", "v9"), Set.of("ruleVersion"));

        Map<String, Object> before = audit.clear();

        assertEquals(Map.of("ruleVersion", "v9"), before);
        assertTrue(audit.overrides().isEmpty());
    }

    @Test
    void appendSnapshotsAreStableDeepCopies() {
        RiskAuditService audit = newAudit();
        Map<String, Object> eff = Map.of("zeroSalesShelfDays", 60);
        audit.append("update", Map.of("ruleVersion", "v9"), eff);

        List<Map<String, Object>> snap = audit.snapshotAudit();
        assertEquals(1, snap.size());
        assertEquals("update", snap.get(0).get("action"));
        assertEquals(60, ((Map<?, ?>) snap.get(0).get("effective")).get("zeroSalesShelfDays"));

        // 外层快照为拷贝：增删快照行不影响内部审计链（与拆分前 snapshotRiskRuleAudit 语义一致）
        snap.clear();
        assertEquals(1, audit.snapshotAudit().size());
        List<Map<String, Object>> snap2 = audit.snapshotAudit();
        assertEquals("v9", ((Map<?, ?>) snap2.get(0).get("detail")).get("ruleVersion"));
    }

    @Test
    void auditLogTrimsToFifty() {
        RiskAuditService audit = newAudit();
        for (int i = 0; i < 70; i++) {
            audit.append("update", Map.of("n", i), Map.of());
        }
        List<Map<String, Object>> snap = audit.snapshotAudit();
        assertEquals(50, snap.size());
        // 最新在最前
        assertEquals(69, ((Map<?, ?>) snap.get(0).get("detail")).get("n"));
    }
}