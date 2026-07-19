package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.service.OntologyService;
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
 * 删除表单意图处理器 —— 对齐 Python {@code app/intent/handlers/delete_form_handler.py::DeleteFormHandler}。
 *
 * <p>自动备份 + 删除。Python 端调用 AdminService.delete_ontology，
 * Java 端使用 {@link OntologyService}（文件数据源模式下写操作返回"不支持"）。
 */
@Component
public class DeleteFormHandler implements BaseIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(DeleteFormHandler.class);

    private final OntologyService ontologyService;

    public DeleteFormHandler(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @Override
    public String getIntentType() {
        return "delete_form";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        Map<String, Object> intentData = ctx.getIntentData();
        String targetCode = firstNonBlank(str(intentData.get("formCode")), str(intentData.get("detectedFormCode")));
        String targetName = str(intentData.get("formName"));
        if (targetName.isEmpty() && targetCode != null && ctx.getOntologies().containsKey(targetCode)) {
            targetName = str(ctx.getOntologies().get(targetCode).get("formName"));
        }

        final String code = targetCode != null ? targetCode : "";
        final String name = targetName;

        // Phase 1：识别
        Map<String, Object> identifyResult = new LinkedHashMap<>();
        identifyResult.put("formCode", code);
        identifyResult.put("formName", name);
        identifyResult.put("autoBackup", true);

        return Flux.concat(
                Flux.just(SseUtils.thinking("🗑\uFE0F 确认删除表单「" + (name.isEmpty() ? code : name) + "」（自动备份）", identifyResult)),

                // Phase 2：执行（在 boundedElastic 线程池中执行阻塞调用）
                reactor.core.publisher.Mono.fromCallable(() -> ontologyService.deleteOntology(code))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(deleteResult -> {
                            List<Map<String, Object>> events = new ArrayList<>();
                            StreamStats stats = ctx.getStreamStats();
                            if (stats != null) {
                                stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
                            }

                            if (Boolean.TRUE.equals(deleteResult.get("success"))) {
                                String backupId = "";
                                Object backup = deleteResult.get("backup");
                                if (backup instanceof Map<?, ?> backupMap) {
                                    backupId = str(backupMap.get("id"));
                                }

                                Map<String, Object> successResult = new LinkedHashMap<>();
                                successResult.put("success", true);
                                successResult.put("backupVersionId", backupId);
                                successResult.put("message", str(deleteResult.get("message")));

                                events.add(SseUtils.thinking("✅ 已删除表单「" + (name.isEmpty() ? code : name) + "」", successResult));
                                if (stats != null) {
                                    events.add(SseUtils.stats(stats));
                                }

                                Map<String, Object> intentEventData = new LinkedHashMap<>();
                                intentEventData.put("formCode", code);
                                intentEventData.put("formName", name);
                                intentEventData.put("backupVersionId", backupId);
                                intentEventData.put("message", str(deleteResult.get("message")));
                                events.add(SseUtils.intentEvent("delete_form", "delete", intentEventData, false));

                                // 流式输出确认文本
                                String confirmText = "已删除表单「" + (name.isEmpty() ? code : name) + "」。如需恢复，可以在版本历史中回退。";
                                events.add(SseUtils.textStart());
                                for (int i = 0; i < confirmText.length(); i += 3) {
                                    events.add(SseUtils.text(confirmText.substring(i, Math.min(i + 3, confirmText.length()))));
                                }
                                events.add(SseUtils.textEnd());
                                events.add(SseUtils.doneEvent("delete_form", false, ctx.getIntentData()));
                            } else {
                                String errorMsg = str(deleteResult.get("message"));
                                if (errorMsg.isEmpty()) {
                                    errorMsg = "删除失败";
                                }
                                Map<String, Object> failResult = new LinkedHashMap<>();
                                failResult.put("success", false);
                                failResult.put("error", errorMsg);
                                events.add(SseUtils.thinking("❌ 删除失败: " + errorMsg, failResult));
                                if (stats != null) {
                                    stats.setError(errorMsg);
                                    events.add(SseUtils.stats(stats));
                                }
                                events.add(SseUtils.error(errorMsg));
                                events.add(SseUtils.doneEvent("delete_form", false, ctx.getIntentData()));
                            }
                            return Flux.fromIterable(events);
                        })
        );
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
