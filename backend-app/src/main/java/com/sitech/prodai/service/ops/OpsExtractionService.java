package com.sitech.prodai.service.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.OpsRulesService;
import com.sitech.prodai.service.ProductExtractionTemplateSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 配置槽位 / 批量文档套餐抽取。
 * 优先 LLM；失败时回退正则切分。不灌入演示样例数据。
 * 商品别名与绑定触发词来自 {@link OpsRulesService}（ops_rules.extraction）。
 * <p>P2-2 模板化（§11.2）：① 槽位白名单经 {@link ProductExtractionTemplateSupport#extractableSlotKeys()}
 * 动态化（基础集 ∪ 模板 draft 字段）；② prompt 按品类模板动态拼装扩展字段约束；③
 * {@code ops_rules.extraction.slotPatterns} 提供内置正则外的可配置补充模式（内置优先，putIfAbsent 不漂移）。
 */
@Service
public class OpsExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OpsExtractionService.class);

    private static final Pattern FEE_PATTERN = Pattern.compile("月费\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern YUAN_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元");
    private static final Pattern BB_PATTERN = Pattern.compile("(\\d+)\\s*[Mm](?:宽带)?");
    private static final Pattern DATA_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[Gg][Bb]\\s*流量|含\\s*(\\d+(?:\\.\\d+)?)\\s*[Gg][Bb]");
    private static final Pattern VOICE_PATTERN = Pattern.compile("(\\d+)\\s*分钟");
    private static final Pattern DISCOUNT_PATTERN = Pattern.compile("折扣\\s*(\\d+(?:\\.\\d+)?)\\s*%");
    /** 匹配独立 5G，避免「5GB」误判为 5G 场景 */
    private static final Pattern FIVE_G_PATTERN = Pattern.compile("(?i)(?<!\\d)5g(?![0-9a-z])|5G套餐|5G个人");
    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?:叫|名称[是为]?)\\s*[「\"]?([^「」\"，。\\s]+)[」\"]?");
    private static final Pattern MONTHS_PATTERN = Pattern.compile("(\\d+)\\s*个?月");

    private final ObjectMapper objectMapper;
    private final ProdAiProperties properties;
    private final OpsRulesService opsRules;
    private final Optional<LlmService> llmService;
    private final ProductExtractionTemplateSupport templateSupport;

    public OpsExtractionService(ObjectMapper objectMapper,
                                ProdAiProperties properties,
                                OpsRulesService opsRules,
                                ProductExtractionTemplateSupport templateSupport,
                                @Autowired(required = false) Optional<LlmService> llmService) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.opsRules = opsRules;
        this.templateSupport = templateSupport;
        this.llmService = llmService == null ? Optional.empty() : llmService;
    }

    public record SlotExtractResult(Map<String, Object> slots, String engine) {}

    public record PackageExtractResult(List<Map<String, Object>> packages, String engine) {}

    /**
     * 工单/草稿修改意图抽取（模板驱动，P2-2 同构）：三步链路——
     * ① 品类识别：草稿已有 categoryCode 直接用（工单上下文最准）；无则按话术 matchers 识别；
     * ② 模板定位：按品类取激活模板的 draft 字段（field_code/label/别名/枚举值域）作为抽取白名单；
     * ③ LLM 约束抽取：只允许输出模板字段 ∪ 基础槽位，未提及不编造。
     * LLM 不可用/失败时返回空 Map（调用方走「未识别到修改字段」回执，不做正则兜底猜测）。
     *
     * @param text         用户修改话术（如「把资费名称改成家庭基础套餐198元」「月费改99」）
     * @param draft        当前草稿（提供 categoryCode/messageRootKey 上下文）
     * @param currentDraft 当前草稿关键字段值（供 LLM 对比判断「改了什么」，仅注入 prompt）
     * @return 抽取到的字段→新值（如 {offeringName: 家庭基础套餐198元, monthlyFee: 198}）；无法识别返回空
     */
    public Map<String, Object> extractUpdateIntent(String text, Map<String, Object> draft, Map<String, Object> currentDraft) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        String category = draft != null ? str(firstNonNull(draft.get("categoryCode"), draft.get("messageRootKey"))) : "";
        if (category == null || category.isBlank()) {
            category = templateSupport.matchCategory(text);
        }
        Set<String> allowedKeys = templateSupport.extractableSlotKeys();
        String templateSection = templateSupport.buildPromptSection(category);
        String currentSection = currentDraft == null || currentDraft.isEmpty() ? ""
                : "当前草稿字段值（用于对比理解用户要改什么，勿原样输出）：\n" + currentDraft.entrySet().stream()
                .limit(30)
                .map(e -> "- " + e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n")) + "\n";
        if (!llmExtractEnabled()) {
            return Map.of();
        }
        try {
            String prompt = """
                    你是电信产商品配置工单修改意图识别助手。从用户话术中抽取要修改的字段和新值，只输出 JSON 对象（字段=新值），不要 markdown。
                    可选字段（只能输出这些 field_code）：%s
                    %s%s规则：
                    - 只抽取话术中明确要修改的字段；未提及的字段不要编造
                    - 名称类变更输出完整新名称（如「资费名称改成家庭基础套餐198元」→ offeringName=家庭基础套餐198元）
                    - 金额类变更输出纯数字（如「月费改99」→ monthlyFee=99；名称里含「198元」视为月费联动 → monthlyFee=198）
                    - 枚举字段取值必须用模板列出的 display 值
                    %s
                    用户话术：
                    %s
                    """.formatted(String.join(",", allowedKeys), templateSection, currentSection, text);
            String content = llmService.orElseThrow().completePrompt(prompt);
            Map<String, Object> intent = parseJsonObject(content);
            if (intent == null) {
                return Map.of();
            }
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : intent.entrySet()) {
                if (allowedKeys.contains(e.getKey()) && e.getValue() != null
                        && !(e.getValue() instanceof String s && s.isBlank())) {
                    filtered.put(e.getKey(), e.getValue());
                }
            }
            return filtered;
        } catch (Exception e) {
            log.warn("[OpsExtractionService] 修改意图 LLM 抽取失败: {}", e.getMessage());
            return Map.of();
        }
    }

    public SlotExtractResult extractSlots(String text) {
        Map<String, Object> regexSlots = parseSlotsByRegex(text);
        if (!llmExtractEnabled() || text == null || text.isBlank()) {
            return new SlotExtractResult(regexSlots, "regex");
        }
        // 正则已抽到关键槽位时跳过 LLM，缩短研发助手对话配置首包时间
        if (regexSlots.containsKey("bizScenario")
                && (regexSlots.containsKey("monthlyFee")
                || regexSlots.containsKey("offeringName")
                || regexSlots.containsKey("targetUser")
                || regexSlots.containsKey("includeBroadband"))) {
            return new SlotExtractResult(regexSlots, "regex-fast");
        }
        try {
            String prompt = buildSlotPrompt(text);
            String content = llmService.orElseThrow().completePrompt(prompt);
            Map<String, Object> llmSlots = parseJsonObject(content);
            if (llmSlots == null || llmSlots.isEmpty()) {
                return new SlotExtractResult(regexSlots, "regex-fallback");
            }
            Map<String, Object> merged = new LinkedHashMap<>(regexSlots);
            Set<String> allowedKeys = templateSupport.extractableSlotKeys();
            for (Map.Entry<String, Object> e : llmSlots.entrySet()) {
                if (!allowedKeys.contains(e.getKey()) || e.getValue() == null) {
                    continue;
                }
                if (e.getValue() instanceof String s && s.isBlank()) {
                    if ("bindExistingMainPkg".equals(e.getKey()) || Boolean.TRUE.equals(llmSlots.get("clearBindExisting"))) {
                        merged.put(e.getKey(), "");
                    }
                    continue;
                }
                merged.put(e.getKey(), e.getValue());
            }
            return new SlotExtractResult(merged, "llm");
        } catch (Exception e) {
            log.warn("[OpsExtractionService] LLM 槽位抽取失败，回退正则: {}", e.getMessage());
            return new SlotExtractResult(regexSlots, "regex-fallback");
        }
    }

    /** LLM 单次抽取的文档分片上限（超过则分段抽取后合并） */
    private static final int LLM_DOC_CHUNK_SIZE = 6000;
    /** 分段抽取的最大分片数（防超长文档打爆 LLM） */
    private static final int LLM_DOC_MAX_CHUNKS = 5;

    /**
     * @param configFallback 保留兼容；当前不使用样例灌入。
     */
    public PackageExtractResult extractPackages(String documentText, List<Map<String, Object>> configFallback) {
        if (documentText == null || documentText.isBlank()) {
            return new PackageExtractResult(List.of(), "empty");
        }
        // 忽略历史样例回退参数，避免校园等演示数据顶替用户文档
        if (configFallback != null && !configFallback.isEmpty()) {
            log.debug("[OpsExtractionService] 忽略 extractFallbackPackages（{} 条），按文档内容抽取", configFallback.size());
        }
        if (llmExtractEnabled()) {
            try {
                List<Map<String, Object>> merged = new ArrayList<>();
                List<String> chunks = splitDocChunks(documentText, LLM_DOC_CHUNK_SIZE, LLM_DOC_MAX_CHUNKS);
                for (String chunk : chunks) {
                    String prompt = """
                            你是电信产商品文档解析助手。从营销/方案文档中抽取套餐列表，只输出 JSON：{"packages":[...]}
                            每个套餐可用字段：offeringName,monthlyFee,includeData,includeVoice,includeBroadband,targetUser,channelScope,bizScenario,offeringType,hasContract,contractMonths,repeatable,discountPercent,dependOn,sourceExcerpt
                            要求：sourceExcerpt 摘录原文短句；未写明的字段省略；不要编造。

                            文档：
                            %s
                            """.formatted(chunk);
                    String content = llmService.orElseThrow().completePrompt(prompt);
                    merged.addAll(parsePackageList(content));
                }
                if (!merged.isEmpty()) {
                    return new PackageExtractResult(dedupePackages(merged), "llm");
                }
            } catch (Exception e) {
                log.warn("[OpsExtractionService] LLM 文档抽取失败: {}", e.getMessage());
            }
        }
        List<Map<String, Object>> regexPkgs = parsePackagesByRegex(documentText);
        if (!regexPkgs.isEmpty()) {
            return new PackageExtractResult(regexPkgs, "regex");
        }
        // 不再灌入校园等演示样例；生产/联调均按文档内容抽取
        return new PackageExtractResult(List.of(), "none");
    }

    /**
     * 文档结构化抽取（Document IR 路径）：表格块表头→槽位映射直通（零 LLM 成本、字段不串行），
     * 段落/标题块走既有 LLM 分片 + 正则兜底链路。
     *
     * @param documentText   文档纯文本投影（IR 缺失时的兜底输入）
     * @param document       结构化文档 IR（{@code ConfigDocumentParser.ParseResult.document().toMap()}），
     *                       可为 null（旧链路/纯文本输入时走 {@link #extractPackages} 同构逻辑）
     * @param configFallback 保留兼容；当前不使用样例灌入。
     */
    public PackageExtractResult extractPackagesFromDocument(String documentText, Map<String, Object> document,
                                                            List<Map<String, Object>> configFallback) {
        List<Map<String, Object>> blocks = document == null ? List.of()
                : document.get("blocks") instanceof List<?> list ? castListOfMaps(list) : List.of();
        if (blocks.isEmpty()) {
            // IR 缺失/空：退回纯文本链路（行为同旧）
            return extractPackages(documentText, configFallback);
        }
        // ① 表格块直通：表头命中槽位词的表格逐行 map（多个 sheet/多表各自独立，不互相污染）
        List<Map<String, Object>> merged = new ArrayList<>();
        boolean tableDirect = false;
        for (Map<String, Object> block : blocks) {
            if (!"table".equals(str(block.get("type")))) {
                continue;
            }
            List<Map<String, Object>> rows = tableRowsToPackages(block);
            if (!rows.isEmpty()) {
                tableDirect = true;
                merged.addAll(rows);
            }
        }
        // ② 段落块（含无表头的文本）：拼段走既有 LLM 分片 + 正则兜底
        StringBuilder narrative = new StringBuilder();
        for (Map<String, Object> block : blocks) {
            String type = str(block.get("type"));
            if ("paragraph".equals(type) || "heading".equals(type)) {
                String text = str(block.get("text"));
                if (!text.isBlank()) {
                    if (narrative.length() > 0) {
                        narrative.append("\n\n");
                    }
                    narrative.append(text);
                }
            }
        }
        String narrativeText = narrative.toString().trim();
        if (!narrativeText.isBlank() && llmExtractEnabled()) {
            try {
                List<String> chunks = splitDocChunks(narrativeText, LLM_DOC_CHUNK_SIZE, LLM_DOC_MAX_CHUNKS);
                for (String chunk : chunks) {
                    String prompt = """
                            你是电信产商品文档解析助手。从营销/方案文档中抽取套餐列表，只输出 JSON：{"packages":[...]}
                            每个套餐可用字段：offeringName,monthlyFee,includeData,includeVoice,includeBroadband,targetUser,channelScope,bizScenario,offeringType,hasContract,contractMonths,repeatable,discountPercent,dependOn,sourceExcerpt
                            要求：sourceExcerpt 摘录原文短句；未写明的字段省略；不要编造。

                            文档：
                            %s
                            """.formatted(chunk);
                    String content = llmService.orElseThrow().completePrompt(prompt);
                    merged.addAll(parsePackageList(content));
                }
            } catch (Exception e) {
                log.warn("[OpsExtractionService] LLM 文档段落抽取失败: {}", e.getMessage());
            }
        }
        if (narrativeText.isBlank() && !tableDirect) {
            return new PackageExtractResult(List.of(), "empty");
        }
        if (merged.isEmpty()) {
            // 表格未命中槽位词、段落未抽出：正则兜底整段
            String fallbackText = !narrativeText.isBlank() ? narrativeText
                    : (documentText == null || documentText.isBlank() ? plainTextOf(blocks) : documentText);
            List<Map<String, Object>> regexPkgs = parsePackagesByRegex(fallbackText);
            if (!regexPkgs.isEmpty()) {
                return new PackageExtractResult(regexPkgs, "regex");
            }
            return new PackageExtractResult(List.of(), "none");
        }
        String engine = tableDirect && !llmExtractEnabled() ? "table-direct" : "document";
        return new PackageExtractResult(dedupePackages(merged), engine);
    }

    /** IR blocks 纯文本兜底投影（表格→TSV 行，段落原样），仅正则兜底时使用。 */
    private String plainTextOf(List<Map<String, Object>> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> block : blocks) {
            if ("table".equals(str(block.get("type")))) {
                List<Map<String, Object>> headers = castListOfMaps(
                        block.get("headers") instanceof List<?> h ? h : List.of());
                // headers/rows 为原始值列表（非 map），直接 join
                if (!headers.isEmpty()) {
                    sb.append(tsvJoin(headers.stream().map(m -> str(m.get("value"))).toList())).append('\n');
                }
                if (block.get("rows") instanceof List<?> rows) {
                    for (Object row : rows) {
                        if (row instanceof List<?> cells) {
                            sb.append(tsvJoin(cellsAsStrings(row))).append('\n');
                        }
                    }
                }
            } else {
                String text = str(block.get("text"));
                if (!text.isBlank()) {
                    sb.append(text).append("\n\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private String tsvJoin(List<String> cells) {
        return String.join("\t", cells);
    }

    private List<String> cellsAsStrings(Object row) {
        List<String> out = new ArrayList<>();
        if (row instanceof List<?> cells) {
            for (Object cell : cells) {
                out.add(str(cell));
            }
        }
        return out;
    }

    /**
     * 表格块 → 套餐行：表头列名命中槽位词映射（月费→monthlyFee 等），逐行组装；
     * 表头未命中任何槽位词（非套餐表）返回空列表（该表交由段落链路兜底，不硬猜）。
     */
    private List<Map<String, Object>> tableRowsToPackages(Map<String, Object> block) {
        Object headersObj = block.get("headers");
        Object rowsObj = block.get("rows");
        if (!(headersObj instanceof List<?> headerList) || headerList.isEmpty()
                || !(rowsObj instanceof List<?> rowList) || rowList.isEmpty()) {
            return List.of();
        }
        List<String> headers = headerList.stream().map(h -> str(h)).toList();
        // 表头 → 槽位键映射（列名含关键词即命中；同槽位多列时取首个）
        Map<Integer, String> colSlot = new LinkedHashMap<>();
        int hits = 0;
        for (int c = 0; c < headers.size(); c++) {
            String slot = headerSlotOf(headers.get(c));
            if (slot != null && colSlot.values().stream().noneMatch(s -> s.equals(slot))) {
                colSlot.put(c, slot);
                hits++;
            }
        }
        if (hits < 2) {
            // 少于 2 列命中视为非套餐表（如纯说明表），不硬猜
            return List.of();
        }
        String sheetName = str(block.get("sheet_name"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object rowObj : rowList) {
            if (!(rowObj instanceof List<?> rawRow)) {
                continue;
            }
            List<String> row = rawRow.stream().map(v -> str(v)).toList();
            Map<String, Object> pkg = new LinkedHashMap<>();
            colSlot.forEach((col, slot) -> {
                if (col < row.size()) {
                    Object value = slotValueOf(slot, row.get(col));
                    if (value != null) {
                        pkg.put(slot, value);
                    }
                }
            });
            if (pkg.isEmpty() || !pkg.containsKey("offeringName") && !pkg.containsKey("monthlyFee")) {
                continue;
            }
            // 场景语义按行内容兜底识别（表格行信息少，正则场景词可命中）
            Map<String, Object> inferred = parseSlotsByRegex(String.join(" ", row));
            inferred.forEach(pkg::putIfAbsent);
            pkg.putIfAbsent("sourceExcerpt", tableExcerpt(block, row));
            out.add(pkg);
        }
        return out;
    }

    /** 列名 → 槽位键（关键词包含式匹配）。 */
    private String headerSlotOf(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String h = header.trim();
        if (containsAny(h, "套餐名", "名称", "资费名")) {
            return "offeringName";
        }
        if (h.contains("月费") || (h.contains("费") && h.contains("元"))) {
            return "monthlyFee";
        }
        if (h.contains("流量") || h.contains("GB") || h.contains("gb")) {
            return "includeData";
        }
        if (h.contains("语音") || h.contains("分钟")) {
            return "includeVoice";
        }
        if (h.contains("宽带")) {
            return "includeBroadband";
        }
        if (h.contains("客群") || h.contains("目标用户") || h.contains("目标客户")) {
            return "targetUser";
        }
        if (h.contains("渠道")) {
            return "channelScope";
        }
        if (h.contains("折扣")) {
            return "discountPercent";
        }
        if (h.contains("合约")) {
            return "hasContract";
        }
        return null;
    }

    /** 单元格值 → 槽位值（金额转数字、流量/宽带补单位、布尔语义）。 */
    private Object slotValueOf(String slot, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return null;
        }
        switch (slot) {
            case "monthlyFee", "discountPercent" -> {
                String digits = value.replaceAll("[^0-9.]", "");
                if (digits.isBlank()) {
                    return null;
                }
                try {
                    return Double.parseDouble(digits);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case "includeData" -> {
                return value.matches(".*\\d.*") ? value.replace(" ", "")
                        : value.matches("\\d+") ? value + "GB" : value;
            }
            case "includeBroadband" -> {
                return value.matches("\\d+") ? value + "M" : value.replace(" ", "");
            }
            case "includeVoice" -> {
                return value.matches("\\d+") ? value + "分钟" : value.replace(" ", "");
            }
            default -> {
                return value;
            }
        }
    }

    /** 表格行原文摘录：sheet 名 + 首列值 + 行内容（≤120 字）。 */
    private String tableExcerpt(Map<String, Object> block, List<String> row) {
        String sheet = str(block.get("sheet_name"));
        String title = str(block.get("title"));
        String scope = !sheet.isBlank() ? "工作表：" + sheet : (!title.isBlank() ? title : "");
        String line = String.join("｜", row.stream().filter(v -> !v.isBlank()).toList());
        String text = (scope.isBlank() ? "" : scope + "｜") + line;
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }

    /**
     * 超长文档分段：优先按空行边界切分（避免截断套餐段落），无空行时硬切。
     * 超过 maxChunks 时截断尾部（与既有 6000 字上限策略一致，防打爆 LLM）。
     */
    private List<String> splitDocChunks(String text, int chunkSize, int maxChunks) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length() && chunks.size() < maxChunks) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                // 向前回退到最近的空行/换行边界，避免段落被拦腰截断
                int boundary = Math.max(text.lastIndexOf("\n\n", end), text.lastIndexOf("\n", end));
                if (boundary > start + chunkSize / 2) {
                    end = boundary + 1;
                }
            }
            chunks.add(text.substring(start, (int) Math.min(end, text.length())).trim());
            start = end;
        }
        return chunks;
    }

    /** 分段抽取合并去重：按 offeringName+monthlyFee 去重，保留首现（分段边界可能重复抽到同一套餐）。 */
    private List<Map<String, Object>> dedupePackages(List<Map<String, Object>> pkgs) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (Map<String, Object> p : pkgs) {
            String key = str(p.get("offeringName")) + "|" + str(p.get("monthlyFee"));
            if (!seen.add(key)) {
                continue;
            }
            out.add(p);
        }
        return out;
    }

    /**
     * 按「套餐A/1/一：…」等段落切分，并用槽位正则填充字段。
     */
    private List<Map<String, Object>> parsePackagesByRegex(String documentText) {
        if (documentText == null || documentText.isBlank()) {
            return List.of();
        }
        Pattern split = Pattern.compile(
                "(?:^|[\\n；;])\\s*(?:套餐\\s*[A-Za-z0-9一二三四五六七八九十]+|[A-Za-z]\\s*[、.．]|\\d+\\s*[、.．])\\s*[:：]?"
        );
        Matcher m = split.matcher(documentText);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }
        List<String> segments = new ArrayList<>();
        if (starts.isEmpty()) {
            // 单段文档增强：先按空行分组尝试切分（表格/TSV/无前缀文档），每行能独立抽出槽位则按行成段
            segments.addAll(splitSegmentsWithoutPrefix(documentText));
        } else {
            for (int i = 0; i < starts.size(); i++) {
                int from = starts.get(i);
                int to = i + 1 < starts.size() ? starts.get(i + 1) : documentText.length();
                String seg = documentText.substring(from, to).replaceFirst("^[\\n；;]+", "").trim();
                if (!seg.isBlank()) {
                    segments.add(seg);
                }
            }
        }
        List<Map<String, Object>> pkgs = new ArrayList<>();
        for (String seg : segments) {
            Map<String, Object> slots = parseSlotsByRegex(seg);
            if (slots.isEmpty()) {
                continue;
            }
            slots.putIfAbsent("sourceExcerpt", seg.length() > 120 ? seg.substring(0, 120) + "…" : seg);
            if (!slots.containsKey("offeringName")) {
                Matcher nameInSeg = Pattern.compile("套餐\\s*([A-Za-z0-9一二三四五六七八九十]+)[：:]\\s*([^；;，,。\\n]{2,30})")
                        .matcher(seg);
                if (nameInSeg.find()) {
                    slots.put("offeringName", nameInSeg.group(2).trim());
                }
            }
            pkgs.add(slots);
        }
        return pkgs;
    }

    /**
     * 无「套餐X：」前缀的单段文档切分：按行（优先空行分组）尝试独立抽取，
     * 每行/组能抽出槽位即成一段；全部失败时回退整篇一段（保持既有行为零漂移）。
     * <p>TSV 表格直通已退役（去旧留新）：CSV/XLSX 产物现走 Document IR 表格直通
     * （{@link #extractPackagesFromDocument}），此处仅剩纯文本无前缀段落兜底。
     */
    private List<String> splitSegmentsWithoutPrefix(String documentText) {
        if (!containsAny(documentText, "月费", "套餐", "元", "GB", "流量")) {
            return List.of();
        }
        String[] lines = documentText.split("\\r?\\n");
        // 先按空行分组（表格类文档通常每套餐一组）
        List<String> groups = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : lines) {
            if (line.isBlank()) {
                if (!cur.isEmpty()) {
                    groups.add(cur.toString().trim());
                    cur.setLength(0);
                }
            } else {
                if (!cur.isEmpty()) {
                    cur.append('\n');
                }
                cur.append(line.trim());
            }
        }
        if (!cur.isEmpty()) {
            groups.add(cur.toString().trim());
        }
        // 组内能独立抽出槽位则成段
        List<String> segments = new ArrayList<>();
        for (String g : groups) {
            if (g.isBlank()) {
                continue;
            }
            Map<String, Object> slots = parseSlotsByRegex(g);
            if (!slots.isEmpty()) {
                segments.add(g);
            }
        }
        if (!segments.isEmpty()) {
            return segments;
        }
        // 空行分组失败（如 TSV 单行表）：逐行尝试
        for (String line : lines) {
            String t = line.trim();
            if (t.isBlank()) {
                continue;
            }
            if (!parseSlotsByRegex(t).isEmpty()) {
                segments.add(t);
            }
        }
        if (segments.size() > 1) {
            return segments;
        }
        // 全部失败回退整篇一段（既有行为）
        return List.of(documentText.trim());
    }

    public Map<String, Object> parseSlotsByRegex(String text) {
        Map<String, Object> slots = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return slots;
        }

        boolean isFamily = containsAny(text, "家庭融合", "家庭用户", "融合套餐", "目标家庭", "家庭体验", "家庭加装");
        boolean isAddon = containsAny(text, "加装", "附加包", "附加资费")
                || (text.contains("流量包") && !containsAny(text, "主套餐", "融合畅享"));
        if (isFamily) {
            slots.put("bizScenario", "家庭融合");
            slots.put("targetUser", "家庭");
            slots.put("offeringType", isAddon ? "addon" : "fusion");
        } else if (containsAny(text, "校园", "大学生", "迎新")) {
            slots.put("bizScenario", "校园体验");
            slots.put("targetUser", "校园");
            slots.put("offeringType", isAddon ? "addon" : "main_pkg");
        } else if (FIVE_G_PATTERN.matcher(text).find()) {
            slots.put("bizScenario", "5G个人主套餐");
            slots.put("targetUser", "个人");
            slots.put("offeringType", isAddon ? "addon" : "main_pkg");
        } else if (isAddon) {
            slots.put("offeringType", "addon");
        }

        Matcher feeM = FEE_PATTERN.matcher(text);
        if (feeM.find()) {
            slots.put("monthlyFee", Double.parseDouble(feeM.group(1)));
        } else {
            Matcher yuanM = YUAN_PATTERN.matcher(text);
            if (yuanM.find()) {
                slots.put("monthlyFee", Double.parseDouble(yuanM.group(1)));
            }
        }

        // 配置模式先行：ops_rules.extraction.slotPatterns 声明的模式优先于内置正则
        // （配置可覆盖内置行为——「去旧留新」：内置正则降级为出厂缺省，运维改 JSON 即改行为）
        applyConfiguredSlotPatterns(text, slots);

        Matcher dataM = DATA_PATTERN.matcher(text);
        if (dataM.find()) {
            String gb = dataM.group(1) != null ? dataM.group(1) : dataM.group(2);
            slots.put("includeData", gb + "GB");
        }
        Matcher voiceM = VOICE_PATTERN.matcher(text);
        if (voiceM.find()) {
            slots.put("includeVoice", voiceM.group(1) + "分钟");
        }

        Matcher bbM = BB_PATTERN.matcher(text);
        if (bbM.find() && (text.contains("宽带") || text.contains("家庭"))) {
            slots.put("includeBroadband", bbM.group(1) + "M");
        }

        if (text.contains("全渠道")) {
            slots.put("channelScope", "全渠道");
        } else if (text.contains("电渠") && text.contains("厅店")) {
            slots.put("channelScope", "电渠+厅店");
        } else if (text.contains("电渠")) {
            slots.put("channelScope", "仅电渠");
        }

        Matcher nameM = NAME_PATTERN.matcher(text);
        if (nameM.find()) {
            slots.put("offeringName", nameM.group(1));
        } else if (text.contains("家庭融合畅享158")) {
            slots.put("offeringName", "家庭融合畅享158");
        }

        List<String> clearTriggers = opsRules.extractionTriggers("clearBindTriggers");
        List<String> bindTriggers = opsRules.extractionTriggers("bindTriggers");
        if (containsAny(text, clearTriggers.toArray(String[]::new))) {
            slots.put("bindExistingMainPkg", "");
            slots.put("clearBindExisting", true);
        } else if (containsAny(text, bindTriggers.toArray(String[]::new))) {
            // 仅在明确「再绑/一起上」等触发词时绑定，避免套餐名含「家庭融合畅享」误绑在架商品
            String bindId = opsRules.resolveAliasOfferingId(text);
            if (bindId != null) {
                slots.put("bindExistingMainPkg", bindId);
            }
        }

        if (text.contains("无合约") || text.contains("没有合约")) {
            slots.put("hasContract", "0");
        }
        if (text.contains("有合约") || text.contains("协议期") || text.contains("补协议")) {
            slots.put("hasContract", "1");
            Matcher monthsM = MONTHS_PATTERN.matcher(text);
            if (monthsM.find()) {
                slots.put("contractMonths", Integer.parseInt(monthsM.group(1)));
            }
        }
        if (text.contains("可重复")) {
            slots.put("repeatable", "true");
        }
        if (text.contains("不可重复") || text.contains("不能重复")) {
            slots.put("repeatable", "false");
        }
        if (text.contains("0元") || text.contains("零元")) {
            slots.put("monthlyFee", 0);
        }
        Matcher discountM = DISCOUNT_PATTERN.matcher(text);
        if (discountM.find()) {
            slots.put("discountPercent", Double.parseDouble(discountM.group(1)));
        }
        if (text.contains("依赖宽带")) {
            slots.put("dependOn", "宽带");
        } else if (text.contains("依赖")) {
            Matcher depM = Pattern.compile("依赖\\s*([^；;，,。\\n]{1,20})").matcher(text);
            if (depM.find()) {
                slots.put("dependOn", depM.group(1).trim());
            }
        }
        if (text.contains("内部验证")) {
            slots.put("channelScope", "内部验证");
        }
        // 尾部补漏：内置正则执行后配置模式再次补漏（首部已命中的槽位此轮自然跳过）
        applyConfiguredSlotPatterns(text, slots);
        return slots;
    }

    /**
     * P2-2 prompt 动态拼装：固定场景规则（inferFields 依赖的场景槽位语义）+
     * 品类模板扩展字段约束段（matchCategory 兜底选模板，§9.4）。
     */
    private String buildSlotPrompt(String text) {
        String templateSection = templateSupport.buildPromptSection(templateSupport.matchCategory(text));
        return """
                你是电信产商品配置槽位抽取助手。从用户话术中抽取字段，只输出 JSON 对象，不要 markdown。
                可选字段：%s
                规则：
                - 家庭融合/融合套餐 → bizScenario=家庭融合, targetUser=家庭, offeringType=fusion
                - 校园/大学生 → bizScenario=校园体验, targetUser=校园
                - 5G → bizScenario=5G个人主套餐, targetUser=个人, offeringType=main_pkg
                - 明确不加/不绑 128 → clearBindExisting=true, bindExistingMainPkg=""
                - 有合约/无合约 → hasContract 为 "1"/"0"
                - 未提及的字段不要编造
                %s
                用户话术：
                %s
                """.formatted(String.join(",", templateSupport.extractableSlotKeys()), templateSection, text);
    }

    /**
     * P2-2 正则通用化：应用 {@code ops_rules.extraction.slotPatterns} 可配置模式。
     * 语义（升级）：配置模式在最早期执行——命中即写入槽位，内置正则对已写槽位让位
     * （putIfAbsent 语义）；这使配置可覆盖内置行为（出厂缺省在代码，调整在 JSON）。
     */
    private void applyConfiguredSlotPatterns(String text, Map<String, Object> slots) {
        for (Map.Entry<String, List<Map<String, Object>>> entry : opsRules.extractionSlotPatterns().entrySet()) {
            String slot = entry.getKey();
            if (slots.containsKey(slot) || entry.getValue() == null) {
                continue;
            }
            for (Map<String, Object> spec : entry.getValue()) {
                Object value = matchConfiguredPattern(text, spec);
                if (value != null) {
                    slots.put(slot, value);
                    break;
                }
            }
        }
    }

    /** 单条配置模式执行：guard 前置检查 + 正则捕获组 + {v} 模板格式化；pattern 非法跳过。 */
    private Object matchConfiguredPattern(String text, Map<String, Object> spec) {
        Object guard = spec.get("guard");
        if (guard != null && !text.contains(String.valueOf(guard))) {
            return null;
        }
        Object patternObj = spec.get("pattern");
        if (patternObj == null) {
            return null;
        }
        try {
            Matcher m = Pattern.compile(String.valueOf(patternObj)).matcher(text);
            if (!m.find()) {
                return null;
            }
            int group = spec.get("group") instanceof Number n ? n.intValue() : 1;
            String raw = m.groupCount() >= group ? m.group(group) : null;
            if (raw == null) {
                return null;
            }
            String template = spec.get("template") != null ? String.valueOf(spec.get("template")) : "{v}";
            return template.replace("{v}", raw);
        } catch (Exception e) {
            log.warn("[OpsExtractionService] slotPatterns 配置非法，跳过: {}", e.getMessage());
            return null;
        }
    }

    private boolean llmExtractEnabled() {
        return properties.getOntology().isLlmExtractEnabled()
                && properties.getLlm().isEnabled()
                && llmService.isPresent();
    }

    private Map<String, Object> parseJsonObject(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return null;
        }
        int start = llmOutput.indexOf('{');
        int end = llmOutput.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readValue(llmOutput.substring(start, end + 1), new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("[OpsExtractionService] JSON 对象解析失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parsePackageList(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return List.of();
        }
        int objStart = llmOutput.indexOf('{');
        int arrStart = llmOutput.indexOf('[');
        try {
            if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
                int end = llmOutput.lastIndexOf('}');
                Map<String, Object> root = objectMapper.readValue(
                        llmOutput.substring(objStart, end + 1), new TypeReference<>() {});
                Object pkgs = root.get("packages");
                if (pkgs instanceof List<?> list) {
                    return castListOfMaps(list);
                }
            }
            if (arrStart >= 0) {
                int end = llmOutput.lastIndexOf(']');
                List<?> list = objectMapper.readValue(
                        llmOutput.substring(arrStart, end + 1), new TypeReference<>() {});
                return castListOfMaps(list);
            }
        } catch (Exception e) {
            log.debug("[OpsExtractionService] 套餐列表解析失败: {}", e.getMessage());
        }
        return List.of();
    }

    private List<Map<String, Object>> castListOfMaps(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> row = new LinkedHashMap<>();
                m.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }

    private boolean containsAny(String text, String... keys) {
        if (keys == null || keys.length == 0) {
            return false;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank() && text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
