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
 * 地市差异化政策查询工具（方案 §6-B3，对应缺口 C2）：省内本地化政策情报。
 * <p>
 * 按地市检索本地差异化政策（地市补贴/渠道佣金/促销窗口/准入要求），条目化呈现：
 * 政策类型、适用商品范围、有效期、关键条款摘要。
 * <p>
 * 安全与口径约束（方案 §6-B3 要点）：
 * <ul>
 *   <li>只读：无写操作、无落库；</li>
 *   <li>回答须附数据来源与截止时间（sources 面必出，含 data_as_of）——
 *       政策有时效与执行口径差异，不可让用户把演示数据当真实政策；</li>
 *   <li>数据敏感度 = INTERNAL：政策属目录级情报，行权限内可见（无需敏感门控）；
 *       A2 行权限的地市维度在数据接入后按 scope 过滤（v1 mock 全地市可查，演示口径）。</li>
 * </ul>
 * <p>
 * 数据源（v1 mock，与指标域同模式）：本地确定性生成政策演示数据（同输入同输出可复现）；
 * 生产接政策库时替换数据供给（工厂扩展位），工具编排与输出契约零改动。
 * <p>
 * 零编排改动接入：实现 {@code getScenes()} 含 query 即进入场景能力集（SPI 自声明）。
 */
@Component
public class CityPolicyQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(CityPolicyQueryTool.class);

    /** 地市字典（mock 政策库演示口径，与省内署地市维度对齐的演示值）。 */
    private static final List<String> CITIES = List.of("昆明", "曲靖", "玉溪", "大理");

    /** 政策类型字典（确定性生成口径）。 */
    private static final List<String> POLICY_TYPES = List.of("地市补贴", "渠道佣金", "促销窗口", "准入要求");

    /** mock 政策库数据截止时间偏移（天前，确定性）。 */
    private static final int DATA_AS_OF_DAYS_AGO = 3;

    private final com.sitech.prodai.service.ProductOntologyService productOntologyService;

    public CityPolicyQueryTool(com.sitech.prodai.service.ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "city_policy_query";
    }

    @Override
    public String getDescription() {
        return "按地市查询本地差异化政策：地市补贴、渠道佣金、促销窗口、准入要求，含适用商品范围与有效期。"
                + "商品要落地某地市（如「昆明这个月有什么促销政策」「XX套餐在曲靖的准入要求」）时使用；"
                + "查竞对资费对比请用 market_benchmark，查商品目录请用 sparql_query。"
                + "注意：政策有时效性，结果附数据截止时间与适用范围";
    }

    @Override
    public String getLabel() {
        return "地市政策查询";
    }

    @Override
    public java.util.Set<String> getScenes() {
        // 方案 §4.6：查询助手为主口径；运营铺市排期同样受益，跨场景自声明
        return java.util.Set.of("query", "ops");
    }

    /** 拿到地市政策后的典型业务链：回到商品目录核对商品 / 看商品全景评估适配。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("sparql_query", "product_360");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("city")
                        .label("地市")
                        .description("地市名称（如昆明/曲靖；必填）")
                        .required()
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("policy_type")
                        .label("政策类型")
                        .description("政策类型过滤（地市补贴/渠道佣金/促销窗口/准入要求，默认全部类型）")
                        .type("string")
                        .defaultValue("")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("政策摘要").type("string")
                        .description("地市政策概览摘要").build(),
                ToolOutputField.builder("city", ToolOutputField.Role.OTHER)
                        .label("地市").type("string")
                        .description("本次查询的地市口径").build(),
                ToolOutputField.builder("policies", ToolOutputField.Role.ITEMS)
                        .label("政策清单").type("list")
                        .description("政策条目清单（类型/适用商品/有效期/关键条款）").build(),
                ToolOutputField.builder("sources", ToolOutputField.Role.OTHER)
                        .label("数据来源").type("object")
                        .description("数据来源与截止时间（政策时效性必透出）").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String city = params != null ? str(params.get("city")) : "";
        String policyType = params != null ? str(params.get("policy_type")) : "";
        log.info("[AgentTool] city_policy_query 执行: city={}, policyType={}", city, policyType);

        if (city.isBlank()) {
            return ExecutionResult.fail(getName(), "缺少地市：请提供要查询的地市名称（如昆明/曲靖）");
        }
        if (CITIES.stream().noneMatch(c -> c.equals(city))) {
            return ExecutionResult.fail(getName(),
                    "暂不支持地市「" + city + "」的政策查询（当前覆盖：" + String.join("/", CITIES) + "）");
        }

        try {
            List<Map<String, Object>> policies = fetchPolicies(city, policyType);
            if (policies.isEmpty()) {
                return ExecutionResult.fail(getName(),
                        "地市「" + city + "」未找到类型为「" + (policyType.isBlank() ? "全部" : policyType)
                                + "」的政策记录（请核对政策类型口径）");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("city", city);
            out.put("policies", policies);
            out.put("sources", buildSources(city));
            out.put("nl_answer", buildSummary(city, policies));
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] city_policy_query 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "地市政策查询失败: " + e.getMessage());
        }
    }

    // ===== 政策数据供给（v1 mock：确定性生成，生产替换为政策库） =====

    /**
     * 政策条目清单：mock 按地市确定性生成 3~4 条（类型覆盖全字典、有效期按种子偏移，
     * 同地市同输入恒定可复现）；policyType 过滤在生成后执行（未命中类型返回空 → 失败不冒充）。
     */
    private List<Map<String, Object>> fetchPolicies(String city, String policyType) {
        long seed = (city + "policy").hashCode() & 0xffff;
        List<Map<String, Object>> policies = new ArrayList<>();
        for (int i = 0; i < POLICY_TYPES.size(); i++) {
            String type = POLICY_TYPES.get(i);
            if (!policyType.isBlank() && !policyType.equals(type)) {
                continue;
            }
            policies.add(policy(city, type, seed, i));
        }
        return policies;
    }

    /** 单条政策记录：目录级政策字段（类型/适用商品/有效期/条款摘要）。 */
    private Map<String, Object> policy(String city, String type, long seed, int idx) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("policy_id", "POL-" + city + "-" + (seed % 90 + 10) + idx);
        p.put("policy_type", type);
        p.put("title", city + type + "（mock 演示条目）");
        p.put("applies_to", appliesTo(city, type));
        p.put("valid_from", java.time.LocalDate.now().minusDays(10 + (seed + idx) % 20).toString());
        p.put("valid_to", java.time.LocalDate.now().plusDays(20 + (seed + idx * 7) % 60).toString());
        p.put("key_terms", keyTerms(type));
        return p;
    }

    /** 适用商品范围（按类型确定性推导，演示口径）。 */
    private String appliesTo(String city, String type) {
        return switch (type) {
            case "地市补贴" -> city + "本地在售融合类商品";
            case "渠道佣金" -> city + "厅店渠道新入网商品";
            case "促销窗口" -> city + "全品类（节点性）";
            default -> city + "新上架商品（上架前核对）";
        };
    }

    /** 关键条款摘要（按类型确定性推导，演示口径）。 */
    private String keyTerms(String type) {
        return switch (type) {
            case "地市补贴" -> "新入网补贴 30 元/户，次月出账后兑现";
            case "渠道佣金" -> "新入网佣金率 8%，存量迁转 5%";
            case "促销窗口" -> "月末最后 5 天冲刺窗口，赠费直充";
            default -> "上架前需地市市场部核准资费下限";
        };
    }

    private Map<String, Object> buildSources(String city) {
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("policy_source", "mock");
        sources.put("policy_mode", "demo");
        sources.put("city", city);
        sources.put("data_as_of", java.time.LocalDate.now().minusDays(DATA_AS_OF_DAYS_AGO).toString());
        sources.put("note", "v1 演示数据源（确定性生成）；生产接政策库，政策时效与执行口径以政策库为准");
        return sources;
    }

    /** 摘要：地市 + 政策条数 + 各类型一句话概览 + 截止时间提示。 */
    private String buildSummary(String city, List<Map<String, Object>> policies) {
        StringBuilder sb = new StringBuilder();
        sb.append("地市「").append(city).append("」共 ").append(policies.size()).append(" 条在期政策：");
        for (int i = 0; i < policies.size(); i++) {
            Map<String, Object> p = policies.get(i);
            if (i > 0) {
                sb.append("；");
            }
            sb.append(p.get("policy_type")).append("（有效期至 ").append(p.get("valid_to")).append("）");
        }
        sb.append("。数据截至 ")
                .append(java.time.LocalDate.now().minusDays(DATA_AS_OF_DAYS_AGO))
                .append("，生产接入政策库后为政策发布口径");
        return sb.toString();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
