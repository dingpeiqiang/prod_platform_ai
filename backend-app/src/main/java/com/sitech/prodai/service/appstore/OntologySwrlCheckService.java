package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产销品需求分析（wf_sub_01 节点108）· 本体 SWRL 逻辑校验代理服务（P2 接入）。
 * <p>
 * 职责：把节点108 已合并的配置要素（camelCase 模板字段）映射为本体绝对 IRI 事实图，
 * 调用 Sitech 本体服务平台的同步 SWRL 推理接口
 * {@code POST /ontology/reasoning/swrl/execute}（{@code factSourceType=FACT_GRAPH}），
 * 将 {@code diagnostics/statistics/results} 归一为工作流可直接展示的校验结论。
 * <p>
 * 设计原则：
 * <ul>
 *   <li><b>仅记录不阻断</b>：本服务不改写方案要素，也不改变 quality_gate；本体不可用/无映射/异常时
 *       返回 {@code checked=0} + 原因，由调用方按附加信息展示；</li>
 *   <li><b>可配置 IRI 映射</b>：模板字段路径 → 本体属性 IRI 由 {@link #PATH_IRI_MAP} 维护，
 *       未命中映射的字段跳过（计入 unmapped），不猜测、不伪造 IRI；</li>
 *   <li><b>实例 IRI 稳定</b>：同一 req_id+offer_seq 生成确定实例 IRI，保证多次调用可复现。</li>
 * </ul>
 */
@Service
public class OntologySwrlCheckService {

    private static final Logger log = LoggerFactory.getLogger(OntologySwrlCheckService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 本体事实图基础命名空间（可经配置覆盖）。 */
    private static final String DEFAULT_BASE_IRI = "http://sitech.com.cn/ontology/prod#";

    /** 模板字段路径 → 本体数据属性本地名（相对 base-iri）。未列出的字段在映射阶段跳过。 */
    private static final Map<String, String> PATH_IRI_MAP = new LinkedHashMap<>();

    static {
        // 数据属性：模板 schema 叶子（camelCase/点号路径）→ 本体数据属性本地名
        PATH_IRI_MAP.put("baseInfo.prodPrcName", "prodPrcName");
        PATH_IRI_MAP.put("optionalInfo.printContent.prcMonthFee", "prcMonthFee");
        PATH_IRI_MAP.put("optionalInfo.acctMonth.fixFee", "fixFee");
        PATH_IRI_MAP.put("optionalInfo.printContent.containResource", "containResource");
        PATH_IRI_MAP.put("optionalInfo.billGprsCfg.theshold", "gprsTheshold");
        PATH_IRI_MAP.put("optionalInfo.billGprsCfg.outChargeMode", "gprsOutChargeMode");
        PATH_IRI_MAP.put("optionalInfo.billGprsCfg.carryFlag", "gprsCarryFlag");
        PATH_IRI_MAP.put("optionalInfo.billVoiceCfg.theshold", "voiceTheshold");
    }

    private final RestClient client;
    private final String baseUrl;
    private final String apiKey;
    private final String projectId;
    private final String baseIri;
    /** 是否执行全部启用规则（true=ALL_ENABLED；false=ruleIds 指定）。 */
    private final boolean useAllRules;
    /** useAllRules=false 时显式指定的 SWRL 规则 ID 列表。 */
    private final List<String> ruleIds;

    public OntologySwrlCheckService(
            @Value("${prodai.appstore.ontology.base-url:http://10.88.212.165:9999/data-onto-server}") String baseUrl,
            @Value("${prodai.appstore.ontology.api-key:}") String apiKey,
            @Value("${prodai.appstore.ontology.project-id:}") String projectId,
            @Value("${prodai.appstore.ontology.base-iri:}" ) String baseIri,
            @Value("${prodai.appstore.ontology.timeout-ms:15000}") int timeoutMs,
            @Value("${prodai.appstore.ontology.swrl.use-all-rules:true}") boolean useAllRules,
            @Value("${prodai.appstore.ontology.swrl.rule-ids:}") String ruleIdsCsv) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.projectId = projectId == null ? "" : projectId.trim();
        this.baseIri = (baseIri == null || baseIri.isBlank()) ? DEFAULT_BASE_IRI : baseIri.trim();
        this.useAllRules = useAllRules;
        this.ruleIds = splitCsv(ruleIdsCsv);
        int timeout = Math.max(1000, timeoutMs);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 执行本体 SWRL 校验（工作流节点108 调用入口）。
     *
     * @param req 入参：{@code elements}（该商品已合并要素对象，camelCase 模板键）或 {@code elements_json}；
     *            可选 {@code template_id}、{@code instance_key}（默认 offer_seq）用于实例 IRI 生成
     * @return 归一化校验结论（snake_case）：{@code checked/code/message/diagnostics/statistics/results/unmapped/fact_count}
     */
    public Map<String, Object> check(Map<String, Object> req) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (projectId.isBlank()) {
            return notChecked(out, "本体项目ID未配置（prodai.appstore.ontology.project-id），跳过本体校验");
        }
        if (baseUrl.isBlank()) {
            return notChecked(out, "本体平台地址未配置，跳过本体校验");
        }
        Map<String, Object> elements = resolveElements(req);
        if (elements.isEmpty()) {
            return notChecked(out, "无有效配置要素，跳过本体校验");
        }
        String instanceKey = MapOps.str(req.get("instance_key"));
        if (instanceKey.isBlank()) {
            instanceKey = MapOps.str(req.get("offer_seq"));
        }
        if (instanceKey.isBlank()) {
            instanceKey = "1";
        }
        FactGraph build = buildFactGraph(elements, instanceKey);
        if (build.nodes.isEmpty()) {
            Map<String, Object> skip = notChecked(out, "要素无可映射的本体字段（映射表未命中），跳过本体校验");
            skip.put("unmapped", build.unmapped);
            return skip;
        }
        out.put("unmapped", build.unmapped);
        out.put("fact_count", build.nodes.size());
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("projectId", projectId);
            body.put("taskName", "需求分析要素校验-" + instanceKey);
            body.put("factSourceType", "FACT_GRAPH");
            body.put("useAllRules", useAllRules);
            body.put("ruleIds", useAllRules ? List.of() : ruleIds);
            body.put("factGraph", build.graph);
            Map<String, Object> resp = post("/ontology/reasoning/swrl/execute", body);
            return normalize(out, resp);
        } catch (Exception ex) {
            log.warn("本体 SWRL 校验调用失败，降级为仅记录: {}", ex.getMessage());
            return notChecked(out, "本体平台调用失败：" + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> out, Map<String, Object> resp) {
        out.put("checked", "1");
        long code = MapOps.toLong(resp.get("code"));
        out.put("code", String.valueOf(code));
        out.put("message", MapOps.str(resp.get("message")));
        Object dataRaw = resp.get("data");
        Map<String, Object> data = dataRaw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        out.put("status", MapOps.str(data.get("status")));
        out.put("task_id", MapOps.str(data.get("taskId")));
        List<Object> diagnostics = MapOps.castList(data.get("diagnostics"));
        out.put("diagnostics", diagnostics);
        out.put("diagnostic_count", String.valueOf(diagnostics.size()));
        out.put("error_count", String.valueOf(countLevel(diagnostics, "ERROR")));
        out.put("warn_count", String.valueOf(countLevel(diagnostics, "WARN")));
        Object statsRaw = data.get("statistics");
        out.put("statistics", statsRaw instanceof Map<?, ?> ? statsRaw : Map.of());
        List<Object> results = MapOps.castList(data.get("results"));
        out.put("results", results);
        out.put("result_count", String.valueOf(results.size()));
        return out;
    }

    private static int countLevel(List<Object> diagnostics, String level) {
        int n = 0;
        for (Object d : diagnostics) {
            if (d instanceof Map<?, ?> m && level.equalsIgnoreCase(MapOps.str(m.get("level")))) {
                n++;
            }
        }
        return n;
    }

    /** 把合并要素映射为本体内联事实图（绝对 IRI）。 */
    private FactGraph buildFactGraph(Map<String, Object> elements, String instanceKey) {
        List<Map<String, Object>> flat = new ArrayList<>();
        flatten(elements, "", flat);
        String instanceIri = baseIri + "instance/" + instanceKey;
        List<Map<String, Object>> dataProps = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();
        for (Map<String, Object> leaf : flat) {
            String path = MapOps.str(leaf.get("path"));
            Object value = leaf.get("value");
            if (value == null || MapOps.str(value).isBlank()) {
                continue;
            }
            String localName = PATH_IRI_MAP.get(path);
            if (localName == null) {
                unmapped.add(path);
                continue;
            }
            Map<String, Object> dp = new LinkedHashMap<>();
            dp.put("propertyIri", baseIri + localName);
            dp.put("value", value);
            dataProps.add(dp);
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("instanceIri", instanceIri);
        node.put("dataProperties", dataProps);
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", List.of(node));
        graph.put("edges", List.of());
        FactGraph fg = new FactGraph();
        fg.graph = graph;
        fg.nodes = dataProps;
        fg.unmapped = unmapped;
        return fg;
    }

    private static void flatten(Object node, String prefix, List<Map<String, Object>> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                flatten(e.getValue(), path, out);
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flatten(list.get(i), prefix + "[" + i + "]", out);
            }
            return;
        }
        Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put("path", prefix);
        leaf.put("value", node);
        out.add(leaf);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveElements(Map<String, Object> req) {
        Object elements = req.get("elements");
        if (elements instanceof Map<?, ?> m && !m.isEmpty()) {
            return (Map<String, Object>) m;
        }
        Object json = req.get("elements_json");
        if (json instanceof Map<?, ?> m && !m.isEmpty()) {
            return (Map<String, Object>) m;
        }
        if (json instanceof String s && !s.isBlank()) {
            try {
                JsonNode node = MAPPER.readTree(stripFence(s));
                if (node.isObject()) {
                    return MAPPER.convertValue(node, Map.class);
                }
            } catch (Exception ignore) {
                // 落空返回空 Map，由调用方按"无有效要素"处理
            }
        }
        return Map.of();
    }

    private static String stripFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl >= 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        int s = t.indexOf('{');
        int e = t.lastIndexOf('}');
        if (s >= 0 && e > s) {
            return t.substring(s, e + 1);
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) throws Exception {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!apiKey.isBlank()) {
            headers.set("x-api-key", apiKey);
        }
        String payload = MAPPER.writeValueAsString(body);
        String respText = client.post()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .body(payload)
                .retrieve()
                .body(String.class);
        if (respText == null || respText.isBlank()) {
            throw new IllegalStateException("本体平台返回空响应");
        }
        return MAPPER.readValue(respText, Map.class);
    }

    private static Map<String, Object> notChecked(Map<String, Object> out, String reason) {
        out.put("checked", "0");
        out.put("message", reason);
        out.putIfAbsent("unmapped", List.of());
        out.put("diagnostics", List.of());
        out.put("diagnostic_count", "0");
        out.put("error_count", "0");
        out.put("warn_count", "0");
        out.put("results", List.of());
        out.put("result_count", "0");
        return out;
    }

    private static List<String> splitCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String part : csv.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    /** 事实图构建中间结果。 */
    private static final class FactGraph {
        Map<String, Object> graph;
        List<Map<String, Object>> nodes;
        List<String> unmapped;
    }
}
