package com.sitech.prodai.service.metric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运营指标字典单源：加载 {@code metrics_registry.json}，供 MetricService / MetricQueryTool / 理解层共用。
 * <p>
 * 对齐 {@code OpsRulesService} 的治理风格：@PostConstruct 加载、版本号、软校验；
 * 字典缺键软补齐、类型错误抛出（与 OpsGraphSchemaValidator 同口径）。
 * <p>
 * 指标即节点：别名（aliases）喂给理解层做口语→指标映射；thresholdRef 与
 * ops_rules.json 规则阈值联动——改指标口径，规则自动跟随。
 */
@Service
public class MetricRegistryService {

    private static final Logger log = LoggerFactory.getLogger(MetricRegistryService.class);

    /** 指标宽表原子列白名单：source.column 必须命中，防字典笔误导致查询期静默空结果。 */
    private static final Set<String> WIDE_TABLE_COLUMNS = Set.of(
            "revenue", "order_cnt", "new_users", "churn_users", "active_users",
            "order_cnt_addon", "order_cnt_main");

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ProdAiProperties properties;

    private volatile Map<String, Object> registryCache;

    public MetricRegistryService(ObjectMapper objectMapper,
                                 ResourceLoader resourceLoader,
                                 ProdAiProperties properties) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            Map<String, Object> loaded = load();
            List<String> errors = validate(loaded);
            if (!errors.isEmpty()) {
                log.warn("[MetricRegistry] 指标字典软校验告警: {}", errors);
            }
            registryCache = loaded;
            log.info("[MetricRegistry] 指标字典加载完成: version={}, metrics={}, dimensions={}",
                    version(), metricIds().size(), dimensionIds().size());
        } catch (Exception e) {
            log.error("[MetricRegistry] 指标字典加载失败，指标查询能力不可用: {}", e.getMessage());
            registryCache = Map.of("version", "MetricsRegistry-empty", "metrics", Map.of(), "dimensions", Map.of());
        }
    }

    public synchronized Map<String, Object> load() {
        if (registryCache != null) {
            return registryCache;
        }
        registryCache = loadPending();
        return registryCache;
    }

    /** 解析字典文件为 pending（守卫 LOAD 语义，同 OpsRulesService）。 */
    public Map<String, Object> loadPending() {
        try {
            Resource resource = resourceLoader.getResource(properties.getMetric().getRegistryPath());
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, new TypeReference<>() {});
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load metrics registry: " + e.getMessage(), e);
        }
    }

    /** 守卫 COMMIT：校验通过后原子切换缓存。 */
    public synchronized void swap(Map<String, Object> pending) {
        List<String> errors = validate(pending);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("metrics registry invalid: " + String.join("; ", errors));
        }
        this.registryCache = pending;
    }

    public String version() {
        return str(load().getOrDefault("version", "MetricsRegistry"));
    }

    /** 软校验：返回告警/错误清单（派生指标 formula 引用未注册指标、原子指标列越界、维度列缺失）。 */
    public List<String> validate(Map<String, Object> registry) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> metrics = castMap(registry.get("metrics"));
        Map<String, Object> dimensions = castMap(registry.get("dimensions"));
        for (Map.Entry<String, Object> e : metrics.entrySet()) {
            Map<String, Object> m = castMap(e.getValue());
            String kind = str(m.getOrDefault("kind", "atomic")).toLowerCase(Locale.ROOT);
            if ("atomic".equals(kind)) {
                String column = str(m.get("column"));
                if (column.isBlank() || !WIDE_TABLE_COLUMNS.contains(column)) {
                    problems.add("atomic metric '" + e.getKey() + "' column not in wide table: " + column);
                }
            } else if ("derived".equals(kind)) {
                for (String ref : formulaRefs(str(m.get("formula")))) {
                    if (!metrics.containsKey(ref)) {
                        problems.add("derived metric '" + e.getKey() + "' formula references unknown metric: " + ref);
                    }
                }
            }
            for (Object drill : stringList(m.get("drill"))) {
                if (!dimensions.containsKey(drill)) {
                    problems.add("metric '" + e.getKey() + "' drill dimension not declared: " + drill);
                }
            }
        }
        for (Map.Entry<String, Object> e : dimensions.entrySet()) {
            if ("time".equals(e.getKey())) {
                continue;
            }
            Map<String, Object> d = castMap(e.getValue());
            if (str(d.get("column")).isBlank()) {
                problems.add("dimension '" + e.getKey() + "' missing column");
            }
        }
        return problems;
    }

    /**
     * 派生公式引用解析：{@code revenue / active_users} → [revenue, active_users]。
     * 引用 token 须为已注册指标 ID 或宽表原子列（如 order_cnt_addon/order_cnt_main）。
     */
    public List<String> formulaRefs(String formula) {
        List<String> refs = new ArrayList<>();
        if (formula == null || formula.isBlank()) {
            return refs;
        }
        Map<String, Object> metrics = castMap(load().get("metrics"));
        for (String token : formula.split("[+\\-*/()\\s]+")) {
            String t = token.trim();
            if (!t.isEmpty() && !t.matches("\\d+(\\.\\d+)?")
                    && (metrics.containsKey(t) || WIDE_TABLE_COLUMNS.contains(t))) {
                refs.add(t);
            }
        }
        return refs;
    }

    // ===== 指标/维度/窗口访问器 =====

    public Map<String, Object> metric(String metricId) {
        return castMap(castMap(load().get("metrics")).get(normalizeMetricId(metricId)));
    }

    /** 口语/别名 → 指标 ID（含 ID 自身与中文名匹配；长短语优先，防「收入」误吞「户均收入」）。 */
    public String resolveMetricByAlias(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String bestId = null;
        int bestLen = 0;
        for (Map.Entry<String, Object> e : castMap(load().get("metrics")).entrySet()) {
            Map<String, Object> m = castMap(e.getValue());
            for (String alias : allAliases(e.getKey(), m)) {
                if (text.contains(alias) && alias.length() > bestLen) {
                    bestId = e.getKey();
                    bestLen = alias.length();
                }
            }
        }
        return bestId;
    }

    public Map<String, Object> dimension(String dimensionId) {
        return castMap(castMap(load().get("dimensions")).get(dimensionId));
    }

    public Map<String, Object> timeWindow(String windowId) {
        return castMap(castMap(load().get("timeWindows")).get(normalizeWindowId(windowId)));
    }

    public Map<String, Object> defaults() {
        return castMap(load().get("defaults"));
    }

    public int defaultMaxSeriesPoints() {
        return (int) num(defaults().get("maxSeriesPoints"), 90);
    }

    public int defaultMaxBreakdownGroups() {
        return (int) num(defaults().get("maxBreakdownGroups"), 20);
    }

    public String defaultWindow() {
        return str(defaults().getOrDefault("window", "recent_30d"));
    }

    public List<String> metricIds() {
        return new ArrayList<>(castMap(load().get("metrics")).keySet());
    }

    public List<String> dimensionIds() {
        return new ArrayList<>(castMap(load().get("dimensions")).keySet());
    }

    /** 指标支持的时间窗口；缺省回落 defaults.window。 */
    public String windowForMetric(String metricId, String requested) {
        Map<String, Object> m = metric(metricId);
        String want = normalizeWindowId(requested);
        if (!want.isBlank()) {
            List<String> supported = stringList(m.get("timeWindows"));
            if (supported.contains(want)) {
                return want;
            }
            if (!supported.isEmpty()) {
                return supported.get(0);
            }
        }
        return normalizeWindowId(defaultWindow());
    }

    /** 指标的异动阈值引用（R-A01 等）；无则空串。 */
    public String anomalyThresholdRef(String metricId) {
        Map<String, Object> anomaly = castMap(metric(metricId).get("anomaly"));
        return str(anomaly.get("thresholdRef"));
    }

    // ===== 内部工具 =====

    private String normalizeMetricId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeWindowId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> allAliases(String metricId, Map<String, Object> metric) {
        Set<String> out = new LinkedHashSet<>();
        out.add(metricId);
        String name = str(metric.get("name"));
        if (!name.isBlank()) {
            out.add(name);
        }
        out.addAll(stringList(metric.get("aliases")));
        return out.stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(this::str).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    private double num(Object v, double d) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return d;
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private Map<String, Object> castMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), val));
            return out;
        }
        return new LinkedHashMap<>();
    }
}
