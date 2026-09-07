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
        // 手册 intent_guide 归口声明下的主路径：LLM 照声明输出规范码（任意大小写）→ 幂等归一
        assertEquals("product_ops_query", IntentRecognitionSupport.normalizeIntentType("PRODUCT_OPS_QUERY"));
        assertEquals("product_ops_reason", IntentRecognitionSupport.normalizeIntentType("Product_Ops_Reason"));
        // 兜底：LLM 未照声明输出自由意图（如 ANALYZE）时映射修补
        assertEquals("product_ops_query", IntentRecognitionSupport.normalizeIntentType("ANALYZE"));
        assertEquals("product_ops_query", IntentRecognitionSupport.normalizeIntentType("analysis"));
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
