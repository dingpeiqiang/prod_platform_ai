package com.sitech.prodai.service.metric;

import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * mock 指标数据源（指标域 P0 dev/demo）：由 opsGraph 事实快照确定性生成日粒度时序。
 * <p>
 * 生成规则（同输入同输出，可复现）：
 * <ul>
 *   <li>商品清单取自 opsGraph/shelfOfferings（revenue30d/salesCnt30d 为近30天基准）</li>
 *   <li>日值 = 基准/30 × 稳定伪随机波动（哈希种子 = offeringId + 日期），有趋势性的商品
 *       （opsGraph.metrics 带 anomaly=true）叠加线性下滑斜率，保证「异动商品时序可辨」</li>
 *   <li>维度：只生成（offering,'ALL','ALL','ALL'）汇总粒度 + region 三档（下钻演示），
 *       channel/customer 维度缺省不生成（字典 drill 兜底空数据如实返回）</li>
 * </ul>
 * 本类不依赖数据库，H2 宽表仅为生产形态演示（demo 用内存生成替代）。
 */
@Component
public class MockMetricDataSource implements MetricDataSource {

    private static final Logger log = LoggerFactory.getLogger(MockMetricDataSource.class);

    /** 稳定哈希种子基数（避免跨日漂移不可复现）。 */
    private static final long SEED_BASE = 1125899906842597L;

    /** region 下钻三档（字典 region 维度演示值）。 */
    private static final List<String> REGIONS = List.of("R-A", "R-B", "R-C");

    private final ProdAiProperties properties;

    /** 上次加载的事实图快照（由 MetricService 回注，避免直接依赖 ProductOntologyService 造成环）。 */
    private volatile Map<String, Object> graphSnapshot = Map.of();

    public MockMetricDataSource(ProdAiProperties properties) {
        this.properties = properties;
    }

    /** 由 MetricService 注入事实图快照（shelfOfferings/opsGraph）。 */
    public void setGraphSnapshot(Map<String, Object> graph) {
        this.graphSnapshot = graph == null ? Map.of() : graph;
    }

    @Override
    public String sourceId() {
        return "mock";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchDailyRows(List<String> offeringIds, LocalDate from, LocalDate to) {
        Map<String, Object> graph = graphSnapshot;
        List<Map<String, Object>> shelf = castListOfMaps(graph.get("shelfOfferings"));
        Map<String, Object> opsGraph = castMap(graph.get("opsGraph"));
        if (shelf.isEmpty()) {
            return List.of();
        }
        int days = properties.getMetric().getMockDays();
        LocalDate effectiveFrom = from;
        LocalDate earliest = to.minusDays(days - 1L);
        if (effectiveFrom.isBefore(earliest)) {
            effectiveFrom = earliest;
        }
        // 趋势锚点 = 今天（与查询参数 from/to 无关）：环比检测会以不同 to（近窗/前窗）两次取数，
        // 若锚点随 to 平移，两窗相位分布完全相同，环比恒为 0；锚定真实今天后各窗口相位随日期
        // 单调推进，近窗必然晚于前窗 → 总量单调更低，异动可触发。
        final LocalDate trendAnchor = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> offering : shelf) {
            String offeringId = str(offering.get("offeringId"));
            if (offeringId.isBlank()) {
                continue;
            }
            if (offeringIds != null && !offeringIds.isEmpty() && !offeringIds.contains(offeringId)) {
                continue;
            }
            double revenueBase = num(offering.get("revenue30d")) / 30.0;
            double orderBase = num(offering.get("salesCnt30d")) / 30.0;
            double usersBase = Math.max(1, num(offering.get("salesCnt30d")) * 3);
            // opsGraph 异动商品叠加下滑趋势（市场洞察/归因演示需要「时序可辨」）
            double trendPerDay = anomalyTrendPerDay(opsGraph, offeringId);
            // 异动商品波动减半：让归一化斜率主导日间走势，连降形态可辨（R-A06 演示前提）
            double waveScale = trendPerDay < 0 ? 0.4 : 1.0;
            for (LocalDate d = effectiveFrom; !d.isAfter(to); d = d.plusDays(1)) {
                long seed = SEED_BASE * offeringId.hashCode() + d.toEpochDay();
                double wave = stableWave(seed) * waveScale;
                // 趋势按 mock 窗口归一化：整窗累计 -45% 线性下滑（相位自今天锚定），
                // 保证任意相邻 30 天子窗（近窗 vs 前窗）总量差约 -15%，可越过 -10% 异动阈值；
                // 若按 -1.2%/日绝对斜率累计，120 天窗口会越过 0 值下限导致清零或平台期抵消。
                long age = java.time.temporal.ChronoUnit.DAYS.between(trendAnchor, d);
                double trend = -0.45 * age / Math.max(1L, days - 1L);
                // 异动商品尾部 7 天确定性单调下滑（每日报价 -0.9%，波动置零）：
                // 保证 decline_days ≥ 3 稳定触发 R-A06 持续下滑规则（演示归因链闭环）。
                long tailAge = java.time.temporal.ChronoUnit.DAYS.between(d, to);
                if (trendPerDay < 0 && tailAge < 7L) {
                    trend += -0.009 * (7L - tailAge);
                    wave = 0;
                }
                long revenue = Math.max(0, Math.round(revenueBase * (1 + wave + trend)));
                int orders = (int) Math.max(0, Math.round(orderBase * (1 + wave * 0.6 + trend)));
                int users = (int) Math.max(0, Math.round(usersBase * (1 + wave * 0.3 + trend * 0.8)));
                rows.add(dayRow(d, offeringId, "ALL", "ALL", "ALL", revenue, orders, users));
                // region 三档下钻（演示贡献度分解：A/B/C 固定配比 5:3:2 + 地域噪声）
                for (String region : REGIONS) {
                    long seedR = seed + region.hashCode() * 7919L;
                    double regionWave = stableWave(seedR);
                    double regionShare = regionShare(offeringId, region);
                    rows.add(dayRow(d, offeringId, region, "ALL", "ALL",
                            Math.max(0, Math.round(revenue * regionShare * (1 + regionWave * 0.2))),
                            (int) Math.max(0, Math.round(orders * regionShare * (1 + regionWave * 0.1))),
                            (int) Math.max(0, Math.round(users * regionShare))));
                }
            }
        }
        log.info("[MockMetricDataSource] 生成演示指标行: offerings={}, rows={}, window=[{} ~ {}]",
                shelf.size(), rows.size(), effectiveFrom, to);
        return rows;
    }

    /** opsGraph.metrics 中 anomaly=true → 自窗口起点每日 -1.2% 单调下滑（近窗 < 前窗，异动可触发）。 */
    private double anomalyTrendPerDay(Map<String, Object> opsGraph, String offeringId) {
        Map<String, Object> node = castMap(opsGraph.get(offeringId));
        for (Map<String, Object> m : castListOfMaps(node.get("metrics"))) {
            Object anomaly = m.get("anomaly");
            if (Boolean.TRUE.equals(anomaly) || "true".equalsIgnoreCase(str(anomaly))) {
                return -0.012;
            }
        }
        return 0;
    }

    /** region 稳定配比（5:3:2 基准 + 商品级偏移，同种子恒定可复现）。 */
    private double regionShare(String offeringId, String region) {
        double base = switch (region) {
            case "R-A" -> 0.5;
            case "R-B" -> 0.3;
            default -> 0.2;
        };
        long h = (SEED_BASE + offeringId.hashCode() * 31L + region.hashCode()) & 0xffff;
        return base + (h / 65535.0 - 0.5) * 0.1;
    }

    /** 稳定波动：±8% 内的伪随机（同种子恒定，可复现）。 */
    private double stableWave(long seed) {
        long h = seed ^ (seed >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        double unit = (h & 0xffff) / 65535.0;
        return (unit - 0.5) * 0.16;
    }

    private long daysSince(LocalDate anchor, LocalDate d) {
        return java.time.temporal.ChronoUnit.DAYS.between(d, anchor);
    }

    private Map<String, Object> dayRow(LocalDate date, String offeringId, String region,
                                       String channel, String segment, long revenue, int orders, int users) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stat_date", date.toString());
        row.put("offering_id", offeringId);
        row.put("region_id", region);
        row.put("channel_id", channel);
        row.put("customer_segment", segment);
        row.put("revenue", revenue);
        row.put("order_cnt", orders);
        row.put("order_cnt_main", (int) Math.max(0, Math.round(orders * 0.7)));
        row.put("order_cnt_addon", (int) Math.max(0, Math.round(orders * 0.25)));
        row.put("new_users", (int) Math.round(orders * 0.35));
        row.put("churn_users", (int) Math.round(orders * 0.08));
        row.put("active_users", users);
        return row;
    }

    private List<Map<String, Object>> castListOfMaps(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, val) -> row.put(String.valueOf(k), val));
                    out.add(row);
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), val));
            return out;
        }
        return new LinkedHashMap<>();
    }

    private double num(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
