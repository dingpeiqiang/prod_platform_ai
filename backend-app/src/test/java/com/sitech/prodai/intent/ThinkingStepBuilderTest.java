package com.sitech.prodai.intent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ThinkingStepBuilderTest {

    @Test
    void doneEventCarriesIdTitleResultAndMetadata() {
        Map<String, Object> event = ThinkingStepBuilder.done(
                "locate",
                "锁定分析对象",
                "定位分析对象与指标快照",
                "家庭融合畅享128（OF-HF-128）",
                2,
                6,
                120,
                "明细",
                Map.of("offeringId", "OF-HF-128")
        );

        assertEquals("thinking", event.get("type"));
        assertEquals("locate", event.get("id"));
        assertEquals("锁定分析对象", event.get("title"));
        assertEquals("llm", event.get("stepType"));
        assertEquals("家庭融合畅享128（OF-HF-128）", event.get("result"));
        assertEquals("明细", event.get("details"));
        assertNotNull(event.get("elapsed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) event.get("metadata");
        assertEquals(2, meta.get("step"));
        assertEquals(6, meta.get("totalSteps"));
        assertEquals("locate", meta.get("scheduleId"));
        assertEquals("done", meta.get("phase"));
        assertEquals("OF-HF-128", meta.get("offeringId"));
    }

    @Test
    void ontologyStepUsesOntologyType() {
        Map<String, Object> event = ThinkingStepBuilder.doneOntology(
                "reason", "规则推理", "执行图谱与 SWRL 归因规则",
                "Openllet 命中", 5, 6, 50, null, Map.of("reasonEngine", "openllet"));
        assertEquals("ontology", event.get("stepType"));
        assertEquals("reason", event.get("id"));
    }

    @Test
    void runningStepHasNegativeElapsedAndRunningPhase() {
        Map<String, Object> event = ThinkingStepBuilder.running(
                "pull", "拉取告警清单", "正在拉取运营监控告警...",
                2, 5, Map.of("action", "ops_monitor"));
        assertNull(event.get("elapsed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) event.get("metadata");
        assertEquals("running", meta.get("phase"));
        assertEquals("pull", meta.get("scheduleId"));
    }

    @Test
    void metaGuideSkipUsesThinkingStepBuilder() {
        List<Map<String, Object>> events = IntentRecognitionSupport.metaGuideSkipEvents("异动归因");
        assertFalse(events.isEmpty());
        Map<String, Object> first = events.get(0);
        assertEquals("thinking", first.get("type"));
        assertEquals("skip", first.get("id"));
        assertNotNull(first.get("result"));
        assertTrue(String.valueOf(first.get("result")).contains("异动归因"));
    }
}
