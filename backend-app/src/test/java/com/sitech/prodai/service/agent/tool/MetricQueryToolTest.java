package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.metric.MetricRegistryService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 指标查询工具（方案 §6-B7，缺口 C7）单元测试：
 * SPI 自声明与输出契约登记、显式参数优先、窗口/下钻话术口语解析兜底
 * （近7天→recent_7d、月环比→mom、本月→mtd、分地市→region，长短语优先）、
 * 下钻意图自动升级 breakdown、指标别名解析、失败不冒充。
 * <p>
 * 注意：rawMetric 为空的用例不触发 {@code metricRegistry.metric}（三元短路），
 * 相关 stub 均以 lenient 声明，避免 strict 模式 UnnecessaryStubbing 误报。
 */
@ExtendWith(MockitoExtension.class)
class MetricQueryToolTest {

    @Mock
    private MetricService metricService;

    @Mock
    private MetricRegistryService metricRegistry;

    private MetricQueryTool tool;

    @BeforeEach
    void setUp() {
        tool = new MetricQueryTool(metricService, metricRegistry);
    }

    @Test
    void spiDeclaresScenesParamsAndOutputFields() {
        assertEquals("metric_query", tool.getName());
        assertTrue(tool.getScenes().contains("ops"));
        assertTrue(tool.getScenes().contains("query"));
        assertFalse(tool.getHandoffs().isEmpty(), "声明典型业务链");
        // B7 契约登记：窗口/下钻/异动关键输出字段齐备
        List<String> fields = tool.getOutputFields().stream().map(ToolOutputField::getName).toList();
        assertTrue(fields.containsAll(List.of("metric", "total", "delta_pct",
                "series", "items", "anomaly", "message")), "输出字段契约登记完整: " + fields);
    }

    @Test
    void explicitParamsTakePrecedenceOverQuestionParsing() {
        when(metricRegistry.metric("revenue")).thenReturn(Map.of("name", "收入"));
        when(metricService.series("revenue", "", "recent_7d"))
                .thenReturn(Map.of("success", true, "metric_name", "收入"));

        // 话术含「近90天」但显式 window=recent_7d → 显式参数优先，不消费话术窗口
        ExecutionResult result = tool.execute(Map.of(
                "question", "近90天收入趋势",
                "metric", "revenue",
                "window", "recent_7d"));

        assertTrue(result.isSuccess());
        verify(metricService).series("revenue", "", "recent_7d");
        verify(metricService, never()).series(any(), any(), eq("recent_90d"));
    }

    @Test
    void questionPhraseResolvesWindowWhenParamAbsent() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias("近7天收入趋势")).thenReturn("revenue");
        when(metricService.series("revenue", "", "recent_7d"))
                .thenReturn(Map.of("success", true, "metric_name", "收入"));

        // LLM 忘传 window，仅话术「近7天」→ 引擎翻译出 recent_7d
        ExecutionResult result = tool.execute(Map.of("question", "近7天收入趋势"));

        assertTrue(result.isSuccess());
        verify(metricService).series("revenue", "", "recent_7d");
    }

    @Test
    void comparePhraseWinsOverLongerWindowPhrase() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias(anyString())).thenReturn("revenue");
        when(metricService.series("revenue", "", "mom"))
                .thenReturn(Map.of("success", true, "metric_name", "收入",
                        "delta_pct", -0.12));

        // 「环比」必须解析为对比窗口 mom，而非退化为普通时序
        ExecutionResult result = tool.execute(Map.of("question", "收入月环比"));

        assertTrue(result.isSuccess());
        verify(metricService).series("revenue", "", "mom");
    }

    @Test
    void monthToDatePhraseResolvesMtd() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias(anyString())).thenReturn("order_cnt");
        when(metricService.series("order_cnt", "", "mtd"))
                .thenReturn(Map.of("success", true, "metric_name", "订购量"));

        ExecutionResult result = tool.execute(Map.of("question", "本月订购量怎么样"));

        assertTrue(result.isSuccess());
        verify(metricService).series("order_cnt", "", "mtd");
    }

    @Test
    void regionPhraseUpgradesToBreakdownAndDrillsByRegion() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias(anyString())).thenReturn("order_cnt");
        when(metricService.breakdown("order_cnt", "", "recent_30d", "region"))
                .thenReturn(Map.of("success", true, "metric_name", "订购量",
                        "dimension", "region",
                        "items", List.of(Map.of("key", "R-A", "value", 120, "ratio", 0.5))));

        // LLM 忘传 dimension/mode，仅话术「分地市」→ 引擎翻译出 region 下钻意图
        ExecutionResult result = tool.execute(Map.of("question", "近30天订购量分地市看"));

        assertTrue(result.isSuccess());
        verify(metricService).breakdown("order_cnt", "", "recent_30d", "region");
        verify(metricService, never()).series(anyString(), anyString(), anyString());
    }

    @Test
    void channelPhraseResolvesChannelDimension() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias(anyString())).thenReturn("revenue");
        when(metricService.breakdown("revenue", "", "", "channel"))
                .thenReturn(Map.of("success", true));

        // 「各渠道收入构成」无窗口词 → window 空串透传（字典默认窗口兜底），下钻维度由话术解析
        ExecutionResult result = tool.execute(Map.of("question", "各渠道收入构成"));

        assertTrue(result.isSuccess());
        verify(metricService).breakdown("revenue", "", "", "channel");
    }

    @Test
    void explicitDimensionBeatsQuestionPhrase() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias(anyString())).thenReturn("revenue");
        when(metricService.breakdown("revenue", "", "", "customer"))
                .thenReturn(Map.of("success", true));

        // 显式参数 customer 优先于话术「分地市」；无窗口词 → window 空串透传
        ExecutionResult result = tool.execute(Map.of(
                "question", "分地市看收入", "dimension", "customer"));

        assertTrue(result.isSuccess());
        verify(metricService).breakdown("revenue", "", "", "customer");
    }

    @Test
    void noDimensionPhraseKeepsSeriesMode() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias(anyString())).thenReturn("revenue");
        when(metricService.series("revenue", "", "recent_30d"))
                .thenReturn(Map.of("success", true, "metric_name", "收入"));

        // 无下钻话术 → 保持 series，不擅自下钻
        ExecutionResult result = tool.execute(Map.of("question", "近30天收入怎么样"));

        assertTrue(result.isSuccess());
        verify(metricService).series("revenue", "", "recent_30d");
        verify(metricService, never()).breakdown(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void blankQuestionFallsBackToDictionaryDefaultWindow() {
        when(metricRegistry.metric("revenue")).thenReturn(Map.of("name", "收入"));
        when(metricService.series("revenue", "", ""))
                .thenReturn(Map.of("success", true, "metric_name", "收入"));

        // 无话术无 window 参数 → 空串透传，由 MetricService/字典默认窗口兜底（零漂移）
        ExecutionResult result = tool.execute(Map.of("metric", "revenue"));

        assertTrue(result.isSuccess());
        verify(metricService).series("revenue", "", "");
    }

    @Test
    void aliasResolutionFallsBackWhenMetricIdUnknown() {
        when(metricRegistry.metric("营收流水")).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias("营收流水")).thenReturn("revenue");
        when(metricService.series("revenue", "", ""))
                .thenReturn(Map.of("success", true));

        // metric 传口语别名 → 非注册 ID 回落别名解析
        ExecutionResult result = tool.execute(Map.of("metric", "营收流水"));

        assertTrue(result.isSuccess());
        verify(metricService).series("revenue", "", "");
    }

    @Test
    void failsWhenMetricCannotBeResolved() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias("火星指标")).thenReturn(null);
        when(metricRegistry.metricIds()).thenReturn(List.of("revenue", "order_cnt"));

        ExecutionResult result = tool.execute(Map.of("question", "火星指标"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("无法从请求解析指标"));
    }

    @Test
    void businessFailureIsPropagatedWithoutFakeSuccess() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias(anyString())).thenReturn("revenue");
        when(metricService.series(eq("revenue"), anyString(), anyString()))
                .thenReturn(Map.of("success", false, "message", "窗口无指标事实数据"));

        ExecutionResult result = tool.execute(Map.of("question", "上季度收入"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("窗口无指标事实数据"), "业务失败如实透出");
    }

    @Test
    void anomalyModeBypassesWindowParsing() {
        lenient().when(metricRegistry.metric(anyString())).thenReturn(new LinkedHashMap<>());
        when(metricRegistry.resolveMetricByAlias(anyString())).thenReturn("revenue");
        when(metricService.detectAnomaly("revenue", ""))
                .thenReturn(Map.of("success", true, "anomaly", true, "delta_pct", -0.15));

        // 异动检测走固定近窗口径，不消费窗口解析结果
        ExecutionResult result = tool.execute(Map.of("question", "近7天收入", "mode", "anomaly"));

        assertTrue(result.isSuccess());
        verify(metricService).detectAnomaly("revenue", "");
        verify(metricService, never()).series(anyString(), anyString(), anyString());
    }

    @Test
    void outputContractDeclaresDeltaAndItemsForRenderers() {
        ToolOutputField delta = tool.getOutputFields().stream()
                .filter(f -> "delta_pct".equals(f.getName())).findFirst().orElse(null);
        assertTrue(delta != null, "delta_pct 输出字段已登记");
        assertEquals(ToolOutputField.Role.OTHER, delta.getRole());
        ToolOutputField items = tool.getOutputFields().stream()
                .filter(f -> "items".equals(f.getName())).findFirst().orElse(null);
        assertTrue(items != null);
        assertEquals(ToolOutputField.Role.ITEMS, items.getRole(), "下钻明细走 ITEMS 证据渲染");
        ToolOutputField series = tool.getOutputFields().stream()
                .filter(f -> "series".equals(f.getName())).findFirst().orElse(null);
        assertTrue(series != null);
        assertEquals(ToolOutputField.Role.ITEMS, series.getRole(), "时序数据走 ITEMS 证据渲染");
    }
}
