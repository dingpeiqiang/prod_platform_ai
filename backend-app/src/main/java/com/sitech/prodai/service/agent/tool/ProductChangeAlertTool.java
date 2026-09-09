package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.domain.entity.ChangeAlert;
import com.sitech.prodai.domain.entity.ProductSubscription;
import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.UserScopeContext;
import com.sitech.prodai.service.changesub.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品变更订阅提醒工具（方案 §6-C3，对应缺口 C5）：查询→行动的轻量中间态。
 * <p>
 * 双动作（action 参数分流，单一工具承载订阅闭环）：
 * <ul>
 *   <li>{@code subscribe}：订阅指定商品变更提醒（月费/状态变更时产生提醒）；
 *       支持按编码精确定阅或按名称解析（与 product_360 同一解析器）；</li>
 *   <li>{@code unsubscribe}：注销订阅（幂等，未订阅也不报错）；</li>
 *   <li>{@code list_alerts}：查看已产生的变更提醒（自己名下 + 广播），
 *       连带展示当前订阅清单——订阅与提醒一屏闭环。</li>
 * </ul>
 * <p>
 * 安全约束：订阅人取服务端登录态（UserScopeContext），LLM/入参不可指定或覆盖；
 * 只读+轻写（仅订阅登记），无资费/档案写路径。
 * <p>
 * 零编排改动接入：实现 {@code getScenes()} 含 query 即进入场景能力集（SPI 自声明）。
 */
@Component
public class ProductChangeAlertTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ProductChangeAlertTool.class);

    /** 动作字典。 */
    private static final String ACTION_SUBSCRIBE = "subscribe";
    private static final String ACTION_UNSUBSCRIBE = "unsubscribe";
    private static final String ACTION_LIST_ALERTS = "list_alerts";

    /** 提醒查询默认条数上限。 */
    private static final int DEFAULT_ALERT_LIMIT = 20;

    private final SubscriptionService subscriptionService;
    private final ProductOntologyService productOntologyService;

    public ProductChangeAlertTool(SubscriptionService subscriptionService,
                                  ProductOntologyService productOntologyService) {
        this.subscriptionService = subscriptionService;
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "product_change_alert";
    }

    @Override
    public String getDescription() {
        return "订阅商品变更提醒或查看已产生的变更提醒：订阅后商品月费调整/上下架状态变化时产生提醒。"
                + "用户说「订阅XX商品的变更提醒」「有变更提醒我」「看一下我的变更提醒」「XX商品有变动吗」时使用；"
                + "查商品当前档案请用 product_360（订阅只关注后续变化，不回应当前状态）";
    }

    @Override
    public String getLabel() {
        return "变更订阅提醒";
    }

    @Override
    public java.util.Set<String> getScenes() {
        // 方案 §6-C3：查询助手"订阅商品变更提醒"轻量中间态为主口径；运营盯盘同样受益
        return java.util.Set.of("query", "ops");
    }

    /** 订阅后的典型业务链：看商品全景 / 查指标趋势。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("product_360", "metric_query", "sparql_query");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("action")
                        .label("动作")
                        .description("subscribe=订阅变更提醒 / unsubscribe=注销订阅 / list_alerts=查看提醒（默认 list_alerts）")
                        .type("string")
                        .enumValues(List.of(ACTION_SUBSCRIBE, ACTION_UNSUBSCRIBE, ACTION_LIST_ALERTS))
                        .defaultValue(ACTION_LIST_ALERTS)
                        .build(),
                ToolParam.builder("offering_id")
                        .label("商品编码")
                        .description("商品编码（subscribe/unsubscribe 必填）；与 offering_name 至少提供一个")
                        .type("string")
                        .source("context")
                        .build(),
                ToolParam.builder("offering_name")
                        .label("商品名称")
                        .description("商品名称或含商品名的话术（无编码时按名称最长匹配解析）")
                        .type("string")
                        .source("question")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("处理结果").type("string")
                        .description("订阅/提醒处理结果摘要").build(),
                ToolOutputField.builder("subscriptions", ToolOutputField.Role.OTHER)
                        .label("当前订阅").type("list")
                        .description("当前生效的订阅清单（offering_id/offering_name/created_at）").build(),
                ToolOutputField.builder("alerts", ToolOutputField.Role.ITEMS)
                        .label("变更提醒").type("list")
                        .description("变更提醒条目（商品/类型/旧值/新值/时间）").build(),
                ToolOutputField.builder("sources", ToolOutputField.Role.OTHER)
                        .label("数据来源").type("object")
                        .description("数据来源与口径").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String action = params != null ? str(params.getOrDefault("action", ACTION_LIST_ALERTS)) : ACTION_LIST_ALERTS;
        String offeringId = params != null ? str(params.get("offering_id")) : "";
        String offeringName = params != null ? str(params.get("offering_name")) : "";
        log.info("[AgentTool] product_change_alert 执行: action={}, offeringId={}, offeringName={}",
                action, offeringId, offeringName);

        // 订阅人取服务端登录态（方案 §4.4 信任模型）：LLM/入参不可指定或覆盖
        String subscriber = UserScopeContext.current().getUserId();
        if (subscriber == null || subscriber.isBlank()) {
            return ExecutionResult.fail(getName(),
                    "未识别到登录用户：变更订阅提醒需登录后使用（订阅人与提醒归属按登录账号隔离）");
        }

        try {
            Map<String, Object> out = new LinkedHashMap<>();
            switch (action) {
                case ACTION_SUBSCRIBE -> out.putAll(handleSubscribe(subscriber, offeringId, offeringName));
                case ACTION_UNSUBSCRIBE -> out.putAll(handleUnsubscribe(subscriber, offeringId, offeringName));
                case ACTION_LIST_ALERTS -> out.putAll(handleListAlerts(subscriber));
                default -> {
                    return ExecutionResult.fail(getName(),
                            "未知动作: " + action + "（支持 subscribe/unsubscribe/list_alerts）");
                }
            }
            out.put("sources", buildSources());
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] product_change_alert 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "变更订阅提醒处理失败: " + e.getMessage());
        }
    }

    /** 订阅登记：商品解析（id 精确/名称模糊）→ 幂等登记 → 回执。 */
    private Map<String, Object> handleSubscribe(String subscriber, String offeringId, String offeringName) {
        String resolvedId = resolveOffering(offeringId, offeringName);
        if (resolvedId == null) {
            return failView("无法定位要订阅的商品：请提供商品编码或确切的商品名称");
        }
        String name = offeringDisplayName(resolvedId);
        Map<String, Object> receipt = subscriptionService.subscribe(subscriber, resolvedId, name);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", ACTION_SUBSCRIBE);
        out.put("subscription", receipt);
        out.put("subscriptions", subscriptionViews(subscriber));
        out.put("alerts", List.of());
        out.put("nl_answer", (Boolean.TRUE.equals(receipt.get("renewed")) ? "已续订" : "订阅成功")
                + "：" + name + "（" + resolvedId + "），该商品月费或上下架状态变更时将产生提醒。"
                + "说「看变更提醒」可随时查看");
        return out;
    }

    /** 注销订阅：幂等（未订阅也返回成功）。 */
    private Map<String, Object> handleUnsubscribe(String subscriber, String offeringId, String offeringName) {
        String resolvedId = resolveOffering(offeringId, offeringName);
        if (resolvedId == null) {
            return failView("无法定位要注销订阅的商品：请提供商品编码或确切的商品名称");
        }
        Map<String, Object> receipt = subscriptionService.unsubscribe(subscriber, resolvedId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", ACTION_UNSUBSCRIBE);
        out.put("subscription", receipt);
        out.put("subscriptions", subscriptionViews(subscriber));
        out.put("alerts", List.of());
        out.put("nl_answer", Boolean.TRUE.equals(receipt.get("cancelled"))
                ? "已注销订阅：" + resolvedId + "（不再接收该商品变更提醒）"
                : "该商品当前不在订阅中：" + resolvedId + "（无需注销）");
        return out;
    }

    /** 提醒查询：已产生的提醒 + 当前订阅清单（一屏闭环）。 */
    private Map<String, Object> handleListAlerts(String subscriber) {
        List<ChangeAlert> alerts = subscriptionService.alertsOf(subscriber, DEFAULT_ALERT_LIMIT);
        List<Map<String, Object>> alertViews = new ArrayList<>();
        for (ChangeAlert alert : alerts) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("offering_id", alert.getOfferingId());
            view.put("offering_name", alert.getOfferingName());
            view.put("change_type", alert.getChangeType());
            view.put("old_value", alert.getOldValue());
            view.put("new_value", alert.getNewValue());
            view.put("created_at", alert.getCreatedAt() == null ? "" : alert.getCreatedAt().toString());
            alertViews.add(view);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", ACTION_LIST_ALERTS);
        out.put("subscriptions", subscriptionViews(subscriber));
        out.put("alerts", alertViews);
        out.put("unread_count", subscriptionService.unreadCount(subscriber));
        out.put("nl_answer", buildAlertsSummary(alerts));
        return out;
    }

    /** 提醒摘要：条数 + 变更要点串述；空态明示无提醒（不冒充）。 */
    private String buildAlertsSummary(List<ChangeAlert> alerts) {
        if (alerts.isEmpty()) {
            return "暂无变更提醒。订阅商品后（说「订阅XX商品的变更提醒」），"
                    + "商品月费调整或上下架时这里会出现提醒";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(alerts.size()).append(" 条变更提醒：");
        for (int i = 0; i < Math.min(3, alerts.size()); i++) {
            ChangeAlert a = alerts.get(i);
            if (i > 0) {
                sb.append("；");
            }
            sb.append(a.getOfferingName());
            if (a.getOfferingName() == null || a.getOfferingName().isBlank()) {
                sb.append(a.getOfferingId());
            }
            sb.append(TYPE_FEE_CN.equals(a.getChangeType()) ? " 月费 " : " 状态 ");
            sb.append(a.getOldValue()).append("→").append(a.getNewValue());
        }
        if (alerts.size() > 3) {
            sb.append(" 等（其余 ").append(alerts.size() - 3).append(" 条见提醒列表）");
        }
        return sb.toString();
    }

    private static final String TYPE_FEE_CN = "fee_change";

    /** 商品解析：与 product_360 同一解析器（id 精确优先，名称模糊兜底）。 */
    private String resolveOffering(String offeringId, String offeringName) {
        String resolved = productOntologyService.resolveOfferingId(offeringId, offeringName);
        return resolved == null || resolved.isBlank() ? null : resolved;
    }

    /** 订阅回执用的商品展示名（档案面反查，未命中回落编码）。 */
    private String offeringDisplayName(String offeringId) {
        try {
            Object shelf = productOntologyService.loadGraph().get("shelfOfferings");
            if (shelf instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && offeringId.equals(str(m.get("offeringId")))) {
                        return str(m.get("offeringName"));
                    }
                }
            }
        } catch (Exception ignored) {
            // 名称反查失败回落编码（不阻断订阅）
        }
        return offeringId;
    }

    private List<Map<String, Object>> subscriptionViews(String subscriber) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (ProductSubscription sub : subscriptionService.activeSubscriptionsOf(subscriber)) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("offering_id", sub.getOfferingId());
            view.put("offering_name", sub.getOfferingName());
            view.put("created_at", sub.getCreatedAt() == null ? "" : sub.getCreatedAt().toString());
            views.add(view);
        }
        return views;
    }

    /** 业务失败（未定位商品等）：不抛异常，转结构化失败视图。 */
    private Map<String, Object> failView(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nl_answer", message);
        out.put("subscriptions", List.of());
        out.put("alerts", List.of());
        return out;
    }

    private Map<String, Object> buildSources() {
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("alert_source", "pd_ai_change_alerts");
        sources.put("detect_mode", "reload_diff");
        sources.put("note", "变更检测随图谱重载运行（prodai.change-sub.detect-enabled 开关），"
                + "提醒落库后经本工具查询透出");
        return sources;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
