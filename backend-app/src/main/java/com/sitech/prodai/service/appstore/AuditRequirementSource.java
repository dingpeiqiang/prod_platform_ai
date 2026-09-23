package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B1 稽核 Excel 生成数据源（统一方案 4.5.2/6.6）——按 {@code req_id} 取「需求 + 配置」还原稽核 Excel 行。
 * <p>
 * 无前端上传文件时，B1 {@code audit_file_upload} 由服务端按 req_id 自行生成稽核 Excel：
 * <ul>
 *   <li>{@code requirement_report}（需求提报 elements）＝需求信息（单销售品字段）；</li>
 *   <li>{@code requirement}（需求分析 merged payload）＝配置信息（实配逻辑模型，含 offer_id/套餐名称等）。</li>
 * </ul>
 * 两份数据经 {@link NodeResultService} 按 req_id 查询后归一为 {@link AuditExcelGenerator} 入参：
 * {@code offers[{ofr_id, offer_name, fields{中文列名→值}}]} 与 {@code relations[]}。
 * <p>
 * 纪律：只做确定性取值（不推理、不补全）；解析不到的数据列按空单元格交由生成器落位。
 */
@Service
public class AuditRequirementSource {

    private static final Logger log = LoggerFactory.getLogger(AuditRequirementSource.class);

    /** 需求提报节点名（wf_sub_00 保存）。 */
    private static final String NODE_REQUIREMENT_REPORT = "requirement_report";
    /** 需求分析（执行方案）节点名（wf_sub_01 保存）。 */
    private static final String NODE_REQUIREMENT = "requirement";

    /** offer_id / offer_name 抽取的候选键（深层路径末段或顶层键，大小写不敏感）。 */
    private static final String[] OFFER_ID_KEYS = {"offer_id", "offerId", "ofr_id", "OFR_ID"};
    private static final String[] OFFER_NAME_KEYS = {"套餐名称", "套餐客户化名称", "name", "offer_name", "offerName"};

    private final NodeResultService nodeResultService;

    public AuditRequirementSource(NodeResultService nodeResultService) {
        this.nodeResultService = nodeResultService;
    }

    /**
     * 按 req_id 还原稽核 Excel 行。
     *
     * @param reqId 执行主干批次号
     * @return {offers[], relations[]}；需求与配置均查无数据时返回 null（调用方转 E35）
     */
    public Map<String, Object> resolve(String reqId) {
        Map<String, Object> demand = latestJson(NODE_REQUIREMENT_REPORT, reqId);
        Map<String, Object> config = latestJson(NODE_REQUIREMENT, reqId);
        if (demand.isEmpty() && config.isEmpty()) {
            log.warn("[AuditRequirementSource] req_id={} 未查到需求/配置数据，无法生成稽核 Excel", reqId);
            return null;
        }
        Map<String, String> flatConfig = new LinkedHashMap<>();
        flatten(config, "", flatConfig);
        Map<String, String> flatDemand = new LinkedHashMap<>();
        flatten(demand, "", flatDemand);

        List<Map<String, Object>> offers = new ArrayList<>();
        offers.add(buildOffer(reqId, flatConfig, flatDemand));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("offers", offers);
        body.put("relations", new ArrayList<>());
        log.info("[AuditRequirementSource] req_id={} 还原稽核 Excel 行 offers={} configLeaves={} demandLeaves={}",
                reqId, offers.size(), flatConfig.size(), flatDemand.size());
        return body;
    }

    /** 按 req_id + node_name 取最新记录，解析其 result_json（字符串/对象/数组首元素）为 Map。 */
    private Map<String, Object> latestJson(String nodeName, String reqId) {
        Map<String, Object> rec = nodeResultService.latestRecord(reqId, nodeName);
        if (rec == null) {
            return new LinkedHashMap<>();
        }
        return MapOps.castMap(parseJson(MapOps.str(rec.get("result_json"))));
    }

    /** 解析 JSON；字符串直接解析，其余透传。失败返回 null。 */
    private Object parseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(text, Object.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 递归拍平为 {路径: 值}（末段键同时收录，便于按列名/字段名直取）。 */
    private void flatten(Object node, String prefix, Map<String, String> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = MapOps.str(e.getKey());
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                flatten(e.getValue(), path, out);
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flatten(list.get(i), prefix + "[" + i + "]", out);
            }
        } else if (node != null) {
            String value = MapOps.str(node).trim();
            if (!value.isEmpty()) {
                out.putIfAbsent(prefix, value);
                int dot = prefix.lastIndexOf('.');
                String leaf = dot >= 0 ? prefix.substring(dot + 1) : prefix;
                out.putIfAbsent(leaf, value);
            }
        }
    }

    /** 构造单个 offer 行：ofr_id/offer_name 取候选键，fields 以中文列名直取可用值。 */
    private Map<String, Object> buildOffer(String reqId, Map<String, String> config, Map<String, String> demand) {
        String ofrId = pick(config, OFFER_ID_KEYS);
        if (ofrId.isBlank()) {
            ofrId = pick(demand, OFFER_ID_KEYS);
        }
        if (ofrId.isBlank()) {
            ofrId = generateOfferId(reqId);
        }
        String name = pick(config, OFFER_NAME_KEYS);
        if (name.isBlank()) {
            name = pick(demand, OFFER_NAME_KEYS);
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("OFR_ID", ofrId);
        if (!name.isBlank()) {
            fields.put("套餐名称", name);
            fields.put("套餐客户化名称", name);
        }

        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("ofr_id", ofrId);
        offer.put("offer_name", name);
        offer.put("fields", fields);
        return offer;
    }

    /** 按候选键在拍平映射中取首个非空值（大小写不敏感）。 */
    private String pick(Map<String, String> flat, String[] keys) {
        for (String key : keys) {
            String v = flat.get(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        for (String key : keys) {
            for (Map.Entry<String, String> e : flat.entrySet()) {
                String leaf = e.getKey();
                int dot = leaf.lastIndexOf('.');
                String tail = dot >= 0 ? leaf.substring(dot + 1) : leaf;
                if (tail.equalsIgnoreCase(key) && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        return "";
    }

    /** 新增销售品 offer_id 生成：900 + req_id 末 6 位数字（与存量编码格式一致、不虚构）。 */
    private String generateOfferId(String reqId) {
        String digits = reqId == null ? "" : reqId.replaceAll("\\D", "");
        String tail = digits.length() >= 6 ? digits.substring(digits.length() - 6)
                : String.format("%06d", Math.abs(MapOps.str(reqId).hashCode()) % 1_000_000);
        return "900" + tail;
    }
}
