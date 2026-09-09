package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.UserScope;
import com.sitech.prodai.service.agent.model.UserScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量用户套包查询工具（方案 §6-B2，对应缺口 C2）：客服/渠道高频场景。
 * <p>
 * 按脱敏用户标识查询其当前订购套包（主套包/可选包/增值包）、合约期与可选变更记录。
 * <p>
 * 安全约束（方案 §4.6 硬性约束）：
 * <ul>
 *   <li>数据敏感度 = SENSITIVITY_SENSITIVE：执行前经 UserScopeContext 强制门控，
 *       非全量权限且 maxSensitivity &lt; 2 的账号直接拒绝（不返回降级数据冒充）；
 *       权限上下文由服务端登录态解析（A2），工具入参不可触达；</li>
 *   <li>输出脱敏：用户标识打码（保留前 3 后 2 位），输出不含完整号码/证件字段；</li>
 *   <li>只读：无写操作、无落库、无开单。</li>
 * </ul>
 * <p>
 * 数据源（v1 mock，与指标域同模式）：本地确定性生成订购面演示数据（同输入同输出可复现）；
 * 生产接 CRM 用户套包只读视图时替换数据供给（工厂扩展位），工具编排与脱敏门控零改动。
 * <p>
 * 零编排改动接入：实现 {@code getScenes()} 含 query 即进入场景能力集（SPI 自声明）。
 */
@Component
public class UserPlanQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(UserPlanQueryTool.class);

    /** 套包类型字典（mock 订购面演示口径）。 */
    private static final String PLAN_TYPE_MAIN = "main";
    private static final String PLAN_TYPE_ADDON = "addon";
    private static final String PLAN_TYPE_VALUE = "value";

    private final com.sitech.prodai.service.ProductOntologyService productOntologyService;

    public UserPlanQueryTool(com.sitech.prodai.service.ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "user_plan_query";
    }

    @Override
    public String getDescription() {
        return "按脱敏用户标识查询存量用户当前订购套包：在订主套包/可选包/增值包、合约期与到期日、近6个月变更记录。"
                + "客服/渠道查某个用户订了什么套餐（如「查一下用户 138****1234 的套包」）时使用；"
                + "查商品目录/资费请用 sparql_query，查单商品全景请用 product_360。"
                + "注意：用户套包属敏感数据，仅限有敏感数据权限的账号查询";
    }

    @Override
    public String getLabel() {
        return "用户套包查询";
    }

    @Override
    public java.util.Set<String> getScenes() {
        // 方案 §4.6：仅查询助手（客服/渠道口径）
        return java.util.Set.of("query");
    }

    /** 查到套包后的典型业务链：查套包内商品详情 / 检索相似配置。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("sparql_query", "product_360", "rd_config_search");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("user_id")
                        .label("用户标识")
                        .description("脱敏用户标识（手机号或客户编号，后端解析为内部 ID 并脱敏呈现）")
                        .required()
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("include_history")
                        .label("含变更记录")
                        .description("是否包含近6个月套包变更记录（默认 false）")
                        .type("boolean")
                        .defaultValue("false")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("套包概览").type("string")
                        .description("在订套包概览（脱敏口径）").build(),
                ToolOutputField.builder("user_masked", ToolOutputField.Role.OTHER)
                        .label("脱敏用户").type("string")
                        .description("打码后的用户标识（保留前3后2）").build(),
                ToolOutputField.builder("current_plans", ToolOutputField.Role.ITEMS)
                        .label("在订套包").type("list")
                        .description("在订套包清单（主套包/可选包/增值包，含月费与生效日期）").build(),
                ToolOutputField.builder("contract", ToolOutputField.Role.OTHER)
                        .label("合约期").type("object")
                        .description("合约期与到期日").build(),
                ToolOutputField.builder("history", ToolOutputField.Role.ITEMS)
                        .label("变更记录").type("list")
                        .description("近6个月变更记录（include_history=true 时返回）").build(),
                ToolOutputField.builder("sources", ToolOutputField.Role.OTHER)
                        .label("数据来源").type("object")
                        .description("数据来源与口径（数据新旧可感知）").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String userId = params != null ? str(params.get("user_id")) : "";
        boolean includeHistory = params != null && Boolean.parseBoolean(str(params.get("include_history")));
        log.info("[AgentTool] user_plan_query 执行: userIdLen={}, includeHistory={}", userId.length(), includeHistory);

        if (userId.isBlank()) {
            return ExecutionResult.fail(getName(), "缺少用户标识：请提供要查询的用户手机号或客户编号");
        }

        // 敏感度门控（方案 §4.6 硬性约束）：用户套包 = SENSITIVITY_SENSITIVE，
        // 权限上下文来自服务端登录态解析（UserScopeContext），入参不可触达
        UserScope scope = UserScopeContext.current();
        if (!scope.canAccess(UserScope.SENSITIVITY_SENSITIVE)) {
            log.info("[AgentTool] user_plan_query 拒绝（敏感度不足）: scope={}", scope.auditSummary());
            return ExecutionResult.fail(getName(),
                    "当前账号无用户套包（敏感数据）查询权限，请联系管理员开通或改用商品目录查询");
        }

        try {
            List<Map<String, Object>> plans = fetchPlans(userId);
            if (plans.isEmpty()) {
                return ExecutionResult.fail(getName(),
                        "未找到用户 " + maskUserId(userId) + " 的在订套包记录（请核对用户标识）");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("user_masked", maskUserId(userId));
            out.put("current_plans", plans);
            out.put("contract", buildContract(userId, plans));
            if (includeHistory) {
                out.put("history", buildHistory(userId));
            }
            out.put("sources", buildSources());
            out.put("nl_answer", buildSummary(userId, plans, out));
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] user_plan_query 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "用户套包查询失败: " + e.getMessage());
        }
    }

    // ===== 订购面数据供给（v1 mock：确定性生成，生产替换为 CRM 只读视图） =====

    /**
     * 在订套包清单：mock 按用户标识确定性生成 1 主 + 1~2 可选/增值；
     * 商品字段对齐事实图货架（月费/状态经 shelfOfferings 校正，保证套包商品真实存在）。
     */
    private List<Map<String, Object>> fetchPlans(String userId) {
        long seed = userId.hashCode() & 0xffff;
        List<Map<String, Object>> plans = new ArrayList<>();
        // 主套包：从货架取一个（确定性选取，同用户恒定）
        List<Map<String, Object>> shelf = shelfOfferings();
        if (shelf.isEmpty()) {
            return plans;
        }
        Map<String, Object> main = shelf.get((int) (seed % shelf.size()));
        plans.add(plan(userId, main, PLAN_TYPE_MAIN, effectiveDate(userId, 0)));
        // 可选包：确定性追加 0~2 个（seed 决定，同用户恒定）
        int addonCount = (int) (seed % 3);
        for (int i = 1; i <= addonCount && i < shelf.size(); i++) {
            Map<String, Object> addon = shelf.get((int) ((seed + i * 7) % shelf.size()));
            if (str(addon.get("offeringId")).equals(str(main.get("offeringId")))) {
                continue;
            }
            plans.add(plan(userId, addon, i == 1 ? PLAN_TYPE_ADDON : PLAN_TYPE_VALUE,
                    effectiveDate(userId, i)));
        }
        return plans;
    }

    /** 单条套包记录：仅目录级字段（商品/类型/月费/生效日期），无用户敏感明细。 */
    private Map<String, Object> plan(String userId, Map<String, Object> offering, String planType,
                                     String effectiveDate) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("offering_id", str(offering.get("offeringId")));
        p.put("offering_name", str(offering.get("offeringName")));
        p.put("plan_type", planType);
        Object fee = offering.get("monthlyFee");
        p.put("monthly_fee", fee == null ? null : fee);
        p.put("state", str(offering.get("state")));
        p.put("effective_date", effectiveDate);
        return p;
    }

    /** 生效日期：mock 确定性（近 180 天内按种子偏移）。 */
    private String effectiveDate(String userId, int offset) {
        long seed = (userId.hashCode() + offset * 31L) & 0xffff;
        LocalDate effective = LocalDate.now().minusDays(30 + seed % 150);
        return effective.toString();
    }

    /** 合约期：主套包含合约时生成 mock 合约信息（到期日确定性）。 */
    private Map<String, Object> buildContract(String userId, List<Map<String, Object>> plans) {
        Map<String, Object> contract = new LinkedHashMap<>();
        long seed = (userId.hashCode() * 3 + 17) & 0xffff;
        boolean hasContract = seed % 2 == 0;
        if (!hasContract) {
            contract.put("has_contract", false);
            return contract;
        }
        LocalDate expiry = LocalDate.now().plusDays(90 + seed % 270);
        contract.put("has_contract", true);
        contract.put("expiry_date", expiry.toString());
        contract.put("remaining_days", (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiry));
        return contract;
    }

    /** 变更记录：mock 确定性 0~3 条（近6个月）。 */
    private List<Map<String, Object>> buildHistory(String userId) {
        List<Map<String, Object>> history = new ArrayList<>();
        long seed = (userId.hashCode() * 7 + 3) & 0xffff;
        int count = (int) (seed % 4);
        String[] actions = {"订购", "退订", "变更"};
        for (int i = 0; i < count; i++) {
            LocalDate date = LocalDate.now().minusDays(20 + (seed + i * 41L) % 160);
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("date", date.toString());
            h.put("action", actions[(int) ((seed + i) % actions.length)]);
            h.put("detail", "mock 演示变更记录（生产接 CRM 变更流水）");
            history.add(h);
        }
        return history;
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
        sources.put("plan_source", "mock");
        sources.put("plan_mode", "demo");
        sources.put("note", "v1 演示数据源（确定性生成）；生产接 CRM 用户套包只读视图");
        return sources;
    }

    /** 摘要：脱敏用户 + 在订主套包 + 合约到期提示。 */
    private String buildSummary(String userId, List<Map<String, Object>> plans, Map<String, Object> out) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户 ").append(maskUserId(userId));
        Map<String, Object> main = plans.stream()
                .filter(p -> PLAN_TYPE_MAIN.equals(p.get("plan_type")))
                .findFirst().orElse(plans.get(0));
        sb.append("当前在订 ").append(plans.size()).append(" 个套包，主套包「")
                .append(main.get("offering_name")).append("」");
        if (main.get("monthly_fee") != null) {
            sb.append("（月费 ").append(main.get("monthly_fee")).append(" 元）");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) out.get("contract");
        if (contract != null && Boolean.TRUE.equals(contract.get("has_contract"))) {
            sb.append("，合约至 ").append(contract.get("expiry_date"));
        }
        return sb.toString();
    }

    /**
     * 用户标识脱敏：保留前 3 后 2 位，中间打码；长度不足 6 位全打码。
     * 输出唯一可见的用户字段即本值（原始标识不落任何输出面）。
     */
    static String maskUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        String trimmed = userId.trim();
        if (trimmed.length() < 6) {
            return "*".repeat(trimmed.length());
        }
        return trimmed.substring(0, 3) + "*".repeat(trimmed.length() - 5) + trimmed.substring(trimmed.length() - 2);
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
