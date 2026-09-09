package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.metric.MetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 商品 360 工具（方案 §6-A4）单元测试：
 * SPI 自声明（scenes 含 query 零编排接入）、商品解析（id 精查/名称匹配/无法定位）、
 * 档案投影、指标面成功与降级口径、风险信号条目化、失败不冒充数据。
 */
@ExtendWith(MockitoExtension.class)
class Product360ToolTest {

    @Mock
    private ProductOntologyService productOntologyService;

    @Mock
    private MetricService metricService;

    private Product360Tool tool;

    @BeforeEach
    void setUp() {
        tool = new Product360Tool(productOntologyService, metricService);
        lenient().when(productOntologyService.withModeMeta(any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void spiDeclaresQuerySceneForZeroChangeAdoption() {
        assertEquals("product_360", tool.getName());
        assertTrue(tool.getScenes().contains("query"), "scenes 含 query → 自动进入场景能力集");
        assertTrue(tool.getScenes().contains("ops"));
        assertFalse(tool.getHandoffs().isEmpty(), "声明典型业务链");
        assertTrue(tool.getOutputFields().stream().anyMatch(f -> "subscriptions".equals(f.getName())));
    }

    @Test
    void resolvesByIdAndAggregatesProfileSubscriptionsAndSignals() {
        when(productOntologyService.resolveOfferingId("SCHEME_FBP_001", ""))
                .thenReturn("SCHEME_FBP_001");
        when(productOntologyService.loadGraph()).thenReturn(graphWithOffering(shelfOffering()));
        when(metricService.series(eq("order_cnt"), eq("SCHEME_FBP_001"), eq("recent_30d")))
                .thenReturn(Map.of("success", true, "metric", "order_cnt", "metric_name", "订购量",
                        "window", "recent_30d", "total", 150, "delta_pct", -12.5, "source", "mock"));

        ExecutionResult result = tool.execute(Map.of("offering_id", "SCHEME_FBP_001"));

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertEquals("SCHEME_FBP_001", data.get("offering_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) data.get("profile");
        assertEquals("家庭亲情网基础套餐", profile.get("offering_name"));
        assertEquals(99, ((Number) profile.get("monthly_fee")).intValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> subs = (Map<String, Object>) data.get("subscriptions");
        assertEquals(150, ((Number) subs.get("total")).intValue());
        assertEquals("mock", subs.get("source"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> signals = (List<Map<String, Object>>) data.get("risk_signals");
        assertTrue(signals.stream().anyMatch(s -> "contract".equals(s.get("type"))), "含合约 → 合约信号");
        assertTrue(signals.stream().anyMatch(s -> "strategic".equals(s.get("type"))), "战略标记 → 战略信号");
        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) data.get("sources");
        assertEquals("unknown", sources.get("profile_source"), "mock 桩未提供 meta → 如实 unknown");
        assertTrue(String.valueOf(data.get("nl_answer")).contains("订购"),
                "摘要串联近30天订购");
    }

    @Test
    void resolvesByNameWhenIdAbsent() {
        when(productOntologyService.resolveOfferingId("", "看一下家庭亲情网基础套餐的全景"))
                .thenReturn("SCHEME_FBP_001");
        when(productOntologyService.loadGraph()).thenReturn(graphWithOffering(shelfOffering()));
        when(metricService.series(any(), any(), any()))
                .thenReturn(Map.of("success", false, "message", "无数据"));

        ExecutionResult result = tool.execute(Map.of("offering_name", "看一下家庭亲情网基础套餐的全景"));

        assertTrue(result.isSuccess(), "指标面失败但档案面可用 → 整体成功（降级缺面）");
        @SuppressWarnings("unchecked")
        Map<String, Object> subs = (Map<String, Object>) result.getData().get("subscriptions");
        assertEquals(Boolean.TRUE, subs.get("unavailable"), "指标面失败如实降级标记，不冒充无数据");
    }

    @Test
    void failsWhenOfferingCannotBeResolved() {
        when(productOntologyService.resolveOfferingId("", "不存在的商品")).thenReturn(null);

        ExecutionResult result = tool.execute(Map.of("offering_name", "不存在的商品"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("无法定位商品"));
    }

    @Test
    void failsWhenOfferingNotOnShelf() {
        when(productOntologyService.resolveOfferingId("GHOST_001", "")).thenReturn("GHOST_001");
        when(productOntologyService.loadGraph()).thenReturn(graphWithOffering(shelfOffering()));

        ExecutionResult result = tool.execute(Map.of("offering_id", "GHOST_001"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未找到商品"));
    }

    @Test
    void dependencySignalEmittedForAddonProducts() {
        when(productOntologyService.resolveOfferingId("SCHEME_FAP_001", "")).thenReturn("SCHEME_FAP_001");
        Map<String, Object> addon = shelfOffering();
        addon.put("offeringId", "SCHEME_FAP_001");
        addon.put("hasContract", false);
        addon.put("strategicTag", false);
        addon.put("dependOn", "MAIN_PKG");
        when(productOntologyService.loadGraph()).thenReturn(graphWithOffering(addon));
        when(metricService.series(any(), any(), any())).thenReturn(Map.of("success", false));

        ExecutionResult result = tool.execute(Map.of("offering_id", "SCHEME_FAP_001"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> signals = (List<Map<String, Object>>) result.getData().get("risk_signals");
        assertTrue(signals.stream().anyMatch(s -> "dependency".equals(s.get("type"))), "附加商品 → 依赖信号");
        assertFalse(signals.stream().anyMatch(s -> "contract".equals(s.get("type"))), "无合约不造信号");
    }

    // ── 测试夹具 ──

    private Map<String, Object> shelfOffering() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("offeringId", "SCHEME_FBP_001");
        o.put("offeringName", "家庭亲情网基础套餐");
        o.put("state", "上架");
        o.put("monthlyFee", 99);
        o.put("shelfDays", 120);
        o.put("hasContract", true);
        o.put("strategicTag", true);
        o.put("mutexGroup", "MAIN_PKG");
        o.put("categoryName", "家庭基础套餐");
        o.put("productLine", "家庭");
        o.put("channelScope", "A7");
        return o;
    }

    private Map<String, Object> graphWithOffering(Map<String, Object> offering) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("shelfOfferings", List.of(offering));
        return graph;
    }
}
