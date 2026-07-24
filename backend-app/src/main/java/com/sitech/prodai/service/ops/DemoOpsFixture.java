package com.sitech.prodai.service.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.OpsRulesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 演示专用造数 / 样例包 / 假趋势。仅 {@code prodai.ontology.demo-enabled=true} 时注册，
 * 业务服务通过 Optional 注入，生产路径无此类 bean。
 */
@Component
@ConditionalOnProperty(prefix = "prodai.ontology", name = "demo-enabled", havingValue = "true")
public class DemoOpsFixture {

    private static final Pattern NUM_PATTERN = Pattern.compile("[\\d.]+");

    private final ObjectMapper objectMapper;
    private final OpsRulesService opsRules;

    public DemoOpsFixture(ObjectMapper objectMapper, OpsRulesService opsRules) {
        this.objectMapper = objectMapper;
        this.opsRules = opsRules;
    }

    public List<Map<String, Object>> defaultCampusPackages() {
        List<Map<String, Object>> pkgs = new ArrayList<>();
        pkgs.add(pkg(
                "校园青春59", 59, "20GB", "200分钟", "校园", "电渠+厅店", "校园体验", "main_pkg",
                "1", 12, null, null,
                "套餐A：校园青春59元；含20GB+200分钟；目标校园；电渠+厅店"));
        Map<String, Object> b = pkg(
                "校园体验0元流量包", 0, "5GB", null, "校园", "全渠道", "校园体验", "addon",
                "0", null, "true", 100,
                "套餐B：校园体验0元流量包；无合约；可重复订购");
        pkgs.add(b);
        Map<String, Object> c = pkg(
                "校园融合加装包", null, null, null, "校园", "电渠+厅店", "校园体验", "addon",
                null, null, null, null,
                "套餐C：校园融合加装包；依赖宽带；未写月费");
        c.put("dependOn", "");
        pkgs.add(c);
        return pkgs;
    }

    public List<Map<String, Object>> fakeChannelTrend(double orderDelta) {
        return List.of(
                Map.of("label", "T-2", "value", 100),
                Map.of("label", "T-1", "value", 82),
                Map.of("label", "T0", "value", Math.round(100 * (1 + orderDelta)))
        );
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> expandShelfOfferings(List<Map<String, Object>> base,
                                                          Map<String, Object> plan) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> o : base) {
            byId.put(str(o.get("offeringId")), deepCopy(o));
        }
        int total = (int) num(plan.get("total"), 80);
        int needZero = (int) num(plan.get("zeroFee"), 8);
        int needDisc = (int) num(plan.get("abnormalDiscount"), 5);
        int needLow = (int) num(plan.get("lowEff"), 7);

        for (int i = 1; i <= needZero; i++) {
            String oid = String.format("OF-RISK-%03d", i);
            if (byId.containsKey(oid)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offeringId", oid);
            row.put("offeringName", "体验测试流量包0元-" + String.format("%02d", i));
            row.put("state", "上架");
            row.put("monthlyFee", 0);
            row.put("oneTimeFee", 0);
            row.put("mutexGroup", "ADDON");
            row.put("offeringType", "addon");
            row.put("shelfDays", 35 + i * 3);
            row.put("salesCnt30d", 10 + i);
            row.put("revenue30d", 0);
            row.put("hasContract", false);
            row.put("strategicTag", false);
            row.put("category", "zero_fee");
            row.put("nameHint", "体验");
            byId.put(oid, row);
        }

        for (int i = 1; i <= needDisc; i++) {
            String oid = String.format("OF-DISC-%03d", i);
            if (byId.containsKey(oid)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offeringId", oid);
            row.put("offeringName", "全额赠送可重复包-" + String.format("%02d", i));
            row.put("state", "上架");
            row.put("monthlyFee", 19);
            row.put("oneTimeFee", 0);
            row.put("discountPercent", 100);
            row.put("repeatable", true);
            row.put("targetCustomerGroup", "");
            row.put("mutexGroup", "ADDON");
            row.put("offeringType", "addon");
            row.put("shelfDays", 30 + i * 2);
            row.put("salesCnt30d", 20 + i);
            row.put("revenue30d", 50);
            row.put("hasContract", false);
            row.put("strategicTag", false);
            row.put("category", "abnormal_discount");
            byId.put(oid, row);
        }

        long lowExisting = byId.values().stream().filter(o -> "low_eff".equals(o.get("category"))).count();
        int lowSeq = 1;
        while (lowExisting < needLow) {
            String oid = String.format("OF-LOW-%03d", lowSeq++);
            if (byId.containsKey(oid)) {
                continue;
            }
            int idx = (int) lowExisting + 1;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offeringId", oid);
            row.put("offeringName", "旧版加装包-长期零销-" + String.format("%02d", idx));
            row.put("state", "上架");
            row.put("monthlyFee", 5 + idx);
            row.put("oneTimeFee", 0);
            row.put("mutexGroup", "ADDON");
            row.put("offeringType", "addon");
            row.put("shelfDays", 190 + idx * 14);
            row.put("salesCnt30d", 0);
            row.put("revenue30d", 0);
            row.put("hasContract", false);
            row.put("strategicTag", false);
            row.put("category", "low_eff");
            byId.put(oid, row);
            lowExisting++;
        }

        for (String extraCat : opsRules.demoRiskExtraCategories()) {
            for (int i = 1; i <= 3; i++) {
                String oid = String.format("OF-LOW-T%02d", i);
                if (byId.containsKey(oid)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("offeringId", oid);
                row.put("offeringName", "旧版加装包-阈值演示-" + String.format("%02d", i));
                row.put("state", "上架");
                row.put("monthlyFee", 6 + i);
                row.put("oneTimeFee", 0);
                row.put("mutexGroup", "ADDON");
                row.put("offeringType", "addon");
                row.put("shelfDays", 100 + i * 12);
                row.put("salesCnt30d", 0);
                row.put("revenue30d", 2 + i);
                row.put("hasContract", false);
                row.put("strategicTag", false);
                row.put("category", extraCat);
                byId.put(oid, row);
            }
        }

        if (byId.containsKey("OF-RISK-001")) {
            byId.get("OF-RISK-001").put("offeringName", "校园体验流量包0元");
        }
        if (byId.containsKey("OF-LOW-019")) {
            Map<String, Object> low = byId.get("OF-LOW-019");
            low.put("offeringName", "旧版彩铃包-2019");
            low.put("shelfDays", 287);
            low.put("salesCnt30d", 0);
            low.put("revenue30d", 0);
            low.put("category", "low_eff");
        }

        int seed = 1;
        while (byId.size() < total) {
            String oid = String.format("OF-N-%03d", seed);
            if (!byId.containsKey(oid)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("offeringId", oid);
                row.put("offeringName", "标准套餐-" + String.format("%03d", seed));
                row.put("state", "上架");
                row.put("monthlyFee", 39 + (seed % 20) * 5);
                row.put("oneTimeFee", 0);
                row.put("mutexGroup", seed % 3 == 0 ? "ADDON" : "MAIN_PKG");
                row.put("offeringType", seed % 3 == 0 ? "addon" : "main_pkg");
                row.put("shelfDays", 60 + seed);
                row.put("salesCnt30d", 80 + seed * 3);
                row.put("revenue30d", 5000 + seed * 120);
                row.put("hasContract", true);
                row.put("strategicTag", seed % 7 == 0);
                row.put("category", "normal");
                byId.put(oid, row);
            }
            seed++;
        }

        List<String> priority = opsRules.demoShelfPriorityIds();
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String pid : priority) {
            Map<String, Object> item = byId.remove(pid);
            if (item != null) {
                ordered.add(item);
            }
        }
        ordered.addAll(byId.values().stream()
                .sorted(Comparator.comparing(o -> str(o.get("offeringId"))))
                .collect(Collectors.toList()));
        return ordered;
    }

    private Map<String, Object> pkg(String name, Object fee, String data, String voice,
                                    String target, String channel, String scenario, String type,
                                    String contract, Integer months, String repeatable, Integer discount,
                                    String excerpt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offeringName", name);
        if (fee != null) {
            m.put("monthlyFee", fee);
        }
        if (data != null) {
            m.put("includeData", data);
        }
        if (voice != null) {
            m.put("includeVoice", voice);
        }
        m.put("targetUser", target);
        m.put("channelScope", channel);
        m.put("bizScenario", scenario);
        m.put("offeringType", type);
        if (contract != null) {
            m.put("hasContract", contract);
        }
        if (months != null) {
            m.put("contractMonths", months);
        }
        if (repeatable != null) {
            m.put("repeatable", repeatable);
        }
        if (discount != null) {
            m.put("discountPercent", discount);
        }
        m.put("sourceExcerpt", excerpt);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> src) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(src), new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>(src);
        }
    }

    private double num(Object v, double d) {
        if (v == null) {
            return d;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            Matcher m = NUM_PATTERN.matcher(String.valueOf(v));
            if (m.find()) {
                return Double.parseDouble(m.group());
            }
        } catch (Exception ignored) {
            // fall through
        }
        return d;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
