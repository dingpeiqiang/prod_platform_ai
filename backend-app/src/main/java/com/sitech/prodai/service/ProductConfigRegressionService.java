package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 产品配置回归用例运行器（P1-7，设计方案 §13.4）。
 * <p>固化家庭融合/校园/5G/宽带典型用例集（{@code ontology/regression_cases.json}），
 * 每条用例按 {@code 输入 draft → inferFields → checkCompliance → toMessage} 全链路执行，
 * 与期望（推导字段 / R-C* 命中 / 报文节点）逐项比对，作为：
 * <ul>
 *   <li>last-known-good 守卫 SMOKE 阶段断言源（回接 P1-6，任一断言失败阻断重载）；</li>
 *   <li>P2 抽取/推理配置化接管后的前后回归基线（字段级 diff 判据）。</li>
 * </ul>
 * <p>P1 链路边界：仍调用存量 Java inferFields/R-C* 逻辑，本运行器只固化行为基线，不接管逻辑。
 */
@Service
public class ProductConfigRegressionService {

    private static final Logger log = LoggerFactory.getLogger(ProductConfigRegressionService.class);
    private static final String DEFAULT_CASES_PATH = "classpath:ontology/regression_cases.json";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ProductOntologyService productOntologyService;
    private final ConfigMessageProjector messageProjector;

    private volatile List<Map<String, Object>> caseCache;

    public ProductConfigRegressionService(ObjectMapper objectMapper,
                                          ResourceLoader resourceLoader,
                                          ProductOntologyService productOntologyService,
                                          ConfigMessageProjector messageProjector) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.productOntologyService = productOntologyService;
        this.messageProjector = messageProjector;
    }

    /** 对现行运行态执行全部回归用例（验收核对入口）。 */
    public Map<String, Object> runAll() {
        return run(null);
    }

    /**
     * 守卫 SMOKE 断言（P1-6 回接）：用 pending 图谱跑全部用例。
     *
     * @return 失败断言列表（空 = 全过；非空 = 阻断重载）
     */
    public List<Map<String, Object>> smokeAgainstGraph(Map<String, Object> pendingGraph) {
        Map<String, Object> report = run(pendingGraph);
        return castListOfMaps(report.get("failures"));
    }

    /**
     * 执行回归用例集。
     *
     * @param graphOverride pending 图谱（SMOKE 模式）；null 表示使用现行运行态
     */
    public Map<String, Object> run(Map<String, Object> graphOverride) {
        List<Map<String, Object>> cases = loadCases();
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Map<String, Object> c : cases) {
            Map<String, Object> result = runCase(c, graphOverride);
            results.add(result);
            if (!Boolean.TRUE.equals(result.get("passed"))) {
                failures.add(result);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", failures.isEmpty());
        body.put("caseSet", "p1_dual_category_regression");
        body.put("caseSetVersion", "1.0.0");
        body.put("smokeMode", graphOverride != null);
        body.put("total", results.size());
        body.put("passedCount", results.size() - failures.size());
        body.put("failedCount", failures.size());
        body.put("failures", failures);
        body.put("cases", results);
        body.put("durationMs", System.currentTimeMillis() - start);
        if (graphOverride != null) {
            log.info("[回归] SMOKE 断言完成: total={}, failed={}", results.size(), failures.size());
        } else {
            log.info("[回归] 用例集执行完成: total={}, failed={}", results.size(), failures.size());
        }
        return body;
    }

    private Map<String, Object> runCase(Map<String, Object> c, Map<String, Object> graphOverride) {
        String caseId = str(c.get("case_id"));
        Map<String, Object> expected = castMap(c.get("expected"));
        Map<String, Object> draftInput = castMap(c.get("draft"));
        Map<String, Object> slots = castMap(c.get("slots"));
        List<Map<String, Object>> assertionFailures = new ArrayList<>();

        // 全链路：推理 → 合规 → 报文投影（P1 边界：存量 Java 逻辑）
        Map<String, Object> infer = productOntologyService.inferFields(slots, draftInput, graphOverride);
        Map<String, Object> draft = castMap(infer.get("draft"));
        Set<String> inferRules = stringSet(infer.get("appliedRules"));
        Map<String, Object> compliance = productOntologyService.checkCompliance(draft, graphOverride);
        Set<String> actualRules = new TreeSet<>(inferRules);
        actualRules.addAll(stringSet(compliance.get("appliedRules")));
        Map<String, Object> message = messageProjector.toMessage(draft);

        // 1. 期望推导字段（终态字段值比对）
        for (Map.Entry<String, Object> e : castMap(expected.get("derivedFields")).entrySet()) {
            Object actual = draft.get(e.getKey());
            if (isEmpty(actual)) {
                assertionFailures.add(assertion("derivedFields", String.valueOf(e.getKey()),
                        str(e.getValue()), "<missing>"));
            } else if (!normalizedEquals(actual, e.getValue())) {
                assertionFailures.add(assertion("derivedFields", String.valueOf(e.getKey()),
                        str(e.getValue()), str(actual)));
            }
        }

        // 2. 期望 R-C* 命中（推理 + 合规规则并集）
        Set<String> expectedRules = new TreeSet<>(castList(expected.get("rules")).stream()
                .map(this::str).toList());
        if (!expectedRules.equals(actualRules)) {
            assertionFailures.add(assertion("rules", "rules", expectedRules.toString(), actualRules.toString()));
        }

        // 3. 期望合规结论
        if (!Objects.equals(expected.get("compliancePass"), compliance.get("compliancePass"))) {
            assertionFailures.add(assertion("compliancePass", "compliancePass",
                    str(expected.get("compliancePass")), str(compliance.get("compliancePass"))));
        }

        // 4. 期望报文根键
        String expectedRoot = str(expected.get("messageRootKey"));
        if (!expectedRoot.isBlank() && !expectedRoot.equals(str(compliance.get("messageRootKey")))) {
            assertionFailures.add(assertion("messageRootKey", "messageRootKey",
                    expectedRoot, str(compliance.get("messageRootKey"))));
        }

        // 5. 期望报文节点（路径 → 值精确比对）
        for (Map.Entry<String, Object> e : castMap(expected.get("messageNodes")).entrySet()) {
            Object actual = messagePath(message, e.getKey());
            if (isEmpty(actual)) {
                assertionFailures.add(assertion("messageNodes", e.getKey(), str(e.getValue()), "<missing>"));
            } else if (!normalizedEquals(actual, e.getValue())) {
                assertionFailures.add(assertion("messageNodes", e.getKey(), str(e.getValue()), str(actual)));
            }
        }

        // 6. 期望报文节点（存在性断言，覆盖话术类易变文本）
        for (String path : castList(expected.get("messageNodePaths")).stream().map(this::str).toList()) {
            if (isEmpty(messagePath(message, path))) {
                assertionFailures.add(assertion("messageNodePaths", path, "<present>", "<missing>"));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", caseId);
        result.put("title", str(c.get("title")));
        result.put("category", str(c.get("category")));
        result.put("templateId", str(c.get("template_id")));
        result.put("templateVersion", str(c.get("template_version")));
        result.put("passed", assertionFailures.isEmpty());
        result.put("failures", assertionFailures);
        result.put("appliedRules", new ArrayList<>(actualRules));
        result.put("compliancePass", compliance.get("compliancePass"));
        result.put("messageRootKey", compliance.get("messageRootKey"));
        return result;
    }

    private List<Map<String, Object>> loadCases() {
        List<Map<String, Object>> cached = caseCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (caseCache != null) {
                return caseCache;
            }
            try {
                Resource resource = resourceLoader.getResource(DEFAULT_CASES_PATH);
                Map<String, Object> root = objectMapper.readValue(resource.getInputStream(),
                        new TypeReference<>() {
                        });
                List<Map<String, Object>> cases = new ArrayList<>();
                if (root.get("cases") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> cast = (Map<String, Object>) m;
                            cases.add(cast);
                        }
                    }
                }
                caseCache = List.copyOf(cases);
                log.info("[回归] 用例集加载完成: {} ({} 条)", root.get("case_set"), cases.size());
                return caseCache;
            } catch (Exception e) {
                log.error("[回归] 用例集加载失败: {}", e.getMessage());
                return List.of();
            }
        }
    }

    /** 报文取值：先解包品类根键（{rootKey: {baseInfo: ...}}），再按点路径下钻。 */
    private Object messagePath(Map<String, Object> message, String path) {
        if (message == null || path == null || path.isBlank()) {
            return null;
        }
        Map<String, Object> body = message;
        // 单一根键包装：进入内层 body
        if (body.size() == 1) {
            Object inner = body.values().iterator().next();
            if (inner instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                body = cast;
            }
        }
        Object current = body;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = m.get(segment);
        }
        return current;
    }

    private boolean normalizedEquals(Object actual, Object expected) {
        return normalizeValue(actual).equals(normalizeValue(expected));
    }

    /** 归一化：数字去尾 .0、布尔/字符串统一字符串形态，规避 JSON 数值类型差异。 */
    private String normalizeValue(Object value) {
        String s = String.valueOf(value).trim();
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s;
    }

    private boolean isEmpty(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private Map<String, Object> assertion(String check, String target, String expected, String actual) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("check", check);
        row.put("target", target);
        row.put("expected", expected);
        row.put("actual", actual);
        return row;
    }

    private Set<String> stringSet(Object raw) {
        Set<String> set = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    set.add(str(item));
                }
            }
        }
        return set;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object raw) {
        return raw instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListOfMaps(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(str(item));
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
