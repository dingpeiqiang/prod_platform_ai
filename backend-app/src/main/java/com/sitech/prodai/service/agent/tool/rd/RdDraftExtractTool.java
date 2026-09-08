package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.service.ConfigMessageProjector;
import com.sitech.prodai.service.OpsRulesService;
import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.TemplateDeriveEngine;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolOutputField;
import com.sitech.prodai.service.agent.tool.ToolParam;
import com.sitech.prodai.service.ops.OpsExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 产商品研发 - 套餐抽取原子工具（智读链路环节②）。
 * <p>
 * 从文档文本逐套餐抽取配置要素并派生为配置草稿（LLM 优先、正则兜底；模板 derive 引擎产出草稿）。
 * 只做抽取+派生环节，不做合规校验与开单（职责拆分：一个工具一个环节）。
 */
@Component
public class RdDraftExtractTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdDraftExtractTool.class);

    private final ProductOntologyService productOntologyService;
    private final OpsExtractionService extractionService;
    private final TemplateDeriveEngine deriveEngine;
    private final ConfigMessageProjector messageProjector;
    private final OpsRulesService opsRules;

    public RdDraftExtractTool(ProductOntologyService productOntologyService,
                              OpsExtractionService extractionService,
                              TemplateDeriveEngine deriveEngine,
                              ConfigMessageProjector messageProjector,
                              OpsRulesService opsRules) {
        this.productOntologyService = productOntologyService;
        this.extractionService = extractionService;
        this.deriveEngine = deriveEngine;
        this.messageProjector = messageProjector;
        this.opsRules = opsRules;
    }

    @Override
    public String getName() {
        return "rd_draft_extract";
    }

    @Override
    public String getDescription() {
        return "从方案文档文本逐套餐抽取配置要素并派生为配置草稿（名称/月费/要素/客群/渠道），不做合规校验与开单";
    }

    @Override
    public String getLabel() {
        return "套餐抽取";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    /** 套餐抽取后的典型业务链：草稿批量落库开单（智读链路下一环节）。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("rd_workorder_create");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("document_text")
                        .label("文档内容")
                        .description("方案文档的文本内容（上一步文档解析的产出）")
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("document")
                        .label("结构化文档")
                        .description("上一步文档解析产出的结构化文档 IR（blocks：heading/paragraph/table）；有值时表格块表头映射直通、段落块 LLM 抽取")
                        .type("object")
                        .build(),
                ToolParam.builder("product_type")
                        .label("产品品类")
                        .description("可选，产品品类码（category_code，如 familyBasePrc）；未提供时由模板 matchers 兜底识别")
                        .type("string")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("抽取摘要").type("string")
                        .description("本次套餐抽取的结果摘要").build(),
                ToolOutputField.builder("items", ToolOutputField.Role.ITEMS)
                        .label("配置草稿").type("list")
                        .description("抽取出的配置草稿清单（逐条含 draft/issues/compliancePass）").build(),
                ToolOutputField.builder("extractEngine", ToolOutputField.Role.OTHER)
                        .label("抽取引擎").type("string").build(),
                ToolOutputField.builder("total", ToolOutputField.Role.COUNT)
                        .label("抽取条数").type("number").build(),
                ToolOutputField.builder("passedCount", ToolOutputField.Role.COUNT)
                        .label("通过条数").type("number").build(),
                ToolOutputField.builder("pendingCount", ToolOutputField.Role.COUNT)
                        .label("待修正条数").type("number").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String docText = params != null ? String.valueOf(params.getOrDefault("document_text", "")) : "";
        Object document = params != null ? params.get("document") : null;
        boolean hasDocument = document instanceof Map<?, ?> doc && !((Map<?, ?>) doc).isEmpty();
        log.info("[AgentTool] rd_draft_extract 执行: hasDocText={}, docLen={}, hasDocumentIR={}",
                !docText.isBlank(), docText.length(), hasDocument);
        if ((docText == null || docText.isBlank() || "null".equals(docText)) && !hasDocument) {
            return ExecutionResult.fail(getName(), "缺少文档文本（需上一步文档解析产出）");
        }
        try {
            // 抽取：结构化文档 IR 优先（表格直通 + 段落链路）；无 IR 走纯文本链路（LLM 优先分段，正则兜底）
            OpsExtractionService.PackageExtractResult extracted = hasDocument
                    ? extractionService.extractPackagesFromDocument(docText, (Map<String, Object>) document, List.of())
                    : extractionService.extractPackages(docText, List.of());
            List<Map<String, Object>> pkgs = extracted.packages();
            String extractEngine = extracted.engine();

            // 派生 + 合规预判（compliancePass 随 item 下发供开单环节挂标签；合规明细由 rd_compliance 环节透出）
            Map<String, Object> graph = productOntologyService.loadGraph();
            List<Map<String, Object>> items = new ArrayList<>();
            int passed = 0;
            for (int idx = 0; idx < pkgs.size(); idx++) {
                Map<String, Object> slots = new LinkedHashMap<>(pkgs.get(idx));
                Map<String, Object> infer = deriveEngine.derive(slots, null, graph);
                @SuppressWarnings("unchecked")
                Map<String, Object> draft = (Map<String, Object>) infer.get("draft");
                Map<String, Object> compliance = productOntologyService.checkCompliance(draft);
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
                if (pass) {
                    passed++;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", idx + 1);
                item.put("sourceExcerpt", slots.getOrDefault("sourceExcerpt", ""));
                item.put("draft", draft);
                item.put("inferredFields", infer.get("inferredFields"));
                item.put("issues", compliance.get("issues"));
                item.put("compliancePass", pass);
                item.put("status", pass ? "通过" : "待修正");
                item.put("appliedRules", applied.stream().sorted().toList());
                item.put("messageRootKey", draft == null ? null : draft.get("messageRootKey"));
                item.put("categoryName", draft == null ? null : draft.get("categoryName"));
                item.put("messagePreview", draft == null ? Map.of() : messageProjector.toMessage(draft));
                items.add(item);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            String summary = "已抽取 " + items.size() + " 条套餐草稿"
                    + "（通过 " + passed + "，待修正 " + (items.size() - passed) + "）";
            if (pkgs.isEmpty()) {
                summary = "未从文档识别到套餐要素（抽取引擎 " + extractEngine + "）";
            }
            out.put("nl_answer", summary);
            out.put("items", items);
            out.put("total", items.size());
            out.put("passedCount", passed);
            out.put("pendingCount", items.size() - passed);
            out.put("extractEngine", extractEngine);
            out.put("appliedRules", opsRules.ruleIds("batch").stream()
                    .filter(id -> opsRules.isRuleEnabled(opsRules.batchRule(id)))
                    .toList());
            if (!items.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> firstDraft = (Map<String, Object>) items.get(0).get("draft");
                String bizScenario = firstDraft == null ? null : str(firstDraft.get("bizScenario"));
                if (bizScenario != null && !bizScenario.isBlank()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> scenarioMeta = castMap(
                            castMap(graph.get("bizScenarios")).get(bizScenario));
                    String scenarioId = str(scenarioMeta.get("scenarioId"));
                    out.put("scenario", scenarioId == null || scenarioId.isBlank() ? bizScenario : scenarioId);
                }
            }
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] rd_draft_extract 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "套餐抽取失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
