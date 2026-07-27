package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.IntentRecognitionSupport;
import com.sitech.prodai.intent.SseStreamSupport;
import com.sitech.prodai.intent.SseUtils;
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
                SseUtils.thinkingRich(
                        "正在加载运营监控告警与处置工单...",
                        Map.of(
                                "step", 5,
                                "totalSteps", 6,
                                "phase", "running",
                                "action", "ops_monitor"
                        ),
                        -1
                )
        );

        return SseStreamSupport.deferWork(
                prelude,
                this::loadMonitorPack,
                pack -> buildAfterEvents(ctx, pack)
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

    private List<Map<String, Object>> buildAfterEvents(IntentContext ctx, Map<String, Object> pack) {
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

        List<Map<String, Object>> events = new ArrayList<>();
        events.add(SseUtils.thinkingRich(
                ok ? "运营监控数据已就绪" : "运营监控加载异常",
                Map.of(
                        "step", 6,
                        "totalSteps", 6,
                        "alertCount", alertItems.size(),
                        "highPriorityCount", high,
                        "openWorkOrderCount", openWo,
                        "success", ok
                ),
                0
        ));
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
