package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.intent.SseStreamSupport;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.ThinkingStepBuilder;
import com.sitech.prodai.service.ProductOntologyService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营监控意图：告警列表 + 处置工单，与 REST /ops/alerts、/ops/work-orders 同源。
 * 不调用 getOpsDashboard（内含全量稽核，偏慢）；摘要由告警与工单聚合。
 */
@Component
public class ProductOpsMonitorHandler implements BaseIntentHandler {

    private final ProductOntologyService productOntologyService;

    public ProductOpsMonitorHandler(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getIntentType() {
        return "product_ops_monitor";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        if (IntentRecognitionSupport.isMetaGuideRequest(ctx.getLastUserMessage())) {
            return Flux.fromIterable(IntentRecognitionSupport.metaGuideSkipEvents("运营监控"));
        }
        List<Map<String, Object>> prelude = List.of(
                ThinkingStepBuilder.running(
                        "pull", "拉取告警清单", "正在拉取运营监控告警...",
                        2, 5, Map.of("action", "ops_monitor"))
        );

        return SseStreamSupport.deferWork(
                prelude,
                this::loadMonitorPack,
                (pack, elapsedMs) -> buildAfterEvents(ctx, pack, elapsedMs)
        );
    }

    private Map<String, Object> loadMonitorPack() {
        Map<String, Object> alerts = productOntologyService.listOpsAlerts(null);
        Map<String, Object> workOrders = productOntologyService.listWorkOrders(null);
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("alerts", alerts);
        pack.put("workOrders", workOrders);
        pack.put("success", !Boolean.FALSE.equals(alerts.get("success"))
                && !Boolean.FALSE.equals(workOrders.get("success")));
        return pack;
    }

    private List<Map<String, Object>> buildAfterEvents(IntentContext ctx, Map<String, Object> pack, long elapsedMs) {
        Map<String, Object> alerts = castMap(pack.get("alerts"));
        Map<String, Object> workOrders = castMap(pack.get("workOrders"));
        List<Map<String, Object>> alertItems = toMapList(alerts.get("items"));
        List<Map<String, Object>> woItems = toMapList(workOrders.get("items"));
        boolean ok = Boolean.TRUE.equals(pack.get("success"));

        long high = alertItems.stream()
                .filter(a -> "HIGH".equals(String.valueOf(a.get("severity")))
                        || "anomaly".equals(String.valueOf(a.get("type"))))
                .count();
        long openWo = woItems.stream()
                .filter(w -> {
                    String st = String.valueOf(w.getOrDefault("status", ""));
                    return "open".equalsIgnoreCase(st) || "in_progress".equalsIgnoreCase(st);
                })
                .count();

        Map<String, Object> intentData = new LinkedHashMap<>();
        intentData.put("action", "ops_monitor");
        intentData.put("success", ok);
        intentData.put("alerts", alerts);
        intentData.put("workOrders", workOrders);
        intentData.put("alertItems", alertItems);
        intentData.put("workOrderItems", woItems);
        intentData.put("alertCount", alerts.getOrDefault("total", alertItems.size()));
        intentData.put("highPriorityCount", high);
        intentData.put("openWorkOrderCount", openWo);
        intentData.put("generatedAt", alerts.get("generatedAt"));

        String answerText = formatMonitorAnswer(alertItems, high, openWo, alerts);
        Object alertTotal = alerts.getOrDefault("total", alertItems.size());
        String concludeResult = ok
                ? "高优待办 " + high + " 条 · 进行中工单 " + openWo + " 张"
                : "运营监控加载异常";

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(ThinkingStepBuilder.done(
                "pull", "拉取告警清单", "拉取运营监控告警",
                "共 " + alertTotal + " 条告警", 2, 5, 0, null,
                Map.of("alertCount", alertItems.size())));
        events.add(ThinkingStepBuilder.done(
                "grade", "告警分级统计", "按优先级与异动类型分级",
                "高优先级 " + high + " 条", 3, 5, 0, null,
                Map.of("highPriorityCount", high)));
        events.add(ThinkingStepBuilder.done(
                "link", "关联处置工单", "关联进行中的处置工单",
                "进行中工单 " + openWo + " 张", 4, 5, 0, null,
                Map.of("openWorkOrderCount", openWo)));
        events.add(ThinkingStepBuilder.done(
                "conclude", "待办摘要", "汇总监控待办",
                concludeResult, 5, 5, elapsedMs, null,
                Map.of("alertCount", alertItems.size(), "highPriorityCount", high,
                        "openWorkOrderCount", openWo, "success", ok)));
        events.add(SseUtils.intentEvent(getIntentType(), "ops_monitor", intentData, false));
        events.addAll(SseStreamSupport.chunkedTextEvents(answerText));
        events.add(SseUtils.stats(ctx.getStreamStats()));

        Map<String, Object> donePayload = new LinkedHashMap<>();
        donePayload.put("intentType", getIntentType());
        donePayload.put("action", "ops_monitor");
        donePayload.put("alerts", alerts);
        donePayload.put("workOrders", workOrders);
        donePayload.put("alertCount", alertItems.size());
        donePayload.put("highPriorityCount", high);
        donePayload.put("openWorkOrderCount", openWo);
        donePayload.put("stats", Map.of(
                "alertCount", alertItems.size(),
                "highPriorityCount", high,
                "openWorkOrderCount", openWo
        ));
        events.add(SseUtils.doneEvent(getIntentType(), false, donePayload));
        return events;
    }

    private String formatMonitorAnswer(
            List<Map<String, Object>> alertItems,
            long high,
            long openWo,
            Map<String, Object> alerts
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 运营监控告警\n\n");
        sb.append("共 **").append(alerts.getOrDefault("total", alertItems.size())).append("** 条告警，")
                .append("高优先级约 **").append(high).append("** 条，")
                .append("进行中工单 **").append(openWo).append("** 张。\n\n");
        int limit = Math.min(6, alertItems.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> a = alertItems.get(i);
            sb.append("- [").append(a.getOrDefault("tag", a.getOrDefault("type", "-"))).append("] **")
                    .append(a.getOrDefault("offeringName", a.getOrDefault("id", "-"))).append("**：")
                    .append(a.getOrDefault("text", "")).append("\n");
        }
        if (alertItems.isEmpty()) {
            sb.append("（暂无告警）\n");
        }
        sb.append("\n右侧已打开监控面板：选择告警后可一键智能归因，或跳转风险稽核。");
        return sb.toString();
    }

    private Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }
}
