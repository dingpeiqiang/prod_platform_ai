package com.sitech.prodai.service.ops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.OpsRulesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置槽位 / 批量文档套餐抽取。
 * 优先 LLM 结构化 JSON；失败或关闭时回退正则 /（仅 demo）样例包。
 * 商品别名与绑定触发词来自 {@link OpsRulesService}（ops_rules.extraction）。
 */
@Service
public class OpsExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OpsExtractionService.class);

    private static final Pattern FEE_PATTERN = Pattern.compile("月费\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern YUAN_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元");
    private static final Pattern BB_PATTERN = Pattern.compile("(\\d+)\\s*[Mm](?:宽带)?");
    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?:叫|名称[是为]?)\\s*[「\"]?([^「」\"，。\\s]+)[」\"]?");
    private static final Pattern MONTHS_PATTERN = Pattern.compile("(\\d+)\\s*个?月");

    private static final Set<String> SLOT_KEYS = Set.of(
            "bizScenario", "targetUser", "offeringType", "monthlyFee", "oneTimeFee",
            "includeBroadband", "includeData", "includeVoice", "channelScope",
            "offeringName", "bindExistingMainPkg", "clearBindExisting",
            "hasContract", "contractMonths", "repeatable", "dependOn", "discountPercent"
    );

    private final ObjectMapper objectMapper;
    private final ProdAiProperties properties;
    private final OpsRulesService opsRules;
    private final Optional<LlmService> llmService;

    public OpsExtractionService(ObjectMapper objectMapper,
                                ProdAiProperties properties,
                                OpsRulesService opsRules,
                                @Autowired(required = false) Optional<LlmService> llmService) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.opsRules = opsRules;
        this.llmService = llmService == null ? Optional.empty() : llmService;
    }

    public record SlotExtractResult(Map<String, Object> slots, String engine) {}

    public record PackageExtractResult(List<Map<String, Object>> packages, String engine) {}

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
            String prompt = """
                    你是电信产商品配置槽位抽取助手。从用户话术中抽取字段，只输出 JSON 对象，不要 markdown。
                    可选字段：bizScenario,targetUser,offeringType,monthlyFee,oneTimeFee,includeBroadband,includeData,includeVoice,channelScope,offeringName,bindExistingMainPkg,clearBindExisting,hasContract,contractMonths,repeatable,dependOn,discountPercent
                    规则：
                    - 家庭融合/融合套餐 → bizScenario=家庭融合, targetUser=家庭, offeringType=fusion
                    - 校园/大学生 → bizScenario=校园体验, targetUser=校园
                    - 5G → bizScenario=5G个人主套餐, targetUser=个人, offeringType=main_pkg
                    - 明确不加/不绑 128 → clearBindExisting=true, bindExistingMainPkg=""
                    - 有合约/无合约 → hasContract 为 "1"/"0"
                    - 未提及的字段不要编造
                    
                    用户话术：
                    %s
                    """.formatted(text);
            String content = llmService.orElseThrow().completePrompt(prompt);
            Map<String, Object> llmSlots = parseJsonObject(content);
            if (llmSlots == null || llmSlots.isEmpty()) {
                return new SlotExtractResult(regexSlots, "regex-fallback");
            }
            Map<String, Object> merged = new LinkedHashMap<>(regexSlots);
            for (Map.Entry<String, Object> e : llmSlots.entrySet()) {
                if (!SLOT_KEYS.contains(e.getKey()) || e.getValue() == null) {
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

    public PackageExtractResult extractPackages(String documentText, List<Map<String, Object>> demoFallback) {
        if (documentText == null || documentText.isBlank()) {
            return new PackageExtractResult(List.of(), "empty");
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
        // 仅演示模式允许关键词样例回退
        if (properties.getOntology().isDemoEnabled() && demoFallback != null && !demoFallback.isEmpty()) {
            String t = documentText;
            if (t.contains("校园青春") || t.contains("套餐A") || t.contains("0元")) {
                return new PackageExtractResult(demoFallback, "demo-fallback");
            }
        }
        return new PackageExtractResult(List.of(), "none");
    }

    public Map<String, Object> parseSlotsByRegex(String text) {
        Map<String, Object> slots = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return slots;
        }

        if (containsAny(text, "家庭融合", "家庭用户", "融合套餐")) {
            slots.put("bizScenario", "家庭融合");
            slots.put("targetUser", "家庭");
            slots.put("offeringType", "fusion");
        } else if (containsAny(text, "校园", "大学生", "迎新")) {
            slots.put("bizScenario", "校园体验");
            slots.put("targetUser", "校园");
            slots.put("offeringType", "main_pkg");
        } else if (text.toLowerCase(Locale.ROOT).contains("5g")) {
            slots.put("bizScenario", "5G个人主套餐");
            slots.put("targetUser", "个人");
            slots.put("offeringType", "main_pkg");
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
        } else if (containsAny(text, bindTriggers.toArray(String[]::new))
                || opsRules.resolveAliasOfferingId(text) != null) {
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
        if (text.contains("内部验证")) {
            slots.put("channelScope", "内部验证");
        }
        return slots;
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
}
