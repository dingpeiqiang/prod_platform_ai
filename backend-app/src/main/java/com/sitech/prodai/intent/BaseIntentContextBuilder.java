package com.sitech.prodai.intent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BaseIntentContextBuilder {

    private BaseIntentContextBuilder() {}

    public static Map<String, Object> extractPolicyFacts(IntentContext ctx) {
        Map<String, Object> facts = new LinkedHashMap<>(ctx.getExtractedFields());
        String message = ctx.getLastUserMessage() != null ? ctx.getLastUserMessage().toLowerCase() : "";
        if (!facts.containsKey("candidateActionType")) {
            facts.put("candidateActionType", message.contains("退款") ? "partial_refund" : "premium_upgrade");
        }
        if (!facts.containsKey("billingActionType")) {
            facts.put("billingActionType", message.contains("全额") ? "full_refund" : message.contains("退款") ? "partial_refund" : "none");
        }
        if (!facts.containsKey("productType")) {
            facts.put("productType", message.contains("5g") || message.contains("套餐") ? "5G套餐" : "融合套餐");
        }
        if (!facts.containsKey("annualSpend")) {
            facts.put("annualSpend", 60000);
        }
        if (!facts.containsKey("creditScore")) {
            facts.put("creditScore", 720);
        }
        if (!facts.containsKey("vipLevel")) {
            facts.put("vipLevel", "Gold");
        }
        return facts;
    }

    public static String resolvePolicySetId(IntentContext ctx) {
        String action = ctx.getAction() != null ? ctx.getAction().toLowerCase() : "";
        String message = ctx.getLastUserMessage() != null ? ctx.getLastUserMessage() : "";
        if (action.contains("risk") || message.contains("零资费") || message.contains("风险")) {
            return "PS_PRODUCT_RISK_V1";
        }
        if (action.contains("online") || message.contains("新品") || message.contains("立项")) {
            return "PS_PRODUCT_ONLINE_V1";
        }
        if (action.contains("refund") || message.contains("退款")) {
            return "PS_BILLING_REFUND_V1";
        }
        return "PS_PRODUCT_RISK_V1";
    }

    public static String resolveExpectationType(IntentContext ctx) {
        String action = ctx.getAction() != null ? ctx.getAction().toLowerCase() : "";
        String message = ctx.getLastUserMessage() != null ? ctx.getLastUserMessage() : "";
        if (action.contains("audit") || message.contains("稽核") || message.contains("筛查")) {
            return "risk_audit";
        }
        if (action.contains("online") || message.contains("立项")) {
            return "online_check";
        }
        return "candidate_check";
    }
}
