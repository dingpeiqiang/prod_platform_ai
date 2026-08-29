package com.sitech.prodai.service;

import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * P1-6 last-known-good 快照守卫回退测试（§13.2）。
 * 覆盖：四步全过提交并登记 published；LOAD/VALIDATE/SMOKE/COMMIT 任一步失败保留现行版本（COMMIT 不执行）、登记 review 复盘行。
 */
class LastKnownGoodGuardTest {

    private OntologyVersionService versionService;
    private LastKnownGoodGuard guard;
    private AtomicInteger commitCount;
    private AtomicReference<Map<String, Object>> committed;

    @BeforeEach
    void setUp() {
        versionService = mock(OntologyVersionService.class);
        OntologyAssetVersion row = new OntologyAssetVersion();
        row.setId(1L);
        when(versionService.register(any(), any(), any(), any(), any(), any(), any())).thenReturn(row);
        guard = new LastKnownGoodGuard(versionService);
        commitCount = new AtomicInteger();
        committed = new AtomicReference<>();
    }

    private LastKnownGoodGuard.GuardRequest.Builder baseBuilder() {
        return LastKnownGoodGuard.GuardRequest
                .builder("template", "t1", () -> Map.of("k", "v"),
                        pending -> {
                            commitCount.incrementAndGet();
                            committed.set(pending);
                        })
                .version("2.0.0")
                .payloadFrom(pending -> "payload-of-" + pending.get("k"));
    }

    @Test
    void shouldCommitAndRegisterPublishedOnAllPass() {
        Map<String, Object> report = guard.execute(baseBuilder().build());

        assertEquals(Boolean.TRUE, report.get("success"));
        assertEquals("COMMIT", report.get("step"));
        assertEquals(1, commitCount.get(), "COMMIT 应恰好执行一次");
        assertEquals("v", committed.get().get("k"));
        verify(versionService).register("template", "t1", "2.0.0",
                OntologyVersionService.STATUS_PUBLISHED, "system", "", "payload-of-v");
        verify(versionService).log(eq(1L), eq("reload"), eq("system"), anyMap());
    }

    @Test
    void shouldRetainCurrentOnLoadFailure() {
        LastKnownGoodGuard.GuardRequest request = LastKnownGoodGuard.GuardRequest
                .builder("template", "t1", () -> {
                    throw new IllegalStateException("bad json");
                }, pending -> commitCount.incrementAndGet())
                .version("2.0.0")
                .payloadFrom(pending -> "payload")
                .build();

        Map<String, Object> report = guard.execute(request);

        assertFalse(Boolean.TRUE.equals(report.get("success")));
        assertEquals("LOAD", report.get("step"));
        assertEquals(0, commitCount.get(), "LOAD 失败不得触碰现行版本");
        verify(versionService).register(eq("template"), eq("t1"), eq("2.0.0"),
                eq(OntologyVersionService.STATUS_REVIEW), eq("system"), any(), any());
    }

    @Test
    void shouldRetainCurrentOnValidateFailure() {
        Map<String, Object> report = guard.execute(baseBuilder()
                .validator(pending -> List.of("missing field: calcMode"))
                .build());

        assertFalse(Boolean.TRUE.equals(report.get("success")));
        assertEquals("VALIDATE", report.get("step"));
        assertEquals(List.of("missing field: calcMode"), report.get("errors"));
        assertEquals(0, commitCount.get(), "VALIDATE 失败不得触碰现行版本");
        verify(versionService).register(eq("template"), eq("t1"), eq("2.0.0"),
                eq(OntologyVersionService.STATUS_REVIEW), eq("system"), any(), any());
    }

    @Test
    void shouldRetainCurrentOnSmokeFailure() {
        Map<String, Object> report = guard.execute(baseBuilder()
                .smoke(pending -> List.of(Map.of(
                        "case", "family_fusion_001",
                        "expected", "kdMbrMax=2",
                        "actual", "kdMbrMax=null")))
                .build());

        assertFalse(Boolean.TRUE.equals(report.get("success")));
        assertEquals("SMOKE", report.get("step"));
        assertEquals(0, commitCount.get(), "SMOKE 失败不得触碰现行版本");
        verify(versionService).register(eq("template"), eq("t1"), eq("2.0.0"),
                eq(OntologyVersionService.STATUS_REVIEW), eq("system"), any(), any());
    }

    @Test
    void shouldRetainCurrentOnCommitFailure() {
        LastKnownGoodGuard.GuardRequest request = LastKnownGoodGuard.GuardRequest
                .builder("template", "t1", () -> Map.of("k", "v"),
                        pending -> {
                            throw new IllegalStateException("cache swap failed");
                        })
                .version("2.0.0")
                .payloadFrom(pending -> "payload-of-v")
                .build();

        Map<String, Object> report = guard.execute(request);

        assertFalse(Boolean.TRUE.equals(report.get("success")));
        assertEquals("COMMIT", report.get("step"));
        assertTrue(String.valueOf(report.get("message")).contains("last-known-good retained"));
        verify(versionService).register(eq("template"), eq("t1"), eq("2.0.0"),
                eq(OntologyVersionService.STATUS_REVIEW), eq("system"), any(), any());
    }

    @Test
    void shouldSkipRegistrationWhenPayloadAbsent() {
        Map<String, Object> report = guard.execute(LastKnownGoodGuard.GuardRequest
                .builder("template", "t1", () -> Map.of("k", "v"),
                        pending -> commitCount.incrementAndGet())
                .build());

        assertEquals(Boolean.TRUE, report.get("success"));
        assertEquals(1, commitCount.get());
        verifyNoInteractions(versionService);
    }

    @Test
    void shouldPreferStaticPayloadOverDynamic() {
        Map<String, Object> report = guard.execute(baseBuilder()
                .payload("static-payload")
                .build());

        assertEquals(Boolean.TRUE, report.get("success"));
        verify(versionService).register("template", "t1", "2.0.0",
                OntologyVersionService.STATUS_PUBLISHED, "system", "", "static-payload");
        verify(versionService, never()).register("template", "t1", "2.0.0",
                OntologyVersionService.STATUS_PUBLISHED, "system", "", "payload-of-v");
    }
}
