package com.sitech.prodai.service.queryheat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.domain.entity.ChatMessage;
import com.sitech.prodai.mapper.ChatMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询热度聚合服务（方案 §6-C4，对应缺口 C8"查询热度反哺商品运营"）。
 * <p>
 * 数据源：{@code pd_ai_chat_messages}（role='user' 行，content=用户原话）。
 * 审计视图（audit key）不含 question 字段，而用户问题天然落在本表 user 行——
 * 聚合直接扫问题行即可支撑"高频查询词 → 商品运营洞察"，无需 JSON 反序列化
 * 审计 metadata（意图/工具维度的审计聚合属 v2 扩展：audit 视图补子入口/数据范围字段
 * 后在 queryHeat 增加对应聚合口径即可，编排与出口零改动）。
 * <p>
 * 聚合口径（方案：只聚合不落明细，输出条目化 top-N，无个人级数据）：
 * <ul>
 *   <li>高频查询词：用户问题原文归并计数（长度截断护栏，最长 top N 条）。</li>
 *   <li>高频关键词：对问题做简单切分（去标点/空白 + 长度≥2 过滤）后计数。
 *       v1 不引分词器（YAGNI：中文按字粒度无业务可读性，英文/数字/中文词块已够运营参考）。</li>
 *   <li>按天趋势：每个查询日的问题条数（业务量随时间分布）。</li>
 * </ul>
 * <p>
 * 隐私护栏：输出仅含计数与日期，绝不透出 session_id/user_id（审计归因链路已有
 * QueryAuditRecorder 覆盖，本服务定位是运营洞察而非合规对账）。
 * <p>
 * 数据源策略（v1 内嵌 H2 → 生产同构 MySQL）：聚合走 MyBatis-Plus Mapper（与
 * ChatPersistenceService 同一数据访问层），生产替换数据供给时编排与输出契约零改动。
 * 持久化不可用（内存形态/存储异常）时如实返回不可用，不冒充空热度。
 */
@Service
public class QueryHeatService {

    private static final Logger log = LoggerFactory.getLogger(QueryHeatService.class);

    /** 输出护栏：top N 条数上限。 */
    private static final int MAX_TOP = 50;
    /** 扫描护栏：单次聚合最多读入的问题行数（防全表拖垮内存，会话量级远小于此）。 */
    private static final int MAX_SCAN_ROWS = 20000;
    /** 单条问题原文截断长度（展示口径，防超长问题占屏）。 */
    private static final int QUESTION_TRUNCATE = 100;
    /** 关键词最短长度（长度&lt;2 的噪声过滤：单字"的/查"无运营价值）。 */
    private static final int MIN_KEYWORD_LENGTH = 2;
    /** 按天趋势回看窗口（天）。 */
    private static final int TREND_DAYS = 14;

    private final ObjectProvider<ChatMessageMapper> messageMapperProvider;

    public QueryHeatService(ObjectProvider<ChatMessageMapper> messageMapperProvider) {
        this.messageMapperProvider = messageMapperProvider;
    }

    /**
     * 聚合查询热度：高频问题原文 + 高频关键词 + 按天业务量趋势（回看 {@link #TREND_DAYS} 天）。
     *
     * @param limit top N 条数（1~50，超界收敛）
     * @param days  兼容参数（v1 聚合无时间窗过滤，趋势固定 TREND_DAYS 天；保留供工具层透传）
     * @return {success, total_questions, window_days, top_questions[], top_keywords[], daily_counts[], sources}
     */
    public Map<String, Object> queryHeat(Integer limit, Integer days) {
        int lim = limit == null || limit <= 0 ? 10 : Math.min(limit, MAX_TOP);
        int window = days == null || days <= 0 ? 30 : Math.min(days, 90);
        Map<String, Object> out = new LinkedHashMap<>();
        ChatMessageMapper mapper = messageMapperProvider == null
                ? null : messageMapperProvider.getIfAvailable();
        if (mapper == null) {
            out.put("success", false);
            out.put("message", "消息存储未装配（内存形态），查询热度不可用");
            return out;
        }
        try {
            List<ChatMessage> questions = fetchUserQuestions(mapper);
            out.put("success", true);
            out.put("total_questions", questions.size());
            out.put("window_days", window);
            out.put("top_questions", topQuestions(questions, lim));
            out.put("top_keywords", topKeywords(questions, lim));
            out.put("daily_counts", dailyCounts(questions, LocalDate.now().minusDays(TREND_DAYS - 1L)));
            out.put("sources", sources());
        } catch (Exception e) {
            log.warn("[查询热度] 聚合失败: {}", e.getMessage());
            out.put("success", false);
            out.put("message", "查询热度聚合失败: " + e.getMessage());
        }
        return out;
    }

    /** 拉取全部用户问题行（role='user'，按时间倒序，护栏截断；窗口过滤在聚合层执行）。 */
    private List<ChatMessage> fetchUserQuestions(ChatMessageMapper mapper) {
        return mapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, MAX_SCAN_ROWS),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getRole, "user")
                        .orderByDesc(ChatMessage::getCreatedAt))
                .getRecords();
    }

    /** 高频问题原文：原文（截断）→ 计数，取 top N（并列按字典序稳定输出，确定性）。 */
    private List<Map<String, Object>> topQuestions(List<ChatMessage> questions, int limit) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (ChatMessage m : questions) {
            String text = truncate(normalize(m.getContent()));
            if (text.isEmpty()) {
                continue;
            }
            counts.computeIfAbsent(text, k -> new long[]{0})[0]++;
        }
        return ranked(counts, limit, entry -> Map.of(
                "question", entry.getKey(),
                "count", entry.getValue()[0]));
    }

    /** 高频关键词：切分（去标点/空白、长度≥2）→ 计数，取 top N。 */
    private List<Map<String, Object>> topKeywords(List<ChatMessage> questions, int limit) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (ChatMessage m : questions) {
            for (String token : tokenize(m.getContent())) {
                counts.computeIfAbsent(token, k -> new long[]{0})[0]++;
            }
        }
        return ranked(counts, limit, entry -> Map.of(
                "keyword", entry.getKey(),
                "count", entry.getValue()[0]));
    }

    /** 按天业务量：窗口内每天的问题条数（补零对齐窗口，前端折线可直接消费）。 */
    private List<Map<String, Object>> dailyCounts(List<ChatMessage> questions, LocalDate since) {
        Map<String, long[]> byDay = new HashMap<>();
        for (ChatMessage m : questions) {
            if (m.getCreatedAt() == null) {
                continue;
            }
            String day = m.getCreatedAt().toLocalDate().toString();
            byDay.computeIfAbsent(day, k -> new long[]{0})[0]++;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate end = LocalDate.now();
        for (LocalDate d = since; !d.isAfter(end); d = d.plusDays(1)) {
            long[] cnt = byDay.get(d.toString());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("count", cnt == null ? 0L : cnt[0]);
            out.add(row);
        }
        return out;
    }

    /** 通用 top-N：计数降序 → 键字典序并列稳定，截断至 limit（确定性输出）。 */
    private List<Map<String, Object>> ranked(Map<String, long[]> counts, int limit,
                                             java.util.function.Function<Map.Entry<String, long[]>, Map<String, Object>> view) {
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(e -> -e.getValue()[0])
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(view)
                .toList();
    }

    /** 词典切分：去标点/空白后按非中文连续块 + 中文整段切分（v1 简单口径，不引分词器）。 */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        // 标点/空白切分 → 词块；块内先剥离 ASCII 字母数字段（5G/129/APP 原样成词），中文段再切词组
        for (String chunk : text.split("[\\p{Punct}\\p{IsPunctuation}\\s，。？！、；：“”‘’（）《》]+")) {
            if (chunk.length() < MIN_KEYWORD_LENGTH) {
                continue;
            }
            // ASCII 字母数字连续段整体成词（数字/英文与中文粘连时也剥离，如 5G套餐→5G+套餐）
            java.util.regex.Matcher ascii = ASCII_TOKEN.matcher(chunk);
            while (ascii.find()) {
                tokens.add(ascii.group().toLowerCase());
            }
            String cjk = ascii.replaceAll(" ").trim();
            if (cjk.length() < MIN_KEYWORD_LENGTH) {
                continue;
            }
            if (cjk.length() <= 4) {
                // 短中文块（≤4 字）整体作为一个词
                tokens.add(cjk);
            } else {
                // 长中文块：3 字滑窗切词组（如"查家庭融合套餐"→ 查家庭/家庭融/庭融合/融合套/合套餐）
                for (int i = 0; i + MIN_KEYWORD_LENGTH + 1 <= cjk.length(); i++) {
                    tokens.add(cjk.substring(i, i + 3));
                }
            }
        }
        return tokens;
    }

    /** ASCII 字母数字词段（英文缩写/数字档位等运营可读原子词）。 */
    private static final java.util.regex.Pattern ASCII_TOKEN =
            java.util.regex.Pattern.compile("[A-Za-z0-9]+");

    /** 展示归一：去首尾空白 + 截断。 */
    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private String truncate(String text) {
        return text.length() <= QUESTION_TRUNCATE ? text : text.substring(0, QUESTION_TRUNCATE);
    }

    private Map<String, Object> sources() {
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("data_source", "pd_ai_chat_messages(role=user)");
        sources.put("aggregate_mode", "in_memory");
        sources.put("privacy", "只聚合计数不透出个人/会话明细（运营洞察口径，合规对账走审计日志）");
        sources.put("note", "生产接 MySQL 实库后同构复用（数据访问层与编排零改动）");
        return sources;
    }
}
