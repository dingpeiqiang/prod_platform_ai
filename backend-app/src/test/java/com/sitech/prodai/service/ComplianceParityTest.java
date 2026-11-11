package com.sitech.prodai.service;

import com.sitech.prodai.ProdAiApplication;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.common.MapOps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 合规双引擎并跑比对（Java 规则 vs OWL SWRL + SHACL）。
 * <p>
 * 目的：验证「R-C03~C09 由 Openllet SWRL 转正后」结论与纯 Java 规则引擎一致。
 * 做法：同一批草稿分别用两种配置各跑一遍——
 * <ul>
 *   <li>基准：关闭 {@code swrl-enabled} 与 {@code shacl-authoritative} → 纯 Java 规则</li>
 *   <li>对照：两者均开启 → SWRL + SHACL 转正</li>
 * </ul>
 * 逐草稿比对「命中规则集合」与「是否合规」，统计一致率，要求 ≥99%。
 * <p>
 * 本测试为回归护栏：任一规则在 SWRL 转正后与 Java 语义漂移，一致率会跌破阈值。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("合规双引擎并跑比对（Java vs SWRL）")
class ComplianceParityTest {

    /** 一致率下限（AGENTS/设计文档要求 ≥99%）。 */
    private static final double PARITY_THRESHOLD = 0.99;

    /** 参与并跑比对的规则：Java 侧与 SWRL 侧均实现了这些规则。 */
    private static final Set<String> COMPARED_RULES =
            Set.of("R-C03", "R-C04", "R-C05", "R-C06", "R-C07", "R-C09");

    @Autowired
    private ProductOntologyService service;

    @Autowired
    private ProdAiProperties properties;

    // ---------- 用例矩阵 ----------

    private Map<String, Object> base() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("offeringId", "OF-PARITY-BASE");
        d.put("offeringName", "并跑基线资费");
        d.put("messageRootKey", "broadBandMainPrc");
        d.put("fixedFeeAmount", 128);
        d.put("oneTimeFee", 0);
        d.put("channelScope", "全渠道");
        return d;
    }

    private Map<String, Object> withBase(java.util.function.Consumer<Map<String, Object>> mutator) {
        Map<String, Object> d = base();
        mutator.accept(d);
        return d;
    }

    /** 在架图谱：含互斥商品与权益白名单，供 R-C03 / R-C05 使用。 */
    private Map<String, Object> graph() {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("shelfOfferings", List.of(
                Map.of("offeringId", "OF-HF-128", "offeringName", "家庭融合畅享128", "mutexGroup", "MAIN_PKG"),
                Map.of("offeringId", "OF-HF-OTHER", "offeringName", "其他主套餐", "mutexGroup", "OTHER_GROUP")));
        g.put("equityGiftWhitelist", List.of("权益赠送", "校园体验"));
        return g;
    }

    /** 用例矩阵：覆盖每条规则的命中与不命中、边界值与豁免分支。 */
    private List<Map<String, Object>> cases() {
        List<Map<String, Object>> list = new ArrayList<>();

        // R-C08 合规基线
        list.add(withBase(d -> {}));

        // R-C06 必填缺失 —— 四个必填项各一条
        list.add(withBase(d -> d.remove("offeringName")));
        list.add(withBase(d -> d.remove("messageRootKey")));
        list.add(withBase(d -> d.remove("fixedFeeAmount")));
        list.add(withBase(d -> d.remove("channelScope")));
        list.add(withBase(d -> {
            d.remove("offeringName");
            d.remove("channelScope");
        }));

        // R-C03 折扣+赠费
        list.add(withBase(d -> {
            d.put("prefDiscount", 80);
            d.put("prefFee", 20);
        }));
        list.add(withBase(d -> {
            d.put("prefDiscount", 50);
            d.put("prefFee", 10);
        }));
        // 仅折扣（无赠费）→ 不命中
        list.add(withBase(d -> d.put("prefDiscount", 80)));
        // 仅赠费 → 不命中
        list.add(withBase(d -> d.put("prefFee", 20)));

        // R-C03 在架互斥
        list.add(withBase(d -> {
            d.put("bindExistingMainPkg", "OF-HF-128");
            d.put("mutexGroup", "MAIN_PKG");
        }));
        list.add(withBase(d -> {
            d.put("bindExistingMainPkg", "OF-HF-128");
            d.put("mutexGroup", "OTHER_GROUP");
        }));
        list.add(withBase(d -> {
            d.put("bindExistingMainPkg", "OF-HF-OTHER");
            d.put("mutexGroup", "OTHER_GROUP");
        }));
        // 绑定不存在的商品 → 无冲突
        list.add(withBase(d -> {
            d.put("bindExistingMainPkg", "OF-NOT-EXIST");
            d.put("mutexGroup", "MAIN_PKG");
        }));

        // R-C04 附加缺依赖
        list.add(withBase(d -> d.put("offeringType", "addon")));
        list.add(withBase(d -> d.put("isMainOffer", false)));
        list.add(withBase(d -> d.put("isMainOffer", "false")));
        // 附加但已声明依赖 → 不命中
        list.add(withBase(d -> {
            d.put("offeringType", "addon");
            d.put("dependOn", "OF-HF-128");
        }));
        list.add(withBase(d -> {
            d.put("offeringType", "addon");
            d.put("sourceOfferRef", "OF-HF-128");
        }));

        // R-C05 零固费
        list.add(withBase(d -> {
            d.put("fixedFeeAmount", 0);
            d.put("oneTimeFee", 0);
        }));
        // 有合约 → 不命中 R-C05（但仍命中 R-C09）
        list.add(withBase(d -> {
            d.put("fixedFeeAmount", 0);
            d.put("oneTimeFee", 0);
            d.put("hasContract", true);
        }));
        // 白名单场景 → R-C05 豁免（R-C09 仍命中）
        list.add(withBase(d -> {
            d.put("fixedFeeAmount", 0);
            d.put("oneTimeFee", 0);
            d.put("bizScenario", "权益赠送");
        }));
        // 内部验证渠道 → R-C05 豁免
        list.add(withBase(d -> {
            d.put("fixedFeeAmount", 0);
            d.put("oneTimeFee", 0);
            d.put("channelScope", "内部验证");
        }));
        // 一次性费非零 → 不命中 R-C05
        list.add(withBase(d -> {
            d.put("fixedFeeAmount", 0);
            d.put("oneTimeFee", 99);
        }));

        // R-C07 全折扣可重复
        list.add(withBase(d -> {
            d.put("discountPercent", 100);
            d.put("repeatable", true);
        }));
        list.add(withBase(d -> {
            d.put("prefDiscount", 100);
            d.put("repeatChargeFlag", "是");
        }));
        // 可重复但折扣不足 100 → 不命中
        list.add(withBase(d -> {
            d.put("discountPercent", 99);
            d.put("repeatable", true);
        }));
        // 折扣 100 但不可重复 → 不命中
        list.add(withBase(d -> {
            d.put("discountPercent", 100);
            d.put("repeatable", false);
        }));

        // R-C09 固费区间边界
        list.add(withBase(d -> d.put("fixedFeeAmount", 8.99)));
        list.add(withBase(d -> d.put("fixedFeeAmount", 9)));
        list.add(withBase(d -> d.put("fixedFeeAmount", 599)));
        list.add(withBase(d -> d.put("fixedFeeAmount", 599.01)));
        list.add(withBase(d -> d.put("fixedFeeAmount", 0)));
        list.add(withBase(d -> d.put("fixedFeeAmount", -1)));
        list.add(withBase(d -> d.put("monthlyFee", 888)));
        list.add(withBase(d -> {
            d.remove("fixedFeeAmount");
            d.put("chargePlan", Map.of("fixedFeeAmount", 999));
        }));

        // 组合命中
        list.add(withBase(d -> {
            d.put("offeringType", "addon");
            d.put("discountPercent", 100);
            d.put("repeatable", true);
            d.put("fixedFeeAmount", 5);
        }));
        list.add(withBase(d -> {
            d.remove("offeringName");
            d.put("fixedFeeAmount", 999);
            d.put("prefDiscount", 90);
            d.put("prefFee", 10);
        }));

        return list;
    }

    // ---------- 双引擎执行 ----------

    /** 用指定开关组合跑一遍全矩阵，返回每条用例的 {ruleIds, pass, engine, issues}。 */
    private List<Map<String, Object>> runWith(boolean swrl, boolean shacl) {
        boolean oldSwrl = properties.getOntology().isSwrlEnabled();
        boolean oldShacl = properties.getOntology().isShaclAuthoritative();
        properties.getOntology().setSwrlEnabled(swrl);
        properties.getOntology().setShaclAuthoritative(shacl);
        try {
            List<Map<String, Object>> out = new ArrayList<>();
            Map<String, Object> g = graph();
            for (Map<String, Object> draft : cases()) {
                Map<String, Object> body = service.checkCompliance(new LinkedHashMap<>(draft), g);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ruleIds", ruleIdsOf(body));
                row.put("pass", Boolean.TRUE.equals(body.get("compliancePass")));
                row.put("engine", body.get("engine"));
                row.put("issues", summarizeIssues(body));
                out.add(row);
            }
            return out;
        } finally {
            properties.getOntology().setSwrlEnabled(oldSwrl);
            properties.getOntology().setShaclAuthoritative(oldShacl);
        }
    }

    /** 每条 issue 压成 ruleId(level,field,evidence) 便于比对差异定位。 */
    private List<String> summarizeIssues(Map<String, Object> body) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> issue : MapOps.castListOfMaps(body.get("issues"))) {
            out.add(MapOps.str(issue.get("ruleId")) + "("
                    + MapOps.str(issue.get("issueLevel")) + ","
                    + MapOps.str(issue.get("field")) + ","
                    + MapOps.str(issue.get("evidence")) + ")");
        }
        return out;
    }

    /** 提取 issue 的规则集合（仅保留 Java 与 SWRL 双侧都实现的规则）。 */
    private Set<String> ruleIdsOf(Map<String, Object> body) {
        Set<String> ids = new TreeSet<>();
        for (Map<String, Object> issue : MapOps.castListOfMaps(body.get("issues"))) {
            String ruleId = MapOps.str(issue.get("ruleId"));
            if (COMPARED_RULES.contains(ruleId)) {
                ids.add(ruleId);
            }
        }
        return ids;
    }

    // ---------- 断言 ----------

    @Test
    @DisplayName("双引擎结论一致率 ≥99%")
    void parityRate() {
        List<Map<String, Object>> javaRun = runWith(false, false);
        List<Map<String, Object>> swrlRun = runWith(true, true);

        Assertions.assertEquals(javaRun.size(), swrlRun.size(), "两轮用例数应一致");

        List<String> mismatches = new ArrayList<>();
        int same = 0;
        for (int i = 0; i < javaRun.size(); i++) {
            Set<String> javaRules = castRules(javaRun.get(i));
            Set<String> swrlRules = castRules(swrlRun.get(i));
            boolean javaPass = Boolean.TRUE.equals(javaRun.get(i).get("pass"));
            boolean swrlPass = Boolean.TRUE.equals(swrlRun.get(i).get("pass"));
            if (javaRules.equals(swrlRules) && javaPass == swrlPass) {
                same++;
            } else {
                mismatches.add("case#" + i
                        + " java=" + javaRun.get(i).get("issues") + "/pass=" + javaPass
                        + " swrl=" + swrlRun.get(i).get("issues") + "/pass=" + swrlPass);
            }
        }
        double rate = javaRun.isEmpty() ? 1.0 : (double) same / javaRun.size();
        System.out.println("[PARITY] total=" + javaRun.size() + " same=" + same
                + " rate=" + String.format("%.4f", rate));
        mismatches.forEach(m -> System.out.println("[PARITY-MISMATCH] " + m));

        Assertions.assertTrue(rate >= PARITY_THRESHOLD,
                "一致率 " + String.format("%.4f", rate) + " 低于阈值 " + PARITY_THRESHOLD
                        + "，不一致用例：\n" + String.join("\n", mismatches));
    }

    @Test
    @DisplayName("SWRL 转正确实生效（引擎标识为 openllet-swrl）")
    void swrlEngineActuallyUsed() {
        List<Map<String, Object>> swrlRun = runWith(true, true);
        long swrlCount = swrlRun.stream()
                .filter(r -> "openllet-swrl".equals(r.get("engine")))
                .count();
        Assertions.assertTrue(swrlCount > 0,
                "启用 SWRL 后应有用例走 openllet-swrl，实际引擎分布："
                        + swrlRun.stream().map(r -> String.valueOf(r.get("engine"))).distinct().toList());
    }

    @Test
    @DisplayName("关闭 SWRL 时回退纯 Java 引擎")
    void javaFallbackWhenSwrlDisabled() {
        List<Map<String, Object>> javaRun = runWith(false, false);
        long javaCount = javaRun.stream()
                .filter(r -> "java-rules".equals(r.get("engine")))
                .count();
        Assertions.assertEquals(javaRun.size(), javaCount,
                "关闭 SWRL 后所有用例应为 java-rules，实际："
                        + javaRun.stream().map(r -> String.valueOf(r.get("engine"))).distinct().toList());
    }

    @SuppressWarnings("unchecked")
    private Set<String> castRules(Map<String, Object> row) {
        return (Set<String>) row.get("ruleIds");
    }
}
