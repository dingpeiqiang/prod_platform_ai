package com.sitech.prodai.intent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IntentRecognitionSupportTest {

    @Test
    void metaGuideDetectsUsageOnlyAndDoNotExecute() {
        String msg = "请输出「立项研判」场景的详细用户使用说明。重要：只输出使用说明，不要直接执行该场景的业务操作。";
        assertTrue(IntentRecognitionSupport.isMetaGuideRequest(msg));
        assertEquals("chat", IntentRecognitionSupport.chatMetaResult().get("intentType"));
    }

    @Test
    void metaGuideBlocksKeywordFallback() {
        String msg = "立项研判使用说明，不要执行";
        Map<String, Object> fallback = IntentRecognitionSupport.tryKeywordFallback(msg, "online_check");
        assertNotNull(fallback);
        assertEquals("chat", fallback.get("intentType"));
        assertEquals(IntentRecognitionSupport.SOURCE_META, fallback.get("source"));
    }

    @Test
    void narrowWhitelistOnlyExactShortCommands() {
        Map<String, Object> hit = IntentRecognitionSupport.tryNarrowWhitelist("打开运营监控");
        assertNotNull(hit);
        assertEquals("product_ops_monitor", hit.get("intentType"));
        assertEquals(IntentRecognitionSupport.SOURCE_WHITELIST, hit.get("source"));

        assertNull(IntentRecognitionSupport.tryNarrowWhitelist(
                "请输出立项研判使用说明，不要直接执行"));
        assertNull(IntentRecognitionSupport.tryNarrowWhitelist(
                "查一下在售5G套餐的增长趋势和风险商品"));
    }

    @Test
    void keywordFallbackNoLongerForcesByOpsSceneAlone() {
        // 任意文本 + online_check 不得因 scene 强制业务意图（旧快路径已删除）
        assertNull(IntentRecognitionSupport.tryKeywordFallback("你好，今天天气怎么样", "online_check"));
    }

    @Test
    void intentLabelDistinguishesOnlineCheckAndRiskAudit() {
        assertEquals("立项研判",
                IntentRecognitionSupport.resolveIntentLabel("product_ops_policy", "online_check"));
        assertEquals("风险稽核",
                IntentRecognitionSupport.resolveIntentLabel("product_ops_policy", "risk_audit"));
    }

    @Test
    void blankInputUsesSceneDefault() {
        Map<String, Object> data = IntentRecognitionSupport.resolveBlankInputByScene("online_check");
        assertNotNull(data);
        assertEquals("product_ops_policy", data.get("intentType"));
        assertEquals("online_check", data.get("action"));
        assertEquals(IntentRecognitionSupport.SOURCE_SCENE_DEFAULT, data.get("source"));
    }
}
