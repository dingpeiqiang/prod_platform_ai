package com.sitech.prodai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事实图灌图器：将 loadGraph() 的 shelfOfferings 翻译为本体 Offering 实例写入 RDF4J。
 * <p>打通「JSON 事实图」与「RDF 本体图」两套割裂数据源，使 SPARQL 可对在架商品做语义检索。
 * 灌图幂等（先删后写），由 {@link ProductOntologyService} 在图谱加载/热重载/发布后调用。
 */
@Service
public class FactGraphSyncService {

    private static final Logger log = LoggerFactory.getLogger(FactGraphSyncService.class);

    /** Offering 实例进入本体图时携带的属性映射（JSON 字段 → 本体属性）。 */
    private static final Map<String, String> FACT_MAPPING = Map.ofEntries(
            Map.entry("offeringId", "offeringId"),
            Map.entry("offeringName", "offeringName"),
            Map.entry("category", "category"),
            Map.entry("categoryCode", "categoryCode"),
            Map.entry("categoryName", "categoryName"),
            Map.entry("offeringType", "offeringType"),
            Map.entry("productLine", "productLine"),
            Map.entry("messageRootKey", "messageRootKey"),
            Map.entry("state", "state"),
            Map.entry("monthlyFee", "monthlyFee"),
            Map.entry("fixedFeeAmount", "fixedFeeAmount")
    );

    private final Rdf4jOntologyStore rdf4jStore;

    public FactGraphSyncService(Rdf4jOntologyStore rdf4jStore) {
        this.rdf4jStore = rdf4jStore;
    }

    /**
     * 将事实图在架商品灌入 RDF 图，返回灌图条数。
     * <p>URIs 采用 {baseIri}offering/{offeringId}，属性同步经 Rdf4jOntologyStore.addInstance 完成。
     */
    public int syncShelfOfferings(Map<String, Object> graph) {
        if (graph == null) {
            return 0;
        }
        List<Map<String, Object>> offerings = castListOfMaps(graph.get("shelfOfferings"));
        int count = 0;
        for (Map<String, Object> o : offerings) {
            String offeringId = str(o.get("offeringId"));
            if (offeringId.isBlank()) {
                continue;
            }
            String uri = "offering/" + offeringId;
            Map<String, Object> facts = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : FACT_MAPPING.entrySet()) {
                Object v = o.get(e.getKey());
                if (v != null && !(String.valueOf(v)).isBlank()) {
                    facts.put(e.getValue(), v);
                }
            }
            rdf4jStore.addInstance(uri, "Offering", facts);
            count++;
        }
        log.info("[FactGraphSync] 在架商品灌图完成：{} 条 Offering 实例", count);
        return count;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListOfMaps(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }
}
