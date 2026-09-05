package com.sitech.prodai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R5：ABoxSyncScheduler 单元测试。
 * 覆盖：同步成功/守卫拒绝/异常三条路径的 last-known-good 语义与可观测状态字段。
 */
@ExtendWith(MockitoExtension.class)
class ABoxSyncSchedulerTest {

    @Mock
    private ProductOntologyService productOntologyService;

    private ABoxSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ABoxSyncScheduler(productOntologyService);
    }

    @Test
    void syncOnceSuccessShouldRecordStatus() {
        when(productOntologyService.reloadGraph())
                .thenReturn(Map.of("success", true, "step", "COMMIT", "message", "reload committed"));

        scheduler.syncOnce();

        Map<String, Object> status = scheduler.syncStatus();
        assertEquals(Boolean.TRUE, status.get("abox_last_sync_ok"));
        assertNotNull(status.get("abox_last_synced_at"), "成功后应记录同步时间");
        assertEquals("reload committed", status.get("abox_last_sync_message"));
        verify(productOntologyService, times(1)).reloadGraph();
    }

    @Test
    void syncOnceRejectedByGuardShouldRetainLastKnownGood() {
        // 守卫拒绝（如 VALIDATE/SMOKE 失败）：不抛异常，标记失败并保留现行图谱
        when(productOntologyService.reloadGraph())
                .thenReturn(Map.of("success", false, "step", "SMOKE",
                        "errors", java.util.List.of(Map.of("caseId", "family_fusion_main_128"))));

        scheduler.syncOnce();

        Map<String, Object> status = scheduler.syncStatus();
        assertEquals(Boolean.FALSE, status.get("abox_last_sync_ok"));
        assertEquals("false", String.valueOf(status.get("abox_last_sync_ok")),
                "lastSyncOk 必须为 false（last-known-good 保留语义）");
        assertNotNull(status.get("abox_last_synced_at"));
    }

    @Test
    void syncOnceExceptionShouldNotThrowAndMarkFailure() {
        when(productOntologyService.reloadGraph())
                .thenThrow(new IllegalStateException("Connection refused: jdbc"));

        // 异常被吞掉（守卫语义：不影响在跑服务）
        scheduler.syncOnce();

        Map<String, Object> status = scheduler.syncStatus();
        assertEquals(Boolean.FALSE, status.get("abox_last_sync_ok"));
        assertTrue(String.valueOf(status.get("abox_last_sync_message")).contains("Connection refused"));
    }

    @Test
    void initialStatusShouldBeNotRunYet() {
        Map<String, Object> status = scheduler.syncStatus();

        assertEquals(null, status.get("abox_last_synced_at"), "未同步过应为 null");
        assertEquals(Boolean.FALSE, status.get("abox_last_sync_ok"));
        assertEquals("not run yet", status.get("abox_last_sync_message"));
        assertFalse(Boolean.TRUE.equals(status.get("abox_last_sync_ok")));
    }

    @Test
    void repeatedSyncsShouldTrackLatestOutcome() {
        lenient().when(productOntologyService.reloadGraph())
                .thenReturn(Map.of("success", true, "message", "ok"))
                .thenReturn(Map.of("success", false, "step", "LOAD", "message", "rejected"));

        scheduler.syncOnce();
        assertEquals(Boolean.TRUE, scheduler.syncStatus().get("abox_last_sync_ok"));

        scheduler.syncOnce();
        Map<String, Object> status = scheduler.syncStatus();
        assertEquals(Boolean.FALSE, status.get("abox_last_sync_ok"), "应反映最近一次结果");
        assertEquals("rejected", status.get("abox_last_sync_message"));
        verify(productOntologyService, times(2)).reloadGraph();
    }
}
