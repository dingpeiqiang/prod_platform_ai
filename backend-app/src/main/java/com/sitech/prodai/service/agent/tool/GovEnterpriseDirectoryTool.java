package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.UserScope;
import com.sitech.prodai.service.agent.model.UserScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 政企 B2B 商品目录查询工具（方案 §6-C2，对应缺口 C6）：客户经理/政企条线高频场景。
 * <p>
 * 检索政企在架商品目录（DICT/集团套餐/专线/物联卡等 B2B 商品面），按行业/规模/资费档位过滤，
 * 条目化呈现：商品名称、品类、月费、适用行业与客户规模、销售状态。
 * <p>
 * 安全与口径约束（方案 §6-C2 要点）：
 * <ul>
 *   <li>数据敏感度 = INTERNAL：B2B 目录属目录级情报（与地市政策/异网资费同口径），
 *       行权限内可见、无需敏感门控硬拒；</li>
 *   <li>行权限（客户经理→客户归属）：scope 携带名下客户列表时，目录条目按归属客户
 *       强制过滤——过滤在本工具参数化层执行，不依赖 LLM 遵从提示词；
 *       scope 客户归属不限（admin/普通角色）时全量可见；</li>
 *   <li>只读：无写操作、无落库、无开单。</li>
 * </ul>
 * <p>
 * 数据源（v1 mock，与 B2/B3 同模式）：本地确定性生成政企目录演示数据（同输入同输出可复现），
 * 商品对齐事实图货架（保证目录商品真实存在）；生产接政企商品库时替换数据供给（工厂扩展位），
 * 工具编排、归属过滤与输出契约零改动。
 * <p>
 * 零编排改动接入：实现 {@code getScenes()} 含 query 即进入场景能力集（SPI 自声明）。
 */
@Component
public class GovEnterpriseDirectoryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(GovEnterpriseDirectoryTool.class);

    /** 客户规模档位字典（确定性生成口径）。 */
    private static final List<String> SCALES = List.of("大型", "中型", "小微");

    /** 行业字典（确定性生成口径，与政企条线演示维度对齐）。 */
    private static final List<String> INDUSTRIES = List.of("政务", "教育", "医疗", "制造", "物流");

    /** 政企商品目录模板（品类/资费形态，月费对齐货架后在生成时校正）。 */
    private static final List<String> B2B_CATALOG_TYPES = List.of("集团套餐", "企业专线", "物联卡", "云和DICT");

    /** mock 目录数据截止时间偏移（天前，确定性）。 */
    private static final int DATA_AS_OF_DAYS_AGO = 3;

    /** 归属客户演示名单（与 UserScopeResolver ACCOUNT_MANAGER_CUSTOMERS 同源演示口径）。 */
    private static final List<String> DEMO_CUSTOMERS = List.of(
            "GE-CUST-001", "GE-CUST-002", "GE-CUST-003", "GE-CUST-004", "GE-CUST-005");

    private final com.sitech.prodai.service.ProductOntologyService productOntologyService;

    public GovEnterpriseDirectoryTool(com.sitech.prodai.service.ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "gov_enterprise_directory";
    }

    @Override
    public String getDescription() {
        return "查询政企 B2B 商品目录：集团套餐、企业专线、物联卡、云和DICT 类商品，按行业/客户规模/资费档位过滤。"
                + "客户经理为政企客户选品（如「教育行业有什么集团套餐」「XX规模客户适合的专线产品」）时使用；"
                + "查个人/家庭商品目录请用 sparql_query，查竞对资费请用 market_benchmark。"
                + "注意：客户经理仅可见名下归属客户的目录条目";
    }

    @Override
    public String getLabel() {
        return "政企商品目录";
    }

    @Override
    public java.util.Set<String> getScenes() {
        // 方案 §6-C2：查询助手（客户经理/政企条线）为主口径；运营盘点同样受益，跨场景自声明
        return java.util.Set.of("query", "ops");
    }

    /** 拿到政企目录后的典型业务链：看商品全景评估适配 / 查地市落地政策。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("product_360", "city_policy_query", "sparql_query");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("industry")
                        .label("行业")
                        .description("行业过滤（政务/教育/医疗/制造/物流，默认全部行业）")
                        .type("string")
                        .defaultValue("")
                        .build(),
                ToolParam.builder("scale")
                        .label("客户规模")
                        .description("客户规模过滤（大型/中型/小微，默认全部规模）")
                        .type("string")
                        .defaultValue("")
                        .build(),
                ToolParam.builder("max_monthly_fee")
                        .label("月费上限")
                        .description("月费上限（元，过滤资费档位；默认不过滤）")
                        .type("number")
                        .defaultValue("")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("目录摘要").type("string")
                        .description("政企商品目录概览摘要").build(),
                ToolOutputField.builder("visible_customers", ToolOutputField.Role.OTHER)
                        .label("可见客户范围").type("string")
                        .description("本次行权限可见的客户归属范围（ALL=不限）").build(),
                ToolOutputField.builder("catalog_items", ToolOutputField.Role.ITEMS)
                        .label("目录条目").type("list")
                        .description("政企商品条目（名称/品类/月费/适用行业与规模/状态/归属客户）").build(),
                ToolOutputField.builder("sources", ToolOutputField.Role.OTHER)
                        .label("数据来源").type("object")
                        .description("数据来源与截止时间（目录时效性必透出）").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String industry = params != null ? str(params.get("industry")) : "";
        String scale = params != null ? str(params.get("scale")) : "";
        Double maxFee = params != null ? castDouble(params.get("max_monthly_fee")) : null;
        log.info("[AgentTool] gov_enterprise_directory 执行: industry={}, scale={}, maxFee={}",
                industry, scale, maxFee);

        // 行权限（方案 §4.4 + §6-C2）：scope 由服务端登录态解析（UserScopeContext），
        // 客户归属过滤在本层强制执行（不依赖 LLM 遵从提示词），LLM/入参不可触达
        UserScope scope = UserScopeContext.current();

        try {
            List<Map<String, Object>> items = fetchCatalog(industry, scale, maxFee);
            if (items.isEmpty()) {
                return ExecutionResult.fail(getName(),
                        "未找到匹配的政企商品（行业=" + (industry.isBlank() ? "全部" : industry)
                                + "，规模=" + (scale.isBlank() ? "全部" : scale)
                                + "，月费上限=" + (maxFee == null ? "不限" : maxFee) + "，请核对过滤口径）");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("visible_customers", scope.isAllCustomers() ? "ALL" : scope.getVisibleCustomers());
            out.put("catalog_items", items);
            out.put("sources", buildSources());
            out.put("nl_answer", buildSummary(items));
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] gov_enterprise_directory 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "政企商品目录查询失败: " + e.getMessage());
        }
    }

    // ===== 目录数据供给（v1 mock：确定性生成，生产替换为政企商品库） =====

    /**
     * 目录条目清单：mock 按目录模板 × 行业确定性生成（月费/规模档经种子偏移，同输入恒定可复现）；
     * 行业/规模/月费过滤在生成后执行（未命中返回空 → 失败不冒充）。
     * <p>行权限：scope 客户归属受限（客户经理）时，条目按名下客户过滤——
     * 过滤在本方法参数化层强制注入。
     */
    private List<Map<String, Object>> fetchCatalog(String industry, String scale, Double maxFee) {
        UserScope scope = UserScopeContext.current();
        List<Map<String, Object>> items = new ArrayList<>();
        for (String catalogType : B2B_CATALOG_TYPES) {
            for (int i = 0; i < INDUSTRIES.size(); i++) {
                long seed = (catalogType + INDUSTRIES.get(i) + "b2b").hashCode() & 0xffff;
                String itemIndustry = INDUSTRIES.get(i);
                String itemScale = SCALES.get((int) (seed % SCALES.size()));
                double fee = 50 + seed % 950;
                if (!industry.isBlank() && !industry.equals(itemIndustry)) {
                    continue;
                }
                if (!scale.isBlank() && !scale.equals(itemScale)) {
                    continue;
                }
                if (maxFee != null && fee > maxFee) {
                    continue;
                }
                Map<String, Object> item = item(catalogType, itemIndustry, itemScale, fee, seed);
                // 行权限过滤（客户经理仅见名下客户归属条目）——权限子句后置且不可绕过
                if (!scope.isAllCustomers() && !scope.getVisibleCustomers().contains(item.get("customer_id"))) {
                    continue;
                }
                items.add(item);
            }
        }
        return items;
    }

    /** 单条目录记录：目录级字段（商品/品类/月费/行业规模/状态/归属客户），对齐货架商品。 */
    private Map<String, Object> item(String catalogType, String industry, String scale, double fee, long seed) {
        Map<String, Object> it = new LinkedHashMap<>();
        String customerId = DEMO_CUSTOMERS.get((int) (seed % DEMO_CUSTOMERS.size()));
        it.put("item_id", "B2B-" + catalogType.hashCode() % 100 + "-" + (seed % 900 + 100));
        it.put("item_name", catalogType + "（" + industry + "·" + scale + "）");
        it.put("catalog_type", catalogType);
        it.put("industry", industry);
        it.put("customer_scale", scale);
        it.put("monthly_fee", fee);
        it.put("state", "上架");
        it.put("customer_id", customerId);
        // 商品对齐真实货架（保证目录商品在货架可溯源）
        shelfOfferingFor(seed).ifPresent(o -> {
            it.put("offering_id", str(o.get("offeringId")));
            it.put("offering_name", str(o.get("offeringName")));
        });
        return it;
    }

    /** 确定性选取一个货架商品作为目录条目的商品锚点（同种子恒定）。 */
    private java.util.Optional<Map<String, Object>> shelfOfferingFor(long seed) {
        List<Map<String, Object>> shelf = shelfOfferings();
        if (shelf.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(shelf.get((int) (seed % shelf.size())));
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

    private Map<String, Object> buildSources() {
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("catalog_source", "mock");
        sources.put("catalog_mode", "demo");
        sources.put("data_as_of", java.time.LocalDate.now().minusDays(DATA_AS_OF_DAYS_AGO).toString());
        sources.put("note", "v1 演示数据源（确定性生成）；生产接政企商品库，目录口径以商品库为准");
        return sources;
    }

    /** 摘要：条目数 + 各品类一句话概览 + 截止时间提示。 */
    private String buildSummary(List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("政企目录共 ").append(items.size()).append(" 条在架商品：");
        List<String> typeNames = items.stream().map(i -> str(i.get("catalog_type"))).distinct().toList();
        sb.append("覆盖 ").append(typeNames.size()).append(" 个品类（").append(String.join("/", typeNames)).append("）");
        double minFee = items.stream().mapToDouble(i -> {
            Double f = castDouble(i.get("monthly_fee"));
            return f == null ? 0 : f;
        }).min().orElse(0);
        double maxFee = items.stream().mapToDouble(i -> {
            Double f = castDouble(i.get("monthly_fee"));
            return f == null ? 0 : f;
        }).max().orElse(0);
        sb.append("，月费区间 ").append((int) minFee).append("~").append((int) maxFee).append(" 元");
        sb.append("。数据截至 ")
                .append(java.time.LocalDate.now().minusDays(DATA_AS_OF_DAYS_AGO))
                .append("，生产接入政企商品库后为商品库发布口径");
        return sb.toString();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private Double castDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
