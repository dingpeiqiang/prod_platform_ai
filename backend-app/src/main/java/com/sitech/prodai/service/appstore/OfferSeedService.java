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
     * 相似度匹配：关键词命中（名称/系列/权益类型/资费档位/资源量）加权 + 档位相近度 + 资源相近度，
     * 返回按 score 降序列表。任意合理需求均至少返回基础分命中产品（未命中不中断流程）。
     *
     * @param businessDesc 业务需求描述（非空，由控制器校验）
     * @param topN 最多返回条数（如 3）
     * @return 相似销售品列表（按相似度降序，最多 topN 条），元素含 similarOfferId/similarOfferName/similarityScore/similarityDesc/offerInfo（完整产品配置信息）
     */
    public List<Map<String, Object>> matchSimilar(String businessDesc, int topN) {
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
        int limit = Math.max(1, Math.min(topN, result.size()));
        return new ArrayList<>(result.subList(0, limit));
    }

    /** 相似度最高（第 1 名）的相似销售品，附完整产品配置信息 offerInfo；无命中返回 null */
    public Map<String, Object> matchBestSimilar(String businessDesc) {
        List<Map<String, Object>> list = matchSimilar(businessDesc, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    /* ---------------- 种子 → 新 24 字段同构转换（V3.0） ---------------- */

    /**
     * 销售品种子全量规则 → 与需求要素解析同构的新 24 字段结构（fields 数组：field/category/value）。
     * 两侧共用同一套配置结构模板规范（字段名=本体注册表 24 字段），整合环节即同构键值合并。
     * 字段/分类对齐《平台配置清单》输出样例：基础信息（产品属性/生命周期/销售属性）、
     * 资源配置（套餐内基础资源/套餐内权益配置/套外资费标准）、业务规则（订购与生效/变更退订拆机/计费支付风控）。
     */
    public Map<String, Object> toFields18(Map<String, Object> offer) {
        Map<String, Object> inFee = castMap(offer.get("in_fee"));
        Map<String, Object> outFee = castMap(offer.get("out_fee"));
        Map<String, Object> subCard = castMap(offer.get("sub_card"));
        List<String> channels = castStrList(offer.get("sale_channels"));
        int fee = parseFeeYuan(MapOps.str(offer.get("monthly_fee")));

        List<Map<String, Object>> fields = new ArrayList<>();
        // 基础信息 / 产品属性
        fields.add(f("套餐名称", "产品属性", MapOps.str(offer.get("offer_name"))));
        fields.add(f("套餐编码", "产品属性", "系统待生成"));
        fields.add(f("套餐档位", "产品属性", fee > 0 ? fee + "元" : "待补充"));
        fields.add(f("套餐属性", "产品属性", "主资费"));
        fields.add(f("计费周期", "产品属性", textOr(offer.get("billing_cycle"), "自然月")));
        // 基础信息 / 生命周期
        fields.add(f("套餐有效期", "生命周期", textOr(offer.get("validity"), "长期有效")));
        fields.add(f("到期处理方式", "生命周期", MapOps.str(offer.get("validity")).contains("续展") ? "自动续展" : "自动续订"));
        // 基础信息 / 销售属性
        fields.add(f("适用用户", "销售属性", orderScopeOf(offer)));
        fields.add(f("销售渠道", "销售属性", channels.isEmpty() ? "实体渠道、电子渠道、直销渠道" : String.join("、", channels)));
        // 资源配置 / 套餐内基础资源
        fields.add(f("国内通用流量", "套餐内基础资源", textOr(inFee.get("国内通用流量"), "无")));
        fields.add(f("本地语音", "套餐内基础资源", textOr(inFee.get("国内语音拨打"), "无")));
        fields.add(f("短信", "套餐内基础资源", textOr(inFee.get("卫星权益") == null ? null : shortSmsOf(inFee), "无")));
        // 资源配置 / 套餐内权益配置
        fields.add(f("是否允许办理副卡", "套餐内权益配置", subCard.isEmpty() || !Boolean.TRUE.equals(subCard.get("允许办理"))
                ? "不允许" : "允许"));
        // 资源配置 / 套外资费标准
        fields.add(f("套外流量-计费标准", "套外资费标准", textOr(outFee.get("套外流量"), "无")));
        fields.add(f("套外语音-国内通话", "套外资费标准", textOr(outFee.get("套外语音"), "无")));
        fields.add(f("套外短彩信-短/彩信", "套外资费标准", textOr(outFee.get("套外短彩信"), "无")));
        // 业务规则 / 订购与生效
        fields.add(f("新入网生效方式", "订购与生效", "立即生效"));
        fields.add(f("老用户生效方式", "订购与生效", "次月1日生效"));
        fields.add(f("过渡期资费规则", "订购与生效", textOr(offer.get("transition_fee"), "按日（当月实际天数）计扣")));
        // 业务规则 / 变更/退订/拆机
        fields.add(f("套餐变更范围", "变更/退订/拆机", textOr(offer.get("change_rule"), "可变更至中国电信其他在售套餐")));
        fields.add(f("变更生效方式", "变更/退订/拆机", "次月1号生效"));
        fields.add(f("退订规则", "变更/退订/拆机", textOr(offer.get("cancel_rule"), "允许退订，次月生效")));
        // 业务规则 / 计费/支付/风控
        fields.add(f("付费方式", "计费/支付/风控", "后付费"));
        fields.add(f("支付方式", "计费/支付/风控", "账单支付"));
        fields.add(f("流量结转规则", "计费/支付/风控", Boolean.TRUE.equals(offer.get("flow_carry_over")) ? "结转" : "不结转"));
        fields.add(f("断网授权", "计费/支付/风控", textOr(offer.get("net_cutoff_limit"), "套外流量使用至600元时暂停上网")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("similarOfferId", MapOps.str(offer.get("offer_id")));
        body.put("similarOfferName", MapOps.str(offer.get("offer_name")));
        body.put("series", MapOps.str(offer.get("series")));
        body.put("sub_type", MapOps.str(offer.get("sub_type")));
        body.put("fields", fields);
        return body;
    }

    /** 适用用户归一：需求文案口径（新老用户均可订购/仅新用户等），种子默认新老用户均可订购 */
    private static String orderScopeOf(Map<String, Object> offer) {
        String rule = MapOps.str(offer.get("order_rule"));
        if (rule.contains("仅新") || rule.contains("新用户")) {
            return "新老用户均可订购".contains(rule) || rule.isBlank() ? "新老用户均可订购" : rule;
        }
        return "新老用户均可订购";
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
        double score = 0.1; // 基础分：保证任意合理需求至少命中产品库（不中断流程）
        String name = MapOps.str(offer.get("offer_name")).toLowerCase(Locale.ROOT);
        String series = MapOps.str(offer.get("series")).toLowerCase(Locale.ROOT);
        String rightType = MapOps.str(offer.get("right_type")).toLowerCase(Locale.ROOT);
        String offerType = MapOps.str(offer.get("offer_type")).toLowerCase(Locale.ROOT);
        Map<String, Object> inFee = castMap(offer.get("in_fee"));
        String fee = MapOps.str(inFee.get("档位")).toLowerCase(Locale.ROOT);

        // ① 产品名全串命中
        if (!name.isBlank() && lower.contains(name)) {
            score += 0.5;
        }
        // ② 系列命中：放宽——"5G"即命中 5g_a 系列（原仅认 5G-A 全写，导致"上新5G套餐"类需求 0 分）
        boolean mention5g = lower.contains("5g");
        if (series.equals("5g_a") && (mention5g || lower.contains("套餐"))) {
            score += 0.25;
        }
        if (series.equals("rights") && (lower.contains("权益") || lower.contains("随心选"))) {
            score += 0.25;
        }
        // ③ 权益版本命中
        if (!rightType.isBlank() && lower.contains(rightType)) {
            score += 0.2;
        }
        // ④ 结构类型命中：融合/单品（需求未明示时不加不扣，明示时精准加权）
        boolean mentionFusion = lower.contains("融合") || lower.contains("宽带") || lower.contains("高清");
        if (mentionFusion && "fusion".equals(offerType)) {
            score += 0.15;
        }
        boolean mentionSingle = lower.contains("单品");
        if (mentionSingle && "single".equals(offerType)) {
            score += 0.15;
        }
        // ⑤ 档位精确命中 + ⑥ 档位相近度加权（如 599 需求最接近 599 档/更高档）
        int wantFee = extractFee(lower);
        int offerFee = parseFeeYuan(fee);
        if (wantFee > 0 && offerFee > 0) {
            if (lower.contains(String.valueOf(offerFee))) {
                score += 0.2; // 档位精确命中
            } else {
                int diff = Math.abs(wantFee - offerFee);
                if (diff <= 20) {
                    score += 0.15;
                } else if (diff <= 100) {
                    score += 0.1;
                } else if (diff <= 300) {
                    score += 0.05;
                }
            }
        }
        // ⑦ 资源需求命中：需求中的流量/语音数值与种子资源量接近时加权
        score += resourceAffinity(lower, inFee);
        return Math.min(score, 0.99);
    }

    /** 提取需求文本中的月费档位（"599元"/"月费 599"→599），无则 0 */
    private static int extractFee(String lower) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:月费|月租|档位)?\\s*(\\d{2,4})\\s*元").matcher(lower);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    /** 档位文本转整数（"199元/月"→199），失败 0 */
    private static int parseFeeYuan(String fee) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(fee == null ? "" : fee);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** 资源相近度：需求含流量/语音数值时，与种子同量纲比较，接近加分 */
    private static double resourceAffinity(String lower, Map<String, Object> inFee) {
        double bonus = 0;
        int wantFlow = extractNum(lower, "(\\d+)\\s*[gG][bB]");
        int offerFlow = extractNum(MapOps.str(inFee.get("国内通用流量")), "(\\d+)");
        if (wantFlow > 0 && offerFlow > 0) {
            double ratio = (double) Math.min(wantFlow, offerFlow) / Math.max(wantFlow, offerFlow);
            if (ratio >= 0.9) {
                bonus += 0.1;
            } else if (ratio >= 0.6) {
                bonus += 0.05;
            }
        }
        int wantVoice = extractNum(lower, "(\\d+)\\s*分?钟");
        int offerVoice = extractNum(MapOps.str(inFee.get("国内语音拨打")), "(\\d+)");
        if (wantVoice > 0 && offerVoice > 0) {
            double ratio = (double) Math.min(wantVoice, offerVoice) / Math.max(wantVoice, offerVoice);
            if (ratio >= 0.9) {
                bonus += 0.1;
            } else if (ratio >= 0.6) {
                bonus += 0.05;
            }
        }
        return bonus;
    }

    /** 按正则提取首个整数，失败 0 */
    private static int extractNum(String text, String regex) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
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
