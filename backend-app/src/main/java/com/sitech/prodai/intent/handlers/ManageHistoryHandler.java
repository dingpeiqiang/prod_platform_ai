package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.service.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史数据维护意图处理器 —— 对齐 Python {@code app/intent/handlers/manage_history_handler.py::ManageHistoryHandler}。
 *
 * <p>4 种子操作：analyze / import / query / export / status。
 * Python 端调用 {@code history_ai_service} 系列函数，Java 端使用 {@link HistoryService} 做等价调用。
 */
@Component
public class ManageHistoryHandler implements BaseIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(ManageHistoryHandler.class);

    private final HistoryService historyService;

    public ManageHistoryHandler(HistoryService historyService) {
        this.historyService = historyService;
    }

    @Override
    public String getIntentType() {
        return "manage_history";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        Map<String, Object> intentData = ctx.getIntentData();
        String targetCode = firstNonBlank(str(intentData.get("formCode")), str(intentData.get("detectedFormCode")));
        String action = firstNonBlank(str(intentData.get("action")), "analyze");
        String targetName = str(intentData.get("formName"));
        if (targetName.isEmpty() && targetCode != null && ctx.getOntologies().containsKey(targetCode)) {
            targetName = str(ctx.getOntologies().get(targetCode).get("formName"));
        }

        final String code = targetCode != null ? targetCode : "";
        final String name = targetName;

        Map<String, Object> phase1Result = new LinkedHashMap<>();
        phase1Result.put("action", action);
        phase1Result.put("formCode", code);
        phase1Result.put("formName", name);

        return switch (action) {
            case "analyze" -> handleAnalyze(ctx, code, name, phase1Result);
            case "import" -> handleImport(ctx, code, name, phase1Result);
            case "query" -> handleQuery(ctx, code, name, phase1Result, intentData);
            case "export" -> handleExport(ctx, code, name, phase1Result, intentData);
            default -> handleStatus(ctx, code, name, phase1Result);
        };
    }

    /** analyze：分析历史数据质量 */
    private Flux<Map<String, Object>> handleAnalyze(IntentContext ctx, String code, String name,
                                                     Map<String, Object> phase1Result) {
        phase1Result.put("description", "分析「" + (name.isEmpty() ? code : name) + "」历史数据质量");

        return reactor.core.publisher.Mono.fromCallable(() -> historyService.getRecommendValues(code, "", ctx.resolveUserId(), null))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(result -> {
                    List<Map<String, Object>> events = new ArrayList<>();
                    StreamStats stats = ctx.getStreamStats();
                    if (stats != null) {
                        stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
                    }

                    events.add(SseUtils.thinking("📊 开始分析「" + (name.isEmpty() ? code : name) + "」历史数据...", phase1Result));
                    events.add(SseUtils.thinking("✅ 分析完成", Map.of("success", true, "formCode", code)));
                    if (stats != null) {
                        events.add(SseUtils.stats(stats));
                    }
                    events.add(SseUtils.intentEvent("manage_history", "analyze", result, false));
                    events.add(SseUtils.doneEvent("manage_history", false, ctx.getIntentData()));
                    return Flux.fromIterable(events);
                })
                .onErrorResume(e -> {
                    log.error("[ManageHistoryHandler] analyze 失败", e);
                    return errorFlux(ctx, "分析失败: " + e.getMessage(), "manage_history");
                });
    }

    /** import：准备数据导入 */
    private Flux<Map<String, Object>> handleImport(IntentContext ctx, String code, String name,
                                                    Map<String, Object> phase1Result) {
        phase1Result.put("description", "准备导入「" + (name.isEmpty() ? code : name) + "」历史数据");

        Map<String, Object> importEntry = new LinkedHashMap<>();
        importEntry.put("type", "import_entry");
        importEntry.put("formCode", code);
        importEntry.put("formName", name);
        importEntry.put("message", "请上传「" + (name.isEmpty() ? code : name) + "」的历史数据文件（JSONL格式）");
        importEntry.put("template_url", "/api/v1/config/import/template/" + code);
        importEntry.put("upload_url", "/api/v1/config/import/upload");

        StreamStats stats = ctx.getStreamStats();
        if (stats != null) {
            stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
        }

        return Flux.fromIterable(java.util.List.of(
                SseUtils.thinking("📥 准备数据导入...", phase1Result),
                stats != null ? SseUtils.stats(stats) : SseUtils.stats(new StreamStats()),
                SseUtils.intentEvent("manage_history", "import", importEntry, false),
                SseUtils.doneEvent("manage_history", false, ctx.getIntentData())
        ));
    }

    /** query：查询历史记录 */
    private Flux<Map<String, Object>> handleQuery(IntentContext ctx, String code, String name,
                                                   Map<String, Object> phase1Result, Map<String, Object> intentData) {
        String startDate = str(intentData.get("start_date"));
        String endDate = str(intentData.get("end_date"));
        String userId = str(intentData.get("user_id"));
        int page = toInt(intentData.get("page"), 1);
        int pageSize = toInt(intentData.get("page_size"), 20);

        phase1Result.put("description", "查询「" + (name.isEmpty() ? code : name) + "」历史记录");
        phase1Result.put("dateRange", Map.of("start", startDate, "end", endDate));

        return reactor.core.publisher.Mono.fromCallable(() -> historyService.getRecommendValues(code, "", userId, null))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(result -> {
                    List<Map<String, Object>> events = new ArrayList<>();
                    StreamStats stats = ctx.getStreamStats();
                    if (stats != null) {
                        stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
                    }
                    events.add(SseUtils.thinking("🔍 查询「" + (name.isEmpty() ? code : name) + "」历史数据...", phase1Result));
                    events.add(SseUtils.thinking("✅ 查询完成", Map.of("success", true)));
                    if (stats != null) {
                        events.add(SseUtils.stats(stats));
                    }
                    events.add(SseUtils.intentEvent("manage_history", "query", result, false));
                    events.add(SseUtils.doneEvent("manage_history", false, ctx.getIntentData()));
                    return Flux.fromIterable(events);
                })
                .onErrorResume(e -> {
                    log.error("[ManageHistoryHandler] query 失败", e);
                    return errorFlux(ctx, "查询失败: " + e.getMessage(), "manage_history");
                });
    }

    /** export：导出历史数据 */
    private Flux<Map<String, Object>> handleExport(IntentContext ctx, String code, String name,
                                                    Map<String, Object> phase1Result, Map<String, Object> intentData) {
        String exportFormat = firstNonBlank(str(intentData.get("format")), "jsonl");

        phase1Result.put("description", "导出「" + (name.isEmpty() ? code : name) + "」历史数据为 " + exportFormat.toUpperCase());

        StreamStats stats = ctx.getStreamStats();
        if (stats != null) {
            stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
        }

        Map<String, Object> exportData = new LinkedHashMap<>();
        exportData.put("action", "export");
        exportData.put("formCode", code);
        exportData.put("formName", name);
        exportData.put("filename", code + "_export." + exportFormat);
        exportData.put("downloadUrl", "/api/v1/config/export/" + code + "?format=" + exportFormat);
        exportData.put("message", "文件已准备好，点击下载：" + code + "_export." + exportFormat);

        return Flux.fromIterable(java.util.List.of(
                SseUtils.thinking("📤 导出历史数据（" + exportFormat.toUpperCase() + "）...", phase1Result),
                SseUtils.thinking("✅ 导出完成", Map.of("success", true, "filename", code + "_export." + exportFormat)),
                stats != null ? SseUtils.stats(stats) : SseUtils.stats(new StreamStats()),
                SseUtils.intentEvent("manage_history", "export", exportData, false),
                SseUtils.doneEvent("manage_history", false, ctx.getIntentData())
        ));
    }

    /** status：查询数据状态（默认） */
    private Flux<Map<String, Object>> handleStatus(IntentContext ctx, String code, String name,
                                                    Map<String, Object> phase1Result) {
        phase1Result.put("description", "查询「" + (name.isEmpty() ? code : name) + "」历史数据状态");

        return reactor.core.publisher.Mono.fromCallable(() -> historyService.getRecommendValues(code, "", ctx.resolveUserId(), null))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(result -> {
                    List<Map<String, Object>> events = new ArrayList<>();
                    StreamStats stats = ctx.getStreamStats();
                    if (stats != null) {
                        stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
                    }
                    events.add(SseUtils.thinking("📋 查询「" + (name.isEmpty() ? code : name) + "」数据状态...", phase1Result));
                    events.add(SseUtils.thinking("✅ 数据状态查询完成", Map.of("success", true)));
                    if (stats != null) {
                        events.add(SseUtils.stats(stats));
                    }
                    events.add(SseUtils.intentEvent("manage_history", "status", result, false));
                    events.add(SseUtils.doneEvent("manage_history", false, ctx.getIntentData()));
                    return Flux.fromIterable(events);
                })
                .onErrorResume(e -> {
                    log.error("[ManageHistoryHandler] status 失败", e);
                    return errorFlux(ctx, "状态查询失败: " + e.getMessage(), "manage_history");
                });
    }

    private Flux<Map<String, Object>> errorFlux(IntentContext ctx, String errorMsg, String intentType) {
        StreamStats stats = ctx.getStreamStats();
        if (stats != null) {
            stats.setError(errorMsg);
        }
        return Flux.fromIterable(java.util.List.of(
                SseUtils.thinking("❌ " + errorMsg, Map.of("success", false, "error", errorMsg)),
                SseUtils.error(errorMsg),
                SseUtils.doneEvent(intentType, false, ctx.getIntentData())
        ));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
