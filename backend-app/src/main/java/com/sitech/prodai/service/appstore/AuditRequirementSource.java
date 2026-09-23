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
    /** 采购/智能配置节点名（wf_sub_02 / wf_sub_12 保存；一品多品由该节点承载 offers[]）。 */
    private static final String NODE_CONFIG = "config";

    /** 一品多品数组键候选（config/requirement 结果内嵌 offers[] 时逐品生成 Excel 行）。 */
    private static final String[] OFFERS_ARRAY_KEYS = {"offers", "offerList", "offer_list", "products"};
    /** 关联关系数组键候选（同上，内嵌 relations[] 填入【关联关系】sheet）。 */
    private static final String[] RELATIONS_ARRAY_KEYS = {"relations", "relationList", "relation_list"};

    /** offer_id / offer_name 抽取的候选键（深层路径末段或顶层键，大小写不敏感）。 */
    private static final String[] OFFER_ID_KEYS = {"offer_id", "offerId", "ofr_id", "OFR_ID"};
    private static final String[] OFFER_NAME_KEYS = {"套餐名称", "套餐客户化名称", "name", "offer_name", "offerName"};

    /** 关联关系字段键候选（内嵌 relations[] 元素的字段名归一）。 */
    private static final String[] REL_A_ID_KEYS = {"a_ofr_id", "aOfrId", "a_id", "aId"};
    private static final String[] REL_A_NAME_KEYS = {"a_name", "aName", "a_ofr_name", "aOfrName"};
    private static final String[] REL_TYPE_KEYS = {"relation", "relationType", "relation_type", "rel_type"};
    private static final String[] REL_Z_ID_KEYS = {"z_ofr_id", "zOfrId", "z_id", "zId"};
    private static final String[] REL_Z_NAME_KEYS = {"z_name", "zName", "z_ofr_name", "zOfrName"};

    /**
     * 可选包 38 列 ← 配置/需求字段映射（列名，按优先级排列的候选键）。
     * <p>
     * 候选键在「配置（requirement）拍平映射」与「需求（requirement_report）拍平映射」中按序直取
     * （末段键匹配、大小写不敏感，见 {@link #pick}）；全部未命中则交由 {@link AuditExcelGenerator}
     * 按列填既定默认值。此表只做确定性取值，不做推理改写。
     */
    private static final List<String[]> COLUMN_SOURCES = List.of(
            new String[]{"OFR_ID", "offer_id", "offerId", "ofr_id"},
            new String[]{"OFFER_NBR", "offer_nbr", "offerNbr"},
            new String[]{"省级/市级*", "groupId", "省级市级", "provinceCity"},
            new String[]{"付费模式", "payMode", "付费模式"},
            new String[]{"套餐客户化名称", "prodPrcName", "套餐客户化名称", "套餐名称"},
            new String[]{"套餐名称", "套餐名称", "prodPrcName", "name"},
            new String[]{"基础包/可选包/加装包", "基础包/可选包/加装包", "offerType", "pkgType"},
            new String[]{"是否主套餐/加装包", "是否主套餐/加装包", "isMainPkg", "isMainProduct"},
            new String[]{"内容描述(套餐的关键信息，如资费价格、优惠折扣、账户/用户、包含的费用、业务等)",
                    "内容描述", "description", "contentDesc"},
            new String[]{"一单清展示（套餐内）", "一单清展示（套餐内）", "billInShow", "sysNoteNow"},
            new String[]{"一单清展示（超出资费）", "一单清展示（超出资费）", "billOutShow", "sysNoteNext"},
            new String[]{"一单清展示（其他提醒）", "一单清展示（其他提醒）", "billOtherShow", "sysNoteErke"},
            new String[]{"首月计费模式", "首月计费模式", "calcMode"},
            new String[]{"次月计费模式", "次月计费模式", "calcModeNext"},
            new String[]{"套餐销售时间", "effDate", "套餐销售时间"},
            new String[]{"套餐停售时间", "expDate", "套餐停售时间"},
            new String[]{"过渡套餐资费描述（不填则不配置过渡套餐）", "过渡套餐资费描述"},
            new String[]{"是否允许修改生失效时间", "是否允许修改生失效时间"},
            new String[]{"是否为不可退订销售品", "是否为不可退订销售品"},
            new String[]{"订购竣工短信提醒", "订购竣工短信提醒"},
            new String[]{"到期提醒", "到期提醒"},
            new String[]{"账目类型", "acctItem", "账目类型"},
            new String[]{"降速级别（单套餐/多套餐）-计费配置", "降速级别"},
            new String[]{"断网降速阈值-计费配置", "断网降速阈值"},
            new String[]{"流量提醒-计费配置", "流量提醒", "remindFlag"},
            new String[]{"语音", "billVoiceCfg.theshold", "containResource", "语音"},
            new String[]{"流量", "billGprsCfg.theshold", "containResource", "流量"},
            new String[]{"短信", "billSmsCfg.theshold", "短信"},
            new String[]{"彩信", "彩信"},
            new String[]{"接听免费范围", "接听免费范围"},
            new String[]{"其他赠送内容", "其他赠送内容", "otherEquity"},
            new String[]{"语音", "billVoiceCfg.outCharge", "out_price", "套外语音"},
            new String[]{"流量", "billGprsCfg.outChargeMode", "out_price", "套外流量"},
            new String[]{"短信", "billSmsCfg.outCharge", "短信"},
            new String[]{"彩信", "彩信"},
            new String[]{"其他业务", "其他业务"});

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
        Map<String, Object> config = latestJson(NODE_CONFIG, reqId);
        if (config.isEmpty()) {
            // 兼容：一品单品的旧工作流把执行方案写 requirement 节点（无 offers[] 数组）
            config = latestJson(NODE_REQUIREMENT, reqId);
        }
        if (demand.isEmpty() && config.isEmpty()) {
            log.warn("[AuditRequirementSource] req_id={} 未查到需求/配置数据，无法生成稽核 Excel", reqId);
            return null;
        }
        Map<String, String> flatDemand = new LinkedHashMap<>();
        flatten(demand, "", flatDemand);

        List<Map<String, Object>> offers = buildOffers(reqId, config, flatDemand);
        List<Map<String, Object>> relations = extractRelations(config, demand);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("offers", offers);
        body.put("relations", relations);
        log.info("[AuditRequirementSource] req_id={} 还原稽核 Excel 行 offers={} relations={} demandLeaves={}",
                reqId, offers.size(), relations.size(), flatDemand.size());
        return body;
    }

    /**
     * 构造销售品行集合（一品多品）：
     * <ul>
     *   <li>config 内嵌 {@code offers[]} 数组时，逐元素生成一行——每行以该品自身拍平映射优先取值，
     *       需求（requirement_report）平面字段兜底（一品多品共享需求提报）；</li>
     *   <li>无 {@code offers[]}（旧工作流/单销售品）时，整体拍平按单行生成（向后兼容）。</li>
     * </ul>
     */
    private List<Map<String, Object>> buildOffers(String reqId, Map<String, Object> config,
                                                  Map<String, String> flatDemand) {
        List<Map<String, Object>> offers = new ArrayList<>();
        List<Map<String, Object>> embedded = extractArray(config, OFFERS_ARRAY_KEYS);
        if (!embedded.isEmpty()) {
            for (Map<String, Object> item : embedded) {
                Map<String, String> flatConfig = new LinkedHashMap<>();
                flatten(item, "", flatConfig);
                offers.add(buildOffer(reqId, flatConfig, flatDemand));
            }
            return offers;
        }
        Map<String, String> flatConfig = new LinkedHashMap<>();
        flatten(config, "", flatConfig);
        offers.add(buildOffer(reqId, flatConfig, flatDemand));
        return offers;
    }

    /** 从 config（优先）或 demand 内嵌 relations[] 数组逐元素归一为关联关系行。 */
    private List<Map<String, Object>> extractRelations(Map<String, Object> config, Map<String, Object> demand) {
        List<Map<String, Object>> raw = extractArray(config, RELATIONS_ARRAY_KEYS);
        if (raw.isEmpty()) {
            raw = extractArray(demand, RELATIONS_ARRAY_KEYS);
        }
        List<Map<String, Object>> relations = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            Map<String, String> flat = new LinkedHashMap<>();
            flatten(item, "", flat);
            Map<String, Object> rel = new LinkedHashMap<>();
            rel.put("a_ofr_id", pick(flat, REL_A_ID_KEYS));
            rel.put("a_name", pick(flat, REL_A_NAME_KEYS));
            rel.put("relation", pick(flat, REL_TYPE_KEYS));
            rel.put("z_ofr_id", pick(flat, REL_Z_ID_KEYS));
            rel.put("z_name", pick(flat, REL_Z_NAME_KEYS));
            if (isRelBlank(rel)) {
                continue;
            }
            relations.add(rel);
        }
        return relations;
    }

    private static boolean isRelBlank(Map<String, Object> rel) {
        for (Object v : rel.values()) {
            if (v != null && !MapOps.str(v).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 在 Map 内按候选键查找数组值（顶层键直取；命中 List 时逐个转为 Map 元素）。
     * 兼容 {@code offers}/{@code offers[]}/{@code result.offers} 等键名与嵌套层级。
     */
    private List<Map<String, Object>> extractArray(Map<String, Object> source, String[] keys) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        for (String key : keys) {
            List<Map<String, Object>> hit = findArray(source, key);
            if (!hit.isEmpty()) {
                return hit;
            }
        }
        return new ArrayList<>();
    }

    private List<Map<String, Object>> findArray(Object node, String key) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String k = MapOps.str(e.getKey());
                if (k.equalsIgnoreCase(key) || k.equalsIgnoreCase(key + "[]")) {
                    collectMaps(e.getValue(), out);
                    if (!out.isEmpty()) {
                        return out;
                    }
                }
            }
            for (Object v : map.values()) {
                List<Map<String, Object>> hit = findArray(v, key);
                if (!hit.isEmpty()) {
                    return hit;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                List<Map<String, Object>> hit = findArray(v, key);
                if (!hit.isEmpty()) {
                    return hit;
                }
            }
        }
        return out;
    }

    private void collectMaps(Object value, List<Map<String, Object>> out) {
        if (value instanceof List<?> list) {
            for (Object el : list) {
                Map<String, Object> m = MapOps.castMap(el);
                if (!m.isEmpty()) {
                    out.add(m);
                }
            }
        } else if (value instanceof Map<?, ?> m && !m.isEmpty()) {
            out.add(MapOps.castMap(m));
        }
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

    /** 构造单个 offer 行：ofr_id/offer_name 取候选键，fields 按 38 列逐一映射配置/需求值。 */
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
        // 按列映射：配置优先、需求兜底（缺失列不写入，交由生成器按列填既定默认值）
        for (String[] src : COLUMN_SOURCES) {
            String column = src[0];
            if (fields.containsKey(column)) {
                continue;
            }
            String[] keys = new String[src.length - 1];
            System.arraycopy(src, 1, keys, 0, keys.length);
            String value = pick(config, keys);
            if (value.isBlank()) {
                value = pick(demand, keys);
            }
            if (!value.isBlank()) {
                fields.put(column, value);
            }
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
