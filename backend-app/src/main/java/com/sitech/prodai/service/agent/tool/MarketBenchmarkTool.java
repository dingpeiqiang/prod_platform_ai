package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异网竞对资费对比工具（方案 §6-B3，对应缺口 C2）：差异化情报查询。
 * <p>
 * 按品类/资费档位检索竞对（联通/电信）在售同类套餐，与本网货架商品并列对比：
 * 套餐名、月费、核心权益、流量语音额度、数据新旧可感知。
 * <p>
 * 安全与口径约束（方案 §6-B3 要点）：
 * <ul>
 *   <li>只读：无写操作、无落库；</li>
 *   <li>回答须附数据来源与截止时间（sources 面必出，含 data_as_of）——
 *       竞对资费时效性强，不可让用户把演示数据当真实行情；</li>
 *   <li>数据敏感度 = INTERNAL：目录级竞对情报，行权限内可见（无需敏感门控，
 *       与 user_plan_query 的 SENSITIVE 口径区分）。</li>
 * </ul>
 * <p>
 * 数据源（v1 mock，与指标域同模式）：本地确定性生成竞对资费演示数据（同输入同输出可复现）；
 * 生产接竞对资费情报库时替换数据供给（工厂扩展位），工具编排与输出契约零改动。
 * <p>
 * 零编排改动接入：实现 {@code getScenes()} 含 query 即进入场景能力集（SPI 自声明）。
 */
@Component
public class MarketBenchmarkTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(MarketBenchmarkTool.class);

    /** 资费档位分箱边界（元/月，确定性分箱口径）。 */
    private static final int FEE_LOW_MAX = 59;
    private static final int FEE_MID_MAX = 129;

    /** 竞对运营商字典（mock 情报库演示口径）。 */
    private static final List<String> CARRIERS = List.of("联通", "电信");

    /** 品类 → 竞对套餐名模板（确定性生成，同品类同模板可复现）。 */
    private static final Map<String, String> CATEGORY_NAME_TEMPLATES = Map.of(
            "融合", "双千兆融合{tier}套餐",
            "流量", "大流量{tier}王卡",
            "宽带", "全屋WiFi{tier}宽带包",
            "语音", "畅听{tier}语音包"
    );

    /** mock 情报库数据截止时间偏移（天前，确定性）。 */
    private static final int DATA_AS_OF_DAYS_AGO = 7;

    private final com.sitech.prodai.service.ProductOntologyService productOntologyService;

    public MarketBenchmarkTool(com.sitech.prodai.service.ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "market_benchmark";
    }

    @Override
    public String getDescription() {
        return "检索异网竞对（联通/电信）同类套餐资费，与本网商品并列对比：套餐名、月费、核心权益、流量语音额度、数据截止时间。"
                + "做定价参考/竞争分析（如「对比一下联通同类融合套餐」「竞对的 99 档是什么价」）时使用；"
                + "查本网商品目录请用 sparql_query，查地市本地政策请用 city_policy_query。"
                + "注意：竞对资费有时效性，结果附数据截止时间";
    }

    @Override
    public String getLabel() {
        return "异网资费对比";
    }

    @Override
    public java.util.Set<String> getScenes() {
        // 方案 §4.6：查询助手为主口径；运营做定价参考同样受益，跨场景自声明
        return java.util.Set.of("query", "ops");
    }

    /** 拿到竞对资费后的典型业务链：回到本网商品查目录 / 看本网商品全景。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("sparql_query", "product_360");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("category")
                        .label("品类")
                        .description("对比品类（如融合/流量/宽带/语音；默认融合）")
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("fee_tier")
                        .label("资费档位")
                        .description("资费档位过滤（low=≤59元 / mid=60~129元 / high=≥130元，默认全部档位）")
                        .type("string")
                        .defaultValue("")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("对比摘要").type("string")
                        .description("竞对与本网资费对比摘要").build(),
                ToolOutputField.builder("category", ToolOutputField.Role.OTHER)
                        .label("品类").type("string")
                        .description("本次对比的品类口径").build(),
                ToolOutputField.builder("rival_plans", ToolOutputField.Role.ITEMS)
                        .label("竞对套餐").type("list")
                        .description("竞对在售同类套餐清单（运营商/套餐名/月费/核心权益）").build(),
                ToolOutputField.builder("our_plans", ToolOutputField.Role.ITEMS)
                        .label("本网对标").type("list")
                        .description("本网货架同品类商品（编码/名称/月费）").build(),
                ToolOutputField.builder("sources", ToolOutputField.Role.OTHER)
                        .label("数据来源").type("object")
                        .description("数据来源与截止时间（竞对资费时效性必透出）").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String category = params != null && !str(params.get("category")).isBlank()
                ? str(params.get("category")) : "融合";
        String feeTier = params != null ? str(params.get("fee_tier")) : "";
        log.info("[AgentTool] market_benchmark 执行: category={}, feeTier={}", category, feeTier);

        try {
            List<Map<String, Object>> rivalPlans = fetchRivalPlans(category, feeTier);
            if (rivalPlans.isEmpty()) {
                return ExecutionResult.fail(getName(),
                        "未找到品类「" + category + "」在档位「" + (feeTier.isBlank() ? "全部" : feeTier)
                                + "」的竞对套餐记录（请核对品类或档位口径）");
            }
            List<Map<String, Object>> ourPlans = fetchOurPlans(category);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("category", category);
            out.put("rival_plans", rivalPlans);
            out.put("our_plans", ourPlans);
            out.put("sources", buildSources());
            out.put("nl_answer", buildSummary(category, rivalPlans, ourPlans));
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] market_benchmark 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "异网资费对比失败: " + e.getMessage());
        }
    }

    // ===== 竞对资费数据供给（v1 mock：确定性生成，生产替换为竞对资费情报库） =====

    /**
     * 竞对套餐清单：mock 按品类 + 档位确定性生成（2 竞对 × 档位内 1~2 档，同输入同输出）；
     * 套餐名经品类模板生成，权益随档位确定性推导。
     */
    private List<Map<String, Object>> fetchRivalPlans(String category, String feeTier) {
        List<Map<String, Object>> plans = new ArrayList<>();
        String tierName = tierName(feeTier);
        String nameTemplate = CATEGORY_NAME_TEMPLATES.getOrDefault(category, "竞对{tier}同品类套餐");
        for (int c = 0; c < CARRIERS.size(); c++) {
            String carrier = CARRIERS.get(c);
            int planCount = 1 + (int) ((seedOf(category, carrier)) % 2);
            for (int i = 0; i < planCount; i++) {
                plans.add(rivalPlan(carrier, category, nameTemplate.replace("{tier}", tierName), c, i));
            }
        }
        return plans;
    }

    /** 单条竞对套餐记录：目录级情报字段（无用户数据，敏感度=INTERNAL）。 */
    private Map<String, Object> rivalPlan(String carrier, String category, String planName,
                                          int carrierIdx, int planIdx) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("carrier", carrier);
        p.put("plan_name", planName + (planIdx == 0 ? "" : "升级版"));
        p.put("monthly_fee", tierFee(carrierIdx, planIdx));
        p.put("data_gb", tierDataGb(carrierIdx, planIdx));
        p.put("voice_min", tierVoiceMin(carrierIdx, planIdx));
        p.put("core_benefit", coreBenefit(category, carrierIdx, planIdx));
        p.put("state", "在售");
        return p;
    }

    /** 档位 → 档位名（确定性分箱）。 */
    private String tierName(String feeTier) {
        return switch (feeTier) {
            case "low" -> "轻量";
            case "mid" -> "畅享";
            case "high" -> "旗舰";
            default -> "标准";
        };
    }

    /** 档位月费（确定性：档位基准 + 运营商/序号偏移，演示口径）。 */
    private int tierFee(int carrierIdx, int planIdx) {
        int[] base = {39, 99, 169};
        int fee = base[(carrierIdx + planIdx) % base.length] + planIdx * 10;
        return fee;
    }

    /** 档位流量额度（GB，确定性）。 */
    private int tierDataGb(int carrierIdx, int planIdx) {
        int[] gb = {20, 60, 100};
        return gb[(carrierIdx + planIdx) % gb.length] + planIdx * 10;
    }

    /** 档位语音额度（分钟，确定性）。 */
    private int tierVoiceMin(int carrierIdx, int planIdx) {
        int[] min = {100, 300, 1000};
        return min[(carrierIdx + planIdx) % min.length] + planIdx * 50;
    }

    /** 核心权益（按品类确定性推导，演示口径）。 */
    private String coreBenefit(String category, int carrierIdx, int planIdx) {
        String benefit = switch (category) {
            case "融合" -> "宽带" + (300 + carrierIdx * 100) + "M+副卡" + (2 + planIdx) + "张";
            case "流量" -> "定向免流" + List.of("视频", "音乐", "阅读").get((carrierIdx + planIdx) % 3) + "App";
            case "宽带" -> "全屋WiFi组网+" + (1 + planIdx) + "个AP";
            case "语音" -> "全国接听免费+" + (planIdx + 1) + "个亲情号";
            default -> "基础权益包";
        };
        return benefit;
    }

    /** 本网对标：货架同品类商品（名称含品类关键词最长匹配，确定性）。 */
    private List<Map<String, Object>> fetchOurPlans(String category) {
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> offering : shelfOfferings()) {
            String name = str(offering.get("offeringName"));
            String scenario = str(offering.get("bizScenario"));
            if (name.contains(category) || scenario.contains(category)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("offering_id", str(offering.get("offeringId")));
                row.put("offering_name", name);
                row.put("monthly_fee", offering.get("monthlyFee"));
                row.put("state", offering.get("state"));
                matched.add(row);
            }
        }
        return matched;
    }

    private List<Map<String, Object>> shelfOfferings() {
        Object shelf = productOntologyService.loadGraph().get("shelfOfferings");
        List<Map<String, Object>> out = new ArrayList<>();
        if (shelf instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    out.add(row);
                }
            }
        }
        return out;
    }

    private long seedOf(String category, String carrier) {
        return (long) (category + carrier).hashCode() & 0xffff;
    }

    private Map<String, Object> buildSources() {
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("rival_source", "mock");
        sources.put("rival_mode", "demo");
        sources.put("data_as_of", java.time.LocalDate.now().minusDays(DATA_AS_OF_DAYS_AGO).toString());
        sources.put("note", "v1 演示数据源（确定性生成）；生产接竞对资费情报库，资费时效以情报库更新为准");
        return sources;
    }

    /** 摘要：品类 + 竞对档位概览 + 本网对标条数 + 截止时间提示。 */
    private String buildSummary(String category, List<Map<String, Object>> rivalPlans,
                                List<Map<String, Object>> ourPlans) {
        StringBuilder sb = new StringBuilder();
        sb.append("「").append(category).append("」品类共检索到 ").append(rivalPlans.size())
                .append(" 个竞对在售套餐，月费区间 ");
        int min = rivalPlans.stream()
                .mapToInt(p -> p.get("monthly_fee") instanceof Number n ? n.intValue() : 0)
                .min().orElse(0);
        int max = rivalPlans.stream()
                .mapToInt(p -> p.get("monthly_fee") instanceof Number n ? n.intValue() : 0)
                .max().orElse(0);
        sb.append(min).append("~").append(max).append(" 元");
        sb.append("；本网货架对标商品 ").append(ourPlans.size()).append(" 个");
        Map<String, Object> first = rivalPlans.get(0);
        if (first.get("monthly_fee") != null) {
            sb.append("，如 ").append(first.get("carrier")).append("「")
                    .append(first.get("plan_name")).append("」").append(first.get("monthly_fee")).append(" 元/月");
        }
        sb.append("（数据截至 ")
                .append(java.time.LocalDate.now().minusDays(DATA_AS_OF_DAYS_AGO))
                .append("，生产接入情报库后为实时口径）");
        return sb.toString();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
