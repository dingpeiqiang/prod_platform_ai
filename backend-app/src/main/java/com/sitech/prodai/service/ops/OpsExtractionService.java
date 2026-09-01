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
                String prompt = """
                        你是电信产商品文档解析助手。从营销/方案文档中抽取套餐列表，只输出 JSON：{"packages":[...]}
                        每个套餐可用字段：offeringName,monthlyFee,includeData,includeVoice,includeBroadband,targetUser,channelScope,bizScenario,offeringType,hasContract,contractMonths,repeatable,discountPercent,dependOn,sourceExcerpt
                        要求：sourceExcerpt 摘录原文短句；未写明的字段省略；不要编造。
                        
                        文档：
                        %s
                        """.formatted(documentText.length() > 6000 ? documentText.substring(0, 6000) : documentText);
                String content = llmService.orElseThrow().completePrompt(prompt);
                List<Map<String, Object>> pkgs = parsePackageList(content);
                if (!pkgs.isEmpty()) {
                    return new PackageExtractResult(pkgs, "llm");
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
            // 单段文档：有月费/套餐关键词时也尝试抽一条
            if (containsAny(documentText, "月费", "套餐", "元", "GB", "流量")) {
                segments.add(documentText.trim());
            }
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
     * P2-2 正则通用化：应用 {@code ops_rules.extraction.slotPatterns} 可配置补充模式。
     * 语义：内置正则优先（已抽取的槽位跳过），配置模式仅补充新变体，保证存量行为零漂移。
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
