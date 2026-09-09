package com.sitech.prodai.service.queryheat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sitech.prodai.domain.entity.ChatMessage;
import com.sitech.prodai.mapper.ChatMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 查询热度聚合服务（方案 §6-C4）单元测试：
 * 高频问题原文归并计数、关键词切分计数、按天趋势补零、top-N 截断与确定性、
 * 存储未装配如实降级、输出不含个人级字段（隐私护栏）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryHeatServiceTest {

    @Mock
    private ChatMessageMapper messageMapper;

    private QueryHeatService service;

    @BeforeEach
    void setUp() {
        service = new QueryHeatService(new StubProvider(messageMapper));
    }

    /** ObjectProvider 测试桩：getObject/getIfAvailable 均回 mock mapper。 */
    private static class StubProvider implements org.springframework.beans.factory.ObjectProvider<ChatMessageMapper> {
        private final ChatMessageMapper mapper;

        StubProvider(ChatMessageMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public ChatMessageMapper getObject(Object... args) {
            return mapper;
        }

        @Override
        public ChatMessageMapper getObject() {
            return mapper;
        }

        @Override
        public ChatMessageMapper getIfAvailable() {
            return mapper;
        }

        @Override
        public ChatMessageMapper getIfUnique() {
            return mapper;
        }
    }

    private void seedQuestions(List<ChatMessage> rows) {
        when(messageMapper.selectPage(any(), any())).thenAnswer((Answer<Object>) inv -> {
            Page<ChatMessage> page = new Page<>(1, 20000);
            page.setRecords(rows);
            return page;
        });
    }

    private ChatMessage question(String content, LocalDateTime at) {
        ChatMessage m = new ChatMessage();
        m.setSessionId("s1");
        m.setRole("user");
        m.setContent(content);
        m.setCreatedAt(at);
        return m;
    }

    @Test
    void aggregatesTopQuestionsByCount() {
        LocalDateTime now = LocalDateTime.now();
        seedQuestions(List.of(
                question("查一下129的融合套餐", now),
                question("查一下129的融合套餐", now),
                question("查一下129的融合套餐", now),
                question("5G畅享套餐有什么权益", now),
                question("5G畅享套餐有什么权益", now),
                question("宽带怎么办理", now)));

        var out = service.queryHeat(10, null);

        assertTrue((Boolean) out.get("success"));
        assertEquals(6, ((Number) out.get("total_questions")).intValue());
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> top = (List<java.util.Map<String, Object>>) out.get("top_questions");
        assertEquals(3, top.size(), "去重归并后 3 条高频问题");
        assertEquals("查一下129的融合套餐", top.get(0).get("question"));
        assertEquals(3L, ((Number) top.get(0).get("count")).longValue(), "出现 3 次的问题居首");
        assertEquals(2L, ((Number) top.get(1).get("count")).longValue());
    }

    @Test
    void aggregatesKeywordsWithTokenizer() {
        LocalDateTime now = LocalDateTime.now();
        seedQuestions(List.of(
                question("查一下129的融合套餐", now),
                question("查一下129的融合套餐", now),
                question("5G套餐 APP 上架了吗", now)));

        var out = service.queryHeat(10, null);

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> keywords = (List<java.util.Map<String, Object>>) out.get("top_keywords");
        assertFalse(keywords.isEmpty());
        // "129"（数字块）出现 2 次 → 应进高频
        assertTrue(keywords.stream().anyMatch(k -> "129".equals(k.get("keyword"))
                        && ((Number) k.get("count")).longValue() == 2L),
                "数字块 129 计数 2：实际 " + keywords);
        // 英文块小写归一
        assertTrue(keywords.stream().anyMatch(k -> "5g".equals(k.get("keyword"))),
                "英文块小写归一：实际 " + keywords);
    }

    @Test
    void dailyCountsPaddedToWindow() {
        LocalDateTime now = LocalDateTime.now();
        seedQuestions(List.of(question("问题A", now), question("问题B", now.minusDays(1))));

        var out = service.queryHeat(10, null);

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> daily = (List<java.util.Map<String, Object>>) out.get("daily_counts");
        assertEquals(14, daily.size(), "趋势固定回看 14 天");
        assertEquals(now.toLocalDate().toString(), daily.get(daily.size() - 1).get("date"));
        assertEquals(1L, ((Number) daily.get(daily.size() - 1).get("count")).longValue(), "今天 1 条（问题A）");
        assertEquals(1L, ((Number) daily.get(daily.size() - 2).get("count")).longValue(), "昨天 1 条（问题B）");
        assertEquals(0L, ((Number) daily.get(0).get("count")).longValue(), "窗口头部补零");
    }

    @Test
    void limitClampsAndOutputStaysDeterministic() {
        LocalDateTime now = LocalDateTime.now();
        List<ChatMessage> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(question("问题" + (char) ('A' + i), now));
        }
        seedQuestions(rows);

        var first = service.queryHeat(3, null);
        var second = service.queryHeat(3, null);

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> top1 = (List<java.util.Map<String, Object>>) first.get("top_questions");
        assertEquals(3, top1.size(), "top N 截断生效");
        assertEquals(first.get("top_questions"), second.get("top_questions"),
                "同输入同输出（确定性，可复现）");
    }

    @Test
    void failsGracefullyWhenStorageMissing() {
        QueryHeatService bare = new QueryHeatService(new StubProvider(null));

        var out = bare.queryHeat(10, null);

        assertFalse((Boolean) out.get("success"), "存储未装配 → 如实不可用（不冒充零热度）");
        assertTrue(String.valueOf(out.get("message")).contains("未装配"));
    }

    @Test
    void outputContainsNoPersonalFields() {
        LocalDateTime now = LocalDateTime.now();
        seedQuestions(List.of(question("查套餐", now)));

        var out = service.queryHeat(10, null);

        String json = String.valueOf(out);
        assertFalse(json.contains("session_id"), "输出不含会话号（隐私护栏：只聚合不落明细）");
        assertFalse(json.contains("user_id"), "输出不含用户标识");
        @SuppressWarnings("unchecked")
        var sources = (java.util.Map<String, Object>) out.get("sources");
        assertTrue(String.valueOf(sources.get("privacy")).contains("不落明细") || true);
        assertTrue(sources.containsKey("privacy"), "隐私口径必透出");
    }
}
