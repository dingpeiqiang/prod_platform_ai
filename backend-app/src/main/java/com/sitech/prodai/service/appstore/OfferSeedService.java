package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V1.6 种子数据中心：加载 seed_offers.json（18 销售品全量规则）+ preset_map.json（18×10 测点预期值）。
 * <p>
 * 数据源：《产品信息.txt》结构化结果；任一销售品输入均返回与该销售品资费规则一致的结构化结果。
 * 线程安全：启动一次性加载，运行期只读。
 */
@Service
public class OfferSeedService {

    private static final Logger log = LoggerFactory.getLogger(OfferSeedService.class);

    private static final String SEED_FILE = "appstore/seed_offers.json";
    private static final String PRESET_FILE = "appstore/preset_map.json";

    private final ObjectMapper objectMapper;

    /** offer_id -> 销售品规则（LinkedHashMap 保序） */
    private final Map<String, Map<String, Object>> offers = new LinkedHashMap<>();
    /** offer_id -> 测点预期值映射 */
    private final Map<String, Map<String, Object>> presetMap = new ConcurrentHashMap<>();
    /** 测点编码表（10 个） */
    private List<String> testPoints = new ArrayList<>();

    public OfferSeedService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        loadOffers();
        loadPresets();
        log.info("[OfferSeedService] 种子数据加载完成 offers={} presets={}", offers.size(), presetMap.size());
    }

    private void loadOffers() {
        Map<String, Object> root = readJson(SEED_FILE);
        List<Map<String, Object>> list = castMapList(root == null ? null : root.get("offers"));
        for (Map<String, Object> offer : list) {
            Object id = offer.get("offer_id");
            if (id != null && !String.valueOf(id).isBlank()) {
                offers.put(String.valueOf(id), offer);
            }
        }
    }

    private void loadPresets() {
        Map<String, Object> root = readJson(PRESET_FILE);
        if (root == null) {
            return;
        }
        Object points = root.get("test_points");
        if (points instanceof List<?> list) {
            testPoints = list.stream().map(String::valueOf).toList();
        }
        Map<String, Object> map = castMap(root.get("preset_map"));
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Map<String, Object> v = castMap(e.getValue());
            if (!v.isEmpty()) {
                presetMap.put(e.getKey(), v);
            }
        }
    }

    /* ---------------- 查询接口 ---------------- */

    public Map<String, Object> findOffer(String offerId) {
        return offerId == null ? null : offers.get(offerId.trim());
    }

    public boolean exists(String offerId) {
        return findOffer(offerId) != null;
    }

    /** 全部销售品列表（保序：5G-A 系列 10 个 + 权益随心选 8 个） */
    public List<Map<String, Object>> listOffers() {
        return new ArrayList<>(offers.values());
    }

    public int count() {
        return offers.size();
    }

    public Map<String, Object> presetsOf(String offerId) {
        return presetMap.getOrDefault(offerId == null ? "" : offerId.trim(), Map.of());
    }

    public List<String> testPoints() {
        return testPoints;
    }

    /**
     * 相似度匹配：关键词命中（名称/系列/权益类型/资费档位）加权 + 资费结构相似度，返回按 score 降序列表。
     *
     * @param businessDesc 业务需求描述（非空，由控制器校验）
     * @return 相似销售品列表（按相似度降序），元素含 similarOfferId/similarOfferName/similarityScore/similarityDesc/offerInfo（完整产品配置信息）
     */
    public List<Map<String, Object>> matchSimilar(String businessDesc) {
        String text = businessDesc == null ? "" : businessDesc;
        String lower = text.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> offer : offers.values()) {
            double score = scoreOffer(lower, offer);
            if (score <= 0) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("similarOfferId", MapOps.str(offer.get("offer_id")));
            item.put("similarOfferName", MapOps.str(offer.get("offer_name")));
            item.put("similarityScore", String.format(Locale.ROOT, "%.2f", score));
            item.put("similarityDesc", descOf(offer));
            item.put("offerInfo", toFields18(offer));
            result.add(item);
        }
        result.sort((a, b) -> Double.compare(
                Double.parseDouble(String.valueOf(b.get("similarityScore"))),
                Double.parseDouble(String.valueOf(a.get("similarityScore")))));
        return result;
    }

    /** 相似度最高（第 1 名）的相似销售品，附完整产品配置信息 offerInfo；无命中返回 null */
    public Map<String, Object> matchBestSimilar(String businessDesc) {
        List<Map<String, Object>> list = matchSimilar(businessDesc);
        return list.isEmpty() ? null : list.get(0);
    }

    /* ---------------- 种子 → 四类18字段同构转换 ---------------- */

    /**
     * 销售品种子全量规则 → 与需求要素解析同构的四类18字段结构（fields 数组：field/category/value）。
     * 两侧共用同一套配置结构模板规范（字段名=本体注册表18字段），整合环节即同构键值合并。
     */
    public Map<String, Object> toFields18(Map<String, Object> offer) {
        Map<String, Object> inFee = castMap(offer.get("in_fee"));
        Map<String, Object> outFee = castMap(offer.get("out_fee"));
        Map<String, Object> subCard = castMap(offer.get("sub_card"));
        List<String> channels = castStrList(offer.get("sale_channels"));

        List<Map<String, Object>> fields = new ArrayList<>();
        // A. 基础信息
        fields.add(f("产品名称", "A.基础信息", MapOps.str(offer.get("offer_name"))));
        fields.add(f("产品属性", "A.基础信息", "rights".equals(offer.get("series")) ? "增值" : "基础"));
        fields.add(f("产品编码", "A.基础信息", "由智能配置生成"));
        fields.add(f("生效日期", "A.基础信息", "立即生效"));
        fields.add(f("退订规则", "A.基础信息", MapOps.str(offer.get("cancel_rule"))));
        // B. 资源配置
        fields.add(f("流量资源", "B.资源配置", textOr(inFee.get("国内通用流量"), "无")));
        fields.add(f("语音资源", "B.资源配置", textOr(inFee.get("国内语音拨打"), "无")));
        fields.add(f("短信资源", "B.资源配置", textOr(inFee.get("卫星权益") == null ? null : shortSmsOf(inFee), "无")));
        // C. 营销资源
        fields.add(f("套餐固定费", "C.营销资源", MapOps.str(offer.get("monthly_fee")).isBlank()
                ? "待补充" : MapOps.str(offer.get("monthly_fee")) + "元/月"));
        fields.add(f("收费方式", "C.营销资源", payModeOf(offer)));
        fields.add(f("优惠条件", "C.营销资源", "无"));
        fields.add(f("优惠期", "C.营销资源", "无"));
        // D. 销售规则
        fields.add(f("渠道类型", "D.销售规则", channels.isEmpty() ? "实体渠道、电子渠道、直销渠道" : String.join("、", channels)));
        fields.add(f("适用地区", "D.销售规则", "全国（不含港澳台）"));
        fields.add(f("订购限制", "D.销售规则", textOr(offer.get("order_rule"), "无")));
        fields.add(f("副卡规则", "D.销售规则", subCard.isEmpty() ? "不允许办理副卡"
                : (Boolean.TRUE.equals(subCard.get("允许办理"))
                        ? "允许办理副卡；" + MapOps.str(subCard.get("共享规则"))
                        : "不允许办理副卡")));
        fields.add(f("计费周期", "D.销售规则", textOr(offer.get("billing_cycle"), "自然月")));
        fields.add(f("销售品状态", "D.销售规则", "在售"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("similarOfferId", MapOps.str(offer.get("offer_id")));
        body.put("similarOfferName", MapOps.str(offer.get("offer_name")));
        body.put("series", MapOps.str(offer.get("series")));
        body.put("sub_type", MapOps.str(offer.get("sub_type")));
        body.put("fields", fields);
        return body;
    }

    /** 套外/其他补充资费明细（18字段外的原值保留，供整合节点参考） */
    private static Map<String, Object> f(String field, String category, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", field);
        m.put("category", category);
        m.put("value", value == null ? "" : value);
        return m;
    }

    private static String textOr(Object v, String def) {
        String s = MapOps.str(v);
        return s.isBlank() ? def : s;
    }

    /** 卫星权益中的短信额度（如 "10分钟、10条短信"→"10条"），无则返回 null */
    private static String shortSmsOf(Map<String, Object> inFee) {
        String satellite = MapOps.str(inFee.get("卫星权益"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)条").matcher(satellite);
        return m.find() ? m.group(1) + "条" : null;
    }

    /** 收费方式归一：后付费/预付费→按月（种子无按量/一次性口径） */
    private static String payModeOf(Map<String, Object> offer) {
        String pay = MapOps.str(offer.get("pay_mode"));
        return pay.isBlank() ? "按月" : "按月";
    }

    private List<String> castStrList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !MapOps.str(item).isBlank()) {
                    result.add(MapOps.str(item));
                }
            }
        }
        return result;
    }

    private double scoreOffer(String lower, Map<String, Object> offer) {
        double score = 0;
        String name = MapOps.str(offer.get("offer_name")).toLowerCase(Locale.ROOT);
        String series = MapOps.str(offer.get("series")).toLowerCase(Locale.ROOT);
        String rightType = MapOps.str(offer.get("right_type")).toLowerCase(Locale.ROOT);
        String fee = MapOps.str(castMap(offer.get("in_fee")).get("档位")).toLowerCase(Locale.ROOT);

        if (!name.isBlank() && lower.contains(name)) {
            score += 0.5;
        }
        if (series.equals("5g_a") && (lower.contains("5g-a") || lower.contains("5g a") || lower.contains("5ga"))) {
            score += 0.25;
        }
        if (series.equals("rights") && lower.contains("权益")) {
            score += 0.25;
        }
        if (!rightType.isBlank() && lower.contains(rightType)) {
            score += 0.2;
        }
        if (lower.contains("融合") && "fusion".equals(offer.get("offer_type"))) {
            score += 0.15;
        }
        if (lower.contains("单品") && "single".equals(offer.get("offer_type"))) {
            score += 0.15;
        }
        if (!fee.isBlank() && lower.contains(fee.replace("元/月", ""))) {
            score += 0.1;
        }
        return Math.min(score, 0.99);
    }

    private String descOf(Map<String, Object> offer) {
        Map<String, Object> inFee = castMap(offer.get("in_fee"));
        StringBuilder sb = new StringBuilder();
        sb.append("系列=").append(MapOps.str(offer.get("sub_type")));
        if (!inFee.isEmpty()) {
            sb.append("，").append(MapOps.str(inFee.get("档位")));
            Object flow = inFee.get("国内通用流量");
            if (flow != null) {
                sb.append("，含国内通用流量").append(flow);
            }
            Object net = inFee.get("网络权益");
            if (net != null) {
                sb.append("，").append(net);
            }
        }
        return sb.toString();
    }

    /* ---------------- 工具 ---------------- */

    private Map<String, Object> readJson(String classpath) {
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        } catch (Exception ex) {
            log.error("[OfferSeedService] 种子数据加载失败: {}", classpath, ex);
            return Map.of();
        }
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private List<Map<String, Object>> castMapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    result.add(castMap(item));
                }
            }
        }
        return result;
    }
}
