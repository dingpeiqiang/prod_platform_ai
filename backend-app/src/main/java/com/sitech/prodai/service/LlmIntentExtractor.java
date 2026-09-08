package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 结构化意图提取器：将自然语言检索问题翻译为本体查询意图（JSON）。
 * <p>输出 schema（snake_case）：
 * <pre>{ "query_type": "campus|family|broadband|5g|risk|all",
 *       "keywords": ["校园"], "monthly_fee": 39, "fee_tolerance": 10,
 *       "state": "上架", "time_window_days": 30, "limit": 20 }</pre>
 * <p>LLM 不可用或解析失败时回退 {@link #fallbackExtract}（正则+词典），保证检索链路永远可用。
 */
@Service
public class LlmIntentExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmIntentExtractor.class);

    private static final String SYSTEM_PROMPT = """
            你是产商品配置检索助手的意图解析器。把用户的检索问题解析为 JSON（仅输出 JSON，不要输出任何其他文字）。
            可用本体概念：
            - query_type（枚举）：campus(校园/学生/大学/青春), family(家庭/融合), broadband(宽带/提速),
              5g(5G/畅享), risk(风险/零资费/低效), all(其他或未提及)
            - keywords（字符串数组）：从问题中提取的业务关键词，如 ["校园","流量"]
            - monthly_fee（数字或 null）：问题中期望的月费金额，如"月费39左右"→39；"0元"→0
            - fee_tolerance（数字）：月费可接受的浮动范围，"左右/上下/大约"→10，"以内/以下"→用户值本身，
              无修饰词→5；未提及月费→null
            - state（枚举）：上架（默认，问题含"在售/在架/上线"时也是上架）、下架、null
            - time_window_days（数字或 null）：上架时间窗（天）。"近30天/最近30天/这30天/30天内"→30；
              "近一周/最近一周"→7；"近三个月"→90；"本月"→当月已过天数；"今年"→365；未提及→null
            - limit（数字）：期望返回条数，默认20
            示例：
            问题"找一下月费39左右的校园套餐" →
            {"query_type":"campus","keywords":["校园","套餐"],"monthly_fee":39,"fee_tolerance":10,"state":"上架","time_window_days":null,"limit":20}
            问题"查一下近30天上架的大学生套餐" →
            {"query_type":"campus","keywords":["大学生","套餐"],"monthly_fee":null,"fee_tolerance":null,"state":"上架","time_window_days":30,"limit":20}
            """;

    private final Optional<LlmService> llmService;
    private final ObjectMapper objectMapper;

    public LlmIntentExtractor(@Lazy Optional<LlmService> llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    /** 意图结构：LLM 解析结果或回退结果，供 SparqlConfigDiscoverer 消费。 */
    public record DiscoverIntent(String queryType, List<String> keywords,
                                 Double monthlyFee, Double feeTolerance,
                                 String state, Integer timeWindowDays,
                                 int limit, String engine) {
    }

    public DiscoverIntent extract(String question) {
        if (question == null || question.isBlank()) {
            return new DiscoverIntent("all", List.of(), null, null, null, null, 20, "empty");
        }
        if (llmService.isPresent()) {
            try {
                return extractByLlm(question);
            } catch (Exception e) {
                log.warn("[LlmIntentExtractor] LLM 意图解析失败，回退词典规则: {}", e.getMessage());
            }
        }
        return fallbackExtract(question);
    }

    private DiscoverIntent extractByLlm(String question) {
        String content = llmService.get().completePrompt(
                SYSTEM_PROMPT + "\n\n用户问题：" + question);
        Map<String, Object> json = parseJson(content);
        if (json == null || json.isEmpty()) {
            return fallbackExtract(question);
        }
        return new DiscoverIntent(
                str(json.get("query_type"), "all"),
                castStringList(json.get("keywords")),
                castDouble(json.get("monthly_fee")),
                castDouble(json.get("fee_tolerance")),
                str(json.get("state"), null),
                castIntOrNull(json.get("time_window_days")),
                castInt(json.get("limit"), 20),
                "llm");
    }

    /** LLM 不可用时的回退：数字提取 + 业务词典，与旧 matchScore 同思路但输出结构化意图。 */
    public DiscoverIntent fallbackExtract(String question) {
        String q = question.toLowerCase();
        Integer timeWindow = extractTimeWindow(q);
        Double fee = null;
        Matcher m = Pattern.compile("(?:月费|月租|资费)?(\\d{1,4})\\s*(?:元)?").matcher(q);
        while (m.find()) {
            int start = m.start();
            int v = Integer.parseInt(m.group(1));
            // 时间窗数字不是月费：「近30天」的 30 隶属时间窗语义，剔除后防止误挂月费过滤
            if (timeWindow != null && timeWindow == v && isTimeWindowContext(q, start, m.group())) {
                continue;
            }
            if (v >= 0 && v <= 999) {
                fee = (double) v;
                break;
            }
        }
        double tolerance = fee == null ? 0 : (q.contains("以内") || q.contains("以下") ? 0 : 10);
        String queryType = "all";
        if (q.contains("校园") || q.contains("学生") || q.contains("大学") || q.contains("青春")) {
            queryType = "campus";
        } else if (q.contains("家庭") || q.contains("融合")) {
            queryType = "family";
        } else if (q.contains("宽带") || q.contains("提速")) {
            queryType = "broadband";
        } else if (q.contains("5g") || q.contains("畅享")) {
            queryType = "5g";
        } else if (q.contains("风险") || q.contains("零资费") || q.contains("低效")) {
            queryType = "risk";
        }
        List<String> keywords = new ArrayList<>();
        for (String w : List.of("校园", "学生", "大学", "青春", "家庭", "融合", "宽带", "提速", "5g", "畅享",
                "风险", "零资费", "低效", "套餐", "模板", "资费", "方案", "配置", "在售", "在架", "上线")) {
            if (q.contains(w)) {
                keywords.add(w);
            }
        }
        return new DiscoverIntent(queryType, keywords, fee, tolerance,
                "上架", timeWindow, 20, "fallback-dict");
    }

    /** 数字前后文是否为时间窗表述（近/最近前缀 + 天/周/月等单位后缀），用于剔除时间窗数字的月费误判。 */
    private boolean isTimeWindowContext(String q, int numStart, String matched) {
        String prefix = q.substring(Math.max(0, numStart - 3), numStart);
        if (prefix.contains("近") || prefix.contains("最近")) {
            return true;
        }
        return matched.matches("\\d+\\s*天");
    }

    /**
     * 时间窗解析（回退链路）：正则识别「近N天/最近N天/N天内/近一周/近一月/近三月/本月/今年」。
     * LLM 链路由提示词解析 time_window_days，本方法仅服务词典回退。
     */
    private Integer extractTimeWindow(String q) {
        Matcher day = Pattern.compile("(?:近|最近)(\\d{1,3})\\s*天").matcher(q);
        if (day.find()) {
            return Integer.parseInt(day.group(1));
        }
        if (q.contains("一周") || q.contains("一星期") || q.contains("7天")) {
            return 7;
        }
        if (q.contains("半月") || q.contains("半个月") || q.contains("15天")) {
            return 15;
        }
        if (q.contains("一月") || q.contains("一个月") || q.contains("30天")) {
            return 30;
        }
        if (q.contains("三月") || q.contains("三个月") || q.contains("一季度") || q.contains("90天")) {
            return 90;
        }
        if (q.contains("半年") || q.contains("6个月") || q.contains("180天")) {
            return 180;
        }
        if (q.contains("今年") || q.contains("一年") || q.contains("365天")) {
            return 365;
        }
        if (q.contains("本月") || q.contains("这个月")) {
            int dayOfMonth = java.time.LocalDate.now().getDayOfMonth();
            return dayOfMonth;
        }
        return null;
    }

    private Map<String, Object> parseJson(String content) {
        if (content == null) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readValue(content.substring(start, end + 1),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Object v, String def) {
        if (v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v))) {
            return def;
        }
        return String.valueOf(v);
    }

    private Double castDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int castInt(Object v, int def) {
        Double d = castDouble(v);
        return d == null ? def : d.intValue();
    }

    private Integer castIntOrNull(Object v) {
        Double d = castDouble(v);
        return d == null ? null : d.intValue();
    }

    private List<String> castStringList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item).toLowerCase());
                }
            }
        }
        return out;
    }
}
