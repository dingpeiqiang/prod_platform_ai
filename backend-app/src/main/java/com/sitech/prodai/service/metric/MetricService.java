package com.sitech.prodai.service.metric;

import com.sitech.prodai.config.ProdAiProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 指标服务（指标域 P0 核心）：时序 / 下钻 / 异动检测三能力，全部引擎计算（LLM 不算数）。
 * <p>
 * 数据流：{@link MetricDataSource}（mock/jdbc）→ 日行缓存 → 按 {@code metrics_registry.json}
 * 字典口径聚合为 series/breakdown/anomaly 三类视图，供 {@code MetricQueryTool} 与
 * 面板渲染消费。口径变更只改字典，本服务零改动（OCP）。
 * <p>
 * 聚合口径（对齐字典 grain）：
 * <ul>
 *   <li>sum 类（revenue/order_cnt/new_users/churn_users）— 窗口内求和</li>
 *   <li>avg_last 类（active_users）— 窗口内末值快照口径（取窗口最后一日）</li>
 *   <li>derived（formula）— 基于聚合结果四则计算（arpu/attach_rate/net_growth）</li>
 * </ul>
 */
@Service
public class MetricService {

    private static final Logger log = LoggerFactory.getLogger(MetricService.class);

    private static final List<String> ATOMIC_METRICS =
            List.of("revenue", "order_cnt", "new_users", "churn_users", "active_users");

    private final MetricRegistryService registry;
    private final ProdAiProperties properties;
    private final MetricDataSource dataSource;
    private final MockMetricDataSource mockDataSource;
    private final JdbcMetricDataSource jdbcDataSource;

    /** 事实图快照供给者（mock 源生成时序基准用；由 ProductOntologyService 回注，避免构造环）。 */
    private volatile Supplier<Map<String, Object>> graphSnapshotSupplier = Map::of;

    public MetricService(MetricRegistryService registry,
                         ProdAiProperties properties,
                         @Lazy org.springframework.beans.factory.ObjectProvider<MockMetricDataSource> mockProvider,
                         @Lazy org.springframework.beans.factory.ObjectProvider<JdbcMetricDataSource> jdbcProvider) {
        this.registry = registry;
        this.properties = properties;
        this.mockDataSource = mockProvider == null ? null : mockProvider.getIfAvailable();
        this.jdbcDataSource = jdbcProvider == null ? null : jdbcProvider.getIfAvailable();
        this.dataSource = resolveDataSource(properties);
    }

    private MetricDataSource resolveDataSource(ProdAiProperties properties) {
        MetricDataSource resolved = "jdbc".equalsIgnoreCase(properties.getMetric().getSource())
                ? jdbcDataSource : mockDataSource;
        if (resolved == null) {
            log.warn("[MetricService] 指标数据源未装配（source={}），指标查询降级为不可用",
                    properties.getMetric().getSource());
            return new MetricDataSource() {
                @Override
                public String sourceId() {
                    return "unavailable";
                }

                @Override
                public java.util.List<Map<String, Object>> fetchDailyRows(
                        java.util.List<String> offeringIds, LocalDate from, LocalDate to) {
                    return java.util.List.of();
                }
            };
        }
        return resolved;
    }

    /** 由 ProductOntologyService 回注事实图快照供给者（mock 生成基准）。 */
    public void setGraphSnapshotSupplier(Supplier<Map<String, Object>> supplier) {
        this.graphSnapshotSupplier = supplier == null ? Map::of : supplier;
    }

    /** 立即刷新 mock 数据源的事实图快照（ETL 等 MetricService 之外直接持有 mockDataSource 的场景）。 */
    public void refreshMockSnapshot() {
        if (mockDataSource != null) {
            mockDataSource.setGraphSnapshot(graphSnapshotSupplier.get());
        }
    }

    @PostConstruct
    public void init() {
        log.info("[MetricService] 初始化: source={}, registry={}",
                dataSource.sourceId(), registry.version());
        if (mockDataSource != null) {
            mockDataSource.setGraphSnapshot(graphSnapshotSupplier.get());
        }
    }

    // ===== ① 时序：日粒度序列 + 窗口聚合 =====

    /**
     * 指标时序视图：窗口内日粒度序列 + 窗口总量 + 环比/同比。
     * <pre>
     * { metric, metric_name, unit, window, from, to, total, delta_pct,
     *   compare: {label, base, target, delta_pct},
     *   series: [{date, value}] }
     * </pre>
     */
    public Map<String, Object> series(String metricId, String offeringId, String windowId) {
        Map<String, Object> metric = registry.metric(metricId);
        if (metric.isEmpty()) {
            return fail("未知指标: " + metricId + "（可用: " + registry.metricIds() + "）");
        }
        String window = registry.windowForMetric(normalize(metricId), windowId);
        WindowRange range = resolveWindow(window);
        List<Map<String, Object>> rows = fetchDailyRows(offeringId, range.from(), range.to());
        if (rows.isEmpty()) {
            return fail("窗口 [" + range.from() + " ~ " + range.to() + "] 无指标事实数据"
                    + (offeringId == null || offeringId.isBlank() ? "" : "（商品 " + offeringId + "）")
                    + "，请确认数据源与窗口范围");
        }

        List<Map<String, Object>> series = dailySeries(rows, normalize(metricId), metric);
        double total = sumSeries(series);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("source", dataSource.sourceId());
        body.put("metric", normalize(metricId));
        body.put("metric_name", str(metric.get("name")));
        body.put("unit", str(metric.get("unit")));
        body.put("window", window);
        body.put("offering_id", blankToNull(offeringId));
        body.put("from", range.from().toString());
        body.put("to", range.to().toString());
        body.put("total", round2(total));
        body.put("series", series);

        Map<String, Object> compare = buildCompare(normalize(metricId), metric, offeringId, window, range);
        if (!compare.isEmpty()) {
            body.put("compare", compare);
            body.put("delta_pct", compare.get("delta_pct"));
        }
        return body;
    }

    // ===== ② 下钻：维度贡献分解 =====

    /**
     * 指标下钻视图：按维度切分窗口内总量 + 贡献占比（排序降序，top-N）。
     * <pre>
     * { metric, dimension, from, to, total,
     *   items: [{key, value, ratio}], ...窗口元信息 }
     * </pre>
     * 贡献度计算替代 opsGraph 手填 weightHint 的数据基础（异动归因 P2 输入）。
     */
    public Map<String, Object> breakdown(String metricId, String offeringId, String windowId, String dimensionId) {
        Map<String, Object> metric = registry.metric(metricId);
        if (metric.isEmpty()) {
            return fail("未知指标: " + metricId);
        }
        Map<String, Object> dimension = registry.dimension(dimensionId);
        if (dimension.isEmpty() || "time".equals(dimensionId)) {
            return fail("未知或不可下钻维度: " + dimensionId + "（可用: " + drillableDimensions(metricId) + "）");
        }
        List<String> drill = stringList(metric.get("drill"));
        if (!drill.contains(dimensionId)) {
            return fail("指标 " + normalize(metricId) + " 不支持下钻维度 " + dimensionId
                    + "（支持: " + drill + "）");
        }
        String window = registry.windowForMetric(normalize(metricId), windowId);
        WindowRange range = resolveWindow(window);
        List<Map<String, Object>> rows = fetchDailyRows(offeringId, range.from(), range.to());
        if (rows.isEmpty()) {
            return fail("窗口 [" + range.from() + " ~ " + range.to() + "] 无指标事实数据，无法下钻");
        }

        String column = str(dimension.get("column"));
        Map<String, Double> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = str(row.get(column));
            if (key.isBlank()) {
                key = str(row.get(camel(column)));
            }
            // 'ALL' 汇总行不参与下钻（与明细行会双重计数）
            if ("ALL".equalsIgnoreCase(key)) {
                continue;
            }
            if (key.isBlank()) {
                key = "UNKNOWN";
            }
            byKey.merge(key, atomicValue(row, normalize(metricId)), Double::sum);
        }
        double total = byKey.values().stream().mapToDouble(Double::doubleValue).sum();
        int limit = registry.defaultMaxBreakdownGroups();
        List<Map<String, Object>> items = byKey.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", e.getKey());
                    item.put("value", round2(e.getValue()));
                    item.put("ratio", total == 0 ? 0 : round2(e.getValue() / total));
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("source", dataSource.sourceId());
        body.put("metric", normalize(metricId));
        body.put("metric_name", str(metric.get("name")));
        body.put("dimension", dimensionId);
        body.put("dimension_label", str(dimension.get("label")));
        body.put("window", window);
        body.put("offering_id", blankToNull(offeringId));
        body.put("from", range.from().toString());
        body.put("to", range.to().toString());
        body.put("total", round2(total));
        body.put("items", items);
        return body;
    }

    // ===== ③ 异动检测：环比突降/连降 =====

    /**
     * 指标异动检测：近期窗口 vs 前一窗口环比 + 连续下滑天数。
     * <pre>
     * { metric, anomaly, delta_pct, decline_days, threshold_ref,
     *   evidence: [...] }
     * </pre>
     * threshold_ref 指向 ops_rules.json 规则（如 R-A01 降幅≤-10%），判定口径与规则联动。
     */
    public Map<String, Object> detectAnomaly(String metricId, String offeringId) {
        Map<String, Object> metric = registry.metric(metricId);
        if (metric.isEmpty()) {
            return fail("未知指标: " + metricId);
        }
        String window = registry.windowForMetric(normalize(metricId), registry.defaultWindow());
        WindowRange range = resolveWindow(window);
        WindowRange prev = previousRange(range);

        List<Map<String, Object>> curRows = fetchDailyRows(offeringId, range.from(), range.to());
        List<Map<String, Object>> prevRows = fetchDailyRows(offeringId, prev.from(), prev.to());
        if (curRows.isEmpty() || prevRows.isEmpty()) {
            return fail("窗口 [" + prev.from() + " ~ " + range.to() + "] 无指标事实数据，无法检测异动");
        }

        String mid = normalize(metricId);
        double current = windowTotal(curRows, mid, metric);
        double previous = windowTotal(prevRows, mid, metric);
        double deltaPct = previous == 0 ? 0 : round2((current - previous) / previous);
        long declineDays = consecutiveDecline(curRows, mid);
        boolean anomaly = deltaPct <= -0.10 || declineDays >= 5;

        List<String> evidence = new ArrayList<>();
        evidence.add("近窗总量 " + formatNum(current) + str(metric.get("unit"))
                + "，前窗 " + formatNum(previous) + str(metric.get("unit")));
        evidence.add("环比 " + Math.round(deltaPct * 100) + "%（阈值 ≤-10%，对齐 "
                + (registry.anomalyThresholdRef(mid).isBlank() ? "R-A01" : registry.anomalyThresholdRef(mid)) + "）");
        if (declineDays > 0) {
            evidence.add("连续下降 " + declineDays + " 天");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("source", dataSource.sourceId());
        body.put("metric", mid);
        body.put("metric_name", str(metric.get("name")));
        body.put("offering_id", blankToNull(offeringId));
        body.put("window", window);
        body.put("current_total", round2(current));
        body.put("previous_total", round2(previous));
        body.put("delta_pct", deltaPct);
        body.put("decline_days", declineDays);
        body.put("anomaly", anomaly);
        body.put("threshold_ref", blankToAnon(registry.anomalyThresholdRef(mid)));
        body.put("evidence", evidence);
        return body;
    }

    // ===== 内部：窗口与聚合 =====

    private record WindowRange(LocalDate from, LocalDate to, String label) {}

    private WindowRange resolveWindow(String windowId) {
        Map<String, Object> w = registry.timeWindow(windowId);
        LocalDate today = LocalDate.now();
        if (w.isEmpty()) {
            return new WindowRange(today.minusDays(29), today, windowId);
        }
        String kind = str(w.getOrDefault("kind", "range"));
        if (w.containsKey("days")) {
            int days = (int) num(w.get("days"), 30);
            return new WindowRange(today.minusDays(days - 1L), today, str(w.get("label")));
        }
        if ("month_to_date".equals(kind)) {
            return new WindowRange(today.withDayOfMonth(1), today, str(w.get("label")));
        }
        // compare 类窗口本身不直接取数（由 buildCompare 处理），回落近30天
        return new WindowRange(today.minusDays(29), today, str(w.get("label")));
    }

    private WindowRange previousRange(WindowRange range) {
        long span = java.time.temporal.ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        return new WindowRange(range.from().minusDays(span), range.from().minusDays(1), "prev");
    }

    /** 环比/同比（compare 类窗口）：base vs target 双窗口求和对比。 */
    private Map<String, Object> buildCompare(String metricId, Map<String, Object> metric,
                                             String offeringId, String windowId, WindowRange range) {
        Map<String, Object> w = registry.timeWindow(windowId);
        if (!"compare".equals(str(w.get("kind")))) {
            return Map.of();
        }
        WindowRange target = resolveCompareTarget(windowId, range);
        List<Map<String, Object>> baseRows = fetchDailyRows(offeringId, range.from(), range.to());
        List<Map<String, Object>> targetRows = fetchDailyRows(offeringId, target.from(), target.to());
        if (baseRows.isEmpty() || targetRows.isEmpty()) {
            return Map.of("label", str(w.get("label")), "delta_pct", 0.0,
                    "note", "对比窗口数据不完整，环比仅供参考");
        }
        double base = windowTotal(baseRows, metricId, metric);
        double value = windowTotal(targetRows, metricId, metric);
        Map<String, Object> compare = new LinkedHashMap<>();
        compare.put("label", str(w.get("label")));
        compare.put("base", round2(base));
        compare.put("target", round2(value));
        compare.put("delta_pct", base == 0 ? 0 : round2((value - base) / base));
        return compare;
    }

    private WindowRange resolveCompareTarget(String windowId, WindowRange base) {
        return switch (windowId) {
            case "wow" -> new WindowRange(base.from().minusDays(7), base.to().minusDays(7), "prev_7d");
            case "mom" -> new WindowRange(base.from().minusDays(30), base.to().minusDays(30), "prev_30d");
            case "yoy" -> new WindowRange(base.from().minusYears(1), base.to().minusYears(1), "prev_year_30d");
            default -> previousRange(base);
        };
    }

    /** 日粒度序列（按 stat_date 升序聚合；跨维度行同日合并求和）。 */
    private List<Map<String, Object>> dailySeries(List<Map<String, Object>> rows,
                                                   String metricId, Map<String, Object> metric) {
        // 派生指标：先聚合各原子引用的日值，再按公式逐日计算（LLM 不算数，引擎算数）
        if (isDerived(metric)) {
            return dailyDerivedSeries(rows, normalize(metricId), metric);
        }
        Map<LocalDate, Double> byDate = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate date = parseDate(row.get("stat_date"));
            if (date == null) {
                continue;
            }
            byDate.merge(date, atomicValue(row, metricId), Double::sum);
        }
        List<Map<String, Object>> series = new ArrayList<>();
        byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("date", e.getKey().toString());
                    point.put("value", round2(e.getValue()));
                    series.add(point);
                });
        return series;
    }

    /** 派生指标日序列：各引用原子指标先按日聚合（avg_last 口径取末值），再按公式求值。 */
    private List<Map<String, Object>> dailyDerivedSeries(List<Map<String, Object>> rows,
                                                          String metricId, Map<String, Object> metric) {
        String formula = str(metric.get("formula"));
        List<String> refs = registry.formulaRefs(formula);
        if (refs.isEmpty()) {
            return List.of();
        }
        // 引用指标各自聚合出日序列（同维度行合并；avg_last 类取日快照）
        Map<String, Map<LocalDate, Double>> refDaily = new LinkedHashMap<>();
        for (String ref : refs) {
            Map<String, Object> refMetric = registry.metric(ref);
            boolean rawColumn = refMetric.isEmpty();
            Map<LocalDate, Double> byDate = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                LocalDate date = parseDate(row.get("stat_date"));
                if (date == null) {
                    continue;
                }
                byDate.merge(date, refValue(row, ref), Double::sum);
            }
            // avg_last 引用（如 active_users）日粒度即行快照，多行同日取求和后不适用——
            // 此处统一用求和口径的日聚合；快照类引用若同日多行（多商品/多维度）求和近似活跃规模
            if (!rawColumn && "avg_last".equals(str(refMetric.get("grain")))) {
                byDate.clear();
                for (Map<String, Object> row : rows) {
                    LocalDate date = parseDate(row.get("stat_date"));
                    if (date == null) {
                        continue;
                    }
                    byDate.merge(date, atomicValue(row, ref), (a, b) -> b);
                }
            }
            refDaily.put(ref, byDate);
        }
        List<Map<String, Object>> series = new ArrayList<>();
        refDaily.values().stream().flatMap(m -> m.keySet().stream()).distinct().sorted()
                .forEach(date -> {
                    Map<String, Double> ctx = new LinkedHashMap<>();
                    for (Map.Entry<String, Map<LocalDate, Double>> e : refDaily.entrySet()) {
                        ctx.put(e.getKey(), e.getValue().getOrDefault(date, 0.0));
                    }
                    series.add(Map.of("date", date.toString(),
                            "value", round2(evalFormula(formula, ctx))));
                });
        return series;
    }

    /** 派生公式四则求值（GraalVM JS 沙箱，仅四则与括号；引用值缺失按 0 处理，除零返回 0）。 */
    private double evalFormula(String formula, Map<String, Double> refValues) {
        org.graalvm.polyglot.Context context = null;
        try {
            context = org.graalvm.polyglot.Context.newBuilder("js")
                    .allowHostAccess(org.graalvm.polyglot.HostAccess.NONE)
                    .allowHostClassLookup(cls -> false)
                    .allowIO(org.graalvm.polyglot.io.IOAccess.NONE)
                    .allowCreateProcess(false)
                    .allowCreateThread(false)
                    .allowNativeAccess(false)
                    .build();
            org.graalvm.polyglot.Value bindings = context.getBindings("js");
            for (Map.Entry<String, Double> e : refValues.entrySet()) {
                bindings.putMember(e.getKey(), e.getValue());
            }
            org.graalvm.polyglot.Value v = context.eval("js", formula);
            return v.isNumber() ? v.asDouble() : 0;
        } catch (Exception e) {
            log.warn("[MetricService] 派生公式求值失败 formula={}: {}", formula, e.getMessage());
            return 0;
        } finally {
            if (context != null) {
                context.close(true);
            }
        }
    }

    private boolean isDerived(Map<String, Object> metric) {
        return "derived".equalsIgnoreCase(str(metric.get("kind")));
    }

    private double windowTotal(List<Map<String, Object>> rows, String metricId, Map<String, Object> metric) {
        // 派生指标：窗口总量 = 按日序列公式值求和（分子分母先按日聚合再算，非全窗口总量直除）
        if (isDerived(metric)) {
            return sumSeries(dailyDerivedSeries(rows, normalize(metricId), metric));
        }
        String grain = str(metric.getOrDefault("grain", "sum"));
        if ("avg_last".equals(grain)) {
            // 快照口径（active_users）：取窗口最后一日值
            LocalDate lastDate = null;
            double lastValue = 0;
            for (Map<String, Object> row : rows) {
                LocalDate date = parseDate(row.get("stat_date"));
                if (date == null) {
                    continue;
                }
                if (lastDate == null || date.isAfter(lastDate)) {
                    lastDate = date;
                    lastValue = atomicValue(row, metricId);
                }
            }
            return lastValue;
        }
        double total = 0;
        for (Map<String, Object> row : rows) {
            total += atomicValue(row, metricId);
        }
        return total;
    }

    /** 行 → 原子指标值（avg_last 类指标行值即快照，直接取）。 */
    private double atomicValue(Map<String, Object> row, String metricId) {
        return num(row.get(columnOf(metricId)));
    }

    /** 取值列名：注册指标取其 column，未注册 token 视为宽表列名直取（如 order_cnt_addon）。 */
    private String columnOf(String metricId) {
        Map<String, Object> metric = registry.metric(metricId);
        String column = str(metric.get("column"));
        return column.isBlank() ? metricId : column;
    }

    /** 公式引用取值：注册指标走 columnOf，宽表列直取；两者兜底 metricId 原名。 */
    private double refValue(Map<String, Object> row, String ref) {
        double v = num(row.get(ref));
        if (v != 0 || row.containsKey(ref)) {
            return v;
        }
        return atomicValue(row, ref);
    }

    private long consecutiveDecline(List<Map<String, Object>> rows, String metricId) {
        List<Map.Entry<LocalDate, Double>> sorted = rows.stream()
                .filter(r -> parseDate(r.get("stat_date")) != null)
                .collect(Collectors.toMap(r -> parseDate(r.get("stat_date")),
                        r -> atomicValue(r, metricId), Double::sum))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        long streak = 0;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getValue() < sorted.get(i - 1).getValue()) {
                streak++;
            } else {
                streak = 0;
            }
        }
        return streak;
    }

    private List<Map<String, Object>> fetchDailyRows(String offeringId, LocalDate from, LocalDate to) {
        try {
            if (dataSource == mockDataSource) {
                mockDataSource.setGraphSnapshot(graphSnapshotSupplier.get());
            }
            List<String> ids = offeringId == null || offeringId.isBlank() ? null : List.of(offeringId);
            List<Map<String, Object>> rows = dataSource.fetchDailyRows(ids, from, to);
            return applyRowScopeFilter(rows);
        } catch (Exception e) {
            log.error("[MetricService] 指标数据拉取失败（source={}）: {}", dataSource.sourceId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 指标行级权限过滤（方案 §4.4，阶段 A2）：与 SPARQL 链路同构的强制注入点。
     * <p>
     * 从请求级 {@link com.sitech.prodai.service.agent.model.UserScopeContext} 取当前用户权限；
     * 受限用户（非全量渠道）按 region_id 过滤——UserScope.visibleChannels 语义为
     * 「可见区域/渠道范围」的统一键（与权限字典对齐），非 'ALL' 汇总行按白名单保留，
     * 'ALL' 行仅在用户为全量时保留（防止受限用户经汇总行绕过明细过滤反推全量）。
     * unrestricted（未启用行权限）行为零漂移。
     */
    private List<Map<String, Object>> applyRowScopeFilter(List<Map<String, Object>> rows) {
        com.sitech.prodai.service.agent.model.UserScope scope =
                com.sitech.prodai.service.agent.model.UserScopeContext.current();
        if (scope.isAllChannels() || rows == null || rows.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> filtered = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String region = str(row.get("region_id"));
            if (region.isBlank()) {
                region = str(row.get("regionId"));
            }
            // 汇总行：受限用户不可见（汇总 = 全域合计，会泄露范围外数据）
            if ("ALL".equalsIgnoreCase(region)) {
                continue;
            }
            if (scope.getVisibleChannels().contains(region)) {
                filtered.add(row);
            }
        }
        if (filtered.isEmpty() && !rows.isEmpty()) {
            log.info("[MetricService] 行权限过滤后无数据（scope={}，原 {} 行）",
                    scope.auditSummary(), rows.size());
        }
        return filtered;
    }

    private List<String> drillableDimensions(String metricId) {
        return stringList(registry.metric(metricId).get("drill"));
    }

    private String normalize(String metricId) {
        return metricId == null ? "" : metricId.trim().toLowerCase();
    }

    private LocalDate parseDate(Object v) {
        try {
            return LocalDate.parse(String.valueOf(v).substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private double sumSeries(List<Map<String, Object>> series) {
        double total = 0;
        for (Map<String, Object> point : series) {
            total += num(point.get("value"));
        }
        return total;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String formatNum(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(round2(v));
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private String blankToAnon(String s) {
        return s == null || s.isBlank() ? "R-A01" : s;
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::str).filter(s -> !s.isBlank()).collect(Collectors.toList());
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

    private double num(Object v, double d) {
        return v == null ? d : num(v);
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String camel(String column) {
        if (column == null || column.isBlank()) {
            return "";
        }
        String[] parts = column.trim().toLowerCase().split("[_\\-]");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)))
                        .append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    /** 供诊断/摘要使用：当前原子指标清单（ATOMIC_METRICS 视图）。 */
    public List<String> atomicMetricIds() {
        return List.copyOf(ATOMIC_METRICS);
    }

    /** 时间粒度枚举（day/week/month），month 聚合按自然月归并日序列。 */
    public List<String> supportedGrains() {
        return Arrays.asList("day", "week", "month");
    }

    /** 周起始（周粒度聚合用；预留 P1 时间粒度扩展）。 */
    public DayOfWeek weekStart() {
        return DayOfWeek.MONDAY;
    }
}
