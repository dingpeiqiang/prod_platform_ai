package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.service.ProductExtractionTemplateSupport;
import com.sitech.prodai.service.ProductTemplateRegistry;
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
 * 产商品研发 - 业务参数抽取原子工具（智聊链路环节①）。
 * <p>
 * 从话术抽取结构化槽位（月费/客群/宽带/渠道/场景等），薄封装
 * {@link OpsExtractionService#extractSlots}（regex 配置模式快抽 → LLM 补抽，引擎与
 * 槽位白名单均为配置/模板驱动，工具本身零业务硬编码）。
 * <p>
 * 缺要素透明化：按激活模板 {@code required_slots} 声明（缺省取基础要素集）对比已抽槽位，
 * 产出 missing_slots 供下游澄清与前端 sop-step 展示（护栏 fallback「提示补充关键要素」的数据依据）。
 * 只做参数抽取环节，不做品类识别与草稿生成（职责拆分：一个工具一个环节）。
 */
@Component
public class RdSlotExtractTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdSlotExtractTool.class);

    /** 基础要素集：模板未声明 required_slots 时的缺省判定口径（对话配置最小可用要素）。 */
    private static final Set<String> DEFAULT_REQUIRED_SLOTS = Set.of("monthlyFee", "targetUser");

    private final OpsExtractionService extractionService;
    private final ProductExtractionTemplateSupport templateSupport;
    private final ProductTemplateRegistry templateRegistry;

    public RdSlotExtractTool(OpsExtractionService extractionService,
                             ProductExtractionTemplateSupport templateSupport,
                             ProductTemplateRegistry templateRegistry) {
        this.extractionService = extractionService;
        this.templateSupport = templateSupport;
        this.templateRegistry = templateRegistry;
    }

    @Override
    public String getName() {
        return "rd_slot_extract";
    }

    @Override
    public String getDescription() {
        return "从用户配置需求话术中抽取结构化业务参数（月费/客群/宽带/渠道/场景等），输出槽位明细与缺失要素";
    }

    @Override
    public String getLabel() {
        return "业务参数抽取";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    /** 参数抽取的典型业务链：品类识别先行（决定抽取模板与 required_slots 口径）→ 草稿生成。 */
    @Override
    public List<String> getHandoffs() {
        return List.of("rd_draft_generate", "rd_category_resolve");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("text")
                        .label("配置需求")
                        .description("用户对产商品配置的自然语言描述")
                        .required()
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("category_code")
                        .label("品类编码")
                        .description("可选，已识别的品类码；提供后按该模板 required_slots 判定缺要素")
                        .type("string")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("抽取摘要").type("string")
                        .description("抽到的槽位逐项摘要与缺失要素提示").build(),
                ToolOutputField.builder("slots", ToolOutputField.Role.OTHER)
                        .label("业务参数").type("object")
                        .description("抽到的结构化槽位（键=槽位名，值=话术值）").build(),
                ToolOutputField.builder("slot_engine", ToolOutputField.Role.OTHER)
                        .label("抽取引擎").type("string")
                        .description("实际生效的抽取引擎（regex/regex-fast/llm/regex-fallback）").build(),
                ToolOutputField.builder("missing_slots", ToolOutputField.Role.ITEMS)
                        .label("缺失要素").type("list")
                        .description("按模板 required_slots 声明判定话术未提供的要素").build(),
                ToolOutputField.builder("slot_count", ToolOutputField.Role.COUNT)
                        .label("抽取数量").type("number")
                        .description("抽到的槽位个数").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String text = params != null ? String.valueOf(params.getOrDefault("text", "")) : "";
        String categoryCode = params != null ? String.valueOf(params.getOrDefault("category_code", "")).trim() : "";
        log.info("[AgentTool] rd_slot_extract 执行: text={}, categoryCode={}", text, categoryCode);
        if (text == null || text.isBlank() || "null".equals(text)) {
            return ExecutionResult.fail(getName(), "缺少配置需求描述");
        }
        try {
            OpsExtractionService.SlotExtractResult result = extractionService.extractSlots(text);
            Map<String, Object> slots = result.slots() == null ? Map.of() : result.slots();
            String category = categoryCode.isBlank() ? templateSupport.matchCategory(text) : categoryCode;
            List<String> missing = missingSlots(category, slots);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("nl_answer", summarize(slots, result.engine(), missing));
            out.put("slots", slots);
            out.put("slot_engine", result.engine());
            out.put("missing_slots", missing);
            out.put("slot_count", slots.size());
            return ExecutionResult.ok(getName(), out);
        } catch (Exception e) {
            log.error("[AgentTool] rd_slot_extract 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "业务参数抽取失败: " + e.getMessage());
        }
    }

    /** 缺要素判定：模板 required_slots 声明优先（子模板继承合并后可直接取），未声明取基础要素集。 */
    private List<String> missingSlots(String category, Map<String, Object> slots) {
        Set<String> required = requiredSlots(category);
        List<String> missing = new ArrayList<>();
        for (String key : required) {
            if (emptySlotValue(slots.get(key))) {
                missing.add(key);
            }
        }
        return missing;
    }

    private Set<String> requiredSlots(String category) {
        if (category != null && !category.isBlank()) {
            Set<String> declared = templateSupport.requiredSlots(category);
            if (!declared.isEmpty()) {
                return declared;
            }
        }
        return DEFAULT_REQUIRED_SLOTS;
    }

    /** 槽位为空值判定（null/空白串/"null" 视为未提供）。 */
    private boolean emptySlotValue(Object value) {
        if (value == null) {
            return true;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s);
    }

    /** 抽取摘要：逐项「槽位=值」列出，缺要素追加补充提示（不静默，护栏 fallback 语义）。 */
    private String summarize(Map<String, Object> slots, String engine, List<String> missing) {
        if (slots.isEmpty()) {
            return "未从话术中抽取到配置要素，请补充月费/客群等关键信息";
        }
        StringBuilder sb = new StringBuilder("已抽取业务参数（引擎 ").append(engine).append("）：");
        List<String> parts = new ArrayList<>();
        slots.forEach((k, v) -> parts.add(k + "=" + v));
        sb.append(String.join("，", parts));
        if (!missing.isEmpty()) {
            sb.append("；话术未提及：").append(String.join("、", missing))
                    .append("（将由模板缺省补全，可补充说明）");
        }
        return sb.toString();
    }
}
