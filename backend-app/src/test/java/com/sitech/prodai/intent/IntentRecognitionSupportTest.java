package com.sitech.prodai.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentRecognitionSupportTest {

    @Test
    void normalizesLlmIntentsAndLegacyEnums() {
        assertEquals("product_ops_query", IntentRecognitionSupport.normalizeIntentType("query"));
        assertEquals("product_ops_query", IntentRecognitionSupport.normalizeIntentType("nl_query"));
        assertEquals("product_ops_query", IntentRecognitionSupport.normalizeIntentType("market_insight"));
        assertEquals("product_ops_policy", IntentRecognitionSupport.normalizeIntentType("risk_audit"));
        assertEquals("product_ops_policy", IntentRecognitionSupport.normalizeIntentType("online_check"));
        assertEquals("product_ops_reason", IntentRecognitionSupport.normalizeIntentType("root_cause"));
        assertEquals("product_ops_monitor", IntentRecognitionSupport.normalizeIntentType("ops_monitor"));
        // W5 意图收敛：compare 并入 query
        assertEquals("product_ops_query", IntentRecognitionSupport.normalizeIntentType("what_if"));
        assertEquals("product_ops_query", IntentRecognitionSupport.normalizeIntentType("compare"));
        assertEquals("product_ops_query", IntentRecognitionSupport.normalizeIntentType("compare_state"));
        assertEquals("chat", IntentRecognitionSupport.normalizeIntentType("guide"));
    }

    @Test
    void canonicalNamesAreStableAndUnknownPassThrough() {
        assertEquals("product_ops_policy", IntentRecognitionSupport.normalizeIntentType("PRODUCT_OPS_POLICY"));
        assertEquals("form", IntentRecognitionSupport.normalizeIntentType("form"));
        assertEquals("", IntentRecognitionSupport.normalizeIntentType(null));
        assertEquals("", IntentRecognitionSupport.normalizeIntentType("  "));
    }
}
