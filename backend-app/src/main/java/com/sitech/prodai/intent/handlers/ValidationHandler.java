package com.sitech.prodai.intent.handlers;

import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.intent.BaseIntentHandler;
import com.sitech.prodai.intent.IntentContext;
import com.sitech.prodai.intent.SseUtils;
import com.sitech.prodai.intent.StreamStats;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Optional;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单校验意图处理器 —— 对齐 Python {@code app/intent/handlers/validation_handler.py::ValidationHandler}。
 *
 * <p>处理流程：
 * <ol>
 *   <li>Step 1：规则引擎校验（基于本体规则，使用 {@link ValidationService}）</li>
 *   <li>Step 2：AI 智能校验（LLM 校验，使用 {@link LlmService}）</li>
 *   <li>Step 3：汇总输出（结构化错误表 + 推荐值 + validation_pass/fail 事件）</li>
 * </ol>
 */
@Component
public class ValidationHandler implements BaseIntentHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidationHandler.class);

    private final ValidationService validationService;
    private final Optional<LlmService> llmService;
    private final ConfigLoader configLoader;

    public ValidationHandler(ValidationService validationService,
                             Optional<LlmService> llmService,
                             ConfigLoader configLoader) {
        this.validationService = validationService;
        this.llmService = llmService;
        this.configLoader = configLoader;
    }

    @Override
    public String getIntentType() {
        return "validate";
    }

    @Override
    public Flux<Map<String, Object>> handle(IntentContext ctx) {
        Map<String, Object> intentData = ctx.getIntentData();

        // 提取 form_data 和 form_code
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) firstNonNull(
                intentData.get("form_data"),
                intentData.get("formData"),
                intentData.get("extractedFields"),
                new LinkedHashMap<>());
        String formCode = firstNonBlank(
                str(intentData.get("form_code")),
                str(intentData.get("formCode")),
                str(intentData.get("detectedFormCode")),
                "unknown");

        final String code = formCode;
        final Map<String, Object> data = formData;

        // Phase 1：识别
        Map<String, Object> identifyResult = new LinkedHashMap<>();
        identifyResult.put("formCode", code);
        identifyResult.put("fieldCount", data.size());

        return Flux.concat(
                Flux.just(SseUtils.thinking("📋 识别到校验任务，表单类型：" + code, identifyResult)),

                // Step 1 + Step 2（异步执行）
                reactor.core.publisher.Mono.fromCallable(() -> doFullValidation(code, data))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(validationResult -> {
                            List<Map<String, Object>> events = new ArrayList<>();

                            // Step 1 结果
                            events.add(SseUtils.thinking(
                                    "🔍 Step 1/2：规则引擎校验" + (validationResult.ruleEnginePassed ? "通过" : "失败，" + validationResult.ruleErrors.size() + " 个问题"),
                                    Map.of(
                                            "formCode", code,
                                            "fieldsChecked", data.keySet(),
                                            "fieldCount", data.size(),
                                            "passed", validationResult.ruleEnginePassed,
                                            "issues", validationResult.ruleErrors,
                                            "issueCount", validationResult.ruleErrors.size()
                                    )
                            ));

                            // Step 2 结果
                            events.add(SseUtils.thinking(
                                    "🤖 Step 2/2：AI 智能校验" + (validationResult.llmErrors.isEmpty() ? "通过" : "发现 " + validationResult.llmErrors.size() + " 个错误"),
                                    Map.of(
                                            "llmErrors", validationResult.llmErrors.size(),
                                            "llmWarnings", validationResult.llmWarnings.size(),
                                            "ruleEnginePassed", validationResult.ruleEnginePassed
                                    )
                            ));

                            // Step 3：汇总
                            int totalErrors = validationResult.allErrors.size();
                            events.add(SseUtils.thinking(
                                    "📋 校验汇总：共 " + totalErrors + " 个错误，" + validationResult.llmWarnings.size() + " 个警告" + (totalErrors == 0 ? "（全部通过）" : ""),
                                    Map.of(
                                            "totalErrors", totalErrors,
                                            "totalWarnings", validationResult.llmWarnings.size(),
                                            "ruleEngineErrors", validationResult.ruleErrors.size(),
                                            "llmErrors", validationResult.llmErrors.size(),
                                            "passed", totalErrors == 0,
                                            "errors", validationResult.allErrors,
                                            "warnings", validationResult.llmWarnings,
                                            "validationTable", validationResult.validationTable
                                    )
                            ));

                            // Phase 3：输出
                            if (totalErrors > 0) {
                                Map<String, Object> failEvent = new LinkedHashMap<>();
                                failEvent.put("type", "validation_fail");
                                failEvent.put("form_code", code);
                                failEvent.put("step", "all");
                                failEvent.put("errors", validationResult.allErrors);
                                failEvent.put("warnings", validationResult.llmWarnings);
                                failEvent.put("rule_engine_passed", validationResult.ruleEnginePassed);
                                failEvent.put("validationTable", validationResult.validationTable);
                                events.add(failEvent);
                            } else {
                                Map<String, Object> passEvent = new LinkedHashMap<>();
                                passEvent.put("type", "validation_pass");
                                passEvent.put("form_code", code);
                                passEvent.put("step", "all");
                                passEvent.put("errors", List.of());
                                passEvent.put("warnings", validationResult.llmWarnings);
                                passEvent.put("rule_engine_passed", validationResult.ruleEnginePassed);
                                passEvent.put("validationTable", validationResult.validationTable);
                                events.add(passEvent);
                            }

                            // 统计 + done
                            StreamStats stats = ctx.getStreamStats();
                            if (stats != null) {
                                stats.setTotalElapsed((System.currentTimeMillis() - ctx.getStartTime()) / 1000.0);
                                events.add(SseUtils.stats(stats));
                            }
                            events.add(SseUtils.doneEvent("validate", false));
                            return Flux.fromIterable(events);
                        })
                        .onErrorResume(e -> {
                            log.error("[ValidationHandler] 校验失败", e);
                            List<Map<String, Object>> events = new ArrayList<>();
                            events.add(SseUtils.thinking("❌ 校验失败: " + e.getMessage(), Map.of("success", false, "error", e.getMessage())));
                            events.add(SseUtils.error("校验失败: " + e.getMessage()));
                            events.add(SseUtils.doneEvent("validate", false));
                            return Flux.fromIterable(events);
                        })
        );
    }

    /** 执行完整校验（规则引擎 + LLM） */
    private FullValidationResult doFullValidation(String formCode, Map<String, Object> formData) {
        // Step 1：规则引擎校验
        Map<String, Object> ontology = configLoader.getOntology(formCode);
        List<Map<String, Object>> fields = new ArrayList<>();
        if (ontology != null) {
            Object entitiesObj = ontology.get("entities");
            if (entitiesObj instanceof List<?> entities) {
                for (Object entity : entities) {
                    if (entity instanceof Map<?, ?> entityMap) {
                        Object fieldsObj = entityMap.get("fields");
                        if (fieldsObj instanceof List<?> fieldList) {
                            for (Object field : fieldList) {
                                if (field instanceof Map<?, ?> f) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> fieldMap = (Map<String, Object>) f;
                                    fields.add(fieldMap);
                                }
                            }
                        }
                    }
                }
            }
        }

        ValidationService.ValidationResult ruleResult = validationService.validateForm(formData, fields);
        List<Map<String, Object>> ruleErrors = new ArrayList<>();
        for (String error : ruleResult.errors) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("field", "");
            err.put("fieldCode", "");
            err.put("message", error);
            err.put("source", "rule_engine");
            err.put("errorCode", "ERR_VAL_RULE_FAIL");
            err.put("suggestion", error);
            ruleErrors.add(err);
        }

        // Step 2：LLM 智能校验
        List<Map<String, Object>> llmErrors = new ArrayList<>();
        List<String> llmWarnings = new ArrayList<>();
        if (llmService.isPresent()) {
            try {
                String llmPrompt = buildLlmValidationPrompt(formCode, formData, fields);
                String llmResponse = llmService.get().completePrompt(llmPrompt);
                // 简单解析 LLM 返回的错误
                if (llmResponse != null && !llmResponse.isEmpty()) {
                    parseLlmValidationResponse(llmResponse, llmErrors, llmWarnings);
                }
            } catch (Exception e) {
                log.warn("[ValidationHandler] LLM 校验失败: {}", e.getMessage());
            }
        } else {
            log.debug("[ValidationHandler] LLM service not available, skipping LLM validation");
        }

        // 汇总
        List<Map<String, Object>> allErrors = new ArrayList<>();
        allErrors.addAll(ruleErrors);
        allErrors.addAll(llmErrors);

        // 构建校验结果表格
        Map<String, Object> validationTable = buildValidationTable(formData, allErrors);

        return new FullValidationResult(
                ruleResult.valid,
                ruleErrors,
                llmErrors,
                llmWarnings,
                allErrors,
                validationTable
        );
    }

    private String buildLlmValidationPrompt(String formCode, Map<String, Object> formData, List<Map<String, Object>> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是表单校验助手。请校验以下表单数据是否符合规则。\n\n");
        sb.append("表单类型：").append(formCode).append("\n");
        sb.append("表单数据：\n");
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("\n字段定义：\n");
        for (Map<String, Object> field : fields) {
            sb.append("- ").append(field.get("fieldCode"))
              .append(" (").append(field.getOrDefault("fieldName", "")).append(")");
            if (Boolean.TRUE.equals(field.get("required"))) {
                sb.append(" [必填]");
            }
            sb.append("\n");
        }
        sb.append("\n请输出校验结果。如果没有错误，输出\"校验通过\"。如果有错误，每行一个错误，格式：\n");
        sb.append("字段名（字段编码）：错误描述\n");
        return sb.toString();
    }

    private void parseLlmValidationResponse(String response, List<Map<String, Object>> errors, List<String> warnings) {
        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.contains("校验通过") || line.contains("全部通过")) {
                continue;
            }
            if (line.startsWith("警告") || line.startsWith("Warning") || line.contains("建议")) {
                warnings.add(line);
                continue;
            }
            // 尝试解析 "字段名（字段编码）：错误描述" 格式
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "^([^（(（]+)[（(]([^）)]+)[）)][：:]\\s*(.*)");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("field", matcher.group(1).trim());
                err.put("fieldCode", matcher.group(2).trim());
                err.put("message", matcher.group(3).trim());
                err.put("source", "llm_validation");
                err.put("errorCode", "ERR_LLM_VALIDATION");
                err.put("suggestion", "请修正「" + matcher.group(1).trim() + "」的值");
                errors.add(err);
            } else if (!line.isEmpty()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("field", "未知字段");
                err.put("fieldCode", "");
                err.put("message", line);
                err.put("source", "llm_validation");
                err.put("errorCode", "ERR_LLM_VALIDATION");
                err.put("suggestion", "请检查输入");
                errors.add(err);
            }
        }
    }

    private Map<String, Object> buildValidationTable(Map<String, Object> formData, List<Map<String, Object>> errors) {
        java.util.Set<String> errorFieldCodes = new java.util.HashSet<>();
        for (Map<String, Object> err : errors) {
            String code = str(err.get("fieldCode"));
            if (!code.isEmpty()) {
                errorFieldCodes.add(code);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            String fieldCode = entry.getKey();
            String errorInfo = null;
            for (Map<String, Object> err : errors) {
                if (fieldCode.equals(str(err.get("fieldCode")))) {
                    errorInfo = str(err.get("suggestion"));
                    break;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fieldCode", fieldCode);
            row.put("fieldName", fieldCode);
            row.put("originalValue", entry.getValue());
            row.put("recommendedValue", "");
            row.put("validationResult", errorFieldCodes.contains(fieldCode) ? "不通过" : "通过");
            row.put("suggestion", errorInfo != null ? errorInfo : "");
            rows.add(row);
        }

        // 排序：不通过的排前面
        rows.sort((a, b) -> {
            boolean aFail = "不通过".equals(a.get("validationResult"));
            boolean bFail = "不通过".equals(b.get("validationResult"));
            return Boolean.compare(!aFail, !bFail);
        });

        long passedCount = rows.stream().filter(r -> "通过".equals(r.get("validationResult"))).count();
        long failedCount = rows.size() - passedCount;

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("columns", List.of(
                Map.of("key", "fieldCode", "label", "编码"),
                Map.of("key", "fieldName", "label", "名称"),
                Map.of("key", "originalValue", "label", "原值"),
                Map.of("key", "recommendedValue", "label", "推荐值"),
                Map.of("key", "validationResult", "label", "校验结果"),
                Map.of("key", "suggestion", "label", "优化建议")
        ));
        table.put("rows", rows);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalFields", rows.size());
        summary.put("passedCount", passedCount);
        summary.put("failedCount", failedCount);
        table.put("summary", summary);
        return table;
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

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object v : values) {
            if (v != null) return v;
        }
        return null;
    }

    /** 完整校验结果 */
    private record FullValidationResult(
            boolean ruleEnginePassed,
            List<Map<String, Object>> ruleErrors,
            List<Map<String, Object>> llmErrors,
            List<String> llmWarnings,
            List<Map<String, Object>> allErrors,
            Map<String, Object> validationTable
    ) {
    }
}
