package com.sitech.prodai.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 草稿场景话术生成（P2-3 自 {@code ProductOntologyService#enrichSceneNotices} 抽取）。
 * <p>场景无关纯函数：打印/短信话术仅由草稿现值推导，无场景分支决策——
 * 存量 {@code inferFields} 与 {@link TemplateDeriveEngine} 共用，保证灰度 diff 语义一致；
 * P2-7 双份逻辑清理时随存量分支一并收敛。
 */
public final class DraftSceneNotices {

    private DraftSceneNotices() {
    }

    /** 按当前草稿生成打印/短信话术，避免沿用校园/个人样例消息。 */
    public static void enrich(Map<String, Object> draft, Map<String, String> fillSources) {
        if (draft == null) {
            return;
        }
        String name = str(firstNonEmpty(draft.get("offerName"), draft.get("offeringName"), "本套餐"));
        String feeText = feeText(firstNonEmpty(draft.get("fixedFeeAmount"), draft.get("monthlyFee")));
        String resources = resourcesText(draft);
        String scenario = str(draft.get("bizScenario"));
        String category = str(firstNonEmpty(draft.get("categoryName"), draft.get("messageRootKey"), "配置方案"));
        String audience = str(firstNonEmpty(draft.get("targetUser"), "客户"));

        putNoticeIfBlank(draft, fillSources, "printMonthlyFeeText", feeText + "/月");
        putNoticeIfBlank(draft, fillSources, "printResourceText", resources);
        putNoticeIfBlank(draft, fillSources, "printLimitText",
                audience + "专属；场景「" + (scenario.isBlank() ? category : scenario) + "」按销售政策执行");
        putNoticeIfBlank(draft, fillSources, "successSmsImmediate",
                "恭喜您成功办理" + name + "，月费" + feeText + "，含" + resources + "，感谢您的支持！");
        putNoticeIfBlank(draft, fillSources, "successSmsReserved",
                "您的" + name + "已预约生效，月费" + feeText + "，生效后可享受约定权益。");
        putNoticeIfBlank(draft, fillSources, "cancelSms",
                "您好，您的" + name + "已退订，如有疑问请咨询客服热线。");
        putNoticeIfBlank(draft, fillSources, "confirmSms",
                "尊敬的客户，您正在办理" + name + "，月费" + feeText + "，是否确认办理？");

        enrichPrintNotice(draft);
        enrichSmsNotice(draft);
    }

    private static String feeText(Object feeObj) {
        return feeObj == null || String.valueOf(feeObj).isBlank()
                ? "按资费标准"
                : String.valueOf(feeObj).replaceAll("\\.0$", "") + "元";
    }

    private static String resourcesText(Map<String, Object> draft) {
        String resources = Stream.of(
                        str(draft.get("includeData")),
                        str(draft.get("includeVoice")),
                        str(draft.get("includeBroadband")))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("+"));
        return resources.isBlank() ? "套餐约定资源" : resources;
    }

    private static void enrichPrintNotice(Map<String, Object> draft) {
        Map<String, Object> print = castMap(draft.get("printNotice"));
        if (print.isEmpty()) {
            print = new LinkedHashMap<>();
        }
        print.putIfAbsent("prcMonthFee", draft.get("printMonthlyFeeText"));
        print.putIfAbsent("containResource", draft.get("printResourceText"));
        print.putIfAbsent("limitCondition", draft.get("printLimitText"));
        draft.put("printNotice", print);
    }

    private static void enrichSmsNotice(Map<String, Object> draft) {
        Map<String, Object> sms = castMap(draft.get("smsNotice"));
        if (sms.isEmpty()) {
            sms = new LinkedHashMap<>();
        }
        sms.putIfAbsent("sysNoteNow", draft.get("successSmsImmediate"));
        sms.putIfAbsent("sysNoteNext", draft.get("successSmsReserved"));
        sms.putIfAbsent("sysNoteCancle", draft.get("cancelSms"));
        sms.putIfAbsent("sysNoteErke", draft.get("confirmSms"));
        draft.put("smsNotice", sms);
    }

    private static void putNoticeIfBlank(Map<String, Object> draft, Map<String, String> fillSources,
                                         String key, String value) {
        if (empty(draft.get(key))) {
            draft.put(key, value);
            if (fillSources != null) {
                fillSources.put(key, "scenario_default");
            }
        }
    }

    private static Object firstNonEmpty(Object... values) {
        for (Object v : values) {
            if (v != null && !String.valueOf(v).isBlank() && !"null".equals(String.valueOf(v))) {
                return v;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private static boolean empty(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
