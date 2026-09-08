package com.sitech.prodai.service.agent.flow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程引擎结果 → 对话回复的统一组装器。
 * <p>
 * SceneFlowRouter（场景工作流）与 FlowIntentRouter（固定流程）的回复契约同构
 * （intent=FLOW_EXEC + report/conclusion/flow_execution/session_id/suggested_follow_ups），
 * 组装逻辑此前在两个路由器内各持一份且结论生成退化（无 flow.output 时整 Map toString）。
 * 依据 DRY 原则收敛到本类，并修复结论语义：
 * <ul>
 *   <li>结论 = flow.output（end 节点透传）→ 各节点自然语言输出（nl_answer/answer 等）
 *       → 旧回落（output_data 原样）；拒绝整 Map toString 串糊脸；</li>
 *   <li>报告 = 流程名 + 各节点产出概要（自然语言摘要优先），不再只报耗时。</li>
 * </ul>
 */
final class FlowReplyBuilder {

    /** 节点输出中的自然语言结论键（顺序即优先级，与 ToolOutputRenderer 旧回落键对齐）。 */
    private static final List<String> NL_ANSWER_KEYS = List.of("nl_answer", "answer", "conclusion", "summary", "response");

    /** 节点输出中的原始结果键（无自然语言时兜底展示条数）。 */
    private static final List<String> RESULT_KEYS = List.of("items", "raw_results", "comparisons", "facts");

    private FlowReplyBuilder() {
    }

    /**
     * 组装流程执行完成（status=completed）的报告与结论。
     *
     * @param flowName   流程展示名（报告文案用）
     * @param data       引擎执行实例 Map（含 output_data / context_data）
     * @param reportBody 报告主体文案槽位（completed 分支填 null 取默认组装）
     * @return {report, conclusion} 两键
     */
    static Map<String, Object> completedReply(String flowName, Map<String, Object> data, String reportBody) {
        Map<String, Object> out = new LinkedHashMap<>();
        String conclusion = buildConclusion(data);
        out.put("report", reportBody != null && !reportBody.isBlank() ? reportBody
                : "流程「" + flowName + "」已执行完成" + reportSummary(data) + "。");
        out.put("conclusion", conclusion);
        return out;
    }

    /**
     * 结论摘要（优先级降序）：
     * <ol>
     *   <li>flow.output：end 节点透传的流程级结论（引擎 variables 的 flow 命名空间）；</li>
     *   <li>各节点自然语言输出：按节点执行序拼接 nl_answer 等可读文本（问询类链路的实际答案在此）；</li>
     *   <li>空串：无任何可读产出时留空（前端结论区不渲染，执行明细兜底）。</li>
     * </ol>
     * 不再回落整 Map toString——那是一串对用户不可读的转义噪声。
     */
    static String buildConclusion(Map<String, Object> data) {
        if (data != null && data.get("output_data") instanceof Map<?, ?> m && !m.isEmpty()) {
            if (m.get("flow") instanceof Map<?, ?> fs && fs.get("output") != null) {
                String s = String.valueOf(fs.get("output"));
                if (!s.isBlank() && !"null".equals(s)) {
                    return s;
                }
            }
            String nodeTexts = joinNodeAnswers(m);
            if (!nodeTexts.isBlank()) {
                return nodeTexts;
            }
        }
        return "";
    }

    /** 报告附注：从各节点输出提取一句话概要（取最后一段自然语言产出，最贴近链路终点）。 */
    private static String reportSummary(Map<String, Object> data) {
        if (data == null || !(data.get("output_data") instanceof Map<?, ?> m) || m.isEmpty()) {
            return "";
        }
        String last = "";
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> nodeScope) {
                String text = firstReadable(nodeScope);
                if (!text.isBlank()) {
                    last = text;
                }
            }
        }
        return last.isBlank() ? "" : "，" + truncate(last, 120);
    }

    /** 按节点执行序拼接各节点的自然语言产出（跳过 start/end/内部命名空间）。 */
    private static String joinNodeAnswers(Map<?, ?> outputData) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : outputData.entrySet()) {
            String key = String.valueOf(e.getKey());
            if ("flow".equals(key) || key.startsWith("__")) {
                continue; // flow 命名空间与内部键不是节点输出
            }
            if (!(e.getValue() instanceof Map<?, ?> nodeScope)) {
                continue;
            }
            String text = firstReadable(nodeScope);
            if (text.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append('·').append(text);
        }
        return sb.toString();
    }

    /** 节点输出 Map 内的第一段可读文本：nl_answer 族 → 结果条数概要。 */
    private static String firstReadable(Map<?, ?> nodeScope) {
        // 引擎变量语义：{{<nodeId>.output.<field>}} —— 自然语言字段在节点的 output 子命名空间内；
        // 兼容平铺结构（字段直接在节点 scope 上，如旧定义/手工数据）
        Object inner = nodeScope.get("output");
        if (inner instanceof Map<?, ?> innerScope) {
            String text = firstReadableInFields(innerScope);
            if (!text.isBlank()) {
                return text;
            }
        }
        return firstReadableInFields(nodeScope);
    }

    /** 在字段层提取可读文本：nl_answer 族（含 *_answer 后缀通配）→ 结果条数概要。 */
    private static String firstReadableInFields(Map<?, ?> fields) {
        for (String key : NL_ANSWER_KEYS) {
            if (fields.get(key) instanceof String s && !s.isBlank() && !"null".equals(s)) {
                return truncate(s.trim(), 160);
            }
        }
        // 工作流 outputParams 投影的自定义摘要字段（discover_answer/sparql_answer/compare_answer 等）
        for (Map.Entry<?, ?> e : fields.entrySet()) {
            if (String.valueOf(e.getKey()).endsWith("_answer")
                    && e.getValue() instanceof String s && !s.isBlank() && !"null".equals(s)) {
                return truncate(s.trim(), 160);
            }
        }
        for (String key : RESULT_KEYS) {
            Object v = fields.get(key);
            int count = v instanceof java.util.Collection<?> c ? c.size() : 0;
            if (count > 0) {
                return "产出 " + count + " 条结果";
            }
        }
        return "";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
