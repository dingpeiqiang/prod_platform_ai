package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.ops.OpsExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * 配置文档批量导入服务（R2 Phase3 从 {@link ProductOntologyService} 拆出）。
 * <p>职责单一：智读链路——文档解析（docx/pdf/xlsx → 文本）、批量抽取（LLM/模板）、
 * 逐案合规评估与置信度排序、上传暂存访问。
 * 事实图读取、合规校验、派生引擎、审计链经函数式回调由宿主提供，保持图缓存与规则所有权不外泄。
 */
@Service
public class ConfigDocImportService {

    private static final Logger log = LoggerFactory.getLogger(ConfigDocImportService.class);

    private final ConfigDocumentParser documentParser;
    private final ConfigDocumentStorage documentStorage;
    private final ObjectMapper objectMapper;

    /** 派生回调：slots → {draft, inferredFields, appliedRules}。 */
    @FunctionalInterface
    public interface DraftDeriver {
        Map<String, Object> derive(Map<String, Object> slots, Map<String, Object> draft, Map<String, Object> graph);
    }

    /** 合规回调：draft → {compliancePass, issues, appliedRules}。 */
    @FunctionalInterface
    public interface ComplianceChecker {
        Map<String, Object> checkCompliance(Map<String, Object> draft);
    }

    /** 事实图读取回调（宿主 graphCache 单源）。 */
    @FunctionalInterface
    public interface GraphSupplier {
        Map<String, Object> loadGraph();
    }

    /** 审计链追加回调（traceId, step）。 */
    @FunctionalInterface
    public interface AuditAppender extends BiConsumer<String, Map<String, Object>> {
        void accept(String traceId, Map<String, Object> step);
    }

    public ConfigDocImportService(ConfigDocumentParser documentParser,
                                  ConfigDocumentStorage documentStorage,
                                  ObjectMapper objectMapper) {
        this.documentParser = documentParser;
        this.documentStorage = documentStorage;
        this.objectMapper = objectMapper;
    }

    /** 智读：先解析文档再批量映射。 */
    public Map<String, Object> batchFromDocumentBytes(byte[] bytes, String fileName,
                                                      GraphSupplier graphSupplier,
                                                      OpsExtractionService extractionService,
                                                      TemplateDeriveEngine deriveEngine,
                                                      ComplianceChecker complianceChecker,
                                                      ConfigMessageProjector messageProjector,
                                                      OpsRulesService opsRules,
                                                      AuditAppender auditAppender) {
        ConfigDocumentParser.ParseResult parsed = documentParser.parse(bytes, fileName);
        if (!parsed.success()) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", parsed.message());
            fail.put("parseEngine", parsed.engine());
            return fail;
        }
        Map<String, Object> body = batchFromDocument(parsed.text(), null,
                graphSupplier, extractionService, deriveEngine, complianceChecker, messageProjector, opsRules);
        body.put("parseEngine", parsed.engine());
        body.put("fileName", fileName);
        body.put("extractedChars", parsed.text() == null ? 0 : parsed.text().length());
        String traceId = "cfg-batch-" + Instant.now().toEpochMilli();
        auditAppender.accept(traceId, Map.of(
                "step", "document_parse",
                "file_name", fileName,
                "engine", parsed.engine(),
                "timestamp", Instant.now().toString()
        ));
        auditAppender.accept(traceId, Map.of(
                "step", "evaluate_policy_with_facts",
                "total", body.get("total"),
                "passed", body.get("passedCount"),
                "timestamp", Instant.now().toString()
        ));
        body.put("trace_id", traceId);
        return body;
    }

    /** 智读：选择文件后预上传，发送时按 fileId 解析映射（不再二次传原文）。 */
    public Map<String, Object> uploadConfigDocument(org.springframework.web.multipart.MultipartFile file) {
        return documentStorage.store(file);
    }

    /** 智读：文档暂存访问器（下载端点按 fileId 取原文件）。 */
    public ConfigDocumentStorage documentStorage() {
        return documentStorage;
    }

    public Map<String, Object> batchFromUploadedFile(String fileId, String fileName,
                                                     GraphSupplier graphSupplier,
                                                     OpsExtractionService extractionService,
                                                     TemplateDeriveEngine deriveEngine,
                                                     ComplianceChecker complianceChecker,
                                                     ConfigMessageProjector messageProjector,
                                                     OpsRulesService opsRules,
                                                     AuditAppender auditAppender) {
        byte[] bytes = documentStorage.readBytes(fileId);
        String name = (fileName == null || fileName.isBlank()) ? fileId : fileName;
        Map<String, Object> body = batchFromDocumentBytes(bytes, name,
                graphSupplier, extractionService, deriveEngine, complianceChecker, messageProjector, opsRules, auditAppender);
        body.put("file_id", fileId);
        body.put("fileId", fileId);
        return body;
    }

    /**
     * 智读主链路：文档文本（或外部已抽取包）→ 派生 → 合规 → 置信度排序。
     */
    public Map<String, Object> batchFromDocument(String documentText, List<Map<String, Object>> packages,
                                                 GraphSupplier graphSupplier,
                                                 OpsExtractionService extractionService,
                                                 TemplateDeriveEngine deriveEngine,
                                                 ComplianceChecker complianceChecker,
                                                 ConfigMessageProjector messageProjector,
                                                 OpsRulesService opsRules) {
        Map<String, Object> graph = graphSupplier.loadGraph();
        List<Map<String, Object>> pkgs = packages;
        String extractEngine = "provided";
        if (pkgs == null || pkgs.isEmpty()) {
            pkgs = List.of();
            OpsExtractionService.PackageExtractResult extracted =
                    extractionService.extractPackages(documentText, List.of());
            pkgs = extracted.packages();
            extractEngine = extracted.engine();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (int idx = 0; idx < pkgs.size(); idx++) {
            Map<String, Object> slots = deepCopy(pkgs.get(idx));
            // 业务场景以文档抽取结果为准，不再默认灌入校园体验
            Map<String, Object> infer = deriveEngine.derive(slots, null, graph);
            @SuppressWarnings("unchecked")
            Map<String, Object> draft = (Map<String, Object>) infer.get("draft");
            Map<String, Object> compliance = complianceChecker.checkCompliance(draft);

            Set<String> applied = new LinkedHashSet<>();
            if (infer.get("appliedRules") instanceof List<?> inferRules) {
                inferRules.forEach(r -> applied.add(String.valueOf(r)));
            }
            if (compliance.get("appliedRules") instanceof List<?> compRules) {
                compRules.forEach(r -> applied.add(String.valueOf(r)));
            }
            applied.add("R-D01");
            applied.add("R-D02");
            applied.add("R-D04");

            boolean pass = Boolean.TRUE.equals(compliance.get("compliancePass"));
            double confidence = estimateExtractConfidence(slots, draft, pass);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", idx + 1);
            item.put("sourceExcerpt", slots.getOrDefault("sourceExcerpt", ""));
            item.put("draft", draft);
            item.put("inferredFields", infer.get("inferredFields"));
            item.put("issues", compliance.get("issues"));
            item.put("compliancePass", pass);
            item.put("status", pass ? "通过" : "待修正");
            item.put("confidence", confidence);
            item.put("needsConfirm", confidence < 0.75 || !pass);
            item.put("appliedRules", applied.stream().sorted().collect(Collectors.toList()));
            item.put("messageRootKey", draft == null ? null : draft.get("messageRootKey"));
            item.put("categoryName", draft == null ? null : draft.get("categoryName"));
            item.put("messagePreview", draft == null ? Map.of() : messageProjector.toMessage(draft));
            items.add(item);
        }

        List<Map<String, Object>> passed = items.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("compliancePass")))
                .collect(Collectors.toList());
        List<Map<String, Object>> confirmable = passed.stream()
                .filter(i -> !Boolean.TRUE.equals(i.get("needsConfirm")) || num(i.get("confidence"), 0) >= 0.75)
                .map(i -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("index", i.get("index"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> draft = (Map<String, Object>) i.get("draft");
                    row.put("offeringName", draft == null ? null : draft.get("offeringName"));
                    row.put("confidence", i.get("confidence"));
                    return row;
                }).collect(Collectors.toList());

        String scenarioId = null;
        if (!items.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstDraft = (Map<String, Object>) items.get(0).get("draft");
            String bizScenario = firstDraft == null ? null : str(firstDraft.get("bizScenario"));
            if (bizScenario != null && !bizScenario.isBlank()) {
                Map<String, Object> scenarioMeta = castMap(
                        castMap(graph.get("bizScenarios")).get(bizScenario));
                scenarioId = str(scenarioMeta.get("scenarioId"));
                if (scenarioId == null || scenarioId.isBlank()) {
                    scenarioId = bizScenario;
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", items.size());
        body.put("passedCount", passed.size());
        body.put("pendingCount", items.size() - passed.size());
        body.put("items", items);
        body.put("appliedRules", opsRules.ruleIds("batch").stream()
                .filter(id -> opsRules.isRuleEnabled(opsRules.batchRule(id)))
                .toList());
        body.put("confirmableDrafts", confirmable);
        body.put("scenario", scenarioId);
        body.put("extractEngine", extractEngine);
        return body;
    }

    /** 智读抽取置信度：关键字段齐全度 + 原文片段 + 合规结果。 */
    private double estimateExtractConfidence(Map<String, Object> slots, Map<String, Object> draft, boolean pass) {
        double score = 0.35;
        if (!empty(slots.get("sourceExcerpt")) || !empty(draft.get("sourceExcerpt"))) {
            score += 0.15;
        }
        String[] keys = {"offeringName", "offerName", "monthlyFee", "fixedFeeAmount", "targetUser", "channelScope"};
        int hit = 0;
        for (String k : keys) {
            if (!empty(draft.get(k)) || !empty(slots.get(k))) {
                hit++;
            }
        }
        score += Math.min(0.35, hit * 0.06);
        if (pass) {
            score += 0.15;
        }
        return Math.round(Math.min(0.99, score) * 100.0) / 100.0;
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        return objectMapper.convertValue(source, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private double num(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean empty(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
